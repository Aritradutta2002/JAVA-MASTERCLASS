# 05 · Security Considerations & Enterprise SSO

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★☆☆ ("How do you secure an OAuth2 implementation?")
> **Prerequisite:** [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Attack Surface Map](#2-mental-model--the-attack-surface-map)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The attack surface](#31-the-attack-surface)
  - [3.2 The state parameter and CSRF](#32-the-state-parameter-and-csrf)
  - [3.3 The PKCE and the downgrade](#33-the-pkce-and-the-downgrade)
  - [3.4 The mix-up attack](#34-the-mix-up-attack)
  - [3.5 The code injection and the redirect](#35-the-code-injection-and-the-redirect)
  - [3.6 The open redirect and the redirect URI validation](#36-the-open-redirect-and-the-redirect-uri-validation)
  - [3.7 The token storage in the browser](#37-the-token-storage-in-the-browser)
  - [3.8 The XSS and the token theft](#38-the-xss-and-the-token-theft)
  - [3.9 The JWT's specific attacks](#39-the-jwts-specific-attacks)
  - [3.10 The SAML vs OIDC](#310-the-saml-vs-oidc)
  - [3.11 The SAML's attacks](#311-the-samls-attacks)
  - [3.12 The enterprise's SSO's architecture](#312-the-enterprises-ssos-architecture)
  - [3.13 The compliance's checklist](#313-the-compliances-checklist)
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

- **The OAuth2's attack surface** is concentrated in the **browser's redirect** and the
  **token's handling**. The classic's attacks: the **CSRF** (the `state`/`PKCE`
  defends), the **authorization's code's injection** (the `state` and the `nonce`
  defend), the **redirect's manipulation** (the exact's `redirect_uri` registration
  defends), the **mix-up's attack** (the `iss` parameter and the per-issuer's client
  defend), and the **token's theft** (the `at_hash`, the short's TTL, and the careful's
  storage defend).
- **The `state` must be unpredictable, bound to the session, and single-use.** A static
  or predictable `state` is no protection.
- **The PKCE is required for the public's clients AND strongly recommended for the
  confidential's** (the OAuth 2.1's rule). The `S256` only — never `plain`.
- **The browser's token's storage:** the httpOnly + Secure + SameSite's cookie is
  safest; the memory is safest against the XSS's exfiltration but loses the session on
  the refresh; the local's storage is **readable by any XSS** — never store a long-lived
  token there.
- **SAML vs OIDC:** SAML 2.0 is the **enterprise's** standard (the attribute's
  assertions, the XML, the browser's POST's binding, the metadata's exchange, the
  XML's signature's vulnerabilities). OIDC is the **modern's** (the JSON, the JWT's
  signing, the API's-friendliness). Most enterprises now support **both**.
- **The enterprise's SSO** = the IdP (the Keycloak, the Okta, the Entra ID, the Ping)
+ the **federation's trust** (the SAML's metadata, the OIDC's discovery) + the **the
  protocol's mapping** (the claims → your local's roles) + the **the governance**
  (the MFA, the JIT's provisioning, the SCIM, the audit).

---

## 1. What & Why

**The uncomfortable truth:** OAuth2 and OIDC are among the most **mis-implemented**
protocols in production, because:
- The specs are long and the "correct" examples are rare.
- The happy path is easy to demo and the attack paths are hard to test.
- Most of the security's failures are **invisible** — nothing breaks, the login just
  silently becomes insecure.

**The most common production's failures (from the real's audits):**
1. The `state` is a **static's string** (or missing) → the login's CSRF.
2. The `redirect_uri` is **wildcarded** → the open's redirect / the code's theft.
3. The **ID token's** signature isn't verified (or the `alg` is trusted) → the forgery.
4. The `nonce` isn't validated → the token's injection.
5. The **access token in local's storage** → the XSS's exfiltration.
6. The **refresh token** in the local's storage → the **permanent's** account's takeover.
7. The **implicit's flow** for the SPAs → the token's exposure in the URL.
8. The **the `aud` isn't checked** → the cross-client's token's acceptance.
9. The **the `iss` isn't checked** → the foreign's issuer's token's acceptance.
10. The **the client secret in the frontend's code** (an SPA) → the secret's compromise.

---

## 2. Mental Model — The Attack Surface Map

```text
                    the BROWSER (untrusted)
                          │
        ┌─────────────────┴──────────────────┐
        │  the FRONT CHANNEL (the redirect)  │  ← the attacker's playground
        │  · the state  (the CSRF's defence)  │
        │  · the code   (the injection's)     │
        │  · the redirect_uri (the open's)    │
        │  · the ID token (in the hybrid)     │
        └─────────────────┬──────────────────┘
                          │  the BACK CHANNEL (the server-to-server, TLS)
        ┌─────────────────┴──────────────────┐
        │  the CODE's EXCHANGE                │  ← the trusted's channel
        │  · the client's authentication      │     (but the client secret!)
        │  · the token's response             │
        │  · the ID token (signed)            │
        └─────────────────┬──────────────────┘
                          │
        ┌─────────────────┴──────────────────┐
        │  the TOKEN's STORAGE & the API       │  ← the second's playground
        │  · the local's storage (the XSS)     │
        │  · the cookie (the CSRF)            │
        │  · the API's validation              │
        └─────────────────────────────────────┘

  the three's questions to ask of any's implementation:
    ① Is the FRONT CHANNEL's content bound to the session?  (the state + the nonce)
    ② Is the TOKEN's handling's blast's radius as small as possible?
    ③ Is every CLAIM validated on the SERVER side?
```

---

## 3. Deep Dive — Theory

### 3.1 The attack surface

```text
  ┌────────────────────────────────────────────────────────────────────────┐
  │  the OAUTH2's ATTACKS (grouped by the target)                        │
  ├────────────────────────────────────────────────────────────────────────┤
  │  the AUTHORIZATION's SERVER:                                          │
  │    · the client's secret's compromise → the impersonation             │
  │    · the open's redirector's registration → the code's theft          │
  │    · the authorization's code's interception (the TLS's downgrade)     │
  │    · the mix-up's attack (the wrong's issuer's token)                 │
  │    · the consent's phishing (the fake's AS's page)                    │
  ├────────────────────────────────────────────────────────────────────────┤
  │  the CLIENT (your app):                                              │
  │    · the login's CSRF (the state's missing or predictable)            │
  │    · the code's injection (the state's not bound to the session)      │
  │    · the token's theft (the XSS + the local's storage)                │
  │    · the session's fixation (the id not regenerated)                  │
  │    · the redirect's manipulation (the open's redirect)                │
  ├────────────────────────────────────────────────────────────────────────┤
  │  the RESOURCE's SERVER (your API):                                   │
  │    · the token's substitution (the issuer/audience's not checked)     │
  │    · the scope's escalation (the scope's not checked)                 │
  │    · the algorithm's confusion (the alg's trusted)                    │
  │    · the confused's deputy (the token's used for the wrong's purpose) │
  └────────────────────────────────────────────────────────────────────────┘
```

### 3.2 The state parameter and CSRF

**What the `state` does:** it binds the **authorization's request** to the **user's
session** in the client. The flow:
```text
  ① the client generates a random's state, stores it in the session (the DB, Redis,
     the cookie), and sends it in the /authorize request
  ② the provider echoes the state back in the redirect
  ③ the client compares the returned's state with the session's state
     → a match: the response belongs to this session's request ✅
     → a mismatch/absent: the response is a REPLAY or an ATTACKER's ❌
```

**The attack it prevents (the login's CSRF):**
```text
  ① the attacker initiates an OAuth's flow with HIS OWN's account
     → the provider redirects to the VICTIM's redirect_uri with the attacker's code
     (the attacker intercepts it, or the victim's browser is navigated there)
  ② without the state, the victim's app accepts the code → exchanges it → the
     ATTACKER's account is logged into the VICTIM's browser
  ③ the victim then enters their credit card details into the attacker's account 😱

  with the state: the victim's session has no matching state → the callback is
  rejected → the attack fails
```

**The rules for the `state`:**
- **Unpredictable** — `SecureRandom`, ≥128 bits of entropy. **Never** a timestamp, a
  counter, or a UUID v4 (well, a UUID v4 is 122 bits of randomness from a CSPRNG —
  acceptable, but a random's string is clearer).
- **Bound to the session** — stored server-side (the `HttpSession`, the Redis) and
  compared on the callback.
- **Single-use** — removed after the successful's comparison (the Spring's
  `HttpSessionOAuth2AuthorizationRequestRepository` does this).
- **The binding's scope** — the state should also bind the **target's path** (so a
  state issued for `/checkout` can't be used for `/admin`).

**The `state` in the Spring's:**
```java
// the default's storage: the HTTP session
HttpSessionOAuth2AuthorizationRequestRepository repo =
        new HttpSessionOAuth2AuthorizationRequestRepository();
```

### 3.3 The PKCE and the downgrade

**What the PKCE does:** it binds the **authorization's code** to the **client's
instance** (the one that made the request), via a `code_verifier`/`code_challenge`
pair.

```text
  the CLIENT generates:
    code_verifier  = 43-128 chars of [A-Za-z0-9-._~] from a CSPRNG
    code_challenge = BASE64URL(SHA256(code_verifier))       (S256)
                     (or code_verifier itself, for "plain" — NEVER)

  sends the code_challenge in the /authorize request
  the provider stores it with the code
  the CLIENT proves the possession at the token's endpoint:
    POST /token  code=...&code_verifier=<the original's verifier>
  the provider checks: SHA256(verifier) == the stored's challenge
```

**The attack it prevents (the authorization's code's interception):**
```text
  the mobile's app uses a custom's URL's scheme (myapp://callback)
  → an app on the device can register the same scheme and intercept the code
  → the attacker's app exchanges the code and gets the tokens

  with the PKCE: the attacker has the code but NOT the code_verifier
  → the exchange fails ✅
```

**The downgrade to `plain`:** if the provider supports both `S256` and `plain`, an
attacker who can modify the /authorize request can **strip** the `code_challenge` and
replace it with `code_challenge_method=plain` and a `code_challenge` they know — then
they intercept the code and present that as the verifier. The defence: **require
`S256`**. The OAuth 2.1's spec makes PKCE **mandatory** for all the clients, and
**the `plain` method is forbidden**.

**The `S256` is mandatory for the confidential's clients too** (in the OAuth 2.1) —
because the client's secret can leak (it's in the config, the logs, the CI), and the
PKCE's binding protects the code even then.

### 3.4 The mix-up attack

**The setup:** your app supports **two** providers (e.g. an internal Keycloak and an
external Google). The user starts the flow with the internal one but is redirected to
the external one, whose response your app then accepts.

```text
  the MITM (or a malicious's provider) swaps the authorization's response
  → the client sends the code to the WRONG's token's endpoint
  → it receives a token from the WRONG's issuer
  → the client doesn't check the issuer → it logs the user in as whoever that
    token describes (the attacker's account!)
```

**The defences:**
1. **The `iss` parameter (RFC 9207)** — the provider includes the `iss` in the
   authorization's response, and the client **must** verify it matches the issuer it
   intended. This is the modern's, standardised's fix.
2. **The per-issuer's client** (the "the distinct's redirect's URI's per issuer" —
   the RFC 8252's guidance) — each provider has its own redirect URI, so a response for
   provider A can't be replayed at provider B's endpoint.
3. **The `at_hash` and the `c_hash`** — they bind the ID token to the access token/code,
   detecting a substituted's response.
4. **The audience's restriction** — the ID token's `aud` must be your client.

**The Spring's support:** the `OidcAuthorizationCodeAuthenticationProvider` in recent
versions validates the `iss` (the `OidcIdTokenValidator` / the `AuthorizationServerContext`),
and the `ClientRegistration`'s per-issuer's redirect URI naturally provides the second's
defence.

### 3.5 The code injection and the redirect

**The code injection:** the attacker gets their own authorization code and tricks the
victim's client into sending it to the victim's redirect URI.
```text
  the attacker starts the flow, gets a code for THEIR's account, and gets the
  victim's browser to hit: https://victim-app/callback?code=ATTACKER_CODE
  → the victim's app exchanges it → the victim is logged in as the ATTACKER
  → the victim then uploads their data "to their account" (the attacker's)
```

**The defences:** the **`state`** (the response must match the session's request) and
the **`nonce`** (the ID token must echo the nonce). Without **both**, the code's
injection succeeds.

**The redirect URI's manipulation:**
```text
  the provider's /authorize accepts a redirect_uri parameter.
  if the provider allows a WILDCARD or a PREFIX match, the attacker can:
    https://as.example.com/authorize?
      redirect_uri=https://victim-app.com.evil.com/callback&
      client_id=...&response_type=code
  → the code is delivered to the attacker's domain
```

**The rules:**
- The `redirect_uri` must be **exactly** registered (the exact's string's match, case-
  sensitive, with the exact's port, and the path's trailing's slash's mattering).
- **Never** allow the wildcard's redirect URIs in the production.
- The provider's configuration must **ignore** the client's `redirect_uri` request's
  parameter entirely and use the registered's one (this is the safest's configuration —
  then the parameter's manipulation has no effect).
- Validate the `redirect_uri` server-side too (a defence in depth): it must be
  HTTPS, it must be in the allowlist, and it must not be a `javascript:`/`data:`/open's
  redirect's URL.

### 3.6 The open redirect and the redirect URI validation

**The open redirect** = the app redirects the user to an **attacker-controlled's URL**
after the login. The impact: the **phishing's amplifier** (the user sees a legitimate
login's page and ends up on a fake's one), the **OAuth's token's leakage** (the
`state`/the code via the `Referer` header or the URL's parameters), and the
**OAuth's token's injection** (the attacker sends the victim to the app's callback with
a crafted's code — a phishing's variant of the code injection).

**The defences:**
- An **allowlist** of the post-login's destinations.
- **Never** build the redirect's target from an unvalidated's parameter.
- The **relative's path's** pattern: only allow the paths that start with a known
  prefix and contain no `//` or a scheme.

```java
// ❌ the open redirect
String target = request.getParameter("redirect_to");
return "redirect:" + target;   // → https://evil.com

// ✅ the allowlist
private static final Set<String> ALLOWED = Set.of("/dashboard", "/orders", "/profile");
String target = request.getParameter("redirect_to");
if (target == null || !ALLOWED.contains(target)) target = "/dashboard";
```

### 3.7 The token storage in the browser

**The three's options for the SPAs:**

| The approach | The XSS's exposure | The CSRF's exposure | The refresh's ability | The verdict |
|--------------|--------------------|---------------------|----------------------|------------|
| **`localStorage`** | ❌❌ Any XSS reads it | ✅ None | ✅ Survives the refresh | **The worst** for the tokens |
| **`sessionStorage`** | ❌ Any XSS reads it (while the tab is open) | ✅ None | ❌ Lost on the tab's close | Better, but still bad |
| **The memory's variable** | ✅ Not directly readable (but reachable via the XSS's RCE-ish tricks) | ✅ None | ❌ Lost on the refresh | **The best** for the access token |
| **The httpOnly's cookie** | ✅ Not readable by the JS | ❌ Needs the CSRF's defence (SameSite + the token) | ✅ The refresh's cookie is a separate's httpOnly | **The best** overall (with the BFF) |

**The recommendation (the modern's):** the **BFF (Backend for Frontend) pattern**:
```text
  the BROWSER holds ONLY an httpOnly, Secure, SameSite=Strict cookie
  (the session's identifier — a random, opaque's value, not a JWT)

  the BFF (your server):
    · holds the access + refresh tokens SERVER-side (the Redis, the in-memory)
    · calls the API with the access token
    · refreshes the token when it expires
    · sets the session's cookie

  → the XSS can't read the tokens (they're not in the browser at all)
  → the XSS can still make the API's calls (the CSRF's risk → the SameSite + the
    origin's check + the CSRF's token)
  → the refresh token never leaves the server ✅
```

**The cookies' settings:**

| The attribute | The value | Why |
|--------------|-----------|-----|
| `HttpOnly` | ✅ true | The JS can't read it (the XSS's theft's defence) |
| `Secure` | ✅ true | Only over HTTPS |
| `SameSite` | `Strict` (or `Lax`) | The CSRF's defence |
| `Path` | `/` (or the API's path) | Scoping |
| `Domain` | The exact's host (omit for the host-only's cookies) | Prevents the subdomain's theft |
| `Max-Age` | The session's (the `Max-Age=0` deletes) | The lifetime's control |

**The `SameSite=Lax` vs `Strict`:** `Lax` still sends the cookie on the top-level's
**GET** navigations (so a link from another site's page to your app works). `Strict`
doesn't send it at all on the cross-site's navigation (which breaks the **OIDC's
redirect back** — the provider's redirect to your app is a cross-site's navigation!).

**The gotcha:** with `SameSite=Strict`, the **OIDC callback's cookie isn't sent** →
your app's session's cookie is missing at the callback → the `state` can't be looked up
→ the login's failure. The fix: use `SameSite=Lax` (which allows the top-level's GET
navigation — the OIDC's callback is a GET) **or** store the `state` in a **separate's
cookie with `SameSite=None; Secure`** (the "the state's cookie" pattern, used by the
Spring's newer versions with `CookieAuthorizationRequestRepository`).

### 3.8 The XSS and the token theft

**The threat:** any XSS in your app → the script reads the token from the local's
storage → it exfiltrates it → the attacker has the account (and, if it's a refresh
token, a **permanent's** foothold that survives the password's change).

**The mitigations, in the order of effectiveness:**
1. **Don't store the long-lived's token in the browser at all** (the BFF, the httpOnly's
   cookie).
2. **The CSP (Content Security Policy)** — `script-src 'self'` blocks the inline's
   scripts and the injection's payloads. A strict's CSP with the nonces is the
   strongest's defence.
3. **The output's encoding** — the context-aware's escaping (the templating's engines
   do this by default; the raw's HTML is the danger).
4. **The `HttpOnly`'s cookie** — the token is unreadable by the JS.
5. **The short's TTL** — the damage is bounded in time.
6. **The refresh's token's rotation** — the reuse's detection catches the theft.
7. **The CSP's reporting** + the Trusted's Types (for the DOM's XSS).
8. **The dependency's scanning** (the npm's audit) — the XSS is often in a dependency.

### 3.9 The JWT's specific attacks

| The attack | The mechanism | The defence |
|-----------|-------------|------------|
| **The `alg: none`** | The token's header says "no's signature" and the server accepts it | Pin the algorithm's list; never accept the header's `alg` blindly |
| **The algorithm's confusion** | The token says `HS256` but the server verifies with the RSA's **public** key as an HMAC's secret | Pin the algorithm; use the asymmetric's verification only |
| **The `kid`'s injection** | The `kid` is used in a SQL/LDAP/query → the injection | Validate the `kid` against the known's keys; use a safe's lookup |
| **The `jku`/`x5u`'s injection** | The token points to a URL for the keys → the attacker hosts the keys | **Ignore** `jku`/`x5u`; use the configured's issuer/JWKS |
| **The claim's injection (`iss`)** | The attacker sets the `iss` to a URL they control | The issuer's **exact's** validation + a **known's issuers** allowlist |
| **The `aud`'s confusion** | The token's `aud` is another's client | The audience's validation |
| **The `sub`'s injection** | The `sub` is a SQL's fragment | The parameterised's queries; the UUID's validation |
| **The password's confusion** | A JWT with a `password` claim is used as a password | Never accept a JWT as a password |

**The Spring's specifics:** the `NimbusJwtDecoder` (Spring Security 6) **validates the
`alg`** against the configured's algorithms — the `withIssuerLocation(...)` path uses
the discovery's `id_token_signing_alg_values_supported`. If you use
`withJwkSetUri(...)` **without** specifying the algorithms, it accepts any's algorithm
that the JWK's key supports — which is where the confusion's risk creeps in. The
defence: `.signatureAlgorithm(SignatureAlgorithm.RS256)` explicitly.

### 3.10 The SAML vs OIDC

| The dimension | SAML 2.0 | OIDC |
|--------------|-----------|------|
| The era | 2005 (the enterprise's legacy) | 2014 (the modern's) |
| The format | XML (the assertions) | JSON (the JWT's claims) |
| The signature's support | ✅ (XML's DSig — complex) | ✅ (JWS) |
| The encryption's support | ✅ (the whole's assertion) | ✅ (the JWE) |
| The transport | The browser's **POST's binding** (the form's auto-submit) | The **redirect's** (the query's parameters) or the back's channel |
| The artifact's binding | ✅ (the SOAP's back channel) | n/a |
| The API's friendliness | ❌ (you'd have to parse the XML's assertion) | ✅ (a JWT's claims are just JSON) |
| The mobile's support | ❌ (the browser's POST's binding is a browser's thing) | ✅ (the custom's URL's scheme + the PKCE) |
| The enterprise's adoption | ✅✅ (the de-facto's standard) | ✅✅ (the modern's enterprise's IdPs all support it) |
| The profile's claims | The **attributes** (a flat, or a SAML's attribute's statement) | The **claims** (the standard's names) |
| The discovery | The **metadata's XML** (an URL's exchange) | The **discovery's JSON** (`.well-known`) |
| The security's history | ⚠ **XML's signature's wrapping** (a whole's class of vulnerabilities) | ⚠ The `alg`'s confusion, the mix-up (but the modern's fixes exist) |

**The recommendation:** support **both** in the enterprise's SSO's gateway — the SAML
for the legacy's applications and the enterprise's customers, the OIDC for the modern's
internal's tools and the APIs.

### 3.11 The SAML's attacks

**The XML Signature Wrapping (XSW) — the big one:**
```text
  the attacker takes a validly signed assertion and RE-PACKAGES it:
    <Assertion A (the valid's signature, but the attacker moves the original's
                 signature onto a different, attacker-controlled's Assertion)>
    → the validator verifies the signature (valid!) but reads the claims from the
      OTHER's Assertion (the attacker's)
```
**The defences:** (a) use a **certified's library** (the OpenSAML, the
`xmlsec`-based's implementations — the Spring's Security's SAML support uses
OpenSAML) that rejects the wrapping's patterns; (b) **the signature's validation's
** placement** — the library's `Response`/`Assertion` extraction must be
**signature-aware** (only the signed's element's content may be read); (c) the
**XPath's injection's** prevention in the assertion's consumer.

**The others:**
| The attack | The defence |
|-----------|------------|
| The signature's stripping | The **require the signature** (never "accept an unsigned's assertion") |
| The **replay** of an assertion | The `NotOnOrAfter` + the `InResponseTo` + the **one-time's use** of the `InResponseTo` (the ID) + the clock's skew |
| The **audience's** confusion | The `AudienceRestriction` must match your entity id exactly |
| The **recipient's** confusion | The `Recipient` must be your endpoint |
| The **the issuer's** spoofing | The `Issuer` must be the trusted's IdP's entity id |
| The **the `InResponseTo` mismatch** | The IdP's response must match the request's `InResponseTo` (the CSRF's defence) |
| The **the subject's** confusion | The `NameID` + the `SubjectConfirmation` |
| The **the XSLT's injection** | The signature's wrapping's protection; the `Signature`'s processing's restrictions |
| The **the ciphertext's** attack | The encryption's support is uncommon; treat it as a hardening's measure |

**The Spring's SAML's configuration:**
```java
// ⚠ the key's setting: the metadata's trust (the trusted's IdPs)
RelyingPartyRegistration reg = RelyingPartyRegistrations
    .withMetadataLocation("https://idp.example.com/metadata")
    .registrationId("idp")
    .entityId("{baseUrl}/saml2/service-provider-metadata/{registrationId}")
    .assertingPartyMetadataFileName("...")
    .build();
// the signature's requirements
RelyingPartyRegistrationRepository registrations = ...
```

### 3.12 The enterprise's SSO's architecture

```text
  ┌──────────────────────────────────────────────────────────────────┐
  │  the EMPLOYEE (or the CUSTOMER's USER)                          │
  └───────────────────────┬──────────────────────────────────────────┘
                          │
        ┌─────────────────┴──────────────────┐
        │  the BROWSER (the SP's app or the   │
        │  the IdP-initiated's portal)        │
        └─────────────────┬──────────────────┘
                          │
  ┌───────────────────────┴───────────────────────────┐
  │  the IDP (the Keycloak, the Okta, the Entra ID,    │
  │  the Ping, the ADFS)                               │
  │  · the authentication (the password, the MFA)      │
  │  · the federation (the enterprise's IdPs)          │
  │  · the SCIM (the provisioning's API)              │
  │  · the conditional's access                        │
  └───────────────────────┬───────────────────────────┘
                          │  the SAML's assertion / the OIDC's token
        ┌─────────────────┴──────────────────┐
        │  the SERVICE's PROVIDER (your app) │
        │  · the SP's configuration          │
        │  · the claim's mapping              │
        │  · the JIT's provisioning           │
        └────────────────────────────────────┘
```

**The federation's trust's models:**

| The model | The topology | The example |
|----------|-------------|-------------|
| **The hub and the spoke** | The IdP is the **hub**; each's app trusts it directly | The Keycloak as the corporate's IdP |
| **The broker** | The **broker** (the Keycloak) federates the upstream's IdPs and is the only's trust's anchor for the apps | The Keycloak with the Google/AD's IdPs |
| **The federation's peer** | The IdPs trust **each other** (the SAML's metadata's exchange) | The academic's federation (the InCommon), the enterprise's partner's access |
| **The bridge** | A **bridge's provider** (the Ping, the Okta) translates between the protocols | The customer's SAML's IdP into your OIDC's apps |

**The claim's mapping (the critical's piece):** the IdP's claims → your local's
authorisation. The steps:
1. The **identity's assertion** — who the user is (the `NameID` in SAML, the `sub` in
OIDC).
2. The **attribute's mapping** — the groups, the roles, the email, the department
(the IdP's names → your local's names; a **mapping's table** or the
`AttributeMapper` in the Spring's SAML).
3. The **the role's resolution** — the groups → your local's permissions (a
`GrantedAuthoritiesMapper` in the Spring's SAML; a `JwtAuthenticationConverter` in the
OIDC).
4. The **JIT's provisioning** — create the local's user on the first's login (with the
default's permissions — **never** admin!).

**The SCIM (System for Cross-domain Identity Management, RFC 7644):** the
**provisioning's API** for the lifecycle:
- `POST /Users` — the create (the joiner)
- `PATCH /Users/{id}` — the update (the name, the email, the active's status)
- `DELETE /Users/{id}` — the disable (the leaver)
- `GET /Users?filter=userName eq "..."` — the lookup
- `GET /Schemas` — the schema's discovery

**The deprovisioning's problem:** the SAML's sessions are **long-lived** — the leaver's
access persists until the session's expiry. The mitigations: the **IdP's session's
limits** (the SSO's session's max), the **SP's session's limits** (the local's session's
max), the **the periodic's re-validation** (the SP periodically re-checks the
assertion's validity, or the IdP's pushes a logout), and the **the directory's
replication** (the leaver's removal from the directory → the SCIM's disable → the
IdP's session's invalidation).

### 3.13 The compliance's checklist

**The GDPR / the privacy's requirements:**
- The **data's minimisation** — request only the scopes you need (`email` and
`profile`, not `address` and `phone`).
- The **the consent's record** — the OIDC's consent's screen (the `prompt=consent`).
- The **the right to erasure** — the local's user's data's deletion (and the
provider's, if the account was created there — which the user must visit).
- The **the data's residency** — where's the IdP's data (and your's).
- The **the data's processing's agreement** — with the social's provider.

**The SOC 2 / the ISO 27001's controls (the relevant's ones):**
- The **the access's control** (the MFA, the RBAC, the least's privilege).
- The **the audit's logging** (the login's, the failed's logins, the privilege's
changes, the exports).
- The **the credential's management** (the rotation, the vault, the no's sharing).
- The **the session's management** (the timeouts, the revocation, the max's idle).
- The **the change's management** (the code's review, the CI's gates, the separation
of duties).

