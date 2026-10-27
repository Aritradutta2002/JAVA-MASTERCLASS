# 07 — Security Design

> Identity, authorization, abuse resistance, secrets, encryption, and threat modeling.

| # | Chapter | Core distinction | Level |
|---|---|---|---|
| 01 | [Security Architecture and Threat Modeling](./01-Security-Architecture-and-Threat-Modeling.md) | Assets, actors, boundaries, abuse | Intermediate → Expert |
| 02 | [Authentication and Authorization](./02-Authentication-and-Authorization.md) | Who vs what | Intermediate → Expert |
| 03 | [JWT, OAuth 2.0, OpenID Connect, and SSO](./03-JWT-OAuth2-OIDC-and-SSO.md) | Token format vs protocol | Advanced → Expert |
| 04 | [API Security and DDoS Protection](./04-API-Security-and-DDoS-Protection.md) | Application abuse vs volumetric attack | Advanced → Expert |
| 05 | [Secrets, Encryption, and Key Management](./05-Secrets-Encryption-and-Key-Management.md) | Data protection vs key lifecycle | Advanced → Expert |

## Threat-Model Checklist

- [ ] Assets and classifications
- [ ] Actors and abuse cases
- [ ] Trust boundaries and privilege transitions
- [ ] Object/tenant authorization at the owner
- [ ] Input, injection, SSRF, and resource-exhaustion paths
- [ ] Secret/key issuance, rotation, audit, and revocation
- [ ] Detection, logging, and incident response

## Practical Drill

- [ ] Threat-model checkout and produce a prioritized risk register.
- [ ] Implement authorization tests for IDOR and cross-tenant access.
- [ ] Rotate a signing key without invalidating every active session unexpectedly.

**Previous:** [06 — API Design](../06-API-and-Communication-Design/README.md) · **Next:** [08 — Microservices](../08-Microservices-Architecture/README.md) · **Up:** [Hub](../README.md)
