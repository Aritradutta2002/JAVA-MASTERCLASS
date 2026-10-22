# 04 · Security Risks, Key Management & Production Design

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★☆ ("How do you secure a JWT implementation?")
> **Prerequisite:** [03 · Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Attack Surface Map](#2-mental-model--the-attack-surface-map)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The attack's taxonomy](#31-the-attacks-taxonomy)
  - [3.2 The algorithm's choice](#32-the-algorithms-choice)
  - [3.3 The key's management](#33-the-keys-management)
  - [3.4 The key's rotation](#34-the-keys-rotation)
  - [3.5 The JWKS](#35-the-jwks)
  - [3.6 The production's hardening](#36-the-productions-hardening)
  - [3.7 The multi-tenancy's isolation](#37-the-multi-tenancys-isolation)
  - [3.8 The audit's trail](#38-the-audits-trail)
  - [3.9 The incident's response](#39-the-incidents-response)
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

- **The attack's taxonomy (the seven that matter):**
  1. **The algorithm's confusion** (`alg: none`, the RS256→HS256 switch) — **fix: pin the algorithm**.
  2. **The signature's forgery** (the weak key, the reused key) — **fix: RS256/ES256 + a strong key**.
  3. **The claim's tampering** (the role's escalation) — **fix: verify the signature over the whole payload**.
  4. **The token's theft & the replay** (the XSS, the log's leak, the URL) — **fix: the short TTL + the storage's choice + the `jti`**.
  5. **The refresh's theft** — **fix: the hash + the rotation + the reuse's detection**.
  6. **The `kid`'s injection** (the path traversal, the URL's fetch) — **fix: never fetch the `kid` from a URL**.
  7. **The `jku`/`x5u`'s SSRF** — **fix: ignore them, or allowlist them**.
- **The algorithm's choice:**
  - **`RS256`/`RS512`** (the asymmetric) — the default for the distributed systems (the issuer signs, the many's verifiers verify the public key).
  - **`ES256`** (the asymmetric, the elliptic) — the same, with the smaller's signatures and the smaller's keys.
  - **`HS256`** (the symmetric) — **only** when the issuer and the verifier are the **same** application. If the verifier is a different service, the symmetric key is a signing key — a compromise of any verifier lets it mint tokens.
  - **Never** `none`, never an **unpinned** `alg`.
- **The key's management:** the keys in the **KMS/HSM** (never in the repo, never in the config's file, never in the image); the **asymmetric** so the verifiers get only the public key; the **`kid`** for the rotation; the **JWKS** published over HTTPS; the **overlap window** for the rotation.
- **The production's checklist:** the algorithm's pin, the issuer + audience's validation, the clock's skew's tolerance (60s), the token's size's budget, the PII's absence, the rate's limiting on the auth's endpoints, the audit's logging, the key's rotation's runbook, the incident's runbook.
- **The one-liner:** *"A JWT implementation is secure only if it is **boring**: pin the algorithm, validate the issuer and the audience, use asymmetric keys from a KMS with a `kid` and an overlap, keep the token short and PII-free, and treat the refresh token as a database credential. Everything interesting — the cleverness — is where the vulnerabilities live."*

---

## 1. What & Why

A JWT implementation is **deceptively simple**: sign some JSON, base64 it, send it. And yet
almost every real-world JWT breach comes from one of a small number of predictable mistakes:

| The breach | The root cause | The chapter's fix |
|------------|----------------|------------------|
| The RS256→HS256's confusion | The library trusted the token's `alg` header | Pin the algorithm |
| The forged the admin's token | The algorithm's confusion or a weak's key | §3.2 |
| The 90M the tokens' leak (the GitHub's 2022) | The private's keys in the repo | §3.3 |
| The escalation's privilege | The claim's tampering / the `kid`'s injection | §3.1 |
| The long-lived the sessions | The TTL's design | [Chapter 03](./03-Access-Refresh-Tokens-and-Rotation.md) |

**This chapter is the "what could go wrong and how do you prevent it" chapter.** It is the
one that separates "I've used JWTs" from "I've designed a JWT-based authentication system".

---

## 2. Mental Model — The Attack Surface Map

```text
                        ┌──────────────────────────────┐
                        │        the ISSUER            │
                        │  (the auth server)           │
                        │  · the private key (KMS)     │
                        │  · the claims' construction  │
                        │  · the refresh's storage     │
                        └──────────────┬───────────────┘
                                       │ signs
                                       ▼
   ┌───────────────────────────────────────────────────────────┐
   │  the TOKEN's LIFECYCLE                                       │
   │                                                            │
   │  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌────────┐ │
   │  │  ISSUE   │──▶│  TRANSIT │──▶│  VERIFY  │──▶│  USE   │ │
   │  └──────────┘   └──────────┘   └──────────┘   └────────┘ │
   │       ▲             ▲              ▲             ▲       │
   │   the claims'     the XSS,       the alg's    the scope's │
   │   tamper,         the logs,      confusion,   escalation, │
   │   the key's       the URL,       the kid's    the object  │
   │   leak            the proxy      injection    leakage     │
   └───────────────────────────────────────────────────────────┘
                                       │
                                       ▼
                        ┌──────────────────────────────┐
                        │      the CONSUMER            │
                        │  (the resource server)       │
                        │  · the public key (JWKS)     │
                        │  · the validation's logic     │
                        │  · the authorization's logic  │
                        └──────────────────────────────┘
```

**Every control in this chapter maps to one of three positions:**
- **The issuer's side** — the keys, the claims, the issuance's policy.
- **In transit** — the storage, the transport, the leakage's surface.
- **The consumer's side** — the validation's strictness, the authorization's correctness.

---

## 3. Deep Dive — Theory

### 3.1 The attack's taxonomy

#### Attack 1 — The algorithm's confusion (`alg: none` / RS256 → HS256)

**The most common JWT vulnerability.** The attacker takes a legitimately signed RS256 token
and re-signs it using HS256 with the **public key as the HMAC secret**:

```text
  the original:  header {"alg":"RS256"}  payload {...}  sig = RSA_SK(header.payload)
  the forged:    header {"alg":"HS256"}  payload {...}  sig = HMAC_SHA256(publicKey, header.payload)
```

If the library picks the algorithm **from the token's header**, and it has the public key, the
attacker's HMAC **verifies** — because the public key is public!

**The fix (mandatory):**
```java
// the algorithm's PIN — never trust the header's alg
NimbusJwtDecoder decoder = NimbusJwtDecoder
        .withPublicKey(publicKey)
        .signatureAlgorithm(SignatureAlgorithm.RS256)   // ← the pin
        .build();
```
```yaml
# the Spring Boot's equivalent — the issuer-uri's discovery pins it from the metadata
spring.security.oauth2.resourceserver.jwt.issuer-uri: https://auth.example.com
```
And the `alg: none` variant — never accept it:
```java
// the check, if you hand-roll
if ("none".equalsIgnoreCase(header.getAlgorithm())) {
    throw new InvalidTokenException("The 'none' algorithm is not allowed");
}
```

**The library's reality:** modern libraries (Nimbus, jjwt, the Spring's) default to a safe
algorithm list. But **the hand-rolled ones and the old's versions don't**. The interview's
point: "the algorithm must be **pinned server-side**, never read from the token".

#### Attack 2 — The signature's forgery (the weak key / the key's reuse)

- **The weak's key:** an HS256 secret of "secret" or "password". The attacker brute-forces
  it offline (the JWT's signature gives them an oracle) and can forge any token.
- **The reused's key:** the same RSA key across the environments (the dev's key in the
  prod's JWKS) — a dev's compromise is a prod's compromise.
- **The truncated's key:** a 512-bit RSA key is considered broken.
- **The NIST's key's length:** RSA ≥ 2048 bits, EC ≥ P-256, and the hash's pairing: RSA
  with SHA-256+ (RS256/384/512), ECDSA with P-256 + SHA-256 (ES256).

**The fix:** a KMS-generated key, ≥2048-bit RSA (or P-256 EC), never a shared's secret
between the environments, and a **secret's scanner** (the GitHub's secret scanning, the
TruffleHog) in the CI.

#### Attack 3 — The claim's tampering / the privilege's escalation

The signature covers the **entire** payload, so a tamper is detected — **if** the signature
is verified. The realistic escalations:

| The escalation | The vector |
|---------------|-----------|
| The user sets `roles: ["ROLE_ADMIN"]` | Only possible without the key — but possible if the `alg: none`/confusion worked, or if the verifier doesn't check the signature at all |
| The user changes `sub` to another user's id | The same |
| The user changes the `tenant_id` | The same — **and the multi-tenant's systems are the biggest's risk** |
| The user removes the `exp` | The same |
| The user changes the `aud` | The same |

**The verifier's checklist (every one of these is mandatory):**
```java
// 1. the signature (the algorithm's pin)
// 2. the exp / nbf (the clock's skew)
// 3. the iss (who signed it)
// 4. the aud (who may use it)
// 5. the required's claims (sub, exp, iat)
// 6. the authorization (the scopes, the ownership)
```

#### Attack 4 — The token's theft & the replay

**The leakage's vectors:**

| The vector | The example | The mitigation |
|------------|-------------|----------------|
| **The XSS** | The token in the `localStorage` → any injected script reads it | The in-memory's storage + the httpOnly's refresh |
| **The logs** | The `Authorization` header's logging by the gateway/APM | The redaction's filter (`Authorization` → `***`) |
| **The URL** | The token in the query's parameter → the access's log, the browser's history, the `Referer` | Never in the URL; the `setAllowUriQueryParameter(false)` |
| **The referrer's leak** | The page's link to an external site leaks the `Referer` | The `Referrer-Policy: no-referrer` |
| **The client's crash's dump** | The token in the heap's dump | The in-memory's storage (as short as possible) |
| **The proxy's cache** | A cached response containing a token | `Cache-Control: no-store` |
| **The clipboard / the shoulder** | The user's copy | N/A (the UX's design) |
| **The mobile's backup** | The token in the unencrypted's backup | The Keychain's `ThisDeviceOnly` |

**The replay's mitigation:** the `jti` + the short TTL + the audience + the device's binding.

#### Attack 5 — The refresh's theft

Covered in [Chapter 03](./03-Access-Refresh-Tokens-and-Rotation.md): the hashed's storage,
the rotation, the reuse's detection, the family, the grace window.

#### Attack 6 — The `kid`'s injection (the SSRF / the path traversal)

The `kid` (the key's id) tells the verifier **which key** to use. If the implementation
**resolves the `kid` by fetching a URL** or by reading a file **named by the `kid`**, the
attacker controls it:

```text
  the token's header: {"alg":"RS256","kid":"../../../../etc/passwd"}
  → the verifier reads /etc/passwd as the key → crash, or a controlled key
  the token's header: {"alg":"RS256","kid":"https://attacker.com/key"}
  → the verifier fetches the attacker's key → the attacker's key verifies the attacker's token
```

**The fix:** the `kid` must be a **lookup in a fixed, in-memory map** of the known keys.
Never a URL, never a file's path, never a database's query built by concatenation.

#### Attack 7 — The `jku` / `x5u` (the JOSE's header's injection)

`jku` (the JWK Set's URL) and `x5u` (the X.509's URL) tell the verifier where to fetch the
keys. If honored, the attacker points it at their own server.

**The fix:** **ignore `jku`/`x5u`** entirely, or allowlist the exact hosts. The Spring's
Nimbus's `RemoteJWKSet` uses the configured URL, not the token's — safe by default. If you
hand-roll, never read them.

#### Attack 8 — The claim's injection via the `kid`/the malformed's input

- The `aud` as an **array vs a string** — a verifier that expects a string but gets an
  array can throw or mis-compare. Use a typed validator.
- The huge's `exp` (the year 9999) — no.
- The `scope` as a string vs an array — normalize before comparing.
- The duplicate's claims — the JWT's parsers should reject the duplicates (the RFC's
  "the duplicate's member names MUST be rejected").

#### Attack 9 — The confused's deputy (the token's substitution)

The attacker obtains a **valid token for a low-privilege user** and uses it where a
high-privilege one is expected. Mitigations: the `aud` (a token for the public's API can't
be used on the admin's API), the `azp` (the authorized party), the **the user-binding's
proof** (the DPoP/mTLS — the token is bound to the client's key, so it's useless when
stolen and replayed elsewhere).

#### Attack 10 — The downgrade (the "the old API accepts anything")

A legacy endpoint that validates the signature but **not** the `exp`/`iss`/`aud` is a
**downgrade's vector**: the attacker finds the laxest validator and targets it. This is
real: it happens when the validation is spread across the services and one team forgot.

**The fix:** the **centralized validation** — a single library, a single filter, used by
every service; plus a **periodic scan** for the endpoints that accept the tokens.

### 3.2 The algorithm's choice

| Algorithm | Type | Key size | Signature | Use when |
|-----------|------|----------|-----------|----------|
| **`HS256`** | Symmetric | ≥256-bit secret | 32 bytes | The issuer and the verifier are the **same** app |
| **`RS256`** | Asymmetric | ≥2048-bit RSA | 256 bytes | The **default** for the distributed's systems |
| **`RS512`** | Asymmetric | ≥2048-bit RSA | 512 bytes | The same, the stronger's hash |
| **`ES256`** | Asymmetric | P-256 | 64 bytes | The same, the smaller's keys and signatures (the mobile's clients) |
| **`PS256`** | RSA's PSS | ≥2048-bit RSA | 256 bytes | The same, the modern's RSA's padding (the preferred over PKCS#1 v1.5) |
| **`none`** | — | — | — | **NEVER** |

**The symmetric vs the asymmetric — the decision that matters most:**

```text
  the SYMMETRIC (HS256):
     the auth server ──(the SAME key)──▶ the API
     the API can SIGN tokens too!  ← if the API is compromised, it can mint admin tokens ⚠

  the ASYMMETRIC (RS256):
     the auth server ──(the private key)──▶ signs
     the API ──(the public key)──▶ verifies only  ← the API cannot mint anything ✅
```

**The rule:** if the **verifier is a different service** from the **issuer**, use the
asymmetric. The symmetric's key must then be shared with every verifier, and every verifier
becomes an issuer. That's the "the API's compromise = the full's compromise" problem.

**The exception that surprises people:** the symmetric's **HMAC with a key derived per
verifier** is sometimes used for **internal** services where the key's distribution is
painful (the mTLS's certs). The `kid` then identifies the derived key. But you're still
giving the verifier the ability to sign — accept it only for the low-privilege's internal
traffic.

**The algorithm's pinning in practice:**
```yaml
# the Spring Boot's — the metadata's advertised algorithms
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          # the algorithms the issuer's metadata advertises are used; Nimbus validates
          # the JWS's alg against the key's type
```
```java
// the explicit's pin (the recommended for a known's issuer)
NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(rsaPublicKey)
        .signatureAlgorithm(SignatureAlgorithm.RS256)
        .build();
```

### 3.3 The key's management

**The hierarchy:**

```text
  the KMS/HSM (the root of trust)
     │  the master key (the CMK)
     ▼
  the signing key (the asymmetric's key pair)
     │  the public key → the JWKS (published)
     │  the private key → stays in the KMS (the sign's operation only)
     ▼
  the tokens
```

**The rules:**

| Rule | Why |
|------|-----|
| The private key **never leaves the KMS** | The application's compromise doesn't yield the key |
| The application's sign's operation is **remote** (a network's hop) | A slower's sign, a much better's security |
| The **asymmetric** key pair for the multi-service's setup | The verifiers get only the public key |
| The **environment's separation** (separate keys per env) | The dev's compromise ≠ the prod's |
| The **audience's separation** (per-audience keys, if the risk warrants) | A partner's verifier can't verify the internal's tokens |
| The **rotation** (a scheduled + the on-demand) | Limits the exposure's window |
| The **`kid`** (a stable, non-secret identifier) | The verifier's selection |
| The **access's logging** (who signed what, when) | The audit |

**Spring Boot's configuration (the KMS-backed):**
```yaml
# the private key from the KMS (the Base64 of the PKCS#8)
app:
  jwt:
    private-key: ${JWT_PRIVATE_KEY}
    public-key: ${JWT_PUBLIC_KEY}
    key-id: k-2026-01
```
```java
@Value("${app.jwt.private-key}")
private String privateKeyPem;

@PostConstruct
void init() throws Exception {
    // the KMS's provider (the AWS KMS, the GCP KMS, the Azure Key Vault, the Vault)
    this.signer = KmsSigner.create(privateKeyPem, kmsClient);   // the remote's sign
}
```

**Or with a JWK's endpoint:**
```yaml
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```

### 3.4 The key's rotation

**The rotation's requirements:**
1. The old tokens must **still verify** during the overlap (otherwise every deployment
   invalidates the users).
2. The new key must be **published before** it's used for signing (otherwise the verifiers
   can't find it).
3. The old key must be **retired after** the max token lifetime (the access's TTL — the
   tokens signed with it have expired by then).

**The procedure:**
```text
  T-7d   the new key pair is generated (in the KMS)
         the new public key is added to the JWKS (the key's set now has k1, k2)
         the signing still uses k1

  T-0    the signing switches to k2 (the deploy)
         the JWKS still publishes k1 and k2

  T+15m  the access tokens signed with k1 have all expired (the 15-min TTL)
         the JWKS drops k1

  ✅ no token is invalidated, no verifier's failure
```

**The emergency's rotation (the compromise):**
```text
  1. remove the compromised key from the JWKS IMMEDIATELY
     → every token signed with it stops verifying (the intentional's mass's invalidation)
  2. generate the new key, publish it
  3. switch the signing
  4. the users are logged out (their access AND refresh tokens are dead) — that's the point
  5. the audit: which tokens were signed with it, what did they do
```

**The trap:** many teams rotate the key but **forget to publish the new public key first**
→ the tokens signed with the new key fail to verify → the total's outage. The order is
non-negotiable: **publish → sign → wait → retire**.

### 3.5 The JWKS

```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "alg": "RS256",
      "kid": "k-2026-01",
      "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
      "e": "AQAB"
    }
  ]
}
```

**The `n` and `e`:** the RSA's modulus and the exponent, base64url's encoded. The `e` is
`AQAB` = 65537 (the standard).

**The JWKS's production's rules:**
- **HTTPS only** (and the HTTP → HTTPS's redirect).
- **Cache headers:** `Cache-Control: public, max-age=300` — so the verifiers don't hammer
  it, but the rotation's propagated within 5 minutes.
- **CORS:** `Access-Control-Allow-Origin: *` is fine (the JWKS is public's data).
- **No the private's material** (obviously).
- **The `kid` is stable and unique.**
- **The keys' set's size is bounded** (the rotation's cleanup).

**The verifier's caching:**
```java
NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuer)
        .build();
// the Nimbus's RemoteJWKSet's default TTL is 5 minutes (or 15 with the Boot's hint)
```
```yaml
# the explicit's TTL
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: https://auth.example.com/.well-known/jwks.json
```
```java
// the custom's TTL (the Spring Boot 3.1+ property)
spring.security.oauth2.resourceserver.jwt.jwk-set-uri: ...
# (the cacheTTL is configured via the NimbusJwtDecoder's bean or the JWKSetCache)
```

### 3.6 The production's hardening

**The checklist:**

```
☐ The algorithm is PINNED (never read from the token's header)
☐ The 'none' algorithm is rejected
☐ The issuer (iss) is validated
☐ The audience (aud) is validated
☐ The expiry (exp) and the not-before (nbf) are validated, with a 60s's skew
☐ The required's claims (sub, exp, iat) are present
☐ The clock's skew is tolerated (60s), and the NTP is monitored
☐ The token is < 1 KB
☐ No PII in the claims
☐ The private key is in the KMS, not in the config
☐ The keys are rotated on a schedule AND on demand
☐ The JWKS is HTTPS + cached + published before use
☐ The Authorization header is redacted in the logs
☐ The token is never in the URL
☐ The auth's endpoints are rate-limited
☐ The refresh's tokens are hashed + rotated
☐ The failed's logins are audited and alerted on
☐ The 'jti' is present for the audit
☐ The response's headers: Cache-Control: no-store, X-Content-Type-Options: nosniff
☐ The CORS is explicitly configured (not '*' with credentials)
☐ The CSRF is enabled where the cookie's auth is used
☐ The dependency's versions are patched (the jjwt/nimbus CVEs)
```

**The CORS + the credentials' trap:**
```java
@Bean
CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("https://app.example.com"));  // NOT "*"
    config.setAllowedMethods(List.of("GET","POST","PUT","DELETE","PATCH"));
    config.setAllowedHeaders(List.of("Authorization","Content-Type","X-CSRF-TOKEN"));
    config.setAllowCredentials(true);       // requires a specific origin, not "*"
    config.setMaxAge(Duration.ofHours(1));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/api/**", config);
    return source;
}
```
> `allowCredentials(true)` + `allowedOrigins("*")` is **rejected by Spring** (and correctly
> so — it's the CSRF's enabler). Use `allowedOriginPatterns` only if you must, and
> understand the risk.

**The logging's redaction:**
```java
@Bean
FilterRegistrationBean<Filter> headerRedactionFilter() {
    FilterRegistrationBean<Filter> registration = new FilterRegistrationBean<>();
    registration.setFilter(new OncePerRequestFilter() {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                        FilterChain chain) throws ServletException, IOException {
            String auth = request.getHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                // the logging's framework's MDC — the raw header is NOT logged
                MDC.put("auth.subject", extractSubjectSafely(auth));
            }
            chain.doFilter(request, response);
        }
    });
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    return registration;
}
```
```yaml
# the Spring Boot's Actuator's scrubbing
management.endpoint.health.show-details: when-authorized
management.endpoint.configprops.show-values: never
logging.level.org.springframework.web.filter.CommonsRequestLoggingFilter: debug
```

### 3.7 The multi-tenancy's isolation

In a multi-tenant SaaS, the token's `tenant_id` is a **cross-tenant's escalation** waiting
to happen. The defence-in-depth:

```text
  layer 1: the TOKEN's claim — the tenant_id is signed
  layer 2: the URL's tenant — the request's path /tenantA/orders
  layer 3: the ROW's scoping — the query's WHERE tenant_id = :currentTenant
  layer 4: the DATABASE's RLS — the PostgreSQL's row's level security
  layer 5: the CONNECTION's scoping — the SET LOCAL app.tenant_id
```

**The classic bug:**
```java
@GetMapping("/tenants/{tenantId}/orders")
public List<OrderDto> list(@PathVariable Long tenantId) {
    return orderRepository.findByTenantId(tenantId);   // ❌ the client's tenantId!
}
```
**The fix:**
```java
@GetMapping("/tenants/{tenantId}/orders")
public List<OrderDto> list(@PathVariable Long tenantId) {
    Long currentTenant = currentTenantId();   // from the JWT's claim
    if (!currentTenant.equals(tenantId)) {
        throw new AccessDeniedException("Cross-tenant access is not permitted");
    }
    return orderRepository.findByTenantId(currentTenant);
}
```
**And the deeper fix (the repository's scoping):**
```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    @Query("SELECT o FROM Order o WHERE o.tenant.id = :tenantId AND o.id = :id")
    Optional<Order> findByIdAndTenantId(@Param("id") Long id, @Param("tenantId") Long tenantId);
}
```
**And the ultimate (the PostgreSQL's RLS):**
```sql
ALTER TABLE orders ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON orders
    USING (tenant_id = current_setting('app.tenant_id')::bigint);
```
```java
// the connection's scoping (the SET LOCAL)
@BeforeEach
void setTenant() {
    entityManager.createNativeQuery("SELECT set_config('app.tenant_id', :t, true)")
            .setParameter("t", currentTenantId().toString())
            .getSingleResult();
}
```

### 3.8 The audit's trail

**What to log (and what NOT to):**

| Log it | Don't log it |
|--------|--------------|
| The `sub` (the user) | The full token |
| The `jti` | The password |
| The `iss` + the `aud` | The refresh token (raw) |
| The `kid` | The private key |
| The decision (allowed/denied) | The PII from the claims |
| The reason (the code) | The whole header (it contains the token's info) |
| The device, the IP, the user-agent | |
| The `amr`/`acr` (the MFA's state) | |

```java
@Component
public class SecurityAuditLogger {

    private static final Logger log = LoggerFactory.getLogger(SecurityAuditLogger.class);

    public void authenticationSuccess(UserPrincipal user, AuthenticationMethod method, HttpServletRequest req) {
        log.info("""
                event=auth_success sub={} method={} ip={} ua={} jti={} mfa={} at={}
                """,
                user.getId(), method, clientIp(req), req.getHeader("User-Agent"),
                jtiOf(user), user.getMfaLevel(), Instant.now());
    }

    public void authenticationFailure(String identifier, HttpServletRequest req) {
        log.warn("event=auth_failure identifier={} ip={} ua={} at={}",
                mask(identifier), clientIp(req), req.getHeader("User-Agent"), Instant.now());
    }

    public void refreshReuseDetected(Long userId, String familyId, HttpServletRequest req) {
        log.error("event=refresh_reuse_detected sub={} family={} ip={} ua={} at={}",
                userId, familyId, clientIp(req), req.getHeader("User-Agent"), Instant.now());
        // → the alert (the PagerDuty)
    }
}
```

**The alerts worth having:**

| The alert | The signal | The severity |
|-----------|-----------|-------------|
| The refresh's reuse's detected | A stolen or replayed token | **High** (the P1) |
| The spike in the auth's failures | The brute force / the credential's stuffing | High |
| The new's device's sign-in | The possible's compromise | Medium |
| The impossible's travel | The possible's compromise | High |
| The admin's sign-in | The privilege's escalation | Medium |
| The key's rotation | The planned or the emergency | Info (High if unscheduled) |

### 3.9 The incident's response

**The runbook: the signing key's compromise.**
```text
  1. ASSESS
     · when was the key's exposure? (the git's history, the CI's logs, the image's layers)
     · which tokens were signed with it? (the issuance's log, the jti's index)
  2. CONTAIN
     · remove the key from the JWKS (the tokens stop verifying immediately)
     · rotate to a new key (publish first, then sign)
     · revoke the affected users' sessions (the version's bump)
  3. ERADICATE
     · the git's history's rewrite (filter-repo / BFG) + the force-push + the collaborators' re-clone
     · the secret's rotation in EVERY place it was used (the CI's secret, the vault, the backups)
  4. RECOVER
     · the users' re-login (forced)
  5. REVIEW
     · the postmortem: how did the key get in the repo? (the pre-commit's hook, the secret's scanner)
     · the process's fix (the scanning, the education, the CI's check)
```

**The runbook: the user account's compromise.**
```text
  1. the version's bump (kills the access's tokens) + the refresh's rows' revocation
  2. the password's reset + the MFA's re-enrollment
  3. the audit: the access's log, the recent's activity, the data's accessed
  4. the notification (the user + the privacy's officer if the data's breach)
  5. the device's list's review (the unknown's devices → revoke)
```

---

## 4. Visualizations

### 4.1 The attack's surface (the complete map)

```text
                    ┌─────────────────────────────────────────────┐
                    │              the ISSUER's SIDE                │
                    │  ⚠ the private key in the repo/config        │
                    │  ⚠ the weak/short key                        │
                    │  ⚠ the over-privileged's claims (the PII)     │
                    │  ⚠ the algorithm's choice (HS256 for a        │
                    │     multi-service's setup)                    │
                    │  ⚠ the unbounded's token's lifetime          │
                    └───────────────────┬─────────────────────────┘
                                        │
   IN TRANSIT                          │            the CONSUMER's SIDE
   ┌──────────────────┐                │            ┌──────────────────────────┐
   │ ⚠ the XSS (the   │                │            │ ⚠ the alg from the header  │
   │   localStorage)  │                │            │   (the confusion)          │
   │ ⚠ the logs       │                ├───────────▶│ ⚠ no iss/aud's validation   │
   │ ⚠ the URL        │                │            │ ⚠ the lax validator (the    │
   │ ⚠ the Referer    │                │            │   downgrade)               │
   │ ⚠ the proxy's    │                │            │ ⚠ the kid's injection       │
   │   cache          │                │            │ ⚠ the jku's SSRF            │
   └──────────────────┘                │            │ ⚠ the cross-tenant's query  │
                                        │            │ ⚠ the missing's ownership   │
                    ┌───────────────────┴─────────────────────────┐│
                    │              the REFRESH's SIDE             ││
                    │  ⚠ the raw's storage (the DB's leak)       ││
                    │  ⚠ no rotation (no theft's detection)      ││
                    │  ⚠ the client-side's storage (the XSS)     ││
                    └─────────────────────────────────────────────┘
```

### 4.2 The key's rotation's timeline

```text
  T-7d            T-0                T+15m            T+7d
  │               │                  │                │
  ▼               ▼                  ▼                ▼
  ┌──────────┐   ┌──────────┐      ┌──────────┐     ┌──────────┐
  │ generate │   │ switch   │      │ retire   │     │ generate │
  │ key k2   │   │ signing  │      │ k1 from  │     │ key k3   │
  │ publish  │   │ to k2    │      │ the JWKS │     │          │
  │ k1+k2    │   │          │      │          │     │          │
  └──────────┘   └──────────┘      └──────────┘     └──────────┘
        │             │                  │
        ▼             ▼                  ▼
  the JWKS:      the JWKS:           the JWKS:
  {k1, k2}       {k1, k2}            {k2}
                    ▲
                    └── the tokens signed with k1 still verify ✅
                        (and they expire within 15 minutes anyway)
```

### 4.3 The multi-tenancy's defence-in-depth

```text
   the request:  GET /tenants/42/orders/999
                 with the token: { "sub": "u1", "tenant_id": "42" }

   layer 1: the TOKEN's claim        tenant_id = 42  ✅ matches the URL
   layer 2: the URL's tenant         42 == 42       ✅
   layer 3: the QUERY's scoping      WHERE id=999 AND tenant_id=42  ✅
   layer 4: the DATABASE's RLS       USING (tenant_id = current_setting(...))  ✅
   layer 5: the CONNECTION's scoping SET LOCAL app.tenant_id = '42'  ✅

   ⚠ any ONE of them missing → the cross-tenant's leak
   ⚠ the token's claim ALONE is not enough — the client's tenant is the attacker's input
```

---

## 5. Worked Examples

### 5.1 The algorithm's confusion's attack (the dry run)

```text
  the LEGITIMATE token (signed with RS256 by the auth server):
    header:  {"alg":"RS256","typ":"JWT","kid":"k-2026-01"}
    payload: {"sub":"u1","roles":["USER"],"exp":1735689900}
    signature: <RSA over base64(header.payload), using the PRIVATE key>

  the ATTACKER's goal: roles → ["ROLE_ADMIN"]

  step 1: the attacker takes the header+payload and changes the payload:
    header:  {"alg":"HS256","typ":"JWT","kid":"k-2026-01"}
    payload: {"sub":"u1","roles":["ROLE_ADMIN"],"exp":1735689900}
    signature: <nothing yet>

  step 2: the attacker needs a valid signature. The verifier has:
    · the RSA's PUBLIC key (to verify RS256)
    · a buggy implementation that picks the algorithm from the header

  step 3: the attacker signs with HMAC-SHA256, using the PUBLIC key as the secret:
    signature = HMAC_SHA256(publicKeyBytes, base64url(header) + "." + base64url(payload))

  step 4: the verifier sees alg=HS256 → looks up "the HS256 key" → it has only the RSA
    public key → it uses THAT as the HMAC secret → computes the same HMAC → MATCH ✅
    → the forged token is ACCEPTED as ROLE_ADMIN 💀

  the FIX (one line):  .signatureAlgorithm(SignatureAlgorithm.RS256)
  → the decoder only accepts RS256 → the HS256 token is rejected with
    "the JWS header's alg doesn't match the expected" ✅
```

### 5.2 The `kid`'s injection (the dry run)

```text
  the vulnerable's implementation (a common anti-pattern):
    Key resolve(String kid) {
        String url = "https://keys.internal/" + kid + ".pem";   // ❌ the kid is the input
        return fetch(url);                                       // the SSRF
    }

  the attacker's token:
    header: {"alg":"RS256","kid":"../../../../etc/passwd"}
    → the verifier fetches https://keys.internal/../../../../etc/passwd.pem
    → the path traversal → the file's read (or a 500 → the DoS)

    header: {"alg":"RS256","kid":"https://attacker.example/key.pem"}
    → the verifier fetches the ATTACKER's key
    → the attacker's token (signed with the attacker's private key) verifies ✅ 💀

  the FIX: the fixed, in-memory's map:
    Map<String, Key> KEYS = Map.of(
        "k-2026-01", k1,
        "k-2026-02", k2);
    Key key = KEYS.get(kid);
    if (key == null) throw new InvalidTokenException("Unknown kid");
    → no URL, no file's path, no the attacker's control ✅
```

### 5.3 The key's rotation (the dry run)

```text
  T-7d:  the KMS generates k2 (the public key is added to the JWKS)
         the JWKS: { "k-2026-01": k1_pub, "k-2026-02": k2_pub }
         the signing: still k1

  T-0:   the deploy switches the signing to k2
         a new token: header {"kid":"k-2026-02"} → signed with k2's private
         the verifiers: they fetch the JWKS, find k-2026-02, verify ✅
         the OLD tokens: {"kid":"k-2026-01"} → the JWKS still has k1 → verify ✅
         the users: nobody is logged out ✅

  T+15m: every token signed with k1 has expired
         the JWKS drops k1 → { "k-2026-02": k2_pub }

  the emergency's rotation (the compromise at T0):
  T0:    the compromised key is REMOVED from the JWKS immediately
         → every token signed with it fails to verify → the users are logged out
           (the intended: the exposure's window is closed)
         the new key k3 is generated and published
  T0+5m: the signing switches to k3
         the JWKS: { k3 } only
```

### 5.4 The multi-tenant's attack (the dry run)

```text
  the attacker:  user of tenant 7
  the request:   GET /tenants/42/orders/999
                Authorization: Bearer <a valid token with tenant_id=7>

  with ONLY the token's claim checked (the vulnerable's version):
    the controller: orderRepository.findById(999)   → no the tenant's filter
    the row 999 belongs to tenant 42
    → the attacker reads tenant 42's order 💀 (the cross-tenant's IDOR)

  with the full's stack:
    layer 1: the claim's tenant_id = 7 != 42 (the URL) → the AccessDeniedException ✅
    (and even if the claim check were skipped)
    layer 3: findByIdAndTenantId(999, 7) → no row → 404 ✅
    layer 4: the RLS: current_setting('app.tenant_id') = '7' → the row is invisible ✅
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The hardened `JwtDecoder` (the pinned algorithm + the validators)

```java
package com.example.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessor;
import com.nimbusds.jwt.proc.JWTProcessorConfiguration;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.*;

import java.net.URL;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Configuration
@RequiredArgsConstructor
public class JwtDecoderConfig {

    @Value("${app.jwt.issuer}")
    private String issuer;

    @Value("${app.jwt.audience}")
    private String audience;

    @Value("${app.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * The HARDENED's decoder:
     *  · the algorithm is PINNED (never read from the token's header)
     *  · the iss / aud / exp / nbf are validated
     *  · the clock's skew is tolerated (60s)
     *  · the required's claims are enforced
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSetUri(jwkSetUri)
                .signatureAlgorithm(SignatureAlgorithm.RS256)   // ← THE PIN
                .build();

        // the cache's TTL: the fewer fetches, the fewer of the auth's spikes
        decoder.setJwtValidator(jwtValidator());

        return decoder;
    }

    private OAuth2TokenValidator<Jwt> jwtValidator() {
        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> audienceValidator = new AudienceValidator(audience);
        OAuth2TokenValidator<Jwt> requiredClaims = new RequiredClaimsValidator(
                List.of("sub", "exp", "iat", "jti"));
        OAuth2TokenValidator<Jwt> skew = new ClockSkewValidator(Duration.ofSeconds(60));

        return new DelegatingOAuth2TokenValidator<>(withIssuer, audienceValidator,
                requiredClaims, skew);
    }

    /** the audience's validator (the aud can be a string OR an array) */
    static class AudienceValidator implements OAuth2TokenValidator<Jwt> {
        private final String required;

        AudienceValidator(String required) { this.required = required; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            List<String> audience = token.getAudience();
            if (audience != null && audience.contains(required)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "The token's audience is invalid", null));
        }
    }

    /** the required's claims */
    static class RequiredClaimsValidator implements OAuth2TokenValidator<Jwt> {
        private final List<String> required;

        RequiredClaimsValidator(List<String> required) { this.required = required; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            for (String claim : required) {
                if (token.getClaims().get(claim) == null) {
                    return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                            "invalid_token", "The required claim '" + claim + "' is missing", null));
                }
            }
            return OAuth2TokenValidatorResult.success();
        }
    }

    /** the clock's skew's tolerance */
    static class ClockSkewValidator implements OAuth2TokenValidator<Jwt> {
        private final Duration skew;

        ClockSkewValidator(Duration skew) { this.skew = skew; }

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            java.time.Instant now = java.time.Instant.now();
            java.time.Instant exp = token.getExpiresAt();
            java.time.Instant nbf = token.getNotBefore();

            if (exp != null && exp.plus(skew).isBefore(now)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "The token has expired", null));
            }
            if (nbf != null && nbf.minus(skew).isAfter(now)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "The token is not yet valid", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
```

### 6.2 The KMS-backed signer (the key never leaves the KMS)

```java
package com.example.auth.token;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.kms.KmsClient;
import software.amazon.awssdk.services.kms.model.SignRequest;
import software.amazon.awssdk.services.kms.model.SignResponse;
import software.amazon.awssdk.services.kms.model.SigningAlgorithmSpec;
import software.amazon.awssdk.services.kms.model.EncryptionAlgorithmSpec;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * The KMS's signer. The private key NEVER exists in the application's memory —
 * the signing is a REMOTE's call. A compromise of the app yields no key material.
 */
@Service
@RequiredArgsConstructor
public class KmsJwtSigner {

    private final KmsClient kmsClient;
    private final String keyId;          // the ARN
    private final String keyAlias;

    /**
     * Signs the JWT's signing's input (base64url(header) + "." + base64url(payload))
     * using the KMS's asymmetric's key.
     */
    public byte[] sign(byte[] signingInput) {
        SignRequest request = SignRequest.builder()
                .keyId(keyId)
                .message(signingInput)
                .messageType(software.amazon.awssdk.services.kms.model.MessageType.RAW)
                .signingAlgorithm(SigningAlgorithmSpec.RSASSA_PKCS1_V1_5_SHA_256)
                .build();

        SignResponse response = kmsClient.sign(request);
        return response.signature().asByteArray();
    }
}
```

**The jjwt's integration with an external signer:**
```java
@Component
public class KmsSigningKey extends SigningKeyResolverAdapter {

    @Override
    public byte[] resolveSigningKey(JwsHeader header, String plaintext) {
        // the KMS's sign → the signature's bytes
        return kmsJwtSigner.sign(plaintext.getBytes(StandardCharsets.UTF_8));
    }
}
```

### 6.3 The key's rotation's service (the safe's procedure)

```java
package com.example.auth.key;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyRotationService {

    private final KeyRepository keyRepository;
    private final KmsClient kmsClient;
    private final JwkSetPublisher publisher;

    /**
     * The SAFE's rotation: publish the new key FIRST, then switch the signing,
     * then retire the old key after the max token's lifetime.
     */
    @Transactional
    public void rotate() {
        Instant now = Instant.now();

        // 1. generate the new key
        SigningKey newKey = keyRepository.createNewKey(now);
        log.info("key rotation: generated {} (kid={})", newKey.id(), newKey.kid());

        // 2. PUBLISH FIRST — the verifiers must be able to find the new key
        publisher.publish(keyRepository.findAllActive());
        log.info("key rotation: published the JWKS with the new key");

        // 3. switch the signing
        keyRepository.activateForSigning(newKey.id());
        log.info("key rotation: signing now uses {}", newKey.kid());

        // 4. schedule the retirement (the old keys stay valid for the max TTL)
        keyRepository.scheduleRetirement(now.plus(Duration.ofMinutes(30)));
    }

    /** the periodic's rotation (the monthly) */
    @Scheduled(cron = "0 0 3 1 * *")   // the 1st of the month, 03:00
    public void scheduledRotation() {
        log.info("key rotation: the scheduled's rotation has started");
        rotate();
    }

    /** the EMERGENCY's rotation (the compromise) */
    @Transactional
    public void emergencyRotation(String reason) {
        log.error("key rotation: EMERGENCY rotation triggered — {}", reason);
        // 1. the compromised key is REMOVED from the JWKS immediately
        keyRepository.deactivateAllForSigning();
        publisher.publish(keyRepository.findAllActive());
        // 2. the new key + the signing's switch
        rotate();
        // 3. the sessions' revocation (the users are logged out — the intended)
        sessionRevocationService.revokeAll();
    }
}
```

### 6.4 The hardened's `SecurityConfig` (the complete)

```java
package com.example.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationConverter jwtAuthenticationConverter;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf
                .ignoringRequestMatchers("/api/auth/login", "/api/auth/refresh"))
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse()))
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                        "default-src 'self'; frame-ancestors 'none'; base-uri 'self'"))
                .referrerPolicy(referrer -> referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                .frameOptions(frame -> frame.deny())
                .httpStrictTransportSecurity(hsts -> hsts
                        .includeSubDomains(true)
                        .maxAgeInSeconds(31536000)))
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .securityContext(context -> context
                .securityContextRepository(new RequestAttributeSecurityContextRepository())
                .requireExplicitSave(false))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/public/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(authenticationEntryPoint)
                .accessDeniedHandler(accessDeniedHandler)
                .jwt(jwt -> jwt
                        .decoder(jwtDecoder)
                        .jwtAuthenticationConverter(jwtAuthenticationConverter)));

        return http.build();
    }
}
```

### 6.5 The multi-tenant's isolation (the entity listener)

```java
package com.example.tenant;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.lang.annotation.*;

/** the entity's tenant's binding — a compile-time's guarantee */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@InterceptorBinding( TenantAwareListener.class )
public @interface TenantId {
}
```

```java
package com.example.tenant;

import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class TenantContext {

    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    public static void set(Long tenantId) { CURRENT.set(tenantId); }
    public static Long get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}
```

```java
@Configuration
@RequiredArgsConstructor
public class TenantConfig {

    /**
     * The Hibernate's integrator — EVERY entity with a @TenantId field is automatically
     * filtered by the current tenant. A forgotten WHERE clause becomes impossible.
     */
    @Bean
    public HibernatePropertiesCustomizer tenantHibernateCustomizer() {
        return properties -> properties.applySetting(
                AvailableSettings.INTERCEPTOR,
                new TenantAwareHibernateInterceptor());
    }
}
```

```java
/**
 * The tenant's filter, applied at the SQL's level:
 *   SELECT ... FROM orders WHERE tenant_id = ? AND id = ?
 * The tenant's predicate is added AUTOMATICALLY — the developer cannot forget it.
 */
public class TenantAwareHibernateInterceptor extends EmptyInterceptor {

    @Override
    public boolean onLoad(Object entity, Object id, Object[] state, String[] propertyNames) {
        if (entity instanceof TenantAware) {
            Long current = TenantContext.get();
            Long entityTenant = ((TenantAware) entity).getTenantId();
            if (current != null && !current.equals(entityTenant)) {
                // the entity belongs to another tenant → the model's exception
                throw new TenantMismatchException(
                        "The entity belongs to a different tenant");
            }
        }
        return false;
    }
}
```

### 6.6 The audit's logger (the production's version)

```java
package com.example.security.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityAuditFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String subject = (auth != null && auth.isAuthenticated()) ? auth.getName() : "anonymous";
            log.info("""
                    event=http_access method={} path={} status={} sub={} ip={} ua={} dur_ms={} at={}
                    """,
                    request.getMethod(),
                    request.getRequestURI(),               // NOT the query string (the token's leak)
                    response.getStatus(),
                    subject,
                    clientIp(request),
                    truncate(request.getHeader("User-Agent"), 200),
                    (System.nanoTime() - start) / 1_000_000,
                    Instant.now());
        }
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max);
    }
}
```

### 6.7 The secret's scanner in the CI (the GitHub's Actions)

```yaml
# .github/workflows/secret-scan.yml
name: Secret Scan
on: [push, pull_request]