**The OWASP's cheat sheet (the auth's items):**
- The **the session's management** cheat sheet (the `Secure`, `HttpOnly`,
`SameSite`, the rotation's id, the idle's timeout).
- The **the CSRF's prevention** cheat sheet (the token's pattern, the
`SameSite`, the origin's check).
- The **the REST's security** cheat sheet (the tokens in the headers, never in the
URL's query or the body's form).

**The security's headers:**
```text
  Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
  Content-Security-Policy: default-src 'self'; script-src 'self'; object-src 'none'; frame-ancestors 'none'
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Referrer-Policy: no-referrer        ← ⚠ the tokens' leakage via the Referer
  Permissions-Policy: geolocation=(), microphone=(), camera=()
  Cache-Control: no-store             ← on the auth's responses
```

**The `Referrer-Policy: no-referrer` is important for the OAuth's flows:** the
callback's URL contains the `code`; if the page then loads a third-party's resource
(the analytics, the CDN's logo), the `Referer` header would leak the code.

---

## 4. Visualizations

### 4.1 The attack surface (the map)

```text
  ┌──────────────────── THE BROWSER (untrusted) ─────────────────────┐
  │                                                                  │
  │  ⚔ the open's redirect   ⚔ the code's injection   ⚔ the XSS      │
  │         ↓                       ↓                      ↓         │
  │   the redirect_uri        the state/nonce          the token's   │
  │   (unregistered)          (missing/predictable)    storage      │
  └──────────┬──────────────────────┬──────────────────────┬─────────┘
             │                      │                      │
  ┌──────────┴──────────────────────┴──────────────────────┴─────────┐
  │                    THE SERVER (your app)                         │
  │   ⚔ the issuer's not checked   ⚔ the audience's not checked      │
  │   ⚔ the alg's trusted          ⚔ the ID token's signature's off  │
  │   ⚔ the nonce's not checked    ⚔ the session's fixation         │
  └──────────┬──────────────────────────────────────────────────────┘
             │
  ┌──────────┴──────────────────────────────────────────────────────┐
  │                    THE RESOURCE's SERVER (your API)              │
  │   ⚔ the scope's not checked   ⚔ the token's substituted        │
  │   ⚔ the confused's deputy     ⚔ the algorithm's confusion      │
  └─────────────────────────────────────────────────────────────────┘
```

### 4.2 The `state` + `nonce` + PKCE's coverage

```text
  ┌──────────────────┬────────────────┬───────────────┬──────────────┐
  │  the attack      │ the state      │ the nonce     │ the PKCE     │
  ├──────────────────┼────────────────┼───────────────┼──────────────┤
  │  the login's CSRF│ ✅ the defence  │               │              │
  │  the code's      │ ✅ the defence  │ ✅ the defence│              │
  │    injection     │                │               │              │
  │  the code's      │                │               │ ✅ the       │
  │    interception  │                │               │   defence    │
  │  the mix-up      │                │ ✅ (with the  │              │
  │                 │                │   iss)        │              │
  │  the open's      │ ✅ (the exact's │               │              │
  │    redirect      │   redirect)    │               │              │
  └──────────────────┴────────────────┴───────────────┴──────────────┘
  ⚠ each's defence covers a DIFFERENT's attack — you need all three
```

### 4.3 The BFF's pattern (the recommended's browser's architecture)

```text
  the BROWSER                     the BFF (your server)         the API
  ┌────────────────┐              ┌─────────────────┐          ┌─────────┐
  │  the JS app    │  fetch with │  the session's   │  Bearer  │         │
  │                │──the cookie─▶│  cookie         │──token──▶│  your   │
  │  NO tokens!    │  (httpOnly)  │                 │          │  API    │
  │                │◀──the data──│  the access +   │◀──the    │         │
  │                │              │  refresh tokens │  data    │         │
  └────────────────┘              │  (server-side)  │          └─────────┘
                                  └─────────────────┘
  the XSS can't read the tokens; the refresh's rotation is server-side
```

---

## 5. Worked Examples

### 5.1 The login's CSRF attack (the dry run)

```text
  the ATTACKER:
  ① the attacker visits your app's /login and starts the OAuth's flow with HIS OWN
     Google account. The state is stored in HIS browser's session.
  ② the attacker intercepts the redirect (before it reaches his browser) and gets:
       https://as.example.com/authorize?...&redirect_uri=https://app.example.com/callback&state=ATTACKER_STATE
  ③ the attacker gets the VICTIM to open that URL
     (the phishing's email: "your session expired, re-login")
  ④ the VICTIM's browser → the provider → the provider sees the VICTIM is logged in
     (as the victim) → redirects to:
       https://app.example.com/callback?code=VICTIM_CODE&state=ATTACKER_STATE
  ⑤ the VICTIM's app: the state is compared with the session's state
     → the victim's session has NO state (or a different's one) → MISMATCH → REJECT ✅

  WITHOUT the state:
  ⑤' the victim's app accepts the code → exchanges it → the VICTIM is logged in as
      the VICTIM (in the attacker's flow)... 
      (in the other variant, the attacker completes the flow first and the victim
       ends up in the ATTACKER's account → the victim enters their card details
       into the attacker's account) ❌
```

### 5.2 The code's injection (the dry run)

```text
  the ATTACKER has a code for their OWN's account: CODE_ATTACKER
  ① the attacker sends the victim a link:
       https://app.example.com/callback?code=CODE_ATTACKER&state=<guessed or stolen>
  ② the victim's app: the state doesn't match → REJECT ✅

  without the state, but with the nonce:
  ②' the app exchanges the code → gets the ID token → the nonce in the ID token is
      the ATTACKER's nonce (from the attacker's session) → the victim's session's
      nonce differs → REJECT ✅

  without BOTH:
  ②'' the victim's app logs them in as the ATTACKER (the ID token says so)
       → the victim then enters sensitive's data "into their account" → the attacker's
```

### 5.3 The mix-up's attack (the dry run)

```text
  the app supports: the internal's Keycloak (A) and the external's Google (B)
  the user's browser is MITM'd (or the malicious's provider)

  ① the user starts the flow with A:
       GET /oauth2/authorization/keycloak → the app stores the state
       → 302 to A's /authorize
  ② the MITM redirects to B's /authorize with the SAME state:
       https://accounts.google.com/o/oauth2/v2/auth?...&state=<the same>
  ③ the user authenticates at Google (or the MITM's already has a session)
  ④ Google redirects to the app's callback (the redirect URI is per-provider, so the
     MITM must use the app's GOOGLE callback URI) with a code for B
  ⑤ the app's callback handler for GOOGLE exchanges the code with GOOGLE's token
     endpoint → gets an ID token from GOOGLE
  ⑥ the app does NOT check the issuer → it accepts the token → the user is logged in
     with the wrong's identity (or the attacker's) ❌

  the defence:
  ⑥' the app checks the ID token's iss == the expected's issuer (A) → MISMATCH → REJECT ✅
  ⑦ OR the RFC 9207's iss parameter in the response (the app compares it to A) → REJECT ✅
  ⑧ OR the per-issuer's redirect URIs (the app's KEYCLOAK callback only accepts a
     state that was issued for the keycloak's flow) → REJECT ✅
```

### 5.4 The `redirect_uri`'s manipulation (the dry run)

```text
  ❌ the OPEN's REDIRECTOR (the provider's config):
     the allowed's redirect URIs: ["https://app.example.com/*"]
     the attacker:
       /authorize?redirect_uri=https://app.example.com.evil.com/steal&...
       → the provider's prefix's match accepts it → the code goes to evil.com ❌

  ✅ the EXACT's MATCH:
     the allowed's redirect URIs: ["https://app.example.com/oauth2/callback"]
     the attacker's redirect_uri → no's match → rejected ✅

  ✅✅ the SAFEST (the provider ignores the parameter):
     the client registration has one's redirect URI; the provider always uses it
     → the parameter has NO effect, whatever the attacker sends ✅
```

### 5.5 The `SameSite=Strict` breaking the OIDC's callback (the dry run)

```text
  the app's session's cookie:  Set-Cookie: SESSION=abc; HttpOnly; Secure; SameSite=Strict
  the state's stored in:       the SESSION (server-side)

  the flow:
  ① the user clicks "Sign in" → the app redirects to the provider
  ② the provider redirects BACK to https://app.example.com/callback?code=...&state=...
     → this is a CROSS-SITE navigation (from accounts.google.com)
  ③ the browser does NOT send the SameSite=Strict cookie → the app has no session →
     → the state can't be found → "invalid_state_parameter" ❌

  the fixes:
  (a) SameSite=Lax → the top-level's GET navigation's cookie IS sent ✅
  (b) a SEPARATE's cookie for the state with SameSite=None; Secure (the Spring's
      CookieAuthorizationRequestRepository) ✅
  (c) store the state in a cookie that's set with the same's policy as the callback ✅
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The secure's `state` (a custom's repository with the strict's checks)

```java
package com.example.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * The state's storage with the ENCRYPTED, the ONE-TIME's semantics.
 * ⚠ The default's implementation (the HTTP session) is fine for a server-side's app.
 *   This one is for the setups where the state must survive a session's rotation.
 */
@Component
public class SecureAuthorizationRequestRepository implements AuthorizationRequestRepository {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String COOKIE_NAME = "oauth2_auth_req";
    private static final int MAX_AGE_SECONDS = 180;   // the flow's timeout

    private final Map<String, OAuth2AuthorizationRequest> store = new HashMap<>();

    @Override
    public OAuth2AuthorizationRequest loadAuthorizationRequest(HttpServletRequest request) {
        String state = request.getParameter("state");
        if (state == null) return null;
        return store.remove(state);   // ⚠ ONE-TIME's use
    }

    @Override
    public void saveAuthorizationRequest(OAuth2AuthorizationRequest request,
                                         HttpServletRequest httpRequest,
                                         HttpServletResponse httpResponse) {
        // ① the state's entropy: 256 bits
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        // ② the binding: the state → the request's map (the server-side, the short's TTL)
        store.put(state, OAuth2AuthorizationRequest.from(
                request.getAttributes().containsKey("registrationId")
                        ? request.getAuthorizationRequestUri() : null,
                state));

        // ③ the cookie (the cross-site's callback's compatibility)
        //    ⚠ the SameSite=None so it survives the provider's redirect back;
        //      the Secure flag means HTTPS only
        httpResponse.addHeader("Set-Cookie",
                COOKIE_NAME + "=" + state + "; Path=/; Max-Age=" + MAX_AGE_SECONDS
                        + "; HttpOnly; Secure; SameSite=None");
    }

    @Override
    public OAuth2AuthorizationRequest removeAuthorizationRequest(HttpServletRequest request,
                                                                 HttpServletResponse response) {
        return loadAuthorizationRequest(request);
    }
}
```

### 6.2 The CSP + the security's headers (the filter)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Configuration
public class SecurityHeadersConfig {

    @Bean
    OncePerRequestFilter securityHeadersFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                // ① the CSP — the XSS's defence-in-depth
                //    ⚠ 'strict-dynamic' allows the scripts loaded by the trusted's scripts
                //    (the React's bundles injected by the bundler) without allowlisting the CDN
                response.setHeader("Content-Security-Policy",
                        "default-src 'self'; "
                        + "script-src 'self' 'nonce-{RANDOM_NONCE}' 'strict-dynamic'; "
                        + "style-src 'self' 'unsafe-inline'; "   // the CSS-in-JS's usual's need
                        + "img-src 'self' data: https:; "
                        + "font-src 'self' data:; "
                        + "connect-src 'self'; "
                        + "frame-ancestors 'none'; "
                        + "base-uri 'self'; "
                        + "form-action 'self' https://accounts.google.com https://as.example.com");

                // ② the HSTS
                response.setHeader("Strict-Transport-Security",
                        "max-age=31536000; includeSubDomains; preload");

                // ③ the clickjacking
                response.setHeader("X-Frame-Options", "DENY");
                response.setHeader("X-Content-Type-Options", "nosniff");

                // ④ ⚠ the Referer's policy — the auth's callback's URL contains the code;
                //    a third-party's resource would leak it in the Referer
                response.setHeader("Referrer-Policy", "no-referrer");

                // ⑤ the permissions
                response.setHeader("Permissions-Policy",
                        "geolocation=(), microphone=(), camera=(), payment=()");

                // ⑥ ⚠ the auth's responses must not be cached
                if (request.getRequestURI().contains("/oauth2/")
                        || request.getRequestURI().contains("/login")) {
                    response.setHeader("Cache-Control", "no-store");
                    response.setHeader("Pragma", "no-cache");
                }

                filterChain.doFilter(request, response);
            }
        };
    }
}
```

### 6.3 The token's storage (the httpOnly's cookie + the BFF)

```java
package com.example.auth;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class SessionCookieService {

    private static final Duration SESSION_TTL = Duration.ofHours(8);

    /**
     * The SESSION's cookie — an OPAQUE's identifier, not a token.
     * ⚠ The XSS can't read it (HttpOnly) and the CSRF is limited by SameSite.
     */
    public void writeSessionCookie(HttpServletResponse response, String sessionId) {
        ResponseCookie cookie = ResponseCookie.from("SESSION", sessionId)
                .httpOnly(true)              // ⚠ the XSS's theft's defence
                .secure(true)                // ⚠ HTTPS only
                .sameSite("Lax")             // ⚠ the CSRF's defence (see the note below)
                .path("/")
                .maxAge(SESSION_TTL)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    /**
     * The REFRESH's cookie — a SEPARATE's cookie with a NARROWER's path.
     * ⚠ The path=/api/auth means it's only SENT to the auth's endpoints,
     *   so a XSS in the app's page can't benefit from it even if the cookie's
     *   flags were wrong.
     */
    public void writeRefreshCookie(HttpServletResponse response, String refreshToken) {
        ResponseCookie cookie = ResponseCookie.from("REFRESH", refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(Duration.ofDays(30))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public void clearCookies(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                "SESSION=; Path=/; Max-Age=0; HttpOnly; Secure; SameSite=Lax");
        response.addHeader(HttpHeaders.SET_COOKIE,
                "REFRESH=; Path=/api/auth; Max-Age=0; HttpOnly; Secure; SameSite=Strict");
    }
}
```

### 6.4 The API's token's validation (the strict's resource's server)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.web.SecurityFilterChain;

import java.util.List;

@Configuration
public class ApiSecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(converter())));
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withIssuerLocation("https://as.example.com/realms/prod")
                // ⚠ the ALGORITHM must be PINNED — the `alg: none` and the
                //   algorithm's confusion attacks depend on the server trusting
                //   the token's header
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();

        OAuth2TokenValidator<Jwt> issuerWithAudience = new DelegatingOAuth2TokenValidator<>(
                // ① the ISSUER (the exact's match — the default's validator)
                JwtValidators.createDefaultWithIssuer("https://as.example.com/realms/prod"),
                // ② the AUDIENCE (your API's identifier must be present)
                audienceValidator("orders-api"),
                // ③ the SUBJECT (the present's check — some APIs require it)
                jwt -> jwt.getSubject() != null
                        ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(
                                new OAuth2Error("invalid_token", "The subject is missing", null)));

        decoder.setJwtValidator(issuerWithAudience);
        return decoder;
    }

    private OAuth2TokenValidator<Jwt> audienceValidator(String required) {
        return jwt -> {
            List<String> audience = jwt.getAudience();
            if (audience != null && audience.contains(required)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "invalid_token", "The required's audience is missing", null));
        };
    }
}
```

### 6.5 The CSRF's defence for a cookie's auth (the SPA + the BFF)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.UUID;

@Configuration
public class SpaSecurityConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            // ① the CSRF is ENABLED — the cookie's auth needs it
            .csrf(csrf -> csrf
                    // the SPA's pattern: the token in a CUSTOM's header,
                    // read from a NON-HttpOnly's cookie (the "the double's submit"
                    // is not possible with the SPA's fetch's header's pattern)
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(requestHandler())
                    .ignoringRequestMatchers("/api/auth/login", "/api/auth/callback"))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(o -> o.loginPage("/index.html"));
        return http.build();
    }

    /**
     * The BREACH's mitigation — the "the XorCsrfTokenRequestAttributeHandler"
     * masks the token in the response's body (so the compression's side channel
     * can't recover it).
     */
    private CsrfTokenRequestHandler requestHandler() {
        return new CsrfTokenRequestAttributeHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response,
                               Supplier<CsrfToken> csrfToken) {
                CsrfToken token = csrfToken.get();
                response.setHeader("X-CSRF-TOKEN", token.getToken());
                super.handle(request, response, csrfToken);
            }
        };
    }
}
```

