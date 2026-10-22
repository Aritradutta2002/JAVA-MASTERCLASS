# 07 — OAuth2 & OpenID Connect · Interview Notes Index

> OAuth 2.0 is **not** an authentication protocol — it is a **delegation framework** that lets a
> third-party app access a user's data **without ever seeing the user's password**. The single idea
> that makes the whole topic click: **OAuth answers "what may this client do on the user's behalf?",
> while OpenID Connect answers "who is the user?"** Every design decision in this folder — PKCE, the
> authorization code, the ID token, the resource server's local JWKS validation — exists to keep
> those two questions separate.

| Priority | Chapters | Level | Read Time | Why It Matters |
|----------|----------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · OAuth2 Architecture, Roles & Grant Types](01-OAuth2-Architecture-Roles-and-Grant-Types.md) | 🟢→⚫ | 55 min | The 4 roles (resource owner / client / authorization server / resource server), the 3 token types, the 7 grant types and their exact wire steps, OAuth 2.1, the 3 Spring Boot starters and how to pick one, the discovery endpoints, `ClientRegistration` → `OAuth2AuthorizedClient` → resource server |
| ⭐⭐⭐⭐⭐ | [02 · Authorization Code & PKCE Flows](02-Authorization-Code-and-PKCE-Flows.md) | 🟡→🔴 | 50 min | Every redirect and every POST of the Authorization Code flow, `state` / `nonce` / `code`, PKCE (`code_verifier` → `code_challenge`) and the 4 attacks it kills, public vs confidential clients, `OAuth2AuthorizationRequestRedirectFilter` → `OAuth2LoginAuthenticationFilter`, the `AuthorizationRequestRepository` |
| ⭐⭐⭐⭐ | [03 · Client Credentials, Refresh Tokens & OpenID Connect](03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) | 🟡→🔴 | 50 min | Machine-to-machine with **no user**, refresh-token rotation, OIDC's `openid` scope / ID token / UserInfo / discovery document / logout, `issuer-uri` discovery at startup, the `aud` validation gotcha, introspection vs local validation |
| ⭐⭐⭐⭐ | [04 · Social Login, Keycloak, Enterprise SSO & Security](04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md) | 🟡→🔴 | 50 min | "Login with Google" end to end, the GitHub private-email gotcha, Keycloak realms/clients/roles and `realm_access`, SAML vs OIDC, JIT provisioning, account-linking rules, scope minimisation, the corporate-domain allowlist |

> **Total:** ~4 chapters · 205 min of reading · 70+ interview questions · 36 counter-questions · 48 MCQs · 16 production debugging scenarios

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| What is the difference between OAuth 2.0 and OpenID Connect? | Saying "OIDC adds an ID token" and stopping — OIDC is a whole **identity layer**: the `openid` scope, the ID token, the **UserInfo endpoint**, the **discovery document**, and **RP-initiated logout** |
| Is OAuth an authentication protocol? | Answering "yes" — OAuth solves **authorisation** for a third-party client; it does **not** prove to the client who the user is. Only OIDC (or SAML) does that |
| Name the four OAuth roles. | Listing "user, app, server, API" without the **canonical RFC 6749 names** — *resource owner*, *client*, *authorization server*, *resource server* — and without saying that the authorization server and the resource server are often **the same deployment** |
| Which grant type should a React SPA use? | "Authorization Code" — correct but incomplete; the answer is **Authorization Code + PKCE**, and the reason is that a SPA is a **public client** that cannot keep a secret |
| Why was the Implicit grant removed in OAuth 2.1? | "It was insecure" — the specific failure is that the **access token was returned in the URL fragment**, where it lands in browser history, `Referer` headers and any JavaScript on the page |
| Why is PKCE mandatory now? | "It adds security" — it closes the **authorization code interception attack**: an attacker who steals the `code` cannot redeem it without the `code_verifier` |
| What is the difference between `state` and `nonce`? | Confusing them — `state` binds the **authorization request to the user's browser session** (CSRF / session-fixation defence); `nonce` binds the **ID token to that session** (ID-token replay defence) |
| What does a resource server do with an access token? | "Calls the authorization server to validate it" — the production answer is **local validation against the JWKS**, with a cached key set; introspection is the *alternative*, not the default |
| Client Credentials — who is the subject of the token? | Saying "the user" — there is **no user**; the token's `sub` is the **application**, and the scope is the application's own permissions |
| Does `JwtValidators.createDefaultWithIssuer(...)` check the audience? | Saying "yes" — it validates `iss` + timestamps **only**; `aud` must be added explicitly with a `DelegatingOAuth2TokenValidator`, and skipping it lets a token minted for **service A** authenticate against **service B** |
| Does the Client Credentials grant return a refresh token? | "Yes, like any other grant" — **no**. There is no end user and no long-lived session to maintain, so the client simply requests a new access token when the old one expires |
| How do you log a user out of an OIDC provider? | "Call `/logout`" — the real mechanism is **RP-initiated logout**: `end_session_endpoint` with `id_token_hint` and a registered `post_logout_redirect_uri` |
| Social login — where do you store the provider's access token? | "In the session" — for most social-login use cases you do **not** need it, and storing it creates a liability you cannot revoke |

