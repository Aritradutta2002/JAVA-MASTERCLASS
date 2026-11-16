# Spring Authorization Server & Custom Identity Provider — The Complete Interview Guide

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ ("When would you choose Spring Authorization Server over Keycloak or Okta?", "How does the dual SecurityFilterChain architecture work in SAS?", "How do you customize JWT claims and handle key rotation with JWKSource?", "Explain RegisteredClient and JdbcRegisteredClientRepository vs custom databases.", "How do you implement a custom grant type in Spring Authorization Server?")
> **Prerequisites:** [01 · OAuth2 Architecture, Roles & Grant Types](./01-OAuth2-Architecture-Roles-and-Grant-Types.md) · [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) · [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) · [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md)
> **Last Updated:** 2026

> **Target stack:** Spring Boot 3.3+ · Spring Authorization Server 1.2+ / 1.3+ · Spring Security 6.3+ · Java 17/21+ · Jakarta EE 10 · Nimbus JOSE + JWT

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory (Beginner → Advanced)](#3-deep-dive--theory-beginner--advanced)
  - [3.1 Beginner Level: Fundamentals & Evolution](#31-beginner-level-fundamentals--evolution)
  - [3.2 Intermediate Level: Core Domain Models & Services](#32-intermediate-level-core-domain-models--services)
  - [3.3 Advanced Level: Token Customization, Cryptography & Key Management](#33-advanced-level-token-customization-cryptography--key-management)
  - [3.4 Expert Level: Protocols, Introspection, Revocation & Custom Grants](#34-expert-level-protocols-introspection-revocation--custom-grants)
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

- **Spring Authorization Server (SAS) is the official next-generation framework for building an OAuth 2.1 and OpenID Connect 1.0 Identity Provider in Java.** It completely replaces the legacy, deprecated Spring Security OAuth (`@EnableAuthorizationServer`, EOL in 2020).
- **SAS is an embeddable framework, not an out-of-the-box identity product.** Unlike Keycloak or Okta, SAS does not provide a turnkey administrative GUI, user registration portal, or out-of-the-box database out of the box. You write Spring Boot code to define exactly how clients, tokens, users, and consent are managed.
- **The Dual `SecurityFilterChain` architecture is mandatory.** SAS requires two distinct filter chains:
  1. *Protocol Chain (`@Order(Ordered.HIGHEST_PRECEDENCE)`):* Dedicated strictly to protocol endpoints (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, `/oauth2/introspect`, `/oauth2/revoke`, `/oauth2/device_authorization`, `/userinfo`, `/.well-known/*`).
  2. *Default / Application Chain (`@Order(2)`):* Handles human interactive authentication (the `/login` page, MFA, consent screen, session management, and standard web/API routes).
- **The handoff between chains occurs via `LoginUrlAuthenticationEntryPoint`.** When an unauthenticated browser requests authorization at `/oauth2/authorize`, the protocol chain catches the `AuthenticationException` and redirects to `/login` on the application chain, preserving the `OAuth2AuthorizationRequest` in the `HttpSession`.
- **`RegisteredClient` is the core client domain model.** It encapsulates client identity, secrets, allowed grant types, authentication methods (`client_secret_basic`, `client_secret_post`, `private_key_jwt`, `none`), redirect URIs, scopes, `ClientSettings` (such as mandatory PKCE and consent enforcement), and `TokenSettings` (TTL, format, signature algorithms).
- **In production, use `JdbcRegisteredClientRepository`, `JdbcOAuth2AuthorizationService`, and `JdbcOAuth2AuthorizationConsentService`.** In-memory implementations lose all active sessions, refresh tokens, and consent decisions upon application restart or pod redeployment.
- **Asymmetric cryptographic key management is driven by `JWKSource<SecurityContext>`.** The Authorization Server signs JWTs using an RSA or EC private key and exposes public keys at `/oauth2/jwks`. Keys MUST have fixed Key IDs (`kid`) and support zero-downtime rotation (publishing the active signing key alongside retired validation keys).
- **`OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` is the extension hook for JWT claims.** It allows you to inject tenant identifiers, user UUIDs, application roles, and fine-grained permissions into the access token or ID token without polluting the core protocol engine.
- **SAS enforces OAuth 2.1 security baselines by default:** PKCE is strictly supported and can be made mandatory for all clients; the insecure Implicit and Resource Owner Password Credentials grants are completely absent; exact redirect URI string matching is strictly enforced.
- **Token Introspection (RFC 7662) and Token Revocation (RFC 7009) endpoints are built-in.** Introspection allows resource servers to query opaque reference tokens, while revocation allows clients to invalidate access or refresh tokens upon user logout.
- **Custom grant types are fully supported via the extension model.** By providing a custom `AuthenticationConverter` to parse incoming HTTP parameters and a corresponding `AuthenticationProvider` to validate and mint tokens, you can implement non-standard flows (such as SMS OTP, biometric authentication, or RFC 8693 Token Exchange).

---

## 1. What & Why — The Problem This Solves

### The Architectural Dilemma: When Do You Build an Identity Provider?

In a distributed microservice architecture, authentication must be decoupled from business services. Every enterprise reaches a fork in the road regarding identity:

```text
                                 ENTERPRISE IDENTITY CHOICES
                                               │
               ┌───────────────────────────────┼───────────────────────────────┐
               ▼                               ▼                               ▼
    ┌─────────────────────┐         ┌─────────────────────┐         ┌─────────────────────┐
    │  Commercial SaaS    │         │  Turnkey OSS (COTS) │         │  Embedded Framework │
    │   (Okta / Auth0 /   │         │     (Keycloak /     │         │ (Spring Authorization│
    │     AWS Cognito)    │         │     Ory Hydra)      │         │       Server)       │
    └──────────┬──────────┘         └──────────┬──────────┘         └──────────┬──────────┘
               │                               │                               │
       • Zero infra setup              • Standalone service            • Single JAR deployment
       • Strict vendor lock-in         • Complex admin GUI             • 100% customizable logic
       • $0.05–$0.30 per MAU           • Heavy memory footprint        • Tight Spring Data/DB integration
       • Rigid custom logic            • Custom extensions via SPI     • Zero third-party runtime cost
       • Network hop to cloud          • Upgrades can break SPIs       • Native CI/CD & testing
```

### Why Spring Security Deprecated `@EnableAuthorizationServer`

Prior to 2018, Spring Security included an authorization server module (`spring-security-oauth2`). In 2018, the Spring team announced its deprecation, leading to the creation of the modern **Spring Authorization Server** project (which reached 1.0 GA in 2022 and is natively integrated into Spring Boot 3.x).

The legacy implementation was retired due to foundational architectural flaws:
1. **Monolithic RFC 6749 Coupling:** The legacy code intermingled Authorization Server, Resource Server, and Client concerns into a single sprawling package. A change to an authorization endpoint frequently broke unrelated client-side classes.
2. **Support for Obsolete & Insecure Grants:** Legacy Spring OAuth promoted the Resource Owner Password Credentials (ROPC) grant and the Implicit grant, both of which are deprecated and prohibited under OAuth 2.1 due to severe credential leakage and URL fragment sniffing risks.
3. **Absence of OpenID Connect 1.0 (OIDC):** The legacy library supported OAuth 2.0 authorization tokens but lacked native OIDC identity tokens, discovery metadata (`/.well-known/openid-configuration`), UserInfo endpoints, and standard JSON Web Key Sets (JWKS). Teams had to write custom controllers to expose public keys.
4. **Rigid Extension Model:** Customizing claims, token storage, and grant types required overriding internal beans (`DefaultTokenServices`, `TokenEnhancer`), which frequently broke during framework upgrades.
5. **No Native PKCE Support:** Legacy Spring OAuth predated the universal requirement for PKCE (RFC 7636) across both public and confidential clients.

### Modern Solution: Spring Authorization Server (SAS)

Spring Authorization Server is a ground-up redesign built directly upon Spring Security 6.x and Java 17+. It provides:
- **Strict Compliance with Modern RFCs:** OAuth 2.1 draft, RFC 6749, RFC 6750 (Bearer Tokens), RFC 7519 (JWT), RFC 7523 (JWT Client Authentication), RFC 7636 (PKCE), RFC 7662 (Introspection), RFC 7009 (Revocation), RFC 8414 (Metadata), and OpenID Connect Core 1.0.
- **Dual SecurityFilterChain Architecture:** Absolute separation between protocol endpoints and user authentication endpoints.
- **Pluggable Architecture:** Complete control over client persistence, token persistence, key storage, and claim generation through clean, non-leaky interfaces (`RegisteredClientRepository`, `OAuth2AuthorizationService`, `OAuth2TokenCustomizer`, `JWKSource`).
- **Native Spring Ecosystem Synergy:** Seamlessly integrates with Spring Data JPA, Spring Cache, Spring Actuator, and Spring Cloud Gateway.

### Comparison Matrix: SAS vs. Keycloak vs. Commercial IdP

| Dimension | Spring Authorization Server (1.2+) | Keycloak (24+) | Okta / Auth0 (SaaS) |
|---|---|---|---|
| **Form Factor** | Embedded Java library in your Spring Boot app | Standalone Quarkus-based server | Multi-tenant cloud service |
| **Administration UI** | None (you write your own MVC/Admin UI) | Comprehensive Web Admin Console | Rich Cloud Management Portal |
| **User Store Integration** | Direct access to existing JPA/Hibernate entities | User Federation (LDAP/AD/Kerberos) or Custom User SPI | Directory sync agents / REST APIs |
| **Custom Business Logic** | Native Spring Beans (`@Service`, `@Autowired`) | Java SPI (Service Provider Interface JARs) | Auth0 Actions (JavaScript / Node.js) |
| **Deployment Footprint** | ~50MB RAM embedded in microservice | 512MB–1.5GB RAM standalone process | 0MB on-premise infrastructure |
| **Protocol Compliance** | OAuth 2.1, OIDC 1.0, PKCE, Introspection, Revocation | OAuth 2.0, OIDC 1.0, SAML 2.0, WebAuthn | OAuth 2.0, OIDC 1.0, SAML 2.0, WS-Fed |
| **Cost at Scale** | $0 licensing / compute cost only | $0 licensing / infrastructure & maintenance cost | Tiered pricing based on Monthly Active Users (MAU) |
| **Upgrade Maintenance** | Standard Maven dependency bumps | Database migration scripts, breaking SPI changes | Automatic platform updates managed by vendor |
| **Best Suited For** | Bespoke fintech/banking apps, multi-tenant DBs, embedded IdP | Enterprise SSO, LDAP federation, off-the-shelf IdP | Fast time-to-market, consumer apps, outsourced compliance |

---

## 2. Mental Model — The One Picture

To understand Spring Authorization Server, you must visualize how the **Dual Security Filter Chain** partitions protocol traffic from human interactive traffic, and how SAS coordinates with persistent stores and the cryptographic engine.

```text
+==================================================================================================================+
|                                    SPRING BOOT APPLICATION (PORT 8080 / 9000)                                   |
|                                                                                                                  |
|  INCOMING HTTP REQUEST                                                                                           |
|          │                                                                                                       |
|          ▼                                                                                                       |
|  ┌────────────────────────────────────────────────────────────────────────────────────────────────────────────┐  |
|  │ FILTER CHAIN 1: Protocol Endpoints (@Order(Ordered.HIGHEST_PRECEDENCE))                                    │  |
|  │ Matches Request Matchers:                                                                                  │  |
|  │  - /oauth2/authorize                 (AuthorizationEndpointFilter)                                         │  |
|  │  - /oauth2/token                     (OAuth2TokenEndpointFilter)                                           │  |
|  │  - /oauth2/jwks                      (NimbusJwkSetEndpointFilter)                                          │  |
|  │  - /oauth2/introspect                (OAuth2TokenIntrospectionEndpointFilter)                              │  |
|  │  - /oauth2/revoke                    (OAuth2TokenRevocationEndpointFilter)                                 │  |
|  │  - /oauth2/device_authorization      (OAuth2DeviceAuthorizationEndpointFilter)                             │  |
|  │  - /userinfo                         (OidcUserInfoEndpointFilter)                                          │  |
|  │  - /.well-known/oauth-authorization-server / /.well-known/openid-configuration                             │  |
|  │                                                                                                            │  |
|  │ Client Authentication:               Token Generation Pipeline:             Security Exception Handling:   │  |
|  │  • OAuth2ClientAuthenticationFilter  • OAuth2TokenGenerator                  • LoginUrlAuthentication-     │  |
|  │    (Basic, Post, PrivateKeyJwt)      • OAuth2TokenCustomizer (Claims)          EntryPoint                  │  |
|  │                                      • Nimbus JWKSource (RSA Signing)          (Redirects to /login)       │  |
|  └──────────────────────────────────────┬─────────────────────────────────────────────────┬───────────────────┘  |
|                                         │ Matches                                         │ Unauthenticated      |
|                                         │ Protocol URLs                                   │ Browser on /authorize|
|                                         ▼                                                 ▼                      |
|                                  [JSON Token/JWKS]                             [HTTP 302 Redirect to /login]     |
|                                                                                           │                      |
|  ┌────────────────────────────────────────────────────────────────────────────────────────┼───────────────────┐  |
|  │ FILTER CHAIN 2: Default Application Security (@Order(2))                               │                   |  |
|  │ Matches Everything Else:                                                               │                   |  |
|  │  - /login               (Form Login Page & DaoAuthenticationProvider) ◄────────────────┘                   │  |
|  │  - /oauth2/consent      (Custom User Consent View & Approval Controller)                                  │  |
|  │  - /actuator/health     (Health Checks & Observability Endpoints)                                          │  |
|  │  - /api/**              (Internal Administration & User Management APIs)                                   │  |
|  │                                                                                                            │  |
|  │ Authenticates: End-User Resource Owner (Username/Password, MFA, WebAuthn, LDAP)                             │  |
|  └────────────────────────────────────────────────────────────────────────────────────────────────────────────┘  |
|                                                                                                                  |
|  SHARED PERSISTENCE & CRYPTO LAYER (Spring Data JPA / JDBC / Vault)                                              |
|  ┌───────────────────────────────┐ ┌───────────────────────────────┐ ┌───────────────────────────────────────┐  |
|  │ RegisteredClientRepository    │ │ OAuth2AuthorizationService    │ │ JWKSource<SecurityContext>            │  |
|  │ • Stores Client ID & Secret   │ │ • Stores Authorization Codes  │ │ • RSA Private Signing Key             │  |
|  │ • Allowed Grants & Scopes     │ │ • Stores Active Access Tokens │ │ • Rotated Public Key Ring             │  |
|  │ • PKCE & Token TTL Settings   │ │ • Stores Refresh Tokens       │ │ • Backed by Keystore/Vault/DB         │  |
|  └───────────────────────────────┘ └───────────────────────────────┘ └───────────────────────────────────────┘  |
+==================================================================================================================+
```

> **Caption:** The Dual Filter Chain Model. The Protocol Chain intercepts OAuth 2.1 protocol endpoints with highest precedence. When an unauthenticated user arrives at `/oauth2/authorize`, the Protocol Chain redirects them to the Application Chain (`/login`). Once authenticated, control bounces back to the Protocol Chain to issue the authorization code and subsequent tokens.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

---

### 3.1 Beginner Level: Fundamentals & Evolution

#### What Spring Authorization Server Provides Out of the Box
When you add the dependency `org.springframework.security:spring-security-oauth2-authorization-server`, Spring Boot auto-configures a baseline identity provider. Out of the box, SAS registers the following RFC-compliant endpoints:

| Endpoint Path | Standard RFC | Description |
|---|---|---|
| `/oauth2/authorize` | RFC 6749 §3.1 | The user-facing authorization endpoint for Authorization Code grants. |
| `/oauth2/token` | RFC 6749 §3.2 | The token exchange endpoint for exchanging codes, client credentials, and refresh tokens. |
| `/oauth2/jwks` | RFC 7517 | Exposes the JSON Web Key Set (public keys) for resource servers to verify token signatures. |
| `/oauth2/introspect` | RFC 7662 | Allows authorized clients and resource servers to query active token status and metadata. |
| `/oauth2/revoke` | RFC 7009 | Allows clients to revoke issued access and refresh tokens. |
| `/oauth2/device_authorization` | RFC 8628 | Device flow authorization endpoint for smart TVs and CLI tools. |
| `/userinfo` | OpenID Connect Core 1.0 §5.3 | Protected resource returning standard claims about the authenticated user. |
| `/.well-known/openid-configuration` | OpenID Connect Discovery 1.0 | OpenID Provider Metadata document advertising all endpoints and supported features. |
| `/.well-known/oauth-authorization-server` | RFC 8414 | OAuth 2.0 Authorization Server Metadata document. |

#### The Minimal Working Configuration
At the beginner level, a working authorization server requires five core Spring beans:
1. **Protocol `SecurityFilterChain`**: Configures protocol endpoint filters and default settings.
2. **Default `SecurityFilterChain`**: Configures form login for authenticating the human user.
3. **`UserDetailsService`**: Authenticates the end-user (Resource Owner).
4. **`RegisteredClientRepository`**: Stores client registrations.
5. **`JWKSource<SecurityContext>`**: Provides cryptographic keys for signing tokens.
6. **`AuthorizationServerSettings`**: Configures protocol endpoint URI paths and the canonical Issuer URI.

---

### 3.2 Intermediate Level: Core Domain Models & Services

Understanding SAS requires mastering its primary domain abstractions and their lifecycles.

```text
       ┌────────────────────────┐
       │    RegisteredClient    │
       └───────────┬────────────┘
                   │ Defines metadata & capabilities
                   ▼
       ┌────────────────────────┐         Issues        ┌────────────────────────┐
       │   OAuth2Authorization  │──────────────────────▶│  OAuth2Authorization-  │
       │                        │                       │        Consent         │
       │ • Authorization Code   │                       │ • User UUID            │
       │ • Access Token (JWT)   │                       │ • Client ID            │
       │ • Refresh Token        │                       │ • Approved Scopes      │
       └────────────────────────┘                       └────────────────────────┘
```

#### 1. `RegisteredClient`
Represents an OAuth 2.0 / OIDC 1.0 client application registered with the Authorization Server (equivalent to a client in Keycloak or an Application in Okta).

Key attributes configured on `RegisteredClient`:
- **`id`**: Internal unique surrogate primary key (UUID string).
- **`clientId`**: Public client identifier (e.g., `client-showcase-app`).
- **`clientSecret`**: Hashed secret (using `{bcrypt}` or `{argon2}` prefix).
- **`clientAuthenticationMethods`**: Supported client authentication types:
  - `ClientAuthenticationMethod.CLIENT_SECRET_BASIC` (HTTP Basic: `Authorization: Basic base64(id:secret)`)
  - `ClientAuthenticationMethod.CLIENT_SECRET_POST` (Request body: `client_id` & `client_secret`)
  - `ClientAuthenticationMethod.PRIVATE_KEY_JWT` (Client signs an assertion with its private key)
  - `ClientAuthenticationMethod.NONE` (Public client — Single Page Application or Mobile app using PKCE)
- **`authorizationGrantTypes`**: Permitted grants (`AUTHORIZATION_CODE`, `CLIENT_CREDENTIALS`, `REFRESH_TOKEN`, `DEVICE_CODE`).
- **`redirectUris`**: Absolute URIs where authorization codes can be delivered. Must be exact matches.
- **`postLogoutRedirectUris`**: Target URIs after OpenID Connect RP-Initiated Logout.
- **`scopes`**: OAuth scopes the client is allowed to request (e.g., `openid`, `profile`, `email`, `orders.read`).
- **`clientSettings`**: Granular client behaviors:
  - `requireProofKey(true)`: Forces PKCE (Proof Key for Code Exchange) even if the client is confidential.
  - `requireAuthorizationConsent(true)`: Displays the user consent screen before code generation.
  - `jwkSetUrl(String)`: Used in `private_key_jwt` to verify the client's public keys.
- **`tokenSettings`**: Token lifecycle configurations:
  - `accessTokenTimeToLive(Duration)`: Access token lifespan (e.g., 15 minutes).
  - `refreshTokenTimeToLive(Duration)`: Refresh token lifespan (e.g., 30 days).
  - `reuseRefreshTokens(false)`: Enforces Refresh Token Rotation (issuing a new refresh token on every exchange).
  - `accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)`: Issues cryptographically signed JWTs. Set to `OAuth2TokenFormat.REFERENCE` for opaque tokens requiring introspection.

#### 2. `RegisteredClientRepository`
The SPI for retrieving and saving client metadata.
- **`InMemoryRegisteredClientRepository`**: Stores clients in a Java `ConcurrentHashMap`. Unsuitable for production; any restart wipes dynamic client registrations.
- **`JdbcRegisteredClientRepository`**: Production-ready implementation backed by a relational database via `JdbcOperations`. It handles serialization of client settings, grant types, and scopes to database columns.

#### 3. `OAuth2AuthorizationService`
Manages the state of an ongoing or completed authorization flow.
- It stores the temporary authorization code, its PKCE code challenge, the issued access token, and the refresh token.
- When `/oauth2/token` is called with an authorization code or refresh token, SAS uses this service to load the existing authorization, validate single-use constraints, update token statuses, and persist the new tokens.
- **`JdbcOAuth2AuthorizationService`**: Stores authorization state in the `oauth2_authorization` table. In a multi-instance Kubernetes deployment, this ensures that if Pod A issues the code, Pod B can exchange it for tokens.

#### 4. `OAuth2AuthorizationConsentService`
Manages the user's explicit consent decisions.
- If `clientSettings.requireAuthorizationConsent(true)` is set, SAS checks this service after user login.
- If the user has previously consented to all requested scopes, SAS bypasses the consent screen.
- If new scopes are requested, the consent screen displays only the unapproved delta.
- **`JdbcOAuth2AuthorizationConsentService`**: Stores approved scopes in the `oauth2_authorization_consent` table keyed by `(client_id, principal_name)`.

---

### 3.3 Advanced Level: Token Customization, Cryptography & Key Management

#### Token Customization Pipeline with `OAuth2TokenCustomizer`
In production, access tokens must convey identity and tenancy context (e.g., `tenant_id`, `user_uuid`, `roles`, `permissions`) to downstream resource servers. 

SAS uses an `OAuth2TokenGenerator` pipeline:
```text
OAuth2TokenGenerator Pipeline
  ├── OAuth2AccessTokenGenerator
  │     └── Uses OAuth2TokenCustomizer<OAuth2TokenClaimsContext>
  ├── OAuth2RefreshTokenGenerator
  └── JwtGenerator
        ├── Calls JWKSource to sign the JWT
        └── Invokes OAuth2TokenCustomizer<OAuth2TokenClaimsContext>
```

The `OAuth2TokenCustomizer<OAuth2TokenClaimsContext>` gives you programmatic access to the token's claims builder before the JWT is serialized and signed:

```java
@Bean
public OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer() {
    return context -> {
        // Customize only JWT Access Tokens (can also customize ID Tokens)
        if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            Authentication principal = context.getPrincipal();
            
            // 1. Inject User Authorities as a clean String List
            Set<String> authorities = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toSet());
            context.getClaims().claim("roles", authorities);

            // 2. Inject Custom Tenant and User Metadata from Custom Principal
            if (principal.getPrincipal() instanceof CustomUserPrincipal user) {
                context.getClaims().claim("tenant_id", user.getTenantId());
                context.getClaims().claim("user_uuid", user.getId().toString());
                context.getClaims().claim("email", user.getEmail());
            }

            // 3. Inject Audience claim matching Resource Server ID
            context.getClaims().audience(List.of("payment-service", "order-service"));
        }
        
        // Customize OIDC ID Token separately
        if (OidcParameterNames.ID_TOKEN.equals(context.getTokenType().getValue())) {
            context.getClaims().claim("auth_provider", "enterprise-auth-server");
        }
    };
}
```

> [!IMPORTANT]
> **Access Token vs. ID Token Claim Segregation:**
> Never dump full user profiles into Access Tokens. The **Access Token** is intended for the API Gateway and Resource Servers; keep it minimal (`sub`, `roles`, `tenant_id`, `aud`). The **ID Token** is intended for the Client application; put user profile claims (`name`, `email`, `picture`, `locale`) into the ID Token or serve them via the `/userinfo` endpoint.

#### Cryptography & Key Management with `JWKSource`
Every issued JWT is signed using a cryptographic private key. Resource servers fetch the corresponding public keys from the `/oauth2/jwks` endpoint to verify the signature locally.

1. **Algorithm Choice:**
   - **RS256 (RSA Signature with SHA-256):** Industry standard, 2048-bit or 4096-bit key length. Supported natively by virtually every client SDK.
   - **ES256 (ECDSA with P-256 Curve):** Modern alternative offering equivalent security to RSA 3072-bit with significantly smaller keys and faster signing operations.
2. **Key ID (`kid`):**
   - Every key published in the JWKS must have a unique, stable `kid` header parameter.
   - The generated JWT header contains `{"alg": "RS256", "kid": "key-2026-v1"}`.
   - Resource servers cache the JWKS. When they see `kid: key-2026-v1`, they use the cached key without re-querying the Authorization Server.
3. **Zero-Downtime Key Rotation Strategy:**
   - In production, keys must be rotated regularly (e.g., every 90 days) without invalidating active tokens.
   - The `JWKSource` must serve a **Key Ring** containing:
     - **Active Signing Key:** The newest private key used by SAS to sign new tokens.
     - **Validation Keys:** Retired public keys that are still valid for verifying unexpired tokens issued prior to rotation.

```text
JWKS KEY ROTATION LIFECYCLE
+-----------------------------------------------------------------------------------------+
| Time T0: Key A is Primary Signing Key. JWKS exposes [Key A].                           |
| Time T1: Key B generated. Key B becomes Primary Signing Key. JWKS exposes [Key B, Key A]|
|          • New tokens signed with Key B.                                                |
|          • Old tokens signed with Key A remain valid.                                   |
| Time T2: Max token TTL expires (e.g., 24h). Key A removed. JWKS exposes [Key B].       |
+-----------------------------------------------------------------------------------------+
```

---

### 3.4 Expert Level: Protocols, Introspection, Revocation & Custom Grants

#### Client Authentication Methods (RFC 7523 & RFC 6749)
A client authenticates itself to the `/oauth2/token` endpoint using one of several methods:

```text
    CLIENT AUTHENTICATION METHODS AT /oauth2/token
    ─────────────────────────────────────────────────────────────────────────────
    ① client_secret_basic:
       Header: Authorization: Basic base64(client_id:client_secret)
       • Standard, simple confidential client auth.

    ② client_secret_post:
       Body: client_id=foo&client_secret=bar
       • Used when HTTP Basic headers are stripped by intermediaries.

    ③ private_key_jwt (RFC 7523 - Highest Security):
       Body: client_assertion_type=urn:ietf:params:oauth:client-assertion-type:jwt-bearer
             &client_assertion=<signed_jwt>
       • The client signs a short-lived JWT with its OWN private RSA key.
       • SAS verifies the assertion using the client's public key (configured via jwkSetUrl).
       • Eliminates shared secrets entirely. Zero risk of leaked client secrets in logs.

    ④ none:
       Body: client_id=public-spa-client
       • Used by Public Clients (React, Angular, iOS, Android) that cannot protect secrets.
       • MUST be accompanied by PKCE (code_verifier).
```

#### Token Introspection (RFC 7662)
Token Introspection allows a protected Resource Server to query the Authorization Server to determine the active state of an OAuth 2.0 token and determine meta-information:

```http
POST /oauth2/introspect HTTP/1.1
Host: auth.acme.com
Authorization: Basic cmVzb3VyY2Utc2VydmVyOnNlY3JldA==
Content-Type: application/x-www-form-urlencoded

token=4f8b2d3e-9a1c-4b7e-8d2a-1c3b5a7e9f0d&token_type_hint=access_token
```

Response for an active opaque token:
```json
{
  "active": true,
  "sub": "9c1b7e4a-2f3a-4c8e-bf91-7d1a2b3c4d5e",
  "aud": ["payment-service"],
  "iss": "https://auth.acme.com",
  "exp": 1773665400,
  "iat": 1773664500,
  "client_id": "client-portal",
  "token_type": "Bearer",
  "scope": "openid profile payment.read",
  "tenant_id": "tenant-finance-01"
}
```

If the token has expired, been revoked, or does not exist, the server returns:
```json
{
  "active": false
}
```

> [!NOTE]
> Introspection introduces a network hop and database query for every API request, negating the stateless performance advantage of JWTs. Use introspection when:
> 1. You require immediate, real-time revocation guarantees for high-value operations.
> 2. You issue opaque reference tokens rather than self-contained JWTs to prevent leaking payload structure to external clients.

#### Token Revocation (RFC 7009)
The Revocation endpoint allows a client to explicitly invalidate an active token:

```http
POST /oauth2/revoke HTTP/1.1
Host: auth.acme.com
Authorization: Basic Y2xpZW50LXBvcnRhbDpzZWNyZXQ=
Content-Type: application/x-www-form-urlencoded

token=eyJhbGciOiJSUzI1NiIs...&token_type_hint=refresh_token
```

- When a **refresh token** is revoked, SAS automatically cascades revocation to all access tokens issued under the same parent authorization.
- Revocation of an already revoked or non-existent token responds with HTTP `200 OK` (RFC 7009 invariant: revocation is idempotent to prevent token scanning attacks).

#### Extending SAS with Custom Grant Types
One of the most powerful capabilities of Spring Authorization Server is its clean extension SPI for building custom OAuth 2.0 grant types (e.g., SMS OTP login, passwordless mobile authentication, or RFC 8693 Token Exchange).

To create a custom grant type, you implement three components:
1. **Custom `Authentication` token**: Represents the parsed grant request (e.g., `OAuth2SmsOtpAuthenticationToken`).
2. **Custom `AuthenticationConverter`**: Reads HTTP request parameters from `/oauth2/token` and constructs the custom authentication token.
3. **Custom `AuthenticationProvider`**: Validates the credentials, checks client authorization for the grant, invokes `OAuth2TokenGenerator`, and returns an `OAuth2AccessTokenAuthenticationToken`.

```text
CUSTOM GRANT EXTENSION ARCHITECTURE
+─────────────────────────────────────────────────────────────────────────────+
| POST /oauth2/token (grant_type=urn:acme:sms_otp&phone=+15551234&otp=948123) │
|                                    │                                        |
|                                    ▼                                        |
|  [Custom AuthenticationConverter (SmsOtpAuthenticationConverter)]           |
|  • Validates parameter presence                                             |
|  • Returns OAuth2SmsOtpAuthenticationToken(phone, otp, clientPrincipal)    |
|                                    │                                        |
|                                    ▼                                        |
|  [AuthenticationManager delegates to Custom AuthenticationProvider]         |
|  • SmsOtpAuthenticationProvider checks OTP against Redis / SMS Service      |
|  • Verifies client is authorized for grant_type 'urn:acme:sms_otp'          |
|  • Loads UserDetails for phone number                                       |
|  • Calls OAuth2TokenGenerator to create JWT Access Token                    |
|  • Saves authorization to OAuth2AuthorizationService                        |
|                                    │                                        |
|                                    ▼                                        |
|  Returns OAuth2AccessTokenResponse JSON                                     |
+─────────────────────────────────────────────────────────────────────────────+
```

---

## 4. Internal Working Mechanism

### The Filter Chain Internals
Spring Authorization Server functions by configuring a dedicated `SecurityFilterChain` containing specialized security filters. Here is the internal order of execution when a request hits the Protocol Filter Chain:

```text
Incoming HTTP Request
       │
       ▼
[Disable CSRF for Token/Revoke/Introspect endpoints]
       │
       ▼
[OAuth2ClientAuthenticationFilter]
  ├── Intercepts /oauth2/token, /oauth2/introspect, /oauth2/revoke
  ├── Extracts client credentials via ClientAuthenticationConverter
  │     (Basic, Post, Public Client PKCE, Private Key JWT)
  └── Invokes AuthenticationManager to authenticate the Client
       │
       ▼
[OAuth2AuthorizationEndpointFilter]
  ├── Intercepts /oauth2/authorize
  ├── Validates client_id, redirect_uri, response_type, scopes, PKCE parameters
  ├── Verifies End-User Authentication in SecurityContext
  └── Redirects to consent page OR issues Authorization Code
       │
       ▼
[OAuth2TokenEndpointFilter]
  ├── Intercepts /oauth2/token
  ├── Extracts grant_type (authorization_code, client_credentials, refresh_token)
  ├── Delegates to OAuth2AuthorizationCodeAuthenticationProvider
  │     (Validates PKCE code_verifier against code_challenge)
  ├── Invokes OAuth2TokenGenerator pipeline
  └── Returns OAuth2AccessTokenResponse (JSON)
       │
       ▼
[NimbusJwkSetEndpointFilter]
  ├── Intercepts /oauth2/jwks
  └── Serializes public JWKSet from JWKSource to JSON
       │
       ▼
[OAuth2TokenIntrospectionEndpointFilter]
  └── Intercepts /oauth2/introspect and queries OAuth2AuthorizationService
       │
       ▼
[OAuth2TokenRevocationEndpointFilter]
  └── Intercepts /oauth2/revoke and marks token revoked in OAuth2AuthorizationService
       │
       ▼
[OidcUserInfoEndpointFilter]
  └── Intercepts /userinfo and maps authenticated user to standard OIDC claims
```

### Protocol Authentication Providers
Under the hood, `AuthenticationManager` delegates to specialized `AuthenticationProvider` implementations registered by SAS:

1. **`OAuth2AuthorizationCodeRequestAuthenticationProvider`**:
   - Validates client permissions to use the authorization code flow.
   - Enforces exact `redirect_uri` matching against registered URIs.
   - Generates the authorization code value (using `StringKeyGenerator`) and persists it in `OAuth2AuthorizationService`.
2. **`OAuth2AuthorizationCodeAuthenticationProvider`**:
   - Validates the submitted `code` parameter.
   - Checks code expiration (default 5 minutes).
   - If PKCE is present, executes `code_verifier` validation:
     $$\text{Base64Url}(\text{SHA-256}(\text{code\_verifier})) \stackrel{?}{=} \text{code\_challenge}$$
   - Marks the authorization code as consumed (preventing replay).
3. **`OAuth2ClientCredentialsAuthenticationProvider`**:
   - Verifies the client's authority to obtain tokens without a human user.
   - Validates requested scopes against allowed client scopes.
   - Generates and signs the M2M Access Token.
4. **`OAuth2RefreshTokenAuthenticationProvider`**:
   - Validates the submitted refresh token.
   - If `reuseRefreshTokens(false)`, revokes the old refresh token and issues a new one.

---

## 5. Step-by-Step Execution Flow

The sequence below traces the full Authorization Code + PKCE flow across both filter chains, including user login and consent.

```text
Browser / Client App                    Authorization Server                  Resource Server
    │                                            │                                   │
    │ 1. GET /oauth2/authorize                   │                                   │
    │    ?response_type=code                     │                                   │
    │    &client_id=portal-app                   │                                   │
    │    &redirect_uri=https://app/callback      │                                   │
    │    &scope=openid profile orders.read       │                                   │
    │    &code_challenge=E9Melhoa2Owv...         │                                   │
    │    &code_challenge_method=S256             │                                   │
    │───────────────────────────────────────────▶│                                   │
    │                                            │ FilterChain 1:                    │
    │                                            │ User is Unauthenticated!          │
    │                                            │ Saves AuthorizationRequest in     │
    │                                            │ HttpSession.                      │
    │ 2. HTTP 302 Redirect to /login             │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                            │                                   │
    │ 3. GET /login                              │                                   │
    │───────────────────────────────────────────▶│ FilterChain 2 renders Login Page  │
    │ 4. Form HTML returned                      │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                            │                                   │
    │ 5. POST /login (username & password)       │                                   │
    │───────────────────────────────────────────▶│ FilterChain 2:                    │
    │                                            │ DaoAuthenticationProvider checks  │
    │                                            │ BCrypt credentials.               │
    │                                            │ SecurityContext updated.          │
    │ 6. HTTP 302 Redirect to /oauth2/authorize  │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                            │                                   │
    │ 7. GET /oauth2/authorize (Session cookie)  │                                   │
    │───────────────────────────────────────────▶│ FilterChain 1:                    │
    │                                            │ Checks OAuth2AuthorizationConsent-│
    │                                            │ Service. Consent required!        │
    │ 8. HTTP 302 Redirect to /oauth2/consent    │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                            │                                   │
    │ 9. POST /oauth2/consent (approved=true)    │                                   │
    │───────────────────────────────────────────▶│ Saves consent to DB.              │
    │                                            │ Generates authorization_code.     │
    │                                            │ Stores code in OAuth2Authorization│
    │ 10. HTTP 302 to redirect_uri               │                                   │
    │     ?code=SplxlOBeZQQYbYS6WxSbIA           │                                   │
    │     &state=xyz                             │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                            │                                   │
    │ 11. POST /oauth2/token                     │                                   │
    │     grant_type=authorization_code          │                                   │
    │     &code=SplxlOBeZQQYbYS6WxSbIA           │                                   │
    │     &redirect_uri=https://app/callback     │                                   │
    │     &code_verifier=dBjftJeZ4CVP-mB92K27uh  │                                   │
    │     &client_id=portal-app                  │                                   │
    │───────────────────────────────────────────▶│ FilterChain 1:                    │
    │                                            │ • Validates code & expiration     │
    │                                            │ • Computes SHA-256(verifier)      │
    │                                            │   matches stored code_challenge!  │
    │                                            │ • Calls OAuth2TokenCustomizer     │
    │                                            │ • Signs JWT with JWKSource        │
    │ 12. HTTP 200 OK                            │                                   │
    │     { "access_token": "ey...",             │                                   │
    │       "id_token": "ey...",                 │                                   │
    │       "refresh_token": "ey...",            │                                   │
    │       "expires_in": 900 }                  │                                   │
    │◀───────────────────────────────────────────│                                   │
    │                                                                                │
    │ 13. GET /api/orders (Authorization: Bearer ey...)                              │
    │───────────────────────────────────────────────────────────────────────────────▶│
    │                                            │                                   │
    │                                            │ 14. GET /oauth2/jwks (Cached)     │
    │                                            │◀──────────────────────────────────│
    │                                            │ 15. Returns Public Key Ring       │
    │                                            │──────────────────────────────────▶│
    │                                            │                                   │
    │                                            │ Resource Server verifies RS256    │
    │                                            │ signature, checks aud & exp.      │
    │ 16. HTTP 200 OK (Orders JSON)              │                                   │
    │◀───────────────────────────────────────────────────────────────────────────────│
```

---

## 6. Visualizations

### Architecture Component Diagram
The following Mermaid diagram illustrates the exact component relationships within Spring Authorization Server:

```mermaid
graph TD
    Client["OAuth2 Client (Browser/SPA/Service)"] -->|1. /oauth2/authorize| AuthEndpoint["OAuth2AuthorizationEndpointFilter"]
    Client -->|2. /oauth2/token| TokenEndpoint["OAuth2TokenEndpointFilter"]
    Client -->|3. /oauth2/introspect| IntrospectEndpoint["OAuth2TokenIntrospectionEndpointFilter"]
    Client -->|4. /oauth2/revoke| RevokeEndpoint["OAuth2TokenRevocationEndpointFilter"]
    
    AuthEndpoint -->|Lookup Client| ClientRepo["RegisteredClientRepository (JDBC)"]
    TokenEndpoint -->|Lookup Client| ClientRepo
    
    AuthEndpoint -->|Check/Persist Auth| AuthValService["OAuth2AuthorizationService (JDBC)"]
    TokenEndpoint -->|Validate Code/Tokens| AuthValService
    IntrospectEndpoint -->|Query Token State| AuthValService
    RevokeEndpoint -->|Revoke Tokens| AuthValService
    
    AuthEndpoint -->|Check Scope Approval| ConsentService["OAuth2AuthorizationConsentService (JDBC)"]
    
    TokenEndpoint -->|Token Generation| TokenGen["OAuth2TokenGenerator Pipeline"]
    TokenGen -->|Inject Custom Claims| Customizer["OAuth2TokenCustomizer"]
    TokenGen -->|Sign JWT| KeySource["JWKSource (Nimbus RSA KeyRing)"]
    KeySource -->|Serve Public Keys| JwksEndpoint["NimbusJwkSetEndpointFilter (/oauth2/jwks)"]
```

> **Caption:** Core Component Interactions. Every endpoint delegates to shared persistence interfaces (`RegisteredClientRepository`, `OAuth2AuthorizationService`, `OAuth2AuthorizationConsentService`) and the cryptographic token generation engine (`JWKSource`).

### Token Lifecycle State Machine

```mermaid
stateDiagram-v2
    [*] --> Requested: Browser hits /oauth2/authorize
    Requested --> UserLogin: Unauthenticated redirect to /login
    UserLogin --> ConsentRequired: User authenticated, scopes unapproved
    ConsentRequired --> CodeIssued: User grants consent
    Requested --> CodeIssued: Consent previously recorded
    CodeIssued --> TokensMinted: Client POSTs to /oauth2/token with Code + PKCE
    CodeIssued --> Expired: >5 minutes without exchange
    TokensMinted --> Refreshed: Client POSTs Refresh Token
    Refreshed --> TokensMinted: New Access + Rotated Refresh Token issued
    TokensMinted --> Revoked: Client POSTs to /oauth2/revoke
    TokensMinted --> Expired: Token TTL elapsed
    Revoked --> [*]
    Expired --> [*]
```

> **Caption:** State Lifecycle of an Authorization. Tracks transitions from initial authorization request through code issuance, token minting, rotation, and revocation.

---

## 7. Production-Level Use Cases & Real Industry Examples

### Case 1: Multi-Tenant Enterprise B2B SaaS
- **Scenario:** A healthcare SaaS platform serving 500 hospital networks. Each hospital has its own isolated tenant database and custom security roles (`DOCTOR`, `NURSE`, `BILLING_ADMIN`).
- **Solution using SAS:**
  - Clients are registered per enterprise tenant in `JdbcRegisteredClientRepository`.
  - An `OAuth2TokenCustomizer` inspects the authenticated user's organization and injects `tenant_id`, `hospital_npi_code`, and scoped permissions into the JWT Access Token.
  - Downstream microservices enforce data isolation at the API gateway using the `tenant_id` claim, eliminating cross-tenant data leakage.

### Case 2: Open Banking API Gateway with Zero-Trust M2M
- **Scenario:** A tier-1 investment bank exposing payment initiation APIs to certified third-party fintechs (TPPs) under PSD2 / Open Banking regulations.
- **Solution using SAS:**
  - Client authentication mandates `private_key_jwt` (RFC 7523) instead of shared secrets.
  - TPPs register their public JWK Set URL with the bank. During token requests, the TPP sends a signed JWT assertion.
  - SAS resolves the client via `JdbcRegisteredClientRepository`, fetches the TPP's public key from their `jwkSetUrl`, verifies the assertion signature, and issues an Access Token strictly scoped to `payments:transfer`.

### Case 3: Smart TV & Embedded IoT Device Authentication
- **Scenario:** A streaming entertainment provider authenticating native apps on smart TVs and gaming consoles without user keyboards.
- **Solution using SAS:**
  - Utilizes the **Device Authorization Grant** (RFC 8628).
  - The TV calls `/oauth2/device_authorization`, receiving a `device_code` and a human-readable `user_code` (e.g., `BDXR-9912`).
  - The TV displays a QR code. The user scans it with their phone, logs in on the phone browser, and confirms the `user_code`.
  - The TV polls `/oauth2/token` in the background; once the user confirms on mobile, SAS releases the access and refresh tokens to the TV.

---

## 8. Worked Examples (with Output + Dry Run)

---

### Example 1: Authorization Code Flow with PKCE via cURL

#### Step 1: Generate PKCE Verifier and Challenge
Using bash and OpenSSL:
```bash
# 1. Generate a 43-character random string (code_verifier)
CODE_VERIFIER="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"

# 2. Compute Base64URL(SHA256(verifier))
CODE_CHALLENGE=$(echo -n "${CODE_VERIFIER}" | openssl dgst -sha256 -binary | base64 | tr '/+' '_-' | tr -d '=')
echo "Challenge: ${CODE_CHALLENGE}"
# Output: E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM
```

#### Step 2: Request Authorization Code in Browser
Navigate to:
```text
http://localhost:9000/oauth2/authorize?response_type=code&client_id=showcase-client&redirect_uri=http://127.0.0.1:8080/authorized&scope=openid%20profile%20orders.read&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&state=secret-state-xyz
```
- Browser prompts for credentials (`user` / `password`).
- User consents to `orders.read`.
- Browser redirects to:
```text
http://127.0.0.1:8080/authorized?code=Q7k2lMnB-91ZxPlokq991Ad_sJkll12089&state=secret-state-xyz
```

#### Step 3: Exchange Code for Tokens using `client_secret_basic`
```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u "showcase-client:secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=authorization_code" \
  -d "code=Q7k2lMnB-91ZxPlokq991Ad_sJkll12089" \
  -d "redirect_uri=http://127.0.0.1:8080/authorized" \
  -d "code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
```

#### Actual HTTP Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImF1dGgta2V5LTIwMjYifQ.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6WyJvcmRlcnMtc2VydmljZSJdLCJuYmYiOjE3NzM2NjQ1MDAsInJvbGVzIjpbIlJPTEVfVVNFUiJdLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJ0ZW5hbnRfaWQiOiJjdXN0b21lci1hY21lIiwiZXhwIjoxNzczNjY1NDAwLCJpYXQiOjE3NzM2NjQ1MDAsInNjb3BlIjoib3BlbmlkIHByb2ZpbGUgb3JkZXJzLnJlYWQifQ.Z2v7Q1n...",
  "id_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImF1dGgta2V5LTIwMjYifQ.eyJzdWIiOiJ1c2VyMSIsImF1ZCI6WyJzaG93Y2FzZS1jbGllbnQiXSwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo5MDAwIiwicHJlZmVycmVkX3VzZXJuYW1lIjoidXNlcjEiLCJleHAiOjE3NzM2NjU0MDAsImlhdCI6MTc3MzY2NDUwMH0.Kp91bA...",
  "refresh_token": "s1K89_Plokq...",
  "scope": "openid profile orders.read",
  "token_type": "Bearer",
  "expires_in": 900
}
```

---

### Example 2: Client Credentials Grant (M2M)

```bash
curl -X POST http://localhost:9000/oauth2/token \
  -u "backend-daemon:daemon-secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "grant_type=client_credentials" \
  -d "scope=system.audit"
```

#### Actual HTTP Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsImtpZCI6ImF1dGgta2V5LTIwMjYifQ.eyJzdWIiOiJiYWNrZW5kLWRhZW1vbiIsImF1ZCI6WyJhdWRpdC1zZXJ2aWNlIl0sIm5iZiI6MTc3MzY2NDUwMCwicm9sZXMiOltdLCJpc3MiOiJodHRwOi8vbG9jYWxob3N0OjkwMDAiLCJleHAiOjE3NzM2NjU0MDAsImlhdCI6MTc3MzY2NDUwMCwic2NvcGUiOiJzeXN0ZW0uYXVkaXQifQ.K2pL...",
  "token_type": "Bearer",
  "expires_in": 900
}
```
> [!NOTE]
> Notice that the `sub` claim is `backend-daemon` (the client itself). There is **no user** in a Client Credentials grant, and **no refresh token** or ID token is issued.

---

### Example 3: Token Introspection & Revocation

#### Step 1: Introspect Active Access Token
```bash
curl -X POST http://localhost:9000/oauth2/introspect \
  -u "backend-daemon:daemon-secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=eyJhbGciOiJSUzI1NiIs..."
```

Output:
```json
{
  "active": true,
  "client_id": "showcase-client",
  "sub": "user1",
  "exp": 1773665400,
  "iat": 1773664500,
  "iss": "http://localhost:9000",
  "token_type": "Bearer",
  "scope": "openid profile orders.read"
}
```

#### Step 2: Revoke the Token
```bash
curl -X POST http://localhost:9000/oauth2/revoke \
  -u "showcase-client:secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=eyJhbGciOiJSUzI1NiIs..." \
  -d "token_type_hint=access_token"
```
Output: HTTP 200 OK (empty body)

#### Step 3: Re-Introspect Revoked Token
```bash
curl -X POST http://localhost:9000/oauth2/introspect \
  -u "backend-daemon:daemon-secret" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "token=eyJhbGciOiJSUzI1NiIs..."
```
Output:
```json
{
  "active": false
}
```

---

### Example 4: OpenID Connect UserInfo Endpoint Request & Response

A client holding an access token with the `openid` and `profile` scopes queries the `/userinfo` endpoint:

```bash
curl -X GET http://localhost:9000/userinfo \
  -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIs..."
```

#### Actual HTTP Response:
```json
{
  "sub": "user1",
  "name": "Jane Doe",
  "given_name": "Jane",
  "family_name": "Doe",
  "preferred_username": "user1",
  "email": "jane.doe@acme.com",
  "email_verified": true,
  "roles": ["ROLE_USER"]
}
```

---

## 9. Code from Scratch — Production-Grade

The following code provides a production-grade, compilable Spring Boot 3.3+ implementation of Spring Authorization Server 1.2+ using PostgreSQL JDBC persistence, custom JWT claims, and dual security filter chains.

### 9.1 Maven Dependencies (`pom.xml`)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.4</version>
        <relativePath/>
    </parent>

    <groupId>com.acme.identity</groupId>
    <artifactId>auth-server</artifactId>
    <version>1.0.0</version>
    <name>Spring Authorization Server Production Guide</name>

    <properties>
        <java.version>17</java.version>
    </properties>

    <dependencies>
        <!-- Spring Authorization Server Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-oauth2-authorization-server</artifactId>
        </dependency>

        <!-- Standard Web & Security Starter -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-thymeleaf</artifactId>
        </dependency>

        <!-- Database Persistence (PostgreSQL & JDBC) -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-jdbc</artifactId>
        </dependency>
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Production Monitoring -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>
    </dependencies>
</project>
```

---

### 9.2 Production Database Schema (PostgreSQL DDL)

Save as `src/main/resources/schema.sql`:

```sql
-- Table: oauth2_registered_client
CREATE TABLE IF NOT EXISTS oauth2_registered_client (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    client_id_issued_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP NOT NULL,
    client_secret VARCHAR(200) DEFAULT NULL,
    client_secret_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    client_name VARCHAR(200) NOT NULL,
    client_authentication_methods VARCHAR(1000) NOT NULL,
    authorization_grant_types VARCHAR(1000) NOT NULL,
    redirect_uris VARCHAR(1000) DEFAULT NULL,
    post_logout_redirect_uris VARCHAR(1000) DEFAULT NULL,
    scopes VARCHAR(1000) NOT NULL,
    client_settings VARCHAR(2000) NOT NULL,
    token_settings VARCHAR(2000) NOT NULL,
    CONSTRAINT uk_registered_client_client_id UNIQUE (client_id)
);

-- Table: oauth2_authorization
CREATE TABLE IF NOT EXISTS oauth2_authorization (
    id VARCHAR(100) NOT NULL PRIMARY KEY,
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorization_grant_type VARCHAR(100) NOT NULL,
    authorized_scopes VARCHAR(1000) DEFAULT NULL,
    attributes TEXT DEFAULT NULL,
    state VARCHAR(500) DEFAULT NULL,
    authorization_code_value TEXT DEFAULT NULL,
    authorization_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    authorization_code_metadata TEXT DEFAULT NULL,
    access_token_value TEXT DEFAULT NULL,
    access_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    access_token_metadata TEXT DEFAULT NULL,
    access_token_type VARCHAR(100) DEFAULT NULL,
    access_token_scopes VARCHAR(1000) DEFAULT NULL,
    oidc_id_token_value TEXT DEFAULT NULL,
    oidc_id_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    oidc_id_token_metadata TEXT DEFAULT NULL,
    refresh_token_value TEXT DEFAULT NULL,
    refresh_token_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    refresh_token_metadata TEXT DEFAULT NULL,
    user_code_value TEXT DEFAULT NULL,
    user_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    user_code_metadata TEXT DEFAULT NULL,
    device_code_value TEXT DEFAULT NULL,
    device_code_issued_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_expires_at TIMESTAMP WITH TIME ZONE DEFAULT NULL,
    device_code_metadata TEXT DEFAULT NULL
);

-- Performance Indexes on oauth2_authorization
CREATE INDEX IF NOT EXISTS idx_oauth2_auth_code ON oauth2_authorization(authorization_code_value);
CREATE INDEX IF NOT EXISTS idx_oauth2_access_token ON oauth2_authorization(access_token_value);
CREATE INDEX IF NOT EXISTS idx_oauth2_refresh_token ON oauth2_authorization(refresh_token_value);
CREATE INDEX IF NOT EXISTS idx_oauth2_state ON oauth2_authorization(state);

-- Table: oauth2_authorization_consent
CREATE TABLE IF NOT EXISTS oauth2_authorization_consent (
    registered_client_id VARCHAR(100) NOT NULL,
    principal_name VARCHAR(200) NOT NULL,
    authorities VARCHAR(1000) NOT NULL,
    PRIMARY KEY (registered_client_id, principal_name)
);
```

---

### 9.3 Authorization Server Configuration (`AuthorizationServerConfig.java`)

```java
package com.acme.identity.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.*;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Configuration
@EnableWebSecurity
public class AuthorizationServerConfig {

    /**
     * Filter Chain 1: Protocol Security Filter Chain
     * Dedicated strictly to OAuth2 / OIDC endpoints.
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);

        http.getConfigurer(OAuth2AuthorizationServerConfigurer.class)
                .oidc(Customizer.withDefaults()) // Enable OpenID Connect 1.0
                .authorizationEndpoint(endpoint -> 
                        endpoint.consentPage("/oauth2/consent")); // Custom Consent View

        http
            // Redirect unauthenticated requests hitting /oauth2/authorize to the login page
            .exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                    new LoginUrlAuthenticationEntryPoint("/login"),
                    new MediaTypeRequestMatcher(MediaType.TEXT_HTML)
                )
            )
            // Accept access tokens for User Info and Client Registration endpoints
            .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()));

        return http.build();
    }

    /**
     * Filter Chain 2: Default Application Security Filter Chain
     * Handles user login, consent UI, and static resources.
     */
    @Bean
    @Order(2)
    public SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/login", "/error", "/css/**", "/images/**").permitAll()
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            );

        return http.build();
    }

    /**
     * Password Encoder using DelegatingPasswordEncoder ({bcrypt}, {argon2}, etc.)
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * RegisteredClientRepository backed by PostgreSQL JDBC
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        JdbcRegisteredClientRepository repository = new JdbcRegisteredClientRepository(jdbcTemplate);

        // Pre-seed an initial client if repository is empty (Dev / Bootstrap helper)
        if (repository.findByClientId("showcase-client") == null) {
            RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                    .clientId("showcase-client")
                    .clientSecret(passwordEncoder.encode("secret"))
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
                    .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .redirectUri("http://127.0.0.1:8080/authorized")
                    .redirectUri("https://oauth.pstmn.io/v1/callback")
                    .postLogoutRedirectUri("http://127.0.0.1:8080/logged-out")
                    .scope(OidcScopes.OPENID)
                    .scope(OidcScopes.PROFILE)
                    .scope("orders.read")
                    .scope("orders.write")
                    .clientSettings(ClientSettings.builder()
                            .requireAuthorizationConsent(true)
                            .requireProofKey(true) // Enforce PKCE!
                            .build())
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(Duration.ofMinutes(15))
                            .refreshTokenTimeToLive(Duration.ofDays(30))
                            .reuseRefreshTokens(false) // Refresh Token Rotation
                            .accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED) // JWT
                            .build())
                    .build();
            repository.save(client);
        }

        return repository;
    }

    /**
     * Authorization Service backed by PostgreSQL JDBC
     */
    @Bean
    public OAuth2AuthorizationService authorizationService(JdbcTemplate jdbcTemplate, RegisteredClientRepository clientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcTemplate, clientRepository);
    }

    /**
     * Authorization Consent Service backed by PostgreSQL JDBC
     */
    @Bean
    public OAuth2AuthorizationConsentService authorizationConsentService(JdbcTemplate jdbcTemplate, RegisteredClientRepository clientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, clientRepository);
    }

    /**
     * Custom JWT Claims Generator using OAuth2TokenCustomizer
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer() {
        return context -> {
            Authentication principal = context.getPrincipal();
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                // Map Spring Security Authorities to 'roles' claim
                Set<String> authorities = principal.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .collect(Collectors.toSet());
                context.getClaims().claim("roles", authorities);

                // Inject Custom Tenant ID & Organization
                context.getClaims().claim("tenant_id", "customer-acme");
                context.getClaims().claim("iss_env", "production-us-east-1");

                // Target Audience
                context.getClaims().audience(List.of("orders-service", "billing-service"));
            }
        };
    }

    /**
     * Cryptographic Key Management (RSA 2048-bit Key Pair)
     */
    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAPublicKey publicKey = (RSAPublicKey) keyPair.getPublic();
        RSAPrivateKey privateKey = (RSAPrivateKey) keyPair.getPrivate();

        RSAKey rsaKey = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID("auth-key-2026")
                .build();

        JWKSet jwkSet = new JWKSet(rsaKey);
        return new ImmutableJWKSet<>(jwkSet);
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA KeyPair", ex);
        }
    }

    /**
     * Authorization Server Settings (Issuer & Endpoint Paths)
     */
    @Bean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer("http://localhost:9000") // Canonical public issuer URL
                .build();
    }
}
```

---

### 9.4 UserDetailsService & Test User Configuration

```java
package com.acme.identity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class UserManagementConfig {

    @Bean
    public UserDetailsService userDetailsService(PasswordEncoder passwordEncoder) {
        UserDetails user = User.builder()
                .username("user1")
                .password(passwordEncoder.encode("password"))
                .roles("USER")
                .build();

        UserDetails admin = User.builder()
                .username("admin1")
                .password(passwordEncoder.encode("admin-pass"))
                .roles("ADMIN", "USER")
                .build();

        return new InMemoryUserDetailsManager(user, admin);
    }
}
```

---

### 9.5 Custom Consent Controller (`AuthorizationConsentController.java`)

```java
package com.acme.identity.web;