```javascript
// the SPA's side — the token's header on every's mutating's request
async function apiFetch(url, options = {}) {
  const token = document.querySelector('meta[name="csrf-token"]')?.content
             || getCookie('XSRF-TOKEN');
  return fetch(url, {
    ...options,
    credentials: 'include',                       // the cookie's auth
    headers: {
      'Content-Type': 'application/json',
      'X-XSRF-TOKEN': decodeURIComponent(token),   // ⚠ the header's name must match
      ...(options.headers || {}),
    },
  });
}
```

### 6.6 The SAML's SP (the Spring's Security's configuration)

```java
package com.example.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
public class SamlConfig {

    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrations() {
        RelyingPartyRegistration idp = RelyingPartyRegistrations
                // ① the METADATA's trust — the IdP's signing's certificate
                //    (the standard's way to establish the trust)
                .withMetadataLocation("https://idp.example.com/metadata")
                .registrationId("corp-idp")
                // ② the SP's entity id — the metadata's export's identifier
                .entityId("{baseUrl}/saml2/service-provider-metadata/{registrationId}")
                // ③ the assertion's consumer's service (the ACS's URL) — the exact's match
                .assertionConsumerServiceLocation("{baseUrl}/login/saml2/sso/{registrationId}")
                // ④ the signature's requirements — the assertions MUST be signed
                .requireSignedAssertions(true)
                .requireSignedResponses(false)
                // ⑤ the name id's format
                .assertionConsumerServiceBinding(Saml2MessageBinding.POST)
                .build();
        return new InMemoryRelyingPartyRegistrationRepository(idp);
    }

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/saml2/**", "/login/saml2/**").permitAll()
                .anyRequest().authenticated())
            .saml2Login(saml -> saml
                // ⑥ the failure's URL
                .failureUrl("/login/saml2/failure")
                // ⑦ the claim's mapping — the IdP's groups → your local's authorities
                .userDetailsService(samlUserDetailsService()));
        return http.build();
    }
}
```

