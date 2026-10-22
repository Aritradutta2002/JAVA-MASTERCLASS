# Social Login, Keycloak, Enterprise SSO & Security — The Complete Interview Guide

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("how do you implement login with Google?", "how do you map Keycloak roles to Spring authorities?", "how do you link a social account to an existing user?")
> **Prerequisites:** [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) · [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) · [README](./README.md)
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

- **Social login is the Authorization Code flow with a different provider.** Nothing about the
  protocol changes — you get a `code`, you exchange it for tokens, and you get an identity.
  What changes is the **provider's claim shape**, and therefore your mapping code.
- **The `CommonOAuth2Provider` presets save you YAML, not thinking.** `GOOGLE`, `GITHUB`,
  `FACEBOOK`, `OKTA` (and `MICROSOFT` from Spring Security 6.2 / Boot 3.2) cover the
  endpoints and the default `user-name-attribute`. The **claims** still differ per provider,
  and that is where the bugs live.
- **The GitHub email gotcha is the single most famous one.** GitHub's `/user` endpoint returns
  `email: null` unless the user has a **public** email set. You must call
  `/user/emails` with the `user:email` scope and pick the entry with `primary: true` **and**
  `verified: true`.
- **`OAuth2User` vs `OidcUser` are not interchangeable.** `OAuth2User` is built from the
  **UserInfo** response; `OidcUser` is built from the **ID token**. Both extend
  `OAuth2AuthenticatedPrincipal` — **neither extends the other**, so a controller taking an
  `OAuth2User` will fail to bind an OIDC login.
- **Keycloak's roles are not where beginners look.** They live under
  `realm_access.roles` (realm-wide) and `resource_access.<client>.roles` (per client) — and
  **only if the `roles` client scope is assigned**. A default `JwtGrantedAuthoritiesConverter`
  reads `scope` and will map **none** of them.
- **Keycloak is just an OIDC provider to Spring.** `spring-boot-starter-oauth2-client` plus
  `issuer-uri: http://localhost:8081/realms/acme` is enough. `keycloak-spring-boot-starter`
  is for the **Keycloak admin API**, not for logging in.
- **OIDC is authentication; your app still needs a local user record.** The "just-in-time
  provisioning" pattern creates or updates the local user on first login — and the account
  **linking** rules decide whether an existing local user is claimed.
- **Link accounts on a *verified* email, never an unverified one.** Auto-linking on an
  unverified provider email is an account-takeover vulnerability, and it is the classic way
  social login goes wrong.
- **You usually do not need the provider's access token.** For a social login whose only
  purpose is authentication, store the **refresh token** (if you need one) and **nothing
  else** — a stored access token is a live third-party credential you cannot revoke.
- **Scope minimisation is a security control.** Google's `profile` scope returns your full
  name, your **picture URL** and your locale. If you only need an email, ask for
  `openid email` — not `openid profile email`.
- **SAML is not OAuth and not OIDC.** It is an **assertion** (XML) in the browser's POST, not
  a token; it uses **metadata** rather than discovery; and Spring supports it with
  `spring-security-saml2-service-provider`, a completely different API surface.
- **The redirect-URI allowlist, the `state` check and the `nonce` check are not negotiable**
  — and the "redirect_uri open redirect" risk is why an **exact** match is mandatory.

---

## 1. What & Why — The Problem This Solves

**The problem — three things a password login gives you that a social login does not.**

```text
   ❌ WHAT BREAKS WHEN YOU ADD "LOGIN WITH GOOGLE"
   ────────────────────────────────────────────────────────────────────
   ① THERE IS NO LOCAL USER
      Your database has users. Google has *claims*. There is no
      row for this person. Who are they? Are they the same person
      who signed up with a password last month? You need an
      identity-resolution rule, not just a login.

   ② THE PROVIDER'S CLAIMS ARE NOT YOUR CLAIMS
      Google gives email, name, picture, email_verified.
      GitHub gives id, login, avatar_url, and *sometimes* email.
      Keycloak gives realm_access.roles and resource_access.
      Your code needs a stable, documented mapping — and the
      mapping differs per provider.

   ③ THE PROVIDER IS NOT A TRUSTED PARTY
      A social provider will happily let someone register
      "victim@yourcompany.com" as an unverified email. If you
      auto-link on email, they take over the account. Scope
      minimisation and email verification are not optional.
```

**The solution — a mapping layer between the provider's claims and your own user model.**

```text
   ✅ THE THREE-LAYER MODEL
   ────────────────────────────────────────────────────────────────────
   LAYER 1 — AUTHENTICATION (the provider's job)
      "This human is alice@acme.com and they proved it."
      → an OAuth2User / OidcUser with a set of attributes.

   LAYER 2 — IDENTITY RESOLUTION (your job)
      "Which of MY users is this?"
      → find or create a local user row.
      → the linking rules are the security boundary.

   LAYER 3 — AUTHORISATION (your job)
      "What may this local user do?"
      → roles and permissions from YOUR database, keyed by the
        local user id — not from the provider's claims.
```

**The analogy — a badge desk at an office building.** The badge office (the provider) issues
badges and knows the person's name and photo. The **building** (your app) has its own
directory of employees and their access floors. A visitor arriving with a valid badge is
**authenticated** — but the receptionist still has to look them up in the building's
directory to decide which floors they may enter, and whether that badge has already been
issued to someone who no longer works here.

The two classic failures map perfectly:

- **Not looking them up (skipping layer 2)** → you trust the badge blindly and anyone with
  any badge becomes an employee.
- **Looking them up by the wrong key (layer 2 done badly)** → you match on the **email** on
  the badge, and a visitor who registered `ceo@company.com` at a permissive badge office
  walks into the executive floor.

**What breaks without it?** A "login with Google" button that creates a **new local user every
time** — so a customer who signed up with a password and later clicks "Google" gets a
**second, empty account** with none of their data. That is the most common social-login bug
in production, and it is a data-consistency incident, not a cosmetic one.

> 💡 **The interview framing.** "How would you implement login with Google?" The answer that
> separates candidates is not the YAML — it is the **identity-resolution rule**: how you map
> the provider's `sub` to a local user, what happens when the provider returns no email, what
> happens when the email matches an existing account, and how you prevent an attacker who
> registers your customer's email address at the provider from taking over the account.

---

## 2. Mental Model — The One Picture

```text
   FROM A PROVIDER'S CLAIMS TO YOUR APPLICATION'S AUTHORITIES
   ═══════════════════════════════════════════════════════════════════

   ┌────────────────────────────────────────────────────────────────────┐
   │  LAYER 1 — THE PROVIDER (Google · GitHub · Keycloak · Okta)       │
   │                                                                    │
   │   Google   : sub, email, email_verified, name, picture, given_name │
   │   GitHub   : id, login, name, avatar_url, email (MAY BE NULL) ★   │
   │   Keycloak : sub, email, email_verified,                         │
   │              realm_access.roles[]      ← realm-wide roles         │
   │              resource_access.<client>.roles[]  ← client roles     │
   │                                                                    │
   │   ★ the shapes are DIFFERENT — that is the whole problem           │
   └──────────────────────────┬─────────────────────────────────────────┘
                              │  OAuth2User  |  OidcUser
                              │  (attributes + authorities)
                              ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │  LAYER 2 — IDENTITY RESOLUTION (YOUR CODE)                        │
   │                                                                    │
   │   find the local user by  (issuer, provider_sub)                  │
   │        │                                                           │
   │        ├── FOUND ──────────► update the profile, keep the roles   │
   │        │                    ★ DO NOT overwrite local roles from   │
   │        │                      provider claims                      │
   │        │                                                           │
   │        └── NOT FOUND ──────► email match?                         │
   │                               ├── verified email + no local user  │
   │                               │    → CREATE (JIT provisioning)     │
   │                               ├── verified email + local user     │
   │                               │    → LINK (only if the policy     │
   │                               │      allows it AND the local     │
   │                               │      account proves ownership)    │
   │                               └── unverified email ───────────────►
   │                                    REJECT / ask for a password    │
   └──────────────────────────┬─────────────────────────────────────────┘
                              │  a local `User` entity with a local id
                              ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │  LAYER 3 — AUTHORISATION (YOUR DATABASE)                           │
   │                                                                    │
   │   local_user_id ──► roles ──► permissions                        │
   │   (your own ids — never the provider's `sub`)                      │
   │                                                                    │
   │   → SecurityContext authorities: ROLE_USER, ROLE_ORDER_ADMIN, …    │
   └────────────────────────────────────────────────────────────────────┘

   ★ THE ONE-LINE VERSION FOR THE INTERVIEW
   ──────────────────────────────────────────────────────────────────
   "The provider authenticates the human; my database decides who that
    human is and what they may do. I resolve identity by (issuer, sub),
    never by email alone, and my roles come from my own table — the
    provider's claims only ever seed the profile."
```

**What to look at:** the three layers have **different owners**. Layer 1 is the provider's,
layer 3 is yours, and layer 2 — the identity resolution — is the **only** layer where a
security mistake turns into an account takeover. That is why this chapter spends so much of
its length on layer 2.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**Social login is the authorization code flow with a different provider.**

```text
   1. The browser hits  /oauth2/authorization/google
   2. Spring redirects to
        https://accounts.google.com/o/oauth2/v2/auth
          ?response_type=code&client_id=…&redirect_uri=…
          &scope=openid%20email%20profile
          &state=…&code_challenge=…&code_challenge_method=S256
   3. Google authenticates the user and shows the consent screen
   4. Google redirects back to
        /login/oauth2/code/google?code=…&state=…
   5. Spring exchanges the code for tokens
   6. Spring builds an OAuth2User / OidcUser
   7. Spring authenticates the session
```

Nothing about this is "social" — it is chapter 02 with a different endpoint set and a
different claim shape.

**The `CommonOAuth2Provider` presets.** Spring ships pre-configured metadata for the common
providers so you do not have to write their endpoint URLs.

| Provider | Constant | Authorization endpoint | Userinfo endpoint | Default `user-name-attribute` |
|---|---|---|---|---|
| Google | `GOOGLE` | `https://accounts.google.com/o/oauth2/v2/auth` | `https://openidconnect.googleapis.com/v1/userinfo` | `sub` |
| GitHub | `GITHUB` | `https://github.com/login/oauth/authorize` | `https://api.github.com/user` | `id` |
| Facebook | `FACEBOOK` | `https://www.facebook.com/v3.3/dialog/oauth` | `https://graph.facebook.com/me` | `id` |
| Okta | `OKTA` | `{baseUrl}/v1/authorize` | `{baseUrl}/v1/userinfo` | `sub` |
| Microsoft | `MICROSOFT` (Spring Security **6.2** / Boot **3.2**+) | `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/authorize` | `https://graph.microsoft.com/oidc/userinfo` | `sub` |

```yaml
# The shortest possible Google login.
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - openid
              - email
              - profile
```

> ⚠️ **What a preset does *not* do.** The preset fills in the **endpoints** and the default
> `user-name-attribute`. It does **not** decide your scopes, it does not tell you whether the
> provider returns a verified email, and it does not map any roles. Those three are yours.

**The scopes each provider actually understands.**

| Provider | Scopes you typically want | Notes |
|---|---|---|
| Google | `openid email profile` | `profile` returns the name, picture and locale — ask only for what you use |
| GitHub | `read:user user:email` | `user:email` is **required** to read the private email |
| Facebook | `email public_profile` | `email` needs Facebook's app review in production |
| Keycloak | `openid profile email roles` | `roles` is a **Keycloak client scope** — it puts the roles in the token |
| Okta | `openid profile email` | Plus your own API scopes |

**What each provider gives you.**

| Provider | Identity claim | Email | Email verified? | Roles/groups |
|---|---|---|---|---|
| Google | `sub` | Yes, in the ID token | **`email_verified`** | No |
| GitHub | `id` (numeric) | Sometimes `null` ★ | Via `/user/emails` | `read:org` for org membership |
| Facebook | `id` | Yes, if granted | No `verified` flag | No |
| Keycloak | `sub` | Yes | **`email_verified`** | `realm_access.roles`, `resource_access` |
| Okta | `sub` | Yes | **`email_verified`** | Groups claim (customisable) |

**The Google ID token.**

```json
{
  "iss": "https://accounts.google.com",
  "azp": "104912...apps.googleusercontent.com",
  "aud": "104912...apps.googleusercontent.com",
  "sub": "1049123...the-google-user-id",
  "email": "alice@acme.com",
  "email_verified": "true",
  "name": "Alice Sharma",
  "given_name": "Alice",
  "family_name": "Sharma",
  "picture": "https://lh3.googleusercontent.com/a/ACg8oc…",
  "locale": "en-IN",
  "hd": "acme.com",
  "iat": 1744999900,
  "exp": 1745000000
}
```

> 💡 **`hd` is a useful hint, not a control.** Google's `hd` claim carries the **hosted
> domain** of the account. It is a strong signal for a Workspace account, but it is **not a
> guarantee** — Google does not let a consumer account *claim* an `hd` it does not have, but
> it is still the provider's claim, not your verification. Enforce the domain rule **after**
> checking `email_verified`, and treat the domain check as your policy, not as Google's.

### 3.2 Intermediate Level

**The GitHub private-email gotcha — the full explanation.**

GitHub's OAuth `user` scope gives you `/user`:

```json
{
  "login": "alice",
  "id": 583231,
  "node_id": "MDQ6VXNlcjU4MzIzMQ==",
  "avatar_url": "https://avatars.githubusercontent.com/u/583231?v=4",
  "name": "Alice Sharma",
  "company": "@acme",
  "blog": "",
  "location": "Bengaluru",
  "email": null,
  "bio": null
}
```

`"email": null` — **and this is not a bug**. GitHub only returns the email in `/user` if the
user has made an email **public** in their settings. The vast majority do not.

The fix is the `user:email` scope, which adds `/user/emails`:

```json
[
  { "email": "alice@acme.com", "primary": true, "verified": true, "visibility": "private" },
  { "email": "alice.sharma@gmail.com", "primary": false, "verified": true, "visibility": "public" }
]
```

**The selection rule, precisely:**

```text
   ✓ pick the entry where  primary == true  AND  verified == true
   ✗ NEVER pick the first entry
   ✗ NEVER pick an entry with verified == false
   ✗ NEVER fall back to /user's email field without checking it
```

**GitHub's scope for this:** `user:email`. Without it, `/user/emails` returns
`403 Resource not accessible by personal access token` / `401 Requires authentication`.

**`OAuth2User` vs `OidcUser` — the precise difference.**

| | `OAuth2User` | `OidcUser` |
|---|---|---|
| Built from | The **UserInfo endpoint** response | The **ID token** claims |
| Interface | `OAuth2AuthenticatedPrincipal` + `getAuthorities()`, `getClaims()` | `OAuth2AuthenticatedPrincipal` + `getAuthorities()`, `getClaims()`, `getIdToken()`, `getIssuer()`, `getAudience()`, `getUserInfo()` |
| Spring service | `OAuth2UserService` → `DefaultOAuth2UserService` | `OidcUserService` → `DefaultOidcUserService` |
| Used with | Plain OAuth 2.0 providers, **GitHub**, Facebook | **Google**, Keycloak, Okta, Entra ID — any OIDC provider |
| `getAttributes()` | The userinfo claims | The ID token claims |
| `getName()` | The `user-name-attribute` value | The ID token's `sub` |

> ⚠️ **The Spring gotcha.** `OidcUser` does **not** extend `OAuth2User`. Both extend
> `OAuth2AuthenticatedPrincipal`. If your controller signature is
> `me(@AuthenticationPrincipal OAuth2User user)` and the login was an **OIDC** login, the
> argument will not bind. Accept `OAuth2AuthenticatedPrincipal` for code that must work with
> both, or branch on the registration.

**The `OAuth2UserService` / `OidcUserService` extension point.**

```java
public interface OAuth2UserService {
    OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException;
}

public interface OidcUserService {
    OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException;
}
```

`OAuth2UserRequest` gives you everything you need to make a decision:

```java
public interface OAuth2UserRequest extends OAuth2UserRequestBase {
    OAuth2AccessToken getAccessToken();          // the provider's access token
    ClientRegistration getClientRegistration();   // the registration (id, scopes, …)
}
```

**`ClientRegistration` in full (the Spring model of a registered client).**

```java
public final class ClientRegistration {
    private String registrationId;                          // "google" — your local handle
    private String clientId;
    private String clientSecret;                            // null for a public client
    private ClientAuthenticationMethod clientAuthenticationMethod;
    private AuthorizationGrantType authorizationGrantType;
    private String redirectUri;                             // "{baseUrl}/login/oauth2/code/{registrationId}"
    private Set<String> scope;
    private URI authorizationUri;
    private URI tokenUri;
    private URI userInfoUri;
    private URI userInfoAuthenticationMethod;
    private String userNameAttributeName;                   // "sub" for Google, "id" for GitHub
    private Map<String, Object> providerConfigurationMetadata;  // everything from discovery
    private ClientSettings clientSettings;                  // requireProofKey, etc.
    private ProviderDetails providerDetails;
}
```

**Keycloak — the concept map.**

| Concept | What it is | Why you care |
|---|---|---|
| **Realm** | An isolated universe of users, clients and roles | One realm per environment, per tenant, or one for production |
| **Client** | An application registered with the realm | Your Spring app is a client |
| **Client type** | **Public** (no secret — SPA/mobile) or **Confidential** (has a secret — server app) | Determines whether you can keep a secret |
| **Client scopes** | The permissions a client may ask for. **Default** scopes are always applied; **Optional** scopes must be requested | `roles` is an optional scope; without it the token has **no** roles |
| **Protocol mappers** | Rules that transform the token's claims | How `realm_access.roles` and `email_verified` get into the token |
| **Identity providers (IdPs)** | External providers Keycloak **federates** — Google, GitHub, a corporate SAML IdP | Lets Keycloak be the single front door |
| **User federation** | Syncing users from an external store (LDAP, AD) | Enterprise SSO without Keycloak holding the passwords |
| **Groups** | Hierarchical collections of users, mapped to roles | Group → role mapping avoids per-user assignments |
| **Realm roles** | Roles defined for the whole realm | Appear in `realm_access.roles` |
| **Client roles** | Roles defined for one client | Appear in `resource_access.<client>.roles` |

**The Keycloak token shape.**

```json
{
  "exp": 1745000000, "iat": 1744999700, "jti": "b7c1…",
  "iss": "http://localhost:8081/realms/acme",
  "aud": ["account", "orders-web"],
  "sub": "f1a2b3c4-…",
  "typ": "Bearer",
  "azp": "orders-web",
  "session_state": "9d0e…",
  "acr": "1",
  "allowed-origins": ["*"],
  "realm_access": {
    "roles": ["offline_access", "uma_authorization", "orders-reader", "billing-admin"]
  },
  "resource_access": {
    "account": { "roles": ["manage-account", "view-profile"] },
    "orders-web": { "roles": ["orders-reader", "orders-admin"] }
  },
  "scope": "openid profile email roles",
  "email_verified": true,
  "email": "alice@acme.com",
  "preferred_username": "alice",
  "name": "Alice Sharma",
  "given_name": "Alice",
  "family_name": "Sharma"
}
```