jobs:
  gitleaks:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0     # the full history — the old commits matter
      - name: Scan the history for leaked secrets
        uses: gitleaks/gitleaks-action@v2
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
      - name: Scan the dependencies for known CVEs
        uses: dependency-check/dependency-check-action@v4
        with:
          fail-on-severity: HIGH
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | The `alg` read from the token's header | The algorithm's pin | The confusion's attack |
| 2 | `alg: none` accepted | Rejected | The unsigned's token |
| 3 | The symmetric's key shared with the API's services | The asymmetric's key pair | The verifier can mint tokens |
| 4 | The private key in `application.yml` | The KMS / the Vault / the env's secret | The repo's leak |
| 5 | The RSA key < 2048 bits | ≥ 2048 (or P-256) | The factoring's risk |
| 6 | The `iss`/`aud` not validated | Always validated | The token's substitution |
| 7 | The `kid` resolved from a URL/file | The in-memory's map | The SSRF / the path traversal |
| 8 | The `jku`/`x5u` honored | Ignored | The SSRF |
| 9 | The PII in the claims | The `sub` as the id | The token is readable |
| 10 | The token in the `localStorage` | The in-memory + the httpOnly's refresh | The XSS |
| 11 | The token in the URL's query | The header only | The logs, the history, the `Referer` |
| 12 | The `Authorization` header's logged | The redaction | The logs' leak |
| 13 | The `Cache-Control` missing on the auth's responses | `no-store` | The proxy's cache |
| 14 | The key rotated without the JWKS's overlap | Publish → sign → wait → retire | The mass's invalidation |
| 15 | The JWKS served over HTTP | HTTPS only | The MITM |
| 16 | The cross-tenant's query (the `tenantId` from the path) | The claim + the query's scoping + the RLS | The cross-tenant's leak |
| 17 | The clock's skew = 0 | 60s (and the NTP's monitoring) | The false's rejections |
| 18 | The `CORS` with `*` + `allowCredentials` | The explicit's origins | The CSRF's enabler |
| 19 | The token > 1 KB | The claims' diet | The header's limit |
| 20 | The refresh's rows never deleted | The scheduled's cleanup | The table's growth |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: Is a JWT encrypted?**
**A:** No — a JWS is signed, not encrypted. The payload is base64url-encoded, so anyone can
decode it. Never put secrets or PII in a JWT. If you need confidentiality, use a JWE
(encrypted) — but then the verifiers need the decryption key, which complicates the design.

