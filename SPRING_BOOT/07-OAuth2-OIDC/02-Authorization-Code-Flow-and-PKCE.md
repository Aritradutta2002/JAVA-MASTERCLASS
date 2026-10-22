# 02 · Authorization Code Flow & PKCE — The Workhorse

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★★ (The #1 OAuth2 interview question)
> **Prerequisite:** [01 · OAuth2 Architecture & Core Concepts](./01-OAuth2-Architecture-and-Fundamentals.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — Two Channels, One Secret](#2-mental-model--two-channels-one-secret)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The flow, step by step](#31-the-flow-step-by-step)
  - [3.2 The `state` — the CSRF's defence](#32-the-state--the-csrfs-defence)
  - [3.3 The `nonce` — the replay's defence](#33-the-nonce--the-replays-defence)
  - [3.4 PKCE, in depth](#34-pkce-in-depth)
  - [3.5 The code's properties](#35-the-codes-properties)
  - [3.6 The token's exchange's validation](#36-the-tokens-exchanges-validation)
  - [3.7 The confidential vs the public's client](#37-the-confidential-vs-the-publics-client)
  - [3.8 The native's apps (RFC 8252)](#38-the-natives-apps-rfc-8252)
  - [3.9 The SPA's patterns](#39-the-spas-patterns)
  - [3.10 The Spring Boot's implementation](#310-the-spring-boots-implementation)
  - [3.11 The "the front channel's logout"](#311-the-the-front-channels-logout)
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

- **The authorization code flow** splits the login into two channels:
  - The **front channel** (the browser) — carries the **authorization code** (a short-lived,
    single-use, opaque string).
  - The **back channel** (your server → the auth server) — carries the **tokens**.
- **Why:** the code is useless to an attacker who intercepts it (it's single-use, short-lived,
  and — with PKCE — bound to the client that requested it). The tokens never touch the browser.
- **The two mandatory client-side defences:**
  - **`state`** — a random value, echoed back, verified on the callback → protects against
    **CSRF / session fixation** (the attacker's authorization code being injected into your
    session).
  - **`nonce`** (OIDC) — a random value, echoed in the ID token, verified → protects against
    the **replay of a captured ID token**.
- **PKCE (RFC 7636):** the client generates a `code_verifier` (a random 43-128 char string),
  sends `code_challenge = BASE64URL(SHA256(code_verifier))` in the authorization request, and
  presents the original verifier at the token exchange. **Mandatory for the public clients,
  recommended for all.** Always `S256`, never `plain`.
- **The `redirect_uri` must be sent in BOTH** the authorization request and the token
  exchange, and it must **exactly** match the registered one.
- **The code's properties:** single-use (the server's atomic's mark), short-lived (60-600 s),
  bound to (the client, the redirect URI, the user, the scopes, and the PKCE's challenge).
- **The one-liner:** *"The code flow is the standard because the authorization code is a
single-use, short-lived, PKCE-bound voucher that can safely travel through the browser;
the real tokens travel over the back channel where the client's credentials protect them.
The `state` stops the CSRF, the `nonce` stops the replay, and the PKCE stops the code's
interception — all three are mandatory."*

---

## 1. What & Why

### The problem the flow solves

The **implicit flow** (2010) returned the access token directly to the browser:

```text
   GET /authorize?response_type=token
   → 302 https://app/callback#access_token=eyJ...&expires_in=3600
```

**The access token is now in the browser's URL.** That means:
- The browser's history
- The `Referer` header sent to every third-party's resource on the page
- The analytics scripts (the Google Analytics, the Segment — they read the URL)
- The browser's extensions
- The screen's shoulder

And an access token is a **bearer credential** with no revocation's lever. Whoever reads it
has it for the full hour. The GitHub's 2022's breach (90M tokens) is a reminder of how this
happens at scale.

### The code flow's answer

```text
   GET /authorize?response_type=code
   → 302 https://app/callback?code=Ospl0XYZ
       (a code: single-use, 60s, useless without the PKCE's verifier)

   the BACKEND exchanges it:
   POST /token  { code, code_verifier, client_id, client_secret, redirect_uri }
   → { access_token, refresh_token, id_token }
       (the tokens NEVER touch the browser)
```

**The trust's reasoning:** the front channel is **hostile** (the browser's history, the
`Referer`, the extensions, the malware). So the front channel carries only a *voucher* that
is useless on its own. The back channel is **trusted** (your server → the AS over TLS, with
the client's authentication). So the back channel carries the *goods*.

---

## 2. Mental Model — Two Channels, One Secret

```text
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  the FRONT channel (the browser — HOSTILE)                              │
  │                                                                          │
  │   the /authorize request  (the parameters: the scopes, the state, ...)  │
  │   the callback            (the code + the state)                        │
  │                                                                          │
  │   the code:  single-use · 60 seconds · bound to the client & the PKCE    │
  │              → an INTERCEPTOR GAINS NOTHING                                │
  └──────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │  the code is the "the ticket's stub"
                                    ▼
  ┌──────────────────────────────────────────────────────────────────────────┐
  │  the BACK channel (your server → the AS — TRUSTED)                       │
  │                                                                          │
  │   POST /token { code, code_verifier, client_id, client_secret }          │
  │   → { access_token, refresh_token, id_token }                            │
  │                                                                          │
  │   the client's credentials + the PKCE's verifier → the exchange's proof  │
  └──────────────────────────────────────────────────────────────────────────┘
```

**The one-liner of the mental model:** *"Everything that could leak through the browser
must be useless by itself. The code is that thing."*

---

## 3. Deep Dive — Theory

### 3.1 The flow, step by step

```text
  STEP 0 — the client prepares the request
    code_verifier  = <43-128 random chars>          ← the SECRET
    code_challenge = BASE64URL(SHA256(code_verifier))  ← the PROOF
    state          = <random>                        ← the CSRF's token
    nonce          = <random>                        ← the OIDC's replay's token
    (both the state and the nonce are stored in the user's SESSION)

  STEP 1 — the browser is redirected to the /authorize
    GET https://as.example.com/authorize
        ?response_type=code
        &client_id=app-123
        &redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback
        &scope=openid%20email%20profile
        &state=8f2b1c...
        &nonce=n-42...
        &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
        &code_challenge_method=S256

  STEP 2 — the AS authenticates the user and collects the consent
    (if the session exists, the AS may skip the login; the consent may be skipped for
     the already-approved's scopes)

  STEP 3 — the AS redirects back
    302 https://app.example.com/callback?code=Ospl0XYZ&state=8f2b1c...

  STEP 4 — the client validates the state
    if (request.state != session.state) → ABORT (the CSRF)

  STEP 5 — the BACKEND exchanges the code
    POST https://as.example.com/token
      grant_type=authorization_code
      &code=Ospl0XYZ
      &redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback   ← the EXACT's one
      &code_verifier=<the original verifier>
      &client_id=app-123
      &client_secret=<the confidential's secret>  (or omitted, for the public's clients)

  STEP 6 — the AS validates EVERYTHING (see §3.6) and issues the tokens

  STEP 7 — the client validates the ID token
    the signature (the JWKS) | the iss | the aud | the exp | the nonce
```

### 3.2 The `state` — the CSRF's defence

**The attack the `state` prevents:**

```text
  the ATTACKER:
    1. starts a login at the AS with HIS OWN account
       → he receives a callback URL: https://app.example.com/callback?code=ATTACKER_CODE
    2. lures the VICTIM to that URL
       → the victim's browser hits your callback with the attacker's code
    3. your server exchanges the code → the victim's session is now the ATTACKER's account
       ("the login's CSRF" / the "the session's fixation")

  the RESULT: the victim enters his data into the attacker's account (the classic
  "the attacker uploads a file to his own Drive and the victim saves it" attack), or the
  victim's actions are attributed to the attacker.
```

**The defence:** the client generates a random `state`, stores it in the **session**, and
compares it with the returned one. The attacker's callback carries **his** state, which
doesn't match the victim's session → rejected.

**The implementation's rules:**
- The `state` must be **unpredictable** (≥ 128 bits from a CSPRNG) — not a timestamp, not
  a counter.
- The **comparison must be constant-time** (it doesn't have to be, but it's a good habit)
  and **must be present** (not "present if configured").
- The `state` must be **bound to the session** and **single-use** (delete it after use).
- The `state` can also carry **the context** (the "where was the user going" — the
  deep-link's target), but it must be **signed or server-side stored** — never trust a
  client-supplied `returnUrl` without validating it against an allowlist (the **open
  redirect** attack).

**The modern alternative:** some ASes support the **PKCE's binding** or the
**`state`-less** flow with the **OIDC's nonce** — but the `state` is still the correct
defence for the CSRF, and every implementation should use it.

### 3.3 The `nonce` — The replay's defence

**The attack the `nonce` prevents:**

```text
  the ATTACKER captures an ID token (e.g. from a log, a proxy, a browser's history,
  or a shoulder-surfing) and later replays it to the client's callback:
    GET /callback?id_token=<captured>&state=...
  → if the client accepts any ID token, the attacker can log in as that user
```

**The defence:** the client sends a random `nonce` in the authorization request; the AS
**echoes it** into the ID token's `nonce` claim; the client verifies that the ID token's
`nonce` equals the one it stored. A captured-and-replayed ID token carries the *original*
nonce, which won't match the fresh session's nonce.

**The rules:**
- The `nonce` must be **unpredictable** and **bound to the session** (the same as the
  `state`).
- It must be **verified** on every login.
- It's **required** for the OIDC's implicit/hybrid flows and **strongly recommended** for
  the code flow (the Spring's `OidcAuthorizationCodeAuthenticationProvider` validates it
  if the `nonce` was sent — the `NonceOidcAuthorizationCodeAuthenticationProvider`).

### 3.4 PKCE, in depth

**The RFC 7636's mechanism:**

```text
  the client generates:
    code_verifier  = 43-128 chars from the unreserved's set
                     (A-Z a-z 0-9 - . _ ~)
    code_challenge = BASE64URL(SHA256(code_verifier))     [the S256 method]
                    = BASE64URL(code_verifier)             [the plain method]

  the /authorize carries:  code_challenge + code_challenge_method
  the /token carries:      code_verifier

  the AS:  BASE64URL(SHA256(code_verifier)) == code_challenge ?  proceed : reject
```

**The `plain` method:** the verifier is sent as-is in the authorization request (and the
`code_challenge` equals it). **The vulnerability:** the code's interceptor sees the
`code_challenge` in the authorization request → it can also send it as the `code_verifier`
in the exchange. So the plain's method provides **no** protection against the interception
— it's only useful for the clients that can't compute SHA-256. (There was also a known
**downgrade** attack where the AS accepted the plain's challenge even when the client
intended the S256 — the fix is that the AS must **bind the method** to the code and
**reject** a mismatch.)

**The S256's requirement:** the `crypto.subtle.digest('SHA-256', ...)` in the browser, or
the equivalent in the mobile/desktop. **The old browsers without the WebCrypto's API can't
do it** — that's the compatibility's issue that pushes people to the BFF.

**The verifier's generation (the correct way):**
```javascript
function generateCodeVerifier() {
  const array = new Uint8Array(32);
  crypto.getRandomValues(array);
  return base64urlEncode(array);   // 43 characters
}

async function generateCodeChallenge(verifier) {
  const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(verifier));
  return base64urlEncode(new Uint8Array(digest));   // 43 characters
}
```
**The trap:** using `Math.random()` for the verifier — it's not a CSPRNG. The verifier is a
**secret**; it must be cryptographically random.

**The verifier's storage (the critical's detail):**
- The **confidential's client** (the backend) → the verifier is **never** in the browser. The
  backend generates it, sends the challenge, and keeps the verifier for the exchange.
  → This is the **BFF's pattern** and it's the strongest: the PKCE's secret never exists in
  the browser at all.
- The **public's client** (the SPA) → the verifier must be kept in the **session's storage**
  (or the memory) and used at the exchange. **Never** in the `localStorage` (a XSS could
  read it, but it also would have read the access token — the point is the localStorage
  survives the tab's closure, so the verifier might outlive the flow's session).

### 3.5 The code's properties

| The property | The value | Why |
|-------------|-----------|-----|
| **Single-use** | The server marks it used atomically | A replay of the code is useless |
| **Short-lived** | 60-600 s (the RFC's recommendation: a maximum of 10 minutes) | A stolen's code has a tiny's window |
| **Opaque** | A random string (not a JWT) | No information's leak, no forgery |
| **Bound to the client** | The `client_id` | A different client can't exchange it |
| **Bound to the redirect URI** | The exact's `redirect_uri` | Prevents the open-redirect's variant |
| **Bound to the user** | The session | The token is for the authenticated user |
| **Bound to the scopes** | The granted's scopes | The exchange can't ask for more |
| **Bound to the PKCE** | The `code_challenge` | The interceptor can't exchange it |
| **Bound to the nonce/state** | (OIDC) | The replay's protection |

**The atomic's single-use enforcement (the crucial's detail):**
```sql
-- the redemption (the atomic's update, then the insert)
UPDATE authorization_codes
   SET used = TRUE, used_at = now()
 WHERE code = :code AND used = FALSE AND expires_at > now();
-- if 0 rows → the code was already used or expired → reject
```
**Why it must be atomic:** two concurrent exchanges of the same code must not both succeed.
Use a single `UPDATE ... WHERE used = FALSE` (the DB's row's lock) and check the affected
row's count — not a `SELECT` then an `UPDATE`.

### 3.6 The token's exchange's validation

**Everything the authorization server must check at `/token`:**

```text
  ① the code exists and is not expired
  ② the code has not been used (the atomic's redemption)
  ③ the code belongs to THIS client (the client_id matches)
  ④ the redirect_uri matches EXACTLY the one in the authorization request
  ⑤ the PKCE: BASE64URL(SHA256(code_verifier)) == the stored code_challenge
  ⑥ the client authenticates (the secret, the private_key_jwt, or the mTLS)
  ⑦ the user is still valid (the session exists, the account isn't disabled)
  ⑧ the granted's scopes are carried into the token (never more than granted)
```

**And the client must check on the ID token:**
```text
  ① the signature (the JWKS, the algorithm's pin)
  ② the iss == the expected's issuer
  ③ the aud contains YOUR client_id
  ④ the exp (the clock's skew's tolerance)
  ⑤ the nonce == the one you sent
  ⑥ the azp == your client_id (if there are multiple audiences)
```

**The Spring's does most of this for you** — the `OidcAuthorizationCodeAuthenticationProvider`
validates the ID token's signature, the `iss`, the `aud`, the `exp`, and the `nonce` (if the
`nonce` was sent). But you should still **know** it, because when you use the OAuth2's
client without the OIDC (e.g. a pure OAuth2's provider), the `nonce` validation is
**yours** to write.

### 3.7 The confidential vs the public's client

| | The confidential's client | The public's client |
|---|---|---|
| **The secret** | Held server-side | **Cannot** be held |
| **The PKCE** | Recommended (and required in OAuth 2.1) | **Mandatory** |
| **The client authentication** | `client_secret_basic` / `private_key_jwt` | None (the PKCE is the proof) |
| **The examples** | The web's backend, the BFF, the server's app | The SPA, the mobile, the desktop, the CLI |
| **The risk if misconfigured** | The secret leaks → the attacker impersonates the client | The code's interception → mitigated by the PKCE |

**The "the public's client" determination:** if the client can't keep the secret
confidential (i.e. it ships in a binary or a bundle that the user can inspect), it's
public. **Mobile apps are public** — the binary can be decompiled, so the embedded secret
is extractable. The OAuth 2.1's guidance: treat all the native's apps as public.

### 3.8 The native's apps (RFC 8252)

**The problem:** the native's app can't receive a redirect on `https://app.example.com` —
it doesn't own that domain's browser's session. It needs a URI scheme it controls.

**The RFC 8252's three options:**

| The option | The example | The notes |
|-----------|------------|----------|
| **The private-use's scheme** | `com.example.app:/oauth2redirect` | ⚠ the claim's collision (another app can register the same scheme) — the PKCE mitigates it |
| **The claimed's HTTPS** | `https://app.example.com/callback` | ✅ **The recommended** — the app's links' verification (the App Links / the Universal Links) prevents the other apps from claiming it |
| **The loopback's IP** | `http://127.0.0.1:8080/callback` | For the desktop's apps; the port's wildcards are allowed (`http://127.0.0.1:*/callback`); the loopback's exemption from the HTTPS's requirement |

**The native's app's extras (the RFC 8252's §7.3):**
- The **PKCE is mandatory** (S256).
- The **external's browser's session** is required (the user sees the browser's UI, not an
  embedded's WebView) — this avoids the "the embedded's browser's phishing" problem, where
  the app could fake the login's page. (Some apps do use a WebView — the ASes can detect
  and reject it; the RFC says to use the external's browser.)
- The **client's registration** should not use the **exact's redirect URI** matching for
  the private-use's schemes (the OS's hijacking is possible) — the AS's should use the
  AS's OS's support (the claimed's HTTPS's verification).

### 3.9 The SPA's patterns

| The pattern | The tokens' location | The XSS's risk | The CSRF's risk | The recommendation |
|-------------|----------------------|---------------|---------------|-------------------|
| **The SPA with the secret** | The browser's memory + the `localStorage` | ❌ High (the secret is extractable) | Low | ❌ **Never** |
| **The SPA with the PKCE** | The access in the memory, the refresh in the httpOnly's cookie | Medium (the access is reachable) | Medium (the cookie) | ✅ Acceptable, with a strict's CSP |
| **The BFF** | The server's session (the httpOnly's cookie) | ✅ None (the tokens never enter the JS) | Medium (needs the CSRF's protection) | ✅ **The recommended** |

**The BFF's pattern in detail:**

```text
   the SPA (the browser)                the BFF (your server)         the AS
   ┌───────────────────┐                ┌──────────────────┐         ┌────────┐
   │ fetch('/api/x',   │  credentials:  │                  │         │        │
   │  {credentials:    │───'include')──▶│ the session      │         │        │
   │   'include'})     │                │ (the httpOnly's  │         │        │
   │                   │◀── the JSON ───│  cookie)         │         │        │
   │                   │                │ the tokens are   │         │        │
   │                   │                │ IN THE SERVER    │         │        │
   │  /login (the      │─── redirect ──▶│ /oauth2/authorize│────────▶│        │
   │   redirect)       │◀── redirect ───│                  │◀────────│        │
   └───────────────────┘                └──────────────────┘         └────────┘
```

**The BFF's benefits:**
- The tokens are **never** in the browser's JS → the XSS can't steal them.
- The refresh is handled **server-side** (the single-flight, the rotation, the reuse's
  detection) → no more multi-tab's races.
- The **CSRF** is the only remaining's risk → handled by the `SameSite` cookie + the
  double-submit's token.
- The API's calls are **first-party** (no CORS's complexity).

**The BFF's costs:** the server's session's state (a store or a signed's cookie), an extra
hop, and the mobile's apps can't use it (they need a different's client).

### 3.10 The Spring Boot's implementation

**The dependencies:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-client</artifactId>
</dependency>
```

**The registration:**
```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope: [openid, email, profile]
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            client-name: Google
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_basic
        provider:
          google:
            issuer-uri: accounts.google.com
            # or, explicitly:
            # authorization-uri: https://accounts.google.com/o/oauth2/v2/auth
            # token-uri: https://oauth2.googleapis.com/token
            # user-info-uri: https://openidconnect.googleapis.com/v1/userinfo
            # jwk-set-uri: https://www.googleapis.com/oauth2/v3/certs
            # user-name-attribute: sub
```

**The `issuer-uri` vs the explicit's URIs:** the `issuer-uri` triggers the **discovery**
(the metadata's fetch at startup, and the validation's issuer). The explicit's URIs avoid
the startup's dependency on the AS (useful if the AS is temporarily down, or in a
test's environment).

**The `redirect-uri` template's variables:**
- `{baseUrl}` — the scheme + host + port
- `{baseUrl}/` — with the trailing's slash
- `{baseUrl}/login/oauth2/code/{registrationId}` — the default's pattern
- `{registrationId}` — the registration's name

### 3.11 The "the front channel's logout"

**The problem:** when the user logs out of your app, the AS still has a session. If the user
then clicks "Sign in with Google" again, the AS **silently re-authorizes** — no login
prompt. For many apps, that's fine (the SSO's experience). For the sensitive's apps (the
banking, the healthcare), the user expects a **fresh** authentication.

**The RP-initiated logout's flow:**
```text
  1. your backend clears its session
  2. your backend redirects the browser to the AS's end_session_endpoint:
     GET https://as.example.com/logout
         ?id_token_hint=<the ID token>
         &post_logout_redirect_uri=https://app.example.com/logged-out
         &state=<random>
  3. the AS clears ITS session and redirects back to post_logout_redirect_uri
```

**The OIDC's front-channel logout (the notification):**
```text
  the AS renders an invisible iframe:
  <iframe src="https://as.example.com/logout?iss=...&sid=..."></iframe>
  → tells every client whose session was ended
```

**The Spring Boot's:**
```java
@GetMapping("/logout")
public String logout(HttpServletRequest request, Authentication authentication) {
    // ① the OIDC's end_session_endpoint (if configured)
    String endSessionEndpoint = clientRegistration
            .getProviderDetails().getConfigurationMetadata().get("end_session_endpoint");
    if (endSessionEndpoint != null) {
        // ② the id_token_hint — the AS knows which session to end
        //    (it must be the ID token from the login)
        // ③ the post_logout_redirect_uri — MUST be pre-registered
        return "redirect:" + UriComponentsBuilder.fromUriString(endSessionEndpoint)
                .queryParam("post_logout_redirect_uri", baseUrl + "/logged-out")
                .queryParam("state", state)
                .build().toUriString();
    }
    // ④ the fallback: the local's logout only
    return "redirect:/logged-out";
}
```

**The `post_logout_redirect_uri` must be pre-registered** at the AS — otherwise the AS
ignores it (or, worse, honours an unregistered one → the open redirect).

---

## 4. Visualizations

### 4.1 The complete flow (the sequence)

```mermaid
sequenceDiagram
    autonumber
    participant U as the User (the browser)
    participant C as the Client (the BFF)
    participant A as the Authorization Server
    participant R as the Resource Server

    Note over C: ① generate the verifier, the challenge,<br/>the state, the nonce; store them in the session
    U->>C: GET /login/google
    C-->>U: 302 → as.example.com/authorize?...&state=..&nonce=..&code_challenge=..
    U->>A: GET /authorize (the browser follows)
    Note over A,U: ② authenticate (the password, the MFA)
    Note over A,U: ③ the consent's screen (the scopes)
    A-->>U: 302 → app.example.com/callback?code=XYZ&state=..
    U->>C: GET /callback?code=XYZ&state=..
    C->>C: ④ verify the state (the CSRF's check)
    C->>A: ⑤ POST /token (the code + the verifier + the client auth + the redirect_uri)
    A->>A: ⑥ validate (the single-use, the expiry, the client,<br/>the redirect_uri, the PKCE) → issue the tokens
    A-->>C: 200 {access_token, refresh_token, id_token}
    C->>C: ⑦ verify the ID token (the sig, the iss, the aud, the exp, the nonce)
    C-->>U: 200 + the session's cookie (the httpOnly, the Secure, the SameSite)
    U->>R: GET /api/orders (the access token)
    R->>R: ⑧ verify the JWT (the JWKS) + the scope's check
    R-->>U: 200 {the data}
```

### 4.2 The three's defences (the attack → the defence)

```text
  ┌────────────────────────────┬──────────────────────┬────────────────────────┐
  │ the ATTACK                 │ the DEFENCE          │ the MECHANISM          │
  ├────────────────────────────┼──────────────────────┼────────────────────────┤
  │ the LOGIN's CSRF           │ the `state`          │ the random's value,   │
  │ (the attacker's code is    │                      │ the session's storage, │
  │  injected into your        │                      │ the exact's comparison │
  │  session)                  │                      │                        │
  ├────────────────────────────┼──────────────────────┼────────────────────────┤
  │ the ID token's REPLAY      │ the `nonce`          │ the random's value,   │
  │ (a captured ID token is    │                      │ the AS's echo, the    │
  │  replayed later)           │                      │ the client's verify    │
  ├────────────────────────────┼──────────────────────┼────────────────────────┤
  │ the CODE's INTERCEPTION    │ the PKCE             │ the verifier's secret,│
  │ (a malicious app grabs the │                      │ the challenge's hash, │
  │  redirect on Android)      │                      │ the S256's proof      │
  ├────────────────────────────┼──────────────────────┼────────────────────────┤
  │ the CODE's REPLAY          │ the single-use       │ the atomic's UPDATE,  │
  │ (the same code is sent     │ + the short's expiry │ the 60s's window      │
  │  twice)                    │                      │                        │
  ├────────────────────────────┼──────────────────────┼────────────────────────┤
  │ the OPEN's REDIRECT        │ the state's payload  │ the allowlist of the  │
  │ (the returnUrl points      │ + the validation     │ allowed's return URLs │
  │  to an attacker's site)    │                      │                        │
  └────────────────────────────┴──────────────────────┴────────────────────────┘
```

### 4.3 The SPA's patterns compared

```text
  (A) the SPA + the SECRET          (B) the SPA + the PKCE          (C) the BFF
  ┌──────────────────────┐          ┌──────────────────────┐         ┌──────────────────────┐
  │ the browser          │          │ the browser          │         │ the browser          │
  │ · the secret ⚠       │          │ · the access (memory)│         │ · the cookie only    │
  │   (in the bundle!)   │          │ · the refresh        │         │   (httpOnly)         │
  │ · the access         │          │   (httpOnly cookie)  │         │ · NO tokens in the JS│
  │ · the refresh        │          │ · the verifier       │         │ · the CSRF's token   │
  └──────────┬───────────┘          └──────────┬───────────┘         └──────────┬───────────┘
             │                                 │                            │
  ❌ the secret is extractable         ⚠ the XSS can use              ✅ the XSS gets nothing
     (the attacker mints the tokens)     the access token            ✅ the server controls
     ❌ NEVER                              (the CSP mitigates)          the refresh's rotation
```

### 4.4 The code's lifecycle (the AS's side)

```text
   t=0        t=30s              t=60s              t=61s
   │          │                  │                  │
   ▼          ▼                  ▼                  ▼
   ┌────────┐ ┌────────┐  ┌──────────────┐  ┌────────┐
   │ ISSUED │ │ REDEEMED│  │  EXPIRED    │  │        │
   │ (code) │ │ (used)  │  │  (garbage)  │  │        │
   └────────┘ └────────┘  └──────────────┘  └────────┘
      │           │              │
      │           └─ the redemption is ATOMIC
      │              (the concurrent's exchange → only one wins)
      │
   the redemption returns:
     the tokens + a NEW code (if the AS rotates the code)
     ⚠ the code MUST be invalidated even if a later step fails
```

---

## 5. Worked Examples

### 5.1 The complete flow (the dry run with the real values)

```text
  ① the CLIENT's preparation:
     state          = "kJ8#pQ2mXvL9nRt4"
     nonce          = "n-8f3a2b1c"
     code_verifier  = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
     code_challenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
       (the SHA-256 of the verifier, base64url'd)
     the session stores: { state, nonce, code_verifier }

  ② the /authorize:
     https://as.example.com/authorize
       ?response_type=code
       &client_id=app-123
       &redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback
       &scope=openid%20email%20profile%20orders%3Aread
       &state=kJ8%23pQ2mXvL9nRt4
       &nonce=n-8f3a2b1c
       &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
       &code_challenge_method=S256

  ③ the user's authentication: the password + the OTP → the consent's screen
     "Example App wants to know your email, your profile, and read your orders"
     → the user clicks "Allow"

  ④ the callback:
     https://app.example.com/callback?code=Ospl0XYZ123abc&state=kJ8%23pQ2mXvL9nRt4
     ⚠ no id_token, no access_token in the URL ✅ (the code flow's whole point)

  ⑤ the state's check:
     session.state == "kJ8#pQ2mXvL9nRt4" == request.state  ✅ → proceed
     (and the session's state is deleted → the single-use)

  ⑥ the token's exchange:
     POST https://as.example.com/token
       Content-Type: application/x-www-form-urlencoded
       Authorization: Basic YXBwLTEyMzpiYXNpY1NlY3JldA==   ← base64("app-123:basicSecret")
       grant_type=authorization_code
       &code=Ospl0XYZ123abc
       &redirect_uri=https%3A%2F%2Fapp.example.com%2Fcallback
       &code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
       &client_id=app-123

  ⑦ the AS's validation:
     the code exists?            ✅
     the code expired?           ✅ (30s < 60s)
     the code already used?      ✅
     the code's client == app-123? ✅
     the redirect_uri matches?   ✅
     SHA256(verifier) == challenge? ✅
     the client authenticates?   ✅ (the Basic's auth)

  ⑧ the response:
     200 {
       "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
       "token_type": "Bearer",
       "expires_in": 300,
       "refresh_token": "rt_9f2a7c...",
       "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6...",
       "scope": "openid email profile orders:read"
     }

  ⑨ the ID token's claims (the decoded payload):
     { "iss": "https://as.example.com",
       "sub": "auth0|1234567890",
       "aud": "app-123",                       ← the client
       "exp": 1735689300,
       "iat": 1735689000,
       "nonce": "n-8f3a2b1c",                  ← the echo ✅
       "name": "Alice Sharma",
       "email": "alice@example.com",
       "email_verified": true,
       "at_hash": "77QmUPtjPfzWtF2AnpK9RQ" }   ← the access token's hash
```

### 5.2 The PKCE's verification (the dry run)

```text
  the /authorize sent:
     code_challenge      = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
     code_challenge_method = S256

  the /token sent (the CORRECT one):
     code_verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
     the AS computes:
        SHA256("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")
          = 0x7f 0xd8 0x9b ... (32 bytes)
        BASE64URL(that) = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
     == the stored challenge  ✅ → the exchange proceeds

  the /token sent (the WRONG one — the attacker's guess):
     code_verifier = "attacker-guessed-value"
     SHA256 → BASE64URL = "someOtherValue..."
     != the stored challenge  ❌ → 400 invalid_grant
     → the INTERCEPTED CODE IS USELESS ✅
```

### 5.3 The `state`'s attack (the dry run)

```text
  the VICTIM's browser is at https://app.example.com/login
  the ATTACKER:
     1. starts the login at the AS with HIS account:
        → he gets: https://app.example.com/callback?code=ATTACKER_CODE&state=ATTACKER_STATE
     2. sends the victim a link to that URL (or an auto-submitting's form)
     3. the victim's browser hits the app's callback with:
          code=ATTACKER_CODE
          state=ATTACKER_STATE
     4. the app checks: session.state ("kJ8#pQ2m...") == "ATTACKER_STATE"?
          ❌ NO → the app REJECTS (401/400) and does NOT exchange the code
        ✅ the attack fails

  ⚠ without the state check, the victim's session would be bound to the ATTACKER's
    Google account — the "the login's CSRF"
```

### 5.4 The `nonce`'s attack (the dry run)

```text
  the legitimate's login:
     the client sent nonce = "n-8f3a2b1c"
     the ID token contains: "nonce": "n-8f3a2b1c"  ✅

  the ATTACKER replays a CAPTURED ID token (from a log, a proxy):
     the ID token contains: "nonce": "n-OLD-VALUE"
     the client's current session's nonce: "n-NEW-VALUE"
     the check: "n-OLD-VALUE" == "n-NEW-VALUE"?  ❌ → the login is REJECTED
     ✅ the replay fails

  ⚠ the nonce only protects the ID token's replay; the state protects the code's
    injection. They're different attacks and both are needed.
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The Spring Boot's configuration (the complete)

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            client-name: Google
            scope:
              - openid
              - email
              - profile
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            authorization-grant-type: authorization_code
            client-authentication-method: client_secret_basic
            user-name-attribute: sub
          okta:
            client-id: ${OKTA_CLIENT_ID}
            client-secret: ${OKTA_CLIENT_SECRET}
            scope: [openid, profile, email, orders:read]
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            provider: okta
        provider:
          google:
            issuer-uri: accounts.google.com
          okta:
            issuer-uri: https://your-org.okta.com/oauth2/default
```

### 6.2 The `SecurityConfig` (the OAuth2's login)

```java
package com.example.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final OAuth2LoginSuccessHandler successHandler;
    private final OAuth2LoginFailureHandler failureHandler;
    private final CustomOAuth2UserService userService;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/error", "/login/**", "/css/**", "/js/**").permitAll()
                .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                        .userService(userService))            // the profile's mapping
                .successHandler(successHandler)              // the token's issuance
                .failureHandler(failureHandler))              // the error's handling
            .logout(logout -> logout
                .logoutSuccessHandler((req, res, auth) -> res.sendRedirect("/login"))
                .invalidateHttpSession(true)
                .clearAuthentication(true)
                .deleteCookies("JSESSIONID"))
            .csrf(csrf -> csrf
                // the CSRF is still needed for the local's forms
                .ignoringRequestMatchers("/login/oauth2/code/*"));  // the OAuth2's callback
        return http.build();
    }
}
```

### 6.3 The custom `OAuth2AuthorizationRequestResolver` (the PKCE's S256 + the extras)

```java
package com.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.web.util.RedirectUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CustomAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

    private final ClientRegistrationRepository clientRegistrationRepository;

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        String registrationId = resolveRegistrationId(request);
        if (registrationId == null) {
            return null;
        }
        OAuth2AuthorizationRequest base = new DefaultOAuth2AuthorizationRequestResolver(
                clientRegistrationRepository).resolve(request);
        if (base == null) {
            return null;
        }
        return withCustomParameters(base);
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, HttpServletResponse response) {
        return resolve(request);
    }

    private OAuth2AuthorizationRequest withCustomParameters(OAuth2AuthorizationRequest base) {
        Map<String, Object> params = new HashMap<>(base.getAttributes());

        // ① the PKCE — the S256's challenge (generated SERVER-side, the verifier stays here)
        if (base.getAuthorizationRequestUri() != null
                && base.getAttributes().containsKey("code_verifier")) {
            // the Spring's 2.1+ already generates the verifier + the S256's challenge.
            // If you're on an older version, generate it yourself:
            String verifier = base.getAttributes().get("code_verifier").toString();
            params.put("code_challenge", s256(verifier));
            params.put("code_challenge_method", "S256");
        }

        // ② the resource's indicator (RFC 8707) — the target's API
        params.put("resource", "https://api.orders.example.com/");

        // ③ the OIDC's nonce (the Spring's does it when the scope includes 'openid')
        if (!params.containsKey("nonce")) {
            params.put("nonce", UUID.randomUUID().toString());
        }

        // ④ the PKCE's S256 by default (the Spring's uses the provider's default)
        return OAuth2AuthorizationRequest.authorizationCode()
                .authorizationUri(base.getAuthorizationRequestUri())
                .clientId(base.getClientId())
                .redirectUri(base.getRedirectUri())
                .scopes(base.getScopes())
                .state(base.getState())
                .attributes(params)
                .additionalParameters(params)
                .build();
    }

    private String s256(String verifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String resolveRegistrationId(HttpServletRequest request) {
        String path = request.getServletPath();
        String prefix = "/oauth2/authorization/";
        if (path != null && path.startsWith(prefix)) {
            return path.substring(prefix.length());
        }
        // the Spring's default's pattern
        String pattern = "/login/oauth2/code/";
        if (path != null && path.startsWith(pattern)) {
            return path.substring(pattern.length());
        }
        return null;
    }
}
```

### 6.4 The success's handler (the token's issuance)

```java
package com.example.security.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oauthUser = oauthToken.getPrincipal();

        // ① map the provider's identity to YOUR user
        String provider = oauthToken.getAuthorizedClientRegistrationId();
        String providerUserId = oauthUser.getAttribute("sub");
        String email = oauthUser.getAttribute("email");

        AppUser user = userService.findOrCreateFromProvider(provider, providerUserId, email);

        // ② issue YOUR tokens (for YOUR API) — not the provider's
        String accessToken = jwtService.generateAccessToken(user, UUID.randomUUID().toString());
        String refreshToken = refreshTokenService.issue(user, request);

        // ③ the session's cookie for the SPA (the tokens are NOT in the response's body)
        HttpSession session = request.getSession(true);
        session.setAttribute("ACCESS_TOKEN", accessToken);
        session.setAttribute("REFRESH_TOKEN", refreshToken);
        session.setAttribute("USER_ID", user.getId());
        session.setMaxInactiveInterval(1800);

        // ④ the redirect — NEVER a client-supplied URL (the open redirect's defence)
        String target = safeRedirect(request.getParameter("redirect"));
        response.sendRedirect(target != null ? target : "/dashboard");
    }

    /**
     * The open redirect's defence — the allowlist of the permitted's paths.
     * NEVER redirect to a client-supplied absolute URL.
     */
    private String safeRedirect(String candidate) {
        if (candidate == null || !candidate.startsWith("/") || candidate.startsWith("//")) {
            return null;
        }
        return candidate;
    }
}
```

### 6.5 The failure's handler (the error's handling)

```java
package com.example.security.oauth2;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        String errorCode = "LOGIN_FAILED";
        String errorDescription = "Sign-in failed. Please try again.";

        if (exception instanceof OAuth2AuthenticationException oae) {
            errorCode = oae.getError().getErrorCode();          // e.g. "access_denied"
            String desc = oae.getError().getDescription();
            if (desc != null) errorDescription = desc;
            log.warn("oauth2 login failure: code={} desc={}", errorCode, desc);
        } else {
            log.warn("oauth2 login failure: {}", exception.getMessage());
        }

        // ⚠ do NOT leak the internal's details to the user
        response.sendRedirect("/login?error=" + errorCode);
    }
}
```

### 6.6 The BFF's pattern (the SPA's recommended's architecture)

```java
package com.example.bff;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequiredArgsConstructor
public class BffController {

    private final ClientRegistrationRepository registrations;

    /**
     * The API's calls are proxied through the BFF — the SPA's JS never sees a token.
     * The SPA's fetch uses `credentials: 'include'`.
     */
    @GetMapping("/api/orders")
    @ResponseBody
    public Map<String, Object> orders(@AuthenticationPrincipal OidcUser user) {
        // the session's cookie was validated by the session's filter
        // the access token is in the session (server-side) — the SPA never had it
        return Map.of("user", user.getFullName(), "orders", List.of(...));
    }
}
```

```javascript
// the SPA's fetch — the SAME-ORIGIN, the credentials, no tokens in JS
async function loadOrders() {
  const res = await fetch('/api/orders', { credentials: 'include' });
  if (res.status === 401) {
    // the session's expired → redirect to the login (the BFF handles the refresh)
    window.location = '/oauth2/authorization/okta';
    return;
  }
  return res.json();
}
```

### 6.7 The native's app's configuration (the mobile)

```yaml
# the mobile's registration (the claimed's HTTPS's redirect)
spring.security.oauth2.client.registration.google.redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
# the app's scheme's registration (the private-use's scheme)
spring.security.oauth2.client.registration.native-app.redirect-uri: "com.example.app:/oauth2redirect"
```

```kotlin
// the Android's PKCE (the AppAuth's library does it, but the manual's version:)
private fun generateCodeVerifier(): String {
    val bytes = ByteArray(32)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}

private suspend fun codeChallenge(verifier: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(verifier.toByteArray(StandardCharsets.US_ASCII))
    return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
}
```

### 6.8 The front channel's logout

```java
package com.example.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OidcUser;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OidcLogoutSuccessHandler implements LogoutSuccessHandler {

    private final ClientRegistrationRepository registrations;

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response,
                                Authentication authentication) throws IOException {
        if (authentication == null || !(authentication.getPrincipal() instanceof OidcUser oidcUser)) {
            response.sendRedirect("/");
            return;
        }
        String registrationId = (String) authentication.getDetails();
        ClientRegistration registration = registrations.findByRegistrationId(registrationId);
        if (registration == null) {
            response.sendRedirect("/");
            return;
        }
        Map<String, Object> metadata = registration.getProviderDetails()
                .getConfigurationMetadata();
        String endSessionEndpoint = (String) metadata.get("end_session_endpoint");

        if (endSessionEndpoint == null) {
            response.sendRedirect("/");
            return;
        }

        // the id_token_hint — the AS needs to know which session to end
        String idToken = oidcUser.getIdToken() != null ? oidcUser.getIdToken().getTokenValue() : null;

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(endSessionEndpoint)
                // ⚠ the post_logout_redirect_uri MUST be pre-registered at the AS
                .queryParam("post_logout_redirect_uri", "https://app.example.com/logged-out")
                .queryParam("state", java.util.UUID.randomUUID().toString());
        if (idToken != null) {
            builder.queryParam("id_token_hint", idToken);
        }
        response.sendRedirect(builder.build().encode().toUriString());
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The `state` not verified | Always verify (and delete it) | The login's CSRF |
| 2 | The `nonce` not verified | Always send + verify (OIDC) | The ID token's replay |
| 3 | The `state` generated with `Math.random()` | A CSPRNG (≥128 bits) | The predictability |
| 4 | The `code_challenge_method=plain` | `S256` always | The interceptor can replay the challenge |
| 5 | The PKCE's verifier in the `localStorage` | The session's storage (or the server) | The XSS + the persistence |
| 6 | The `redirect_uri` omitted in the exchange | Always send the exact's one | The binding's check |
| 7 | The `redirect_uri` with a wildcard's path | The exact's registration | The code's interception |
| 8 | The `client_secret` in the SPA | The BFF (or PKCE alone) | The bundle's extraction |
| 9 | The code's redemption not atomic | `UPDATE ... WHERE used = FALSE` | The concurrent's exchange |
| 10 | The code's TTL > 10 minutes | 60-600 seconds | The interception's window |
| 11 | The redirect to a client-supplied `returnUrl` | The allowlist (the relative's paths) | The open redirect |
| 12 | The provider's ID token used as your API's token | Issue YOUR tokens | The audience's confusion |
| 13 | The account's linking without `email_verified` | Require the verification | The account's takeover |
| 14 | The scopes' over-requesting at the login | The incremental's consent | The consent's fatigue |
| 15 | The `state` carrying a sensitive's value | The random's value only | The leakage |
| 16 | The error's details leaked to the user | The generic's message + the log | The reconnaissance |
| 17 | The `post_logout_redirect_uri` unregistered | Pre-register it | The open redirect |
| 18 | The mobile's app embedding a WebView | The external's browser (RFC 8252) | The phishing's risk |
| 19 | The session's fixation after the login | Rotate the session's id | The session's hijack |
| 20 | The `id_token_hint` missing from the logout | Include it | The AS's session's not ended |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is the authorization code flow?**
**A:** The client redirects the user to the authorization server, the user authenticates and
consents, and the server redirects back with a short-lived, single-use authorization code.
The client's backend then exchanges that code (over a back channel) for the access token,
the refresh token, and the ID token. The tokens never pass through the browser.

**Q: Why not just return the access token directly (the implicit flow)?**
**A:** Because the access token would be in the browser's URL — in the history, the
`Referer` header, the analytics, the extensions. The code flow keeps the browser's channel
harmless by only passing a useless-without-the-client code.

**Q: What is PKCE?**
**A:** Proof Key for Code Exchange (RFC 7636). The client sends a SHA-256 hash of a random
verifier in the authorization request and must present the original verifier at the token
exchange. It's mandatory for the public clients (SPAs, mobile) and prevents an intercepted
code from being exchanged.

### 🟡 Intermediate
**Q: Explain the `state` parameter.**
**A:** A random value the client generates, stores in the session, and includes in the
authorization request. The AS echoes it back on the callback, and the client compares it
with the stored one. A mismatch means the callback wasn't initiated by this session — the
login's CSRF (an attacker injecting their own code to bind the victim to the attacker's
account).

**Q: What is the `nonce` for?**
**A:** The OIDC's replay's protection. The client sends a random `nonce` in the
authorization request, the AS echoes it into the ID token, and the client verifies it.
This prevents a captured ID token from being replayed in a later login.

**Q: What does the authorization server check at the token exchange?**
**A:** The code exists, isn't expired, hasn't been used (atomically), belongs to this
client, the `redirect_uri` matches exactly, the PKCE's verifier hashes to the stored
challenge, the client authenticates, and the granted scopes are carried into the token.

### 🔴 Advanced / Senior
**Q: Why does the exchange need the `redirect_uri` if it was already in the authorize
request?**
**A:** To bind the exchange to the same redirect URI the code was issued for. Without it,
a code obtained for one redirect URI could be exchanged by a client registered for a
different one, and the AS couldn't detect the mismatch. It also protects against a variant
of the open-redirect attack where the code is forwarded to a different URI.

**Q: How do you handle the mobile app's redirect?**
**A:** RFC 8252: the private-use scheme (`com.example.app:/cb`), the claimed HTTPS
(`https://app.example.com/cb` with the App Links / Universal Links verification, which is
the recommended one because it prevents the scheme hijacking), or the loopback IP
(`http://127.0.0.1:<port>/cb`, for the desktops). The external browser is required (not a
WebView), and the PKCE is mandatory because the native apps are public clients.

**Q: How do you prevent the SPA from having to handle the refresh token?**
**A:** The BFF pattern. The SPA's JS calls your own backend with `credentials: 'include'`;
the backend holds the tokens in the server's session, does the refresh (with the
single-flight and the rotation), and returns the data. The tokens never enter the browser's
JS, so the XSS can't steal them, and the multi-tab's refresh race disappears. The cost is
the server's session's state and an extra hop.

**Q: What happens if the user denies the consent?**
**A:** The AS redirects back with `error=access_denied` and the `state`. The client handles
it as a normal failure (a message, not a crash) and still validates the state.

### ⚫ Expert / Principal
**Q: Design the login's architecture for a platform with the SPAs, the mobile apps, the
partner's integrations, and the enterprise's SSO.**
> (1) The **the client's taxonomy** and the per-client's registration (the BFF's SPA, the
native's app, the M2M, the partner's code flow, the customer's IdP's federation); (2) the
**the flow's choice** per client and the PKCE's enforcement; (3) the **the account's
linking's model** (the (`issuer`, `sub`) → `user_id` mapping, the verified-email's rule,
the JIT's provisioning); (4) the **the consent's UX** and the incremental's scopes; (5) the
**the session's strategy** (the BFF's session, the mobile's token's storage, the
partner's none); (6) the **the logout's semantics** (the local's, the AS's, the
front-channel's); (7) the **the observability** (the login's method, the failure's
reasons, the consent's grants, the scope's usage); (8) the **the migration's plan** (the
password → OIDC, the account's linking, the legacy's clients' isolation).

---

## 9. Counter-Questions & Traps

### CQ1. "Why does the flow use a code at all? Why not send the tokens directly in the
front channel?"
- **Naive answer:** "For security."  ← the trap
- **The trap:** The interviewer wants the specific's properties of the code.
- **Expert answer:** The code is a **one-time, short-lived, opaque, scoped voucher** that is
safe to expose to the front channel, because exposing it grants nothing by itself. Four
properties make it so: (1) **single-use** — the AS's atomic redemption means a leaked code
can be exchanged at most once, and the legitimate's client wins the race (or the attacker
does, and then the legitimate's exchange fails loudly — a detectable, recoverable situation
that the reuse's detection can even flag). (2) **short-lived** (60-600s) — a leaked code's
window is tiny, unlike an access token's hour. (3) **bound** — to the client, the redirect
URI, the user, the scopes, and (with PKCE) the verifier that only the initiating client
holds. (4) **opaque** — it leaks nothing about the user's session or the scopes. The
front channel is hostile (the history, the `Referer`, the analytics, the extensions,
the shoulder) and the back channel is trusted (TLS + the client's authentication). The
architecture's whole point is to **move the valuable thing (the tokens) from the hostile
channel to the trusted one**, and to leave in the hostile channel only a thing that is
worthless on its own. The modern's refinement (the PKCE) closes the last gap: even if the
code is intercepted, it can't be redeemed.

### CQ2. "If the `state` prevents the CSRF, why do we also need the `nonce`?"
- **Naive answer:** "For the OIDC."  ← the trap
- **The trap:** The interviewer wants the two's attacks precisely separated.
- **Expert answer:** They defend against **different attacks at different points in the
flow**. The `state` defends the **callback** against the **code's injection** (the login's
CSRF): the attacker starts a flow, gets a code bound to his own session, and tricks the
victim into hitting the callback. The victim's session and the returned `state` don't
match → rejected. The `nonce` defends the **ID token** against the **replay**: the
attacker captures a legitimate ID token (from a log, a proxy, the history) and presents it
in a **later** login attempt. In that attempt, the client sent a *fresh* nonce, and the
replayed ID token carries the *old* nonce → the mismatch → rejected. Note the timeline
difference: the `state` is validated at the **start** of the callback (against the current
session), the `nonce` is validated **after** the ID token arrives (against the value sent
in that same flow). And the practical note: the `state` protects the **whole** callback
(including the pure OAuth2's flows with no ID token), while the `nonce` is the OIDC's
mechanism and is **required** in the implicit/hybrid flows (where there's no back channel
to bind the token) and recommended in the code flow. An implementation with only the
`state` is protected against the CSRF but not against the ID token's replay.

### CQ3. "Why is PKCE required even for the confidential clients?"
- **Naive answer:** "It's for the public clients."  ← the trap
- **The trap:** The interviewer wants the defence-in-depth's reasoning.
- **Expert answer:** For the public clients, PKCE is **functional** — it's the only thing
standing between an intercepted code and the attacker's token's exchange, because the
client can't authenticate itself. For the confidential clients, it's **defence in depth**
against a set of attack paths the client secret doesn't cover: (1) the **authorization
code's interception** via a malicious app or a compromised browser extension that reads the
callback — the PKCE's verifier is not available to the interceptor even if the client
secret is safe; (2) the **the redirect's URI's compromise** — if the registered URI is
somehow hijacked (a subdomain's takeover, a stale's registration in a third-party's
service), the code's interception is possible, and the PKCE still blocks it; (3) the
**the AS's internal's compromise** — an AS that logs the codes and the verifiers (in the
clear) can be exploited; the PKCE binds the code to the verifier in a way that a leaked
"code" alone doesn't satisfy; (4) the **the code's leakage in the logs** — a code in the
AS's logs, without the verifier, is useless. This is why the OAuth 2.1's draft and the
current best practice (the OAuth's Security BCP) recommend PKCE for **all** the clients.
The counter-consideration: the extra's complexity is minimal (a SHA-256 and a random
string) and the Spring's generates it for you since 2.1 — so the "it's only for the public
clients" is a legacy's framing, not a design's justification.

### CQ4. "The `redirect_uri` must match exactly. Isn't that too strict? What about the
multiple environments (dev, staging, prod)?"
- **Naive answer:** "Register multiple URIs."  ← the trap
- **The trap:** The interviewer wants the registry's design and the wildcards's danger.
- **Expert answer:** The exactness is a **security control**, and the multi-environment's
problem is solved with **multiple registrations**, not with a loose's matching. The
practical's design: register the **explicit** URIs per environment (the dev's
`http://localhost:3000/callback`, the staging's `https://staging.example.com/callback`,
the prod's `https://example.com/callback`) as **separate client registrations** (or as
multiple URIs on one registration, if the AS supports it). The reasons for the exactness:
(1) the **wildcard's path** (`https://*.example.com/cb`) would let an attacker who
controls **any** subdomain (a dangling's DNS, a forgotten's subdomain, a third-party's
hosting) intercept the code; (2) the **open redirect's** risk if the path can be
manipulated; (3) the **the redirect's integrity** is what binds the code to a specific
app. The RFC 8252's exceptions are narrow and specific: the **loopback** (the native's
apps — the port is dynamic, so the AS allows `http://127.0.0.1:*/cb`) and the
**private-use's scheme** (the native's apps, where the scheme itself is the app's
identity). And for the **SPAs** in a multi-environment's setup, the "the environment's
prefix" pattern (`https://dev.example.com/cb` vs `https://example.com/cb`) keeps the
exactness per environment. The **operational's tip**: the AS's config should be
**version-controlled and deployed** (as code, with a review), not edited by hand in the
admin's console — a wrong's registration in the console is invisible in the code's review
and is a common's cause of the "it works in the dev" bug.

### CQ5. "The user logs out of my app but the AS still has a session. Next time, they're
silently logged in. Is that a problem?"
- **Naive answer:** "That's the point of SSO."  ← the trap
- **The trap:** The interviewer wants the trade-off and the controls.
- **Expert answer:** It's the intended behaviour of the SSO's model, and for most apps it's
a **feature** — the user shouldn't have to re-enter their password for every app in the
ecosystem. But it has real consequences that you should handle deliberately:
(1) **The "the shared's device's" risk** — the user's colleague or a family member can
access the app without a password because the AS's session is alive. Mitigations: the
AS's **session's idle/max timeouts**, the **re-authentication** for the sensitive's actions
(the `prompt=login` or the `max_age`), the **the step-up's authentication** for the
money's operations, and the app's own **session's timeout** (which bounds the exposure
independently of the AS). (2) **The "the logout's expectation"** — the users expect that
"log out" ends their session *in this app*; they don't necessarily expect it to end the
Google session. But if the app claims "sign out" and the user is auto-logged-in on their
next visit, that's a **trust-destroying** bug. The fix: the **RP-initiated logout**
(the `end_session_endpoint` with the `id_token_hint` and the pre-registered
`post_logout_redirect_uri`) — the AS's session is ended too, and the next login is a real
login. Some apps deliberately **don't** do this (the "keep me signed in" behaviour) — but
then the UX should be explicit. (3) **The compromise's blast radius** — a stolen AS's
session means access to **all** the apps in the federation, until the AS's session expires
or the user revokes it. That's the argument for the **the AS's session's hygiene**
(shorter's lifetimes, the risk's assessment, the device's visibility) and for the
**per-app's authorization** (the app's own scopes, the app's own revocation's path).
(4) The **security's nuance**: the "the logout everywhere" at the AS (Google's "your
access to third-party apps") is a separate, user-facing control, and you should link to it
so the user can find it. The senior framing: "**the SSO's session is the AS's decision, and
your app's session is yours — bound them deliberately.** The auto-login is a feature when
the user expects it and a bug when they don't. The controls are the AS's session's
timeouts, the per-app's session's timeout, the step-up's re-authentication for the
sensitive's actions, and a real RP-initiated logout (with the `id_token_hint`) so that
'log out' means what the user thinks it means. And the consent's revocation — the user's
ability to remove your app's access entirely — is the control that must exist and be
discoverable, because it's the only one that actually contains a compromised app."

---

## 10. Rapid-Fire MCQs

**1.** The authorization code is passed:
A) In the POST's body  B) In the browser's URL (the front channel)  C) In a header  D) In the session
**✅ B**

**2.** The tokens are exchanged:
A) In the browser's URL  B) Over the back channel (server-to-server)  C) Via the WebSocket  D) In the cookie
**✅ B**

**3.** The `state` parameter protects against:
A) The replay  B) The CSRF  C) The MITM  D) The token's theft
**✅ B**

**4.** The `nonce` parameter protects against:
A) The CSRF  B) The ID token's replay  C) The XSS  D) The CORS
**✅ B**

**5.** PKCE's `code_challenge_method` should be:
A) `plain`  B) `S256`  C) `none`  D) `MD5`
**✅ B**

**6.** The code's recommended maximum lifetime:
A) 10 seconds  B) 60-600 seconds  C) 1 hour  D) 24 hours
**✅ B**

