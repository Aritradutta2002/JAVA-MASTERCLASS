# 04 · OpenID Connect, Social Login & Keycloak

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do you implement login with Google?")
> **Prerequisite:** [03 · Client Credentials & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — Identity vs Authorization](#2-mental-model--identity-vs-authorization)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 What OIDC adds on top of OAuth2](#31-what-oidc-adds-on-top-of-oauth2)
  - [3.2 The ID token](#32-the-id-token)
  - [3.3 The UserInfo endpoint](#33-the-userinfo-endpoint)
  - [3.4 The scopes and the claims](#34-the-scopes-and-the-claims)
  - [3.5 The OIDC Discovery document](#35-the-oidc-discovery-document)
  - [3.6 The Dynamic Client Registration](#36-the-dynamic-client-registration)
  - [3.7 The social login (OAuth2 with a social's provider)](#37-the-social-login-oauth2-with-a-socials-provider)
  - [3.8 The Keycloak's realm, client, and role model](#38-the-keycloaks-realm-client-and-role-model)
  - [3.9 The Keycloak's token exchange and the advanced's features](#39-the-keycloaks-token-exchange-and-the-advanceds-features)
  - [3.10 The session's management](#310-the-sessions-management)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples](#5-worked-examples)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter- Questions & Traps](#9-counter--questions--traps)
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

- **OAuth2 = authorization** ("what may this app do?"). **OIDC = authentication** ("who
  is the user?") — it's a **layer on top** of OAuth2, not a replacement.
- **OIDC adds three things:** the **ID token** (a signed JWT about the user's identity),
  the **UserInfo endpoint** (the standard's way to get more claims), and the
  **Discovery document** (the standard's way to find the endpoints and the keys).
- **The ID token is for your server, not the browser.** The browser gets the **access
  token**; your server validates the **ID token** to learn who the user is. Never trust
  the ID token's claims from the browser.
- **The `iss`, `aud`, `exp`, `nonce`, `azp` validation is non-negotiable** — the ID token
  is a bearer credential, and the `nonce` binds it to the session (the replay's defence).
- **Social login (login with Google) = OAuth2 + OIDC against a social's provider** —
  your server is the client, and the flow is the authorization code with the PKCE.
- **Keycloak** = an identity's provider: the realms (the tenants), the clients (the
  applications), the users, the roles, the identity's providers (the social's login), and
  the token's exchange.

---

## 1. What & Why

### The "login with X" problem

```text
  the USER wants:   "I already have a Google account. Let me use it here."
  YOUR app needs:   the user's identity (id, email, name) AND a way to authenticate
                    them on subsequent's requests.

  The naive's approach:
    ① the "Sign in with Google" button → Google's OAuth2
    ② you get an access token → you call Google's API → you get the profile
    ③ you create a local user and log them in with a session

  The problems:
    · Google's access token is for GOOGLE's API, not yours. Using it as your
      session's token means you're trusting a third party's token's semantics.
    · You have no standard's way to know the user is who they claim (the email
      from Google's API might not be verified, depending on the scope).
    · You can't support multiple's providers (the code explodes).
    · You can't support your OWN users (the admins) alongside the social's ones.
```

### The OIDC's answer

```text
  ① the user authenticates at the provider (Google, Keycloak, Okta)
  ② the provider authenticates the CLIENT (your app) — the authorization code flow
  ③ the provider returns:
       - an ACCESS TOKEN (for the provider's APIs, and for YOUR APIs if you're
         the audience — the resource's indicator)
       - an ID TOKEN (a signed JWT: "the user is alice, email a@x.com, the issuer
         is me") — this is the OIDC's addition
  ④ YOUR server validates the ID token (the signature via the JWKS, the iss, the aud,
     the exp, the nonce) → now it knows the user
  ⑤ YOUR server creates ITS OWN session/token for the user → your API's requests
     use YOUR credential
```

**The key's insight:** the ID token is a **provider-signed statement of identity**,
verifiable offline (the JWKS's keys), bound to the client (`aud`), and bound to the
session (`nonce`). That's what makes "login with Google" safe.

---

## 2. Mental Model — Identity vs Authorization

```text
  ┌───────────────────────────────────────────────────────────────────────┐
  │  OAuth2 answers: "MAY this client DO this?"                          │
  │    the token = the DELEGATION (a capability, scoped, audience-bound) │
  │    the audience = the resource's server                             │
  │                                                                       │
  │  OIDC answers: "WHO is the user?"                                    │
  │    the token = the IDENTITY (a statement, signed by the issuer)      │
  │    the audience = the CLIENT (your app)                              │
  └───────────────────────────────────────────────────────────────────────┘

  the consequence: the SAME token's type can carry EITHER meaning, and
  confusing them is the classic's vulnerability:

    ❌ using the ID token as the API's bearer token
       → the ID token is for YOUR server, and it's often not audience'd
         at your API → the API might accept a token it shouldn't
       → the ID token usually has a LONG expiry (an hour) — the browser
         holding it can replay it

    ❌ using the access token to learn the user's identity
       → the access token's claims are the RESOURCE's business
       → the profile's data might live in the UserInfo endpoint, not the token
```

**The rule of thumb:** *The ID token goes to the **client's server**. The access token
goes to the **API's server** (and to the browser's API's calls).*

---

## 3. Deep Dive — Theory

### 3.1 What OIDC adds on top of OAuth2

```text
  OAuth2 core (RFC 6749)              OIDC (OpenID Connect Core 1.0)
  ─────────────────────────           ─────────────────────────────────
  · the authorization's endpoint       · the ID token
  · the token's endpoint               · the UserInfo endpoint
  · the resource's owner                · the Discovery document
    (the "who is the user"              · the standard's claims
     — a "profile of the resource        (sub, name, email, ...)
     owner" in the USERINFO             · the `nonce` (the session's binding)
     response)                        · the `acr`/`amr` (the auth's context)
  · the scopes                         · the `prompt` (the re-auth's control)
                                        · the `max_age` (the freshness)
                                        · the `id_token_hint`/`login_hint`
```

**The OIDC's three "discovery" mechanisms:**

| The mechanism | The URL | The purpose |
|--------------|---------|-------------|
| The **Discovery document** | `/.well-known/openid-configuration` | The endpoints, the JWKS's URI, the supported's algorithms |
| The **JWKS** | `/protocol/openid-connect/certs` | The public's keys for the JWT's verification |
| The **UserInfo** | `/userinfo` | The user's claims (with an access token) |

**The `.well-known` path's rule:** for an issuer `https://as.example.com/realms/prod`,
the discovery's URL is `https://as.example.com/realms/prod/.well-known/openid-configuration`
— the path's suffix is appended to the issuer's path, **not** the host's root. This is a
classic's mistake (Keycloak's realms are path-based).

### 3.2 The ID token

**The ID token's claims (the mandatory's set):**

| The claim | The meaning | The validation's rule |
|-----------|-------------|---------------------|
| `iss` | The issuer (the provider) | **Must** match the expected's issuer, **exactly** (the string's comparison) |
| `sub` | The subject (the user's id **at this issuer**) | **Must** be present; the `sub` is the stable's identifier |
| `aud` | The audience (the client) | **Must** contain your client id; a single's audience is a string, multiple's is an array |
| `exp` | The expiry | **Must** be in the future (with the clock's skew) |
| `iat` | The issued-at | Sanity's check |
| `nonce` | The session's binding | **Must** equal the one you sent in the request (if you sent one) |
| `auth_time` | The authentication's time | The check for the `max_age` |
| `acr` | The authentication's context class | The check for the required's level (MFA) |
| `amr` | The authentication's methods | What was used (the password, the OTP, the hardware's key) |
| `azp` | The authorized party | The check when `aud` has multiple's entries |

**The ID token's shape:**
```json
{
  "iss": "https://accounts.google.com",
  "azp": "123456789-abc.apps.googleusercontent.com",
  "aud": "123456789-abc.apps.googleusercontent.com",
  "sub": "110248495921238986420",
  "email": "alice@example.com",
  "email_verified": true,
  "name": "Alice Smith",
  "picture": "https://lh3.googleusercontent.com/a/...",
  "given_name": "Alice",
  "family_name": "Smith",
  "iat": 1735689000,
  "exp": 1735692600,
  "nonce": "d2a4c1b8-...",
  "at_hash": "77QmUPtjPfzWtF2AnpK9RQ=="
}
```

**The `at_hash` claim:** the **left-most half of the SHA-256 hash** of the ASCII's
access token, base64url-encoded. It lets the client verify that the ID token and the
access token came from the **same authorization's response** (a mix-up's defence). The
Spring's `OidcIdTokenDecoder` verifies it when the access token is present.

**The `c_hash` claim:** the same's idea for the authorization code (in the hybrid's flow).

**The `nonce` (the critical's one):**
```text
  the CLIENT generates a random nonce, stores it in the session, and sends it:
    /authorize?nonce=d2a4c1b8-...

  the PROVIDER copies it into the ID token.

  the CLIENT validates: idToken.nonce == session.nonce
    · if it doesn't match → the ID token is a REPLAY (from another session)
    · if the provider DIDN'T echo the nonce → you can't bind → you must not
      accept the token (or you must treat the flow as unverified)

  ⚠ NEVER skip the nonce's validation in a confidential's client with the
    authorization code flow. It's the defence against an ID token's injection
    (the attacker's token substituted for the user's).
```

### 3.3 The UserInfo endpoint

```text
  GET https://as.example.com/userinfo
    Authorization: Bearer <the access token>

  → 200 {
      "sub": "110248495921238986420",   ← the REQUIRED claim (it must match the ID token's sub)
      "name": "Alice Smith",
      "given_name": "Alice",
      "family_name": "Smith",
      "email": "alice@example.com",
      "email_verified": true,
      "picture": "https://..."
    }
```

**The rules:**
- The `sub` in the UserInfo's response **must** equal the `sub` in the ID token. If they
  differ → reject.
- The claims returned depend on the **scopes** you requested (`openid`, `profile`,
  `email`, `phone`, `address`).
- You call the UserInfo endpoint when you need **fresh** data or **claims not in the ID
  token** (e.g. the user's organization from a custom's mapper).

**The scopes → claims's mapping (the standard's defaults):**

| The scope | The claims |
|-----------|-----------|
| `openid` | `sub` (**required**) |
| `profile` | `name`, `family_name`, `given_name`, `middle_name`, `nickname`, `preferred_username`, `profile`, `picture`, `website`, `gender`, `birthdate`, `zoneinfo`, `locale`, `updated_at` |
| `email` | `email`, `email_verified` |
| `phone` | `phone_number`, `phone_number_verified` |
| `address` | `address` |
| (the custom) | Whatever the provider's mappers add |

**The `email_verified` trap:** the `email` claim means nothing without
`email_verified: true`. A provider that lets a user set an arbitrary email (or a
provider that doesn't support the claim) will return `email_verified: false` or omit it
— and using that email as the user's identity lets someone **claim another person's
email address** and take over the account. The correct's logic:
```java
if (!isEmailVerified(idToken)) {
    // DO NOT trust the email as the identity
    // → require a verified's second's factor, or link the account via a
    //   separate's verification's email (the "the pending's account linking" flow)
}
```

### 3.4 The scopes and the claims

```text
  the SCOPE is what you REQUEST (the access's intent)
  the CLAIM is what you RECEIVE (the identity's data)

  the requesting's URL:
    /authorize?
      client_id=app-123&
      scope=openid profile email&
      response_type=code&
      redirect_uri=https://app.example.com/callback&
      state=xyz&
      nonce=abc&
      code_challenge=...&code_challenge_method=S256
```

**The `prompt` parameter (the login's control):**

| The value | The meaning |
|-----------|------------|
| `none` | **No** interaction — fail if the user isn't already authenticated (the silent's renewal). If the provider must interact → `login_required` |
| `login` | Force a fresh's authentication (re-enter the password) |
| `consent` | Force the consent's screen (re-ask for the permissions) |
| `select_account` | Show the account's picker (the multi-account's hint) |

**The `max_age` parameter:** the maximum's age (in seconds) of the authentication. If the
user authenticated longer ago → **re-authenticate**. This is how "sensitive operations
require a fresh login" is implemented (the step-up's flow).

**The `id_token_hint`:** the previous's ID token, as a hint about which session to
resume. The provider may use it to skip the re-authentication.

**The `login_hint`:** a hint about the account (the email) to pre-fill — a UX's
convenience, not a security's control (the provider must not trust it).

**The `acr_values`:** the requested's authentication's context (the MFA's requirement).

### 3.5 The OIDC Discovery document

```json
{
  "issuer": "https://as.example.com/realms/prod",
  "authorization_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/auth",
  "token_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/token",
  "userinfo_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/userinfo",
  "jwks_uri": "https://as.example.com/realms/prod/protocol/openid-connect/certs",
  "end_session_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/logout",
  "introspection_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/token/introspect",
  "revocation_endpoint": "https://as.example.com/realms/prod/protocol/openid-connect/revoke",
  "response_types_supported": ["code", "id_token", "code id_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "code_challenge_methods_supported": ["S256"],
  "grant_types_supported": ["authorization_code", "refresh_token", "client_credentials"],
  "scopes_supported": ["openid", "profile", "email", "phone", "address", "offline_access"]
}
```

**The fields that matter in practice:**
- `issuer` — **must** be validated against the ID token's `iss` with an **exact** string
  comparison (a trailing's slash or a case's difference must fail — this is a
  known's class of vulnerability).
- `jwks_uri` — the keys's endpoint.
- `id_token_signing_alg_values_supported` — **pin the algorithm**; never trust the
  token's header's `alg` blindly (the `alg: none` and the algorithm's confusion
  attacks).
- `code_challenge_methods_supported` — must contain `S256` (not `plain`).

**The Spring's `ClientRegistrations.fromIssuerLocation(...)`** fetches this document
automatically and configures the endpoints and the JWKS. That's why the Spring's OIDC
setup is a 5-liner.

### 3.6 The Dynamic Client Registration

```text
  the CLIENT registers ITSELF with the provider (RFC 7591):
    POST https://as.example.com/reg
      { "client_name": "My App",
        "redirect_uris": ["https://app.example.com/callback"],
        "grant_types": ["authorization_code", "refresh_token"],
        "response_types": ["code"],
        "token_endpoint_auth_method": "private_key_jwt",
        "id_token_signed_response_alg": "RS256" }

  → 201 { "client_id": "...", "client_secret": "...", "registration_access_token": "..." }
```

**Who uses it:**
- The **dynamic's clients** — a multi-tenant's SaaS where each tenant's app registers
  itself (the "the customer's own's app connects to us").
- The **CLI's tools** (`gh`, `kubectl`'s plugins) that register on the fly.
- The **development's prototyping** (no's manual's setup in the console).

**The security's concerns:** the registration's endpoint must be **gated** (an
approval's workflow, an invitation's code, or an admin's approval), the redirect's URIs
must be **validated** (the exact's match, no wildcards), and the registration's access
token must be protected (it's a **management's credential**).

### 3.7 The social login (OAuth2 with a social's provider)

**The social's providers are not all OIDC-compliant:**
- **Google, Apple, Microsoft, Facebook (with the Facebook's Login), GitHub (with a
  caveat)** — support OIDC (or an OIDC-compatible's variant). Google and Microsoft are
the cleanest's OIDC implementations.
- **GitHub, Twitter/X, LinkedIn (older)** — plain OAuth2 with a **custom's profile's
  endpoint**. They give you an access token and you call their's API to get the
  profile. **There's no `id_token`** — so you must call the profile's API and treat its
  response as the identity's source, with a **manual's verification** (the email's
  verification's status is not standardised).

**The Spring's social login (the "the user's login with X" flow):**

```text
  the BROWSER:  GET /oauth2/authorization/{registrationId}
       → the Spring redirects to the provider's /authorize
       (the PKCE, the state, the nonce are all handled by the Spring)

  the PROVIDER: authenticates the user → redirects back with ?code=...&state=...

  the BROWSER:  GET /oauth2/callback/{registrationId}
       → the Spring exchanges the code for the tokens
       → the ID token is validated (the signature, the iss, the aud, the exp, the nonce)
       → the OidcUser is built
       → the success's handler runs → your app's session is created
```

**The registration's configuration:**
```yaml
  spring:
    security:
      oauth2:
        client:
          registration:
            google:
              client-id: ${GOOGLE_CLIENT_ID}
              client-secret: ${GOOGLE_CLIENT_SECRET}
              scope: [openid, profile, email]
            github:
              client-id: ${GITHUB_CLIENT_ID}
              client-secret: ${GITHUB_CLIENT_SECRET}
              scope: [read:user]
```

### 3.8 The Keycloak's realm, client, and role model

**The realm = the tenant's boundary.** Each realm has its own:
- users (and the user federation)
- clients (the applications)
- roles (the realm's roles and the client's roles)
- identity's providers (the social's login)
- keys (the realm's signing's keys)
- sessions, and the events (the audit's log)

**The client's types:**

| The type | The capability | The secret's required | The typical's use |
|---------|---------------|----------------------|------------------|
| **Public** | The PKCE's flow, no's secret | ❌ | The SPA, the mobile's app, the CLI's tools |
| **Confidential** | The secret (or the key) | ✅ | The server-side's web app, the services |
| **Bearer-only** | The resource's server only | ✅ | The legacy's API (the token's introspection) |

**The client's authentication's methods (in the Keycloak):**
`client-secret` (the default), `client-jwt` (the `private_key_jwt`), `client-secret-jwt`
(the secret's key, but the assertion is signed), `tls-client-certificate` (mTLS).

**The roles vs the groups vs the scopes — the distinctions that get confused:**

| The concept | The scope | The granularity | The typical's use |
|-----------|---------|-----------------|------------------|
| **Realm's role** | The whole's realm | The coarse | The admin, the user |
| **Client's role** | One's client | The medium | The "the orders' admin" within the orders's client |
| **Group** | An organizational's unit | The organisational | The "the finance's team" (the groups can map to the roles) |
| **Client's scope** | The permission's definition | The API's contract | The `orders:read` — a **client's scope** in the Keycloak is the closest to an OAuth2's scope |

**The "the realm's role vs the client's role"** confusion in the token:
- A **realm's role** appears in the `realm_access.roles` claim.
- A **client's role** appears in the `resource_access.<client-id>.roles`.

```json
{
  "realm_access": { "roles": ["user"] },
  "resource_access": {
    "orders-api": { "roles": ["orders:admin", "orders:read"] },
    "inventory-api": { "roles": ["inventory:read"] }
  }
}
```

**The scope's mapper:** by default, the Keycloak puts the **effective's scopes** (the
client's roles + the optional's client scopes) into the `scope` claim as **space-
separated strings** — the standard's format. With the **"the audience's mapper"** (the
hardcoded's audience, the client scope's mapper), the client scopes are ALSO emitted as
the `scope` claim of the **audience's client's** token.

**The identity's providers (the social's login in the Keycloak):**
- The Keycloak acts as the **broker**: the user logs in at the Keycloak, the Keycloak
  redirects to Google, gets the identity, and creates/maps a local's user.
- **The account's linking:** by default, the Keycloak creates a **separate's local
  user** for each's identity's provider (so a user with a Google and a Keycloak account
  is two users!). The **"the first's login's flow"** or the **"the account's linking"
  feature** (the `broker` + `link` parameters) is needed to merge them — this is a
  common's production's surprise.
- The **"the account's linking"** flow: `GET /realms/{realm}/broker/{id}/link?code=...`
  with the `broker` and `currentBroker` parameters.

### 3.9 The Keycloak's token exchange and the advanced's features

**The Keycloak's token exchange (RFC 8693) — the "the standard's exchange":**
```text
  the GRANT's TYPE: token-exchange
  the subject's token: the user's token (or the service's token)
  the requested's subject: a different's user (the impersonation!)
  the audience: the target's client
  the scope: the narrowing
```

**The Keycloak's specific's features:**
- **The impersonation's headers** — the `X-impersonate-user` header (the admin's
  impersonation, used by the admin's consoles).
- **The token's exchange's permissions** (the "the push's claims" and the "the pull's
  claims" mappings) — the on-the-fly's claim's manipulation, useful for the
  multi-tenant's token's shaping.
- **The client's policies** (the fine-grained's authorization) — the JSON-based
  policies that evaluate the request's context.
- **The customer's tokens** (the SPIFFE's "the tokens are the permissions" model).
- **The declarative's UI** (the Realm's configuration as the code — the GitOps's
  friendly).
- **The organization's feature** (the multi-tenancy's model, the B2B's feature) — a
  realm with the organizations, each with its own's members, roles, and identity's
  providers.

**The admin's REST's API** — everything the console does is available as a REST's API,
which is how the **infrastructure's as code** (the Terraform's provider, the
Crossplane) configures the realms.

### 3.10 The session's management

**The RP-Initiated Logout (the OIDC's back-channel's logout):**
```text
  the RP redirects the user to:
    https://as.example.com/realms/prod/protocol/openid-connect/logout?
        post_logout_redirect_uri=https://app.example.com/bye&
        id_token_hint=<the ID token>&
        client_id=app-123

  the provider ends the session and redirects back.
```

**The Front-Channel Logout (the "the iframe's ping"):**
```text
  the provider loads an iframe:
    <iframe src="https://app.example.com/logout?iss=...&sid=...">
  → the app clears its session
  (⚠ deprecated in the OIDC's back-channel's logout's spec; the back-channel's
   logout — a server-to-server's POST — is the modern's way)
```

**The Back-Channel Logout (the modern's way):**
```text
  the provider POSTs to your's registered's endpoint:
    POST https://app.example.com/backchannel-logout
      sid=<the session's id>&iss=<the issuer>
    → your app ends the session
```

**The session's durations in the Keycloak:**
- The **SSO's session** (`ssoSessionIdleTimeout`, `ssoSessionMaxLifespan`) — the user's
  session at the provider (across the clients).
- The **offline's session** (`offlineSessionIdleTimeout`, `offlineSessionMaxLifespan`) —
  the refresh's token's session. The refresh token is only issued if the **offline's
  session's enabled** and the `offline_access` scope is requested.

**The `prompt=none` and the "the silent's renewal":**
```text
  the SPAs can't hold a refresh token safely → they use the implicit's flow's
  "the silent's renewal": a hidden iframe (or the modern's "the check-session"
  iframe) that does /authorize?prompt=none&id_token_hint=...
  → if the user is still logged in at the provider, a NEW ID token is returned
  → the SPAs renew the session without a user interaction
```

---

## 4. Visualizations

### 4.1 The OIDC's authorization code flow (with the nonce)

```mermaid
sequenceDiagram
    autonumber
    participant B as the Browser
    participant R as your App (the RP)
    participant P as the Provider (the OP)

    B->>R: GET /oauth2/authorization/google
    Note over R: generate the state, the nonce, the PKCE's verifier
    R->>P: /authorize?client_id=..&scope=openid profile email&state=..&nonce=..&code_challenge=..
    P->>B: the login & consent's screen
    B->>P: the credentials
    P-->>B: 302 /callback?code=ABC123&state=..
    B->>R: GET /oauth2/callback/google?code=ABC123&state=..
    R->>R: validate the state (the CSRF's defence)
    R->>P: POST /token (code + the PKCE's verifier + the client auth)
    P-->>R: { access_token, id_token, refresh_token }
    R->>R: validate the ID token<br/>signature, iss, aud, exp, nonce, at_hash
    R-->>B: the session's cookie; your own's token
```

### 4.2 The ID token's validation's checklist

```text
  ┌───────────────────────────────────────────────────────────────┐
  │  the ID TOKEN's validation (in this order)                    │
  └───────────────────────────────────────────────────────────────┘
   ① the SIGNATURE      → the JWKS's key (the alg is pinned: RS256)
   ② the iss            → EXACT's match with the configured's issuer
   ③ the aud            → CONTAINS your client id
   ④ the exp            → in the future (the clock's skew's tolerance)
   ⑤ the nonce          → EQUALS the one you sent (if you sent one)
   ⑥ the azp            → equals your client id (if aud has multiple's)
   ⑦ the at_hash        → matches the access token (if you have one)
   ⑧ the sub            → is present; (the UserInfo's sub must match it)
   ⑨ the email_verified → true (before you trust the email)
  ⚠ NEVER skip ②–⑤: they are what stops a token's injection and a mix-up.
```

### 4.3 The social's login (the Keycloak as the broker)

```text
  the USER → your APP → the KEYCLOAK (the broker) → GOOGLE
                    ↑                              │
                    └──── the ID token (the broker's)←┘

  ⚠ the Keycloak's identity's mapping:
    by default, a Google's login creates a SEPARATE's local's user
    → the "the first's login's flow" or the account's linking merges them
```

### 4.4 The Keycloak's realm's structure

```text
  the REALM (the tenant)
  ├── the users (the identities)          → the login's credentials
  ├── the GROUPS (the organisational)      → "the finance's team"
  ├── the REALM's ROLES                    → admin, user
  ├── the CLIENTS
  │     ├── orders-api (the resource's server, the bearer-only)
  │     ├── web-app    (the confidential's client, the web)
  │     └── mobile-app (the public's client, the PKCE)
  ├── the CLIENT's SCOPES                  → orders:read, orders:write
  │     └── the DEFAULT's client scopes    → profile, email, roles
  ├── the IDENTITY's PROVIDERS             → google, github, saml
  ├── the KEYS                             → the realm's signing's keys
  ├── the EVENTS (the audit)               → the login's log
  └── the SESSIONS                         → the SSO's sessions
```

---

## 5. Worked Examples

### 5.1 The full's code flow (the dry run)

```text
  ① GET https://app.example.com/oauth2/authorization/google
     (the Spring generates the state, the nonce, the PKCE)

  ② 302 → https://accounts.google.com/o/oauth2/v2/auth?
            client_id=123.apps.googleusercontent.com&
            redirect_uri=https://app.example.com/oauth2/callback/google&
            response_type=code&
            scope=openid%20profile%20email&
            state=6B3C1A2F&
            nonce=9f2a7c1e&
            code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
            code_challenge_method=S256&
            prompt=consent

  ③ the user authenticates at Google → consents

  ④ 302 → https://app.example.com/oauth2/callback/google?code=4/0AX...&state=6B3C1A2F

  ⑤ the Spring validates: the state == the session's state ✅
     the Spring POSTs:
       POST https://oauth2.googleapis.com/token
         code=4/0AX...&
         client_id=123.apps.googleusercontent.com&
         client_secret=...&
         redirect_uri=https://app.example.com/oauth2/callback/google&
         grant_type=authorization_code&
         code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk

  ⑥ 200 {
       "access_token": "ya29.a0AfB_byC...",
       "expires_in": 3599,
       "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
       "refresh_token": "1//0eXy...",
       "token_type": "Bearer",
       "scope": "openid https://www.googleapis.com/auth/userinfo.email ..."
     }

  ⑦ the Spring validates the ID token:
       the signature (via Google's JWKS)  ✅
       iss == "https://accounts.google.com"  ✅
       aud contains the client id  ✅
       exp in the future  ✅
       nonce == the session's nonce  ✅
       at_hash matches the access token  ✅

  ⑧ the OidcUser is built:
       { "name": "Alice Smith", "email": "alice@example.com",
         "emailVerified": true, "provider": "google",
         "attributes": { "sub": "110248495921238986420" } }
```

### 5.2 The ID token's decoded claims

```json
{
  "iss": "https://accounts.google.com",
  "azp": "123456789-abc.apps.googleusercontent.com",
  "aud": "123456789-abc.apps.googleusercontent.com",
  "sub": "110248495921238986420",
  "email": "alice@example.com",
  "email_verified": true,
  "name": "Alice Smith",
  "given_name": "Alice",
  "family_name": "Smith",
  "picture": "https://lh3.googleusercontent.com/a/ACg8oc...",
  "iat": 1735689000,
  "exp": 1735692600,
  "nonce": "9f2a7c1e-4b8d-...",
  "at_hash": "77QmUPtjPfzWtF2AnpK9RQ=="
}
```

### 5.3 The Keycloak's token (the roles' placement — the dry run)

```json
{
  "exp": 1735692600,
  "iat": 1735689000,
  "jti": "f2b8a1c4-...",
  "iss": "https://as.example.com/realms/prod",
  "aud": "account",
  "sub": "b3a9c1d2-...",
  "typ": "Bearer",
  "azp": "web-app",
  "session_state": "3d4e5f6a-...",
  "acr": "1",
  "allowed-origins": ["https://app.example.com"],
  "realm_access": { "roles": ["user"] },
  "resource_access": {
    "account": { "roles": ["manage-account", "view-profile"] },
    "orders-api": { "roles": ["orders:read", "orders:admin"] }
  },
  "scope": "openid profile email orders:read orders:admin",
  "email_verified": true,
  "preferred_username": "alice"
}
```

**The reading:**
- `realm_access.roles` → `["user"]` — the realm's roles.
- `resource_access["orders-api"].roles` → the client's roles for the orders API.
- `scope` → the effective's scopes (the optional's client scopes that were enabled).
- `aud: "account"` → the **account's console's audience** — the default. **This is a
  problem** if your API validates the audience: you need the **audience's mapper** to
  add your API as an audience.

### 5.4 The "the account's linking" (the dry run — the Keycloak's broker)

```text
  the problem: the user signed up with a password, then tries "Sign in with Google"
  → by default, the Keycloak creates a SECOND's user (the Google identity maps to a
    new local's user) → the user is confused ("my history is gone")

  the FIX: the account's linking (the Keycloak's feature)
  ① the app detects the email's match → shows "you already have an account; link it?"
  ② the app redirects to:
       GET /realms/prod/broker/google/link?code=<the Google code>
       &client_id=web-app&client_secret=...&redirect_uri=...
       &currentBroker=google&currentBrokerAccount=<the Google user's id>
  ③ the Keycloak links the Google identity to the EXISTING's local user
  ④ the user is logged in as the original's user ✅
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The OIDC's login (the Spring's configuration)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.List;

@Configuration
public class OAuth2ClientConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/login/**", "/oauth2/**", "/error").permitAll()
                .anyRequest().authenticated())
            // the OAuth2's login's endpoints:
            //   /oauth2/authorization/{registrationId}  → the redirect to the provider
            //   /oauth2/callback/{registrationId}        → the redirect back
            .oauth2Login(o -> o
                .authorizationEndpoint(authorization -> authorization
                        .baseUri("/oauth2/authorization")
                        .authorizationRequestResolver(
                                authorizationRequestResolver("google")))
                .redirectionEndpoint(redirection -> redirection
                        .baseUri("/oauth2/callback/*"))   // the * is required!
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(oidcUserService()))
                .successHandler(oauth2SuccessHandler()));
        return http.build();
    }

    /**
     * The PKCE is enabled by default for the public's clients.
     * The S256 is REQUIRED for the confidential's clients too (the OAuth 2.1's rule).
     */
    private DefaultOAuth2AuthorizationRequestResolver authorizationRequestResolver(String registrationId) {
        DefaultOAuth2AuthorizationRequestResolver resolver =
                new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository(), "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(builder ->
                builder.additionalParameters(params -> {
                    params.put("prompt", "consent");     // the consent's screen (or "select_account")
                    // params.put("access_type", "offline");  // the Google-specific's refresh token
                }));
        return resolver;
    }

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration google = ClientRegistration.withRegistrationId("google")
                .clientId("123.apps.googleusercontent.com")
                .clientSecret("...")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/oauth2/callback/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://accounts.google.com/o/oauth2/v2/auth")
                .tokenUri("https://oauth2.googleapis.com/token")
                .userInfoUri("https://openidconnect.googleapis.com/v1/userinfo")
                .userNameAttributeName("sub")   // ⚠ the OIDC's sub, not "email"
                .clientName("Google")
                .build();
        return new InMemoryClientRegistrationRepository(List.of(google));
    }
}
```

### 6.2 The custom's `OidcUserService` (the user's mapping + the account's linking)

```java
package com.example.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.userinfo.*;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.*;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CustomOidcUserService implements OAuth2UserService<OidcUserRequest, OidcUser> {

    private final UserService userService;   // your local's users
    private final LinkedAccountService linkedAccountService;

    @Override
public OidcUser loadUser(OidcUserRequest request) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(request);   // the validation happens HERE

        String provider = request.getClientRegistration().getRegistrationId();
        String providerUserId = oidcUser.getSubject();          // ⚠ the STABLE's id
        String email = oidcUser.getEmail();
        boolean emailVerified = oidcUser.getEmailVerified();

        // ① find the local's user by the LINKED's account (the provider's id)
        Optional<AppUser> linked = linkedAccountService.find(provider, providerUserId);
        if (linked.isPresent()) {
            return toOidcUser(oidcUser, linked.get());
        }

        // ② no's link → by the email (the account's linking's flow)
        if (email != null && emailVerified) {
            Optional<AppUser> byEmail = userService.findByEmail(email);
            if (byEmail.isPresent()) {
                // ⚠ the SECURITY's decision: auto-link, or ask the user to confirm?
                //    auto-linking is a takeover's RISK if the provider's email's
                //    verification is weak. The safe path: send a confirmation's email.
                linkedAccountService.link(byEmail.get(), provider, providerUserId);
                return toOidcUser(oidcUser, byEmail.get());
            }
        }

        // ③ a brand's new user
        AppUser created = userService.createFromProvider(provider, providerUserId, email, oidcUser.getName());
        return toOidcUser(oidcUser, created);
    }

    private OidcUser toOidcUser(OidcUser oidcUser, AppUser local) {
        Map<String, Object> claims = new HashMap<>(oidcUser.getClaims());
        claims.put("local_user_id", local.getId());
        claims.put("local_role", local.getRole().name());
        return new DefaultOidcUser(oidcUser.getAuthorities(), oidcUser.getId(),
                oidcUser.getUserName(), oidcUser.getEmail(), emailVerified(oidcUser), claims);
    }

    private boolean emailVerified(OidcUser user) {
        Object v = user.getClaims().get("email_verified");
        return v instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(v));
    }
}
```

### 6.3 The custom's `OAuth2SuccessHandler` (the session's creation + the JWT's issue)

```java
package com.example.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final TokenService tokenService;
    private final AppUserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oauthUser = ((OAuth2AuthenticationToken) authentication).getPrincipal();
        String providerUserId = oauthUser.getAttribute("sub");
        String provider = ((OAuth2AuthenticationToken) authentication).getAuthorizedClientRegistrationId();

        AppUser user = userService.findByLinkedAccount(provider, providerUserId)
                .orElseThrow(() -> new IllegalStateException("The user was not resolved"));

        // ⚠ issue YOUR OWN's token — NEVER forward the provider's ID token to the browser
        String accessToken = tokenService.issueAccessToken(user);
        String refreshToken = tokenService.issueRefreshToken(user);

        // the SPAs: set the token in an httpOnly's cookie (the safest)
        // the native's apps: return it in the body
        response.setHeader("Set-Cookie",
                "refresh_token=" + refreshToken + "; HttpOnly; Secure; SameSite=Strict; Path=/api/auth");
        response.setContentType("application/json");
        response.getWriter().write("{\"access_token\":\"" + accessToken + "\"}");
    }
}
```

### 6.4 The ID token's validation (the manual's decoder — for the non-Spring's paths)

```java
package com.example.auth;

import com.nimbusds.jwt.*;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jose.jwk.source.*;
import com.nimbusds.jose.proc.*;
import com.nimbusds.jose.util.*;
import com.nimbusds.oauth2.sdk.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.text.ParseException;
import java.util.*;

@Component
@RequiredArgsConstructor
public class IdTokenValidator {

    private final DiscoveryService discoveryService;   // caches the discovery + the JWKS

    /**
     * The ID token's validation — EVERY check, in order.
     */
    public JWTClaimsSet validate(String idTokenString, String expectedClientId,
                                 String expectedNonce, String accessToken) throws Exception {

        SignedJWT jwt = SignedJWT.parse(idTokenString);

        // ① the SIGNATURE (the JWKS's key, the alg is PINNED)
        JWSVerifier verifier = new RSASSAVerifier(
                discoveryService.getJwkSet().getKeyByKeyId(jwt.getHeader().getKeyID()));
        if (!jwt.verify(verifier)) {
            throw new IllegalArgumentException("The ID token's signature is invalid");
        }
        // ⚠ the alg is pinned — never trust the header's alg
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new IllegalArgumentException("Unexpected the ID token's algorithm");
        }

        JWTClaimsSet claims = jwt.getJWTClaimsSet();

        // ② the ISS — the EXACT's match
        if (!Objects.equals(claims.getIssuer(), discoveryService.getIssuer())) {
            throw new IllegalArgumentException("The issuer's mismatch");
        }

        // ③ the AUD — must CONTAIN your client id
        List<String> audience = claims.getAudience();
        if (audience == null || !audience.contains(expectedClientId)) {
            throw new IllegalArgumentException("The audience's mismatch");
        }

        // ④ the EXP
        if (claims.getExpirationTime() == null
                || claims.getExpirationTime().before(new Date(System.currentTimeMillis() - 60_000))) {
            throw new IllegalArgumentException("The ID token has expired");
        }

        // ⑤ the NONCE — the session's binding
        if (expectedNonce != null && !Objects.equals(expectedNonce, claims.getStringClaim("nonce"))) {
            throw new IllegalArgumentException("The nonce's mismatch — a replay's risk");
        }

        // ⑥ the AZP — required when the aud has multiple's entries
        if (audience.size() > 1 && !Objects.equals(expectedClientId, claims.getStringClaim("azp"))) {
            throw new IllegalArgumentException("The azp's mismatch");
        }

        // ⑦ the AT_HASH — the mix-up's defence
        if (accessToken != null) {
            String expectedAtHash = computeAtHash(accessToken, claims.getIssuer());
            if (!expectedAtHash.equals(claims.getStringClaim("at_hash"))) {
                throw new IllegalArgumentException("The at_hash's mismatch");
            }
        }

        // ⑧ the SUB — the identity's anchor
        if (claims.getSubject() == null || claims.getSubject().isBlank()) {
            throw new IllegalArgumentException("The sub is missing");
        }

        return claims;
    }

    /**
     * The at_hash = base64url( the left-most half of the SHA-256(access token) )
     */
    private String computeAtHash(String accessToken, String issuer) {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] hash = digest.digest(accessToken.getBytes(StandardCharsets.US_ASCII));
        byte[] half = Arrays.copyOf(hash, hash.length / 2);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(half);
    }
}
```

### 6.5 The Keycloak's realm's configuration (the Terraform)

```hcl
# the realm
resource "keycloak_realm" "prod" {
  realm        = "prod"
  enabled      = true
  ssl_required = "external"

  access_token_lifespan = "300"    # 5 minutes
  sso_session_idle_timeout = "1800"
  sso_session_max_lifespan  = "36000"
  revoke_refresh_token     = true   # ⚠ the rotation's ON
  refresh_token_max_reuse  = 0      # ⚠ 0 = NO reuse → the reuse's DETECTION's ON
}

# the API's client (the resource's server)
resource "keycloak_client" "orders_api" {
  realm_id  = keycloak_realm.prod.id
  client_id = "orders-api"
  name      = "Orders API"

  public_client = false
  service_accounts_enabled = true

  standard_flow_enabled            = false   # it doesn't accept the users' logins
  direct_access_grants_enabled     = false
  implicit_flow_enabled            = false

  access = {
    "roles" = ["orders:read", "orders:write", "orders:admin"]
  }
}

# the web's client (the confidential's client)
resource "keycloak_client" "web_app" {
  realm_id  = keycloak_realm.prod.id
  client_id = "web-app"

  public_client = false
  standard_flow_enabled = true
  redirect_uris = ["https://app.example.com/*"]   # ⚠ the narrowest's possible
  web_origins   = ["https://app.example.com"]

  attributes = {
    "pkce.code_challenge.method" = "S256"
  }

  default_client_scopes = ["profile", "email", "roles"]
  optional_client_scopes = ["offline_access", "offline_access_grant", "microprofile-jwt"]
}

# the mobile's client (the public's client, the PKCE)
resource "keycloak_client" "mobile_app" {
  realm_id  = keycloak_realm.prod.id
  client_id = "mobile-app"
  public_client = true
  standard_flow_enabled = true
  direct_access_grants_enabled = false   # ⚠ the password's grant is DISABLED
  redirect_uris = ["com.example.app://callback"]
  attributes = {
    "pkce.code.challenge.method" = "S256"
  }
}

# the Google as the identity's provider
resource "keycloak_identity_provider" "google" {
  realm = keycloak_realm.prod.id
  provider_id = "google"
  provider_name = "Google"
  enabled = true

  config = {
    "clientId"     = var.google_client_id
    "clientSecret" = var.google_client_secret
  }
}
```

### 6.6 The resource's server with the Keycloak (the audience + the roles' mapping)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.*;

@Configuration
public class KeycloakResourceServerConfig {

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withIssuerLocation("https://as.example.com/realms/prod")
                .build();
        // the audience's validation — ⚠ without it, a token for another's API is accepted
        OAuth2TokenValidator<Jwt> audienceValidator = jwt -> {
            List<String> aud = jwt.getAudience();
            if (aud != null && aud.contains("orders-api")) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The required's audience is missing", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer("https://as.example.com/realms/prod"),
                audienceValidator));
        return decoder;
    }

    /**
     * The CLAIMS's mapping: the scopes AND the Keycloak's client's roles.
     */
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        Converter<Jwt, Collection<GrantedAuthority>> combined = jwt -> {
            Set<GrantedAuthority> authorities = new LinkedHashSet<>(scopes.convert(jwt));

            // the realm's roles
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> roles) {
                roles.forEach(r -> authorities.add(
                        new SimpleGrantedAuthority("ROLE_" + r.toString().toUpperCase())));
            }

            // the CLIENT's roles (the Keycloak's resource_access)
            Map<String, Object> resourceAccess = jwt.getClaim("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((client, perms) -> {
                    if (perms instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles) {
                        roles.forEach(r -> authorities.add(
                                new SimpleGrantedAuthority(client + ":" + r)));
                    }
                });
            }
            return authorities;
        };

        return new JwtAuthenticationConverter() {{
            setJwtGrantedAuthoritiesConverter(combined);
        }};
    }
}
```

### 6.7 The OIDC's login (the backend's client, the Spring's `ClientRegistration`)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;

import java.util.List;

@Configuration
public class OidcClientConfig {

    @Bean
    public ClientRegistrationRepository clientRegistrationRepository() {
        // the OIDC's discovery — the endpoints and the JWKS are fetched automatically
        ClientRegistration keycloak = ClientRegistration
                .withRegistrationId("keycloak")
                .clientId("web-app")
                .clientSecret("...")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "profile", "email")
                .authorizationUri("https://as.example.com/realms/prod/protocol/openid-connect/auth")
                .tokenUri("https://as.example.com/realms/prod/protocol/openid-connect/token")
                .userInfoUri("https://as.example.com/realms/prod/protocol/openid-connect/userinfo")
                .jwkSetUri("https://as.example.com/realms/prod/protocol/openid-connect/certs")
                .userNameAttributeName("preferred_username")
                .clientName("Keycloak")
                .build();
        return new InMemoryClientRegistrationRepository(List.of(keycloak));
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | Trusting the ID token's claims from the browser | The server validates the ID token | The browser's claims are attacker-controlled |
| 2 | The ID token used as the API's bearer token | The access token | The audience's mismatch, the replay's risk |
| 3 | Skipping the `nonce` validation | Validate it (if you sent one) | The ID token's injection |
| 4 | Not validating the `aud` | The audience's validator | Another's client's token is accepted |
| 5 | Not validating the `iss` exactly | The exact's match | The issuer's confusion |
| 6 | Trusting the token's header's `alg` | Pin the algorithm | The `alg: none` / the algorithm's confusion |
| 7 | The `email` trusted without `email_verified` | Require `email_verified: true` | The account's takeover |
| 8 | The `email` as the user's primary's key | The `(issuer, sub)` as the key | The email's change, the provider's collision |
| 9 | The social's provider's email used for the account's linking without's confirmation | Confirm by's email | The takeover's risk |
| 10 | The Keycloak's identity's mapping creating a duplicate's user | The account's linking's flow | The user's confusion |
| 11 | The `redirect_uris` with a wildcard | The narrowest's possible | The open's redirect |
| 12 | The discovery's URL at the host's root | The issuer's path + the suffix | The Keycloak's realms are path-based |
| 13 | The `offline_access` missing | Request it | No refresh token |
| 14 | The refresh's token's reuse allowed | `revoke_refresh_token = true`, `refresh_token_max_reuse = 0` | The theft's undetected |
| 15 | The scope's claim as the Spring's `SCOPE_` prefix, but the code compares without it | The consistent's naming | The 403s |
| 16 | The Keycloak's `aud: account` not validated | The audience's mapper | The cross-client's tokens |
| 17 | The `id_token_hint`/`login_hint` trusted as the identity's input | The provider's decision | The account's confusion |
| 18 | The `prompt=none` failing as an error | Handle `login_required` | The silent's renewal |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What's the difference between OAuth2 and OpenID Connect?**
**A:** OAuth2 is an **authorization** framework — it lets an app access a resource on a
user's behalf (the scopes, the access token, the delegates). OIDC is an **authentication**
layer on top of it — it tells you **who the user is** (the ID token, the UserInfo
endpoint, the standard's claims). OIDC uses the OAuth2's flows; the difference is the
`openid` scope and the ID token.

**Q: What is the ID token?**
**A:** A JWT signed by the identity's provider that contains the user's identity claims
(the `sub`, the `iss`, the `aud`, the `exp`, and the profile's claims). Your server
validates it to learn who the user is. It's meant for your client application, not for
the resource's API.

### 🟡 Intermediate
**Q: How do you implement "login with Google" in Spring Boot?**
**A:** Register the app at Google, configure the `ClientRegistration` (the client id and
secret, the scopes `openid profile email`), and enable `oauth2Login()`. The Spring
handles the redirect to Google, the callback, the code's exchange, the ID token's
validation, and the `OidcUser` creation. Then a `successHandler` creates **my own**
session or JWT — never forwarding Google's token.

**Q: What is the nonce and why is it needed?**
**A:** A random value the client generates, sends in the `/authorize` request, and stores
in the session. The provider echoes it into the ID token, and the client verifies it
matches. It binds the ID token to the specific login's session, so an attacker can't
substitute a token from their own login session for the user's. It's the defence against
the **ID token's injection**.

**Q: How do you find a user who logged in with a social provider?**
**A:** By the **provider + the `sub`** pair, not the email. The `sub` is the stable's
identifier at that issuer. The email can change and can be unverified. Store the
`(provider, subject)` as the unique's key of the linked's account, and treat the email
as a mutable's attribute.

### 🔴 Advanced / Senior
**Q: Why don't you trust the email from a social provider?**
**A:** Because the email's **verification** is not guaranteed and the identity's anchor
must be stable. Some providers return `email_verified: false`, some let a user set an
arbitrary email, and emails can change or be recycled. Using the email as the primary's
key enables the **account's takeover**: an attacker registers with someone else's email
at a weak provider, and your auto-linking logic then hands them that account. The correct
model: identify by `(issuer, sub)`, require `email_verified: true` before you use the
email for anything consequential, and make the account's linking an **explicit,
verified** action (send a confirmation's email) rather than an automatic's match.

**Q: What is the difference between the ID token and the access token?**
**A:** The **audience**: the ID token's audience is your client; the access token's
audience is the resource's API. The **purpose**: the ID token is an identity's assertion
to the client; the access token is a capability's grant to the API. The **lifetime**: the
ID token is short (an hour) and the access token is usually shorter. The **content**: the
ID token has the user's claims; the access token has the scopes. The **consumers**: your
server validates the ID token; your API validates the access token. The **mix-up's risk**:
if you swap them (or accept the wrong one's audience), you get the vulnerability — hence
the `at_hash` and the `azp` claims.

**Q: How would you implement single sign-on with Keycloak for 30 internal applications?**
**A:** One realm (or a realm per environment), 30 confidential's clients (each with its
own redirect URIs and its own roles), and each app configured as an OIDC's client. The
roles live in the realm or the clients; the apps' authorisation is derived from the
token's claims. The apps' authorisation is derived from the token's claims. The
scaling's consideration: the realm's user count and the SSO's session's load; the
Keycloak's clustering for HA; the per-app's client secrets in the secret's manager; and
the "the local's admin" — usually a local's role in the app's DB, because the Keycloak
is the identity's provider but the app's authorisation may need its own's data.

### ⚫ Expert / Principal
**Q: Design the identity's strategy for a company with 5 B2B tenants, each with their
own's users, some wanting SSO (SAML/OIDC) into the customer's IdP, plus a consumer's
mobile app.**
>
> (1) The **the tenant's model** — a realm per tenant (the isolation's maximum) vs a
shared realm with the groups (the isolation's minimum). The choice's driver: the
**regulatory's requirements** and the **tenants' willingness to pay** for the isolation
(the dedicated realm is an enterprise's feature). The middle's ground: a shared realm
with the **organization's feature** (the Keycloak's organizations) giving the logical's
isolation without the operational's burden of many realms; (2) the **the identity's
federation** — the customers' IdPs via the identity's providers (the OIDC's or the SAML's)
with the **just-in-time's provisioning** and the account's linking; (3) the **the
consumer's app** — a public's client with the PKCE and the **native's app's** best
practice (the AS's Registered's app — the claimed's HTTPS scheme, the reverse's DNS's
verification, the associated's domains) — the Apple/Google's requirements; (4) the **the
trust's federation** (the SAML's broker) if the customers' IdPs are SAML-only; (5) the
**the governance** — the central's authentication (the admins), the break-glass's
accounts, the local's emergency's access; (6) the **the auditing** — the central's
audit's log with the per-tenant's segregation and the export; (7) the **the migration**
— from the app's local's passwords to the IdP (the phased's, with the local's fallback
and the forced's re-authentication).

---

## 9. Counter- Questions & Traps

### CQ1. "Why do we need an ID token? Can't the client just call the UserInfo endpoint?"
- **Naive answer:** "The UserInfo endpoint gives the user's data."  ← the trap
- **The trap:** The interviewer wants the *reason the ID token exists*.
- **Expert answer:** The ID token exists because the **UserInfo endpoint requires a
network's call and an access token**, and it can't be used at the moment the client
**needs** the identity. Three concrete's reasons:
(1) **The immediate's availability** — the ID token arrives in the **authorization's
response**, so the client knows who the user is **without a second's round trip** and
without needing the access token. The UserInfo endpoint would add a network's call to
every login, and if the UserInfo endpoint is down, the login breaks even though the
authorization succeeded.
(2) **The client's verification's cost** — the ID token is **self-contained**: it's a
signed JWT that the client can verify with the JWKS's keys it already has, **offline**
(no network, no API's call, no rate's limit on the provider). The UserInfo endpoint's
response is a **JSON's blob** whose authenticity depends entirely on the transport's
security — you must make a network's call to it and trust the TLS.
(3) **The session's binding** — the ID token carries the **nonce** and the `at_hash`,
which bind it to the specific's request and the specific's token. The UserInfo endpoint
can't carry that binding. That's the whole's point of the ID token: it's a **signed,
self-contained, session-bound's statement of identity** that the client can verify
without trusting the network or the provider's availability.
(4) **The UserInfo endpoint's own's role** — it exists for the **claims not in the ID
token** (the groups, the organisation, the custom's attributes) and for the **fresh's
data** (the user's name changed after the login). The idiomatic's pattern: **use the ID
token for the identity, the UserInfo endpoint for the enrichment** — but **always
cross-check** that the UserInfo's `sub` matches the ID token's `sub` (that's the
requirement's purpose: binding the two responses to the same's user).
The framing: *"the ID token is the identity's **proof** — signed, self-contained, and
session-bound, so the client can verify it offline. The UserInfo endpoint is the
identity's **enrichment** — it needs a live's call and a bearer token, so it's for the
claims the ID token doesn't carry, and its `sub` must be cross-checked against the ID
token's."*

### CQ2. "Why is the ID token signed rather than encrypted?"
- **Naive answer:** "Signing proves it's genuine."  ← the trap
- **The trap:** The interviewer wants the *whole* reason, including what encryption
would and wouldn't achieve.
- **Expert answer:** The ID token is a **statement** to the client about the identity.
The client must be able to **verify** it (the signature) — that's the whole's purpose.
The encryption (the JWE) would do the opposite: it would hide the content from the
client, which is exactly what the client needs to read. So the ID token is **signed,
not encrypted** — and that means **the claims are visible** to anyone holding the token
(the browser included). The consequences to name: the ID token must **not** contain
secrets (no password, no PII beyond the minimum's claims — the profile's claims like the
name and email are visible to the browser and to any's interceptor who can read it,
though the TLS protects it in transit).
The subtle's point: the **hybrid's flow** (where the ID token comes back in the
**front channel**, via the URL's fragment) is where the encryption becomes relevant —
the `response_type=id_token` and `code id_token` returns the ID token through the
browser, and there the JWE (the encrypted's response) is the modern's recommendation
because the front channel is untrusted. And the `at_hash`/`c_hash` exist because the ID
token and the access token travel on **different's channels** (one in the front, one in
the back) — the hash's binding proves they belong to the same's authorization's response
without revealing the access token. The framing: *"the ID token is a **statement to the
client**, so it must be verifiable — the signature's proof. It is not encrypted because
the client has to read the claims; that's why the ID token must carry no secrets. The
one's place the encryption's matter is the front channel (the hybrid's flow), where the
token passes through the browser and the JWE's encrypted's response is the modern's
recommendation."*

### CQ3. "Why can't I just use the email as the user's primary key?"
- **Naive answer:** "It's unique."  ← the trap
- **The trap:** The interviewer wants the security's reason, and the data's reason.
- **Expert answer:** Three's problems, in increasing's order of importance:
(1) **The data's problem** — emails **change** (people change their names, companies
re-domain, people get married). If the email is the primary's key, a change breaks the
identity, and the user's data is tied to a mutable's attribute. The stable's anchor is
the **`(issuer, sub)`** pair: the provider's id for the user, which is guaranteed stable
for the lifetime of the account at that issuer.
(2) **The uniqueness's problem** — an email is unique at **one** provider, not across
them. Two different providers can (and do) issue the same email to different people
(Gmail's plus-addressing, the corporate's aliasing, the recycled's addresses). If you
key globally by the email, those two accounts **collide** — and one user sees another's
data. That's a data's leak, not just a's bug.
(3) **The security's problem** — the account's **takeover**. If your auto-linking logic
matches by the email, and one provider lets a user set an unverified email, an attacker
sets the victim's email at that provider, signs in, and your logic links them to the
victim's account. The mitigation is a chain: require **`email_verified: true`** from the
provider, and make the account's linking an **explicit, confirmed** action (send a
confirmation's email to the address before linking) rather than an automatic's match.
The framing: *"the email is an **attribute** of a user, not the user's identity. The
identity is the `(issuer, sub)` — the provider's stable's id. Keying by the email gives
you the breakage when it changes, the collision across the providers, and the account's
takeover when a weak's provider lets an attacker set someone else's email. The email is
still useful — as a lookup's hint and as a 'does this look like the same's person?'
signal — but the link must be **confirmed**, not inferred."*

### CQ4. "The Keycloak creates a duplicate user when the user signs in with Google. Why?"
- **Naive answer:** "It's a Keycloak's bug."  ← the trap
- **The trap:** The interviewer wants the identity's mapping's model.
- **Expert answer:** It's not a bug — it's the **identity's mapping's model** in action.
In the Keycloak, a user is a **local's account** with **federated's identities**
attached. A federated's identity is a link: `(the identity's provider, the provider's
user id)` → the local's user. When the user logs in via Google and there's **no** link
for `(google, <the google's sub>)`, the Keycloak's default is to **create a new's local
user** and attach the Google identity to it. So a user who first signed up with a
password and later signs in with Google (with the same email) gets **two local's users**
— the password one and the Google one — because the default does **not** match by email
(the email is a mutable's attribute, and the auto-linking by email is exactly the
account's takeover's risk). The **fixes**, in the order of preference:
(1) **The account's linking's flow** (the `broker` + `link` endpoints) — the app detects
the email's match, asks the user to confirm (ideally by sending a confirmation's email),
and then links the identity to the **existing** user. This is the **explicit's** and
**safe's** approach, and it's the one to recommend.
(2) **The "the first's login's flow"** (the Keycloak's feature) — the user authenticates
with the existing's method first, then the social's identity is attached. Also explicit.
(3) **The trusted's providers** (the Keycloak's config: a "the trusted's provider" can
add the user's roles on the first's login) — a policy's hook, not a linking's mechanism.
(4) **A custom's user's storage's provider** — you can implement the linking's logic
yourself in Java (match by email + verify), which gives you full's control but also
full's responsibility for the security.
The framing to give: *"the duplicate's user is the default because the email is not a
trustworthy's join's key — auto-linking by email is the account's takeover's hole. The
right's answer is the **explicit's linking**: the app detects the probable's match, asks
the user to confirm **via an email's verification**, and then attaches the federated's
identity to the existing's user. The Keycloak's `broker/link` endpoint does exactly
that. The alternative — the 'first's login's flow' — puts the existing's credential
first, which is even safer, because the user proves both the identities."*

### CQ5. "Why does the Spring's `oauth2Login` use `userNameAttributeName`, and what if I
get it wrong?"
- **Naive answer:** "It's the field of the username."  ← the trap
- **The trap:** The interviewer wants the `sub`-vs-`email` subtlety.
- **Expert answer:** For an OIDC's provider, the attribute to use is the **`sub`** — the
subject's identifier. For a plain's OAuth2's provider (the GitHub's, the Twitter's),
there's no `sub`, so you point it at the provider's unique's id field (the GitHub's
`id`, the Twitter's `id_str`). The **why it matters**:
(1) **The stability** — the `sub` is guaranteed stable and unique **at that issuer**.
The `email` is neither (it can change, and it can be shared/unverified).
(2) **The uniqueness's scope** — the `sub` is unique **within the issuer**; the `email`
is unique across the internet only in theory. The Spring's default for an OIDC's
registration is `sub`; for a non-OIDC's provider, the default is `name` — and that's
often wrong, because the `name` (the display's name) **is not unique** and can change.
(3) **The wrong's choice's consequence** — if you set it to `email` and two accounts
have the same email, the Spring's `OAuth2User` will have the same's `name` and your
account's lookup may **merge or fail** the wrong's user. And if you set it to a
non-existent field, the Spring's throws at the token's response's time
("UserInfo response does not contain the attribute").
(4) **The `username` vs the `name` distinction in the Spring's** — the
`userNameAttributeName` is used to populate the `OAuth2User.getName()`, which is what
ends up as the `Principal.getName()`. That's the value your application's user-locating
code should key on. The Spring's `OidcUser` also has `getSubject()` for the `sub`
specifically, and `getEmail()`/`getEmailVerified()` for the email's attributes.
(5) **The nuance to raise for the OIDC's providers**: you should generally key your
local's user on the **`(provider, sub)`** pair, not on the username's attribute alone —
the Spring's `getName()` is just a convenience's projection of it. The framing: *"the
attribute is the key your local's user is looked up by — it must be **stable** and
**unique within the provider**. For the OIDC's providers that's the `sub`, which is
guaranteed; for the plain's OAuth2's providers (the GitHub's) it's the provider's numeric
`id`. The `email` fails on both's counts: it changes and it isn't guaranteed unique. The
Spring's default for a non-OIDC's provider is `name` — the display's name — which is
wrong for the production, and it's the classic's misconfiguration."*

---

## 10. Rapid-Fire MCQs

**1.** OIDC is a layer on top of:
A) JWT  B) OAuth2  C) TLS  D) SAML
**✅ B**

**2.** The ID token's `sub` is:
A) The email  B) The user's id at the issuer  C) The client  D) The scope
**✅ B**

**3.** The ID token's `aud` identifies:
A) The resource's API  B) The client  C) The issuer  D) The user's org
**✅ B**

**4.** The `nonce` defends against:
A) The CSRF  B) The ID token's injection / the replay  C) The XSS  D) The SQL injection
**✅ B**

