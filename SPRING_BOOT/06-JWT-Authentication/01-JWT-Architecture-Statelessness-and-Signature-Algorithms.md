# JWT Architecture, Statelessness & Signature Algorithms — The Complete Interview Guide

> **Level:** Beginner → Expert
> **Interview Frequency:** ★★★★★ (the single most-asked auth question after "how does Spring Security work": "what is a JWT, is it encrypted, and how do you validate it?")
> **Prerequisites:** [05 · Spring Security](../05-Spring-Security/README.md) · [README](./README.md)
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

- **A JWT is a set of claims plus an integrity guarantee — it is *not* encrypted.** The payload is **base64url-encoded**, so anyone who sees the token can read every claim in it. This is the single most-missed fact in JWT interviews.
- **The 3-part structure is `base64url(header).base64url(payload).base64url(signature)`.** The signature covers `header + "." + payload` — the **header is part of the signed input**, which is why you cannot swap the `alg` after signing.
- **base64url, not base64.** Plain base64 emits `+`, `/` and `=`, which are illegal or unsafe in a URL path and in an HTTP header. Using base64 is the classic "works in curl, breaks in the browser" bug.
- **The 7 registered claims are `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti`** — and knowing the names is half the answer; the other half is *how each is validated*. `aud` may be a string **or** an array; `exp` is compared against the **server's** clock with a leeway; `jti` is the token id you use for revocation.
- **Only the signature is a security control.** `alg`, `typ`, `kid`, `cty` and `crit` are *routing metadata* — `kid` tells the verifier *which key* to use, it is not itself a trust decision.
- **HS256 = shared symmetric secret → every verifier can also *mint* tokens.** RS256/ES256/EdDSA = asymmetric → only the issuer can sign. This is a **key-distribution** problem, not a "which is more secure" problem.
- **Never read the algorithm from the token.** Pin it server-side. Reading `alg` from the token is what enables the **algorithm-confusion attack**, where the server is tricked into using the RSA *public* key as an HMAC secret.
- **"Stateless" describes the *access token*, not the *system*.** Verifying an access token needs zero server state — but refresh-token storage, denylists and `jti` tracking are all state. Claiming "JWTs are stateless, so we have no database" is the classic wrong answer.
- **The access token's TTL *is* your revocation mechanism.** A signed JWT is valid until it expires and **cannot be un-signed**. 5–15 minutes is the risk limiter; everything beyond that is authority you cannot take back.
- **The Spring Security chain is:** `BearerTokenAuthenticationFilter` → `DefaultBearerTokenResolver` → `JwtDecoder` (`NimbusJwtDecoder`) → `OAuth2TokenValidator` chain → `Jwt` → `JwtAuthenticationConverter` → `JwtAuthenticationToken` → `SecurityContextHolder` → `AuthorizationFilter`.

---

## 1. What & Why — The Problem This Solves

**The problem.** A traditional session means **every** API request needs a lookup: the `sessionId` in the cookie is a key into `HttpSession` (in-memory in one node, Redis-backed across nodes). That lookup is a network hop on the hot path, it is a single point of contention at scale, and it couples every API service to the session store — including the shared-database problem where **10 services × 2 pods must all reach the same session store**.

```text
   ❌ WHAT BREAKS WITH SERVER-SIDE SESSIONS
   ───────────────────────────────────────────────────────────────
   ① EVERY REQUEST NEEDS STATE   — the API cannot verify a request
                                  without asking someone "is this session valid?"
   ② THE SESSION STORE IS A HUB  — 10 services × 2 pods = 20 clients
                                  of one Redis, and one Redis outage
                                  takes down every service at once.
   ③ HORIZONTAL SCALING IS PAIN  — sticky sessions, or a shared store,
                                  or a session-replication layer.
   ④ MOBILE / SPA IS AWKWARD    — the client must manage a cookie and
                                  cope with third-party-cookie blocking.
```

**The solution.** The **issuer** signs a self-contained token. The **verifier** holds only the **public** key and can check the signature **locally, with no network call, no database, and no shared store**. The API is then *horizontally* scalable in the trivial sense: any node can verify any token.

```java
// ✅ The API verifies locally — no session store, no lookup
@Bean
JwtDecoder jwtDecoder(RSAPublicKey publicKey) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
            .signatureAlgorithm(SignatureAlgorithm.RS256)   // PINNED — never read from the token
            .build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://auth.acme.com"));
    return decoder;
}
```

**The analogy — a passport, not a key card.** A passport is issued by one country (the issuer), carries a set of printed facts (the claims: name, nationality, date of birth, expiry), and carries a **seal** that a foreign border post (the verifier) can check using only the issuer's *public* book of stamp designs (the public key). The border post never rings the issuing country to ask "is this still valid?" — but it *can* refuse it if the seal is broken, if the facts contradict the photo, or if it has a revocation list. Notice the three things the analogy gives you that the "JWT is stateless" slogan does not:

1. **The facts are readable** — anyone holding the passport can read the printed claims. Nobody mistakes a passport for a secret.
2. **The seal proves origin, not revocation** — a passport with a valid seal is accepted right up to its expiry date. That is why passports have a limited validity period.
3. **There is still a revocation list** — lost-passport databases exist precisely because the seal alone cannot express "this one is cancelled".

**What breaks with JWTs?** Everything that session storage gave you for free: **immediate revocation**, **server-side state** (the ability to look up "is this user currently banned?"), and **single-logout**. Chapter 03 is entirely about how you rebuild those on top of a signed blob.

> 💡 **The interview framing.** The question is never "is a JWT better than a session?". It is:
> **"what did I give up by making verification local, and how do I rebuild what I gave up?"**
> The honest answer is: I gave up instant revocation and server-side introspection, and I rebuild them with
> short TTLs + a refresh-token family + a token-version counter — which costs me state, but only
> on the *login/refresh* path, not on the *request* path.

---

## 2. Mental Model — The One Picture

```text
   THE JWT IS A SEALED ENVELOPE — NOT A LOCKED BOX
   ════════════════════════════════════════════

      eyJhbGciOiJSUzI1NiIsImtpZCI6ImtleS0yMDI2In0      ← PART 1: HEADER
      .eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiIxMjM0NTY3ODkwIiw
        .ImF1ZCI6WyJvcmRlcnMtYXBpIl0sImV4cCI6MTc0NTAwMDAwMH0      ← PART 2: PAYLOAD
      .c2lnbmF0dXJlLXZhbHVl                       ← PART 3: SIGNATURE

      ┌──────────────────────────────────────────────────────────────┐
      │ ① HEADER — "how to read me"                                  │
      │    alg, typ, kid, cty, crit                                 │
      │    ENCODED (anyone can read) but SIGNED (nobody can change)  │
      ├──────────────────────────────────────────────────────────────┤
      │ ② PAYLOAD — "what I claim"                                   │
      │    iss, sub, aud, exp, nbf, iat, jti + your claims          │
      │    ENCODED (anyone can read) and SIGNED (nobody can change)  │
      ├──────────────────────────────────────────────────────────────┤
      │ ③ SIGNATURE — "proof I am who I say I am"                    │
      │    HMAC(secret) or RSA/ECDSA(private key, signing input)    │
      │    ← THE ONLY PART THAT IS A SECURITY CONTROL                │
      └──────────────────────────────────────────────────────────────┘

      SIGNING INPUT (what actually gets signed):
        base64url(header) + "." + base64url(payload)
        └──────────────┬────────────────────────┘
                       └── the header is INSIDE the signed bytes.
                           Change the alg → signature breaks.

      ★ THE THREE THINGS YOU MUST BE ABLE TO EXPLAIN
      ────────────────────────────────────────────────
      ① ENCODING    base64url, NOT encryption. Reading is public.
      ② INTEGRITY   the signature covers the header AND the payload.
      ③ REVOCATION  a valid signature ≠ a currently-valid token.
                    `exp` is the only thing that stops it.
```

**What to look at:** the picture separates the two questions people conflate. **"Can I read it?"** is answered by the *encoding* (base64url — yes, anyone can). **"Can I trust it?"** is answered by the *signature* (only the key holder can produce one). **"Should I still accept it?"** is answered by the *claims* and by your revocation state — and this is the third question that most candidates forget exists.

---

## 3. Deep Dive — Theory (Beginner → Advanced)

### 3.1 Beginner Level

**The JOSE family — who does what.**

| JOSE object | What it is | Real-world analogy |
|---|---|---|
| **JWT** (RFC 7519) | A set of claims, encoded as a **JWS** or a **JWE** | The document itself |
| **JWS** (RFC 7515) | Signed content — `header.payload.signature`. Integrity, **not** confidentiality. | A wax seal on an open envelope |
| **JWE** (RFC 7516) | Encrypted content — `header.encryptedKey.iv.ciphertext.tag`. **Confidentiality.** | A sealed, opaque envelope |
| **JWK** (RFC 7517) | A single key expressed as JSON — `kty`, `n`, `e`, `kid`, `alg`, `use` | One key in a keyring |
| **JWK Set / JWKS** (RFC 7517 §5) | A JSON document containing an **array** of JWKs — what you publish at `/.well-known/jwks.json` | The whole keyring, published |
| **`jwk_set_uri`** | The URL where the JWKS lives — how a verifier discovers the keys | The address of the keyring |

**The key insight:** a JWT is *not* a JWS and *not* a JWE — it is a **claims set** that has been *serialized into* one of them. In practice, almost every "JWT" you meet in the wild is a **JWS** (signed, readable). A JWE-based JWT (RFC 7519 §5.2) is rare and is used when the payload must be confidential — e.g. an OpenID Connect **ID token** in the hybrid flow.

**The 3-part structure.**

```
base64url(UTF8(JWS Protected Header))  "."  base64url(UTF8(JWS Payload))  "."  base64url(JWS Signature)
```

For a JWE there are **five** parts:

```
base64url(Protected Header) "." base64url(Encrypted Key) "." base64url(IV) "." base64url(Ciphertext) "." base64url(Auth Tag)
```

**base64url vs base64 — the bug everybody hits once.**

| | Standard base64 | **base64url** (RFC 4648 §5) |
|---|---|---|
| Alphabet | `A–Z a–z 0–9 + /` | `A–Z a–z 0–9 - _` |
| Padding | `=` | usually **omitted** in JWT |
| `+` → | `-` | |
| `/` → | `_` | |
| URL-safe | ❌ | ✅ |
| Header-safe | ❌ (`+` and `/` are legal but confusing; `=` breaks value parsing) | ✅ |

A JWT encoded with **standard** base64 will:
- break when placed in a URL path or query string (`/` becomes a path separator, `+` becomes a space in a query string);
- frequently break `Authorization: Bearer` header parsing in strict servers;
- confuse anyone who copies the token out of a browser.

Java's `Base64.getUrlEncoder().withoutPadding()` is the correct encoder; `Base64.getEncoder()` is the wrong one.

**The most important sentence in this chapter.**

> **The payload is encoded, not encrypted. Anyone who can see the token can read every claim in it.**

```bash
# Decode a token's payload in one line — no tools needed:
echo 'eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20ifQ' | base64 -d 2>/dev/null || \
echo 'eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20ifQ' | tr '_-' '/+' | base64 -d
# → {"iss":"https://auth.acme.com"}
```

That command is the reason "never put PII in the payload" is a hard rule and not a style preference.

### 3.2 Intermediate Level

**The registered claims (RFC 7519 §4.1) and how each one is *validated*.**

| Claim | Name | RFC 7519 semantics | **How you validate it** | Common bug |
|---|---|---|---|---|
| `iss` | Issuer | Who issued the token | Must **exactly equal** your configured issuer. Use `JwtIssuerValidator` (or `JwtValidators.createDefaultWithIssuer`) | Accepting any issuer — the confused-deputy problem |
| `sub` | Subject | Who the token is about | Not empty; becomes the principal name | Leaving it null → an anonymous-looking principal that still passes authorization |
| `aud` | Audience | **Which service** may accept it | String **or** array of strings; the token is valid only if **your** service id is in it | Only checking the first element; or not checking at all |
| `exp` | Expiry | When it stops being valid | `now < exp + leeway`; the leeway covers clock skew (`JwtTimestampValidator` defaults to **60 s**) | Using a huge leeway (e.g. 10 minutes) — that is your TTL back |
| `nbf` | Not before | When it starts being valid | `now > nbf - leeway` | Ignoring it entirely on tokens that are supposed to be future-dated |
| `iat` | Issued at | When it was issued | Informational, but check it is not in the future and not absurdly old | Using `iat` as a substitute for `exp` |
| `jti` | JWT ID | A **unique identifier** for this token | The handle you use for revocation/denylist | Using it as an idempotency key for something else |

**`aud` is the one that bites.** RFC 7519 §4.1.3 says `aud` is "the intended audience". It **MAY** be a single string, or it **MAY** be an array. `Jwt.getAudience()` in Spring Security normalises this — it returns a `List<String>` regardless of which form the token used, and it returns an **empty list** if the claim is absent. That normalisation is a feature: a validator written against `getAudience()` handles both forms.

But note the corollary: **a token with no `aud` claim is not rejected by `getAudience()`** — it returns an empty list. Your validator must therefore explicitly require **non-empty** and **contains(yourServiceId)**. A validator that only checks `contains` on an empty list will correctly fail, but a validator that checks `isEmpty() → throw` on a token where you *meant* `aud` to be optional is the opposite bug.

**Private (custom) claims.** Everything that is not registered is private: `role`, `roles`, `scope`, `tenant_id`, `email`, `name`, `entitlements`. The rule is short and absolute:

> **The payload is not encrypted. Never put anything in it that the bearer should not be able to read.**

That means: no passwords, no card numbers, no national IDs, no home addresses, no anything that would become a liability in a log file. A token is a **bearer credential** — it goes in logs, in browser history, in reverse proxies, in APM traces, in error reports, in support screenshots. Treat it as public the moment it leaves the process.

**The JOSE header and why it must be integrity-protected.**

| Header param | Name | Purpose |
|---|---|---|
| `alg` | Algorithm | The signature algorithm — **the verifier must pin this, not read it** |
| `typ` | Type | `JWT` for a plain JWT, `at+jwt` for an OAuth2 access token, `JOSE`/`JOSE+JSON` for a nested JWS |
| `kid` | Key ID | **Which key** in the JWKS signed this token — the rotation handle |
| `cty` | Content type | The media type of the *payload* when the payload is itself a JWS/JWE (nested tokens) |
| `crit` | Critical | Extensions the recipient **MUST** understand and process |

The critical point: **the header is part of the signing input**. `signingInput = base64url(header) + "." + base64url(payload)`. Changing `alg` from `RS256` to `HS256` after the fact changes the signing input, so the signature no longer verifies. *That is the whole basis of the defence against algorithm confusion* — but only if the verifier is checking the algorithm **against its own pinned value**, because an unpinned verifier will *recompute* the signature using the attacker's chosen algorithm and it will match.

**`typ` matters more than beginners think.** `at+jwt` (RFC 9068) marks an OAuth2 access token; `JWT` marks a generic token. If your API accepts *both* an ID token and an access token, checking `typ` (or the claim set) prevents a **confused-deputy** where an ID token — meant for the client — is replayed at your API.

### 3.3 Advanced Level

**The signature algorithms — and the real trade-off.**

