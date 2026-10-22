# 03 · CI/CD & Release Strategies

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do you deploy without downtime?")
> **Prerequisite:** [02 · Kubernetes for Java Developers](./02-Kubernetes-for-Java-Developers.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. CI vs CD vs CD](#1-ci-vs-cd-vs-cd)
- [2. Mental Model — The Delivery Pipeline](#2-mental-model--the-delivery-pipeline)
- [3. The Pipeline, Stage by Stage](#3-the-pipeline-stage-by-stage)
- [4. Deployment Strategies](#4-deployment-strategies)
- [5. Database Migrations in a Pipeline](#5-database-migrations-in-a-pipeline)
- [6. Feature Flags & Decoupling Deploy from Release](#6-feature-flags--decoupling-deploy-from-release)
- [7. Rollback](#7-rollback)
- [8. Secrets & Supply Chain](#8-secrets--supply-chain)
- [9. Multi-Environment & Promotion](#9-multi-environment--promotion)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> **CI** = build + test every change automatically. **Continuous delivery** = every change is
> deployable, deploy is a button. **Continuous deployment** = every green change goes to prod
> automatically.
>
> **Build the artifact once and promote it.** Rebuilding per environment means what you tested
> isn't what you shipped — the single most common release bug.
>
> Deployment strategies: **rolling** (default, needs correct probes), **blue-green** (instant
> switch and instant rollback, double the infrastructure), **canary** (small % of real traffic, the
> best signal, the most machinery), **A/B** (routing by user attribute for experiments).
>
> **Database migrations are the hard part.** Expand → migrate → contract, backward-compatible at
> every step, because old and new code run simultaneously during a rollout.
>
> **Rollback must be designed, not hoped for.** If the migration isn't backward compatible, "roll
> back the deploy" doesn't work — you need a forward fix or a restore.

---

## 1. CI vs CD vs CD

| Term | Meaning | Gate |
|---|---|---|
| **CI** (continuous integration) | every push builds and tests | none — it's automatic |
| **Continuous delivery** | every green build is *deployable* | a human clicks deploy |
| **Continuous deployment** | every green build *is deployed* | automated, feature flags gate exposure |

**The progression that matters:** CI without CD means you still have a "release process" that's
manual, slow and scary. CD without feature flags means every deploy is a user-visible event.

**The senior position:** "I'd push for continuous deployment *with* feature flags — deploys become
boring and frequent, risk is decoupled from release, and the rollback for a bad feature is a flag
flip, not a redeploy."

---

## 2. Mental Model — The Delivery Pipeline

```
  commit ──► BUILD ──► TEST ──► PACKAGE ──► PUBLISH ──► DEPLOY ──► VERIFY
              │         │         │           │           │          │
           compile   unit+IT   container   registry    staging    health/SLO
           static    contract   layered     signed     canary     auto-rollback
                     load(nightly)          scanned     prod
                                                      
     ┌──────────────────────────────────────────────────────────┐
     │  THE ARTIFACT IS BUILT ONCE AND PROMOTED, NEVER REBUILT. │
     └──────────────────────────────────────────────────────────┘
```

**The invariant:** the image digest that passed staging is byte-identical to the one in production.
Promotion changes a *configuration reference*, not the artifact.

```
❌ build on PR → build again on merge → build again for staging → build again for prod
✅ build once → tag v1.2.3 / sha256:abc → promote the same digest through environments
```

---

## 3. The Pipeline, Stage by Stage

### 3.1 Stage design

| Stage | Runs on | Budget | Fails the build? |
|---|---|---|---|
| compile + static analysis | every push | < 2 min | yes |
| unit tests | every push | < 3 min | yes |
| integration tests (Testcontainers) | every PR | < 10 min | yes |
| security scan (deps + image) | every PR | < 3 min | yes (high/critical) |
| package + publish | merge to main | < 3 min | yes |
| deploy to staging | merge to main | < 5 min | yes |
| smoke tests | after staging deploy | < 2 min | yes |
| deploy to prod (canary) | release / auto | < 15 min | auto-rollback |
| load + mutation tests | nightly | 30–60 min | alert, not block |

**Rule:** the pipeline's job is to **fail fast and fail loudly**. Every stage that can run in
parallel should.

### 3.2 Artifact promotion

```yaml
# build once, tag by commit sha AND semver
- run: docker buildx build --push
        -t registry/acme/orders:${{ github.sha }}
        -t registry/acme/orders:1.2.3 .
- run: cosign sign registry/acme/orders:1.2.3          # supply chain

# deploy by DIGEST, not tag — tags are mutable
- run: kubectl set image deployment/orders
        orders=registry/acme/orders@sha256:abc123 -n staging
- run: kubectl set image deployment/orders
        orders=registry/acme/orders@sha256:abc123 -n prod
```

**Why digest and not tag:** a tag can be repointed. `:latest` in production is a bet that nobody
pushed since your test — and that bet loses.

### 3.3 Environment parity

| Config | Where it lives | Changes per env? |
|---|---|---|
| image digest | deploy manifest | **no** (same digest) |
| env vars / profiles | ConfigMap, secrets | yes |
| resource limits | overlay/Helm values | usually yes |
| replica count | overlay | yes |
| feature flags | flag service | yes |

**Parity rule:** the *artifact* is identical; only *configuration* differs. If staging has 1
replica and prod has 20, you're not testing the same concurrency behaviour — acknowledge that and
align the important bits.

---

## 4. Deployment Strategies

### 4.1 Rolling update

```
[v1] [v1] [v1]  →  [v1][v1][v2]  →  [v1][v2][v2]  →  [v2][v2][v2]
```

- ✅ No extra infrastructure, built into Kubernetes.
- ❌ Two versions serve traffic simultaneously — **both must be compatible** with the DB schema.
- ❌ Rollback is another rolling update (slow).
- Needs: correct readiness probes, `maxUnavailable: 0`, `preStop` sleep.

**This is the default and it's fine for most services.** The constraint it imposes — version
compatibility during the overlap — is the same constraint any blue-green or canary imposes.

### 4.2 Blue-green

```
   ┌─────────┐          ┌─────────┐
   │  BLUE   │  live    │  GREEN  │  idle, fully deployed
   │  v1     │◄─────────│  v2     │
   └─────────┘          └─────────┘
        │                    │
        └──── switch LB ─────┘   (instant, reversible)
```

- ✅ Instant switch **and** instant rollback (switch back).
- ✅ Green is fully tested before any traffic.
- ❌ **Double the infrastructure** during the switch.
- ❌ Database migrations must be compatible with *both* versions (blue still running).
- ❌ Long-lived connections and in-flight work need draining.

```bash
kubectl apply -f green-deployment.yaml
kubectl wait --for=condition=available deployment/orders-green
kubectl patch service orders -p '{"spec":{"selector":{"version":"green"}}}'
# rollback:
kubectl patch service orders -p '{"spec":{"selector":{"version":"blue"}}}'
```

### 4.3 Canary

```
   95% ──► [v1] [v1] [v1]
    5% ──► [v2]          ← measure error rate + latency
            │
            ├─ metrics OK  → 25% → 50% → 100%
            └─ metrics bad → route 0% (rollback)
```

- ✅ **The best signal** — real traffic, real data, real dependencies.
- ✅ Blast radius is bounded and you find out with 5% of users, not all of them.
- ❌ Needs traffic splitting (service mesh, ingress weights, or two Deployments + weights).
- ❌ Statistical subtlety: 5% of traffic may not be enough to detect a 1% error increase. Size the
  canary against the metric you're watching.

```yaml
# Argo Rollouts
spec:
  strategy:
    canary:
      steps:
        - setWeight: 5
        - pause: { duration: 10m }
        - analysis:
            templates: [{ templateName: error-rate }]
        - setWeight: 25
        - pause: { duration: 10m }
        - setWeight: 100
```

```yaml
# the automated gate — this is what makes canary worth it
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata: { name: error-rate }
spec:
  metrics:
    - name: error-rate
      interval: 1m
      successCondition: result[0] < 0.01
      failureLimit: 2
      provider:
        prometheus:
          address: http://prometheus:9090
          query: |
            sum(rate(http_server_requests_seconds_count{app="orders",status=~"5.."}[2m]))
            /
            sum(rate(http_server_requests_seconds_count{app="orders"}[2m]))
```

**A canary without automated analysis is just a slow rolling update.** The analysis is the point.

### 4.4 A/B testing and shadow

| Strategy | Routes by | Purpose |
|---|---|---|
| **A/B** | user attribute (header, cohort) | product experiments, not deploys |
| **Shadow / mirror** | copy of traffic sent to v2, response discarded | validate with real traffic, zero risk |
| **Feature flag** | runtime config | decouple deploy from release |

**Shadow traffic is underrated** for risky changes (a new pricing engine, a rewritten search):
mirror production requests to the new version, compare outputs, ship with confidence.

### 4.5 Choosing

| Situation | Strategy |
|---|---|
| Standard service, good probes | **rolling** |
| Need instant rollback, stateful or risky | **blue-green** |
| High-traffic, metric-driven, mature observability | **canary** |
| Validating a risky rewrite | **shadow** |
| User-visible behaviour change | **feature flag** (with any of the above) |

---

## 5. Database Migrations in a Pipeline

### 5.1 The core constraint

```
during a rolling deploy, v1 and v2 run AT THE SAME TIME
⇒ the schema must satisfy BOTH
```

**Therefore: every migration must be backward compatible with the previous release.**

### 5.2 Expand → migrate → contract

```
Release N:    add column (nullable)         ← v1 ignores it; v2 writes it
Release N+1:  backfill in batches           ← separate job, no locking
Release N+2:  start reading the new column  ← v2 reads both, falls back
Release N+3:  add NOT NULL / drop old col   ← v1 is gone
```

| Step | Safe operation | Unsafe operation |
|---|---|---|
| add column | `ADD COLUMN ... NULL` (metadata-only) | `ADD COLUMN ... NOT NULL DEFAULT` (old PG rewrites) |
| backfill | batched `UPDATE ... WHERE id BETWEEN` | one big `UPDATE` (locks, bloat) |
| constraint | `ADD CONSTRAINT ... NOT VALID` then `VALIDATE` | `ADD CONSTRAINT` with a scan |
| index | `CREATE INDEX CONCURRENTLY` | `CREATE INDEX` (blocks writes) |
| rename | add new + dual-write + drop old | `ALTER ... RENAME` |
| drop | only after no code references it | dropping during the same release |

### 5.3 Who runs migrations

| Option | Pros | Cons |
|---|---|---|
| **App startup (Flyway `migrate` on boot)** | simple, always in sync | every replica races; a slow migration blocks startup; rollback runs nothing |
| **Separate init job / pipeline step** | one runner, controlled, observable | needs coordination with the deploy |
| **DBA-gated for prod** | review + safety | slow, doesn't scale |

**The professional answer:** "migrations run as a discrete pipeline step (a Kubernetes Job or an
init container), not on every replica's startup, with a lock so only one runs, and with a
`lock_timeout` so a blocked migration fails fast instead of queueing all traffic behind it."

```yaml
# Flyway in the pipeline, not at startup
- run: ./mvnw -q flyway:migrate \
        -Dflyway.url=$DB_URL -Dflyway.user=$DB_USER -Dflyway.password=$DB_PASS
  env: { FLYWAY_LOCK_RETRY_COUNT: 0 }
```

```yaml
# or a Kubernetes Job before the Deployment
apiVersion: batch/v1
kind: Job
metadata: { name: orders-migrate-1-2-3 }
spec:
  backoffLimit: 0
  template:
    spec:
      restartPolicy: Never
      containers:
        - name: migrate
          image: registry/acme/orders@sha256:abc123
          command: ["java", "-cp", "/app/app.jar", "org.flywaydb.core.Flyway", ...]
```

### 5.4 The irreversible migration

Some changes genuinely can't be backward compatible: dropping a column, changing a type,
splitting a table. Your options:

1. **Never do it in one step** — always expand/contract, even if it takes three releases.
2. **Back up + accept downtime** for the rare case (and schedule it).
3. **Dual-write during the transition** so both schemas stay correct.

**Say the consequence:** "if the migration is irreversible, rolling back the deploy leaves the code
expecting a schema that no longer exists — so rollback is not an option, and that must be a
deliberate, communicated decision."

---

## 6. Feature Flags & Decoupling Deploy from Release

### 6.1 The idea

```
DEPLOY  = code is running in production     (frequent, boring, invisible)
RELEASE = users can see it                  (controlled by a flag)
```

### 6.2 Flag types

| Type | Lifetime | Example |
|---|---|---|
| **Release** | days–weeks | `new-checkout` |
| **Experiment** | weeks | `pricing-variant-b` |
| **Ops / kill switch** | permanent | `disable-recommendations` |
| **Permission** | permanent | `beta-access` |

### 6.3 Implementation

```java
@Service
public class CheckoutService {
    private final FeatureFlags flags;

    public CheckoutResult checkout(Cart cart) {
        if (flags.isEnabled("new-checkout", cart.customerId())) {
            return newCheckout(cart);
        }
        return legacyCheckout(cart);
    }
}
```

```yaml
# Unleash / LaunchDarkly / Flagsmith — or a config table for simplicity
features:
  new-checkout:
    enabled: true
    rollout: 10              # percent of users
    allowlist: ["internal-testers"]
```

### 6.4 The disciplines that make flags safe

- **Every flag has an owner and an expiry date.** Stale flags are permanent `if` branches nobody
  understands — flag debt is real and it accumulates fast.
- **Test both paths in CI.** A flag that's never tested "off" is a landmine.
- **Flags must not break the schema.** A flag that changes data format needs its own migration plan.
- **A kill switch must be fast** — reading from a local cache, not a remote call per request.
- **Don't nest flags.** Combinatorial explosion in tests.

```java
@ParameterizedTest
@ValueSource(booleans = {true, false})
void checkoutWorks(boolean newCheckoutEnabled) {
    flags.set("new-checkout", newCheckoutEnabled);
    assertThat(service.checkout(cart).status()).isEqualTo(CONFIRMED);
}
```

---

## 7. Rollback

### 7.1 The three kinds

| Kind | Mechanism | Speed | Works when |
|---|---|---|---|
| **Flag flip** | disable the feature | seconds | the change was behind a flag |
| **Redeploy previous image** | `kubectl rollout undo` | 30 s–2 min | schema is backward compatible |
| **Forward fix** | deploy a patch | minutes | rollback is impossible |
| **Database restore (PITR)** | restore point-in-time | 30 min–hours | data was corrupted |

**The best rollback is a flag flip.** That's the main argument for flags: it turns a 2-minute
redeploy into a 2-second config change, with no deployment at all.

### 7.2 Making rollback possible

- **Backward-compatible migrations** (expand/contract) — otherwise you can't roll back.
- **Immutable images** — you can always run the previous digest.
- **`revisionHistoryLimit`** high enough to hold the previous versions.
- **No destructive data changes in the same release** — a `DROP COLUMN` can't be rolled back.
- **Rollback tested** — a rollback path you've never run is a hypothesis.

### 7.3 Automatic rollback

```yaml
# Argo Rollouts aborts and reverts on a failed analysis
analysis:
  failureLimit: 2
  successfulRunHistoryLimit: 3
```

```yaml
# Kubernetes: progressDeadlineSeconds triggers a failed rollout, but NOT a revert
spec:
  progressDeadlineSeconds: 600
# a rollout that never becomes ready stays "Progressing=False" — you must
# act on it: `kubectl rollout undo` in the pipeline or via Argo/Flagger
```

**Important nuance:** `kubectl rollout` does **not** automatically revert a failed rollout — it
stops and waits. Automatic revert needs Argo Rollouts, Flagger, or a pipeline step that checks
`rollout status` and undoes on failure.

---

## 8. Secrets & Supply Chain

### 8.1 Secrets in CI

| ❌ Never | ✅ Instead |
|---|---|
| secrets in the repo (`application-prod.yml`) | a secret manager, injected at runtime |
| secrets in Dockerfile `ENV`/`ARG` | runtime env from a secret store |
| secrets echoed in CI logs | mask them; never `cat` a secret |
| long-lived cloud keys in CI | OIDC federation to the cloud provider |
| the same secret across environments | separate credentials per env |

```yaml
# GitHub Actions OIDC — no long-lived credentials at all
permissions:
  id-token: write
  contents: read
steps:
  - uses: aws-actions/configure-aws-credentials@v4
    with:
      role-to-assume: arn:aws:iam::123:role/github-deploy
      aws-region: eu-west-1
```

### 8.2 Supply chain

```
source ──► build ──► image ──► registry ──► cluster
   │         │         │          │            │
 SBOM     hermetic   sign      scan         verify signature
          build      (cosign)  (Trivy)      (admission controller)
```

- **SBOM** — `syft`/`cyclonedx` generate it at build time; it's how you answer "are we affected?"
  in five minutes instead of five days.
- **Sign images** with `cosign` (keyless, via OIDC) and **verify at admission** (Kyverno/Gatekeeper
  policy: only signed images from your registry).
- **Pin base images by digest**; rebuild regularly so base patches reach production.
- **SLSA provenance** — attest which source commit and build produced the artifact.

**The answer interviewers like:** "the pipeline produces a signed artifact with an SBOM, deploys by
digest, and the cluster rejects unsigned images — so 'what's running in prod' is answerable and
tampering is detectable."

---

## 9. Multi-Environment & Promotion

### 9.1 Environments

```
dev      ──► PR preview / ephemeral      automatic, per-branch
staging  ──► main                        automatic, production-like
prod     ──► tag / manual approval       canary → full
```

### 9.2 Ephemeral preview environments

```yaml
# per-PR namespace, torn down on merge
- run: |
    kubectl create namespace pr-${{ github.event.number }}
    helm upgrade --install orders ./chart -n pr-${{ github.event.number }} \
      --set image.tag=${{ github.sha }}
```

**Preview environments are the highest-leverage QA investment** for a team with many PRs: reviewers
click a link instead of pulling a branch.

### 9.3 Promotion, not rebuild

```yaml
stages:
  - deploy-staging
  - verify-staging        # smoke + contract + a small load test
  - deploy-prod-canary
  - verify-canary         # automated analysis
  - deploy-prod
```

**Each stage deploys the same digest.** The only things that change are config and replica count.

---

## 10. Code Examples

### 10.1 A complete GitHub Actions pipeline

```yaml
name: ci-cd
on:
  push: { branches: [main] }
  pull_request:

env:
  IMAGE: ghcr.io/acme/orders

jobs:
  build-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: '21', cache: maven }
      - run: mvn -B -q spotless:check
      - run: mvn -B -q test
      - run: mvn -B -q verify          # Testcontainers integration tests
      - uses: actions/upload-artifact@v4
        if: always()
        with: { name: reports, path: 'target/*-reports' }

  security:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: mvn -B -q org.owasp:dependency-check-maven:check -DfailBuildOnCVSS=7

  publish:
    if: github.ref == 'refs/heads/main'
    needs: [build-test, security]
    runs-on: ubuntu-latest
    permissions: { contents: read, packages: write, id-token: write }
    outputs:
      digest: ${{ steps.build.outputs.digest }}
    steps:
      - uses: actions/checkout@v4
      - uses: docker/setup-buildx-action@v3
      - uses: docker/login-action@v3
        with: { registry: ghcr.io, username: ${{ github.actor }}, password: ${{ secrets.GITHUB_TOKEN }} }
      - id: build
        uses: docker/build-push-action@v6
        with:
          push: true
          tags: ${{ env.IMAGE }}:${{ github.sha }}
          cache-from: type=gha
          cache-to: type=gha,mode=max
      - run: |
          trivy image --severity HIGH,CRITICAL --exit-code 1 ${{ env.IMAGE }}:${{ github.sha }}
          cosign sign --yes ${{ env.IMAGE }}@${{ steps.build.outputs.digest }}

  deploy-staging:
    if: github.ref == 'refs/heads/main'
    needs: publish
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - uses: actions/checkout@v4
      - run: |
          ./mvnw -q flyway:migrate -Dflyway.url=$STAGING_DB_URL -Dflyway.user=$STAGING_DB_USER
        env: { FLYWAY_PASSWORD: ${{ secrets.STAGING_DB_PASSWORD }} }
      - run: |
          helm upgrade --install orders ./chart -n staging --wait --timeout 5m \
            --set image.digest=${{ needs.publish.outputs.digest }}
      - run: ./scripts/smoke.sh https://staging.example.com

  deploy-prod:
    if: github.ref == 'refs/heads/main'
    needs: deploy-staging
    runs-on: ubuntu-latest
    environment: production          # manual approval gate
    steps:
      - uses: actions/checkout@v4
      - run: |
          helm upgrade --install orders ./chart -n prod --wait --timeout 10m \
            --set image.digest=${{ needs.publish.outputs.digest }}
      - run: kubectl argo rollouts status orders -n prod --timeout 15m
```

**Note the three properties that matter:** the digest is built once and reused; migrations run
before the deploy; the prod deploy is gated and verified.

### 10.2 Helm values per environment

```yaml
# values.yaml (defaults)
image:
  repository: ghcr.io/acme/orders
  digest: ""
replicaCount: 2
resources:
  requests: { memory: 1Gi, cpu: 500m }
  limits:   { memory: 1Gi, cpu: 2 }
```

```yaml
# values-prod.yaml
replicaCount: 6
resources:
  requests: { memory: 2Gi, cpu: 1000m }
  limits:   { memory: 2Gi, cpu: 2 }
autoscaling:
  enabled: true
  minReplicas: 6
  maxReplicas: 30
```

### 10.3 Smoke test script

```bash
#!/usr/bin/env bash
set -euo pipefail
BASE="$1"

for i in {1..30}; do
  if curl -fsS "$BASE/actuator/health/readiness" >/dev/null; then break; fi
  sleep 2
done

code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/products?page=1")
[[ "$code" == "200" ]] || { echo "products returned $code"; exit 1; }

# assert the deployed version
curl -fsS "$BASE/actuator/info" | grep -q "$GIT_SHA"
echo "smoke ok"
```

**Assert the version.** A smoke test that passes against the *old* pods is worse than none.

### 10.4 Feature flag with a local cache (fast kill switch)

```java
@Component
public class FeatureFlags {
    private final Map<String, Boolean> local = new ConcurrentHashMap<>();
    private final FlagClient client;

    @Scheduled(fixedDelay = 10_000)
    void refresh() { local.putAll(client.fetchAll()); }   // never call out per request

    public boolean isEnabled(String flag, Long userId) {
        var override = local.get(flag + ":" + userId);     // per-user allowlist
        if (override != null) return override;
        return local.getOrDefault(flag, false);
    }
}
```

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Rebuilding per environment | what you tested ≠ what you shipped | build once, promote the digest |
| Deploying by mutable tag | surprise version in prod | deploy by digest |
| Migrations on app startup | N replicas race; slow boot | separate migration job |
| Irreversible migration in a release | rollback impossible | expand/contract |
| `NOT NULL DEFAULT` on a big table | table rewrite, long lock | nullable + backfill + `NOT VALID` |
| Canary without analysis | just a slow rolling update | automated metric gate |
| Flags with no expiry | permanent dead branches | owner + expiry date |
| No smoke test after deploy | broken deploy until users complain | verify version + health |
| Long-lived cloud keys in CI | credential leak | OIDC federation |
| Unsigned images | supply-chain risk | cosign + admission policy |
| `kubectl rollout undo` assumed automatic | a failed rollout stays failed | pipeline check or Argo/Flagger |
| No rollback drill | the rollback path fails when needed | practise quarterly |
| Manual prod deploys with no checklist | human error at 5pm Friday | pipeline with gates |

---

## 12. Interview Questions & Model Answers

**Q1. What's the difference between continuous delivery and continuous deployment?**
> Delivery means every green build is *deployable* and the deploy is a click; deployment means it
> happens automatically. Deployment requires more confidence in testing and observability, and in
> practice it needs feature flags so a deploy isn't a user-visible release.

**Q2. Walk me through your deployment pipeline.**
> Compile and static analysis, then unit tests, then integration tests with Testcontainers, then a
> security scan. On merge to main I build the image **once** — layered, non-root, signed — push it
> by digest, run migrations as a separate job, deploy to staging, and smoke test. For prod I promote
> the same digest behind a canary with automated analysis on error rate and p99, and roll back
> automatically if it fails. The one invariant throughout is that the artifact never gets rebuilt.

**Q3. How do you deploy without downtime?**
> A rolling update with `maxUnavailable: 0`, a readiness probe that only passes when the app can
> serve, a `preStop` sleep so the load balancer stops routing before shutdown, and a termination
> grace period longer than Spring's graceful shutdown timeout. Plus backward-compatible migrations,
> because old and new pods overlap during the rollout.

**Q4. Blue-green or canary?**
> Blue-green for instant switch and instant rollback, at the cost of double infrastructure — good
> for stateful or risky services where I want a fast escape. Canary when I have mature metrics,
> because it validates with real traffic and limits blast radius, at the cost of needing traffic
> splitting and automated analysis. A canary without analysis is just a slow rolling update.

**Q5. How do you handle database migrations in a rolling deploy?**
> Expand/contract, because old and new code run simultaneously and the schema has to satisfy both.
> Add a nullable column, backfill in batches, dual-write, switch reads, then add the constraint as
> `NOT VALID` and validate it, and only drop the old column in a later release. And migrations run
> as a discrete job, not on every replica's startup, with a `lock_timeout` so a blocked migration
> fails fast.

**Q6. How do you roll back?**
> In order of preference: flip a feature flag — seconds, no deploy; redeploy the previous image
> digest — a minute; forward fix if the migration was irreversible. The requirement that makes
> the middle one possible is that migrations are backward compatible, so I design for that
> explicitly. And I test rollback, because an untested rollback path is a guess.

**Q7. How do you secure the pipeline?**
> No long-lived credentials — OIDC federation from CI to the cloud. Secrets injected at runtime
> from a secret manager, never in the repo or the image. Images signed with cosign and verified by
> an admission policy, an SBOM generated at build time so I can answer "are we affected" quickly,
> and dependencies plus images scanned with a build-failing threshold.

**Q8. Why deploy by digest instead of a tag?**
> Because tags are mutable — someone can repoint `:latest` or even a version tag. A digest is the
> content hash, so I know that what's running in production is byte-identical to what passed
> staging. It also makes rollback unambiguous.

**Q9. How do feature flags change your release process?**
> They decouple deploy from release. Deploys become frequent and boring, and exposing a feature is
> a separate, reversible config change — which also means the rollback for a bad feature is a flag
> flip rather than a redeploy. The discipline is that flags have owners and expiry dates, both
> paths are tested in CI, and a flag never changes the data format without its own migration plan.

---

## 13. Self-Check & Revision Card

- [ ] I can distinguish CI, continuous delivery and continuous deployment.
- [ ] I can explain build-once-promote and why digests beat tags.
- [ ] I can describe rolling, blue-green, canary and shadow with trade-offs.
- [ ] I can explain why canary needs automated analysis.
- [ ] I can run expand/contract migrations safely.
- [ ] I can explain where migrations run and why not on startup.
- [ ] I can describe the three rollback kinds and what enables each.
- [ ] I can list the supply-chain steps.

```
┌──────────────── CI/CD — 60s RECALL ─────────────────────────────────────────┐
│ CI = build+test · DELIVERY = deployable (button) · DEPLOYMENT = automatic.   │
│                                                                              │
│ BUILD ONCE, PROMOTE THE DIGEST. Rebuilding per env = shipping something      │
│ you never tested. Deploy by DIGEST, not tag (tags are mutable).              │
│                                                                              │
│ ROLLING  default, needs probes + maxUnavailable 0 + preStop                  │
│ BLUE-GREEN  instant switch + instant rollback, 2x infra                      │
│ CANARY  best signal, needs automated analysis (else it's a slow rolling)     │
│ SHADOW  mirror traffic, compare, zero risk (risky rewrites)                  │
│                                                                              │
│ MIGRATIONS: expand → backfill → contract. Old AND new code run together.     │
│   Run as a JOB, not on startup (N replicas race). NOT VALID + VALIDATE.      │
│   Irreversible migration ⇒ ROLLBACK IS NOT AN OPTION ⇒ say so.               │
│                                                                              │
│ ROLLBACK: flag flip (seconds) > previous digest (~1 min) > forward fix >     │
│   PITR. kubectl rollout does NOT auto-revert — Argo/Flagger does.            │
│                                                                              │
│ SUPPLY CHAIN: OIDC (no static keys) · sign (cosign) · SBOM · scan ·          │
│   admission policy rejects unsigned images.                                  │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Kubernetes for Java Developers](./02-Kubernetes-for-Java-Developers.md) · **Next:** [04 · Observability, SRE & Incident Response](./04-Observability-SRE-and-Incident-Response.md)
