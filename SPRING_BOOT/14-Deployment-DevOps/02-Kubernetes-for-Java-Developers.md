# 02 · Kubernetes for Java Developers

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How does your service scale / how do probes work?")
> **Prerequisite:** [01 · Containerising Spring Boot](./01-Containerizing-Spring-Boot-with-Docker.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. The Object Model](#1-the-object-model)
- [2. Mental Model — Desired State](#2-mental-model--desired-state)
- [3. Deployments and Rollouts](#3-deployments-and-rollouts)
- [4. Probes — the Java-Specific Traps](#4-probes--the-java-specific-traps)
- [5. Resources & the JVM](#5-resources--the-jvm)
- [6. Autoscaling](#6-autoscaling)
- [7. Config, Secrets & Service Discovery](#7-config-secrets--service-discovery)
- [8. Running Java Well on Kubernetes](#8-running-java-well-on-kubernetes)
- [9. Debugging a Failing Pod](#9-debugging-a-failing-pod)
- [10. Code Examples](#10-code-examples)
- [11. Common Mistakes](#11-common-mistakes)
- [12. Interview Questions & Model Answers](#12-interview-questions--model-answers)
- [13. Self-Check & Revision Card](#13-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> Kubernetes is a **declarative reconciliation loop**: you declare the desired state (a Deployment
> with 3 replicas of image X), and controllers continuously make reality match. You never "start a
> pod"; you declare that pods should exist.
>
> The four objects a Java developer must know: **Deployment** (replicas + rollout),
> **Service** (stable virtual IP + load balancing across pods), **Ingress** (HTTP routing from
> outside), **ConfigMap/Secret** (configuration).
>
> **Probes are where Java services die.** A `/health` that pings the database makes liveness fail
> during a DB outage → Kubernetes restarts every healthy pod → you turned a degradation into a
> total outage. **Liveness = is the JVM wedged. Readiness = can it serve traffic.**
>
> **Set memory requests = limits** (Guaranteed QoS, no eviction surprises), keep CPU requests
> honest, and let the JVM read the limit (`MaxRAMPercentage`). Then autoscale on **latency or queue
> depth**, not just CPU — CPU looks idle while requests queue.

---

## 1. The Object Model

```
   Ingress  ──┬──► Service (ClusterIP) ──► Pod  ┐
              │                                ├── part of a Deployment's ReplicaSet
              └──► Service (ClusterIP) ──► Pod ┘

   ConfigMap / Secret ──► mounted or env ──► Pod
   Namespace            ──► isolation boundary
   HPA                  ──► scales the Deployment's replica count
```

| Object | Job | Java relevance |
|---|---|---|
| **Pod** | one or more containers, one IP | your app container, usually one per pod |
| **Deployment** | replicas + rolling update | the thing you actually deploy |
| **ReplicaSet** | keeps N pods alive | created by the Deployment; you don't touch it |
| **Service** | stable IP + LB across pods | how `inventory-service` resolves |
| **Ingress** | HTTP host/path routing | TLS termination, path routing |
| **ConfigMap** | non-secret config | `application.yml` overrides, profiles |
| **Secret** | base64 "secret" config | DB passwords, JWT keys |
| **HPA** | autoscaling | CPU/memory/custom metrics |
| **PDB** | disruption budget | keeps quorum during node drains |
| **StatefulSet** | stable identity + storage | Kafka, Postgres (not your app) |
| **Job / CronJob** | run to completion / scheduled | migrations, batch jobs |

---

## 2. Mental Model — Desired State

```
        you:  "I want 3 replicas running image orders:1.2.3"
                          │
                          ▼
              ┌───────────────────────┐
              │  Deployment controller│
              └───────────┬───────────┘
                          │  reconcile loop (every few seconds)
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   actual: 2          actual: 3         actual: 4
        │                 │                 │
        └─ create 1 ──────┘                 └─ delete 1 ─┘
```

**Consequences of declarative thinking:**

- `kubectl delete pod` isn't permanent — the ReplicaSet recreates it. To stop pods, scale the
  Deployment to 0.
- `kubectl exec` changes are lost on restart. Use an image.
- Rollbacks are `kubectl rollout undo`, because the previous ReplicaSet still exists.
- **You don't manage pods; you manage the declaration.**

---

## 3. Deployments and Rollouts

### 3.1 The strategy

```yaml
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1              # one extra pod during rollout
      maxUnavailable: 0        # never drop below the desired count
  minReadySeconds: 10          # a new pod must be ready for 10s before proceeding
  progressDeadlineSeconds: 600
```

```
start:   [v1] [v1] [v1]
step 1:  [v1] [v1] [v1] [v2]   ← surge
step 2:  [v1] [v1]  [v2]       ← one v1 removed (after v2 is Ready + 10s)
step 3:  [v1]  [v2] [v2]
end:     [v2] [v2] [v2]
```

`maxUnavailable: 0` + `minReadySeconds` + a correct readiness probe = **zero-downtime deploys**.
Without the readiness probe, "ready" means "the container started", so traffic hits a JVM that's
still booting.

### 3.2 The Java-specific rollout problem

```
JVM start → Spring context → Flyway runs → caches warm → READY
   ~15–40 s for a real service
```

During that window:
- **Readiness must return NOT READY**, or traffic arrives before the app can handle it.
- **Liveness must NOT restart it** — a slow boot is not a hung process.

```
❌ liveness initialDelaySeconds: 10   ← kills the pod at 10s, before first request
✅ liveness failureThreshold: 6, periodSeconds: 10, initialDelaySeconds: 60
```

**The most common Kubernetes outage for Java teams:** liveness probe with a short initial delay,
restarting pods during a slow start, which causes an endless restart loop.

### 3.3 Rollback

```bash
kubectl rollout status deployment/orders
kubectl rollout history deployment/orders
kubectl rollout undo deployment/orders              # previous revision
kubectl rollout undo deployment/orders --to-revision=3
```

Because images are immutable, this is fast and reliable — the main reason to prefer it over
mutable deployments.

---

## 4. Probes — the Java-Specific Traps

### 4.1 The three probes

| Probe | Question | On failure |
|---|---|---|
| **startup** | has it finished booting? | restart (but disables liveness until it passes) |
| **liveness** | is the process wedged? | restart the pod |
| **readiness** | can it accept traffic? | remove from the Service endpoints |

```yaml
startupProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  failureThreshold: 30
  periodSeconds: 5                # up to 150s to boot
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 8080 }
  periodSeconds: 10
  failureThreshold: 3             # restart after 30s of being wedged
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 8080 }
  periodSeconds: 5
  failureThreshold: 2
  successThreshold: 1
```

**`startupProbe` is the elegant solution** to the slow-JVM problem: liveness is disabled until it
passes, so you can have an aggressive liveness threshold without risking a slow boot.

### 4.2 What each endpoint should actually check

| Endpoint | Should check | Must NOT check |
|---|---|---|
| **liveness** | the app can serve HTTP at all | DB, Redis, Kafka |
| **readiness** | dependencies needed for traffic | non-critical dependencies |

**The rule that prevents the classic outage:**

> *A dependency failure must fail **readiness**, never **liveness**.*

```
DB down:
  ✅ readiness fails → pods removed from the LB → requests return 503 quickly → recovery is instant
  ❌ liveness fails → all pods restart → the DB gets a connection storm on every restart → total outage
```

Spring Boot Actuator's **probes** endpoint does exactly this by design:

```yaml
management:
  endpoint.health:
    probes:
      enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

- `/actuator/health/liveness` → only `LivenessState` (**no DB, no Redis**)
- `/actuator/health/readiness` → `ReadinessState` + any indicators you group under `readiness`

```yaml
management:
  endpoint:
    health:
      group:
        readiness:
          include: readinessState, db, redis      # these gate traffic
        # liveness intentionally has no includes → no external checks
```

### 4.3 The slow-dependency trap

```
readiness include: db
DB latency rises to 5s
→ each readiness probe takes >timeout (1s) → probe fails → ALL pods removed
→ the Service has zero endpoints → total outage, though the DB is only slow
```

**Fix:** the health check must be **cheap and bounded** — use `validationQuery` with a short
timeout, or check a local signal (last successful query time) rather than performing a query on
every probe. Probe traffic at 3 replicas × every 5 s is real load too.

### 4.4 Graceful shutdown, end to end

```
kubectl delete pod
   │
   ├─ endpoint removed from Service (async! takes time to propagate)
   ├─ preStop hook runs (sleep 5)      ← covers the propagation window
   ├─ SIGTERM → Spring graceful shutdown (30s)
   └─ SIGKILL at terminationGracePeriodSeconds
```

```yaml
spec:
  terminationGracePeriodSeconds: 45      # > preStop 5s + shutdown 30s
  containers:
    - lifecycle:
        preStop:
          exec: { command: ["sh", "-c", "sleep 5"] }
```

**Without the `preStop` sleep, in-flight requests are dropped on every deploy** — the pod gets
SIGTERM before the load balancer stops routing to it. That's the second most common Java-on-k8s
bug, and it's invisible in tests.

---

## 5. Resources & the JVM

### 5.1 Requests vs limits

```
requests → used for scheduling; the guaranteed share
limits   → the hard cap; CPU is throttled, memory causes OOMKill
```

| Combination | QoS class | Behavior |
|---|---|---|
| requests = limits for all | **Guaranteed** | last evicted |
| some set | Burstable | evicted under pressure |
| none set | BestEffort | first evicted |

**For a Java service: set memory requests = limits.** A JVM that thinks it has 1 GB but gets
evicted at 700 MB is a mystery outage. `Guaranteed` for memory is the professional default.

```yaml
resources:
  requests:
    memory: 1Gi
    cpu: 500m
  limits:
    memory: 1Gi          # memory limit = request ⇒ no OOMKill surprises
    cpu: 2               # CPU limit can be higher (burstable)
```

### 5.2 The JVM must agree with the limit

```
container memory limit: 1 Gi
├── JVM heap: 75%  → 768 MB       (-XX:MaxRAMPercentage=75)
├── metaspace, code cache, thread stacks, direct buffers → ~150 MB
└── OS / page cache → the rest
```

**Two failure modes to name:**

- **Heap too large** (`MaxRAMPercentage=95`) → native memory pushes past the cgroup limit →
  OOMKilled with no Java exception, no heap dump. Looks like "the pod just dies".
- **Heap too small** (`-Xmx256m` on a 2 GB container) → GC thrash at 60% memory usage, wasting
  the memory you're paying for.

### 5.3 CPU and the JVM

```
limits.cpu = 1  →  the JVM sees 1 CPU
               →  GC threads, JIT compiler threads, ForkJoinPool sized to 1
               →  request latency is dominated by GC and JIT
```

- **Don't set `cpu` limits too low** — a JVM needs headroom for GC and JIT spikes. `500m` with a
  `2` limit (burstable) is often better than `500m`/`500m`.
- CPU limits cause **throttling**, visible as `container_cpu_cfs_throttled_seconds_total`. JVM
  p99 latency correlates directly with throttling.
- Thread pools: a Tomcat `max-threads: 200` on a 1-CPU pod is 200 threads fighting for one core —
  more latency, not more throughput.

### 5.4 Sizing method

```
1. measure heap-after-full-GC under peak load  →  H
2. total container memory ≈ H / 0.75            (25% for non-heap)
3. round up to a sane instance size, set request = limit
4. verify: no OOMKills, GC pause < 200ms, throttling < 5%
```

---

## 6. Autoscaling

### 6.1 HPA

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata: { name: orders }
spec:
  scaleTargetRef: { kind: Deployment, name: orders }
  minReplicas: 3
  maxReplicas: 30
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 30
      policies: [{ type: Percent, value: 100, periodSeconds: 30 }]
    scaleDown:
      stabilizationWindowSeconds: 300        # be slow to scale down
      policies: [{ type: Percent, value: 50, periodSeconds: 60 }]
  metrics:
    - type: Resource
      resource: { name: cpu, target: { type: Utilization, averageUtilization: 65 } }
```

**Why 65% and not 90%?** Because latency grows non-linearly near saturation
([T12 ch02](../12-System-Design-for-Java-Backend/02-Scalability-Availability-and-Reliability.md)),
and because a Java pod takes 20–40 s to become ready — you must scale **before** you're saturated.

### 6.2 Why CPU autoscaling is often wrong for Java

```
CPU 50%  but p99 is 2s because requests are queued on a connection pool
→ HPA sees 50% CPU and does nothing
```

**Better metrics:**

| Signal | Metric | When |
|---|---|---|
| Request rate | `http_server_requests_seconds_count` | throughput-bound |
| Latency | p95 of request duration | latency-bound |
| Queue depth | queue size, consumer lag | async workers |
| Concurrency | Tomcat busy threads | thread-bound |
| In-flight | active requests | I/O-bound |

```java
// expose a business metric that reflects load
registry.gauge("orders_queue_depth", queue, q -> q.size());
```

```yaml
metrics:
  - type: Pods
    pods:
      metric: { name: orders_queue_depth }
      target: { type: AverageValue, averageValue: "20" }
```

**KEDA** extends HPA for event-driven scaling (Kafka lag, SQS depth) — the right tool for consumers.

### 6.3 Scaling that's blocked by something else

| Symptom | Why HPA doesn't help |
|---|---|
| DB connection pool saturated | more pods = more connections = worse |
| Cache hot key | more pods don't split a key |
| Downstream rate limit | more pods hit the limit faster |
| JVM warm-up | new pods are slow for 30 s (see below) |
| Cluster out of capacity | pods Pending |

**Say the constraint:** "autoscaling the app tier is pointless if the DB connection pool is the
ceiling — I'd need PgBouncer or a bigger DB, and the pod count has to be sized so
`pods × poolSize ≤ DB max_connections`."

### 6.4 The warm-up problem

A new Java pod isn't useful for 20–40 s. Under a traffic spike, HPA adds pods that **increase**
load (JIT, GC, cold caches) before they help.

Mitigations:
- **Pre-scale before known events** (`kubectl scale` on a schedule).
- **Keep a warm floor** (minReplicas = peak-off-peak, not 1).
- **Readiness gate that waits for warm-up** ([T10 ch04](../10-Caching/04-Multi-Level-Caching-and-Production-Operations.md)).
- **AppCDS / CRaC** to cut startup — or native images ([ch 04](./04-Observability-SRE-and-Incident-Response.md)).

---

## 7. Config, Secrets & Service Discovery

### 7.1 ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata: { name: orders-config }
data:
  application.yml: |
    server:
      tomcat:
        threads:
          max: 50
    spring:
      datasource:
        hikari:
          maximum-pool-size: 10
    management:
      endpoints:
        web:
          exposure:
            include: health,info,prometheus
```

```yaml
# in the pod spec
volumeMounts:
  - name: config
    mountPath: /config
volumes:
  - name: config
    configMap: { name: orders-config }
env:
  - name: SPRING_CONFIG_ADDITIONAL_LOCATION
    value: /config/
```

**Mounted ConfigMaps update automatically** (with a delay); **env-var ConfigMaps do not** — env vars
are fixed at container start. So mounted files + Spring's `@RefreshScope`/config reload is the only
path to live config changes without a restart.

### 7.2 Secrets — and why they're not secret

**Kubernetes Secrets are base64, not encrypted.** Anyone with `get secret` RBAC can read them.

Better:
- **External Secrets Operator / Vault / AWS Secrets Manager** injecting at runtime.
- **Encryption at rest** enabled on etcd.
- **Restrict RBAC** so only the service account can read its own secret.
- **Never** put secrets in a ConfigMap, image, or env var you log.

```yaml
env:
  - name: DB_PASSWORD
    valueFrom:
      secretKeyRef: { name: orders-db, key: password }
```

```java
// Spring Boot reads it from the environment via relaxed binding
spring.datasource.password: ${DB_PASSWORD}
```

**Never log `Environment`/`System.getenv()`** — a very common debug-print accident that leaks
secrets into logs.

### 7.3 Service discovery

```
orders-service.default.svc.cluster.local:8080
       └─ Service name  └─ namespace
```

- **CoreDNS** resolves the Service name to a ClusterIP.
- **kube-proxy** load-balances across pod IPs (iptables/IPVS), roughly random.
- **Headless Service** (`clusterIP: None`) resolves to pod IPs directly — used for client-side
  load balancing and StatefulSets.

```yaml
spring:
  cloud:
    openfeign:
      client:
        config:
          inventory:
            url: http://inventory-service:8080     # DNS-based, no Eureka needed
```

**This is why Eureka is often unnecessary on Kubernetes** — DNS + a Service is the discovery
mechanism. Say that when asked about service discovery
([T08 ch03](../08-Microservices-with-Spring-Boot/03-Service-Discovery-Config-and-API-Gateway.md)).

---

## 8. Running Java Well on Kubernetes

### 8.1 The checklist

- [ ] `MaxRAMPercentage`, not `-Xmx`; `memory request == limit`.
- [ ] `startupProbe` for slow boot; liveness doesn't check dependencies.
- [ ] Readiness checks exactly what's needed to serve traffic.
- [ ] `preStop` sleep + `terminationGracePeriodSeconds` > shutdown timeout.
- [ ] `spring.lifecycle.timeout-per-shutdown-phase` set.
- [ ] Tomcat threads sized to the CPU (not 200 on 1 CPU).
- [ ] Hikari `maximum-pool-size` × max pods ≤ DB `max_connections`.
- [ ] HPA on latency/queue, with a warm floor.
- [ ] PDB so node drains don't kill quorum.
- [ ] Logs to stdout as JSON, not files.
- [ ] Metrics on `/actuator/prometheus`, scraped.
- [ ] Resource `Guaranteed` for memory.

### 8.2 Logging

```yaml
logging:
  pattern:
    console: '{"ts":"%d{ISO8601}","level":"%p","logger":"%logger{36}","msg":"%m","traceId":"%X{traceId:-}","spanId":"%X{spanId:-}"}%n'
```

**Log to stdout, let the platform collect it.** Writing to a file in the container means you need
a sidecar, and the file is lost on restart. JSON makes it queryable — and including `traceId` is
what links logs to traces ([T08 ch05](../08-Microservices-with-Spring-Boot/05-Distributed-Tracing-and-Observability.md)).

### 8.3 JVM warm-up shortcuts

| Technique | Startup gain | Cost |
|---|---|---|
| AppCDS (class data sharing) | 10–30% | build step |
| `-XX:TieredStopAtLevel=1` | faster start, slower peak | only for short-lived jobs |
| CRaC (checkpoint/restore) | near-instant | new, needs a compatible JDK |
| **GraalVM native image** | ~50 ms, ~80 MB | reflection config, longer build, different perf profile |
| More CPU at startup | linear | money |

**For a service that scales frequently, native or CRaC is transformative** — you stop paying 30 s
of warm-up per scale-out. Mentioning the trade-off (native images have a different peak-throughput
profile and need reflection hints) shows depth.

---

## 9. Debugging a Failing Pod

### 9.1 The decision tree

```
Pod not running?
├─ Pending            → scheduling: insufficient resources, PVC unschedulable, taints
├─ ImagePullBackOff   → bad tag, registry auth, arch mismatch
├─ CrashLoopBackOff   → app exits: OOMKilled, config error, port conflict
│   └─ kubectl logs --previous   ← the CRUCIAL flag; current logs are from the new attempt
└─ Running but not Ready → readiness probe failing
    └─ kubectl describe pod → probe events; curl the endpoint from inside
```

### 9.2 The commands that matter

```bash
kubectl get pods -o wide                      # node, IP, restarts
kubectl describe pod orders-abc123            # events at the bottom — read these first
kubectl logs orders-abc123 --previous         # the crash, not the restart
kubectl logs -l app=orders --tail=100         # all replicas
kubectl exec -it orders-abc123 -- sh           # if there's a shell
kubectl get events --sort-by=.lastTimestamp
kubectl top pod orders-abc123                  # actual usage vs requests
kubectl port-forward svc/orders 8080:8080      # test without ingress
```

### 9.3 The four failure signatures

| Signature | Meaning | Fix |
|---|---|---|
| `OOMKilled` (exit 137) | the process exceeded the memory limit | raise the limit or lower `MaxRAMPercentage` |
| `exit 1` immediately | config/startup error | `--previous` logs |
| Ready → NotReady in a loop | a dependency check in readiness | make readiness cheap |
| Restarts every ~30–60 s | liveness probe failing during slow start | `startupProbe` or higher `failureThreshold` |

**Exit code 137 = 128 + 9 (SIGKILL)** — usually the OOM killer. Knowing that off the top of your
head is a nice signal.

### 9.4 Reading `kubectl describe` properly

The **Events** section at the bottom is where the answer usually is:

```
Events:
  Warning  Unhealthy  3s (x4 over 30s)  kubelet  Liveness probe failed: HTTP probe failed with statuscode: 503
  Normal   Killing    3s                 kubelet  Container orders failed liveness probe, will be restarted
```

That pattern — 503 on liveness — is the dependency-in-liveness bug from §4.

---

## 10. Code Examples

### 10.1 A production Deployment

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: orders
  labels: { app: orders }
spec:
  replicas: 3
  revisionHistoryLimit: 5
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 1, maxUnavailable: 0 }
  minReadySeconds: 10
  selector:
    matchLabels: { app: orders }
  template:
    metadata:
      labels: { app: orders }
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/path: "/actuator/prometheus"
        prometheus.io/port: "8080"
    spec:
      terminationGracePeriodSeconds: 45
      securityContext:
        runAsNonRoot: true
        runAsUser: 1000
        fsGroup: 1000
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: topology.kubernetes.io/zone
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels: { app: orders }
      containers:
        - name: orders
          image: registry.example.com/acme/orders:1.2.3
          imagePullPolicy: IfNotPresent
          ports: [{ containerPort: 8080, name: http }]
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: prod
            - name: DB_PASSWORD
              valueFrom: { secretKeyRef: { name: orders-db, key: password } }
            - name: JAVA_TOOL_OPTIONS
              value: "-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
          resources:
            requests: { memory: 1Gi, cpu: 500m }
            limits:   { memory: 1Gi, cpu: 2 }
          startupProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            failureThreshold: 30
            periodSeconds: 5
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: http }
            periodSeconds: 10
            failureThreshold: 3
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: http }
            periodSeconds: 5
            failureThreshold: 2
          lifecycle:
            preStop:
              exec: { command: ["sh", "-c", "sleep 5"] }
          securityContext:
            allowPrivilegeEscalation: false
            readOnlyRootFilesystem: true
            capabilities: { drop: ["ALL"] }
          volumeMounts:
            - { name: tmp, mountPath: /tmp }
            - { name: config, mountPath: /config }
      volumes:
        - name: tmp
          emptyDir: {}
        - name: config
          configMap: { name: orders-config }
```

### 10.2 Service, Ingress and PDB

```yaml
apiVersion: v1
kind: Service
metadata: { name: orders }
spec:
  selector: { app: orders }
  ports: [{ port: 80, targetPort: http, name: http }]
---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: orders
  annotations:
    nginx.ingress.kubernetes.io/proxy-read-timeout: "60"
spec:
  tls: [{ hosts: ["api.example.com"], secretName: api-tls }]
  rules:
    - host: api.example.com
      http:
        paths:
          - path: /orders
            pathType: Prefix
            backend: { service: { name: orders, port: { number: 80 } } }
---
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata: { name: orders }
spec:
  minAvailable: 2          # a node drain can't take more than 1 of 3
  selector: { matchLabels: { app: orders } }
```

### 10.3 Async consumer autoscaling with KEDA

```yaml
apiVersion: keda.sh/v1alpha1
kind: ScaledObject
metadata: { name: order-consumer }
spec:
  scaleTargetRef: { name: order-consumer }
  minReplicaCount: 2
  maxReplicaCount: 20
  triggers:
    - type: kafka
      metadata:
        bootstrapServers: kafka:9092
        consumerGroup: order-consumer
        topic: orders.v1
        lagThreshold: "1000"        # scale out when lag > 1000 per consumer
```

**Consumer lag is the right autoscaling metric for Kafka** — CPU is a lagging indicator, and a
consumer at low CPU with a growing lag is exactly the case CPU autoscaling misses.

### 10.4 Actuator probe configuration

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
      group:
        readiness:
          include: readinessState, db
          show-details: never
        liveness:
          include: livenessState          # NOTHING external
  health:
    livenessstate.enabled: true
    readinessstate.enabled: true
    db:
      enabled: true
```

---

## 11. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| Liveness checks the DB | DB blip restarts every pod → outage | readiness only |
| `initialDelaySeconds: 10` for a 30s JVM boot | restart loop | `startupProbe` |
| No `preStop` sleep | dropped requests on deploy | sleep 5s |
| `terminationGracePeriodSeconds` < shutdown timeout | SIGKILL mid-request | align them |
| Memory request ≠ limit | eviction / OOM surprises | `Guaranteed` for memory |
| `MaxRAMPercentage=95` | OOMKilled with no Java log | 75% |
| CPU limit too low | throttling → p99 spikes | burstable CPU, check throttling metric |
| HPA on CPU only | doesn't scale when queued | latency/queue metrics, KEDA |
| `minReplicas: 1` | every scale-up pays cold start | warm floor |
| `pods × poolSize > DB max_connections` | DB connection exhaustion | PgBouncer + explicit math |
| Secrets in ConfigMap/env logged | credential leak | External Secrets, never log env |
| `kubectl exec` hotfixes | lost on restart | build an image |
| Logs to a file | lost, needs a sidecar | stdout JSON |
| No PDB | node drain takes the service down | `minAvailable` |
| `:latest` image tag | surprise deploys | immutable tags/digests |

---

## 12. Interview Questions & Model Answers

**Q1. What's the difference between liveness and readiness?**
> Liveness asks "is this process wedged" — a failure restarts the pod. Readiness asks "can it serve
> traffic" — a failure removes it from the Service. The critical rule is that a dependency failure
> must fail **readiness** and never **liveness**, because restarting healthy pods during a database
> incident turns a degradation into a total outage and floods the DB with reconnects.

**Q2. Your Java pods take 30 seconds to start. How do you handle that?**
> A `startupProbe` with a generous `failureThreshold`, which disables liveness until it passes, so I
> can have a tight liveness threshold without risking a slow boot. Plus a readiness probe that only
> goes green after the context is up and, if I have warm-up, after warm-up. Without the startup
> probe, the classic bug is a restart loop that looks like a crash when it's just a slow JVM.

**Q3. How do you size memory for a JVM in Kubernetes?**
> Measure heap-after-full-GC at peak load, then set the container memory to about that divided by
> 0.75 to leave room for metaspace, code cache, thread stacks and direct buffers. Set the memory
> request equal to the limit so the pod is Guaranteed, and let the JVM size itself with
> `MaxRAMPercentage=75` rather than a fixed `-Xmx`. Then verify: no OOMKills, GC pauses under a
> couple hundred milliseconds, and no CPU throttling.

**Q4. Why doesn't autoscaling on CPU work for your service?**
> Because the bottleneck is often a queue, not CPU. If requests are waiting on a connection pool,
> CPU stays at 50% while p99 is seconds, so the HPA never scales. I'd autoscale on request latency,
> in-flight requests, or consumer lag — and check that the limit isn't actually downstream, like the
> DB connection pool, where adding pods makes things worse.

**Q5. How do you achieve zero-downtime deploys?**
> Four things together: `maxUnavailable: 0` with `maxSurge: 1`, a readiness probe that's only green
> when the app can actually serve, a `preStop` sleep so the load balancer stops routing before
> shutdown, and `terminationGracePeriodSeconds` longer than Spring's graceful shutdown timeout. Miss
> the preStop and you drop in-flight requests on every deploy — and it's invisible in tests.

**Q6. How does service discovery work on Kubernetes?**
> CoreDNS resolves the Service name to a ClusterIP and kube-proxy load-balances across the pod IPs.
> So `http://inventory-service:8080` just works — which is why Eureka is usually unnecessary on
> Kubernetes; you inherit discovery from the platform. A headless Service resolves to pod IPs
> directly when I need client-side load balancing.

**Q7. A pod is in CrashLoopBackOff. What do you do?**
> `kubectl describe pod` and read the Events section first, then `kubectl logs --previous` — the
> important flag, because the current container's logs are from the new attempt. If the exit code is
> 137 it's a SIGKILL, almost always OOMKilled, so I'd check `kubectl top pod` against the limit and
> whether `MaxRAMPercentage` is too high. Exit 1 immediately is a config or startup error.

**Q8. Why are Kubernetes Secrets not a good secret store?**
> They're base64-encoded, not encrypted, and anyone with `get secret` RBAC can read them. I'd use
> External Secrets or Vault to inject them at runtime, enable etcd encryption at rest, restrict RBAC
> to the specific service account, and never log the environment — which is the most common way they
> leak.

---

## 13. Self-Check & Revision Card

- [ ] I can explain declarative reconciliation vs imperative commands.
- [ ] I can name the objects and what each does.
- [ ] I can explain liveness vs readiness vs startup and the DB-outage rule.
- [ ] I can design a zero-downtime rollout on Java.
- [ ] I can size JVM memory for a container and explain the 75%.
- [ ] I can explain why CPU autoscaling fails for queued workloads.
- [ ] I can explain service discovery via DNS and why Eureka is redundant.
- [ ] I can debug a CrashLoopBackOff in four commands.

```
┌────────────── KUBERNETES FOR JAVA — 60s RECALL ─────────────────────────────┐
│ Declarative reconcile loop. You declare desired state; controllers converge. │
│                                                                              │
│ PROBES: startup (slow boot) · liveness (wedged ⇒ restart) ·                  │
│   readiness (can serve ⇒ remove from LB).                                    │
│ ★ A DEPENDENCY FAILURE MUST FAIL READINESS, NEVER LIVENESS. ★                │
│   (DB blip + liveness = restart storm = outage)                              │
│                                                                              │
│ ZERO DOWNTIME: maxUnavailable 0 + readiness + preStop sleep 5s +             │
│   terminationGracePeriod > spring shutdown timeout.                          │
│                                                                              │
│ MEMORY: request == limit (Guaranteed) · MaxRAMPercentage=75 (not -Xmx) ·     │
│   heap-after-GC ÷ 0.75 = limit · exit 137 = OOMKilled.                       │
│ CPU: throttling kills p99 — check cfs_throttled.                             │
│                                                                              │
│ AUTOSCALE on latency/queue/Kafka LAG, not CPU. Warm floor (minReplicas).     │
│ pods × poolSize ≤ DB max_connections.                                        │
│                                                                              │
│ DEBUG: describe (Events) → logs --previous → top → exec.                     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [01 · Containerising Spring Boot with Docker](./01-Containerizing-Spring-Boot-with-Docker.md) · **Next:** [03 · CI/CD & Release Strategies](./03-CICD-and-Release-Strategies.md)