| Algorithm | Family | Key | Signature length | Key distribution | Use when |
|---|---|---|---|---|---|
| `HS256` / `HS384` / `HS512` | **Symmetric** (HMAC) | 1 shared secret | 32 / 48 / 64 B | ⚠️ **Every verifier can also sign** | Single service, or many services that all trust the same issuer |
| `RS256` / `RS384` / `RS512` | **Asymmetric** (RSA PKCS#1 v1.5) | private + public | 256 / 384 / 512 B | ✅ Only the issuer can sign | Many verifiers, or a public JWKS endpoint |
| `PS256` / `PS384` / `PS512` | **Asymmetric** (RSA-PSS) | private + public | 256 / 384 / 512 B | ✅ | Newer systems — **probabilistic**, so a signature never repeats |
| `ES256` / `ES384` / `ES512` | **Asymmetric** (ECDSA) | private + public | **64** / 96 / 132 B (raw r‖s) | ✅ | Mobile/IoT, small keys (P-256 = 32 B), high volume |
| `EdDSA` | **Asymmetric** (Ed25519) | private + public | 64 B | ✅ | **New systems — fast, modern, and the Spring Security 6.4+ recommendation** |

**The key-distribution trade-off — memorise this table.**

| | `HS256` | `RS256` / `ES256` / `EdDSA` |
|---|---|---|
| Who holds the secret? | Issuer **and every verifier** | Only the issuer (private key) |
| Who can **sign**? | Anyone who can verify | Only the issuer |
| Who can **verify**? | Anyone with the secret | Anyone with the public key |
| Blast radius of a leaked verifier | **Total compromise** — the attacker mints tokens | Low — the attacker can only verify |
| Public distribution | ❌ **Must stay secret** | ✅ Public key can be published openly |

> 🎯 **The interview line.** "HS256 is not 'less secure' than RS256 — it is a **different trust model**. With HS256, *every* party that can verify a token can also *create* one, so the verifier is inside the trust boundary. With RS256, the verifier holds only a public key, so it can check tokens but can never mint them. Use HS256 when one service both issues and verifies; use RS256 when you publish a JWKS to many verifiers, or when the verifiers are outside your trust boundary (partner apps, mobile clients, a service mesh)."

**The `none` algorithm attack.** RFC 7515 §4.1.10 defines `alg: "none"` — "no digital signature or MAC performed". A naive verifier that dispatches on the token's `alg` will happily accept:

```json
{ "alg": "none", "typ": "JWT" }
```

with an empty signature. **Defence:** pin the algorithm server-side. Spring Security's `NimbusJwtDecoder` requires an algorithm to be configured — `NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256)` or `NimbusJwtDecoder.withPublicKey(pk).signatureAlgorithm(SignatureAlgorithm.RS256)`. If you never configure one, Spring uses the algorithm from the header and you are vulnerable.

**The algorithm-confusion attack — the full story.**

This is the single most-asked JWT security question, so get the mechanism exactly right:

1. The service publishes an **RSA public key** at its JWKS endpoint (correct, normal design).
2. The attacker takes that **public key** and crafts a token with `"alg": "HS256"`.
3. The vulnerable server reads `alg` from the header → it now believes the token is HMAC-signed → it uses the **public key's bytes as the HMAC secret**.
4. The attacker, who also knows the public key (it is public!), computes `HMAC-SHA256(publicKeyBytes, header.payload)` — a perfectly valid signature under the server's own (mis)interpretation.
5. The server verifies → **match** → the attacker has minted an admin token. 🎉

The defence is a one-liner in the config: **never read `alg` from the token; pin it**. With a pinned `RS256` decoder, an `HS256` token is rejected before any key lookup happens.

**Keys, `kid` and rotation.**

- A **JWK** is a JSON representation of one key. For RSA: `{"kty":"RSA","n":"<modulus base64url>","e":"AQAB","kid":"key-2026-01","alg":"RS256","use":"sig"}`.
- A **JWKS** is `{"keys":[ <jwk>, <jwk> ]}` — the **array** matters, because during rotation there are ≥2 live keys.
- The **`kid`** in the header tells the verifier *which* JWK to use. Without it, the verifier must try every key (which works, but is slower and makes rotation ambiguous).
- A **`jwk_set_uri`** is how a verifier discovers the JWKS — configured as `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=...`, or discovered via the issuer's `/.well-known/openid-configuration` (`JwtDecoders.fromIssuerLocation(...)`).

**The rotation pattern.** Publish the new **public** key alongside the old one in the JWKS, start signing with the new key, wait for **the maximum token lifetime** (so every token signed by the old key has expired), then remove the old key. Skipping the overlap window is how you cause a 401 storm — see chapter 03.

**What "stateless" really means.** This deserves its own careful treatment because it is where the best and worst answers diverge.

| Layer | Stateless? | Why |
|---|---|---|
| **Verifying an access token** | ✅ **Fully stateless** | Signature check + claim check. No I/O, no DB, no shared store. Any node can verify. |
| **The system as a whole** | ❌ **Not stateless** | Refresh-token rows, denylist entries, `jti` tracking, token-version counters — all server state. |
| **Authorisation decisions based on live state** | ❌ **Not stateless** | "Is this user banned right now?" needs a lookup. A JWT cannot answer it without a check. |
| **Immediate logout** | ❌ **Not stateless** | A signed token stays valid until `exp`. You cannot un-sign it. |

So the precise statement is: **the access token is a *stateless bearer of a snapshot of authority*, and the refresh token is the *stateful, revocable root of trust*.** Statelessness is not a property of the system; it is a property of the *hot path*.

### 3.4 Expert Level

**JWT vs JWS vs JWE vs "JWS nested inside a JWT" — the composite cases.**

- A **JWS** whose payload is a JSON claims set → an ordinary **signed JWT**.
- A **JWE** whose plaintext is a JSON claims set → an **encrypted JWT** (RFC 7519 §5.2).
- A **JWS** whose payload is *another* JWS (or JWE) → a **nested JWT**; `cty: "JWT"` marks it. Used by token exchange / delegation chains.
- A **JWE** whose plaintext is a JWS → confidentiality *and* integrity, but **no one can read the inner claims without decrypting** — which defeats the point of introspection-free verification.

**Why nobody encrypts access tokens.** An encrypted access token (JWE) is decryptable only by holders of the decryption key. If the resource server must decrypt it, **the resource server holds a secret** — and then you are back to symmetric key distribution, plus you have *added* a decryption cost on the hot path and *lost* the ability to inspect claims in a gateway, a log pipeline or an API-gateway policy engine. The whole point of the JWS access token is that it is **readable everywhere and forgeable nowhere**. Confidentiality belongs in **transport** (TLS) and in the **refresh token** (which is opaque or hashed, never sent as an API credential).

**The `crit` header — the extension mechanism almost nobody uses.** RFC 7515 §4.1.11: `crit` lists header parameters that the recipient **MUST** understand. If a verifier encounters a `crit` parameter it does not implement, it **MUST** reject the token. This is the correct way to add a custom, security-relevant header (e.g. `crit: ["tenant"]`) — it turns "unknown extension" from "silently ignored" into "hard failure". Spring Security 6 does not enforce `crit` for you; you would implement it as a custom `OAuth2TokenValidator` or a `JwtDecoder` wrapper.

**The `jti` and replay.** RFC 7519 §4.1.7 defines `jti` as a unique identifier for the token, so that a recipient can detect replay. Note the subtlety: **`jti` alone does not prevent replay.** It *enables* replay detection only if the recipient keeps the set of seen `jti` values — which is state, which defeats the purpose of a stateless token. In practice, `jti` is used for (a) the denylist handle (chapter 03), (b) correlation/audit logs, and (c) idempotency in token-exchange flows. The honest statement in an interview: **a stateless JWT inherently permits replay within its TTL; the mitigation is a short TTL, not a `jti` blacklist.**

**Key sizes and the cost you actually pay.**

| Algorithm | Key size | Signature bytes | Base64url chars in the token | Notes |
|---|---|---|---|---|
| HS256 | 256-bit secret | 32 | 43 | Secret must be **≥32 bytes of entropy** |
| RS256 | 2048-bit (2048/3072/4096) | 256 | 342 | RSA-2048 is still acceptable; RSA-4096 costs ~4× the sign time |
| PS256 | 2048-bit | 256 | 342 | Probabilistic — the same payload never yields the same signature |
| ES256 | P-256 | **64** | 86 | Raw `r‖s` — **must be DER-encoded for JOSE** |
| EdDSA | Ed25519 (32 B private) | 64 | 86 | Fast, small, modern |

The **DER conversion** point trips up almost everyone: ECDSA in JOSE is the **raw concatenation** of `r` and `s`, each padded to the curve size (`r‖s` = 64 bytes for P-256). Java's `Signature` API produces **DER-encoded** ECDSA signatures. Nimbus handles the conversion internally — but if you ever implement a verifier by hand with `java.security.Signature`, you must convert DER ↔ raw yourself, or every ES256 token fails verification.

**The `aud` multi-service pattern.** A common production design:

```
iss = https://auth.acme.com         (who minted it)
aud = ["orders-api", "payments-api"] (who may accept it)
sub = user-12345                    (who it is about)
```

A verifier MUST check that **its own** service id is in `aud`. Checking "is `aud` non-empty" is not a check.

**Clock skew — the number to quote.** `JwtTimestampValidator` in Spring Security uses a default clock skew of **60 seconds**. That is the standard: enough to absorb NTP drift and slight container-clock differences between the issuer and the verifier, small enough that it does not meaningfully extend the token's life. Quoting "60 seconds" and naming the class is a strong expert signal.

**Token size — the practical limit.** A JWT is carried in an `Authorization` header. Most servers cap headers at **8 KB** (Tomcat's default `maxHttpHeaderSize` is 8 KB in Boot 3), and many ingresses/proxies cap at 4 KB. Every claim costs bytes. A JWT with 40 roles can exceed 4 KB and be rejected by the proxy with a 400 that looks like a client bug. This is why the **claim→authority mapping** matters (§ chapter 04) and why the right fix is to put **role IDs** in the token and resolve them to names server-side, rather than embedding full role objects.

**The `iat` check most people skip.** A token with `exp` in the future but `iat` in the past 30 days is a *valid* token by the RFC. If your issuer was compromised yesterday and you rotate today, tokens minted yesterday are still accepted. Checking `iat` against a reasonable window (e.g. `now - issuedAt <= maxAge`) is a cheap extra constraint, but note the trade-off: it **breaks** long-lived tokens, so it is usually applied to *refresh* tokens, not access tokens.

**What "JWT" means when people say "OAuth2 access token".** Per RFC 9068, a JWT-format OAuth2 access token is a JWS whose `typ` is `at+jwt` and whose `aud` names the resource server. Many implementations also include `client_id`, `scope`, `conf` (confirmation) and `act` (actor) claims. If your API accepts *both* a JWT access token and a JWT ID token, **`typ` and `aud` are the discriminators**, and skipping that check is a real vulnerability (see chapter 04, attack #10, the confused deputy).

---

## 4. Internal Working Mechanism

**The exact Spring Security call chain for a bearer-token request.**

```
HTTP request:  GET /orders/42
               Authorization: Bearer eyJhbGciOiJSUzI1NiIs...

FilterChainProxy.doFilter()
  └─ FilterChainProxy$VirtualFilterChain.doFilter()
       ├─ WebAsyncManagerIntegrationFilter
       ├─ SecurityContextHolderFilter            ← loads any existing SecurityContext
       ├─ HeaderWriterFilter
       ├─ CorsFilter                            ← MUST run before authentication
       ├─ CsrfFilter
       ├─ LogoutFilter
       ├─ BearerTokenAuthenticationFilter        ← ★ THE JWT ENTRY POINT
       │    ├─ BearerTokenResolver.resolve(request)
       │    │     └─ DefaultBearerTokenResolver
       │    │          ├─ reads the "Authorization" header
       │    │          ├─ checks the scheme is "Bearer" (case-insensitive)
       │    │          ├─ trims whitespace, rejects a multi-value header
       │    │          └─ returns the raw token String, or null
       │    ├─ if token == null → chain.doFilter() (no authentication attempted)
       │    ├─ JwtAuthenticationProvider.authenticate(authentication)
       │    │    └─ JwtDecoder.decode(token)
       │    │         └─ NimbusJwtDecoder.decode()
       │    │              ├─ parse into SignedJWT (split on '.', base64url-decode)
       │    │              ├─ select the key:
       │    │              │    ├─ if 'kid' present → look it up in the JWK Set
       │    │              │    └─ else → try every key in the set
       │    │              ├─ VERIFY THE SIGNATURE with the pinned algorithm
       │    │              │    └─ (algorithm comes from the DECODER CONFIG,
       │    │              │         never from the token header)
       │    │              ├─ JwtTimestampValidator  (exp / nbf, 60 s leeway)
       │    │              ├─ JwtIssuerValidator    (iss == expected)
       │    │              ├─ JwtClaimValidator      (aud contains this service)
       │    │              └─ DelegatingOAuth2TokenValidator runs them in order
       │    │                   └─ any failure → JwtValidationException
       │    ├─ build a Jwt object (claims map + headers + tokenValue)
       │    ├─ JwtAuthenticationConverter.convert(jwt)
       │    │    └─ JwtAuthenticationToken(jwt, authorities, principalName)
       │    └─ SecurityContextHolder.getContext().setAuthentication(auth)
       ├─ ... (UsernamePasswordAuthenticationFilter, etc. are skipped) ...
       ├─ AnonymousAuthenticationFilter          ← only if still unauthenticated
       ├─ SessionManagementFilter
       ├─ ExceptionTranslationFilter            ← turns exceptions into 401/403
       └─ AuthorizationFilter                    ← ★ the final authorization decision
            └─ AuthorizationManager.check(authentication, request)
                 └─ your .requestMatchers(...) rules
```

**The extension points a developer would actually implement.**

| Interface / hook | Package | What you implement it for |
|---|---|---|
| `BearerTokenResolver` | `org.springframework.security.oauth2.server.resource.web` | Read the token from somewhere other than the header (a cookie, a query param — usually a bad idea) |
| `OAuth2TokenValidator<Jwt>` | `org.springframework.security.oauth2.core` | **The** place to add custom claim validation |
| `Converter<Jwt, AbstractAuthenticationToken>` | `org.springframework.security.oauth2.server.resource.authentication` | Map claims → `GrantedAuthority` (this is what `JwtAuthenticationConverter` does) |
| `Converter<Jwt, Collection<GrantedAuthority>>` | same package | The narrower "claims → authorities" hook |
| `JwtDecoder` | `org.springframework.security.oauth2.jwt` | Replace the whole decode+validate step (e.g. to add a denylist check) |
| `AuthenticationEntryPoint` | `org.springframework.security.web` | Customise the **401** body (the default is empty) |
| `AccessDeniedHandler` | `org.springframework.security.web` | Customise the **403** body |

**`SecurityContextHolder` — the one thing to understand precisely.** It is a `ThreadLocal<SecurityContext>` (by default, `ThreadLocalSecurityContextHolderStrategy`). The `SecurityContext` holds an `Authentication` object. In a **stateless** JWT setup:

- `SessionCreationPolicy.STATELESS` means Spring does **not** create an `HttpSession` and does **not** persist the `SecurityContext` between requests;
- therefore, on **every single request**, the `BearerTokenAuthenticationFilter` must re-parse the token and re-populate the `SecurityContext` — that is the price of statelessness;
- in a **virtual thread** or **reactive** context this matters: Spring Security 6 provides `DelegatingSecurityContextExecutor` and `DelegatingSecurityContextCallable` to propagate the context across thread boundaries, and `ReactiveSecurityContextHolder` for WebFlux.

**The `OAuth2TokenValidator` chain — order and semantics.**

```java
OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer("https://auth.acme.com");
// ↳ internally: DelegatingOAuth2TokenValidator( JwtTimestampValidator, JwtIssuerValidator )

OAuth2TokenValidator<Jwt> audienceValidator =
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains("orders-api"));

decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuerValidator, audienceValidator));
```

`DelegatingOAuth2TokenValidator` runs the validators **in order** and returns the **first** failure. The `JwtClaimValidator` receives the **already-normalised** claim value — for `aud`, Spring Security's `Jwt` object stores it as a `List<String>` even if the token carried a bare string.

**A critical detail about `JwtValidators.createDefaultWithIssuer`.** It creates a **timestamp validator + issuer validator**, and the timestamp validator uses the default **60-second** clock skew. It does **not** validate `aud`. That is why most production code adds a separate `JwtClaimValidator` for the audience — a step that is very often forgotten and produces the "token is accepted by the wrong service" bug.

---

## 5. Step-by-Step Execution Flow

**Request path: a valid bearer token arrives at a resource server.**

1. **Socket accept → Tomcat.** Tomcat accepts the connection and hands the request to the Servlet container. Spring Boot's `DispatcherServlet` is *not* reached yet — the security filter chain runs first.
2. **`FilterChainProxy`** (a single `Servlet` filter) holds the ordered list of security filters and walks it. The order is fixed by `SecurityConfigurer`s, not by `@Order` on your own filters.
3. **`SecurityContextHolderFilter`** loads the `SecurityContext` for this thread. In `STATELESS` mode it starts **empty** — nothing is restored from a session.
4. **`CorsFilter`** — CORS **must** run before authentication so that a preflight `OPTIONS` request (which carries no `Authorization` header) is answered rather than rejected.
5. **`CsrfFilter`** — for a bearer-token API this is usually disabled, because there is no cookie for a cross-site attacker to ride on. (If your refresh token lives in a cookie, CSRF protection must stay on for that endpoint — see chapter 02.)
6. **`BearerTokenAuthenticationFilter`** — the JWT entry point:
   1. `BearerTokenResolver.resolve(request)` reads the `Authorization` header.
   2. If the header is absent or not `Bearer`, it returns `null` and the filter **does nothing** — the request continues unauthenticated. (It does **not** 401 here.)
   3. If a token is present, the filter attempts authentication. A failure here produces a **401** through `ExceptionTranslationFilter` → `BearerTokenAuthenticationEntryPoint`.
7. **`DefaultBearerTokenResolver`** — rejects:
   - a missing or non-`Bearer` scheme;
   - a header with **multiple** values (a request-smuggling vector);
   - an empty token after trimming.
8. **`JwtDecoder.decode(token)`** — `NimbusJwtDecoder`:
   1. Split the token on `.` → must be **3** parts.
   2. Base64url-decode the header → parse JSON → read `alg` and `kid`.
   3. Base64url-decode the payload → parse JSON → the claims map.
   4. Select the verification key:
      - `kid` present → look it up in the JWK Set (cached, refreshed per the cache policy);
      - `kid` absent → try each key in the set.
   5. **Verify the signature** using the **pinned** algorithm. A mismatch → `JwtValidationException`.
   6. Run the `OAuth2TokenValidator` chain.
9. **`Jwt` object** is produced — `getTokenValue()`, `getHeaders()`, `getClaims()`, `getSubject()`, `getIssuer()`, `getAudience()`, `getExpiresAt()`, `getId()`.
10. **`JwtAuthenticationConverter`** converts the `Jwt` into an `AbstractAuthenticationToken`:
    - principal name = `sub` by default (configurable via `setPrincipalClaimName`);
    - authorities = from the `scope` claim by default, each prefixed `SCOPE_`.
11. **`SecurityContextHolder.getContext().setAuthentication(auth)`** — the request is now authenticated for the remainder of the chain.
12. **`AuthorizationFilter`** — the **final** decision. `AuthorizationManager` evaluates your `.requestMatchers(...)` rules against the `Authentication` in the context.
13. **Controller** executes; the `Authentication` is available via `@AuthenticationPrincipal Jwt jwt`.
14. **`SecurityContextHolder.clearContext()`** at the end of the request (in `STATELESS` mode there is nothing to persist).

**Failure paths — what the user actually sees.**

| Failure | Exception | HTTP status | Body (default Spring) |
|---|---|---|---|
| No `Authorization` header on a protected route | *(none — anonymous)* | **401** | empty (from `BearerTokenAuthenticationEntryPoint`) |
| Malformed token (not 3 parts) | `JwtException` | **401** | empty |
| Bad signature | `JwtValidationException` | **401** | empty |
| Expired | `JwtValidationException` ("`exp` is in the past") | **401** | empty |
| Wrong `iss` | `JwtValidationException` | **401** | empty |
| Wrong `aud` | `JwtValidationException` | **401** | empty |
| Valid token, insufficient authority | `AccessDeniedException` | **403** | empty (from `BearerTokenAccessDeniedHandler`) |

> 📌 The distinction between **401** and **403** is a favourite interview question and it is pinned in this repository's canonical status table: **401 = not authenticated** (no/invalid credentials), **403 = authenticated but not authorized**.

---

## 6. Visualizations

### 6.1 How Spring Security validates a JWT (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant F as FilterChainProxy
    participant B as BearerTokenAuthenticationFilter
    participant R as DefaultBearerTokenResolver
    participant D as JwtDecoder (NimbusJwtDecoder)
    participant K as JWK Set (cached)
    participant V as OAuth2TokenValidator chain
    participant X as JwtAuthenticationConverter
    participant S as SecurityContextHolder
    participant A as AuthorizationFilter

    C->>F: GET /orders/42<br/>Authorization: Bearer eyJhbGci...
    F->>B: filter(request, chain)
    B->>R: resolve(request)
    R-->>B: "eyJhbGci..." (token String)
    B->>D: decode(token)
    D->>K: find JWK by kid="key-2026-01"
    K-->>D: RSAPublicKey
    Note over D: VERIFY with the PINNED algorithm RS256<br/>(never the alg from the header)
    D->>V: validate(jwt)
    V->>V: JwtTimestampValidator (exp/nbf, 60s leeway)
    V->>V: JwtIssuerValidator (iss == auth.acme.com)
    V->>V: JwtClaimValidator (aud contains "orders-api")
    V-->>D: OK
    D-->>B: Jwt (claims, headers, tokenValue)
    B->>X: convert(jwt)
    X-->>B: JwtAuthenticationToken(sub, authorities)
    B->>S: setAuthentication(auth)
    B->>F: chain.doFilter()
    F->>A: authorize(auth, request)
    A-->>C: 200 OK
```

**What to look at:** the **signature check happens before the claim validation**, and both happen
**before** the `SecurityContext` is populated. A token that fails either step never reaches
`AuthorizationFilter` — it fails at the *authentication* stage and becomes a **401**, not a
**403**. Also note that the algorithm used for verification comes from the **decoder
configuration**, never from the header the attacker controls.

### 6.2 Anatomy of a real token (ASCII)

```text
   HEADER   ─┐
   eyJhbGciOiJSUzI1NiIsImtpZCI6ImtleS0yMDI2IiwicmF0IjoiUlMyNTYifQ
   │  base64url-decode │
   ▼                   │
   {                          │
     "alg": "RS256",          │   ← the SIGNATURE ALGORITHM (pin this server-side!)
     "kid": "key-2026-01",    │   ← which key in the JWKS signed this
     "typ": "at+jwt"          │   ← token type (JWT | at+jwt | JOSE)
   }                          │
   ───────────────────────────┘

   PAYLOAD  ─┐
   eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJ1c2VyLTQyMjk1NyIsImF1ZCI6
   ICJvcmRlcnMtYXBpIiwicm9sZXMiOlsiUkVBRF9PTkxZIiwiU1RBRkYiXSwInRlcml0Ijoib25seSIs
   ImV4cCI6MTc0NTAwMDAwMCwianRpIjoiZjQ5MmEzMDctOGQ5Yy00YjRhLTkxOGMtN2M1MGIzNThlZTAi
   │  base64url-decode │
   ▼                   │
   {                          │
     "iss": "https://auth.acme.com",  ← issuer — MUST be validated
     "sub": "user-42957",             ← subject — the principal
     "aud": ["orders-api"],           ← audience — string OR array
     "roles": ["READ_ONLY", "STAFF"], ← private claim
     "territory": "only",             ← private claim
     "exp": 1745000000,               ← expiry (epoch seconds)
     "jti": "f492a307-8d9c-4b4a-918c-7c50b358ee0"  ← unique token id
   }                          │
   ───────────────────────────┘

   SIGNATURE
   n0mQ2b8Xh1yQ... (342 base64url chars for RS256 = 256 raw bytes)
   │
   └─▶ SIGNED OVER: base64url(header) + "." + base64url(payload)
       └─▶ VERIFIED WITH: the RSA PUBLIC KEY for kid="key-2026-01"
           └─▶ if ANY byte of the header or payload changed → signature fails

   ★ READ THE PAYLOAD — NO KEY NEEDED:
     echo '<payload-part>' | tr '_-' '/+' | base64 -d
     → this is why PII must NEVER go in a JWT payload.
```

**What to look at:** the header is *inside* the signed bytes — that is why `alg` cannot be
swapped after signing. The payload is *outside* the confidentiality boundary but *inside* the
integrity boundary. The signature is the only part that requires a key.

### 6.3 The algorithm families and the key-distribution implication (Mermaid graph)

```mermaid
graph TD
    A["A token arrives at the verifier"] --> B{"Which family?"}

    B -->|"alg = HS256/384/512"| C["SYMMETRIC — HMAC"]
    C --> C1["1 shared secret"]
    C1 --> C2["Issuer SIGNS with it"]
    C1 --> C3["Verifier VERIFIES with it"]
    C2 --> C4["⚠️ The verifier holds the SAME bytes"]
    C4 --> C5["⇒ the verifier can also MINT tokens<br/>(blast radius = total compromise)"]
    C5 --> C6["Trust boundary must contain<br/>every verifier"]

    B -->|"alg = RS256/384/512<br/>PS256/384/512<br/>ES256/384/512<br/>EdDSA"| D["ASYMMETRIC — public key"]
    D --> D1["Issuer holds the PRIVATE key"]
    D1 --> D2["Issuer SIGNS with it"]
    D --> D3["Verifier holds only the PUBLIC key"]
    D3 --> D4["✅ the verifier can NEVER mint a token"]
    D4 --> D5["⇒ public key can be published<br/>at a JWKS endpoint"]
    D5 --> D6["⇒ safe across a trust boundary<br/>(partners, mesh, mobile)"]

    style C5 fill:#ffd6d6,stroke:#c00
    style D4 fill:#d6ffd6,stroke:#0a0
    style C6 fill:#fff3cd,stroke:#c80
    style D6 fill:#d6ffd6,stroke:#0a0
```

**What to look at:** the green box and the red box. Everything else in the algorithm choice
is a second-order concern (key size, signature length, CPU cost). The **first-order** concern
is: *does anyone outside my trust boundary need to verify a token I mint?* If yes, asymmetric.
If no, HS256 is simpler and perfectly fine.

### 6.4 The JWKS key-rotation flow (Mermaid sequence)

```mermaid
sequenceDiagram
    autonumber
    participant I as Issuer (holds private key OLD + NEW)
    participant J as JWKS endpoint (/.well-known/jwks.json)
    participant V as Verifier (NimbusJwtDecoder)
    participant R as Resource API

    Note over I,J: T0 — rotation starts. New key "key-2026-02" generated.
    I->>J: publish BOTH keys: key-2026-01 (old) + key-2026-02 (new)
    Note over V: Verifier refreshes its JWK Set cache<br/>(default: on unknown kid, then on the cache TTL)

    I->>I: from now on, SIGN with key-2026-02
    I-->>R: token with header kid="key-2026-02"

    R->>V: decode(token)
    V->>V: kid found → RSAPublicKey(key-2026-02)
    V-->>R: valid ✅

    Note over I: OVERLAP WINDOW — must be >= max token lifetime (e.g. 15 min)
    Note over I,V: tokens signed with key-2026-01 are STILL VALID during the window

    I->>J: T0 + 15min+ — REMOVE key-2026-01
    Note over V: any token with kid="key-2026-01" now →<br/>"no matching key" → 401

    Note over I,J: ⚠️ If you remove the old key EARLY,<br/>every live token dies at once = 401 storm.
```

**What to look at:** the **overlap window is longer than the maximum token lifetime**, not
shorter. The window starts when you *publish* the new key and ends only after the old key's
last possible token has expired. Removing the old key is the *last* step, not the first.

---

## 7. Production-Level Use Cases & Real Industry Examples

**1. Banking — a multi-service platform with per-service audiences.**
A retail bank runs ~40 services behind an API gateway. Every token carries
`aud: ["payments-api", "ledger-api"]`, `sub: <customerId>`, `roles: ["PAYMENT_INITIATE"]` and
`exp` = **5 minutes**. Signing is `RS256` with a private key in a **KMS** (HSM-backed); the
public key is published at `/oauth2/jwks`. The per-transaction authorisation check (a
₹2,00,000 transfer needs step-up re-authentication within the last 5 minutes) is enforced by
reading an `amr`/`auth_time` claim, not by a session. Scale: ~12,000 API calls/second peak,
p99 verification budget **< 1 ms** — which is why the API does *not* hit a database on the
request path.

**2. E-commerce — a mobile app with a public JWKS.**
An e-commerce mobile app talks to ~25 microservices. The auth service signs with `ES256`
(P-256) because the token must fit in a mobile header and the signature must be small;
the private key never leaves the issuer, and the public key is embedded in the app *and*
published at the JWKS. Access token TTL is **10 minutes**; the refresh token is a 256-bit
random opaque string stored **SHA-256 hashed** in Postgres, rotated on every use, delivered
in an `HttpOnly` cookie for web and in the **Keychain** for iOS / **EncryptedSharedPreferences**
for Android. Scale: 2M daily active users, ~30,000 refreshes/second at peak.

**3. Multi-tenant SaaS — one `kid` per tenant.**
A SaaS platform serves 4,000 tenants from one deployment. Each tenant's tokens are signed
with a **per-tenant key** identified by `kid: "tenant-<tenantId>-v3"`, so a tenant can be
rotated or revoked independently. The tenant is read from the **validated** `tenant_id`
claim — never from an `X-Tenant-Id` header — and the resource server fetches the JWKS from
the issuer, which returns only the keys for tenants that are allowed to talk to it.

**4. Internal service mesh — 5-minute tokens, per-destination audience.**
A service mesh issues a token per destination service: `aud: ["inventory-api"]`, TTL **5
minutes**, `act`/`sub` carrying the calling service. The whole mesh mints ~50,000 tokens/
second, so the issuer is a dedicated, horizontally-scaled service and signing is `EdDSA`
for speed (Ed25519 signs in ~20 µs vs ~700 µs for RSA-2048).

**5. A B2B integration platform — the asymmetric-only case.**
An integration platform lets *customer* systems push data. Those systems must **verify**
tokens the platform mints, but the platform does **not** trust them to verify tokens the
customer mints. That asymmetry is exactly why this pattern uses `RS256`/`ES256`: the
platform publishes a public key (safe to share), and the customer's verification key is
**never** the platform's.

---

## 8. Worked Examples (with Output + Dry Run)

### 8.1 Worked Example 1 — Encode a token, decode it, and dry-run the signing input

**Problem.** You need to *see* what a real token looks like, what the signing input is, and
how the decoder verifies it.

**Code — the issuer (HS256).**

```java
package com.example.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

public final class TokenDemo {

    // 32 bytes = 256 bits of entropy. A short secret is a forgeable secret.
    private static final SecretKey SECRET =
            new SecretKeySpec("a-very-long-32-byte-secret-key-value!!".getBytes(StandardCharsets.UTF_8),
                    "HmacSHA256");

    public static void main(String[] args) {
        JwtEncoder encoder = NimbusJwtEncoder.withSecretKey(SECRET)
                .algorithm(MacAlgorithm.HS256)
                .build();

        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("https://auth.acme.com")
                .subject("user-42957")
                .audience(java.util.List.of("orders-api"))
                .issuedAt(now)
                .expiresAt(now.plus(10, ChronoUnit.MINUTES))
                .id("f492a307-8d9c-4b4a-918c-7c50b358ee0")
                .claim("roles", java.util.List.of("READ_ONLY", "STAFF"))
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256)
                .type("JWT")
                .keyId("key-2026-01")
                .build();

        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        System.out.println("TOKEN   = " + token);

        String[] parts = token.split("\\.");
        System.out.println("HEADER  = " + decode(parts[0]));
        System.out.println("PAYLOAD = " + decode(parts[1]));
        System.out.println("SIGNING INPUT (what the MAC covers) =");
        System.out.println("  " + parts[0] + "." + parts[1]);
    }

    private static String decode(String segment) {
        byte[] json = Base64.getUrlDecoder().decode(segment);
        return new String(json, StandardCharsets.UTF_8);
    }
}
```

**Actual expected output.**

```text
TOKEN   = eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleS0yMDI2LTAxIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJ1c2VyLTQyOTU3IiwiYXVkIjpbIm9yZGVycy1hcGkiXSwiaWF0IjoxNzQ1MDAwMDAwMCwiZXhwIjoxNzQ1MDA2MDAwMCwianRpIjoiZjQ5MmEzMDctOGQ5Yy00YjRhLTkxOGMtN2M1MGIzNThlZTAiLCJyb2xlcyI6WyJSRUFEX09OTFkiLCJTVEFGRiJdfQ.dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
HEADER  = {"alg":"HS256","kid":"key-2026-01","typ":"JWT"}
PAYLOAD = {"iss":"https://auth.acme.com","sub":"user-42957","aud":["orders-api"],"iat":1745000000,"exp":1745006000,"jti":"f492a307-8d9c-4b4a-918c-7c50b358ee0","roles":["READ_ONLY","STAFF"]}
SIGNING INPUT (what the MAC covers) =
  eyJhbGciOiJIUzI1NiIsImtleS0yMDI2LTAxIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJ1c2VyLTQyOTU3IiwiYXVkIjpbIm9yZGVycy1hcGkiXSwiaWF0IjoxNzQ1MDAwMDAwMCwiZXhwIjoxNzQ1MDA2MDAwMCwianRpIjoiZjQ5MmEzMDctOGQ5Yy00YjRhLTkxOGMtN2M1MGIzNThlZTAiLCJyb2xlcyI6WyJSRUFEX09OTFkiLCJTVEFGRiJdfQ
```

**Dry run — step by step.**

1. `Instant now` is captured **once** and reused for `iat` and `exp`, so the two are
   guaranteed consistent (a classic bug is calling `Instant.now()` twice and getting a
   1 ms drift).
2. `now.plus(10, MINUTES)` → `exp = now + 600 s`. With `iat = 1745000000`,
   `exp = 1745006000`. **Difference = 600 s = 10 minutes.** ✅
3. `JwtClaimsSet.builder()` collects the claims into a `LinkedHashMap`; the **registered**
   claims (`iss`, `sub`, `aud`, `iat`, `exp`, `jti`) are set through their dedicated
   builder methods, and everything else goes through `.claim(name, value)`. This is why
   `aud` is always a `List<String>` in the JSON, even when you passed a single string.
4. `JwsHeader.with(MacAlgorithm.HS256)` sets `alg: "HS256"`. `.keyId("key-2026-01")` sets
   `kid`. `.type("JWT")` sets `typ`.
5. `NimbusJwtEncoder` serialises the claims to JSON, base64url-encodes the header and the
   payload **without padding**, computes
   `HMAC-SHA256(secret, base64url(header) + "." + base64url(payload))`, and base64url-encodes
   the 32-byte MAC. The header JSON key order is `alg`, `kid`, `typ` — the order the
   builder writes them.
6. The result is `header + "." + payload + "." + signature` — **exactly three parts**.
7. `decode(parts[1])` reproduces the original claims JSON. Note there is **no
   `=` padding** anywhere and **no `+` or `/`** — because base64url was used.

**Takeaway.** The signing input is *literally* `parts[0] + "." + parts[1]`. If you change
**either** of those two parts, the MAC changes and verification fails. That single fact
underpins the `alg: none` defence, the algorithm-confusion defence, and the reason the
header must be protected.

### 8.2 Worked Example 2 — An expired token, the exact error, and the 401 response

**Problem.** A user's access token expires while they are reading a page. The next API
call fails. You need to know exactly what the server does and what it returns.

**Code — the decoder with validators.**

```java
package com.example.jwt;

import java.time.Instant;
import java.util.List;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class ExpiredTokenDemo {

    public static void main(String[] args) {
        SecretKey key = new SecretKeySpec(
                "a-very-long-32-byte-secret-key-value!!".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");

        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> timestampAndIssuer =
                JwtValidators.createDefaultWithIssuer("https://auth.acme.com");
        OAuth2TokenValidator<Jwt> audience =
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud -> aud.contains("orders-api"));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestampAndIssuer, audience));

        // A token that expired 2 minutes ago (TTL was 10 min, issued 12 min ago).
        String expiredToken = "eyJhbGciOiJIUzI1NiIsImtpZCI6ImtleS0yMDI2LTAxIiwidHlwIjoiSldUIn0."
                + "eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJ1c2VyLTQyOTU3IiwiYXVkIjpbIm9yZGVycy1hcGkiXSwi"
                + "aWF0IjoxNzQ5OTk2ODAwMCwiZXhwIjoxNzQ5OTk3NDAwMCwianRpIjoiZjQ5MmEzMDctOGQ5Yy00YjRhLTkxOGMtN2M1MGIzNThlZTAifQ."
                + "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";

        try {
            Jwt jwt = decoder.decode(expiredToken);
            System.out.println("UNEXPECTED: decoded " + jwt.getSubject());
        } catch (Exception ex) {
            System.out.println("EXCEPTION CLASS = " + ex.getClass().getName());
            System.out.println("MESSAGE         = " + ex.getMessage());
        }
    }
}
```

**Actual expected output.**

```text
EXCEPTION CLASS = org.springframework.security.oauth2.jwt.JwtValidationException
MESSAGE         = "exp" claim timestamp check failed (exp: 1744999740, now: 1745000120)
```

**Dry run — step by step.**

1. `NimbusJwtDecoder.decode()` splits the token into 3 parts, parses the header
   (`alg: HS256`, `kid: key-2026-01`) and the payload.
2. **Signature verification happens first** and it **succeeds** — the token was signed with
   the correct secret. The token is *authentic*.
3. `JwtValidators.createDefaultWithIssuer(...)` returns a `DelegatingOAuth2TokenValidator`
   wrapping `JwtTimestampValidator` and `JwtIssuerValidator`.
4. `JwtTimestampValidator` computes `now = 1745000120` (the server clock) and compares it
   with `exp = 1744999740`. `now - exp = 380 s > 0` → the token is expired.
   The **60-second leeway** is already applied: the effective threshold is
   `exp + 60 = 1744999800`, and `1745000120 > 1744999800`, so it still fails — with
   **380 s − 60 s = 320 s** of margin, comfortably beyond the leeway.
5. The validator returns an `OAuth2Error` with code `invalid_token`, and
   `DelegatingOAuth2TokenValidator` short-circuits: the **issuer** and **audience**
   validators never run. That is why the error names only `exp`.
6. `NimbusJwtDecoder` wraps it in a `JwtValidationException`.
7. In the filter chain this propagates out of `JwtAuthenticationProvider`, and
   `BearerTokenAuthenticationFilter` catches it and calls
   `AuthenticationEntryPoint.commence(...)` → `BearerTokenAuthenticationEntryPoint` →
   **401**.

**The HTTP response the client sees.**

```text
HTTP/1.1 401 Unauthorized
WWW-Authenticate: Bearer error="invalid_token", error_description="An error occurred while attempting to decode the Jwt: Signed JWT rejected: Another algorithm expected, or no matching key(s) found"
Content-Length: 0
```

> 📌 Note the `WWW-Authenticate` header: RFC 6750 §3 requires the resource server to
> return it with the 401 so the client knows **why** it must refresh. A custom
> `AuthenticationEntryPoint` should preserve it.

**Takeaway.** An expired token produces a **401**, never a 403. And the error message
distinguishes "the signature was wrong" from "the claims were wrong" — if you cannot tell
them apart, your monitoring is blind.

### 8.3 Worked Example 3 — The `alg: none` attack and the exact rejection

**Problem.** Someone posts an unsigned token to your API. What happens?

**The attacker's token.**

```json
{ "alg": "none", "typ": "JWT" }
```

```text
header  : eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0
payload : eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJhdHRhY2tlciIsImF1ZCI6WyJvcmRlcnMtYXBpIl0sImV4cCI6NTE5MjM2MjM4ODgsInJvbGVzIjpbIlJPT0wiXX0
signature: (empty)
```

**The full token:** `eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0.eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJhdHRhY2tlciIsImF1ZCI6WyJvcmRlcnMtYXBpIl0sImV4cCI6NTE5MjM2MjM4ODgsInJvbGVzIjpbIlJPT0wiXX0.`

**Code — a vulnerable decoder vs a pinned decoder.**

```java
package com.example.jwt;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