> 📌 **Two things to notice.**
> 1. `realm_access.roles` contains `offline_access` and `uma_authorization` — these are
>    **Keycloak's own internal roles**, not your application roles. A naive mapper that
>    turns every realm role into a `ROLE_*` authority grants `ROLE_OFFLINE_ACCESS` and
>    `ROLE_UMA_AUTHORIZATION`. You must **filter** them.
> 2. `resource_access.orders-web.roles` are **your client's** roles — usually the ones you
>    want for authorisation.

### 3.3 Advanced Level

**The role-mapping problem — a table, because no two providers agree.**

| Provider | Where the roles are | Example claim |
|---|---|---|
| **Google** | Not provided at all | — (use groups via the Admin SDK, or your own DB) |
| **GitHub** | Not in the token — via the API | `GET /user/orgs` → org names |
| **Keycloak** (realm roles) | `realm_access.roles` | `"realm_access": {"roles": ["orders-reader"]}` |
| **Keycloak** (client roles) | `resource_access.<client>.roles` | `"resource_access": {"orders-web": {"roles": ["orders-admin"]}}` |
| **Okta** | A custom claim you configure | `"groups": ["Admins", "Users"]` |
| **Entra ID** | `roles` (application roles) or `groups` | `"roles": ["Order.Read.All"]` |

**Mapping Keycloak's roles into Spring authorities.**

```java
package com.example.orders.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Maps a Keycloak access token into Spring authorities.
 *
 * WHY a custom converter: the default JwtGrantedAuthoritiesConverter reads the `scope`
 * claim and produces SCOPE_-prefixed authorities. Keycloak's ROLES are not in `scope` —
 * they are in realm_access / resource_access — so the default maps NONE of them.
 */
@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    /** Keycloak's own internal realm roles — never map these to application roles. */
    private static final Set<String> KEYCLOAK_INTERNAL_ROLES =
            Set.of("offline_access", "uma_authorization", "uma_protection");

    private static final String THIS_CLIENT = "orders-web";

    /** The application's own role names. Unknown roles are dropped, not renamed. */
    private static final Map<String, String> ROLE_MAP = Map.of(
            "orders-reader", "ROLE_ORDER_READER",
            "orders-admin", "ROLE_ORDER_ADMIN",
            "billing-admin", "ROLE_BILLING_ADMIN");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {

        Set<String> keycloakRoles = Stream.concat(
                        realmRoles(jwt).stream(),
                        clientRoles(jwt).stream())
                .filter(role -> !KEYCLOAK_INTERNAL_ROLES.contains(role))
                .toList();

        return keycloakRoles.stream()
                // ★ WHY: an UNKNOWN role must produce NO authority. Renaming it would
                //   turn a provider-side rename into an unintended privilege.
                .map(ROLE_MAP::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Set<String> realmRoles(Jwt jwt) {
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess == null) {
            return Set.of();
        }
        Object roles = realmAccess.get("roles");
        return roles instanceof List<?> list ? (Set<String>) Set.copyOf(list.stream()
                .filter(String.class::isInstance).map(String.class::cast).toList())
                : Set.of();
    }

    @SuppressWarnings("unchecked")
    private Set<String> clientRoles(Jwt jwt) {
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess == null) {
            return Set.of();
        }
        Object client = resourceAccess.get(THIS_CLIENT);
        if (!(client instanceof Map<?, ?> clientMap)) {
            return Set.of();
        }
        Object roles = clientMap.get("roles");
        return roles instanceof List<?> list ? (Set<String>) Set.copyOf(list.stream()
                .filter(String.class::isInstance).map(String.class::cast).toList())
                : Set.of();
    }
}
```

**The client-side equivalent — a `GrantedAuthoritiesMapper` for the login flow.**

```java
package com.example.orders.auth;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.access.intercept.GrantedAuthoritiesMapper;
import org.springframework.stereotype.Component;

@Component
public class KeycloakGrantedAuthoritiesMapper implements GrantedAuthoritiesMapper {

    private static final Set<String> KEYCLOAK_INTERNAL_ROLES =
            Set.of("offline_access", "uma_authorization", "uma_protection");

    private static final Map<String, String> ROLE_MAP = Map.of(
            "orders-reader", "ROLE_ORDER_READER",
            "orders-admin", "ROLE_ORDER_ADMIN");

    @Override
    public Collection<? extends GrantedAuthority> mapAuthorities(
            Collection<? extends GrantedAuthority> authorities) {

        return authorities.stream()
                .filter(a -> a.getAuthority().startsWith("ROLE_"))
                .map(GrantedAuthority::getAuthority)
                .filter(role -> !KEYCLOAK_INTERNAL_ROLES.contains(role))
                .map(role -> ROLE_MAP.get(role))
                .filter(Objects::nonNull)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }
}
```

> ⚠️ **Where the authorities come from in the client flow.** In the **client** (login) flow,
> Spring's `DefaultOAuth2UserService` / `DefaultOidcUserService` builds the authorities
> from the user's **roles/scopes** in the provider's response — for Keycloak those come from
> the `realm_access`/`resource_access` attributes when the `roles` client scope is assigned.
> In the **resource server** flow, the authorities come from the **JWT claims** via
> `JwtAuthenticationConverter`. Two different code paths, two different mappers — and a very
> common reason "the roles work in the login flow but not in the API".

**Keycloak client configuration for a Spring Boot app — the exact settings.**

```text
   Keycloak admin console → realm "acme" → Clients → Create client
   ─────────────────────────────────────────────────────────────────────────

   General settings
     Client type            : Confidential   ★ (a secret — the app has a backend)
     Client ID              : orders-web
     Name                   : Orders Web
     Enabled                : on

   Capability configuration
     Standard flow          : ON   ★ (the authorization code flow)
     Direct Access Grants   : OFF  ★ never enable — it is the password grant
     Implicit flow          : OFF  ★ removed in OAuth 2.1
     Service Accounts       : ON  (only if you also use client_credentials)
     Device Authorization   : OFF

   Client scopes
     Default client scopes  : web-origins, acr, roles, basic, microprofile-jwt
                              ★ "roles" MUST be here or the token has no roles
     Optional client scopes : profile, email, offline_access, phone, address
                              → add "email" and "profile" if you need them

   Settings → Valid redirect URIs
     http://localhost:8080/login/oauth2/code/keycloak
     https://app.acme.com/login/oauth2/code/keycloak
     ★ EXACT — no trailing slash, no wildcard

   Settings → Web origins
     http://localhost:8080
     https://app.acme.com

   Credentials
     Client secret          : copy it into the secret manager
     ★ never into application.yml in git
```

**`keycloak-spring-boot-starter` vs `spring-boot-starter-oauth2-client`.**

| | `spring-boot-starter-oauth2-client` | `keycloak-spring-boot-starter` |
|---|---|---|
| Purpose | Log users in against **any** OIDC provider | Talk to **Keycloak's own APIs** (admin client, token verification) |
| Needed for a plain "login with Keycloak"? | ✅ **Yes — this is the one you want** | ❌ No |
| Adds | Spring Security OAuth2 client support | The Keycloak admin client, a `Keycloak` bean, token utilities |
| Verdict | Use this | Use it only if you administer the realm from the app |

**The JIT provisioning + account-linking flow.**

```text
   ON LOGIN (your custom OAuth2UserService / OidcUserService)
   ────────────────────────────────────────────────────────────

   ① provider claims arrive
      sub        = "f1a2b3c4-…"
      email      = "alice@acme.com"
      email_verified = true

   ② find by (issuer, provider_sub)          ← the primary key
      └── FOUND → update the profile (name, picture); KEEP the local roles
                   → this is a returning user

   ③ not found → look for an existing local user by email
      ├── no local user with that email
      │     └── CREATE a local user (JIT provisioning)
      │         → assign the default role (ROLE_USER)
      │         → record (issuer, provider_sub) on the row
      │
      └── a local user with that email EXISTS
            ├── the local account has a PASSWORD (it was created locally)
            │     └── ★ DO NOT auto-link. Require the user to log in with
            │         the password and then link explicitly, OR send a
            │         "confirm this email" link to the local account.
            │
            └── the local account was ALSO created by a provider (social)
                  └── ★ LINK ONLY IF: the provider's email_verified is true
                      AND the local account's email is verified
                      AND the domain is on the allowlist
```

**Why the "local account has a password" case must not auto-link.** The local account's owner
proved control of the email by setting a password (weak evidence, but some evidence) — or
more importantly, the local account may be **the corporate account of a different employee**.
If you auto-link a provider account to it simply because the emails match, an attacker who
registers `ceo@acme.com` at a permissive provider takes over the CEO's account. The rule
that makes this safe: **only auto-link when both sides are verified**, and prefer explicit
linking with proof of the other credential.

### 3.4 Expert Level

**SAML 2.0 vs OIDC — the real comparison.**

| | **OAuth 2.0 + OIDC** | **SAML 2.0** |
|---|---|---|
| **What it is** | A token-based protocol | An **XML assertion** protocol |
| **The artefact** | A JWT (access token, ID token) | A base64-encoded XML `<saml:Assertion>` |
| **How it arrives** | The ID token in the token response; the access token in a header | The assertion in an **auto-submitted HTML form POST** (`SAMLResponse` field) |
| **Transport** | The **front channel** carries a `code`; the tokens come on the **back channel** | The assertion travels in the **browser's POST** — it is in the front channel |
| **Endpoint discovery** | **Discovery document** (`.well-known/openid-configuration`) | **Metadata XML** (`/saml/metadata`) — and the **federation metadata**, which is usually exchanged offline |
| **Issuer trust** | The `iss` claim + a JWKS | The issuer's certificate, pinned via metadata |
| **Signing** | JWT signatures (JWS) | XML-DSig envelopes, often with a **SHA-1** legacy weakness |
| **Audience** | `aud` claim | the `<AudienceRestriction>` element |
| **Spring support** | `spring-boot-starter-oauth2-client` / `-resource-server` | **`spring-security-saml2-service-provider`** (a separate artifact and API) |
| **Typical users** | Modern SPAs, mobile apps, APIs | Enterprise IdPs, B2B federation, government |
| **Practical strengths** | Stateless, JSON, developer-friendly, better for APIs | Ubiquitous in the enterprise; many vendors support *only* SAML |
| **Practical weaknesses** | More work for a SAML-only vendor | The assertion goes through the browser; XML parsing has a long CVE history; no native API-credential story |

**The two are complementary, not competing.** The pattern in large enterprises is a **broker**:
Keycloak (or Ping, or Okta) speaks SAML to the legacy IdP and **OIDC** to everything
internal. Your application only ever sees an OIDC token. That is the single most valuable
architectural move for a mixed estate — and it is exactly why chapter 01's "one AS, many
resource servers" topology matters.

**Spring's SAML support — the shape of it.**

```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          okta:
            assertingparty:
              metadata-uri: https://okta.okta.com/app/xxx/sso/saml/metadata
            # Spring parses the metadata and derives the SSO URL, the
            # signing credentials and the entity id.
            signing:
              credentials:
                - private-key-location: classpath:saml/okta.key
                  certificate-location: classpath:saml/okta.crt
```

```java
http.saml2Login(saml2 -> saml2
        .relyingPartyRegistrationRepository(repos)
        .authenticationSuccessHandler(...));
```

Two things to know:

1. **`RelyingPartyRegistrationRepository`** is the SAML equivalent of `ClientRegistration` —
   it is built from the metadata, and it is where you map the SAML attributes to Spring
   authorities (`saml2Login(...).userDetailsService(...)` or a custom
   `GrantedAuthoritiesMapper`).
2. **The SAML response is signed** — and Spring validates the signature against the
   metadata's certificate. If the metadata is fetched over HTTPS from the IdP, you are
   trusting the IdP's TLS for the certificate; for high-value integrations, **pin the
   certificate locally**.

**The social-login security model, threat by threat.**

| Threat | Attack | Defence |
|---|---|---|
| **Account takeover via email** | The attacker registers `victim@acme.com` at a permissive provider | Link only on **`email_verified` + a verified local email**; prefer explicit linking |
| **Unverified provider email** | The provider returns an email it has not verified | Check `email_verified`; reject or require a second factor |
| **Role injection** | A provider lets the user set a role claim | **Never** map provider roles straight to authority; use an explicit map and drop unknown roles |
| **Scope creep** | The app requests `profile` and stores the picture URL, which is a tracking pixel | Request the **minimum** scope; do not persist what you do not use |
| **Provider token storage** | The provider's access token is stored and later leaked | Usually **do not store it**; the refresh token is the only one worth keeping |
| **Open redirect via `redirect_uri`** | A lax redirect registration sends the code to an attacker | **Exact** matching; OAuth 2.1 mandates it |
| **Login CSRF** | The victim's browser is bound to the attacker's provider account | **`state`** — validate it (chapter 02) |
| **ID token replay** | A captured ID token is replayed into another session | **`nonce`** — validate it |
| **Provider account takeover** | The attacker takes over the provider account itself | Out of your control — mitigate with your own second factor and a session-risk check |
| **Mass assignment** | The provider's claims are written straight into your user entity | Map **explicitly**; never bind provider attributes to entity fields automatically |
| **Unlinked-account sprawl** | Each provider creates a separate local user | Resolve by `(issuer, sub)` and link deliberately |
| **Consent fatigue** | The consent screen is broad, so users click through | Minimise scopes; show a clear, specific consent screen |

**The "corporate domains only" rule — where it must be enforced.**

```text
   ❌ ENFORCING IT ONLY IN YOUR CODE
   ──────────────────────────────────────────────────────────
   The check runs in YOUR application, AFTER the user has already
   authenticated at the provider. The attacker is already inside
   the door; you are checking the badge at the reception desk.

   ✅ ENFORCING IT AT THE PROVIDER
   ──────────────────────────────────────────────────────────
   For GOOGLE WORKSPACE:
     Google Cloud Console → APIs & Services → OAuth consent screen
       → "Authorized domains": acme.com, acme.co.uk
     → a consumer gmail.com account CANNOT complete the consent
       screen for your client at all. The provider blocks it.

   For KEYCLOAK (a social/enterprise IdP):
     Keycloak → realm → Identity Providers → Google → Config
       → "Hosted domain" / a "Domain" attribute mapper
     → the IdP rejects the sign-in before the token is issued.

   ✅ DEFENCE IN DEPTH
   ──────────────────────────────────────────────────────────
   ALSO check in your code:
     - email_verified == true
     - the email's domain is on your allowlist
     - the provider's `hd` claim (Google Workspace) matches
   This is your policy. The provider's block is their policy.
   Both are needed: the provider's block stops the sign-in; your
   check stops a misconfigured or newly-added provider.
```

**The enterprise case: a SAML assertion carrying the domain.** In an enterprise, the
employee's domain usually comes from the **directory** (AD/LDAP) rather than an email
address, and it is in the SAML assertion as an attribute or a group. The rule is the same —
extract the attribute and compare against the allowlist — but the source is the assertion, and
an enterprise IdP is a far more trustworthy source than a consumer social provider.

**Social login + password fallback — the combination that works.**

```text
   ┌────────────────────────────────────────────────────────────────┐
   │  SOCIAL FIRST, PASSWORD AS THE FALLBACK                      │
   │                                                                │
   │   "Continue with Google"   ← the primary path                  │
   │   "Use a password instead"  ← for users who cannot or will not  │
   │                                                                │
   │  WHY both:                                                     │
   │   • some corporate users are blocked from consumer providers    │
   │   • some users prefer not to hand over a provider account      │
   │   • a provider outage must not lock EVERY user out             │
   │                                                                │
   │  ★ THE CRITICAL RULE: a social login may NEVER escalate into  │
   │    a privileged local account without an explicit check.      │
   │    Admin accounts: social login DISABLED.  See §7.4.           │
   └────────────────────────────────────────────────────────────────┘
```

**The audit trail — what to record.**

```text
   ON EVERY AUTHENTICATION EVENT, LOG:
     ✔ the issuer / registration id      ("google", "keycloak", "okta")
     ✔ the provider's subject             (sub / id)  — hashed or PII-flagged
     ✔ the local user id it resolved to
     ✔ whether the user was NEW, EXISTING, or LINKED  ★
     ✔ the email_verified flag
     ✔ the client_id / registration id
     ✔ the outcome (success / rejected / linked)
     ✔ a correlation id and the source IP
     ✘ NEVER the token, the code, the client_secret, or the PKCE verifier
```

> 💡 **The "NEW vs EXISTING vs LINKED" field is the one auditors ask for.** A spike in
> "LINKED" events means account-linking is happening more than expected — which is either a
> legitimate migration, or an account-takeover campaign in progress. It is a single field that
> turns a log into an early-warning system.

---

## 4. Internal Working Mechanism

### 4.1 The social login chain, end to end

```text
   1  Browser → GET /oauth2/authorization/google
   2  OAuth2AuthorizationRequestRedirectFilter
        └─▶ DefaultOAuth2AuthorizationRequestResolver
              └─▶ reads the ClientRegistration ("google")
              └─▶ builds the URL from providerDetails.configurationMetadata
        └─▶ AuthorizationRequestRepository#saveAuthorizationRequest
        └─▶ 302 → accounts.google.com
   3  Google authenticates the user and shows the consent screen
   4  Google → 302 → /login/oauth2/code/google?code=…&state=…
   5  OAuth2AuthorizationCodeGrantFilter → OAuth2LoginAuthenticationFilter
        ├─▶ AuthorizationRequestRepository#removeAuthorizationRequest  [state check]
        └─▶ AuthenticationManager → OAuth2LoginAuthenticationProvider
              ├─▶ exchange the code → OAuth2AccessTokenResponse
              │     (OAuth2AccessTokenResponseHttpMessageConverter)
              ├─▶ ★ userService.loadUser(userRequest)          [EXTENSION POINT]
              │     ├─ DefaultOidcUserService      (OIDC provider)
              │     │    ├─ decode the id_token → OidcIdToken
              │     │    ├─ call the userinfo endpoint (if configured)
              │     │    └─ build an OidcUser with the authorities
              │     ├─ DefaultOAuth2UserService    (plain OAuth 2.0 provider)
              │     │    └─ call the userinfo endpoint → OAuth2User
              │     └─ ★ YOUR service (extends one of the above)
              │          ├─ fetch extra attributes (GitHub /user/emails)
              │          ├─ resolve / create the LOCAL user   [JIT provisioning]
              │          ├─ map the provider's roles → authorities
              │          └─ throw OAuth2AuthenticationException to REJECT
              └─▶ return OAuth2LoginAuthenticationToken
   6  OAuth2AuthenticationToken → SecurityContextHolder
   7  OAuth2LoginAuthenticationSuccessHandler → redirect
```

**The extension points in one table.**

| Interface | Implement it to | The default |
|---|---|---|
| `OAuth2UserService` | Map a plain-OAuth provider's user into your model | `DefaultOAuth2UserService` |
| `OidcUserService` | The same, for an OIDC provider (Google, Keycloak, Okta) | `DefaultOidcUserService` |
| `GrantedAuthoritiesMapper` | The provider's roles → your authorities | `SCOPE_`-prefixed scopes |
| `AuthorizationRequestRepository` | Where the `state` lives | The `HttpSession` |
| `OAuth2AuthorizationRequestResolver` | Extra parameters, PKCE, a custom redirect | `DefaultOAuth2AuthorizationRequestResolver` |
| `AuthenticationSuccessHandler` | Where to redirect after login; mint your own session | `OAuth2LoginAuthenticationSuccessHandler` |
| `AuthenticationFailureHandler` | Log the failure reason; render an error page | `SimpleUrlAuthenticationFailureHandler` |

