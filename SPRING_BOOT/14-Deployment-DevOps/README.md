# 14 — Deployment & DevOps

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★☆ (asked in every senior loop, usually as "how do you get this to production?")
> **Prerequisite:** [13 · Testing](../13-Testing/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Writing the code is the part everyone practises. **Operating it** is the part that decides whether
you're trusted with a production system:

- Can you get a change to production **safely, repeatedly, and reversibly**?
- When it breaks at 3am, can you find out **why** — and fix it before you understand it?
- Can you make a schema change on a 400-million-row table **without downtime**?
- Can you answer "what's running in prod, who changed it, and what does it cost" **in minutes**?

This topic is deliberately blunt about the parts that bite: probes, graceful shutdown,
backward-compatible migrations, alert fatigue, and the fact that **rollback must be designed**.

The single sentence that frames it: *"deployment is a repeatable, observable, reversible
operation — not an event."*

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [Containerising Spring Boot with Docker](./01-Containerizing-Spring-Boot-with-Docker.md) | Layers and caching, multi-stage builds, layered JARs/buildpacks, JVM flags, hardening | ⭐⭐⭐ |
| 02 | [Kubernetes for Java Developers](./02-Kubernetes-for-Java-Developers.md) | Deployment/Service/Ingress, the three probes, resource sizing, autoscaling, debugging pods | ⭐⭐⭐⭐ |
| 03 | [CI/CD & Release Strategies](./03-CICD-and-Release-Strategies.md) | Build once/promote, rolling vs blue-green vs canary, migrations, flags, rollback, supply chain | ⭐⭐⭐⭐ |
| 04 | [Observability, SRE & Incident Response](./04-Observability-SRE-and-Incident-Response.md) | Pillars, SLOs and burn-rate alerting, Java metrics, tracing, incident roles, postmortems | ⭐⭐⭐⭐⭐ |
| 05 | [Cloud, IaC & Cost](./05-Cloud-IaC-and-Cost.md) | Managed services, Terraform patterns, twelve-factor config, cost levers, security baseline | ⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
              DEPLOYMENT IS A REPEATABLE, OBSERVABLE,
                     REVERSIBLE OPERATION.

   ┌───────────────────────────────────────────────────────────────┐
   │  REPEATABLE  the same artifact + the same pipeline            │
   │              → the same result, every time                    │
   │                                                               │
   │  OBSERVABLE  you know it worked (metrics, smoke, SLO)         │
   │              and you know when it stops working               │
   │                                                               │
   │  REVERSIBLE  a flag flip, or the previous digest —            │
   │              and the schema allows it                         │
   └───────────────────────────────────────────────────────────────┘

   Miss "reversible" and every deploy is a one-way door.
```

**And the corollary that decides most incidents:**

```text
   MITIGATE BEFORE DIAGNOSE.
   Roll back, flip the flag, fail over — then understand it
   with the system healthy.
```

---

## 📊 The Decision Matrix

| Decision | Default | Alternative | Trade-off |
|---|---|---|---|
| **Image build** | buildpacks | multi-stage Dockerfile | maintenance vs control |
| **Base image** | `jre-jammy` | distroless / alpine | compatibility vs size/surface |
| **Deploy strategy** | rolling | blue-green / canary | simplicity vs blast radius |
| **Rollout gate** | readiness + smoke | canary + automated analysis | speed vs confidence |
| **Migrations** | expand/contract, separate job | app startup | safety vs simplicity |
| **Config** | env vars + secret manager | config server | simplicity vs dynamic refresh |
| **Secrets** | external secrets / OIDC | K8s Secrets | security vs convenience |
| **Memory** | request = limit | burstable | predictability vs bin-packing |
| **Autoscaling** | latency/queue | CPU | correctness vs simplicity |
| **Alerting** | SLO burn rate | cause thresholds | signal vs coverage |
| **Tracing** | 1% head + tail sampling | 100% | cost vs completeness |
| **Infra** | Terraform modules | console | reproducibility vs speed |
| **Rollback** | flag flip | previous digest | seconds vs a minute |

---

## ⚠️ The Traps

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Liveness should check the DB" | "It's thorough" | A DB blip then **restarts every pod** — dependency checks belong in **readiness** |
| "`initialDelaySeconds: 10` is enough" | "The JVM starts fast" | A 30 s boot → restart loop. Use a **`startupProbe`** |
| "`-Xmx512m` in the Dockerfile" | "Explicit is better" | Ignores the container limit — **`MaxRAMPercentage`** |
| "Rebuild the image for prod" | "Same source, same thing" | You shipped something you never tested — **promote the digest** |
| "Deploy by `:latest`" | "It's convenient" | Tags are mutable — deploy by **digest** |
| "Migrations on app startup" | "Always in sync" | N replicas **race**, slow boot blocks readiness. Use a **job** |
| "Roll back the deploy" | "Just undo it" | Only works if the migration was **backward compatible** |
| "Canary without analysis" | "We watched the dashboard" | That's a slow rolling update — automate the gate |
| "Alert on CPU > 80%" | "It's a signal" | Alert on **symptoms/SLO burn**; causes belong on dashboards |
| "Average p99 across instances" | "It's a summary" | Percentiles **don't aggregate** — use histograms |
| "Log everything at INFO" | "More data is better" | Logs are the priciest pillar — log outcomes |
| "Tag metrics with orderId" | "Great filtering" | **Unbounded cardinality** will kill Prometheus |
| "Human error" as a root cause | "Someone made a mistake" | What made it **easy**, and what would have **caught it**? |
| "We'll fix reliability after this feature" | "Ships matter" | That's what the **error budget** is for |
| "Non-prod runs 24/7" | "It's convenient" | Pure waste — schedule scale-to-zero |
| "Console changes are fine for urgent fixes" | "No time for a PR" | That's **drift** — the next plan will fight you |

---

## 🎤 The 20 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Walk me through your Dockerfile | [Ch 01](./01-Containerizing-Spring-Boot-with-Docker.md) |
| 2 | Why does Docker rebuild everything on a one-line change? | [Ch 01](./01-Containerizing-Spring-Boot-with-Docker.md) |
| 3 | Container vs VM? | [Ch 01](./01-Containerizing-Spring-Boot-with-Docker.md) |
| 4 | Why does `-Xmx` break in containers? | [Ch 01](./01-Containerizing-Spring-Boot-with-Docker.md) |
| 5 | How do you make the image smaller and safer? | [Ch 01](./01-Containerizing-Spring-Boot-with-Docker.md) |
| 6 | Liveness vs readiness? | [Ch 02](./02-Kubernetes-for-Java-Developers.md) |
| 7 | How do you handle a 30-second JVM boot in k8s? | [Ch 02](./02-Kubernetes-for-Java-Developers.md) |
| 8 | How do you size JVM memory in a pod? | [Ch 02](./02-Kubernetes-for-Java-Developers.md) |
| 9 | Why doesn't CPU autoscaling work here? | [Ch 02](./02-Kubernetes-for-Java-Developers.md) |
| 10 | A pod is in CrashLoopBackOff — what now? | [Ch 02](./02-Kubernetes-for-Java-Developers.md) |
| 11 | CI vs continuous delivery vs continuous deployment? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 12 | How do you deploy with zero downtime? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 13 | Blue-green or canary? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 14 | How do you run a migration during a rolling deploy? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 15 | How do you roll back? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 16 | How do you secure the pipeline? | [Ch 03](./03-CICD-and-Release-Strategies.md) |
| 17 | Monitoring vs observability? | [Ch 04](./04-Observability-SRE-and-Incident-Response.md) |
| 18 | Which Java metrics do you watch? | [Ch 04](./04-Observability-SRE-and-Incident-Response.md) |
| 19 | Production is down — walk me through it | [Ch 04](./04-Observability-SRE-and-Incident-Response.md) |
| 20 | How do you cut cloud cost 30%? | [Ch 05](./05-Cloud-IaC-and-Cost.md) |

---

## 🛠️ What You Actually Produce

| Artifact | Contents |
|---|---|
| Dockerfile / buildpacks | multi-stage, non-root, layered, exec-form entrypoint |
| Kubernetes manifests | Deployment + Service + Ingress + PDB + HPA + ConfigMap |
| Probes | startup/liveness/readiness with the right dependencies |
| `application.yml` | env-var placeholders, graceful shutdown, actuator exposure |
| Pipeline | build → test → package → scan → sign → migrate → deploy → verify |
| Migration plan | expand → backfill → contract, with `lock_timeout` |
| Runbook | per alert: what it means, first three commands, escalation |
| Dashboards | RED/USE + JVM + SLO burn |

---

## 📐 The Reference Architecture

```text
   git push
      │
      ▼
 ┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
 │ CI: build   │─►│ test + scan  │─►│ publish by   │─►│ migrate (job)│
 │ + static    │  │ unit/IT/CVE  │  │ DIGEST +sign │  │ expand-only  │
 └─────────────┘  └──────────────┘  └──────┬───────┘  └──────┬───────┘
                                            │                 │
                                            ▼                 ▼
                                     ┌──────────────────────────────┐
                                     │  K8s: rolling / canary       │
                                     │  startup→readiness→liveness  │
                                     │  maxUnavailable 0 + preStop  │
                                     └──────────────┬───────────────┘
                                                    │
                    ┌───────────────────────────────┼───────────────────────────┐
                    ▼                               ▼                           ▼
            ┌───────────────┐              ┌────────────────┐          ┌───────────────┐
            │ metrics (SLO) │              │ traces 1%+tail │          │ logs: JSON    │
            │ burn-rate     │              │ error/slow 100%│          │ + traceId     │
            │ alerts → page │              └────────────────┘          └───────────────┘
            └───────┬───────┘
                    ▼
            on-call: MITIGATE (rollback / flag) then diagnose → postmortem
```

**Rules the diagram encodes:**
- The artifact is built **once** and promoted by digest.
- Migrations run **before** the deploy, and are backward compatible.
- Rollout is gated by readiness and, in prod, by a canary analysis.
- Every deploy is a **marker** on the dashboards — so "what changed" is answerable in seconds.

---

## ✅ The Self-Check

- [ ] I can explain layer caching and write a multi-stage Dockerfile.
- [ ] I can explain `MaxRAMPercentage` and why `-Xmx` is wrong.
- [ ] I can explain liveness vs readiness and the DB-outage rule.
- [ ] I can design a zero-downtime rollout including `preStop`.
- [ ] I can size JVM memory and CPU for a pod.
- [ ] I can explain why CPU autoscaling fails and what to use instead.
- [ ] I can debug a CrashLoopBackOff in four commands.
- [ ] I can explain build-once-promote and digest-based deploys.
- [ ] I can compare rolling, blue-green and canary.
- [ ] I can run an expand/contract migration.
- [ ] I can describe three kinds of rollback and what enables each.
- [ ] I can define SLI/SLO/error budget and burn-rate alerting.
- [ ] I can name the Java metrics that matter, including the connection pool.
- [ ] I can run an incident with roles and a comms cadence.
- [ ] I can write a blameless postmortem with owned action items.
- [ ] I can lay out a Terraform repo with modules and per-env state.
- [ ] I can list the cost cuts in order of impact.

---

## 🧪 The Practice Drill

- [ ] Write a Dockerfile and prove that a code-only change rebuilds ~2 MB, not ~60 MB.
- [ ] Kill a pod with `SIGTERM` mid-request and verify zero dropped requests (then remove the
      `preStop` hook and watch them drop).
- [ ] Set `MaxRAMPercentage=95` and OOMKill a pod; read the exit code and explain it.
- [ ] Break the liveness probe to check the DB and observe a restart storm during a DB pause.
- [ ] Run a rolling deploy with `maxUnavailable: 0` and confirm no 5xx during it.
- [ ] Deploy a canary at 5% with an automated analysis on error rate and watch it auto-abort.
- [ ] Add a `NOT NULL` column to a large table with `NOT VALID` + `VALIDATE` and check the lock.
- [ ] Roll back a deploy that included an irreversible migration and explain what breaks.
- [ ] Build the four SLO burn-rate alerts and verify the fast one fires in under 5 minutes.
- [ ] Run a postmortem on a simulated incident and produce action items with owners.

---

**Related folders:** ← [13 · Testing](../13-Testing/README.md) · **Root:** [Spring Boot roadmap](../README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
