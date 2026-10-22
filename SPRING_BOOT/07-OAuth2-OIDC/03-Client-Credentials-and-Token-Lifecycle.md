# 03 · Client Credentials, Refresh Tokens & Token Lifecycle

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★☆☆ ("How do your microservices talk to each other securely?")
> **Prerequisite:** [02 · Authorization Code Flow & PKCE](./02-Authorization-Code-Flow-and-PKCE.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Token's Two Uses](#2-mental-model--the-tokens-two-uses)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The client credentials' flow](#31-the-client-credentials-flow)
  - [3.2 The client's authentication's methods](#32-the-clients-authentications-methods)
  - [3.3 The service's identity: OAuth2 vs mTLS vs SPIFFE](#33-the-services-identity-oauth2-vs-mtls-vs-spiffe)
  - [3.4 The refresh token's grant](#34-the-refresh-tokens-grant)
  - [3.5 The refresh's rotation](#35-the-refreshs-rotation)
  - [3.6 The token's lifecycle's states](#36-the-tokens-lifecycles-states)
  - [3.7 The refresh's reuse's detection](#37-the-refreshs-reuses-detection)
  - [3.8 The token's exchange (RFC 8693)](#38-the-tokens-exchange-rfc-8693)
  - [3.9 The resource's indicators (RFC 8707)](#39-the-resources-indicators-rfc-8707)
  - [3.10 The device's flow (RFC 8628)](#310-the-devices-flow-rfc-8628)
  - [3.11 The service's account](#311-the-services-account)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples](#5-worked-examples)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Hands-On Exercises](#11-hands-on-exercises)
- [12. Code Challenges](#12-code-challenges)
- [13. Scenario-Based Questions](#13-scenario-based-questions)
- [14. Tricky Interview Questions](#14-tricky-interview-questions)
- [15. Follow-Up Questions Asked by Interviewers](#15-follow-up-questions-asked-by-interviewers)
- [16. Real 3–5 Year Scenarios](#16-real-35-year-scenarios)
- [17. Frequently Asked Production Issues](#17-frequently-asked-production-issues)
- [18. Debugging Scenarios](#18-debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Cheat Sheet — One-Page Summary](#20-cheat-sheet--one-page-summary)
- [21. Follow-up Learning](#21-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **The client credentials grant** = the machine-to-machine (M2M) flow. The client
  authenticates **itself** (no user), and receives an access token with a `sub` of the
  **client's** id. Use it for the service-to-service and the partner's API's access.
- **The client's authentication's methods** (strongest → weakest):
  `private_key_jwt` (the client signs a JWT with its own key) → `mTLS` → `client_secret_basic`
  → `client_secret_post`. Prefer the first two at scale (no shared secret to rotate).
- **Do you need OAuth2 between your own microservices?** Usually **no** — use the
  **workload's identity** (mTLS/SPIRE, the cloud's IAM) and propagate the user's identity
  as a downstream's claim. OAuth2's client credentials is for the **cross-trust's-domain**
  cases (a partner, a customer, a legacy's system).
- **The refresh token's grant:** the client exchanges the refresh token for a new access
  token. The modern's best practice is the **rotation** (the refresh token is single-use;
  the reuse's detection revokes the family).
- **The token's lifecycle:** issued → active → (rotating) → expired/revoked. The states
  matter because the client's behaviour differs per state (use it, refresh it, or
  re-authenticate).
- **The token's exchange (RFC 8693)** lets a client trade a broad token for a narrow one
  (the "the token's downgrade" pattern — the modern's least's privilege).
- **The one-liner:** *"The client credentials grant is the machine's identity in OAuth's
clothes; use it when the client crosses a trust's boundary, and use the workload's
identity when it doesn't. The refresh token's grant is the same for the user-facing
clients, with the rotation's reuse's detection as the modern's addition."*

---

## 1. What & Why

### Two different problems, two different flows

```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │  "A USER wants to let my app access their Google Drive."              │
  │  → the user must be involved → the AUTHORIZATION CODE flow            │
  │  → the delegation's consent → the user sees a consent's screen        │
  └────────────────────────────────────────────────────────────────────────┘

  ┌────────────────────────────────────────────────────────────────────────┐
  │  "MY REPORTING SERVICE needs to call the Orders API every 5 minutes." │
  │  → there is NO user → the CLIENT CREDENTIALS flow                     │
  │  → no consent, no browser, no code                                    │
  └────────────────────────────────────────────────────────────────────────┘
```

**The mistake that's made constantly:** using the client credentials grant for the
user-facing's flows (because it's "simpler" — no redirect, no state) and thereby giving
the app **full access to the user's account** with no consent and no scope's limitation.
Or worse: using the **password grant** for a machine's flow (the app stores the user's
password in the config).

---

## 2. Mental Model — The Token's Two Uses

```text
  ┌──────────────────────────────────────────────────────────────────┐
  │  the ACCESS token is used for TWO different purposes:             │
  └──────────────────────────────────────────────────────────────────┘

  (A) the USER's DELEGATION            (B) the CLIENT's OWN IDENTITY
      "Alice allowed this app              "The reporting service is
       to read her orders"                 allowed to read orders"
      the sub = alice's id                the sub = the service's id
      the scopes = the user's consent      the scopes = the client's registration
      the flow = the authorization code    the flow = the client credentials
      the refresh = yes                    the refresh = no
      the human = involved                 the human = not involved
```

**The `sub` tells you which one you're looking at.** That single field disambiguates the
token's meaning — and the APIs must handle both (a "the user's data" endpoint and a "the
system's data" endpoint).

---

## 3. Deep Dive — Theory

### 3.1 The client credentials' flow

```text
  POST https://as.example.com/token
    Content-Type: application/x-www-form-urlencoded
    Authorization: Basic <base64(client_id:client_secret)>
    grant_type=client_credentials
    &scope=orders:read reports:write

  → 200 {
      "access_token": "eyJhbGciOiJSUzI1NiJ9...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "orders:read reports:write"
    }
```

**The characteristics:**

| Aspect | The behaviour |
|--------|---------------|
| The user | **None** — there's no `sub` for a human |
| The `sub` | The **client's** id (`service-reporting`) |
| The refresh token | **Not issued** (the client can always get a new one) |
| The consent | **None** — the scope's grant is pre-configured at the registration |
| The `gty` claim | Often `client-credentials` (the grant's type) |
| The `azp` | The client (the party to which the token was issued) |
| The audience | The resource's servers (via the resource's indicator) |

**Why no refresh token:** the client has a **credential** (the secret/key) that it can
present again at any time. A refresh token would be a strictly worse version of that (a
second, longer-lived secret with an extra's step). The refresh token exists for the
**user's** case, where the client has no credential of its own.

**The token's caching (the #1 implementation's bug):**
```text
  ❌ the naive: every API's call → a POST /token → then the API's call
     (doubles the requests, hits the rate's limit, adds the latency)

  ✅ the correct: cache the token in memory, refresh it BEFORE it expires
     (the Spring's OAuth2AuthorizedClientManager does this with the
      OAuth2AuthorizedClientProvider — see §6.2)
```

### 3.2 The client's authentication's methods

| The method | The RFC | The secret's material | The rotation | The scale |
|-----------|--------|---------------------|-------------|----------|
| **`client_secret_basic`** | OAuth 2.0 | A shared's secret | The hard's problem (both's sides) | Fine for a few |
| **`client_secret_post`** | OAuth 2.0 | A shared's secret | The same | ⚠ the body-logging's risk |
| **`private_key_jwt`** | RFC 7523 | The client's **private** key (asymmetric) | ✅ **Easy** (the AS's trust's anchor is the public key) | ✅ The scalable's choice |
| **`tls_client_auth`** | RFC 8705 | The client's **certificate** (mTLS) | ✅ Easy | ✅ The strongest |
| **`none`** | — | Nothing | N/A | The public's clients (the PKCE's flow) |

**`private_key_jwt` in detail:**
```text
  the client signs a JWT's assertion:
    header: { "alg": "RS256", "kid": "client-key-1" }
    payload: {
      "iss": "svc-reporting",      ← the client_id
      "sub": "svc-reporting",      ← the client_id (the same)
      "aud": "https://as.example.com/token",   ← the token's endpoint
      "jti": "<a unique id>",      ← the replay's protection
      "exp": <now + 60s>,          ← SHORT-lived (60s is typical)
      "iat": <now>
    }
    signed with the client's PRIVATE key

  the AS verifies with the client's registered PUBLIC key
  → no shared secret to distribute or rotate
  → the assertion is short-lived and single-use (the jti)
```

**The `exp` must be short (60s or so)** — the assertion is a bearer credential for the
duration of its validity; a long-lived assertion is a long-lived secret.

### 3.3 The service's identity: OAuth2 vs mTLS vs SPIFFE

**This is the question that separates the "3 years" candidate from the "5 years" one.**

```text
  ┌──────────────────────┬────────────────┬────────────────┬──────────────────┐
  │                      │ the shared's    │ the SPIFFE /    │ the cloud's      │
  │                      │ secret (OAuth2)│ mTLS           │ workload's ID    │
  ├──────────────────────┼────────────────┼────────────────┼──────────────────┤
  │ the identity's form  │ a static's     │ a cryptographic │ a platform's     │
  │                      │ password       │ identity       │ attestation      │
  ├──────────────────────┼────────────────┼────────────────┼──────────────────┤
  │ the rotation         │ ⚠ manual,      │ ✅ automatic   │ ✅ automatic     │
  │                      │ coordinated    │ (short's TTL)  │ (short's TTL)    │
  ├──────────────────────┼────────────────┼────────────────┼──────────────────┤
  │ the blast's radius   │ ⚠ the secret   │ ✅ per-workload│ ✅ per-workload  │
  │ of a leak            │ grants all     │                │                  │
  ├──────────────────────┼────────────────┼────────────────┼──────────────────┤
  │ the user's identity  │ ✅ can be       │ ⚠ must be      │ ⚠ must be       │
  │                      │ propagated     │ propagated     │ propagated       │
  │                      │ (the token)    │ (the headers)  │ (the headers)    │
  ├──────────────────────┼────────────────┼────────────────┼──────────────────┤
  │ the best for         │ the cross-     │ the internal's  │ the cloud's      │
  │                      │ trust's domain │ services       │ internal's       │
  └──────────────────────┴────────────────┴────────────────┴──────────────────┘
```

**The modern's microservices's answer:**
```text
  ① the SERVICE's identity → the workload's identity (mTLS/SPIRE, the cloud's IAM)
     → "which service is calling?" is answered cryptographically, automatically rotated
  ② the USER's identity → propagated as a downstream's claim (the JWT's delegation)
     → the gateway validates the user's token and forwards the identity
  ③ the cross-organisation's calls → OAuth2's client credentials (or SPIFFE's federation)
     → the third-party's services can't participate in your SPIFFE's mesh
```

**The "the downstream's claim" pattern:**
```text
   the client → the GATEWAY (with the user's JWT)
                the gateway validates it and re-signs a smaller token:
                { "sub": "alice", "scope": "orders:read", "tenant": "t-7",
                  "orig": "https://gateway", "iat": ..., "exp": ... }
                → the internal's services validate the gateway's token (the internal's JWKS)
                → no user-facing token's traffic internally
```

### 3.4 The refresh token's grant

```text
  POST https://as.example.com/token
    grant_type=refresh_token
    &refresh_token=rt_9f2a...
    &client_id=app-123
    &client_secret=***
    &scope=orders:read       ← OPTIONAL: the narrowing of the scopes

  → 200 { "access_token": "<a new one>", "expires_in": 300, ... }
```

**The `scope` parameter's semantics (the subtle part):**
- If the request **omits** the `scope` → the AS issues the **same** scopes as the original
  grant (or whatever the AS's policy allows).
- If the request **includes** the `scope` → the AS may issue a **subset** (a narrowing).
  **It must never widen** — the RFC says the requested scope "MUST NOT include any scope
  not originally granted".
- Many ASes (Keycloak included) **ignore** the narrowing by default — the full's grant
  is returned. That's a configuration's item, not a bug.

**The OIDC's `offline_access`:** the standard's way to ask for a refresh token in the OIDC
flows. Without it, the AS **won't** issue a refresh token (even if the client is allowed
one) — this is a very common "why don't I get a refresh token?" bug.

### 3.5 The refresh's rotation

**The rotation's model (RFC 6749 §6 recommends it; OAuth 2.1 requires it):**

```text
  the client holds: R1
  refresh(R1) → R1 is REVOKED, R2 is issued
  refresh(R2) → R2 is REVOKED, R3 is issued
  ...
  refresh(R1) again → ⚠ R1 was already used → the REUSE's DETECTION
                     → revoke the whole FAMILY
```

**The family's states:**
```text
  the family's ID (a UUID, the lineage's identifier)

  R1 (revoked) ──► R2 (revoked) ──► R3 (ACTIVE)
        │                 │              │
     the reuse's detection revokes the ENTIRE family
```

**The implementation's options:**

| The option | The mechanism | The trade-off |
|-----------|--------------|--------------|
| **The AS-managed** (the family table) | The AS tracks the family and revokes it | The AS's complexity; the standard's behaviour |
| **The client-managed** (your own's table) | Your app tracks the family in your DB | You control the semantics; but the AS's reuse's detection doesn't know about it |
| **The stateless** (the JWT's refresh + the `jti` blocklist) | The family is encoded in the token's claim | No's storage; but the family's revocation needs the blocklist |

**The gap to know about:** many ASes (Keycloak's default, Auth0's default) **do not**
implement the rotation's reuse's detection out of the box — they issue a new refresh token
and keep the old one valid until it expires. The **security's consequence**: a stolen
refresh token lives for its full lifetime with no detection. The mitigation is to
implement the rotation **in your client** (your own refresh's table, with the family's
logic) — see §6.4.

### 3.6 The token's lifecycle's states

```text
   ISSUED ──► ACTIVE ──► EXPIRED
      │           │
      │           ├──► REVOKED (the consent's revoked, the admin's revoked,
      │           │    the family's revoked, the rotation's reuse)
      │           │
      │           └──► ROTATED (superseded by a successor)
      │
      └──► (never used, the flow's abandoned) → expires
```

**The client's behaviour per state (the error's contract):**

| The state | The API's response | The client's action |
|-----------|-------------------|---------------------|
| Active | 200 | Proceed |
| Expired | 401 `TOKEN_EXPIRED` | Refresh (and retry once) |
| Revoked | 401 `TOKEN_REVOKED` | **Re-authenticate** (do not refresh) |
| Rotated | 401 `TOKEN_REUSED` | **Re-authenticate** + alert (the theft) |
| Invalid (the signature's failure) | 401 `TOKEN_INVALID` | Re-authenticate |
| Insufficient's scope | 403 `FORBIDDEN` | **Never** retry (the step-up's consent) |

### 3.7 The refresh's reuse's detection

**The flow (the client-side implementation):**

```text
  ① the client presents R1
  ② the server: SELECT the row for hash(R1)
       → the row: { revoked: false, family: F1, version: 1, successor_hash: null }
  ③ the ATOMIC's rotation:
       UPDATE refresh_tokens
          SET token_hash = hash(R2), successor_hash = hash(R1),
              revoked = true, version = 2
        WHERE id = ? AND version = 1 AND revoked = false
       → 1 row updated ✅
       (0 rows → someone else rotated it concurrently → the reuse's detection)
  ④ the response: { access_token, refresh_token: R2 }

  --- later ---
  ⑤ the client presents R1 AGAIN
  ⑥ the server: SELECT for hash(R1)
       → the row: { revoked: true, successor_hash: hash(R1), family: F1 }
       → ⚠ the successor_hash matches → this is the grace's window's case
          OR the reuse's detection (if the window has closed)
  ⑦ the reuse's detection → revoke the whole family F1 → 401
```

**The grace window (the important's nuance):** a network's retry or a client's double
refresh will present the old token again **within seconds**. If the server treats that as
a theft, legitimate's users get logged out. The fix: keep the `successor_hash` and
remember the old token for a **short window (30-60s)** — during that window, return the
**same successor** (idempotent). After the window, a presentation of the old token is a
**genuine** reuse → revoke the family.

### 3.8 The token's exchange (RFC 8693)

```text
  the client holds: a broad token (aud = api-a, api-b, api-c; scope = read, write)
  the exchange:
    POST https://as.example.com/token
      grant_type=urn:ietf:params:oauth:grant-type:token-exchange
      &subject_token=<the broad's token>
      &subject_token_type=urn:ietf:params:oauth:token-type:access_token
      &audience=https://api-b.example.com
      &scope=orders:read
      &requested_token_type=urn:ietf:params:oauth:token-type:access_token

  → 200 { "access_token": "<a narrow's token, only for api-b, only orders:read>",
          "issued_token_type": "...access_token",
          "token_type": "Bearer",
          "expires_in": 300 }
```

**The use cases:**
- **The least's privilege's narrowing** — the "the token's downgrade" pattern: a service
  holding a broad token trades it for a narrow one when calling a specific API.
- **The impersonation's delegation** — the service acts on the user's behalf with a
  narrower's scope.
- **The cross's cloud's federation** — trading a Kubernetes's service account token for a
  cloud's STS token (the AWS's, the GCP's, Azure's).

**The security's rules (RFC 8693):**
- The requested's scope must be a **subset** of the subject's token's scopes.
- The requested's audience must be **explicitly allowed** by the AS's policy.
- The AS must **not** issue a token with a broader's audience than requested.
- The exchange's response is itself a **short-lived** token.

### 3.9 The resource's indicators (RFC 8707)

```text
  the /authorize request:
    &resource=https://api.orders.example.com/

  the AS computes the audience:
    the resource URI:  https://api.orders.example.com/orders
    the audience:       https://api.orders.example.com      ← the path & query stripped
```

**Why it matters:**
- **The per-API's audience** — a token for API A can't be used on API B.
- **The migration's path** — the API's host can change without the client's
  re-registration.
- **The token's exchange's input** — the exchange's `audience` parameter.

**The support:** Keycloak (with the `Audience` mapper), Auth0, Okta, and the Spring's
Resource Server all support it (as an additional's parameter).

### 3.10 The device's flow (RFC 8628)

```text
  the DEVICE (a TV, a CLI, a headless's IoT):

  ① POST /device_authorize
       client_id=tv-app
       scope=openid profile
     → 200 {
         "device_code": "GmRhmhcxhwAzkoEqiMEg_DnyEysNkuNhszIySk9eS",
         "user_code": "WDJB-MJHT",         ← the short's code for the user to type
         "verification_uri": "https://as.example.com/device",
         "verification_uri_complete": "https://as.example.com/device?user_code=WDJB-MJHT",
         "expires_in": 900,
         "interval": 5                     ← the polling's interval (seconds)
       }

  ② the user goes to the verification_uri on ANOTHER device (the phone) and types the user_code

  ③ the user authenticates and consents there

  ④ the DEVICE polls:
       POST /token  grant_type=urn:ietf:params:oauth:grant-type:device_code
                    &device_code=...&client_id=tv-app
     → while pending: 400 {"error": "authorization_pending"}
     → when denied:  400 {"error": "access_denied"}
     → when expired: 400 {"error": "expired_token"}
     → on success:   200 { the tokens }
```

**The polling's rules:** the client must wait at least `interval` seconds between the polls
(and the AS may increase the interval if the client polls too fast), and it must respect
the `slow_down` error.

**When it's used:** the TVs, the CLI's tools (`gh auth login` uses it), the headless's
setups, the devices with no keyboard.

### 3.11 The service's account

**Some ASes (Keycloak, Azure AD) model the M2M's access as a **service account** (a
special's client with the "the service's account's roles" enabled). The distinction:

| | The normal's client | The service's account |
|---|---|---|
| The human | Logs in | Doesn't exist |
| The credentials | The secret's key | The secret's key |
| The token's `sub` | The user's id | The service's id |
| The scopes | The user's consent | The client's configuration |
| The typical's use | The app's users | The batch's jobs, the automation |

**The best practice:** the service's account's **client** should have a **minimal's scope
set** and the token's TTL should be **short** (5-15 min) with the client's caching.

---

## 4. Visualizations

### 4.1 The client credentials' flow (the sequence)

```mermaid
sequenceDiagram
    autonumber
    participant S as the Service (the client)
    participant A as the Authorization Server
    participant R as the Resource Server (the API)

    Note over S: the token's cache is empty
    S->>A: POST /token (grant_type=client_credentials, the client auth)
    A->>A: authenticate the client (the secret / the private_key_jwt)
    A-->>S: 200 {access_token, expires_in: 3600}
    Note over S: cache the token with the expiry
    S->>R: GET /api/orders (Authorization: Bearer <the token>)
    R->>R: verify the JWT + the scope's check
    R-->>S: 200 {the data}
    Note over S,R: ... many calls, ONE token's acquisition ...
    Note over S: the token's expiry approaches
    S->>A: POST /token (a refresh, before the expiry)
    A-->>S: 200 {a new access_token}
```

### 4.2 The refresh's rotation (the family's lifecycle)

```text
  login (the family's birth)
     │
     ▼
  ┌──────┐   refresh    ┌──────┐   refresh    ┌──────┐
  │  R1  │─────────────▶│  R2  │─────────────▶│  R3  │
  └──────┘              └──────┘              └──────┘
   revoked               revoked               ACTIVE
      ▲                     ▲
      │                     │
      └─────────────────────┘
   the reuse's detection → revoke the WHOLE family (F1)
   (the attacker or the network's race presented R1/R2 again)
```

### 4.3 The service's identity's options

```text
  ┌─────────────────────────────────────────────────────────────────┐
  │  OPTION A: OAuth2's client credentials                          │
  │  the service → the AS (with its secret) → a token → the API     │
  │  ✅ standard, works across the trust's boundaries               │
  │  ❌ a static's secret to rotate, the blast's radius = the secret │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │  OPTION B: the workload's identity (mTLS/SPIRE, the cloud's IAM) │
  │  the service → the mesh (with its cert, auto-rotated) → the API │
  │  ✅ no's secret, the auto's rotation, the per-workload's identity│
  │  ❌ doesn't cross the organisation's boundary                   │
  │  ❌ the user's identity must be propagated separately            │
  └─────────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────────┐
  │  OPTION C: the hybrid (the modern's microservices)              │
  │  the workload's identity proves the SERVICE                      │
  │  + the gateway's downstream's claim carries the USER             │
  │  ✅ the best's isolation, the least's privilege                 │
  └─────────────────────────────────────────────────────────────────┘
```

### 4.4 The token's lifecycle (the states & the client's behaviour)

```text
        ┌──────────┐
        │ ISSUED   │
        └────┬─────┘
             ▼
        ┌──────────┐  401 TOKEN_EXPIRED   ┌──────────┐
        │  ACTIVE  │─────────────────────▶│ EXPIRED  │──▶ (refresh or re-auth)
        └────┬─────┘                      └──────────┘
             │
             ├──▶ 401 TOKEN_REVOKED ──▶ re-authenticate
             ├──▶ 401 TOKEN_REUSED  ──▶ re-authenticate + ALERT (the theft)
             └──▶ 403 FORBIDDEN     ──▶ never retry (the step-up's consent)
```

---

## 5. Worked Examples

### 5.1 The client credentials (the dry run)

```text
  POST https://as.example.com/token
    Authorization: Basic c3ZjLXJlcG9ydGluZzpzZWNyZXQtdjI=
       (base64("svc-reporting:secret-v2"))
    Content-Type: application/x-www-form-urlencoded

    grant_type=client_credentials&scope=orders:read reports:write

  → 200 {
      "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
      "token_type": "Bearer",
      "expires_in": 3600,
      "scope": "orders:read reports:write"
    }

  the token's claims:
    {
      "iss": "https://as.example.com",
      "sub": "svc-reporting",              ← the SERVICE, not a user
      "aud": ["orders-api"],               ← the resource's indicator
      "scope": "orders:read reports:write",
      "azp": "svc-reporting",
      "gty": "client-credentials",         ← the grant's type
      "client_id": "svc-reporting",
      "iat": 1735689000,
      "exp": 1735692600,
      "jti": "c1d2e3f4-..."
    }

  ⚠ note: NO user, NO email, NO "sub" of a human
  ⚠ note: the scopes are the CLIENT's configuration, not a user's consent
```

### 5.2 The `private_key_jwt`'s client authentication (the dry run)

```text
  the CLIENT signs the assertion:
    header:  {"alg":"RS256","typ":"JWT","kid":"svc-reporting-key-1"}
    payload: {
      "iss": "svc-reporting",                        ← the client_id
      "sub": "svc-reporting",                        ← the client_id
      "aud": "https://as.example.com/token",         ← the token's endpoint
      "jti": "9f2a7c1e-4b8d-...",                    ← the unique id
      "iat": 1735689000,
      "exp": 1735689060                              ← 60 seconds only!
    }
    signature: RSA-SHA256 over the signing's input

  POST /token
    grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer
    &assertion=<the signed JWT>
    &scope=orders:read

  the AS:
    ① looks up the client's registered public key (by the kid)
    ② verifies the signature
    ③ checks: iss == sub == the client_id
    ④ checks: aud == the token's endpoint (the audience's binding!)
    ⑤ checks: exp (the clock's skew's tolerance)
    ⑥ checks: jti (not reused, within a short's window)
    → the client is authenticated ✅
```

### 5.3 The refresh's rotation (the dry run)

```text
  the DB's row before:
    { id: 91, client_id: "app-123", user_id: 42, token_hash: "aa11...",
      family_id: "F1", successor_hash: null, version: 1, revoked: false,
      expires_at: 2026-02-20 }

  ① POST /token  grant_type=refresh_token&refresh_token=R1_raw&client_id=app-123
  ② the hash: sha256(R1_raw) = "aa11..." → SELECT → the row
  ③ the checks: revoked=false ✅, not expired ✅
  ④ the ATOMIC's rotation:
       UPDATE refresh_tokens
          SET token_hash = "bb22...", successor_hash = "aa11...",
              revoked = true, version = 2
        WHERE id = 91 AND version = 1 AND revoked = false
       → 1 row ✅
  ⑤ INSERT { id: 92, token_hash: "bb22...", family_id: "F1", version: 1 }
  ⑥ 200 { access_token, refresh_token: R2_raw, expires_in: 300 }

  --- the attacker replays R1 ---
  ⑦ POST /token  refresh_token=R1_raw
  ⑧ SELECT hash(R1)="aa11..." → the row { revoked: true, successor_hash: "aa11...", family: F1 }
  ⑨ the grace's window? the successor was created < 60s ago
     → the server returns the SAME R2 (idempotent) ✅ (the network's race)
     → if > 60s ago → THE REUSE → revoke the family F1 🚨
```

### 5.4 The token's exchange (the dry run)

```text
  the client holds:
    { "aud": ["api-a", "api-b"], "scope": "read write admin" }  ← too broad

  POST /token
    grant_type=urn:ietf:params:oauth:grant-type:token-exchange
    &subject_token=<the broad's token>
    &subject_token_type=urn:ietf:params:oauth:token-type:access_token
    &audience=https://api-b.example.com
    &scope=orders:read
    &requested_token_type=urn:ietf:params:oauth:token-type:access_token

  the AS's validation:
    · the subject's token is valid?           ✅
    · the requested's scope ⊆ the subject's?  ✅ ("orders:read" ⊆ "read write admin")
    · the requested's audience is allowed?    ✅ (the policy allows api-b)
    · the subject's issuer is allowed to exchange for api-b?  ✅

  → 200 {
      "access_token": "<aud: api-b only, scope: orders:read only, exp: +300s>",
      "issued_token_type": "urn:ietf:params:oauth:token-type:access_token",
      "token_type": "Bearer",
      "expires_in": 300,
      "scope": "orders:read"
    }

  ✅ the downstream's API (api-b) can now only do what it needs
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The `OAuth2AuthorizedClientManager` (the token's caching — the critical's piece)

```java
package com.example.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.*;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

@Configuration
@RequiredArgsConstructor
public class OAuth2ClientConfig {

    private final ClientRegistrationRepository clientRegistrationRepository;

    /**
     * The MANAGER: it caches the authorized's client (the token) in memory and
     * re-acquires it ONLY when it's expired (or about to expire).
     *
     * ⚠ WITHOUT the OAuth2AuthorizedClientManager, a naive implementation calls the
     *   token's endpoint on EVERY API's call — doubling the requests and adding the
     *   latency. This is the #1 bug in the M2M's implementations.
     */
    @Bean
    public OAuth2AuthorizedClientManager authorizedClientManager() {
        OAuth2AuthorizedClientProvider provider = OAuth2AuthorizedClientProviderBuilder.builder()
                .authorizationCode()      // the code's flow
                .refreshToken()           // the refresh's grant
                .clientCredentials()      // the M2M's grant
                .build();

        // the provider's defaults: re-authorize when the token is expired,
        // and refresh when it's close to the expiry
        DefaultOAuth2AuthorizedClientManager manager =
                new DefaultOAuth2AuthorizedClientManager(clientRegistrationRepository, provider);

        // the token's expiry's skew — refresh 30s before the expiry, not after
        manager.setAuthorizedClientReuseInterval(Duration.ofSeconds(30));

        return manager;
    }
}
```

### 6.2 The M2M's client (the correct's implementation)

```java
package com.example.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.oauth2.client.*;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

@Component
@RequiredArgsConstructor
public class OrdersApiClient {

    private final OAuth2AuthorizedClientManager authorizedClientManager;
    private final RestClient restClient;

    /**
     * The M2M's call — the service authenticates ITSELF (no user).
     */
    public OrdersResponse fetchOrders(String customerId) {
        // ① the principal: a synthetic's identity (the "the service" is the principal)
        Authentication principal = new AnonymousAuthenticationToken(
                "service-account", "service-account",
                AuthorityUtils.createAuthorityList("ROLE_SERVICE"));

        // ② the token's request (the scope's request)
        OAuth2AuthorizeRequest authorizeRequest = OAuth2AuthorizeRequest
                .withClientRegistrationId("orders-api-client")
                .principal(principal)
                .attributes(attrs -> attrs.put("scope", "orders:read"))
                .build();

        // ③ the manager handles the caching + the refresh
        OAuth2AuthorizedClient authorizedClient =
                authorizedClientManager.authorize(authorizeRequest);

        OAuth2AccessToken token = authorizedClient.getAccessToken();
        if (token == null || token.getExpiresAt() == null
                || token.getExpiresAt().isBefore(java.time.Instant.now())) {
            throw new IllegalStateException("The access token could not be obtained");
        }

        // ④ the call
        return restClient.get()
                .uri("https://orders-api.example.com/api/orders/{id}", customerId)
                .header("Authorization", "Bearer " + token.getTokenValue())
                .retrieve()
                .body(OrdersResponse.class);
    }
}
```

### 6.3 The `private_key_jwt`'s client (the configuration)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          orders-api-client:
            client-id: svc-reporting
            client-authentication-method: private_key_jwt
            authorization-grant-type: client_credentials
            scope: [orders:read]
            # the client's key (the PKCS#8's PEM) — from the secret's store
            private-key: ${CLIENT_PRIVATE_KEY}
            # the AS's needs the client's PUBLIC key (registered out of band)
        provider:
          keycloak:
            issuer-uri: https://as.example.com/realms/prod
```

```java
@Configuration
public class PrivateKeyJwtConfig {

    /**
     * The client's assertion's supplier — the Spring's builds the JWT's assertion
     * from the registration's private key + the client id.
     */
    @Bean
    public OAuth2AccessTokenResponseClient<...> { /* the default's is fine */ }
}
```

### 6.4 The client-side refresh's rotation (the reuse's detection — the code you write)

```java
package com.example.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RotatingRefreshTokenService {

    private final RefreshTokenRepository repository;
    private final Clock clock;
    private final Duration graceWindow = Duration.ofSeconds(60);

    /**
     * The ATOMIC's rotation with the reuse's detection.
     * The whole's point: an already-used token → the family's revocation.
     */
    @Transactional
    public TokenPair rotate(String rawRefreshToken) {
        String hash = sha256(rawRefreshToken);
        Instant now = clock.instant();

        RefreshToken existing = repository.findByTokenHash(hash)
                .orElseThrow(() -> new InvalidRefreshTokenException());

        // ① the GRACE's window — the recently rotated's token returns the same successor
        if (existing.isRevoked()) {
            Optional<String> successor = repository.findBySuccessorHash(hash);
            if (successor.isPresent() && withinGraceWindow(existing)) {
                // the network's race — the idempotent's response
                return buildPair(existing.getUserId(), successor.get());
            }
            // ② ⚠ THE REUSE'S DETECTION — the whole family dies
            repository.revokeFamily(existing.getFamilyId(), "REUSE_DETECTED");
            securityEventPublisher.publishRefreshReuseDetected(existing);
            throw new RefreshTokenReuseDetectedException();
        }

        if (existing.getExpiresAt().isBefore(now)) {
            throw new InvalidRefreshTokenException("The refresh token has expired");
        }

        // ③ the rotation
        String newRaw = generateRefreshToken();
        boolean rotated = repository.rotate(existing.getId(), existing.getVersion(),
                sha256(newRaw), hash, now.plus(graceWindow),
                now.plus(Duration.ofDays(30)));

        if (!rotated) {
            // the concurrent's rotation won → the reuse's detection
            repository.revokeFamily(existing.getFamilyId(), "REUSE_DETECTED");
            throw new RefreshTokenReuseDetectedException();
        }
        return buildPair(existing.getUserId(), newRaw);
    }

    private boolean withinGraceWindow(RefreshToken token) {
        return token.getRevokedAt() != null
                && token.getRevokedAt().isAfter(clock.instant().minus(graceWindow));
    }
}
```

### 6.5 The token's exchange (the client-side)

```java
package com.example.integration;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class TokenExchangeClient {

    private final RestClient restClient;

    /**
     * The TOKEN'S EXCHANGE (RFC 8693) — the trade a broad's token for a narrow's one.
     * The least's privilege at the token's level.
     */
    public String exchangeForAudience(String subjectToken, String audience, String scope) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange");
        form.add("subject_token", subjectToken);
        form.add("subject_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("audience", audience);
        form.add("scope", scope);
        form.add("requested_token_type", "urn:ietf:params:oauth:token-type:access_token");
        form.add("client_id", "svc-broker");
        form.add("client_secret", clientSecret);

        @SuppressWarnings("unchecked")
        Map<String, Object> response = restClient.post()
                .uri("https://as.example.com/token")
                .body(form)
                .retrieve()
                .body(Map.class);

        return (String) response.get("access_token");
    }
}
```

### 6.6 The device's flow (the polling's client)

```java
package com.example.device;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class DeviceAuthorizationClient {

    private final RestClient restClient;

    public TokenPair pollForToken(String deviceCode, String clientId) {
        long interval = 5;   // the AS's suggested interval
        long deadline = System.currentTimeMillis() + Duration.ofMinutes(15).toMillis();

        while (System.currentTimeMillis() < deadline) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "urn:ietf:params:oauth:grant-type:device_code");
            form.add("device_code", deviceCode);
            form.add("client_id", clientId);

            try {
                Map<String, Object> response = restClient.post()
                        .uri("https://as.example.com/token")
                        .body(form)
                        .retrieve()
                        .body(Map.class);
                return TokenPair.from(response);
            } catch (RestClientResponseException ex) {
                String error = extractError(ex);
                switch (error) {
                    case "authorization_pending" -> sleep(interval);
                    case "slow_down" -> { interval += 5; sleep(interval); }
                    case "access_denied" -> throw new DeviceAuthorizationDeniedException();
                    case "expired_token" -> throw new DeviceCodeExpiredException();
                    default -> throw new OAuth2TokenException(error);
                }
            }
        }
        throw new DeviceCodeExpiredException();
    }
}
```

### 6.7 The resource server's scope's enforcement (the defence in depth)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class ResourceServerConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/orders/**").hasAuthority("SCOPE_orders:read")
                .requestMatchers("/api/reports/**").hasAuthority("SCOPE_reports:write")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o
                .jwt(jwt -> jwt
                        .decoder(jwtDecoder())
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation("https://as.example.com/realms/prod")
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://as.example.com/realms/prod"),
                new AudienceValidator("orders-api")));
        return decoder;
    }

    /**
     * The M2M's and the user's tokens are BOTH accepted, but they're distinguished
     * by the sub / the gty. The API's code must handle both.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            String scope = jwt.getClaimAsString("scope");
            if (scope != null) {
                Arrays.stream(scope.split(" "))
                        .filter(StringUtils::hasText)
                        .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                        .forEach(authorities::add);
            }
            return authorities;
        });
        return converter;
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The token's endpoint called on every API's call | The `OAuth2AuthorizedClientManager`'s caching | The latency, the rate's limit |
| 2 | The client credentials used for the user-facing's flows | The authorization code | No consent, no scope's limitation |
| 3 | The password grant for a machine's flow | The client credentials | The app stores the user's password |
| 4 | A shared's secret for 200 services | The `private_key_jwt` / the workload's identity | The rotation, the blast's radius |
| 5 | A long's `exp` in the `private_key_jwt`'s assertion | 60 seconds | The assertion is a bearer credential |
| 6 | The refresh token stored in the plain | Hashed | The DB's leak |
| 7 | No rotation on the refresh | The rotation + the reuse's detection | The theft's undetected |
| 8 | The rotation with no grace's window | The 30-60s's grace | The network's race → the mass's logout |
| 9 | The `offline_access` missing | Request it (the OIDC) | No refresh token is issued |
| 10 | The `scope` in the refresh's request widening the grant | The subset only | The RFC's rule |
| 11 | The token's exchange widening the scope | The subset only | The privilege's escalation |
| 12 | The exchange's audience not validated by the AS | The policy | The cross-service's reuse |
| 13 | The service's account with the admin's scopes | The narrowest's scope | The blast's radius |
| 14 | The same's token used by the user and the service | The `sub`/`gty`'s distinction | The logic's confusion |
| 15 | The device's flow polling too fast | The `interval` + the `slow_down` | The AS's rate's limit |
| 16 | OAuth2 between your own microservices (by default) | The workload's identity | Unnecessary's complexity |
| 17 | The 401's refresh loop (no's single-flight) | The single-flight | The token's endpoint's overload |
| 18 | The `aud` not validated in the resource server | The audience's validator | The cross-service's reuse |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is the client credentials grant for?**
**A:** For machine-to-machine access — a service (or a CLI, or a cron job) authenticating
itself to get an access token for an API, with no user involved. The client presents its
own credentials (a secret or a key) and receives an access token scoped to what its
registration allows.

**Q: What's the difference between the client credentials and the authorization code?**
**A:** The authorization code involves a user who authenticates and consents (the code flow
in the browser). The client credentials has no user — the client is the identity, and the
`sub` in the token is the client's id.

### 🟡 Intermediate
**Q: How do your microservices authenticate to each other?**
**A:** With the client credentials grant against the authorization server, with the
`private_key_jwt` (or a strong secret) for the client's authentication, and the token
cached in memory and refreshed before it expires. The scopes are narrow and the token's TTL
is short (5-15 min).

**Q: What is the `private_key_jwt` client authentication?**
**A:** Instead of a shared secret, the client signs a short-lived JWT assertion with its
own private key (RFC 7523). The AS verifies it with the client's registered public key.
The assertion contains the client id as both the `iss` and the `sub`, the token endpoint
as the `aud`, a unique `jti`, and a 60-second expiry. The benefit: no shared secret to
distribute or rotate — the rotation is just a key's publish.

**Q: How does the refresh token flow work?**
**A:** The client posts the refresh token to the token endpoint with the
`grant_type=refresh_token`. The AS validates it and issues a new access token (and, with
the rotation, a new refresh token, invalidating the old one). The modern's best practice
is the rotation with the reuse's detection: if an already-used refresh token is presented,
the entire token family is revoked and the user is forced to re-authenticate.

### 🔴 Advanced / Senior
**Q: Should you use OAuth2 between your own microservices?**
**A:** Usually not for the internal's calls. The workload's identity (mTLS with SPIFFE, or
the cloud's workload identity) proves the calling service cryptographically with
automatically rotated, short-lived credentials and no secret to store. The user's identity
is propagated separately as a downstream claim from the gateway. OAuth2's client
credentials is for the **cross-trust-boundary** cases: a partner's API, a customer's
integration, a legacy's system that can't do mTLS. The honest framing: OAuth2 in the
internal's mesh often adds a runtime's dependency on the AS for no's security's gain.

**Q: How do you implement the refresh token rotation with the reuse detection?**
**A:** Store the refresh tokens hashed, with a `family_id` (the lineage) and a `version`.
The rotation is a single atomic update: `UPDATE ... SET token_hash = <new>,
successor_hash = <old>, revoked = true, version = version + 1 WHERE id = ? AND version =
<expected> AND revoked = false` — only one concurrent rotation succeeds. If an
already-revoked token is presented, check whether it's within the short grace window (the
network's race → return the same successor, idempotently); if not, it's a genuine reuse →
revoke the whole family and alert.

**Q: What is the token exchange and when would you use it?**
**A:** RFC 8693 — the client trades a token for another token, typically a narrower one.
The main use is the least privilege at the token level: a service holding a broad token
exchanges it for one scoped to a single audience and a single scope when calling a specific
API, so a compromise of that service yields only the narrow token. The rules: the requested
scope must be a subset of the subject's, the audience must be explicitly allowed, and the
resulting token is short-lived.

### ⚫ Expert / Principal
**Q: Design the service-to-service's authentication for a platform with 200 services, 50
external's partners, and 3 cloud's environments.**
> (1) The **the trust's zones** (the internal's mesh → the workload's identity; the
cloud → the cloud's IAM; the cross-organisation → OAuth2's client credentials; the
legacy → the API's key's gateway); (2) the **the identity's model** (the SPIFFE's IDs,
the OAuth2's clients, the mapping); (3) the **the token's design** (the workload's
certificate, the downstream's claim, the partner's JWT's access with the per-partner's
audience); (4) the **the credential's lifecycle** (the automatic's rotation, the KMS, the
revocation); (5) the **the authorisation's model** (the scopes, the policy's engine, the
exchange's narrowing); (6) the **the observability & the audit** (the per-identity's usage,
the anomaly's detection); (7) the **the migration's path** (the shared's secrets → the
workload's identity).

---

## 9. Counter-Questions & Traps

### CQ1. "What's the difference between the client credentials and the API key for
service-to-service calls?"
- **Naive answer:** "The client credentials are more secure."  ← the trap
- **The trap:** The interviewer wants the mechanism's difference.
- **Expert answer:** An API key is a **static, long-lived, opaque bearer credential** — the
same string every time, with no audience, no scope, no expiry, and no identity's
attributes. The client credentials is a **protocol** that turns a credential into a
**scoped, time-limited, audience-bound token** with a *standardised* meaning. The
differences that matter: (1) **the expiry** — the key lives forever until rotated, the
token lives minutes; (2) **the scoping** — the key is all-or-nothing, the token carries
the scopes; (3) **the audience** — the key works on any's API that accepts it, the token
is bound to one; (4) **the audit** — with a key you can't tell *which* service used it
unless you give each one a different key; with a token you get a `sub`, a `jti`, an
`azp`; (5) **the standardisation** — the client credentials is understood by every AS, so
the service's code is portable. But the honest caveat: **the client credentials' token
is only as good as the credential behind it** — if the client secret is a static string in
a config file, you've just traded a static API key for a static API key with extra
steps. The real's gain comes from the **asymmetric** client authentication
(`private_key_jwt`, mTLS) or the **short-lived** credential (the workload's identity),
where the credential itself rotates automatically. And the counter-argument for the API
key: for a **single, simple, internal** integration, an API key is simpler and its
weaknesses (the expiry, the scoping) may not matter. The framing: *"the difference isn't
the protocol, it's the credential's lifecycle — the client credentials lets you get
short-lived, scoped, audienced tokens from a credential that never expires, but only if
the credential itself is strong. A static secret behind the client credentials is just a
fancier API key."*

### CQ2. "Why is the token's exchange needed? Isn't the narrow scope enough?"
- **Naive answer:** "For the flexibility."  ← the trap
- **The trap:** The interviewer wants the specific's scenarios.
- **Expert answer:** The narrow scope at the **issuance** is a *coarse* control — it says
"this token may read orders", but it doesn't say "**for this audience**" and it doesn't
let you narrow *at the moment of use*. The exchange solves three real's problems:
(1) **The audience's constraint at the call's site** — a service that holds one broad token
for "the whole platform" can exchange it for a token scoped to *just* the API it's about
to call, with *just* the scope it needs. That means a compromise of that service (or a bug
in it) yields a token that can only do one narrow thing, and only at one API — the
difference between "the attacker has the platform" and "the attacker has read access to
the orders API". (2) **The delegation's narrowing** — the "the act on the user's behalf"
pattern: the service exchanges the user's token for a narrower one, so the downstream
sees only what the service was given, not the user's full's scope. (3) **The
cross-environment's federation** — the cloud's providers use the exchange to trade a
workload's identity for a cloud's STS token (the AWS's, GCP's, Azure's), which is a
de-facto standard. The subtlety to raise: the exchange is **not a free pass** — the AS
must enforce that the requested scope is a **subset** of the subject's and that the
requested audience is **allowed** by the policy; and the resulting token is itself
short-lived and auditable. The framing: *"the narrow scope at the issuance is the
'what' of the permission; the exchange adds the 'where' and the 'when' — it lets the
permission be narrowed at the moment of use, per call site, which is the difference
between a service that holds a broad token and one that only holds what it's about to
use."*

### CQ3. "Why does the refresh token exist at all? The client could just get a new access
token with its credentials."
- **Naive answer:** "The user doesn't have credentials."  ← the trap
- **The trap:** The interviewer wants the full's reason including the M2M's contrast.
- **Expert answer:** For the **user-facing's client** (the SPA, the mobile), there's no
credential the client can present — the user is the identity, and the user shouldn't be
re-prompted every 5 minutes. The refresh token is the **server-side evidence of a
completed, consented authorization**, so the client can mint a new access token without the
user. For the **M2M's client**, the client *does* have a credential, so a refresh token
is redundant — it can just get a new access token with its credentials. That's exactly why
the client credentials' grant issues no refresh token. The deeper's point: the refresh
token's value is that it carries a **history of a consent** — the AS can check the granted
scopes, the revocation's state, and the family, and it doesn't need the user's password or
a re-consent. And the reason the refresh token is **opaque and stateful** (rather than a
JWT) is the same reason as the whole's design: it must be **revocable** and its **reuse
must be detectable** — a JWT can't be revoked, and a stolen JWT's refresh token is
undetectable. So the refresh token is the "the consent's handle" — short-lived access
to the resource, revocable, and rotatable. The framing: *"the refresh token is what
makes the user's consent durable without the user's credentials — and the reason it's
opaque is that it has to be revocable, which a self-contained JWT can't be."*

### CQ4. "The rotation's reuse detection can log out a legitimate user if their network
retries the refresh. How do you handle that?"
- **Naive answer:** "Don't implement the rotation."  ← the trap
- **The trap:** The interviewer wants the reconciliation.
- **Expert answer:** This is a real's tension and the resolution is the **grace window**
plus the **client's single-flight**. The rotation's reuse detection is a **security**
control — it's the only thing that turns a silent theft into a detected one — so removing
it is the wrong trade. Instead: (1) **the grace window** (30-60s) — when an
already-revoked token is presented, check whether it was revoked *within* the window and
whether its `successor_hash` is known; if so, return the **same successor** (idempotent)
rather than treating it as a theft. After the window, a presentation of the old token is
genuine. (2) **The client's single-flight** — all the concurrent 401s share one refresh
promise, so the client never fires two refreshes; and the cross-tab's coordination (the
refresh token in an httpOnly's cookie, or a `BroadcastChannel`) handles the multi-tab
case. (3) **The server's idempotency** — some ASes (Keycloak has this) will re-return
the same tokens for a repeat's exchange of the same code within a window. The layering:
the **client reduces the race**, the **server tolerates the residual race** (the grace
window), and the **server catches the real theft**. And the honest's framing: *"the
rotation's detection and the spurious logouts are not opposed — they're the same
mechanism, and the grace window is the reconciliation. The pure's detection would log
out users on a flaky's network; no detection at all means a stolen's refresh token is
invisible for 30 days. The window says: 'a reuse within a minute is a network's race, a
reuse later is a theft'. Everything else — the single-flight, the tab's coordination — is
about never creating the race in the first place."*

### CQ5. "Why not use OAuth2's client credentials between all your microservices instead of
building a service mesh?"
- **Naive answer:** "The client credentials is simpler."  ← the trap
- **The trap:** The interviewer wants the trade-off's articulation.
- **Expert answer:** The client credentials' approach puts the **authorization server on
the critical path** of every inter-service call, and it forces a **shared's secret's
distribution** problem (or the workload's identity, which is the same as the mesh). The
mesh (mTLS with SPIFFE) moves the identity's proof into the **transport** — every
connection is mutually authenticated with an automatically rotated, short-lived certificate,
and the service's identity is a **cryptographic fact**, not a token from a third party.
That gives you: no runtime's dependency on the AS (the calls work when the AS is down), no
secret to distribute, the automatic's rotation, and the per-workload's identity (a
compromised pod gets exactly its own identity). What the mesh **doesn't** give you is
**authorization** — the mesh answers "who is calling", not "what may they do" — and it
doesn't carry the **user's** identity (that comes from the gateway as a downstream's
claim). So the modern's answer is the **hybrid**: the mesh for the service's identity, the
gateway's downstream's claim for the user's identity and the coarse's scope, and OAuth2's
client credentials only where you cross an organisational boundary (a partner, a customer,
a legacy). The framing: *"OAuth2 answers 'may this client do this' — but between your own
services, the question 'who is calling' is better answered by the transport (mutual
TLS with an automatically rotated identity), because it removes a runtime's dependency
and a secret's distribution problem. Use OAuth2 where the trust actually ends — at the
partner's boundary."*

---

## 10. Rapid-Fire MCQs

**1.** The client credentials' grant involves:
A) A user's browser  B) No user  C) A username + password  D) An MFA
**✅ B**

**2.** In a client credentials' token, the `sub` is:
A) The user  B) The client  C) The resource server  D) The scope
**✅ B**

**3.** The client credentials' grant issues a refresh token:
A) Always  B) Never  C) Only with the `offline_access`  D) Only for the confidential's clients
**✅ B**

**4.** The strongest's client authentication:
A) `client_secret_post`  B) `client_secret_basic`  C) `private_key_jwt` / mTLS  D) `none`
**✅ C**

**5.** The `private_key_jwt`'s assertion's `exp` should be:
A) 1 hour  B) 24 hours  C) ~60 seconds  D) The same as the access's TTL
**✅ C**

**6.** The `private_key_jwt`'s assertion's `aud` must be:
A) The client's id  B) The token's endpoint  C) The resource's API  D) The user's id
**✅ B**

**7.** The refresh's rotation means:
A) The token is reused  B) The token is single-use and replaced  C) The TTL is extended  D) The scopes are widened
**✅ B**

**8.** On the refresh's reuse's detection:
A) The token is extended  B) The family is revoked  C) A new's token is issued  D) Nothing happens
**✅ B**

**9.** The grace's window's purpose:
A) The performance  B) Tolerating the network's duplicate's requests  C) The encryption  D) The expiry
**✅ B**

**10.** The `offline_access` scope is required to:
A) Get the ID token  B) Get the refresh token  C) Get the userinfo  D) Use the PKCE
**✅ B**

**11.** The token's exchange (RFC 8693) must never:
A) Shorten the token's TTL  B) Widen the scope or the audience  C) Require the PKCE  D) Be used by a confidential's client
**✅ B**

**12.** The resource's indicator (RFC 8707) specifies:
A) The scopes  B) The target's resource server  C) The redirect's URI  D) The client's secret
**✅ B**

**13.** The device's flow's polling's error that means "keep waiting":
A) `access_denied`  B) `authorization_pending`  C) `expired_token`  D) `invalid_grant`
**✅ B**

**14.** The token's caching's purpose in the M2M:
A) The security  B) Avoid a token's request per API's call  C) The compliance  D) The encryption
**✅ B**

**15.** The workload's identity (SPIFFE/mTLS) is the better's choice for:
A) The partner's API  B) The internal's services  C) The user's login  D) The legacy's integration
**✅ B**

**16.** The M2M's token's TTL should be:
A) 24 hours  B) 5-15 minutes  C) 30 days  D) Forever
**✅ B**

**17.** The `gty` claim indicates:
A) The token's type  B) The grant's type  C) The audience  D) The issuer
**✅ B**

**18.** The refresh's request's `scope` parameter can:
A) Widen the grant  B) Narrow the grant  C) Change the audience  D) Reset the expiry
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The client credentials, end to end**
1. Register a client with the `client_credentials` grant.
2. Get a token with `curl`. Decode it — confirm the `sub` is the client and there's no user.
3. Call the API with it.

**🧪 Lab 2 — The token's caching's proof**
1. Call the API 10 times with a naive's implementation (a token's request each time).
2. Switch to the `OAuth2AuthorizedClientManager`.
3. Count the `/token` calls: 10 → 1.

**🧪 Lab 3 — The rotation's reuse's detection**
1. Refresh once. Note the new token.
2. Present the old one within the grace window → the same successor, no error.
3. Wait past the window, present it again → the family is revoked.

**🧪 Lab 4 — The `private_key_jwt`**
1. Generate an RSA key pair. Register the public key at the AS.
2. Configure the client with the private key.
3. Verify the assertion's `iss`/`sub`/`aud`/`exp`/`jti` in the AS's logs.

**🧪 Lab 5 — The token's exchange**
1. Get a broad token (`aud: [a, b]`, `scope: read write`).
2. Exchange it for a narrow one (`aud: api-b`, `scope: orders:read`).
3. Assert the narrow token cannot be used on api-a.

---

## 12. Code Challenges

### Challenge 1 — The M2M's client library (medium-hard)
**Goal:** A reusable client with the token's caching, the automatic's refresh before the
expiry, the scope's request, the retry's discipline (a 401 → refresh once → retry), and the
metrics.
**Requirements:** the concurrency's safety (a single's refresh for many threads), the
timeouts, the circuit breaker, the tests.

### Challenge 2 — The refresh's rotation's service (hard)
**Goal:** The atomic's rotation, the grace's window, the reuse's detection, the family's
revocation, the audit, the alert.
**Requirements:** the transactions, the concurrency's tests, the metrics, the runbook.

### Challenge 3 — The token's exchange's broker (hard)
**Goal:** A service that holds a broad token and exchanges it for narrow ones per
downstream, with the policy, the caching, and the audit.
**Requirements:** the scope's subset's validation, the audience's allowlist, the caching
per (audience, scope), the tests.

---

## 13. Scenario-Based Questions

**S1.** *"Our 200 microservices each hold a client secret. The security team says a leaked
secret would let the attacker access everything that service can access. What's the
modern's fix?"**
>
> **Ideal answer:** (1) **The framing:** the problem isn't the OAuth2 — it's that 200
long-lived's secrets are 200 single points of compromise, and the AS's "the service's
identity" is a shared's password. (2) **The immediate's mitigation (this week):** (a) the
**scope's audit** — most of the 200 clients were provisioned with the blanket's scope on
day one and never narrowed; measure the **actual's usage** (the AS's token's logs) and
re-register each client with the narrowest's scope. This is the highest-leverage, zero-cost
change. (b) the **short's access token's TTL** — from 24h to 5-15 min, so a leaked
secret's exposure is bounded by the token's lifetime... no, by the *secret's* lifetime,
because the secret can mint tokens forever. So the secret's rotation is what matters —
but the *access* token's TTL still matters for the *stolen token* case. (c) the **the
monitoring** — the per-client's usage's baseline (the rate, the scopes, the source's IPs);
an alert on a client using a **new scope** or from a **new source** is the leak's
detection. (3) **The structural's fix — the workload's identity:** the 200 services run
in Kubernetes (say), so the platform can give each pod a **short-lived, automatically
rotated identity** with no secret at all:
- **SPIFFE/SPIRE** — each workload gets a **SVID** (a short-lived X.509 certificate or
a JWT's bundle) with a stable's identity (the SPIFFE's ID, e.g.
`spiffe://prod.example.com/ns/default/sa/orders-service`). The rotation is automatic
(the default's TTL: 1 hour, with a renewal's process) and the identity is per-workload.
- **The cloud's workload's identity** — AWS's **IRSA** (the pod's service account → the
IAM's role via the OIDC's federation), GCP's **Workload Identity Federation**, Azure's
**Managed Identity**. Zero secrets, automatic's rotation, the per-pod's identity.
The AS (or the mesh's gateway) trusts the platform's attestation and issues a token. The
blast's radius of a compromised pod becomes exactly that pod's identity, and the
credentials rotate without anyone doing anything. (4) **The intermediate's step if the
platform doesn't support it: `private_key_jwt`** — each client gets its own key pair; the
AS trusts the public key. The rotation is a key's publish (the AS's trust's anchor
updates), not a secret's swap across 200 systems, and the assertion is bound to the
`aud` and single-use (the `jti`), so a captured assertion is useless. (5) **The design's
decision to make explicit: does the internal's traffic need OAuth2 at all?** If the
services are all in your trust's domain, the answer is usually **the mesh's identity for
"who is calling" + the gateway's downstream's claim for "which user"**. OAuth2's client
credentials earns its keep at the **boundary**: a partner's API, a customer's integration,
a legacy's system that can't do mTLS. (6) **The migration's plan:** (a) the scope's
audit + the narrowing (week 1-2); (b) the secret's rotation automation (the zero-
downtime's pattern: publish the new secret, the services pick it up, revoke the old —
the AS's "the secret's list" or a scheduled's overlap); (c) the pilot of the workload's
identity for 10 services on one K8s's cluster; (d) the expansion; (e) the retirement of
the shared's secrets. (7) The senior framing: "**the number of static's secrets is the
problem.** 200 secrets is 200 things to store, rotate, audit, and eventually leak. The
**scope's audit** and the **short's token's TTL** are the two changes I'd make in the
first week — they're free and they shrink the blast's radius by orders of magnitude. The
structural's answer is the **workload's identity**: the credential becomes a platform's
attestation instead of a stored's password, it's short-lived and auto-rotated, and a
compromised pod gets exactly its own identity. The `private_key_jwt` is the intermediate's
step that doesn't need the platform's support. And the question I'd force in the design
review: **do these 200 services actually need OAuth2 with each other?** If they're all
in your trust's domain, the mesh's identity (mutual TLS, auto-rotated) plus the
gateway's downstream's claim is simpler, has no runtime's dependency on the AS, and gives
the same or better's guarantee. Use OAuth2 where the trust actually ends."

**S2.** *"A partner needs to call our API. They only support the client credentials grant
and they want a static API key. How do we make this production-grade?"**
>
> **Ideal answer:** (1) **The framing:** the partner is a **confidential's client** in
your AS (or a proxy in front of their static-key's API). The goal is to **never** let a
long-lived's static key touch your services — it lives in exactly one place (the adapter)
and your services use short-lived, scoped tokens. (2) **The architecture:**
```
  the PARTNER's service ──[the static's API key]──▶ the ADAPTER (your side)
  the ADAPTER ──[the client credentials → a short's JWT]──▶ the AS
  the ADAPTER ──[the short's JWT]──▶ YOUR APIs
```
  The adapter is a thin, well-defined's component: it holds the static key (in the
secret's store), it acquires the token with the client credentials, it caches it, and it
re-attaches it to the outgoing's calls. Your services never see the static key. (3) **The
adapter's requirements:** (a) the **token's caching** (the `OAuth2AuthorizedClientManager`
or an equivalent) — the token's endpoint is called **once per TTL**, not per request;
(b) the **scope's minimization** — request the narrowest's scope the partner's API needs
(`orders:read`, not `orders:*`); if the partner's scopes are coarse, request the least
and rely on the adapter's own authorization (the adapter is the enforcement's point);
(c) the **refresh before the expiry** (with the skew); (d) the **the 401 vs 403's
distinction** — a 401 from your API means the token's expired → refresh and retry **once**;
a 403 means the scope is wrong → **never** retry, and alert (it's a configuration's
problem); (e) the **timeouts and the circuit breaker** — the partner's latency is your
latency, and their outage must not exhaust your thread's pool; (f) the **the observability**
— the token's acquisition's latency and error's rate, the 401/403's distribution, the
partner's quota's usage; (g) the **the idempotency** — the adapter's token's acquisition
must be single-flight (10 concurrent's calls → 1 token's request). (4) **The static key's
own hygiene (it's the highest-value's credential):** it lives **only** in the secret's
manager (the Vault, the AWS's Secrets Manager, the K8s's sealed's secrets) — never in the
code, the config's file, the image, or the git; it's **rotated** on a schedule and on
suspicion; the **HTTP client's logging must redact it**; it must be **scoped** on the
partner's side (the partner's API's key's permissions and quota). (5) **The migration's
path — push them to the proper's OAuth2:** (a) the AS's client credentials is the
standard's answer and it's a small's change for them (they may already have an AS with
the M2M's support); (b) if they truly can't, the **dynamic's credentials** (the AWS's
IAM's instance profiles, the Vault's dynamic's secrets) — the key rotates automatically
and never exists statically; (c) the **the key's rotation's coordination** — the rotation
requires the partner's action, so it must be a **scheduled, rehearsed's process** (a
notice's period, a dual-key's overlap, a monitoring's check), not an emergency's scramble.
Put it in the **integration's runbook**. (6) **The governance's items to negotiate up
front:** the **rate's limits** (the quota's contract), the **the SLA's numbers** (the
latency's budget, the availability's commitment), the **the audit's logs** (both sides'
access's logs, for the incident's forensics), the **the data's handling** (the PII's
residency, the retention), and the **the authentication's requirements** (the mTLS's
client's certificates as an alternative to the static key, which is the strongest). (7)
The senior framing: "**the static key is a standing's credential, so the design's job is
to make sure it exists in exactly one place and for as short a time as possible.** The
adapter's pattern does that: the key is a vault's secret behind a controlled's boundary,
and everything above it uses a short-lived, scoped, per-audience token. The three
production's details that people skip: the **token's caching** (the naive's
implementation hits the token's endpoint on every call — the adapter must cache and
refresh before the expiry), the **401 vs 403's distinction** (a 403 is a
configuration's bug, not a transient's error, and retrying it just burns the partner's
quota), and the **key's rotation's rehearsal** (when the key's rotation comes, it comes
on a day you choose, not on the day a partner's engineer is on holiday — the dual-key's
overlap is the mechanism). And the strategic's push: the **client credentials is a small
change for them** and it's the standard's answer — the dynamic's credentials are the
better's one if the key's rotation is their's problem too."

---

## 14. Tricky Interview Questions

**T1. "Can a client use the client credentials grant to get a token for a specific user?"**
> No — that's the **impersonation** / the **token's exchange** territory. The client
credentials grant identifies the **client**, not a user. If a service needs to act on a
user's behalf, it either holds the **user's token** (from the delegated flow) and
**exchanges** it for a narrower one (RFC 8693), or the AS provides a specific
**impersonation's** grant (a Keycloak feature, an Azure's "the on-behalf-of" flow — OBO).
The security's reason: the client credentials' grant is a **high-trust, no-user**
assertion; letting it request a user's identity would let any registered service
impersonate any user, which is a catastrophic's escalation.

**T2. "Why doesn't the client credentials grant return a refresh token?"**
> Because the client already holds a credential it can use to get a new access token at
any time — the refresh token would be a strictly worse duplicate (a second, longer-lived
secret requiring an extra's step). The refresh token exists for the **user-facing**
client, which has **no** credential of its own.

**T3. "What's the difference between the `client_id` and the `azp` claim?"**
> The `client_id` is the **identifier** of the client (the registration's key). The `azp`
(the authorized party) is the client the token was **issued to** — meaningful when the
token has **multiple audiences** (the `azp` disambiguates which of them is the intended
recipient). The RFC 6749 also mentions `azp` for the authorization server's use. In
practice, for a single-audience's token, they're the same.

**T4. "Can the same service have two client registrations (e.g. for different APIs)?"**
> Yes, and it's often a good idea: separate registrations let you have separate's scopes,
separate's secrets, separate's rate's limits, and separate's audit trails. The cost: the
service holds more credentials. With the `private_key_jwt` or the workload's identity, the
"more registrations" cost is small (the same key, different the `audience`/`kid`).
Alternatively, one registration with the `resource` indicator for the per-API's audience.

**T5. "What happens if the authorization server is down when a service needs a token?"**
> If the service has a **cached, still-valid** token, the calls continue. Once the token
expires, the service can't call the API — the AS is on the critical path. That's the
**availability's argument** for the mesh's identity over OAuth2 in the internal's calls:
the mesh's mTLS doesn't depend on the AS. The mitigations for the OAuth2's design: the
token's TTL must be **long enough to tolerate a reasonable AS's outage** (a balance
against the security's exposure — 5-15 min is typical), the AS must be **HA**, and the
service must **fail gracefully** (the circuit breaker on the token's acquisition, so the
AS's outage doesn't exhaust the service's thread's pool with token's requests).

**T6. "How do you prevent a service from requesting a scope it doesn't need?"**
> The **registration's ceiling** — the scopes are configured per client, and the AS will
not issue a scope that isn't in the registration (regardless of what the request asks for).
Beyond that: the **scope's naming's discipline** (a granular's taxonomy, so a
"broad" scope doesn't exist), the **monitoring** (a client using a scope it never used
before is an alert), and the **the exchange's narrowing** for the fine-grained's
per-call control.

**T7. "Can you use the same access token for multiple APIs?"**
> Technically yes (if the `aud` covers them), but it's a security's decision you should
make deliberately. The **single's audience per API** (via the resource's indicator) gives
the least's privilege and prevents a compromise of API A from yielding a token for API B.
The multi-audience is convenient but increases the blast's radius. Note: the `aud` in the
token must be **validated by the resource server** — otherwise the audience's separation
is decorative.

**T8. "What's the difference between the `scope` and the `aud`?"**
> The **scope** says *what operation* ("read orders"). The **audience** says *at which
resource server* it's valid ("the orders API"). They're orthogonal: a token can have
`orders:read` and be valid for two audiences, or be valid for one audience with several
scopes. The practical's rule: **the scopes are the API's contract, the audience is the
API's identity** — and the API's code should depend on the scope (mapped to an internal
permission's code), never on the audience's string.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the client credentials) → *"How do you handle the case where a service
needs to call a partner's API and that partner has a different auth's scheme?"*
→ The **adapter's pattern** (above) — a thin's component per partner that translates the
auth's scheme. The rule: the adapter is the **only** place that knows the partner's
credential, and it's a small, well-tested's boundary. If the number of partners grows,
make it a **generic's gateway** (an "the API's gateway" that terminates the external's
auth and issues an internal's token) — but keep the credential's handling in one place.

**F2** (after the token's caching) → *"What if 50 threads all need a token at the same
time and the cache is empty?"**
→ The `OAuth2AuthorizedClientManager` is thread-safe and it handles the concurrent's
acquisition (it synchronises per authorized-client id). But if you hand-roll, you need
your own single-flight (a `CompletableFuture` cache or a lock) — otherwise you get a
thundering herd of 50 token's requests, which will hit the AS's rate's limit.

**F3** (after the rotation) → *"How do you test the reuse detection?"*
→ Integration's test with the real's DB (Testcontainers): (1) refresh and capture the new
token; (2) present the old one within the grace window → assert the same successor and
that the family is **not** revoked; (3) wait past the window (or manipulate the `revoked_at`
in the test) → present the old one → assert 401 **and** that **all** the family's rows are
revoked; (4) the concurrent's test: two threads refresh the same token → exactly one
succeeds.

**F4** (after the workload's identity) → *"What if our services run outside Kubernetes?"*
→ The **SPIFFE/SPIRE** approach (the workload's API — a running's agent on the host that
attests the workload and issues the SVIDs) works for VMs and bare metal. Or the cloud's
instance's profiles (the AWS's instance profiles) — the credential is delivered by the
instance's metadata's service, rotated automatically. Or, if neither is available, the
`private_key_jwt` with an automated's key's delivery (the Vault's auto-renew's PKI, the
SPIFFE's agent, a K8s's operator). The point: **the goal is a short-lived,
automatically-rotated credential that the service never has to store as a long-lived
secret** — the delivery's mechanism varies, the principle doesn't.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We're extracting our monolith into services. The services need to call each
other. The team wants OAuth2's client credentials with a shared's secret. What do you
advise?"**
>
> **Ideal answer:** (1) **The framing:** the extraction's hardest's problem is usually
**not** the code's split — it's the **trust's and the data's** boundary. The team's
instinct (OAuth2's client credentials with a shared's secret) is a reasonable place to
start, and it's what many companies do, but the **shared's secret** is where it degrades
quickly: N services × M environments = hundreds of secrets, each a's manually-rotted
credential that a human's on-call has to rotate under pressure. (2) **The phased's
recommendation:**
**Phase 1 (now):** OAuth2's client credentials, but with the **scopes designed properly**
from the start — this is the thing that is expensive to retrofit. Each service's client
gets the **narrowest** scope set that matches its **actual** calls (not the domain's
maximum — the *actual*). The scopes are the API's contract, so getting them right at the
start means the future's microservices have a coherent's vocabulary. Use the
`private_key_jwt` from the beginning if you can (it removes the secret's distribution
problem immediately, and it's a small's change to the AS).
**Phase 2 (once the platform's ready):** move the internal's service's identity to the
**workload's identity** — SPIFFE/SPIRE, or the cloud's workload's identity if you're on
AWS/GCP/Azure. Now the "service's identity" is a cryptographic's attestation, the
credentials rotate automatically, and a compromised pod gets exactly its own identity. The
OAuth2's client credentials then remains only for the **cross-boundary** calls.
**Phase 3:** the **user's identity's propagation** — the gateway validates the user's
token and issues the internal's downstream's claim (a small, short-lived, narrow's token
signed by the platform), so the internal's services never handle the user's
bearer token. (3) **The data's side (the harder's half, which the question hints at):**
the extraction forces the **data's ownership's decision** — which service owns which
table? The answer determines the **synchronous vs the asynchronous** boundary:
- The **synchronous** call (the order's service needs the customer's data) → the API's
call with the service's identity. The latency's budget and the availability's coupling
are the costs: the order's service now **breaks** when the customer service is down. The
mitigations: the timeout, the circuit breaker, the fallback, the **the data's
replication** for the read's paths (the order's service keeps a local's copy of the
customer's name/email for display — the CQRS's read's model).
- The **asynchronous** coupling (the events) → the order's service publishes "the order
was created" and the customer service (or the notification's service) reacts. This
**removes the runtime's dependency** and is the right's default for the non-interactive's
paths. The costs: the eventual's consistency, the event's ordering, the exactly-once's
problem, the debugging's difficulty, and the **the data's duplication's ownership** (the
read's model is now a copy — who updates it?).
The senior's heuristic: **"if the user is waiting for the answer, it can be a
synchronous's call; if the user is waiting for the *reaction*, it must be an event."**
(4) **The extraction's sequencing (the strangler's pattern):** the monolith and the
services coexist; the **anti-corruption's layer** (the facade) is the seam; the routing
moves gradually (the gateway's routes by the feature's flag); the data's ownership is
migrated with the **dual-write's** or the **CDC** (the Debezium's connector) approach, not
with the distributed's transactions. (5) **The interview's point to make:** the
microservices' split is not a **technical's** decision about the code — it's a decision
about the **data's ownership**, the **teams's** autonomy, and the **failure's boundaries**.
The tech choice (OAuth2 vs the workload's identity) is a 10% of the question; the data's
ownership's model and the coupling's mode are the 90%. (6) The senior framing: "**the
team's instinct is a fine's starting point, but the shared's secret is the thing that
will hurt them at scale — hundreds of credentials, manually rotated, with a human's
coupling between the services' availability and the AS's.** The right's shape: the
scopes designed properly from day one (they're the API's contract and they're expensive
to change), the `private_key_jwt` if you can, and the **workload's identity** as the
destination. But the question I ask first is: **'which service owns which data, and
does the user wait for the answer?'** Because a synchronous's call between the services
couples their availability — the order's service is now down whenever the customer
service is — and that's usually a worse's outcome than the auth's mechanics. The
asynchronous's decoupling is where the resilience's gains are, and it costs the eventual's
consistency, which the domain may or may not tolerate. The auth's choice is a day's
decision; the data's ownership and the coupling's mode are the architecture."

**R2. *"Our partner's integration keeps failing with 403s. The partner says their token
is valid. The tokens look fine. What do you check?"**
>
> **Ideal answer:** (1) **The framing:** a 403 means the token was **accepted** (the
signature, the `exp`, the `iss` are all fine — otherwise it'd be a 401) but the
**authorization** failed. So the problem is in the **scope** or the **audience** — not the
token's validity. (2) **The checks, in order:**
(a) **The scope's comparison** — read the token's `scope` claim and compare it with what
the endpoint requires. The classic's gotchas: (i) the **prefix's mismatch** — the
Spring's maps the scopes to `SCOPE_orders:read` but the endpoint's check was written
against `ROLE_ORDER_READ` (or the raw `orders:read`) → the mismatch; (ii) the
**case's** sensitivity; (iii) the **scoping's hierarchy** — the partner's token has
`orders:*` (the wildcard) but your code compares with `equals` instead of matching the
wildcard → a false's 403; (iv) the **the scope's request vs the grant** — the partner
requested `orders:read` but their's AS narrowed it.
(b) **The audience's check** — your API's `aud` validator. If your API requires
`aud: orders-api` and the partner's AS issues `aud: https://partner-api.example.com`
(the partner's own API's identifier, which makes sense for them), your audience's
validator rejects it — with a 403. This is **very** common when a partner's AS is
configured for their own's APIs and your's is a "the new's audience" in their realm.
The fix: the **resource's indicator** (the partner's client must request
`resource=https://your-api.example.com/` so the AS mints the right's audience) or the
configuration of the audience's validator in your API.
(c) **The role's vs the scope's mapping** — the partner's token carries `roles` (not
`scope`). If your `JwtAuthenticationConverter` only maps the `scope` claim, the
authorities are empty → 403. (d) **The `sub`'s type** — the partner's service-to-service
token has a `sub` of the client, and if your endpoint requires a **user's** context (e.g.
`@PreAuthorize("hasAuthority('SCOPE_orders:read')")` plus a check that the `sub` resolves
to a user), the service's token fails the user-specific's check.
(e) **The partner's consent** — if the partner's user-facing's client is involved, the
granted's scopes may be narrower than the requested's (the user's consent).
(f) **The clock** — a token with a *just-expired* `exp` gives a 401, but some gateways
convert it to a 403. Verify the server's clocks.
(g) **The token's exchange's audience's policy** — if the partner's AS performs an
exchange for your API, its policy may be rejecting the requested's audience.
(3) **The methodology:** (a) get the **exact's error** — the 403's body should carry a
code (`INSUFFICIENT_SCOPE` in the OAuth's spec — the RFC 6750 defines it, and the
`WWW-Authenticate` header should include the `scope` attribute: `Bearer error="insufficient_scope",
scope="orders:read"`); (b) **decode the token's claims** and compare them with the
endpoint's requirement; (c) check the **audience's validator's configuration**; (d) the
**audit's log** on the API's side (what the authorities actually were after the mapping
— the `JwtAuthenticationConverter`'s output).
(4) **The fix's options:** (a) fix the **scope's naming** (the prefix, the hierarchy) and
the code's consistency; (b) fix the **audience's configuration** (the resource's
indicator, the audience's validator, or the audience's mapping in the partner's AS);
(c) fix the **the claims's mapping** (the `roles` → the authorities); (d) if the partner
is misconfigured and can't change quickly, add a **the temporary's mapping** in your
converter (a scoped's exception) — but log it loudly and track it to be removed.
(5) The senior framing: "**a 403 means the token is valid and the permission is wrong —
so I stop looking at the token's validity and start comparing the two's authorities: the
token's `scope`/`aud`/`roles` claims against what the endpoint's `@PreAuthorize` and the
audience's validator actually require.** The three failures I see most: the **prefix's
mismatch** (`SCOPE_orders:read` vs `orders:read` — the Spring's prefixes it, and if the
check was written without the prefix, the comparison is against a string that never
appears in the authorities), the **wildcard's handling** (the partner's token has
`orders:*` and the code uses `equals` instead of the prefix's match), and the **audience**
— the partner's AS issues the audience of *their* API because nobody told it about
yours, and the fix is the **resource's indicator** in the client's registration. The
debugging's discipline that catches all three: log the **authorities after the
conversion**, not the token's claims — because the bug is usually in the conversion, not
in the token."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The 403s on the partner's calls | The scope's prefix / the wildcard's handling | The consistent's naming; the prefix's match |
| The 403s (the audience) | The partner's AS issues their own's audience | The resource's indicator; the audience's validator |
| The token's endpoint's rate's limit | The token's re-fetching per call | The `OAuth2AuthorizedClientManager`'s caching |
| The high's latency on the partner's calls | The token's acquisition in the hot path | The caching; the single-flight |
| The 401's loop in the service | The refresh's logic missing | Refresh once + retry once |
| The 500s when the AS is down | The token's acquisition's threads exhausted | The circuit breaker; the AS's HA |
| The scope's mismatch in the logs | The `roles` vs the `scope` | The custom's `JwtAuthenticationConverter` |
| The partner's "the token's valid" | The audience's check (a 403's body is opaque) | The `WWW-Authenticate` header's details |

---

## 18. Debugging Scenarios

**D1. "Decode the M2M's token:"**
```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq '{sub, aud, scope, azp, gty, exp}'
```

**D2. "Compare the token's scopes with the endpoint's requirement:"**
```bash
# the token's scopes
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq -r '.scope'
# the endpoint's requirement
grep -rn "hasAuthority\|@PreAuthorize" --include=*.java src/ | grep -i orders
```

**D3. "Verify the `private_key_jwt`'s assertion:"**
```bash
echo "$ASSERTION" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq '{iss, sub, aud, jti, exp}'
```

**D4. "Find the services using a scope they shouldn't:"**
```sql
-- the AS's token's audit (Keycloak's events, or your own's log)
SELECT client_id, scope, count(*) FROM token_audit_log
 WHERE created_at > now() - interval '7 days'
 GROUP BY client_id, scope ORDER BY client_id;
```

**D5. "Check the token's caching's effectiveness:"**
```bash
# the count of the /token's calls vs the API's calls (the proxy's metrics)
curl -s http://localhost:9090/metrics | grep -E "token_endpoint_requests_total|http_server_requests_total"
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the service-to-service's authentication for a 200-service platform with
50 partners.**
> (1) The **the trust's zones** (the internal's mesh, the cloud, the cross-organisation,
the legacy); (2) the **the identity's model** (SPIFFE's IDs, the OAuth2's clients, the
mapping's table); (3) the **the credential's lifecycle** (the automatic's rotation, the
KMS, the revocation); (4) the **the authorization's model** (the scopes, the policy,
the exchange's narrowing); (5) the **the audit & the anomaly's detection**; (6) the
**the migration's path** (the shared's secrets → the workload's identity).

**Q2. 🏗️ Design the M2M's auth for a partner's ecosystem with 50 integrations.**
> (1) The **the trust's model** (the tiered: the strategic's partners get a proper's
OAuth2, the long's tail get the key's adapter); (2) the **the adapter's pattern** and the
credential's isolation; (3) the **the quota's model** (the per-partner's limits, the
burst's allowance); (4) the **the scope's catalogue** (the partner's tiers' permissions);
(5) the **the key's rotation's process** (the scheduled's rehearsal, the dual-key's
overlap); (6) the **the observability & the support's tooling** (the per-partner's
dashboard, the replay's tools).

**Q3. 🏗️ Design the auth for a bank's internal's platform with 300 services across
on-prem and 2 clouds.**
> (1) The **the regulatory's constraints** (the FIPS's validation, the HSM, the key's
custody, the audit's evidence); (2) The **the identity's model** (the SPIFFE's hierarchy
per environment, the cloud's federation); (3) the **the data's residency's constraint**
(the token's issuer must be in the region, the keys are region-bound); (4) the **the
legacy's integration** (the API's gateway's adapter, the on-prem's certificate's
authority); (5) the **the DR** (the AS's failover, the keys's availability, the
token's validation during the failover — the JWKS's caching's TTL vs the region's
isolation); (6) the **the evidence & the audit's retention**.

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the CLIENT CREDENTIALS: no user; the sub = the CLIENT; no refresh token     │
│  the client's AUTH: private_key_jwt (60s's exp, aud = the token's endpoint)   │
│                    > client_secret_basic > client_secret_post                │
├──────────────────────────────────────────────────────────────────────────────┤
│  the REFRESH's GRANT: the client presents the refresh → a new access token   │
│  the ROTATION: single-use + the family's lineage + the reuse's detection      │
│  the GRACE's WINDOW (30-60s): the duplicate's request → the SAME successor    │
│  the `offline_access` scope: required to GET a refresh token (the OIDC)       │
├──────────────────────────────────────────────────────────────────────────────┤
│  the TOKEN's CACHING: the OAuth2AuthorizedClientManager — 1 call per TTL      │
│  the TOKEN's EXCHANGE (8693): the broad → the narrow (the subset only)       │
│  the RESOURCE's INDICATOR (8707): the per-API's audience                      │
│  the DEVICE's FLOW (8628): the TVs, the CLI's, the IoT's                      │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠ The internal's microservices: the workload's identity (mTLS/SPIRE) is      │
│    usually better than OAuth2 — no's secret, the auto's rotation, no's AS     │
│  ⚠ The OAuth2's client credentials is for the CROSS-BOUNDARY's calls         │
│  ⚠ The scope's naming: consistent's prefixing + the wildcard's handling      │
│  ⚠ The 401 vs 403: refresh-and-retry-once vs never-retry                    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)
- → [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md)
- → [06 · JWT Authentication](../06-JWT-Authentication/README.md)

---
**Next Chapter:** → [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)
**Previous Chapter:** ← [02 · Authorization Code Flow & PKCE](./02-Authorization-Code-Flow-and-PKCE.md)
