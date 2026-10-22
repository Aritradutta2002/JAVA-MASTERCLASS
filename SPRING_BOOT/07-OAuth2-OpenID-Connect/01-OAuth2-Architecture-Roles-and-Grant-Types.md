# OAuth2 Architecture, Roles & Grant Types — The Complete Interview Guide

> **Level:** Beginner → Expert
> **Interview Frequency:** ★★★★★ (the "what is OAuth and what are its roles/grant types?" question is the standard opener for any identity or API-security interview)
> **Prerequisites:** [06 · JWT Authentication](../06-JWT-Authentication/README.md) · [05 · Spring Security](../05-Spring-Security/README.md) · [README](./README.md)
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

- **OAuth 2.0 is a *delegation framework*, not an authentication protocol.** The precise sentence that wins the interview: *OAuth solves authorisation for a third-party client; it does not prove who the user is to the client.* Authentication is what OpenID Connect (chapter 03) adds on top.
- **The four roles are named in RFC 6749 §1.1**: **resource owner** (the user), **client** (the app asking for access), **authorization server** (issues tokens, holds the user identity) and **resource server** (the API holding the protected data). The authorization server and the resource server are often **the same deployment** — Keycloak does both.
- **Three token types, three different audiences.** The **access token** is a bearer credential for the *API*; the **refresh token** lets the *client* mint new access tokens; the **ID token** is an OIDC-only identity assertion for the *client*. Sending an ID token to your own API is a confused-deputy bug.
- **A scope is an OAuth concept; a role is an application concept.** A scope says *what this grant allows* ("read:orders"); a role says *what this user may do* ("ORDER_ADMIN"). You need an **explicit mapping** — the token's `scope` claim becomes `SCOPE_`-prefixed authorities by default, and nothing more.
- **There are seven grant types to know**, but only four you will actually use: **Authorization Code** (server web apps), **Authorization Code + PKCE** (SPAs and mobile — the default since OAuth 2.1), **Client Credentials** (machine-to-machine, *no user*), and **Refresh Token**. **Implicit is deprecated** and **removed in OAuth 2.1**.
- **The Client Credentials grant has no user.** The token's `sub` is the *application*, the scope is the application's own permissions, and there is **no refresh token** — the client just asks for a new access token when the old one expires.
- **PKCE is not optional any more.** `code_challenge = BASE64URL(SHA256(code_verifier))` with method `S256`; it closes the **authorization-code interception attack** in which an attacker who steals the `code` redeems it at the token endpoint.
- **A resource server should validate tokens locally against the JWKS**, not call the authorization server on every request. Local validation costs ~50–200 µs and keeps the API alive when the authorization server is down; introspection costs a network hop and makes the authorization server part of your API's availability chain.
- **Three Spring Boot starters, three different jobs.** `spring-boot-starter-oauth2-client` = you are the app doing "login with Google". `spring-boot-starter-oauth2-resource-server` = you are the API validating tokens. `spring-boot-starter-oauth2-authorization-server` = you are the issuer. Picking the wrong one is the most common configuration mistake in this whole area.
- **Discovery is how modern clients avoid hard-coding URLs.** `/.well-known/openid-configuration` (OIDC) and `/.well-known/oauth-authorization-server` (plain OAuth) publish the `authorization_endpoint`, `token_endpoint`, `jwks_uri` and everything else — the client fetches them at startup instead of hard-coding four URLs per provider.
- **The `redirect_uri` allowlist is the single most important security control in the whole protocol.** It must be an **exact string match** — including the port — and it is what stops an attacker from redirecting the authorization code to their own server.

---

## 1. What & Why — The Problem This Solves

**The problem.** Before OAuth, the only way for a third-party application to act on a user's behalf at another service was to ask the user for their **password**.

```text
   ❌ WHAT BREAKS WHEN A THIRD-PARTY APP HOLDS THE USER'S PASSWORD
   ──────────────────────────────────────────────────────────────────
   ① THE APP CAN LOG IN AS THE USER, FULL STOP
      There is no way to say "this app may read my photos but may not
      change my password". The credential is all-or-nothing.
   ② THE USER MUST RE-TYPE A PASSWORD THEY DO NOT OWN
      A photo-printing site needs the user's Google password. The user
      cannot set a restricted, revocable, expiring credential.
   ③ THE USER CANNOT AUDIT OR REVOKE PER-APP ACCESS
      "Revoke access" means "change my password" — which breaks every
      other app at once.
   ④ THE USER MUST TRUST THE APP WITH THEIR CREDENTIAL
      Phishing and credential-stuffing risk moves into the app.
   ⑤ THE APP MUST BUILD AND MAINTAIN ITS OWN LOGIN UI
      Every app reimplements password reset, MFA, lockout, hashing.
```

**The solution.** The user authenticates **at the authorization server**, which is the only party that ever sees the password. The authorization server then issues a **scoped, expiring credential** to the client — and the client passes that credential (never the password) to the resource server.

```text
   ✅ WITH OAUTH — THE USER NEVER LEAVES THE AUTHORIZATION SERVER
   ────────────────────────────────────────────────────────────────
   The user authenticates at the authorization server (Google, Okta,
   Keycloak). The client receives a TOKEN, not a password.
   The token says:
      WHO is acting      → the user (the resource owner)
      WHAT may be done   → the granted scopes
      AGAINST WHAT       → the resource server(s)
      UNTIL WHEN         → an expiry (access tokens: 5–15 min)
   The user can revoke ONE app without breaking the others.
   The app never handles a password, so it never stores one.
```

**The analogy — a valet key.** Think of a car and a valet. The old way: you hand the valet **your house key**, and now the valet can drive to your house, open your postbox, and stay in your driveway. The OAuth way: you hand the valet a **valet key** — it starts the engine and drives the car, but the glovebox and the front door are locked, and the valet must present it (a *bearer credential*) to do so, and the key expires in 15 minutes. The authorization server is the hotel desk that issues the valet key; the resource server is the car that checks it.

Three things fall straight out of the analogy, and all three are interview points:

1. **The credential is scoped** — the valet key opens the car, not the house.
2. **The credential is delegated, not transferred** — the valet key is *for the car*, and the desk knows who the guest is.
3. **The credential is a bearer token** — whoever holds it can use it. That is why the transport must be TLS and why it must be short-lived.

**What breaks *without* OAuth?** Nothing — but the moment you want a third-party integration, you have reinvented OAuth badly. The classic anti-pattern is the **"pre-issued API key per user"** hack: your app stores the user's `username:password` in its own database, or stores a long-lived per-user API key it minted itself. Both work until you need revocation granularity, consent, audit, expiry or multiple scopes.

> 💡 **The interview framing.** The question is never "what is OAuth?" It is:
> **"a user wants to grant a third-party app access to my data — how do I do that without giving it my password, and how do I make the grant narrow, expiring and revocable?"**
> The honest answer is: the authorization server authenticates the user, records a **consent grant** binding (client, scopes, user), and mints a **short-lived bearer token** carrying that grant. The client never sees a credential that outlives the grant.

---

## 2. Mental Model — The One Picture

```text
   THE FOUR ROLES AND THE THREE TOKENS
   ═══════════════════════════════════════════════════════════════════

                          ┌──────────────────────────────────────────┐
                          │           ① RESOURCE OWNER               │
                          │           (the end user)                  │
                          │  owns the data · grants access · never   │
                          │  sees the client secret                 │
                          └───────────────────┬──────────────────────┘
                                              │  "I trust Acme to read
                                              │   my files, read-only"
                                              ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │                    ② CLIENT  (your app — the "relying party")     │
   │                                                                    │
   │  public  (SPA, mobile)  →  no secret, MUST use PKCE               │
   │  confidential (server)  →  has a secret, SHOULD still use PKCE     │
   └──────────┬────────────────────────────────────┬────────────────────┘
              │ ③ AUTHORIZATION REQUEST             │
              │    /authorize?response_type=code   │
              │    &client_id=…&redirect_uri=…    │
              │    &scope=…&state=…&code_challenge │
              ▼                                     │
   ┌──────────────────────────────────────────────┴────────────────────┐
   │                    ③ AUTHORIZATION SERVER                        │
   │                    (holds the user identity)                      │
   │                                                                    │
   │   /authorize  →  authenticate the user → consent → issue a CODE   │
   │   /token      →  swap CODE (+ PKCE verifier) for tokens            │
   │   /userinfo   →  OIDC: return the user's claims                   │
   │   /jwks.json  →  publish the public keys                          │
   │   /revoke · /introspect · /.well-known/openid-configuration       │
   └──────────┬────────────────────────────────────┬────────────────────┘
              │ ④ ACCESS TOKEN                    │ ⑤ ID TOKEN (OIDC only)
              │    "you may do X on resource Y"   │    "the user is Alice"
              │    5–15 min · Bearer              │    audience = THIS CLIENT
              ▼                                     ▼
   ┌──────────────────────────────────────────────┬────────────────────┐
   │           ④ RESOURCE SERVER (your API)       │   the CLIENT only   │
   │                                            │   — never the API   │
   │  BearerTokenAuthenticationFilter            │                    │
   │       → JwtDecoder (JWKS, cached)            │  "sub":"alice",     │
   │       → Jwt → authorities                   │   "email":"a@acme"  │
   │       → SecurityContextHolder                │   "aud":"my-app"    │
   └────────────────────────────────────────────┴────────────────────┘

   ★ THE THREE TOKENS — WHO GETS WHAT
   ────────────────────────────────────────────────────────────────────
   ┌──────────────┬───────────────────────┬──────────────────────────┐
   │ TOKEN        │ WHO HOLDS IT          │ WHAT IT PROVES           │
   ├──────────────┼───────────────────────┼──────────────────────────┤
   │ ACCESS TOKEN │ the client → the API  │ "this bearer may do X    │
   │              │ (Authorization header)│  on resource Y for 15m"  │
   ├──────────────┼───────────────────────┼──────────────────────────┤
   │ REFRESH TOKEN│ the client only       │ "this client may ask for │
   │              │ (server-side / cookie)│  a new access token"     │
   ├──────────────┼───────────────────────┼──────────────────────────┤
   │ ID TOKEN     │ the client only       │ "the user is Alice,      │
   │ (OIDC)       │ (never the API)       │  authenticated by the AS"│
   └──────────────┴───────────────────────┴──────────────────────────┘

   ★ THE TWO ENDS OF THE PROTOCOL (the mental trap)
   ────────────────────────────────────────────────────────────────────
   /authorize  → /token        = the CLIENT talks to the AS
   /token      → your API      = the CLIENT talks to the RESOURCE SERVER
   The AS and the RS are different destinations even when they are the
   same product. Mixing them up = "why is my API calling /userinfo?"
```

**What to look at:** the picture separates the two conversations that make up OAuth.
**The front-channel conversation** (browser → authorization server) produces an
*authorization code*; **the back-channel conversation** (client server → token endpoint)
produces the *tokens*. And it separates the two token audiences: the **access token is
addressed to the API**, the **ID token is addressed to the client**. Almost every real-world
OAuth bug is one of these two separations being violated.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**The four roles, exactly as RFC 6749 §1.1 names them.**

| # | Role | RFC name | What it is | In your codebase |
|---|---|---|---|---|
| 1 | **Resource owner** | Resource Owner | The **user**. Owns the data and grants (or refuses) access to it. | A person; often only exists as an `email` + `sub` in the token |
| 2 | **Client** | Client | The **application** asking for access on the user's behalf. Has a `client_id`; may have a `client_secret`. | Your Spring Boot app (`spring-boot-starter-oauth2-client`) |
| 3 | **Authorization server** | Authorization Server | The service that **authenticates the user** and **issues tokens**. | Keycloak, Okta, Auth0, Spring Authorization Server |
| 4 | **Resource server** | Resource Server | The API that **holds the protected data** and **validates the access token**. | Your Spring Boot app (`spring-boot-starter-oauth2-resource-server`) |

> ⚠️ **The most common structural misconception.** "The authorization server and the resource
> server are different servers" is *usually* wrong in practice. In Keycloak, in Okta and in Spring
> Authorization Server, **one product hosts both roles** — different URLs, same deployment, same
> database. The RFC separates them **conceptually** so that the protocol does not require them to
> be the same thing, and so you can reason about trust boundaries. Say that explicitly in an
> interview: *"conceptually distinct roles, frequently one deployment."*

**The tokens.**

| Token | Format | Lifetime | Who consumes it | Sent as |
|---|---|---|---|---|
| **Access token** | JWT (common) or opaque | **5–15 min** | The **resource server** | `Authorization: Bearer <token>` |
| **Refresh token** | Opaque (recommended) or JWT | **7–30 days** | The **authorization server** (via the client) | `POST /token` body, or an `HttpOnly` cookie for a browser |
| **ID token** (OIDC only) | JWT — **always** a JWT | **5–60 min** | The **client** | In the token response; never to the API |

**Scopes vs roles — the distinction interviewers probe.**

| | **Scope** | **Role / Authority** |
|---|---|---|
| Layer | **OAuth** (protocol) | **Your application** (domain) |
| Meaning | "What this **grant** permits" | "What this **principal** may do" |
| Example | `orders:read profile:read` | `ORDER_ADMIN`, `CUSTOMER_SERVICE` |
| Who defines it | The authorization server (or you, if you own it) | You |
| Where it lives | The `scope` claim of the access token | The `SecurityContext` authorities |
| Granularity | Coarse — usually a handful of verbs on a resource | Fine — as many as your domain needs |

**The mapping.** Spring Security's default is a mechanical one: the `scope` claim becomes
`SCOPE_`-prefixed authorities.

```text
   token claim:  "scope": "orders:read profile:read"
   default map :  SCOPE_orders:read, SCOPE_profile:read
   check with  :  @PreAuthorize("hasAuthority('SCOPE_orders:read')")
```

That default is **almost always wrong for a real application** because a scope like
`orders:read` is a protocol artefact, not a business permission. The production mapping is
usually two-step:

```text
   scope  →  (application-specific translator)  →  authority
   "orders:read"      →  ORDER_READER
   "orders:write"     →  ORDER_WRITER
   "profile:read"     →  PROFILE_SELF
```

and the translator is a `JwtAuthenticationConverter` with a custom
`JwtGrantedAuthoritiesConverter` (chapter 03) or, in a client-credentials world, a
`GrantedAuthoritiesMapper`.

**OAuth vs OIDC vs SAML — the 30-second version.**

| | **OAuth 2.0** | **OpenID Connect 1.0** | **SAML 2.0** |
|---|---|---|---|
| Purpose | **Delegated authorisation** | **Authentication** (identity) | **Authentication** (identity) |
| Output | Access token | Access token **+ ID token** | **SAML assertion** (XML) |
| Identity endpoint | ❌ | `userinfo` | Attribute service / IdP portal |
| Request the identity | ❌ | `scope=openid` → ID token | Automatic in the SSO flow |
| Spring support | `spring-boot-starter-oauth2-client` / `-resource-server` | same | `spring-security-saml2-service-provider` |

**How OAuth relates to the rest of your security stack.**

```text
   ┌────────────────────────────────────────────────────────────────┐
   │  Spring Security 5 — the foundation: filters, Authentication, │
   │  AuthorizationManager, the SecurityContext                     │
   ├────────────────────────────────────────────────────────────────┤
   │  Spring Security OAuth2 — the *protocol* layer: client login,  │
   │  resource-server token validation, JWT/JOSE support            │
   ├────────────────────────────────────────────────────────────────┤
   │  OAuth2 Client        → your app logs users IN                  │
   │  OAuth2 Resource Server → your app validates tokens             │
   │  OAuth2 Authorization Server → your app ISSUES tokens           │
   ├────────────────────────────────────────────────────────────────┤
   │  Spring Authorization Server (Boot starter) — a full RFC 6749  │
   │  + OIDC + JWT authorization server you can run yourself         │
   └────────────────────────────────────────────────────────────────┘
```

### 3.2 Intermediate Level

**The seven grant types.** Each one is a different way of answering a single question:
*how does the client get the tokens?*

| # | Grant | Wire steps (compressed) | Client type | Status |
|---|---|---|---|---|
| 1 | **Authorization Code** | browser → `/authorize` → `code` → back-channel `POST /token` → tokens | Confidential (server web app) | ✅ Recommended |
| 2 | **Authorization Code + PKCE** | same, plus `code_challenge` on the front channel and `code_verifier` on the back channel | **Public** (SPA, mobile) — also confidential | ✅ **Default since OAuth 2.1** |
| 3 | **Client Credentials** | back-channel only: `POST /token` with `grant_type=client_credentials` | Confidential (service) | ✅ Recommended |
| 4 | **Implicit** | browser → `/authorize` → **token in the URL fragment** | Public | ❌ **Deprecated / removed in OAuth 2.1** |
| 5 | **Device Authorization** | `POST /device_authorization` → user code → user polls `POST /token` | Public (TV, CLI, IoT) | ✅ RFC 8628 |
| 6 | **Refresh Token** | back-channel: `POST /token` with `grant_type=refresh_token` | Any | ✅ RFC 6749 §6 |
| 7 | **Token Exchange** | back-channel: `POST /token` with `grant_type=urn:ietf:params:oauth:grant-type:token-exchange` | Confidential | ✅ RFC 8693 |

