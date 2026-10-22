# 07 — OAuth2 & OpenID Connect

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★☆ (the standard "how do you integrate with a third
> party?" question — and the one that separates the people who *use* OAuth2 from the
> people who *understand* it)
> **Prerequisite:** [06 · JWT Authentication](../06-JWT-Authentication/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

OAuth2 is **not** "login with Google". OAuth2 is a **delegation framework**, and OIDC is a
**layer on top of it** that answers a *different* question. Most tutorials blur the two; the
interview will not.

What this topic teaches that a tutorial does not:

- The **four grants**, and when each one is the *wrong* choice (and why OAuth 2.1 deleted two
  of them)
- The **`state` / `nonce` / PKCE** triad — what each one defends against, and why "PKCE
  replaced `state`" is wrong
- The **mix-up attack** and the RFC 9207 `iss` parameter
- **Token exchange** (RFC 8693) and the "broad token → narrow token" downgrade pattern
- The **enterprise SSO** world: SAML vs OIDC, SCIM, claim mapping, multi-tenant resolution,
  and the deactivation gap

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [OAuth2 Architecture & Fundamentals](./01-OAuth2-Architecture-and-Fundamentals.md) | Roles, grants, endpoints, tokens, scopes | ⭐⭐ |
| 02 | [Authorization Code Flow & PKCE](./02-Authorization-Code-Flow-and-PKCE.md) | The code flow, PKCE, `state`, `nonce`, the hybrid & implicit flows | ⭐⭐⭐ |
| 03 | [Client Credentials & Token Lifecycle](./03-Client-Credentials-and-Token-Lifecycle.md) | The M2M flow, refresh rotation, token exchange, lifecycle states | ⭐⭐⭐ |
| 04 | [OpenID Connect, Social Login & Keycloak](./04-OpenID-Connect-and-Social-Login.md) | The ID token, UserInfo, discovery, "login with Google", the Keycloak model | ⭐⭐⭐ |
| 05 | [Security Considerations & Enterprise SSO](./05-Security-Considerations-and-Enterprise-SSO.md) | Attack surface, SAML attacks, the compliance checklist | ⭐⭐⭐⭐⭐ |
| 06 | [Enterprise SSO Patterns](./06-Enterprise-SSO-Patterns.md) | Federation topologies, multi-tenancy, SCIM, claim mapping | ⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
  OAuth2 = AUTHORIZATION            OIDC = AUTHENTICATION
  "may this client DO this?"        "who IS this user?"

  ┌──────────────┐        ┌──────────────┐        ┌──────────────┐
  │  the USER    │───────▶│  the CLIENT  │───────▶│ the RESOURCE │
  │ (the person) │consent │  (your app)  │ token  │  (the API)   │
  └──────────────┘        └──────────────┘        └──────────────┘
       the IDENTITY            the RELAY             the CAPABILITY
                            (it holds the           (it holds the
                             token)                  access token)
```

**And the distinction that makes it work:**

```text
  the ID TOKEN     → goes to your CLIENT's server    → "the user is alice"
  the ACCESS token → goes to the RESOURCE's server   → "you may read orders"
```

> Swap those two and you have the classic vulnerability.

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|----------|---------|-------------|-----------|
| **The flow** | Authorization code + PKCE | Implicit | Token exposure in the URL |
| **Client authentication** | `client_secret_basic` | `private_key_jwt` / mTLS | Secret rotation vs setup cost |
| **M2M identity** | Client credentials | Workload identity (SPIFFE/mTLS) | The AS dependency vs the secret problem |
| **Refresh rotation** | On, with reuse detection | Off | Theft detection vs spurious logouts |
| **ID token use** | Server-side validation | Trusting the browser | ID token injection |
| **Scope naming** | `resource:action` (e.g. `orders:read`) | Flat (`read_orders`) | Prefix consistency vs length |
| **Browser storage** | BFF + httpOnly cookie | Local storage | XSS surface vs simplicity |
| **Enterprise protocol** | Both (OIDC preferred) | SAML only | Enterprise reach vs complexity |
| **Tenant resolution** | Host / the `iss` | A URL parameter | Phishing protection |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | The wrong answer | The right answer |
|------|------------------|------------------|
| "OAuth2 is authentication" | "Yes, it logs you in" | "OAuth2 is **authorization**; **OIDC** adds authentication (the ID token)" |
| "The ID token goes to the browser" | "Yes, it's a JWT" | "It goes to **your server**; the browser gets the **access token**" |
| "PKCE replaced `state`" | "Yes, it's enough" | "**No** — `state` binds the session, PKCE binds the code, `nonce` binds the ID token" |
| "Client credentials has a refresh token" | "Yes, like the others" | "**No** — the client has a credential; refresh exists for user-facing clients" |
| "Store the refresh token in localStorage" | "It's fine, it's encrypted" | "**No** — XSS reads it; use an **httpOnly cookie** or a **BFF**" |
| "The client secret is enough for machine calls" | "Yes, that's standard" | "Then you've built a **fancier API key**; use `private_key_jwt` or **workload identity**" |
| "The email identifies the user" | "Yes, it's unique" | "The **(issuer, sub)** pair; the email is an attribute, and `email_verified` matters" |
| "`state` is checked against the session" | "Yes" | "It must be **unpredictable** (CSPRNG), **session-bound**, and **single-use**" |
| "`alg` comes from the token header" | "Yes, we use it" | "**Pin the algorithm** — otherwise `alg: none` and algorithm confusion" |
| "We use SAML because the customer does" | "Yes, it's required" | "Support **both**; the SAML path is the customer's constraint, not your architecture" |
| "A user deactivated in Okta lost access" | "Yes, SCIM disabled them" | "**Existing sessions survive** — you must revoke them explicitly" |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where it's covered |
|---|----------|--------------------|
| 1 | What is OAuth2 and what problem does it solve? | [Ch 01](./01-OAuth2-Architecture-and-Fundamentals.md) |
| 2 | What are the four roles? | [Ch 01](./01-OAuth2-Architecture-and-Fundamentals.md) |
| 3 | What are the grants? | [Ch 01](./01-OAuth2-Architecture-and-Fundamentals.md) |
| 4 | What is the difference between the authorization code and implicit flows? | [Ch 02](./02-Authorization-Code-Flow-and-PKCE.md) |
| 5 | What is PKCE and why is it mandatory for public clients? | [Ch 02](./02-Authorization-Code-Flow-and-PKCE.md) |
| 6 | What is `state` for? | [Ch 02](./02-Authorization-Code-Flow-and-PKCE.md) |
| 7 | What is `nonce` for? | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| 8 | What's the difference between an access token and an ID token? | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| 9 | How do you implement "login with Google"? | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| 10 | What is OIDC Discovery? | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| 11 | How do you validate an ID token? | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| 12 | What's the client credentials grant for? | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| 13 | How do your microservices talk to each other? | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| 14 | How do you implement refresh token rotation? | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| 15 | What is reuse detection and why does it matter? | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| 16 | What is token exchange? | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| 17 | What are the main OAuth2 attacks? | [Ch 05](./05-Security-Considerations-and-Enterprise-SSO.md) |
| 18 | SAML vs OIDC? | [Ch 05](./05-Security-Considerations-and-Enterprise-SSO.md) |
| 19 | How do you implement enterprise SSO for 100 tenants? | [Ch 06](./06-Enterprise-SSO-Patterns.md) |
| 20 | What is SCIM and why do you need it? | [Ch 06](./06-Enterprise-SSO-Patterns.md) |

---

## 🛠️ The Code You'll Actually Write

| Class | What it does | Where |
|-------|--------------|-------|
| `ClientRegistration` | Provider configuration (endpoints, scopes, client authentication) | [Ch 02](./02-Authorization-Code-Flow-and-PKCE.md), [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| `OAuth2AuthorizedClientManager` | Token caching + automatic refresh (the M2M hot path) | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| `OidcUserService` | ID token → your local user mapping + account linking | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| `OAuth2SuccessHandler` | Post-login session creation (issue **your own** token) | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| `IdTokenValidator` | Manual validation (signature, `iss`, `aud`, `nonce`, `at_hash`) | [Ch 04](./04-OpenID-Connect-and-Social-Login.md) |
| `RotatingRefreshTokenService` | Atomic rotation + reuse detection + grace window | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| `TokenExchangeClient` | Broad → narrow token downgrade | [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| `JwtAuthenticationConverter` | Claims → authorities (scopes + Keycloak roles) | [Ch 04](./04-OpenID-Connect-and-Social-Login.md), [Ch 03](./03-Client-Credentials-and-Token-Lifecycle.md) |
| `SecureAuthorizationRequestRepository` | `state` generation, binding, single use | [Ch 05](./05-Security-Considerations-and-Enterprise-SSO.md) |
| `ClaimMappingEngine` | Raw IdP claims → canonical fields + role mapping | [Ch 06](./06-Enterprise-SSO-Patterns.md) |
| `ScimUserController` | SCIM lifecycle (create, patch, search) | [Ch 06](./06-Enterprise-SSO-Patterns.md) |
| `StepUpPolicy` | `amr`/`acr` → the action's assurance policy | [Ch 06](./06-Enterprise-SSO-Patterns.md) |

---

## 🔗 The Spring Boot 3 Setup (the minimum)

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
```

```java
@Bean
SecurityFilterChain chain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(a -> a
            .requestMatchers("/oauth2/**", "/login/**").permitAll()
            .anyRequest().authenticated())
        .oauth2Login(Customizer.withDefaults());   // redirect, callback, validation
    return http.build();
}
```

---

## 📐 The Reference Architecture

```text
   the BROWSER                    your APP (the RP)              the PROVIDER (the OP)
   ┌──────────────┐              ┌──────────────────┐           ┌──────────────────┐
   │ "Sign in     │──authorize──▶│  state,          │──auth───▶ │  the user        │
   │  with X"     │              │  nonce,          │           │  authenticates   │
   │              │◀─callback─── │  PKCE            │◀─token─── │  the consent     │
   └──────────────┘              │  ID token        │           └──────────────────┘
                                 │  validation      │
                                 │  → YOUR session  │
                                 └──────────────────┘
```

---

## ✅ The Self-Check

- [ ] I can explain the **four roles** and name who plays which in a given flow.
- [ ] I can name the **four grants** and say which ones OAuth 2.1 deleted.
- [ ] I can explain the **`state` / `nonce` / PKCE** triad and what each defends.
- [ ] I can describe the **five mandatory ID token validations**.
- [ ] I know why the **access token and the ID token go to different places**.
- [ ] I can explain the **client credentials** flow and why it has no refresh token.
- [ ] I can describe **refresh token rotation** and **reuse detection**.
- [ ] I can explain **token exchange** (RFC 8693) and the resource indicator (RFC 8707).
- [ ] I know the **four main OAuth2 attacks** and their defences.
- [ ] I can explain **SAML vs OIDC** and the **XML signature wrapping** attack.
- [ ] I know what **SCIM** is and where the **deactivation gap** comes from.
- [ ] I can describe **multi-tenant resolution** and why a URL parameter is unsafe.
- [ ] I can explain the **BFF pattern** and the cookie flags.

---

## 🧪 The Coding Drill

- [ ] Build **"login with Google"** in Spring Boot and decode the ID token at `jwt.io`.
- [ ] Write the **negative test suite**: wrong `state`, wrong `iss`, wrong `aud`, `alg: none`,
  expired token, replayed `nonce`.
- [ ] Implement the **M2M client** with `OAuth2AuthorizedClientManager` and prove the `/token`
  call count is 1 per TTL, not 1 per API call.
- [ ] Implement **refresh rotation** with a grace window and prove a duplicate request returns
  the same successor.
- [ ] Implement **token exchange** and prove the narrow token fails on the other API.
- [ ] Stand up a **local Keycloak** and add it as an identity provider to another one; observe
  the **duplicate user**; implement **account linking**.
- [ ] Implement a **SCIM endpoint** and run the **deactivation**; prove the sessions are revoked.

---

**Related folders:** ← [06 · JWT Authentication](../06-JWT-Authentication/README.md) · → [08 · Microservices](../08-Microservices-with-Spring-Boot/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