import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.security.Principal;
import java.util.*;

@Controller
public class AuthorizationConsentController {

    private final RegisteredClientRepository clientRepository;
    private final OAuth2AuthorizationConsentService consentService;

    public AuthorizationConsentController(RegisteredClientRepository clientRepository,
                                          OAuth2AuthorizationConsentService consentService) {
        this.clientRepository = clientRepository;
        this.consentService = consentService;
    }

    @GetMapping("/oauth2/consent")
    public String consent(Principal principal,
                          Model model,
                          @RequestParam(OAuth2ParameterNames.CLIENT_ID) String clientId,
                          @RequestParam(OAuth2ParameterNames.SCOPE) String scope,
                          @RequestParam(OAuth2ParameterNames.STATE) String state) {

        RegisteredClient client = this.clientRepository.findByClientId(clientId);
        if (client == null) {
            throw new IllegalArgumentException("Invalid client: " + clientId);
        }

        Set<String> scopesToApprove = new LinkedHashSet<>();
        Set<String> previouslyApprovedScopes = new LinkedHashSet<>();

        OAuth2AuthorizationConsent currentConsent = this.consentService.findById(client.getId(), principal.getName());
        Set<String> authorizedScopes = (currentConsent != null) ? currentConsent.getScopes() : Collections.emptySet();

        for (String requestedScope : StringUtils.delimitedListToStringArray(scope, " ")) {
            if (authorizedScopes.contains(requestedScope)) {
                previouslyApprovedScopes.add(requestedScope);
            } else {
                scopesToApprove.add(requestedScope);
            }
        }

        model.addAttribute("clientId", clientId);
        model.addAttribute("clientName", client.getClientName());
        model.addAttribute("state", state);
        model.addAttribute("scopes", scopesToApprove);
        model.addAttribute("previouslyApprovedScopes", previouslyApprovedScopes);
        model.addAttribute("principalName", principal.getName());

        return "consent";
    }
}
```

---

### 9.6 Custom Thymeleaf Templates

#### Custom Login Template (`src/main/resources/templates/login.html`)
```html
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="https://www.thymeleaf.org">
<head>
    <title>Acme Identity — Sign In</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f4f6f9; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
        .card { background: white; padding: 2.5rem; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); width: 100%; max-width: 400px; }
        h2 { margin-top: 0; color: #1a202c; font-size: 1.5rem; }
        .form-group { margin-bottom: 1.25rem; }
        label { display: block; margin-bottom: 0.5rem; color: #4a5568; font-size: 0.875rem; font-weight: 500; }
        input[type="text"], input[type="password"] { width: 100%; padding: 0.65rem; border: 1px solid #cbd5e0; border-radius: 4px; box-sizing: border-box; }
        button { width: 100%; padding: 0.75rem; background: #3182ce; color: white; border: none; border-radius: 4px; font-weight: 600; cursor: pointer; }
        button:hover { background: #2b6cb0; }
        .error { color: #e53e3e; margin-bottom: 1rem; font-size: 0.875rem; }
    </style>
</head>
<body>
<div class="card">
    <h2>Sign in to Acme Identity</h2>
    <div th:if="${param.error}" class="error">
        Invalid username or password. Please try again.
    </div>
    <form th:action="@{/login}" method="post">
        <div class="form-group">
            <label for="username">Username</label>
            <input type="text" id="username" name="username" autofocus="autofocus" required />
        </div>
        <div class="form-group">
            <label for="password">Password</label>
            <input type="password" id="password" name="password" required />
        </div>
        <button type="submit">Sign In</button>
    </form>
</div>
</body>
</html>
```

#### Custom Consent Template (`src/main/resources/templates/consent.html`)
```html
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:th="https://www.thymeleaf.org">
<head>
    <title>Authorize Application — Acme Identity</title>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <style>
        body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #f4f6f9; display: flex; justify-content: center; align-items: center; min-height: 100vh; margin: 0; }
        .card { background: white; padding: 2.5rem; border-radius: 8px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); width: 100%; max-width: 480px; }
        h2 { margin-top: 0; color: #1a202c; font-size: 1.35rem; }
        p { color: #4a5568; line-height: 1.5; font-size: 0.95rem; }
        .scope-item { display: flex; align-items: center; margin-bottom: 0.75rem; padding: 0.5rem; background: #edf2f7; border-radius: 4px; }
        .scope-item input { margin-right: 0.75rem; }
        .actions { display: flex; gap: 1rem; margin-top: 1.5rem; }
        button { flex: 1; padding: 0.75rem; border: none; border-radius: 4px; font-weight: 600; cursor: pointer; }
        .btn-approve { background: #38a169; color: white; }
        .btn-cancel { background: #e2e8f0; color: #4a5568; }
    </style>
</head>
<body>
<div class="card">
    <h2>Application Authorization</h2>
    <p>The application <strong th:text="${clientName}">App</strong> wants access to your account (<span th:text="${principalName}">User</span>).</p>
    
    <form name="consent_form" method="post" th:action="@{/oauth2/authorize}">
        <input type="hidden" name="client_id" th:value="${clientId}" />
        <input type="hidden" name="state" th:value="${state}" />

        <div th:if="${not #sets.isEmpty(scopes)}">
            <p><strong>Review requested permissions:</strong></p>
            <div th:each="scope: ${scopes}" class="scope-item">
                <input type="checkbox" name="scope" th:value="${scope}" th:id="${scope}" checked="checked" />
                <label th:for="${scope}" th:text="${scope}">Scope Name</label>
            </div>
        </div>

        <div th:if="${not #sets.isEmpty(previouslyApprovedScopes)}" style="margin-top: 1rem;">
            <p style="font-size: 0.85rem; color: #718096;">Previously authorized permissions:</p>
            <ul>
                <li th:each="scope: ${previouslyApprovedScopes}" th:text="${scope}" style="font-size: 0.85rem; color: #718096;"></li>
            </ul>
        </div>

        <div class="actions">
            <button type="submit" name="consent_action" value="cancel" class="btn-cancel">Cancel</button>
            <button type="submit" name="consent_action" value="approve" class="btn-approve">Authorize</button>
        </div>
    </form>
</div>
</body>
</html>
```

---

## 10. Common Mistakes & Gotchas

### 1. The Single Filter Chain Trap
- **❌ Wrong:** Configuring protocol endpoints and form login within a single `SecurityFilterChain` bean.
- **Why it Fails:** The `OAuth2AuthorizationServerConfigurer` expects to manage its own request matchers and exception handling at `HIGHEST_PRECEDENCE`. Merging them causes authorization endpoint matchers to be intercepted by standard web filters, resulting in infinite login redirects or CSRF rejections at `/oauth2/token`.
- **✅ Right:** Always declare two distinct `@Bean SecurityFilterChain` methods with `@Order(Ordered.HIGHEST_PRECEDENCE)` and `@Order(2)`.

### 2. Ephemeral In-Memory Keys on Pod Restart
- **❌ Wrong:** Using `generateRsaKey()` directly inside `@Bean public JWKSource jwkSource()` without persistent backing.
- **Why it Fails:** Every time the Spring Boot container restarts or a new pod spins up in Kubernetes, a new RSA key pair is generated. All previously issued, unexpired JWT access tokens immediately fail signature verification across all Resource Servers with:
  `JwtVerificationException: Invalid signature`.
- **✅ Right:** Load keys from a secure PKCS12 / JKS keystore, HashiCorp Vault, AWS Secrets Manager, or an encrypted database table.

### 3. Plaintext Client Secrets
- **❌ Wrong:** Saving `clientSecret("secret")` without password encoding prefix.
- **Why it Fails:** Spring Security 6.x defaults to `DelegatingPasswordEncoder`. When the client submits its secret at `/oauth2/token`, Spring attempts to extract the encoder prefix (e.g., `{bcrypt}`) and throws:
  `java.lang.IllegalArgumentException: There is no PasswordEncoder mapped for the id "null"`.
- **✅ Right:** Always encode client secrets using `passwordEncoder.encode("secret")` before persisting to `RegisteredClientRepository`.

### 4. Mismatched Issuer URI Behind Reverse Proxies
- **❌ Wrong:** Omitting `authorizationServerSettings.issuer(...)` when running behind Nginx, AWS ALB, or Cloudflare.
- **Why it Fails:** SAS automatically discovers the issuer from the incoming HTTP request (`HttpServletRequest.getRequestURL()`). If the reverse proxy forwards requests from `https://auth.acme.com` to `http://internal-ip:9000`, the generated tokens will have `iss: "http://internal-ip:9000"`. Downstream resource servers validating `https://auth.acme.com` reject the token due to issuer mismatch.
- **✅ Right:** Either set an explicit canonical issuer via `AuthorizationServerSettings.builder().issuer("https://auth.acme.com").build()`, or configure `server.forward-headers-strategy=native` in `application.yml` and ensure your ingress sets `X-Forwarded-Proto` and `X-Forwarded-Host`.

### 5. Refresh Token Reuse Vulnerabilities
- **❌ Wrong:** Leaving `tokenSettings.reuseRefreshTokens(true)` in production.
- **Why it Fails:** If an attacker intercepts a refresh token, they can silently mint new access tokens indefinitely while the legitimate user is unaware.
- **✅ Right:** Set `reuseRefreshTokens(false)`. Under OAuth 2.1, when a refresh token is presented, SAS issues a new refresh token and invalidates the previous one. If a revoked refresh token is presented a second time, SAS triggers **Automatic Reuse Detection** and invalidates the entire authorization family.

### 6. PII Leakage in Access Tokens
- **❌ Wrong:** Injecting user home address, phone numbers, and Social Security Numbers into the JWT Access Token in `OAuth2TokenCustomizer`.
- **Why it Fails:** Access tokens pass through multiple intermediate proxies, API gateways, and logs. Anyone possessing the token can decode the unencrypted base64url payload.
- **✅ Right:** Keep Access Token claims minimal (`sub`, `roles`, `tenant_id`, `aud`). Place detailed user profile data into the OpenID Connect ID Token or serve it via the `/userinfo` endpoint.

### 7. Missing Database Indexes on `oauth2_authorization`
- **❌ Wrong:** Creating the `oauth2_authorization` table without indexes on `authorization_code_value`, `access_token_value`, and `refresh_token_value`.
- **Why it Fails:** In production, this table grows rapidly. When `/oauth2/token` is called to redeem a code or refresh a token, PostgreSQL performs a full table scan, degrading token endpoint throughput from 2000 req/sec to 10 req/sec.
- **✅ Right:** Create explicit B-tree indexes on `authorization_code_value`, `access_token_value`, `refresh_token_value`, and `state`.

### 8. Hardcoded Redirect URIs in Localhost Dev Leaking to Production
- **❌ Wrong:** Registering `http://localhost:3000/callback` in production client records.
- **Why it Fails:** Open redirect vulnerability allowing malicious actors to capture authorization codes by triggering redirects to local browser instances.
- **✅ Right:** Strict environment separation: enforce HTTPS URLs in production client records and use separate client credentials for staging and production.

---

## 11. Performance Considerations

```text
CRITICAL PERFORMANCE BOTTLENECKS IN AUTHORIZATION SERVERS
─────────────────────────────────────────────────────────────────────────────
① RSA-2048 CRYPTOGRAPHIC SIGNING
   • RSA private-key signing is CPU-intensive (~1.5ms per token on modern vCPU).
   • Solution: Benchmark ES256 (ECDSA P-256) which yields ~4x faster signing throughput.

② JDBC AUTHORIZATION TABLE EXPLOSION
   • Every token exchange creates rows in `oauth2_authorization`.
   • High traffic without scheduled cleanup causes table to reach millions of rows.
   • Solution: Mandatory indexing on `access_token_value` & `refresh_token_value`.
   • Run a nightly cron job: DELETE FROM oauth2_authorization WHERE access_token_expires_at < NOW() - INTERVAL '7 DAYS'.

③ JWKS ENDPOINT CACHING
   • Resource servers fetch /oauth2/jwks on startup and during cache misses.
   • Solution: Set HTTP header Cache-Control: public, max-age=86400, stale-while-revalidate=3600.
```

### HikariCP Connection Pool Sizing
During peak login spikes (e.g., 9:00 AM enterprise start-of-day), the `/oauth2/token` endpoint incurs multiple JDBC reads and writes per transaction (reading client, reading authorization, updating tokens). Configure HikariCP to avoid thread starvation:

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 30
      minimum-idle: 10
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
```

---

## 12. Security Considerations

Follow the **OAuth 2.0 Security Best Current Practice (RFC 9700 / BCP)**:

1. **Strict Exact Redirect URI Matching:**
   Never allow wildcards (e.g., `https://*.acme.com/callback`) or regexes in `redirectUris`. Wildcards enable open-redirect and authorization code injection attacks.
2. **Mandatory PKCE for All Clients:**
   Even for confidential clients possessing a client secret, enforce PKCE via `ClientSettings.builder().requireProofKey(true).build()`. This mitigates code interception across network boundaries.
3. **Short-Lived Access Tokens:**
   Configure Access Token TTL between 5 and 15 minutes (`Duration.ofMinutes(15)`). Never issue access tokens with multi-hour lifespans.
4. **Sender-Constrained Tokens:**
   For high-security banking APIs, bind access tokens to client TLS certificates using **Mutual TLS (mTLS) Profile for OAuth 2.0 (RFC 8705)** or DPoP (Demonstrating Proof-of-Possession at the Application Layer, RFC 9449).
5. **Constant-Time Secret Verification:**
   Ensure password hashing and client secret comparisons execute in constant time to prevent timing attacks against credentials.

---

## 13. Best Practices

- **Externalize Identity Configuration:** Keep client IDs, secrets, and redirect URIs in database tables managed by an Admin API, not hardcoded in Java beans.
- **Separate Identity and Resource Concerns:** Do not host business domain REST APIs (`/api/v1/orders`) inside the Authorization Server application. Keep the Authorization Server strictly isolated as an identity microservice.
- **Audience (`aud`) Claim Validation:** Always inject explicit audience claims in your `OAuth2TokenCustomizer`. Prevent tokens minted for Service A from being accepted by Service B.
- **Structured Audit Logging:** Log all authorization attempts, token exchanges, client authentication failures, and revocation events with structured JSON logs containing correlation IDs.
- **Graceful Token Revocation Cascade:** Revoking a parent authorization or refresh token must immediately mark all corresponding child access tokens as revoked in `OAuth2AuthorizationService`.

---

## 14. Hands-On Exercises & Code Challenges

### Exercise 1: Implement Zero-Downtime Key Rotation
- **Goal:** Modify the `JWKSource` bean to read multiple keys from a database or JSON file.
- **Task:** Configure Key A (`kid: 2026-q1`) and Key B (`kid: 2026-q2`). Make Key B the primary signing key for new tokens, while publishing both Key A and Key B at `/oauth2/jwks`. Verify that existing tokens signed with Key A continue to validate against a Resource Server.

### Exercise 2: Implement a Custom M2M Grant Type (`urn:acme:params:oauth:grant-type:service-impersonation`)
- **Goal:** Create an extension grant allowing an admin service to request a delegated token on behalf of a user.
- **Task:** Implement `AuthenticationConverter` to parse request parameters, create a custom `Authentication` token, implement `AuthenticationProvider` to validate permissions, and register them via `http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).tokenEndpoint(...)`.

### Exercise 3: Dynamic Multi-Tenant Issuer Routing
- **Goal:** Support dynamic issuer URLs per enterprise customer (e.g., `https://auth.acme.com/tenants/tenant-a` and `https://auth.acme.com/tenants/tenant-b`).
- **Task:** Implement a custom `AuthorizationServerSettings` or custom request filter that resolves tenant paths dynamically, isolates registered clients per tenant ID, and sets the `iss` claim accordingly.

---

## 15. Interview Q&A — Level-wise

---

### 🟢 Beginner

#### Q1: What is Spring Authorization Server, and how does it relate to the old `spring-security-oauth2`?
**A:** Spring Authorization Server (SAS) is the official framework supported by the Spring Security team for creating OAuth 2.1 and OpenID Connect 1.0 compliant authorization servers in Java. The old `spring-security-oauth2` library was deprecated because it mixed Client, Resource Server, and Authorization Server concerns, lacked OIDC support, and implemented older, insecure RFC 6749 patterns. SAS is a modular, secure, ground-up rewrite built on Spring Security 6.x.

#### Q2: What are the two essential SecurityFilterChain beans required in a Spring Authorization Server app?
**A:** 
1. **Protocol Chain (`@Order(Ordered.HIGHEST_PRECEDENCE)`):** Captures requests targeting OAuth 2.1 protocol endpoints (`/oauth2/authorize`, `/oauth2/token`, `/oauth2/jwks`, etc.) using `OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http)`.
2. **Default Application Chain (`@Order(2)`):** Handles normal web security, including the `/login` page and form authentication for the human user.

#### Q3: What is the purpose of the `RegisteredClient` class?
**A:** `RegisteredClient` is the central domain entity that represents a registered client application. It holds the client ID, encrypted client secret, allowed grant types, authentication methods, redirect URIs, scopes, and specific client and token settings (such as token TTL and PKCE enforcement).

#### Q4: Why is an in-memory `RegisteredClientRepository` unsuitable for production?
**A:** `InMemoryRegisteredClientRepository` stores client definitions in a local in-memory map. When the application restarts or crashes, all dynamically registered clients and configuration updates are lost. Furthermore, in-memory repositories cannot be shared across multiple instances in a clustered or load-balanced environment. Production requires `JdbcRegisteredClientRepository`.

#### Q5: What grant types are supported by modern Spring Authorization Server?
**A:** SAS supports `authorization_code`, `client_credentials`, `refresh_token`, and `urn:ietf:params:oauth:grant-type:device_code` (Device Flow). The legacy `implicit` and `password` (ROPC) grant types are intentionally omitted as they are prohibited under OAuth 2.1.

---

### 🟡 Intermediate

#### Q6: How does Spring Authorization Server handle the transition from unauthenticated user to logged-in user during the Authorization Code flow?
**A:** When an unauthenticated browser sends a request to `/oauth2/authorize`, the Protocol Filter Chain intercepts it. Finding no authenticated `SecurityContext`, its `LoginUrlAuthenticationEntryPoint` captures the incoming `OAuth2AuthorizationRequest`, serializes it into the `HttpSession`, and redirects the user to `/login`. Once the user successfully authenticates on the Application Filter Chain, Spring Security's `SavedRequestAwareAuthenticationSuccessHandler` redirects the browser back to the original `/oauth2/authorize` URL, where the Protocol Chain resumes processing.

#### Q7: How do you add custom claims (like `tenant_id` or user roles) to an Access Token in SAS?
**A:** By declaring a bean of type `OAuth2TokenCustomizer<JwtEncodingContext>`. Inside the customizer, check `context.getTokenType().equals(OAuth2TokenType.ACCESS_TOKEN)`, extract the authenticated `Principal`, read custom attributes, and add them via `context.getClaims().claim("claim_name", value)`.

#### Q8: What is the difference between `OAuth2AuthorizationService` and `OAuth2AuthorizationConsentService`?
**A:** 
- `OAuth2AuthorizationService` manages the state of actual authorizations: active authorization codes, issued access tokens, refresh tokens, and their respective lifespans and states.
- `OAuth2AuthorizationConsentService` manages the persistent record of a user's explicit consent decisions (which scopes a specific user has granted to a specific client), preventing the user from having to approve the consent screen on every subsequent login.

#### Q9: How does SAS support PKCE, and how can it be enforced for confidential clients?
**A:** SAS includes built-in PKCE validation in `OAuth2AuthorizationCodeAuthenticationProvider`. For public clients (`ClientAuthenticationMethod.NONE`), PKCE is mandatory by specification. For confidential clients, PKCE can be strictly enforced by configuring `ClientSettings.builder().requireProofKey(true).build()` on the `RegisteredClient`.

#### Q10: What is the function of `AuthorizationServerSettings`?
**A:** `AuthorizationServerSettings` (formerly `ProviderSettings`) encapsulates the URI paths for all standard OAuth2 and OpenID Connect endpoints (`authorizationEndpoint`, `tokenEndpoint`, `jwkSetEndpoint`, `tokenRevocationEndpoint`, `tokenIntrospectionEndpoint`, `oidcUserInfoEndpoint`). Most importantly, it configures the canonical `issuer` URL used in token metadata and verification.

---

### 🔴 Advanced / Senior

#### Q11: How do you implement zero-downtime cryptographic key rotation in Spring Authorization Server?
**A:** You implement an asymmetric key-ring pattern in your `JWKSource<SecurityContext>`. The key ring holds two (or more) RSA keys:
1. The **Active Key** (e.g., `kid: 2026-primary`): Used by SAS to sign all newly issued JWTs.
2. The **Retiring Key** (e.g., `kid: 2025-retiring`): Public key published in `/oauth2/jwks` so resource servers can verify tokens issued prior to rotation.
When rotating: generate Key C, publish Key C and B at `/oauth2/jwks`, make Key C the active signing key, and after Key A's maximum token TTL has passed, remove Key A from the JWKS.

#### Q12: What is `private_key_jwt` client authentication, and why is it superior to `client_secret_basic`?
**A:** `private_key_jwt` (RFC 7523) authenticates a client without transmitting a shared secret across the wire. The client signs a short-lived JWT assertion using its own private key and sends it in the `client_assertion` parameter. SAS fetches or looks up the client's public key (via `ClientSettings.jwkSetUrl`) to verify the signature. It is superior because:
1. No shared secret exists in the Authorization Server's database.
2. Compromise of transmission logs or database dumps does not expose credentials capable of forging client requests.

#### Q13: How do you configure SAS to run correctly behind an API Gateway / Reverse Proxy with TLS termination?
**A:** When TLS is terminated at a reverse proxy, the internal request arrives as HTTP on port 8080. SAS will by default use this internal URL as the `iss` (issuer) claim in JWTs, breaking client validation. To fix this:
1. Set `server.forward-headers-strategy=native` (or register a `ForwardedHeaderFilter`) so Spring respects `X-Forwarded-Proto`, `X-Forwarded-Host`, and `X-Forwarded-Port`.
2. Explicitly define the canonical public issuer URI in `AuthorizationServerSettings.builder().issuer("https://auth.acme.com").build()`.

#### Q14: How does Refresh Token Rotation work in SAS, and what is Automatic Reuse Detection?
**A:** When `TokenSettings.builder().reuseRefreshTokens(false)` is configured, SAS invalidates the submitted refresh token upon exchange and issues a brand-new refresh token alongside the new access token. If an attacker and victim both possess the old refresh token and the attacker attempts to redeem it after the victim has already rotated it, SAS detects that a revoked refresh token is being used. Under RFC 6749 / BCP guidelines, SAS immediately revokes all access tokens and refresh tokens belonging to that authorization chain.

#### Q15: What is the difference between `OAuth2TokenFormat.SELF_CONTAINED` and `OAuth2TokenFormat.REFERENCE`?
**A:** 
- `SELF_CONTAINED` generates signed JSON Web Tokens (JWTs) containing all user claims and scopes. Resource servers validate these tokens locally using the authorization server's JWKS without calling the server.
- `REFERENCE` generates opaque strings (e.g., UUIDs) with no encoded claims. Resource servers must call `/oauth2/introspect` (RFC 7662) on every API request to validate the token and obtain user metadata.

---

### ⚫ Expert / Principal

#### Q16: In a multi-region active-active deployment of SAS, how do you handle `OAuth2AuthorizationService` persistence and authorization code exchange latency?
**A:** Authorization codes have an extremely short lifespan (e.g., 60 seconds) and can only be used once. If a code is issued in `us-east` and the client's token request hits `eu-west` due to geo-DNS routing, cross-region DB replication lag can cause a race condition (`code not found` or duplicate redemption).
- **Architecture Strategy:**
  1. Route all protocol token exchanges for a given session/authorization to the originating region using session affinity or state routing encoded in the `code` value (e.g., `us_east_SplxlOBeZQQYbYS6WxSbIA`).
  2. Use globally distributed multi-region databases (e.g., CockroachDB, AWS Aurora Global) with strong consistency for the `oauth2_authorization` table.
  3. Pre-generate and synchronize public/private signing keys across regions so any region can sign or verify tokens with identical `kid`.

#### Q17: How would you extend SAS to implement RFC 8693 (OAuth 2.0 Token Exchange) for zero-trust microservice propagation?
**A:**
1. Implement a custom `AuthenticationConverter` (`OAuth2TokenExchangeAuthenticationConverter`) to parse `grant_type=urn:ietf:params:oauth:grant-type:token-exchange`, `subject_token`, and `audience`.
2. Implement a custom `AuthenticationToken` (`OAuth2TokenExchangeAuthenticationToken`).
3. Implement an `AuthenticationProvider` that validates the caller client, verifies the incoming `subject_token` via `JwtDecoder`, checks tenant policies, and calls `OAuth2TokenGenerator` to issue an audience-restricted downstream token.
4. Register the custom converter and provider in `http.getConfigurer(OAuth2AuthorizationServerConfigurer.class).tokenEndpoint(endpoint -> ...)`.

#### Q18: How do you secure the `/oauth2/jwks` endpoint against denial of service and cache-poisoning attacks?
**A:**
1. **HTTP Caching Directives:** Add strict `Cache-Control: public, max-age=86400, stale-while-revalidate=3600` headers via a custom response filter so CDNs (Cloudflare/CloudFront) and downstream resource servers cache public keys.
2. **Rate Limiting:** Protect `/oauth2/jwks` at the gateway level using Bucket4j or Redis rate limiters.
3. **Response Immutability:** Ensure the `NimbusJwkSetEndpointFilter` reads from an in-memory `ImmutableJWKSet` rather than reloading from disk or DB per request.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1: Immediate Session Revocation across Microservices
- **Problem:** An employee's laptop is stolen. An administrator clicks "Terminate All Sessions" in the admin console. Because all downstream microservices validate JWTs locally using cached JWKS, the stolen JWT access token remains valid until its 15-minute expiration.
- **Production Solution:**
  1. Admin console calls SAS to revoke all tokens in `oauth2_authorization` and updates a Redis "revocation blocklist" keyed by `user_uuid` with a timestamp `revoked_at`.
  2. SAS publishes a "UserRevocationEvent" over Apache Kafka.
  3. Downstream API Gateways (Spring Cloud Gateway) subscribe to the Kafka event or check the Redis blocklist during request filtering. If `jwt.iat < user.revoked_at`, the gateway rejects the request with HTTP 401 Unauthorized immediately, mitigating the 15-minute window without burdening individual microservices.

### Scenario 2: Container Restart Causes 401 Invalid Signature in Staging
- **Problem:** Every time the staging Kubernetes deployment of SAS scales or updates, existing frontend users receive `401 Unauthorized` errors when calling backend services.
- **Root Cause Analysis:** The team configured `JWKSource` using an ephemeral `generateRsaKey()` in memory. When Pod A restarted, it minted a new RSA key pair. Backend resource servers continued validating tokens using the old public key cached from `/oauth2/jwks`.
- **Resolution:**
  - Migrated RSA keys into AWS Secrets Manager.
  - Implemented a custom `JWKSource` that loads the persistent RSA private key and assigns a fixed `kid: "staging-2026-v1"`.
  - Configured backend resource servers with Spring Boot property `spring.security.oauth2.resourceserver.jwt.jwk-set-uri` and appropriate HTTP caching headers.

### Scenario 3: Custom PBKDF2 Password Legacy Migration
- **Problem:** A company migrating a 10-year-old monolithic app to SAS has 2 million users whose passwords are encrypted using a custom PBKDF2 algorithm with unique per-user salts.
- **Production Solution:**
  - Registered a custom `PasswordEncoder` implementation in Spring Security:
    ```java
    @Bean
    public PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new HashMap<>();
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("legacy-pbkdf2", new LegacyPbkdf2PasswordEncoder());
        return new DelegatingPasswordEncoder("bcrypt", encoders);
    }
    ```
  - Stored existing user password hashes with the prefix `{legacy-pbkdf2}`.
  - In the custom `UserDetailsService`, when a user successfully logs in, the service detects the legacy prefix, re-hashes the plaintext password with `{bcrypt}`, updates the database record, and future logins execute on modern BCrypt transparently.

### Scenario 4: High-Throughput M2M Token Flooding
- **Problem:** A B2B partner microservice makes 3,000 requests/second to `/oauth2/token` using the Client Credentials grant, obtaining a new access token for every single outbound HTTP call, overwhelming the database with write locks on `oauth2_authorization`.
- **Root Cause:** The client application was poorly designed and did not cache access tokens locally.
- **Resolution:**
  - Educated partner engineering team on token lifecycle: tokens have a 15-minute TTL and MUST be cached in memory until expiration minus 60 seconds.
  - On the SAS server, implemented API Gateway rate limiting (10 token requests/minute per client).
  - Configured an in-memory Redis cache layer in front of `JdbcOAuth2AuthorizationService` for M2M tokens, preventing repetitive database writes.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Why not just use Keycloak instead of spending time building a Spring Authorization Server?"
- **Naive answer:** "Because SAS is lighter and written in Java."
- **The trap:** Keycloak is also written in Java (Quarkus) and is very mature. The interviewer wants to test your ability to evaluate Total Cost of Ownership (TCO), architecture fit, and team capability.
- **Expert answer:** "Keycloak is an excellent turnkey identity solution if you need out-of-the-box user federation, SAML support, and an administrative console. However, if your application requires deep domain-model coupling (e.g., dynamic multi-tenant billing rules determining token claims), single-binary deployment within an existing Spring Boot microservice, or lightweight memory consumption (<100MB vs. Keycloak's 1GB+), SAS is significantly superior. With SAS, identity logic is maintained as standard Spring code with standard unit/integration tests, avoiding complex Keycloak SPI deployments and upgrade migrations."

### CQ2. "Is the `/oauth2/token` endpoint protected by CSRF in Spring Authorization Server?"
- **Naive answer:** "No, CSRF is disabled on all API endpoints."
- **The trap:** Confusing browser-driven endpoints with machine-to-machine endpoints. Why is it safe to disable CSRF on `/oauth2/token`?
- **Expert answer:** "CSRF is intentionally disabled on `/oauth2/token`, `/oauth2/introspect`, and `/oauth2/revoke`. These endpoints are invoked via HTTP POST using client credentials (`client_secret_basic`, `private_key_jwt`, or PKCE `code_verifier`). Because requests require explicit credentials or single-use, unguessable cryptographic tokens rather than ambient browser session cookies, CSRF attacks are mathematically impossible. However, the interactive `/oauth2/authorize` and `/login` endpoints **must** have CSRF protection enabled."

### CQ3. "What happens if a client presents an unhashed secret to a SAS instance configured with BCrypt?"
- **Naive answer:** "It authenticates if the strings match."
- **The trap:** Failing to understand how `DelegatingPasswordEncoder` and `JdbcRegisteredClientRepository` interact.
- **Expert answer:** "Authentication will fail with `BadCredentialsException`. SAS uses Spring Security's `PasswordEncoder` to verify `client_secret`. When the client submits `client_secret=secret`, SAS compares it against the stored hash in `oauth2_registered_client` using `passwordEncoder.matches("secret", storedHash)`. If the database contains plaintext or a malformed hash, the verification returns `false`."

### CQ4. "Does the Client Credentials grant support Refresh Tokens in SAS?"
- **Naive answer:** "Yes, if configured in `authorizationGrantTypes`."
- **The trap:** Violating RFC 6749 §4.4.3.
- **Expert answer:** "No. Even if you add `REFRESH_TOKEN` to the client's `authorizationGrantTypes`, SAS will **never** issue a refresh token for a Client Credentials exchange. RFC 6749 explicitly states that a refresh token MUST NOT be included in Client Credentials responses because there is no human resource owner or interactive session. When the access token expires, the client simply authenticates again using its client credentials."

### CQ5. "If JWT access tokens are stateless, why does SAS save them in `oauth2_authorization`?"
- **Naive answer:** "To verify signatures on every request."
- **The trap:** Forgetting the distinction between token issuance and token lifecycle management.
- **Expert answer:** "SAS saves tokens in `oauth2_authorization` for token lifecycle management, not verification. Specifically:
  1. To support Token Introspection (RFC 7662) for clients or resource servers querying active status.
  2. To support Token Revocation (RFC 7009) so revoked tokens can be tracked.
  3. To associate refresh tokens with their parent authorizations and enforce refresh token rotation.
  Resource servers do NOT query this table; they validate tokens locally via JWKS."

### CQ6. "Can you issue both opaque reference tokens and JWTs from the same SAS instance?"
- **Naive answer:** "No, it's a global server setting."
- **The trap:** Assuming `TokenSettings` is global rather than per-client.
- **Expert answer:** "Yes. In SAS, `TokenSettings` is configured on a per-`RegisteredClient` basis. Client A (e.g., an internal microservice) can have `accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)` to receive JWTs, while Client B (e.g., an external partner app) can have `accessTokenFormat(OAuth2TokenFormat.REFERENCE)` to receive opaque tokens that require introspection."

### CQ7. "Why is `NimbusJwkSetEndpointFilter` mapped to the protocol filter chain rather than a public REST controller?"
- **Naive answer:** "Because it's easier to configure."
- **The trap:** Understanding filter precedence, protocol auto-configuration, and security headers.
- **Expert answer:** "SAS encapsulates the JWKS endpoint inside `NimbusJwkSetEndpointFilter` so it can be automatically registered at `/.well-known/jwks.json` or `/oauth2/jwks` without requiring an MVC dispatcher servlet or controller beans. It ensures that standard security headers (`Cache-Control`, `Pragma`, `Access-Control-Allow-Origin`) are consistently enforced at the protocol layer."

### CQ8. "What is the difference between `OAuth2AuthorizationCodeRequestAuthenticationToken` and `OAuth2AuthorizationCodeAuthenticationToken`?"
- **Naive answer:** "They are the same token at different stages."
- **The trap:** Confusing authorization request handling with token exchange handling.
- **Expert answer:** 
  - `OAuth2AuthorizationCodeRequestAuthenticationToken` is used during the **authorization phase** at `/oauth2/authorize`. It validates the user's consent and authorization request parameters, resulting in the generation of the `authorization_code`.
  - `OAuth2AuthorizationCodeAuthenticationToken` is used during the **token exchange phase** at `/oauth2/token`. It authenticates the client, validates the submitted code and PKCE verifier, and results in the generation of the `OAuth2AccessToken` and `OAuth2RefreshToken`."

### CQ9. "If an authorization code is stolen, can the attacker redeem it without the client secret?"
- **Naive answer:** "No, because the client secret is required."
- **The trap:** Forgetting Public Clients (Single Page Apps / Mobile Apps) which do not have a client secret.
- **Expert answer:** "For confidential clients, the attacker cannot redeem it without the secret. However, for public clients (`client_secret: none`), the attacker *could* redeem it unless **PKCE** is enforced. With PKCE, the attacker cannot compute the `code_verifier` corresponding to the `code_challenge` sent in the initial request, causing the token endpoint to reject the redemption with `invalid_grant`."

### CQ10. "Can an Access Token be revoked without revoking its associated Refresh Token?"
- **Naive answer:** "Yes, they are independent tokens."
- **The trap:** Understanding parent-child relationships in `OAuth2Authorization`.
- **Expert answer:** "Yes. Calling `/oauth2/revoke` with the access token marks only that access token as invalidated in `OAuth2AuthorizationService`. The user can still present the refresh token to obtain a new access token. However, revoking the **refresh token** cascades revocation to the entire authorization, invalidating all associated access tokens immediately."

---

## 18. Frequently Asked Production Issues & Debugging Scenarios

---

### Issue 1: `invalid_client` Error on `/oauth2/token`
- **Symptoms:** Client sends POST to `/oauth2/token` and receives:
  `HTTP 401 Unauthorized` with `{"error": "invalid_client"}`.
- **Debugging Steps:**
  1. Inspect the incoming request headers and body. Is the client using HTTP Basic Auth or POST body parameters?
  2. Verify that the client's `clientAuthenticationMethods` in `RegisteredClient` includes the method being used (`client_secret_basic` vs `client_secret_post`).
  3. Check the client secret encoding. Ensure the database record has the `{bcrypt}` prefix.
  4. Enable debug logging:
     ```yaml
     logging:
       level:
         org.springframework.security.oauth2.server.authorization: DEBUG
         org.springframework.security.web.authentication: DEBUG
     ```

---

### Issue 2: Infinite Redirect Loop Between `/oauth2/authorize` and `/login`
- **Symptoms:** Navigating to `/oauth2/authorize` causes the browser to cycle between `/oauth2/authorize` and `/login` until `ERR_TOO_MANY_REDIRECTS`.
- **Root Cause:** The Application Filter Chain does not permit unauthenticated access to `/login`, or static assets required by the login page are triggering authentication exceptions.
- **Fix:** Ensure the Application Security Filter Chain explicitly allows permitAll on `/login`, `/error`, and all login assets:
  ```java
  http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/login", "/error", "/css/**").permitAll()
      .anyRequest().authenticated()
  );
  ```

---

### Issue 3: Custom Claims Missing from JWT in Resource Server
- **Symptoms:** `OAuth2TokenCustomizer` is registered as a bean, but the Resource Server's `JwtAuthenticationToken` contains only default claims (`sub`, `iss`, `exp`).
- **Root Cause:**
  1. The customizer does not check `OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())` and the claims are being added only to the ID Token.
  2. Or, the Resource Server's `JwtGrantedAuthoritiesConverter` is overriding the claims mapping.
- **Verification:** Inspect the raw JWT using `jwt.io`. If the claim is present in the raw payload, the bug is on the Resource Server side, not the Authorization Server.

---

### Issue 4: `oauth2_authorization` Table Deadlocks under High Concurrency
- **Symptoms:** Under load testing (500 req/sec on `/oauth2/token`), PostgreSQL throws:
  `org.postgresql.util.PSQLException: ERROR: deadlock detected`.
- **Root Cause:** `JdbcOAuth2AuthorizationService` performs sequential SELECT and UPDATE operations across concurrent requests exchanging codes or refreshing tokens.
- **Fix:**
  - Add composite index on `(registered_client_id, principal_name)`.
  - Ensure transaction isolation level is set to `READ_COMMITTED` (not `SERIALIZABLE`).
  - Configure connection pool retry logic in HikariCP.

---

## 19. Architecture Design Questions

### Design 1: Multi-Region Active-Active Identity Provider
- **Requirements:** 99.999% availability across US-East and EU-West, <50ms token validation latency globally.
- **Architecture Blueprint:**

```text
                       GLOBAL ANYCAST DNS / ROUTE 53
                                     │
                 ┌───────────────────┴───────────────────┐
                 ▼                                       ▼
       [REGION 1: US-EAST]                     [REGION 2: EU-WEST]
    ┌─────────────────────────┐             ┌─────────────────────────┐
    │ Spring Auth Server Pods │             │ Spring Auth Server Pods │
    └────────────┬────────────┘             └────────────┬────────────┘
                 │                                       │
                 ▼                                       ▼
    ┌─────────────────────────┐             ┌─────────────────────────┐
    │ Aurora Global Database  │◀──Bi-Dir────▶│ Aurora Global Database  │
    │ (PostgreSQL US-East)    │  Replication│ (PostgreSQL EU-West)    │
    └─────────────────────────┘             └─────────────────────────┘
                 │                                       │
                 └───────────────┬───────────────────────┘
                                 ▼
                     CENTRALIZED KEY VAULT
           (Synchronized RSA Signing Key Ring: kid 2026)
```

1. **Shared Cryptographic Ring:** Both regions use identical RSA Key Pairs loaded from Vault, ensuring a JWT minted in US-East can be verified against EU-West's `/oauth2/jwks`.
2. **Stateless Local Validation:** Resource servers validate tokens locally via in-memory cached JWKS. Zero cross-region network calls during API execution.
3. **Sticky Authorization Code Routing:** Encode the originating region identifier into the authorization code (e.g., `us_Splxl...`). The API Gateway routes the subsequent `/oauth2/token` request to the matching region, eliminating replication race conditions.

---

### Design 2: Hybrid Identity Architecture (Federation Gateway)
- **Requirements:** Allow enterprise clients to log in via Azure Active Directory (SAML/OIDC), while minting internal company-standard OAuth 2.1 JWTs for downstream microservices.
- **Solution:**
  - Configure the Spring Authorization Server as an **OAuth2 Client** in Filter Chain 2 (using `spring-boot-starter-oauth2-client`).
  - When an employee hits `/oauth2/authorize`, Filter Chain 2 presents a "Log in with Azure AD" button.
  - Upon successful external authentication, an `OAuth2UserService` maps Azure claims to internal company user records.
  - Control returns to Filter Chain 1, which mints the standardized internal JWT token.

---

## 20. Rapid-Fire MCQs

#### Q1. Which interface represents a registered client application in Spring Authorization Server?
- A) `OAuth2ClientDetails`
- B) `RegisteredClient`
- C) `ClientRegistration`
- D) `OAuth2AppClient`
- **Answer: B** · `RegisteredClient` is the core client domain model in SAS. (`ClientRegistration` is used in client applications, not authorization servers).

#### Q2. What is the default order required for the Protocol SecurityFilterChain in SAS?
- A) `@Order(1)`
- B) `@Order(Ordered.LOWEST_PRECEDENCE)`
- C) `@Order(Ordered.HIGHEST_PRECEDENCE)`
- D) Order does not matter
- **Answer: C** · It must be `HIGHEST_PRECEDENCE` so protocol endpoint matchers take priority over general application security.

#### Q3. Which endpoint exposes public cryptographic keys for token verification?
- A) `/.well-known/security-keys`
- B) `/oauth2/keys`
- C) `/oauth2/jwks`
- D) `/api/v1/jwk`
- **Answer: C** · RFC 7517 JSON Web Key Set is served at `/oauth2/jwks`.