```java
package com.example.config;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.saml2.provider.service.authentication.Saml2AuthenticatedPrincipal;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class SamlAuthoritiesMapper implements GrantedAuthoritiesMapper {

    /**
     * The ATTRIBUTE's mapping: the IdP's names → your local's names.
     * ⚠ The IdP's attribute's names DIFFER between vendors (the "groups" vs the
     *   "memberOf" vs the "eduPersonAffiliation") — configure the per-vendor's mapping.
     */
    private static final Map<String, String> ATTRIBUTE_MAPPING = Map.of(
            "http://schemas.xmlsoap.org/claims/Group", "groups",
            "http://schemas.microsoft.com/ws/2008/06/identity/claims/role", "roles",
            "urn:oid:1.2.840.113549.1.9.1", "email");

    @Override
    public Collection<SimpleGrantedAuthority> mapAuthorities(Collection<? extends GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_") || a.startsWith("SCOPE_"))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /**
     * The ROLE's resolution — the groups → your local's permissions.
     * ⚠ NEVER grant the admin's role based on a group's name alone without
     *   an explicit's allowlist.
     */
    public List<SimpleGrantedAuthority> resolveRoles(Saml2AuthenticatedPrincipal principal) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        List<String> groups = principal.getAttribute("groups");
        if (groups != null) {
            for (String group : groups) {
                switch (group) {
                    case "platform-admins" -> authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    case "developers"     -> authorities.add(new SimpleGrantedAuthority("ROLE_DEV"));
                    default -> authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
                }
            }
        }
        return authorities;
    }
}
```

### 6.7 The SCIM's provisioning (the user's lifecycle)