### 4.2 Where the authorities come from — client flow vs resource-server flow

```text
   CLIENT FLOW (the login)                        RESOURCE-SERVER FLOW (the API)
   ─────────────────────────                       ───────────────────────────────
   DefaultOidcUserService                          JwtDecoder → Jwt
     ├─ decode the id_token                          ↓
     ├─ call the userinfo endpoint                  JwtAuthenticationConverter
     │    → the userinfo response has a                └─▶ JwtGrantedAuthoritiesConverter
     │      "roles" / "scope" attribute                    ├─ reads "scope" or "scp"
     └─ build the authorities                            ├─ prefix "SCOPE_"
        └─ OAuth2UserAuthority / OidcUserAuthority         └─ ★ does NOT read realm_access
           └─ mapped by GrantedAuthoritiesMapper
                                                       For Keycloak roles you need a
                                                       ★ CUSTOM Converter<Jwt, …> that
                                                         reads realm_access.roles and
                                                         resource_access.<client>.roles
```

> 📌 **This is the #1 source of "the roles work on the login page but not in the API".**
> The two paths use **different code** to derive the authorities, and they are configured in
> different places. When you switch from a session cookie to a bearer token, the role
> mapping silently changes unless you implement **both**.

### 4.3 The Keycloak token → Spring authority chain

```text
   Keycloak issues the token
     └─ client scope "roles" is attached
          └─ a protocol mapper adds realm_access.roles
          └─ a protocol mapper adds resource_access.orders-web.roles
     └─ the token is signed with the realm's active key

   Spring resource server
     └─ NimbusJwtDecoder
          ├─ fetch the JWKS from http://localhost:8081/realms/acme/protocol/openid-connect/certs
          ├─ resolve the key by kid
          ├─ verify the signature (RS256, pinned)
          └─ run the validators (iss + timestamps + aud)
     └─ JwtAuthenticationConverter
          └─ setJwtGrantedAuthoritiesConverter(KeycloakRoleConverter)
               ├─ read realm_access.roles      → filter the internal ones → map
               └─ read resource_access.orders-web.roles → map
     └─ JwtAuthenticationToken → SecurityContextHolder
     └─ AuthorizationFilter
```

---

## 5. Step-by-Step Execution Flow

### 5.1 "Login with Google", end to end

1. The browser requests `GET /oauth2/authorization/google`.
2. Spring builds the redirect URL from the `google` registration: `response_type=code`,
   `client_id`, `redirect_uri`, `scope=openid profile email`, `state`, and — because the
   client is confidential and Spring enables PKCE for it — `code_challenge` and
   `code_challenge_method=S256`.
3. The `state` is stored in the session (or the cookie).
4. Google authenticates the user and shows the consent screen the first time.
5. Google redirects back to `http://localhost:8080/login/oauth2/code/google?code=…&state=…`.
6. Spring validates the `state` and exchanges the code at Google's token endpoint.
7. The response contains an `access_token` and an `id_token`.
8. `DefaultOidcUserService` **decodes the ID token** and, because `user-info-uri` is
   configured, calls Google's UserInfo endpoint.
9. Spring builds an `OidcUser` whose `getName()` is the `sub` and whose attributes are the
   ID token's claims (`email`, `email_verified`, `name`, `picture`, …).
10. The authorities are derived from the `scope` (or the userinfo's roles) and mapped by the
    `GrantedAuthoritiesMapper`.
11. Your custom `OidcUserService` (if you have one) runs here — it can fetch extra data,
    resolve the local user, or **reject** the login with an
    `OAuth2AuthenticationException`.
12. An `OAuth2AuthenticationToken` is placed in the `SecurityContextHolder` and the session
    is created.
13. The success handler redirects to the originally requested page.

### 5.2 The GitHub email fetch, step by step

1. The login completes with a GitHub access token that carries the `user:email` scope.
2. `DefaultOAuth2UserService` calls `GET https://api.github.com/user` → the `email` field is
   `null` (the user has no public email).
3. Your custom service notices `email == null` and calls
   `GET https://api.github.com/user/emails` with the same access token.
4. The response is a JSON **array**; you select the entry with `primary == true` **and**
   `verified == true`.
5. If no such entry exists, you **reject** the login (an OAuth2AuthenticationException) or
   ask the user to supply an email and verify it — you must **not** guess.

### 5.3 The Keycloak login + role mapping, step by step

1. The browser hits `/oauth2/authorization/keycloak`; Spring redirects to
   `http://localhost:8081/realms/acme/protocol/openid-connect/auth`.
2. The user authenticates in the Keycloak login page.
3. Keycloak redirects back with the code; Spring exchanges it at the token endpoint.
4. The token contains `realm_access.roles` and `resource_access.orders-web.roles` — **only
   because the `roles` client scope is assigned to the client**.
5. The `OidcUser` is built; the attributes include the whole claim set.
6. `KeycloakGrantedAuthoritiesMapper` (or `KeycloakRoleConverter`) maps
   `orders-reader` → `ROLE_ORDER_READER`, drops `offline_access`, and drops any unknown role.
7. The `SecurityContext` now carries the mapped authorities.

### 5.4 The JIT provisioning + account linking, step by step

1. The provider's claims arrive at your `OidcUserService`.
2. You look up the local user by `(issuer, provider_sub)`:
   - **Found** → update the profile fields (name, picture) and return. **The local roles are
     not touched.**
3. **Not found** → look up by verified email:
   - **No local user** → create one with the default role, persist the
     `(issuer, provider_sub)` link, and log the event as `PROVISIONED`.
   - **A local user exists** → apply the linking policy:
     - the provider's `email_verified` must be **true**;
     - the local account's email must be **verified**;
     - if the local account has a **password** → do **not** auto-link; require an explicit
       link step (log in with the password, then link);
     - if the local account was created by **another provider** → link only if the policy
       allows it and the emails are verified on both sides;
     - otherwise → log the event as `LINKED` and attach the `(issuer, provider_sub)` row.

---

## 6. Visualizations

### 6.1 "Login with Google", end to end (sequence)

```mermaid
sequenceDiagram
    autonumber
    actor U as User<br/>(browser)
    participant C as Spring Boot app<br/>(client)
    participant G as Google<br/>(authorization server)

    U->>C: GET /oauth2/authorization/google
    C->>C: state = random(32 bytes)<br/>code_challenge = BASE64URL(SHA256(verifier))<br/>SAVE pending request
    C-->>U: 302 → accounts.google.com/o/oauth2/v2/auth<br/>?scope=openid profile email&state=…&code_challenge=…
    U->>G: authenticate (Google account + MFA)
    G-->>U: consent screen ("orders-web wants your email and name")
    G-->>U: 302 → localhost:8080/login/oauth2/code/google?code=…&state=…
    U->>C: GET /login/oauth2/code/google?code=…&state=…
    C->>C: compare state ✔
    C->>G: POST /token<br/>grant_type=authorization_code&code=…<br/>&redirect_uri=…&code_verifier=…<br/>Basic(client_id:client_secret)
    G-->>C: 200 { access_token, id_token, refresh_token, expires_in }
    C->>C: decode the id_token → OidcIdToken<br/>(iss, aud, exp, nonce ✔)
    C->>G: GET /userinfo  Authorization: Bearer <access_token>
    G-->>C: { sub, email, email_verified, name, picture }
    C->>C: ★ OidcUserService → resolve/create the LOCAL user
    C->>C: map the provider's claims → ROLE_* authorities
    C-->>U: 302 → /orders  (session cookie set)
```

**What to look at:** the provider gives you **claims about a human**, and step 8 is where
*your* application decides whether that human is one of your users. The provider never
touches your user table — and that separation is the security boundary.

### 6.2 The Keycloak realm / client / role hierarchy (ASCII)

```text
   THE KEYCLOAK HIERARCHY
   ═══════════════════════════════════════════════════════════════════════

   ┌──────────────────────────────────────────────────────────────────┐
   │  REALM  "acme"                        ← the isolation boundary   │
   │  (one per environment; one per tenant for a multi-tenant IdP)    │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  CLIENTS                                                   │  │
   │  │                                                            │  │
   │  │  ┌──────────────────┐   ┌──────────────────┐               │  │
   │  │  │ orders-web       │   │ orders-spa       │               │  │
   │  │  │ CONFIDENTIAL     │   │ PUBLIC           │               │  │
   │  │  │ has a secret    │   │ no secret        │               │  │
   │  │  │ redirect:        │   │ redirect:        │               │  │
   │  │  │  .../code/keycloak│  │  .../code/keycloak│               │  │
   │  │  │ scopes:          │   │ scopes:          │               │  │
   │  │  │  default: web-origins, acr, roles, basic ★│           │  │
   │  │  │  optional: profile, email, offline_access    │           │  │
   │  │  └────────┬─────────┘   └────────┬─────────┘               │  │
   │  └───────────┼──────────────────────┼─────────────────────────┘  │
   │              │                      │                             │
   │  ┌───────────▼──────────────────────▼─────────────────────────┐  │
   │  │  CLIENT SCOPES  (the permissions a client may ask for)     │  │
   │  │     "roles"  → adds realm_access + resource_access claims  │  │
   │  │     "email"  → adds email + email_verified                │  │
   │  │     "profile"→ adds name, preferred_username, …            │  │
   │  │     ★ "roles" must be ASSIGNED or the token has NO roles  │  │
   │  └────────────────────────────────────────────────────────────┘  │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  REALM ROLES                     (realm_access.roles)       │  │
   │  │     orders-reader      orders-admin      billing-admin      │  │
   │  │     offline_access ★   uma_authorization ★   ← internal     │  │
   │  │     ★ filter these out — they are Keycloak's, not yours    │  │
   │  └────────────────────────────────────────────────────────────┘  │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  CLIENT ROLES  (resource_access.<clientId>.roles)          │  │
   │  │     for client "orders-web":                                │  │
   │  │        orders-reader   orders-admin                        │  │
   │  │     for client "account":                                  │  │
   │  │        manage-account  view-profile                       │  │
   │  └────────────────────────────────────────────────────────────┘  │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  GROUPS  (collections of users → mapped to roles)          │  │
   │  │     /finance        → billing-admin                        │  │
   │  │     /warehouse      → orders-reader                        │  │
   │  └────────────────────────────────────────────────────────────┘  │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  IDENTITY PROVIDERS (federation)                            │  │
   │  │     google  →  GitHub  →  corporate SAML (Okta/AD)          │  │
   │  │     ★ the realm becomes the single front door              │  │
   │  └────────────────────────────────────────────────────────────┘  │
   │                                                                  │
   │  ┌────────────────────────────────────────────────────────────┐  │
   │  │  USER FEDERATION (LDAP / AD sync)                          │  │
   │  └────────────────────────────────────────────────────────────┘  │
   └──────────────────────────────────────────────────────────────────┘

   ★ THE SPRING SIDE
   ────────────────────────────────────────────────────────────────────
   token → realm_access.roles        → filter → map → ROLE_ORDER_READER
          resource_access.orders-web.roles → filter → map → ROLE_ORDER_ADMIN
          ★ an UNKNOWN role → NO authority (never renamed)
```

**Caption:** the realm is the isolation boundary, the **client scope** decides what a token
can contain at all, and the **role claim's location** depends on whether the role is
realm-wide or client-specific. The three Keycloak-internal roles at the bottom of the realm
list are the classic accident — mapping them blindly produces `ROLE_OFFLINE_ACCESS`.

### 6.3 JIT provisioning and account linking (sequence)

```mermaid
sequenceDiagram
    autonumber
    actor U as User
    participant P as Provider<br/>(Google / GitHub / Keycloak)
    participant S as Custom OidcUserService
    participant DB as Local database

    U->>P: sign in
    P-->>S: OidcUser { sub, email, email_verified, … }

    S->>DB: find by (issuer, provider_sub)
    alt FOUND — a returning user
        DB-->>S: local user
        S->>DB: update the profile (name, picture)<br/>★ keep the local roles
        S-->>U: logged in
    else NOT FOUND — a new visitor
        S->>DB: find by email
        alt no local user with that email
            S->>DB: CREATE local user (JIT provisioning)<br/>default role ROLE_USER<br/>store (issuer, provider_sub)
            S-->>U: logged in — event = PROVISIONED
        else a local user exists
            S->>S: check email_verified (provider)<br/>AND the local email is verified
            alt both verified AND the domain is allowed
                S->>DB: attach (issuer, provider_sub) to the local user
                S-->>U: logged in — event = LINKED
            else the local account has a PASSWORD
                S-->>U: ★ REJECT — "sign in with your password,<br/>then link this account"
            else the email is unverified
                S-->>U: ★ REJECT — email not verified
            end
        end
    end
```

**What to look at:** the **three** rejection paths at the bottom. They are the whole point
of this diagram: "found by email" is **not** the same as "safe to link", and an
implementation that skips the checks is one line away from an account-takeover bug.

### 6.4 Provider → role claim → Spring authority (ASCII table)

```text
   ┌────────────┬──────────────────────────────────┬──────────────────────────┬───────────────────────┐
   │ PROVIDER   │ WHERE THE ROLES ARE             │ THE CLAIM                │ THE SPRING AUTHORITY  │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ Google     │ nowhere in the token             │ —                        │ (none — use your own  │
   │            │ (Workspace groups need the Admin │                          │  database, or the     │
   │            │  SDK, not the login token)       │                          │  Admin SDK API)       │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ GitHub     │ nowhere in the token             │ —                        │ (none — call the      │
   │            │ (orgs come from the API:         │                          │  /user/orgs API)      │
   │            │  GET /user/orgs)                 │                          │                       │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ Keycloak   │ realm-wide roles                 │ realm_access.roles       │ ROLE_<MAPPED>         │
   │ (realm)    │                                  │ ["orders-reader", …]     │ via an explicit map   │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ Keycloak   │ per-client roles                 │ resource_access.        │ ROLE_<MAPPED>         │
   │ (client)   │                                  │   orders-web.roles      │ via an explicit map   │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ Okta       │ a custom claim you configure     │ groups                  │ ROLE_<MAPPED>         │
   │            │                                  │ ["Admins","Users"]       │ (or a GROUPS→role    │
   │            │                                  │                          │  lookup in your DB)   │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ Entra ID   │ application roles or groups      │ roles / groups          │ ROLE_<MAPPED>         │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ SAML/AD    │ directory attributes or groups   │ SAML attribute / group   │ ROLE_<MAPPED>         │
   ├────────────┼──────────────────────────────────┼──────────────────────────┼───────────────────────┤
   │ ANY        │ the OAuth scopes                 │ scope                   │ SCOPE_orders:read     │
   │            │                                  │ "openid profile email"   │ (the DEFAULT mapping) │
   └────────────┴──────────────────────────────────┴──────────────────────────┴───────────────────────┘

   ★ THE MAPPING RULES THAT APPLY TO EVERY ROW
   ────────────────────────────────────────────────────────────────────
   ① EXPLICIT — a table, not a convention. "orders-reader" → "ROLE_ORDER_READER".
   ② UNKNOWN → NO AUTHORITY. Never rename it into a role; that is how a
      provider-side rename becomes an unintended privilege.
   ③ FILTER the provider's internal roles (Keycloak's offline_access,
      uma_authorization, uma_protection).
   ④ YOUR DATABASE IS THE AUTHORITY FOR BUSINESS ROLES.
      A provider role is an INPUT to your mapping, not your permission model.
   ⑤ THE LOCAL USER ROW IS THE KEY. The provider's `sub` identifies the
      person at the provider; your `user_id` identifies them everywhere else.
```

**Caption:** the table's bottom line is rule ④. Provider roles are a *convenient input*;
your own database is the permission model. Every team that gets this wrong ends up with
"the intern is an admin because Google Workspace said so".

---

## 7. Production-Level Use Cases & Real Industry Examples

### 7.1 Consumer SaaS — social login as the primary path

**The situation.** A consumer SaaS (note-taking app for students) has **800,000 registered
users**. 70% sign in with Google, 20% with Apple (mandatory if you offer social sign-in on
iOS), 10% with a password.

**The design.** Three `ClientRegistration` beans: `google`, `apple`, `local`. The `OidcUserService`
resolves by `(issuer, provider_sub)`, JIT-provisions on first sight, and links only on a
**verified** email. Apple is the special case: Apple **hides the user's email** unless the
user chooses to share it, so the first login for an Apple user has **no email** — the app
must ask for one and verify it, or key the account by `(issuer, sub)` only.

**The numbers.** The consent screen asks for `openid email profile` — the **minimum** that
gives the app a usable identity. The `picture` URL is **not** persisted (it is a tracking
pixel that can be revoked by the user). Login latency: **380 ms** at p50, **1,140 ms** at p99
(the login page dominates). The `local_user` table has 800,000 rows and a
`provider_identity` table with ~560,000 rows.

### 7.2 Enterprise B2B — Keycloak as the broker for SAML and OIDC tenants

**The situation.** A B2B logistics platform serves **340 enterprise customers**. 210 of them
have a SAML-only IdP (Azure AD, Okta, Ping, one ADFS); 130 use OIDC. 40 have Google
Workspace and want social-style login *inside* the platform's login page.

**The design.** **Keycloak as the broker.** Keycloak federates all 210 SAML IdPs and all 40
Google accounts; it is the single OIDC issuer for the platform's applications. Your Spring
apps see exactly one issuer, one JWKS, one `aud` convention, and one token shape.

**The numbers.** Keycloak's discovery URL is
`https://sso.acme.com/realms/{tenant}/.well-known/openid-configuration`; each tenant is a
**realm**. Onboarding a new customer = a realm + a SAML client + a protocol mapper =
**~20 minutes of Keycloak admin work**, and **zero** application changes. The platform's
resource servers validate `iss` (the realm's issuer) and `aud` (the platform's API), plus a
**`tenant_id` claim** for the tenant boundary. The alternative — one OIDC integration per
SAML customer in the application — would be **340 bespoke integrations**.

### 7.3 A bank's employee portal — SAML federation with a corporate-domain rule

**The situation.** A bank has 4,200 employees, all in AD. Their IdP is Azure AD, exposed as
SAML. The portal must be reachable only from the corporate network, and the identity must
carry the department and the cost-centre for downstream authorisation.

**The design.** Spring's `spring-security-saml2-service-provider` consumes the Azure AD
metadata; the SAML assertion carries the `department` and `costcenter` attributes; a custom
`GrantedAuthoritiesMapper` maps them to `ROLE_*` plus **data-scope** claims. The corporate
domain rule is enforced by the **IdP** (Azure AD only issues assertions for the tenant), and
in code as a second check on the `email` attribute's domain.

**The numbers.** The assertion is **~8 KB** of base64 XML — larger than a JWT ID token
(~1.2 KB) — which is the practical cost of SAML. The portal's session TTL is **30 minutes**
with a **15-minute** re-authentication for payments. The SAML response arrives in the
browser's POST body, so the portal's callback must be **CSRF-exempt** (it is not a
cookie-driven session action) and the **access log must be redacted** for the `SAMLResponse`
parameter.

### 7.4 A regulated platform — social login for customers, password for administrators

**The situation.** A health-tech platform serves **30,000 clinics**. Clinic staff log in with
their **Google Workspace** accounts (the clinics use Workspace). **Administrators** (about 40
people) must not be able to use a consumer social account — a personal Gmail with the same
email could be used for a takeover.

**The design.** Social login is enabled for the `customer` role but **explicitly disabled**
for the `admin` role. The `OidcUserService` checks the **resolved local user's** role after
provisioning: if the local role set contains `ROLE_ADMIN`, the login is **rejected** unless
the provider's `hd` claim matches the corporate domain **and** the provider's email is
verified.

**The numbers.** The `hd` check allows `clinic-corp.com` only; the consent screen is
configured in Google Cloud with `clinic-corp.com` as an authorized domain, so a personal
Gmail **cannot complete the consent screen at all** — the provider blocks it before your
code runs. Your own check is the **second** layer. Admin audit events are logged with the
provider, the `hd` claim and the local role set, and reviewed monthly: **1 admin login from
a non-corporate domain** was caught and rejected in the previous quarter.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 A "Login with Google" configuration, the redirect URL, and the printed attributes

**The problem.** Configure the Google login, capture the exact redirect URL, and print the
exact attributes Spring produces for the authenticated principal.

**The configuration.**

```yaml
# application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
            # WHY minimal: 'profile' returns the name, the picture URL and the locale.
            # If the app only needs an email, drop 'profile'.
            scope:
              - openid
              - email
              - profile
            provider: google
```

```java
package com.example.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class GoogleLoginConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/error", "/public/**").permitAll()
                    .requestMatchers("/login/**", "/oauth2/**").permitAll()
                    .anyRequest().authenticated())
            .oauth2Login(oauth2 -> oauth2
                    .defaultSuccessUrl("/orders", true));
        return http.build();
    }
}
```

**The Google Cloud console side.**

```text
   Google Cloud Console → APIs & Services → Credentials → Create OAuth client ID
   ──────────────────────────────────────────────────────────────────────────────
   Application type : Web application
   Name             : orders-web
   Authorized redirect URIs :
       http://localhost:8080/login/oauth2/code/google
       https://app.acme.com/login/oauth2/code/google
       ★ EXACT — no wildcard, no trailing slash
   Authorized domains (the consent screen) :
       app.acme.com
       ★ this is what blocks a personal gmail.com from completing the consent screen
```

**The expected output — the outbound redirect.**

```text
DEBUG o.s.s.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter : Redirecting to authorization request
GET https://accounts.google.com/o/oauth2/v2/auth
    ?response_type=code
    &client_id=104912384719263810231.apps.googleusercontent.com
    &scope=openid%20email%20profile
    &state=6f3a9c2b7e1d4805b3c6f9a2d8e5b1c74
    &redirect_uri=http%3A%2F%2Flocalhost%3A8080%2Flogin%2Foauth2%2Fcode%2Fgoogle
    &code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
    &code_challenge_method=S256
```

**The expected output — the principal, printed.**

```java
@Component
public class PrincipalLogger {

    private static final Logger log = LoggerFactory.getLogger(PrincipalLogger.class);

    @EventListener
    public void onLogin(AuthenticationSuccessEvent event) {
        Authentication auth = event.getAuthentication();
        log.info("login principalClass={} name={} authorities={}",
                auth.getPrincipal().getClass().getSimpleName(),
                auth.getName(),
                auth.getAuthorities());

        if (auth.getPrincipal() instanceof OidcUser oidcUser) {
            log.info("oidc issuer={} audience={} idTokenExpiresAt={} attributes={}",
                    oidcUser.getIssuer(),
                    oidcUser.getAudience(),
                    oidcUser.getIdToken().getExpiresAt(),
                    oidcUser.getAttributes());
        }
    }
}
```

```text
login principalClass=OidcUser name=104912384719263810231
      authorities=[SCOPE_openid, SCOPE_email, SCOPE_profile]
oidc issuer=https://accounts.google.com
     audience=[104912384719263810231.apps.googleusercontent.com]
     idTokenExpiresAt=2026-01-14 09:12:40
     attributes={sub=104912384719263810231, iss=https://accounts.google.com,
                 azp=104912384719263810231.apps.googleusercontent.com,
                 aud=104912384719263810231.apps.googleusercontent.com,
                 email=alice@acme.com, email_verified=true,
                 name=Alice Sharma, given_name=Alice, family_name=Sharma,
                 picture=https://lh3.googleusercontent.com/a/ACg8oc…,
                 locale=en-IN, iat=1744999900, exp=1745000000}
```

**Dry run — step by step.**

1. The browser requests `GET /oauth2/authorization/google`.
2. `DefaultOAuth2AuthorizationRequestResolver` reads the `google` `ClientRegistration`. Its
   `authorizationUri` came from `CommonOAuth2Provider.GOOGLE`; the `client_id` and
   `client_secret` from the configuration.
3. The scope is `openid email profile`; the `state` is 32 random bytes; PKCE is on.
4. Google authenticates the user and shows the consent screen (the first time only).
5. Google redirects back; Spring validates the `state` and exchanges the code.
6. The token response contains an `id_token`. `DefaultOidcUserService` **decodes** it and
   then calls Google's UserInfo endpoint (`https://openidconnect.googleapis.com/v1/userinfo`)
   because `user-info-uri` is configured by the preset.
7. The `OidcUser` is built: `getName()` returns the **`sub`**
   (`104912384719263810231`), and the attributes are the **merged** claim set.
8. The authorities are `SCOPE_openid`, `SCOPE_email`, `SCOPE_profile` — the **default**
   `SCOPE_` mapping of the granted scopes. There are **no** `ROLE_` authorities, because
   Google does not supply roles.
9. `OidcUser.getIssuer()` is `https://accounts.google.com` and `getAudience()` is your
   client id — both are what you assert in an interview when asked "how do you know the ID
   token was meant for you?".

**The takeaway.** The `sub` is the **stable** identifier — note that it is a numeric string,
not the email, and that it does not change if the user renames their Google account. The
email is the **mutable** one. That is why the local key is `(issuer, sub)`.

---

### 8.2 A GitHub login that fetches the primary verified email

**The problem.** Implement a GitHub login where the user's email comes from
`/user/emails` and is always the **primary + verified** address.

**The code.**

```java
package com.example.orders.auth;

import java.util.List;
import java.util.Map;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class GitHubOAuth2UserService extends DefaultOAuth2UserService {

    private final RestClient restClient;

    public GitHubOAuth2UserService(RestClient.Builder builder) {
        this.restClient = builder.build();
    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        // The default service calls GET https://api.github.com/user
        OAuth2User user = super.loadUser(userRequest);

        String email = user.getAttribute("email");
        if (email == null || email.isBlank()) {
            // ★ THE GITUB GOTCHA: /user returns null unless the user has a PUBLIC email.
            //   With the "user:email" scope we can read /user/emails.
            email = fetchPrimaryVerifiedEmail(userRequest);
        }

        if (email == null) {
            // Never guess, never fall back to the login name.
            throw new OAuth2AuthenticationException(
                    "No primary, verified email is available for this GitHub account");
        }

        Map<String, Object> attributes = new java.util.HashMap<>(user.getAttributes());
        attributes.put("email", email);
        attributes.put("email_verified", true);

        return new DefaultOAuth2User(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                attributes,
                "id");       // the user-name-attribute for GitHub
    }

    private String fetchPrimaryVerifiedEmail(OAuth2UserRequest userRequest) {
        String token = userRequest.getAccessToken().getTokenValue();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> emails = restClient.get()
                .uri("https://api.github.com/user/emails")
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .retrieve()
                .body(List.class);

        if (emails == null) {
            return null;
        }
        // ★ NEVER "the first entry" — pick primary AND verified.
        return emails.stream()
                .filter(e -> Boolean.TRUE.equals(e.get("primary")))
                .filter(e -> Boolean.TRUE.equals(e.get("verified")))
                .map(e -> (String) e.get("email"))
                .findFirst()
                .orElse(null);
    }
}
```

**The exact HTTP calls.**

```text
   ① GET https://api.github.com/user
      Authorization: Bearer gho_16C7e42F292c6912E7710c838347Ae178B4a
      Accept: application/vnd.github+json

      HTTP/1.1 200 OK
      {
        "login": "alice",
        "id": 583231,
        "node_id": "MDQ6VXNlcjU4MzIzMQ==",
        "avatar_url": "https://avatars.githubusercontent.com/u/583231?v=4",
        "name": "Alice Sharma",
        "company": "@acme",
        "blog": "",
        "location": "Bengaluru",
        "email": null,                      ← ★ null: no public email set
        "bio": null,
        "public_repos": 42,
        "created_at": "2017-03-14T08:22:11Z"
      }

   ② GET https://api.github.com/user/emails
      Authorization: Bearer gho_16C7e42F292c6912E7710c838347Ae178B4a
      Accept: application/vnd.github+json

      HTTP/1.1 200 OK
      [
        {
          "email": "alice@acme.com",
          "primary": true,
          "verified": true,
          "visibility": "private"
        },
        {
          "email": "alice.sharma@gmail.com",
          "primary": false,
          "verified": true,
          "visibility": "public"
        },
        {
          "email": "alice.old@gmail.com",
          "primary": false,
          "verified": false,                ← ★ unverified — never use
          "visibility": "public"
        }
      ]
```

**The expected log output.**

```text
INFO  c.e.orders.auth.GitHubOAuth2UserService : github /user returned no email — fetching /user/emails
INFO  c.e.orders.auth.GitHubOAuth2UserService : resolved email=alice@acme.com primary=true verified=true
INFO  c.e.orders.auth.GitHubOAuth2UserService : login principalClass=DefaultOAuth2User name=583231
      authorities=[ROLE_USER] attributes={login=alice, id=583231, name=Alice Sharma,
      email=alice@acme.com, email_verified=true, avatar_url=https://avatars.githubusercontent.com/u/583231?v=4}
```

**Dry run — step by step.**

1. `super.loadUser(userRequest)` calls `GET /user` and builds the `OAuth2User`. The `email`
   attribute is **null**.
2. The override detects the null and calls `/user/emails`.
3. The response has **three** entries. The filter selects `primary == true` **and**
   `verified == true` → `alice@acme.com`.
4. The **unverified** `alice.old@gmail.com` is rejected by the `verified` filter; the
   non-primary `alice.sharma@gmail.com` is rejected by the `primary` filter.
5. The attributes are copied and enriched with `email` and `email_verified`, and the
   `DefaultOAuth2User` is returned with the name attribute `id` (the numeric GitHub id —
   **stable**, unlike the login name which the user can change).
6. If the account had **no** primary verified email, the service throws
   `OAuth2AuthenticationException` → the login **fails**, which is the correct behaviour
   (rather than storing a null email and creating a broken account).

**The takeaway.** Three rules, in order of importance: (1) `/user` returning `null` is
**normal**, not an error; (2) the selection is `primary && verified`, never "the first"; and
(3) a failure to resolve a verified email should **reject** the login, not create a
half-initialised account.

---

### 8.3 A Keycloak login with a `JwtAuthenticationConverter` mapping `realm_access.roles`

**The problem.** A resource server must map Keycloak's realm roles into `ROLE_*`
authorities, and the API must be able to prove the mapping works.

**The configuration.**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8081/realms/acme
          jwk-set-uri: http://localhost:8081/realms/acme/protocol/openid-connect/certs
```

**The converter (a compact version of §3.3's).**

```java
package com.example.orders.config;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    private static final Set<String> INTERNAL =
            Set.of("offline_access", "uma_authorization", "uma_protection");
    private static final String THIS_CLIENT = "orders-web";

    private static final Map<String, String> ROLE_MAP = Map.of(
            "orders-reader", "ROLE_ORDER_READER",
            "orders-admin", "ROLE_ORDER_ADMIN",
            "billing-admin", "ROLE_BILLING_ADMIN");

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        return roles(jwt).stream()
                .filter(role -> !INTERNAL.contains(role))
                .map(ROLE_MAP::get)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Set<String> roles(Jwt jwt) {
        Set<String> result = new java.util.LinkedHashSet<>();
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof List<?> list) {
            list.stream().filter(String.class::isInstance).map(String.class::cast)
                    .forEach(result::add);
        }
        Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
        if (resourceAccess != null && resourceAccess.get(THIS_CLIENT) instanceof Map<?, ?> m
                && m.get("roles") instanceof List<?> list) {
            list.stream().filter(String.class::isInstance).map(String.class::cast)
                    .forEach(result::add);
        }
        return result;
    }
}
```

**The expected output — the printed `SecurityContext` authorities.**

```java
@Component
public class AuthorityLogger {