#### Q4. What grant types are supported by OAuth 2.1 in Spring Authorization Server?
- A) Authorization Code, Implicit, Password
- B) Authorization Code, Client Credentials, Refresh Token
- C) Implicit, Client Credentials, Device Code
- D) Password, Token Exchange, Implicit
- **Answer: B** · Implicit and Password grants are removed in OAuth 2.1 and are not supported in SAS.

#### Q5. Which class should you customize to inject custom claims into a JWT Access Token?
- A) `JwtClaimsSetCustomizer`
- B) `OAuth2TokenCustomizer<JwtEncodingContext>`
- C) `TokenEnhancer`
- D) `JwtTokenEnhancer`
- **Answer: B** · `OAuth2TokenCustomizer<JwtEncodingContext>` is the standard SPI for customizing JWTs.

#### Q6. What does `reuseRefreshTokens(false)` do?
- A) Disables refresh tokens completely
- B) Enforces Refresh Token Rotation by issuing a new refresh token on every exchange
- C) Allows a refresh token to be used an infinite number of times
- D) Makes refresh tokens immutable
- **Answer: B** · It rotates the refresh token on every use and revokes the prior token.

#### Q7. Which RFC defines Token Revocation?
- A) RFC 6749
- B) RFC 7519
- C) RFC 7009
- D) RFC 7662
- **Answer: C** · RFC 7009 defines OAuth 2.0 Token Revocation. (RFC 7662 is Introspection).