```java
package com.example.scim;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.RequestMapping;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/scim/v2")
public class ScimController {

    private final ScimUserService scimUserService;

    /**
     * The CREATE (the joiner) — RFC 7644's POST /Users
     */
    @PostMapping(path = "/Users", consumes = "application/scim+json",
                 produces = "application/scim+json")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody Map<String, Object> body) {
        Map<String, Object> user = scimUserService.createFromScim(body);
        return ResponseEntity.ok(user);
    }

    /**
     * The PATCH (the update / the deactivation) — the DISABLE is a PATCH with
     * active:false, NOT a DELETE (the SCIM's spec recommends deactivation to
     * preserve the audit's trail)
     */
    @PatchMapping(path = "/Users/{id}", consumes = "application/scim+json",
                  produces = "application/scim+json")
    public ResponseEntity<Map<String, Object>> patchUser(
            @PathVariable String id, @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(scimUserService.patch(id, patch));
    }

    /**
     * The LOOKUP — the IdP's provisioning's polls
     */
    @GetMapping(path = "/Users", produces = "application/scim+json")
    public ResponseEntity<List<Map<String, Object>>> listUsers(
            @RequestParam(name = "filter", required = false) String filter) {
        return ResponseEntity.ok(scimUserService.search(filter));
    }

    @GetMapping(path = "/Users/{id}", produces = "application/scim+json")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(scimUserService.find(id).orElseThrow());
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The `state` is static or predictable | 128+ bits of CSPRNG, session-bound, single-use | The login's CSRF |
| 2 | The `state` isn't compared on the callback | Compare it (and remove it) | The code's injection |
| 3 | The `nonce` isn't validated | Validate it | The ID token's injection |
| 4 | The `redirect_uri` is wildcarded | The exact's match | The code's theft |
| 5 | The PKCE is off for the confidential's clients | On (S256) for everyone | The code's interception |
| 6 | `code_challenge_method=plain` is allowed | S256 only | The downgrade's attack |
| 7 | The access token is in the local's storage | The httpOnly's cookie / the memory / the BFF | The XSS's theft |
| 8 | The refresh token is in the local's storage | The BFF / the httpOnly's cookie | The permanent's takeover |
| 9 | The `iss` isn't checked | The exact's validation | The foreign's issuer |
| 10 | The `aud` isn't checked | The audience's validator | The cross-client's token |
| 11 | The `alg` is trusted from the header | Pin the algorithm | The `alg: none` / the confusion |
| 12 | The ID token is used as the API's token | The access token | The audience's mismatch |
| 13 | The `state` is stored in a `SameSite=Strict` cookie only | `Lax` (or a separate's `None` cookie) | The callback's failure |
| 14 | The `Referer-Policy` is default | `no-referrer` on the auth's pages | The code's leakage |
| 15 | The CSP is missing | A strict's CSP | The XSS |
| 16 | The SAML's assertion's signature isn't required | `requireSignedAssertions(true)` | The forgery |
| 17 | The SAML's `InResponseTo` isn't checked | The library's handling | The CSRF |
| 18 | The JIT's provisioning grants admin | The default's role only | The privilege's escalation |
| 19 | The leaver's SAML's session persists | The session's limits + the SCIM + the re-validation | The access's persistence |
| 20 | The `email_verified` isn't checked | Require it before the email's trust | The account's takeover |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is the `state` parameter for?**
**A:** It ties the authorization's response to the user's session in the client. The
client generates a random value, stores it in the session, and sends it with the
authorization's request. The provider echoes it back, and the client compares it. If it
doesn't match, the response is a replay or an attack — and it's rejected. It's the
defence against the **login's CSRF** (an attacker logging you into their account) and
the **code's injection**.

**Q: Why can't we just use a long-lived API key?**
**A:** Because an API key is a **static, permanent, all-or-nothing** credential. A token
is short-lived, scoped, and audienced, and it can be revoked without rotating anything
else. The OAuth2's machinery exists to get a **scoped, expiring** credential from a
**long-lived** one.

### 🟡 Intermediate
**Q: What is PKCE and why do we need it?**
**A:** PKCE (RFC 7636) binds the authorization's code to the client's instance. The
client generates a random `code_verifier`, sends its SHA-256 hash (the `code_challenge`)
with the authorization's request, and presents the verifier when exchanging the code.
Without it, an attacker who intercepts the code (on a mobile's app with a custom's URL
scheme, or via a malicious's app registering the same scheme) could redeem it. It is
mandatory for the public's clients and required by the OAuth 2.1 for all the clients.

**Q: Where should you store the access token in a browser app?**
**A:** Not in `localStorage` — any XSS can read it. The best is the **BFF pattern**: the
browser holds only an httpOnly, Secure, SameSite cookie with an opaque's session id,
and the tokens stay server-side. If you must keep the tokens in the browser, keep the
access token in **memory** (not readable by a simple XSS's read) and use the refresh
token's rotation to limit the damage.

### 🔴 Advanced / Senior
**Q: What is the mix-up attack and how do you prevent it?**
**A:** It targets apps that use multiple providers. An attacker (or a MITM) redirects the
authorization's request from provider A to provider B; the app then receives a token
from B and, if it doesn't check the issuer, accepts it — logging the user in with the
wrong's (or the attacker's) identity. The defences: the **`iss` parameter** (RFC 9207) —
the provider includes its identifier in the response and the client must verify it
matches the intended's provider; the **per-issuer's redirect URIs** so a response for
one provider can't be replayed at another's endpoint; and the **`at_hash`/`c_hash`**
binding the ID token to the access token/code. The Spring's recent versions validate the
issuer in the OIDC's provider.

**Q: How would you review a new OAuth2 integration for security?**
**A:** A structured's review: (1) the **front channel** — is the `state`
unpredictable, session-bound, and single-use? Is the `nonce` sent and validated? Is the
`redirect_uri` exactly registered? (2) the **the token's handling** — where's the access
and the refresh token stored, and what's the blast's radius of an XSS? (3) the **the
server's validation** — the signature, the pinned's algorithm, the issuer, the audience,
the expiry, the nonce, the scopes. (4) the **the browser's config** — the CSP, the
`Referrer-Policy`, the cookies' flags. (5) the **the error's handling** — does a failed
login leak the code or the state in the logs or the URL's query? (6) the **the secrets'**
handling — is the client secret in the vault, and is it rotated? (7) the **the
scopes'** minimality — is `email` requested when only `openid` is needed?

### ⚫ Expert / Principal
**Q: You inherited a SAML-based enterprise SSO with known's XML signature's
vulnerabilities. How do you plan the migration to OIDC without breaking the enterprise's
customers?**
>
> (1) The **the framing:** the migration's constraint is not the technology — it's the
**customers' IdPs**. Many enterprise's customers (the banks, the governments, the
legacy's software's vendors) have **SAML-only** IdPs, and their's timelines are not
yours. So the plan is **not** "replace SAML with OIDC" — it's **"keep SAML as a
first-class's option while the OIDC's path becomes the default"**. (2) The **the
architecture's decision:** a **federation's gateway** (a dedicated's service that
terminates SAML and OIDC and issues a **normalised's internal's token**) vs the
**in-app's dual support** (the Spring's `saml2Login` + `oauth2Login` side by side).
The gateway is better's for a platform with many's apps (the SAML's configuration lives
in one place; the apps just consume an internal's OIDC), but it's an extra's hop and
another's critical's system. The in-app's support is simpler but each app's config
grows. My default: the **gateway**, because the SAML's configuration is notoriously
fiddly and the trust's setup (the metadata's exchange, the certificate's rotation) is
the part that consumes the most's time. (3) The **the security's migration** (the
critical's part): first, **verify the current's SAML's implementation** — the OpenSAML's
version (the XSW's protection), the signature's requirements, the `InResponseTo`'s
checking, the `AudienceRestriction`, the assertion's replay's protection. The
OpenSAML's libraries handle the XML's signature's wrapping correctly **if** you use them
properly — the "the unsigned's assertion's fallback" and the "the XSLT's injection" are
the dangerous's misconfigurations. Then, **the metadata's trust's hardening** — the
signing's certificates' rotation's plan (the IdPs' certificates expire, and a surprise
expiry is the #1's cause of a sudden's SAML's outage), the `wantAssertionsSigned` and
`wantAuthnRequestsSigned` flags, and the **the signature's algorithm's restriction**
(no SHA-1, no RSA-1024). (4) The **the OIDC's path's design:** your IdP is already
OIDC-capable (the Keycloak, the Okta, the Entra ID), so the OIDC's support is the
**modern's** direction; the migration's trick is the **the claim's mapping** — the
SAML's attributes and the OIDC's claims carry the same's information in different's
shapes (the `memberOf` vs the `groups` vs the `roles`), so build a **canonical's
internal's model** (the internal's user + the internal's roles) and map **both**
protocols into it. Then the apps only ever see your canonical's model. (5) The **the
rollout's plan:** the **the pilot's customer** (a friendly one with both's protocols
supported), the **the feature's flag** per customer, the **the parallel's run** (both
the paths's live for a while), the **the migration's communication's kit** (the
customers' admins' guide: the metadata's URL, the ACS's URL, the entity id, the
attributes' mapping, the certificate's requirements), and the **the support's
runbook** (the SAML's error's decoding — the `status` codes, the trace's ID, the
common's failures). (6) The **the operational's concerns** to plan for: the **the
metadata's refresh** (the IdP's metadata changes — the certificate's rotation —
your's SP's config must be updated, and if the metadata is fetched automatically, a
break's change on the IdP's side breaks you); the **the session's limits** (the SAML's
sessions are long — the deprovisioning's problem, which the SCIM's disable doesn't
immediately solve — the SP's session's max and the periodic's re-validation are the
mitigations); the **the clock's skew** (the assertions' `NotOnOrAfter` — the
distributed's IdPs' clocks drift, and a 5-minute's skew kills the logins); the **the
multi-tenancy's rate's limits** (the IdP's load for the many's customers). (7) The
**the key's insight to articulate:** the SAML's *technical* problems are solvable (the
libraries handle them), but the SAML's *organisational* problems are the real's cost —
the XML's configuration, the metadata's exchanges, the certificate's rotations, and the
fact that **every** customer's admin's team has to be involved in **any** change. That's
the argument for the OIDC's path, and the argument for **not** ripping out the SAML
until the customers' IdPs are actually's OIDC-capable. (8) The senior framing: "**I'd
frame this as a federation's-capability's migration, not a protocol's replacement.** The
SAML's path stays live — many's enterprise's customers are SAML-only and their's
timelines aren't mine. What I'd do is build a **canonical's identity's model** and map
both's protocols into it, so the apps are protocol-agnostic, and then move the
customers whose IdPs support OIDC, one at a time, behind a feature's flag. The
security's work in the meantime is the **current's implementation's audit** — the
OpenSAML's version (the XSW's), the signature's requirements, the `InResponseTo`, the
algorithm's restriction, and above all the **certificate's rotation's plan**, because
the #1's cause of a sudden's SAML's outage is an expired's signing's certificate on
either's side. And the operational's problem to solve either's way is the **leaver's
access** — the SAML's sessions are long and the SCIM's disable doesn't kill them, so
the SP's session's max and the periodic's re-validation are the real's controls."

---

## 9. Counter-Questions & Traps

### CQ1. "If you use PKCE, do you still need `state`?"
- **Naive answer:** "No, PKCE replaces it."  ← the trap
- **The trap:** The interviewer wants the precise's distinction.
- **Expert answer:** No — they defend against **different's attacks** and you need both.
PKCE defends the **code's integrity against interception**: it proves the party
redeeming the code is the party that started the flow. `state` defends the **response's
binding to the user's session**: it proves the response belongs to *this* user's
in-flight request. Concretely, the **login's CSRF** (the attacker logs the victim into
the attacker's account) is **not** stopped by the PKCE — the attacker's flow has a valid
PKCE of its own; the victim's app accepts the *attacker's* code and exchanges it
successfully (the code and the verifier match — they're the attacker's). The `state` is
what catches it: the victim's session has no matching state, so the response is
rejected. The reverse is also true: the **code's injection** where the attacker
substitutes a *code* the victim's client would redeem — here the PKCE matters, because
the attacker's code was created for the attacker's PKCE's verifier, which the victim's
client doesn't have; combined with the `nonce` on the ID token, the injection is
stopped. The clean's framing: **`state` is about the session, PKCE is about the code's
redemption, and the `nonce` is about the ID token's provenance.** They compose — the
OAuth 2.1 requires all three for the authorization's code's flow, and the reasoning is
precisely that they cover non-overlapping's threats. And the practical's note: with the
`state` stored in the **session**, you also get the **one-time's use** and the **the
session's rotation's protection** for free.

### CQ2. "Why not just use the implicit flow for SPAs? It's simpler."
- **Naive answer:** "It's the standard's flow for SPAs."  ← the trap
- **The trap:** The interviewer wants the modern's objection.
- **Expert answer:** The implicit's flow returns the tokens **directly in the URL's
fragment** — which means they pass through the browser's history, the browser's
extensions, the `Referer` header (in some's flows), and any's script on the page. The
tokens end up in the browser's memory with no's way to protect them, and there's no's
refresh token (the implicit's flow has no refresh token by design), so the SPA's
"silent's renewal" (the `prompt=none` in a hidden iframe) is needed — a complexity's
cost with a weaker's guarantee. The **authorization's code + PKCE** gives the SPA the
same's result with a better's guarantee: the code is in the browser but it's useless
without the `code_verifier`, which never leaves the SPA's memory; the tokens come back
over the **back channel** (the server-to-server's TLS) where they're not exposed to the
browser's URL at all. The OAuth 2.1 **removes** the implicit's flow entirely. There's
one's caveat to name: the code's flow for a **pure's SPA** (no's server at all) means
the tokens are delivered to the browser (that's the "the SPA's tokens in the browser"
problem) — which is why the **BFF** pattern is the better's answer for anything
serious: the browser never holds a token at all. The framing: *"the implicit's flow
puts the tokens in the URL, and the URL is the least's protected's place a token can
live — the history, the extensions, the copy-paste, the Referer. The code's flow with
the PKCE gives the SPA the same's UX with the tokens delivered over the back's
channel, and the code in the URL is useless without the verifier that never leaves the
page. The OAuth 2.1 dropped the implicit's flow for exactly this reason. The residual's
issue with a pure's SPA is that the tokens still end up in the browser — and the BFF
pattern is the fix for that."*

### CQ3. "If the access token is short-lived (5 minutes), why bother with refresh tokens?
Just have the user log in again."
- **Naive answer:** "That's simpler."  ← the trap
- **The trap:** The interviewer wants the UX's and the security's balance.
- **Expert answer:** The 5-minute's access token is a **good's default** for the API's
risk, but the **user's experience** requirement is different: a user shouldn't be
re-authenticated every 5 minutes, and the re-authentication itself (the password, the
MFA, the risk's analysis) is the expensive's part. The refresh token is the mechanism
that lets the app maintain a **session** without a **user's interaction** — the refresh
token is the **consent's evidence**, held by a server that can prove its possession.
The security's model: the access token is a **bearer** credential for the API — it's
exposed to the browser, to the logs, to the intermediaries — so it's kept short-lived
and narrow-scoped. The refresh token is more dangerous (longer-lived, more powerful), so
it's kept **server-side** (in the BFF), it's **rotated** (so a theft is detectable), and
it's **bound** to the device/session where possible. The comparison to make: "re-login
every 5 minutes" isn't a simpler design — it's a **worse** one that trades the session's
security's properties (the MFA's satisfaction, the risk's analysis, the rate's limiting)
for a worse's UX. The framing: *"the access token's TTL is about the API's exposure's
window; the refresh token is about the user's experience. The re-authentication isn't
free — it's the password's replay, the MFA's prompt, the risk's analysis, the
rate's limiting. The refresh token is the mechanism that carries the 'the user already
proved who they are' fact across the session, securely, because it lives on the server
and it's rotated so a theft is detectable. The pair is the standard's answer to 'the
bearer token has to be short-lived' — you don't shorten the access token's life and
also make the user log in again; you add a second, better-guarded credential."*

### CQ4. "Your app uses a cookie for the session. Doesn't that make you vulnerable to
CSRF on the API?"
- **Naive answer:** "No, we use SameSite."  ← the trap
- **The trap:** The interviewer wants the layered's defence.
- **Expert answer:** `SameSite` is a **powerful** first's line but not a complete's
answer, and the modern's browsers' defaults have made it stronger (`Lax` is now the
default) — but you should not rely on it **alone**, for three's reasons: (1) **`SameSite`
isn't a universal's protection** — the older's browsers and the non-browser's clients
(the mobile's apps, the server-to-server's callers) don't implement it, and a
cross-site's **GET** request is still sent with `Lax` (so a `GET /api/export` could be
triggered by an `<img>` tag — the state-changing's GET is the classic's example); (2)
**the subdomain's attacks** — a sibling's subdomain (or a user's own's upload's host,
or an XSS on a related's domain) is "same-site" for the cookie's purposes, so
`SameSite` doesn't stop it (the `Origin`/`Referer`'s check does); (3) the **`Origin`
header's spoofing** by a non-browser's client is irrelevant to the browser's threat
model but means the server's check should combine the `Origin` (if present) with the
**CSRF's token**. The layered's defence: (a) the `SameSite=Strict`/`Lax` cookie; (b) the
**CSRF's token** (the synchroniser's pattern for the SPAs — the double's cookie with
the header's echo — or the httponly's cookie with the header's comparison for the
server-rendered's apps); (c) the **`Origin`/`Referer`'s check** as a defence in depth
(especially for the non-browser's clients, where the CSRF's token's absence is
legitimate — the API's key or the bearer token); (d) **the state-changing's operations
must not be GETs** (the `GET /api/delete?id=5` is the CSRF's paradise — the `DELETE`
with a body, or a POST, fixes it); (e) the **CORS** is not a CSRF's defence (CORS
protects the *reading* of the response, not the *sending* of the request). The framing:
*"`SameSite` is the first's line, not the whole's answer. It doesn't cover the
older's browsers, it doesn't cover the non-browser's clients, it doesn't stop the
sibling's subdomain, and it doesn't stop a cross-site's GET — which is why the
state-changing's operations must not be GETs. The complete's answer is layered:
`SameSite` + the CSRF's token (the synchroniser's pattern for the SPAs) + the
`Origin`'s check + the correct's HTTP's semantics. And the one that's often confused:
CORS is **not** a CSRF's defence — CORS is about whether the browser lets the calling
page *read* the response; the CSRF is about the request being *sent* at all, which
happens regardless of the CORS's policy."*

### CQ5. "Why does the OIDC require the `nonce` if the authorization code flow already has
the PKCE?"
- **Naive answer:** "It's belt and braces."  ← the trap
- **The trap:** The interviewer wants the precise's reason — the `nonce` and the PKCE
protect **different tokens**.
- **Expert answer:** The PKCE and the `nonce` protect different artifacts of the flow.
The **PKCE** protects the **authorization code's redemption**: it proves the party
exchanging the code is the party that started the flow — a defence against the
**code's interception** (someone else stealing the code from the redirect). The
**`nonce`** protects the **ID token's provenance**: it proves the ID token was issued
*in response to this client's request* — a defence against the **ID token's injection /
substitution**. The concrete's attack the `nonce` stops: the attacker obtains a valid
ID token for **their own** account (a completely legitimate token, correctly signed, from
a legitimate's flow they started themselves) and gets the victim's client to accept it.
The PKCE doesn't help here — the victim isn't redeeming a code at all, or the code
succeeds because the attacker did a proper's flow. The `nonce` catches it: the
attacker's ID token carries the attacker's nonce (or no nonce at all), which doesn't
match the victim's session's nonce. And a subtlety: the `nonce` also protects the
**hybrid's flow** where the ID token comes back in the **front channel** (the URL's
fragment) — there, it's the *only* defence against the injection, since the code is
still pending's redemption. And the `at_hash`/`c_hash` are the third's leg: they bind
the ID token to the access token / the code, catching a **mix-up's attack** where a
valid's ID token from one provider is combined with a valid's access token from
another. The framing: *"the PKCE protects the **code**, the `nonce` protects the **ID
token**. They're not redundant — they're guarding different artifacts. The attack the
`nonce` stops is the **ID token's injection**: the attacker takes a perfectly valid ID
token from their own login and gets your client to accept it. There's no code's
redemption involved, so the PKCE has nothing to say about it. The `nonce` catches it
because the token carries the attacker's nonce, not the victim's. The third's leg is the
`at_hash`, which binds the ID token to the access token and stops the **mix-up**."*

---

## 10. Rapid-Fire MCQs

**1.** The `state` parameter defends against:
A) The CSRF  B) The XSS  C) The SQL injection  D) The SSRF
**✅ A**

**2.** The `state` must be:
A) A UUID  B) Unpredictable, session-bound, single-use  C) The client's id  D) The redirect's URI
**✅ B**

**3.** PKCE's `code_challenge_method` should be:
A) `plain`  B) `S256`  C) `MD5`  D) `none`
**✅ B**

**4.** PKCE is mandatory for:
A) The public's clients  B) The confidential's clients only  C) No one  D) The browser's clients
**✅ A** (and required for all in the OAuth 2.1)

**5.** The `nonce` defends against:
A) The CSRF  B) The ID token's injection  C) The CSRF's token  D) The replay's cookie
**✅ B**

**6.** The `redirect_uri` must be:
A) A prefix's match  B) The exact's match  C) A wildcard  D) Optional
**✅ B**

**7.** The `iss` validation must be:
A) A prefix's match  B) The exact's match  C) A case-insensitive's match  D) Optional
**✅ B**

**8.** The `aud` validation ensures the token was issued:
A) By the AS  B) For your API  C) By the user  D) With the scope
**✅ B**

**9.** The `alg: none` attack is prevented by:
A) The TLS  B) Pinning the algorithm  C) The long's token's TTL  D) The HTTPS's HSTS
**✅ B**

**10.** The refresh token should NOT be stored in:
A) The server's session  B) The local's storage  C) The Redis  D) The httpOnly's cookie
**✅ B**

**11.** `SameSite=Strict` can break:
A) The CSRF  B) The OIDC's redirect back  C) The XSS  D) The TLS
**✅ B**

**12.** The `at_hash` binds the ID token to:
A) The client's secret  B) The access token  C) The state  D) The user
**✅ B**

**13.** The `c_hash` binds the ID token to:
A) The code  B) The client's secret  C) The state  D) The scope
**✅ A**

**14.** The BFF pattern's main's benefit:
A) Fewer network hops  B) The tokens never reach the browser  C) No's cookies  D) Better's caching
**✅ B**

**15.** The `Referrer-Policy: no-referrer` prevents:
A) The CSRF  B) The code's leakage via the Referer  C) The XSS  D) The caching
**✅ B**

**16.** The SAML's XSW (the signature's wrapping) attack is prevented by:
A) A longer's key  B) A certified's library (OpenSAML) + the signature-aware's reading  C) The HTTPS  D) The `aud` check
**✅ B**

**17.** The SAML's `InResponseTo` defends against:
A) The CSRF  B) The replay  C) The XSW  D) The algorithm's confusion
**✅ A**

**18.** SCIM is used for:
A) The authentication  B) The user's lifecycle's provisioning  C) The token's signing  D) The MFA
**✅ B**

**19.** The JIT's provisioning should grant:
A) Admin  B) The default's role  C) The IdP's admin  D) Nothing
**✅ B**

**20.** The OAuth 2.1:
A) Removes the implicit's flow  B) Removes the PKCE  C) Allows `plain`  D) Removes the `state`
**✅ A**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The `state`'s CSPRNG**
1. Generate a 256-bit's state with `SecureRandom`.
2. Verify two calls never collide and the value is 43 characters of URL-safe's base64.
3. Compare with `UUID.randomUUID()` and `System.currentTimeMillis()`.

**🧪 Lab 2 — The `state`'s single-use's check**
1. Log in and capture the callback's URL.
2. Replay the exact same callback's URL → the app must reject it (the state is consumed).

**🧪 Lab 3 — The CSP's effect**
1. Add the strict's CSP.
2. Try an XSS's payload → the browser blocks it (check the console).

**🧪 Lab 4 — The `SameSite=Strict` breaking the callback**
1. Set `SameSite=Strict` on the session's cookie.
2. Complete the OAuth's flow → observe the "invalid_state_parameter".
3. Switch to `Lax` → it works.

**🧪 Lab 5 — The SAML's XSW's test**
1. Use the OpenSAML's `OpenSaml4AuthenticationProvider` with the signature's wrapping
   enabled.
2. Craft a wrapped's assertion → confirm the library's rejects it.

---

## 12. Code Challenges

### Challenge 1 — The strict's OAuth2's client (hard)
**Goal:** A client that implements the `state` (the CSPRNG, the session's binding, the
one-time's use), the PKCE (S256), the `nonce`, the `iss` validation, the exact's
`redirect_uri` allowlist, and the post-login's redirect's allowlist — with the negative's
tests for each.
**Requirements:** the CSPRNG, the storage's abstraction, the error's handling, the
tests.

### Challenge 2 — The BFF's session's service (hard)
**Goal:** The server-side's session's store (the Redis), the access + refresh token's
holding, the automatic's refresh before the expiry, the rotation's reuse's detection,
the cookie's writing (the flags), the logout's revocation, and the CSRF's protection.
**Requirements:** the concurrency, the fail's modes (the Redis's down), the tests.

### Challenge 3 — The SAML's SP with the claim's mapping (hard)
**Goal:** A SP that consumes the SAML's assertions from 3 different's IdPs (the
metadata's trust, the attribute's mapping per vendor, the role's resolution, the
JIT's provisioning, the deprovisioning's handling) and exposes a canonical's internal's
model.
**Requirements:** the metadata's trust, the per-vendor's mapping, the audit's log, the
tests with the sample's assertions.

---

## 13. Scenario-Based Questions

**S1.** *"A security auditor found that our web app's OAuth2 flow doesn't validate the
`state` parameter and stores the access token in localStorage. They want a fix by
Friday. What's the plan?"**
>
> **Ideal answer:** (1) **The framing:** two's findings, two's severities, and the
response's order is determined by the **exploitability**, not by the effort. The missing
`state` is a **live's vulnerability** (the login's CSRF is exploitable against every
user, right now, with a crafted's link). The token in the local's storage is a
**severity's amplifier** (it's not exploitable by itself — it requires an XSS, which you
may not have — but it turns any future's XSS into an account's compromise). So: **the
`state` first, this week**; the storage's migration as the **planned's work** (it's a
bigger's change). But there's a third's thing to do immediately: **check whether you've
already been compromised** (see §3 — the logs).
(2) **The immediate's fix (this week):**
(a) **The `state`:** the Spring's default (`HttpSessionOAuth2AuthorizationRequestRepository`)
already generates a **random** state and stores it in the session and removes it after
use — so if the audit found the `state` unvalidated, the likely causes are (i) a
**custom's repository** that doesn't compare the state, (ii) a **hand-rolled** callback
that skips the `OAuth2LoginAuthenticationFilter`, (iii) the `state` being generated but
the **comparison's failure being swallowed** (a `try/catch` that logs and continues), or
(iv) an old's Spring's Security version. The fix: use the default's filter and
repository, remove the custom's callback's handling, and **add the test** — a callback
with a **mismatched's state** must return a 401 and must **not** create a session.
(b) **The `nonce`:** verify the OIDC's provider's validation includes it (the Spring's
does by default when the nonce was sent — make sure the `authorizationRequestCustomizer`
doesn't drop the parameters).
(c) **The `redirect_uri`:** audit every registered's client (at the provider) for
wildcards; make them exact. Add a server's side check.
(d) **The interim's mitigation for the storage:** the lowest's-effort's win is to
**shorten the access token's TTL** (5-10 min) and add a **CSP** to reduce the XSS's
probability, plus **the `Referrer-Policy: no-referrer`**. If the app is a SPA, the
**quick's version of the BFF** is a single's endpoint that sets the httpOnly's cookie.
(e) **The log's review (do it now):** search the access's logs for the **callback's
requests with a `state` that doesn't match** a session's state (if you log it), for the
**callback's requests with no `state` at all**, and for the **callback's requests whose
`code` was already used** (the replay). Also check for the **single's IP's hitting
many's accounts' callbacks** (the credential's stuffing's pattern). If there's evidence
of exploitation, then it's an incident's response, not just a fix.
(3) **The planned's fix (the next's sprint — the storage's migration):**
(a) **The BFF pattern** — a small's endpoint (`/api/auth/session`) that holds the tokens
server-side (the Redis), sets the httpOnly's cookie, and the SPA's code stops using
the token entirely. The SPA's change is small (remove the token's storage, add
`credentials: 'include'`).
(b) **The CSRF's protection** — the cookie's auth means the CSRF's token is now
required (the `CookieCsrfTokenRepository` + the header's pattern). The API's mutating's
endpoints need the `X-XSRF-TOKEN` header.
(c) **The `SameSite=Lax`** and the flags.
(d) **The migration's plan** for the existing's sessions: the old's local's storage's
tokens are invalidated on the deploy (users re-login once) — acceptable if it's
communicated; alternatively, the deploy can keep a **transitional's shim** that
exchanges the old's token for a session's cookie (careful — the shim is itself a
security's surface).
(e) **The CSP's rollout** — start with `Content-Security-Policy-Report-Only` to find the
violations without breaking the app, then enforce.
(4) **The prevention (the process's fixes, so it doesn't recur):**
(a) **The PR's checklist** — an auth's checklist item (the state, the nonce, the PKCE,
the issuer, the audience, the algorithm, the storage, the cookies' flags).
(b) **The negative's test's suite** — the CI's tests that feed the callback with the
bad's inputs (a wrong's state, a missing's state, a replayed's state, a wrong's
`iss`, a wrong's `aud`, an expired's token, an `alg: none` token) and assert the
rejection. This is the real's prevention — the vulnerability was a *missing test*, not
a *missing feature*.
(c) **The dependency's hygiene** — the Spring's Security and the Nimbus's versions
patched; the SCA's scanning on.
(d) **The security's headers** as a **default's filter** (not a per-team's task) — the
CSP, the HSTS, the `Referrer-Policy`, the `no-store` on the auth's responses.
(e) **The periodic's review** — an auth's design's review for the new's features.
(5) The senior framing: "**I'd triage by exploitability, not by the audit's checklist.**
The missing's `state` is exploitable **today** with a single's crafted's link against
every user, so it's this week's fix — and the interesting's part is *why* it's missing,
because the Spring's default already does it correctly. The answer is almost always one
of: a custom's callback's handler that bypasses the framework's filter, a `try/catch`
that swallows the comparison's failure, or a custom's `AuthorizationRequestRepository`
that generates the state but doesn't compare it. So the fix is to **delete the custom's
handling**, use the framework's path, and add the **negative's test** that a mismatched's
state is rejected and creates no session. The local's storage's finding is a severity's
amplifier rather than a live's exploit — it needs an XSS to matter — so the **immediate's
mitigation** is the CSP (in `Report-Only` first) and the short's token's TTL, and the
**proper's fix** is the BFF's migration, which is a week's work for the API and a
small's change for the SPA. But the one thing I'd do **regardless of the fix's schedule
is the log's review** — if the state wasn't validated, the logs are the only's evidence
of whether it was exploited. And the prevention is the part that actually matters: the
vulnerability was a *missing test*, not a missing feature, so the CI's negative's suite
(the wrong's state, the replayed's state, the wrong's issuer, the wrong's audience, the
`alg: none`) is the real's fix, and the auth's checklist's item in the PR's template is
what stops the next's one."

**S2.** *"We're adding SSO to our B2B product. Our biggest customer wants to use their
Okta (SAML). The engineering team has never done SAML. What's the plan?"**
>
> **Ideal answer:** (1) **The framing:** the B2B's SSO's requirement has three's
dimensions that the team must decide before writing any's code: the **protocol's
support** (SAML, OIDC, or both — the customer's Okta supports **both**, so the choice is
driven by the *other* customers and by your's architecture), the **the claim's mapping**
(what the customer's IdP's attributes mean in your app), and the **the lifecycle's
integration** (how the users get into your app — JIT's provisioning, SCIM's
provisioning, or the customer's directory's push). (2) **The protocol's decision:** given
the customer's Okta supports OIDC perfectly well, **prefer OIDC** — the implementation
is far simpler (a JSON's token vs XML's signature's parsing), the mobile's support is
better, and the OIDC's flows' security's model is cleaner. But **support SAML** as well,
because SAML-only's enterprise's IdPs (the ADFS, the legacy's Ping, some of the banks)
are common and they'll come. The **implementation's shape:** if you expect many's
customers and many's apps, use a **federation's gateway** (a dedicated's service that
terminates both's protocols and issues a **canonical's internal's token**). For a
single's product with a single's app, the in-app's support is fine.
(3) **The plan's phases:**
**Phase 1 (the foundation, 1-2 weeks):** the identity's model — the **canonical's
internal's user** with the `(tenant, external_issuer, external_subject)` as the unique's
key, the local's roles, and the local's user's table. Then the **protocol's layer:**
the OIDC's client (the Spring's `oauth2Login` with the per-tenant's `ClientRegistration`
— a `ClientRegistrationRepository` that resolves the registration by the tenant, from
the DB) and the SAML's SP (the Spring's Security's `saml2Login` with a per-tenant's
`RelyingPartyRegistration` built from the stored's metadata). The **multi-tenancy's
key's design decision:** the tenant's resolution **must not** come from an untrusted's
parameter (the `?tenant=` in the URL is a classic's vulnerability — the attacker
redirects a victim to your app with a tenant's ID, and the victim logs into the
attacker's's tenant!). The resolution's options, in the order of safety: (a) the
**subdomain** (`https://acme.yourapp.com`) — the DNS is the trust's anchor (the
attacker must control the DNS); (b) the **email's domain's mapping** (the user types
their email; the domain determines the tenant) — needs the domain's ownership's
verification; (c) the **explicit's tenant's selection** (the user picks their org on
the login's page) — the safest of the UI's approaches because the user's choice is
confirmed; (d) the **the `iss` parameter** (the RFC 9207's `iss` tells you which
provider's response this is — the modern's and the clean's way to bind the response to
the tenant, and it prevents the mix-up's attack across the tenants). **Never** trust
the `state`'s contents for the tenant's selection — the state is opaque and you should
keep it that way.
**Phase 2 (the customer's onboarding, 1-2 weeks):** the **self-service's tenant's
setup** — the customer's admin's configures the IdP (the metadata's URL or the
issuer's URL, the client id/secret, the domain's allowlist). The **validation's
tooling:** a **test's connection** button that validates the metadata's reachability,
the certificate's validity, the claims's mapping (show the sample's attributes), and
the **attribute's mapping's configuration** (a UI mapping the customer's attribute's
names → your canonical's fields: the email, the first's name, the groups → the roles).
The **the claim's mapping's UI is the crux** of the product's success — every customer's
IdP names things differently, and the customer's admin **cannot** read your code. If the
mapping's configuration is a JSON's file that only your's engineers can edit, the
onboarding's cost is 5's hours per customer and your's support's queue is destroyed.
**Phase 3 (the user's lifecycle):** decide **JIT vs SCIM**. **JIT's provisioning** is
the MVP (the user is created on the first's login with the **default's role** — never
admin) — simple, and it works for most of the customers. **SCIM** is what the enterprise's
customers will ask for (the joiner/leaver's automation) — implement it when the customer
demands it, and implement it as the **SCIM 2.0's spec** (RFC 7644) with the `PATCH`'s
activation for the leaver. **The deactivation's problem:** the SAML's and OIDC's sessions
are long-lived, so the leaver's access persists. The controls: the **session's max's
lifespan** (the IdP's and the SP's), the **the periodic's re-validation** (the SP
re-checks the session's validity with the IdP periodically — the `prompt=none` check or
the back-channel's logout), and the **the SCIM's disable** triggering the local's
revocation (which invalidates the local's session, but not the IdP's).
**Phase 4 (the hardening, ongoing):** the **the customer's admin's testing's toolkit**,
the **the audit's log** (who logged in, from which's IdP, with which's claims), the
**the per-tenant's configuration's validation** (the metadata's URL is SSRF-able — the
admin's configuration is a privileged's input; validate the URL's scheme and block the
internal's addresses!), the **the signing's certificate's rotation's process** (the
SAML's metadata's certificates expire — a process, not an event), and the **the
break-glass's access** (the local's admin's login that bypasses the SSO, for the
IdP's outage — sealed, audited, and a runbook).
(4) **The security's details to get right:** the **the IdP's SP-initiated's flow** (the
app redirects the user to the IdP — the usual's case); the **the IdP-initiated's flow**
(the IdP redirects the user to your app with the assertion — the **SSO's portal's**
case; this requires your SP to be able to accept an unsolicited's response, which means
the **the `InResponseTo`'s check must be relaxed** for that flow — the OpenSAML's
handles it with the `wantAuthnRequestsSigned` and the unsolicited's-response's
settings, and it's a common's source of bugs); the **the signature's requirements**
(`requireSignedAssertions(true)`; the signed's responses are optional); the **the
algorithm's restriction** (no SHA-1 — the SHA-1's sunset means some's IdPs still
default to it, and you'll need to configure them to move to SHA-256); the **the
`assertion`'s audience** (the `AudienceRestriction` must match your entity id exactly);
the **the NameID's format** (the `emailAddress` vs the `persistent` vs the
`transient` — the `transient` is useless for a user's account's linking: it changes
every's login! Ask for the `persistent` or the `emailAddress`).
(5) The senior framing: "**the hard's part of B2B's SSO isn't the protocol — it's the
onboarding's economics and the tenant's resolution's security.** Every customer's IdP
names its attributes differently, so the claim's mapping has to be a **self-service's
UI**, not a code's change; otherwise every customer is a support's ticket and the
product's SSO is unsellable. And the tenant's resolution must be bound to something the
attacker can't forge — the **subdomain** (the DNS is the trust's anchor) or the
**`iss` parameter** (RFC 9207, which also kills the mix-up's attack across the
tenants) — never a `?tenant=` in the URL, because that's an open's redirect to an
attacker's tenant and a phishing's primitive. Then there's the lifecycle: **JIT** for
the MVP (the default's role on the first's login — never admin), **SCIM** when the
enterprise's customer demands it, and the **deactivation's gap** — a disabled's user
keeps working until the session's expiry, so the session's max and the periodic's
re-validation are the real's controls, not the SCIM. And the operational's bit that
bites the SAML's implementations in production: the **signing's certificate's rotation**
— the IdP's certificate expires, your's SP's trust's config goes stale, and every
user's login's broken at once. That needs a process with a rehearsal, not a
monitoring's alert. Finally, the **SSRF's risk in the customer's configuration** — the
metadata's URL is set by the customer's admin, so validate the scheme, block the
internal's address's ranges, and fetch it from an egress's proxy. The protocol's
choice itself: prefer **OIDC** where the customer's IdP supports it (the Okta does),
but support **SAML** because the SAML-only's IdPs are still a large's segment of the
enterprise's market, and keep the **protocol's layer isolated** behind a **canonical's
identity's model** so you can add a third's protocol (the SCIM-style's push's
provisioning) without touching the apps."

---

## 14. Tricky Interview Questions

**T1. "If the ID token is signed, why do you need the `at_hash`?"**
> Because the ID token and the access token travel on **different's channels** and
arrive at **different's parties**. The ID token goes to the **client** (and in the
hybrid's flow, through the browser's front channel); the access token goes to the
**resource's server** (the back channel). The `at_hash` proves they came from the
**same's authorization's response** — defending against a **mix-up's attack** where an
attacker combines a valid's ID token (from their own's login) with a valid's access
token (from elsewhere). The hash is of the access token, so the ID token's carrier can
verify the binding without revealing the access token.

**T2. "Why is the `sub` a pair with the `iss` in practice, even though the spec says it's
locally unique within the issuer?"**
> Because a **user's identity is only meaningful in the context of the issuer**. If your
app federates with three's providers, provider A's `sub=1234` and provider B's
`sub=1234` are different's people. The spec's model: the identity's key is
`(iss, sub)`. Your local's database must store both. In practice, many's apps create
the local's user with just the `sub` and get it wrong — then two's unrelated's users
collide into one account, which is a **privilege's escalation** if one of them is an
admin.

**T3. "The OAuth 2.1 removes the implicit flow and the password grant. What breaks?"**
> (1) The **implicit's flow** users — the legacy's SPAs that relied on the tokens in the
fragment. They must move to the **code's flow + PKCE** (a client-side's change, usually
mechanical) or to the **BFF**. (2) The **password's grant** users — the legacy's
first-party's mobile's apps and the CLI's tools that collected the user's password. They
must move to the **authorization's code + PKCE** (a native's app's flow) or to the
**device's flow** (for the input's constrained's devices) or to the **client
credentials** (for the truly's machine-to-machine's cases). The **device's flow** is the
one that's most's commonly's forgotten in that migration. (3) The **remote's
password's grant** (the AS's credential's exchange) — for the first-party's apps that
need the user's password at the AS — there's no direct's equivalent; the **OIDC's
hybrid's** or the **CIBA's** (the Client-Initiated's Backchannel's Authentication) flow
is the modern's answer.

**T4. "How do you handle a security's incident where tokens may have leaked?"**
> (1) **The containment:** rotate the **signing's keys** (the AS's realm's keys) — this
invalidates every's token that the AS issued (the resource's servers must fetch the new
JWKS, which they do automatically — but the **JWKS's cache's TTL** determines how fast
the old's tokens die; reduce it during an incident); (2) the **client's secrets'**
rotation (if the leak is a client-side's secret — the same's procedure, plus the
audit of what that client could do); (3) **the session's revocation** — the AS's
**admin's sessions** API (the Keycloak's `logout` endpoint per session, the
`/admin/realms/{realm}/sessions/{id}` revocation) and the **the resource's server's**
local's revocation; (4) **the investigation:** the access's logs (the token's `jti`, the
`sub`, the `aud`, the IP, the user-agent) — the `jti` is the key's identifier for
auditing; the AS's token's events; (5) **the communication** — the users and (if
applicable) the regulator's notification's timing; (6) the **the postmortem's actions**
with owners and dates. The honest's note: the **JWT's statelessness is a liability**
here — you can't revoke a single's JWT, which is one of the reasons the short's TTL
matters and why the refresh token's rotation (with the server-side's state) is the
revocable's part.

**T5. "Is the `state` parameter necessary if you use the PKCE and the `nonce`?"**
> The `state` protects the **response's binding to the session**; the PKCE protects the
**code's redemption**; the `nonce` protects the **ID token's provenance**. The login's
**CSRF** — the attacker logging the victim into the attacker's account — is caught by the
`state` (the victim's session has no matching's state) and **not** by the PKCE (the
attacker's flow has a valid's PKCE of its own) nor by the `nonce` (the ID token in the
attacker's flow carries the attacker's nonce... and the victim's app's session has a
different's one, so actually the `nonce` *does* catch it). Hmm — let me be careful: the
`nonce` **does** help against the login's CSRF, because the attacker's ID token carries
the attacker's nonce. But the `state` is the primary's defence and is what the OAuth 2.1
requires, and it also protects the case where the provider doesn't return an ID token
at all (the plain's OAuth2's flow, where there is no `nonce`). The strongest's framing:
"**all three are required, and the reason is that the OAuth 2.1's threat's model
enumerates them separately.** The `state` is the session's binding; the PKCE is the
code's redemption's binding; the `nonce` is the ID token's binding. In the OIDC's flow
the `nonce` overlaps with the `state` in practice, but the `state` is what protects the
non-OIDC's OAuth2's flows (where there is no ID token and no `nonce`) and what protects
you when the provider's implementation of the nonce's echo is unreliable. The cost of
each is trivial; the cost of missing one is an account's takeover."

**T6. "Why does the SAML need a browser's POST binding? Why can't it use a redirect like
OIDC?"**
> Because of the **assertion's size and the XML's content** — a SAML's assertion (with
the attributes, the signature, the conditions) is typically 2-10 KB, and a redirect's
URL has a practical's length's limit (and the URL's length's limits vary across the
browsers and the servers). The **POST's binding** — the IdP renders an HTML's form with
the assertion's base64 in a hidden's field and auto-submits it to your ACS's URL —
has no practical's length's limit. The historical's reason: SAML predates the modern's
browser's URL's limits and was designed for the **signed's XML's assertion** to travel
intact. The **artifact's binding** (a third's option) solves the same's problem by
exchanging the artifact for the assertion over a SOAP's back channel — which is why it's
the "the secure's" option (the assertion never passes through the browser) but the
most's complex. The OIDC's design avoids the problem entirely by putting the **code**
(a short's opaque's string) in the URL and exchanging it over the back's channel for
the tokens.

**T7. "The CSP with `strict-dynamic` — what does it actually protect against?"**
> The **XSS's execution**. `strict-dynamic` (which requires `script-src` to have a
nonce or a hash) means: only the scripts that were **nonce'd** (the ones the server
injected) are allowed to run, and any script they load is **trusted by
transitivity** — the `script-src` allowlist is ignored for the dynamically's loaded
scripts. This defeats the classic's bypasses: an injected's `<script src="https://evil.com/x.js">` won't run (it's not nonce'd and the allowlist is ignored), but a `document.createElement('script')` with a `src` set by an **already-trusted** script *will*. The trade-off: `strict-dynamic` breaks the **inline's** handlers (`onclick=`) and the **eval**-based's code, so the codebase must be free of inline's handlers (which is good hygiene anyway) and the CSP must be applied with a **nonce** that's regenerated per's response. The rollout's advice: start with
`Content-Security-Policy-Report-Only` to enumerate the violations, fix them, then
enforce.