    private static final Logger log = LoggerFactory.getLogger(AuthorityLogger.class);

    @EventListener
    public void onAuthentication(AuthenticationSuccessEvent event) {
        JwtAuthenticationToken token = (JwtAuthenticationToken) event.getAuthentication();
        log.info("subject={} issuer={} audience={} authorities={}",
                token.getToken().getSubject(),
                token.getToken().getIssuer(),
                token.getToken().getAudience(),
                token.getAuthorities());
    }
}
```

```text
subject=f1a2b3c4-5d6e-7f80-9a1b-2c3d4e5f6071
        issuer=http://localhost:8081/realms/acme
        audience=[orders-api]
        authorities=[ROLE_ORDER_READER, ROLE_BILLING_ADMIN]
```

**Note what is *absent*: `ROLE_OFFLINE_ACCESS`, `ROLE_UMA_AUTHORIZATION`, and
`ROLE_ORDERS_ADMIN`.** The first two were filtered as Keycloak internals; the third is a
**client role** (`resource_access.orders-web.roles`) that this mapping deliberately does not
include — the application decided that the client's own roles are not the same thing as the
realm roles it authorises on.

**Dry run — step by step.**

1. The request carries `Authorization: Bearer eyJ…`.
2. `BearerTokenAuthenticationFilter` → `DefaultBearerTokenResolver` extracts the token.
3. `JwtDecoder` resolves the `kid` from the **cached** JWKS, verifies the `RS256` signature
   (pinned), and runs the validators: `iss` (`http://localhost:8081/realms/acme`) ✔,
   `exp`/`nbf` ✔, and the **audience** validator (`orders-api` ∈ `["orders-api"]`) ✔.
4. `JwtAuthenticationConverter` calls `KeycloakRoleConverter`.
5. The converter reads `realm_access.roles` = `["offline_access", "uma_authorization",
   "orders-reader", "billing-admin"]` and `resource_access.orders-web.roles` =
   `["orders-reader", "orders-admin"]`. The union is
   `{offline_access, uma_authorization, orders-reader, billing-admin, orders-admin}`.
6. The `INTERNAL` filter removes `offline_access` and `uma_authorization`.
7. `ROLE_MAP` maps `orders-reader` → `ROLE_ORDER_READER`, `billing-admin` →
   `ROLE_BILLING_ADMIN`, and **`orders-admin` → `null`** (it is not in the map) → filtered
   out by `Objects::nonNull`.
8. The result is `[ROLE_ORDER_READER, ROLE_BILLING_ADMIN]`.

**The takeaway.** The mapping is **explicit and lossy on purpose**. An unknown Keycloak role
produces **no** authority, which means a provider-side rename can never silently escalate a
user — it can only lock them out, which is the safe direction to fail.

---

## 9. Code from Scratch — Production-Grade

### 9.1 A complete social-login configuration with a custom user service

```java
package com.example.orders.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

/**
 * A social-login client that works across 6 horizontally scaled instances with no shared
 * session store.
 */
@Configuration
public class SocialLoginConfig {

    @Bean
    SecurityFilterChain chain(HttpSecurity http) throws Exception {

        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookiePath("/");

        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/", "/error", "/public/**", "/login/**", "/oauth2/**")
                    .permitAll()
                    .anyRequest().authenticated())
            // WHY csrf is ON: the pending authorization request lives in a cookie, so the
            // callback is a cross-site state-changing request.
            .csrf(csrfSpec -> csrfSpec.csrfTokenRepository(csrf))
            .oauth2Login(oauth2 -> oauth2
                    // WHY a customizer for the failure handler: the default renders a
                    // bare error page; we want to log the OAuth error code.
                    .failureHandler((request, response, exception) -> {
                        String error = exception instanceof org.springframework.security.oauth2.core.OAuth2AuthenticationException oae
                                ? oae.getError().getErrorCode()
                                : "unknown";
                        org.slf4j.LoggerFactory.getLogger(SocialLoginConfig.class)
                                .warn("oauth2 login failed: error={} registrationId={}",
                                        error, request.getParameter("code"));
                        response.sendError(401, "Login failed");
                    })
                    .defaultSuccessUrl("/orders", true));
        return http.build();
    }
}
```

### 9.2 An `OidcUserService` that JIT-provisions and links accounts safely

```java
package com.example.orders.auth;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.userinfo.DefaultOidcUserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves the provider's identity to a LOCAL user, and creates one if this is a first
 * login (JIT provisioning).
 *
 * ★ THE THREE LINKING RULES, in order:
 *   1. resolve by (issuer, provider_sub) — the stable key
 *   2. otherwise, a verified email on both sides may link to an existing local user
 *   3. NEVER link automatically to a local account that has a password
 */
@Service
public class AccountLinkingOidcUserService extends DefaultOidcUserService {

    private static final Logger log = LoggerFactory.getLogger(AccountLinkingOidcUserService.class);

    /** Only these email domains may create or link a local account. */
    private static final Set<String> ALLOWED_DOMAINS = Set.of("acme.com", "acme.co.uk");

    private final LocalUserService localUsers;

    public AccountLinkingOidcUserService(LocalUserService localUsers) {
        this.localUsers = localUsers;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OidcUser providerUser = super.loadUser(userRequest);

        String issuer = providerUser.getIssuer();
        String providerSubject = providerUser.getSubject();
        String email = providerUser.getAttribute("email");
        Boolean emailVerified = providerUser.getAttribute("email_verified");

        // RULE 0 — the provider must have verified the email, and it must be an allowed domain.
        if (email == null || email.isBlank()) {
            throw new OAuth2AuthenticationException("The provider did not supply an email");
        }
        if (!Boolean.TRUE.equals(emailVerified)) {
            throw new OAuth2AuthenticationException("The provider has not verified the email");
        }
        String domain = domainOf(email);
        if (!ALLOWED_DOMAINS.contains(domain)) {
            throw new OAuth2AuthenticationException("Email domain is not allowed: " + domain);
        }

        // RULE 1 — the stable key.
        Optional<LocalUser> byProvider = localUsers.findByProviderIdentity(issuer, providerSubject);
        if (byProvider.isPresent()) {
            LocalUser user = byProvider.get();
            localUsers.updateProfile(user, providerUser.getAttributes());
            log.info("oauth2 login event=RETURNING userId={} issuer={}", user.getId(), issuer);
            return withAuthorities(providerUser, user);
        }

        // RULE 2/3 — the email path, which is where the danger is.
        Optional<LocalUser> byEmail = localUsers.findByEmail(email);
        if (byEmail.isEmpty()) {
            LocalUser created = localUsers.createFromProvider(issuer, providerSubject, email,
                    providerUser.getAttributes());
            log.info("oauth2 login event=PROVISIONED userId={} issuer={} email={}",
                    created.getId(), issuer, email);
            return withAuthorities(providerUser, created);
        }

        LocalUser existing = byEmail.get();
        if (existing.hasPassword()) {
            // ★ NEVER auto-link to a password account: the owner may be a different
            //   person who merely shares the address.
            throw new OAuth2AuthenticationException(
                    "An account with this email already exists — sign in with your password "
                    + "and link this provider from your profile settings");
        }
        if (!existing.isEmailVerified()) {
            throw new OAuth2AuthenticationException("The local email is not verified");
        }
        localUsers.attachProviderIdentity(existing, issuer, providerSubject);
        log.info("oauth2 login event=LINKED userId={} issuer={}", existing.getId(), issuer);
        return withAuthorities(providerUser, existing);
    }

    /**
     * WHY the local roles replace the provider's roles: the local database is the
     * authority for what this person may do. The provider's claims only seed the profile.
     */
    private OidcUser withAuthorities(OidcUser providerUser, LocalUser localUser) {
        Collection<GrantedAuthority> authorities = localUser.getRoleNames().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new DefaultOidcUser(authorities, providerUser.getIdToken(),
                providerUser.getUserInfo(), providerUser.getNameAttributeKey());
    }

    private String domainOf(String email) {
        int at = email.lastIndexOf('@');
        return at < 0 ? "" : email.substring(at + 1).toLowerCase(Locale.ROOT);
    }
}
```