#### Q8. What client authentication method is used by a public Single Page App using PKCE?
- A) `client_secret_basic`
- B) `client_secret_post`
- C) `private_key_jwt`
- D) `none`
- **Answer: D** · Public clients that cannot protect secrets use `ClientAuthenticationMethod.NONE`.

#### Q9. Where does SAS store user consent decisions in a JDBC-backed deployment?
- A) `oauth2_user_consent`
- B) `oauth2_authorization_consent`
- C) `oauth2_approved_scopes`
- D) `oauth2_client_consent`
- **Answer: B** · The standard table name is `oauth2_authorization_consent`.

#### Q10. What HTTP status code is returned by `/oauth2/revoke` when revoking an invalid token?
- A) 400 Bad Request
- B) 404 Not Found
- C) 200 OK
- D) 401 Unauthorized
- **Answer: C** · RFC 7009 mandates HTTP 200 OK even if the token does not exist, preventing token probing attacks.

#### Q11. Which bean configures the canonical public issuer URL in SAS?
- A) `IssuerConfigurationSettings`
- B) `AuthorizationServerSettings`
- C) `OAuth2ServerProperties`
- D) `ServerEndpointSettings`
- **Answer: B** · `AuthorizationServerSettings.builder().issuer("...").build()`.

#### Q12. What algorithm is standard for signing JWTs in SAS?
- A) HS256
- B) RS256
- C) MD5
- D) SHA-1
- **Answer: B** · RS256 (RSA with SHA-256) asymmetric signing.