**1. Authorization Code (RFC 6749 §4.1).** The browser is redirected to the authorization
server; the user authenticates and consents; the AS redirects back with a **short-lived,
single-use `code`**; the **client's back end** exchanges that code for tokens.

```text
   BROWSER                          CLIENT SERVER                AUTHORIZATION SERVER
   ───────                          ─────────────                ────────────────────
    │  302 → /authorize?...&state=xy                              │
    ├────────────────────────────────────────────────────────────►│
    │                                       (user logs in, consents)
    │  302 ← /callback?code=abc123&state=xy                      │
    │◄────────────────────────────────────────────────────────────┤
    │                                     POST /token
    │                                     grant_type=authorization_code
    │                                     code=abc123
    │                                     client_id + client_secret
    ├────────────────────────────────────────────────────────────►│
    │                                     { access_token, refresh_token, id_token }
    │                                     ◄──────────────────────┤
```

Why the indirection? Because the front channel is the **browser**, and the browser is
untrusted. The `code` is a *single-use, 30–60 second, client-bound* artefact; the tokens are
returned on the **back channel**, directly between the client server and the AS, over TLS,
with the client secret. The user never sees a token.

**2. Authorization Code + PKCE (RFC 7636).** Identical to #1, plus a binding:

```text
   FRONT CHANNEL (browser):   code_challenge = BASE64URL(SHA256(code_verifier))
   BACK CHANNEL (client):     code_verifier  = the original random string
   The AS stores the CHALLENGE and, on /token, recomputes
   BASE64URL(SHA256(code_verifier)) and compares it to the stored challenge.
   Mismatch → invalid_grant, the code is NOT redeemed.
```

Full detail in chapter 02.

**3. Client Credentials (RFC 6749 §4.4).** No browser, no user, no redirect.

```text
   CLIENT SERVICE                     AUTHORIZATION SERVER
   ──────────────                     ────────────────────
        │  POST /token
        │  grant_type=client_credentials
        │  scope=orders:read
        │  Authorization: Basic base64(client_id:client_secret)
        ├──────────────────────────────►│
        │  { "access_token":"eyJhbGci…", "token_type":"Bearer", "expires_in":300 }
        │  ◄────────────────────────────┤
```

The token's subject is the **application**. The scope is the application's own permission set.
There is **no refresh token** — the client requests a new access token when the current one
expires, which is a single cheap back-channel call.

**4. Implicit (RFC 6749 §4.2) — deprecated.** The AS returns the access token **directly in the
URL fragment**:

```text
   302 → https://client.example.com/callback#access_token=eyJhbGci…&token_type=bearer&expires_in=3600
```

**Why it is banned in OAuth 2.1.** Three concrete reasons, not "it was insecure":

1. **The token is in the URL.** Fragments are not sent to the server, but they *are* kept in
   browser history, are visible in the address bar, and are exposed to any JavaScript on the page
   (including third-party analytics scripts) via `location.hash`.
2. **There is no client authentication.** A public client cannot prove who it is, so the AS has
   no way to bind the issued token to a registered client — which means a token could in
   principle be replayed by any party who intercepted it.
3. **There is no `code` to bind the transaction to.** The token arrives before the client has
   done anything, so there is no opportunity for a `code_challenge`/`code_verifier` binding.

The replacement is **Authorization Code + PKCE**, which keeps the token on the back channel and
adds the binding that implicit never had.

**5. Device Authorization Grant (RFC 8628).** For input-constrained devices: smart TVs, set-top
boxes, CLI tools, printers, IoT devices that have a keyboard but no browser.

```text
   DEVICE                         AS                        USER's PHONE/BROWSER
   ──────                         ──                        ───────────────────
   POST /device_authorization
   client_id, scope               │
   ├────────────────────────────►│
   │ { device_code, user_code,    │
   │   verification_uri,          │
   │   verification_uri_complete, │
   │   expires_in: 1800 }        │
   │ ◄────────────────────────────┤
   │                                                        (user opens the URL
   │                                                         on another device,
   │                                                         enters user_code,
   │                                                         logs in, consents)
   │  POST /token (poll every 5s)
   │  grant_type=urn:ietf:params:oauth:grant-type:device_code
   │  device_code=…, client_id=…
   ├────────────────────────────►│
   │  { authorization_pending }  │  ← while the user has not approved yet
   │  { access_token, … }        │  ← once approved
   │ ◄────────────────────────────┤
```

The polling response `authorization_pending` (RFC 8628 §3.5) is not an error — the client
keeps polling at the interval the AS specified.

**6. Refresh Token Grant (RFC 6749 §6).** Covered in depth in chapter 03.

**7. Token Exchange (RFC 8693).** Swapping one token for another, for two real use cases:

```text
   DELEGATION:    a service holding a user's token needs a downstream service to act
                  FOR the user → exchange the user token for a narrower downstream token
                  (requested_token_type=urn:ietf:params:oauth:token-type:access_token,
                   subject_token=the user's token,
                   audience=downstream-api, act=the service acting on the user's behalf)

   IMPERSONATION: a trusted service needs to act AS the user with no user context
                  → subject_token_type=urn:ietf:params:oauth:token-type:access_token,
                    actor_token=the service's own token, actor_token_type=...:client_credentials
```

Chapter 03 covers the mechanics; the security angle is in §12 here.

**OAuth 2.1 — the current consolidation draft.** OAuth 2.0 (2012) had accumulated real problems,
and the OAuth 2.1 IETF draft folds the fixes in:

| Change | Why |
|---|---|
| **PKCE is mandatory** for the authorization code grant | closes the code-interception attack for public clients |
| **The implicit grant is removed** | the token in the URL fragment is a leak channel |
| **The resource-owner-password grant is removed** | it trained users to hand passwords to third parties — the exact thing OAuth exists to stop |
| **Exact `redirect_uri` matching** | prefix/wildcard matching has produced real open-redirect and code-theft bugs |
| **Refresh tokens for public clients must be sender-constrained or rotated** | a long-lived credential on a device that cannot keep a secret needs rotation with reuse detection |
| **`Bearer` is deprecated in favour of `DPoP`/mTLS-bound tokens** | a bearer token can be replayed by anyone who copies it (RFC 9449 / RFC 8705) |

**The protocol endpoints.** The **names below are the logical RFC endpoints**; real products
map them to their own URLs, which is precisely why **discovery documents** exist.

| Logical endpoint | Purpose | Spring Authorization Server default | Keycloak default |
|---|---|---|---|
| `/authorize` | Start the authorization request (front channel) | `/oauth2/authorize` | `/realms/{realm}/protocol/openid-connect/auth` |
| `/token` | Exchange a grant for tokens (back channel) | `/oauth2/token` | `/realms/{realm}/protocol/openid-connect/token` |
| `/userinfo` | OIDC: return the user's claims | `/oauth2/userinfo` | `/realms/{realm}/protocol/openid-connect/userinfo` |
| `/revoke` | Revoke a token (RFC 7009) | `/oauth2/revoke` | `/realms/{realm}/protocol/openid-connect/revoke` |
| `/introspect` | Ask about a token (RFC 7662) | `/oauth2/introspect` | `/realms/{realm}/protocol/openid-connect/token/introspect` |
| `/.well-known/openid-configuration` | OIDC discovery | `/.well-known/openid-configuration` | same |
| `/.well-known/oauth-authorization-server` | OAuth AS metadata (RFC 8414) | `/.well-known/oauth-authorization-server` | — (Keycloak serves the OIDC one) |
| `/jwks.json` | Publish the public signing keys | `/oauth2/jwks` | `/realms/{realm}/protocol/openid-connect/certs` |

> 💡 **Why the discovery document exists.** Hard-coding `https://idp.example.com/authorize` and
> `https://idp.example.com/token` in every application means every IdP URL change is a
> coordinated redeploy across your estate, and every new IdP needs a new config block. The
> discovery document turns **one** URL (`issuer-uri`) into the full endpoint set. Spring Boot's
> `issuer-uri` property does exactly this — see chapter 03.

**The consent model.** Three levels — **no consent** (a trusted or first-party client: the
AS redirects straight back), **static consent** (the scopes are pre-agreed, so nothing is
shown at request time), and **dynamic consent** (the user approves the specific scopes on a
consent screen, usually with "remember this decision" **off** by default).

### 3.3 Advanced Level

**The Spring Security OAuth2 client model.** Spring models "a registered client" as a
`ClientRegistration` bean:

```java
// The Spring Security model of a registered client (Spring Security 6.1+).
public final class ClientRegistration {

    private String registrationId;                       // "google" — your local handle
    private String clientId;                             // the public identifier
    private String clientSecret;                         // null for a public client
    private ClientAuthenticationMethod clientAuthenticationMethod;  // client_secret_basic | client_secret_post | none
    private AuthorizationGrantType authorizationGrantType;          // authorization_code | client_credentials | refresh_token
    private String redirectUri;                          // "{baseUrl}/login/oauth2/code/{registrationId}"
    private Set<String> scope;                           // the scopes you request
    private URI authorizationUri;                        // /authorize
    private URI tokenUri;                                // /token
    private URI userInfoUri;                             // /userinfo
    private URI jwkSetUri;                               // from the discovery document
    private String userNameAttributeName;                // which claim is the "username"
    private Map<String, Object> providerConfigurationMetadata;  // everything else from the provider
    private ClientSettings clientSettings;               // e.g. requireProofKey
    private ProviderDetails providerDetails;
}
```

The registration is assembled by a `ClientRegistrationRepository` — in Boot, an
`InMemoryClientRegistrationRepository` built from your `application.yml`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: [ openid, profile, email ]
```

**`ClientRegistration` vs `OAuth2AuthorizedClient` — the distinction everybody gets wrong.**

| | `ClientRegistration` | `OAuth2AuthorizedClient` |
|---|---|---|
| What it is | The **static registration** of a client with the provider | A **runtime grant**: a registered client + the tokens it currently holds |
| Created by | **You**, at startup, from configuration | The framework, **after** a successful token response |
| Contains | `clientId`, `clientSecret`, redirect URI, scopes | `accessToken`, `refreshToken`, `registrationId`, `clientId`, `principalName`, `scopes` |
| Mutates over time? | ❌ static | ✅ the access token changes on every refresh |
| Stored where | In the `ClientRegistrationRepository` (usually in-memory) | In an `OAuth2AuthorizedClientRepository` (session, or a DB table) |

So: **registration** = "this client is allowed to ask"; **authorized client** = "this client
currently holds these tokens, on behalf of this principal".

**`OAuth2AuthorizedClientManager` — how a server-side client obtains and refreshes its own
access tokens.** This is the API you use when your **server** (not the browser) needs to call
another API on behalf of the logged-in user.

```java
// The chain: repository (where grants live) + provider (how a new token is obtained).
OAuth2AuthorizedClientService service = new InMemoryOAuth2AuthorizedClientService();
OAuth2AuthorizedClientProvider provider = new AuthorizationCodeOAuth2AuthorizedClientProvider();

OAuth2AuthorizedClientManager manager =
        new AuthorizedClientServiceOAuth2AuthorizedClientManager(service, provider);

// Use it:
OAuth2AuthorizedClient client = manager.authorize(OAuth2AuthorizedClientRequest
        .withClientRegistrationId("google")
        .principal(SecurityContextHolder.getContext().getAuthentication())
        .attribute(OAuth2AuthorizedClientManager.REQUEST_SCOPE_KEY, ...)
        .build());

String accessToken = client.getAccessToken().getTokenValue();
```

The servlet equivalents of the reactive providers are
`AuthorizationCodeOAuth2AuthorizedClientProvider`,
`RefreshTokenOAuth2AuthorizedClientProvider` and
`ClientCredentialsOAuth2AuthorizedClientProvider`
(`AuthorizationCodeReactiveOAuth2AuthorizedClientProvider` is the WebFlux twin of the first).

**`OAuth2AuthorizedClientRepository` — the storage half.** It answers two questions:
*which authorized client is bound to this request?* and *should I save the refreshed tokens?*

```java
public interface OAuth2AuthorizedClientRepository {
    <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String registrationId,
                                                               Authentication principal,
                                                               HttpServletRequest request);
    void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient,
                              Authentication principal,
                              HttpServletRequest request);
    void removeAuthorizedClient(String registrationId,
                                Authentication principal,
                                HttpServletRequest request);
}
```

**Token propagation — from the client to the resource server.** When your server-side client
calls a downstream API, it must send **the user's** token, not its own. The idiomatic way is
an `OAuth2AuthorizedClient` argument resolver, so the controller can simply declare it:

```java
// Spring MVC: inject the authorized client as a method argument.
@GetMapping("/me")
public Map<String, Object> me(@AuthenticationPrincipal OAuth2User user,
                              OAuth2AuthorizedClient client) {
    // client.getAccessToken().getTokenValue() is the USER's token for the downstream API.
    return Map.of("user", user.getName(), "tokenExpiresAt", client.getAccessToken().getExpiresAt());
}
```

The manual alternative is a `RestClient` interceptor:

```java
@Bean
RestClient downstreamRestClient(OAuth2AuthorizedClientManager manager) {
    RestClient restClient = RestClient.builder().build();
    return restClient.requestInterceptor((request, body, execution) -> {
        // WHY: the downstream API must see the USER's token, so the audience is right
        // and the scopes are the user's, not the service's.
        OAuth2AuthorizedClient client = manager.authorize(
                OAuth2AuthorizedClientRequest.withClientRegistrationId("downstream-api")
                        .principal(SecurityContextHolder.getContext().getAuthentication())
                        .build());
        request.getHeaders().setBearerAuth(client.getAccessToken().getTokenValue());
        return execution.execute(request, body);
    });
}
```

**The resource-server side.** The other half of the picture — the API that validates tokens —
is driven by `JwtDecoder` (for JWTs) or `OpaqueTokenIntrospector` (for opaque tokens):

```java
// The Boot 3.3 auto-configuration that does all of this for you:
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.acme.com/realms/acme
```

That single property triggers **OIDC discovery at startup**: Boot fetches
`https://auth.acme.com/realms/acme/.well-known/openid-configuration`, reads `jwks_uri` from it,
and builds a `NimbusJwtDecoder` against it. Chapter 03 covers this in detail.

**The `scope` → authority mapping in a resource server.** `JwtGrantedAuthoritiesConverter`
reads the `scope` (or `scp`) claim and produces `SCOPE_`-prefixed authorities by default.
To read a custom claim, override `setAuthorityPrefix` and `setAuthoritiesClaimName`.

**The three-party vocabulary you must not mix up.** This is the most common interview
misunderstanding, and it shows up as a security bug:

```text
   ┌──────────────┐        ┌────────────────────┐        ┌──────────────────┐
   │  YOUR APP    │        │  AUTHORIZATION     │        │  THIRD-PARTY API │
   │  (client)    │        │  SERVER            │        │  (resource srv)  │
   ├──────────────┤        ├────────────────────┤        ├──────────────────┤
   │ client_id ✓  │───────►│                    │        │                  │
   │ client_secret│        │  knows the USER    │        │  knows the TOKEN │
   │              │        │  (authenticates)   │        │  (authorises)    │
   │              │        │                    │        │  NEVER the user  │
   │ access_token │────────┼───────────────────►│        │                  │
   │              │        │                    │        │  checks aud/scope│
   └──────────────┘        └────────────────────┘        └──────────────────┘
      knows the CLIENT         knows the USER                knows the GRANT
      (and the token)          (and the client)              (and nothing else)
```

### 3.4 Expert Level

**The trust model, stated precisely.** Three different parties hold three different kinds of
knowledge, and the protocol is designed so that **no party needs to hold more than one**:

- The **authorization server** is the only party that knows the **user's identity** and the
  **client's secret**.
- The **resource server** only ever sees an **access token**. It must therefore be able to
  answer "is this token valid, and what does it permit?" *without* asking the AS — which is
  exactly why JWTs with a locally cached JWKS became the dominant design.
- The **client** holds the **tokens** and the **grant**, but must never be trusted to enforce
  its own scope restrictions — a malicious client can request any scope it likes; only the AS
  and the RS enforce.

**Why the "resource server calls the AS" design (introspection) still exists.** Because
self-contained JWTs have two costs: (1) a **revoked token stays valid until it expires** — the
signature is permanent, and (2) the **audience/scope are baked in at issuance** and cannot be
consulted live. Introspection (RFC 7662) trades latency and availability for immediacy. The
decision table:

| | **Local JWT validation** | **Introspection** |
|---|---|---|
| Latency per request | ~50–200 µs (no network) | 1 network hop (5–40 ms typical) |
| Availability coupling | AS outage ⇒ API still serves | AS outage ⇒ **API cannot authenticate** |
| Revocation | Only at expiry | **Immediate** |
| Scale | Unlimited | AS must handle **every** request |
| Token size | Large (1–3 KB) | Small (opaque, ~40 chars) |
| Good for | Public, high-volume APIs | High-value APIs needing instant revocation |

**Sender-constrained tokens — the frontier.** The bearer-token model has one structural flaw:
**possession is proof**. The mitigations, all RFC-numbered:

| Mechanism | RFC | Idea | Status |
|---|---|---|---|
| **mTLS-bound access tokens** | RFC 8705 | The token is bound to the client's TLS certificate (`cnf.x5t#S256`) — a copy-paste attacker cannot use it | Deployed (Keycloak supports it) |
| **DPoP** (Demonstrating Proof of Possession) | RFC 9449 | The token is bound to a key the client holds; every request carries a proof signed by that key, so a stolen token is useless without the key | Emerging; Spring Authorization Server 1.4+ has experimental support |
| **Private Key JWT** (client authentication) | RFC 7523 | The client authenticates with an asymmetric key rather than a shared secret | Widely supported |

OAuth 2.1 explicitly requires refresh tokens for public clients to be **sender-constrained or
rotated**, because a public client cannot keep a secret.

**FAPI (Financial-grade API) — the hardened profile.** FAPI 1.0 Advanced adds, on top of
OAuth 2.1: **PKCE mandatory**, **mTLS or DPoP** for sender-constrained tokens, **no implicit
flow**, **exact redirect-uri matching**, **sender-constrained refresh tokens**, and **PAR**
(Pushed Authorization Requests, RFC 9126) to prevent request tampering. If your interview is
in a bank or a payments company, knowing "PAR" and "sender-constrained" is a strong signal.

**JAR (JWT-Secured Authorization Request, RFC 9101).** The authorization request itself is a
signed JWT, so the AS can verify that the client meant exactly these parameters and that
nobody tampered with the scopes in transit. It complements PAR: JAR gives *integrity*,
PAR gives *confidentiality* (the request never leaves the client in the clear).

**DPoP and the "proof" in the token.** A DPoP-bound token carries a `cnf` claim with the
thumbprint of the client's public key:

```json
{ "cnf": { "jkt": "0ZcOCORZNYy-DWpqq30jZyJGHTN0d2HglBV3uiguA4I" } }
```

and every request must include a `DPoP` header with a JWT signed by that key, containing the
`htu` (the HTTP URI) and `htm` (the method). A stolen token alone is useless.

**OAuth 2.1 in one paragraph.** It removes the two grants that cannot be made safe for public
clients (implicit and password), it requires PKCE everywhere, it requires exact redirect-uri
matching, and it recognises that a bearer token on a public device is a liability — hence
rotation and sender-constrained refresh tokens. The **directional change** is worth stating in
an interview: OAuth 2.0 optimised for *simplicity of integration*; OAuth 2.1 optimises for
*the client being untrusted*.

---

## 4. Internal Working Mechanism

### 4.1 The client-side login chain (who calls whom)

The "login with Google" chain in Spring Security 6.1:

```text
   1. GET /oauth2/authorization/google
      └─▶ OAuth2AuthorizationRequestRedirectFilter#doFilter
            └─▶ OAuth2AuthorizationRequestResolver#resolve            [EXTENSION POINT]
                  └─▶ DefaultOAuth2AuthorizationRequestResolver
                        ├─ builds OAuth2AuthorizationRequest
                        │    (response_type=code, client_id, redirect_uri,
                        │     scope, state, + any additionalParameters)
                        ├─ calls clientRegistration.getProviderDetails()
                        │    .getConfigurationMetadata()  ← the discovery document
                        └─ builds the redirect URI
            └─▶ AuthorizationRequestRepository#saveAuthorizationRequest   [STATE IS STORED HERE]
            └─▶ 302 Location: https://accounts.google.com/o/oauth2/v2/auth?...

   2. GET /login/oauth2/code/google?code=…&state=…
      └─▶ OAuth2AuthorizationCodeGrantFilter#doFilter
            └─▶ OAuth2LoginAuthenticationFilter#doFilter
                  ├─▶ AuthorizationRequestRepository#removeAuthorizationRequest  [STATE IS COMPARED HERE]
                  ├─▶ OAuth2AuthorizationRequest.buildAuthorizationRequest(...)
                  └─▶ AuthenticationManager#authenticate
                        └─▶ OAuth2LoginAuthenticationProvider#authenticate
                              ├─▶ exchange code for tokens
                              │     (RestOperations → token_uri, via
                              │      OAuth2AccessTokenResponseHttpMessageConverter)
                              ├─▶ OAuth2UserService / OidcUserService
                              │     (DefaultOAuth2UserService / DefaultOidcUserService)
                              │      → OidcUserInfoEndpoint / userinfo_uri
                              └─▶ returns OAuth2LoginAuthenticationToken
            └─▶ OAuth2AuthenticationToken placed in the SecurityContext
            └─▶ successHandler (default: OAuth2LoginAuthenticationSuccessHandler)
```

**The extension points a developer actually implements:**

| Interface | What you implement it for |
|---|---|
| `OAuth2AuthorizationRequestResolver` | Add PKCE, add custom parameters, change the redirect URI |
| `AuthorizationRequestRepository` | Where `state` lives — session, cookie, or a distributed store for a multi-node app |
| `OAuth2UserService` | Map the provider's user into your own `UserDetails`/domain user; fetch extra data (e.g. GitHub's email) |
| `OidcUserService` | Same, for OIDC providers, where the ID token already carries the claims |
| `OAuth2AuthorizedClientProvider` | How a new access token is obtained (code exchange, refresh, client credentials) |
| `OAuth2AuthorizedClientRepository` | Where the granted tokens are stored between requests |
| `GrantedAuthoritiesMapper` | The scope → authority mapping |
| `OAuth2AuthorizedClientManager` | The orchestration of the two above |

### 4.2 The resource-server validation chain

```text
   Request:  GET /api/orders   Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

   BearerTokenAuthenticationFilter#doFilter
     ├─▶ BearerTokenResolver#resolve            [DefaultBearerTokenResolver]
     │     reads the "Authorization" header, checks the "Bearer " prefix
     ├─▶ AuthenticationManager#authenticate
     │     └─▶ ProviderManager
     │           └─▶ JwtAuthenticationProvider#authenticate
     │                 ├─▶ JwtDecoder#decode                   [NimbusJwtDecoder]
     │                 │     ├─ fetch/refresh the JWK Set (cached)
     │                 │     ├─ resolve the key by `kid`
     │                 │     ├─ verify the signature (pinned algorithm)
     │                 │     └─ run the OAuth2TokenValidator chain
     │                 │           ├─ JwtTimestampValidator (exp/nbf, 60 s skew)
     │                 │           ├─ JwtIssuerValidator  (iss)
     │                 │           └─ YOUR audience validator (aud)   ← often missing
     │                 └─▶ JwtAuthenticationConverter#convert
     │                       └─▶ JwtGrantedAuthoritiesConverter → authorities
     ├─▶ SecurityContextHolder
     └─▶ AuthorizationFilter#doFilter           [the final authorization decision]
```

> ⚠️ **The single most-missed line in that chain.** `JwtValidators.createDefaultWithIssuer()`
> validates the **issuer** and the **timestamps** — it does **not** validate the **audience**.
> Chapter 03 §8 shows the exact fix.

### 4.3 The authorized-client refresh chain (server-to-server, on behalf of a user)

```text
   Service method needs a downstream access token
     └─▶ OAuth2AuthorizedClientManager#authorize(request)
           ├─▶ OAuth2AuthorizedClientRepository#loadAuthorizedClient(...)
           │     → an existing grant with a valid, unexpired access token? → RETURN IT
           ├─▶ (expired or missing) → OAuth2AuthorizedClientProvider#authorize
           │     ├─ AuthorizationCodeOAuth2AuthorizedClientProvider
           │     │     → re-run the code exchange using the stored refresh token
           │     ├─ RefreshTokenOAuth2AuthorizedClientProvider
           │     │     → POST /token with grant_type=refresh_token
           │     └─ ClientCredentialsOAuth2AuthorizedClientProvider
           │           → POST /token with grant_type=client_credentials
           └─▶ OAuth2AuthorizedClientRepository#saveAuthorizedClient(...)
```

**The extension point:** implement `OAuth2AuthorizedClientProvider` to add a custom grant,
or `OAuth2AuthorizedClientService` to store the grants in a database rather than in memory
(the in-memory service loses every grant on restart and cannot be shared across nodes).

---

## 5. Step-by-Step Execution Flow

### 5.1 The Authorization Code flow, request by request

1. **The user clicks "Log in with Google".** The browser is sent to
   `GET /oauth2/authorization/google` on the client.
2. **The client builds the authorization request.** `DefaultOAuth2AuthorizationRequestResolver`
   produces a URL on the authorization server:
   ```text
   GET https://accounts.google.com/o/oauth2/v2/auth
       ?response_type=code
       &client_id=1049...apps.googleusercontent.com
       &scope=openid%20profile%20email
       &state=6f3a...            ← 20+ random bytes, stored in the session
       &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fgoogle
       &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
       &code_challenge_method=S256
   ```
3. **The authorization server authenticates the user.** Login form, MFA, password manager —
   whatever the IdP does. The AS *never* sends the password to the client.
4. **The authorization server asks for consent** (or skips it, if the grant is pre-approved).
5. **The AS redirects back** with a **single-use authorization code**:
   ```text
   GET http://localhost:8080/login/oauth2/code/google?code=4/0Ad…&state=6f3a…
   ```
6. **The client validates `state`.** It loads the pending request from the
   `AuthorizationRequestRepository` and compares the stored `state` with the returned one. A
   mismatch ⇒ the request is rejected (`invalid_state`).
7. **The client redeems the code on the back channel:**
   ```text
   POST https://accounts.google.com/o/oauth2/v4/token
   Content-Type: application/x-www-form-urlencoded
   Authorization: Basic <base64(client_id:client_secret)>

   grant_type=authorization_code
   &code=4/0Ad…
   &redirect_uri=http://localhost:8080/login/oauth2/code/google
   &code_verifier=btlY8…                     ← only with PKCE
   ```
8. **The AS validates and responds:**
   ```json
   { "access_token": "ya29.a0Af…", "expires_in": 3599,
     "refresh_token": "1//0eXa…", "scope": "openid profile email",
     "token_type": "Bearer", "id_token": "eyJhbGciOi…" }
   ```
9. **The client fetches the user info** (or decodes the ID token) and builds an
   `OAuth2AuthenticationToken` whose principal is an `OAuth2User` / `OidcUser`.
10. **The success handler runs.** The default
    `OAuth2LoginAuthenticationSuccessHandler` redirects to
    `SavedRequestAwareAuthenticationSuccessHandler`'s saved URL, or `/`.
11. **The client calls the API** with the access token:
    ```text
    GET /api/orders   Authorization: Bearer ya29.a0Af…
    ```
12. **The resource server validates locally** against the cached JWKS and authorises the request.

### 5.2 The Client Credentials flow, request by request

1. The service decides it needs a token for the downstream API.
2. It POSTs to the token endpoint with its own credentials — **no browser, no user**:
   ```text
   POST https://auth.acme.com/realms/acme/protocol/openid-connect/token
   Authorization: Basic <base64(client_id:client_secret)>
   Content-Type: application/x-www-form-urlencoded

   grant_type=client_credentials&scope=orders:read
   ```
3. The AS authenticates the client (`client_secret_basic`), checks the requested scope against
   the client's assigned scopes, and issues a token whose `sub` is the **client id**.
4. The service calls the API with the token and **caches it until `expires_in` minus a skew**
   (typically 60 seconds).
5. When it expires, the service requests a new one. **No refresh token is involved** — there
   is no user session to preserve.

### 5.3 The Token Exchange flow, request by request

1. Service A holds a user's access token for `api-a`.
2. It needs `api-b` to act on the user's behalf.
3. It POSTs to the token endpoint with `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`,
   the `subject_token`, the `subject_token_type`, the requested `audience` and (optionally)
   the `act` claim:
   ```text
   POST https://auth.acme.com/oauth2/token
   grant_type=urn:ietf:params:oauth:grant-type:token-exchange
   &subject_token=eyJhbGciOi…
   &subject_token_type=urn:ietf:params:oauth:token-type:access_token
   &requested_token_type=urn:ietf:params:oauth:token-type:access_token
   &audience=https://api-b.acme.com
   &client_id=service-a&client_secret=…
   ```
4. The AS validates the subject token, checks that the client is allowed to exchange it, and
   issues a **new, narrower token** for `api-b` — optionally with an `act` claim naming the
   party that is actually acting.

---

## 6. Visualizations

### 6.1 The four roles exchanging credentials and tokens (sequence)

```mermaid
sequenceDiagram
    autonumber
    actor User as Resource Owner<br/>(browser)
    participant C as Client<br/>(your app)
    participant AS as Authorization Server
    participant RS as Resource Server<br/>(your API)

    Note over User: never gives the password to the client
    User->>C: GET /oauth2/authorization/google
    C->>C: build authorization request<br/>(state, PKCE challenge, scope)
    C-->>User: 302 → /authorize?response_type=code&...&state=...
    User->>AS: GET /authorize?client_id=...&scope=...
    AS->>User: authenticate (login / MFA)
    AS->>User: consent screen
    AS-->>User: 302 → redirect_uri?code=XYZ&state=...
    User->>C: GET /login/oauth2/code/google?code=XYZ&state=...
    C->>C: load + compare stored state
    C->>AS: POST /token (code + client_secret + code_verifier)
    AS-->>C: access_token + refresh_token + id_token
    C->>RS: GET /api/orders  Authorization: Bearer <access_token>
    RS->>RS: verify signature against cached JWKS
    RS->>RS: validate iss / aud / exp / scope
    RS-->>C: 200 OK
```

**What to look at:** two separate conversations. The **front channel** (browser ↔ AS) only ever
carries an authorization code and a `state`; the **back channel** (client server ↔ AS) is where
the tokens and the client secret live. And note that the **resource server never talks to the
authorization server** — it validates the token itself.

### 6.2 The four roles and the three tokens (ASCII)

```text
                        ┌───────────────────────────────────────────┐
                        │        ① RESOURCE OWNER (the user)        │
                        │  owns the data · grants access · never     │
                        │  sees the client secret · never sees a    │
                        │  token it did not ask for                 │
                        └────────────────────┬──────────────────────┘
                                             │  consent
                                             ▼
   ┌───────────────────────────────────────────────────────────────────────┐
   │              ② CLIENT (your app) — has client_id [+ client_secret]    │
   │                                                                       │
   │   PUBLIC     : SPA, mobile, CLI      → no secret → PKCE mandatory    │
   │   CONFIDENTIAL: server web app, backend service → secret + PKCE       │
   └───────────┬───────────────────────────────────────────┬────────────────┘
               │ ③ /authorize  (front channel)             │
               ▼                                           │
   ┌───────────────────────────────────────────────────────┴────────────────┐
   │              ③ AUTHORIZATION SERVER — the only party that knows         │
   │                 the USER and authenticates them                        │
   │                                                                       │
   │   /authorize · /token · /userinfo · /revoke · /introspect             │
   │   /jwks.json · /.well-known/openid-configuration                      │
   └───────────┬───────────────────────────────────────────┬────────────────┘
               │ ④ ACCESS TOKEN                            │ ⑤ ID TOKEN
               │   audience = the RESOURCE SERVER          │   audience = THE CLIENT
               │   carries scope/roles                    │   carries the USER's claims
               │   5–15 min · Bearer credential            │   5–60 min · JWT, always
               ▼                                           ▼
   ┌────────────────────────────────────────┐   ┌────────────────────────────┐
   │   ④ RESOURCE SERVER (your API)         │   │   only the CLIENT reads    │
   │   validates the token, never the user  │   │   the ID token             │
   │   BearerTokenAuthenticationFilter      │   │   NEVER send it to the API │
   │        → JwtDecoder (JWKS, cached)     │   └────────────────────────────┘
   │        → Jwt → authorities              │
   │        → SecurityContextHolder         │
   └────────────────────────────────────────┘
```

**Caption:** the access token is addressed to the API; the ID token is addressed to the client.
Sending the ID token to your own API is a confused-deputy bug — the API would trust a credential
that was minted for a *different* audience.

### 6.3 Which grant type do I use? (flowchart)

```mermaid
flowchart TD
    Start["Who needs a token?"] --> Q1{"Is there an end user<br/>in the loop?"}

    Q1 -- "NO — a service or a job" --> Q2{"Does the client have<br/>a trustworthy server?"}
    Q2 -- "YES (confidential)" --> CC["③ CLIENT CREDENTIALS<br/>grant_type=client_credentials<br/>no user · no refresh token"]
    Q2 -- "NO (public device)" --> DA["⑤ DEVICE AUTHORIZATION<br/>RFC 8628<br/>TV · CLI · IoT"]

    Q1 -- "YES — a human signs in" --> Q3{"Can the client keep a<br/>client secret?"}
    Q3 -- "YES — server-side web app" --> AC["① AUTHORIZATION CODE<br/>+ PKCE (recommended)<br/>secret AND PKCE — defence in depth"]
    Q3 -- "NO — SPA / mobile / desktop" --> PKCE["② AUTHORIZATION CODE + PKCE<br/>S256 challenge<br/>RFC 7636 · OAuth 2.1 default"]

    AC --> Refresh["⑥ REFRESH TOKEN<br/>RFC 6749 §6<br/>rotate + reuse detection"]
    PKCE --> Refresh
    CC --> X["❌ IMPLICIT — REMOVED IN OAUTH 2.1<br/>token in the URL fragment"]
```

**What to look at:** the decision is driven by **two** questions — is there a user, and can the
client keep a secret. Almost everyone reaches for "Authorization Code" and then has to work out
which *variant*; the point of the diagram is that **PKCE is on the modern branch of every
user-facing flow**, and that Client Credentials is the only correct answer when there is no user.