### 9.3 A domain allowlist as a reusable rule

```java
package com.example.orders.auth;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AllowedEmailDomains {

    private final Set<String> allowed;

    public AllowedEmailDomains(@Value("${acme.allowed-email-domains}") String csv) {
        this.allowed = Set.of(csv.split(",")).stream()
                .map(d -> d.trim().toLowerCase(Locale.ROOT))
                .filter(d -> !d.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /**
     * @param email          the email the provider supplied
     * @param emailVerified  the provider's email_verified flag
     * @return true only if the email is verified AND the domain is allowed
     */
    public boolean isAllowed(String email, Boolean emailVerified) {
        if (email == null || !Boolean.TRUE.equals(emailVerified)) {
            return false;
        }
        int at = email.lastIndexOf('@');
        if (at < 0) {
            return false;
        }
        return allowed.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }
}
```

### 9.4 A SAML relying-party configuration (for the enterprise case)

```yaml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          azure:
            assertingparty:
              # ★ Spring fetches the metadata and derives the SSO URL, the entity id and
              #   the signing certificate from it. For a high-value integration, pin the
              #   certificate locally instead of trusting the metadata over the network.
              metadata-uri: https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id>
            signing:
              credentials:
                - private-key-location: classpath:saml/sp-signing.key
                  certificate-location: classpath:saml/sp-signing.crt
            registration-id: azure
```

```java
package com.example.orders.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.web.authentication.Saml2WebSsoAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SamlConfig {

    @Bean
    RelyingPartyRegistrationRepository relyingPartyRegistrations() {
        RelyingPartyRegistration azure = RelyingPartyRegistration
                .withRegistrationId("azure")
                .assertingPartyMetadataLocation(
                        "https://login.microsoftonline.com/<tenant-id>/federationmetadata/2007-06/federationmetadata.xml?appid=<app-id>")
                .signingX509Credentials(c -> c.add(
                        org.springframework.security.saml2.core.Saml2X509Credential
                                .signing(Saml2X509Credential
                                        .privateKeyLocation(this.getClass()
                                                .getResource("/saml/sp-signing.key")))))
                .build();
        return new InMemoryRelyingPartyRegistrationRepository(azure);
    }

    @Bean
    SecurityFilterChain samlChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/saml2/**", "/login/saml2/**").permitAll()
                    .anyRequest().authenticated())
            .saml2Login(saml2 -> saml2.relyingPartyRegistrationRepository(relyingPartyRegistrations()));
        return http.build();
    }
}
```

> ⚠️ **A note on the SAML artifact.** `spring-security-saml2-service-provider` is a
> **separate** dependency from the OAuth2 starters, and its API is completely different —
> `RelyingPartyRegistrationRepository` instead of `ClientRegistrationRepository`, an
> `AssertionConsumerService` instead of an `OAuth2UserService`. Do not try to mix the two
> mental models.

---

## 10. Common Mistakes & Gotchas

| ❌ Wrong | ✅ Right | Why it breaks |
|---|---|---|
| Auto-linking a local account by **email** alone | Link only on a **verified** email, and never to a password account | An attacker who registers `victim@acme.com` at a provider takes over the account |
| Creating a **new local user** on every social login | Resolve by `(issuer, provider_sub)` first | The same human ends up with several accounts, and their data splits |
| Using the **email** as the local key | Use `(issuer, sub)`; the email can change | The account breaks when the user changes their provider email |
| Mapping the provider's roles **straight** to authorities | An explicit map; unknown roles → **no** authority | A provider-side rename becomes a privilege escalation |
| Mapping Keycloak's `offline_access` / `uma_authorization` | Filter the provider's internal roles | You grant `ROLE_OFFLINE_ACCESS` to every user |
| Expecting `realm_access.roles` without the `roles` client scope | Attach the **`roles` client scope** to the client in Keycloak | The token simply has no roles, and the mapping produces nothing |
| Reading the roles from `scope` | Read `realm_access` / `resource_access` | The scopes are the OAuth protocol scopes, not the Keycloak roles |
| Treating `OAuth2User` and `OidcUser` as the same type | They both extend `OAuth2AuthenticatedPrincipal`; **neither** extends the other | An OIDC login fails to bind to an `OAuth2User` controller argument |
| Requesting `profile` and storing the picture URL | Request only the scopes you use | The picture URL is a tracking pixel the user can revoke |
| Storing the provider's **access token** | Store the **refresh token** (if you need one); usually store neither | A live third-party credential in your database that you cannot revoke |
| Configuring the redirect URI with a trailing slash or a wildcard | Register the **exact** URI | The AS rejects the request, or — worse — a lax match enables code theft |
| Assuming social login works without the `user:email` scope | Request `read:user user:email` for GitHub | `/user/emails` returns 401/403 without it |
| Selecting the **first** email from `/user/emails` | Select `primary && verified` | You may pick an unverified or secondary address |
| Using the ID token at your own API | Send the **access token** | The ID token's audience is the client — a confused-deputy bug |
| Disabling CSRF for a cookie-based callback | Keep CSRF **on** | The callback is a cross-site state-changing request |
| Enabling Keycloak's **Direct Access Grants** | Leave it off | It is the resource-owner-password grant, removed in OAuth 2.1 |
| Expecting the provider's logout to end your session | Destroy your own session too | The user stays logged into **your** app |
| Trusting `hd` as a security control | Use it as a hint; enforce the domain yourself | It is the provider's claim, not your policy |

---

## 11. Performance Considerations

**Where the time goes in a social login.**

```text
   LEG 1 — browser → provider login page        150–600 ms   ★ the biggest cost
   LEG 2 — provider → browser redirect            1–5 ms
   LEG 3 — browser → your app callback            1–5 ms
   LEG 4 — your app → provider token endpoint    10–80 ms
   LEG 5 — your app → provider userinfo          10–80 ms
   LEG 6 — your app → GitHub /user/emails        10–80 ms   (the extra call)
   LEG 7 — your app → your database               2–15 ms   (the local user lookup)
   ────────────────────────────────────────────────────────────────
   SERVER-SIDE TOTAL (4+5+6+7)                    32–255 ms
   USER-PERCEIVED (includes the provider page)    300–1,200 ms
```

**The optimisation that matters most: skip the userinfo call.** For an OIDC provider, the ID
token already carries the claims you need. In Spring:

```java
http.oauth2Login(oauth2 -> oauth2
        .userInfoEndpoint(userInfo -> userInfo
                .oidcUserService(new DefaultOidcUserService()) {
                    @Override
                    public OidcUser loadUser(OAuth2UserRequest userRequest) {
                        // Overriding and NOT calling super would skip the userinfo call,
                        // but the cleanest way is a service that only reads the ID token.
                        return super.loadUser(userRequest);
                    }
                }));
```

More practically: implement an `OidcUserService` that builds the user **from the ID token
only** and does not call the userinfo endpoint. That removes **leg 5** — 10–80 ms and one
connection to the provider per login. At **1900 logins/min** (the payroll-day peak) that is
1,900 fewer provider calls per minute.

**The GitHub `/user/emails` call is unavoidable — but it is conditional.** Only make it when
`/user` returned no email, which is the majority of accounts. The cached variant (the
provider's `ETag` + `If-None-Match`) can save the body, but not the round trip.

**The local-user lookup.** Every login does at least one database query. At **12,000 DAU**
with a **1.8 logins per user per session**, that is ~**22,000 lookups/day** — trivial. But
the lookup is on the **login path**, so it must be indexed:

```sql
-- The lookup MUST be indexed, or a busy login page becomes a slow login page.
CREATE UNIQUE INDEX ux_provider_identity ON provider_identity (issuer, provider_subject);
CREATE UNIQUE INDEX ux_local_user_email  ON local_user (email);
```

**The provider's consent screen — a cache-buster.** Google and GitHub only show the consent
screen the **first** time (or when you change the scopes). Every scope change re-triggers
it for every user — a rollout that adds `profile` to `openid email` will show **every
existing user** a consent screen on their next login. Plan for that.

**What to measure.**

| Metric | Why |
|---|---|
| `oauth2.login.count{provider}` | Logins per provider — the load on the provider |
| `oauth2.login.duration{provider}` (p50/p99) | Separates the provider's page from your back channel |
| `oauth2.userinfo.calls.count` | Should be **0** if you use the ID token only |
| `oauth2.user.emails.calls.count` | The GitHub extra call — should be bounded by the % of private-email accounts |
| `oauth2.login.provisioned.count` / `.linked.count` | **The identity-resolution metrics** — a spike in `linked` is a signal |
| `oauth2.login.rejected.count{reason}` | Domain-not-allowed, email-not-verified, local-account-exists |
| `local_user.lookup.duration` | The database hit on the login path |
| `provider_identity.duplicate.count` | Should be **0** — a non-zero value means the linking logic is broken |

**Tuning knobs.**

- `spring.security.oauth2.client.registration.<id>.scope` — **the** biggest lever: every
  scope you drop removes claims, removes consent friction and reduces PII.
- The `user-info-uri` — remove it (via a custom service) to skip the userinfo call.
- `ClientSettings.requireProofKey` — keep it `true`.
- The `AuthorizationRequestRepository` — cookie for a stateless SPA, session for a
  server-side app.
- The `email_verified` and domain checks — these are **policy**, and they are evaluated on
  every login (a `Set` lookup — free).

---

## 12. Security Considerations

### 12.1 The threat model for social and enterprise login

| Threat | Attack path | Defence |
|---|---|---|
| **Account takeover via email** | The attacker registers the victim's email at a permissive provider and logs in | Link only on a **verified** email; never auto-link to a **password** account; enforce the domain allowlist |
| **Unverified provider email** | The provider returns an email it has not verified | Check `email_verified`; reject |
| **Personal account on a corporate tenant** | An admin signs in with a personal Gmail | Enforce the domain at the **provider** (Google's authorized domains) **and** in code; disable social login for privileged roles |
| **Role injection** | A provider lets a user influence a role claim | An explicit role map; unknown roles → **no** authority; the local DB is the permission model |
| **Scope creep / over-collection** | The app requests `profile` and stores the picture | Request the **minimum** scope; do not persist what you do not use |
| **Provider token leakage** | The provider's access token is stored and later leaked | Usually do not store it; store only the refresh token, hashed/encrypted, if needed |
| **Open redirect / code theft** | A lax `redirect_uri` match | **Exact** matching; OAuth 2.1 mandates it |
| **Login CSRF** | The victim's browser is bound to the attacker's provider account | **`state`** — validated (chapter 02) |
| **ID token replay** | A captured ID token is replayed into another session | **`nonce`** — validated |
| **Provider account takeover** | The attacker takes over the provider account itself | Your second factor, your session-risk checks, your audit trail |
| **Mass assignment** | Provider attributes are bound straight onto your entity | Map **explicitly**; never auto-bind |
| **Duplicate local users** | Every provider creates a separate account | Resolve by `(issuer, sub)`; link deliberately |
| **Provider outage locks out everyone** | The provider is the only way in | Keep a password fallback; make the provider adapter resilient |
| **Consent fatigue** | A broad consent screen trains users to click through | Minimise the scopes; be specific in the screen |
| **JWT confusion** | An **ID token** is replayed at your API | Validate `aud`; the ID token's audience is the client |
| **SAML signature wrapping** | A malicious SAML response wraps a valid assertion | Spring's `Saml2WebSsoAuthenticationFilter` validates the signature and rejects wrapping — do not disable it |
| **Unaudited identity changes** | Accounts are created and linked with no record | Log the **issuer**, the **event type** (RETURNING / PROVISIONED / LINKED / REJECTED) and the outcome |

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

> 📌 **The OAuth login statuses in practice.** A failed social login — a domain-not-allowed
> rejection, an unverified email, a local-account-exists case — is an **authentication**
> failure and should be **401** with a generic message; revealing *which* check failed is an
> oracle. A user who is authenticated but whose role does not permit the action is **403**.
> A provider that is down is **503** from your application's point of view — the dependency
> is unavailable, not the user's credential. And a rate-limited provider should surface as
> **429** with a `Retry-After`.

### 12.4 The rules that are not negotiable

1. **Resolve identity by `(issuer, sub)`** — never by email alone.
2. **Link only on a verified email**, and **never** auto-link to a local account with a
   password.
3. **Enforce the domain allowlist in your code**, and at the provider where you can.
4. **Map provider roles explicitly**, drop unknown roles, and filter the provider's
   internal roles.
5. **Request the minimum scopes**, and do not persist claims you do not use.
6. **Store the refresh token, not the access token** — and if you store the refresh token,
   encrypt it (it must be presented back) rather than hashing it.
7. **Validate `state` and `nonce`**, and use an **exact** `redirect_uri`.
8. **The local database is the permission model** — the provider's roles are an input.
9. **Log every authentication event** with the issuer and the event type.
10. **Disable social login for privileged roles**, or require the provider's corporate
    domain for them.

---

## 13. Best Practices

**Provider integration**

1. **One `ClientRegistration` per provider**, named by the `registrationId` — and use it as
   the key when you need to know "which provider did this user come from?".
2. **Configure the provider with `issuer-uri`** (OIDC) so the endpoints are discovered.
3. **Request the minimum scopes**, and document why each one is needed.
4. **Handle the provider's shape explicitly** — a per-provider `OAuth2UserService` /
   `OidcUserService` is cleaner than one generic mapper with `if (provider.equals(...))`.
5. **Fail loudly when the identity cannot be resolved** — reject the login rather than
   create a half-initialised account.

**Identity resolution**

6. **Key by `(issuer, provider_sub)`** and make it a **unique index**.
7. **Never** auto-link to a local account that has a password.
8. **Check `email_verified`** on both sides.
9. **Enforce the domain allowlist**, and enforce it at the provider too.
10. **Log the event type** — RETURNING / PROVISIONED / LINKED / REJECTED — and alert on a
    spike in LINKED.

**Authorisation**

11. **An explicit role map**, with unknown roles producing **no** authority.
12. **Filter the provider's internal roles** (`offline_access`, `uma_authorization`,
    `uma_protection`).
13. **Your database is the permission model**; the provider's roles only seed it.
14. **Keep the two authority paths in sync** — the client flow (`GrantedAuthoritiesMapper`)
    and the resource-server flow (`JwtAuthenticationConverter`) must produce the same
    authorities, or the roles will "work on the login page and not in the API".

**Security**

15. **Do not store the provider's access token** unless you actively call the provider's
    API with it.
16. **Do not store the picture URL** unless you display it, and never as an identifier.
17. **Exact `redirect_uri`** per environment, never a wildcard.
18. **Disable CSRF only for the specific endpoint that genuinely needs it** — and be aware
    that the SAML response arrives in a cross-site POST.
19. **Redact `code`, `SAMLResponse` and `state`** from the access log.
20. **Disable social login for admin accounts**, or require a corporate `hd`.

**Operations**

21. **Watch the provider's consent screen** — adding a scope re-prompts every user.
22. **Keep a password fallback** so a provider outage does not lock out every user.
23. **Monitor per-provider login latency** — a provider's degradation is your degradation.

---

## 14. Hands-On Exercises & Code Challenges

### 14.1 Hands-on exercises

**Exercise 1 — Implement the GitHub email resolution.**

1. Register a GitHub OAuth app with the `read:user user:email` scopes.
2. Use a GitHub account **without** a public email so `/user` returns `email: null`.
3. Implement an `OAuth2UserService` that fetches `/user/emails` and picks `primary && verified`.
4. **Acceptance criteria:** the login succeeds and the local user record contains
   `alice@acme.com` (not the login name, not the first entry); a test with a
   `MockRestServiceServer` proves the extra call happens **only** when `/user` has no email;
   and a test with only an unverified primary email proves the login is **rejected**.

**Exercise 2 — Map Keycloak's roles and prove it.**

1. Start Keycloak (`docker run -p 8081:8080 … quay.io/keycloak/keycloak:25.0 start-dev`),
   create the realm `acme`, and assign the client scope `roles` to the client `orders-web`.
2. Give a test user the realm roles `orders-reader` and `offline_access`.
3. Implement a `JwtAuthenticationConverter` with a custom role converter.
4. **Acceptance criteria:** the printed authorities are exactly
   `[ROLE_ORDER_READER]` — **`ROLE_OFFLINE_ACCESS` is absent**, and an unknown role produces
   **no** authority.

**Exercise 3 — Write the domain allowlist rule.**

1. Implement an `AllowedEmailDomains` component backed by a property.
2. Write it so that it checks `email_verified` **and** the domain.
3. **Acceptance criteria:** a test asserts `alice@acme.com` (verified) is allowed,
   `alice@gmail.com` is rejected, `alice@acme.com` with `email_verified=false` is rejected,
   and `alice@ACME.COM` (uppercase) is allowed — because the comparison is
   case-insensitive.

### 14.2 Code challenges

**Challenge 1 — A safe JIT-provisioning `OidcUserService`.**

> Implement an `OidcUserService` that resolves the provider's identity to a local user,
> provisions on first sight, and links only under the three rules.

**Acceptance criteria:**
- [ ] The primary lookup is by **`(issuer, provider_sub)`**.
- [ ] A first login **creates** a local user with a default role and persists the provider
      identity.
- [ ] A returning login **updates the profile** but does **not** touch the local roles.
- [ ] A local account with a **password** is **never** auto-linked — the service throws an
      `OAuth2AuthenticationException` with a message telling the user to link explicitly.
- [ ] An **unverified** provider email is rejected.
- [ ] A domain outside the allowlist is rejected.
- [ ] Every path logs the **event type**: `RETURNING`, `PROVISIONED`, `LINKED` or
      `REJECTED`.
- [ ] Unit tests cover all four event types.

**Stretch goal:** add a **link-token** flow — the user logs in with the password, receives a
short-lived link token, and completes the provider link with it.

**Challenge 2 — A provider-agnostic role mapper.**

> Implement one mapper that turns a provider's role claims into Spring authorities, and that
> works for Keycloak, Okta and Entra ID.

**Acceptance criteria:**
- [ ] The claim **location** is per-provider (Keycloak: `realm_access` /
      `resource_access.<client>`; Okta: `groups`; Entra: `roles`).
- [ ] The **mapping table** is shared and explicit.
- [ ] An **unknown** role produces **no** authority.
- [ ] The provider's **internal** roles (`offline_access`, `uma_authorization`,
      `uma_protection`) are filtered.