#### Q13. Which component connects an unauthenticated request on `/oauth2/authorize` to the `/login` page?
- A) `SecurityContextHolderFilter`
- B) `LoginUrlAuthenticationEntryPoint`
- C) `OAuth2LoginAuthenticationFilter`
- D) `BasicAuthenticationEntryPoint`
- **Answer: B** · `LoginUrlAuthenticationEntryPoint` handles unauthenticated redirects.

#### Q14. What is the purpose of the `kid` header in a JWT?
- A) Identifies the user's child account
- B) Identifies the specific Key ID in the JWKS used to sign the token
- C) Specifies the client encryption scheme
- D) Defines the kernel ID of the server
- **Answer: B** · Key ID (`kid`) directs the validator to the exact public key in the JWK Set.

#### Q15. In a Client Credentials grant, what is the value of the `sub` claim?
- A) The username of the logged-in user
- B) `null`
- C) The client identifier (`clientId`)
- D) The server hostname
- **Answer: C** · In M2M flows without a human user, the subject is the client application itself.

#### Q16. What occurs if a revoked refresh token is presented during a token exchange when rotation is enabled?
- A) The server ignores the request
- B) Automatic Reuse Detection triggers, revoking all tokens in that authorization family
- C) The server returns a warning header
- D) The user's account is deleted
- **Answer: B** · Automatic Reuse Detection revokes the entire authorization chain to prevent replay attacks.