### 6.4 The grant types at a glance (ASCII table)

```text
   ┌───────────────┬──────────────┬────────────────────┬──────────────────────┬───────────┐
   │ GRANT         │ CLIENT TYPE  │ WHERE THE TOKEN    │ REFRESH TOKEN?       │ POSTURE   │
   │               │              │ ARRIVES            │                      │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ① Auth Code   │ Confidential │ BACK CHANNEL       │ YES                  │ ✅ OK     │
   │   (RFC 6749   │ (server web  │ POST /token        │                      │           │
   │    §4.1)      │  app)        │                    │                      │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ② Auth Code  │ Public OR    │ BACK CHANNEL       │ YES (rotate)        │ ✅ DEFAULT│
   │   + PKCE      │ Confidential │ POST /token        │                      │  (2.1)    │
   │   (RFC 7636)  │ (SPA/mobile) │ + code_verifier    │                      │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ③ Client      │ Confidential │ BACK CHANNEL ONLY  │ NO (request a new   │ ✅ OK     │
   │   Credentials │ (service)    │ POST /token        │ one when it expires)│           │
   │   (§4.4)      │              │                    │                      │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ④ Implicit    │ Public       │ ★ URL FRAGMENT ★   │ NO                   │ ❌ BANNED │
   │   (§4.2)      │              │ #access_token=…    │                      │  in 2.1   │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ⑤ Device Auth │ Public       │ BACK CHANNEL       │ YES (rotate)        │ ✅ OK     │
   │   (RFC 8628)  │ (TV/CLI/IoT) │ POST /token (poll) │                      │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ⑥ Refresh     │ Any          │ BACK CHANNEL       │ YES — issues a new   │ ✅ OK     │
   │   (§6)        │              │ POST /token        │ one (rotation)       │           │
   ├───────────────┼──────────────┼────────────────────┼──────────────────────┼───────────┤
   │ ⑦ Token       │ Confidential │ BACK CHANNEL       │ depends on the       │ ✅ OK     │
   │   Exchange    │ (service)    │ POST /token        │ requested grant      │           │
   │   (RFC 8693)  │              │                    │                      │           │
   └───────────────┴──────────────┴────────────────────┴──────────────────────┴───────────┘
```

**Caption:** the "where the token arrives" column is the security column. Every flow that
delivers the token on the **back channel** (over TLS, between two servers) is fine; the one
that delivers it in the **URL fragment** is the reason Implicit was removed.

---

## 7. Production-Level Use Cases & Real Industry Examples

### 7.1 E-commerce — "let a partner app read my orders"

**The situation.** A marketplace has 40,000 partner apps. A partner's app wants to show the
user their order history. The marketplace must not give partners the user's password, and the
user must be able to revoke one partner without affecting the others.

**The design.** The marketplace runs Keycloak as the authorization server; each partner app is
a **confidential client** with a pre-configured scope `orders:read`. The **marketplace's
`orders-api`** is the resource server and validates the access token locally against the JWKS.

**The numbers.** Access token TTL **300 s**; the orders API serves **1,800 req/s** at peak;
local validation costs **~90 µs** per request versus **~12 ms** for an introspection call —
which would have added **1,800 × 12 ms = 21.6 seconds** of network time per second of peak
traffic across the fleet. That single number is why local validation is the default.

### 7.2 Banking — service-to-service calls inside the perimeter

**The situation.** A payments platform has 60 microservices. The `ledger-service` calls the
`fraud-service` 900 times per second, and both are internal. There is **no user** in that call
— the user context was already resolved at the edge.

**The design.** Client Credentials. Each service is a confidential client with its own secret
(ideally rotated via a secrets manager). The `fraud-service` is the resource server; it
validates the token locally **and** validates the `aud` claim contains `fraud-service` — without
that check, a token minted for `ledger-service` would also be accepted.

**The numbers.** Token TTL **60 s** (short, because the client can re-mint cheaply); the client
caches the token for `expires_in - 60 s`; re-minting costs **~8 ms** against a **60,000 req/s**
peak — 480 s of AS time per second of traffic, which is why the cache matters and why the TTL
must not be so short that every service hammers the AS.

### 7.3 SaaS — third-party integrations with per-user consent

**The situation.** A SaaS product (project-management tool) lets customers connect their
Google Drive, Slack and GitHub accounts. 30,000 workspaces, average **4.3** connected accounts
per workspace.

**The design.** Authorization Code + PKCE, one `ClientRegistration` per provider, dynamic
consent. The SaaS stores **only** the refresh token, hashed, in its own database
(`connected_account` table) — and **never** stores the provider's access token, because it
never calls the provider's API on the user's behalf except to refresh.

**The numbers.** Refresh token TTL **90 days** (the SaaS re-authorises only on explicit user
action); the stored refresh token is encrypted at rest with a KMS-managed key; disconnecting an
integration deletes the row and calls the provider's `/revoke` endpoint. Per the pinned
canonical table, the refresh token is stored **hashed** server-side.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 Configure a generic OIDC login and observe the exact redirect

**The problem.** You want "Log in with Acme ID" in a Spring Boot app. The IdP is a generic
OIDC provider at `https://idp.acme.com`, the client id is `orders-web`, and you want the
scopes `openid profile email`.

**The configuration.**

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          acme:
            client-id: orders-web
            client-secret: ${ACME_CLIENT_SECRET}
            # The template is resolved by Spring: {baseUrl} and {registrationId}
            # are substituted at runtime.
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            scope:
              - openid
              - profile
              - email
            provider: acme
        provider:
          acme:
            # With issuer-uri, Spring performs OIDC DISCOVERY at startup and fills in
            # authorization-uri, token-uri, user-info-uri and jwk-set-uri from the
            # discovery document. You do NOT hard-code them.
            issuer-uri: https://idp.acme.com/realms/acme
            user-name-attribute: sub
```

**The expected output.** Starting the app with
`logging.level.org.springframework.security=DEBUG` and hitting the login link produces:

```text
DEBUG o.s.s.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter : Redirecting to authorization request
GET https://idp.acme.com/realms/acme/protocol/openid-connect/auth
    ?response_type=code
    &client_id=orders-web
    &scope=openid%20profile%20email
    &state=8f2b1c9e7d4a6053f8c2e1b0d9a7f4e3c
    &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Facme
    &code_challenge=9Y8Zp1nQ8Q8m2sKq3Y7bVvH4nLwT6xR9cQ1mZ5pE7sK0
    &code_challenge_method=S256
```

**Dry run — step by step.**

1. The browser requests `GET /oauth2/authorization/acme`.
2. `OAuth2AuthorizationRequestRedirectFilter` calls
   `DefaultOAuth2AuthorizationRequestResolver#resolve(request, registrationId)`.
3. The resolver reads the `ClientRegistration` bean with `registrationId = "acme"`.
4. Because the provider block declares `issuer-uri`, Boot's
   `ClientRegistrationRepository` performed discovery **at startup** and populated
   `providerDetails.configurationMetadata` with
   `authorization_endpoint`, `token_endpoint`, `userinfo_endpoint`, `jwks_uri` and
   `end_session_endpoint`.
5. The resolver builds an `OAuth2AuthorizationRequest` with
   `responseType=code`, `clientId=orders-web`, `redirectUri` resolved from the template,
   `scopes={openid, profile, email}` and a **random 32-hex-character `state`**.
6. Because `ClientSettings.requireProofKey` is `true` by default for public clients and Spring
   enables PKCE by default when no client secret is present, the resolver adds
   `code_challenge` and `code_challenge_method=S256`.
7. The resolver asks the `AuthorizationRequestRepository` to **save** the pending request, so
   the `state` can be compared on the callback.
8. The filter sends `302 Location: <the URL above>`.

**The takeaway.** With `issuer-uri` you configure **one** URL per provider, and the whole
endpoint set — including the JWKS URI — comes from the discovery document. The `state` is
generated and stored *before* the redirect; if you ever see `invalid_state` on the callback,
the storage is the thing to check (chapter 02).

### 8.2 A `ClientRegistration` for a self-hosted Keycloak realm

**The problem.** You run Keycloak 25.0 in-house, realm `acme`, client `orders-web`, and you
want the realm's roles to become Spring authorities.

**The configuration.**

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: orders-web
            client-secret: ${KEYCLOAK_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_basic
            scope:
              - openid
              - profile
              - email
              - roles
            provider: keycloak
        provider:
          keycloak:
            issuer-uri: http://localhost:8081/realms/acme
            user-name-attribute: preferred_username
```

**The Keycloak side** (realm `acme` → Clients → `orders-web`):

```text
Client type          : Confidential  (OpenID Connect enabled)
Valid redirect URI   : http://localhost:8080/login/oauth2/code/keycloak
Web origins          : http://localhost:8080
Standard flow        : enabled
Direct access grants : DISABLED   ← never enable this in production
Implicit flow        : DISABLED   ← removed in OAuth 2.1 anyway
Client scopes        : openid, profile, email, roles
```

**The expected output.** With DEBUG logging on `org.springframework.security.oauth2.client`:

```text
DEBUG o.s.s.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter : Redirecting to authorization request
GET http://localhost:8081/realms/acme/protocol/openid-connect/auth
    ?response_type=code
    &client_id=orders-web
    &scope=openid%20profile%20email%20roles
    &state=1d0f6c3a92b7481eac50d9b3f7c21e08
    &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fkeycloak
    &code_challenge=6Mkd1rY0j3nW2pQ8sZ5xK7bL9mC4vH6fN1tJ0aB2dE
    &code_challenge_method=S256
```

and after the callback:

```text
DEBUG o.s.s.oauth2.client.authentication.OAuth2LoginAuthenticationProvider : Retrieved the access token
DEBUG o.s.s.oauth2.client.authentication.OAuth2LoginAuthenticationProvider : Retrieved the id token
DEBUG o.s.s.oauth2.client.authentication.OAuth2LoginAuthenticationProvider : Retrieved the user info
DEBUG o.s.s.oauth2.client.authentication.OAuth2LoginAuthenticationProvider : Exchanged the authorization code
DEBUG o.s.security.oauth2.client.web.OAuth2LoginAuthenticationFilter : Authentication success
```

**Dry run — step by step.**

1. The `issuer-uri` is `http://localhost:8081/realms/acme`; Boot fetches
   `http://localhost:8081/realms/acme/.well-known/openid-configuration` **at startup**.
2. The discovery document contains
   `"authorization_endpoint": ".../protocol/openid-connect/auth"`,
   `"token_endpoint": ".../protocol/openid-connect/token"`,
   `"userinfo_endpoint": ".../protocol/openid-connect/userinfo"` and
   `"jwks_uri": ".../protocol/openid-connect/certs"`.
3. Those four values fill the `ClientRegistration` — so the YAML above contains **no endpoint
   URLs at all**.
4. `scope: [roles]` is a **Keycloak client scope**: it puts the realm roles into the access
   token under `realm_access.roles` and into the ID token under `realm_access.roles`.
5. `user-name-attribute: preferred_username` makes `Authentication#getName()` return
   `alice` rather than the opaque `sub`.
6. The flow then runs exactly as in §5.1.

**The takeaway.** The YAML is provider-agnostic — Google, Okta and Keycloak differ only in
the `provider` block and in the scopes. The **redirect URI must match the Keycloak client
configuration exactly**, including the `http` scheme and the port, or the AS will refuse with
`invalid_redirect_uri`.

### 8.3 A client-credentials token fetch — the exact request and response

**The problem.** A `reporting-worker` service needs a token to call `orders-api`.

**The request.**

```bash
curl -s -X POST \
  "https://auth.acme.com/realms/acme/protocol/openid-connect/token" \
  -H "Authorization: Basic $(printf 'reporting-worker:s3cr3t-value' | base64 -w0)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "scope=orders:read"
```

**The expected response.**

```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJrZXktMjAyNi0wMSJ9.eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20vcmVhbG1zL2FjY2UiLCJzdWIiOiJyZXBvcnRpbmctd29ya2VyIiwiYXVkIjoiW3JlcG9ydGluZy1hcGkiXSwic2NvcGUiOiJvcmRlcnM6cmVhZCIsImV4cCI6MTc0NTAwMDAwMH0.c2lnbmF0dXJlLXZhbHVl",
  "expires_in": 300,
  "token_type": "Bearer",
  "not-before-policy": 0,
  "scope": "orders:read"
}
```

**Dry run — step by step.**

1. `printf 'reporting-worker:s3cr3t-value' | base64 -w0` produces
   `cmVwb3J0aW5nLXdvcmtlcjpzM2NyM3QtdmFsdWU=`. The AS decodes it into
   `client_id = "reporting-worker"` and `client_secret = "s3cr3t-value"`.
2. The `grant_type=client_credentials` selects the client-credentials grant — **there is no
   `redirect_uri`, no `code` and no user involved**.
3. The AS checks that `orders:read` is in the client's **assigned scope** (in Keycloak, the
   *optional client scopes* + *default client scopes* of the `reporting-worker` client). If it
   is not, the AS returns `400 invalid_scope`.
4. The AS authenticates the client. A wrong secret returns `401 invalid_client`; a wrong
   *format* (secret in the body instead of the Basic header, when the client is configured for
   `client_secret_basic`) also returns `401 invalid_client`.
5. The AS mints an access token whose `sub` is `"reporting-worker"` — **the application, not a
   user** — whose `aud` is `["reporting-api"]` and whose `scope` is `"orders:read"`.
6. `expires_in: 300` tells the client when to re-mint. The client caches the token for
   `300 - 60 = 240 s`.

> 🔍 **Read the token.** Decoding the payload gives
> `{"iss":"https://auth.acme.com/realms/acme","sub":"reporting-worker","aud":["reporting-api"],"scope":"orders:read","exp":1745000000}`.
> Note `"sub":"reporting-worker"` — **there is no user in this flow.** That single fact
> distinguishes Client Credentials from every other grant.

**The takeaway.** Client Credentials is a **two-legged, no-user, no-redirect** flow: one POST
in, one token out. The `sub` being the application is the proof that you used the right grant.

---

## 9. Code from Scratch — Production-Grade

### 9.1 The client side — a Spring Boot app that logs users in

```java
package com.example.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A Spring Boot application acting as an OAuth2 CLIENT.
 *
 * It redirects the user to an external authorization server and comes back with a token.
 * The starter that makes this work is spring-boot-starter-oauth2-client.
 */
@Configuration
public class OAuth2ClientConfig {

    @Bean
    SecurityFilterChain clientChain(HttpSecurity http,
                                     ClientRegistrationRepository registrations) throws Exception {

        http
            // WHY oauth2Login: it registers OAuth2AuthorizationRequestRedirectFilter
            // (/oauth2/authorization/{registrationId}), OAuth2AuthorizationCodeGrantFilter
            // (/login/oauth2/code/{registrationId}) and OAuth2LoginAuthenticationFilter,
            // in the correct positions in the chain.
            .oauth2Login(oauth2 -> oauth2
                    // WHY the default success handler: it honours the originally
                    // requested URL (SavedRequestAwareAuthenticationSuccessHandler),
                    // so "GET /api/orders?x=1" returns the user to where they were.
                    .defaultSuccessUrl("/orders", true))
            // WHY permitAll on the callback: the browser arrives there WITHOUT a session
            // and WITHOUT a CSRF token — requiring one here is the classic
            // "403 on the OAuth callback" bug.
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/error", "/public/**").permitAll()
                    .requestMatchers("/oauth2/**", "/login/**").permitAll()
                    .anyRequest().authenticated());

        return http.build();
    }
}
```

### 9.2 The client-credentials token provider — with the correct cache skew

```java
package com.example.reporting.client;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

/**
 * Obtains and caches client-credentials access tokens for a downstream API.
 *
 * WHY a cache at all: the token endpoint is a network hop (5-40 ms). Without a cache, a
 * service making 900 calls/second would make 900 token requests/second.
 *
 * WHY the 60-second skew: a token that expires exactly when it is handed to the caller
 * fails intermittently under load. Caching for (expires_in - 60) removes the race.
 */
@Component
public class ClientCredentialsTokenProvider {

    private final RestClient restClient;
    private final String tokenUri;
    private final String clientId;
    private final String clientSecret;
    private final String scope;

    // A tiny in-process cache. In a multi-instance deployment each instance keeps its own
    // token — which is fine and is the normal design; it is not a correctness problem.
    private final Map<String, CachedToken> cache = new ConcurrentHashMap<>();

    private record CachedToken(String value, Instant expiresAt) { }

    public ClientCredentialsTokenProvider(
            RestClient.Builder builder,
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
        // WHY 30 seconds of margin: cover both clock skew and the in-flight request.
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) {
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

        // WHY Basic auth in the header: client_secret_basic is the default method for a
        // confidential client in Spring Security. Putting the secret in the body would be
        // client_secret_post, and a server configured for Basic rejects it with
        // 401 invalid_client.
        TokenResponse response = restClient.post()
                .uri(tokenUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .header("Authorization", "Basic " + java.util.Base64.getEncoder()
                        .encodeToString((clientId + ":" + clientSecret).getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .body(form)
                .retrieve()
                .body(TokenResponse.class);

        if (response == null || response.accessToken() == null) {
            throw new IllegalStateException("Token endpoint returned no access_token");
        }
        Instant expiry = Instant.now().plusSeconds(response.expiresIn() - 60);
        return new CachedToken(response.accessToken(), expiry);
    }

    private record TokenResponse(
            @com.fasterxml.jackson.annotation.JsonProperty("access_token") String accessToken,
            @com.fasterxml.jackson.annotation.JsonProperty("expires_in") long expiresIn,
            @com.fasterxml.jackson.annotation.JsonProperty("token_type") String tokenType,
            String scope) { }
}
```