**Q: Why can't you just delete a JWT when the user is deleted?**
**A:** Because it's self-contained — the server doesn't store it, so there's nothing to delete.
The revocation has to come from elsewhere: a short TTL, a blocklist, a version claim, or
putting the revocable state in the refresh token.

**Q: What is the `kid`?**
**A:** The key id — an identifier in the JWT's header that tells the verifier which key to
use. It's how key rotation works: the issuer signs with the new key and sets the new `kid`;
the verifiers look it up in the JWKS.

### 🟡 Intermediate
**Q: Explain the algorithm confusion attack.**
**A:** If the verifier picks the algorithm from the token's header, an attacker can take a
valid RS256 token, change the header to HS256, and re-sign it using the RSA **public** key
as the HMAC secret. If the verifier has that public key (it must, to verify RS256), the HMAC
checks out and the forged token is accepted. The fix is to pin the algorithm server-side.

**Q: How do you rotate the signing key without logging everyone out?**
**A:** Publish the new public key in the JWKS first, then switch the signing, wait for the
max token lifetime (so the tokens signed with the old key expire naturally), then remove
the old key from the JWKS. The `kid` in the header tells each verifier which key to use.

**Q: What are the risks of putting user data in a JWT?**
**A:** The payload is readable by anyone who has the token (base64, not encryption). It
persists in the client's storage, in the logs, in the browser's history, and in the
analytics. So: no PII, no secrets. Use an opaque `sub` (the user's id) and fetch the
profile from a protected endpoint.

### 🔴 Advanced / Senior
**Q: How do you prevent cross-tenant data access in a multi-tenant system?**
**A:** Defence in depth, because any single layer can be missed: (1) the signed `tenant_id`
claim; (2) the request's tenant must match the claim; (3) every query includes the tenant
predicate — best enforced by an ORM-level filter (a Hibernate interceptor) so a forgotten
`WHERE` becomes impossible; (4) PostgreSQL's row-level security as the backstop; (5) the
connection's `SET LOCAL app.tenant_id` so the RLS can read it. Never trust the path
parameter as the tenant — it's the attacker's input.

**Q: What's your runbook if the signing key leaks?**
**A:** Remove the key from the JWKS immediately (that kills every token signed with it —
the intended mass invalidation), generate a new key, publish it, switch the signing,
force the sessions' revocation (the version bump), then the forensics: when was it
exposed, which tokens were signed with it, what did those tokens do. And the remediation:
purge the git history, rotate the secret everywhere it was used, and add the secret
scanning to the CI.

**Q: How do you prevent the tokens from leaking into the logs?**
**A:** The `Authorization` header's redaction at the logging's framework level (the
`CommonsRequestLoggingFilter` off; a custom's filter that logs a placeholder), never
logging the query string (the token's URL's leak), the `Cache-Control: no-store` on the
auth's responses, the `Referrer-Policy: no-referrer`, and the monitoring of the APM's
spans for the token's patterns.

### ⚫ Expert / Principal
**Q: Design the token's security's architecture for a regulated's environment (the financial,
the health) across 30 services.**
> (1) The **the trust's zones** (the internal's workload's identity via SPIFFE/mTLS, the
user's JWT only at the edge, the partner's scoped's tokens); (2) the **the key's hierarchy**
(the KMS's CMKs per environment and per audience, the HSM, the audit's log of every sign);
(3) the **the rotation's automation** (the scheduled + the on-demand + the overlap's
guarantee); (4) the **the validation's centralization** (one library, one filter, the
downgrade's detection scanning); (5) the **the tenant's isolation** (the claim + the ORM's
filter + the RLS); (6) the **the audit & the evidence** (the immutable's log, the
correlation's id, the regulator's access); (7) the **the incident's playbooks** (the key's
compromise, the user's compromise, the insider's threat); (8) the **the compliance's
mapping** (the PCI's scope for the token's storage, the GDPR's erasure's interaction with
the audit's logs, the FIPS's validation of the crypto's module).

---

## 9. Counter-Questions & Traps

### CQ1. "If the JWT is signed, isn't it tamper-proof? Why do you need all these checks?"
- **Naive answer:** "Yes, the signature protects it."  ← the trap
- **The trap:** The interviewer wants the distinction between the **integrity** and the
**authorization**.
- **Expert answer:** The signature gives you **integrity** (nobody changed the content
without the key) and **authenticity** (the issuer is who they claim). It does *not* give
you: (1) **the authorization** — a validly signed token can still carry insufficient
scopes; (2) **the freshness** — a signed token from a year ago is still validly signed; (3)
**the audience's correctness** — a token signed by your issuer for another service is still
validly signed; (4) **the revocation** — a signed token can't be un-signed. So the signature
is **one of five** checks: the signature, the `exp`/`nbf`, the `iss`, the `aud`, and the
authorization (the scopes + the ownership). And there's a fifth axis: the **the algorithm's
pin** — a "validly signed" check is meaningless if the attacker chose the algorithm (the
confusion's attack). The framing: *"the signature answers 'is this authentic and unmodified' —
it doesn't answer 'is it current, for me, and sufficient'. Every other check answers one of
those."*

### CQ2. "Why not just encrypt the JWT (JWE) so nobody can read the claims?"
- **Naive answer:** "To hide the PII."  ← the trap
- **The trap:** The interviewer wants the trade-off's depth.
- **Expert answer:** Three reasons it usually doesn't help: (1) **the verifier's key
problem** — the JWE requires the *decryption* key at every verifier, which means every
service can decrypt every token — you've just moved the trust boundary without a gain, and
you've lost the asymmetric's benefit (the verifiers only had the *public* key, which
can't mint). (2) **the revocation and the audit's degradation** — you can't inspect a
token's claims in the logs, the API's gateway can't route on the scopes, the platform's
tooling can't see what's happening. (3) **it's the wrong tool** — the problem isn't that the
claims are readable, it's that the claims contain data that shouldn't be there. The fix is
**don't put it in** — use the opaque `sub` and fetch the profile from an API. JWE is the
right tool when the *claims themselves* must be confidential (the health's data, the
internal's identifiers) and the verifiers are few and trusted. The rule: **minimize the
claims first, encrypt second (if at all).**

### CQ3. "Why is the refresh token opaque instead of a JWT? Isn't JWT simpler?"
- **Naive answer:** "JWT is simpler."  ← the trap
- **The trap:** The interviewer wants the state's reasoning.
- **Expert answer:** The refresh token's entire purpose is to be **stateful** — revocable,
rotatable, and reuse-detectable. A JWT is self-contained, so you cannot revoke it, and
without the state you cannot detect the reuse. The moment you add the state to make the
JWT refresh token revocable, you have reinvented the opaque token with extra steps (the
signature's verification, the key's management, the claims' parsing). The opaque token is
the **minimal** expression: a random string, a hash, a row. JWT is simpler *only* in the
sense that it avoids a table — and that trade is exactly the one that costs you the
revocation and the theft's detection. The one place the JWT refresh wins is when the
**partner** must validate the token without calling you — then you'd use a JWT plus a
stateful's check.

### CQ4. "You say pin the algorithm. But what if you need to support multiple algorithms
during a migration?"
- **Naive answer:** "Read the alg from the header but check it's in a whitelist."  ← the trap
- **The trap:** The interviewer wants the subtle failure mode.
- **Expert answer:** A whitelist of *allowed* algorithms is necessary but **not sufficient** —
the classic bug is: the code checks "the alg is in the allowed set" but then, for the
symmetric case, uses **the key it looked up by that algorithm's type** — so an attacker
picks `HS256` (which is in the whitelist because you legitimately use it somewhere) and
the code finds the RSA public key. The safe design: **the algorithm determines the key
type, and the key is only ever used with its own type** — i.e., the `HS256` path uses a
`SecretKey`, the `RS256` path uses an `RSAPublicKey`, and the verifier never has a
secret key available at all. Then a `HS256` token simply has nothing to verify against.
The modern libraries (Nimbus's `JWSVerificationKeySelector`, the Spring's
`NimbusJwtDecoder`) enforce exactly this. The migration's safe approach: run **two
decoders** side by side (one per algorithm, each pinned, each with its own key's source),
and select between them on a **trusted** basis (the `kid`'s prefix, e.g. `rsa-2026-01` vs
`ec-2026-01`) — **never** on the token's `alg` alone.

### CQ5. "How do you know your JWT implementation is secure?"
- **Naive answer:** "We use a well-known library."  ← the trap
- **The trap:** The interviewer wants the assurance's process.
- **Expert answer:** A library removes the *implementation's* risk, not the *configuration's*
risk — and most real JWT breaches were configuration's failures (the `alg: none` accepted,
the `aud` not checked, the key in the repo, the HS256/RS256 confusion). So the assurance is
a **process**, not a library: (1) the **known-answer's tests** — a suite of the negative's
tests: the `alg: none` token, the HS256-with-the-public-key token, the expired token, the
wrong-`aud` token, the wrong-`iss` token, the missing-`exp` token, the `kid` with a path
traversal, the token with a duplicate's claim, the token with a huge's `exp`. **Every
one must be rejected** — and that suite is a *regression's test* that runs in the CI. (2)
the **the SAST's rules** (the `jwt-none-alg` in Semgrep, the security's code scanning).
(3) the **the dependency's monitoring** (the jjwt/nimbus's CVEs). (4) the **the
configuration's review** (the checklist: the pin, the issuer, the audience, the skew, the
size). (5) the **the penetration's test** (the token's tampering on the live's staging).
(6) the **the runtime's monitoring** (the reuse's detections, the auth's failure's
spikes, the unexpected's `kid`s, the verification's error's distribution — an alert on a
sudden spike in the "the signature's invalid" bucket is a strong attack's signal). The
framing: *"the library is necessary but not sufficient; the assurance comes from the
negative's test's suite and the configuration's review, because the failures are almost
always in the configuration."*

---

## 10. Rapid-Fire MCQs

**1.** The algorithm's confusion's attack is prevented by:
A) A long secret  B) Pinning the algorithm  C) The https  D) The rate's limiting
**✅ B**