**7.** The code must be:
A) Reusable  B) Single-use  C) A JWT  D) Encrypted
**✅ B**

**8.** The `redirect_uri` must be sent in:
A) Only the authorize request  B) Only the token's exchange  C) Both  D) Neither
**✅ C**

**9.** The `redirect_uri` matching should be:
A) Prefix  B) Wildcard  C) Exact  D) Regex
**✅ C**

**10.** The PKCE's verifier's length:
A) 16-32 chars  B) 43-128 chars  C) 256+ chars  D) Any
**✅ B**

**11.** The code's redemption should be:
A) A SELECT then an UPDATE  B) An atomic UPDATE ... WHERE used = FALSE  C) In memory  D) Not tracked
**✅ B**

**12.** The native's app's recommended's redirect:
A) `http://example.com/cb`  B) The claimed's HTTPS  C) `javascript:alert()`  D) The custom's scheme (preferred)
**✅ B** (B is the RFC's recommended; D is the older's option)

**13.** The SPA's tokens should live:
A) The `localStorage`  B) The memory (+ the httpOnly's refresh cookie)  C) The URL  D) The `sessionStorage` only
**✅ B**

**14.** The BFF's pattern's main benefit:
A) Better's performance  B) The tokens never enter the browser's JS  C) No CORS  D) Fewer's dependencies
**✅ B**