### 9.3 The resource-server side — with the audience validator that everyone forgets

```java
package com.example.orders.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
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
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

/**
 * A Spring Boot application acting as an OAuth2 RESOURCE SERVER.
 * The starter is spring-boot-starter-oauth2-resource-server.
 */
@Configuration
public class ResourceServerConfig {

    /** The audience THIS API answers to. Tokens minted for other services must not work here. */
    private static final String THIS_SERVICE = "orders-api";

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter converter)
            throws Exception {

        http
                // WHY stateless: no HttpSession — a JWT access token needs no server state.
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // WHY csrf disabled here: no cookie carries the credential. If a refresh
                // token ever lives in a cookie, re-enable CSRF for that endpoint only.
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                        .anyRequest().authenticated())
                // WHY oauth2ResourceServer: registers BearerTokenAuthenticationFilter and
                // the BearerTokenAuthenticationEntryPoint (401) in the right positions.
                .oauth2ResourceServer(oauth2 -> oauth2
                        .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // WHY pin the algorithm: never let the token's own header pick the
                // verification algorithm.
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();

        // WHY: createDefaultWithIssuer validates iss + exp/nbf ONLY — NOT aud.
        OAuth2TokenValidator<Jwt> issuerAndTimestamps =
                JwtValidators.createDefaultWithIssuer(issuer);

        // WHY the audience validator: without it, a token minted for "billing-api"
        // authenticates against "orders-api" — the confused-deputy problem.
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(THIS_SERVICE));

        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(issuerAndTimestamps, audience));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        // WHY an explicit prefix: the default is SCOPE_, and .hasRole("ADMIN") expects ROLE_.
        authorities.setAuthorityPrefix("ROLE_");
        // WHY an explicit claim name: the default is "scope" or "scp" — never rely on it.
        authorities.setAuthoritiesClaimName("scope");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
```

---

## 10. Common Mistakes & Gotchas

| ❌ Wrong | ✅ Right | Why it breaks |
|---|---|---|
| "OAuth authenticates the user" | "OAuth **delegates authorisation**; OIDC authenticates" | The access token says *what* the client may do, never *who* the user is. Only the **ID token** (or UserInfo) carries the identity. |
| Using the **ID token** as the API credential | Send the **access token** to your API; keep the ID token in the client | The ID token's `aud` is **your client**, not your API. Sending it to the API is the confused-deputy attack, and an API that accepts it will trust a credential minted for someone else. |
| Using the **access token** to identify the user in your app | Read the **ID token** or call **UserInfo** | The access token is for the API; its claims are about the *grant*, not (reliably) about the user. Some ASs put `sub` in it, but the client must not depend on that. |
| `scope` == roles | Map scopes → authorities **explicitly** | A scope is a protocol artefact (`orders:read`); a role is a domain concept (`ORDER_ADMIN`). A blanket mapping either loses fidelity or grants too much. |
| Picking the **Implicit** grant for an SPA because "it has no backend" | Authorization Code + **PKCE** | Implicit put the token in the URL fragment (history, `Referer`, page JS). It is **removed in OAuth 2.1**. |
| One `spring-boot-starter-oauth2-client` for everything | `oauth2-client` **or** `oauth2-resource-server`, not both in one service | A service that is both client and resource server has two trust roles in one process, two token types in one context, and a much harder security story. |
| Configuring `issuer-uri` **and** `jwk-set-uri` and expecting them to disagree silently | They must agree — `issuer-uri` drives discovery, `jwk-set-uri` drives key fetching | If the JWKS URI points at a different issuer than `iss` allows, tokens will be rejected with a confusing `iss` error. Chapter 03 §8 covers this. |
| Storing the provider's access token for a social login | Store **only the refresh token**, if you need one at all | An unused access token is pure liability: it is a live credential in your database that you cannot revoke without going to the provider. |
| Redirect URI registered as `http://localhost:8080/**` | Register **exact** URIs | Wildcard/prefix matching is how code-theft and open-redirect bugs happen; OAuth 2.1 requires exact matching. |
| `redirect-uri` omitted from the token request | Always send the **same** `redirect_uri` in the token request | RFC 6749 §4.1.3 — the AS compares it with the one bound to the code. A mismatch is `invalid_grant`. |
| Treating `client_secret` as a required defence for a SPA | SPA is a **public client** — it has no secret and must use PKCE | Trying to embed a secret in a SPA ships it to every browser. A "secret" in a SPA is not a secret. |
| Using `CommonOAuth2Provider.GITHUB` and expecting `email` in the principal | GitHub's `/user` may not return the email — call `/user/emails` | The classic GitHub gotcha; chapter 04 covers it in full. |
| Believing `JwtValidators.createDefaultWithIssuer` validates `aud` | Add an **audience validator** explicitly | It validates `iss` + timestamps only. Chapter 03 §8 shows the exact fix. |
| Assuming `spring.security.oauth2.resourceserver.jwt.issuer-uri` works offline | Use `jwk-set-uri` when the AS may be down at boot | `issuer-uri` triggers **discovery at startup** — the app will not start if the AS is unreachable. |

---

## 11. Performance Considerations

**The cost of each decision, in numbers.**

| Decision | Cost | When to optimise |
|---|---|---|
| **Local JWT validation** | ~50–200 µs per request (RSA-2048 verify ~80 µs, ECDSA P-256 ~40 µs, HMAC ~5 µs) | Always — this is the default and it is fast |
| **Introspection** | +5–40 ms per request (network RTT + AS processing) | Only when instant revocation is worth the latency |
| **Discovery at startup** | One HTTP call, ~50–200 ms, **once per JVM start** | Acceptable; but see the failure mode in chapter 03 |
| **JWKS refresh** | ~1–5 KB per refresh; the library caches and refreshes on an unknown `kid` | Align the cache TTL with the rotation runbook |
| **Client-credentials token caching** | Saves one token request per TTL | Always cache; use `expires_in - 60 s` |
| **HS256 vs RS256** | HS256 ~5 µs, RS256 ~80 µs, ES256 ~40 µs | 15 µs × 1,800 rps = 27 ms/s of CPU — irrelevant. Choose on **key distribution**, not speed |
| **Large claim sets** | Every request parses the JSON payload; a 3 KB token adds ~20–40 µs of parse time and risks the 4 KB proxy header limit | Keep the claim set small; put role IDs in the token, resolve permissions at the edge |

**Throughput reality check.** A typical orders API at **1,800 req/s** with local validation:

```text
   1,800 req/s × 90 µs = 0.162 s of CPU per second of wall clock
   → roughly 16% of one core, on a 4-core pod.

   The SAME API using introspection:
   1,800 req/s × 12 ms = 21.6 s of waiting per second of wall clock
   → ~22 cores of blocking work, or a connection pool of hundreds,
     and the AS becomes a hard dependency of the API.
```

That is the entire performance argument for local validation, in one line of arithmetic.

**What to measure.**

| Metric | Why |
|---|---|
| `oauth2.token.issued.count` (by `grant_type`, by `client_id`) | Detect a client that is re-minting instead of caching |
| `oauth2.token.introspect.duration` (p50/p95/p99) | The introspection tax, if you use it |
| `jwt.validation.duration` (p50/p99) | Detects a regression to a slow algorithm or a huge claim set |
| `jwt.failures.by_reason` (signature / claims / unknown-`kid` / audience) | Separates an attack from a misconfiguration |
| `security.oauth2.authorizations` | The 401 rate — the single most important auth metric |
| `jwks.cache.hit_ratio` | A fall means unknown `kid`s — possibly a rotation, possibly an attack |
| `token.size.p99` | A rising tail means the claim set is growing |

**Tuning knobs.** `issuer-uri` (the discovery entry point) · `jwk-set-uri` (skips discovery
and is the **availability** escape hatch) · `spring.security.oauth2.client.provider.<name>.issuer-uri`
(discovery for the client too) · `ClientSettings.requireProofKey` (keep it `true`). The Nimbus
`RemoteJWKSet` refreshes on an **unknown `kid`**, so align the rotation runbook with that
rather than trying to tune the cache precisely.

---

## 12. Security Considerations

### 12.1 The threat model

| Threat | Attack path | The Spring-specific defence |
|---|---|---|
| **Code interception** | A malicious app registers the same custom URL scheme and steals the `code` from the redirect | **PKCE** — the `code_verifier` never leaves the legitimate client |
| **CSRF on the callback** | An attacker crafts an authorize URL with *their* `state` and tricks the victim into completing it, so the victim's session is bound to the attacker's account | **Validate `state`** against the stored value in the `AuthorizationRequestRepository` |
| **ID token replay** | A valid ID token is captured and replayed into a different session | **`nonce`** — the ID token must echo the nonce from the authorization request |
| **Open redirect / code theft** | A lax `redirect_uri` match sends the code to the attacker | **Exact `redirect_uri` matching** (OAuth 2.1) and a per-client allowlist |
| **Token leakage via logs** | The `code` or the token is written to the access log or the `Referer` header | Redact query strings on `/login/oauth2/code/**`; `Referrer-Policy: no-referrer` |
| **Scope escalation** | A client asks for more scopes than the user consented to | The **AS** enforces the requested scopes against the grant; the RS enforces the token's scopes |
| **Confused deputy** | An ID token minted for the client is replayed at the API | Validate **`aud`** on every token at the API; never accept the ID token as an API credential |
| **Cross-service token reuse** | A token minted for `service-a` is accepted by `service-b` | The **`aud` validator** — the single most-missed line in Spring Security |
| **Client impersonation** | An attacker registers a client with the same `client_id` | The AS owns the registration; the `client_secret` proves the confidential client |
| **Unverified email linking** | An attacker creates a provider account with the victim's email and links it to the victim's local account | Only auto-link on a **verified** email; chapter 04 |

### 12.2 The pinned token-lifetime table (canonical — from `STANDARD.md` §4.4)

| Token | TTL | Storage | Sent as |
|---|---|---|---|
| Access token | **5–15 min** | Server: none (stateless) | `Authorization: Bearer <jwt>` |
| Refresh token | **7–30 days** | Server: **hashed** in DB/Redis | `HttpOnly; Secure; SameSite=Strict` cookie |
| ID token (OIDC) | 5–60 min | none | never sent to the browser JS |

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

> 📌 **How OAuth maps onto those codes.** A missing token, an expired token, a bad signature,
> an unknown `kid`, an `aud` mismatch and a failed PKCE check are **all 401** — the request
> was not authenticated. A **valid** token whose `scope` does not include what the endpoint
> requires is **403**, and per RFC 6750 §3.1 the response should carry
> `WWW-Authenticate: Bearer error="insufficient_scope"`. A malformed token request
> (missing `grant_type`, an unknown `grant_type`) is **400**; a client that fails to
> authenticate is **401**; a valid client requesting a scope it does not hold is
> **400 `invalid_scope`** per RFC 6749 §5.2.

### 12.4 The rules that are not negotiable

1. **Never read a token from a query string.** `?access_token=…` lands in the access log, the
   browser history and the `Referer` header. Use the `Authorization` header.
2. **Never put a client secret in a SPA or a mobile app.** It ships to every device.
3. **Always use PKCE**, even for confidential clients. It costs one hash and one extra
   parameter and it defends against a compromised intermediary.
4. **Always validate `aud`.** Without it, your API is a confused deputy.
5. **Always validate `state`** and (for OIDC) **`nonce`**.
6. **Register exact redirect URIs.** No wildcards.
7. **Never log the token.** Redact `Authorization` at the proxy and query strings on the
   callback endpoint.
8. **Keep the scopes minimal.** Ask for `email` only if you use the email.
9. **Use TLS everywhere**, including the back channel.
10. **Treat the client as untrusted.** The client can request any scope and can tamper with
    anything it sends you; only the AS and the RS enforce policy.

---

## 13. Best Practices

The rules a reviewer on your team would actually enforce in a pull request.

**Protocol**

1. Use **Authorization Code + PKCE (S256)** for every user-facing flow, confidential clients
   included. Defence in depth costs nothing.
2. Use **Client Credentials** for every machine-to-machine call. Never mint a "user token" for
   a service.
3. **Never** use the Implicit grant. If you find it in a config file, reject the PR.
4. Require **exact `redirect_uri`** matching, and register one URI per environment.
5. Always send the **`redirect_uri` in the token request**, identical to the one in the
   authorization request.
6. Request the **minimum scope set**. Split broad scopes into narrow ones so you can
   downgrade a grant without a re-consent.

**Spring**

7. Use the **`issuer-uri`** property rather than hand-written endpoint URLs — one URL per
   provider, and the JWKS URI comes from discovery.
8. Configure **one starter per service**: client **or** resource server, never both.
9. Always define an **audience validator** in a resource server.
10. Use a **custom `JwtGrantedAuthoritiesConverter`** (or `GrantedAuthoritiesMapper`) so the
    scope→authority mapping is explicit and reviewed.
11. Keep the `AuthorizationRequestRepository` **per-request-session** by default; use the
    cookie variant for a stateless SPA with CSRF protection.
12. Store authorized clients in a **database-backed `OAuth2AuthorizedClientService`** in a
    clustered deployment — the in-memory one loses every grant on restart.

**Operations**

13. **Redact tokens and authorization codes** in every log pipeline, at the proxy.
14. **Rotate client secrets** on a schedule (90 days) and on every offboarding; support **two
    active secrets** so the rotation needs no downtime.
15. **Monitor the 401 rate** and the failure reasons separately — signature vs claims vs
    unknown-`kid`.
16. Keep an **emergency revoke** path: a documented way to disable a client in the AS and to
    kill its tokens.

**Data**

17. **Never store the provider's access token** for a social login unless you actively call
    the provider's API with it.
18. Store **refresh tokens hashed** (see §12.2) and **encrypted at rest** where the
    protection scheme permits.
19. **Never trust an unverified email** for account linking.
20. Log the **provider/issuer** on every authentication event — you cannot audit an identity
    system that does not record where the identity came from.

---

## 14. Hands-On Exercises & Code Challenges

### 14.1 Hands-on exercises

**Exercise 1 — Build and observe a client registration.**

1. Create a Spring Boot 3.3 app with `spring-boot-starter-oauth2-client`.
2. Add a `keycloak` registration pointing at `http://localhost:8081/realms/acme` via
   `issuer-uri` (no hard-coded endpoint URLs).
3. Start Keycloak (`docker run -p 8081:8080 -e KEYCLOAK_ADMIN=admin … quay.io/keycloak/keycloak:25.0 start-dev`)
   and create the realm, the client and a user with the `orders:read` scope.
4. Set `logging.level.org.springframework.security.oauth2=DEBUG` and hit
   `/oauth2/authorization/keycloak`.
5. **Acceptance criteria:** the log shows a `302` to
   `…/protocol/openid-connect/auth` carrying `response_type=code`, `client_id`,
   `redirect_uri`, `state`, `code_challenge` and `code_challenge_method=S256`; **no endpoint
   URL appears in your `application.yml`**.

**Exercise 2 — Implement a client-credentials token provider with a cache.**

1. Create a `ClientCredentialsTokenProvider` that POSTs to the token endpoint with
   `client_secret_basic`.
2. Cache the token for `expires_in - 60` seconds in a `ConcurrentHashMap`.
3. Write a test that calls `getAccessToken()` 100 times and asserts that **exactly one**
   HTTP call was made.
4. **Acceptance criteria:** the test fails with 2+ HTTP calls before the cache is added and
   passes with 1 after; the cached token is never returned with less than 30 s of life left.

**Exercise 3 — Prove the audience validator matters.**

1. Configure a resource server with `issuer-uri` and **no** audience validator.
2. Mint a token whose `aud` is `["another-api"]` and call your API with it → observe **200**.
3. Add the `JwtClaimValidator<List<String>>` audience validator and repeat → observe **401**.
4. **Acceptance criteria:** the test asserts the 200 before and the 401 after, and the 401
   response body contains `invalid_token`.

### 14.2 Code challenges

**Challenge 1 — A scope→authority translator.**

> Implement a `GrantedAuthoritiesMapper` (or a `JwtGrantedAuthoritiesConverter`) that maps
> provider scopes to domain authorities.

**Acceptance criteria:**
- [ ] `orders:read` → `ORDER_READER`; `orders:write` → `ORDER_WRITER`;
      `profile:read` → `PROFILE_SELF`; anything unknown → **no authority** (not a default one).
- [ ] An unknown scope never produces a wildcard authority.
- [ ] A unit test asserts that a token with `scope=orders:read admin:all` yields **exactly**
      one authority, `ORDER_READER`.
- [ ] The mapping table is a `Map` constant, not a chain of `if` statements.

**Stretch goal:** support a provider-specific prefix (`kc:` → Keycloak roles) via a
`Converter<String, String>` injected into the mapper.

**Challenge 2 — A provider-agnostic `OAuth2UserService` that fetches the email.**