---

## 🧠 The OAuth2 World, in One Picture

```text
                        ┌───────────────────────────────────────────────┐
                        │            THE RESOURCE OWNER                 │
                        │            (the end user, in a browser)        │
                        │                                               │
                        │   "I want Acme to read my Google Drive files." │
                        └──────────────────────┬────────────────────────┘
                                               │  ① (authenticate + consent)
                                               ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                      THE AUTHORIZATION SERVER  (holds the identity)          │
   │                                                                              │
   │   /authorize  ──▶ login ──▶ consent screen ──▶ issues an authorization CODE  │
   │   /token      ──▶ exchanges CODE (+ PKCE verifier) ──▶ ACCESS TOKEN          │
   │   /userinfo   ──▶ returns the OIDC claims (email, name, sub)                 │
   │   /jwks.json  ──▶ publishes the public keys so verifiers can check signatures │
   │   /revoke · /introspect · /.well-known/openid-configuration                  │
   └───────────┬──────────────────────────────────────────────┬───────────────────┘
               │  ② ACCESS TOKEN (5–15 min)                   │  ③ ID TOKEN (OIDC only)
               │     Authorization: Bearer <jwt>              │     "who the user is"
               │     → verified LOCALLY against the JWKS      │     → sent to the CLIENT, never to the API
               ▼                                              ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                       THE CLIENT  (your web app / SPA / mobile app)         │
   │                                                                              │
   │   holds the tokens  ·  calls the resource server on the user's behalf        │
   └───────────┬──────────────────────────────────────────────────────────────────┘
               │  ④ Authorization: Bearer <access token>
               ▼
   ┌──────────────────────────────────────────────────────────────────────────────┐
   │                    THE RESOURCE SERVER  (the API holding the data)            │
   │                                                                              │
   │   BearerTokenAuthenticationFilter → JwtDecoder (JWKS) → JWT → authorities      │
   │   → SecurityContextHolder → AuthorizationFilter                              │
   └──────────────────────────────────────────────────────────────────────────────┘

   ★ THE ONE-LINE VERSION FOR THE INTERVIEW
   ──────────────────────────────────────────
   "OAuth lets a third-party app act on a user's behalf without ever holding the
    user's password. OpenID Connect adds the missing identity piece — an ID token
    and a UserInfo endpoint — so the client can actually know who signed in."
```