**15.** The `at_hash` claim in the ID token is:
A) The access token's hash  B) The user's password's hash  C) The code's hash  D) The client's secret's hash
**✅ A**

**16.** The `azp` claim is needed when:
A) The token has multiple audiences  B) The PKCE is used  C) The client is public  D) The scopes are wide
**✅ A**

**17.** The `prompt=login` parameter:
A) Hides the consent  B) Forces the re-authentication  C) Skips the consent  D) Selects the account
**✅ B**

**18.** The `prompt=select_account` parameter:
A) Forces the re-authentication  B) Skips the consent  C) Lets the user pick the account  D) Cancels the flow
**✅ C**

**19.** The `max_age=300` parameter:
A) Limits the session's lifetime to 300s  B) Requires a session younger than 300s  C) Sets the token's TTL  D) The consent's timeout
**✅ B**

**20.** The `post_logout_redirect_uri` must be:
A) Any URL  B) Pre-registered at the AS  C) Relative  D) Encoded
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The full flow with `curl`**
1. Generate the verifier + the challenge (a small script).
2. `curl -v -L -c cookies.txt "<the authorize URL>"` — complete the login (in a real setup).
3. Capture the `code` and the `state` from the redirect.
4. `curl -X POST /token -d grant_type=authorization_code -d code=... -d code_verifier=...`
5. Decode the ID token and verify the `nonce` matches.