> Implement an `OAuth2UserService` that, for providers where the user info endpoint does not
> return a verified email, calls an extra endpoint and produces an `OAuth2User` with a verified
> email attribute.

**Acceptance criteria:**
- [ ] The extra HTTP call happens **only** when the primary user info has no `email`.
- [ ] The email is taken from the **primary + verified** entry, never from the first entry.
- [ ] A `MockRestServiceServer` test proves that no extra call is made when the email is
      already present.
- [ ] A test proves that an account whose only email is unverified produces **no** email
      attribute (and therefore cannot be auto-linked).

**Stretch goal:** make the extra call configurable per provider via
`providerConfigurationMetadata`, so GitHub uses `/user/emails` and Google uses nothing.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q:** What is OAuth 2.0, in one sentence?
**A:** OAuth 2.0 is a **delegation framework** that lets a third-party application access a
resource on a user's behalf without the user giving that application their password. The
authorization server authenticates the user and issues a scoped token to the client, which the
client presents to the resource server.

**Q:** Name the four roles in OAuth.
**A:** The **resource owner** (the user), the **client** (the app requesting access), the
**authorization server** (issues tokens and holds the user identity) and the **resource
server** (the API holding the protected data). Conceptually they are four distinct roles,
though in practice the authorization server and the resource server are often the same
product — Keycloak hosts both.

**Q:** What is the difference between an access token and a refresh token?
**A:** The **access token** is the short-lived credential the client sends to the API on every
call — typically 5–15 minutes. The **refresh token** is a longer-lived credential (7–30 days)
that the client exchanges at the token endpoint for a new access token, so the user is not
re-prompted. The refresh token is stored server-side, hashed, and is never sent to the API.

**Q:** What is the difference between OAuth and OpenID Connect?
**A:** OAuth 2.0 is about **authorisation** — what a client may do. OpenID Connect is an
**identity layer on top of OAuth**: it adds the `openid` scope, the **ID token** (a JWT
asserting who the user is), the **UserInfo endpoint**, the **discovery document** and
**RP-initiated logout**. Without the `openid` scope it is just plain OAuth 2.0 and no identity
is returned.

### 🟡 Intermediate

**Q:** What is PKCE and why is it mandatory now?
**A:** PKCE (RFC 7636) is Proof Key for Code Exchange. The client generates a random
`code_verifier`, sends `BASE64URL(SHA256(code_verifier))` as the `code_challenge` on the
authorization request, and sends the raw `code_verifier` on the token request; the
authorization server recomputes the hash and compares. It is mandatory in OAuth 2.1 because it
closes the **authorization code interception attack** — an attacker who steals the code cannot
redeem it without the verifier, which never left the legitimate client.

**Q:** What is the difference between a public client and a confidential client?
**A:** A **confidential client** can keep a secret (a server-side web app, a backend service);
a **public client** cannot (a SPA, a mobile app, a desktop app, a CLI) because the code
ships to the user. Public clients therefore rely on PKCE instead of a secret. A confidential
client should use PKCE *as well* — the secret authenticates the client, PKCE binds the
authorization code to that particular request.

**Q:** Explain the `state` parameter.
**A:** `state` is an opaque value the client generates, stores in the session (or a cookie)
before redirecting to the authorization server, and includes in the authorization request. When
the callback comes back, the client compares the returned `state` with the stored one; a
mismatch means the response does not belong to this session — the classic **CSRF / login-CSRF**
attack. It is also the place to carry the user's original destination.

**Q:** When would you use the Client Credentials grant?
**A:** When there is **no end user** in the interaction — a backend service calling another
service, a worker consuming an internal API, a CI pipeline deploying to your platform. The
access token represents the **application**, the scope is the application's own permission set,
and there is no refresh token because there is no user session to preserve — the client simply
requests a new access token when the old one expires.

### 🔴 Advanced / Senior

**Q:** Should a resource server validate tokens locally or call the authorization server?
**A:** **Locally, by default** — verify the JWT signature against a cached JWKS and run the
`iss`/`aud`/`exp` validators. It costs ~50–200 µs versus 5–40 ms for an introspection call,
and it keeps the API serving when the authorization server is down. Introspection
(RFC 7662) is the right trade when you need **immediate revocation** or the token is
**opaque**, accepting that the authorization server's availability becomes part of your API's
availability and that it must handle every request.

**Q:** How do you make a Spring Security resource server validate the audience?
**A:** `JwtValidators.createDefaultWithIssuer(issuer)` validates the **issuer and the
timestamps only** — it does *not* check `aud`. You add an audience validator yourself and
compose it with `DelegatingOAuth2TokenValidator`:

```java
OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD, aud -> aud != null && aud.contains("orders-api"));
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audience));
```

Without it, a token minted for `billing-api` authenticates against `orders-api` — the
confused-deputy problem.

**Q:** What is the difference between a `ClientRegistration` and an `OAuth2AuthorizedClient`?
**A:** `ClientRegistration` is the **static registration** of a client with a provider — the
client id, secret, redirect URI, scopes and the discovered endpoint URLs. `OAuth2AuthorizedClient`
is the **runtime grant**: a registered client plus the tokens it currently holds for a
particular principal. Registrations live in a `ClientRegistrationRepository`; authorized
clients live in an `OAuth2AuthorizedClientRepository` and change every time the token is
refreshed.

**Q:** How does a server-side Spring app call a downstream API on behalf of the logged-in user?
**A:** With `OAuth2AuthorizedClientManager`. It loads the stored authorized client for the
current principal, and if the access token has expired it runs the configured
`OAuth2AuthorizedClientProvider` (refresh or re-exchange) and saves the result back to the
`OAuth2AuthorizedClientRepository`. You then inject the `OAuth2AuthorizedClient` as a
controller method argument, or use a `RestClient` interceptor that sets the bearer header —
the key point is that the **user's** token is forwarded, so the downstream API sees the right
audience and the right scopes.

### ⚫ Expert / Principal

**Q:** OAuth 2.1 changed several things. Which ones matter most and why?
**A:** Four. **PKCE becomes mandatory** for the authorization code grant — because the code
interception attack is the main way a public client gets compromised. **The implicit grant is
removed** — it returned the access token in the URL fragment, which leaks through browser
history, the `Referer` header and page JavaScript. **The resource-owner-password grant is
removed** — it normalised handing passwords to third parties, the exact behaviour OAuth exists
to prevent. And **exact `redirect_uri` matching** is required — prefix and wildcard matching
have produced real code-theft and open-redirect bugs. Underneath, the direction of travel is
that **OAuth 2.0 optimised for easy integration and OAuth 2.1 optimises for the client being
untrusted**.

**Q:** A bearer token can be replayed by anyone who copies it. What mitigations exist?
**A:** Three families. **Sender-constrained tokens**: mTLS-bound access tokens (RFC 8705) bind
the token to the client's TLS certificate via a `cnf.x5t#S256` claim; DPoP (RFC 9449) binds it
to a public key the client holds and requires a signed proof per request. **Rotation with reuse
detection**: a refresh token is single-use, and presenting a consumed one revokes the whole
family — which is what OAuth 2.1 requires for public clients. And **short lifetimes**, which
bound the window rather than preventing the replay. The general principle: possession is
proof, so anything that binds the credential to a *key* rather than a *string* removes the
replay.

**Q:** How would you migrate an estate that still uses the Implicit grant?
**A:** In order: (1) inventory the clients — in a Spring estate that is the
`registration.authorizationGrantType` and the `scope` in the YAML, plus the AS's grant
configuration. (2) Enable the authorization code grant **alongside** implicit on the AS, so
nothing breaks. (3) Migrate each client: public clients switch to code + PKCE; confidential
clients get the code exchange added. (4) For each public client, ensure it generates a
`code_verifier` of 43–128 characters from a CSPRNG and sends `code_challenge_method=S256`.
(5) Watch the AS's per-grant issuance metrics and the client's 401 rate. (6) **Disable
implicit** — and note that turning it off produces an immediate spike of `unsupported_grant_type`
errors from any client you missed, which is the metric to watch on the day.

**Q:** Your API is used by 200 partner applications. Explain the trade-off you made.
**A:** I would run **one authorization server** and have every partner register as a client.
The critical decisions are: (1) **per-partner scopes**, so revoking one partner's access is a
config change, not a token change; (2) **local JWT validation** in the API so the AS is not on
the hot path and its outage does not take the API down; (3) a **per-partner `aud`** so a token
minted for partner A cannot be replayed against partner B; (4) **rate limits per partner** at
the gateway; and (5) a **short access-token TTL** so a leaked partner token has a small blast
radius, accepting that the AS must handle 200 × (token rate) refreshes. If a partner requires
instant revocation, I would give *that partner* an introspection-based validation path — a
per-partner decision, not a fleet-wide one.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "The partner says the redirect URI is being rejected"

**The situation.** A partner integrates against your authorization server. They report that
`/authorize` returns `400 invalid_request` with the message *"The redirect_uri does not match
any of the registered redirect URIs"*. Their config looks correct.

**Your first move.**

```bash
# 1. Read the actual redirect_uri the client sent — from the AS access log, not from the client.
grep -o 'redirect_uri=[^&]*' /var/log/keycloak/keycloak.log | sort | uniq -c | sort -rn | head

# 2. Compare it, byte for byte, with what is registered.
#    Keycloak admin console → realm acme → Clients → partner-web → Valid redirect URIs
```

**Expected signal.** The client is sending
`https://app.partner.com/oauth2/callback` while the registered URI is
`https://app.partner.com/oauth2/callback/` — **a trailing slash**. Or the client is sending
`http://` and the registration is `https://`, or the **port** differs (`8080` vs no port),
or the client sends the URI with a trailing `?` or a fragment.

**The trap.** "Just enable wildcard matching." That turns a configuration error into a
**code-theft** vulnerability: with a lax match, an attacker can register
`https://app.partner.com.attacker.example/oauth2/callback` and receive the authorization
code. OAuth 2.1 removed lax matching for exactly this reason.

**The resolution.** Make the match exact on both sides: the client sends precisely the
registered string, including the scheme, the port and the path. If the partner needs multiple
environments, register each URI explicitly — Keycloak supports several exact URIs per client.

**The lesson.** The `redirect_uri` is a **byte-for-byte comparison**, and the most common
cause of a mismatch is not a wrong path but a **subtle character difference** (trailing slash,
`http` vs `https`, an explicit `:443`, a URL-encoded value). Compare the strings, do not
eyeball them.

---

### Scenario 2 — "Our API suddenly returns 401 for every request, and the IdP team says nothing changed"

**The situation.** Monday 09:14. Every request to the orders API returns 401 with
`WWW-Authenticate: Bearer error="invalid_token"`. The IdP team reports no changes and no
incidents. Tokens issued 20 minutes ago work; tokens issued 5 minutes ago do not.

**Your first move.**

```bash
# 1. Get one failing token and one working token, and compare their headers.
curl -s -H "Authorization: Bearer $BAD"  https://api.acme.com/api/orders -i | head -20
curl -s -H "Authorization: Bearer $GOOD" https://api.acme.com/api/orders -i | head -20

# 2. Compare the 'kid' in the two tokens.
echo "$BAD"  | cut -d. -f1 | base64 -d 2>/dev/null; echo
echo "$GOOD" | cut -d. -f1 | base64 -d 2>/dev/null; echo

# 3. What keys does the JWKS currently publish?
curl -s https://auth.acme.com/realms/acme/protocol/openid-connect/certs \
  | jq '.keys[] | {kid, alg, use}'
```

**Expected signal.** The failing token's header has `"kid":"key-2026-04"`, which is **not in
the JWKS**. The JWKS contains only `key-2026-01` and `key-2026-02`. The IdP rotated its
signing key at 09:00 and **removed the old key immediately** instead of keeping it live for the
overlap window.

**The trap.** "The tokens are expired." They are not — the good token is 2 minutes old and
works. The signal is a **key-id mismatch**, and the failure is per-token, not per-request.

**The resolution.** (1) Restore the old key to the JWKS **immediately** — this is a
configuration push, not a redeploy. (2) Then fix the process: publish the new key **first**,
sign with it, wait **at least the maximum token TTL plus clock skew** (with a 5-minute access
token, that is ~6 minutes; with a 15-minute token, ~16), *then* remove the old key. (3) Add an
alert on `jwt.failures.by_reason{reason="unknown_kid"}` — it would have fired at 09:00.

**The lesson.** Key rotation is a **three-phase operation** (publish → sign → remove), and the
removal phase is scheduled, not immediate. The "removal" is not really a removal — it is the
moment you allow the old key to die of old age.

---

### Scenario 3 — "A new service joined the mesh and can call every other service"

**The situation.** You add a `reporting-service`. It needs to call `orders-api` and
`billing-api`. A week later, security reviews the token flow and discovers that
`reporting-service`'s access token — minted with `aud=["reporting-api"]` — is **accepted by
`orders-api`**. The reviewer's report says "confused deputy, high severity".

**Your first move.**

```bash
# 1. Prove it. Mint a token for reporting-api and call orders-api with it.
TOKEN=$(curl -s -X POST https://auth.acme.com/realms/acme/protocol/openid-connect/token \
  -u 'reporting-worker:secret' -d 'grant_type=client_credentials' \
  | jq -r .access_token)
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $TOKEN" \
  https://api.acme.com/orders/api/orders
# → 200   ← the bug

# 2. What does the resource server actually validate?
grep -rn "JwtValidators\|JwtClaimValidator\|DelegatingOAuth2TokenValidator" src/main/java/
```

**Expected signal.** The only validator in the code is
`JwtValidators.createDefaultWithIssuer(issuer)` — **no audience validator**. The `iss` matches
because every token comes from the same authorization server; the `aud` is never checked, so
the token is valid everywhere.

**The trap.** "Add the audience check globally, but make it configurable so services can opt
out." That reintroduces the hole with extra steps.

**The resolution.** Give **every** resource server an audience validator that hard-codes its
own service id, and make it a shared, reusable configuration so no service can forget it:

```java
@Bean
JwtDecoder jwtDecoder(String issuer, @Value("${acme.service-id}") String serviceId) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(RS256).build();
    OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
            JwtClaimNames.AUD, aud -> aud != null && aud.contains(serviceId));
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuer), audience));
    return decoder;
}
```

**The lesson.** "Validate the audience" is a one-line advice and a fleet-wide bug. Put it in
**shared, mandatory configuration** and make the service id a required property, so that
omitting it is a startup failure rather than a silent hole.

---

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Isn't OAuth just fancy session management? Why not a token that says 'user 123'?"

- **Naive answer:** "OAuth is a session mechanism with extra steps."  ← *confuses the layers*
- **The trap:** The trap is that a "token saying user 123" is an **ID token**, and using it as
  an API credential is the confused-deputy bug. OAuth adds three things a session identifier
  does not have: **delegation** (the token is scoped to a client and a grant), **an
  authorization server that can authenticate a user the client has never seen**, and **a
  back-channel exchange** so the user never hands a credential to the client.
- **Expert answer:** A session id is an *opaque pointer to server state*. OAuth's access token
  is a **self-contained, scoped, expiring bearer credential issued by a separate party**. The
  difference shows up in three concrete capabilities: (1) a third-party app can be granted
  access to *your* account without your password; (2) the API can validate the credential
  **without a shared store** — no session affinity, no sticky routing; (3) the grant is
  *auditable and revocable per client*. The cost is that revocation becomes a TTL problem
  rather than a delete, which is why refresh tokens and rotation exist.

### CQ2. "Why does the authorization server need to know about the resource server at all?"

- **Naive answer:** "It doesn't — the token just carries the scopes."  ← *true but incomplete*
- **The trap:** The trap is thinking the AS only needs to know the *client*. The AS must know
  the **resource servers** because it has to decide **which audience the token is for** and
  **what to put in the `aud` claim** — and that decision is exactly what the resource server's
  audience validator later enforces. Without a registered resource server there is no `aud`,
  and without `aud` validation every service accepts every token.