**2.** The `alg: none` should be:
A) Allowed for the internal  B) Always rejected  C) Allowed with a flag  D) Ignored
**✅ B**

**3.** The symmetric's key (HS256) is safe when:
A) The API's services verify it  B) The issuer and the verifier are the same app  C) The key is long  D) The TLS is on
**✅ B**

**4.** The `kid` should be resolved by:
A) Fetching the URL in the header  B) A fixed in-memory map  C) A file's path  D) A SQL's concatenation
**✅ B**

**5.** The `jku`/`x5u` headers should be:
A) Honored  B) Ignored (or allowlisted)  C) Logged  D) Cached
**✅ B**

**6.** The RSA's key's size should be at least:
A) 1024  B) 2048  C) 512  D) 4096 always
**✅ B**

**7.** The key's rotation's correct order:
A) Sign → publish → retire  B) Publish → sign → wait → retire  C) Retire → sign → publish  D) Publish → retire → sign
**✅ B**

**8.** The emergency's key's rotation's first step:
A) Switch the signing  B) Remove the key from the JWKS  C) Notify the users  D) Delete the old's tokens
**✅ B**

**9.** The PII in the JWT's claims is:
A) Encrypted by default  B) Readable by anyone with the token  C) Hashed  D) Protected by the signature
**✅ B**