- [ ] A test asserts that a token with `scope=orders:read` and a token with
      `realm_access.roles=["orders-reader"]` produce the **same** authority set.
- [ ] The mapper is unit-testable without Spring.

**Stretch goal:** add a **fallback** — when the provider supplies no roles at all, fall back
to a single default role, and log a warning so the misconfiguration is visible.

**Challenge 3 — A provider-agnostic audit log.**

> Implement an audit trail for every authentication event.

**Acceptance criteria:**
- [ ] Each event records: the issuer/registration id, the provider subject, the local user
      id, the event type, the `email_verified` flag and the outcome.
- [ ] The event is emitted for **every** outcome: success, provisioning, linking, rejection.
- [ ] The audit record contains **no** token, code, secret or PKCE verifier.
- [ ] The local user id is **not** derivable from the provider subject alone — the two are
      stored as separate fields.
- [ ] A test asserts that a rejection event is emitted when the domain is not allowed.
- [ ] A metric counter is incremented per event type.

**Stretch goal:** make the event stream append-only and tamper-evident (a hash chain).

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q:** What is "login with Google" technically?
**A:** It is the **OAuth 2.0 Authorization Code flow** (with PKCE) against Google as the
authorization server. Your app redirects the browser to Google, Google authenticates the user
and redirects back with a code, your app exchanges the code for tokens, and it reads the
user's identity from the **ID token**. Nothing about it is special — the only difference from
a generic OIDC provider is the endpoint URLs and the claim names.

**Q:** What is the difference between `OAuth2User` and `OidcUser`?
**A:** `OAuth2User` is built from the **UserInfo endpoint** response and is used with plain
OAuth 2.0 providers such as GitHub. `OidcUser` is built from the **ID token** and is used
with OIDC providers such as Google, Keycloak and Okta. Both extend
`OAuth2AuthenticatedPrincipal` — **neither extends the other**, so a controller that takes an
`OAuth2User` will not bind an OIDC login.

**Q:** Why does GitHub sometimes return a null email?
**A:** Because GitHub's `/user` endpoint only includes the email when the user has made one
**public** in their settings. The fix is the `user:email` scope, which grants access to
`/user/emails` — and from that list you must pick the entry that is both **`primary`** and
**`verified`**.

**Q:** What is Keycloak?
**A:** An open-source **identity and access management** server that implements OAuth 2.0,
OpenID Connect and SAML. The key concepts are the **realm** (an isolated set of users and
clients), the **client** (your application), the **client scopes** (what a client may ask
for), and the **roles** — which appear in the token under `realm_access.roles` and
`resource_access.<client>.roles`.

### 🟡 Intermediate

**Q:** How do you map Keycloak's roles to Spring authorities?
**A:** With a custom `Converter<Jwt, Collection<GrantedAuthority>>` wired into a
`JwtAuthenticationConverter`. You read `realm_access.roles` and
`resource_access.<client>.roles` from the token, **filter out** Keycloak's internal roles
(`offline_access`, `uma_authorization`, `uma_protection`), and pass what remains through an
**explicit mapping table**. An unknown role must produce **no** authority — renaming it
would turn a provider-side rename into a privilege escalation.

**Q:** Why is there no role mapping in the default `JwtGrantedAuthoritiesConverter`?
**A:** Because the default reads the **`scope`** claim and produces `SCOPE_`-prefixed
authorities. `scope` is the OAuth protocol's permission list — Keycloak's *roles* are a
different concept and live in a different claim. So the default maps **none** of them, and
you get an authenticated user with no application roles.

**Q:** What is JIT provisioning?
**A:** **Just-in-time provisioning**: on the first login from a provider, your application
creates the local user record automatically rather than requiring a pre-registration. The
practical rules are that the provider's identity must be **verified**, the email must be
**verified**, the domain must be on your allowlist, and the new user must get only a
**default role** — never elevated privileges.

**Q:** How should you link a provider account to an existing local account?
**A:** Match on the **verified email** on both sides, and **never** auto-link to a local
account that has a **password** — that account's owner may be a different person who merely
shares the address. The safe pattern is to require the user to log in with their password
and then link the provider explicitly from the profile page.

### 🔴 Advanced / Senior

**Q:** How do you prevent account takeover through social login?
**A:** Three controls, in order of importance. (1) **Never** auto-link on an **unverified**
email — check the provider's `email_verified`. (2) **Never** auto-link to a local account
that has a password; require an explicit, authenticated linking step. (3) **Enforce a domain
allowlist** — and enforce it at the provider too, so a personal Gmail cannot complete the
consent screen for a corporate client at all. On top of that, resolve identity by
`(issuer, sub)` rather than by email, so an email change never creates a duplicate
account.

**Q:** A social login is failing for some users. How do you debug it?
**A:** Turn on `logging.level.org.springframework.security.oauth2=DEBUG` and read the two
things it prints: the outbound authorization request (the `scope`, the `state`, the
`code_challenge`) and the provider's response. The usual causes are a **scope** the provider
does not grant (a `invalid_scope` at the token endpoint), an **unregistered redirect URI**
(the AS rejects the authorization request outright), or a **provider email** that is `null`
or unverified — which your own policy rejects. The provider's own error page is usually the
fastest diagnostic: it names the exact failure.

**Q:** How do you support both Keycloak roles and your own database roles?
**A:** Treat them as **two inputs to one decision**, not as two systems. The provider's roles
map into a **small set of bootstrap roles** — for example `orders-reader` →
`ROLE_ORDER_READER` — and your own database holds the **fine-grained permissions** that
you resolve at the edge against the cached permission set. The rule that keeps this sane:
**your database is the permission model**; the provider's roles are a convenient input that
can *seed* it, never a substitute for it.

**Q:** Why would you use SAML when OIDC exists?
**A:** Because the **enterprise IdP only speaks SAML**. Azure AD, Okta, Ping, ADFS and most
legacy vendors ship SAML, and some ship SAML only. The right answer for a mixed estate is
not to implement SAML in your application but to put a **broker** in front: Keycloak speaks
SAML to the legacy IdP and OIDC to your applications, so your code only ever handles OIDC.
The trade-off of SAML directly in your app is that the assertion travels through the browser
as an XML form POST — it is larger than a JWT, it goes through the front channel, and its
XML parsing has a long CVE history — which is why Spring ships it as a separate artifact
with a separate API.

### ⚫ Expert / Principal

**Q:** Design the identity layer for a marketplace with 12,000 tenants.
**A:** One **platform issuer** (Keycloak or Spring Authorization Server), with a **realm per
tenant** and per-tenant OIDC clients, and **SAML federation** inside each realm for the
tenants that only speak SAML. Every token carries a **`tenant_id` claim**, and every API
validates it against the tenant resolved from the **Host** — server-side, never from a
client-supplied header. The local user identity is keyed by **`(issuer, external_subject)`**,
with a `membership` table holding the per-tenant role. Users can also sign in with social
providers, but those accounts are only auto-linked on a **verified** email and a
domain-allowlisted tenant. The trade-offs: one issuer concentrates blast radius but gives
one key set and one rotation runbook; `tenant_id` in every token grows the payload slightly
but makes the boundary machine-checkable; and the real risk is not the token but a
**repository query that forgets the tenant filter** — which is why the tenant predicate
belongs in the repository base class, not in each query.

**Q:** How do you migrate 12,000 tenants from local passwords to enterprise SSO without
locking anyone out?
**A:** In four phases, each independently reversible. **Phase 1 — add, do not replace:** the
tenant can enable SSO, but password login still works; log every SSO login with the tenant
and the provider. **Phase 2 — verify and link:** on the first SSO login, link the provider
identity to the existing local account **only** when the verified emails match on both
sides; where they do not match, route the user to an **admin-assisted link** (a support
workflow, with an audit trail) rather than guessing. **Phase 3 — require step-up:** for
privileged roles, require the password *once more* before the SSO link is trusted, and record
it. **Phase 4 — enforce:** after a grace period, disable password login for that tenant and
keep an **emergency break-glass account** that is not federated. The critical design point is
the **break-glass path** and the **link-conflict queue** — those two are what make the
migration safe, and both are operational rather than technical.

**Q:** A provider has a 4-hour outage. What is the impact and what do you do?
**A:** Every social login fails; **existing sessions are unaffected** — they are your own
sessions, validated locally, and the provider's outage has no effect on them. Only *new*
logins and *refreshes* break, so the impact is proportional to your session length: with a
**30-minute** session TTL, a 4-hour outage logs out every user who has not logged in during
it. The mitigations, in order of preference: (1) keep a **password fallback** so users are
never fully locked out; (2) **extend the session TTL** for existing sessions — and *only* for
existing sessions, because the risk of a longer-lived session is bounded by what the session
already grants, whereas extending the *refresh* window is not; (3) for a **SAML** provider,
use the metadata-pinned configuration so a metadata endpoint outage does not break the
**existing** assertion validation; and (4) **fail gracefully** — show a clear "identity
provider is unavailable, try again in a few minutes" rather than a stack trace. What you
must **not** do is fall back to a less-secure path — accepting an unverified email, or
trusting the provider's claim without validation.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "Users who signed up with a password now have a second, empty account"

**The situation.** A customer support ticket: *"I logged in with Google and all my data is
gone."* The customer originally signed up with a password in 2023, has 400 orders, and then
clicked "Continue with Google" last week — and got a brand-new account with zero orders.

**Your first move.**

```bash
# 1. Find the two local users.
psql -c "select id, email, created_at, provider_id
        from local_user
        where email in ('alice@acme.com');"

# 2. What does the provider identity table say?
psql -c "select * from provider_identity where email = 'alice@acme.com';"

# 3. How many users are affected?  (this is the real question)
psql -c "select count(*) from provider_identity pi
         join local_user lu on lu.id = pi.local_user_id
         where pi.created_at > now() - interval '30 days';"
```

**Expected signal.** There are **two** `local_user` rows with the same email: the original
(id 1043, created 2023, 400 orders, `provider_id` null) and a new one (id 9982, created last
Tuesday, 0 orders, `provider_id` = the Google sub). The `provider_identity` table points at
the **new** row.

The root cause is in the `OidcUserService`: the lookup is **by email**, and when it finds a
local user it **returns it** — but the *link* was never persisted, so the next login creates
a **new** row again. Or, in the other common variant, the lookup is **only** by
`(issuer, sub)`, so an email-matched local account is never found and a new one is
provisioned every time.

**The trap.** "Just merge the two rows in the database." That fixes the symptom for this
customer and leaves the bug in place — the next login for any user creates a new row again.

**The resolution.** (1) Fix the service: resolve by `(issuer, sub)`; if not found, look up
by email; if a local account is found **and it has no provider identity**, **attach** the
provider identity to **that** row — do not create a new one. (2) Write a **migration** that
merges the duplicate accounts: move the orders, the addresses and the preferences to the
**older** row, keep the newer row's `provider_identity`, and mark the newer row as
**merged** (do not delete it — you need the audit trail). (3) Add a **unique index** on
`local_user.email` so the duplicate cannot be created again, and a **monitor** for
conflicts. (4) Ship a regression test that logs in with a password-created account and then
with a provider and asserts the same `local_user_id` is returned.

**The lesson.** Identity resolution is a **data model**, not a code path. The provider
identity must be a **first-class record attached to the local user**, not something
recomputed on every login.

---

### Scenario 2 — "The Keycloak roles are not showing up in the API"

**The situation.** After moving the API to bearer-token authentication, the role-based
endpoints started returning **403**. The roles work in the web app (the login page shows the
right menu) but the API says the user has no roles.

**Your first move.**

```bash
# 1. Decode the token and LOOK at the claims.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{scope, realm_access, resource_access}'

# 2. What does the AS actually issue for this client?
#    Keycloak → realm acme → Clients → orders-web → Client scopes
#    Look for "roles" under Default client scopes.

# 3. What does the API's converter read?
grep -rn "setJwtGrantedAuthoritiesConverter\|JwtGrantedAuthoritiesConverter\|realm_access" src/main/java/
```

**Expected signal.** The decoded token shows:

```json
{ "scope": "openid profile email",
  "realm_access": { "roles": ["offline_access", "uma_authorization"] },
  "resource_access": { "account": { "roles": ["manage-account", "view-profile"] } } }
```

Two findings: (a) the client's **`roles` client scope is not assigned** — the only realm
roles present are Keycloak's own two internal ones, and the user's actual role
`orders-reader` is **missing**; and (b) the API's converter is the **default**
`JwtGrantedAuthoritiesConverter`, which reads `scope` — so even if the roles were present,
they would be ignored.

**The trap.** "The roles are in the token, so the converter must be reading them." They are
**not** in the token — the `roles` client scope is not attached to the client. And even when
they are, the default converter reads a **different claim**.

**The resolution.** (1) In Keycloak, add the **`roles` client scope** to the client's
**default client scopes** (or make it an optional scope you request). (2) In the API,
replace the default converter with the custom `KeycloakRoleConverter` that reads
`realm_access.roles` and `resource_access.<client>.roles`, filters the internal roles and
maps the rest through the explicit table. (3) Remember the **client-flow** side: the web
app's `GrantedAuthoritiesMapper` produced the right authorities — the two paths are
**separate code** and must be kept in sync. (4) Add a test that decodes a fixture token and
asserts the expected authority set.

**The lesson.** "The roles work in the login page but not in the API" is almost always one
of two things: the **claim is not in the token**, or the **converter reads a different
claim**. Decode the token first; do not guess.

---

### Scenario 3 — "A security researcher says they can take over any customer account"

**The situation.** A bug bounty report: *"Using social login, an attacker can log in as any
customer whose email address we know, by registering that address at the provider."* The
reproduction: register `victim@acme.com` at a permissive social provider, click "Continue
with <provider>", and the application logs the attacker in as the victim.

**Your first move.**

```bash
# 1. Reproduce: log in with a provider account whose email is NOT verified.
#    Look at the claims the application received.
grep -o 'email_verified[^,]*' /var/log/app/oauth2-claims.log | tail -20

# 2. What does the identity resolution do with an unverified email?
grep -rn "email_verified\|emailVerified\|isVerified" src/main/java/

# 3. What does the linking path do?
grep -rn "findByEmail\|LINK\|linkProvider" src/main/java/
```

**Expected signal.** The claims log shows `"email_verified": false` for the attacker's
account, and the code has **no** `email_verified` check. The linking path is
`findByEmail(email)` → found → attach the provider identity → log in. There is **no**
verification check and **no** check for whether the local account has a password.

**The trap.** "Add the domain allowlist." That would block the attack only if the victim's
email is not on the allowlist — but the victim's email **is** on the allowlist (it is your
own domain), and the attacker registered it at the provider. The allowlist does not help
here at all; the **verification** check does.

**The resolution.** (1) **Require `email_verified == true`** before any linking or
provisioning — reject otherwise. (2) **Never auto-link to a local account with a password**
— require an explicit, authenticated link. (3) Enforce the domain allowlist **and** the
provider's corporate-domain configuration, so that a personal account cannot complete the
consent screen at all. (4) **Audit the existing accounts**: find every provider identity
linked to a local account whose email was unverified at link time — those are **potentially
already-compromised accounts**, and they need a forced re-authentication. (5) Turn on the
`PROVISIONED` / `LINKED` / `REJECTED` event logging, and alert on a spike in `LINKED`.

**The lesson.** An email address is **not** an identity claim until someone has verified
it. The provider telling you "this is the email" is not the same as "this person controls
this mailbox" — and only the second one is safe to link an account to.

---

### Scenario 4 — "Social login is slow, and the provider says it is not their fault"

**The situation.** Login latency has gone from **380 ms** p50 to **1,900 ms** p50. Google
reports all systems green. The team suspects the network.

**Your first move.**

```bash
# 1. Where is the time? Time each leg from the application's own logs.
grep 'oauth2' app.log | grep -o 'providerPageMs=[0-9]*\|tokenExchangeMs=[0-9]*\|userinfoMs=[0-9]*\|dbLookupMs=[0-9]*' \
  | awk -F= '{sum[$1]+=$2; n[$1]++} END {for (k in sum) printf "%-20s avg=%d\n", k, sum[k]/n[k]}'

# 2. What are the per-leg p95s?  (the p50 can hide a bimodal distribution)
curl -s -o /dev/null -w 'dns=%{time_namelookup} tcp=%{time_connect} tls=%{time_appconnect} ttfb=%{time_starttransfer} total=%{time_total}\n' \
  https://accounts.google.com/o/oauth2/v2/auth

# 3. Is the userinfo call happening?
grep -c 'openidconnect.googleapis.com' app.log
```

**Expected signal.** The breakdown is unambiguous:

```text
providerPageMs      avg=1180    ← the provider's login page
tokenExchangeMs     avg=42
userinfoMs          avg=88
dbLookupMs          avg=11
```

The provider's **login page** dominates. And the userinfo call is still happening —
**88 ms** that could be removed by using the ID token only. The `curl` timing shows
`ttfb=1140ms` for the Google authorization page itself, so the provider **is** slow — their
status page just does not cover it.

**The trap.** "Let's add a timeout and retry." That turns a slow login into a slow login
plus a duplicate page load. And "the network" is unfalsifiable without per-leg timing.

**The resolution.** (1) **Remove the userinfo call** — build the user from the ID token
only. That saves **88 ms** on every login and one provider connection. (2) Consider **SAML
or a self-hosted IdP** for the corporate tenant if the provider's latency is a contractual
problem — with a broker (Keycloak) in front, the *login page* is served by your own IdP and
the latency is under your control. (3) Set a **connect timeout of 3 s and a read timeout of
10 s** on the provider calls, and render a clear error rather than hanging. (4) Track the
per-leg p95 separately — the p50 hid this.

**The lesson.** "Login is slow" must be decomposed into **legs** before anyone optimises.
And a provider's status page is not a measurement of *your* login path.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Why not just use the email address as the user's primary key everywhere?"

- **Naive answer:** "Because emails can change."  ← *true, and it misses the real danger*
- **The trap:** The trap is thinking the only problem is that emails change. The far bigger
  problem is that an email address is **not a verified identity claim** — the provider
  supplies it, and a provider account can be registered with an email it has not verified.
  Keying on the email means the key's *provenance* is "something a provider said".
- **Expert answer:** The correct key is **`(issuer, external_subject)`** — the provider's
  stable, opaque identifier for the human. It does not change when the user renames their
  account, and it is issued by the provider, not derived from anything the user can type.
  The email is a **mutable, unverified-at-first** attribute — useful for *display* and for
  *notification*, and dangerous as a key. The standard shape is exactly that: the
  `(issuer, sub)` pair is the identity, the email is a **profile attribute** that is updated
  when the provider says so, and the local `user_id` is the key everything else uses.

### CQ2. "Is the domain allowlist enough to stop someone registering your customer's email?"

