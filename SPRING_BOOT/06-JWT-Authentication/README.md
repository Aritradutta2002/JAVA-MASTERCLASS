# 06 — JWT Authentication (Deep Dive)

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (the single most-asked auth topic in Java backend interviews)
> **Prerequisite:** [05 · Spring Security](../05-Spring-Security/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Spring Security teaches you **how to plug in a filter chain**. JWT teaches you **what the
credential actually is** — and where it breaks. Most "3-year Java developer" interviews expect
you to have *shipped* JWT-based auth, including the parts nobody puts in a tutorial:

- Why the refresh token is **opaque**, not a JWT
- How **rotation with reuse detection** actually works (and why it logs users out when you get
  the race wrong)
- Why a JWT **cannot be revoked**, and what you do about that
- The **algorithm confusion** attack — and why it still happens in 2026
- How you **rotate the signing key** without logging out every user

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [JWT Architecture & Anatomy](./01-JWT-Architecture-and-Anatomy.md) | JOSE structure, claims, `alg: none` | ⭐⭐ |
| 02 | [Stateless Authentication & Flow](./02-Stateless-Authentication-and-Flow.md) | The three architectures, login/refresh flows, the error contract | ⭐⭐⭐ |
| 03 | [Access & Refresh Tokens, Rotation, Revocation](./03-Access-Refresh-Tokens-and-Rotation.md) | Token lifecycle, rotation, reuse detection, five revocation strategies | ⭐⭐⭐⭐ |
| 04 | [Security Risks & Production Design](./04-Security-Risks-and-Production-Design.md) | Attack taxonomy, key management, multi-tenant isolation | ⭐⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
  ┌─────────────────┐   the HOT path (every request)   ┌─────────────────┐
  │  ACCESS token   │ ────────────────────────────────▶ │  RESOURCE       │
  │  JWT, 5-15 min  │   verify locally, no store        │  SERVER         │
  └─────────────────┘                                   └─────────────────┘
         ▲ expired
         │
  ┌─────────────────┐   the COLD path (every ~15 min)   ┌─────────────────┐
  │  REFRESH token  │ ────────────────────────────────▶ │  AUTH SERVER    │
  │  opaque, 7-30 d │   hashed, rotating, revocable     │  (the state)    │
  └─────────────────┘                                   └─────────────────┘
```

> **The state lives in the cold path, not the hot path.** Every design decision follows from
> this one picture.

---

## 📊 The Design Decision Matrix

| Decision | Default | Alternative | Trade-off |
|----------|---------|-------------|-----------|
| **Access token** | JWT, 15 min | Opaque + Redis | Redis dependency vs immediate revocation |
| **Refresh token** | Opaque, hashed, 30 d | JWT | Revocability vs simplicity |
| **Rotation** | On, with a 60-second grace window | Off | Theft detection vs network races |
| **Revocation** | TTL + a version counter | Introspection / blocklist | Immediacy vs hot-path cost |
| **Algorithm** | RS256 | HS256 | Key distribution vs minting risk |
| **Storage (web)** | Access in memory, refresh in an httpOnly cookie | Both in `localStorage` | XSS surface vs CSRF complexity |
| **Scoping** | One `aud` per API | One `aud` for all | Isolation vs client friction |

---

## ⚠️ The Traps (the ones that cost people interviews)

| Trap | The wrong answer | The right answer |
|------|------------------|------------------|
| "The JWT is stateless" | "Yes, there's no session" | "The **access** token is; the **refresh** token is stateful — that's where revocation lives" |
| "How do you revoke it?" | "You can't" | "You can't revoke an access token directly — revoke the **refresh**, bump the **version**, or **blocklist** the `jti`" |
| "The algorithm" | "HS256" | "RS256/ES256 — symmetric means every verifier can **mint** tokens" |
| "Where do you store the refresh token?" | "In the DB" | "In the DB **hashed**; on the client in an **httpOnly cookie**" |
| "Refresh rotation" | "You issue a new one" | "And **revoke the old**; if an already-used one comes back, revoke the **whole family**" |
| "The claims" | "User id, name, email, roles" | "`sub`, `aud`, `iss`, `jti`, `scope`, `ver` — **no PII**, the token is readable" |
| "Multi-tenancy" | "The token's `tenantId`" | "The claim **plus** query scoping **plus** DB RLS — one layer is not enough" |
| "Key rotation" | "Generate a new key" | "**Publish → sign → wait → remove.** Remove last, or you log everyone out" |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where it's covered |
|---|----------|--------------------|
| 1 | What are the parts of a JWT? | [Ch 01](./01-JWT-Architecture-and-Anatomy.md) |
| 2 | Is a JWT encrypted? | [Ch 01](./01-JWT-Architecture-and-Anatomy.md) |
| 3 | How does the server verify it? | [Ch 01](./01-JWT-Architecture-and-Anatomy.md) |
| 4 | What's the difference between access and refresh tokens? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 5 | Walk me through your login flow. | [Ch 02](./02-Stateless-Authentication-and-Flow.md) |
| 6 | Why is the access token short-lived? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 7 | How do you refresh? | [Ch 02](./02-Stateless-Authentication-and-Flow.md) |
| 8 | What happens if the refresh token is stolen? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 9 | What is rotation with reuse detection? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 10 | How do you revoke a JWT? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 11 | What is `jti`? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 12 | `aud` vs `iss`? | [Ch 01](./01-JWT-Architecture-and-Anatomy.md) |
| 13 | What is the algorithm confusion attack? | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| 14 | RS256 vs HS256? | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| 15 | How do you rotate the signing key? | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| 16 | Where do you store tokens on the client? | [Ch 02](./02-Stateless-Authentication-and-Flow.md) |
| 17 | How do you handle multi-tenancy? | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| 18 | How do you prevent token leakage? | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| 19 | How do you scale it to 10M users? | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| 20 | Design the auth for a multi-tenant SaaS. | [Ch 04](./04-Security-Risks-and-Production-Design.md) |

---

## 🛠️ The Code You'll Actually Write

| Class | What it does | Where |
|-------|--------------|-------|
| `JwtService` | Issuance (claims, signing, hashing) | [Ch 02](./02-Stateless-Authentication-and-Flow.md), [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| `AuthService` | Login, refresh (rotation + detection), logout | [Ch 02](./02-Stateless-Authentication-and-Flow.md) |
| `RefreshTokenRepository` | Atomic rotation (the `version` check) | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| `JwtDecoderConfig` | Pinned algorithm + five validators | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| `JwtAuthenticationConverter` | Claims → authorities | [Ch 02](./02-Stateless-Authentication-and-Flow.md) |
| `TokenVersionValidator` | The `ver` check (revocation on the hot path) | [Ch 03](./03-Access-Refresh-Tokens-and-Rotation.md) |
| `KeyRotationService` | Publish → sign → wait → remove | [Ch 04](./04-Security-Risks-and-Production-Design.md) |
| `SecurityAuditLogger` | Audit trail + alerts | [Ch 04](./04-Security-Risks-and-Production-Design.md) |

---

## 🔗 The Spring Boot 3 Setup (the minimum)

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: https://auth.example.com
          # the audience validator is NOT automatic — add it yourself
```

```java
@Bean
SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(a -> a
            .requestMatchers("/api/auth/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(o -> o.jwt(Customizer.withDefaults()));
    return http.build();
}
```

---

## 📐 The Reference Architecture

```text
   the CLIENT (the SPA)                the AUTH SERVER              the API
   ┌──────────────────┐                ┌──────────────────┐       ┌──────────┐
   │ access (memory)  │─── Bearer ────▶│ verify (JWKS)    │       │          │
   │ refresh (cookie) │─── POST ──────▶│ the DB (hashed)  │       │ verify   │
   └──────────────────┘                │ the KMS (sign)   │       │ (JWKS)   │
                                       └──────────────────┘       └──────────┘
                                               │
                                        the JWKS (public)
```

---

## ✅ The Self-Check

- [ ] I can explain the **three architectures** (fully stateless, hybrid, opaque) and when to use each.
- [ ] I can draw the **login → request → refresh → logout** flow from memory.
- [ ] I can explain **rotation with reuse detection** and why the whole **family** must die.
- [ ] I can name the **five revocation strategies** and their costs.
- [ ] I can explain the **algorithm confusion** attack and its one-line fix.
- [ ] I can explain the **key rotation ordering** and what happens if you get it wrong.
- [ ] I know the **five mandatory validation checks** (signature, exp/nbf, iss, aud, authorization).
- [ ] I know why **claims must not contain PII** and what the **size budget** is.
- [ ] I can describe **multi-tenant defence in depth** (three or more layers).
- [ ] I know the **client-side storage** trade-offs and the **single-flight** refresh.

---

## 🧪 The Coding Drill

- [ ] Build **login / refresh / logout** with hashed storage and atomic rotation.
- [ ] Write the **negative test suite**: `alg: none`, HS256 signed with the public key, expired, wrong `aud`, wrong `iss`, missing `exp`, unknown `kid`.
- [ ] Implement the **grace window** and prove a duplicate request returns the same successor.
- [ ] Prove **reuse detection** revokes the entire family.
- [ ] Implement **key rotation** (publish → sign → wait → remove) and prove nobody is logged out.
- [ ] Add the **`ver` claim** and a cached check; prove revocation takes effect within the TTL.
- [ ] Write the **single-flight** interceptor for the SPA and prove concurrent 401s cause one refresh.

---

**Related folders:** ← [05 · Spring Security](../05-Spring-Security/README.md) · → [07 · OAuth2 & OIDC](../07-OAuth2-OIDC/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
