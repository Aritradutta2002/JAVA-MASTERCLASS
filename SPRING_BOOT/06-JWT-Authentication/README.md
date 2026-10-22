# 06 — JWT Authentication · Interview Notes Index

> JWT is **not** a session, and it is **not** encryption. The single idea that makes the
> whole topic click: **a JWT is a set of claims plus an integrity guarantee, and nothing
> else.** Anyone can *read* the claims; only the holder of the signing key can *prove*
> they are genuine. Every design decision in this folder — the 5-minute access token,
> the hashed refresh token, the denylist, the `kid` rotation — is a consequence of that
> one sentence.

| Priority | Chapters | Level | Read Time | Why It Matters |
|----------|----------|-------|-----------|----------------|
| ⭐⭐⭐⭐⭐ | [01 · JWT Architecture, Statelessness & Signature Algorithms](01-JWT-Architecture-Statelessness-and-Signature-Algorithms.md) | 🟢→⚫ | 55 min | JOSE/JWS/JWE, the 3-part structure, base64url, the 7 registered claims, HS/RS/PS/ES/EdDSA, algorithm confusion, the `JwtDecoder` → `Jwt` → `JwtAuthenticationToken` chain, JWKS rotation |
| ⭐⭐⭐⭐⭐ | [02 · Access & Refresh Tokens and Token Rotation](02-Access-Refresh-Tokens-and-Token-Rotation.md) | 🟡→🔴 | 50 min | The two-token model, the refresh flow, rotation with **reuse detection**, sliding vs absolute expiry, hashed storage, the `HttpOnly` cookie decision, device/session management |
| ⭐⭐⭐⭐⭐ | [03 · Token Revocation, Storage & Production Design](03-Token-Revocation-Storage-and-Production-Design.md) | 🔴→⚫ | 50 min | Why you **cannot un-sign** a JWT, the 6 revocation strategies and their per-request cost, the token-version claim, denylist TTL maths, key-rotation overlap windows, fail-open vs fail-closed |
| ⭐⭐⭐⭐⭐ | [04 · Security Risks, Complete Flows & Real Examples](04-Security-Risks-Complete-Flows-and-Real-Examples.md) | 🔴→⚫ | 55 min | The 11 attacks and their defences, login/refresh/logout/service-to-service flows, claim→authority mapping, banking / multi-tenant SaaS / service-mesh designs, a 25-item hardening checklist |

> **Total:** ~4 chapters · 210 min of reading · 70+ interview questions · 38 counter-questions · 50 MCQs · 16 production debugging scenarios

---

## 🎯 Most Asked in Interviews

| Question | The Trap |
|----------|----------|
| Is a JWT encrypted? | Saying "yes" — the payload is **base64url-encoded, not encrypted**. Anyone holding the token can decode it with `echo $TOKEN | cut -d. -f2 \| base64 -d` |
| Why is the access token only 5–15 minutes? | "Because it's short" — the real reason is that **the TTL *is* the revocation mechanism**, and every extra minute is a minute of unrevocable authority |
| HS256 vs RS256 — when do you use which? | "RS256 is more secure" — it is not. HS256 is fine; the difference is **key distribution**: with HS256 *every verifier can also mint tokens* |
| What is the algorithm-confusion attack? | Describing it as "the attacker changes `alg`" without the crucial second half: the server must then use the **public key as the HMAC secret** for the forgery to work |
| How do you revoke a JWT? | "Add it to a blacklist" — without saying **the access token still works until it expires**; the blacklist only stops *future* presentations |
| How do you implement "log out everywhere"? | Only mentioning the denylist — the scalable answer is the **token-version claim** compared against one Redis key, which is O(1) and kills every outstanding token at once |
| How is the refresh token stored? | "In the DB" — it must be stored **hashed**; and for a browser SPA it must live in an `HttpOnly; Secure; SameSite=Strict` cookie, not `localStorage` |
| What is refresh-token rotation? | Only "we issue a new refresh token" — the point is **reuse detection**: presenting a consumed token means it was stolen, so the whole token *family* is revoked |
| Why validate `aud`? | "For security" — the concrete attack is a **confused deputy**: a token minted for `billing-api` being accepted by `orders-api` because `aud` was never checked |
| Why not put the role in the token? | Missing the **claim→authority mapping** point: a blanket "map every claim to an authority" is how a mis-typed claim becomes a privilege escalation |
| What happens if the JWKS endpoint is down? | "The API breaks" — the correct behaviour is that the **JWKS cache keeps serving the last known keys** for a bounded overlap window; only a *cache miss for an unknown `kid`* is fatal |
| How do you rotate signing keys? | "Generate a new key and swap it" — without the **overlap window**: publish the new public key first, keep the old one valid for the maximum token lifetime, *then* remove it, or every live token dies at once |

---

## 🧠 The JWT World, in One Picture