#### Q17. Which client setting enforces PKCE even for confidential clients?
- A) `requirePkce(true)`
- B) `requireProofKey(true)`
- C) `enforceCodeChallenge(true)`
- D) `strictPkce(true)`
- **Answer: B** · `ClientSettings.builder().requireProofKey(true).build()`.

#### Q18. How does a Resource Server validate opaque reference tokens issued by SAS?
- A) By inspecting the local signature
- B) By calling the `/oauth2/introspect` endpoint (RFC 7662)
- C) By querying the `oauth2_registered_client` table directly
- D) Opaque tokens cannot be validated
- **Answer: B** · Reference tokens require calling Token Introspection.

#### Q19. What format does `accessTokenFormat(OAuth2TokenFormat.SELF_CONTAINED)` produce?
- A) UUID opaque string
- B) Cryptographically signed JSON Web Token (JWT)
- C) XML SAML assertion
- D) Binary protocol buffer
- **Answer: B** · `SELF_CONTAINED` creates standard signed JWTs.

#### Q20. Where should fine-grained user profile claims (e.g., `family_name`, `picture`) be placed according to best practices?
- A) In the Access Token
- B) In the ID Token or served via the `/userinfo` endpoint
- C) In the Refresh Token
- D) In the Authorization Code
- **Answer: B** · Access tokens should remain lightweight; profile data belongs in the ID Token or `/userinfo`.

