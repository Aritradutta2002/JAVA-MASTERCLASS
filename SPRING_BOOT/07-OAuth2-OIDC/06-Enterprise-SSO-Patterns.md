# 06 · Enterprise SSO Patterns

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★☆☆☆ (common at senior/principal level for B2B products
> and platform roles)
> **Prerequisite:** [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Federation Layers](#2-mental-model--the-federation-layers)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The federation topologies](#31-the-federation-topologies)
  - [3.2 Multi-tenant resolution](#32-multi-tenant-resolution)
  - [3.3 The canonical identity model](#33-the-canonical-identity-model)
  - [3.4 Claim mapping](#34-claim-mapping)
  - [3.5 JIT provisioning](#35-jit-provisioning)
  - [3.6 The SCIM lifecycle](#36-the-scim-lifecycle)
  - [3.7 The session and logout model](#37-the-session-and-logout-model)
  - [3.8 The federation gateway](#38-the-federation-gateway)
  - [3.9 SAML profile selection](#39-saml-profile-selection)
  - [3.10 Private key federation](#310-private-key-federation)
  - [3.11 Enterprise API access](#311-enterprise-api-access)
  - [3.12 Step-up authentication](#312-step-up-authentication)
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

- **Enterprise SSO is a federation problem, not a login problem:** your platform must accept
  identity from systems you don't control, map their claims into your own model, and manage
  the user lifecycle across all of them.
- **Three topologies:** **hub-and-spoke** (your IdP is the hub, apps trust it), **broker**
  (your IdP federates upstream IdPs and becomes the only trust anchor), and **bridge** (a
  third-party bridge provider translates the protocols).
- **Multi-tenant resolution is the highest-risk decision.** Bind the tenant to the
  **subdomain** (DNS is the trust anchor), the **`iss`** (RFC 9207), or the **verified email
  domain** — never to a URL parameter the attacker can choose.
- **The canonical identity model** is the abstraction that keeps apps protocol-agnostic:
  `(tenant, issuer, subject)` → your local user → your local roles. Both SAML and OIDC map
  into it.
- **JIT provisioning** creates the user on first login with the **default role** — never
  admin. **SCIM** manages the lifecycle, and **deactivation does not kill IdP sessions**, so
  you need a session maximum plus periodic re-validation.
- **Claim mapping must be self-service.** Every customer's IdP names its attributes
  differently; if the mapping needs a code change, onboarding cost and support load become
  the product's bottleneck.

---

## 1. What & Why

**The B2B reality:** every enterprise customer arrives with a different identity provider:

| Customer | IdP | Protocol | Attributes |
|----------|-----|----------|------------|
| Bank | On-premises ADFS | SAML 2.0 | `groups`, `department` |
| Tech company | Okta | SAML or OIDC | `groups` (array) |
| Government | ADFS with custom claims | SAML 2.0 | `URN:oid:` claims |
| Startup | Google Workspace | OIDC | `hd`, `email` |
| Enterprise partner | Ping | SAML 2.0 | `memberOf` |
| Legacy agency | Static SAML IdP (.NET page) | SAML 2.0 | custom attributes |

**The cost of getting this wrong is enormous:** each customer is a 2–6 week engineering
project (metadata configuration, claim mapping, testing, debugging), which is why many B2B
companies end up with a **federation gateway** and **self-service onboarding**.

**The business framing:** SSO onboarding **time** is the sales blocker. If a customer cannot
self-serve within 30 minutes, the deal cycle grows by months.

---

## 2. Mental Model — The Federation Layers

```text
  ┌─────────────────────────────────────────────────────────────────────────┐
  │  LAYER 5 — APPLICATION authorization (local roles)                      │
  │    the user's local permissions — the app's concern, protocol-agnostic   │
  ├─────────────────────────────────────────────────────────────────────────┤
  │  LAYER 4 — CANONICAL identity model                                     │
  │    (tenant, issuer, subject) → local user → local roles                 │
  │    ⚠ the abstraction layer — apps never see SAML or OIDC                │
  ├─────────────────────────────────────────────────────────────────────────┤
  │  LAYER 3 — CLAIM MAPPING (per tenant, per IdP)                          │
  │    the IdP's attribute names → the canonical fields                     │
  │    ⚠ the hardest part in practice — every customer is different        │
  ├─────────────────────────────────────────────────────────────────────────┤
  │  LAYER 2 — PROTOCOL termination (SAML SP / OIDC RP)                     │
  │    signatures, assertions, tokens, nonces, sessions                     │
  ├─────────────────────────────────────────────────────────────────────────┤
  │  LAYER 1 — FEDERATION (trust establishment)                             │
  │    metadata exchange, certificates, issuer allowlist                    │
  └─────────────────────────────────────────────────────────────────────────┘
```

**The design principle:** each layer can be replaced without touching the layers above.
That is what makes a SAML→OIDC migration (and onboarding automation) tractable.

---

## 3. Deep Dive — Theory

### 3.1 The federation topologies

```text
  ① HUB and SPOKE (the IdP is the hub)
     your IdP (Keycloak) ← apps trust it directly
     ✅ simplest; the apps' trust list is short
     ❌ each app must be configured (metadata per app)

  ② BROKER (the IdP federates upstream IdPs)
                 ┌→ Google
     your IdP ───┼→ Okta
     (the broker)└→ AD
     ✅ apps trust ONLY you; upstream trust becomes your problem
     ✅ upstream IdPs can change without re-registering apps
     ❌ you operate the broker (metadata, certificates, mapping)

  ③ PEER FEDERATION (IdPs trust each other)
     IdP A ←→ IdP B (SAML metadata exchange)
     ✅ no central broker; each keeps autonomy
     ❌ the trust web grows; policy consistency is hard

  ④ BRIDGE (a third-party bridge provider translates)
     your app ← the bridge provider (Ping/Okta) ← the customer's IdP
     ✅ fastest onboarding (the bridge pre-integrated the IdPs)
     ❌ third-party trust and cost; the extra hop
```

**The hybrid pattern (most common in practice):**
```text
  your apps ← your IdP (Keycloak) ← brokered IdPs (Google, Okta, AD)
                                  ← directly configured IdPs (SAML customers
                                    who need their own attributes)
```
**Brokered** relationships cover **generic identity** (email, name, photo — "sign in with
Google"). **Directly configured** relationships cover **customer-specific attributes**
(groups, department, employee id) that a broker cannot know.

### 3.2 Multi-tenant resolution

**The problem:** one platform, many tenants, each with its own IdP. Which IdP should this
user go to? And — critically — **can an attacker send a victim to the wrong tenant's IdP?**

**The attack:**
```text
  the attacker owns a tenant (evilco) on your platform
  ① the attacker crafts: https://app.example.com/oauth2/authorize?tenant=evilco
  ② the victim clicks it (a phishing email: "the quarterly report")
  ③ your app redirects the victim to evilco's IdP (the attacker's Okta)
  ④ the attacker controls that IdP → they can mint ANY claims
  ⑤ the app logs the victim in as... whatever the attacker decides
  ⑥ the victim then uploads their data into the attacker's tenant (or the attacker
     gains access to the REAL tenant if the session mapping is wrong)
```

**The defences, in order of safety:**

| Mechanism | Trust anchor | Notes |
|-----------|--------------|-------|
| **The subdomain** (`https://acme.app.example.com`) | **DNS** | The attacker must control DNS. Best option for a self-service product. Bonus isolation: cookies are scoped per tenant via the `Domain` attribute, so there is no cross-tenant cookie leakage. |
| **The `iss` (RFC 9207)** | **The response itself** | The provider identifier in the authorization response; the client must verify it matches the tenant's expected issuer. The clean fix for mix-up attacks. |
| **The verified email domain** | **Email ownership** | The user types an email; the domain routes to the tenant. Requires domain-ownership verification (DNS TXT record or admin email). Best for email-first onboarding. |
| **An explicit selection screen** | **The user's choice** | The user picks their organisation; the platform validates the email domain against the chosen tenant. Safe, but an extra step. |
| **A URL parameter** (`?tenant=`) | ❌ **nothing** | **Never** — the attacker controls it. |

**The combination pattern (best practice):**
```text
  ① the tenant is resolved from the HOST (subdomain) — the routing decision
  ② the tenant's expected issuer is looked up
  ③ the authorization request is built for THAT tenant's IdP
  ④ on the callback, the `iss` in the response is verified against the tenant's
     expected issuer  ← the binding proof
  ⑤ the user's email domain is checked against the tenant allowlist (if the tenant
     requires a domain restriction) — the JIT provisioning guard
```

### 3.3 The canonical identity model

```text
  TENANT
    ├── IDP CONFIGURATION
    │     idpId, protocol (SAML/OIDC), issuer/metadata, client id/secret,
    │     claim mapping, role mapping, domain allowlist,
    │     JIT enabled, SCIM enabled, status
    │
    └── USERS
          (tenant_id, issuer, subject)  ← UNIQUE  ← the federation key
            ├── LOCAL attributes (name, email, department)
            ├── LOCAL roles       ← the app's authorization
            ├── LAST login timestamp
            └── PROVENANCE (the IdP, claim source, raw claims)
```

**Key design decisions:**

1. **The federation key is `(tenant_id, issuer, subject)`** — never just `subject` (the same
   `sub` can exist at multiple IdPs), and never the email.
2. **Local attributes are separate from federated claims.** The display name comes from the
   IdP, but local profile fields (preferences, permissions, billing contact) are yours. The
   **sync policy** decides direction: **authoritative** (IdP claims overwrite local values on
   every login), **on-first-login** (claims populate empty fields only), or **local-wins**
   (IdP claims are advisory). For an enterprise product, **on-first-login with a manual
   override** is usually right — the customer's HR system is authoritative for identity, but
   your app's own fields shouldn't be clobbered.
3. **Roles are local.** IdP groups map into local roles through a **configurable mapping**
   (group name → role id). Never map a group string directly to an admin role without an
   explicit allowlist — the customer's "Admins" group may mean something else, and a
   misconfiguration here is privilege escalation.

### 3.4 Claim mapping

**The mapping's shape:**
```text
  IDP attribute name  →  canonical field  →  transform
  ─────────────────────────────────────────────────────────────────────────
  "email" | "mail" | "urn:oid:0.9.2342.19200300.100.1.3"
                          →  email    →  lowercase, trim
  "firstName" | "givenName" | "http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname"
                          →  firstName →  as-is
  "groups" | "memberOf" | "http://schemas.microsoft.com/ws/2008/06/identity/claims/groups"
                          →  groups   →  list normaliser
  "department" | "urn:oid:1.2.840.113549.1.9.1"
                          →  department → as-is
  "employeeId" | "http://schemas.xmlsoap.org/.../nameidentifier"
                          →  employeeId → as-is
```

**The role mapping (group → role):**
```text
  group name (normalised)  →  local role
  "platform-admins" | "SAML-Admin" | "admin"    →  ROLE_ADMIN
  "developers"      | "SAML-Users" | "employees" →  ROLE_USER
  (unmapped)                                    →  ROLE_USER (the default)
```

**Configuration options:**
- **A UI** (the self-service onboarding gold standard — the customer self-serves).
- **A JSON file per tenant** (an engineer's job — onboarding cost).
- **Smart defaults** (a heuristic: look for `email`/`mail`, `groups`/`memberOf`) with the
  customer's admin **confirming** the mapping in a wizard. **This is the sweet spot** — the
  heuristic gets it right ~70% of the time and the wizard makes the customer confirm.

**Mapping validation tooling (essential):** a **test connection** button in the admin console
that:
1. Fetches the metadata (SAML) or discovery document (OIDC) and validates it (certificate
   validity, algorithm support, endpoint reachability).
2. Performs a **real login** with a test user and shows the **raw claims** the IdP returned.
3. Shows the **mapped values** next to the raw ones.
4. **Flags problems**: "your `groups` attribute is a **string**, not an **array** — the mapping
   will treat it as one group named 'A,B,C'"; "the `email` claim is missing, so JIT
   provisioning will fail".

**The normalisers (transforms):**
```java
public final class ClaimNormalizer {
    /** "Alice.Smith@Acme.com" → "alice.smith@acme.com" */
    public static String email(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** "a,b,c" | ["a","b","c"] | "a b c" → ["a","b","c"] */
    public static List<String> groups(Object raw) {
        if (raw == null) return List.of();
        if (raw instanceof Collection<?> c) {
            return c.stream().map(Object::toString).map(String::trim)
                     .filter(s -> !s.isEmpty()).distinct().toList();
        }
        String s = raw.toString().trim();
        if (s.startsWith("[") && s.endsWith("]")) {
            s = s.substring(1, s.length() - 1);
        }
        // SAML memberOf is often a comma or semicolon list
        return Arrays.stream(s.split("[,;]")).map(String::trim)
                     .filter(x -> !x.isEmpty()).distinct().toList();
    }
}
```

### 3.5 JIT provisioning

**The flow:**
```text
  the user logs in with the tenant's IdP
  → the platform validates the assertion/token
  → looks up (tenant, issuer, subject)
  → NOT FOUND → create the local user:
       · the default role (ROLE_USER) — NEVER admin
       · the attributes from the mapping
       · the source provider (the IdP)
       · a flag: provisioned_by = JIT
  → FOUND → the sync policy (attribute update, last-login timestamp)
  → the session creation
```

**JIT risks and mitigations:**

| Risk | Mitigation |
|------|------------|
| **Privilege escalation** — an IdP group maps to an admin role and JIT creates an admin | The **default role is the minimum**; admin roles require explicit **approval** (a JIT approval workflow: the user is created **pending** until an admin approves) |
| **Mass account creation** — a misconfigured (or malicious) IdP creates thousands of users | **Rate limit** JIT creation per tenant; **alert** on spikes; a **tenant approval required** toggle |
| **Unverified email** — an account created with a spoofable email | The **domain allowlist**; **email verification** for sensitive tenants |
| **Orphaned accounts** — JIT created a user who never returns | An **inactive account cleanup** job (90 days without login) |
| **Duplicate identity** — one person with two accounts (different emails at the same IdP) | The **domain allowlist** plus **email verification**; an admin merge tool |

**The "JIT approval" pattern:**
```text
  the first login → the local user is created with status = PENDING
  → the tenant's admins are notified (email/Slack)
  → an admin approves (or an auto-approve policy allows read-only roles)
  → the user's session is limited to a "pending account" state
```
This is a real pattern for enterprise products where the tenant admin wants control over
who gets in.

### 3.6 The SCIM lifecycle

**SCIM 2.0 operations (RFC 7644):**

| Operation | HTTP | Semantics |
|-----------|------|-----------|
| Create | `POST /Users` | The joiner. The IdP sends the user's attributes. |
| Replace | `PUT /Users/{id}` | Full update. |
| Patch | `PATCH /Users/{id}` | Partial update — **deactivation** is `active: false`. |
| Delete | `DELETE /Users/{id}` | Hard delete. **Prefer deactivate** (keeps the audit trail). |
| List | `GET /Users?filter=...` | Lookup — the IdP polls for changes. |
| Get | `GET /Users/{id}` | Single read. |
| Schema | `GET /Schemas`, `GET /ResourceTypes` | Discovery. |

**Deactivation semantics (the subtle part):**
```json
{
  "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
  "Operations": [{
    "op": "replace",
    "path": "active",
    "value": false
  }]
}
```
- `active: false` → the user is **disabled**: logins are blocked, but **existing sessions are
  NOT killed** and **SCIM data is retained** (for audit).
- `DELETE` → the user is **removed**: data is gone (a GDPR erasure conflict — the SCIM spec
  says the IdP sends `DELETE` when the user is fully deleted).

**The recommended implementation:**
```text
  on SCIM deactivate:
    ① set the local user's status = DISABLED
    ② REVOKE the user's sessions (local sessions AND IdP sessions, via back-channel
       logout / the admin API)
    ③ keep the user's data (audit, reporting history)
    ④ log the event

  on SCIM delete:
    ① the same as deactivate, PLUS
    ② anonymise or purge personal data (per the GDPR erasure policy)
```

**SCIM security requirements:**
- **Mutual TLS** (recommended by SCIM spec §3.2) or **OAuth 2.0 client credentials** with a
  bearer token — SCIM endpoints are **highly privileged** (they can create and disable users).
- **Rate limiting** and **audit logging** of every SCIM call.
- **SSRF protection** on the IdP metadata URL (a privileged configuration input).

### 3.7 The session and logout model

**Session layers in a federated setup:**
```text
  BROWSER session       ← your app's session (the local cookie)
  APP session           ← the app's server-side session (Redis, DB)
  IDP SSO session       ← the Keycloak session (across the tenant's apps)
  IDP OFFLINE session   ← the refresh-token session
```

**The logout variants:**

| Type | Trigger | What ends |
|------|---------|-----------|
| **Local logout** | The user clicks "log out" in your app | The app session (and ideally refresh-token revocation) |
| **RP-initiated logout** | The app redirects to the IdP's `end_session_endpoint` | The IdP SSO session + the app session |
| **SP back-channel logout** | The IdP POSTs to your endpoint | The app session (when the user logged out at the IdP, not at your app) |
| **IdP admin logout** | An admin revokes a user's sessions | IdP sessions (plus back-channel logout notification) |
| **SCIM deactivation** | The IdP PATCHes `active: false` | The app session (if you revoke on deactivation) |

**The "log out of all apps" problem:** in a federated environment the user expects "log out"
to end the session **everywhere**. The SP's own logout doesn't achieve that — you need
**RP-initiated logout** (redirect to the IdP's `end_session_endpoint` with `id_token_hint`
and `post_logout_redirect_uri`). But then the IdP session is dead and the user must
re-authenticate at the next app — correct behaviour for a shared IdP, surprising for a single
app. Make that a deliberate product decision.

**Back-channel logout (provider-initiated):**
```text
  the IdP POSTs to your registered endpoint:
    POST https://app.example.com/backchannel-logout
      Content-Type: application/x-www-form-urlencoded
      id_token_hint=<the ID token>   (or "logout_token" — a signed JWT)
    → you validate it and destroy the session
```
**The validation rules:** the `logout_token` (a **signed JWT**) must be validated for its
signature, `iss`, `aud` (your client id), and `iat` (replay window); its `events` claim must
contain `http://schemas.openid.net/event/backchannel-logout` and must **not** contain the
front-channel logout event. The OIDC spec defines both the older `id_token_hint` form and the
newer `logout_token` form.

### 3.8 The federation gateway

**The pattern:**
```text
  the customer's IdP ──(SAML/OIDC)──▶ FEDERATION GATEWAY
                                           │  (protocol termination,
                                           │   claim mapping,
                                           │   canonical token issuance)
                                           │
                                           ▼
                                    your apps' APIs
                                    (they trust ONLY the gateway)
```

**Gateway benefits:**
- Apps become **protocol-agnostic** — they consume a single canonical JWT.
- SAML configuration (metadata, certificates, quirks) lives in **one** place.
- **Central claim mapping** (per-tenant configuration) lives in one place.
- **Central audit log** and **session management**.
- **Customer onboarding** becomes a configuration change, not a code change.

**Gateway costs:**
- An extra hop (latency) and an extra critical system.
- The gateway becomes a **high-value target** (it holds the trust material).
- Both **SP-initiated** and **IdP-initiated** flows must be supported.

**The canonical token's contents:**
```json
{
  "iss": "https://gateway.example.com",
  "sub": "<the canonical user id>",
  "aud": "orders-api",
  "tenant": "acme",
  "email": "alice@acme.com",
  "name": "Alice Smith",
  "roles": ["ORDER_ADMIN"],
  "groups": ["acme-engineering"],
  "amr": ["pwd", "mfa"],
  "auth_time": 1735689000,
  "acr": "urn:mace:incommon:iap:silver",
  "orig": {
    "issuer": "https://acme.okta.com",
    "subject": "00u1abc2def",
    "protocol": "saml"
  },
  "jti": "...",
  "iat": 1735689000,
  "exp": 1735692600
}
```
The `orig` block preserves provenance (valuable for audit), and `roles` are already **mapped**,
so apps never need to know the customer's group names.

### 3.9 SAML profile selection

| Profile | Purpose | Support |
|---------|---------|---------|
| **Web Browser SSO profile** | The SP-initiated flow (the app redirects the user) | ✅ Most common; Spring's `saml2Login` |
| **SP-Initiated SSO profile** | Same as above, named explicitly | ✅ |
| **IdP-Initiated SSO profile** | The IdP redirects the user to the app with an assertion (the portal case) | ⚠ Unsolicited response handling; `InResponseTo` must be relaxed |
| **SAML 2.0 Web SSO profile** | Legacy naming for the same thing | Alias names |
| **Single Logout profile** | The SP sends a `LogoutRequest` to the IdP | The IdP-initiated logout counterpart |
| **Artifact Resolution profile** | Artifact binding (the assertion is fetched over a back channel) | The more secure transport; rarely supported |
| **Assertion Consumer Service profile** | ACS specifics | ✅ |
| **Metadata profile** | Metadata exchange (configuration) | ✅ |
| **NameID Management profile** | NameID format negotiation (`IDPCapable`/`IDPList`) | Rarely used |
| **Attribute Authority profile** | Attribute retrieval from the IdP's API instead of the assertion | Rarely used |

**The IdP-initiated flow (an important operational detail):**
```text
  the user is at the IdP's portal (the Okta dashboard)
  → clicks your app's tile
  → the IdP POSTs the assertion to your ACS URL — WITHOUT a request ID
  → your SP must accept an unsolicited response
  → ⚠ the InResponseTo check must be conditional (only if you initiated the flow)
  → ⚠ security implication: an unsolicited response can be a CSRF attack
    (the attacker makes the victim's browser POST a forged assertion) — the defence
    is the mandatory assertion signature (the attacker can't forge it) plus proper
    session binding (your app issues its own session cookie only after a successful
    authentication)
```

### 3.10 Private key federation

**The advanced pattern: your platform acts as the IdP for your customers' applications.**

```text
  the customer's app → your platform (the IdP) → the customer's IdP
  (the customer's app users log in with the customer's own IdP,
   but the tokens come from YOUR platform)
```

**Why you'd do it:**
- The customer's app needs a **single API** across the customer's ecosystem, and your platform
  holds the relationships.
- You want to **aggregate** identity context (risk score, tenant policy) into the token.
- You want **centralised audit** and **policy enforcement**.

**Requirements:**
- **Metadata trust** with the upstream IdP (SAML or OIDC).
- A **token issuance** service that mints tokens for your downstream consumers, with your
  platform as `iss`.
- **PKCE** plus confidential-client support.
- **Scope design** — what the customer's app may request.

**Modern implementation — Keycloak's organizations feature (or customer tokens, or an
external IdP broker):**
```text
  the Keycloak (your platform) acts as:
    · the IdP for your internal apps
    · the broker for upstream IdPs (the customer's)
    · the issuer of customer tokens (via push/pull claim mappings on exchange)
```

**Token exchange (RFC 8693)** in this topology: the customer's app presents the **upstream**
token to your platform, which exchanges it for **its own** token with policy applied:
```text
  the customer's app → your platform's /token (exchange)
     subject_token = the upstream token
     audience = the downstream API
  → your platform's token (with your claims, your policy applied)
```

### 3.11 Enterprise API access

**The scenarios:**
1. **The customer's app calls your API on behalf of the user session** — authorization code
   flow (the customer's app is the client; the user consents).
2. **The customer's app calls your API machine-to-machine** — client credentials.
3. **The customer's service calls your API with a user context** — token exchange
   (on-behalf-of).
4. **Your platform calls the customer's API on the user's behalf** — authorization code flow
   where **your** platform is the client and the customer's IdP is the authorization server.

**Per-tenant API design (enterprise requirements):**
- **Tenant isolation at the data layer** (row-level security; schema-per-tenant vs shared
  schema).
- **Scope design that includes the tenant dimension**: `orders:read` plus tenant context from
  the token — not from a client parameter, which would be an IDOR vulnerability.
- **Rate limiting per tenant** (per-API keys).
- **Audit logging with the tenant dimension.**

### 3.12 Step-up authentication

**When an enterprise customer requires step-up (re-authentication for sensitive actions):**
```text
  the action request → the app sees the session's `acr` is insufficient
  → the app redirects to the IdP with:
       prompt=login  (or prompt=consent)
       max_age=0     (force a fresh authentication)
       acr_values=<the required context>   (e.g. "urn:mace:incommon:iap:silver"
                                            or the OIDC multi-factor policy URI)
  → the IdP performs MFA
  → the app receives a new ID token with a higher `acr` / `amr`
  → the app upgrades the session (and the local token)
```

**The `amr` (Authentication Methods References) claim** tells you **how** the user
authenticated: `pwd`, `otp`, `mfa`, `sms`, `hwk` (hardware key), `fpt` (fingerprint), `face`,
`swk` (software key), `webauthn`. Your policy can require a specific method (e.g. "payments
require `hwk` or `webauthn`").

**The `acr` (Authentication Context Class Reference)** tells you the **level** — registered
values such as `urn:mace:incommon:iap:silver`/`:bronze`/`:gold`, or the NIST levels. Many IdPs
don't populate `acr`, so build policy on **`amr`** (methods) as the primary signal, with `acr`
secondary.

**API step-up (the service-to-service case):** the client uses
`urn:ietf:params:oauth:grant-type:token-exchange` to trade a low-assurance token for a
high-assurance one (after fresh authentication at the IdP), or a token exchange with a
`requested_subject` for impersonation.

---

## 4. Visualizations

### 4.1 The federation topologies (comparison)

```text
  ① HUB and SPOKE          ② BROKER                 ③ BRIDGE
  ┌──────────┐              ┌──────────┐              ┌──────────┐
  │ your IdP │◀── apps      │ your IdP │◀── apps      │ the      │◀── apps
  └──────────┘              └────┬─────┘              │ bridge   │
                                 │                    └────┬─────┘
                         ┌───┬───┴───┬───┐                 │
                      Google Okta  AD  Ping          customer's IdP
  ✅ the simplest      ✅ one trust anchor          ✅ fastest onboarding
  ❌ per-app config    ❌ you operate the broker     ❌ the third party
```

### 4.2 Multi-tenant resolution (the trust anchors)

```text
  ┌───────────────────────┬──────────────────┬────────────────────────┐
  │  mechanism            │  trust anchor    │  attacker's ability     │
  ├───────────────────────┼──────────────────┼────────────────────────┤
  │  the subdomain        │  DNS             │  must control the DNS   │
  │  the `iss` (9207)     │  the response    │  must run the IdP       │
  │  the email domain     │  the email       │  must own the domain    │
  │  the user's selection │  the user        │  must social-engineer   │
  │  ⚠ a URL parameter    │  ❌ NOTHING      │  trivial                │
  └───────────────────────┴──────────────────┴────────────────────────┘
```

### 4.3 The federation layers

```text
  APPLICATION authorization   (local roles)
  ───────────────────────────────────────────────────
  CANONICAL identity          (tenant, issuer, subject) → user → roles
  ───────────────────────────────────────────────────
  CLAIM MAPPING               (customer names → canonical fields)
  ───────────────────────────────────────────────────
  PROTOCOL termination        (SAML SP / OIDC RP)
  ───────────────────────────────────────────────────
  FEDERATION trust            (metadata, certificates, allowlist)
```

### 4.4 Session layers and logout propagation

```text
  BROWSER cookie ──▶ your app's session ──▶ the IdP's SSO session
        (logout click)     (app kill)           (RP-initiated logout)
                                │                       │
                                ▼                       ▼
  the IdP's back-channel logout ──▶ app session kill ──▶ cookie clear
```

---

## 5. Worked Examples

### 5.1 Tenant resolution (worked example)

```text
  the REQUEST: https://acme.app.example.com/dashboard

  ① HOST resolution:
       acme.app.example.com → tenant "acme" (DB lookup by host)
       (⚠ app.example.com → the "platform's own" tenant, the staff console)
  ② the tenant's IdP configuration:
       { "tenant": "acme",
         "protocol": "oidc",
         "issuer": "https://acme.okta.com/oauth2/default",
         "clientId": "0oa1b2c3...",
         "domainAllowlist": ["acme.com", "acme.co.uk"] }

  ③ the AUTHORIZE redirect:
       GET https://acme.okta.com/oauth2/default/v1/authorize?
         client_id=0oa1b2c3...&
         redirect_uri=https://acme.app.example.com/login/oauth2/code/acme&
         response_type=code&
         scope=openid email groups&
         state=<256-bit random>&
         nonce=<256-bit random>&
         code_challenge=<S256>&
         code_challenge_method=S256&
         iss=https://acme.okta.com/oauth2/default        ← RFC 9207's iss

  ④ the CALLBACK:
       GET https://acme.app.example.com/login/oauth2/code/acme?
         code=00oKc...&state=...&iss=https://acme.okta.com/oauth2/default
       → the state is compared with the session's state ✅
       → the iss is compared with the tenant's expected issuer ✅
       → the code is exchanged with the PKCE verifier
       → the ID token is validated (signature, iss, aud, exp, nonce)

  ⑤ CLAIM mapping:
       { "email": "alice@acme.com",
         "name": "Alice Smith",
         "groups": ["engineering", "oncall"],
         "sub": "00u1abc2def" }
       → { "email": "alice@acme.com",
           "firstName": "Alice",
           "groups": ["engineering", "oncall"] }
       → ROLE mapping:
           "oncall"      → ROLE_SUPPORT
           "engineering" → ROLE_USER
           (unmapped)    → ROLE_USER
```

### 5.2 SCIM deactivation (worked example)

```text
  the IdP (Okta), on a leaver's deactivation, sends:
    PATCH https://app.example.com/scim/v2/Users/8f2b...
      Content-Type: application/scim+json
      {
        "schemas": ["urn:ietf:params:scim:api:messages:2.0:PatchOp"],
        "Operations": [{
          "op": "replace",
          "path": "active",
          "value": false
        }]
      }

  the platform's response:
  ① AUTHENTICATION: the SCIM bearer token (OAuth2 client credentials) —
     ⚠ validate the token and its tenant association
  ② the local update:
       UPDATE users SET status='DISABLED', disabled_at=now()
        WHERE tenant_id='acme' AND id=:id
  ③ SESSION REVOCATION:
       DELETE FROM sessions WHERE user_id=:id;          (app sessions)
       POST {idp}/oauth2/v1/revoke                      (the IdP's refresh tokens)
       (the back-channel logout → the other apps)
  ④ AUDIT log: { "event": "USER_DEACTIVATED", "tenant": "acme",
                 "actor": "scim:acme-connector", "user": "8f2b..." }
  ⑤ the response:
       204 No Content  (or 200 with the user representation)
```

### 5.3 Step-up authentication (worked example)

```text
  the user clicks "Approve the payment of $50,000"
  → the app checks the session's assurance:
       the session's `amr` = ["pwd"]  → the required methods = ["hwk", "webauthn"]
       → INSUFFICIENT

  → the app redirects to the IdP with:
       GET https://acme.okta.com/oauth2/default/v1/authorize?
         ...&prompt=login&
         max_age=0&
         acr_values=http://schemas.openid.net/pape/policies/2007/06/multi-factor

  → the user re-authenticates (password + WebAuthn key)
  → the IdP redirects back with a NEW ID token:
       { "amr": ["pwd", "hwk"], "acr": "urn:mace:incommon:iap:gold",
         "auth_time": 1735689000, ... }

  → the app validates it and UPGRADES the session:
       the session's assurance = HIGH
       the session's re-authenticated-at = now
       the session's max = 15 minutes (the step-up TTL)
  → the action proceeds

  ⚠ the app must NOT accept "the client says it's high assurance" — it must read
    `amr`/`acr` from the VALIDATED ID token
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 Tenant resolution (host-based)

```java
package com.example.tenant;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class TenantResolver {

    private final TenantRepository tenantRepository;

    /**
     * TENANT resolution — from the HOST, never from a URL parameter.
     * ⚠ The host is the trust anchor: an attacker must control the DNS.
     */
    public Tenant resolve(String host) {
        if (host == null) throw new TenantNotFoundException("The host is missing");

        // strip the port
        String hostname = host.split(":")[0].toLowerCase(Locale.ROOT);

        // exact match first (the "platform's own" console)
        Optional<Tenant> exact = tenantRepository.findByHost(hostname);
        if (exact.isPresent()) return exact.get();

        // subdomain pattern: <slug>.<base-domain>
        for (String baseDomain : tenantRepository.getBaseDomains()) {
            if (hostname.endsWith("." + baseDomain)) {
                String slug = hostname.substring(0, hostname.length() - baseDomain.length() - 1);
                return tenantRepository.findBySlug(slug)
                        .orElseThrow(() -> new TenantNotFoundException(hostname));
            }
        }
        throw new TenantNotFoundException(hostname);
    }
}
```

### 6.2 The claim mapping engine

```java
package com.example.federation;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ClaimMappingEngine {

    private final ClaimMappingRepository mappingRepository;

    /**
     * MAPPING execution — the IdP's raw claims → the canonical fields.
     */
    public CanonicalIdentity map(Tenant tenant, Map<String, Object> rawClaims) {
        ClaimMapping mapping = mappingRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new IllegalStateException("The mapping is not configured"));

        // ① field mappings
        Map<String, Object> mapped = new HashMap<>();
        for (ClaimMappingRule rule : mapping.getRules()) {
            Object value = rawClaims.get(rule.getSourceAttribute());
            if (value == null) continue;
            mapped.put(rule.getTargetField(), transform(rule, value));
        }

        // ② ROLE mapping (groups → local roles)
        Set<String> roles = resolveRoles(mapping, rawClaims);

        // ③ validation — required fields must be present
        for (String required : mapping.getRequiredFields()) {
            if (!mapped.containsKey(required) || isBlank(mapped.get(required))) {
                throw new IdentityMappingException(
                        "Required attribute '" + required + "' is missing from the IdP response");
            }
        }

        return CanonicalIdentity.builder()
                .tenantId(tenant.getId())
                .issuer(mapping.getIssuer())
                .subject(String.valueOf(mapped.getOrDefault("subject", rawClaims.get("sub"))))
                .email((String) mapped.get("email"))
                .firstName((String) mapped.get("firstName"))
                .lastName((String) mapped.get("lastName"))
                .department((String) mapped.get("department"))
                .groups(ClaimNormalizer.groups(rawClaims.get("groups")))
                .roles(roles)
                .rawClaims(rawClaims)
                .build();
    }

    private Set<String> resolveRoles(ClaimMapping mapping, Map<String, Object> rawClaims) {
        List<String> groups = ClaimNormalizer.groups(
                rawClaims.get(mapping.getGroupAttribute() == null ? "groups" : mapping.getGroupAttribute()));

        Set<String> roles = new HashSet<>();
        for (RoleMapping rule : mapping.getRoleMappings()) {
            for (String group : groups) {
                if (rule.getGroupNames().contains(group.toLowerCase(Locale.ROOT))) {
                    roles.add(rule.getRoleName());
                }
            }
        }
        // ⚠ the DEFAULT role — never empty (an empty role set locks the user out)
        roles.add(mapping.getDefaultRole());
        return roles;
    }

    private Object transform(ClaimMappingRule rule, Object value) {
        return switch (rule.getTransform() == null ? "NONE" : rule.getTransform()) {
            case "LOWERCASE" -> value.toString().trim().toLowerCase(Locale.ROOT);
            case "TRIM" -> value.toString().trim();
            case "EMAIL" -> ClaimNormalizer.email(value.toString());
            case "GROUPS" -> ClaimNormalizer.groups(value);
            case "FIRST" -> value instanceof Collection<?> c && !c.isEmpty()
                    ? c.iterator().next().toString() : value.toString();
            default -> value;
        };
    }
}
```

### 6.3 JIT provisioning (with an approval workflow)

```java
package com.example.federation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class JustInTimeProvisioningService {

    private final LocalUserRepository userRepository;
    private final LinkedIdentityRepository linkedIdentityRepository;
    private final DomainAllowlistService domainAllowlistService;
    private final ApprovalService approvalService;
    private final Clock clock;

    @Transactional
    public ProvisioningResult provision(Tenant tenant, CanonicalIdentity identity) {
        // ① FEDERATION key — (tenant, issuer, subject)
        Optional<LocalUser> existing = linkedIdentityRepository
                .find(tenant.getId(), identity.getIssuer(), identity.getSubject())
                .map(LinkedIdentity::getUser);

        if (existing.isPresent()) {
            LocalUser user = existing.get();
            if (user.getStatus() == UserStatus.DISABLED) {
                // ⚠ a disabled user must not be able to log in again
                throw new AccountDisabledException(user.getId());
            }
            userService.syncAttributes(user, identity);   // the sync policy
            userRepository.save(user);
            return ProvisioningResult.existing(user);
        }

        // ② the NEW user — validation checks
        if (!domainAllowlistService.isAllowed(tenant, identity.getEmail())) {
            // ⚠ the email domain must belong to the tenant
            throw new EmailDomainNotAllowedException(identity.getEmail());
        }

        LocalUser user = new LocalUser();
        user.setTenantId(tenant.getId());
        user.setEmail(identity.getEmail());
        user.setFirstName(identity.getFirstName());
        user.setLastName(identity.getLastName());
        user.setDepartment(identity.getDepartment());
        // ⚠ DEFAULT role — NEVER an admin role taken from the claims
        user.setRoles(tenant.getDefaultRoles());
        user.setStatus(tenant.isJitApprovalRequired() ? UserStatus.PENDING : UserStatus.ACTIVE);
        user.setProvisionedBy(ProvisioningSource.JIT);
        user.setCreatedAt(clock.instant());
        userRepository.save(user);

        linkedIdentityRepository.save(new LinkedIdentity(
                tenant.getId(), identity.getIssuer(), identity.getSubject(), user.getId()));

        if (tenant.isJitApprovalRequired()) {
            // ③ APPROVAL workflow — the tenant's admins approve
            approvalService.requestApproval(tenant, user, identity);
            log.info("JIT provisioning requires approval: tenant={}, user={}",
                    tenant.getId(), user.getId());
        }

        return ProvisioningResult.created(user);
    }
}
```

### 6.4 The multi-tenant `ClientRegistrationRepository` (OIDC clients per tenant)

```java
package com.example.federation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class TenantClientRegistrationRepository implements ClientRegistrationRepository {

    private final TenantRepository tenantRepository;
    private final TenantClaimMappingRepository mappingRepository;
    private final TenantSecretService secretService;

    /**
     * Registrations are built DYNAMICALLY from tenant configuration.
     * ⚠ Client secrets come from the secret store, never from plaintext DB columns.
     */
    @Override
    public ClientRegistration findByRegistrationId(String registrationId) {
        // the registrationId encodes the tenant: "oidc_acme"
        Tenant tenant = tenantRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + registrationId));

        TenantClaimMapping mapping = mappingRepository.findByTenantId(tenant.getId())
                .orElseThrow(() -> new IllegalStateException("The mapping is not configured"));

        String clientSecret = secretService.get(tenant.getId(), "oauth2-client-secret");

        return ClientRegistration.withRegistrationId(registrationId)
                .clientId(mapping.getClientId())
                .clientSecret(clientSecret)
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/" + registrationId)
                .scope("openid", "email", "profile", "groups")
                .authorizationUri(mapping.getAuthorizationUri())
                .tokenUri(mapping.getTokenUri())
                .userInfoUri(mapping.getUserInfoUri())
                .jwkSetUri(mapping.getJwksUri())
                .issuerUri(mapping.getIssuer())
                // ⚠ `sub` — the STABLE identifier, never the email
                .userNameAttributeName("sub")
                .clientName(tenant.getName())
                .build();
    }

    @Override
    public void saveAuthorizationRequest(String registrationId, org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest request) {
        throw new UnsupportedOperationException("Dynamic registrations are read-only");
    }

    @Override
    public org.springframework.security.oauth2.client.registration.ClientRegistration removeAuthorizationRequest(String registrationId, org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest request) {
        return findByRegistrationId(registrationId);
    }
}
```

### 6.5 The multi-tenant SAML SP (dynamic `RelyingPartyRegistrationRepository`)

```java
package com.example.federation;

import lombok.RequiredArgsConstructor;
import org.springframework.security.saml2.provider.service.registration.*;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TenantRelyingPartyRepository implements RelyingPartyRegistrationRepository {

    private final TenantRepository tenantRepository;
    private final SamlMetadataService metadataService;   // fetches and caches metadata

    @Override
    public RelyingPartyRegistration findByRegistrationId(String registrationId) {
        Tenant tenant = tenantRepository.findByRegistrationId(registrationId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tenant: " + registrationId));

        RelyingPartyRegistration.Builder builder = RelyingPartyRegistration
                .withRegistrationId(registrationId)
                // METADATA trust — the IdP signing certificate
                .relyingPartyMetadata(metadataService.get(tenant.getId()))
                // ⚠ the SP entity id must be STABLE and per-tenant (the IdP config
                //   references it) — use the tenant slug, not a random UUID
                .entityId("urn:example:sp:" + tenant.getSlug())
                // ⚠ the EXACT ACS URL — the IdP config must match this exactly
                .assertionConsumerServiceLocation("{baseUrl}/login/saml2/sso/" + registrationId)
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                // ⚠ signatures are mandatory
                .requireSignedAssertions(true)
                .requireSignedResponses(false)
                // ⚠ algorithm restriction — no SHA-1
                .signatureAlgorithm(SignatureAlgorithm.SHA256)
                .assertionNameIdFormat(Saml2NameIdFormat.PERSISTENT);

        // the IdP-initiated flow (portal case) — unsolicited responses
        if (tenant.isIdpInitiatedEnabled()) {
            builder = builder.assertionConsumerServiceBinding(Saml2MessageBinding.POST);
        }
        return builder.build();
    }
}
```

### 6.6 The SCIM endpoint (secured implementation)

```java
package com.example.scim;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(value = "/scim/v2", produces = "application/scim+json")
@RequiredArgsConstructor
public class ScimUserController {

    private final ScimUserService scimUserService;
    private final ScimAuditService auditService;
    private final ScimAuthService scimAuthService;

    /**
     * SCIM CREATE (RFC 7644 §3.3)
     */
    @PostMapping(value = "/Users", consumes = "application/scim+json")
    @PreAuthorize("hasAuthority('SCIM_ADMIN')")
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body,
                                                      @RequestHeader("Authorization") String auth) {
        ScimContext ctx = scimAuthService.authenticate(auth);
        Map<String, Object> user = scimUserService.create(ctx.tenantId(), body);
        auditService.log(ctx, "USER_CREATED", String.valueOf(user.get("id")));
        return ResponseEntity.ok(user);
    }

    /**
     * SCIM PATCH (§3.5.2) — the DEACTIVATION path
     */
    @PatchMapping(value = "/Users/{id}", consumes = "application/scim+json")
    @PreAuthorize("hasAuthority('SCIM_ADMIN')")
    public ResponseEntity<Map<String, Object>> patch(@PathVariable String id,
                                                     @RequestBody Map<String, Object> patch,
                                                     @RequestHeader("Authorization") String auth) {
        ScimContext ctx = scimAuthService.authenticate(auth);
        Map<String, Object> result = scimUserService.patch(ctx.tenantId(), id, patch);
        auditService.log(ctx, "USER_PATCHED", id);
        return ResponseEntity.ok(result);
    }

    /**
     * SCIM FILTER (§3.4.2) — the IdP polls with:
     *   GET /Users?filter=userName eq "alice@acme.com"
     */
    @GetMapping("/Users")
    @PreAuthorize("hasAuthority('SCIM_ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> list(
            @RequestParam(name = "filter", required = false) String filter,
            @RequestParam(name = "startIndex", defaultValue = "1") int startIndex,
            @RequestParam(name = "count", defaultValue = "100") int count,
            @RequestHeader("Authorization") String auth) {
        ScimContext ctx = scimAuthService.authenticate(auth);
        return ResponseEntity.ok(scimUserService.search(ctx.tenantId(), filter, startIndex, count));
    }
}
```

### 6.7 Back-channel logout validation

```java
package com.example.federation;

import com.nimbusds.jwt.*;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;

@RestController
public class BackChannelLogoutController {

    private static final String BACKCHANNEL_EVENT =
            "http://schemas.openid.net/event/backchannel-logout";
    private static final String FRONTCHANNEL_EVENT =
            "http://schemas.openid.net/event/frontchannel-logout";

    private final TenantJwksService tenantJwksService;
    private final SessionRevocationService sessionRevocationService;

    /**
     * The IdP POSTs here when a user logs out upstream.
     * ⚠ Every claim must be validated, and the call must be idempotent.
     */
    @PostMapping("/backchannel-logout")
    public ResponseEntity<Void> logout(@RequestParam("logout_token") String logoutToken) {
        try {
            SignedJWT jwt = SignedJWT.parse(logoutToken);
            JWTClaimsSet claims = jwt.getJWTClaimsSet();

            // ① signature against the tenant's JWKS (the issuer selects the key set)
            var verifier = new RSASSAVerifier(tenantJwksService.publicKey(claims.getIssuer()));
            if (!jwt.verify(verifier)) return ResponseEntity.badRequest().build();

            // ② the replay window — the token must be recent
            Date issuedAt = claims.getIssueTime();
            if (issuedAt == null || Math.abs(System.currentTimeMillis() - issuedAt.getTime()) > 120_000) {
                return ResponseEntity.badRequest().build();
            }

            // ③ no nonce — a logout token must NOT carry one (OIDC back-channel spec)
            if (claims.getClaim("nonce") != null) return ResponseEntity.badRequest().build();

            // ④ the events claim
            Map<String, Object> events = claims.getJSONObjectClaim("events");
            if (events == null || !events.containsKey(BACKCHANNEL_EVENT)) {
                return ResponseEntity.badRequest().build();
            }
            if (events.containsKey(FRONTCHANNEL_EVENT)) return ResponseEntity.badRequest().build();

            // ⑤ revoke
            String subject = claims.getSubject();
            String sid = (String) claims.getClaim("sid");
            sessionRevocationService.revokeBySubjectAndSid(claims.getIssuer(), subject, sid);

            return ResponseEntity.ok().build();   // 200 — the IdP expects success
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
```

**Why every check matters:** a signature check alone is not enough. A replayed token keeps
killing sessions, a token without the `events` claim may be an ordinary ID token reused as a
logout token, and accepting a token that also carries the front-channel event lets an attacker
(the front-channel is browser-driven and thus forgeable in some flows) escalate.

---

## 7. Common Pitfalls & Gotchas

| # | Pitfall | Why it bites | The fix |
|---|---------|--------------|---------|
| 1 | Resolving the tenant from a query parameter | The attacker chooses the IdP, so they choose the claims | Resolve from the host, then verify the `iss` |
| 2 | Using the email as the federation key | Emails change, and a domain change breaks every account | `(tenant, issuer, subject)` |
| 3 | Granting a role straight from an IdP group string | A misnamed customer group becomes an admin | An explicit allowlist plus a default role |
| 4 | Treating `active: false` as a full revocation | The existing sessions keep working | Revoke sessions on deactivation |
| 5 | Relying on IdP logout to end your session | The IdP session dies; your cookie does not | Support back-channel logout, and keep a session maximum |
| 6 | Accepting unsolicited SAML responses without a signature check | CSRF-style assertion injection | `requireSignedAssertions(true)` |
| 7 | Assuming SCIM uses the same credentials as login | SCIM is far more privileged | A scoped, audited client credential |
| 8 | Believing the `Content-Type` of an uploaded metadata file | SSRF and XML attacks arrive through metadata | Fetch server-side with an allowlist, disable external entities |
| 9 | Hardcoding one IdP in `application.yml` | Onboarding becomes a deploy | A per-tenant repository, backed by the database |
| 10 | Ignoring clock skew in assertions | Intermittent, unexplainable 401s after a time sync | Bound the skew explicitly |
| 11 | Leaving the IdP-initiated flow on by default | A wider attack surface for one portal feature | Enable per tenant, with a signature requirement |
| 12 | Storing refresh tokens in plaintext | One database read becomes account takeover | Hash on write, compare hashes |
| 13 | Trusting `X-Forwarded-Host` for tenant resolution | The client controls it | Derive from the server-validated host, behind a trusted proxy |
| 14 | Skipping the `iss` check because TLS "proves" the IdP | TLS proves the endpoint, not the tenant binding | Verify `iss` against the tenant's expected issuer |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is SSO, and why does an enterprise customer ask for it?**
**A:** Single sign-on lets a user authenticate once at the organisation's identity provider and
reach every connected application without a second login. The customer asks for it for three
reasons: their security team wants central control of who has access, their HR process needs
automatic deprovisioning when someone leaves, and their users refuse another password.

**Q: What is the difference between SAML and OIDC?**
**A:** Both federate identity; they differ in format and era. SAML 2.0 (2005) exchanges signed
XML assertions through browser POSTs and is entrenched in enterprise identity providers.
OIDC (2014) is a thin identity layer on OAuth 2.0 that exchanges JSON tokens and works well
with mobile and SPA clients. Choose by what the customer already runs, not by preference.

### 🟡 Intermediate
**Q: How do you decide which IdP a login should go to?**
**A:** Resolve the tenant from something the attacker cannot set: the subdomain (so DNS is the
trust anchor) or a verified email domain. Then confirm the binding on the callback by checking
the `iss` value in the response against the issuer configured for that tenant. A `?tenant=`
parameter must never be the sole input.

**Q: What is JIT provisioning, and what is the risk?**
**A:** Just-in-time provisioning creates the local account on the first successful federated
login instead of waiting for a sync. The risk is privilege escalation and account floods: a
role taken from a claim can grant admin, and a misconfigured IdP can create thousands of
users. The mitigations are a default minimum role, an email domain allowlist, rate limiting,
and an optional approval workflow.

**Q: Why do you need SCIM if you already have JIT?**
**A:** JIT only handles the joiner. SCIM handles the whole lifecycle — updates, deactivation,
and deletion — pushed by the IdP. Without SCIM, a departing employee keeps a working account
until someone notices, which is precisely the gap an audit will find.

### 🔴 Advanced / Senior
**Q: Design tenant resolution for a multi-tenant SSO platform.**
**A:** Terminate the request on a per-tenant hostname so DNS anchors the tenant; look up the
IdP configuration by that tenant; build the authorization request for that IdP only; verify
the `iss` on the callback; then apply the tenant's domain allowlist before provisioning. Add
the explicit selection screen for the email-first journey, and never accept a tenant identifier
from a client-controlled parameter alone.

**Q: A customer requires "log out everywhere". How do you implement it?**
**A:** Local logout alone cannot deliver it. Implement RP-initiated logout to end the IdP
session, and support back-channel logout so that a logout started at the IdP terminates your
session too. Then bound the exposure with a session maximum and periodic re-validation, because
a federated environment can never guarantee immediate global termination.

### ⚫ Expert / Principal
**Q: When would you build a federation gateway instead of integrating each IdP per app?**
**A:** When more than a handful of apps and more than a handful of IdPs exist. The gateway
concentrates protocol termination, metadata trust, claim mapping, audit, and canonical token
issuance in one place, so onboarding becomes configuration and the apps stay protocol-agnostic.
The cost is an extra hop, an extra critical service, and a very attractive target — so it needs
its own hardening, key management, and availability story.

---

## 9. Counter-Questions & Traps

### CQ1. "Can we just trust the `email` claim as the username?"
- **Naive answer:** "Yes, it's what the IdP gives us."  ← the trap
- **The trap:** The interviewer wants the identity-key reasoning.
- **Expert answer:** The email is an *attribute*, not an identifier. It changes when someone
marries or moves department, it can be recycled by the customer's IT, and two IdPs can assert
the same address. Key the account on `(tenant, issuer, subject)` and treat the email as
mutable data with a sync policy. If you key on the email, a recycled address silently hands
one person's account and history to another.

### CQ2. "If the IdP signs the assertion, isn't the tenant implied?"
- **Naive answer:** "Yes, the signature tells us which customer it is."  ← the trap
- **The trap:** The interviewer is probing the mix-up attack.
- **Expert answer:** The signature proves the assertion came from *some* IdP you trust, not
that it is the IdP the user's journey intended. If the routing decision is client-controlled,
an attacker can send a victim to the attacker's own IdP and then control every claim. Bind the
request to a tenant before the redirect, and verify the `iss` on the response against that
tenant's expected issuer.

### CQ3. "SCIM deactivation disables the account, so we're compliant, right?"
- **Naive answer:** "Yes, they can't log in any more."  ← the trap
- **The trap:** The interviewer wants the session and evidence angle.
- **Expert answer:** Blocking new logins is not revocation. Sessions issued before the
PATCH keep working until they expire, and a long-lived refresh token keeps renewing them. On
deactivation you must revoke sessions and refresh tokens, then keep the record — the audit
trail is the evidence that the control fired, and deleting it destroys your own proof.

### CQ4. "Why not map the customer's `groups` straight into roles?"
- **Naive answer:** "It saves configuration."  ← the trap
- **The trap:** The interviewer wants the privilege-escalation path.
- **Expert answer:** Because the customer's namespace is not yours. A group called `Admins` at
one customer can mean the desktop support team, and at another it can mean a group every
employee joins by default. Map through an explicit allowlist that names your roles, default to
the minimum role for anything unmapped, and require a human to confirm the mapping during
onboarding.

### CQ5. "If the flow works, do we really need the `state` and `nonce`?"
- **Naive answer:** "The signature protects us."  ← the trap
- **The trap:** The interviewer wants the replay and CSRF reasoning.
- **Expert answer:** They defend different things. `state` binds the callback to the browser
session that started the flow, which is what stops login CSRF — an attacker completing a flow
in your browser with their own account. `nonce` binds the ID token to that same request, which
is what stops a token minted for another session being replayed into yours. Neither is
redundant with the signature, and neither is optional.

---

## 10. Rapid-Fire MCQs

**1.** The federation key for a local user should be:
A) The email  B) The subject alone  C) `(tenant, issuer, subject)`  D) The username
**✅ C**

**2.** The safest anchor for multi-tenant resolution is:
A) A query parameter  B) The request body  C) The subdomain  D) A cookie
**✅ C**

**3.** RFC 9207 defines:
A) PKCE  B) The `iss` authorization response parameter  C) SCIM filtering  D) Token exchange
**✅ B**

**4.** A new user provisioned by JIT should receive:
A) The admin role  B) The role from the group claim  C) The default minimum role  D) No roles
**✅ C**

**5.** SCIM `active: false` means:
A) The account is deleted  B) The account is disabled, sessions may survive  C) The password changed  D) The tenant is suspended
**✅ B**

**6.** Which logout ends the IdP's session?
A) Local logout  B) RP-initiated logout  C) Clearing the cookie  D) Deleting the refresh token
**✅ B**

**7.** The `nonce` defends against:
A) CSRF on the callback  B) ID token replay  C) SQL injection  D) Clickjacking
**✅ B**

**8.** The `state` parameter defends against:
A) Login CSRF  B) Token forgery  C) Tenant mix-up  D) Session fixation
**✅ A**

**9.** The IdP-initiated SAML flow requires:
A) A relaxed `InResponseTo` plus signed assertions  B) No signature  C) A query parameter  D) An unsigned response
**✅ A**

**10.** A federation gateway primarily removes:
A) The need for TLS  B) Per-app protocol integration  C) The need for audit  D) Session management
**✅ B**

**11.** `acr` describes:
A) The authentication method used  B) The authentication context class  C) The client type  D) The tenant
**✅ B**

**12.** `amr` describes:
A) The methods by which the user authenticated  B) The authorization server  C) The audience  D) The assertion
**✅ A**

**13.** Token exchange (RFC 8693) is used to:
A) Refresh an access token  B) Trade a token for one with different audience or assurance  C) Revoke a session  D) Provision a user
**✅ B**

**14.** SCIM endpoints must be protected by:
A) The same password as the user  B) A scoped, audited credential or mTLS  C) Nothing, they are internal  D) A shared API key in the URL
**✅ B**

**15.** A back-channel logout token must NOT contain:
A) `iss`  B) `sub`  C) `nonce`  D) `events`
**✅ C**

---

## 11. Hands-On Exercises

1. **Model the identity tables.** Write the DDL for `tenant`, `idp_configuration`,
   `linked_identity`, and `local_user` with the unique constraint on
   `(tenant_id, issuer, subject)`. Explain why the constraint belongs on the link table rather
   than on the user table.
2. **Resolve a tenant from a host.** Given `acme.app.example.com` and
   `acme.co.uk.app.example.com`, write the resolution logic and list the cases that must fail
   closed.
3. **Build a claim mapping.** Take a SAML assertion with `mail`, `givenName`, `sn`, and a
   comma-separated `memberOf`, and produce the canonical identity. Handle the single-value
   case and the multi-value case.
4. **Write the deactivation handler.** Implement the SCIM PATCH path so that disabling a user
   revokes sessions and refresh tokens, and prove with a test that an existing session stops
   working.
5. **Prove the mix-up defence.** Write an integration test that sends a callback whose `iss`
   does not match the tenant's configured issuer, and assert that authentication fails.
6. **Log out everywhere.** Wire RP-initiated logout plus back-channel logout, then describe
   the window in which a session can still be alive, and why you cannot make it zero.

---

## 12. Code Challenges

**C1.** Implement `TenantClientRegistrationRepository` with a cache that expires on a tenant
configuration change, and show how you invalidate it without a restart.

**C2.** Write the `ClaimNormalizer` that accepts a group claim as an array, a comma-separated
string, a semicolon-separated string, or a JSON-encoded array string, and returns a
normalised, de-duplicated, sorted list. Cover it with tests for all four shapes.

**C3.** Write a `JitProvisioningService` that is idempotent under concurrent first logins for
the same subject. Two requests arrive at once; exactly one user must be created. Show the
constraint and the exception handling.

**C4.** Implement the back-channel logout endpoint with full validation, and write the tests
for each rejection path: bad signature, stale `iat`, missing `events`, wrong event, present
`nonce`.

**C5.** Build the step-up guard: an aspect that inspects `amr` in the validated token and
rejects a sensitive operation with a redirect to the IdP when the assurance is too low.

---

## 13. Scenario-Based Questions

**S1. A customer's users land on the wrong tenant's login page after a marketing campaign.**
> Start with the link: it carried a tenant identifier in a parameter. Move resolution to the
> host, pin the marketing links to full hostnames, and add the `iss` verification so a
> mis-routed callback fails closed rather than authenticating the wrong IdP.

**S2. A leaver still has an active session two days after HR disabled them.**
> The IdP disabled the account and blocked new logins, but nothing revoked the existing
> session or the refresh token. Add session and refresh revocation on SCIM deactivation, and
> add a session maximum so the worst case is bounded even when a webhook is missed.

**S3. Onboarding a new enterprise customer takes six weeks of engineering.**
> The mapping is code and the metadata is a deploy. Move both into configuration, add the
> test-connection tool with raw-claim display, ship smart defaults plus a confirmation wizard,
> and measure time-to-first-successful-login as the product metric.

**S4. An auditor asks you to prove that a specific employee lost access on their last day.**
> Produce the SCIM event, the local status change with a timestamp, and the session revocation
> record. This is why deactivation keeps the record instead of deleting the user.

---

## 14. Tricky Interview Questions

**T1. "Two IdPs assert the same `sub` for different people. What breaks?"**
> Nothing, if the key includes `issuer`. If you keyed on `sub` alone, the second person would
> be merged into the first account — the classic federated-identity collision. The unique
> constraint must span tenant, issuer, and subject.

**T2. "Why does the SP need its own session at all, if the IdP already has one?"**
> Because the IdP's session answers "who is this" for the browser, not "what did this
> application authorise". Your session carries local roles, local consent, tenant context, and
> your own idle and absolute timeouts. Dropping it means every request round-trips to the IdP
> and your authorization decisions become theirs.

**T3. "Can a SAML assertion be replayed?"**
> Yes, if you ignore the conditions. The defence is the `NotBefore`/`NotOnOrAfter` window, the
> `InResponseTo` correlation where the flow is SP-initiated, the audience restriction, and a
> one-time assertion ID cache for the window's duration.

**T4. "What happens if the SCIM call arrives before the user's first login?"**
> The user is created by SCIM and marked as provisioned by SCIM, then the first login links the
> federated identity to that existing account rather than creating a duplicate. That is why the
> link step must be a lookup by `(tenant, issuer, subject)` and then by verified email, in that
> order, with an explicit conflict error rather than a silent merge.

**T5. "How do you rotate the SAML signing certificate without breaking logins?"**
> Publish the new certificate in your metadata alongside the old one, wait for the IdP to
> refresh metadata, then start signing with the new key, then retire the old. The failure mode
> to avoid is a simultaneous switch on both sides, which produces an unpredictable interval of
> invalid signatures.

**T6. "Why is the IdP-initiated flow considered riskier?"**
> Because the assertion arrives unsolicited, so `InResponseTo` cannot be validated and the
> browser-driven POST is a candidate for CSRF-style injection. It is acceptable when the
> assertion signature is mandatory and your app issues its own session only after validation,
> and it should be enabled per tenant rather than globally.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** → *"What if the customer's IdP changes its certificate without telling you?"*
> Refresh the metadata on a schedule rather than once at configuration time, cache it with a
> bounded TTL, accept a key rollover window with multiple valid keys, and alert when signature
> validation starts failing for a tenant — the failure will look like "everyone at one customer
> cannot log in" and should page someone before the customer calls.

**F2** → *"How do you stop one tenant's broken IdP from affecting others?"*
> Isolation boundaries: per-tenant caching, per-tenant connection and rate limits, a circuit
> breaker around metadata fetching and token exchange, and a failure that degrades to that
> tenant only. A shared metadata cache with a single lock is the usual way one slow IdP stalls
> every other customer's login.

**F3** → *"What do you monitor in a federated identity platform?"*
> Login success rate per tenant and per IdP, assertion and token validation failures broken out
> by reason, JIT creation rate, SCIM call failures and latency, back-channel logout delivery,
> certificate expiry, and time-to-first-successful-login for new tenants.

**F4** → *"Where does authorization live in this architecture?"*
> With you. The IdP asserts identity and attributes; it must not decide what your application
> permits. Map its groups into your roles, own the permission model, and enforce it at the data
> layer. Handing authorization to the IdP means each customer's configuration becomes your
> security policy.

---

## 16. Real 3–5 Year Scenarios

**R1. "A customer wants SSO in two weeks, and their IdP has no test environment."**
>
> **Ideal answer:** Use their production tenant but a staging application registration, and
> test with a small pilot group from their side. Build the claim preview first — raw claims,
> mapped values, and flags — so the customer's admin can confirm the mapping without a round
> trip through you. Ship the JIT path with the domain allowlist and the minimum role, keep SCIM
> for phase two, and write the rollback (flag off returns them to password login). Then
> document what is not covered: no automated deprovisioning yet, which is a risk the customer
> must accept explicitly in writing.

**R2. "A customer's security team demands that a disabled user lose access instantly."**
>
> **Ideal answer:** Separate the mechanisms and state the guarantee honestly. SCIM deactivation
> revokes sessions and refresh tokens at the moment of the call, so access ends within seconds
> of the webhook. What you cannot promise is zero latency for a call that never arrives, so add
> a session maximum of fifteen minutes, a periodic re-validation against the IdP's status, and
> an alert on SCIM delivery failures. Then demonstrate the revocation with a test and give them
> the audit record. Instant is achievable for the normal path; provably bounded is what you can
> promise for the abnormal one.

**R3. "Two of your enterprise customers are the same company after a merger, each with its own IdP and its own tenant."**
>
> **Ideal answer:** Do not merge the tenants technically; merge them organisationally. Add an
> organisation level above tenants, allow one user to hold links to multiple tenants, and make
> the tenant selection explicit at login. Migrating data between tenants is a product and legal
> decision, not an identity one — and an email-domain rename will break the allowlist if you
> keyed on the domain, which is a good reason to key on `(tenant, issuer, subject)` from the
> start.

**R4. "Your federation gateway is a single point of failure and it just took down every login."**
>
> **Ideal answer:** Immediate: fail over to the standby, and if the gateway itself is the
> problem, enable the documented break-glass path (a local admin login that bypasses federation
> and is audited and time-boxed). Short term: cache validated tenant configuration and JWKS so
> an upstream IdP outage does not stop logins for tenants whose keys you already hold.
> Longer term: multi-region deployment, per-tenant circuit breakers, and a load test of the
> login path as a first-class scenario rather than an afterthought.

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| Every user at one customer gets a 401 after a certificate change | Metadata cached since onboarding | Scheduled metadata refresh plus a multi-key rollover window |
| Login loops between the app and the IdP | The session cookie is not being set, usually `SameSite` or `Secure` on an HTTP origin | Fix cookie attributes; verify the callback host |
| `InResponseTo` validation errors only for one customer | Their portal uses the IdP-initiated flow | Enable it per tenant, with the signature requirement |
| Users appear twice | The link lookup matched on email while the subject changed | Key strictly on `(tenant, issuer, subject)` and merge explicitly |
| Deactivated users still active for hours | Sessions and refresh tokens not revoked | Revoke on SCIM deactivation; add a session maximum |
| Intermittent signature failures | Clock skew between the IdP and the app | Bound the allowed skew and verify NTP |
| A slow IdP stalls all logins | Shared metadata fetch under one lock | Per-tenant cache, timeouts, and a circuit breaker |
| SCIM calls fail with 401 | Rotated client credential not deployed | Automate credential rotation with an overlap window |
| Duplicate accounts after an email domain change | The identity was keyed on the email | Re-key on subject and migrate the links |
| Logout does not end other apps' sessions | No back-channel logout support | Implement the endpoint and validate the logout token |

---

## 18. Debugging Scenarios

**D1. "Which tenant did this request resolve to?"**
```java
log.info("tenant={} host={} idp={}", tenant.getSlug(), host, tenant.getIdpConfiguration().getIssuer());
```

**D2. "What did the IdP actually send?"**
> Log the raw claims at DEBUG with redaction, and keep them in the JIT provenance record so a
> mapping dispute can be settled from evidence rather than from memory.

**D3. "Why did the `iss` check fail?"**
> Compare three values: the tenant's configured issuer, the `iss` on the authorization request,
> and the `iss` in the response. A trailing slash or a different Okta authorization-server path
> is the usual culprit.

**D4. "Is the back-channel logout arriving at all?"**
> Log every request to the endpoint before validation, with the outcome and the reason. A silent
> 400 is indistinguishable from a delivery failure without that log line.

**D5. "Why is this user mapped to the wrong role?"**
> Print the normalised group list next to the mapping rules and the resolved roles. The usual
> cause is a single-value claim treated as one group named `A,B,C`, which then matches nothing
> and falls through to the default role.

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design SSO for a B2B SaaS with 500 enterprise tenants and 30 IdPs.**
> Five layers: tenant resolution from the host, per-tenant IdP configuration with cached
> metadata and JWKS, protocol termination for SAML and OIDC, a claim mapping engine with
> per-tenant rules and a test-connection tool, and a canonical identity store keyed on
> `(tenant, issuer, subject)`. Around them: JIT provisioning with a minimum role and domain
> allowlist, SCIM for lifecycle, session revocation on deactivation, back-channel logout, an
> audit stream per tenant, and per-tenant circuit breakers so one slow IdP cannot stall the
> rest. Ship self-service onboarding as the product feature that makes 500 tenants tractable.

**Q2. 🏗️ Design the identity layer for a platform that both consumes and issues identity.**
> Broker upstream IdPs, terminate their protocols, then issue your own canonical tokens with
> your platform as `iss`. Downstream services trust only you, so upstream churn never reaches
> them. Preserve provenance in the token, map roles centrally, and use token exchange for
> on-behalf-of calls so downstream services never hold a customer's credential.

**Q3. 🏗️ Design the migration from per-app SAML integrations to a gateway.**
> Run both paths behind a flag, migrate tenant by tenant starting with the lowest-risk customer,
> keep the old path working until the new one has produced clean logins for a full week, and
> measure per-tenant success rate and time-to-login. The hard part is not the protocol — it is
> the claim mapping, so bring the mapping engine and its preview tooling online first.

---

## 20. Cheat Sheet — One-Page Summary

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│  THE FIVE DECISIONS                                                            │
│  ① tenant resolution: HOST (DNS) + `iss` verification — never a URL param     │
│  ② identity key: (tenant, issuer, subject) — never the email                  │
│  ③ roles: local, mapped through an allowlist — default is the minimum        │
│  ④ lifecycle: JIT for the joiner, SCIM for mover and leaver                   │
│  ⑤ logout: local + RP-initiated + back-channel + a session maximum           │
├───────────────────────────────────────────────────────────────────────────────┤
│  THE ATTACKS TO NAME                                                           │
│  tenant mix-up     → the attacker's IdP mints the victim's claims             │
│  login CSRF        → `state` binds the callback to the browser session        │
│  token replay      → `nonce` binds the ID token to the request                │
│  assertion replay  → the conditions window + InResponseTo + one-time ID       │
│  privilege creep   → an unmapped customer group must not become admin         │
├───────────────────────────────────────────────────────────────────────────────┤
│  THE OPERATIONAL TRAPS                                                         │
│  ⚠ metadata cached at onboarding → refresh on a schedule                     │
│  ⚠ `active: false` does not kill sessions → revoke explicitly                │
│  ⚠ one shared metadata lock → per-tenant cache + circuit breaker             │
│  ⚠ certificate rotation on both sides at once → overlap the keys             │
│  ⚠ a silent 400 on logout → log the reason, or you cannot debug delivery     │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md) — the threat model behind these patterns.
- → [04 · OpenID Connect & Social Login](./04-OpenID-Connect-and-Social-Login.md) — the OIDC mechanics.
- → [01 · OAuth2 Architecture & Fundamentals](./01-OAuth2-Architecture-and-Fundamentals.md) — the grant types.
- → [08 · Microservices](../08-Microservices-with-Spring-Boot/README.md) — service-to-service identity.

---
**Previous Chapter:** ← [05 · Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md)
**Next Topic:** → [08 · Microservices with Spring Boot](../08-Microservices-with-Spring-Boot/README.md)