**5.** The `openid` scope is required to receive:
A) The access token  B) The refresh token  C) The ID token  D) The userinfo
**✅ C**

**6.** The `offline_access` scope is required to receive:
A) The ID token  B) The refresh token  C) The userinfo  D) The JWKS
**✅ B**

**7.** The UserInfo endpoint's `sub` must:
A) Match the ID token's `sub`  B) Be the email  C) Be the client id  D) Not exist
**✅ A**

**8.** The `email_verified` claim protects against:
A) The CSRF  B) The account's takeover  C) The slow's hash  D) The replay
**✅ B**

**9.** The discovery document's URL for an issuer with a path:
A) Host's root + the suffix  B) The issuer's path + the suffix  C) The token's endpoint  D) The JWKS
**✅ B**

**10.** The `at_hash` binds:
A) The ID token and the access token  B) The client and the user  C) The scopes  D) The keys
**✅ A**

**11.** The `prompt=none` means:
A) Log out  B) No interaction; fail if not already's authenticated  C) Force the login  D) Skip the consent
**✅ B**

**12.** The `max_age` parameter controls:
A) The token's TTL  B) The authentication's freshness  C) The scopes  D) The audience
**✅ B**

**13.** A Keycloak's **realm** is:
A) A client  B) A tenant's boundary  C) A user  D) A role
**✅ B**