---

## 21. Cheat Sheet / One-Page Summary

### Essential Classes & Interfaces

| Component | Package | Role |
|---|---|---|
| `OAuth2AuthorizationServerConfiguration` | `.config.annotation.web.configuration` | Applies default security filter chain for protocol endpoints. |
| `RegisteredClient` | `.client` | Domain model representing an OAuth 2.1 client. |
| `RegisteredClientRepository` | `.client` | Persistence SPI for clients (`JdbcRegisteredClientRepository`). |
| `OAuth2AuthorizationService` | `.server.authorization` | Persistence SPI for authorizations (`JdbcOAuth2AuthorizationService`). |
| `OAuth2AuthorizationConsentService` | `.server.authorization` | Persistence SPI for user consent (`JdbcOAuth2AuthorizationConsentService`). |
| `OAuth2TokenCustomizer` | `.token` | Extension hook for modifying JWT claims before signing. |
| `JWKSource<SecurityContext>` | `com.nimbusds.jose.jwk.source` | Provides public/private cryptographic keys for token signing. |
| `AuthorizationServerSettings` | `.settings` | Configures endpoint paths and the canonical Issuer URI. |

### Default Protocol Endpoints

```text
GET  /.well-known/openid-configuration          OpenID Provider Metadata
GET  /.well-known/oauth-authorization-server    OAuth 2.0 Server Metadata
GET  /oauth2/authorize                          Authorization Endpoint (User Consent / Code)
POST /oauth2/token                              Token Exchange Endpoint
GET  /oauth2/jwks                               JSON Web Key Set (Public Keys)
POST /oauth2/introspect                         Token Introspection (RFC 7662)
POST /oauth2/revoke                             Token Revocation (RFC 7009)
POST /oauth2/device_authorization               Device Authorization Flow (RFC 8628)
GET  /userinfo                                  OIDC UserInfo Endpoint
```