**10.** The token's length should be:
A) < 1 KB  B) < 10 KB  C) As long as needed  D) < 100 bytes
**✅ A**

**11.** The `iss` claim's validation prevents:
A) The expired's tokens  B) The token from another issuer  C) The replay  D) The CSRF
**✅ B**

**12.** The `aud` claim's validation prevents:
A) The expired's tokens  B) A token for API A being used on API B  C) The forging  D) The kid's injection
**✅ B**

**13.** In a multi-tenant's system, the tenant's id should come from:
A) The URL's path  B) The signed token's claim  C) The request's header  D) The query's parameter
**✅ B**

**14.** The `Authorization` header in the logs should be:
A) Logged fully for the debugging  B) Redacted  C) Logged at the debug's level only  D) Encoded
**✅ B**

**15.** The auth's responses should include:
A) `Cache-Control: public`  B) `Cache-Control: no-store`  C) Nothing  D) `Cache-Control: max-age=3600`
**✅ B**

**16.** The clock's skew's tolerance should be:
A) 0  B) ~60 seconds  C) 1 hour  D) 5 minutes
**✅ B**

**17.** The strongest's defence against the cross-tenant's leak:
A) The token's claim  B) The claim + the query's scoping + the DB's RLS  C) The gateway's check  D) The audit's log
**✅ B**