**14.** A Keycloak's client's roles appear in:
A) `realm_access.roles`  B) `resource_access.<client>.roles`  C) `scope`  D) `groups`
**✅ B**

**15.** The `azp` claim is required when:
A) The token is expired  B) The `aud` has multiple's entries  C) The nonce is missing  D) The email is unverified
**✅ B**

**16.** The Keycloak's `refresh_token_max_reuse = 0` enables:
A) The long's TTL  B) The reuse's detection  C) The token's caching  D) The audience's mapping
**✅ B**

**17.** The OIDC's **back-channel logout** is:
A) The iframe  B) The server-to-server's POST  C) The redirect  D) The token's revocation
**✅ B**

**18.** The `sub` is unique within:
A) The internet  B) The issuer  C) The client  D) The email's domain
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The "login with Google" (the local's Keycloak)**
1. Start the Keycloak (`docker run -p 8080:8080 -e KEYCLOAK_ADMIN=admin quay.io/keycloak/keycloak:latest start-dev`).
2. Create the realm, the client (the public's client, the PKCE's S256), the redirect URI.
3. Add the Google as the identity's provider (or a fake one).
4. Log in and inspect the ID token's claims at `jwt.io`.

**🧪 Lab 2 — The ID token's validation (the negative's tests)**
1. Fetch an ID token.
2. Tamper with the payload (change the `aud`) → the signature's failure.
3. Change the `nonce` → the mismatch.
4. Change the `iss` → the mismatch.
5. Assert each is rejected.