---

## 22. Real-World Production Notes

### Monitoring Metrics to Instrument
Expose these metrics via Micrometer and Prometheus to monitor authorization server health:

| Metric Name | Type | Description |
|---|---|---|
| `oauth2.tokens.issued` | Counter | Number of tokens issued, tagged by `grant_type` and `client_id`. |
| `oauth2.tokens.revoked` | Counter | Number of token revocation requests received. |
| `oauth2.authorizations.active` | Gauge | Total active authorizations stored in the database. |
| `oauth2.jwks.requests` | Counter | Frequency of public key fetches from `/oauth2/jwks`. |
| `oauth2.client.auth.failures` | Counter | Failed client authentication attempts (alert threshold for credential stuffing). |

### Security Hardening Checklist for Production
- [ ] Passwords and client secrets hashed with `{bcrypt}` (work factor $\ge 12$) or `{argon2}`.
- [ ] `requireProofKey(true)` enforced for ALL clients.
- [ ] `reuseRefreshTokens(false)` enabled for refresh token rotation.
- [ ] Asymmetric private keys loaded from external secret manager (Vault/AWS SM), never committed to git or generated ephemerally.
- [ ] Canonical `issuer` URI set to public HTTPS domain.
- [ ] Database indexes created on `oauth2_authorization` columns (`access_token_value`, `refresh_token_value`, `state`, `authorization_code_value`).
- [ ] Scheduled purge job running to clean up expired authorizations.

---

## 23. Follow-up Learning

**Previous Chapters in this Folder:**
- ← [01 · OAuth2 Architecture, Roles & Grant Types](./01-OAuth2-Architecture-Roles-and-Grant-Types.md) — The fundamental OAuth roles, tokens, and grants.
- ← [02 · Authorization Code & PKCE Flows](./02-Authorization-Code-and-PKCE-Flows.md) — PKCE mechanics and browser redirects.
- ← [03 · Client Credentials, Refresh Tokens & OpenID Connect](./03-Client-Credentials-Refresh-Tokens-and-OpenID-Connect.md) — M2M communication and OIDC identity layer.
- ← [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md) — Third-party providers, Keycloak, and enterprise SSO.

**Related Core Modules:**
- [06 · JWT Authentication](../06-JWT-Authentication/README.md) — Deep dive into JOSE, JWT validation, and claims structures.
- [05 · Spring Security](../05-Spring-Security/README.md) — Architecture of `SecurityFilterChain`, `AuthenticationManager`, and `UserDetailsService`.
- [08 · Microservices with Spring Boot](../08-Microservices-with-Spring-Boot/README.md) — Implementing API Gateways and downstream JWT validation.

**Authoritative Standards & RFC References:**
- [RFC 6749: The OAuth 2.0 Authorization Framework](https://datatracker.ietf.org/doc/html/rfc6749)
- [RFC 7636: Proof Key for Code Exchange by OAuth Public Clients (PKCE)](https://datatracker.ietf.org/doc/html/rfc7636)
- [RFC 7519: JSON Web Token (JWT)](https://datatracker.ietf.org/doc/html/rfc7519)
- [RFC 7662: OAuth 2.0 Token Introspection](https://datatracker.ietf.org/doc/html/rfc7662)
- [RFC 7009: OAuth 2.0 Token Revocation](https://datatracker.ietf.org/doc/html/rfc7009)
- [RFC 8414: OAuth 2.0 Authorization Server Metadata](https://datatracker.ietf.org/doc/html/rfc8414)
- [RFC 9700 / BCP: OAuth 2.0 Security Best Current Practice](https://datatracker.ietf.org/doc/html/rfc9700)
- [OpenID Connect Core 1.0 incorporating errata set 1](https://openid.net/specs/openid-connect-core-1_0.html)

---

**Previous Chapter:** ← [04 · Social Login, Keycloak, Enterprise SSO & Security](./04-Social-Login-Keycloak-Enterprise-SSO-and-Security.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