**18.** The `jti` claim enables:
A) The encryption  B) The revocation's blocklist + the audit's correlation  C) The compression  D) The audience's check
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The negative's test's suite**
Write the tests that MUST fail: the `alg: none`, the HS256-signed-with-the-public-key, the
expired, the wrong-`aud`, the wrong-`iss`, the missing-`exp`, the unknown-`kid`, the
`kid` with a path traversal, the token with a duplicate's claim.

**🧪 Lab 2 — The key's rotation (the safe's procedure)**
1. Publish a second key in the JWKS.
2. Switch the signing.
3. Verify: the old tokens still work, the new tokens work.
4. Remove the old key → the old tokens (still valid) now fail.
5. Assert: at no point did an active user get logged out.

**🧪 Lab 3 — The multi-tenant's isolation**
1. Two tenants in the DB.
2. A token for tenant A; a request to tenant B's resource.
3. Assert: 403/404 at **every** layer (the claim, the query, the RLS).

**🧪 Lab 4 — The token's leakage's audit**
1. Turn on the full request's logging.
2. Search the logs for the token's fragments, the `Authorization` header, the query's string.
3. Fix the leaks (the redaction, the path's logging).

---

## 12. Code Challenges

### Challenge 1 — The hardened's JWT library (hard)
**Goal:** A `JwtDecoder` with the algorithm's pin, the strict's validators, the in-memory's
`kid` map, the JWE's rejection, the duplicate-claim's rejection, and the metrics.
**Requirements:** the negative's test's suite (10+ cases), the fuzzing's harness, the
performance's benchmark.

### Challenge 2 — The key's rotation's service (hard)
**Goal:** The safe's rotation (publish → sign → wait → retire), the emergency's rotation, the
audit's log, the metrics, the runbook's documentation.
**Requirements:** the idempotency, the concurrency (two admins rotating at once), the
rollback, the tests.

### Challenge 3 — The tenant's isolation (hard)
**Goal:** The ORM's filter + the RLS + the connection's scoping, with the guarantee that a
forgotten `WHERE` can't leak.
**Requirements:** the Hibernate's integrator, the PostgreSQL's policy, the multi-tenant's
tests (the parallel's execution, the connection's pool's return), the performance's impact.

---

## 13. Scenario-Based Questions

**S1.** *"A security researcher reports that they can escalate to admin by changing the
token's `roles` claim. The token in their hand is a legitimately signed one. How do you
respond?"**
>
> **Ideal answer:** (1) **The immediate's containment:** (a) **the reproduction** — take
their token, modify the payload, re-sign with the same `alg` and `kid`, and send it. If
the API accepts it, the vulnerability is confirmed and it's a **P1** — the API is
compromised. (b) **the mitigation** — if it's the algorithm's confusion, the fix is the
algorithm's pin (and the immediate's mitigation: the temporary's `jwk-set-uri` with a
strict's configuration, or the WAF's rule rejecting the non-RS256's tokens). If it's the
`alg: none`, the same. If it's a **missing's signature's verification** entirely (a
deviation — the endpoint that parses the token without verifying), the fix is to route it
through the standard's filter. (2) **The root's cause's hunt:** the most likely culprits,
in order: (a) the **the algorithm's confusion** (the classic); (b) a **hand-rolled's
decoder** somewhere in the codebase (the endpoint that "just decodes" the token to read a
claim — the `Base64.getUrlDecoder().decode(payload)` shortcut, which is a **critical**
finding); (c) the **the key's compromise** (the weak or the leaked's key); (d) the
**the kid's injection** (the `kid` pointing to an attacker's key). Grep for the base64
decoding of the payload — if you find `Base64.getUrlDecoder()` applied to a token in a
controller, that's your bug. (3) **The blast's radius:** how many endpoints are affected?
Enumerate every endpoint that accepts a token (the `grep` for the `Authorization` header's
usage) and check each one's validation. The downgraded validator (the one that checks only
the signature, or only the `exp`) is the **real** scope. (4) **The detection's addition:**
(a) the **the negative's test's suite** in the CI (the exact payload from the report,
regression-tested); (b) the **the monitoring** — alert on the "the signature's invalid"
verification's error's spike, and log the `alg` + `kid` + the claims (not the token) of
every rejected token for the forensics; (c) the **the gateway's rule** (reject the tokens
whose `alg` isn't in the allowed's set, as a defence in depth). (5) **The remediation's
follow-through:** the secret's rotation (if the key's compromised — the emergency's
rotation), the git's history's audit, the SAST's rules (Semgrep's `jwt-none-alg` and a
custom rule banning the manual's payload's decoding), the code's review's checklist, and
the security's training. (6) The senior framing: "**the first question is always 'does it
reproduce against the staging?'** — and the second is '**is the signature actually
verified on every endpoint that accepts a token?'** The classic root cause isn't a subtle
crypto's flaw; it's an endpoint that decodes the JWT to read a claim without verifying it
(the `Base64.getUrlDecoder().decode(...)` shortcut). So the audit isn't just 'fix the
decoder' — it's '**find every place a token is read**', because the bug is a *missing*
verification somewhere, and the fix is incomplete until you've enumerated them all. The
defence in depth: the gateway's `alg` allowlist, the CI's negative's tests, and the
monitoring on the rejection's spike — because the next researcher won't report it."