> **Caption:** The split that matters is **access token = authority for the API** and
> **ID token = identity for the client**. The authorization server is the only party that
> knows the user; the resource server only ever sees a token. Mixing those two tokens
> up is the single most common OAuth bug — and it is exactly the confused-deputy
> attack that chapter 03's `aud` validation stops.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The exact wording of the OAuth/OIDC split.** "OAuth solves authorisation for a third-party client; it does not prove who the user is to the client." Say it in one sentence, unprompted.
- [ ] **The four canonical role names** (RFC 6749 §1.1): *resource owner*, *client*, *authorization server*, *resource server* — and that the authorization server and the resource server are often **one deployment** (Keycloak does both).
- [ ] **The three token types and who each is for.** Access token → the API. Refresh token → the client, to get more access tokens. ID token → the client, to know the user. **Never send the ID token to your own API.**
- [ ] **Scopes vs roles/authorities.** A scope is an *OAuth* concept ("what this grant allows"); a role is an *application* concept ("what this user may do"). You need an explicit mapping between them.
- [ ] **All seven grant types and their exact wire steps.** Authorization Code, Authorization Code + PKCE, Client Credentials, Implicit (**deprecated**), Device Authorization, Refresh Token, Token Exchange. Know which one applies to a SPA, a mobile app, a server-to-server call and a TV.
- [ ] **What OAuth 2.1 actually changes.** PKCE mandatory · implicit grant removed · resource-owner-password grant removed · exact `redirect_uri` matching · refresh tokens must be sender-constrained or rotated.
- [ ] **Which Spring Boot starter to use.** `oauth2-client` (you are the app logging users in) · `oauth2-resource-server` (you are the API validating tokens) · `oauth2-authorization-server` (you issue tokens) · `spring-security-oauth2-jose` (JOSE/JWK support). Most people mix these up.
- [ ] **PKCE mechanics.** `code_verifier` = 43–128 random chars; `code_challenge` = `BASE64URL(SHA256(code_verifier))`; the `S256` method; `plain` exists only for legacy.
- [ ] **`state` vs `nonce` vs `code`.** `state` = CSRF defence on the authorization request; `nonce` = ID-token replay defence; `code` = single-use, short-lived, bound to the client and the `redirect_uri`.
- [ ] **The discovery endpoints.** `/.well-known/openid-configuration` (OIDC) and `/.well-known/oauth-authorization-server` (plain OAuth) — and that the JWKS URI is *discovered from* the discovery document, not hard-coded, in the `issuer-uri` mode.
- [ ] **The `aud` validation gotcha.** `JwtValidators.createDefaultWithIssuer()` checks `iss` + timestamps **only**. A token minted for `orders-api` will happily authenticate against `billing-api` unless you add an audience validator.
- [ ] **Introspection vs local validation.** Latency, revocation accuracy, and the fact that introspection makes the authorization server's availability part of your API's availability.
- [ ] **The GitHub private-email gotcha.** GitHub's `/user` does not always return `email`; you must call `/user/emails` and pick the **primary + verified** one.
- [ ] **Keycloak's role claim shape.** `realm_access.roles` (realm-wide) vs `resource_access.<client>.roles` (per-client) — and that a plain `JwtGrantedAuthoritiesConverter` reading `scope` will map **none** of them.

---

## 🧪 Coding Drill

- [ ] Configure a `spring-boot-starter-oauth2-client` login against a generic OIDC provider and print the exact **redirect URL** Spring builds at `/oauth2/authorization/<registrationId>` — then decode the `state` and confirm it round-trips through the `AuthorizationRequestRepository`.
- [ ] Build a `ClientRegistration` for a **Keycloak realm** in YAML, then start the app and capture the **exact log line** Spring emits when it resolves the `redirect-uri` template.
- [ ] Perform a **client-credentials** token request with `curl` against a Keycloak realm, then decode the access token and print its `iss`, `sub`, `aud`, `scope` and `exp` — and prove that **`sub` is the client id, not a user**.
- [ ] Write a `DefaultOAuth2AuthorizationRequestResolver` customiser that adds a custom parameter and confirm the parameter appears in the **generated redirect URL**.
- [ ] Implement a PKCE derivation in a unit test: generate a `code_verifier`, compute `BASE64URL(SHA256(verifier))`, and assert it equals the `code_challenge` you sent.
- [ ] Build a **resource server** with `issuer-uri`, start it, and capture the **startup discovery + JWKS fetch** in the logs — then show that the same app with `jwk-set-uri` starts even when the authorization server is down.
- [ ] Add a `DelegatingOAuth2TokenValidator` with a `JwtClaimValidator<List<String>>` for `aud` and prove that a token issued for a *different* audience is now rejected with 401.
- [ ] Write a custom `OAuth2UserService` that calls the GitHub `/user/emails` endpoint, picks the **primary verified** address, and asserts that a private-email GitHub account still resolves to a usable email.
- [ ] Map Keycloak's `realm_access.roles` into `ROLE_*` authorities with a custom `JwtAuthenticationConverter` and print the resulting `SecurityContext` authorities.
- [ ] Implement the **corporate-domain allowlist** rule in an `OAuth2UserService` and write the test that rejects `attacker@gmail.com` while accepting `user@acme.com`.

---

**Related folders:** ← [06 · JWT Authentication](../06-JWT-Authentication/README.md) · → [08 · Microservices with Spring Boot](../08-Microservices-with-Spring-Boot/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