**🧪 Lab 3 — The discovery document**
```bash
curl -s https://as.example.com/realms/prod/.well-known/openid-configuration | jq .
```

**🧪 Lab 4 — The account's linking**
1. Sign up with a password → the user A.
2. Sign in with Google (the same email) → the user B (the duplicate).
3. Implement the linking's flow → the users merge.

**🧪 Lab 5 — The Keycloak's realm as code**
```bash
docker run --rm -p 8080:8080 quay.io/keycloak/keycloak:latest start-dev --http-relative-path=/kc
# then: the admin's CLI
docker exec ... kcadm.sh config credentials --server http://localhost:8080/kc \
  --realm master --user admin --password admin
docker exec ... kcadm.sh create realms -s realm=prod -s enabled=true
```

---

## 12. Code Challenges

### Challenge 1 — The OIDC's provider's abstraction (medium-hard)
**Goal:** A provider-agnostic's login that works with the OIDC's providers (Google,
Keycloak) and the plain's OAuth2's providers (GitHub), with the unified's local's user
model keyed on `(provider, sub)`, the account's linking's flow, and the safe's email's
handling.
**Requirements:** the provider's registry, the user's mapping, the linking's confirmation
(the email's token), the tests.

### Challenge 2 — The ID token's validator (hard)
**Goal:** A standalone's validator (no Spring's Security) that performs every check —
the signature, the pinned's alg, the iss, the aud, the exp, the nonce, the azp, the
at_hash — with the JWKS's caching, the key's rotation's handling, and a clear's failure's
message per check.
**Requirements:** the JWKS's cache's refresh, the key's rotation (the `kid` not found →
refetch), the clock's skew, the test's suite (one test per failed's check).

### Challenge 3 — The multi-tenant's identity (hard)
**Goal:** A realm-per-tenant's model with the central's user's federation, the
per-tenant's branding's configuration, the break-glass's admin, and the audit's log's
partitioning.
**Requirements:** the tenant's resolution (the subdomain/host), the config's caching, the
routing's correctness, the tests.

---

## 13. Scenario-Based Questions

**S1.** *"We want to add 'Sign in with Google' to our app. The app currently has its own
username/password users. How do you do this without creating duplicate accounts or
letting someone take over an account?"*
>
> **Ideal answer:** (1) **The framing:** the requirement has two's halves that pull in
opposite's directions — a **seamless's experience** (the Google user should land in
their existing's account) and a **safe's linking** (you must not merge accounts on the
strength of an email's string alone). The design's job is to satisfy both, which means
**explicitly confirming** the linking. (2) **The identity's model:** your local's user
gets a **linked's account** table — `(provider, provider_user_id) → local_user_id`,
unique on the pair. The identity's anchor is **never** the email. The email is a
**mutable's attribute** on the user (with a flag for the verification's status and the
source's provider). (3) **The login's flow:**
① the user clicks "Sign in with Google" → the authorization code flow with the PKCE;
② the callback → the server validates the ID token (the signature, the `iss`, the `aud`,
the `exp`, the `nonce`) → you now have `(google, <the sub>)` and the email's claim;
③ **the resolution's logic**:
   - the **linked's account exists** → log them in (the common's case, the fastest's
     path);
   - **no's link, but a local's user exists with that email** → **do not log in**; send
     an **account's linking's confirmation** to the email's address (a one-time token
     with a short's expiry) and show "we found an account with this email — confirm to
     link them";
   - **no's link, no's match** → create the new's user (the email's verification's status
     recorded as it came from the provider) and the link.
④ the confirmation's link → the linked's account is created → the user is logged in.
(4) **The email's verification's rules:** only use the email for a **match's hint** if
the provider's `email_verified` is `true`; if it's false/unknown, **never** match by
the email — always create a separate's account and let the user link it explicitly (or
require a password's proof on the existing's account, which is stronger: the
"the first's login's flow" from the Keycloak). (5) **The account's takeover's
prevention (the specific's attacks to design against):**
   - the **email's spoofing** at a weak's provider → the `email_verified` requirement
     + the confirmation's email;
   - the **provider's account's reuse** — a user who changes their Google email later
     doesn't break your identity (you key on the `sub`);
   - the **the OAuth's account's linking's CSRF** — the attacker initiates the flow and
     tricks the victim into completing it (the **CSRF's protection** on the linking's
   endpoint: the `state`/the PKCE, and a **re-authentication of the existing's account**
   before linking);
   - the **the sub's reuse across's providers** — key on the **pair**, never on the
     `sub` alone.
(6) **The design's decisions to raise in the review:** (a) the **auto vs the explicit's
linking** — the explicit is safer; the auto is smoother; the recommendation is the
**explicit with a smooth's UX** (the confirmation's email is one click); (b) **the
email's changes** — when the Google email changes, do you update the local's user's
email? (the recommendation: **no**, by default — the email is a **claim**, not an
identity; update it only if it's still verified and only with a policy's decision, and
keep the old's value as an alias); (c) **the local's password's coexistence** — the
password's users keep their password's login; the social's login is an *alternative*;
the **password's reset's flow** must not be hijackable by a social's account; (d) **the
merge's flow** — if two's local's users end up with the same email (one from the
password, one from the Google), you need an **admin's merge's tool** (and a policy for
which's record wins). (7) The senior framing: "**the key's decision is to make the
Google identity a *link*, not a *key*.** The `sub` is the identity's anchor and the
email is a hint — so a user who signs in with Google and then with a password is one
person, and a person whose Google email changes is still one person. The dangerous's
part is the **linking's moment**: matching by the email alone is the account's
takeover's door, so the link is confirmed — either by a confirmation's email to the
address or by re-authenticating the existing's account (the 'first's login's flow',
which is stronger because it proves both's identities). And I'd design the linking's
endpoint with the full's CSRF's protection and a re-authentication, because the
attacker's scenario is exactly there: they initiate the flow with their own Google
account, then get the victim to complete it."

**S2.** *"Our Keycloak is configured, but users are complaining that some of them can't
log in with Google anymore, and we see duplicate users in the database. What happened?"
**
> **Ideal answer:** (1) **The framing:** the most likely's cause is the **identity's
mapping's default** in the Keycloak — a federated's login creates a **new's local
user** unless there's an existing's link for `(the provider, the provider's user id)`.
If the users' local's accounts were created **before** the social's login was enabled
(with a password), then the social's login creates a **second's user** with the same
email. They can't log in with the account they expect (the history, the orders, the
settings are on the other's user), and the admin's dashboard shows the duplicates.
(2) **The diagnosis's steps:** (a) check the **linked's accounts** — for the affected
user, is there a `federatedIdentity` row for `(google, <the sub>)`? (b) compare the
**email's** values across the duplicate's accounts; (c) check the **Keycloak's events**
(the admin's console → Events → look for the `LOGIN` and the `IDENTITY_PROVIDER_LOGIN`
entries, which show the provider and the user's id); (d) verify the **email's
verification's status** on the Google identity (if `email_verified` is false, the
auto-matching by email wouldn't have applied anyway).
(3) **The other's possible's causes to rule out:** (a) the **Google's client's
configuration** changed (the `client_id`/`secret` rotated, the redirect URI's mismatch);
(b) the **Google's app's** verification's status changed, or the app's's `test users`
setting expired (a common's cause of "it worked last week"); (c) the **scope's
change** — if the `email` scope was removed, the ID token has no email → the local's
user's creation fails; (d) the **Keycloak's realm's** configuration (the identity
provider's disabled, the client's redirect URI's changed); (e) the **federated's
settings** — the Keycloak's "the Edit mode" of the identity's provider determines what
happens when the identity's details are **changed** at the provider: "the Update
profile's mode" — if it's set to "the only's forced" and the provider's email is
unverified, the login **fails** with an error instead of creating the user. This is a
**very** common's misconfiguration: the "the force's update" mode requires a verified
email, and a provider that doesn't mark it verified breaks the login entirely.
(4) **The fix's options:**
   - **The immediate:** for the affected's users, create the **account's link** manually
     in the admin's console (the user's "the identity's provider" → "the add's
     provider" → the provider + the id) or via the admin's API, pointing it at the
     original's user. For the users who *prefer* the new's account, merge the data
     (the orders, the preferences) into it.
   - **The proper's fix (the prevention):** the **first's login's flow** or the
     **account's linking's flow** — so a social's login links to the existing's user
     instead of creating a new one. In the Keycloak, the first's login's flow means the
     user authenticates with the existing's method first, then the social's identity is
     attached. The alternative is to handle the linking in **your own's application**
     (the S1's design) — your app owns the identity's mapping, so you can match by the
     email and send a confirmation.
   - **The federated's settings:** set the identity provider's "the Edit mode" to
     **"Update only the overridden's attributes"** (or leave the existing's profile's
     attributes) so a provider's unverified email doesn't break the login.
   - **The scope's check:** ensure the `email` scope is requested and that the ID token
     carries `email_verified`.
(5) **The prevention's process:** (a) the **identity's mapping's documentation** — one
page: the anchor is `(provider, sub)`, the email is a hint, the linking is explicit;
(b) a **monitoring's dashboard** — a count of the local's users with a duplicate's email
(a simple's query — it's the leading's indicator of a mapping's problem); (c) the
**onboarding's change** — the social's login must be tested with an account that
**already exists** (the exact's case that broke); (d) the **data's migration** if the
duplicates are widespread — a one-time's script that merges them, keeping the earliest's
user id and unioning the linked's identities.
(6) The senior framing: "**the duplicate is the default's behaviour, and the diagnosis's
first question is 'was the account created before the social's login existed?'** — the
federated's identity is a *link*, and the Keycloak only creates a link when it doesn't
already have one for that `(provider, sub)`. So the fix's first step is to decide, per
user, which's record is the real one and to **merge** rather than leave the duplicate —
and the fix's second step is to make the linking explicit, so it never recurs. The
'login's broken for some users' symptom is a *different* failure than the duplicate:
that's the **federated's 'the forced's update'** setting, which fails the login when the
provider's email is unverified — a one-line's config's change. Both are the same
underlying's problem: the identity's mapping's rules were never written down, so
Keycloak's defaults (which are safe but confusing) are doing the work."

---

## 14. Tricky Interview Questions

**T1. "The `sub` is unique within the issuer. What if a user has accounts at two
providers?"**
> They are **two's identities** until you link them. Your local's user can hold multiple
linked's accounts (a list of `(provider, sub)` rows), each pointing at the same local
user. The linking's decision is the business's (and the security's) — the explicit's
confirmation is the rule.

**T2. "What's the difference between the `at_hash` and the `c_hash`?"**
> `at_hash` binds the ID token to the **access token** (both are in the authorization's
response — but on different's channels, since the access token comes in the back
channel). `c_hash` binds the ID token to the **authorization code** (used in the
**hybrid's flow**, where the ID token comes in the front channel with the code). Both
prevent a **response's mixing** — where an attacker substitutes a valid ID token from
their own's response into the victim's.

**T3. "Can you use the ID token as the API's bearer token if the audience matches?"**
> Technically you can, but **don't**. The ID token is issued to the **client**, not to
the resource's server, and it usually carries a **longer** expiry and no scopes. It's
also **visible to the browser** by design (the front channel's risk). The correct's
pattern: the browser uses the **access token** for the API's calls, and the server
validates the **ID token** only for the login's step. If you want a single token for
both, issue **your own** JWT after the login (which is the recommended's pattern for the
SPAs anyway).

**T4. "The `prompt=none` request failed with `login_required`. What do you do?"**
> That's the expected's behaviour: the user isn't authenticated at the provider, and
`prompt=none` forbids the interaction. The client's action: fall back to a **visible's
login** (redirect to the normal's authorize with the interaction's allowed). The
`login_required` error is **not** an error's condition — it's the signal to start the
interactive's flow.

**T5. "Why does the Keycloak put `aud: "account"` in the tokens by default?"**
> Because the default's audience is the **account's console's client** (`account`) — the
realm's management's client. It's a known's Keycloak's characteristic. For your API's
audience's validation, you need an **audience's mapper** (a client's scope's mapper with
the hardcoded's audience, or a protocol's mapper) to add your API's client id to the
`aud`. Alternatively, the resource's server can skip the audience's validation and rely
on the issuer + the scopes (weaker but common).

**T6. "What happens if the user's email changes at Google?"**
> Your identity is unaffected (you key on the `sub`). Whether you update your local's
email is a **policy's decision**: the recommendation is to **not** silently update it
(the provider's claim is weaker than a confirmation's email), and to keep the old's value
as an alias for the lookups. If you do update it, update it only when
`email_verified` is still true, and log the change.

**T7. "How do you log out of the app when the user logs out of the provider?"**
> The **RP-Initiated logout** handles the app-initiated's case (the app redirects the
user to the provider's `end_session_endpoint` with the `id_token_hint` and the
`post_logout_redirect_uri`, and the provider ends the session and redirects back). The
**provider-initiated's** case (the user logged out at the provider, not at your app)
needs the **back-channel's logout** (the provider POSTs to your's registered's
endpoint) or the **front-channel's logout** (the iframe — deprecated). Without one of
these, the app's session survives the provider's logout.

**T8. "Is the `nonce` required for the confidential's client?"**
> For the **authorization code** flow, yes — the OIDC's spec requires it (the client
must send it and validate the echo). For the **implicit's flow**, the nonce is the
**only** defence against the ID token's injection, so it's required there too. The
Spring's `DefaultOAuth2AuthorizationRequestResolver` adds it automatically for the OIDC's
registrations and validates it in the `OidcAuthorizationCodeAuthenticationProvider`.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the OIDC's config) → *"What if you have to support both OIDC providers
(Google) and plain OAuth2 providers (GitHub)?"*
→ Abstract behind an interface: `ExternalIdentity` (the provider, the id, the email, the
name, the verified's flag) and a `UserMapper` per provider's family. The **OIDC's
provider** path uses the Spring's `OidcUser`; the **plain's OAuth2** path uses the
Spring's `OAuth2User` with a `userNameAttributeName` pointing at the provider's id
field. The **common's model**: `(provider, providerUserId)` → the local's user.

**F2** (after the Keycloak's realm) → *"How do you handle the Keycloak's downtime?"*
→ The **token's validation's resilience**: the resource's server caches the **JWKS**
(the keys) and validates the tokens **locally** — so the API keeps working when the
Keycloak is down, as long as the cached's keys are valid. The **login** is what breaks
(no new's authentications). Mitigations: the **clustering** (the HA's deployment), the
**JWKS's caching** (the Spring's caches it and refreshes it on an unknown's `kid`), and
a **token's TTL** long enough to ride out an outage. And the **failure's mode**: the
**login's outage** should be a clean's error (the "we're having trouble signing you in"
page), not a 500.

**F3** (after the account's linking) → *"How do you prevent the linking's CSRF?"*
→ The linking's endpoint must require: (a) the **CSRF's token** (the Spring's default),
(b) the **state's parameter** in the flow (the `state` is stored in the session and
checked on the callback — the Spring's `OAuth2AuthorizationRequestResolver` does this),
(c) the **re-authentication** of the existing's account (a password's prompt) before the
link is created, and (d) a **one-time's token** with a short's expiry for the
confirmation's email (single-use, invalidated after the link).

**F4** (after the discovery) → *"What if the provider's issuer changes?"*
→ The `issuer` is the **identity's anchor** — changing it changes the identity's
namespace, so the `sub` values become meaningless. The migration's plan: the old's
issuer and the new's issuer run in **parallel**, and you keep a **mapping's table**
(`old_issuer + old_sub → local_user`), so a user who logs in via the old's issuer is
resolved through the mapping and their local's account is **re-linked** to the new's
`(new_issuer, new_sub)`. The Spring's `ClientRegistrations` supports multiple
registrations; the resolution's logic must consult the mapping's table first.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We're moving from our own username/password to Keycloak for single sign-on
across 20 internal tools. How do we plan this?"**
>
> **Ideal answer:** (1) **The framing:** this is a **migration's project**, not a
configuration's task, and the risk is entirely in the **identity's continuity** — a user
who can't log in to a tool they used yesterday is a support's incident and a trust's
break. The plan's principle: **the existing's identities must survive the migration, and
the new's IdP must be able to resolve them.** (2) **The identity's model:** the
**email** is the natural's join's key between the local's accounts and the Keycloak's
users — but the email's uniqueness and verification are the risk. The mitigation: import
the users' **email** and set the Keycloak's **`emailVerified` flag to true for the
imported's accounts** (you **verified** them at the old's system — you had the password),
and use the **email** as the Keycloak's username. The `sub` will be newly generated —
that's fine, because the local's accounts' identity is the **email**, not the `sub`.
(3) **The plan's phases:**
**Phase 0 (the preparation, 1-2 weeks):** stand up the Keycloak (the HA's cluster, the
database's Postgres, the realm's configuration as the code — the Terraform/the
Keycloak's operator, the declarative's UI). Create the **clients** for the 20 tools (the
confidential's clients, the redirect URIs, the PKCE's S256, the secret's manager). Decide
the **roles' model**: the realm's roles for the coarse's tiers (admin, user) and the
per-tool's roles for the fine-grained's permissions; define the **group's** mapping from
the existing's department's structure. Import the users (`realm` import via the admin's
REST's API or a bulk's script) with the email's verification's flag set. **Do not**
change any application yet.
**Phase 1 (the pilot, 2-4 weeks):** pick **2-3 low-risk's tools** (an internal's
dashboard, a reporting's tool) and convert them. They use the OIDC's client (or the
Spring's `oauth2Login`) with a **local's fallback** — if the Keycloak's login fails, the
user can still use the old's password (for the first few weeks). Validate the **role's
mapping** carefully: this is where the surprises live (the old's system's `ROLE_ADMIN`
vs the Keycloak's realm's role vs the client's role, and the token's claim's placement).
**Phase 2 (the migration, per team):** convert the tools in waves, with the **per-tool's
communication** (the "your account is moving to single sign-on, here's what changes, here's
the date, here's what to do if you can't log in"). Keep the local's fallback for 1-2
weeks per tool. Watch the **login's failure's rate** per tool — the top's issue in these
migrations is a misconfigured's redirect URI or a role's mapping, and the metric finds it
in minutes rather than in a ticket.
**Phase 3 (the cutover):** disable the local's password's login per tool (a **feature's
flag**, per tool, not a big's bang), then delete the local's password's column after the
tools' soak. Monitor the **support's volume** and the **login's success's rate**.
(4) **The migration's gotchas (the things that bite):** (a) the **redirect's URI's
mismatch** — a single's character difference; the provider's error is opaque ("invalid
request"), so the migration's checklist must include an exact's match of every URI;
(b) the **roles' mapping's semantics** — the old's system's roles might be *group's
membership* while the Keycloak's realm's roles are *authorisations*; a one-to-one's
mapping is often wrong (a group of 50 people → one role, but the group's membership also
implies other permissions); (c) the **email's collisions** — two users with the same email
(the old's system allowed it!) → the import's conflict; resolve manually; (d) the
**service's accounts** — the tools that authenticate as a machine (the cron, the
integration's user) need a **service's account** in the Keycloak, not a human's login;
the client's credentials go into the secret's manager; (e) the **the local's API's
tokens** — if the apps' APIs validate their own's JWTs, the Keycloak's tokens are a
**different's issuer** — the apps' APIs need to accept the Keycloak's issuer (and
usually the audience's mapper), which is a **code's change** per API, not a
configuration's; (f) the **the session's experience** — a single's sign-on means the user
is logged into **all** the tools; the first's login's prompt is a UX's moment, and the
"never log out of all the apps" vs "log out of one app" behaviour is a **product's
decision** with the OIDC's logout's semantics; (g) the **MFA** — if the company
requires MFA, the Keycloak's realms' OTP/ WebAuthn is the natural's home, and the
migration's opportunity is to **standardise** it (the old's system may have had it per
tool, inconsistently).
(5) **The governance's items to decide up front:** the **the account's ownership** (the
Keycloak's admin's team), the **the break-glass's access** (the local's emergency's
accounts — locked, sealed, audited, and a runbook), the **the joiner's/leaver's process**
(the HR's feed → the Keycloak's provisioning), the **the audit's retention** (the
Keycloak's events → the SIEM), and the **the identity's data's residency** (where's the
realm's data — the users' PII).
(6) The senior framing: "**the migration's success is measured in 'users who can still
log in', not in 'the Keycloak is configured'.** So the plan's spine is: keep the local's
fallback while you migrate, and watch the **per-tool's login's success's rate** — that
one's metric finds the redirect's URI's typo and the role's mapping's error in minutes
instead of in a ticket. The **identity's continuity** is the whole's risk, and the way to
protect it is to import the users with the **email's verification's flag set to true**
(you verified them at the old's system — you held their passwords) and to keep the
**email** as the join's key, since the Keycloak's `sub` is new. The three's things that
actually bite, in order: the **roles' mapping's semantics** (a group of 50 → one role
is often not a one-to-one), the **service's accounts** (the machines that log in as
users need a different's path entirely), and the **the APIs' issuer's change** (every
API that validates its own's JWTs needs a code's change to accept the Keycloak — that's
the part that quietly turns a 'config's task' into a 'quarter's project' if it's not
planned). And the **break-glass**: before you delete the local's passwords, seal the
emergency's access and rehearse it."

**R2. *"Security audit finding: 'the application accepts ID tokens from any issuer' and
'the nonce is not validated'. How do you fix this and what would have prevented it?"**
>
> **Ideal answer:** (1) **The framing:** this is a **token's validation's failure**, and
the two's findings compound: the missing's issuer's check means an attacker can mint a
token with **any** issuer and have it accepted; the missing's nonce's check means a
token from a **different's session** (or a **different's client**) can be replayed. The
fix's priority is the **issuer's validation** (it stops the forgery) and then the
**nonce** (it stops the replay's injection). (2) **The immediate's fix:**
  - **The issuer's validation** — the resource's server must validate the ID token's
    `iss` against the **exact's expected's issuer** (a string's comparison, no
    normalisation, no "starts with"). With the Spring's Security, the `IssuerJwtValidator`
    is the default in the `JwtValidators.createDefaultWithIssuer(...)`; if the
    configuration uses a `NimbusJwtDecoder` **without** a validator (or with a
    custom's one that omits the issuer), that's the bug. The `JwtIssuerValidator` with
    the **exact's** string is the fix.
  - **The audience's validation** — the `aud` must contain your client id; the
    `AudienceValidator` (a custom's `OAuth2TokenValidator`) implements it. The `azp` when
    the `aud` has multiple's entries.
  - **The nonce's validation** — the `OidcAuthorizationCodeAuthenticationProvider`
    (the Spring's) does it **if** the `nonce` was sent and stored. The bug is usually:
    the nonce was **not sent** in the request (a custom's `authorizationRequestCustomizer`
    that overrode the parameters, or a hand-rolled's authorize's URL), or it wasn't
    **stored** in the session (a session's store that doesn't persist it — the
    `HttpSessionOAuth2AuthorizationRequestRepository` is the default and does). The fix:
    ensure the nonce is generated, sent, stored, and compared.
  - **The algorithm's pinning** — the `alg` must be **validated against the allowed's
    algorithms**, not taken from the token's header. The Spring's `NimbusJwtDecoder`
    restricts the algorithms when you configure it with the `withJwkSetUri`/the issuer
    (it uses the discovery's `id_token_signing_alg_values_supported`); a hand-rolled's
    decoder that calls `SignedJWT.parse` and verifies with a fixed's key without
    checking the `alg` is vulnerable to the **algorithm's confusion** (an `RS256` token
    verified with an `HMAC` key) or `alg: none`.
  - **The expiry's validation** — the `exp` and the `nbf` (the Spring's default's
    validator includes them, with a 60-second's clock's skew).
(3) **The audit's wider's review (the other's findings to look for, since the auditor
found two):** (a) the **the ID token's audience's check** in the login's callback; (b)
the **`at_hash`** verification (the mix-up's defence); (c) the **`email_verified`**'s
handling (the account's takeover); (d) the **the token's `kid`** handling — an unknown's
`kid` should trigger a **JWKS's refetch** (the key's rotation), not a hard's failure;
(e) the **the access's token's handling** in the browser's storage (the local's storage
vs the httpOnly's cookie vs the memory — the XSS's exposure); (f) the **the redirect's
URI's validation** (the exact's match, the state's parameter's presence and validation);
(g) the **the session's fixation** after the login (regenerate the session's id on
login — the Spring's `SessionAuthenticationStrategy` does this); (h) the **the CSP**
and the **the `state`** on the OAuth's callbacks.
(4) **The root's cause and the prevention (the question's second half — this is what the
interviewer is really asking):**
  - **The custom's token's decoding** is the usual's culprit — someone wrote a
    `JwtDecoder` or a manual's validation and got a detail's wrong. The prevention:
    **use the framework's validated's path** (`oauth2Login()` / `oauth2ResourceServer()
    .jwt()`) and, if a custom's validation is needed, **compose** it with the default's
    validator (`DelegatingOAuth2TokenValidator`) rather than replacing it. Add a
    **lint's rule** or a **test's requirement**: every custom's decoder must have a test
    that asserts a token with a foreign's issuer is rejected.
  - **The missing's negative's tests** — the prevention's real's answer: a test's suite
    that feeds the validator a set of **bad** tokens: a wrong's issuer, a wrong's
    audience, an expired's token, a replayed's nonce, an `alg: none` token, a token
    signed with the wrong's key, a token with a future's `nbf`, a token with the `aud`
    missing. These are the **fuzzing's cases** and they should be in the CI. A
    **mutation's testing** tool (PIT) on the validator's code helps too.
  - **The dependency's scanning and the SCA** — an outdated's Nimbus or the Spring's
    Security with a known's CVE (e.g. the `alg: none` issue in some versions) — keep it
    patched.
  - **The security's headers and the CSP** — the token's storage's exposure.
  - **The code's review's checklist** — a specific's item in the PR's template:
    "if you touch the auth's code, list every claim's validation in the description".
  - **The centralized's config** — a single's `JwtDecoder` bean that is correct by
    construction, and a **lint's rule / an ArchUnit's test** that forbids constructing a
    `NimbusJwtDecoder` outside the one's config's class. That last one's the one that
    actually prevents the recurrence: the bug was possible because anyone could build
    their own's decoder.
(5) The senior framing: "**the fix is two's lines; the prevention is the interesting
half.** The fix: the issuer's validation (the exact's string) and the nonce's
comparison. But the *reason* both were missing is that someone hand-rolled the token's
validation instead of composing the framework's — and that's the thing to change. The
prevention is three's layers: (1) **one's decoder, by construction** — a single
`JwtDecoder` bean in the config, and an **ArchUnit's test** that fails the build if
anyone constructs another one; (2) **the negative's test's suite** — a table of bad
tokens (the wrong's issuer, the wrong's audience, the expired, the replayed's nonce,
the `alg: none`, the wrong's key, the future's `nbf`) asserted to be rejected; that's
the difference between 'the validation is there' and 'the validation is enforced'; (3)
**the review's checklist** — if the PR touches the auth's code, the description must
enumerate the claim's validations. And the audit's second's finding is the clue: the
missing's nonce is usually a symptom of the **custom's `authorizationRequestCustomizer`
or a hand-rolled's authorize's URL** that bypassed the framework's nonce's handling —
so the fix isn't only to validate it, it's to make sure it's **generated, sent, stored,
and compared**, and that the customiser isn't dropping it."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The 403s on the Keycloak's tokens | The audience isn't mapped | The audience's mapper; the `audienceValidator` |
| The 403s (the scope's prefix) | `SCOPE_` vs the raw scope | The consistent's naming |
| The user's "logged in as the wrong user" | The email's collision; the `sub` isn't the key | Key on `(provider, sub)` |
| The login's redirect's mismatch | The redirect URI's typo | The exact's match; the per-tool's checklist |
| The "the nonce is missing" | The custom's authorizer | Use the framework's default; don't drop the nonce |
| No refresh token from the Keycloak | The `offline_access` scope; the offline's session | Request it; enable the offline's session |
| The duplicate's users in the Keycloak | The identity's mapping's default | The account's linking's flow |
| The "the user has no email" | The `email` scope missing; the provider's claims | Add the scope; map the provider's claim |
| The logout doesn't end the app's session | The back-channel's logout not configured | Configure it |
| The ID token's validation's failure in the test | The `nonce`/the `state` not stored | The session's store; the test's setup |
| The JWKS's key's rotation breaks the validation | The unknown's `kid` | The Spring's refetch's handling; the cache's TTL |
| The "the user logged in but has no permissions" | The role's claim's placement | The `resource_access` vs the `realm_access` |

---

## 18. Debugging Scenarios

**D1. "Decode the ID token and check every claim:"**
```bash
echo "$ID_TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq '{
  iss, aud, sub, azp, exp, nonce, email, email_verified, at_hash,
  realm_access, resource_access
}'
```

**D2. "Fetch the discovery document:"**
```bash
curl -s https://as.example.com/realms/prod/.well-known/openid-configuration | jq '{
  issuer, authorization_endpoint, token_endpoint, jwks_uri, userinfo_endpoint,
  code_challenge_methods_supported, id_token_signing_alg_values_supported
}'
```

**D3. "Check the JWKS:"**
```bash
curl -s https://as.example.com/realms/prod/protocol/openid-connect/certs | jq '.keys[] | {kid, alg, use, kty}'
```

**D4. "The Keycloak's events (the audit's log):**
```bash
# the realm's events
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "https://as.example.com/admin/realms/prod/events?type=LOGIN&max=20" | jq '.events[] | {
  time, type, userId, ipAddress, clientId, details
}'
```

**D5. "The admin's CLI (the realm's inspection):**
```bash
kcadm.sh get realms/prod/clients -d clientId=orders-api | jq '.[].id'
kcadm.sh get users -r realm=prod -d email=alice@example.com | jq '.[].id'
```

**D6. "Find the users with a duplicate's email (the leading's indicator):**
```sql
SELECT lower(email) AS e, count(*) FROM users
 WHERE email IS NOT NULL GROUP BY 1 HAVING count(*) > 1 ORDER BY 2 DESC;
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the login's system for a B2B SaaS with 500 tenants, each with their own
users, some wanting SAML's SSO into their IdP.**
> (1) The **the tenant's model** (a realm per tenant vs the shared realm + the groups vs
the organizations); (2) the **the federation's model** (the OIDC's and the SAML's
identity's providers, the JIT's provisioning, the account's linking); (3) the **the
local's user's model** (the `(issuer, sub)` anchor, the email as the attribute, the
local's roles); (4) the **the session's model** (the SSO's session, the refresh's
rotation, the logout's propagation); (5) the **the MFA's policy** (the tenant's
requirements, the step-up's flows); (6) the **the audit & the compliance** (the
per-tenant's segregation, the export); (7) the **the scale & the availability** (the
clustering, the database, the caching).

**Q2. 🏗️ Design the "login with anything" for a consumer's app.**
> (1) The **the provider's abstraction** (a common's interface, the provider's registry);
(2) the **the identity's model** (the `(provider, sub)` anchor, the local's user);
(3) the **the account's linking's policy** (the explicit's confirmation, the email's
verification's requirements); (4) the **the native's app's compliance** (the AS's
Registered's app, the claimed's HTTPS, the reverse's DNS, the associated's domains, the
Apple's Sign-in-with-Apple's requirements); (5) the **the session's strategy** (the
SPAs vs the native's apps vs the web — the token's storage's differences); (6) the
**the abuse's prevention** (the rate's limits, the fraud's detection, the
account's-linking's abuse's monitoring).

**Q3. 🏗️ Design the SSO's migration for 20 internal's tools.**
> (1) The **the identity's continuity's plan** (the import, the email's verification,
the mapping); (2) The **the phases** (the pilot, the waves, the cutover); (3) the
**the roles' mapping**; (4) the **the API's issuer's migration**; (5) the **the
fallback's plan** and the **the break-glass**; (6) the **the metrics** (the per-tool's
login's success's rate, the support's volume).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  OIDC = the IDENTITY's layer on top of OAuth2 (the authorization's layer)      │
│  the ID token → to your CLIENT's server; the access token → to your API      │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ID token's VALIDATION: the signature, the alg (pinned), the iss (exact), │
│  the aud (contains your client), the exp, the nonce, the azp, the at_hash     │
│  ⚠ NEVER skip the issuer + the nonce                                        │
├──────────────────────────────────────────────────────────────────────────────┤
│  the IDENTITY's ANCHOR = (issuer, sub) — NEVER the email                     │
│  the email is an ATTRIBUTE; the linking is EXPLICIT + CONFIRMED               │
│  the `email_verified` is REQUIRED before you trust the email                  │
├──────────────────────────────────────────────────────────────────────────────┤
│  the scopes: openid (the ID token), profile, email, phone, address,           │
│              offline_access (the refresh token)                               │
│  the claims: the `sub` (required), the name, the email, the email_verified    │
│  the discovery: <the issuer's path>/.well-known/openid-configuration         │
├──────────────────────────────────────────────────────────────────────────────┤
│  the KEYCLOAK: the realm (the tenant), the client (the app), the user's role │
│  the realm's roles → realm_access.roles                                      │
│  the client's roles → resource_access.<client>.roles                         │
│  the audience: the mapper is needed (the default is `account`)                │
│  the rotation: revoke_refresh_token=true, refresh_token_max_reuse=0          │
│  the duplicate's user: the account's linking's flow / the first's login's flow│
├──────────────────────────────────────────────────────────────────────────────┤
│  the LOGOUT: the RP-initiated (the redirect) / the back-channel (the POST)   │
│  the SPAs: the PKCE + no's secret; issue YOUR OWN's token after the login    │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning
- → [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md)
- → [03 · Client Credentials & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md)
- → [06 · JWT Authentication](../06-JWT-Authentication/README.md)

---
**Next Chapter:** → [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md)
**Previous Chapter:** ← [03 · Client Credentials & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md)