- **Expert answer:** The AS knows the resource servers so it can (1) issue an `aud` claim that
  names the intended API, (2) scope the issued token to the resources that client is allowed to
  use, and (3) in the *introspection* model, answer "is this token valid for this audience?".
  In OAuth 2.0 the relationship between client and resource server was loose — the AS could
  issue a token for a client without knowing which APIs it would call. That looseness is the
  root of the confused-deputy class of bugs, and modern practice (RFC 8707's `aud` parameter
  on the authorization request, RFC 9396's `resource` parameter on the token request) tightens
  it: the client now states which resource it wants the token for.

### CQ3. "The client requests `scope=admin`. Why doesn't the authorization server just issue it?"

- **Naive answer:** "Because the client asked nicely."  ← *the trap is in the middle*
- **The trap:** The trap is thinking the check happens in one place. There are **three**
  independent checks, and a deployment that only implements two is exploitable: (1) the AS
  checks the requested scope against the **client's pre-registered scopes**; (2) the AS checks
  it against the **user's consent**; (3) the **resource server** checks the token's actual
  `scope` against what the endpoint needs. Remove (3) and the token is a skeleton key for
  every endpoint; remove (1) and any registered client can ask for anything.
- **Expert answer:** Scope enforcement is **defence in depth across three parties**, and each
  one defends against a different mistake. The AS defends against a *misconfigured or
  malicious client*. The consent screen defends against a *misleading request* to the user. The
  resource server defends against a *misconfigured API* — and this last one is the one teams
  forget, because the AS already "did its job". The resource server is the only party that
  knows what a particular endpoint needs, so it must enforce it. The canonical mapping is
  Spring's `SCOPE_`-prefixed authorities plus
  `@PreAuthorize("hasAuthority('SCOPE_orders:read')")` — or a custom converter that maps
  scopes to domain roles.

### CQ4. "Is it safe for my API to accept the ID token as well as the access token?"

- **Naive answer:** "Yes, they're both JWTs signed by the same key."  ← *false, and dangerous*
- **The trap:** The trap is that **same key** does not mean **same audience**. The ID token's
  `aud` is your **client**; the access token's `aud` is your **API**. If your API validates
  only the signature and the issuer, an ID token minted for your client authenticates against
  your API. The attack is not exotic — the ID token is in the browser, and any user can paste
  it into a request to your API.
- **Expert answer:** No — accepting the ID token at the API is the confused-deputy attack in
  its simplest form. The defence is the audience validator: the API accepts only tokens whose
  `aud` contains **its own** service id. If you genuinely need to accept both token types,
  add a `typ` check (RFC 9068 defines `at+jwt` for access tokens) *and* keep the audiences
  disjoint — never let one audience set contain both. Chapter 03 §8 shows the exact
  validator.

### CQ5. "Why did OAuth 2.1 remove the password grant? It was convenient."

- **Naive answer:** "Because it was insecure."  ← *true, and it misses the design reason*
- **The trap:** The trap is treating the password grant as a security hole in the protocol.
  It was not — it was perfectly implementable. The problem is that it **teaches the wrong
  model**: the third-party app handles the user's password, so the app becomes a phishing
  target, stores the credential, and is responsible for password policy. The grant made the
  OAuth ecosystem's *worst practice* the *easiest path*.
- **Expert answer:** The password grant collapses the two problems OAuth exists to separate.
  It makes the client a **credential-handling party** — the thing OAuth was designed to
  eliminate — and it removes the authorization server's ability to control the scope, the
  audience and the lifetime of the grant, because the app now holds the master credential. In
  the words of the draft, the grant is removed because it "exposes the user credentials to the
  client". The functional gap it leaves (a first-party app with its own login form) is filled
  by OpenID Connect's authorization code flow plus a local session — the modern equivalent of
  what the password grant did badly.

### CQ6. "How is OAuth different from just issuing API keys?"

- **Naive answer:** "OAuth is more secure than API keys."  ← *true, and it dodges the point*
- **The trap:** The trap is comparing them on a security axis when they solve **different
  problems**. An API key identifies a *system*. OAuth delegates a *user's authority* to a
  *system*. There is no user in the API-key model, so there is no consent, no scope, no
  per-user revocation.
- **Expert answer:** The two compose rather than compete. A common real architecture is: the
  client-credentials grant issues a **short-lived token that is itself scoped**, and the token
  is minted from an **API key** that is the client's long-lived credential. The API key is the
  identity used at the token endpoint; the token is the short-lived, scoped, revocable
  credential used at the API. That layering gives you the best of both: the API key never
  travels on every request (so a log leak is far less damaging), and the token is scoped and
  short-lived. The trap to avoid is minting a token with **no scopes** — a long-lived,
  unscoped token is just an API key with extra steps.

### CQ7. "What happens if the authorization server is down while users are already logged in?"

- **Naive answer:** "Nothing — the access tokens are still valid."  ← *true, and incomplete*
- **The trap:** The trap is stopping at "the API still works". It does — but the **refresh
  path** and the **login path** are dead. So the blast radius of the outage is exactly the
  **access-token TTL**: after 15 minutes, every user is logged out and cannot get back in.
- **Expert answer:** The degradation is asymmetric and you should say so. **Access-token
  validation** is local, so it is unaffected. **Refresh** and **new logins** are not. The
  blast radius is therefore the access-token TTL, which means **the access-token TTL is your
  auth-outage tolerance** — a 5-minute TTL means a 5-minute outage impact, a 60-minute TTL
  means an hour. That is the real trade-off, and it is the argument for keeping the TTL short
  in the first place. You can also add a **stale-while-revalidate** refresh: if the AS is
  unreachable, serve the still-valid refresh token's access token until it truly expires,
  with an alert. Do not, however, extend the access-token TTL "temporarily" — that is the one
  change that converts a login outage into an unrevocable-authority outage.

### CQ8. "Can one client be registered with several providers?"

- **Naive answer:** "Yes, just add two registrations."  ← *true, and the trap is in 'just'*
- **The trap:** The trap is the word "just". Two registrations means **two `registrationId`s**
  and **two `OAuth2AuthorizedClient`s**, and the design question becomes: *which one is the
  `OAuth2AuthorizedClientRepository` keyed by?* The repository is keyed by
  `(registrationId, principalName)`. If your app uses Google for consumer login and a
  corporate OIDC for employees, you have **two grants for the same person**, and the
  `OAuth2AuthorizedClientManager` must be told which one to use.
- **Expert answer:** Yes, and Spring models it naturally: one `ClientRegistration` per
  provider, each with its own `registrationId`. The consequences are real, though: (1) the
  `OAuth2AuthorizedClientRepository` stores both, keyed by `registrationId` + principal;
(2) the `OAuth2AuthorizedClientManager.authorize(...)` call must name the `registrationId`;
(3) if a user has both accounts, you need an explicit **account-linking** rule — usually
  "link by a verified email in a verified domain", never by email alone; and (4) the
  `SecurityContext` principal carries the registration id, so downstream code that needs
  "which provider did this user come from?" must read it from the
  `OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()` rather than guess.

---

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — "Every request returns 401 `invalid_token`"

**Symptom.** A resource server returns 401 for every authenticated call; the body is empty and
the header says `WWW-Authenticate: Bearer error="invalid_token"`. New logins work; the API
never sees a valid request.

**Diagnostic tool/command.**

```bash
# 1. Turn on the OAuth2 client debug logging on ONE replica.
kubectl set env deployment/orders-api LOGGING_LEVEL_ORG_SPRINGFRAMEWORK_SECURITY_OAUTH2=DEBUG
kubectl logs deploy/orders-api --tail=200 | grep -i "jwt\|token\|kid\|validat"

# 2. Decode the failing token locally.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null; echo

# 3. Compare the 'kid' with the published JWKS.
curl -s "$JWKS_URI" | jq '.keys[].kid'
```

**Expected signal.** One of exactly four things appears in the debug log:
`JwtTimestampValidator` (the token is expired or not yet valid — check the clocks),
`JwtIssuerValidator` (the `iss` does not match — check the `issuer-uri` configuration),
`SignatureException` / `InvalidSignatureException` (wrong key or a `kid` that is not in the
JWKS), or your own audience validator (the `aud` does not contain your service id).

**The signal to distinguish.** A `JwtTimestampValidator` failure on *every* token points at
**clock skew** — compare `date -u` on the API pod with the IdP's time. A `kid` mismatch on
*new* tokens only points at a **key rotation** that skipped the overlap window.

**Fix.** For clock skew: fix the NTP configuration (`timedatectl` showing
`System clock synchronized: no` is the giveaway) and keep the 60-second leeway. For rotation:
publish the missing key to the JWKS and then change the rotation runbook to the three-phase
procedure.

---

### Issue 2 — "The token request returns `401 invalid_client`"

**Symptom.** The client POSTs to `/token` and gets:
`{"error":"invalid_client","error_description":"Client authentication failed"}`.

**Diagnostic tool/command.**

```bash
# 1. What exactly are we sending? Never log the secret — print the header NAME and length.
curl -s -X POST "$TOKEN_URI" \
  -H "Authorization: Basic $(printf '%s:%s' "$CLIENT_ID" "$CLIENT_SECRET" | base64 -w0)" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code&code=$CODE&redirect_uri=$REDIRECT" -i | head -20

# 2. Check the client is enabled, and check the expected client-auth method.
#    Keycloak → realm → Clients → your-client → Credentials tab / Advanced settings.
#    Look for: "Client Authenticator" = "Client Id and Secret", and "Secret" set.

# 3. Confirm the credentials are what you think they are (no trailing whitespace/newline).
printf '%s' "$CLIENT_SECRET" | wc -c
```

**Expected signal.** The most common causes, in order of frequency:

1. **The secret has a trailing newline** from a `kubectl get secret` → `echo` pipeline. The
   `wc -c` count is one byte longer than the secret.
2. **The wrong client id** — you are using the *public* `client_id` from the config but the
   secret belongs to a different client, or the id has the `realm` suffix
   (`orders-web` vs `acme/orders-web` — the latter is needed for token introspection with
   `client_secret_post`).
3. **The client is disabled** in the realm.
4. **The client is public** (`Client Authenticator: Client Id Only`) but you are sending a
   secret — the AS ignores it and then fails.
5. **The secret is wrong for the environment** — a staging secret left in the prod config.

**Fix.** Reset the secret in the IdP, store it in a secret manager (never in `application.yml`
in git), and add a startup smoke test that performs a client-credentials token request
against the **real** token endpoint — it turns a runtime 401 into a startup failure.

---

### Issue 3 — "The discovery document returns 404"

**Symptom.** `issuer-uri` is configured and the application refuses to start with
`Failed to retrieve the configuration from the issuer` or a 404 on
`/.well-known/openid-configuration`.

**Diagnostic tool/command.**

```bash
# 1. Fetch the discovery document exactly as the app does.
curl -s -i https://idp.acme.com/realms/acme/.well-known/openid-configuration | head -20

# 2. Check the issuer URL has no trailing slash and no path mistakes.
curl -s -o /dev/null -w '%{http_code}\n' https://idp.acme.com/realms/acme

# 3. Compare the 'issuer' field in the document with the configured issuer-uri.
curl -s https://idp.acme.com/realms/acme/.well-known/openid-configuration | jq -r .issuer
```

**Expected signal.** The discovery document's `issuer` value is
`https://idp.acme.com/realms/acme` but the configured `issuer-uri` is
`https://idp.acme.com/realms/acme/` — **with a trailing slash**. Spring resolves the
discovery URL by appending `/.well-known/openid-configuration`, and a trailing slash produces
`…/realms/acme//.well-known/openid-configuration`, which Keycloak 404s. The same 404 happens
when the **realm name is wrong** — a typo in the realm path.

**The trap.** "Let me just hard-code the token-uri and user-info-uri so discovery is not
needed." You can, but you then lose the JWKS discovery and have to hard-code that too — and
you re-introduce the coordinated-redeploy problem discovery was meant to remove.

**Fix.** Remove the trailing slash from `issuer-uri` and make the `issuer` claim in the IdP
**exactly** equal to the configured value (Spring's `JwtIssuerValidator` compares them
literally). Add a startup check that fetches the document and asserts the `issuer` matches.

---

### Issue 4 — "The callback returns `invalid_state` / the login loops forever"

**Symptom.** The user clicks "Log in with Google", authenticates, and is bounced back to the
login page — repeatedly.

**Diagnostic tool/command.**

```bash
# 1. Is the state being stored at all? Check the session cookie before and after the redirect.
#    Browser devtools → Application → Cookies → JSESSIONID; or:
curl -s -c /tmp/cookies.txt -o /dev/null -w '%{http_code}\n' https://app.acme.com/oauth2/authorization/google
grep JSESSIONID /tmp/cookies.txt

# 2. What does the callback carry, and what is stored?
#    DEBUG on org.springframework.security.oauth2.client.web
kubectl logs deploy/orders-api --tail=100 | grep -i "state\|authorization request"

# 3. Is the app behind more than one instance, with a non-shared session store?
kubectl get deploy orders-api -o jsonpath='{.spec.template.spec.containers[0].env}' | tr ',' '\n' | grep -i session
```

**Expected signal.** Three distinct causes, distinguishable in the logs:

1. **`Authorization request not found`** — the state was never stored. Either the
   `AuthorizationRequestRepository` is not writing (a custom repository that returns without
   saving) or the **session is not being carried** across the redirect. The classic cause is
   **more than one application instance with a non-shared session store**: the login lands on
   pod A, the callback lands on pod B, and pod B has never heard of the state.
2. **`state` mismatch** — the stored and returned values differ. Usually a **load balancer
   that is not sticky**, or a `AuthorizationRequestRepository` that stores in a cookie with a
   `SameSite` attribute that the browser refuses to send back.
3. **The session cookie is dropped** because the redirect crosses sites and the cookie is
   `SameSite=Strict` — use `SameSite=Lax` for the session cookie, which is the default for
   Spring Boot's `server.servlet.session.cookie.same-site`.

**Fix.** For multi-instance deployments, either use a **shared session store** (Redis) or
switch to `HttpCookieOAuth2AuthorizationRequestRepository` with **CSRF protection enabled** on
the callback — the double-submit-cookie pattern, covered in chapter 02.

---

---

## 19. Architecture Design Questions

### 19.1 "Design the OAuth2 architecture for a multi-tenant SaaS platform"

**The requirement.** A multi-tenant SaaS (12,000 workspaces) must let each workspace connect
its own Google Workspace, Slack and GitHub. Each connection must be revocable independently,
the SaaS must call the providers' APIs on the user's behalf, and a customer must be able to
audit which connections exist.

**Reference design.**

```text
   ┌──────────────────────────────────────────────────────────────────┐
   │  SaaS FRONTEND (SPA)  — public client, Authorization Code + PKCE │
   └──────────────────┬───────────────────────────────────────────────┘
                      │ 302 → /oauth2/authorization/google
                      │      (state in an HttpOnly cookie, CSRF on)
                      ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │  SaaS BACKEND (Spring Boot, oauth2-client)                       │
   │    • owns the client_id/client_secret (confidential client)      │
   │    • stores the REFRESH TOKEN only, hashed + encrypted at rest    │
   │    • table: connected_account(workspace_id, provider,             │
   │             external_subject, encrypted_refresh_token, scopes,    │
   │             created_at, revoked_at)                              │
   │    • never stores the provider's access token                     │
   └──────────┬───────────────────────────────────────────────────────┘
              │  refresh when the provider access token expires
              ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │  PROVIDERS: Google / Slack / GitHub                              │
   └──────────────────────────────────────────────────────────────────┘

   ┌──────────────────────────────────────────────────────────────────┐
   │  SaaS API (Spring Boot, oauth2-resource-server)                  │
   │    • validates its OWN tokens locally (JWKS)                      │
   │    • the connected_account lookup is the app's own concern        │
   └──────────────────────────────────────────────────────────────────┘
```

**Data-flow and the key decisions.**

| Decision | Choice | Rationale |
|---|---|---|
| Which party holds the provider credentials | The SaaS backend | A SPA cannot keep a secret; the backend is the confidential client |
| Grant type | Authorization Code + **PKCE** | SPA is a public client; PKCE is mandatory in OAuth 2.1 |
| Where `state` lives | `HttpCookieOAuth2AuthorizationRequestRepository` | The SPA is stateless; a session would force sticky sessions |
| What is stored | The **refresh token**, encrypted at rest, plus the scope list | The access token is short-lived and re-mintable; storing it adds risk for no benefit |
| Per-connection revocation | `revoked_at` on the row + a call to the provider's `/revoke` | One connection dies; the other connections and the workspace survive |
| Tenant isolation | `workspace_id` on every row; every query filtered by it | Never let a provider's `external_subject` be a global key |
| Provider failure | A per-connection `status` (`ACTIVE` / `REAUTH_REQUIRED` / `REVOKED`) | A revoked Google account must not look like a bug |

**The trade-offs to say out loud.**

1. **Storing the refresh token** means the SaaS holds a long-lived credential for a third
   party — encrypted at rest, rotated, and revocable, but a real liability. The alternative is
   re-authorising the user each time, which is unacceptable for a background integration.
2. **Encrypting, not hashing**, the refresh token — unlike our *own* refresh tokens (which are
   hashed because we only ever compare them), a **provider's** refresh token must be
   **presented back to the provider**, so it cannot be hashed. This is a subtle and
   high-scoring distinction.
3. **The `state` in a cookie** means the callback is a state-changing request that a browser
   sends cross-site — so CSRF protection must be on for that endpoint. That is the
   double-submit-cookie pattern (chapter 02).
4. **Storing the access token would let us skip the refresh round-trip**, saving ~300 ms per
   provider call — but it doubles the credential surface. Not worth it.

---

### 19.2 "Design the auth layer for a bank: 40 services, an existing session-based monolith, and a regulatory requirement for full audit"

**The requirement.** A bank's internal platform must support browser sessions (customers),
service-to-service calls (40 microservices) and third-party open-banking access (UK-style,
with FAPI-like requirements). Every access decision must be auditable for 7 years.

**Reference design.**

```text
   ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────┐
   │ Browser (web)   │   │ Mobile app      │   │ Open-banking    │
   │ session cookie  │   │ PKCE + refresh  │   │ partner (conf.) │
   └────────┬────────┘   └────────┬────────┘   └────────┬────────┘
            │  Authorization Code + PKCE (S256)          │
            └───────────────┬──────────────────────────┘
                            ▼
   ┌────────────────────────────────────────────────────────────────┐
   │  AUTHORIZATION SERVER (Spring Authorization Server / Keycloak) │
   │   • authorization_code + PKCE (S256) for all user flows         │
   │   • client_credentials for the 40 internal services              │
   │   • refresh tokens: ROTATED, sender-constrained where possible  │
   │   • PAR (RFC 9126) enabled for the open-banking partners       │
   │   • every consent decision persisted and auditable              │
   └───────────┬────────────────────────────────────────────────────┘
               │  issuer · jwks · discovery
               ▼
   ┌────────────────────────────────────────────────────────────────┐
   │  40 RESOURCE SERVERS                                           │
   │   • local JWT validation (JWKS, cached) — no introspection at   │
   │     the hot path                                                │
   │   • audience validator on EVERY service (aud = service id)      │
   │   • a shared, mandatory JwtDecoder configuration so no service  │
   │     can omit the audience check                                 │
   │   • every authorization decision emits an audit event with:    │
   │     subject, client_id, scope, aud, decision, correlation id    │
   └────────────────────────────────────────────────────────────────┘
```

**The audit story**, which is the part most candidates miss: JWT validation is *stateless*, so
the resource server has no record of what happened. The audit trail must be **emitted as an
event at decision time** (decision, subject, client, scope, outcome, timestamp) and shipped to
an append-only store — Kafka → object storage with WORM retention for the 7-year requirement.
The 401/403 counters are metrics; the audit events are evidence.

**The trade-offs to say out loud.**

1. **Local validation vs introspection.** Local validation keeps the 40 services alive during
   an AS outage but means a revoked token is valid until it expires. For a bank, that pushes
   the access-token TTL down to **5 minutes** and requires a refresh-token denylist. If
   *instant* revocation of a high-value token is a hard requirement, that specific API gets
   introspection — a per-service decision, not a fleet-wide one.
2. **One AS vs a federation.** One AS is simple and gives a single consent model; a
   federation (the bank's IdP federating to an external IdP for employees) adds an identity
   provider hop and a second trust boundary. The bank would run its own AS and use OIDC
   federation for employee logins.
3. **Refresh-token rotation** costs a database write per refresh and a 7-year audit trail of
   token events — but it gives reuse detection, which is the only way to detect a stolen
   refresh token.
4. **PAR** adds a network round trip on the authorization request and a client-side artifact
   to manage, but it removes the request-tampering attack — worth it only for the
   open-banking partners, not for internal flows.
5. **The 7-year audit requirement rules out** "the access token is the audit record" — a JWT
   is not an audit log, and it is readable by anyone who holds it.

---

## 20. Rapid-Fire MCQs

**1.** OAuth 2.0 is fundamentally:
- A) An authentication protocol that returns a user identity token
- B) **A delegation framework for granting a third-party client scoped access to a resource**
- C) A session management protocol
- D) A token format specification