public final class NoneAlgorithmDemo {

    private static final String NONE_TOKEN =
            "eyJhbGciOiJub25lIiwidHlwIjoiSldUIn0."
          + "eyJpc3MiOiJodHRwczovL2F1dGguYWNtZS5jb20iLCJzdWIiOiJhdHRhY2tlciIsImF1ZCI6WyJvcmRlcnMtYXBpIl0sImV4cCI6NTE5MjM2MjM4ODgsInJvbGVzIjpbIlJPT0wiXX0.";

    public static void main(String[] args) {
        SecretKey key = new SecretKeySpec(
                "a-very-long-32-byte-secret-key-value!!".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                "HmacSHA256");

        // ── ✅ PINNED decoder — the algorithm comes from CONFIG ──────────────
        JwtDecoder pinned = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        try {
            pinned.decode(NONE_TOKEN);
            System.out.println("PINNED  : token ACCEPTED  ← THIS MUST NOT HAPPEN");
        } catch (JwtValidationException ex) {
            System.out.println("PINNED  : rejected -> " + ex.getMessage());
        }
    }
}
```

**Actual expected output.**

```text
PINNED  : rejected -> Failed to create JWS SignedJWT: Unsupported algorithm 'none' for the signer
```

**Dry run — step by step.**

1. The decoder splits the token on `.`. The trailing `.` produces an **empty third
   part** — Nimbus sees a token with **no signature** at all.
2. The decoder reads the header and sees `"alg": "none"`.
3. **The pinned decoder never asks the header for the algorithm.** It was built with
   `macAlgorithm(MacAlgorithm.HS256)`, so Nimbus's `MACVerifier` is configured for
   `HS256` only, and the JWS algorithm family is `MAC`.
4. Nimbus attempts to build the `SignedJWT` and finds the header's algorithm
   (`Algorithm.NONE`) is not a member of the configured family → it throws.
   Spring wraps this in a `JwtValidationException`.
5. In the filter chain: **401**, `WWW-Authenticate: Bearer error="invalid_token"`.

**The same attack against an UNPINNED decoder.** If the decoder is built without an
explicit algorithm — for example `NimbusJwtDecoder.withSecretKey(key).build()` with no
`macAlgorithm(...)`, or a hand-rolled verifier that dispatches on the header — the header's
`alg` selects the verification path. For `alg: none` the verifier performs **no signature
check at all** and accepts the token:

```text
VULNERABLE: token ACCEPTED, subject = attacker, authorities = [ROLE_ROOT]
```

> ⚠️ **This is exactly why `NimbusJwtDecoder` must always be given an explicit
> algorithm.** Spring's `NimbusJwtDecoder` *does* fall back to the header's algorithm
> when none is configured. Configure it. Always.

**Takeaway.** The `none` attack is trivially blocked by one line of configuration, and
catastrophically expensive if you forget it. It is also the perfect regression test — write
it once, keep it forever.

---

## 9. Code from Scratch — Production-Grade

**The complete, copy-pasteable resource-server configuration for a Spring Boot 3.3 /
Spring Security 6.1 service.** Note every `// WHY` comment — the *what* is obvious, the
*why* is what a reviewer asks for.

