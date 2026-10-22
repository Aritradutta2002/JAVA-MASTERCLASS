# 05 · Cloud, Infrastructure as Code & Cost

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★☆☆ ("How do you manage infrastructure? How do you control cost?")
> **Prerequisite:** [04 · Observability, SRE & Incident Response](./04-Observability-SRE-and-Incident-Response.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Cloud You Actually Need to Know](#1-the-cloud-you-actually-need-to-know)
- [2. Mental Model — Everything Is a Trade of Control vs Effort](#2-mental-model--everything-is-a-trade-of-control-vs-effort)
- [3. Compute, Storage, Network](#3-compute-storage-network)
- [4. Infrastructure as Code](#4-infrastructure-as-code)
- [5. Terraform Patterns](#5-terraform-patterns)
- [6. Twelve-Factor Configuration](#6-twelve-factor-configuration)
- [7. Cost — Where It Goes and How to Cut It](#7-cost--where-it-goes-and-how-to-cut-it)
- [8. Architecture Decisions That Drive Cost](#8-architecture-decisions-that-drive-cost)
- [9. Security Baseline](#9-security-baseline)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **Managed services are the default answer** for a small team: RDS instead of self-run Postgres,
> MSK/Confluent instead of self-run Kafka, S3 instead of a filesystem. The trade is control and
> portability for operational burden — and for most teams, the burden is the bigger risk.
>
> **Everything is code.** Terraform (or OpenTofu) for infrastructure, Helm/Kustomize for workloads,
> Flyway for schema. Clicking in a console produces undocumented, unreproducible, un-reviewable
> state — and "works in staging" becomes luck.
>
> **Remote state with locking, no secrets in state, modules with pinned versions.**
>
> **Twelve-factor config:** everything environment-specific comes from the environment. No
> `application-prod.yml` in the jar. `SPRING_PROFILES_ACTIVE` + env vars + a secret manager.
>
> **Cost is an architecture property.** The biggest levers are usually: over-provisioned memory
> requests (bin-packing waste), idle non-prod environments, un-tiered object storage, cross-AZ data
> transfer, and no autoscaling on non-prod.

---

## 1. The Cloud You Actually Need to Know

### 1.1 The service map (provider-agnostic)

| Capability | AWS | GCP | Azure |
|---|---|---|---|
| VM | EC2 | Compute Engine | Virtual Machines |
| Managed k8s | EKS | GKE | AKS |
| Serverless containers | Fargate | Cloud Run | Container Apps |
| Managed Postgres | RDS / Aurora | Cloud SQL / AlloyDB | Flexible Server |
| Managed Redis | ElastiCache | Memorystore | Cache for Redis |
| Managed Kafka | MSK | Pub/Sub (different model) | Event Hubs |
| Object storage | S3 | GCS | Blob Storage |
| CDN | CloudFront | Cloud CDN | Front Door |
| Secrets | Secrets Manager | Secret Manager | Key Vault |
| IaC-native | CloudFormation | Deployment Manager | Bicep |
| Identity | IAM | IAM | Entra ID |

**Interview-safe position:** name the category and the trade-off, not the SKU. "A managed Postgres
with a read replica, in two AZs" beats reciting instance families.

### 1.2 Serverless vs containers vs VMs

| | Cold start | Cost model | Control | Best for |
|---|---|---|---|---|
| **Lambda / Cloud Run** | 100 ms–5 s (JVM: seconds) | per request + duration | low | spiky, low-volume, event-driven |
| **Containers on k8s** | 20–40 s (JVM warm-up) | per node/pod-hour | medium | steady services |
| **VMs** | minutes | per hour | full | stateful, specialised, licensed |

**The JVM-specific trap:** a Spring Boot Lambda with a cold start of 3–8 seconds is a bad user
experience. Mitigations: **SnapStart/CRaC**, **GraalVM native images**, provisioned concurrency
(expensive), or don't use Lambda for latency-sensitive Java.

---

## 2. Mental Model — Everything Is a Trade of Control vs Effort

```
        CONTROL                                          EFFORT
   ┌────────────────────────────────────────────────────────────┐
   │  self-managed VMs          ████████████  ████████████      │
   │  self-managed k8s          ██████████    ██████████        │
   │  managed k8s (EKS)         ████████      ██████            │
   │  managed container (Cloud Run) ████      ███               │
   │  managed service (RDS)     ███           ██                │
   │  serverless (S3, SQS)      █             █                 │
   └────────────────────────────────────────────────────────────┘
```

**The rule:** move right until you hit a real constraint. Most "we need to self-host" decisions are
underestimating the ongoing cost of patching, backup testing, failover drills, and 3am pages.

**Legitimate reasons to self-host:** data residency you can't meet managed, a cost profile managed
can't match at your scale (> tens of millions/month of a specific service), a feature the managed
offering lacks, or a regulatory requirement.

---

## 3. Compute, Storage, Network

### 3.1 Compute

- **Autoscaling groups / node pools** — size nodes so pods bin-pack well.
- **Spot / preemptible** for stateless, interruption-tolerant work — 60–90% cheaper, with the
  requirement that your service handles a 2-minute termination notice gracefully (which a
  well-configured Java service does).
- **Reserved / committed use** for steady baseline — 30–60% cheaper. The usual mix:
  baseline on commitments, spikes on on-demand, batch on spot.

### 3.2 Storage

| Type | Use | Cost trap |
|---|---|---|
| Object (S3) | blobs, backups, static assets | no lifecycle policy → pays for cold data at hot prices |
| Block (EBS) | VM/k8s volumes | orphaned volumes after instance deletion |
| File (EFS) | shared filesystem | expensive per GB; rarely needed |
| Database | structured data | over-provisioned IOPS, idle replicas |

**Always set a lifecycle policy:**

```hcl
resource "aws_s3_bucket_lifecycle_configuration" "logs" {
  bucket = aws_s3_bucket.logs.id
  rule {
    id     = "tier-and-expire"
    status = "Enabled"
    transition { days = 30  storage_class = "STANDARD_IA" }
    transition { days = 90  storage_class = "GLACIER" }
    expiration { days = 365 }
  }
}
```

### 3.3 Network — the hidden cost

| Flow | Cost |
|---|---|
| Within an AZ | free |
| **Cross-AZ** | ~$0.01/GB **each way** |
| Internet egress | ~$0.09/GB |
| NAT gateway | per-GB processing + per-hour |
| Cross-region | ~$0.02/GB |

**The classic surprise:** a chatty service talking to a DB in another AZ, or a Redis in another AZ,
generates terabytes of cross-AZ traffic. At 10 TB/month that's ~$200 — which is fine, but at 1 PB
it's the bill.

**Mitigations:** keep a service and its database in the same AZ where possible (with a replica in
another AZ for failover), compress payloads, batch, and cache to reduce chatter.

---

## 4. Infrastructure as Code

### 4.1 Why

| Manual (console) | IaC |
|---|---|
| undocumented | reviewed in PRs |
| irreproducible | identical in every env |
| no rollback | `git revert` + apply |
| drift undetected | `terraform plan` shows drift |
| one person knows | the repo knows |

### 4.2 The toolchain

| Layer | Tool |
|---|---|
| Cloud resources | **Terraform / OpenTofu**, Pulumi, CDK |
| Kubernetes workloads | **Helm** or **Kustomize** |
| Database schema | Flyway / Liquibase |
| App config | ConfigMaps + env vars + a secret manager |
| Policy | OPA/Gatekeeper, Kyverno, `checkov`/`tfsec` |

**Helm vs Kustomize:**

| | Helm | Kustomize |
|---|---|---|
| Model | templating + values | overlays/patches on plain YAML |
| Power | very high (logic, hooks) | limited (no templating by design) |
| Readability | templates get complex | plain manifests |
| Best for | distributing a chart | environment overlays of your own manifests |

**Practical answer:** "Kustomize for our own services' overlays because the manifests stay plain
YAML, Helm for third-party charts because that's how they're distributed."

---

## 5. Terraform Patterns

### 5.1 Layout

```
infra/
├── modules/
│   ├── service/            ← reusable: deployment + service + hpa + alerts
│   ├── postgres/
│   └── network/
├── envs/
│   ├── staging/
│   │   ├── main.tf
│   │   ├── backend.tf
│   │   └── terraform.tfvars
│   └── prod/
└── global/                 ← DNS, IAM, ECR
```

### 5.2 Remote state with locking

```hcl
terraform {
  required_version = ">= 1.6"
  backend "s3" {
    bucket         = "acme-tfstate"
    key            = "prod/network.tfstate"
    region         = "eu-west-1"
    dynamodb_table = "acme-tfstate-lock"     # prevents concurrent applies
    encrypt        = true
  }
  required_providers {
    aws = { source = "hashicorp/aws", version = "~> 5.40" }
  }
}
```

**Non-negotiables:**

- **Remote state** — local state means one person's laptop holds the truth.
- **Locking** — two concurrent applies can corrupt state.
- **Encryption** — state contains resource attributes, sometimes secrets.
- **Separate state per environment** — a single state for all envs means one bad plan can affect
  everything.
- **Pinned provider versions** — an unpinned provider upgrade can change behaviour silently.

### 5.3 The module pattern

```hcl
module "orders" {
  source = "../../modules/service"

  name           = "orders"
  environment    = "prod"
  image_digest   = var.orders_digest
  replicas       = 6
  cpu_request    = "500m"
  memory_request = "1Gi"
  memory_limit   = "1Gi"
  min_replicas   = 6
  max_replicas   = 30
}
```

**Modules are how you avoid 12 near-identical copies of a service definition** — and how you make a
cross-cutting change (adding a new required label, a new sidecar) in one place.

### 5.4 The apply workflow

```
PR:       terraform fmt -check && terraform validate && terraform plan → comment on PR
          + checkov/tfsec scan
merge:    terraform plan -out=tfplan && terraform apply tfplan    (approved, in CI)
```

**Never `terraform apply` from a laptop in production.** The plan reviewed in the PR is the change.

### 5.5 Drift and import

- `terraform plan` showing changes you didn't make = drift (someone used the console).
- Adopt existing resources with `terraform import` / `import` blocks, don't recreate.
- **Don't put secrets in Terraform variables** — they land in state. Use a secret manager and
  reference it, or `sensitive = true` + an encrypted backend at minimum.

---

## 6. Twelve-Factor Configuration

### 6.1 The rule

> **Config that varies between environments must come from the environment, not from the artifact.**

```
❌ src/main/resources/application-prod.yml  with a real DB password
❌ a jar built differently per environment
✅ one artifact + SPRING_PROFILES_ACTIVE + env vars + a secret manager
```

### 6.2 Spring's precedence (highest wins)

```
1. command-line args                    --server.port=9090
2. OS environment variables             SERVER_PORT=9090
3. application-{profile}.yml (external) /config/application.yml
4. application.yml (in the jar)
5. @PropertySource / defaults
```

**Relaxed binding** means `SPRING_DATASOURCE_URL` maps to `spring.datasource.url` — the mechanism
that makes env-var config work in containers and Kubernetes
([ch 02](./02-Kubernetes-for-Java-Developers.md)).

### 6.3 Secrets

```yaml
spring:
  config:
    import: "optional:aws-secretsmanager:/prod/orders/db"
  datasource:
    username: ${DB_USER}
    password: ${DB_PASSWORD}
```

Or with a Spring Cloud Vault / AWS Secrets Manager integration, so the app fetches secrets at
startup and they never exist as files.

### 6.4 The twelve factors that still matter

| # | Factor | Java relevance |
|---|---|---|
| 1 | Codebase | one repo, one artifact |
| 3 | Config | env vars, not profiles-in-jar |
| 4 | Backing services | DB/Redis as attachable resources |
| 6 | Processes | stateless; no local session |
| 7 | Port binding | `server.port=${PORT}` |
| 8 | Concurrency | scale out, not thread count |
| 9 | Disposability | fast startup, graceful shutdown |
| 11 | Logs | stdout as event streams |
| 12 | Admin processes | migrations as a job |

**Factor 9 is the one Java struggles with** — a 30-second start and a slow shutdown violate
"disposability". That's the argument for startup probes, CRaC, and native images
([ch 02](./02-Kubernetes-for-Java-Developers.md)).

---

## 7. Cost — Where It Goes and How to Cut It

### 7.1 The usual breakdown for a containerised service

```
compute (nodes/pods)     40–60%
database                 20–35%
network (NAT, egress)     5–15%
storage + logs            5–10%
observability SaaS        5–15%
```

### 7.2 The seven cuts, in order of impact

**1. Rightsize memory requests.** The single biggest waste in Kubernetes:

```
pods request 2 Gi, use 600 MB  →  70% of the nodes are idle but billed
```

```bash
# compare requests to actual usage
kubectl top pods --sort-by=memory
```

Set requests from measured p95 usage + 20%, not from a guess, then let the JVM size itself with
`MaxRAMPercentage`.

**2. Turn off non-prod when unused.**

```yaml
# scale staging to zero outside working hours
- cron: "0 20 * * 1-5"
  action: kubectl scale deployment --all --replicas=0 -n staging
```

A staging environment running 24/7 at 30% of prod cost is a large, avoidable line item.

**3. Commit to your baseline.** Reserved instances / savings plans for the steady floor, spot for
batch and stateless burst.

**4. Tier your storage.** S3 lifecycle policies, delete old logs, prune old DB partitions
([T11 ch05](../11-Database-Design-Optimization/05-Scaling-Partitioning-and-Replication.md)).

**5. Right-size the database.** The most common issues: an oversized instance from a launch-day
guess, an idle read replica, over-provisioned IOPS, and a long-retention backup policy nobody
reviewed.

**6. Reduce cross-AZ chatter** and use a NAT gateway only where needed (a VPC endpoint for S3 saves
the NAT per-GB charge).

**7. Control observability cost.** Metrics cardinality, log volume, trace sampling
([ch 04](./04-Observability-SRE-and-Incident-Response.md)). Observability can quietly become 15% of
the bill.

### 7.3 Attributing cost

```
tag everything: service · team · environment · cost-centre
```

```hcl
default_tags {
  tags = {
    Service     = var.name
    Environment = var.environment
    Team        = var.team
    ManagedBy   = "terraform"
  }
}
```

**Untagged infrastructure is unattributable cost** — you can't decide what to cut if you don't know
who owns it.

### 7.4 Unit economics

```
cost per request   = monthly infra / monthly requests
cost per tenant    = attributable infra / tenants
cost per order     = ...
```

These are the numbers that make cost a product decision rather than a monthly surprise. A 10%
growth in traffic that grows cost 40% is an architecture problem, not a billing problem.

---

## 8. Architecture Decisions That Drive Cost

| Decision | Cheap | Expensive | Note |
|---|---|---|---|
| Chatty cross-AZ calls | co-locate, batch, cache | per-request cross-AZ | egress adds up fast |
| Unbounded log volume | structured, sampled | INFO on every step | logs are the priciest pillar |
| High-cardinality metrics | bounded tags | ids as labels | can take down Prometheus |
| Always-on non-prod | scheduled shutdown | 24/7 | pure waste |
| Over-provisioned requests | measured + 20% | "just in case" | breaks bin-packing |
| One DB per microservice | shared cluster with schemas | 12 separate RDS instances | revisit at scale |
| Synchronous call chains | async where possible | 5 sync hops | also a latency/reliability issue |
| Per-request serverless with cold starts | containers for steady load | Lambda for steady load | serverless is for spiky |
| Full 3x replication for cold data | lifecycle to cheaper tiers | everything in hot storage | |

**The interview framing:** "cost is a non-functional requirement like latency. I'd want cost per
request on a dashboard next to p99, because otherwise it's discovered a month late by finance."

---

## 9. Security Baseline

| Control | Why |
|---|---|
| **IAM least privilege** | one role per service, scoped to its resources; no wildcards |
| **No long-lived keys** | OIDC federation for CI, instance roles for compute |
| **Encryption at rest** | default on for DB, volumes, object storage |
| **TLS everywhere** | including service-to-service (mTLS via a mesh if required) |
| **Private subnets** | databases and internal services not internet-reachable |
| **Security groups / NACLs** | deny by default, allow explicitly |
| **Secrets manager** | never in env files, never in state |
| **Audit logs** | CloudTrail/equivalent, retained and alerted on |
| **Image scanning + signed images** | ([ch 03](./03-CICD-and-Release-Strategies.md)) |
| **Network policy in k8s** | default-deny pod-to-pod |
| **Backups tested** | an untested backup is a hypothesis |

**The controls that catch most real incidents:** least-privilege IAM, no public databases, and
tested backups. Say those three first.

---

## 10. Code Examples

### 10.1 Terraform module for a Java service

```hcl
variable "name"         { type = string }
variable "environment"  { type = string }
variable "image_digest" { type = string }

variable "replicas" {
  type    = number
  default = 3
}

variable "cpu_request" {
  type    = string
  default = "500m"
}

variable "memory_request" {
  type    = string
  default = "1Gi"
}

variable "memory_limit" {
  type    = string
  default = "1Gi"
}

resource "kubernetes_deployment_v1" "this" {
  metadata {
    name      = var.name
    namespace = var.environment
    labels    = { app = var.name, env = var.environment }
  }

  spec {
    replicas = var.replicas

    strategy {
      type = "RollingUpdate"
      rolling_update {
        max_surge       = 1
        max_unavailable = 0
      }
    }

    selector { match_labels = { app = var.name } }

    template {
      metadata { labels = { app = var.name } }
      spec {
        termination_grace_period_seconds = 45
        security_context {
          run_as_non_root = true
          run_as_user     = 1000
        }

        container {
          name  = var.name
          image = "ghcr.io/acme/${var.name}@${var.image_digest}"

          port {
            container_port = 8080
            name           = "http"
          }

          env {
            name  = "SPRING_PROFILES_ACTIVE"
            value = var.environment
          }
          env {
            name = "JAVA_TOOL_OPTIONS"
            value = "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
          }

          resources {
            requests = {
              cpu    = var.cpu_request
              memory = var.memory_request
            }
            limits = {
              memory = var.memory_limit
            }
          }

          startup_probe {
            http_get { path = "/actuator/health/liveness", port = "http" }
            failure_threshold = 30
            period_seconds    = 5
          }
          liveness_probe {
            http_get { path = "/actuator/health/liveness", port = "http" }
            period_seconds    = 10
            failure_threshold = 3
          }
          readiness_probe {
            http_get { path = "/actuator/health/readiness", port = "http" }
            period_seconds    = 5
            failure_threshold = 2
          }

          lifecycle {
            pre_stop {
              exec { command = ["sh", "-c", "sleep 5"] }
            }
          }
        }
      }
    }
  }
}
```

### 10.2 Managed Postgres with a replica

```hcl
resource "aws_db_instance" "primary" {
  identifier             = "orders-${var.environment}"
  engine                 = "postgres"
  engine_version         = "16.3"
  instance_class         = var.db_instance_class
  allocated_storage      = 100
  max_allocated_storage  = 1000          # storage autoscaling
  storage_type           = "gp3"
  storage_encrypted      = true
  db_name                = "orders"
  username               = "orders"
  manage_master_user_password = true     # password lives in Secrets Manager
  multi_az               = true          # synchronous standby, automatic failover
  backup_retention_period = 14
  deletion_protection    = true
  skip_final_snapshot    = false
  performance_insights_enabled = true

  tags = { Service = "orders", Environment = var.environment, ManagedBy = "terraform" }
}

# a read replica in another AZ for reporting, never for writes
resource "aws_db_instance" "replica" {
  identifier          = "orders-${var.environment}-replica"
  replicate_source_db = aws_db_instance.primary.identifier
  instance_class      = var.db_instance_class
  publicly_accessible = false
  skip_final_snapshot = true
}
```

**`manage_master_user_password = true`** — RDS creates and rotates the password in Secrets Manager.
That removes the most common secret-handling mistake in Terraform: a password in state.

### 10.3 Environment-aware Spring config

```yaml
# application.yml — in the jar, no environment values
spring:
  application.name: orders
  datasource:
    url: ${DB_URL}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: ${DB_POOL_SIZE:10}
  data:
    redis:
      host: ${REDIS_HOST}
      port: ${REDIS_PORT:6379}
server:
  port: ${PORT:8080}
```

```yaml
# application-prod.yml — behaviour, not secrets
management:
  tracing.sampling.probability: 0.05
logging:
  level.root: INFO
```

**The line to state:** "the jar contains no environment-specific values — only defaults and
behaviour. Everything that differs comes from the environment, so one artifact runs everywhere."

### 10.4 Cost attribution with Terraform default tags

```hcl
provider "aws" {
  region = var.region
  default_tags {
    tags = {
      Service     = "orders"
      Environment = var.environment
      Team        = "checkout"
      ManagedBy   = "terraform"
      CostCenter  = "eng-42"
    }
  }
}
```

### 10.5 Scheduled scale-down for non-prod

```yaml
apiVersion: batch/v1
kind: CronJob
metadata: { name: scale-down-nonprod, namespace: platform }
spec:
  schedule: "0 20 * * 1-5"        # 8pm weekdays
  jobTemplate:
    spec:
      template:
        spec:
          serviceAccountName: scaler
          containers:
            - name: kubectl
              image: bitnami/kubectl:1.30
              command:
                - sh
                - -c
                - |
                  for d in $(kubectl get deploy -n staging -o name); do
                    kubectl scale "$d" --replicas=0 -n staging
                  done
          restartPolicy: Never
```

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Console changes in production | drift, unreproducible | IaC only; detect drift in CI |
| Local Terraform state | one laptop holds the truth | remote backend + locking |
| Secrets in variables/state | leakage in state files | Secrets Manager + `manage_master_user_password` |
| One state for all environments | one bad plan breaks everything | state per env |
| Unpinned provider versions | silent behaviour change | pin `~>` versions |
| Over-provisioned memory requests | 70% idle nodes | measure p95, +20% |
| `terraform apply` from a laptop | unaudited production change | apply in CI from a reviewed plan |
| No storage lifecycle | cold data at hot prices | lifecycle policies |
| Non-prod running 24/7 | pure waste | scheduled scale-to-zero |
| Untagged resources | unattributable cost | `default_tags` |
| Public database | breaches | private subnet + security group |
| Long-lived cloud keys | credential leakage | OIDC / instance roles |
| All services on one DB cluster without limits | noisy neighbour | per-service limits or clusters |
| Cross-AZ chatty calls | egress surprises | co-location, batching, caching |

---

## 12. Interview Questions & Model Answers

**Q1. Managed or self-hosted?**
> Managed by default — I'd only self-host for a specific constraint: data residency the managed
> offering can't meet, a cost profile that only works at very large scale, or a missing feature. The
> reason is that the ongoing cost of self-hosting isn't the install, it's the patching, backup
> testing, failover drills and 3am pages — and that's usually more expensive than the managed
> premium for a small team.

**Q2. Why infrastructure as code?**
> Because the console produces state that's undocumented, unreviewed and irreproducible. With IaC,
> a change is a pull request with a plan attached, environments are identical by construction, drift
> is visible in `terraform plan`, and rollback is a revert plus apply. The operational win is that
> the repository — not a person's memory — is the source of truth.

**Q3. What's the most common Terraform mistake you've seen?**
> Local state, or a single state file for all environments. The first means one laptop holds the
> truth and nobody can collaborate safely; the second means a bad plan can affect everything at
> once. The fix is a remote backend with locking and encryption, one state per environment, and
> pinned provider versions.

**Q4. How do you handle secrets in Terraform?**
> I avoid putting them in Terraform at all. For databases I use the managed password rotation —
> RDS generating it into Secrets Manager — and reference the secret rather than the value. The app
> fetches it at runtime. If a secret must pass through Terraform, it's marked sensitive and the
> state backend is encrypted, but I'd treat that as a fallback, not the design.

**Q5. How do you configure a Spring app across environments?**
> Twelve-factor: one artifact, and everything environment-specific comes from the environment —
> `SPRING_PROFILES_ACTIVE`, env vars with relaxed binding, and a secrets manager for credentials.
> The jar only contains defaults and behaviour, never a value. That's also what makes the same
> image work in staging and production, which is the whole point of promoting a digest.

**Q6. How do you cut cloud cost by 30%?**
> Start with attribution — if it's untagged I can't tell what to cut. Then the usual order:
> rightsize memory requests (over-provisioning wastes most of the cluster), shut down non-prod
> outside working hours, commit to the baseline with reserved capacity and use spot for
> batch, tier object storage and prune old data, and right-size the database and any idle replicas.
> Then look at cross-AZ and NAT egress, and observability spend — cardinality and log volume. The
> biggest single win is usually requests being far above actual usage.

**Q7. How do you decide serverless or containers for a Java service?**
> By traffic shape and latency sensitivity. Serverless is great for spiky or low-volume event-driven
> work, but a Spring Boot cold start of several seconds is a bad user experience — I'd need
> SnapStart, CRaC or a native image to make it viable. For steady traffic, containers are cheaper
> per request and have no cold start for most requests. So: spiky and latency-tolerant → serverless;
> steady and latency-sensitive → containers.

**Q8. How do you keep infrastructure secure?**
> Least-privilege IAM with a role per service, no long-lived keys — OIDC from CI and instance roles
> on compute. Databases and internal services in private subnets with deny-by-default security
> groups. Encryption at rest and TLS in transit. Secrets in a manager, never in state or env files.
> Audit logs on and alerted. And backups that are actually restored in a drill — an untested backup
> isn't a control.

---

## 13. Self-Check & Revision Card

- [ ] I can map the three clouds' core services by category.
- [ ] I can compare serverless/containers/VMs including the JVM cold-start issue.
- [ ] I can explain the control-vs-effort trade and when to self-host.
- [ ] I can name the cross-AZ and egress cost traps.
- [ ] I can lay out a Terraform repo with modules and per-env state.
- [ ] I can explain remote state, locking, and why not to apply from a laptop.
- [ ] I can state the twelve-factor config rule and Spring's precedence.
- [ ] I can list the cost cuts in order of impact.
- [ ] I can name the three security controls that catch most incidents.

```
┌──────────── CLOUD, IaC & COST — 60s RECALL ─────────────────────────────────┐
│ MANAGED BY DEFAULT. Self-host only for a named constraint (residency,       │
│   scale economics, missing feature). Ops burden > managed premium usually.   │
│                                                                              │
│ SERVERLESS vs CONTAINERS: traffic shape + latency. JVM cold start (3–8s)     │
│   kills Lambda for user-facing work unless SnapStart/CRaC/native.            │
│                                                                              │
│ IAC: remote state + LOCKING + encryption + STATE PER ENV + pinned providers. │
│   Apply in CI from a reviewed plan. Never from a laptop. Drift = console.    │
│   MODULES for repeated services. Secrets via Secrets Manager, not variables. │
│                                                                              │
│ CONFIG: one artifact · env vars + profile + secret manager · relaxed binding.│
│   The jar holds no environment values. Ever.                                 │
│                                                                              │
│ COST ORDER: rightsize memory requests (biggest) → shut non-prod off →        │
│   commit baseline + spot → tier storage → right-size DB/replicas →           │
│   cross-AZ/NAT egress → observability cardinality + log volume.              │
│ TAG EVERYTHING or you can't attribute it. Cost per request on the dashboard. │
│                                                                              │
│ SECURITY: least-privilege IAM · no long-lived keys (OIDC) · private subnets · │
│   encryption · secrets manager · TESTED backups.                             │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [04 · Observability, SRE & Incident Response](./04-Observability-SRE-and-Incident-Response.md) · **Up:** [README](./README.md)
