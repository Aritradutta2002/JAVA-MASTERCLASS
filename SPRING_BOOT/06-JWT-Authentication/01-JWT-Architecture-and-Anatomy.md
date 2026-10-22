# 01 · JWT Architecture & Anatomy

> **Level:** Beginner → Advanced
> **Interview Frequency:** ★★★★★ ("How does a JWT work? What are its claims?")
> **Prerequisite:** [05 · Spring Security](../05-Spring-Security/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — A Verifiable Token](#2-mental-model--a-verifiable-token)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The anatomy](#31-the-anatomy)
  - [3.2 The header](#32-the-header)
  - [3.3 The payload](#33-the-payload)
  - [3.4 The signature](#34-the-signature)
  - [3.5 Base64URL](#35-base64url)
  - [3.6 The registered claims](#36-the-registered-claims)
  - [3.7 The private claims](#37-the-private-claims)
  - [3.8 The signature algorithms](#38-the-signature-algorithms)
  - [3.9 `alg: none` and algorithm confusion](#39-alg-none-and-algorithm-confusion)
  - [3.10 The JOSE ecosystem](#310-the-jose-ecosystem)
  - [3.11 JWT vs session](#311-jwt-vs-session)
  - [3.12 JWT vs opaque token](#312-jwt-vs-opaque-token)
  - [3.13 JWE (the encrypted JWT)](#313-jwe-the-encrypted-jwt)
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
- [16. Real 3–5 Year Experience Scenarios](#16-real-35-year-experience-scenarios)
- [17. Frequently Asked Production Issues](#17-frequently-asked-production-issues)
- [18. Debugging Scenarios](#18-debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Cheat Sheet — One-Page Summary](#20-cheat-sheet--one-page-summary)
- [21. Follow-up Learning](#21-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **A JWT is a JSON object, base64url-encoded, signed.** Three parts separated by dots:
  `header.payload.signature`.
- **The header** = the algorithm + the key id (`{"alg":"RS256","typ":"JWT","kid":"abc"}`).
- **The payload** = the claims (identity, roles, expiry, metadata).
- **The signature** = `HMAC/RSA/ECDSA` over `base64url(header) + "." + base64url(payload)`.
- **The payload is NOT encrypted — anyone can decode it.** Base64 is encoding, not encryption.
  Never put a secret in a JWT.
- **The signature gives integrity and authenticity** (not confidentiality). The server verifies
  it with the issuer's public key; if it matches, the claims are trustworthy.
- **Verification requires the key** — and since the algorithm comes from the *token's header*,
  you must **pin the expected algorithm** (otherwise `alg: none` and HMAC/RSA confusion).
- **The registered claims** you must know: `iss` (issuer), `sub` (subject), `aud` (audience),
  `exp` (expiry), `nbf` (not before), `iat` (issued at), `jti` (unique id).
- **In Spring:** `spring-boot-starter-oauth2-resource-server` + `NimbusJwtDecoder` +
  `JwtAuthenticationConverter` — you almost never hand-roll verification.
- **The one-liner:** *"A JWT lets the server verify a token's integrity without storing
  anything — that's the win. The trade-offs are no revocation before expiry, no
  confidentiality, and the size. So: short-lived access tokens, rotating refresh tokens, and
  never a secret in the payload."*

---

## 1. What & Why

### The problem

Traditional session auth: the server stores the session (`sessionId → user`) and the client
holds a cookie. Every request requires a **server-side lookup**. That breaks down when:

- The app is **stateless** (serverless, autoscaling, horizontal scale-out with sticky sessions
  disabled).
- The **API is consumed by third parties** (a mobile app, a partner, a service) — cookie
  semantics don't fit.
- **Services are independent** — service A must verify service B's calls without calling an
  auth server on every request.

### The JWT's answer

**Self-contained, signed, verifiable credentials.** The issuer gives the client a signed proof
containing the claims. Any party with the issuer's **public key** can verify that proof
**offline, with no database lookup**.

### The trade-offs you must be able to state

| ✅ Gains | ❌ Costs |
|---------|---------|
| No server-side session store (stateless) | **No instant revocation** before `exp` |
| The claims are readable by the holder (and any interceptor) | **No confidentiality** (base64, not encryption) |
| Verifiable offline (microservices, mobile) | **Size** (header and cookie limits) |
| Self-contained context (no extra DB round trip) | **Staleness** (claims can't change until the next issuance) |
| The standard (the core of OAuth2/OIDC) | **Clock dependency** (`exp`/`nbf`/`iat` need synced clocks) |

---

## 2. Mental Model — A Verifiable Token

```text
   the SERVER (the issuer)                        the CLIENT (the holder)
   ─────────────────────────                       ─────────────────────
   1. build the claims
        { "sub": "u1", "exp": 1735689600 }
   2. sign with the PRIVATE key
        signature = Sign(privKey, header + "." + payload)
   3. concatenate
        eyJhb... . eyJzd... . Xy9_Z...
                    │
                    └──────────────►  the client stores it (memory,
                                       localStorage, cookie) and sends it:
                                       "Authorization: Bearer <token>"

   the RESOURCE SERVER (the verifier)
   ─────────────────────────────────────
   1. split the token into 3 parts
   2. fetch the issuer's PUBLIC key (the JWKS, cached)
   3. recompute: Sign-check(pubKey, header + "." + payload) == signature?
      ├─ no  → 401 (invalid signature / expired / wrong issuer)
      └─ yes → the claims are trusted → build the Authentication
```

> **Caption:** the signature is a **MAC over the encoded content**. Anyone can read the content;
> only the key holder can prove it wasn't changed. Verification is asymmetric (private key
> signs, public key verifies) — which is exactly why the resource server doesn't need the
> issuer's secret.

---

## 3. Deep Dive — Theory

### 3.1 The anatomy

```text
eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleS0xMjMifQ
  .  ← the separator (a dot)
eyJzdWIiOiJ1MTIzIiwiZXhwIjoxNzM1Njg5NjAwLCJyb2xlcyI6WyJVU0VSIl19
  .
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c
```

| Part | Content | Encoded? | Readable by anyone? |
|------|---------|----------|---------------------|
| 1 | the header | base64url | ✅ Yes |
| 2 | the payload (claims) | base64url | ✅ Yes |
| 3 | the signature | binary → base64url | ❌ (it's a MAC) |

**`header.payload` is the *signed message*.** The signature covers exactly those two parts
(concatenated with a dot), encoded with base64url.

### 3.2 The header

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-2026-01"
}
```

| Field | Meaning |
|-------|---------|
| `alg` | the signature algorithm — **must be pinned by the verifier** |
| `typ` | token type (`JWT`, `at+jwt` for OAuth2 access tokens, `dpop+jwt`) |
| `kid` | key id — tells the verifier **which key** to use (essential for rotation) |
| `cty` | content type (for nested/JWE encryption) |
| `jku` / `x5u` | key URLs (`jku` = JWK Set URL, `x5u` = X.509 URL) — **never trust these blindly** (SSRF / key injection) |

**The `alg` values (JWA):**

| Family | Algorithms | Key | Notes |
|--------|-----------|-----|-------|
| HMAC | HS256, HS384, HS512 | **symmetric** secret | Both sides know the secret → the verifier can *forge*. Use only when issuer and verifier are the same party. |
| RSA | RS256, RS384, RS512, PS256… | **asymmetric** key pair | RSA PKCS#1 v1.5 (RS\*) or PSS (PS\*). The most common in production. |
| ECDSA | ES256, ES384, ES512 | elliptic curve | Shorter signatures (64 vs 256 bytes) → smaller tokens. `ES256` (P-256) is popular. |
| None | `none` | — | **Must never be accepted** (the attack: strip the signature). |

### 3.3 The payload

```json
{
  "sub": "user-123",
  "iat": 1735689000,
  "exp": 1735690800,
  "iss": "https://auth.example.com",
  "aud": ["orders-api"],
  "jti": "8f1c...",
  "scope": "orders:read orders:write",
  "roles": ["USER"],
  "tenantId": 42,
  "email": "user@example.com"
}
```

**The payload is the claims set:** registered, public, and private claims.

### 3.4 The signature

For **HS256** (symmetric):
```text
signature = HMAC-SHA256(
    base64url(header) + "." + base64url(payload),
    secret
)
```

For **RS256** (asymmetric):
```text
signature = RSA-SHA256-PKCS1v1.5(
    base64url(header) + "." + base64url(payload),
    privateKey        // the issuer
)
// the verifier:
ok = RSA-SHA256-PKCS1v1.5-verify(
    base64url(header) + "." + base64url(payload),
    signature,
    publicKey         // the verifier
)
```

For **ES256** (ECDSA):
```text
signature = ECDSA-SHA256(msg, privateKey)   // the DER-encoded (r,s)
```

### 3.5 Base64URL

Base64URL is Base64 with:
- `+` → `-`
- `/` → `_`
- **no padding** (`=`)

Why: JWTs travel in HTTP headers, cookies, and URLs. Standard Base64's `+` and `/` are
URL-unsafe, and `=` padding breaks concatenation and some query-string parsers.

**Java:**
```java
Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
Base64.getUrlDecoder().decode(tokenPart);
```

**⚠ Never use `java.util.Base64.getEncoder()` for JWT segments** — the standard alphabet will
corrupt the token (or throw).

### 3.6 The registered claims

| Claim | Name | Meaning | Production note |
|-------|------|---------|-----------------|
| `iss` | Issuer | who issued the token | **Verify it** (`JwtIssuerValidator`) — otherwise another issuer's token is accepted |
| `sub` | Subject | who the token is about | The user id; the `principal` by default |
| `aud` | Audience | who the token is for | **Verify it** (`JwtAudienceValidator`) — prevents replay across APIs |
| `exp` | Expiry | when it stops being valid | Short TTL (5–15 min for access tokens) |
| `nbf` | Not Before | when it becomes valid | Clock skew tolerance |
| `iat` | Issued At | when it was issued | Clock skew tolerance; the refresh-age check |
| `jti` | JWT ID | unique id | The **blocklist** and replay detection; the refresh family |

**`iss` and `aud` are the most commonly missed.** A token issued for `orders-api` being
accepted by `payments-api` is a real vulnerability (the confused deputy).

### 3.7 The private claims

Anything you define: `role`, `roles`, `scope`, `permissions`, `tenantId`, `email`, `name`.

**Rules:**
- Don't duplicate standard semantics (`exp` vs `expiresAt` → use `exp`).
- Keep it small (size).
- Don't put anything secret, or mutable-but-critical (permissions change → the token is stale
  until it expires).

### 3.8 The signature algorithms

```text
  HS256  ──▶ [ HEADER.payload ] ──HMAC(secret)──▶ sig
             both sides share the secret  ⇒ the verifier can FORGE

  RS256  ──▶ [ HEADER.payload ] ──RSA(priv)──▶ sig ──RSA-verify(pub)──▶ ok
             only the issuer has the priv     ⇒ the verifier cannot forge
```

**The comparison:**

| | HMAC (HS256) | RSA (RS256) | ECDSA (ES256) |
|---|---|---|---|
| Key size | 256-bit secret | 2048+ bit | 256-bit curve |
| Token size | ~identical | ~identical | shorter (64-byte signature vs 256) |
| Verification speed | fast | moderate | **fastest** |
| Issuer/verifier split | **must be the same party** | ✅ any party with the public key | ✅ same |
| Production use | Single service | The common default | Mobile (size) |

**The rule:** if **issuer and verifier are different parties** (the OAuth2 model), use an
**asymmetric** algorithm (RS/ES). Symmetric is only for self-issued tokens where the same
secret signs and verifies.

### 3.9 `alg: none` and algorithm confusion

**Attack 1 — `alg: none`:**
```json
{ "alg": "none", "typ": "JWT" }
```
The attacker strips the signature. If the verifier trusts the token's header for the algorithm
and accepts `none`, the token is accepted. **Mitigation: never allow `none`; pin the
algorithm.**

**Attack 2 — algorithm confusion (RS256 → HS256):**
```text
1. The service publishes the RSA PUBLIC key at /oauth2/jwks
2. The attacker takes that public key and uses it as an HMAC SECRET
3. The attacker signs the token with HS256, using the public key as the secret
4. The verifier (accepting both RS256 and HS256, reading alg from the token) verifies it
   with HMAC using the public key as the secret → VALID
```

**Mitigation:** the verifier must **only** accept the algorithms it configured (never the
token's choice), and with an asymmetric algorithm it must use the **public key as a public
key** (never as an HMAC secret). Spring's `NimbusJwtDecoder` handles this correctly when you
don't add a symmetric decoder to the same decoder.

**The safe Spring configuration:**
```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(props.jwkSetUri()).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(props.issuer()),   // exp/nbf/iss
            new JwtAudienceValidator(props.audience())));            // aud
    return decoder;
}
```
> ⚠ **Never** configure a `NimbusJwtDecoder` with a *secret* while also exposing a JWKS (or
> vice versa) in the same app. That is the confusion setup.

### 3.10 The JOSE ecosystem

```text
  JOSE = JSON Object Signing and Encryption
    ├── JWS (JSON Web Signature)  — the signed token    → "I know who sent this"
    ├── JWE (JSON Web Encryption) — the encrypted token → "Only you can read this"
    ├── JWK (JSON Web Key)        — key material (public/private)
    ├── JWK Set                   — the key set (JWKS, published at a URL)
    └── JWA (JSON Web Algorithms) — the algorithm registry
```

**The RFCs:**
| RFC | What |
|-----|------|
| RFC 7515 | JWS (signature) |
| RFC 7516 | JWE (encryption) |
| RFC 7517 | JWK (key format) |
| RFC 7518 | JWA (algorithms) |
| RFC 7519 | **JWT** (claims) |
| RFC 7523 | JWT authorization grants |
| RFC 7638 | JWK thumbprint (key identification) |
| RFC 8725 | **JWT best current practices** (algorithm pinning, `kid` validation) |

### 3.11 JWT vs session

| Aspect | Session (cookie) | JWT |
|--------|------------------|-----|
| Storage | **Server** (memory/Redis) | **Client** (cookie/localStorage/memory) |
| Revocation | **Instant** (delete the session) | Only by expiry (or blocklist/version) |
| Contents | An opaque id | Readable claims |
| Scale-out | A shared store (Redis) or sticky sessions | **Stateless** — any instance can verify |
| Data change | Immediate (next request re-reads) | Until the next issuance |
| Cookie theft | The server can invalidate | Valid until `exp` |
| Size | ~40 bytes (the id) | 300–2000+ bytes |

**The production compromise (most common architecture):**
- **Access token** = JWT (short, 5–15 min, stateless).
- **Refresh token** = **opaque random** string (stored hashed in the DB) → revocable,
  rotatable, detectable on reuse.

### 3.12 JWT vs opaque token

```text
  the OPAQUE token:  "a7f3d9e2b1c8..."  (a random string)
      the server looks it up → state, revocation, instant expiry
      ✅ revocable, ✅ attributes are changeable
      ❌ state, ❌ a DB lookup per request

  the JWT:  eyJhbGci... . eyJzdWIi... . SflKxw...
      the server verifies the signature → no lookup
      ✅ stateless, ✅ fast, ✅ self-contained claims
      ❌ not revocable before expiry, ❌ not confidential, ❌ stale claims
```

**Modern best practice:** the **hybrid** — JWT access token + opaque refresh token. The API is
stateless; session control lives in the refresh token's row.

### 3.13 JWE (the encrypted JWT)

**JWS** = signed (readable). **JWE** = encrypted (opaque to the client).

```text
  a JWE has 5 parts:  header.encrypted_key.iv.ciphertext.tag
```

**When you need it:**
- The token carries **sensitive claims** (PII, internal ids, entitlements the client shouldn't
  read).
- You need the "opaque token" property without server-side state.

**The costs:** complexity, key management (content encryption key vs signature key),
non-debuggability, and interoperability (most libraries handle JWS well, JWE less so).
**In a typical 3–4 year backend, JWS + short TTL + "no secrets in the payload" is the right
call.** Mention JWE as the option for sensitive claims.

---

## 4. Visualizations

### 4.1 The structure

```text
 ┌────────────────────────────────────────────────────────────────────────┐
 │ BASE64URL(HEADER)                                                      │
 │ { "alg":"RS256", "typ":"JWT", "kid":"k1" }                             │
 └───────────────────────────┬────────────────────────────────────────────┘
                             │  the signed message = header + "." + payload
                             ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ BASE64URL(PAYLOAD)                                                     │
 │ { "sub":"u1", "iss":"...", "aud":["api"], "exp":..., "scope":"..." }   │
 └───────────────────────────┬────────────────────────────────────────────┘
                             │
                             ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │ SIGNATURE = Sign(privateKey, base64url(header) + "." + base64url(payload))│
 │ encoded as base64url                                                    │
 └────────────────────────────────────────────────────────────────────────┘
```

### 4.2 The issuance and verification flow

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant I as Issuer (auth server)
    participant K as JWKS endpoint
    participant R as Resource server

    C->>I: POST /oauth2/token (grant=password / refresh_token)
    I->>I: verify the credentials
    I->>I: build the claims (sub, exp, scope)
    I->>I: sign with the private key (RS256)
    I-->>C: { access_token: "eyJ...", refresh_token: "opaque", expires_in: 900 }
    C->>R: GET /api/orders  Authorization: Bearer eyJ...
    R->>K: GET /.well-known/jwks.json (cached!)
    K-->>R: the public keys (kid → key)
    R->>R: split the token, recompute the signature
    R->>R: validate: signature, exp, nbf, iss, aud
    alt valid
        R-->>C: 200 OK
    else invalid
        R-->>C: 401 Unauthorized
    end
```

### 4.3 The claim lifetime

```text
  ──────────|------------------------|──────────►
             issued (iat)            exp
             │                        │
             │◄──── the access token window (5-15 min) ────►│
             │◄─────── the refresh token window (7-30 days) ──────────────►│
```

---

## 5. Worked Examples

### 5.1 Decoding a token by hand

```bash
# the token
TOKEN="eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImtleS0xMjMifQ.eyJzdWIiOiJ1MTIzIiwiZXhwIjoxNzM1Njg5NjAwfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

# the header
echo "$TOKEN" | cut -d. -f1 | base64 -d 2>/dev/null; echo
# {"alg":"RS256","typ":"JWT","kid":"key-123"}

# the payload
echo "$TOKEN" | cut -d. -f2 | base64 -d 2>/dev/null; echo
# {"sub":"u123","exp":1735689600}

# the signature (binary — not human readable)
echo "$TOKEN" | cut -d. -f3 | base64 -d 2>/dev/null | xxd | head -3
```

**In Java (the JDK's built-in, no library):**
```java
public static String decodeSegment(String segment) {
    return new String(Base64.getUrlDecoder().decode(segment), StandardCharsets.UTF_8);
}
```

### 5.2 A dry run of the verification

```text
  the token header:  {"alg":"RS256","kid":"k1"}
  the token payload: {"sub":"u1","exp":1735689600,"aud":["orders-api"]}

  the verifier:
    1. split → [h, p, s]
    2. header alg = "RS256"  → is it in my allowed list {RS256}?  YES
    3. header kid = "k1"     → the JWKS cache → the public key
    4. recompute: RSA-verify(pubKey, h + "." + p, s)
         → TRUE
    5. the claims:
         exp (1735689600) > now (1735689000)?          YES
         iss == "https://auth.example.com"?            YES
         aud contains "orders-api"?                    YES
    6. → the Authentication is built:
         principal = "u1"
         authorities = [SCOPE_orders:read]
```

**The failure modes:**
| Check that fails | Result | Status |
|------------------|--------|--------|
| Signature | Forged or altered token | 401 |
| `exp` | Expired | 401 (`ExpiredJwtException`) |
| `iss` | Wrong issuer | 401 (`InvalidIssuerException`) |
| `aud` | Wrong audience | 401 (`InvalidAudienceException`) |
| Missing `exp` | Unbounded token | 401 (if you require it) |

### 5.3 The Spring Boot 3 setup (resource server)

```bash
# the pom
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>
```

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com/realms/myapp
          audiences: orders-api
```

**What `issuer-uri` does:** Spring Boot **discovers** the issuer metadata at startup
(`https://auth.example.com/realms/myapp/.well-known/openid-configuration`), reads `jwks_uri`
and the supported algorithms, and configures the `JwtDecoder` for you.

> ⚠ **The startup requirement:** the app **fails to start** if the issuer is unreachable (eager
> discovery). In production with a flaky auth server, use `jwk-set-uri` (no discovery) or lazy
> initialization. This is the classic "the app won't start in staging" bug.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A hand-rolled JWT service (for learning)

> ⚠ **In production use `spring-boot-starter-oauth2-resource-server`.** This exists to show the
> mechanics.

```java
@Service
@RequiredArgsConstructor
public class JwtService {

    private final SecretKey signingKey;                 // HMAC secret (>= 32 bytes for HS256)
    private final ObjectMapper objectMapper;
    private final Duration accessTokenTtl = Duration.ofMinutes(15);
    private final Duration refreshTokenTtl = Duration.ofDays(7);

    public IssuedTokens issue(UserPrincipal user) {
        Instant now = Instant.now();
        String accessToken = buildAccessToken(user, now);
        String refreshToken = buildRefreshToken(user, now);
        return new IssuedTokens(accessToken, refreshToken, accessTokenTtl.toSeconds());
    }

    private String buildAccessToken(UserPrincipal user, Instant now) {
        Map<String, Object> header = Map.of("alg", "HS256", "typ", "JWT");
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("sub", user.getId().toString());
        claims.put("iss", "myapp");
        claims.put("aud", "myapp-api");
        claims.put("iat", now.getEpochSecond());
        claims.put("exp", now.plus(accessTokenTtl).getEpochSecond());
        claims.put("jti", UUID.randomUUID().toString());
        claims.put("scope", String.join(" ", user.getScopes()));
        return sign(header, claims, signingKey);
    }

    private String buildRefreshToken(UserPrincipal user, Instant now) {
        // the refresh token is OPAQUE (not a JWT) — revocable and rotatable
        return user.getId() + ":" + UUID.randomUUID() + ":"
                + now.plus(refreshTokenTtl).getEpochSecond();
    }

    private String sign(Map<String, Object> header, Map<String, Object> claims, SecretKey key) {
        String encodedHeader = encode(header);
        String encodedPayload = encode(claims);
        String signingInput = encodedHeader + "." + encodedPayload;
        byte[] signature = signBytes(signingInput, key);
        return signingInput + "." + encode(signature);
    }

    private byte[] signBytes(String input, SecretKey key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to sign JWT", e);
        }
    }

    public Jws<Claims> parse(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer("myapp")
                    .requireAudience("myapp-api")
                    .clockSkewSeconds(60)
                    .build()
                    .parseSignedClaims(token);
        } catch (ExpiredJwtException e) {
            throw new AccessTokenExpiredException(e);
        } catch (JwtException e) {
            throw new InvalidTokenException(e);
        }
    }

    private String encode(Object object) {
        try {
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(objectMapper.writeValueAsBytes(object));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to encode JWT segment", e);
        }
    }
}
```

### 6.2 The production configuration (resource server)

```java
@Configuration
@EnableWebSecurity
public class ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    @Value("${app.security.audience}")
    private String audience;

    @Bean
    JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();

        OAuth2TokenValidator<Jwt> validators = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),   // exp, nbf, iss
                new JwtAudienceValidator(audience),                  // aud
                new JwtTimestampValidator(Duration.ofSeconds(60))    // clock skew
        );
        decoder.setJwtValidator(validators);
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("scope");
        authoritiesConverter.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        converter.setPrincipalClaimName("sub");
        return converter;
    }

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/api/**")
            .csrf(csrf -> csrf.disable())
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
                .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                .accessDeniedHandler(new BearerTokenAccessDeniedHandler()));
        return http.build();
    }
}
```

### 6.3 A custom `AuthenticationConverter` (roles, not just scopes)

```java
@Component
public class JwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        // the scopes (fine-grained)
        String scope = jwt.getClaimAsString("scope");
        if (scope != null) {
            Arrays.stream(scope.split(" "))
                  .filter(s -> !s.isBlank())
                  .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
                  .forEach(authorities::add);
        }
        // the roles (coarse)
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
            roles.stream()
                 .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                 .forEach(authorities::add);
        }
        return authorities;
    }
}
```
```java
@Bean
JwtAuthenticationConverter jwtAuthenticationConverter(JwtRolesConverter rolesConverter) {
    JwtGrantedAuthoritiesConverter granted = new JwtGrantedAuthoritiesConverter();
    granted.setAuthoritiesClaimName("scope");
    granted.setAuthorityPrefix("SCOPE_");

    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        Set<GrantedAuthority> authorities = new HashSet<>(granted.convert(jwt));
        authorities.addAll(rolesConverter.convert(jwt));
        return authorities;
    });
    return converter;
}
```

### 6.4 Getting the current user

```java
@Component
public class CurrentUser {

    public UserPrincipal get() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuth) {
            Jwt jwt = jwtAuth.getToken();
            return UserPrincipal.builder()
                    .id(Long.valueOf(jwt.getSubject()))
                    .username(jwt.getClaimAsString("preferred_username"))
                    .tenantId(jwt.getClaimAsString("tenantId"))
                    .build();
        }
        throw new IllegalStateException("No authenticated principal in the context");
    }
}
```

### 6.5 A token introspection endpoint (admin/debug)

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class TokenController {

    private final JwtDecoder jwtDecoder;

    @GetMapping("/introspect")
    public Map<String, Object> introspect(@AuthenticationPrincipal Jwt jwt) {
        // the JwtDecoder's strict validation
        Jwt decoded = jwtDecoder.decode(jwt.getTokenValue());
        return Map.of(
                "subject", decoded.getSubject(),
                "issuer", decoded.getIssuer().toString(),
                "audience", decoded.getAudience(),
                "expiresAt", decoded.getExpiresAt(),
                "claims", decoded.getClaims());
    }
}
```

> ⚠ The introspection endpoint must be **admin-only** — it echoes the claims (PII).

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | Taking `alg` from the token's header | Pin the expected algorithm | The `none` and confusion attacks |
| 2 | Standard Base64 | `Base64.getUrlEncoder().withoutPadding()` | `+`/`/`/`=` corrupt the token |
| 3 | A secret in the payload | Nothing secret in the payload | Base64 is not encryption |
| 4 | A long-lived access token (a day) | 5–15 minutes | The theft window |
| 5 | The refresh token stored raw | Hashed (SHA-256) in the DB | A DB leak becomes account takeover |
| 6 | `iss`/`aud` not verified | `JwtIssuerValidator` + `JwtAudienceValidator` | Token replay across issuers/APIs |
| 7 | The token in `localStorage` | An httpOnly cookie (with CSRF) or in-memory | XSS theft |
| 8 | The refresh token as a JWT | Opaque (revocable) | Rotation detection needs the state |
| 9 | Fine-grained permissions in the token | Roles/scopes in the token, fine-grained in the DB | Staleness |
| 10 | No `kid` in the header | Include the `kid` | Rotation can't select the key |
| 11 | Hand-rolled verification | `NimbusJwtDecoder` | The subtle bugs |
| 12 | Clock skew ignored | `ClockSkew` (60s) | Distributed clocks drift |
| 13 | No `exp` claim | Always set and validate it | The eternal token |
| 14 | The `Authorization` header stripped by a proxy | Fix the proxy/ingress config | Silent 401s everywhere |
| 15 | Logging full tokens | Log `kid`, `iss`, `aud`, `exp` only | Tokens in logs are credentials in logs |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is a JWT and what are its three parts?**
**A:** A JSON Web Token — a compact, URL-safe, signed credential. The three parts are
`base64url(header).base64url(payload).signature`. The header says how it's signed, the payload
carries the claims, the signature proves integrity.

**Q: Is a JWT encrypted?**
**A:** No. A standard JWT is **signed**, not encrypted. The payload is base64url — anyone can
decode it. If you need confidentiality, use JWE or encrypt the sensitive fields yourself.

**Q: Why use a JWT instead of a session?**
**A:** Statelessness (no server-side store, any instance can verify), self-contained claims (no
extra lookup), and suitability for APIs and microservices. The costs: no instant revocation,
no confidentiality, and size.

### 🟡 Intermediate
**Q: Explain signing and verification.**
**A:** The issuer builds `header.payload`, signs that string with the private key (RS/ES) or the
shared secret (HS), and appends the signature. The verifier fetches the issuer's public key
(via JWKS, cached), recomputes the signature over the same string, and compares. If it matches,
the content is unaltered and the issuer is who it claims to be.

**Q: What are the standard claims?**
**A:** `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`. The security-critical ones are `iss`,
`aud`, `exp`, and `jti` (the blocklist and replay detection).

**Q: What's the difference between RS256 and HS256?**
**A:** RS256 is asymmetric (RSA key pair — private signs, public verifies), so a verifier can
check without being able to forge. HS256 is symmetric (the same secret signs and verifies) —
anyone who can verify can also forge. Use HS256 only when issuer and verifier are the same
party.

**Q: Why is the `kid` header needed?**
**A:** So the verifier knows *which* key to use. During rotation the `kid` identifies the
version, and the verifier looks up the right key in the JWKS instead of trying every key.

### 🔴 Advanced / Senior
**Q: What is algorithm confusion and how do you prevent it?**
**A:** The attacker takes the RSA **public** key (published at the JWKS) and uses it as the
**HMAC secret**, crafting an HS256-signed token. If the verifier accepts both algorithms and
takes the algorithm from the token header, it verifies the HMAC signature using the public key
as the secret → accepted. Prevention: **pin the accepted algorithms** server-side and never
derive the key type from the token header. Spring's `NimbusJwtDecoder` is safe by default; the
danger is a custom `JwtDecoder` that switches on `alg`.

**Q: How would you implement revocation?**
**A:** (1) **Short access-token TTL** (5–15 min) plus **refresh rotation** — the effective
revocation window is the access TTL. (2) A **blocklist** of revoked `jti`s in Redis, checked per
request. (3) A **per-user token version** — a counter in the user record embedded in the token;
a mismatch invalidates all that user's tokens, so "revoke everything" is one increment. (4) A
**server-side session** (opaque access token) for instant revocation at the cost of state. The
production standard: (1) + (3), with (4) for the refresh token.

**Q: How do you rotate signing keys?**
**A:** The `kid` identifies the key; the JWKS publishes the **active and previous** keys during
the grace period (the max access-token TTL). The issuer signs with the new key; verifiers
accept both (their JWKS cache TTL must be shorter than the token TTL, or new tokens fail until
the cache refreshes). After the grace period the old key is removed from the JWKS.

**Q: When would you use JWE instead of JWS?**
**A:** When claims are **sensitive** (PII, internal entitlements) and the client must not read
them, or when you need "opaque to the client" without server state. The trade-offs are key
management complexity, harder debugging, and weaker library support. In most backends, a short
TTL plus "no secrets in the payload" is simpler and sufficient.

### ⚫ Expert / Principal
**Q: Design the token strategy for a platform with SPAs, mobile apps, partners, and internal
services.**
> (1) **Trust boundaries** — the browser (hostile), the mobile app (untrusted, often rooted),
> partners (third party, limited scope), internal (trusted, workload identity). (2) **Token
> profiles** — the SPA: BFF pattern with httpOnly cookies plus CSRF, so the token never sits in
> JS memory where XSS can read it; mobile: access token in memory, refresh in the
> Keychain/Keystore, bound to the device; partners: client-credentials tokens with narrow scopes
> and rate limits; internal: mTLS or scoped tokens. (3) **Key management** — per-environment
> keys, automated rotation, `kid` discipline, JWKS caching. (4) **Revocation** — access TTL as
> the primary bound, version/blocklist for a compromised account, refresh rotation for
> detection. (5) **Governance** — a scope catalogue, TTL policy, audit trail (issuance, refresh,
> revocation), and an incident runbook for key compromise.

---

## 9. Counter-Questions & Traps

### CQ1. "If the JWT is signed, why do I need to check `exp`?"
- **Naive answer:** "Because the signature covers `exp`."  ← the trap
- **The trap:** The interviewer wants the two independent concerns.
- **Expert answer:** The signature guarantees the token is **unaltered**; `exp` decides
**whether it's still usable**. They are orthogonal: a perfectly signed token from last week is
still validly signed — and must be rejected because it's expired. Concretely, verification is a
conjunction: `signature_valid ∧ exp > now ∧ iss == expected ∧ aud contains me ∧ nbf <= now`.
`JwtValidators.createDefaultWithIssuer` composes the timestamp validator (with skew) and the
issuer validator; the signature is a separate layer underneath. Candidates who say "the
signature protects the expiry" are conflating integrity with validity.

### CQ2. "Why store the refresh token hashed if it's random?"
- **Naive answer:** "Because the database could leak."  ← the trap
- **The trap:** The interviewer wants the principle.
- **Expert answer:** The refresh token is a **bearer credential** — whoever holds it can mint
access tokens. If the DB leaks (SQL injection, a backup, a replica, an insider), a raw token
means the attacker has every user's session. Hashing makes leaked rows **useless** without the
token itself. The principle: **never store a credential in recoverable form** — the same reason
passwords are hashed. Because the token is random with no structure to exploit, a **fast hash**
(SHA-256) is appropriate, unlike passwords where slowness is the feature. Lookup is by token
hash: hash the incoming token, look up the row.

### CQ3. "Is the JWT really stateless? What about revocation?"
- **Naive answer:** "Yes, completely stateless."  ← the trap
- **The trap:** The interviewer wants the honest answer.
- **Expert answer:** The **verification** is stateless (no lookup), but the **system's
behaviour** isn't: refresh-token storage, the revocation list, the user version counter, rate
limiting, and audit are all state. The honest framing: **access-token verification is
stateless; identity lifecycle management is stateful elsewhere.** That's why production
architectures move state to the refresh token and keep the hot path stateless. Say it
explicitly: "the JWT makes the *request path* stateless, not the *identity system*."

### CQ4. "Why not just put the permissions in the token and check them?"
- **Naive answer:** "That's the standard way."  ← the trap
- **The trap:** The interviewer wants the staleness problem.
- **Expert answer:** Roles and scopes in the token are a **coarse, cached** view of permissions —
and that's a *good* thing (fast path, no DB lookup). The problem is **staleness**: a role is
revoked, but the token says `ROLE_ADMIN` for the next ten minutes. Mitigations in increasing
cost: (1) the **short TTL** (bounds staleness); (2) a **user version counter** (one cache read);
(3) a **fine-grained check against the DB** for sensitive operations (ownership, resource
state); (4) **event-driven invalidation** (role change → blocklist update). The rule: **coarse
roles in the token, fine-grained and volatile data in the DB**.

### CQ5. "Why is base64 a security risk?"
- **Naive answer:** "Because it's not encryption."  ← partially right
- **The trap:** The interviewer wants the operational consequences.
- **Expert answer:** Base64 is trivially reversible, so **the payload must be treated as
public**. Consequences: (1) **never put PII** there (email, phone, address) — a data-protection
and size issue; (2) **never put secrets** (passwords, API keys) — anyone with the token can read
them; (3) **mutable claims are visible** — a user can see their own permissions, which is fine,
because tampering is prevented while reading is not; (4) **logs leak claims** if tokens are
logged. The design implication: use JWE or field encryption if you need confidentiality; keep
the payload minimal and non-sensitive if you only need integrity.

---

## 10. Rapid-Fire MCQs

**1.** A JWT's three parts are separated by:
A) `,`  B) `.`  C) `:`  D) `|`
**✅ B**

**2.** A standard JWT is:
A) Encrypted  B) Signed  C) Hashed  D) Compressed
**✅ B**

**3.** The `alg: none` attack is prevented by:
A) A long secret  B) Pinning the expected algorithm  C) TLS  D) The `kid`
**✅ B**

**4.** RS256 uses:
A) A shared secret  B) A key pair (public/private)  C) No key  D) The TLS key
**✅ B**

**5.** The `aud` claim is used to:
A) Identify the issuer  B) Restrict the token to the intended recipient  C) Store roles  D) Prevent replay
**✅ B**

**6.** The `jti` claim is mainly used for:
A) Expiry  B) Unique identification (blocklist, replay detection)  C) Audience  D) Algorithm
**✅ B**

**7.** The refresh token should be stored:
A) In localStorage  B) Hashed in the DB  C) As a plain JWT  D) In the URL
**✅ B**

**8.** The recommended access-token TTL is:
A) 24 hours  B) 5–15 minutes  C) 30 days  D) 1 year
**✅ B**

**9.** The `kid` header is used for:
A) Key selection during rotation  B) Encryption  C) User identity  D) CSRF
**✅ A**

**10.** The best algorithm for a third-party-verifiable token:
A) HS256  B) RS256  C) none  D) MD5
**✅ B**

**11.** Base64URL differs from standard Base64 by:
A) Compression  B) `-`/`_` and no padding  C) Encryption  D) A checksum
**✅ B**

**12.** `JwtAudienceValidator` prevents:
A) Expired tokens  B) Token replay against the wrong API  C) Forgery  D) CSRF
**✅ B**

**13.** A JWT stored in `localStorage` is vulnerable to:
A) CSRF  B) XSS token theft  C) SQL injection  D) Network replay
**✅ B**

**14.** The `nbf` claim stands for:
A) Not Before  B) No Bearer  C) Non-Forbidden  D) New Batch
**✅ A**

**15.** Clock-skew tolerance exists because:
A) Tokens are slow  B) Server clocks drift  C) TLS  D) Database lag
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — Decode by hand**
1. Take a token from jwt.io and decode header and payload with `base64 -d` (adding padding).
2. Change the payload (`role` → `admin`), re-encode, and observe the signature failure.

**🧪 Lab 2 — The Spring resource server**
1. Add `spring-boot-starter-oauth2-resource-server`.
2. Configure `issuer-uri` (a local Keycloak or a mock JWKS endpoint).
3. Call a protected endpoint with a valid, an expired, and a tampered token → observe the 401s.

**🧪 Lab 3 — Claim mapping**
1. Map the `scope` and `roles` claims to authorities.
2. Write a test asserting `hasAuthority("SCOPE_orders:read")` and `hasRole("ADMIN")`.

**🧪 Lab 4 — Clock skew**
1. Issue a token that expired 10 seconds ago → 401.
2. Raise `ClockSkew` to 60s → 200. Understand the trade-off.

---

## 12. Code Challenges

### Challenge 1 — A JWT library from scratch (medium-hard)
**Goal:** sign and verify with HS256 and RS256, with base64url, claim validation, and tests.
**Requirements:** encoding, signing, parsing, `exp`/`nbf`/`iss`/`aud` validation, clock skew,
error types, and unit tests (expired, tampered, wrong issuer, malformed).

### Challenge 2 — Key rotation with JWKS (hard)
**Goal:** an issuer that publishes the active and previous key, and a verifier that selects by
`kid`.
**Requirements:** key generation, the JWKS endpoint, `kid` calculation (thumbprint), decoder
caching, a rotation script, and tests (an old token still verifies during the grace period).

### Challenge 3 — Refresh rotation with reuse detection (hard)
**Goal:** rotating refresh tokens with family lineage and reuse detection → whole-family
revocation.
**Requirements:** hashed storage, the family model, detection logic, concurrent-use handling,
and tests.

---

## 13. Scenario-Based Questions

**S1. "A client says their JWT is rejected intermittently — about 1% of requests."**
>
> **Ideal answer:** (1) **Triage order, most likely first:**
> (a) **Clock skew** — the classic. `exp` is compared to the server clock, so a 30–60 second
> drift (NTP hiccup, container clock, VM pause) rejects a fraction of requests, and it
> correlates with *newly issued* tokens near the boundary rather than old ones. Check `iat`/
> `exp` against server `now` in the logs. Fix: `ClockSkew` (60s) plus NTP monitoring.
> (b) **JWKS cache expiry** — after a key rotation, a stale cached JWKS fails new-key tokens
> until it refreshes. Symptom: a burst right after rotation, then recovery. Fix: JWKS
> `cacheTTL` shorter than the token TTL, publish before use, multiple JWKS hosts.
> (c) **Multiple issuers behind a load balancer** — config drift across pods gives mismatched
> `iss`/`aud` for a fraction of requests. Check: log `iss`/`aud` of rejected tokens; two values
> will appear.
> (d) **A proxy stripping `Authorization`** on some paths (ingress config, canary routing) → a
> 401 with no token reaching the app.
> (e) **Header size limits** — proxies cap header size (commonly 8KB); bloated tokens produce
> 400/431 that can surface as intermittent failures on long requests.
> (f) **Concurrent refresh** — two tabs refresh at once, one wins, the other presents a reused
> token → 401. Client-side, but must be considered.
> (2) **Diagnosis method:** correlation analysis — failures by time (bursts → rotation, steady 1%
> → clock), by instance (pod → config drift), by proximity to `exp` (clock), by `kid`
> (rotation cache). Logging `kid` is the single best diagnostic.
> (3) **Senior framing:** "Intermittent 401s are almost never random — they're a boundary
> condition. My first hypothesis is the clock, because `exp` sits exactly on a boundary and 1%
> is the signature of a small skew; the second is the rotation cache. The rule I use: log `iss`,
> `aud`, `kid`, `exp`, and server `now` on every rejection — that one line answers 90% of these
> tickets."

**S2. "We need to add logout. Users expect the session to end immediately."**
>
> **Ideal answer:** (1) **The core problem:** a JWT is stateless — logout isn't a native
> concept. The client can stop sending the token, and the server has no idea. (2) **Options:**
> (a) **Client-side deletion** (clear memory, expire the cookie) — simplest, what most apps do;
> the weakness is a copied token stays valid until `exp`; (b) **Blocklist** (`jti` → Redis with a
> TTL to natural expiry) — real revocation, per-request cost; (c) **Token version counter**
> incremented on logout, invalidating all that user's tokens — coarse, cheap, natural for
> "logout everywhere"; (d) **Short TTL plus refresh revocation** (delete the DB row) — the access
> token dies in minutes and the session cannot be renewed; (e) **The production recommendation:**
> short access token + refresh revocation on logout + client-side deletion. That satisfies the
> user's expectation in practice. For strict requirements (financial, admin), add the version
> counter or blocklist. (3) **Design detail:** "logout everywhere" → version increment;
> "logout this device" → delete that refresh row; make sure rotation cannot re-issue after
> logout. (4) **Client behaviour:** the SPA must stop its retry loop — after logout the
> interceptor must not attempt refresh, or a 401 triggers a refresh against a dead token and
> bounces the user to login. Fix with an "intentional logout" flag. (5) **Senior framing:**
> "With a JWT, immediate logout is impossible by construction — that's the trade-off you
> accepted for statelessness. The production answer moves revocation state to where it can
> live, the refresh-token row, and bounds access-token exposure with a short TTL. If the
> requirement is truly 'dead now', you need state — a version counter, a blocklist, or a
> session. Be explicit with product about the residual window instead of pretending logout is
> instantaneous."

**S3. "Security wants to move from HS256 to RS256. What breaks?"**
>
> **Ideal answer:** (1) **Mechanics:** generate an RSA key pair (2048-bit minimum, 3072 for
> long-lived; private key in KMS/HSM/vault, never in the repo); configure the issuer to sign
> with the private key and set the `kid`; publish the public key in JWKS format at the
> well-known URL; configure verifiers with `NimbusJwtDecoder.withJwkSetUri(...)` or
> `issuer-uri` (algorithms pinned by the issuer metadata). (2) **Breakages:** (a) **existing
> tokens** signed with HS256 become invalid if the allowed algorithms change → mass logout; the
> mitigation is a dual-algorithm window bounded by the max access-token TTL; (b) **shared-secret
> consumers** (mobile, partners, internal services) all need a JWKS client → a client migration
> plan; (c) **key distribution** — verification now needs the public key, so JWKS availability
> and caching matter, and `issuer-uri` adds a startup dependency (the app fails to start if the
> auth server is down; use `jwk-set-uri`); (d) **size** — an RS256 signature is 256 bytes vs
> HMAC's 32, so tokens grow ~300 bytes in base64, which matters for the 4KB cookie budget;
> (e) **KMS latency** — signing through an HSM adds a round trip per issuance; measure it, and
> watch throttling. (3) **Migration plan:** publish both keys with distinct `kid`s → have the
> issuer sign with RS256 while verifiers accept both → observe metrics → remove HS256 from the
> allowed list and the secrets store → update partner/internal/mobile documentation.
> (4) **Senior framing:** "Algorithm migration is really a key-distribution migration — that's
> the underestimated part. Going asymmetric means every verifier must reach the JWKS, which
> means a caching strategy, an availability story, and a rollout that doesn't log everyone out.
> The dual-algorithm window is the standard trick: publish both keys keyed by `kid`, sign with
> the new one, accept both for the max token TTL, and use metrics to tell you when the old key
> is unused — then remove it."

---

## 14. Tricky Interview Questions

**T1. "Can a JWT be revoked before it expires?"**
> Not intrinsically — that's the defining property of the stateless design. Practically: short
> TTL, a blocklist on `jti`, a user version counter, or a server-side session. The honest
> framing: "the *verification* is stateless; the *revocation* is state, and it lives elsewhere."

**T2. "What's the maximum size of a JWT and why does it matter?"**
> Practically the cookie limit (4KB per cookie, plus per-domain limits) and header size (proxy
> caps, commonly 8KB). A token with many claims (groups, scopes, permission arrays) can reach
> several kilobytes → larger request headers → latency and memory. Mitigations: minimal claims,
> references resolved server-side, cookie splitting, or the BFF pattern.

**T3. "Why is `iat` validated with skew tolerance rather than an exact match?"**
> Because clocks drift (NTP latency, container clocks, VM pauses). `iat` is compared to server
> `now` with a tolerance (typically 60s) to avoid rejecting freshly issued tokens. The trade-off:
> a 60-second skew means a token "from the future" is accepted for 60 seconds — acceptable.

**T4. "What happens if the signing key is compromised?"**
> Every token it signed is forgeable until expiry → emergency rotation: a new key with a new
> `kid`, publish it, and remove the compromised key — but removal invalidates outstanding tokens
> (mass logout). The middle ground is keeping the compromised key for a very short window, which
> is exactly the attacker's window. The real mitigations are the short access TTL and refresh
> revocation, so exposure is minutes rather than the token TTL. In a severe compromise, revoke
> refresh tokens and rotate immediately.

**T5. "Why is `nbf` rarely used, and when would you use it?"**
> Because issuance is immediate — `iat` ≈ `nbf`. Use cases: scheduled tokens that become valid
> later (pre-provisioned credentials), skew handling (setting `nbf` to issuance time for a small
> grace), and pre-signed-style links with a not-before/not-after window.

**T6. "Is JWT signature comparison constant-time? Could there be a timing attack?"**
> Modern libraries use constant-time comparison (`MessageDigest.isEqual` in the JDK, byte
> comparison in Nimbus). The more realistic risk isn't a timing side channel but the algorithmic
> attacks — `alg: none` and confusion. If you hand-roll the comparison, use
> `MessageDigest.isEqual(bytes1, bytes2)`, never `String.equals`.

**T7. "Why does the `typ` header matter?"**
> It identifies the token type and prevents **token substitution** — a refresh token used as an
> access token, or an ID token used at an API. OAuth2 defines `at+jwt` for access tokens (RFC
> 9068), `dpop+jwt` for DPoP proofs. A verifier that checks `typ` rejects substitution. In
> practice `typ` is rarely enforced, but distinguishing refresh from access tokens is a real
> defence.

**T8. "Does `iss` need to match exactly?"**
> Yes — issuer comparison is exact string equality (normalise trailing slashes carefully). `aud`
> is a **contains** check, since a token may have multiple audiences. Spring's
> `JwtIssuerValidator` does the exact match; `JwtAudienceValidator` does the contains.

**T9. "Why doesn't the JWT protect against replay?"**
> Because it's a **bearer token** — possession is sufficient, and there's no nonce or
> consumption tracking by default. Mitigations: short TTL (bounds the window), `jti` with
> one-time use (refresh rotation detects replay), `aud` restriction (replay against another API
> fails), TLS (network interception), and for high-value operations an idempotency key or
> transaction nonce at the domain level.

**T10. "What is the difference between `exp` and `max-age`?"**
> `exp` is the token's own absolute claim. `max-age` is an HTTP cache directive describing a
> relative age for caches. A JWT in a `Cache-Control: max-age=3600` response could be replayed
> from a cache for an hour — so API responses should be `no-store`, and `exp` is the
> authoritative check.

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after "statelessness") → *"So there's no server-side session at all?"*
→ Not in the **request path**. Refresh-token state lives in the DB (hashed rows, rotation
family, revocation), and session *control* ("log out everywhere") is state. Access-token
verification is the only stateless part — and that's where the performance comes from.

**F2** (after "claims") → *"What if a user's role changes?"*
→ The token carries the role as of issuance, so it's stale until `exp`. Mitigations in
increasing cost: the short TTL; a user version counter (increment → mismatch → reject);
event-driven invalidation (role change → blocklist/version bump). Fine-grained permissions
should never rely on the token alone.

**F3** (after "refresh") → *"Why not issue one long-lived JWT and skip refresh?"*
→ (1) **Revocation** — a year-long exposure if stolen; (2) **staleness** of permissions;
(3) **session concurrency** — device listing and "log out everywhere" need state; (4) **reuse
detection** — the strongest argument: a stolen refresh token used once is detected and the whole
family is killed before the attacker gets a usable access token. The refresh token is where the
statefulness and the security value live.

**F4** (after "JWKS") → *"What if the auth server is down when a token is verified?"*
→ The verifier has a **cached** JWKS and keeps working until the cache expires (Nimbus defaults
to about 5 minutes). `RemoteJWKSet` fails **open** on refresh errors — it keeps using cached
keys and logs a warning, preserving availability at the cost of key freshness. That trade-off is
deliberate, and it's why rotation must publish the new key before using it.

---

## 16. Real 3–5 Year Experience Scenarios

**R1. "We moved to JWTs and now get tickets: 'I get logged out randomly'."**
>
> **Ideal answer:** (1) **Triage** — a "random logout" is almost never random. Four causes:
> (a) **refresh race** — two tabs refresh simultaneously, the first rotates, the second presents
> a rotated token, reuse detection may revoke the family → the whole session dies. This is the
> most common cause, and it's a client bug or a non-atomic server rotation. (b) **clock skew**
> (`exp` vs server time). (c) **short TTL plus a transient refresh failure** — the user idles,
> the access token expires, the refresh fails for a transient reason (DB, Redis) and the client
> logs them out instead of retrying. (d) **multi-tab cookie race** — one tab writes the new
> token while another writes the old one back, so last-write-wins corrupts the session.
> (2) **Diagnosis:** correlation by time (bursts), refresh endpoint latency and failure metrics,
> token `exp` proximity at failure (clock), the reuse-detection log (the smoking gun for the
> race), and the browser network log for request order and cookie values.
> (3) **Fixes:** (a) **atomic rotation** — `UPDATE refresh_tokens SET ... WHERE id=? AND
> version=?`; zero affected rows means reuse; (b) **single-flight refresh** on the client (one
> in-flight promise shared by concurrent requests); (c) **cross-tab coordination** via
> `BroadcastChannel`, or keep the refresh token in an httpOnly cookie so tabs share it and the
> race moves to server-side atomicity; (d) **graceful failure handling** — retry with backoff on
> transient errors, log out only on definitive rejections; (e) **clock skew** tolerance plus NTP
> monitoring. (4) **Systemic:** refresh endpoint metrics and alerts; structured logs with reason
> codes in the 401 body (`token_expired`, `refresh_reused`, `clock_skew`, `server_error`) so
> clients can act and support can diagnose; a staging soak test covering multi-tab, long idle,
> and network flaps; graceful degradation where a 15-minute access token rides out a short auth
> outage.
> (5) **Senior framing:** "A 'random logout' is a symptom of a non-atomic state transition. The
> fix is atomic rotation plus a response contract that distinguishes transient from definitive
> failures. The biggest UX win is reason codes in the 401 body — the client can retry a 500 and
> re-authenticate only on a real rejection. And I always check the reuse-detection log first: if
> it's firing, it's the race, and the fix is the atomic update, not a longer TTL."

**R2. "We have 40 microservices; each validates the JWT. The auth team's key rotation broke
production for 20 minutes."**
>
> **Ideal answer:** (1) **Diagnosis:** the rotation was naive — the new key was published and
> used simultaneously, and verifiers' cached JWKS (5-minute TTL or longer if configured) served
> the stale key set, so new-key tokens failed until the cache expired. Twenty minutes ≈ cache
> TTL plus propagation delay. If any service still used a symmetric secret while others used
> JWKS, the inconsistency multiplied the failure modes. (2) **Immediate:** republish both keys,
> invalidate caches (restart or shorten `cacheTTL`), accept both algorithms during the window
> via a config push, and communicate on the status page. (3) **Systemic — a rotation that cannot
> break:** (a) **three-phase rotation**: publish the new key to the JWKS without using it, wait
> max cache TTL plus margin, then start signing with it, then after max token TTL plus skew
> remove the old key. The invariant: **publish before use, remove only after old tokens expire.**
> (b) Verifier config where `cacheTTL` is shorter than the token TTL, and fail-open refresh
> behaviour preserves availability. (c) Automated rotation with canary verification and a
> monitoring gate (old-key usage at zero before removal). (d) A chaos drill — a "rotation game
> day" in staging first. (e) Centralised config so algorithm list, `cacheTTL`, `aud`, and `iss`
> are uniform across all 40 services; per-service divergence is what makes them fail differently.
> (4) **Checklist:** publish → wait for propagation → switch signing → observe → remove, with a
> verification at every step. (5) **Senior framing:** "Key rotation is a distributed protocol,
> not a config change — every participant caches, so publication must always precede usage and
> removal must always follow expiry. The outage is the cache TTL made visible. The fix is a
> phased rotation with a propagation gate, not a shorter cache (which means more JWKS traffic
> and a nastier failure mode). And 40 services with 40 configs is 40 failure modes — algorithm
> list, `cacheTTL`, `aud`, and `iss` must be centrally managed."

**R3. "Security wants to add claims (department, clearance level) and the token is already 2KB.
The API latency is fine, but mobile users complain."**
>
> **Ideal answer:** (1) **Diagnosis:** growth has two costs — transport (header size, mobile
> bandwidth, latency) and parsing (every gateway and service parses it; RSA verification is
> fixed, JSON parsing is linear). The hard limits are the 4KB cookie budget (access + refresh +
> session + CSRF must fit) and proxy header caps (commonly 8KB).
> (2) **The right fix — the reference pattern:** don't put data in the token; put a **reference**
> and resolve server-side. A permissions-set id or policy version, or a group id mapped to roles
> in cache, keeps the token small and makes permissions **current**, which also removes the
> staleness problem. That's a double win worth naming.
> (3) **Alternatives:** (a) **compressed JWT** (DEFLATE via the `zip` header, RFC 7520) — often
> 30–50% savings for repetitive claims, at CPU cost and interoperability risk; (b) **shorter
> identifiers** (standard claim names, short ids) — often the cheapest win; (c) **trimming**
> unused claims (email is often the biggest and least used by services); (d) separate tokens —
> no, too much complexity.
> (4) **Measure before optimising:** token size distribution (p50/p95/p99), parsing cost,
> end-to-end latency on mobile, and how close you are to proxy/cookie limits.
> (5) **Recommendation hierarchy:** measure; use the reference pattern for volatile claims
> (department and clearance change, so a token copy is both bloated *and* wrong); shorten
> identifiers and trim; consider compression last, with measurement; treat 4KB as the cookie
> ceiling. (6) **Senior framing:** "Token size is usually a modelling error — caching volatile
> data in the token. Department and clearance change, so the copy is bloated and wrong at the
> same time. The reference pattern fixes both: smaller token, current permissions, cache-hit
> latency. And measure first: a 2KB token is fine until the ingress 8KB cap or the cookie 4KB
> budget starts biting."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| 401 "invalid token signature" | Clock skew, wrong key, proxy alteration | `ClockSkew`; log `kid`; check the JWKS cache |
| 401 burst after key rotation | Verifiers' stale JWKS cache | Two-phase rotation; `cacheTTL` < token TTL |
| 401 "invalid audience" | `aud` not configured on the verifier | `JwtAudienceValidator` |
| Random logouts | Refresh race (multi-tab) | Atomic rotation; client single-flight |
| "Token expired" right after issuance | Clock skew | `ClockSkew`; NTP |
| 401 in local dev, fine in prod | Different secrets per environment | Environment-specific config |
| Tokens over 4KB | Too many claims | Reference pattern; trimming |
| CORS pre-flight fails with a token | `Authorization` missing from allowed headers | CORS config |
| `InvalidBearerTokenException` with a valid token | Proxy stripped the header | Ingress config |
| Users logged out after a deploy | New pods rejected old tokens (key or config drift) | Publish-before-use rotation; uniform config |

---

## 18. Debugging Scenarios

**D1. "Decode the token without verifying it (safe triage):"**
```bash
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq .
```

**D2. "Log token metadata on rejection:"**
```java
@Bean
JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            JwtValidators.createDefaultWithIssuer(issuerUri),
            new JwtAudienceValidator(audience) {
                @Override
                public OAuth2TokenValidatorResult validate(Jwt token) {
                    OAuth2TokenValidatorResult result = super.validate(token);
                    if (result.hasErrors()) {
                        log.warn("aud mismatch: tokenAud={} expected={} kid={} exp={} now={}",
                                token.getAudience(), audience, token.getHeader().get("kid"),
                                token.getExpiresAt(), Instant.now());
                    }
                    return result;
                }
            }));
    return decoder;
}
```

**D3. "Which key was used?"**
```java
log.info("kid={} alg={}", token.getHeader().get("kid"), token.getHeader().get("alg"));
```

**D4. "Compare token `exp` with server clock:"**
```java
log.info("exp={} serverNow={} delta={}s", token.getExpiresAt(), Instant.now(),
        Duration.between(Instant.now(), token.getExpiresAt()).getSeconds());
```

**D5. "Inspect the JWKS the verifier is using:"**
```bash
curl -s https://auth.example.com/realms/myapp/protocol/openid-connect/certs | jq '.keys[] | {kid, alg, use}'
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the token architecture for a platform with SPAs, mobile apps, partners, and
internal services.**
> (Covered in the Expert Q&A: trust boundaries, per-surface token profiles, key management,
> revocation strategy, governance.)

**Q2. 🏗️ Design authentication for an e-commerce platform (guest checkout, customer account,
admin console, partner integration).**
> **Answer structure:** (1) **Guest path** — no token, or a short-lived server-side cart session
> in a cookie; completing checkout issues the customer token. (2) **Customer path** — an SPA
> behind a BFF (httpOnly cookies, CSRF, token stored server-side so the SPA never sees it), or an
> in-memory access token plus a rotating refresh token in an httpOnly cookie. (3) **Admin path** —
> a separate chain, mandatory MFA, step-up for sensitive operations, short sessions, audit trail,
> network restriction. (4) **Partner path** — client credentials with narrow scopes, quotas,
> rotated secrets, audit. (5) **Shared concerns** — key management, revocation strategy, audit,
> monitoring, incident runbook.

**Q3. 🏗️ Design key management and rotation for a 40-service platform.**
> (Covered in R2: two-phase rotation, propagation gate, centralised config, drills, metrics.)

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  STRUCTURE: base64url(header) . base64url(payload) . base64url(signature)   │
│  the SIGNED MESSAGE = header + "." + payload (exactly)                      │
├──────────────────────────────────────────────────────────────────────────────┤
│  the CLAIMS: iss sub aud exp nbf iat jti (+ scope, roles, tenantId)         │
│  VERIFY: signature ∧ exp ∧ nbf ∧ iss ∧ aud — never trust the token's alg    │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ALGORITHMS: HS256 (symmetric, same party) | RS256 (asymmetric) |       │
│  ES256 (asymmetric, shorter) | none (NEVER)                                 │
│  the ATTACKS: alg:none | confusion (RS→HS) | missing algorithm pinning      │
├──────────────────────────────────────────────────────────────────────────────┤
│  the PRODUCTION RULES                                                       │
│  the access token: 5-15 min, JWT, stateless                                  │
│  the refresh token: opaque, HASHED, rotating, revocable                      │
│  the SPA: httpOnly cookie + CSRF (the BFF) — not the token in JS             │
│  the mobile: access in memory, refresh in the Keychain                        │
│  the key rotation: publish → wait → use → observe → remove                    │
│  the revocation: TTL bound, blocklist (jti), version counter                  │
├──────────────────────────────────────────────────────────────────────────────┤
│  ⚠ The payload is PUBLIC — never a secret, rarely PII                        │
│  ⚠ `iss` + `aud` MUST be validated (replay prevention)                       │
│  ⚠ The refresh token is HASHED in the DB (a leak = a takeover)               │
│  ⚠ The `kid` is mandatory (rotation key selection)                           │
│  ⚠ The `exp` is mandatory (no eternal tokens)                                │
│  ⚠ Hand-rolled verification is a bug source — use Nimbus                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning

- → [02 · Stateless Authentication & the Full Flow](./02-Stateless-Authentication-and-Flow.md)
- → [03 · Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md)
- → [06 · JWT Authentication](../06-JWT-Authentication/README.md) — the index.
- → [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md) — the standard's flows.

---
**Next Chapter:** → [02 · Stateless Authentication & the Full Flow](./02-Stateless-Authentication-and-Flow.md)
**Previous Topic:** ← [05 · Spring Security](../05-Spring-Security/README.md)