- **Naive answer:** "Yes — they would have to use an acme.com address."  ← *false, and it is the exact attack*
- **The trap:** The trap is that an attacker **can** register `victim@acme.com` at a
  permissive provider. Whether they can depends on the provider, not on your allowlist. If
  your allowlist contains `acme.com` — and it must, because your employees use it — then the
  attacker's registration of `victim@acme.com` **passes your allowlist**.
- **Expert answer:** No. The domain allowlist filters on the *string after the `@`*, and the
  attacker controls that string by registering the address at the provider. The allowlist is
  a **useful second filter**, but the control that actually stops the attack is
  **`email_verified`**: a permissive social provider will let you register an address you do
  not own, and it will mark that address **unverified** — so checking the flag rejects the
  attacker's registration while accepting a genuine corporate one. The strongest version
  combines three layers: the provider's own **corporate-domain restriction** (Google's
  "authorized domains" makes a personal Gmail unable to complete the consent screen at all),
  your **`email_verified`** check, and your **domain allowlist**.

### CQ3. "Should the application's roles come from the provider or from the database?"

- **Naive answer:** "From the provider — that's the whole point of SSO."  ← *true for a small app, wrong for a growing one*
- **The trap:** The trap is assuming the provider's role model and your business's role model
  are the same thing. They are not: a provider's roles are a **flat list of strings** that
  reflects the customer's *directory*, while your business needs **fine-grained permissions**,
  often **per tenant**, often **time-bounded** (a temporary elevated role), and often
  **composite** (a permission that depends on the resource, not the user).
- **Expert answer:** Use the provider's roles as an **input**, never as the model. The
  production pattern is: map the provider's roles onto a **small set of bootstrap roles**
  (`orders-reader` → `ROLE_ORDER_READER`), and resolve the **fine-grained permissions**
  from your own database, keyed by the local `user_id`. This keeps the provider
  integration **stable** (a customer's directory rename is a mapping-table change, not a
  schema change) and keeps the permission model **yours**. The rule of thumb: the provider
  answers *"who is this person?"* and *"what is their coarse group?"*; your database answers
  *"what may they do, on which tenant, until when?"*.

### CQ4. "Why is `email_verified` sometimes a string instead of a boolean?"

- **Naive answer:** "It's a bug in the provider."  ← *sometimes true, and it misses the trap*
- **The trap:** The trap is writing `if (emailVerified)` — which in Java **compiles** for a
  `Boolean` but throws a `NullPointerException` on a null unboxing, and for a `String`
  `"false"` it is **truthy**. A JSON boolean parsed into a `String` field, or a provider
  that sends `"email_verified": "true"` as a string, will silently pass a `!= null` check.
- **Expert answer:** It happens because OIDC providers are inconsistent, and some send the
  claim as a string because JavaScript's `true`/`false` and the JSON spec disagree in some
  serialisers. The robust check is explicit and total:

```java
private static boolean isVerified(Object raw) {
    if (raw instanceof Boolean b) {
        return b;
    }
    if (raw instanceof String s) {
        return "true".equalsIgnoreCase(s.trim());
    }
    return false;
}
```

Note that the `String` branch treats the literal `"false"` as **not** verified — the naive
`Boolean.parseBoolean` also does this, but `"yes"` or `"1"` would be **false** under
`parseBoolean` while a hand-rolled check might treat them as true. Be **explicit** about
what you accept, and **default to not verified**. Spring's `OidcUser` gives you the claim
as a typed object, so the check belongs at the boundary.

### CQ5. "Should I store the provider's access token?"

- **Naive answer:** "Yes, in case I need to call the provider's API."  ← *true, and it creates a liability*
- **The trap:** The trap is treating the access token as a passive record. It is a **live
  third-party credential** with a 1-hour lifetime, stored in your database, readable by
  anyone with database access or a backup, and **revocable only by the user at the provider**
  — not by you. If you do not actually call the provider's API, it is pure liability.
- **Expert answer:** Only if you genuinely call the provider's API on the user's behalf (for
  example, importing their Google Drive files). If you do, store the **refresh token**
  instead — it is the long-lived credential you can actually use — and encrypt it, because
  you must present it back to the provider (unlike your *own* refresh tokens, which you only
  ever compare and can therefore hash). If you do **not** call the provider's API, store
  **neither**: for a pure authentication integration you need the local user record and
  your own session, and that is all. The default for a social login should be "store
  nothing from the provider".

### CQ6. "Why does the same user get two accounts when they use Google and GitHub?"

- **Naive answer:** "Because the `sub` is different in each provider."  ← *true, and it is the correct diagnosis*
- **The trap:** The trap is treating this as an unavoidable consequence. It is a **design
  decision**: if you resolve only by `(issuer, sub)`, then two provider accounts are two
  identities **by construction** — and the user experiences that as a bug.
- **Expert answer:** It is unavoidable *if* you only key on `(issuer, sub)`, and that is the
  right key — but linking is a **separate, explicit step**. The production pattern is: the
  first provider identity creates the account; a second provider identity for the same human
  is **attached** to the existing local user **only** when the linking policy allows it —
  a verified email on both sides, or an explicit "link this account" flow authenticated with
  the *other* credential. So the answer is: two provider identities, **one local user**, and
  a `provider_identity` table that can hold many rows per local user. What you must never do
  is merge on an unverified email — that is the takeover again.

### CQ7. "What is the difference between the `roles` in Keycloak's `realm_access` and its
`resource_access`?"

- **Naive answer:** "Realm roles are global, client roles are per client."  ← *true, and it misses the practical trap*
- **The trap:** The trap is that a **client role** only appears in `resource_access` for the
  client it belongs to — so a token issued to `orders-web` contains
  `resource_access.orders-web.roles` but **not** `resource_access.billing-api.roles`. A
  mapper that reads `resource_access` generically ("all the roles in there") will therefore
  see a **different set** depending on which client the token was issued to — and your
  authorisation behaviour will vary by login path.
- **Expert answer:** `realm_access.roles` are the roles defined **for the realm** — they
  appear in every token from that realm, for every client. `resource_access.<clientId>.roles`
  are the roles defined **for one client**, and they appear only in tokens issued to that
  client. The practical consequence: if you want the same behaviour regardless of which
  client issued the token, read **`realm_access.roles`** — and filter out Keycloak's
  internal roles (`offline_access`, `uma_authorization`, `uma_protection`), which live there
  precisely because they are realm-wide. If you want **per-application** roles, read
  `resource_access[yourClientId].roles` — and be aware that the map is keyed by the
  **client id**, so it must be your exact id.

### CQ8. "Is it safe to trust the provider's `hd` claim (Google Workspace hosted domain)?"

- **Naive answer:** "Yes — Google sets it and it can't be forged."  ← *true, and it misses the policy boundary*
- **The trap:** The trap is treating a provider's claim as a **policy decision**. `hd` says
  "this account is hosted on <domain>" — it is a **fact about the account**, not a decision
  about who may sign in to your application. Whether `acme.com` is allowed is **your**
  policy, and the provider has no way to express it.
- **Expert answer:** `hd` is a strong **hint** — Google does not let a consumer account
  claim a Workspace domain it does not have — but the **policy** is yours. The layering is:
  (1) at the **provider**, configure the authorized domain so a personal Gmail cannot
  complete the consent screen at all; (2) in your code, check `email_verified` **and** the
  domain against **your** allowlist; (3) optionally, use `hd` as a **corroborating** signal
  (it must agree with the email's domain). And note that `hd` is **not present** for
  consumer accounts — its absence is informative, but its presence is what you act on, and
  only after the verification check.

### CQ9. "What happens when the provider's user changes their email address?"

- **Naive answer:** "The user's email in our database updates on the next login."  ← *only if you implemented it*
- **The trap:** The trap is assuming the update is automatic. It is not — the provider's
  claims arrive, and **your** service must choose to copy them onto the local record. A
  naive implementation either (a) never updates, leaving a stale email forever, or (b)
  overwrites **every** field, including fields the provider does not own.
- **Expert answer:** Update **explicitly and selectively**: the display name and the avatar
  may come from the provider; the **email** may be updated only if the provider says it is
  **verified** — and then you must decide whether that email is allowed to be a **login
  identifier**, because an email change that silently moves the account's login is a
  support incident waiting to happen. The safe rule: the provider's claims refresh the
  **profile**; the **local roles and the account's identifiers** are yours. And a useful
  refinement: keep the *original* verified email as the account's stable contact and store
  the provider's current email separately, so you can audit the change.

### CQ10. "Can I use the same `OAuth2UserService` for both Google and GitHub?"

- **Naive answer:** "Yes, the interface is the same."  ← *true, and it produces a mess*
- **The trap:** The trap is that the interface is the same but the **inputs** are not. Google
  returns an **ID token** (so you use `OidcUserService`); GitHub returns only a userinfo
  response (so you use `OAuth2UserService`). Their claim names differ, their **username
  attributes** differ (`sub` vs `id`), and their **email semantics** differ (always present
  and verified vs sometimes `null`). One service that handles both is a chain of
  `if (provider.equals("github"))` branches.
- **Expert answer:** Keep the **interface** uniform — that is the point of the extension
  point — but implement **one service per provider family**: an `OidcUserService` for OIDC
  providers (Google, Keycloak, Okta, Entra) and an `OAuth2UserService` for the plain-OAuth
  ones (GitHub, Facebook). Share the **policy** — the domain allowlist, the verification
  check, the JIT provisioning — in a common collaborator that both call. That way the
  security rules are written **once** (and reviewed once), while the provider-specific
  mechanics stay in their own class. The anti-pattern to avoid is not duplication of
  *mechanics*; it is duplication of *policy*.

---

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — "The GitHub email is null"

**Symptom.** The GitHub login succeeds but the local user record has no email, or the
application throws a "no email" error during provisioning.

**Diagnostic tool/command.**

```bash
# 1. What did /user actually return?
grep -o '"email":[^,]*' github-user-response.log

# 2. Does the token have the user:email scope?
grep -o 'scope=[^&]*' authorize-url.txt
# → scope=read:user%20user:email

# 3. Can you call /user/emails with this token?
curl -s -H "Authorization: Bearer $TOKEN" \
     -H "Accept: application/vnd.github+json" \
     https://api.github.com/user/emails | jq '.[] | {email, primary, verified}'
```

**Expected signal.** `"email": null` from `/user` — the user has no **public** email set.
And `/user/emails` returns **403** with
`{"message":"Requires authentication","documentation_url":"..."}` if the `user:email` scope
was not granted — which happens when the app was registered without it, or when the user's
OAuth grant predates the scope change (GitHub **re-prompts** when scopes change, but only
after the user consents).

**The fix.** (1) Register the app with the `read:user user:email` scopes. (2) Call
`/user/emails` when `/user` has no email. (3) Select `primary && verified`. (4) If there is
no primary verified email, **reject** the login and ask the user to make an email public or
to supply and verify one — never create an account with a null email.

---

### Issue 2 — "The roles are not mapped"

**Symptom.** The API returns **403** for a role-protected endpoint even though the user has
the role in Keycloak.

**Diagnostic tool/command.**

```bash
# 1. Decode the token and LOOK for the roles.
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null \
  | jq '{scope, realm_access, resource_access}'

# 2. Is the "roles" client scope assigned to the client?
#    Keycloak → realm → Clients → <client> → Client scopes → Default/Optional
#    Or via the admin API:
curl -s -H "Authorization: Bearer $ADMIN_TOKEN" \
  "https://localhost:8081/admin/realms/acme/clients?clientId=orders-web" | jq '.[].defaultClientScopes'

# 3. What does the API's converter read?
grep -rn "JwtGrantedAuthoritiesConverter\|realm_access\|resource_access" src/main/java/
```

**Expected signal.** Two distinguishable findings:

1. **The token has no `realm_access.roles` with your roles** → the `roles` client scope is
   not assigned to the client. The token will show only Keycloak's internal roles, or no
   `realm_access` at all.
2. **The token HAS the roles, but the converter is the default** → the default
   `JwtGrantedAuthoritiesConverter` reads `scope`, and the roles are in `realm_access`, so
   nothing is mapped.

**The fix.** Assign the `roles` client scope, **and** implement the custom converter. Both
are required — and if the web app uses a `GrantedAuthoritiesMapper` while the API uses a
`JwtAuthenticationConverter`, implement **both**.

---

### Issue 3 — "The redirect URI is not registered"

**Symptom.** The AS rejects the authorization request. Google shows
*"Error 400: redirect_uri_mismatch"*; Keycloak shows
*"Invalid parameter: redirect_uri"*.

**Diagnostic tool/command.**

```bash
# 1. What redirect_uri did the client actually send?
grep -o 'redirect_uri=[^&]*' authorize-url.txt | sed 's/redirect_uri=//' | python3 -c "import sys,urllib.parse;print(urllib.parse.unquote(sys.stdin.read()))"

# 2. What is registered at the provider?
#    Google Cloud Console → Credentials → the OAuth client → Authorized redirect URIs
#    Keycloak → realm → Clients → the client → Valid redirect URIs

# 3. Compare them BYTE FOR BYTE.
printf '%s\n' "$SENT" | cat -A
printf '%s\n' "$REGISTERED" | cat -A
```

**Expected signal.** The usual differences, all of them invisible to the eye:

- a **trailing slash** (`.../code/google/` vs `.../code/google`);
- **`http` vs `https`**;
- an **explicit port** (`http://localhost:8080/...` vs `http://localhost/...`);
- a **`%2F` vs `/`** difference in the encoding;
- the app running on `localhost` while the registration says `127.0.0.1`;
- a **staging** redirect URI not registered in production.

**The fix.** Register the **exact** URI, and register **one URI per environment**. The
`cat -A` comparison is the diagnostic that finds the invisible character. Never use a
wildcard.

---

### Issue 4 — "The login loops back to the login page"

**Symptom.** The user authenticates at the provider, is redirected back, and lands on the
login page again — repeatedly.

**Diagnostic tool/command.**

```bash
# 1. What does the framework say?
kubectl logs deploy/orders-web --tail=200 | grep -i "state parameter\|invalid_state\|oauth2"

# 2. Was the session cookie carried through the redirect?
curl -s -c /tmp/c.txt -o /dev/null -w '%{http_code}\n' https://app.acme.com/oauth2/authorization/google
grep -c JSESSIONID /tmp/c.txt

# 3. Which instance handled the callback?   (the key question for a multi-instance app)
kubectl logs deploy/orders-web --tail=2000 | grep 'login/oauth2/code' \
  | grep -o 'instance=[a-z0-9-]*' | sort | uniq -c

# 4. Is the cookie's SameSite attribute blocking the callback?
kubectl get deploy orders-web -o jsonpath='{.spec.template.spec.containers[0].env}' \
  | tr ',' '\n' | grep -i 'same-site\|session'
```

**Expected signal.** Three distinguishable causes:

1. **"The state parameter is missing"** and the session cookie is absent → the session was
   not carried across the redirect. The session cookie is `SameSite=Strict`, which the
   browser refuses to send on a cross-site redirect. **Fix: `SameSite=Lax`.**
2. **The failures correlate with one instance** → the session is not shared across the
   replicas. **Fix: a shared session store, or `HttpCookieOAuth2AuthorizationRequestRepository`
   with CSRF enabled.**
3. **A state *mismatch*** → a forged or replayed callback, or a non-sticky load balancer.
   This one is a **security event**.

**The fix.** Apply the fixes above, and alert on `invalid_state` — it is either a topology
bug or an attack.

---

### Issue 5 — "A user is linked to the wrong account"

**Symptom.** A customer reports that their account shows another person's data, or that
their social login lands them in the wrong workspace.

**Diagnostic tool/command.**

```bash
# 1. Which provider identities point at this local user?
psql -c "select pi.issuer, pi.provider_subject, pi.created_at, u.email, u.id
         from provider_identity pi join local_user u on u.id = pi.local_user_id
         where u.id = 1043;"

# 2. What was the email_verified flag at link time?
psql -c "select * from auth_audit_log
         where local_user_id = 1043 order by created_at desc limit 20;"

# 3. Is there an unverified provider identity anywhere in the estate?
psql -c "select count(*) from provider_identity where email_verified_at_link = false;"
```

**Expected signal.** The local user `1043` has **two** provider identities attached, and the
audit log shows that one was linked when `email_verified` was **false** — the linking path
did not check the flag. The duplicate-identity query finds **214** such rows across the
estate.

**The trap.** "Just detach the wrong provider identity." That fixes the one user and leaves
**213 more compromised accounts**.

**The resolution.** (1) **Freeze the linking path** — deploy the `email_verified` check and
the "never link to a password account" rule **before** fixing the data. (2) **Audit the
estate**: find every provider identity linked with an unverified email or to a password
account; those accounts are **potentially already-compromised** and need a **forced
re-authentication** (invalidate their sessions and require a fresh login). (3) Detach the
wrong identities and merge or restore the affected data — and check what the attacker
**did** while they were in the account, because "linked to the wrong account" means the
attacker had **access**. (4) Add the audit event types (`PROVISIONED` / `LINKED` /
`REJECTED`) with the `email_verified` flag, so this is **detectable** next time.

**The lesson.** A linking rule that is missing a check is not a bug — it is a **mass
compromise**. The audit log is what turns "one user's report" into "214 affected accounts",
and it must exist **before** the incident, not after it.

---

## 19. Architecture Design Questions

### 19.1 "Design social login for a consumer app with 800,000 users"

**The requirement.** 800,000 consumer users. Google (70%), Apple (20%), password (10%).
Apple hides the email unless the user shares it. The app must work across 6 instances with
no shared session store, and must survive a provider outage.

**Reference design.**

```text
   ┌──────────────────────────────────────────────────────────────────┐
   │  SPA / mobile                                                  │
   │    → Authorization Code + PKCE (public client)                 │
   │    → tokens NEVER reach the browser JS (a BFF session cookie)   │
   └───────────────────────┬──────────────────────────────────────────┘
                           │
   ┌───────────────────────▼──────────────────────────────────────────┐
   │  BFF (Spring Boot, oauth2-client)                              │
   │    • HttpCookieOAuth2AuthorizationRequestRepository (no session)│
   │    • CSRF enabled on the callback                                │
   │    • OidcUserService → the identity resolution policy           │
   └───────────────────────┬──────────────────────────────────────────┘
                           │
   ┌───────────────────────▼──────────────────────────────────────────┐
   │  DATABASE                                                     │
   │    local_user(id, email, email_verified, created_at, ...)      │
   │    provider_identity(id, local_user_id, issuer, provider_subject,│
   │                       email_at_link, email_verified_at_link)    │
   │    ★ UNIQUE (issuer, provider_subject)                          │
   │    ★ UNIQUE local_user.email                                   │
   └──────────────────────────────────────────────────────────────────┘

   PROVIDERS: Google · Apple · (password fallback)

   THE APPLE CASE
   ─────────────
   Apple hides the email → the first Apple login has NO email.
   → the account is keyed by (issuer, sub) ONLY
   → the user is asked for an email LATER, and it is verified
     by a one-time link before it becomes a login identifier.
```

**The decisions and the trade-offs.**

| Decision | Choice | Trade-off |
|---|---|---|
| **Session** | A BFF session cookie, stateless if possible | The browser holds no token — XSS cannot steal one. The cost is a BFF component and a stateless session-cookie design |
| **`state` storage** | A cookie | No shared session store, so any instance handles the callback. The price: CSRF must be enabled on the callback |
| **Identity key** | `(issuer, provider_subject)` | Apple and Google both work, with no email. The price: the local user may have no email until the user supplies one |
| **Email handling** | Never a login identifier until verified | A Google user's email can change; keying on it would break the account |
| **Scope** | `openid email` only; `profile` only if the UI needs the name | A smaller scope = less PII, a simpler consent screen, and no re-consent when the app later adds a scope |
| **Provider outage** | A password fallback for 10% of users; a clear error for the rest | Not every user can log in during an outage. The alternative — a weaker fallback — is worse |
| **Audit** | `RETURNING` / `PROVISIONED` / `LINKED` / `REJECTED` events with the issuer and the `email_verified` flag | A write per login (cheap: a single indexed insert) and the ability to detect an account-takeover campaign |

**The trade-offs to say out loud.**

1. **The BFF costs a component.** The alternative — tokens in the browser — puts the access
   and refresh tokens in reach of any XSS, which for 800,000 consumer users is a
   material risk. The BFF is the right call.
2. **Apple's email hiding forces a two-stage identity.** The account exists with a
   `(issuer, sub)` key and **no** email; the email is collected and verified later. That is
   more complex, and it means "email" is not always present — so **every** part of the
   product must tolerate a null email.
3. **A password fallback for 10% of users** is a permanent feature, not a temporary one —
   and it means you keep a password store, a hashing policy and a breach surface. The
   honest trade: you accept that to avoid a total lockout during a provider outage.
4. **Not using the `profile` scope** means the app shows the provider's display name only
   if it asks for it later — and asking later re-triggers the consent screen for all
   800,000 users.

---

### 19.2 "Design enterprise SSO for 340 B2B customers"

**The requirement.** 340 enterprise customers. 210 speak SAML only, 130 speak OIDC, 40 want
Google Workspace. Each customer's users must land in **that customer's** tenant with **that
customer's** roles. Onboarding a new customer must not require an application change.

**Reference design.**

```text
   ┌────────────────────────────────────────────────────────────────────┐
   │  KEYCLOAK  —  the broker.  One realm per customer.                  │
   │                                                                    │
   │  realm "customer-acme"                                            │
   │    ├─ client "acme-web"  (confidential, your app)                  │
   │    ├─ identity provider: Google (Workspace, for customer-acme)     │
   │    ├─ identity provider: Okta (SAML)                               │
   │    └─ realm roles: acme-order-reader, acme-order-admin            │
   │  realm "customer-globex"                                           │
   │    └─ …                                                           │
   │                                                                    │
   │  ★ Every realm is an OIDC issuer to YOUR application.              │
   │  ★ Your application never speaks SAML.                             │
   └────────────────────────────┬───────────────────────────────────────┘
                                │  discovery: /.well-known/openid-configuration
                                │  (one per realm)
                                ▼
   ┌────────────────────────────────────────────────────────────────────┐
   │  YOUR APPLICATION (Spring Boot)                                    │
   │                                                                    │
   │  ONE registration per customer, discovered by tenant:              │
   │    spring.security.oauth2.client.registration.acme.*              │
   │    spring.security.oauth2.client.provider.acme.issuer-uri=…       │
   │                                                                    │
   │  The JwtDecoder validators:                                        │
   │    iss  — the realm's issuer                                       │
   │    aud  — your API                                                │
   │    tenant_id — the customer, resolved from the Host, SERVER-SIDE   │
   └────────────────────────────────────────────────────────────────────┘
```

**The decisions and the trade-offs.**

| Decision | Choice | Rationale |
|---|---|---|
| **The broker** | Keycloak (or Ping/Okta) in front | Your application speaks **one** protocol; onboarding a SAML customer is a broker configuration change, not a code change |
| **Tenant model** | One realm per customer | Complete isolation — a customer's users, roles and identity providers cannot see another's. The cost is 340 realms to administer |
| **The tenant boundary** | A `tenant_id` claim, validated against the **server-resolved** tenant | The claim is machine-checkable; the resolution is server-side, so a forged header cannot move you across a boundary |
| **Roles** | The broker's realm roles → your mapping table → your own `tenant_membership.role` | The broker's directory drives the **coarse** role; your database holds the fine-grained permission and the tenant membership |
| **Your roles** | Seeded at first login from the broker's roles, then **yours** | A customer's directory rename must not silently elevate a user; the mapping is explicit and unknown roles drop out |
| **Logout** | Federated — the broker's `end_session_endpoint` | Ending the session at the broker ends it everywhere; your local session is destroyed separately |
| **Assertions** | The broker's | The user's password never reaches your application |

**The trade-offs to say out loud.**

1. **340 realms is real operational work** — the broker's admin API can automate it, but
   someone owns the runbook, the backup, and the upgrade of 340 realms.
2. **The broker is a single point of failure** for login. Multi-region active/active brokers
   are the answer; the alternative is per-customer federation in your application, which
   moves the complexity from the broker into your code and is much worse.
3. **A per-tenant issuer means a per-tenant `iss`** — and a **multi-issuer** validation
   problem in your API. The `JwtIssuerValidator` validates one issuer; a fleet serving 340
   issuers needs either a **gateway that normalises** them into one internal issuer, or a
   validator that accepts a set. The gateway approach is the one that scales.
4. **The `tenant_id` validator is mandatory but not sufficient.** The token's tenant must
   match the request's tenant — but the **queries** must also filter by the tenant, and
   that filter belongs in the repository base class, not in each query. A missing filter is
   a data leak, and no token validator can prevent it.

---

## 20. Rapid-Fire MCQs

**1.** Which Spring type is built from the OIDC **ID token**?
- A) `OAuth2User`
- B) **`OidcUser`**
- C) `OAuth2UserRequest`
- D) `OAuth2AuthenticationToken`