**T8. "How do you safely rotate the SAML signing certificate without downtime?"**
> SAML's metadata contains the **signing's certificate** and the IdP's SP's config
contains the **metadata's trust**. The rotation's sequence: (1) publish the **new's
certificate in the metadata** while the **old's** is still valid (the metadata can
contain **multiple's certificates** — the validators try each); (2) the SP's config
fetches/updates the metadata (the automatic's fetch — the SP's must poll, not cache
forever, and must handle the metadata's change without a restart); (3) switch the IdP's
**signing's key** to the new's one (the assertions are now signed with the new, and
both are trusted); (4) after the old's certificate's expiry and the validation of
traffic, remove the old's from the metadata. The risk: the metadata's change breaks
the SP's if the SP caches the metadata too long, or if the SP's parser rejects the
metadata's structure's change. The rehearsal is mandatory — do it with a test's tenant
first. And the **certificate's expiry's monitoring** is the control: alert at 30/14/7
days.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the CSP) → *"How do you roll out a strict CSP on a legacy app without
breaking it?"*
→ `Content-Security-Policy-Report-Only` first, with a `report-uri`/`report-to` endpoint
that aggregates the violations. Fix the violations (the inline's scripts → the nonces,
the CDN's origins → the allowlist, the third-party's widgets → the allowlist or a
proxy). Then enforce. The nonce's generation: a random's value per's response,
injected into the CSP's header and the script's tags by the server's side (the
templating's engine). The alternative to the nonce's CSP is the **hash's** based's CSP
(`script-src 'sha256-...'`) for a fully's static's build.