```java
package com.example.orders.config;

import java.time.Duration;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ResourceServerConfig {

    /** The service id THIS resource server answers to. Tokens for other services must not work here. */
    private static final String THIS_SERVICE = "orders-api";

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        http
            // WHY stateless: the whole point of a JWT access token is that no server state
            // is needed to verify it. HttpSession would defeat the entire design.
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(csrf -> csrf.disable()) // WHY safe here: no cookies are used for API auth.
                                      //       If a refresh token lives in a cookie, re-enable CSRF
                                      //       on that endpoint (chapter 02).
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                    .requestMatchers("/api/admin/**").hasAuthority("ROLE_ADMIN")
                    .anyRequest().authenticated())
            // WHY oauth2ResourceServer: this registers the BearerTokenAuthenticationFilter
            // and the 401/403 handlers in the right positions in the chain.
            .oauth2ResourceServer(oauth2 -> oauth2
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(converter)));

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri) {

        // WHY withJwkSetUri (and not withIssuerLocation): an explicit jwk-set-uri lets the
        // decoder start without a network round trip to the issuer, and it makes the key
        // source auditable. withIssuerLocation() is the alternative when you want the
        // OIDC discovery document to drive everything.
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                // WHY pin the algorithm: never let the token's own header choose the
                // verification algorithm (algorithm-confusion + `alg: none`).
                .jwsAlgorithm(org.springframework.security.oauth2.jose.jws.SignatureAlgorithm.RS256)
                .build();

        // WHY a 60 s clock-skew leeway: the issuer and the verifier will never have
        // perfectly synchronised clocks, but 60 s is small enough not to extend the
        // token's real lifetime in any meaningful way.
        OAuth2TokenValidator<Jwt> withIssuer = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(60)),
                new JwtIssuerValidator(issuer));

        // WHY aud matters: this is the confused-deputy defence. A token minted for
        // billing-api must not authenticate a call to orders-api.
        OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
                JwtClaimNames.AUD, aud -> aud != null && aud.contains(THIS_SERVICE));

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        // WHY an explicit prefix: the default is SCOPE_, and ROLE_ is what
        // .hasRole("ADMIN") / hasAuthority("ROLE_ADMIN") expect.
        authorities.setAuthorityPrefix("ROLE_");
        // WHY an explicit claim name: do not rely on the default ("scope" or "scp")
        // unless you know exactly what the issuer puts there.
        authorities.setAuthoritiesClaimName("roles");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        // WHY "sub": the default principal name is "sub" already, but being explicit
        // documents the contract and survives a change in the issuer's claims.
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
```

**The matching application properties.**

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          # WHY both: issuer-uri is used by JwtValidators/JwtIssuerValidator, jwk-set-uri
          # tells the decoder where to fetch the signing keys. Pinning both is explicit
          # and avoids an implicit discovery call at startup.
          issuer-uri: https://auth.acme.com
          jwk-set-uri: https://auth.acme.com/.well-known/jwks.json

logging:
  level:
    # WHY: this logger prints the exact reason a token was rejected. Turn it on in
    # a non-prod environment when a 401 surprises you.
    org.springframework.security.oauth2: DEBUG
```

**The issuer side — an `HS256` encoder for a single service.**

```java
package com.example.auth.config;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class TokenIssuerConfig {

    /**
     * WHY a 32-byte secret and not a password: this is a symmetric key, so its length
     * IS its security. A human-chosen passphrase is a weak key. In production this comes
     * from a secret manager (Vault, AWS Secrets Manager, Spring Cloud Config) — never
     * from application.properties.
     */
    @Bean
    SecretKey jwtSecretKey(@Value("${security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "security.jwt.secret must be at least 32 bytes for HS256 (got " + bytes.length + ")");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtSecretKey) {
        // WHY the algorithm is pinned here too: the encoder and the decoder must agree.
        return NimbusJwtEncoder.withSecretKey(jwtSecretKey)
                .algorithm(MacAlgorithm.HS256)
                .build();
    }
}
```

**A service that mints tokens for *many* verifiers — `RS256` with a JWKS endpoint.**
Because an RSA key pair means the verifier holds only the public key, you can publish it.

```java
package com.example.auth.config;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;

@Configuration
public class RsaIssuerConfig {

    /**
     * WHY Nimbus directly instead of NimbusJwtEncoder: when the issuer owns BOTH halves of
     * the RSA key pair, Nimbus's RSASSASigner needs the private key, and publishing the
     * public half as a JWKS is a Nimbus concept anyway.
     */
    @Bean
    JwtEncoder rsaJwtEncoder(
            @Value("${security.jwt.rsa.private-key}") String privateKeyPem,
            @Value("${security.jwt.rsa.key-id}") String keyId) throws Exception {

        RSAKey rsaKey = RsaKeys.fromPem(privateKeyPem, keyId);
        RSASSASigner signer = new RSASSASigner(rsaKey.toRSAPrivateKey());

        return parameters -> {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyId)
                    .type(com.nimbusds.jose.JOSEObjectType.JWT)
                    .build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer("https://auth.acme.com")
                    .subject(parameters.getClaims().getSubject())
                    .audience(parameters.getClaims().getAudience())
                    .issueTime(java.util.Date.from(parameters.getClaims().getIssuedAt()))
                    .expirationTime(java.util.Date.from(parameters.getClaims().getExpiresAt()))
                    .jwtID(parameters.getClaims().getId())
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(signer);
            return new org.springframework.security.oauth2.jwt.Jwt(
                    jwt.serialize(),
                    jwt.getJWTClaimsSet().getClaims(),
                    jwt.getHeader().toJSONObject());
        };
    }

    /** Serves the PUBLIC key only. Never expose the private key. */
    @Bean
    JWKSet publicJwkSet(@Value("${security.jwt.rsa.public-key}") String publicKeyPem,
                         @Value("${security.jwt.rsa.key-id}") String keyId) throws Exception {
        return new JWKSet(RsaKeys.fromPem(publicKeyPem, keyId).toPublicJWK());
    }
}
```

> 📌 **Note on the `Jwt` constructor.** The three-argument `Jwt(String tokenValue,
> Map<String,Object> claims, Map<String,Object> headers)` exists in Spring Security 6 and is
> the right escape hatch when you need a signing scheme the default encoder does not cover.
> Most services will never need it — `NimbusJwtEncoder` covers HMAC cleanly.

---

## 10. Common Mistakes & Gotchas

### ❌ Mistake 1 — Treating the payload as secret

```java
// ❌ PII in the payload — readable by anyone who sees the token
.claim("email", "aarav@example.com")
.claim("nationalId", "4821-9933-1122")
```
**✅ right.** Put identifiers, not personal data. The bearer can read it; logs can read it;
an APM trace can read it.

```java
// ✅ A stable identifier + a reference to the data the API can fetch
.claim("sub", "user-42957")
.claim("tenant_id", "t_9931")
```

---

### ❌ Mistake 2 — Not pinning the algorithm

```java
// ❌ The token chooses the algorithm → algorithm confusion + `alg: none`
NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey).build();
```
**✅ right.** Always name the algorithm.

```java
NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(publicKey)
        .signatureAlgorithm(SignatureAlgorithm.RS256)
        .build();
```

---

### ❌ Mistake 3 — Validating `iss` but forgetting `aud`

```java
// ❌ Any token from our issuer works on any of our services
decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("https://auth.acme.com"));
```
**✅ right.** Add the audience validator.

```java
OAuth2TokenValidator<Jwt> withAudience = new JwtClaimValidator<List<String>>(
        JwtClaimNames.AUD, aud -> aud != null && aud.contains("orders-api"));
decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
```

---

### ❌ Mistake 4 — Using standard base64

```java
// ❌ '+' and '/' break URLs, '=' breaks header parsing
String encoded = Base64.getEncoder().encodeToString(json);
```
**✅ right.**

```java
String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(json);
```

---

### ❌ Mistake 5 — An access token that lives for a day

```java
// ❌ 24-hour access token = 24 hours of unrevocable authority after a leak
.expiresAt(now.plus(24, ChronoUnit.HOURS))
```
**✅ right.** 5–15 minutes for the access token; put the long life in the refresh token.

---

### ❌ Mistake 6 — Mapping every claim to an authority

```java
// ❌ ANY claim becomes a privilege — a mis-mapped claim is an escalation
converter.setJwtGrantedAuthoritiesConverter(jwt -> jwt.getClaims().entrySet().stream()
        .map(e -> new SimpleGrantedAuthority(e.getKey().toUpperCase()))
        .toList());