**2.** `OidcUser` extends `OAuth2User` in Spring Security 6?
- A) Yes
- B) **No — both extend `OAuth2AuthenticatedPrincipal`**
- C) Yes, since 6.1
- D) Only in Spring Security 5

**3.** GitHub's `/user` returns `email: null` when:
- A) The token lacks the `user:email` scope
- B) **The user has no public email set in their GitHub settings**
- C) The email is unverified
- D) The user has no verified email at all

**4.** To read GitHub's email you must request the scope:
- A) `read:user`
- B) `repo`
- C) **`user:email`**
- D) `gist`

**5.** The correct rule for choosing an email from `/user/emails` is:
- A) The first entry
- B) The last entry
- C) **The entry with `primary: true` and `verified: true`**
- D) Any entry with a non-null address

**6.** In Keycloak, the realm-wide roles appear in:
- A) `resource_access.roles`
- B) **`realm_access.roles`**
- C) `scope`
- D) `roles`

**7.** Why might a Keycloak token contain **no** application roles?
- A) The client is public
- B) **The `roles` client scope is not assigned to the client**
- C) The user has no roles
- D) The token is a client-credentials token

**8.** Which of these is a Keycloak **internal** role that must be filtered out?
- A) `orders-reader`
- B) **`offline_access`**
- C) `orders-admin`
- D) `view-profile`

**9.** The default `JwtGrantedAuthoritiesConverter` reads which claim?
- A) `realm_access`
- B) `resource_access`
- C) **`scope` (or `scp`)**
- D) `roles`

**10.** What is the safest key for a local user record backed by social login?
- A) The email address
- B) The provider's display name
- C) **`(issuer, provider_sub)`**
- D) The provider's avatar URL

**11.** Why should you usually **not** store the provider's access token?
- A) It is too large
- B) **It is a live third-party credential you cannot revoke**
- C) The provider forbids it
- D) It expires too quickly to be useful

**12.** An unverified provider email should be:
- A) Accepted — the provider returned it
- B) Stored but flagged
- C) **Rejected — never used to link an existing account**
- D) Accepted if the domain is allowed

**13.** Which scope set is the most minimal for "just get the user's email" from Google?
- A) `openid profile email`
- B) **`openid email`**
- C) `email`
- D) `openid`

**14.** In the Google Cloud console, "Authorized domains" is used to:
- A) Speed up the consent screen
- B) **Prevent a personal Gmail account from completing the consent screen**
- C) Restrict the redirect URIs
- D) Set the access-token TTL

**15.** What does JIT provisioning do?
- A) Re-issues the access token automatically
- B) **Creates the local user on the first successful provider login**
- C) Migrates users between providers
- D) Refreshes the ID token

**16.** Which Spring artifact provides SAML service-provider support?
- A) `spring-boot-starter-oauth2-client`
- B) **`spring-security-saml2-service-provider`**
- C) `spring-boot-starter-oauth2-resource-server`
- D) `spring-security-saml2-core`

**17.** SAML and OIDC differ most fundamentally in that SAML's assertion:
- A) Is encrypted
- B) **Travels in the browser's form POST (the front channel)**
- C) Is always signed
- D) Uses XML namespaces

**18.** In a SAML flow, the endpoint configuration comes from:
- A) A discovery document (`.well-known/openid-configuration`)
- B) **Metadata XML**
- C) A YAML file only
- D) The `client_secret`

**19.** You should disable social login for admin accounts because:
- A) Social providers are slower
- B) **A personal account could be used to reach a privileged account**
- C) Admins need 2FA and social providers do not offer it
- D) The scope limits differ

**20.** A spike in "LINKED" audit events most likely means:
- A) A successful user migration
- B) **A possible account-takeover campaign — investigate**
- C) A new provider was added
- D) The database is slow

---

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **B** | `OidcUser` is built from the ID token; `OAuth2User` from the userinfo response. |
| 2 | **B** | Both implement `OAuth2AuthenticatedPrincipal`; neither extends the other. |
| 3 | **B** | GitHub includes the email in `/user` only when it is public. |
| 4 | **C** | `user:email` is the scope that grants access to `/user/emails`. |
| 5 | **C** | `primary && verified` — the first entry may be unverified or secondary. |
| 6 | **B** | `realm_access.roles` is the realm-wide list; `resource_access` is per client. |
| 7 | **B** | The `roles` client scope is what puts the roles into the token at all. |
| 8 | **B** | `offline_access` (and `uma_authorization`) are Keycloak's own roles. |
| 9 | **C** | The default reads the OAuth scope claim and produces `SCOPE_` authorities. |
| 10 | **C** | `(issuer, sub)` is stable and provider-issued; the email is mutable and unverified. |
| 11 | **B** | It is a live credential in your database that only the user can revoke. |
| 12 | **C** | An unverified address does not prove the person controls the mailbox. |
| 13 | **B** | `openid email` is the minimum; `profile` returns the name, picture and locale. |
| 14 | **B** | It makes a personal Gmail unable to complete the consent screen for your client. |
| 15 | **B** | The local user is created on the first successful provider login. |
| 16 | **B** | SAML is a separate artifact with a completely different API. |
| 17 | **B** | The SAML assertion is posted by the browser — the front channel. |
| 18 | **B** | SAML uses metadata XML rather than a discovery document. |
| 19 | **B** | A personal provider account could otherwise reach a privileged local account. |
| 20 | **B** | Linking should be rare; a spike is either a migration or a takeover campaign. |

> 📌 **The four to re-read:** **Q3/Q5** (the GitHub email rule), **Q7/Q8** (Keycloak's roles
> and internal roles), **Q12** (unverified emails) and **Q20** (the LINKED spike).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **Social login = ?** | The Authorization Code flow (+ PKCE) against a different provider |
| **`CommonOAuth2Provider`** | `GOOGLE` `GITHUB` `FACEBOOK` `OKTA` (`MICROSOFT` from Security 6.2 / Boot 3.2) |
| **A preset gives you** | The endpoints and the default `user-name-attribute` — **not** your scopes or your role mapping |
| **`OAuth2User`** | Built from the **UserInfo** response · GitHub, Facebook |
| **`OidcUser`** | Built from the **ID token** · Google, Keycloak, Okta |
| **Neither extends the other** | Both implement `OAuth2AuthenticatedPrincipal` |
| **GitHub email** | `/user` → `email: null` unless public · use `/user/emails` + the `user:email` scope |
| **Email selection** | `primary: true` **and** `verified: true` — never "the first" |
| **Keycloak — realm** | The isolation boundary · one per environment, per tenant |
| **Keycloak — client type** | **Public** (no secret) / **Confidential** (secret) |
| **Keycloak — client scopes** | `roles` MUST be assigned or the token has **no** roles |
| **Keycloak — realm roles** | `realm_access.roles` (includes `offline_access`, `uma_authorization`) |
| **Keycloak — client roles** | `resource_access.<clientId>.roles` |
| **The default converter** | Reads `scope` → `SCOPE_` authorities — it maps **none** of Keycloak's roles |
| **The role mapping** | An explicit table; unknown role → **no** authority; filter the internal roles |
| **JIT provisioning** | Create the local user on the first verified login, with a **default** role |
| **The local key** | **`(issuer, provider_sub)`** — never the email |
| **Linking rules** | Verified email on both sides · **never** auto-link to a **password** account |
| **The domain rule** | Enforce it in your code **and** at the provider (Google's authorized domains) |
| **Scopes** | `openid email` is usually enough · `profile` returns the name, picture and locale |
| **Token storage** | Usually store **nothing** from the provider; the refresh token if you must |
| **SAML** | An XML assertion in a browser POST · **metadata**, not discovery · `spring-security-saml2-service-provider` |
| **SAML vs OIDC** | Assertion vs token · front channel vs back channel · XML vs JSON |
| **The broker pattern** | One AS in front of all the IdPs — your app speaks only OIDC |
| **Hard rules** | Verify the email · never link on an unverified email · explicit role map · exact redirect URI · validate `state` + `nonce` · audit the event type |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Scopes requested | `openid email profile` | **`openid email`** if you only need the email — fewer PII, less consent friction |
| `email_verified` check | Required | **Never** disable it |
| Domain allowlist | `acme.com` | Add domains as tenants are onboarded; never remove one without an audit |
| Role mapping table | An explicit `Map` | Update it when the provider renames a role — and **only** the table |
| Local user key | `(issuer, provider_sub)` | Never change it — it is the audit anchor |
| `provider_identity` uniqueness | Unique on `(issuer, provider_sub)` | Mandatory — it prevents duplicate identities |
| `local_user.email` uniqueness | Unique | Mandatory — it catches duplicate-account bugs at the database level |
| Provider access token | **Not stored** | Store it only if you actively call the provider's API |
| Provider refresh token | **Not stored** | If you must, **encrypt** it (it must be presented back) — do not hash it |
| Picture URL | Not persisted | Persist it only if you display it; never use it as an identifier |
| Userinfo call | Enabled by default | **Disable** it (use the ID token only) to remove a round trip per login |
| Provider timeouts | The HTTP client's defaults | Connect **3 s**, read **10 s** — and render a clear error, never a hang |
| Social login for admins | Disabled or corporate-`hd`-only | Always — a personal account is a takeover path |
| The `state` storage | The `HttpSession` | A cookie for a stateless SPA — **with CSRF on** |
| Session TTL | 30 min | 8 h for a low-risk consumer app; 15 min for a privileged one |
| The consent screen | Provider-managed | Expect a re-prompt for every user whenever you add a scope |
| Audit event types | None | `RETURNING` / `PROVISIONED` / `LINKED` / `REJECTED` — **with the issuer and the flag** |
| GitHub scopes | `read:user user:email` | `repo` only if you actually read private repositories |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `oauth2.login.count{provider}` | Logins per provider — the load on the provider |
| `oauth2.login.duration{provider}` (p50/p95/p99) | Per-leg timing — the provider's page usually dominates |
| `oauth2.login.provisioned.count` | New accounts — a spike means a campaign or a marketing push |
| `oauth2.login.linked.count` | **A spike means a possible takeover campaign** |
| `oauth2.login.rejected.count{reason}` | Domain-not-allowed, email-not-verified, local-account-exists |
| `oauth2.userinfo.calls.count` | Should be **0** if you use the ID token only |
| `provider_identity.duplicate.count` | Should be **0** — a non-zero value means the linking logic is broken |
| `local_user.email.duplicate.count` | Should be **0** — a non-zero value means the identity resolution has a bug |
| `login.identity.mismatch.count` | A provider identity pointing at a user whose email changed — worth a look |

**Defaults to deviate from, deliberately.**

- **Never** drop the `email_verified` check to "fix" a login failure — fix the provider
  configuration or the user's account.
- **Never** auto-link to a local account with a password to "fix" a duplicate-account bug.
- **Never** map an unknown provider role into a role name — drop it.
- **Never** store the provider's access token "just in case".
- **Never** disable CSRF on the callback to "fix" a cookie problem — fix the `SameSite`
  attribute.
- **Never** widen the domain allowlist to fix a login failure without an audit.

---

## 23. Follow-up Learning

**Previous in this folder**

- ← [01 · OAuth2 Architecture, Roles & Grant Types](./01-OAuth2-Architecture-Roles-and-Grant-Types.md) —
  the roles, the tokens, the grant types, the Spring starters.
- ← [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) — the
  code flow, `state`, `nonce`, PKCE.
- ← [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) —
  machine-to-machine, refresh rotation, the OIDC identity layer, the `aud` gotcha.

**Related folders**

- [06 · JWT Authentication](../06-JWT-Authentication/README.md) — the token format and the
  validator chain.
  → [06/01 · JWT Architecture](../06-JWT-Authentication/01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md)
- [05 · Spring Security](../05-Spring-Security/README.md) — the filter chain, method
  security, password encoding.
  → [05/02 · Authentication Flow, UserDetailsService & Providers](../05-Spring-Security/02-Authentication-Flow-UserDetailsService-and-Providers.md)
- **08 · Microservices with Spring Boot** (folder, being written in parallel) — the gateway,
  service-to-service auth, resilience.

**Source pointers (RFCs, OIDC and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| OpenID Connect Core 1.0 | The `openid` scope, the ID token claims, `nonce`, `auth_time`, `azp` |
| OpenID Connect Discovery 1.0 | The discovery document and the `issuer` match rule |
| OpenID Connect RP-Initiated Logout 1.0 | `end_session_endpoint`, `id_token_hint`, `post_logout_redirect_uri` |
| OAuth 2.0 Security BCP (RFC 9700) | The modern hardening baseline for every provider |
| RFC 6749 | The authorization code flow that all of this rests on |
| RFC 7636 | PKCE — see chapter 02 |
| GitHub OAuth docs | The `user:email` scope and the `/user/emails` endpoint |
| Google OAuth 2.0 docs | The authorized-domains restriction and the `hd` claim |
| Keycloak documentation | Realms, clients, client scopes, protocol mappers, identity providers |
| Spring Security `spring-boot-starter-oauth2-client` | `ClientRegistration`, `OAuth2UserService`, `OidcUserService`, `GrantedAuthoritiesMapper` |
| Spring Security `spring-security-saml2-service-provider` | `RelyingPartyRegistrationRepository` and the SAML flow |
| Spring Security `JwtAuthenticationConverter` | The claim → authority mapping in a resource server |

---

**Next Chapter:** → [08 · Microservices with Spring Boot](../08-Microservices-with-Spring-Boot/README.md)
**Previous Chapter:** ← [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026