**F2** (after the SAML) → *"What happens when the customer's IdP sends an attribute you
don't map?"*
→ It's **ignored** by default (the unmapped's attributes are simply not mapped into
your canonical's model). The risks are: (a) the **local's role's defaulting** — if a
customer's IdP doesn't send the groups at all, everyone gets the default's role, and
the customer will report "the permissions don't work"; (b) the **attribute's name's
drift** — a customer's admin's renames an attribute and your mapping silently's breaks
(the user's role drops to the default). The mitigations: the **the mapping's
validation's testing** (the "the test's connection" tool that shows the sample's
attributes), the **the required's attribute's enforcement** (fail the login's
configuration if a required's attribute is missing, rather than silently's degrading),
and the **the monitoring's alert** on the mapping's coverage (the ratio of the logins
that arrive with the full's attribute's set).

**F3** (after the BFF) → *"How does the BFF handle the token's refresh when the access
token expires mid-request?"**
→ The BFF's filter: on a 401 from the upstream, the BFF attempts **one** refresh (a
single-flight — all the concurrent's requests share one refresh's promise), retries the
upstream's call **once**, and if that fails, redirects the browser to the login. The
critical's details: the refresh's **proactive** behaviour (refresh when the token is
within the skew's window of the expiry, not reactively — the reactive's path adds a
round trip to the user's latency), the **the single-flight** (without it, 50 concurrent
requests → 50 refreshes → the AS's rate's limit, and the refresh's rotation's reuse
detection would kill the session!). And the **the failure's mode** when the refresh's
token is invalid (the reuse's detection) → the session is destroyed and the user
re-authenticates.

**F4** (after the tenant's resolution) → *"How do you prevent a user from one tenant
logging into another?"*
→ The **the identity's binding** — the local's user is created within the tenant that
the **IdP's issuer** identifies, not within the tenant the request's URL suggests. The
`iss` (RFC 9207) or the `sub`'s lookup in the per-tenant's table determines the
tenant; the URL's tenant is only a **routing's hint** for *which* IdP to send them to,
never an authority's decision. And the **the domain's verification** — a user's email's
domain must be verified as belonging to the tenant before the JIT's provisioning — so
someone with a Gmail address can't create a user in a tenant that expects
`@acme.com`.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We had an incident: an employee's session token was stolen from a browser
extension. The attacker used it for 3 days before we noticed. How do you respond, and
what do you change so it can't happen again?"**
>
> **Ideal answer:** (1) **The framing:** this is a **token's theft's incident** with a
**delayed's detection** and a **long's dwell's time** — both are the design's failures,
not just the incident's. The response has three's phases (contain, investigate, learn),
and the **learn**'s phase is the interview's real's content. (2) **The containment (the
first's hours):**
(a) **Revoke the session's access** — kill the user's sessions at the IdP (the
Keycloak's admin's API: the `/admin/realms/{realm}/sessions/{id}` revocation, the
`logout` endpoint with the `refresh_token`), and **rotate the refresh's token's family**
if your system has one. If the token is a **JWT**, you cannot revoke it — **which is the
first's lesson** (see §4).
(b) **Check the blast's radius** — what could the attacker do? If the access token was
your own's JWT with the scopes, the attacker could do **everything the user could do**
for the token's lifetime. The scope's review: the user's scopes should have been
**minimal** (the principle of least's privilege) — if the token carried `admin` because
the app's roles are coarse, the damage is far's larger.
(c) **The signing's key's rotation** — if you suspect a systemic's leak (a
misconfigured's API, a logged's token), rotate the realm's keys. That invalidates
**every's** token, so it's disruptive; it's the right's call if the leak's scope is
unknown.
(d) **The preserve's evidence** — snapshot the access's logs, the IdP's events, the
audit's log, **before** they rotate. The investigation's timeline depends on the logs'
survival's window.
(3) **The investigation:** the **forensic's questions** — what was accessed (the
`jti`'s trail in the API's logs), from where (the IP, the user-agent — the extension's
identity is often visible in the UA), when (the exact's window), and what the attacker
**used** it for (the data's exfiltration — the bulk's reads, the exports, the
searches' patterns). The **the token's claims** tell you the impersonated's user's
permissions; the **the session's records** tell you whether the user was really's
active at the time (if the user was on holiday, it's a theft; if they were actively
using the app, it might have been a session's fixation or a legitimate's concurrent's
access). (4) **The lessons (the actual's interview's content) — four's changes:**
**(a) Move the tokens out of the browser's reach.** The theft's vector was a
**browser's extension** reading the local's storage. The BFF's pattern (the httpOnly's
cookie, the tokens server-side) doesn't stop a malicious's extension from *using* the
session, but it stops it from *exfiltrating a reusable token* — the extension would
have to act in the session's context (and the CSP + the extension's permissions' model
make that harder). **The honest's framing:** no's browser's design can fully's stop a
malicious's extension; the **blast's radius's reduction** is the goal. (b) Shorten the
lifetime and add the **revocation's capability.** The 3 days' dwell was possible
because the token was long-lived and the API's server could not revoke it. The
**the refresh's token's rotation with a server-side's session's store** makes the
**session's revocation** possible — kill the session's record, and the access tokens
issued from it become unusable at the next's refresh. (c) **Detect faster.** The 3
days' detection's delay is the process's failure. The controls: the **anomaly's
detection** (the impossible's travel, the new's device, the new's geography, the
unusual's hour, the **the token's reuse from a different's user-agent** — the JWT's
`jti` seen twice with a different's fingerprint is a strong's signal), the **the
alerting** on the sensitive's actions (the bulk's export, the permission's change, the
new's payee), and the **the session's visibility** (a "your account's active's sessions"
page — the user sees the attacker and can revoke it). (d) **The scope's minimality.**
The impersonated's user's token's scope defines the damage. If the app's roles are
coarse (`user`, `admin`), the token carries a broad's power. The **the per-endpoint's
permissions** (or at least the **the fine-grained's scopes**) reduce the damage's
ceiling. (5) **The organisational's changes:** the **the extension's policy** (the
company's managed's browser, the approved's extension's list — many's companies now
**block the extensions** entirely in the managed's browser), the **the session's
timeout's** policy (the idle's timeout, the absolute's limit, the re-authentication for
the sensitive's actions), and the **the incident's runbook** (the tokens' revocation's
playbook, the **who can declare's an incident**, the communication's templates, the
regulator's notification's criteria). (6) The senior framing: "**the dwell's time is the
design's metric.** Three days' is a direct's consequence of a long-lived,
un-revocable's bearer token in the browser. So the changes I'd make are: (1) the **BFF**
so there's no reusable token to exfiltrate; (2) the **server-side's session's store**
with the **refresh's rotation** so a session *can* be killed (a JWT alone cannot be
revoked — that's a structural's limitation you have to design around); (3) the
**anomaly's detection** on the `jti` and the fingerprint, so the next one's detection is
minutes rather than days; (4) the **scopes' minimality**, so the ceiling of the damage
is lower; and (5) the **session's visibility's page**, so the user can see and revoke
the attacker themselves. The extension's vector is worth naming honestly — you can't
fully's stop a malicious's extension, so the goal is the blast's radius, not the
prevention. And the one I'd raise in the design's review: **if the theft's detection
took three days, the detection's pipeline is the real's defect** — the token's `jti` was
there the whole's time; nobody was looking for the reuse."

**R2. *"The security team says our SAML implementation is one XSS away from a full
takeover. They want a rewrite. Is there a middle path?"**
>
> **Ideal answer:** (1) **The framing:** the finding is probably about the **XML's
processing's surface** — the classic's class of vulnerabilities in the SAML's
implementations is the **XML Signature's Wrapping (XSW)** and the **XSLT's injection**,
which come from **how the assertion's consumer reads the signed's XML**. A "the
rewrite" is almost never the right's answer, because **the vulnerability is in the
configuration and the library's usage, not in your business's logic**. The correct's
first's step is to **identify the exact's vulnerability** (which class? which's
library's version? which's parsing's behaviour?) — the "the XSS away from a takeover"
is a symptom's description, not a diagnosis. (2) **The diagnosis's steps:** (a) the
**library's version** — the OpenSAML's version (the XSW's protection improved
substantially across the versions, and the `OpenSaml4AuthenticationProvider` has the
signature's validation's fixes); (b) the **the signed's element's selection** — is the
assertion's consumer reading the **signed's** assertion, or is it searching for
*any* assertion in the response and trusting the signature's presence? That single's
bug is the XSW's root's cause; (c) the **the signature's requirements** — is
`wantAssertionsSigned` enforced? Is an unsigned's assertion's ever accepted? (d) the
**the `InResponseTo`'s checking** — is the unsolicited's response's allowed? (e) the
**the XML's parser's configuration** — are the external's entities disabled (the XXE)?
Is the XSLT's processing's disabled? (f) the **`AudienceRestriction`** and the
`Recipient` — are they checked? (g) the **the replay's protection** — the
`NotOnOrAfter` and the `InResponseTo`'s single-use's tracking.
(3) **The middle's path (the hardening's plan — this is the interview's real's
content):**
**Tier 1 (days — the configuration's fixes, no's code's rewrite):**
- Update the **OpenSAML** to a current's version (a dependency's bump, usually a few
  lines) and re-run the tests.
- Enforce **`requireSignedAssertions(true)`** and reject the unsigned's responses.
- Enforce the **`InResponseTo`** for the SP-initiated's flow (the unsolicited's
  response's allowed only if you support the IdP-initiated's flow, and then with the
  **the `InResponseTo`'s tracking's store** and the short's window).
- Enforce the **`AudienceRestriction`** (the exact's match on your entity id) and the
**`Recipient`** (the exact's match on your ACS's URL).
- Enforce the **`Issuer`** (the allowlist's of the trusted's IdPs).
- Enforce the **the algorithm's restriction** (the SHA-256, not the SHA-1; the RSA-2048+
  minimum) — the configuration's metadata's `SignatureMethodFilters` and the
`SignatureAlgorithmFilters` in the Spring's SAML's config, or the OpenSAML's
`SignatureAlgorithm` restrictions.
- Enable the **the parser's hardening** (the XXE's off, the XSLT's off).
- Add the **the replay's cache** (the `InResponseTo`'s IDs, with a TTL).
**Tier 2 (weeks — the isolation's refactor):** extract the SAML's handling into a
**single's component** (an `AuthenticationProvider` or a dedicated's library) behind
your app's own's **authentication's interface** — so the SAML's specifics are in one
place and the tests are comprehensive. Add the **the fuzzing's harness** (feed the
parser the malformed's and the crafted's assertions — the `XXE`'s payloads, the
XSW's variants, the billion's laughs) as a CI's job. The **the security's test's
suite** with the known's XSW's payloads (the OWASP's SAML's cheat sheet's examples).
**Tier 3 (months — the protocol's migration):** stand up the OIDC's path alongside
(SAML **and** OIDC, per the customer), with a **canonical's identity's model** so the
apps don't know which's protocol's authenticated the user. Migrate the customers whose
IdPs support OIDC, one at a time, behind a feature's flag. Deprecate the SAML's path
when the last's SAML-only's customer is gone.
(4) **The middle's path's other's element — the compensating's controls** (while the
hardening's in progress): the **the session's limits** (a shorter's max's lifespan),
the **the re-authentication's requirement** for the sensitive's actions, the **the
auditing's alert** on the unusual's logins (a new's IP, an impossible's travel, a new's
user-agent, the logins outside the hours), the **the WAF's input's filtering** (as a
blunt's instrument — it won't stop a targeted's attack but it raises the bar), and the
**the bug's bounty / the disclosure's process** to find the other XSS's before the
attackers do.
(5) **The important's nuance to raise:** the **SAML's "the XSS's vector"** in the
question is interesting — in the SAML's model, the attacker's payload typically arrives
as a **base64's assertion in a POST's body** (the browser's form's auto-submit), and
the **XML's parsing** of it is the attack's surface. The mitigations: the **strict's
parsing** (the hardened's parser's config), and — the elegant's one — the **the
IdP-initiated's flow's avoidance** and the **the assertion's consumption's isolation**
(an untrusted's XML's parsing in a **separate's process** with no's filesystem's access
and no's network's access — a "the parser's sandbox", which is a real's technique for
the XML's attack's surface). Also the **the `Content-Security-Policy` on the ACS's page**
matters: the SAML's ACS's page auto-submits a form — the CSP's `form-action` restricts
where it can post, and the page itself should have **no's scripts** at all (the
`default-src 'none'`), so the classic's XSS's payload has nowhere to run.
(6) The senior framing: "**I wouldn't rewrite before I'd diagnose.** 'One XSS away from
a takeover' is a symptom's description, and the SAML's vulnerabilities are almost
always in the **library's version and the configuration's choices** — the
**XSW** comes from *how* the assertion's consumer selects the signed's element (a
one-line's fix with the correct's API), the **unsigned's assertion's acceptance** is a
config's flag, the **`InResponseTo`/`Audience`/`Recipient`** are validations that either
exist or don't, and the **algorithm's restrictions** are two's lines in the metadata's
config. So Tier 1 is days, not months, and it removes the finding. Then I'd **isolate**
the SAML's handling behind your own's authentication's interface with a comprehensive's
test's suite — including the known's XSW's payloads and a **fuzzing's harness** — so
Tier 3's migration is a **configuration's change**, not a rewrite's project. The
compensating's controls in the meantime are the session's limits, the alerting on the
unusual's logins, and — the one that's specific's to SAML — the **ACS's page's CSP**
(`default-src 'none'`, a strict's `form-action`), because the SAML's ACS's page is a
form's auto-submit's page and it has **no's reason to run any's script**. And I'd add
one more thing: the **parsing's isolation** — the XML's parsing of an untrusted's
assertion in a sandboxed's process, which removes the XXE's/XSLT's/injection's
blast's radius entirely. The 'rewrite' is a **last's resort**, and the honest's
diagnosis usually shows it isn't necessary."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The 403s after the CSP's rollout | The inline's scripts / the third-party's origins | The nonces; the allowlist; `Report-Only` first |
| "invalid_state_parameter" | The `SameSite=Strict`'s cookie; the state's not stored | `Lax`; the separate's `None` cookie |
| The users can't log in after the deploy | The `state`'s storage's change (the session's rotation) | The sticky's sessions; the state's store |
| The SAML's logins break after a date | The signing's certificate's expiry | The certificate's rotation's process |
| The SAML's "the assertion is not valid" | The metadata's cache; the algorithm's mismatch | The metadata's refresh; the SHA-256 |
| The XSW's suspicion | The assertion's consumer's selection | The certified's library's API |
| The 401s after the JWKS's rotation | The API's cached the old's keys | The Spring's auto's refetch; the `kid`'s unknown's handling |
| The tokens in the access's logs | The logging's config | Redact the `Authorization` header |
| The CORS's errors on the SPA | The CORS's config vs the credentials | The exact's origin; `allowCredentials` |
| The 429s from the AS | The token's re-fetching per call | The token's caching |

---

## 18. Debugging Scenarios

**D1. "Dump the ID token's claims and check them by hand:"**
```bash
decode() { echo "$1" | cut -d. -f2 | tr '_-' '/+' | base64 -d | jq .; }
decode "$ID_TOKEN"
echo "--- the expected's issuer:"; echo "https://as.example.com/realms/prod"
```

**D2. "Check the CORS's and the credentials:"**
```bash
curl -i -X OPTIONS https://api.example.com/api/orders \
  -H "Origin: https://app.example.com" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: authorization,content-type"
# look for: Access-Control-Allow-Origin: https://app.example.com (NOT *)
#           Access-Control-Allow-Credentials: true
```

**D3. "Find the callback's requests with a missing or mismatched's state:"**
```bash
# the access's log (the structured's JSON)
grep '"path":"/oauth2/callback' access.log \
  | jq -r 'select(.query.state == null or .query.state == "") | .ts' | head -20
```

**D4. "The SAML's response's decoding (the Keycloak's admin's):"**
```bash
# the SAML's decoder (the samltool or the python's onelogin)
python3 -m onelogin.saml2_settings --help
# or: the shibboleth's xmlsec's tool
xmlsec1 --verify --pubkey-pem cert.pem --id-attr:ID urn:oasis:names:tc:SAML:2.0:assertion response.xml
```

**D5. "Check the CSP's violations (the report's endpoint's data):"**
```bash
# the report's endpoint (the own's) — the violations
curl -s http://localhost:8080/csp-report | jq '.["csp-report"] | {blocked, violated}'
```

**D6. "The JWT's header (check the alg's pinning):"**
```bash
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d | jq .
# → the alg must be RS256, and never "none"
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the security's architecture for a consumer's fintech's app (the web + the
mobile + the partner's APIs).**
> (1) The **the credential's model** (the BFF for the web, the native's app's flow with
the PKCE for the mobile, the client credentials for the partners); (2) the **the token's
model** (your own's JWT, the short's TTL, the refresh's rotation, the server-side's
session); (3) the **the trust's boundaries** (the device's attestation, the root's
detection, the step-up's authentication for the money's movements); (4) the **the CSP
and the browser's security**; (5) the **the monitoring's and the detection** (the
anomaly's models, the session's visibility); (6) the **the incident's readiness**
(the key's rotation, the revocation's runbook, the notification's path).

**Q2. 🏗️ Design the federation's gateway for a B2B's platform with 300 tenants and
mixed's IdPs (SAML, OIDC, SCIM).**
> (1) The **the protocol's termination** (SAML's SP, OIDC's RP, the SCIM's server); (2)
the **the canonical's identity's model** (the tenant, the user, the roles, the
provenance); (3) the **the tenant's resolution** (the subdomain, the `iss`, the
domain's mapping); (4) the **the claim's mapping's configuration** (the self-service's
UI, the validation, the versioning); (5) the **the lifecycle's integration** (the JIT,
the SCIM, the deactivation); (6) the **the session's and the logout's model** (the
SP's sessions, the back-channel's logout, the re-validation); (7) the **the
availability's model** (the HA, the fallback's local's login for the IdP's outage, the
break-glass); (8) the **the audit's model** (the per-tenant's segregation, the export).

**Q3. 🏗️ Design the token's rotation and the revocation's model for a platform with
JWTs everywhere.**
> (1) The **the lifetime's hierarchy** (the access's 5 min, the refresh's 30 days, the
ID's 1 hour, the session's 8 hours); (2) the **the server-side's state's store** (the
Redis, the session's records, the family's rotation); (3) the **the revocation's
mechanisms** (the session's kill, the family's kill, the key's rotation for the
systemic's compromise, the `jti`'s blocklist for the targeted's one); (4) the **the
detection** (the `jti`'s reuse, the fingerprint's change, the anomaly's models); (5)
the **the blast's radius's controls** (the scopes' minimality, the step-up's
authentication, the per-action's re-authentication); (6) the **the incident's runbook**.

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the FRONT CHANNEL's defences: the state (the session's binding, CSPRNG,      │
│  one-time's use) + the nonce (the ID token's binding) + the PKCE (the code's  │
│  redemption's binding, S256 only) + the exact's redirect_uri                 │
│  the BACK CHANNEL: the client auth, the token's exchange                      │
│  the SERVER's validation: the signature, the PINNED's alg, the iss (exact),   │
│  the aud (contains), the exp, the nonce, the scopes                          │
├──────────────────────────────────────────────────────────────────────────────┤
│  the STORAGE: the BFF + the httpOnly's cookie (the tokens stay on the server) │
│  the cookies: HttpOnly, Secure, SameSite=Lax, the narrow's path              │
│  ⚠ the local's storage = the XSS's theft                                   │
│  ⚠ SameSite=Strict breaks the OIDC's callback → use Lax                     │
├──────────────────────────────────────────────────────────────────────────────┤
│  the HEADERS: the CSP (the nonce's, 'strict-dynamic'), the HSTS,             │
│  the Referrer-Policy: no-referrer (⚠ the code's in the URL), no-store on the  │
│  auth's responses, the X-Frame-Options                                     │
├──────────────────────────────────────────────────────────────────────────────┤
│  the SAML: the OpenSAML's library, requireSignedAssertions, the InResponseTo,│
│  the AudienceRestriction, the Recipient, the Issuer's allowlist, the SHA-256,  │
│  the replay's cache, the 'persistent' NameID, the no's-inline's scripts on the │
│  ACS's page, the parse's isolation                                         │
├──────────────────────────────────────────────────────────────────────────────┤
│  the SCIM: the create / the PATCH (the deactivate) / the lookup              │
│  ⚠ the deactivation doesn't kill the IdP's sessions → the session's max +    │
│    the re-validation                                                       │
│  the JIT's provisioning → the DEFAULT's role, never admin                   │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning
- → [06 · JWT Authentication](../06-JWT-Authentication/README.md)
- → [07 · Security Best Practices](../05-Spring-Security/07-Security-Best-Practices.md)
- → [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)

---
**Next Chapter:** → [06 · Enterprise SSO Patterns](./06-Enterprise-SSO-Patterns.md)
**Previous Chapter:** ← [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)