**🧪 Lab 2 — The negative's cases**
1. Exchange the code **twice** → the second must fail.
2. Exchange with a **wrong's verifier** → must fail.
3. Exchange with a **wrong's client_id** → must fail.
4. Exchange with a **wrong's redirect_uri** → must fail.
5. Exchange after the **code's expiry** → must fail.

**🧪 Lab 3 — The `state`'s check**
1. Complete the flow but tamper with the `state` in the callback → the client must reject.

**🧪 Lab 4 — The `nonce`'s check**
1. Replay a captured ID token in a new session → must be rejected.

**🧪 Lab 5 — The Spring's PKCE's inspection**
1. Enable the `logging.level.org.springframework.security=DEBUG` and inspect the
   authorization request's parameters (`code_challenge`, `code_challenge_method`).
2. Confirm the `S256`.

---

## 12. Code Challenges

### Challenge 1 — The authorization server's stub (hard)
**Goal:** A minimal AS implementing `/authorize` (the login + the consent), `/token` (the
code's exchange with the PKCE, the single-use, the expiry, the client validation), and
`/jwks.json`.
**Requirements:** the code's store with the atomic's redemption, the PKCE's verification,
the `state`'s echo, the OIDC's `nonce`, the tests (the negative's cases).

**Challenge 2 — The BFF (medium-hard)**
**Goal:** A BFF that handles the OAuth2's login, holds the tokens in the session, proxies
the API's calls, and refreshes transparently.
**Requirements:** the session's store, the refresh's single-flight, the CSRF's protection,
the logout's redirect, the tests.

**Challenge 3 — The multi-provider's federation (hard)**
**Goal:** Support Google + Okta + Keycloak with a unified's user model, the account's
linking, the incremental's consent, and the per-provider's claim's mapping.
**Requirements:** the `identity` table, the linking's rules, the provider's registry, the
tests.

---

## 13. Scenario-Based Questions

**S1.** *"Our SPA stores the access token in the localStorage. The security team flagged
it. The team argues the XSS risk is theoretical. How do you handle this?"**
>
> **Ideal answer:** (1) **The framing:** the localStorage's choice isn't a style's
preference — it's the **maximum** XSS's exposure, and the security's team's concern is
**valid, not theoretical**. The threat model: any script that runs on your page can read
the localStorage. That includes (a) the **third-party's scripts** (the analytics, the chat
widget, the A/B's testing, the ad's network, the tag's manager) — you don't control their
code and they have been a repeated supply-chain's vector; (b) the **the npm's
dependencies** (one compromised transitive dependency with the DOM access = your tokens);
(c) the **the browser's extensions** (some have the DOM access); (d) the **the XSS itself**
(whatever the source — a reflected, a stored, a DOM's bug). With the localStorage, the
stolen's token is **usable for the token's lifetime** (and if it's a refresh token, for
**30 days**, and the refresh's rotation's reuse's detection would then log the legitimate
user out — a self-inflicted DoS as well). (2) **The technical's facts to present:**
- The localStorage is **readable by any script** and is **not** protected by the browser's
security model (no httpOnly, no origin's isolation, it persists across the tabs and the
restarts).
- The **in-memory** storage is not immune (the XSS can read the JS's variables), but the
token's lifetime is bounded to the page's session, and the `localStorage`'s **persistence**
(which extends the exposure across the tabs and the browser's restarts) is eliminated.
- The **httpOnly** cookie is genuinely immune to the XSS (the JS can't read it at all) —
but it's sent automatically, so it needs the **CSRF's** protection.
(3) **The options, in order of preference:** (a) **The BFF (the recommended)** — the tokens
live in the server's session; the browser's JS never sees them; the XSS's theft is
impossible; the refresh's rotation and the reuse's detection are server-side (no
multi-tab's races); the CSRF is handled with the `SameSite=Lax/Strict` cookie + the
double-submit's token. The costs: the server's session's state (Redis or a signed's
cookie), an extra's hop, and the mobile's apps can't use it. (b) **The SPA with the PKCE
+ the in-memory's access + the httpOnly's refresh's cookie** — the access token is
readable by the XSS (so it must be short: 5-15 min) but the refresh token (the valuable
one) is not. The CSRF on the refresh's endpoint is handled by the `SameSite=Strict` + the
Origin's check. (c) **The SPA with the in-memory + the sessionStorage's refresh** — the
`sessionStorage` is per-tab and cleared on the close (better than the localStorage, but
the XSS can still read it while the tab is open). (d) ❌ **The localStorage** — only
acceptable for a **non-sensitive** app with a **very short** token's TTL and a
non-suspicious's threat model. (4) **The migration's plan:** (a) the backend's
implementation of the BFF's endpoints (`/oauth2/authorization/{provider}`, the callback,
the session, the `/api/*`'s proxying, the refresh's endpoint, the logout); (b) the SPA's
refactor (replace the `Authorization` header with the `credentials: 'include'`); (c) the
CORS's configuration (the same-origin's BFF avoids the CORS entirely — a bonus); (d) the
**the CSRF's protection** (the `SameSite` cookie + the double-submit's token for the
state-changing's methods); (e) the run in parallel behind a flag; (f) the **the
headers' hardening** (a strict's CSP to reduce the XSS's surface, the
`X-Content-Type-Options: nosniff`, the trusted's types). (5) **The mitigation's argument
if the team refuses the BFF:** (a) shrink the access token's TTL to 5 minutes; (b) never
put the refresh token in the localStorage (the httpOnly's cookie, or the memory); (c) the
strict's CSP (no the inline's scripts, the nonce-based, the allowlist of the origins) —
which is the **real** XSS's mitigation; (d) the dependency's review and the SRI (the
integrity's hashes) for the third-party's scripts; (e) the token's **audience's
restriction** and the narrow's scopes (the limit of what a stolen token can do); (f) the
**the monitoring** (the anomaly's detection: the token's usage from a new's IP or a new's
user-agent → the revoke). (6) The senior framing: "**the localStorage's question isn't
'how likely is the XSS' — it's 'what's the impact when it happens, and what's the
detection's time'.** A stored's XSS in a third-party's script is not a hypothetical; it's
happening regularly. And the impact with the localStorage is a **persistent, cross-session
credential** that the XSS can read once and use for its lifetime — and if you put the
refresh token there, it's 30 days. The BFF eliminates the class by construction: the tokens
never enter the browser's JS, so the XSS has nothing to read. If the team can't take the
BFF's cost, then the fallback is: the access token in the **memory** with a 5-minute TTL,
the refresh token in the **httpOnly's cookie**, and a **real CSP** — because the CSP, not
the storage's choice, is your actual XSS's defence. And the argument that usually lands:
the BFF also fixes the multi-tab's refresh race and the CSRF's design gets simpler, so
it's not a pure cost."

**S2.** *"A user's login keeps failing with 'invalid_grant' at the token endpoint, but
only for some users and only in production. The code flow works in staging. What do you
check?"**
>
> **Ideal answer:** (1) **The framing:** the failure is at the **token's exchange**, which
means the AS rejected the code. The candidates, in the order of likelihood:
(2) **The clock's skew** — the code's expiry is checked against the AS's clock, and the
AS's nodes may be skewed. If the login's node has a fast clock and the token's node a slow
one (or they're different servers), a code issued at T could appear "expired" at T+1s if
the expiry's window is very short (e.g. 30s) or if the skew is large. **Check**: the
NTP's status on all the AS's nodes, and the *code's TTL* in the configuration. (3) **The
`redirect_uri` mismatch** — the production's registration differs from the app's
configuration (e.g. the prod's `https://app.example.com/callback` vs the app's
`https://app.example.com/login/oauth2/code/google`, a trailing's slash, an `http` vs
`https`, or a different's host behind the proxy). This would break **all** the users, not
some — so it's less likely if it's only some, but check the AS's logs. (4) **The PKCE's
mismatch** — the production's AS is configured with `pkce.code.challenge.method=plain`
(or the client's registration differs), or the app's version is older and doesn't send the
challenge while the AS requires it. Also **per-user-ish** if the AS is configured with
`plain` for some clients. (5) **The concurrent's exchange** — the code is single-use, and
if the app exchanges the code **twice** (a retry's logic, a double-submitted form, the
frontend and the backend both exchanging), the second gets `invalid_grant`. This is a
**classic** and it's user-specific-looking if it depends on the browser or the network
(a flaky connection causing a client's retry). Check the app's logs: is the same code
being submitted twice? Add the idempotency's handling (the AS's "the code's exchange is
idempotent for a short window" — Keycloak has this setting). (6) **The session's
stickiness** — if the app is **load-balanced without the session's stickiness**, the
callback lands on a **different instance** than the one that stored the `state`/`nonce`/
verifier in the session → the state check fails (or the verifier is missing) → `invalid_grant`
or a 401. This is **extremely** common and it hits a **subset** of the users (those whose
callback lands on a different instance) — the classic's signature: a **random** subset,
sometimes only the first login after a deploy. **Check**: the app's session's store
(Redis or a sticky's session) and the instances' configuration. (7) **The proxy's
header's handling** — the `X-Forwarded-Proto`/the `Host` isn't configured → the generated
`redirect_uri` uses `http` or the internal's host → the mismatch. (8) **The `scope`'s
mismatch** — the AS's client's configuration doesn't allow a scope the app requests → the
AS rejects with `invalid_scope` (not `invalid_grant`, but worth checking the raw error).
(9) **The AS's session's timeout** — the AS's own login's session expired between the
authorize and the callback (rare with the code's flow). (10) **The methodology:** (a) get
the **exact's error** — `invalid_grant` is a bucket; the AS's server's log has the
specific's reason (the "the code is invalid", "the code is expired", "the redirect URI
does not match", "the PKCE's verification failed"); in Keycloak, the event's log has the
`error` and the `error_description`; (b) **correlate by the instance** — plot the failures
against the callback's instance (the session's affinity's check); (c) **compare the
environments** — the diff of the client's registration and the AS's realm's config; (d)
**the reproducible's test** — run the flow twice with the same's code (simulating the
double's exchange) to confirm the single-use's behaviour. (11) The senior framing: "**the
random's subset is the signature.** `invalid_grant` for *some* users in one environment
points at the **state's locality**: either the session's stickiness (the callback lands on
a different instance than the one holding the `state`/verifier) or the **double's
exchange** (the code's single-use and the client's retry). Both are invisible in the
staging because there's one instance and no flaky's network. So the first checks are: the
instance's correlation in the app's logs, and the app's logs for the same code appearing
twice. The environment's diff is the systematic's checklist — the redirect's URI, the
PKCE's method, the clock's skew, and the proxy's forwarded headers. And the fix for the
double's exchange is to make the exchange **idempotent** for a short window (the AS's
setting) rather than to retry blindly — because the retry is what creates the second
failure."

---

## 14. Tricky Interview Questions

**T1. "Why does the authorization code flow use two channels? Couldn't the code flow work
with the tokens in the front channel?"**
> The tokens in the front channel is the implicit flow — the access token in the URL's
fragment, which leaks into the history, the `Referer`, the analytics, and the extensions,
and can't be revoked. The two-channel design exists precisely to move the valuable
credential out of the hostile channel. The code is the "the voucher" that the front
channel can safely carry.

**T2. "What happens if the authorization server doesn't support PKCE?"**
> The client's security's posture drops to the redirect's URI's integrity alone. If the
client is public, the intercepted code is directly redeemable. Mitigations: the client
must not be public (it must be confidential, with a secret), the redirect's URI must be
exact and on a domain the client controls exclusively, the code's TTL must be short, and
the code must be single-use. But the right answer is: **require PKCE** — it's in the OAuth
2.1's draft as mandatory, the major ASes (Keycloak, Auth0, Okta, Entra) support it, and
Google's OIDC's endpoints support it (they've required it for the native's apps for
years).

**T3. "Can the code be exchanged more than once?"**
> No — the code is single-use by definition (the RFC says it "MUST NOT be used more than
once"). The AS's redemption must be atomic. Some ASes allow a **short idempotency's
window** (re-returning the same tokens for a repeat's exchange within N seconds) to
tolerate the client's retries — that's a pragmatic's extension of the strict's rule, and it
should be paired with the reuse's **detection** (a reuse after the window → the family
revoked), exactly like the refresh token's rotation.

**T4. "Why does the ID token have an `at_hash` claim?"**
> It's the hash of the **access token** in the token's exchange's response, computed with
the **left-most half** of the hash's output (per the OIDC's spec). It lets the client
verify that the access token it received is the same one the ID token was issued with —
a binding between the two tokens, which is a defence against a **token's substitution**
attack (an attacker swapping the access token in the response). It's optional but
recommended; the Spring's doesn't enforce it.

**T5. "What is the `c_hash` claim?"**
> The same idea for the **authorization code** — the hash of the code, bound into the ID
token. It's used in the hybrid flow (where the code and the ID token come back together)
to verify the code's integrity.

**T6. "Can you skip the consent screen for the already-approved scopes?"**
> Yes — most ASes support it (Keycloak's "the consent's required" toggle and the
incremental's consent). The risk is the **scope's creep**: the app requests more and more
and the user clicks through. The mitigations: the app requests the **minimum** and
**incrementally** (with a fresh's consent for the new's scope at the moment it's needed),
the AS shows only the **newly requested** scopes, and the app explains **why** it needs
each one.

**T7. "What is the difference between the `code` and the `token` in terms of the security's
boundary?"**
> The code is a **front-channel's artifact** — it transits the browser, so it must be
treated as potentially exposed: single-use, short-lived, PKCE-bound, and carrying no
sensitive's information. The token is a **back-channel's artifact** — it transits only
between the AS and your server, over TLS, with the client's authentication. The design's
principle: never let a front-channel's artifact be a bearer credential.

**T8. "Why does the flow work for the 'Sign in with Google' but not for the 'Sign in with
our own password'?"**
> The Google login is a **federation** — the AS redirects to Google (a third-party's AS),
the user authenticates there, and Google returns an assertion to your AS, which then
issues **your** tokens. Your own's password's login is the **same protocol** with your AS
acting as its own identity's provider (the "the local's account" is just another identity
provider in the realm). This is a great point to make in an interview: the OIDC's flow
doesn't change based on where the user authenticates — only the AS's internals change.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the flow) → *"What if the user's session at the AS expires between the
authorize and the callback?"*
→ The code is still valid (it's independent of the AS's session once issued), so the
exchange works. But the AS may require a re-authentication if the policy demands it
(`prompt=login`), and some ASes invalidate the code if the session dies. The general
answer: the code's validity is governed by the code's own TTL, not by the AS's session.

**F2** (after the PKCE) → *"Can you use PKCE with the client credentials grant?"*
→ No — the client credentials grant has **no authorization request** and no code; the
client authenticates with its secret (or its private key). The equivalent's protection
for that flow is the **strong's client authentication** (`private_key_jwt`, mTLS) — the
same "proof of possession" idea, applied to the client itself.

**F3** (after the state) → *"Should the state be stored in a cookie or a session?"*
→ A **server-side session** (or a signed/encrypted cookie) is the stronger choice: the
state's value never leaves your server. A cookie-based state is fine if it's
**signed** (so it can't be forged) and **same-site**; the risk with a plain cookie is that
an attacker can set it (the "the cookie's injection" via a subdomain) and then read the
callback's state. The Spring's stores the authorization request (including the state) in
the `HttpSession` by default.

**F4** (after the consent) → *"How do you handle the user's consent when the app's scopes
change between the versions?"*
→ The incremental's consent: the AS remembers the previous's grant and only prompts for the
**newly requested** scopes. The app's UX should make the new's scope's request explicit
("we need read access to your orders to show your tracking"). And the **step-up's
pattern**: request the sensitive's scope at the moment the user needs it, with a fresh's
consent, rather than up-front — better for the conversion and better for the least's
privilege.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We're building a mobile app (iOS + Android) with 'Sign in with Google'. The iOS
app is being rejected by the app store's review for using a WebView for the login. What's
going on?"**
>
> **Ideal answer:** (1) **The rule:** the **App Store's review guidelines (5.1.1(v))**
> require that apps use the **system's browser's authentication** (Safari's `ASWebAuthenticationSession`
> / Chrome's Custom Tabs) for the third-party's sign-in, not an embedded's WebView. The
reason is **phishing**: an app that renders the login's page in its own WebView can
modify the page (change the domain's display, intercept the credentials, show a fake's
password field) and the user has no browser's chrome to reassure them. It's the same
reason the RFC 8252 requires the **external's browser** for the native's apps. (2) **The
implementation's fix:** (a) **iOS**: `ASWebAuthenticationSession` (iOS 12+) — the
system's browser's sheet, with the `callbackURLScheme` (or the newer
`ASWebAuthenticationSession.Callback` with the HTTPS's callback for the iOS 17.4+). The
session handles the redirect's interception and shares the Safari's cookies (so the
user's existing Google session means no login prompt — a better's UX too). (b) **Android**:
the **Chrome Custom Tabs** (via `androidx.browser`) — the same's principle, and it shares
the browser's cookies. (c) The **AppAuth-iOS / AppAuth-Android** libraries implement the
authorization code + PKCE + the external's browser correctly — using them is the fastest
path and they're well-audited. (d) **The redirect's URI**: for the native's apps, the
**claimed's HTTPS** (`https://app.example.com/callback` with the **Apple's Associated
Domains** / the **Android's App Links**) is the recommended one — it prevents another app
from claiming the custom's scheme. (3) **The deeper's design:** the mobile's app should
use the **authorization code + PKCE**, store the tokens in the **Keychain/Keystore**
(`ThisDeviceOnly` — the non-migratable variant, so the backup doesn't carry the token),
and use the **refresh token's rotation** with the **biometric's gate** (the
`LocalAuthentication` / the `BiometricPrompt` before the refresh's use — the "the
refresh's token's protection" pattern from the OAuth 2.1's draft). (4) **The review's
process:** the rejection's reason should be documented in the review's notes (the ASWebAuthenticationSession's
usage), and if the app needs the WebView for a non-auth's purpose (the in-app's browser
for the help's articles), the app should use `SFSafariViewController` (iOS) / the Custom
Tabs (Android) for **all** the web's content, which is also the Apple's current
recommendation. (5) The senior framing: "**the WebView's rejection isn't a store's
policy quirk — it's the phishing's defence, and the review's guidelines are just the
enforcement of it.** An app that renders the Google's login in its own WebView could
show a fake's password field and steal the credentials; the user has no browser's chrome
to check the domain. The fix is the system's browser (`ASWebAuthenticationSession` on iOS,
the Custom Tabs on Android) — and the practical's bonus is that the user gets the **shared
session's cookies**, so an already-signed-in Google user isn't prompted at all. The
related's trap: the `redirectURLScheme` in the `ASWebAuthenticationSession` means you're
**claiming the scheme globally** — so the better's design is the **claimed's HTTPS** with
the Associated Domains, which no other app can take. And while you're at it, the
mobile's token's storage should be the Keychain with the `ThisDeviceOnly` and the
biometric's gate on the refresh — because the mobile's device is the least's trusted
environment."

**R2. *"We support 'Sign in with Google' and our own email/password. The product wants to
add 'Sign in with Apple' because the app store requires it. How do we add it without
breaking the existing users?"**
>
> **Ideal answer:** (1) **The context:** "the Sign in with Apple" is required by the
App Store's guidelines for apps that offer **third-party's social logins** (the guideline
4.8). It's implemented as an **OIDC's provider** (Apple is a standard's OIDC's provider —
the `https://appleid.apple.com/auth/authorize` endpoint, the ID token's issuer
`https://appleid.apple.com`, the JWKS, the discovery's document). So the **integration's
work is the same as Google's** — a new registration + the provider's config. (2) **The
technical's differences (the ones that bite):** (a) **The `email` claim is only provided
on the FIRST authorization** — after that, Apple returns only the `sub` (and the user's
email is **hidden**, unless you use the `email` scope and the user's consent, and even
then only on the first's authorization). **This is the critical's detail**: you cannot
rely on the email being returned every time. So your identity's model must be keyed on the
**(`issuer`, `sub`)** pair, and the email must be stored **at the first's link** — not
looked up on every login. (b) **The user's can hide the email** — a "the private relay"
(`{uuid}@privaterelay.appleid.com`) is provided for users who choose it; if you request
the real's email, the user must share it explicitly (a separate's scope + consent). Your
app must handle the relay's address as a valid, opaque's identifier — and **not** try to
email it. (c) **The `nonce` is REQUIRED** for the native's apps (the same reason as the
PKCE) and the `state` too. (d) **The `name` is provided only on the FIRST authorization**
(the same as the email) — so if you show the name, capture it then. (e) **The full's name
relay**: you can ask Apple to relay the user's name and email directly to your server
(the `/auth/authorize` with the `name` and the `email` scopes, and Apple posts the
encrypted's form to your `form_post` endpoint — the `response_mode=form_post`). (3) **The
account's linking's strategy (the hard part):** the platform already has users with email
+ password. Now a user signs in with Apple. The linking's decision tree:
- **The email matches an existing account AND Apple says it's verified** → link
automatically (the same rule as the social's login). ⚠ But if the email is the private
relay, there's no match → you need the **the "the account's linking" flow** instead.
- **The email is a private relay / hidden / doesn't match** → present a **"the account's
linking"** screen: "you're signing in with Apple as {relay}; is this the same as your
{existing email} account?" — and to prove it, require the user to **re-authenticate** on
the existing's account (the password's re-entry, or a **magic link** to the existing's
email). The proof-of-ownership's requirement is essential — otherwise anyone could claim
any account.
- **The new's user** → create the account (with the email, if provided).
The **identity's table** (the `identity` table keyed by (`issuer`, `sub`)) makes this
clean and future-proof: the provider's identity is the primary's key, and the email is
just an attribute. (4) **The migration's safety:** (a) **the existing's users are
unaffected** — their email + password's login keeps working; (b) the **the new's
identity's rows are additive** — no data's migration is needed, just the linking's
records; (c) the **the account's page** should show the linked's providers so the user can
see and manage them; (d) the **the un-link** flow (the user removes a provider's link —
with a warning if it's their only's login's method). (5) **The UX's detail:** the
"the sign-in's methods" screen should show the Apple button **only on the iOS** (and not
on the Android, where it isn't required and adds a confusing's step) — and the
"the private relay" explanation should be present, because a user who signs in with a
relay's address and then can't receive emails will file a ticket. (6) The senior framing:
"**Apple's OIDC is standard, but its data's model has two quirks that will bite you if you
don't design for them: the `email` and the `name` come only on the FIRST authorization,
and the user can hide the email behind a private's relay.** That means your identity's
model must be keyed on the (`issuer`, `sub`) pair — not the email — and the email's
capture has to happen at the linking's moment. For the existing's users, the linking's
flow is the interesting's part: when Apple gives you a relay's address that doesn't match
any account, you can't auto-link (that would be an account's takeover), so you need an
explicit's linking with a **proof of ownership** on the existing's side — a password's
re-entry or a magic's link. And the product's requirement of 'Sign in with Apple on iOS
only' should be honoured in the UI, because Apple on Android is a confusing's step for the
user and it isn't required. The whole's thing is additive — no migration's of the
existing's accounts, just the identity's rows."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| `invalid_grant` for some users | The session's stickiness (the callback on a different instance) | The shared's session's store or the sticky's sessions |
| `invalid_grant` (all users) | The `redirect_uri` mismatch | The exact's registration |
| `invalid_grant` (intermittent) | The code's double's exchange | The idempotency's window; the client's retry's discipline |
| `invalid_grant` (staging only) | The AS's clock's skew | The NTP; the code's TTL |
| `invalid_client` | The wrong's secret, or the wrong's auth's method | The registration; the clock's skew (the secret's JWT) |
| The login's loop | The session's cookie's domain/path; the HTTPS's mismatch | The cookie's configuration; the `Secure` flag |
| The consent's screen every time | The consent's required is on; the scopes are changing | The incremental's consent's configuration |
| The CORS's error on the token's exchange | The token's endpoint's CORS | Do the exchange on the backend (the BFF) |
| The "the state is invalid" | The session's loss (the instance) | The shared's session's store |
| The mobile's "the redirect never arrives" | The scheme's hijack; the WebView | The claimed's HTTPS; the external's browser |

---

## 18. Debugging Scenarios

**D1. "Inspect the authorization request's parameters:"**
```bash
# the browser's dev tools → the Network tab → the /authorize request
# or enable the Spring's debug:
logging.level.org.springframework.security.oauth2.client.web=DEBUG
```

**D2. "Decode the ID token:"**
```bash
echo "$ID_TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,sub,exp,nonce,email,email_verified}'
```

**D3. "Verify the ID token's signature manually (openssl):"**
```bash
# the header.payload, signed with the JWKS's public key
printf '%s' "$HEADER.$PAYLOAD" | openssl dgst -sha256 -verify pubkey.pem -signature sig.bin
```

**D4. "Check the AS's logs (Keycloak):"**
```
  the admin's console → Events → the login's events
  the server's log:  org.keycloak.events → the error and the error_description
```

**D5. "Check the code's TTL and the PKCE's config (Keycloak):"**
```bash
# the realm's settings
kcadm.sh get realms/prod | jq '{accessTokenLifespan, ssoSessionIdleTimeout}'
# the client's attributes
kcadm.sh get clients/<id> | jq '.attributes'
```

**D6. "Find the app's double's exchange:"**
```bash
grep -n "token_uri\|/token" /var/log/app/oauth2-exchange.log | \
  awk '{print $1, $4}' | sort | uniq -c | sort -rn | head
# the same's code appearing twice → the double's exchange
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the login's flow for a platform with the SPAs, the mobile's apps, the
partner's integrations, and the enterprise's SSO.**
> (1) The **the client's taxonomy** (the BFF's SPA, the native's app with the claimed's
HTTPS, the partner's code flow, the customer's federation) and the per-client's
registration; (2) the **the flow per client** (the code + PKCE everywhere, the PKCE's
enforcement); (3) the **the account's linking's model** (the (`issuer`, `sub`) → `user_id`,
the verified-email's rule, the proof-of-ownership's flow, the relay's address's handling);
(4) the **the session's strategy** (the BFF's server's session, the mobile's Keychain, the
biometric's gate); (5) the **the consent's UX and the incremental's scopes**; (6) the
**the logout's semantics** (the local's, the AS's, the front-channel's, the consent's
revocation's discoverability); (7) the **the observability** (the login's method, the
failure's reasons, the grant's history); (8) the **the migration's plan**.

**Q2. 🏗️ Design the account's linking for a platform with the password's login + 3
social's providers.**
> (1) The **the data's model** (the `user` + the `identity` table, the `email_at_link_time`,
the `verified` flag); (2) the **the linking's rules** (the auto-link only with a verified
email + a trusted's domain; the explicit's linking with a proof of ownership otherwise;
the never's link on the unverified's email); (3) the **the un-link's flow** (the
"it wasn't me" → the revoke + the un-link + the audit); (4) the **the edge's cases** (the
email's change at the provider, the provider's account's deletion, the multiple's providers
for one account, the account's split); (5) the **the abuse's prevention** (the rate's
limiting on the linking's attempts, the notification to the existing's email on the link,
the audit's log); (6) the **the GDPR's erasure** (the identity's rows' deletion, the
audit's retention).

**Q3. 🏗️ Design the enterprise's SSO's onboarding for 50 B2B customers.**
> (1) The **the tenancy's model** (the realm per customer vs the shared with the tenant's
claim); (2) the **the protocol's choice** (the OIDC vs the SAML's adapter); (3) the
**the claims's mapping** (the groups → the roles, the JIT's provisioning, the
domain's validation); (4) the **the configuration's automation** (the realm's config as
code — the Terraform's provider for Keycloak, not the console's clicks); (5) the
**the testing's strategy** (a test's IdP, the synthetic's users, the per-customer's
smoke's test); (6) the **the certificate's expiry's monitoring** (the classic's outage!);
(7) the **the break-glass's access**.

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the FRONT channel (the browser): /authorize → the callback with the CODE    │
│  the BACK channel (the server):   POST /token → the TOKENS                  │
│  the code: single-use · 60-600s · opaque · bound to (client, redirect_uri,   │
│           user, scopes, PKCE) → exposing it grants NOTHING                   │
├──────────────────────────────────────────────────────────────────────────────┤
│  the MANDATORY's CHECKS:  the state (CSRF) · the nonce (replay)              │
│                          the redirect_uri (exact, in BOTH requests)          │
│                          the code (single-use, atomic) · the PKCE (S256)     │
│  the ID token's CHECKS:  the signature · the iss · the aud · the exp         │
│                          the nonce · the azp (if multi-audience)             │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠ The PKCE: S256 always; the verifier is the SECRET (≥43 chars, a CSPRNG)   │
│  ⚠ The code's redemption MUST be atomic (UPDATE ... WHERE used = FALSE)     │
│  ⚠ The redirect's URI: exact's match; multiple's registrations, no's path  │
│  ⚠ The BFF is the recommended's SPA's pattern (the tokens never in the JS)   │
│  ⚠ The mobile: the claimed's HTTPS + the external's browser (never a WebView)│
│  ⚠ The logout: the RP-initiated (the id_token_hint + a registered's URI)    │
│  ⚠ The scopes: the grant may be NARROWER than the request — handle it       │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [03 · Client Credentials, Refresh Tokens & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md)
- → [04 · OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md)
- → [01 · OAuth2 Architecture & Core Concepts](./01-OAuth2-Architecture-and-Fundamentals.md)

---
**Next Chapter:** → [03 · Client Credentials, Refresh Tokens & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md)
**Previous Chapter:** ← [01 · OAuth2 Architecture & Core Concepts](./01-OAuth2-Architecture-and-Fundamentals.md)