**S2.** *"The security's team wants to rotate the signing key immediately because they
believe it was exposed. Rotating it will invalidate every access token and log out all
users. How do you handle this?"**
>
> **Ideal answer:** (1) **The triage (the first 15 minutes):** (a) **the severity's
assessment** — is the key *actually* exposed, or suspected? Where? (The git's history? The
CI's logs? A dumped heap? A former's employee's access? A misconfigured's S3 bucket?) The
action differs radically: a *confirmed* exposure → the immediate's removal; a *suspicion*
→ the containment (the audit's logs, the access's review) before the mass's logout. (b)
**the scope's calculation** — how many tokens are affected? The keys are usually used to
sign the access tokens (15 min) and possibly the refresh tokens (if the refresh is a JWT).
The access tokens die at the rotation; the refresh tokens survive if they're opaque and
stored in the DB — **so the users would be forced to re-login only if the refresh tokens
are also signed with the key**. That's a crucial question: **is the refresh token a JWT
in your design?** If it's opaque (the recommended), the emergency's rotation is a **15-minute
annoyance** (the access tokens die, the users refresh transparently) — NOT a mass's
logout. (2) **The containment's plan (the no-downtime version):** (a) generate the new key
in the KMS; (b) publish it in the JWKS **alongside** the old one — at this point, nothing
is broken and the old key still works; (c) switch the signing to the new key — the new
tokens are signed with the new key, the old tokens still verify (the old key is still
published); (d) **wait for the old tokens to expire** (the max TTL, e.g. 15-30 minutes) —
during this window the system is fully functional and only the compromised key remains
exposed; (e) remove the old key from the JWKS — now no live token was signed with it;
(f) if the refresh tokens are also JWTs, revoke them (the version's bump / the family) and
force the re-login. **The key's insight: the removal of the key from the JWKS is the
moment of the actual's protection — everything before it is preparation.** The order is
**publish → sign → wait → remove**, and the removal is deliberately **last**, so the
exposure's window is closed at the moment of the impact, not before. (3) **The forensics
(parallel):** (a) the **the issuance's audit** — which tokens were signed with the
compromised key, and when (the `kid` in the audit's log); (b) the **the access's logs** —
what those tokens did (the `sub` + the `jti` correlation); (c) the **the git's history**
and the CI's logs, the image's layers, the backups, the vault's audit — to find the
exposure's vector; (d) the **the blast's radius** — the data that could have been
accessed; (e) the **the notification's obligation** — the regulatory's assessment (the GDPR's
breach's notification, the contractual's notices). (4) **The remediation (the follow-through):**
(a) the **the removal of the exposure's source** (the git's history's rewrite, the
credential's rotation everywhere); (b) the **the prevention** — the secret's scanning in
the CI, the KMS-only's key's access (no key's material in the app at all), the
pre-commit's hook, the least-privilege's IAM, the access's reviews; (c) the **the
monitoring** — the alert on the key's usage (the sign's volume's anomaly, the sign's
origin's anomaly); (d) the **the postmortem** (the blameless's, the systemic's fixes). (5)
The senior framing: "**the question that determines the blast's radius is 'what else did
that key sign?'** If the refresh token is a JWT, the exposure is total and the users must
re-login; if it's opaque and hashed in the DB, the emergency's rotation costs the users one
invisible re-authentication. That single design decision — the opaque refresh token — is
what makes the incident's response a 15-minute operation instead of a crisis. And the
sequencing matters: **the removal of the key from the JWKS is the protection, so it goes
last** — publish, sign, wait for the TTL, remove. Do the removal first and you've logged
out every user for nothing. The other half is the forensics running in parallel — because
the key's exposure has to become an exposure's *report*, and in a regulated's environment
the notification's clock starts when you know, not when you finish."

---

## 14. Tricky Interview Questions

**T1. "What's the difference between `RS256` and `PS256`?"**
> Both are RSA. PKCS#1 v1.5 (`RS256`) vs PSS (`PS256`). PSS is the modern, randomized
padding — it's provably secure and it's the recommended one. The practical difference: some
older libraries don't support PSS, and there was historically a Bleichenbacher-style
signature's forgery concern with v1.5 in the JWT's context (largely mitigated by the
libraries' strictness). Use `PS256` where supported.

**T2. "Can you use the same RSA key for signing and encrypting?"**
> Technically possible but a bad practice — the key's usage separation. The `use` claim in
the JWK exists for this ("sig" vs "enc"). Different keys mean a compromise in one usage
doesn't affect the other, and the algorithms differ (RSA-OAEP for the encryption vs
RSA-SHA256 for the signature). Use separate keys.

**T3. "What's the `jti` for, besides revocation?"**
> The audit's correlation (which token's action), the idempotency's key (a request's replay
protection), the detection of the duplicate's issuance, the correlation across the services
in a distributed's trace, and the "the log out this device's token" (if the server keeps a
registry).

**T4. "If a token is stolen, can you tell who stole it?"**
> No — the token is bearer. Anyone presenting it is indistinguishable. You can only
correlate the *presentation* with the context (the IP, the user-agent, the device, the
timing) — and the differences from the legitimate's usage (the impossible's travel, the new's
user-agent, the odd's hour) are the signals. The defence is to make the token **not
bearer** — the DPoP (RFC 9449) or the mTLS's client certificates bind the token to the
client's key, so a stolen token is useless to the thief.

