# 09 — Cloud and Infrastructure Design

> Cloud service mapping, containers/Kubernetes, delivery, elasticity, resilience, IaC, and cost.

| # | Chapter | Core decision | Level |
|---|---|---|---|
| 01 | [Cloud Service Mapping](./01-Cloud-Service-Mapping.md) | Capability vs control/cost | Intermediate → Expert |
| 02 | [Containers and Kubernetes](./02-Containers-and-Kubernetes.md) | Desired state, probes, disruption | Advanced → Expert |
| 03 | [CI/CD and Release Strategies](./03-CI-CD-and-Release-Strategies.md) | Blast radius and rollback | Intermediate → Expert |
| 04 | [Load Balancing, Autoscaling, and CDN](./04-Load-Balancing-Autoscaling-and-CDN.md) | Where traffic is cached/scaled | Advanced → Expert |
| 05 | [Multi-Region and High Availability](./05-Multi-Region-and-High-Availability.md) | RTO/RPO and data model | Advanced → Expert |
| 06 | [Infrastructure as Code and Cost](./06-Infrastructure-as-Code-and-Cost.md) | Repeatability and unit cost | Advanced → Expert |

## Architecture Review

- [ ] Failure domains and quotas documented
- [ ] Warm failover capacity funded
- [ ] Schema/artifact compatibility during rollback
- [ ] Database connection budget across scale-out
- [ ] IaC drift and secret leakage controlled
- [ ] Idle/log/egress cost attributed

## Practical Drill

- [ ] Write Kubernetes deployment, service, probes, limits, HPA, and PDB.
- [ ] Model canary guardrails and an expand/contract schema release.
- [ ] Calculate monthly compute/storage/egress and failover cost.

**Previous:** [08 — Microservices](../08-Microservices-Architecture/README.md) · **Next:** [10 — Observability](../10-Observability-and-Reliability-Engineering/README.md) · **Up:** [Hub](../README.md)