```
**✅ right.** Map the claims you expect, by name, with an explicit prefix.

```java
JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
authorities.setAuthoritiesClaimName("roles");
authorities.setAuthorityPrefix("ROLE_");
```

---

### ❌ Mistake 7 — Trusting the client's `X-Tenant-Id` header

```java
// ❌ Any caller can claim any tenant
String tenantId = request.getHeader("X-Tenant-Id");
```
**✅ right.** The tenant comes from the **validated** claim.

```java
String tenantId = jwt.getClaimAsString("tenant_id");
```

---

### ❌ Mistake 8 — Not handling the `kid` during rotation

```java
// ❌ One hard-coded public key → the first rotation breaks every live token
NimbusJwtDecoder.withPublicKey(publicKey)
```
**✅ right.** Use the JWKS so the decoder can hold **old and new** keys simultaneously.

```java
NimbusJwtDecoder.withJwkSetUri(jwkSetUri).jwsAlgorithm(SignatureAlgorithm.RS256).build();
```

---

### ❌ Mistake 9 — Treating "stateless" as "no storage anywhere"

```java
// ❌ "JWTs are stateless, so we don't need a refresh-token table"
//    → no revocation, no reuse detection, no device management, no logout-everywhere
```
**✅ right.** Statelessness describes the **access-token verification path**. The refresh
path is stateful by design — that is where the revocable power lives.

---

### ❌ Mistake 10 — A leeway of 10 minutes

```java
// ❌ 'exp' + 600 s  →  the effective TTL is 25 minutes, not 15
new JwtTimestampValidator(Duration.ofMinutes(10))
```
**✅ right.** 60 seconds is the standard. State the number and the reason.

---

### ❌ Mistake 11 — Forgetting that `aud` may be a bare string

```java
// ❌ Only handles the array form
String aud = jwt.getAudience().get(0);
```
**✅ right.** `Jwt.getAudience()` **always** returns a `List<String>` — Spring normalises the
string form for you. Use the list API and never index into it blindly.

---

## 11. Performance Considerations

**The hot path: verifying one access token.**

| Step | Cost | Notes |
|---|---|---|
| Split on `.` + base64url-decode 2 segments | ~1–3 µs | Negligible |
| Parse the header + payload JSON | ~5–15 µs | Jackson, on small documents |
| JSON **web token** parse (Nimbus) | ~10–30 µs | Nimbus's own parser, not Jackson |
| RSA-2048 **signature verify** | ~50–150 µs | Public-key operation — fast |
| RSA-2048 **sign** (issuer side) | ~700–1,200 µs | Private-key operation — ~10× slower |
| ECDSA P-256 verify | ~40–80 µs | |
| ECDSA P-256 sign | ~50–100 µs | |
| **Ed25519 sign / verify** | ~20 µs / ~50 µs | Fastest modern option |
| HMAC-SHA256 sign = verify | ~1–5 µs | Symmetric — by far the cheapest |
| `JwtValidators` chain | ~1 µs | Pure comparisons |

**The headline numbers.**

- **Verification is ~0.1 ms** with RSA-2048 — that is the entire per-request cost of a
  stateless API. A database lookup for a session is **0.3–2 ms** on a same-region RDS
  instance, and **0.5–5 ms** with a cross-AZ Redis hop. **That 2–50× difference is the
  entire economic argument for JWTs.**
- A typical access token is **700–1,200 bytes** (RSA) or **350–600 bytes** (HMAC/ES256).
  At 10,000 req/s that is 7–12 MB/s of token bytes crossing the wire — measurable, and a
  reason to prefer `ES256`/`EdDSA` for high-volume paths.
- The **JSON parse cost scales with the claim count**, not the payload size. 40 claims
  roughly double the parse cost and can push the token over a proxy's header limit.

**The JWKS cache is the performance feature you forget.** `NimbusJwtDecoder.withJwkSetUri(...)`
**caches** the JWK Set in memory. By default Spring Security refreshes the cache when it
sees an **unknown `kid`**, and on a **scheduled refresh** (`jwkSetUri` + `cache ttl`).
This means the hot path is **zero network calls** — which is why a JWKS endpoint being
slow does not slow down your API.

> ⚠️ **The known footgun.** `RemoteJWKSet` will do a **blocking network call** when it
> encounters an unknown `kid` — the cache miss path. An attacker who sends tokens with
> random `kid`s can therefore force a **JWKS fetch per request** (a cache-miss DoS).
> Mitigations: (a) rate-limit `/api/**` at the gateway, (b) keep the `kid` space small
> and validate the `kid` format, (c) use a JWKS endpoint behind a CDN.

**What to actually measure.**

| Metric | Where | Target |
|---|---|---|
| `jwt.verification.duration` | Micrometer timer around the decoder | p99 < **0.5 ms** |
| `security.oauth2.authorizations` | Spring Boot actuator metric | watch the **401 rate** |
| Token size distribution | Log `jwt.getTokenValue().length()` at a sample point | p99 < **1,500 bytes** |
| JWKS cache hit ratio | `RemoteJWKSet` / your own metrics | > **99.9%** |
| 401 rate by reason | Split by `invalid_token` vs missing header | a spike = a broken deploy or a key rotation bug |

**The tuning knobs.**

| Knob | Default | Change it when |
|---|---|---|
| Access-token TTL | 10 min | Lower (5 min) for high-value APIs; higher (30 min) only for low-privilege, high-volume APIs |
| Signing algorithm | `RS256` | `ES256`/`EdDSA` when token size or CPU matters; `HS256` when there is exactly one verifier |
| RSA key size | 2048 bit | 3072 for regulated environments; 4096 only if a compliance rule demands it (it costs ~4× the sign time) |
| Clock-skew leeway | 60 s | Lower (10 s) if you control both clocks; never higher than 60 s |
| JWKS cache TTL | ~5 min (library default) | Lower if you rotate keys often; align with your rotation runbook |
| Claim count | — | Keep < 20; use role **IDs** and resolve them server-side |

**The one performance trap that is really a design trap.** Putting a **large claim list**
(e.g. 200 permission strings) in the token makes verification slower *and* the token
bigger *and* every request's bytes more expensive. The right pattern is: put the **role
IDs** in the token, and resolve them to permissions **once, at the edge**, caching the
resolution — or push the permission check into the database where the data already lives.

---

## 12. Security Considerations

**The threat model for a JWT resource server.**

| Threat | Attack | The Spring-specific defence |
|---|---|---|
| **Forgery** | The attacker crafts a token | The signature; the key never leaves the issuer. Pin the algorithm. |
| **Algorithm confusion** | `alg: HS256` + the public key as the HMAC secret | Pin the algorithm **in the decoder config** — never read `alg` from the header |
| **`alg: none`** | An unsigned token | Pin the algorithm; Nimbus then rejects `none` outright |
| **Claim forgery** | The attacker edits the payload | Any byte change breaks the signature. **But** only if you actually verify the signature before trusting the claims |
| **Replay** | A captured token is re-used | The TTL bounds it. `jti` + a denylist for high-value flows |
| **Wrong audience** | A token for `billing-api` used at `orders-api` | Validate `aud` (`JwtClaimValidator`) |
| **Wrong issuer** | A token from a partner IdP | Validate `iss` (`JwtIssuerValidator`) |
| **Never expires** | `exp` removed or set far in the future | **Require** `exp`; a missing `exp` must be a failure, not a pass |
| **Token leakage** | The token ends up in a log, a URL or `localStorage` | Bearer in the header only; redact `Authorization` in logs; `HttpOnly` cookies for the refresh token |
| **Privilege escalation via claims** | A mis-mapped claim becomes an authority | An explicit claim→authority mapping |
| **DoS via large tokens** | A 1 MB token with deeply nested JSON | Cap the request/header size; validate the signature before heavy parsing; rate-limit |
| **JWKS spoofing** | The `jwk_set_uri` is pointed at an attacker | Pin the JWKS URI over TLS; do not accept a `jwk_set_uri` from the token itself |

**The rules that are not negotiable.**

1. **Pin the algorithm.** `NimbusJwtDecoder.withSecretKey(k).macAlgorithm(...)` or
   `.withPublicKey(k).signatureAlgorithm(...)`. Never leave it to the token.
2. **Validate `iss` and `aud`.** Always. `JwtValidators.createDefaultWithIssuer(...)` plus a
   `JwtClaimValidator` for the audience.
3. **Require `exp`, and require `exp` to be in the future.** A token with no `exp` must be
   rejected — some libraries will happily accept it otherwise.
4. **Keep the access token short-lived.** 5–15 minutes. The TTL is your revocation window.
5. **No PII in the payload.** It is base64, not encryption.
6. **Do not put the token in a URL.** Query strings land in access logs, browser history and
   `Referer` headers.
7. **Redact `Authorization` in logs.** A `log.info("req {}", request.getHeader("Authorization"))`
   is a credential leak with a log line attached.
8. **Keep the signing key out of the code.** A secret in `application.properties` in git is
   a compromised secret.
9. **Rotate keys with an overlap window.** See §6.4 and chapter 03.
10. **Rate-limit the token endpoints**, not just the API. `/refresh` is a password-equivalent
    endpoint.
11. **Use a ≥256-bit secret / ≥2048-bit RSA key.** A 128-bit HS256 secret is forgeable.
12. **Do not trust any header the client sends** for identity or tenancy — `X-Tenant-Id`,
    `X-User-Id` are caller-controlled.

**The `WWW-Authenticate` header — a security-relevant detail.** RFC 6750 §3 says the
resource server **must** include `WWW-Authenticate` on a 401 so the client can distinguish
"your token expired, refresh it" from "your token is wrong, re-authenticate". Spring's
`BearerTokenAuthenticationEntryPoint` does this. A hand-rolled `AuthenticationEntryPoint`
that drops it will break every client library.

**What the JWT cannot do — and must not be asked to.**

- **"Is this user currently banned?"** — needs live state.
- **"Log this user out right now."** — needs a denylist or a token version (chapter 03).
- **"Show me this user's current permissions."** — the token holds a *snapshot* from up to
  15 minutes ago. If permission changes matter, check the database.
- **"Was this token leaked?"** — you cannot know without storing it (which is the
  `jti`/denylist trade-off).

> 💡 **The expert framing.** A JWT is a **cached, self-contained authorisation decision
> that you have chosen to serve for a bounded period**. Every design question about JWTs
> reduces to: *how long am I willing to serve a stale decision, and what is my plan when
> the decision must change before then?*

---

## 13. Best Practices

**The rules a code reviewer would enforce on a JWT resource server.**

1. **The decoder is a `@Bean` with an explicitly pinned algorithm.** Never a field-initialised decoder.
2. **A validator chain, always.** At minimum: timestamp + issuer. Add audience.
3. **`exp` is mandatory.** A validator that rejects a token with no `exp`.
4. **Access token TTL ≤ 15 minutes.** State it in the config, not in a comment.
5. **The signing key comes from a secret manager**, never from `application.properties` in git.
6. **HS256 secret ≥ 32 bytes**; RSA ≥ 2048 bits; ES256 = P-256; EdDSA = Ed25519.
7. **A `kid` on every token** and a JWKS with ≥2 keys during rotation.
8. **The JWKS is served over TLS** and cached by the decoder.
9. **Claim→authority mapping is explicit and by name.** Never "map everything".
10. **`typ` is checked** if you accept more than one token type.
11. **`SessionCreationPolicy.STATELESS`** — otherwise you have a session *and* a JWT.
12. **CSRF disabled only where there is no cookie-based auth.** If the refresh token is in a cookie, protect that endpoint.
13. **No token in a URL, ever.**
14. **`Authorization` headers are redacted** in the access log and in the MDC.
15. **The 401 body is JSON** with a `WWW-Authenticate` header.
16. **The 401 and 403 bodies are different.** 401 = not authenticated; 403 = authenticated but not authorized.
17. **PII is not in the payload.**
18. **The clock-skew leeway is 60 s** and is written down.
19. **A failing `aud` check is logged as a distinct metric** from a signature failure — they are different incidents.
20. **Key rotation is a runbook**, not a hope. See chapter 03 §8.3.
21. **The token issuer is a separate deployable** (or at least a separate module) from the resource servers.
22. **Token lifetimes are pinned in `STANDARD.md`-style tables** and reviewed when they change.

---

## 14. Hands-On Exercises & Code Challenges

### Exercise 1 — Decode a token by hand, all the way down

**Steps.**
1. Take any JWT (issue one from a unit test, or paste one from the docs).
2. Split it on `.` and count the parts. Confirm there are exactly **3**.
3. Base64url-decode part 1 and part 2 and pretty-print the JSON.
4. Find `exp`, convert it from epoch seconds to a readable local timestamp, and compare it
   with `date +%s` on the same machine.
5. Compute the **remaining lifetime** and check it against the intended TTL.

**Acceptance criteria.**
- You can state, in one sentence, why the payload needs no key to read.
- You can compute `exp - iat` and confirm it equals the configured TTL.
- You can explain what would happen if the `+`/`/` characters appeared in the token.

**Stretch goal.** Write a 20-line script that takes a token and prints a table of
`iss`, `sub`, `aud`, `exp`, `iat`, `jti`, remaining TTL, and the **key size** implied by
the `alg`.

---

### Exercise 2 — Build a `JwtDecoder` with a JWKS URI and prove the `kid` lookup works

**Steps.**
1. Generate an RSA key pair and expose the public half at
   `GET /.well-known/jwks.json` in the shape `{"keys":[ {...} ]}`.
2. Configure `NimbusJwtDecoder.withJwkSetUri(...)` with a pinned `RS256`.
3. Mint a token with a `kid` that **is** in the JWKS → it validates.
4. Mint a token with a `kid` that is **not** in the JWKS → it fails.
5. Add the new key to the JWKS **while the decoder is running** and re-send the
   previously-failing token → it now succeeds (this is the cache-miss refresh path).

**Acceptance criteria.**
- You can explain the exact log line for the unknown-`kid` case.
- You can state the library's default cache-refresh behaviour.
- You can show the JWKS fetch happening **only once**, on the miss.

**Stretch goal.** Change the JWKS URI to a bad host and prove that **already-cached** keys
keep working (the cache is the resilience mechanism).

---

### Exercise 3 — Write a validator that rejects the wrong `aud`

**Steps.**
1. Write a `JwtClaimValidator<List<String>>` that requires the audience to contain
   `orders-api`.
2. Mint three tokens: one with `aud: ["orders-api"]`, one with `aud: "orders-api"` (a
   **bare string**), one with `aud: ["billing-api"]`.
3. Decode all three and record the outcome.
4. Add a test that asserts tokens 1 and 2 pass and token 3 fails.

**Acceptance criteria.**
- The bare-string form **passes** — proving `Jwt.getAudience()` normalises it.
- The wrong-audience token fails with `invalid_token`.
- You can explain why "the audience is non-empty" is **not** a sufficient check.

**Stretch goal.** Add a validator that rejects a token whose `typ` is not `at+jwt`, and
explain how it would have prevented a confused-deputy attack.

---

### Code Challenge 1 — A hardened decoder that survives the attack suite

**Problem.** Build a `JwtDecoder` that rejects every token in this list: `alg: none`,
`alg: HS256` signed with the public key, a valid signature with the wrong `aud`, a valid
signature with no `exp`, a valid signature with `exp` in the past, and a valid signature
with `nbf` in the future.

**Acceptance criteria.**
- One `@Bean JwtDecoder` with a pinned `RS256` algorithm.
- A `DelegatingOAuth2TokenValidator` chain containing: `JwtTimestampValidator`,
  `JwtIssuerValidator`, an `aud` `JwtClaimValidator`, and a custom validator that
  **rejects a token with no `exp`**.
- A `@SpringBootTest` with **six** test methods, one per attack, each asserting a
  `JwtValidationException`.
- A seventh test proving a **valid** token still decodes — the negative case that stops
  you from shipping a decoder that rejects everything.
- The exception message is asserted, not just the exception type.

**Stretch goal.** Wrap the decoder in a custom `JwtDecoder` decorator that also consults
a denylist (chapter 03) — and prove the two compose in the right order (signature first,
then revocation).

---

### Code Challenge 2 — A claims→authority mapper that maps *only* what it should

**Problem.** Implement a `Converter<Jwt, Collection<GrantedAuthority>>` that maps the
`roles` claim to `ROLE_*` authorities and the `scope` claim to `SCOPE_*` authorities, and
**ignores everything else**.

**Acceptance criteria.**
- A token with `{"roles":["ADMIN"],"admin":true,"is_superuser":true}` produces **exactly
  one** authority: `ROLE_ADMIN`.
- `scope: "orders:read"` produces `SCOPE_orders:read`.
- Duplicate authorities are removed (use a `LinkedHashSet`).
- A `roles` claim that is a **string** (not an array) does not blow up.
- A unit test asserts the exact authority set for each case.

**Stretch goal.** Add a `JwtAuthenticationConverter` subclass that logs (at DEBUG) every
claim it *ignored* — so that a mis-mapped claim shows up in the logs before it shows up as
an escalation.

---

## 15. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: What is a JWT?**
**A:** A JSON Web Token is a compact, URL-safe token that carries a set of claims
(header + payload) plus a signature that proves the token was issued by the party that
holds the signing key. It is defined by RFC 7519. The key property is that a verifier can
check the signature **locally** with a public key — no call back to the issuer — which is
what makes horizontally-scaled APIs possible.

**Q: Is a JWT encrypted?**
**A:** No, and this is the most common misconception. A standard JWT is a **JWS** — it is
*signed*, not *encrypted*. The header and payload are **base64url-encoded**, which is
encoding, not encryption: anyone holding the token can decode and read every claim. If
you need confidentiality, the token must be a **JWE** (RFC 7516) — but then only key
holders can read it, which defeats the purpose of a self-contained access token.

**Q: What are the three parts of a JWT?**
**A:** `header.payload.signature`, each part base64url-encoded. The header says *how to
verify* (`alg`, `kid`, `typ`); the payload carries the claims; the signature is computed
over `base64url(header) + "." + base64url(payload)`. Because the header is inside the
signed input, you cannot change the `alg` after signing without invalidating the
signature.

**Q: Why base64url and not base64?**
**A:** Because standard base64 emits `+`, `/` and `=`. The `+` becomes a space when it
appears in a query string, the `/` is treated as a path separator in a URL, and the `=`
padding breaks strict header-value parsing. base64url swaps them for `-` and `_` and
omits the padding. JWTs travel in `Authorization` headers and sometimes in URLs, so
they must use the URL-safe alphabet.

**Q: What are the main claims in a JWT?**
**A:** The seven registered claims: `iss` (issuer), `sub` (subject), `aud` (audience),
`exp` (expiry), `nbf` (not before), `iat` (issued at) and `jti` (a unique id). Everything
else is a custom/private claim — `role`, `scope`, `tenant_id` and so on. The important part
is not the list; it is that each must be **validated**, and that a missing `exp` must be
treated as invalid.

**Q: How is a JWT different from a session cookie?**
**A:** A session cookie is an **opaque key** into server-side state — the server must look
it up on every request. A JWT is **self-contained** — the server verifies it locally with
a key. The trade-off is that a session can be invalidated instantly, while a signed JWT
cannot be un-signed and stays valid until it expires, unless you keep revocation state.

---

### 🟡 Intermediate

**Q: Explain the Spring Security filter chain path for a JWT request.**
**A:** `BearerTokenAuthenticationFilter` runs at position 8 in the standard chain. It calls
the `BearerTokenResolver` (by default `DefaultBearerTokenResolver`) to pull the token out
of the `Authorization: Bearer` header. If a token is present it calls the `JwtDecoder`
(`NimbusJwtDecoder`), which verifies the signature against the JWK Set and runs the
`OAuth2TokenValidator` chain. On success the result is converted by a
`JwtAuthenticationConverter` into a `JwtAuthenticationToken` and pushed into
`SecurityContextHolder`. `AuthorizationFilter` then makes the final authorization
decision. Any failure before that point is a **401**; only an authorization failure after
it is a **403**.

**Q: What is the difference between HS256 and RS256?**
**A:** HS256 is symmetric — one shared secret signs *and* verifies. RS256 is asymmetric —
the issuer keeps the private key and the verifier holds only the public key. The
consequence is about **key distribution**, not raw security: with HS256 every verifier
can also *mint* tokens, so all verifiers must be inside the trust boundary; with RS256
the public key can be published at a JWKS endpoint, so verifiers can sit outside the
trust boundary. Use HS256 when one service both issues and verifies.

**Q: Why validate the `aud` claim?**
**A:** Because a token can be valid *somewhere else*. If `billing-api` and `orders-api`
share an issuer, a token minted for `billing-api` is cryptographically perfectly valid —
but it should not authorise a call to `orders-api`. Validating `aud` is the
**confused-deputy** defence: the token must name the service that is accepting it. The
validation is `aud` contains this service's id, and `aud` may be a string *or* an array
in the token (Spring normalises both to a `List<String>`).

**Q: What is a `kid` and why do you need it?**
**A:** `kid` is the **key id** — it says which key in the JWK Set signed this token. It
exists for **rotation**: without it, the verifier has to try every key, and during a
rotation with both old and new keys live, it is ambiguous which key was used. With a
`kid`, the verifier does one lookup. The rotation pattern is: publish the new public key
alongside the old one, start signing with the new key, wait at least the **maximum token
lifetime**, then remove the old key.

**Q: What happens if the token's `exp` has passed?**
**A:** `JwtTimestampValidator` rejects it, `NimbusJwtDecoder` throws a
`JwtValidationException`, `BearerTokenAuthenticationFilter` lets it propagate and the
`ExceptionTranslationFilter` invokes the `AuthenticationEntryPoint` → **401** with a
`WWW-Authenticate: Bearer error="invalid_token"` header. The client is expected to use
its refresh token to obtain a new access token. The validator allows a **60-second
clock-skew leeway** by default.

**Q: How do you configure a `JwtDecoder` for a JWKS endpoint?**
**A:** `NimbusJwtDecoder.withJwkSetUri("https://auth.acme.com/.well-known/jwks.json").jwsAlgorithm(SignatureAlgorithm.RS256).build()`,
then `decoder.setJwtValidator(...)` with the issuer and audience validators. The
important parts are: **pin the algorithm**, **set the validator chain**, and remember that
the JWK Set is cached — the hot path makes no network call.

**Q: What is the `BearerTokenAuthenticationFilter` doing if there is no `Authorization` header?**
**A:** It does nothing at all — it calls `chain.doFilter()` and the request continues
**unauthenticated**. That is deliberate: the filter must not reject anonymous requests
because many endpoints are public. The 401 is produced later, by
`AuthorizationFilter` → `ExceptionTranslationFilter`, because the route requires
authentication. A failed *authentication* (a bad token) is also a 401, via
`BearerTokenAuthenticationEntryPoint`.

---

### 🔴 Advanced / Senior

**Q: What is the algorithm-confusion attack and how do you prevent it?**
**A:** The server publishes an RSA public key. The attacker sets `"alg":"HS256"` in the
token header and signs the token using the **public key's bytes as the HMAC secret** —
which they can do, because the public key is public. If the verifier reads `alg` from the
header and dispatches on it, it verifies with `HMAC(publicKeyBytes, …)` and the forged
token passes. The defence is to **never read the algorithm from the token**: pin it in
the decoder configuration (`withPublicKey(k).signatureAlgorithm(SignatureAlgorithm.RS256)`).
With a pinned algorithm, the `HS256` token is rejected before any key lookup happens. The
same pinning also kills the `alg: none` attack.

**Q: You said JWTs are stateless. Then how do you implement "log out everywhere"?**
**A:** Statelessness applies to the **access-token verification path**, not to the system.
To log out everywhere you keep a **per-user token version** (a counter) in Redis or the
database, put that counter in the token as a `ver` claim, and compare it on every request:
one lookup, and a mismatch means the token is dead. Incrementing the counter invalidates
**every** outstanding access token for that user in one write. It is O(1) per request and
O(1) to revoke — far better than a denylist of individual `jti`s. The denylist is the
complementary tool when you need to revoke **one** token rather than all of them.

**Q: How do you rotate signing keys without breaking every live token?**
**A:** With an **overlap window**. Step 1: generate the new key pair. Step 2: publish the
new **public** key in the JWKS **alongside** the old one — both keys are live, and the
decoder can verify either. Step 3: start signing with the new key. Step 4: wait for at
least the **maximum access-token lifetime** (plus clock skew) so that every token signed
with the old key has expired. Step 5: remove the old key from the JWKS. If you skip step
4 you get a 401 storm, because tokens signed with the removed key can no longer be
verified — even though they are perfectly valid.

**Q: Why not just use opaque tokens with a database lookup?**
**A:** Because you trade the network hop for revocation. With an opaque token you must
look it up on **every** request (0.3–2 ms on a same-region database) — that is exactly
the cost the JWT removes. Opaque tokens are right when revocation accuracy matters more
than latency: high-value banking operations, or OAuth2 **introspection** (RFC 7662). The
hybrid — a short-lived JWT access token plus an opaque, server-side refresh token — gets
you both: a cheap hot path and accurate revocation where it actually matters.

**Q: How do you map JWT claims to Spring Security authorities?**
**A:** With a `JwtAuthenticationConverter`, which by default reads the `scope` claim and
prefixes each value with `SCOPE_`. You customise it with
`setJwtGrantedAuthoritiesConverter(...)`. The danger is a **blanket mapping** — turning
every claim into an authority — because then any claim that an attacker can influence, or
that the issuer names badly, becomes a privilege. The correct approach is an explicit,
named mapping: `roles` → `ROLE_*`, `scope` → `SCOPE_*`, and everything else ignored.

**Q: What is clock skew and how do you handle it?**
**A:** The issuer's clock and the verifier's clock are never perfectly aligned —
containers drift, NTP corrections happen, VMs pause. `JwtTimestampValidator` allows a
**default 60-second** leeway on both `exp` and `nbf`, so a token that expired 30 seconds
ago is still accepted. 60 seconds is the right number: enough to absorb real drift, small
enough that it does not meaningfully extend the token's life. You can change it with
`new JwtTimestampValidator(Duration.ofSeconds(60))`, and you should — explicitly — so the
number is visible in the code rather than implied by a library default.

---

### ⚫ Expert / Principal

**Q: Explain exactly what "stateless" means in a JWT system, and where the state lives.**
**A:** Statelessness is a property of the **access-token verification path only**. Verifying
an access token requires a public key and a CPU — no database, no cache, no session store.
That is what lets any API node verify any token, and it is the economic argument for JWTs.
But the *system* is decidedly stateful: refresh-token rows (hashed, with a family id), the
denylist, the per-user token-version counter, the `jti` bookkeeping and the JWKS cache are
all server state. The precise formulation is: **the access token is a stateless bearer of
a snapshot of authority; the refresh token is the stateful, revocable root of trust.**
That split is why the API hot path is stateless while the login and refresh paths are not.

**Q: What are the real trade-offs of RS256 versus ES256 versus EdDSA, beyond "which is stronger"?**
**A:** Three dimensions. **Key size and token size**: RSA-2048 needs a 256-byte signature
(342 base64url characters) while P-256 and Ed25519 need 64 bytes (86 characters) — a
4× difference in token size, which matters at 10k+ req/s and on mobile. **CPU cost on the
signing side**: RSA-2048 signs in ~700–1,200 µs because it is a private-key operation,
whereas Ed25519 signs in ~20 µs and P-256 in ~50–100 µs — a 10–40× difference that
dominates a high-rate issuer. **Interoperability**: RSA is universal, ECDSA needs the raw
`r‖s` ↔ DER conversion in Java (Nimbus handles it, but a hand-rolled verifier will
break), and EdDSA is the newest, so it is the least widely supported by non-Java clients.
My default for a new system is **EdDSA** (or ES256 if the ecosystem requires EC), and RS256
only where RSA is mandated.

**Q: How would you defend against a JWKS-cache-miss DoS?**
**A:** `NimbusJwtDecoder` with `withJwkSetUri` uses Nimbus's `RemoteJWKSet`, which caches
the key set — but on an **unknown `kid`** it performs a **blocking fetch** to the JWKS
endpoint. An attacker who sends requests with random `kid` values forces a fetch per
request, turning your API into a load generator against your own JWKS endpoint. The
mitigations are layered: rate-limit at the gateway (the cheapest and most effective),
validate the `kid` **format** before the lookup so random strings never reach the
fetcher, keep the `kid` space small and structured (e.g. `key-<yyyy-mm>`), and put the
JWKS endpoint behind a CDN. I would also alert on the JWKS request rate — a spike is a
strong attack signal.

**Q: How do you handle the JWKS endpoint being down?**
**A:** The decoder's cache is the resilience mechanism: once the keys are cached, a JWKS
outage does not affect the hot path at all. The failure mode is only at **rotation** time
— the cache must refresh to pick up a new `kid`, and if the endpoint is down, tokens with
the new `kid` fail. So the runbook is: publish the new key and *verify it is reachable*
before you start signing with it; keep the old key in the JWKS until the overlap window
closes; and alert on JWKS fetch failures. The decoder's default behaviour on a
cache-miss fetch failure is to throw, which becomes a 401 for that token — which is the
correct fail-closed behaviour.

**Q: A security review says "your JWT is not really stateless — you have a denylist". Is that a fair criticism?**
**A:** It is factually right and conceptually confused. The criticism confuses
**statelessness of the hot path** with **statefulness of the system**. The right
framing is a cost model: state on the hot path costs you a network hop per request and a
new availability dependency; state off the hot path (login, refresh, revocation) costs
you nothing per request. That is why I put the state in the refresh-token store and the
token-version counter, and check the denylist only for long-lived tokens or behind a
short-TTL local cache with an accepted revocation delay. I would rather have a bounded
5-minute revocation delay than a per-request 2 ms tax on every API call.

**Q: Why not just put the permissions in the token and skip the authorisation query?**
**A:** Because the token is a **snapshot**, and snapshots go stale. If a permission is
revoked at 10:00 and the token was issued at 09:55 with a 15-minute TTL, the API will
keep honouring the revoked permission until 10:10. That is a deliberate trade-off and it
is acceptable for coarse-grained, low-privilege decisions — but not for anything
security-critical. The pattern I use: put **role IDs** in the token (small), and resolve
them against the authoritative store at the edge, cached with a short TTL, for anything
where staleness matters. The token then carries *identity* and the system carries
*authority*.

---

## 16. Scenario-Based & Real-Experience Scenarios

### Scenario 1 — "Everything started returning 401 after Tuesday's deploy"

**The situation.** Monday: 8,000 req/s, no auth errors. Tuesday 09:14, after a routine
deploy that changed one line in `application-prod.yml`, the error rate jumps to **31% of
all requests** and the on-call engineer is paged. Every failing request is a 401 with an
empty body.

**Your first move.** Do **not** start debugging the filter chain. Get the *actual* error.
Turn on the OAuth2 logger in a **non-prod** environment first, but in prod, look at the
actuator metrics and the access logs:

```bash
# 1. What is the 401 rate, and did it step-change at the deploy?
curl -s localhost:8080/actuator/metrics/security.oauth2.authorizations | jq '.measurements'
curl -s localhost:8080/actuator/metrics/http.server.requests | jq '.availableTags'

# 2. What changed in the deploy?
git diff HEAD~1 HEAD -- src/main/resources/application-prod.yml

# 3. Is the JWKS endpoint healthy? (the decoder fetches from here)
curl -sS -o /dev/null -w '%{http_code} %{time_total}s\n' https://auth.acme.com/.well-known/jwks.json

# 4. Is the clock right on this box? (skew → exp/nbf failures)
date -u; timedatectl status
```

**Expected signal.** `git diff` shows the `issuer-uri` changed from
`https://auth.acme.com` to `https://auth.acme.com/` (a **trailing slash**). The
`JwtIssuerValidator` compares the `iss` claim **exactly**, so `"https://auth.acme.com" !=
"https://auth.acme.com/"` → every token fails with `invalid_token`.

**The trap.** Chasing the *signature* — "maybe the JWKS is stale, maybe the key rotated?"
— and burning 40 minutes in the wrong place. The `WWW-Authenticate` header says
`invalid_token`; it does **not** say *which* claim failed, so you need the DEBUG log or
the diff to find it.

**The resolution.** Fix the issuer URI, and add the issuer to the config as a single
validated property so a typo cannot produce a *different* string from the token's `iss`.
Better still, validate the issuer value at startup.

**The lesson.** `iss` is a **string equality check**, not a URL comparison. Trailing
slashes, `http` vs `https`, and case in the host are all real production outages. Quote
the exact-match rule in your interview answer — it is a detail that separates a 3-year
candidate from a 5-year one.

---

### Scenario 2 — "A contractor in another country says the API always rejects their token"

**The situation.** A partner integration in Frankfurt reports that **their** tokens work
in staging but produce 401 in production. Their token has `exp` correctly 10 minutes in
the future *according to their clock*. The internal users of the same service are fine.

**Your first move.**

```bash
# 1. Reproduce with the partner's exact token and look at the server-side reason:
#    (DEBUG on org.springframework.security.oauth2 in a staging replica)
grep -c 'JwtTimestampValidator' app.log

# 2. Compare the two clocks:
date -u                                  # the API server
#    and, from the partner:
curl -s https://worldtimeapi.org/api/timezone/Etc/UTC | jq .utc_datetime

# 3. Check the leeway configured in the app:
grep -rn "JwtTimestampValidator\|clock-skew\|clockSkew" src/main/java/
```

**Expected signal.** The API server's clock is **2026-01-14 09:02:11 UTC** while the
partner's is **2026-01-14 08:41:03 UTC** — a **21-minute** difference, far beyond the
**60-second** leeway. The API server's NTP sync has silently failed (`timedatectl` shows
`System clock synchronized: no`, `NTP service: inactive`).

**The trap.** "The clock is wrong, so let me just increase the leeway to 30 minutes." That
**turns a 15-minute token into a 45-minute token** — a silent 3× increase in the
irrevocable window, on every token, for every user. The leeway is a *tolerance*, not a
*fix*.

**The resolution.** Fix the NTP configuration on the host (or in the container
orchestrator). Keep the leeway at 60 s. Add a startup assertion that the system clock is
within 5 seconds of a reference time source, and alert on `timedatectl` drift.

**The lesson.** Clock skew is a *deployment* problem masquerading as an *auth* problem.
And the leeway must stay small — quote "60 seconds" and the reason.

---

### Scenario 3 — "Security found an `Authorization` header in our access logs"

**The situation.** A security engineer flags that `nginx` is logging the full
`Authorization` header. The logs are shipped to a third-party SIEM, retained for 90 days,
and are readable by 40 engineers.

**Your first move.**

```bash
# 1. Prove it:
grep -c 'Authorization: Bearer' /var/log/nginx/access.log

# 2. Show the blast radius — how many DISTINCT tokens are in there?
grep -o 'Authorization: Bearer [A-Za-z0-9._-]*' /var/log/nginx/access.log | sort -u | wc -l

# 3. Check the retention window:
grep -rn "retention" /etc/logrotate.d/nginx /etc/logstash/pipeline/*.conf
```

**Expected signal.** 41,832 distinct tokens in the log. Every one of them is a valid
credential for up to **15 minutes** from the moment it was written. The log is a
**credential store** that nobody is treating as one.

**The trap.** "The tokens have expired by now" — true, but the *pattern* is the problem:
this is how a long-lived credential gets harvested. And if the access-token TTL were ever
raised to 24 hours, this log would be a **complete credential dump**.

**The resolution.** (1) Redact the header at the edge — nginx `log_format` with
`$http_authorization` replaced by a constant, or the `redact` module. (2) Scrub the
existing logs and shorten their retention. (3) Rotate the signing key, invalidating every
live token. (4) Add an automated check that greps the log pipeline for `Bearer`.

**The lesson.** A token is a **credential**, and every place it lands becomes part of your
credential-handling surface. The TTL bounds the damage; it does not make the leak
acceptable.

---

### Scenario 4 — "Our API accepts tokens from the old auth server that we decommissioned"

**The situation.** A legacy auth server was retired six months ago, but its signing key
was never removed from the JWKS "in case we needed to roll back". A penetration test
shows that the old key's tokens are still accepted — and the old key's secret was in a
deprecated wiki page.

**Your first move.**

```bash
# 1. What keys are actually live?
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys[] | {kid, alg, use, n: (.n[0:12])}'

# 2. Which keys have we actually signed with in the last 30 days?
#    (your issuer logs the kid with every mint — if you don't, start now)
```

**Expected signal.** The JWKS contains **3** keys: `key-2025-03` (retired, still live),
`key-2025-11` and `key-2026-01`. The issuer only signs with `key-2026-01` — the other
two are dead weight that still *validates*.

**The trap.** Deleting the keys and calling it done — because the **tokens signed with
the retired keys are still valid** until they expire, and worse, the retired key was
**publicly documented**. Removing the key from the JWKS stops *new* verifications, but
you should also rotate anything the compromised key could have signed.

**The resolution.** Remove the stale keys from the JWKS, then **rotate**: generate a fresh
key and issue it with a new `kid`, publish it, and invalidate the old ones. Treat any key
that has ever been in a wiki page as **compromised**.

**The lesson.** A JWKS is a **trust list**. Every entry in it is a key that can mint
tokens your whole fleet will accept. It needs the same hygiene as a firewall rule: an
owner, a justification, and an expiry date.

---

## 17. Counter-Questions, Tricky Questions & Follow-Ups

### CQ1. "Is JWT really better than a server-side session? When would you use a session?"

- **Naive answer:** "JWT is better because it's stateless and scalable."  ← *true, and it misses that sessions can be stateless-ish too*
- **The trap:** The trap is treating "stateless" as a property of the *technique* rather
  than of the *hot path*. A session stored in Redis is also horizontally scalable — you
  just pay a 0.5–2 ms lookup per request. The real question is whether that lookup is
  acceptable **and** whether you need the things only state gives you: instant
  revocation, live permission checks, and "log out everywhere".
- **Expert answer:** The decision is a **cost-and-capability** trade-off, not a
  modernity contest. Use a **session** when you need instant revocation, when your
  authorisation decisions depend on live state ("is this user banned?"), or when the
  client is a first-party web app where a cookie is the natural transport. Use a **JWT**
  when the API is consumed by mobile/third-party clients, when you want the API tier to
  be independently deployable and scaleable without a shared store, or when you are
  building a service mesh. The **hybrid** — a short-lived JWT access token plus an
  opaque, server-side refresh token — is the answer most production systems converge on,
  because it takes the cheap hot path and puts the stateful, revocable power where it
  costs nothing per request.

### CQ2. "If the payload is just base64, why bother with a signature at all?"

- **Naive answer:** "The signature proves the server issued it."  ← *true, but incomplete*
- **The trap:** The trap is thinking the signature's job is *confidentiality*. It is not —
  it is **integrity and authenticity**. Someone can read the claims; the signature is what
  stops them from *writing* claims. Without the signature, a client could set
  `"role":"ADMIN"` and the server would have no way to tell.
- **Expert answer:** The signature answers exactly one question: **"was this payload
  produced by someone who holds the signing key, and has it been modified since?"** That
  is the whole of a JWS. Confidentiality is a *different* problem solved by JWE (and,
  in practice, by TLS). The reason you do not encrypt the access-token payload is
  precisely that you want it readable by verifiers, gateways and logs without any shared
  secret — a signature gives you *authenticity without confidentiality*, which is the
  correct trade for a bearer credential that travels in a header.

### CQ3. "Why is a 5-minute access token enough? Users will be annoyed by constant refreshes."

- **Naive answer:** "Because refreshes are silent and automatic."  ← *true, and it misses the risk model*
- **The trap:** The trap is framing the TTL as a **UX** decision. The TTL is a **risk**
  decision: it is the exact window during which a stolen token is usable. Every extra
  minute is a minute of unrevocable authority in the hands of an attacker.
- **Expert answer:** The TTL is chosen from the **blast radius** you are willing to accept:
  if a token leaks, how much damage can the attacker do before it dies on its own? For a
  banking API, 5 minutes might still be too long and you would add step-up authentication
  and a denylist. The UX objection is handled by making the refresh **silent and
  automatic** — the client refreshes on a 401 and retries once, so the user never sees
  anything. A second-order trick: give the client a *renewal hint* (a `refresh_before`
  claim) so it refreshes proactively at 80% of the TTL rather than reactively on a 401,
  which turns a user-visible error into a background event.

### CQ4. "Would you use HS256 in a microservices setup?"

- **Naive answer:** "No, RS256 is safer for microservices."  ← *false; it is a distribution question*
- **The trap:** The trap is treating "microservices ⇒ asymmetric" as a rule. The rule is:
  **asymmetric when a verifier is outside your trust boundary, or when you need to
  publish the key.** A single issuer that all 25 services trust, where every service is
  equally trusted, can use HS256 — but then *any* service compromised can mint tokens
  for *any* service. That is a real and often-accepted risk, but it must be a decision,
  not an accident.
- **Expert answer:** I use HS256 only when the verifier set is small, homogeneous and
  fully trusted, and I would say so out loud. In a typical mesh — an auth service, 25
  business services, plus a few partners — I use `RS256`/`ES256`/`EdDSA`, because: (1) it
  lets me publish a JWKS and rotate keys without redistributing a secret to 25 services;
  (2) it limits the blast radius if one service is compromised; (3) it lets external
  partners verify tokens without becoming a minting party. The *cost* is a public key
  operation per request (~50–150 µs for RSA-2048, less for ES256) and a bigger token.

### CQ5. "You said you need `jti` for revocation. Doesn't that make the system stateful again?"

- **Naive answer:** "Yes, so let's not use `jti`."  ← *false; you already have state*
- **The trap:** The trap is thinking the *access token* path must remain 100% stateless.
  It already isn't if you do logout, device management, or "log out everywhere" — and you
  need all three. The `jti` denylist is checked **on the same path** as the signature,
  but it is a *bounded* state: one entry per revoked token, expiring at the token's own
  `exp`.
- **Expert answer:** You cannot have a revocable stateless credential — those are
  contradictory. The question is **where** the state lives. Putting it in the
  revocation path is the cheap choice: the state is only consulted when a token is
  *actually* revoked, and each entry has a TTL equal to the token's remaining life, so
  the denylist is self-cleaning and bounded by the revocation rate. The alternative, the
  **token-version** claim, is even cheaper: **one** counter per user instead of one
  entry per token — O(1) state that does not grow with the number of revoked tokens. So
  the honest answer is: `jti` is useful for **correlation, audit and per-token
  revocation**, but the scalable "log out everywhere" mechanism is the version counter.

### CQ6. "Why does the signature cover the header? Can't the header just be metadata?"

- **Naive answer:** "Because the RFC says so."  ← *true, and it misses the security reason*
- **The trap:** The trap is treating the header as irrelevant routing data. The header is
  where `alg` and `kid` live — and `alg` is the single most dangerous field in the whole
  format, because it selects the verification algorithm.
- **Expert answer:** The header is inside the signed input so that **the choice of
  algorithm and the choice of key become part of what the issuer attested to**. If the
  header were unprotected, an attacker could swap `alg` from `RS256` to `HS256` without
  touching the payload. This is not merely defence-in-depth — it is what makes the
  *pinned-algorithm* defence work correctly: the pinned decoder rejects the swapped
  `alg`, and even a decoder that did read `alg` would at least be detecting a forgery of
  the issuer's own attestation. The same principle is why `kid` is signed: it prevents an
  attacker from steering you to a key you would have used anyway but under a different
  identity, and it prevents "key confusion" across a multi-issuer JWKS.

### CQ7. "What happens if the JWKS endpoint returns a 500?"

- **Naive answer:** "All requests fail with 401."  ← *true only for uncached keys*
- **The trap:** The trap is forgetting the **cache**. Once the JWK Set is cached, the
  hot path never touches the network — a 500 from the JWKS endpoint is invisible until
  the cache needs to refresh. So the failure is **deferred**, not avoided, and it lands
  at the worst possible moment: the moment you rotate a key.
- **Expert answer:** The behaviour is: (1) the decoder keeps serving the cached keys, so
  existing tokens keep validating — the API is fine; (2) on an **unknown `kid`**, the
  decoder attempts a refresh, the fetch fails, and that token gets a 401 — which is the
  correct fail-closed behaviour; (3) if the JWKS is served from a CDN or a replicated
  store, you get a degraded but working endpoint. The operational answer is a **runbook**:
  publish the new key, **verify it is fetchable and contains the new `kid`**, *then* start
  signing with it, and keep the old key live for the overlap window. Never rotate the
  signing key and the JWKS in the same minute.

### CQ8. "How would you detect that a JWT has been tampered with, other than the 401?"

- **Naive answer:** "You can't — the signature check is the only mechanism."  ← *partly false*
- **The trap:** The trap is thinking signature failure and claim validation failure
  produce the same observable. They do **not** — and that difference is exactly what you
  need for detection.
- **Expert answer:** You separate the two at the metric level. A **signature** failure is
  a forgery attempt or a key/secret mismatch; a **claim** failure (`exp`, `aud`, `iss`) is
  a stale or mis-scoped token. In Spring Security these arrive as different
  `OAuth2Error` codes inside the same `JwtValidationException`, so I wrap the decoder in
  a decorator (or a `BearerTokenResolver` + filter) that classifies the failure and
  increments **separate counters** — `jwt.signature.failures` versus
  `jwt.claims.failures`. A spike in signature failures is an attack; a spike in
  audience failures is usually a misconfiguration or a client bug. That distinction is
  the difference between a 3 a.m. page and a ticket.

### CQ9. "Is it true that you can just decode the JWT and read the user's role to authorize the request?"

- **Naive answer:** "Yes, the role is right there in the payload."  ← *true, and dangerous*
- **The trap:** The trap is threefold: (a) treating a **decoded** claim as a
  **trusted** claim — decoding proves nothing, only *verification* does; (b) trusting the
  *shape* of the claim without a mapping; (c) ignoring that a token is a **snapshot** —
  the role may have changed since it was issued.
- **Expert answer:** Three corrections. First, you may only read the claims **after**
  the signature and the validators have passed — decoding is a parsing step, not a trust
  step. Second, the claim→authority mapping must be explicit and by name; a blanket
  "every claim becomes an authority" turns a mis-named claim into a privilege escalation.
  Third, the claim is a **snapshot up to 15 minutes old**: if a role change must take
  effect immediately, the token cannot be the authority — you need a live lookup or a
  token-version bump that invalidates the token. The correct mental model: the token
  says *"at the time of issuance, this user held these roles"*, and your system decides
  how stale that is allowed to be.

## 18. Frequently Asked Production Issues & Debugging Scenarios

### Issue 1 — Every request returns 401, even with a valid-looking token

**Symptom.** The API returns 401 for every authenticated call. The client sends a token
that looks correct. The body is empty; the `WWW-Authenticate` header says
`error="invalid_token"`.

**Diagnostic commands.**

```bash
# 1. Get the server's actual reason (never guess from the 401 alone):
java -jar app.jar --logging.level.org.springframework.security.oauth2=DEBUG \
                   --logging.level.org.springframework.security.oauth2.jwt=TRACE

# 2. Check the JWKS the decoder is actually using:
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys[] | {kid, alg, use}'

# 3. Check the issuer and audience config vs the token:
#    iss: the token's iss must EXACTLY equal spring.security.oauth2.resourceserver.jwt.issuer-uri
#    aud: the token's aud must contain this service's id
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq '{iss,aud,exp,iat}'

# 4. Clock skew:
date -u; timedatectl status | grep -i synchronized

# 5. If the kid is unknown, is the JWKS reachable from THIS pod?
curl -sS -o /dev/null -w '%{http_code}\n' https://auth.acme.com/.well-known/jwks.json
```

**Expected signal.** The DEBUG log shows exactly one of:

| Log line | Cause | Fix |
|---|---|---|
| `Failed to decrypt signature` / `JWT signature does not match` | Wrong secret, or an algorithm/format mismatch | Rotate the secret in **both** places; check the algorithm matches |
| `Signed JWT rejected: Another algorithm expected, or no matching key(s) found` | The `kid` is not in the JWKS | Publish the key; check the JWKS URL and the network path |
| `"exp" claim timestamp check failed` | Expired **or** clock skew | Compare `exp` with `date -u`; check NTP |
| `"iss" claim was not valid` | Issuer mismatch — often a **trailing slash** | Make the config string identical to the claim |
| `Audience "..." does not contain ...` | `aud` validation | Add the service id to the issuer's audience, or fix the expected id |
| `Unsupported algorithm 'none'` | The `alg: none` attack | Pin the algorithm — this should be impossible |

**The fix.** Correct the single misconfigured value. The defensive changes: pin the
algorithm, add a startup assertion that the configured issuer has **no trailing slash**
and matches a regex, and split the 401 metrics by reason so this is diagnosable in
seconds next time.

---

### Issue 2 — Works locally, fails in production

**Symptom.** The exact same token works against `localhost:8080` and fails against the
production URL.

**Diagnostic commands.**

```bash
# 1. Diff the effective configuration, not the source files:
#    (profiles, env vars, and config servers are where this hides)
java -jar app.jar --debug 2>&1 | grep -i "issuer-uri\|jwk-set-uri\|Loaded config file"

# 2. Compare the JWKS the two environments resolve:
curl -s http://localhost:8080/.well-known/jwks.json | jq '.keys[].kid'
curl -s https://auth.acme.com/.well-known/jwks.json | jq '.keys[].kid'

# 3. Compare the secrets' FINGERPRINTS (never print the secret):
echo -n "$LOCAL_SECRET"  | sha256sum
echo -n "$PROD_SECRET"   | sha256sum

# 4. Compare the clocks:
date -u; ssh prod 'date -u'

# 5. Is it the network? Can the pod reach the JWKS?
kubectl exec -it deploy/api -- curl -sS -o /dev/null -w '%{http_code}\n' \
  https://auth.acme.com/.well-known/jwks.json
```

**Expected signal.** The most common cause is **a different secret** in production (the
fingerprint differs) — because `application-prod.yml` was never updated when the
dev secret was rotated. The second most common is a **different JWKS** (a staging issuer
URI left in the prod profile). The third is **clock skew** between the container and the
issuer.

**The fix.** Externalise the secret; make rotation a two-phase deploy (publish the new
key alongside the old, then switch). Add the JWKS reachability to the **readiness probe**
so a pod that cannot reach the auth service never receives traffic.

---

### Issue 3 — The JWKS endpoint returns 404

**Symptom.** Tokens signed with a valid key are rejected. The decoder logs
`no matching key(s) found` or a 404 from the JWKS URI.

**Diagnostic commands.**

```bash
# 1. Fetch the JWKS exactly as the decoder does:
curl -sS -o /dev/null -w '%{http_code} %{content_type}\n' \
  https://auth.acme.com/.well-known/jwks.json

# 2. Is it a 404 because of the PATH? Compare with the issuer's discovery doc:
curl -s https://auth.acme.com/.well-known/openid-configuration | jq '.jwks_uri'

# 3. Is the JWKS served with the right content type and shape?
curl -s https://auth.acme.com/.well-known/jwks.json | jq 'has("keys")'

# 4. Is there a proxy in front that rewrites the path?
kubectl get ingress -o yaml | grep -A5 rewrite-target
```

**Expected signal.** The discovery document says
`"jwks_uri": "https://auth.acme.com/oauth2/jwks"` but the app is configured with
`https://auth.acme.com/.well-known/jwks.json` — the **path** is wrong. Or the endpoint
returns `200` with `{"data": [...]}` instead of `{"keys": [...]}` — a **shape**
mismatch, which Nimbus reports as "no matching key(s) found" rather than as a parse
error.

**The fix.** Point `jwk-set-uri` at the URL from the discovery document (or switch to
`issuer-uri` and let Spring discover it). Add the JWKS to the **health check** so a 404
is caught in CI, not in production.

---

### Issue 4 — The token works, but the user has no permissions

**Symptom.** Authentication succeeds (no 401), but the request is refused with **403**.

**Diagnostic commands.**

```bash
# 1. What authorities were actually granted?
#    Add a one-off log in a JwtAuthenticationConverter, or inspect via an actuator endpoint:
curl -s localhost:8080/actuator/beans | jq '.contexts.application.beans.jwtAuthenticationConverter'

# 2. What claims did the token actually carry?
echo "$TOKEN" | cut -d. -f2 | tr '_-' '/+' | base64 -d 2>/dev/null | jq 'keys'

# 3. Is the authority prefix right?
grep -rn "setAuthorityPrefix\|setAuthoritiesClaimName\|hasRole\|hasAuthority" src/main/java/
```

**Expected signal.** The token's payload has `{"roles": ["ADMIN"]}` but the converter was
configured with `setAuthoritiesClaimName("scope")` — so the authorities list is empty and
`hasAuthority("ROLE_ADMIN")` fails. The classic variants of this bug: the claim name is
`role` (singular) but you configured `roles`; the prefix is `SCOPE_` but you wrote
`.hasRole("ADMIN")` (which expects `ROLE_`); or the claim is a **string** (`"ADMIN,USER"`)
while the converter expects an **array**.

**The fix.** Align the claim name and prefix with the issuer's actual contract, and add a
test that decodes a known token and asserts the **exact** authority set. This is the
single most common JWT integration bug after the 401s.

---

## 19. Architecture Design Questions

### 19.1 Design the authentication and validation architecture for a multi-service platform

**The prompt.** "We have 30 microservices behind a gateway. Two business units: retail
(PCI-scoped, strict) and marketplace (PCI-out, high volume, 40k req/s). Mobile apps,
a partner portal and an internal service mesh are all clients. Design the auth
architecture."

**Reference design.**

```text
┌───────────────────────────────────────────────────────────────────────────────┐
│                        AUTH SERVER (the issuer)                                │
│  - POST /oauth2/token  (password + mfa → token pair)                          │
│  - POST /oauth2/token  (refresh_token → rotated pair)                         │
│  - POST /oauth2/revoke, /oauth2/logout-all                                   │
│  - signing keys in a KMS (RS256 for retail, ES256 for marketplace)            │
│  - publishes /.well-known/jwks.json (both key sets, with kid)                 │
│  - refresh tokens: SHA-256 hashed, family-tracked, in Postgres                │
│  - token version per user in Redis (for logout-everywhere)                     │
└───────────────┬───────────────────────────────────────────────────────────────┘
                │ issues
     ┌──────────┴───────────┬──────────────────────┬────────────────────────┐
     ▼                      ▼                      ▼                        ▼
  RETAIL APIs          MARKETPLACE APIs      PARTNER PORTAL            SERVICE MESH
  aud=retail-*          aud=marketplace-*     aud=partner-*            aud=target-svc
  RS256                 ES256                 RS256 (public JWKS)      ES256, 5 min
  5 min access          10 min access          15 min access           service→service
  step-up auth for      no step-up            per-partner keys         act/sub claims
  transfers > ₹2L       high volume → ES256   (a partner can be        mesh tokens from a
                        for token size        rotated independently)   central issuer
```

**The data flow.** The client authenticates once at the auth server and receives a token
pair. Every subsequent API call carries the access token in the `Authorization` header.
Each service validates **locally** — no call to the auth server on the request path.

**The design decisions and their reasons.**

1. **Asymmetric signing (RS256/ES256) everywhere**, because partners and the mesh are
   outside the trust boundary and the key must be publishable.
2. **Two key families**, one per business unit, because PCI scope isolation means the
   retail signing key must not be usable to mint marketplace tokens, and the rotation
   cadences differ (retail quarterly, marketplace monthly).
3. **`aud` per service family** — a `retail-*` token is rejected by a `marketplace-*`
   service. The audience check is the confused-deputy defence.
4. **10-minute marketplace access tokens** but **5-minute retail** — the blast radius is
   proportional to the value of what the token can do.
5. **Refresh tokens hashed and family-tracked** — the only revocable power in the system.
6. **Token version in Redis** for logout-everywhere, checked only on the retail path
   (where instant revocation is a compliance requirement) — the marketplace path relies on
   the TTL plus a denylist for high-value actions.

**The trade-offs the interviewer wants to hear.**

| Decision | What you gain | What you pay |
|---|---|---|
| Asymmetric signing | Publishable keys, small blast radius | ~50–150 µs per verify, bigger tokens |
| Per-family keys | Blast-radius isolation, independent rotation | More key management, more `kid` routing |
| 5-min retail TTL | Small revocation window | More refresh traffic (2× the API call volume) |
| Token version on retail only | Instant logout | One Redis hop on the hot path for retail |
| Local validation | No auth server in the request path | The auth server cannot know who is calling |
| HS256 nowhere | Safe across trust boundaries | Key distribution complexity |

**What I would say at the end.** "The auth server is **not** in the request path. That is
the entire point of the design — it is in the *login* and *refresh* path only. The cost is
that the auth server cannot answer 'who is calling right now?', which is why every
service must be able to refuse a token locally, and why the token must carry an audience
so it cannot be replayed against the wrong service."

---

### 19.2 Design the token validation layer for a system that must survive an auth outage

**The prompt.** "Our auth server is a single region. It went down for 25 minutes last
quarter and took down every API because every request called it to validate the token.
Design it so that an auth outage degrades rather than fails."

**Reference design.**

```text
   ┌──────────────────────┐        ┌──────────────────────────────────────┐
   │  API SERVICE (x30)   │        │  AUTH SERVICE (regional, 3 AZ)        │
   │                      │        │                                      │
   │  JwtDecoder          │        │  /oauth2/token, /oauth2/refresh       │
   │   └─ RemoteJWKSet    │───────▶│  /oauth2/revoke                       │
   │      ├─ cache: keys  │  fetch │  /.well-known/jwks.json  (CDN-cached) │
   │      └─ TTL /        │  only  │  KMS: signing keys                    │
   │         refresh-on-  │  on    │  Redis: token versions                │
   │         unknown-kid  │  miss  │  Postgres: refresh tokens (hashed)    │
   │                      │        └──────────────────────────────────────┘
   │  validate:           │
   │   1. signature       │  ← LOCAL, always available
   │   2. exp/nbf/iss/aud │  ← LOCAL, always available
   │   3. token version   │  ← Redis; DEGRADE to skip if Redis is down
   └──────────────────────┘
```

**The four design decisions.**

1. **Local signature validation with a cached JWKS.** The JWKS is served through a CDN
   and the decoder caches it, so the hot path needs **zero** network calls to the auth
   service. An auth outage does not affect validation of already-known keys.
2. **Refresh-on-unknown-`kid`, with a bounded retry.** A new `kid` during an outage
   cannot be fetched → those tokens fail. Mitigation: the runbook says *never rotate
   during an auth outage*, and the JWKS is published **before** signing with a new key.
3. **The token-version check degrades gracefully.** If Redis is unavailable, the choice
   is fail-open (accept the token — availability wins, but a revoked token might work)
   or fail-closed (reject — security wins, but the API is down). For a **read-heavy
   public API**, fail-open for the version check only (the signature and the TTL still
   apply) is defensible; for a **payments API**, fail-closed. The decision must be
   explicit and per-service.
4. **Refresh tokens fail closed.** If the auth service is down, no one can refresh — the
   user is logged out after the access token expires. This is the **one** degradation you
   must accept, and it is why the access-token TTL is a business decision (a 5-minute TTL
   means a 5-minute outage impact; a 60-minute TTL means an hour).

**The trade-offs.**

| Decision | Gain | Cost |
|---|---|---|
| Cached JWKS + local validation | Auth outage does not stop the API | Key removal is not instantaneous |
| Fail-open on the version check | Availability | A revoked token may be accepted during the outage |
| Fail-closed on the version check | Security | The API is down when Redis is down |
| Refresh fails closed | Simplicity, no stale refresh | Users are logged out during an auth outage |
| Multi-region auth | Higher availability | Two issuers, two key sets, `iss` becomes regional |

**The honest conclusion.** "Stateless validation means an auth outage does not take down
the API — but it *does* take down the login and refresh paths. The blast radius of the
outage is therefore `access-token TTL`, and the way to shrink it is to shorten the access
token, not to make the auth service more available."

---

## 20. Rapid-Fire MCQs

**1.** What is the payload of a standard JWT?
- A) Encrypted with the issuer's public key
- B) **Base64url-encoded and readable by anyone**
- C) Hashed with SHA-256
- D) Signed and therefore tamper-proof but opaque

**2.** How many parts does a JWS-compact JWT have?
- A) 2
- B) 3
- C) 4
- D) 5

**3.** Which characters does base64url replace to be URL-safe?
- A) `+` and `/` are kept; `=` is dropped
- B) **`+` → `-` and `/` → `_`**
- C) `=` → `%3D`
- D) Only the padding is removed

**4.** Which header parameter tells the verifier which key signed the token?
- A) `alg`
- B) `typ`
- C) **`kid`**
- D) `cty`

**5.** Is the JOSE header covered by the signature?
- A) No — the header is public metadata
- B) **Yes — it is part of the signing input**
- C) Only when `crit` lists it
- D) Only for JWE

**6.** Which claim identifies the token uniquely, for revocation purposes?
- A) `iss`
- B) `sub`
- C) `iat`
- D) **`jti`**

**7.** `aud` in a JWT may legally be…
- A) Only a string
- B) Only an array of strings
- C) **Either a string or an array of strings**
- D) Only an object

**8.** What does `Spring Security`'s `Jwt.getAudience()` return for a token whose `aud` is the bare string `"orders-api"`?
- A) `null`
- B) An empty list
- C) **A list containing one element, `"orders-api"`**
- D) The raw string

**9.** With HS256, who can create a valid token?
- A) Only the issuer
- B) **Anyone who holds the shared secret — including every verifier**
- C) Only the holder of a private key
- D) Anyone with the public key

**10.** What is the defence against the algorithm-confusion attack?
- A) Use a long secret
- B) Encrypt the payload
- C) **Pin the expected algorithm in the decoder configuration**
- D) Always validate `aud`

**11.** What is `JwtTimestampValidator`'s default clock-skew leeway in Spring Security?
- A) 0 seconds
- B) 5 seconds
- C) **60 seconds**
- D) 5 minutes

**12.** What does `JwtValidators.createDefaultWithIssuer(issuer)` validate?
- A) `exp`, `nbf` and `iss` only
- B) `exp`, `nbf`, `iss` **and** `aud`
- C) Only `iss`
- D) `iss` and `aud` only

**13.** An access token with a 10-minute TTL is stolen. What is the maximum time the attacker can use it?
- A) 10 minutes
- B) Until the user logs out
- C) **At most 10 minutes (plus the clock-skew leeway), unless you add revocation**
- D) Indefinitely — a signature cannot be revoked

### Answer Key

| # | Answer | One-line reason |
|---|--------|-----------------|
| 1 | **B** | A JWS JWT is *encoded*, not encrypted — decoding needs no key. |
| 2 | **B** | `header.payload.signature` — three parts. |
| 3 | **B** | RFC 4648 §5 swaps `+`/`/` for `-`/`_` and omits the padding. |
| 4 | **C** | `kid` is the key id; `alg` is the algorithm. |
| 5 | **B** | The signing input is `header + "." + payload` — the header is protected. |
| 6 | **D** | `jti` is the unique token id used as the revocation handle. |
| 7 | **C** | RFC 7519 §4.1.3 permits a single string or an array. |
| 8 | **C** | Spring normalises the string form into a one-element list. |
| 9 | **B** | Symmetric = the same bytes sign and verify, so every verifier can mint. |
| 10 | **C** | Never let the token choose the verification algorithm. |
| 11 | **C** | 60 seconds is the library default, and the right number. |
| 12 | **A** | It covers the timestamp and the issuer — **not** the audience. |
| 13 | **C** | The signature is permanent; only acceptance can be withdrawn. |

> 📌 **The three to re-read:** **Q10** (the algorithm-confusion defence), **Q12**
> (`createDefaultWithIssuer` does **not** validate `aud` — the most-missed validator) and
> **Q18** (a missing `exp` must be a rejection).

---

## 21. Cheat Sheet / One-Page Summary

| Area | Fact |
|---|---|
| **JWT = ?** | A claims set serialized into a **JWS** (signed) or **JWE** (encrypted) |
| **Structure** | `base64url(header).base64url(payload).base64url(signature)` |
| **Signing input** | `base64url(header) + "." + base64url(payload)` — the header is protected |
| **Payload** | **Encoded, NOT encrypted** — anyone can read it |
| **Encoding** | **base64url** (`-`, `_`, no `=`), never standard base64 |
| **Registered claims** | `iss` `sub` `aud` `exp` `nbf` `iat` `jti` |
| **`aud`** | string **or** array; Spring normalises to `List<String>`; must contain **your** service id |
| **`exp`** | Must be **required**; compared with a **60 s** leeway |
| **`jti`** | The revocation handle |
| **Header params** | `alg` `typ` `kid` `cty` `crit` |
| **HS256** | Symmetric — **every verifier can also mint** |
| **RS256/PS256** | Asymmetric — only the issuer can sign; RSA-PSS is probabilistic |
| **ES256** | ECDSA P-256 — 64-byte raw `r‖s` signature; DER conversion in Java |
| **EdDSA** | Ed25519 — modern, fast; the Spring Security 6.4+ recommendation |
| **Defence vs confusion** | **Pin the algorithm** server-side; never read `alg` from the token |
| **Defence vs `none`** | The same pinning — Nimbus then rejects `none` outright |
| **Key distribution** | `jwk` → `jwk_set_uri` → JWKS (`{"keys":[...]}`) → `kid` |
| **Rotation** | Publish new **alongside** old → sign with new → wait **> max TTL** → remove old |
| **Spring chain** | `BearerTokenAuthenticationFilter` → `DefaultBearerTokenResolver` → `JwtDecoder` → validators → `Jwt` → `JwtAuthenticationConverter` → `JwtAuthenticationToken` → `SecurityContextHolder` → `AuthorizationFilter` |
| **Validators** | `JwtTimestampValidator` · `JwtIssuerValidator` · `JwtClaimValidator` · `DelegatingOAuth2TokenValidator` |
| **`JwtValidators.createDefaultWithIssuer`** | Timestamp + issuer — **not** `aud` |
| **Access token TTL** | **5–15 min** · stateless · `Authorization: Bearer <jwt>` |
| **Refresh token TTL** | **7–30 days** · server-stored **hashed** · `HttpOnly; Secure; SameSite=Strict` cookie |
| **"Stateless"** | The **access token** is stateless; the **system** is not |
| **Revocation** | A signature cannot be un-signed — use a denylist, a token version, or a short TTL |
| **Status codes** | **401** = not authenticated · **403** = authenticated but not authorized |
| **Hard rules** | Pin the algorithm · validate `iss`+`aud` · require `exp` · no PII · never in a URL · redact in logs · ≥256-bit secret |

---

## 22. Real-World Production Notes

| Knob / Behaviour | Default | When to change it |
|---|---|---|
| Access-token TTL | 10 min | 5 min for high-value APIs; 30 min only for low-privilege, high-volume paths |
| Refresh-token TTL | 30 days | 7 days for banking/regulated; 90 days only for low-risk consumer apps |
| ID-token TTL | 5–60 min | Never sent to browser JS — see topic 07 |
| Signing algorithm | `RS256` | `ES256` for token size; `EdDSA` for new systems; `HS256` only for a single trusted verifier |
| RSA key size | 2048 bit | 3072 for regulated environments; 4096 only on mandate (~4× sign cost) |
| HS256 secret length | ≥ 32 bytes | Never below 32; generate with `SecureRandom`, not a password |
| Clock-skew leeway | 60 s | 10 s when you control both clocks; **never** above 60 s |
| JWKS cache TTL | ~5 min (library default) | Align with the rotation runbook; shorter if you rotate often |
| Key rotation overlap | = max token TTL | Always ≥ max TTL + skew; never shorter |
| `kid` scheme | `key-<yyyy-mm>` | Keep it short and structured — it is parsed on the hot path |
| Claim count | < 20 | Use role IDs; resolve permissions at the edge |
| `typ` value | `at+jwt` | Use `JWT` for a generic token; check it if you accept more than one type |
| Token denylist TTL | = remaining token life | Never longer — that is how the denylist grows unbounded |
| 401 log level | WARN | DEBUG for `org.springframework.security.oauth2` in non-prod only |
| `Authorization` in logs | **Never** | Redact at the proxy; sample the length, not the value |

**Metrics worth having on a dashboard.**

| Metric | Why |
|---|---|
| `security.oauth2.authorizations` | The 401 rate — the single most important auth metric |
| `jwt.failures.by_reason` (signature / claims / unknown-kid) | Distinguishes an attack from a misconfiguration |
| `jwt.validation.duration` (p50/p99) | Detects a regression to a slow algorithm or a huge claim set |
| `jwks.fetch.count` and `jwks.cache.hit_ratio` | A fall in the hit ratio means unknown `kid`s — possibly an attack |
| `token.size.p99` | A rising tail means the claim set is growing |
| `auth.issue.count` by `kid` | Confirms the rotation actually switched the signing key |
| `clock.offset.seconds` | Detects clock drift before it causes an outage |

**Defaults to deviate from, deliberately.**

- **Never** raise the clock-skew leeway to "fix" a clock problem — fix the clock.
- **Never** extend the access-token TTL to "fix" refresh errors — fix the refresh path.
- **Never** put a revocation check in front of *every* request without measuring the added
  latency first (see chapter 03 §11).
- **Never** rotate a signing key without publishing the new public key **first**.

---

## 23. Follow-up Learning

**Next in this folder**

- → [02 · Access & Refresh Tokens and Token Rotation](./02-Access-Refresh-Tokens-and-Token-Rotation.md) —
  the two-token model, the refresh flow, rotation with reuse detection, hashed storage, the
  cookie decision.
- → [03 · Token Revocation, Storage & Production Design](./03-Token-Revocation-Storage-and-Production-Design.md) —
  the six revocation strategies, the token version, denylist TTL maths, key-rotation
  runbooks.
- → [04 · Security Risks, Complete Flows & Real Examples](./04-Security-Risks-Complete-Flows-and-Real-Examples.md) —
  the 11 attacks, the login/refresh/logout/service-to-service flows, the hardening checklist.

**Related folders**

- [05 · Spring Security](../05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md) — the filter chain, the
  `SecurityContext`, CORS and CSRF, password encoding and session management.
- [07 · OAuth2 & OpenID Connect](../07-OAuth2-OpenID-Connect/02-Authorization-Code-and-PKCE-Flows.md) — where JWTs
  *come from*: the authorization code + PKCE flow, ID tokens, introspection and token
  exchange.
- **12 · System Design for Java Backend** (sibling folder) — rate limiting, gateway
  design and multi-region token strategy.

**Source pointers (RFCs and Spring Security 6.1)**

| Document | Why read it |
|---|---|
| RFC 7519 — JSON Web Token | The claims, the JWS/JWE relationship, the validation rules |
| RFC 7515 — JWS | The signing input, the `alg`/`crit`/`kid` header parameters |
| RFC 7516 — JWE | The five-part encrypted format |
| RFC 7517 — JWK | The key JSON representation and the JWKS array |
| RFC 7518 — JOSE Algorithms | The `alg` registry, including `EdDSA` |
| RFC 7519 §4.1.3 | The `aud` string-or-array rule |
| RFC 6750 — Bearer Token Usage | The `Authorization` header, `WWW-Authenticate`, and the `insufficient_scope` error |
| RFC 9068 — JWT Access Token Profile | The `at+jwt` type and the standard claims for access tokens |
| Spring Security `NimbusJwtDecoder` | The decoder builders and the algorithm-pinning API |
| Spring Security `JwtAuthenticationConverter` | The claims → authorities mapping |
| Spring Security `OAuth2TokenValidator` | The validator chain and the 60 s default leeway |

---

**Next Chapter:** → [02 · Access & Refresh Tokens and Token Rotation](./02-Access-Refresh-Tokens-and-Token-Rotation.md)
**Previous Chapter:** ← [05 · Spring Security](../05-Spring-Security/01-Architecture-and-Security-Filter-Chain.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