**2.** Which role holds the user's identity in a standard OAuth deployment?
- A) The client
- B) The resource server
- C) **The authorization server**
- D) The resource owner

**3.** In the Client Credentials grant, the access token's `sub` claim is:
- A) The end user
- B) `null`
- C) **The client (application) id**
- D) The resource server id

**4.** Which grant type was **removed** in OAuth 2.1?
- A) Authorization Code
- B) Client Credentials
- C) Device Authorization
- D) **Implicit**

**5.** The Implicit grant is insecure primarily because:
- A) It uses symmetric keys
- B) **It returns the access token in the URL fragment**
- C) It does not use TLS
- D) It has no client id

**6.** In PKCE, the `code_challenge` sent on the authorization request is:
- A) The `code_verifier` itself
- B) `BASE64URL(SHA256(code_verifier))`
- C) A random UUID
- D) `BASE64URL(SHA256(client_secret))`

**7.** The `plain` PKCE challenge method exists for:
- A) Confidential clients
- B) **Legacy clients that cannot compute a hash**
- C) Machine-to-machine clients
- D) Token exchange

**8.** Which parameter protects the authorization request against CSRF / login-CSRF?
- A) `nonce`
- B) `code`
- C) **`state`**
- D) `client_id`

**9.** Which parameter binds the ID token to the user's browser session (replay defence)?
- A) `state`
- B) **`nonce`**
- C) `code`
- D) `jti`

**10.** Does the Client Credentials grant return a refresh token?
- A) Yes, always
- B) Yes, if the client requests the `offline_access` scope
- C) **No — there is no user session to refresh**
- D) Only for confidential clients

**11.** `JwtValidators.createDefaultWithIssuer("https://auth.acme.com")` validates:
- A) `iss`, `exp`, `nbf` **and** `aud`
- B) **`iss`, `exp` and `nbf` only**
- C) Only the signature
- D) `iss` and `aud` only

**12.** The `redirect_uri` in the token request must:
- A) Match any registered URI with the same path
- B) Be omitted for public clients
- C) **Be identical to the one used in the authorization request**
- D) Always be an HTTPS URL

**13.** Which Spring Boot starter makes a Spring app **validate tokens issued by others**?
- A) `spring-boot-starter-oauth2-client`
- B) `spring-boot-starter-oauth2-authorization-server`
- C) **`spring-boot-starter-oauth2-resource-server`**
- D) `spring-security-oauth2-jose`

**14.** Which Spring Boot starter makes a Spring app **issue tokens to other applications**?
- A) `spring-boot-starter-oauth2-client`
- B) **`spring-boot-starter-oauth2-authorization-server`**
- C) `spring-boot-starter-oauth2-resource-server`
- D) `spring-boot-starter-security`

**15.** The scope claim of an access token maps by default to Spring authorities:
- A) `ROLE_`-prefixed
- B) **`SCOPE_`-prefixed**
- C) `AUTHORITY_`-prefixed
- D) Mapped verbatim with no prefix

**16.** An access token is accepted by `billing-api` even though it was minted for `orders-api`.
What is missing?
- A) A `state` parameter
- B) **An audience (`aud`) validator**
- C) A `nonce` parameter
- D) A longer token TTL

**17.** OAuth 2.1 requires the `redirect_uri` to be matched:
- A) By prefix
- B) By wildcard
- C) **Exactly**
- D) Case-insensitively

---

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **B** | OAuth delegates *authorisation*; OpenID Connect adds identity. |
| 2 | **C** | Only the authorization server authenticates the user — the client and the API never see the password. |
| 3 | **C** | There is no user in the flow; the subject is the application. |
| 4 | **D** | Implicit was removed; so was the resource-owner-password grant. |
| 5 | **B** | The URL fragment leaks through history, the `Referer` header and page JavaScript. |
| 6 | **B** | `S256` = `BASE64URL(SHA256(code_verifier))`; `plain` sends the verifier itself. |
| 7 | **B** | `plain` is the legacy escape hatch for clients that cannot hash — it is nearly useless. |
| 8 | **C** | `state` is stored before the redirect and compared on the callback. |
| 9 | **B** | `nonce` is echoed in the ID token; `state` protects the request, `nonce` protects the token. |
| 10 | **C** | No user session exists, so there is nothing for a refresh token to preserve. |
| 11 | **B** | It validates the issuer and the timestamps — **not** the audience. |
| 12 | **C** | RFC 6749 §4.1.3 — a mismatch with the bound URI is `invalid_grant`. |
| 13 | **C** | `-resource-server` registers `BearerTokenAuthenticationFilter` and the `JwtDecoder`. |
| 14 | **B** | `-authorization-server` runs Spring Authorization Server and issues tokens. |
| 15 | **B** | `JwtGrantedAuthoritiesConverter` defaults to the `SCOPE_` prefix. |
| 16 | **B** | Without an `aud` validator the API is a confused deputy for every other service. |
| 17 | **C** | OAuth 2.1 removed prefix/wildcard matching after real code-theft bugs. |

> 📌 **The three to re-read:** **Q11** (the `aud` validation gotcha), **Q12** (the
> `redirect_uri` on the token request) and **Q16** (the confused-deputy `aud` check).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **OAuth 2.0 = ?** | A **delegation framework** for third-party access — *not* an authentication protocol |
| **OIDC = ?** | The **identity layer** on top: `openid` scope, ID token, UserInfo, discovery, logout |
| **Roles** | Resource owner · Client · Authorization server · Resource server (AS and RS are often one product) |
| **Access token** | Bearer credential **for the API** · 5–15 min · `Authorization: Bearer <jwt>` |
| **Refresh token** | **For the client** to get new access tokens · 7–30 days · stored **hashed** |
| **ID token** | **For the client** · OIDC only · identity assertion · **never** sent to the API |
| **Scope vs role** | OAuth concept ("what this grant allows") vs app concept ("what this user may do") — needs an explicit map |
| **Default scope→authority** | `SCOPE_`-prefixed, from the `scope`/`scp` claim |
| **Grant — web app** | **Authorization Code + PKCE** (secret *and* PKCE) |
| **Grant — SPA / mobile** | **Authorization Code + PKCE** (public client, S256) |
| **Grant — service-to-service** | **Client Credentials** — no user, no refresh token |
| **Grant — TV / CLI / IoT** | **Device Authorization** (RFC 8628) |
| **Grant — banned** | **Implicit** — token in the URL fragment; removed in OAuth 2.1 |
| **PKCE** | `code_challenge = BASE64URL(SHA256(code_verifier))` · method `S256` · `plain` = legacy only |
| **`state`** | CSRF defence on the authorization request — stored, then compared on the callback |
| **`nonce`** | ID-token replay defence — must be echoed in the ID token |
| **`code`** | Single-use · ~30–60 s · bound to the client and the `redirect_uri` |
| **OAuth 2.1** | PKCE mandatory · Implicit removed · password grant removed · **exact** `redirect_uri` |
| **Discovery** | `/.well-known/openid-configuration` · `/.well-known/oauth-authorization-server` · `/jwks.json` |
| **Spring — client** | `spring-boot-starter-oauth2-client` · `/oauth2/authorization/{id}` · `/login/oauth2/code/{id}` |
| **Spring — resource server** | `spring-boot-starter-oauth2-resource-server` · `issuer-uri` / `jwk-set-uri` |
| **Spring — issuer** | `spring-boot-starter-oauth2-authorization-server` · `/oauth2/authorize` · `/oauth2/token` |
| **Spring — JOSE** | `spring-security-oauth2-jose` — JWK/JWKS, `NimbusJwtDecoder` |
| **Client registration** | `ClientRegistration` = static; `OAuth2AuthorizedClient` = the runtime grant |
| **Client token mgmt** | `OAuth2AuthorizedClientManager` + `OAuth2AuthorizedClientProvider` + `OAuth2AuthorizedClientRepository` |
| **Client flow chain** | `OAuth2AuthorizationRequestRedirectFilter` → `OAuth2AuthorizationCodeGrantFilter` → `OAuth2LoginAuthenticationFilter` → `OAuth2LoginAuthenticationProvider` → `OAuth2AuthenticationToken` |
| **Resource chain** | `BearerTokenAuthenticationFilter` → `JwtDecoder` → validators → `Jwt` → authorities → `SecurityContextHolder` → `AuthorizationFilter` |
| **Local vs introspection** | Local = ~90 µs, survives an AS outage; introspection = 5–40 ms, AS is on the hot path |
| **`createDefaultWithIssuer`** | Validates `iss` + timestamps — **NOT** `aud` |
| **Status codes** | **401** = not authenticated · **403** = authenticated but not authorized |
| **Hard rules** | Exact `redirect_uri` · PKCE everywhere · validate `state`+`nonce`+`aud` · minimal scopes · never log a token · never embed a secret in a SPA |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Access-token TTL | 300 s (5 min) | 900 s for low-privilege, high-volume APIs; **never** above 15 min for anything with PII |
| Refresh-token TTL | 30 days | 7 days for banking/regulated; 90 days for low-risk consumer integrations |
| ID-token TTL | 300 s | It is consumed immediately; keep it short |
| PKCE challenge method | `S256` | **Never** `plain` for anything new |
| PKCE verifier length | 43–128 chars | 64 characters is a good default; must be from a CSPRNG |
| `state` length | ≥ 16 random bytes | 32 bytes if you also use it to carry a nonce-like value |
| `code` TTL | 30–60 s | 10 s for high-value clients; it is single-use anyway |
| Authorization request TTL (session) | Session-scoped | Use a cookie with a 5-minute max-age for a stateless SPA |
| `redirect_uri` matching | Exact | **Never** enable prefix/wildcard matching |
| Requested scopes | Minimal per client | Split broad scopes so a grant can be downgraded without re-consent |
| Token audience | One service id per API | Use an array when the same token legitimately serves two APIs — and validate it |
| Client-credentials cache skew | `expires_in - 60 s` | `expires_in - 30 s` on a fast LAN; keep ≥ 30 s |
| Discovery at startup | Enabled via `issuer-uri` | Switch to `jwk-set-uri` if the AS may be unreachable at boot |
| JWKS cache | ~5 min (library) | Align with the rotation runbook; refresh also happens on an unknown `kid` |
| Key-rotation overlap | ≥ max token TTL + skew | **Always** publish → sign → wait → remove |
| Client secret rotation | 90 days | Support two active secrets so rotation needs no downtime |
| `state` storage | `HttpSession` | `HttpCookieOAuth2AuthorizationRequestRepository` for a stateless SPA — **with CSRF on** |
| Logging | Never log tokens or codes | Redact `Authorization` at the proxy; redact the query string on the callback |
| 401 metric | WARN | DEBUG for `org.springframework.security.oauth2` in non-production only |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `oauth2.token.issued.count{grant_type,client_id}` | Spot a client that re-mints instead of caching |
| `oauth2.token.introspect.duration{p99}` | The introspection tax, if you use it |
| `jwt.failures.by_reason` (signature / claims / unknown-kid / audience) | Separates an attack from a misconfiguration |
| `security.oauth2.authorizations` | The 401 rate — the single most important auth metric |
| `oauth2.client.authorization.failures{reason}` | `invalid_state`, `invalid_grant`, `invalid_scope`, `unauthorized_client` |
| `jwks.cache.hit_ratio` | A fall means unknown `kid`s — a rotation, or an attack |
| `token.size.p99` | A rising tail means the claim set is growing |
| `connected_account.refresh.failure{provider}` | A revoked upstream account masquerading as a bug |

**Defaults to deviate from, deliberately.**

- **Never** downgrade to `plain` PKCE "to make the client work" — fix the client.
- **Never** relax the `redirect_uri` match "just for this partner" — register the exact URI.
- **Never** turn on `direct access grants` (password grant) in a production realm — it is
  removed in OAuth 2.1 for a reason.
- **Never** raise the access-token TTL to "fix" a refresh failure — fix the refresh path; the
  TTL is your auth-outage tolerance.
- **Never** accept the ID token at the API — keep the audiences disjoint.

---

## 23. Follow-up Learning

**Next in this folder**

- → [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) —
  every redirect and every POST of the code flow, `state`/`nonce`/`code`, the PKCE derivation
  and the four attacks it kills, public vs confidential clients, the Spring filter chain.
- → [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) —
  machine-to-machine flows, refresh rotation, the OIDC identity layer, `issuer-uri` discovery,
  the `aud` gotcha, introspection.
- → [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md) —
  "login with Google", the GitHub email gotcha, Keycloak realms and roles, SAML vs OIDC, JIT
  provisioning, scope minimisation.

**Related folders**

- [06 · JWT Authentication](../06-JWT-Authentication/README.md) — the token format itself:
  JOSE, the claims, the `JwtDecoder` chain, key rotation.
  → [06/01 · JWT Architecture](../06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md)
- [05 · Spring Security](../05-Spring-Security/README.md) — the filter chain, the
  `SecurityContext`, CORS and CSRF.
- **08 · Microservices with Spring Boot** (folder, being written in parallel) — service-to-service
  calls, the gateway, resilience.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 6749 — The OAuth 2.0 Authorization Framework | The roles, the grant types, the token endpoint, the error codes |
| RFC 7636 — PKCE | The `code_verifier`/`code_challenge` derivation and the `S256` method |
| RFC 8628 — Device Authorization Grant | The device flow and the `authorization_pending` response |
| RFC 8693 — Token Exchange | Delegation and impersonation, the `act` claim |
| RFC 7009 — Token Revocation | The `/revoke` endpoint |
| RFC 7662 — Token Introspection | The `/introspect` endpoint and its trade-offs |
| RFC 8414 — Authorization Server Metadata | The `/.well-known/oauth-authorization-server` document |
| RFC 9700 — OAuth 2.0 Security Best Current Practice | The modern hardening baseline (mix-up defence, refresh-token rotation, PKCE) |
| OpenID Connect Core 1.0 | The `openid` scope, the ID token claims, the UserInfo endpoint, RP-initiated logout |
| OAuth 2.1 (IETF draft) | The consolidation: PKCE mandatory, implicit removed, exact redirect matching |
| RFC 8705 — mTLS-Bound Access Tokens | Sender-constrained tokens via `cnf.x5t#S256` |
| RFC 9449 — DPoP | Sender-constrained tokens via a per-request proof |
| RFC 8707 — Resource Indicators | The `resource` parameter for audience narrowing |
| Spring Security 6.x `spring-boot-starter-oauth2-client` | `ClientRegistration`, the filters, the `AuthorizationRequestRepository` |
| Spring Security 6.x `spring-boot-starter-oauth2-resource-server` | `JwtDecoder`, the validators, the introspection path |

---

**Next Chapter:** → [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md)
**Previous Chapter:** ← [06 · JWT Authentication](../06-JWT-Authentication/README.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026