**T5. "What is a 'JWT confusion' attack between a JWT and a JWK?"**
> When a system accepts the *input type* as the *identifier* — e.g. a parameter that says
"the key to use" and can be a raw key or a URL to fetch a key. The attacker passes a URL to
their own server, and the system fetches and trusts it. The defense: strict, typed
discrimination (never infer the type from the string's shape), allowlisted origins, and
never fetching a key's URL from the request.

**T6. "Why is a 2048-bit RSA key considered safe but a 128-bit symmetric key also?"**
> Both are considered ~128 bits of security (RSA-2048 ≈ 112 bits by NIST's estimate — so
actually RSA-2048 is ~112 bits, and a 128-bit symmetric key is stronger). The point for the
interview: RSA-2048 is the *minimum* accepted today; RSA-3072+ is preferable for the
long-term's security (the quantum's threat requires the post-quantum's migration anyway).
And the cost of asymmetric: the verification is much slower than HMAC (which matters at
high throughput), and the keys are bigger (the network's cost for the JWKS).

**T7. "What happens if the JWKS endpoint is down?"**
> The verifier keeps using its **cached** keys until the cache expires, then it fails
closed (the token can't be verified → 401). Nimbus's `RemoteJWKSet` fails open with the
stale cache (it keeps the last known good keys) but only until the cache's TTL. The
mitigations: a long-enough cache TTL (the JWKS's rotation's propagation is bounded by it
too — a trade-off), the JWKS's HA, and the mirrored's endpoint. And the monitoring: the
JWKS's fetch's failure's rate is a critical metric.

**T8. "Can a JWT be revoked by changing the signing key?"**
> Only for the tokens signed with the *old* key — and only by removing that key from the
JWKS, which invalidates them all at once (the blunt's instrument). For a *single* token, no
— that's what the blocklist's for. And removing a key is the **emergency's** tool, not the
routine's.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the pin) → *"What if you have to support both RS256 and ES256?"*
→ Two decoders, each pinned, each with its own key's source, selected by a **trusted**
signal — the `kid`'s prefix (which the issuer controls and signs into the header) or the
`jku`'s allowlist. Never select by the token's `alg` alone. The Spring's `JwtDecoder`
isn't a composite, so you write a small `JwtDecoder` that delegates based on the `kid`
(and fails closed if the `kid` is unknown or ambiguous).

**F2** (after the rotation) → *"How do you test the rotation without logging out the
users?"*
→ A staging's environment with the JWKS's control: publish k2, switch the signing, assert
the old tokens still verify (a fixture token signed with k1), then remove k1 and assert the
*new* tokens still verify and the *old* ones fail. Plus the **canary**: rotate one
instance's key first, watch the error's rate, then roll the rest.

**F3** (after the tenant's isolation) → *"What if a query legitimately needs to cross
tenants (an admin's report)?"**
→ The RLS's bypass with a **separate, explicitly-authorized** path: a dedicated connection
or a `SECURITY DEFINER` function that takes the tenant as a parameter and validates the
caller's admin's rights. The rule: the cross-tenant's access must be a **deliberate,
audited** code path — never an accident of a missing `WHERE`.

**F4** (after the audit) → *"How do you handle the GDPR's erasure with the audit's logs?"**
→ The audit's logs are legitimate's interest (the security) and are typically retained
under a legal's basis, but the identifiers must be **pseudonymized**: log the `sub` as a
hashed/pseudonymous's id, keep the mapping in a separate, erasable's store, and define the
retention's periods per log's type. The design principle: **separate the identity from the
evidence**.

---

## 16. Real 3–5 Year Scenarios

**R1. "We need to add a new API service to the platform. It has to accept the existing
users' tokens. How do you onboard it safely?"**
>
> **Ideal answer:** (1) **The trust's model** (the questions to answer first): (a) which
issuer? (the same auth service — yes); (b) which audience? (a **new** `aud` for the new
API — the least's privilege, so a token for the new API can't be used on the old ones and
vice versa); (c) which scopes? (the new API's own — the auth service's mapping must be
extended); (d) which algorithm and key? (the same issuer's key, or a **per-audience** key —
the latter gives a better's blast's radius's containment, at the cost of the key's
management); (e) what does the API do with the user's identity? (the `sub` only — the
profile's lookup goes through the identity's service, not by putting the PII in the token).
(2) **The technical's onboarding** (the checklist): (a) the **the decoder's configuration**
(the issuer-uri or the jwk-set-uri, the **pinned** algorithm, the `aud` validator, the
issuer's validator, the 60s's skew); (b) the **the shared's library** — the validation's
logic should be a **shared module** (a Spring Boot starter), not a copy-paste. The reason
is the "the downgrade" risk: a hand-rolled decoder in a new service is exactly where a
missing's validation creeps in. The starter carries the pins, the validators, the
metrics, and the tests; (c) the **the authorization's mapping** (the scopes → the
permissions, the method's security's annotations); (d) the **the multi-tenant's scoping**
(if the new API is multi-tenant, the ORM's filter + the RLS — from day one, retrofitting
it is a nightmare); (e) the **the rate's limiting** and the **the audit's logging** (the
same's format, so the correlation works across the services). (3) **The security's
review's checklist** (the same negative's test's suite runs against the new service in
the CI — the exact same tests, so the new service can't be laxer by accident); the
penetration's test in the staging; the **the key's rotation's runbook** — the new service
must refresh the JWKS's cache (so it must support the `kid`'s rotation, and its cache's
TTL must be shorter than the rotation's window). (4) **The operational's readiness:** the
JWKS's cache's TTL (and the monitoring on the fetch's failures), the **the clock's NTP**
(the skew's tolerance is a band-aid, not a fix), the **the dashboards** (the auth's error's
distribution — a spike in the "the audience's invalid" means a misconfiguration in the new
service, and you want to catch it in minutes, not days), the **the runbook's links**. (5)
**The tricky's parts (the ones people forget):** (a) the **the `ver` claim** — if the
platform uses the version's counter for the revocation, the new service MUST implement the
check, or the revocation's story has a hole in the new service. **This is the single most
common onboarding's bug** — the new service accepts the revoked's tokens. (b) the
**the scope's naming's convention** (the new API's scopes must be namespaced, e.g.
`ordersapi:read`, or they'll collide with the existing ones). (c) the **the token's size**
— if the new API's claims are fat, the header's size grows for everyone. (d) the **the
audience's list** — if the client gets a token with both audiences, the token's size grows
and the isolation weakens. (6) The senior framing: "**the onboarding's risk isn't the
JWT's mechanics — it's the copy-paste's drift.** The new service's team will copy the
decoder's config from an existing service, and they'll drop the `aud` validator or the
`ver` check because they didn't understand why it was there. So the onboarding's process
must make the *safe* thing the *easy* thing: a shared starter with the pins and the
validators baked in, the same negative's test's suite in every service's CI (so a lax
service fails the build), and a **dashboards' anomaly's detection** (a new service's sudden
appearance in the "the audience's invalid" bucket is a misconfiguration's canary). The
specific's bug I'd bet on: the new service validates the signature and the `exp` but not
the `aud` and not the `ver` — so it's a token's replay's target and a revocation's
bypass. Both are caught by the shared's test's suite."

**R2. "Our tokens are 4 KB because we put the user's permissions and the org's data in
them. The API gateway rejects the requests with 'header too large'. What do we do?"**
>
> **Ideal answer:** (1) **The diagnosis:** the token's size is a **symptom** of the
claims' design. 4 KB of claims means either (a) a large's permission's set (the user's
roles/scopes enumerated — a user with 500 permissions), (b) the embedded's data (the
profile, the org's info), or (c) the bloated's custom's claims (the debug's info, the
feature's flags, the UI's preferences). The gateway's limit (8 KB for the headers, but
often 1-4 KB at the proxy) is the constraint; but the **real** problems with a 4 KB token
are: the **network's overhead** on every request (4 KB × 1M requests/hour = ~4.4 GB/hour of
pure's header's overhead), the **cache's efficiency** (the CDN/gateway's caching of the
responses keyed by the token), the **the client's memory**, and the **the parsing's CPU**
(every service parses it). (2) **The fix, in order of preference:** (a) **the claims' diet
— replace the enumerated's permissions with the *scope's names* (or a *permission's
version*/a *policy's id*): the API resolves the scope → the permission from its own
policy's engine (or a cached's policy's document), rather than the token carrying the
resolved's set. **This is the architectural fix** — the token should carry the *identity and
the coarse's grants*, not the *resolved's policy*. (b) **the reference's token** — the
token carries a `policy_ref` (the id of a policy's document) and the API fetches it (the
Redis, cached). This is the **the reference's token** pattern (the macaroons's cousin) and
it's the right answer for the fine-grained's permissions. (c) **the data's removal** — the
profile, the org's data, the feature's flags, the UI's preferences: none of that belongs
in the token. The API's endpoints (`/me`, `/permissions`) serve them, and the client's
cache holds them (with the ETag). (d) **the compression** — the JWT's payload is
**not** compressed by default, and you can't compress the signature's input without
changing the standard. (There's a non-standard "the compressed's JWT" but it's not
interoperable.) Don't go there. (e) **the gateway's limit's increase** — a band-aid, and it
pushes the problem to the other proxies. (3) **The migration's plan:** (a) the **the dual's
issuance** — issue the new, slim's tokens while still accepting the old's fat's ones (the
verifiers accept both, distinguished by the `kid` or a `ver` claim); (b) the **the clients'
renewal** — the access tokens are short-lived, so the fat's tokens age out in 15 minutes;
the refresh's tokens are opaque and unaffected → the **zero-downtime** migration; (c) the
**the permissions' resolution's move** — the API's side must gain the policy's resolution
before the new's tokens are issued (the order matters: the API first, then the issuer).
(4) **The lessons:** (a) the claims' budget should be **an explicit's design's constraint**
("< 1 KB", in the ADR and the review's checklist); (b) the **the tokens' size's monitoring**
(a metric on the average/percentile size, with an alert — a scope's addition that quietly
doubles the size should be caught); (c) the **the JWT's claims' review** in the PR's
template ("what's the new claim and how many bytes does it add?"); (d) the **the scopes'
taxonomy** — the naming's convention that keeps the permissions' count bounded (the
per-domain's scopes, not the per-action's). (5) The senior framing: "**the 4 KB token is
the symptom of the wrong's model — the token is being used as a 'resolved's permissions'
carrier instead of an 'identity + coarse's grant' carrier.** The instant you put the
resolved's permission's set in the token, the size is unbounded — it scales with the user's
role's complexity, and the permissions' model will change. The fix is to move the
resolution to the API's side (the policy's engine, cached) and have the token carry only
the *scope's names*. And the migration is actually easy **because** the access tokens are
short-lived and the refresh tokens are opaque: you can issue the new's format and the old
ones age out in 15 minutes with no client change and no logout. The other half is the
**prevention**: a size's budget in the design's checklist and a metric on the token's size —
because the next person's feature request will add a claim and nobody will notice the
header's growth until the gateway starts rejecting requests."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The 401s after a key's rotation | The new's key published after the signing's switch | Publish → sign → wait → retire |
| The p99's spikes every 5 minutes | The JWKS's cache's expiry + a slow's fetch | The longer's `cacheTTL`; the JWKS's HA |
| The 401s with "the audience's invalid" | The `aud` mismatch (the new service) | The config; the dashboard's canary |
| The 401s with "the expired" (the clock) | The server's NTP drift | The monitoring on the clock's offset |
| The intermittent's 401s (the multi-instance) | The version's cache's TTL vs the instance | The consistent's invalidation; the lower's TTL |
| The 502s from the gateway | The header's size | The claims' diet |
| The mass's logout on a deploy | The key's rotation without the overlap | The safe's procedure |
| The "the signature's invalid" spike | An attack, or a clock/key's issue | The alert; the forensic's check |
| The old's tokens failing (the clients) | The key's retired too early | The rotation's procedure |
| The `kid` not found | The JWKS's cache is stale | The `kid`'s publication before the signing |

---

## 18. Debugging Scenarios

**D1. "Inspect a token's header safely (no verification):"**
```bash
echo "$TOKEN" | cut -d. -f1 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
```

**D2. "Find the tokens with a suspicious 'alg':"**
```sql
-- the audit's log
SELECT alg, kid, count(*) FROM token_verification_log
 WHERE created_at > now() - interval '1 hour'
 GROUP BY alg, kid ORDER BY count(*) DESC;
-- ⚠ any 'alg' other than RS256/ES256 in the logs is an attack's signal
```

**D3. "Check the JWKS's freshness:"**
```bash
curl -s https://auth.example.com/.well-known/jwks.json | jq '.keys[] | {kid, alg, use}'
```

**D4. "Simulate the algorithm's confusion against your own API (in the staging):"**
```bash
# 1. take a legit token, flip the alg to HS256
HEADER=$(echo "$TOKEN" | cut -d. -f1)
PAYLOAD=$(echo "$TOKEN" | cut -d. -f2)
NEW_HEADER=$(echo -n '{"alg":"HS256","typ":"JWT"}' | base64 -w0 | tr '+/' '-_' | tr -d '=')
# 2. sign it with the PUBLIC key as the HMAC secret
#    (in the staging only!)
# 3. send it → the API MUST reject it with 401
```

**D5. "Find the endpoints that read a token without verifying it:"**
```bash
grep -rn "getUrlDecoder\|Base64.decode\|parseClaimsJwt\|readClaimsJwt" --include=*.java .
# ⚠ any hit in a controller is a critical finding
```

**D6. "Detect the cross-tenant's query (the static's scan):"**
```bash
grep -rn "findById(" --include=*Repository.java . | grep -v "AndTenant\|AndUserId\|AndAccountId"
# the hits without a tenant's scoping are the candidates
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the security's architecture for a JWT-based platform (the SPAs, the mobile,
the internal's services, the partners).**
> **Answer structure:** (1) The **the trust's zones** (the edge, the internal, the external,
the partner) and the identity's provider per zone; (2) the **the key's hierarchy** (the
KMS's CMKs, the per-audience's keys, the `kid`, the rotation's automation); (3) the **the
validation's centralization** (the shared's starter, the negative's test's suite in every
CI, the gateway's `alg` allowlist); (4) the **the revocation's strategy** (the version, the
blocklist, the events); (5) the **the tenant's isolation** (the claim + the ORM's filter +
the RLS); (6) the **the observability** (the verification's error's distribution, the
token's size, the JWKS's fetch's health, the reuse's detections, the key's usage's
anomalies); (7) the **the runbooks**.

**Q2. 🏗️ Design the key's management for a regulated's environment.**
> (1) The **the KMS's hierarchy** (the account's separation, the per-environment's CMKs, the
HSM); (2) the **the access's control** (the app's role can sign but not export; the
separation of the duties for the rotation's approval); (3) the **the rotation's
automation** (the scheduled, the on-demand, the overlap, the audit's log of every sign);
(4) the **the non-exportability** (the remote's sign — the app never holds the key); (5)
the **the FIPS's validation** (the approved's algorithms, the validated's module); (6) the
**the backup & the DR** (the key's availability across the regions, the continuity of the
verification during a region's failover); (7) the **the evidence** (the audit's export for
the regulator).

**Q3. 🏗️ Design the tenancy's isolation for a multi-tenant's SaaS.**
> (1) The **the tenancy's models** (the shared's schema with a `tenant_id` vs the
schema-per-tenant vs the database-per-tenant — the trade-offs: the cost's isolation vs the
operational's overhead); (2) the **the enforcement's layers** (the claim, the URL, the
ORM's filter, the RLS, the connection's scoping); (3) the **the testing's strategy** (the
cross-tenant's access's attempts as a first-class's test's suite, the property-based's
testing that no query can escape the tenant); (4) the **the operational's aspects** (the
connection's pool's reset of the tenant's context — a leak there is a breach; the
background's jobs and the multi-tenant's fan-out); (5) the **the onboarding's enforcement**
(make it impossible to create a repository that bypasses the filter — the ArchUnit's rule);
(6) the **the audit's evidence** (the cross-tenant's attempt's logging and the alerting).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the ALGORITHM: RS256/ES256 (asymmetric) by default; HS256 only if the       │
│  issuer == the verifier. NEVER 'none'. NEVER read the alg from the token.     │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ATTACKS:  the alg's confusion | the weak's key | the claim's tampering   │
│  the XSS | the logs | the URL | the refresh's theft | the kid's injection      │
│  the jku's SSRF | the lax validator (the downgrade)                           │
├──────────────────────────────────────────────────────────────────────────────┤
│  the KEYS: the KMS (never the repo/config), the asymmetric, the kid, the      │
│  separate's envs, the rotation's procedure: PUBLISH → SIGN → WAIT → REMOVE    │
├──────────────────────────────────────────────────────────────────────────────┤
│  the VALIDATION (all 5, always): signature | exp/nbf | iss | aud | authz      │
│  + the required's claims (sub, exp, iat, jti) + the 60s's skew                │
├──────────────────────────────────────────────────────────────────────────────┤
│  the CLAIMS: sub=the id, aud, iss, jti, scope, ver — NO PII, < 1 KB         │
│  the STORAGE: the access in the memory, the refresh in the httpOnly's cookie  │
│  the LOGS: redact the Authorization; never the query's string                 │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠ The multi-tenant: the claim + the query's scoping + the RLS (all three)  │
│  ⚠ The 'kid': a fixed's map — never a URL, never a file's path                │
│  ⚠ The 'jku'/'x5u': ignored                                                  │
│  ⚠ The negative's test's suite in the CI is the real's assurance             │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md) — the standard's flows, the PKCE, the Keycloak.
- → [05 · Spring Security](../05-Spring-Security/README.md) — the filter chain, the method's security.
- → [Chapter 01 · JWT Architecture & Anatomy](./01-JWT-Architecture-and-Anatomy.md) — the JOSE's ecosystem, the JWE.

---
**Next Chapter:** → [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md)
**Previous Chapter:** ← [03 · Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md)