```text
                        ┌───────────────────────────────────────────────────┐
                        │                   THE ISSUER                    │
                        │   (your auth service — it holds the PRIVATE key) │
                        │                                                   │
                        │   sign(claims) ──▶ header.payload.signature       │
                        └───────────────────────────┬───────────────────────┘
                                                    │  ① access token (5–15 min)
                                                    │     → travels as: Authorization: Bearer <jwt>
                                                    │     → verified LOCALLY, no server state
                                                    ▼
   ┌──────────────────────────────────────────────────────────────────────────┐
   │                        THE RESOURCE SERVER (the API)                     │
   │                                                                          │
   │   BearerTokenAuthenticationFilter                                        │
   │        └─▶ DefaultBearerTokenResolver   "Bearer eyJhbGci…"               │
   │        └─▶ JwtDecoder ─▶ NimbusJwtDecoder                                │
   │               ├─ verify SIGNATURE  (public key from JWKS, alg PINNED)     │
   │               ├─ validate CLAIMS    (exp / nbf / iss / aud / jti)          │
   │               └─ validate REVOCATION (denylist or token version)          │
   │        └─▶ JwtAuthenticationToken ─▶ JwtAuthenticationConverter          │
   │        └─▶ SecurityContextHolder ─▶ AuthorizationFilter                   │
   └──────────────────────────────────────────────────────────────────────────┘
                                                    ▲
   ┌────────────────────────────────────────────────┴───────────────────────┐
   │                      THE REFRESH TOKEN (7–30 days)                      │
   │   stored HASHED server-side · rotated on every use · family-tracked      │
   │   sent only to /refresh — NEVER used as an API credential                │
   └──────────────────────────────────────────────────────────────────────────┘

   ★ THE ONE-LINE VERSION FOR THE INTERVIEW
   ──────────────────────────────────────────
   "A JWT lets the API verify a token with nothing but a public key — no session
    store, no database hit on the hot path — and I pay for that by making the
    token short-lived and by keeping the long-lived power in a separate, revocable,
    server-side refresh token."
```

> **Caption:** The split that matters is **access token = stateless bearer of
> authority** and **refresh token = stateful, revocable, server-side secret**.
> Everything in this folder exists to make that split work without turning the API
> into a session server, and to keep the blast radius of a stolen token bounded.

---

## ⚠️ Weakest Areas to Brush Up

- [ ] **The exact 3-part structure and what each part is.** `base64url(header).base64url(payload).base64url(signature)` — and the fact that the *header* is part of the signed input, so it cannot be tampered with.
- [ ] **base64 vs base64url.** The `+`, `/` and `=` characters break URL and header transport. If a token works in `curl` but not in a browser, this is why.
- [ ] **The 7 registered claims with their *validation* rules** — `iss`, `sub`, `aud` (string **or** array), `exp`, `nbf`, `iat`, `jti`. Knowing the names is worthless; knowing *how each is validated* is the answer.
- [ ] **Algorithm confusion.** The attack only works if the server *reads `alg` from the token*. Pin the algorithm server-side and the entire class of attack dies.
- [ ] **HS256 vs RS256 vs ES256 vs EdDSA** — the key-distribution trade-off, not the "security level". Every verifier of an HS256 token can also **mint** one.
- [ ] **What "stateless" actually means.** The *access token* is stateless; the *system* is not — refresh-token storage, denylists and `jti` tracking are all server state.
- [ ] **The `JwtDecoder` → `Jwt` → `JwtAuthenticationToken` chain** and exactly where `SecurityContextHolder` is populated.
- [ ] **The refresh flow end to end**, including the 401 → refresh → **retry once** client pattern, and why the retry must happen exactly once.
- [ ] **Refresh-token rotation with reuse detection.** The *family* concept and why a stolen token used twice kills every token in the family.
- [ ] **Why the refresh token is hashed, and why SHA-256 is fine there but terrible for a password.** High-entropy random token ≠ low-entropy user-chosen secret.
- [ ] **Cookie vs body vs `localStorage`** for the refresh token, and the XSS/CSRF exposure of each.
- [ ] **The 6 revocation strategies and their per-request cost.** "Add a blacklist" without the cost model is a half-answer.
- [ ] **The token-version (`ver`) claim** — the O(1) "log out everywhere" mechanism that most candidates never mention.
- [ ] **Key rotation with an overlap window**, and the 401 storm that happens when you get it wrong.
- [ ] **The `none`-algorithm attack and the `alg: none` rejection** — what the error actually says.
- [ ] **Claim → authority mapping.** `scope` → `SCOPE_`, a custom `roles` claim → `ROLE_`, and why mapping *every* claim is dangerous.

---

## 🧪 Coding Drill

- [ ] Decode a token **by hand**: split on `.`, base64url-decode parts 1 and 2, and read the `exp` as an epoch second — then convert it to a local time and check it against the clock.
- [ ] Build a `JwtDecoder` with `NimbusJwtDecoder.withSecretKey(...)`, pin `MacAlgorithm.HS256`, and add a `JwtValidators.createDefaultWithIssuer(...)` plus a `JwtClaimValidator` for `aud`.
- [ ] Write a test that **proves** a token signed with `HS256` using the *public key* as the secret is rejected by a pinned-`RS256` decoder — the algorithm-confusion regression test.
- [ ] Implement `NimbusJwtEncoder.withSecretKey(...)` and issue a token with `jti`, then prove the `jti` is stable across a refresh of the same session.
- [ ] Implement `RefreshTokenService` with issue → rotate → **reuse detection**, and write the test that presents a consumed token and asserts the whole family is revoked.
- [ ] Add a **token-version** check (`ver` claim vs a Redis counter) and prove that incrementing the counter invalidates an access token that is still within its TTL.
- [ ] Implement a **denylist** with `SETEX` whose TTL equals the token's remaining lifetime — then prove with `redis-cli TTL` that no entry outlives its token.
- [ ] Publish a JWKS endpoint, rotate the key, and prove that **a token signed with the old key still validates** during the overlap window and fails after it.
- [ ] Add a `JwtAuthenticationConverter` that maps **only** `roles` and `scope`, and assert that an unexpected claim (e.g. `admin: true`) produces **zero** authorities.
- [ ] Turn on Spring Security's debug logging (`logging.level.org.springframework.security=DEBUG`) and capture the exact 401 body for an expired token, an `aud` mismatch and an unknown `kid`.

---

**Related folders:** ← [05 · Spring Security](../05-Spring-Security/README.md) · → [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/README.md) · ↑ [Master Index](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
