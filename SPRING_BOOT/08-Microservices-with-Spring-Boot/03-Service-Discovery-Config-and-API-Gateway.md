# 03 · Service Discovery, Config & the API Gateway

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do your services find each other?")
> **Prerequisite:** [02 · Service Communication & Contracts](./02-Service-Communication-and-API-Contracts.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Three Moving Parts](#2-mental-model--the-three-moving-parts)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The discovery's problem](#31-the-discoverys-problem)
  - [3.2 The client's vs the server's load balancing](#32-the-clients-vs-the-servers-load-balancing)
  - [3.3 The service's registries](#33-the-services-registries)
  - [3.4 The Spring's Cloud's LoadBalancer](#34-the-springs-clouds-loadbalancer)
  - [3.5 The Eureka](#35-the-eureka)
  - [3.6 The Kubernetes's DNS](#36-the-kubernetess-dns)
  - [3.7 The config's server](#37-the-configs-server)
  - [3.8 The API's gateway](#38-the-apis-gateway)
  - [3.9 The gateway's cross-cutting's concerns](#39-the-gateways-cross-cuttings-concerns)
  - [3.10 The service's mesh](#310-the-services-mesh)
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
- [16. Real 3–5 Year Scenarios](#16-real-35-year-scenarios)
- [17. Frequently Asked Production Issues](#17-frequently-asked-production-issues)
- [18. Debugging Scenarios](#18-debugging-scenarios)
- [19. Architecture Design Questions](#19-architecture-design-questions)
- [20. Cheat Sheet — One-Page Summary](#20-cheat-sheet--one-page-summary)
- [21. Follow-up Learning](#21-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **Service discovery exists because "the IP address" is a lie.** In a containerised,
  autoscaled, rolling-deployment system, the address of a service changes
  constantly. Discovery is the mechanism that maps a **logical name** (`catalogue`)
  to a **current set of healthy instances** (`10.42.1.7:8080`, `10.42.2.3:8080`).
- **Client-side vs server-side load balancing** is the first's decision:
  - **Client-side** (the caller knows the instances, picks one): fewer hops, the
    client controls the algorithm, but the client's config's more complex.
  - **Server-side** (a load's balancer picks): simpler's clients, a central's
    policy, but an extra's hop and a potential's SPOF.
  - **Sidecar** (the service's mesh): the best's of both, with a real's cost.
- **The registry is a critical's dependency.** If Eureka is down, **existing**
  services keep working (they have their cached instance lists) but **new
  registrations and new resolutions fail**. It must be **highly available** (3+
  nodes), and the client must cache the registry's failures gracefully.
- **The config's server is where the secrets live** — and it must be the **most
  hardened** component in the estate. A config's server that leaks is a
  credentials' breach across every service.
- **The API's gateway is the edge's policy**, not the business's logic. Its job:
  the routing, the TLS's termination, the rate's limiting, the coarse's auth, the
  request's ID, the observability's edge, and the BFF's composition. It should
  **not** contain business's rules — that recreates the monolith at the edge.
- **Kubernetes's DNS is a service's registry** — `catalogue.namespace.svc.cluster.local`
  resolves to a cluster's IP, and the `kube-proxy`/`iptables` does the load's
  balancing. In K8s, you usually **don't** need Eureka.
- **The gateway is a SPOF and a bottleneck.** It must be stateless, horizontally's
  scaled, and its failure's mode must be a clean's `503` — not a hang.

---

## 1. What & Why

### The scenario

```text
  "We have 8 microservices running on Kubernetes, 3 replicas each, deployed
   10 times a day. How does the orders service find the catalogue service?
   And how do we change a feature flag without redeploying 24 pods?"
```

Two questions, two infrastructure's problems. The first is **discovery** (the
addressing); the second is **configuration** (the runtime's change). And both
have a modern, K8s-native answer that is different from the classic Eureka /
Spring Cloud Config answer — knowing both is what the interview's testing.

### Why the classic answer exists

Before containers and the elastic's cloud, a service's address was a **DNS name**
that a human registered. When a server died, the ops's team removed it from DNS.
That was the registry. The "registry's software" (Eureka) was the generalisation
of the DNS for the dynamic's case.

```text
  the CLASSIC's model (the "the client knows the server's address"):
    the CLIENT's config:  catalogue.internal:8080
    the CLIENT resolves it via the DNS (or the Eureka's registry)
    the CLIENT connects directly

  the MODERN's model (the "the server's address is ephemeral"):
    the CLIENT asks the REGISTRY: "give me the healthy's instances of 'catalogue'"
    the CLIENT connects to one of them directly
    OR: the CLIENT connects to a stable's SERVICE's name, and the platform's
        proxy does the load's balancing
```

---

## 2. Mental Model — The Three Moving Parts

```text
  ┌──────────────────────────────────────────────────────────────────────┐
  │  ① the DISCOVERY's: "where is 'catalogue' RIGHT NOW?"               │
  │                                                                      │
  │      the SERVICE's INSTANCE's ──registers──▶ the REGISTRY            │
  │              ▲                                │                      │
  │              │                                │ the instance's list  │
  │              │  the client resolves & caches  ▼                      │
  │              └────────────────────────────  the CLIENT               │
  │                                                                      │
  │  who owns the addresses:  the SERVICE (it knows them)               │
  │  who makes the decision:  the CLIENT (it picks) or a PROXY (it picks)│
  ├──────────────────────────────────────────────────────────────────────┤
  │  ② the CONFIG's: "what's the current's setting for 'catalogue'?"   │
  │                                                                      │
  │      the CONFIG's SERVER  ◀── the git's repo (the source's truth)   │
  │            │                                                         │
  │            ▼                                                         │
  │      the CLIENT (fetches at startup, caches, refreshes on a poll)   │
  │                                                                      │
  │  who owns the settings: the TEAM (in git)                           │
  │  who delivers them: the SERVER (the push or the poll)              │
  ├──────────────────────────────────────────────────────────────────────┤
  │  ③ the GATEWAY's: "the edge's policy"                                │
  │                                                                      │
  │      the CLIENT ──▶ the GATEWAY ──▶ the SERVICE                    │
  │                   (TLS, the rate's limit, the auth, the routing,   │
  │                    the request's ID, the aggregation)                │
  │                                                                      │
  │  who owns the policy: the PLATFORM's TEAM                          │
  └──────────────────────────────────────────────────────────────────────┘
```

**The one-line expert framing:**

> "Discovery answers **'where'**, config answers **'what'**, and the gateway
> answers **'how do they get in'**. Three different concerns, three different
> failure modes, and conflating them — putting the business's logic in the
> gateway, or the secrets in the service's code — is how estates get into
> trouble."

---

## 3. Deep Dive — Theory

### 3.1 The discovery's problem

**The requirements a discovery's system must satisfy:**

| The requirement | Why |
|----------------|-----|
| **The registration** | A new's instance's address must be discoverable |
| **The deregistration** | A dead's instance must be removed (or the client must stop sending to it) |
| **The health's checks** | An instance's alive but broken must not receive the traffic |
| **The client's caching** | The registry must not be on the hot's path (a registry's outage must not stop the traffic) |
| **The consistency** | Multiple's registries must not disagree (the split's brain) |
| **The watch's invalidation** | The client must learn about the changes (the scale-up, the scale-down) |

**The deregistration's problem (the "the graceful's shutdown" gap):**

```text
  the INSTANCE's lifecycle:
    starts → registers → healthy → [deregisters on SIGTERM] → stops

  ⚠ the GAP: the pod's shutdown takes TIME (the preStop's hook, the in-flight's
    requests' draining, the JVM's shutdown). If the instance deregisters
    IMMEDIATELY on SIGTERM, the registry's clients start sending to it while
    it's still draining → the errors. If it deregisters at the END, the
    registry's list is stale for the whole's drain → also errors.

  the SOLUTION: the deregistration is the LAST's step, and the draining's
  protection is the K8s's "the preStop's sleep" + the "the endpoint's removal's
  propagation's delay" — but that delay is NOT instant, so the client must
  also handle a dead's peer (the TCP's RST, the timeout, the retry).
```

**The split's brain (the registry's consistency):**

With multiple's registry's nodes, a client might ask node A (which has 3
instances) and then node B (which has 5). The consequences: the traffic is
unevenly's distributed, and worse, a just-terminated's instance might still be
in one node's list. Eureka's answer: the registry's nodes **peer** with each
other (the replication's pull) and the client typically queries **one** node
(the `eureka.client.serviceUrl` list is tried in order) — so the inconsistency
is bounded by the replication's lag. Consul's answer: the **Raft** consensus —
a single's leader, so the writes are linearizable's, at the cost of the
availability's during the leader's election. The trade: **AP vs CP** — the
Eureka-style is more available (a stale's list is usually fine), the
Consul-style is more consistent (but the registry's outage stops the
registrations).

### 3.2 The client's vs the server's load balancing

```text
  the CLIENT's load balancing (the caller knows the instances)
  ┌─────────┐   1. GET /instances/catalogue   ┌──────────────┐
  │ the     │────────────────────────────────▶│ the REGISTRY  │
  │ CLIENT  │◀────────────────────────────────│              │
  │         │   [catalogue-a:8080,            └──────────────┘
  │         │    catalogue-b:8080]                 (cached!)
  │         │
  │   2. picks catalogue-b (the round's robin)   ← the CLIENT decides
  │         │
  │         ▼
  │   ┌──────────────────┐
  │   │ catalogue-b:8080 │  ← the CLIENT connects directly (no extra hop)
  │   └──────────────────┘

  the SERVER's load balancing (a proxy decides)
  ┌─────────┐                              ┌──────────────┐
  │ the     │──────────────▶┌───────────┐──▶│ catalogue-a  │
  │ CLIENT  │              │ the LOAD  │──▶│ catalogue-b  │
  │         │              │ BALANCER  │──▶│ catalogue-c  │
  └─────────┘              └───────────┘   └──────────────┘
                       (the CLIENT just knows ONE stable's address)
```

**The comparison:**

| Dimension | The client's | The server's | The sidecar's |
|-----------|-------------|--------------|---------------|
| The extra's hop | None | One (the LB) | None (the sidecar is local) |
| The client's complexity | High (the resolution, the algorithm, the caching) | Low (one URL) | Medium (the sidecar's config) |
| The algorithm's flexibility | Full (per-caller, per-request) | Central (the LB's policy) | Per-service (the sidecar's config) |
| The client's library's dependency | Yes (the discovery's client) | No | No |
| The observability | The client's metrics | The LB's metrics | The sidecar's metrics |
| The failure's blast's radius | Per-client | The LB (a SPOF unless's scaled) | Per-service's sidecar |
| The K8s's fit | Awkward (the K8s's DNS already does it) | Natural (the Ingress/Service) | Natural (the Istio's) |
| The TLS's hop | The client ↔ the service (the identity's direct) | The client ↔ the LB ↔ the service | The sidecar ↔ the sidecar (mTLS) |

**The interview's answer (the nuance):**

> "In a **client-side** model the caller knows the instances and picks — fewer
> hops, more flexibility, but every client carries the discovery's library and
> the algorithm. In a **server-side** model, a load's balancer picks — the clients
> are simple, the policy is central, but there's an extra hop and the balancer
> is a critical's path. In **Kubernetes**, the answer is usually neither: the
> Service's cluster's IP is a stable's name, `kube-proxy` programs `iptables`
> for the load's balancing, and the client just uses the **DNS** name. And in a
> **mesh**, the sidecar does the client-side's discovery with a server-side's
> control's plane. The **trade's summary**: the client's is fast and flexible
> but complex; the server's is simple and central but adds a hop and a SPOF."

### 3.3 The service's registries

| The registry | The consistency's model | The availability's model | The notes |
|--------------|-------------------------|------------------------|---------|
| **Eureka** (Netflix) | The eventual's (the peers' replication) | The AP (the client's cached's lists) | The classic's Spring Cloud's pairing; the aging's but the battle's tested |
| **Consul** (HashiCorp) | The CP (the Raft's leader) | The CP (the leader's election's downtime) | The health's checks are excellent; the KV's store is a plus |
| **etcd** | The CP (the Raft) | The CP | The Kubernetes's backend; the K8s's API is a registry by another's name |
| **ZooKeeper** | The CP (the ZAB) | The CP | The heavy's weight; the watcher's model is excellent |
| **Nacos** (Alibaba) | The AP (the ephemeral's + the persistent's) | The AP | The config's server + the registry; the Chinese's ecosystem's popular |
| **K8s's DNS** | The K8s's API's etcd (the CP) | The client's cached's DNS | The default's answer on K8s |

### 3.4 The Spring's Cloud's LoadBalancer

**The modern's default (post-2020):** Spring Cloud **deprecated** the Ribbon
client and replaced it with **Spring Cloud LoadBalancer** (an abstraction over
the discovery's client). This matters for the interview — "which Ribbon?" is a
**dated** question.

```text
  the REQUEST's flow with Spring Cloud LoadBalancer:
    the RestClient → the @LoadBalanced RestClient
      → the LoadBalancerClient
        → the ServiceInstanceListSupplier
           → the DiscoveryClient (Eureka / the K8s's API)
              → the cached's instance's list
        → the RoundRobinLoadBalancer (the default's algorithm)
      → the chosen's instance (host + port)
    → the ACTUAL's HTTP's call to that instance
```

**The load-balancing's strategies:**

| The strategy | The behaviour | The use case |
|-------------|---------------|-------------|
| **Round Robin** | The alternating's cycle | The default; the instances are identical |
| **Random** | The random's pick | The cheap; the good enough |
| **Weighted** | The proportional to the weight | The heterogeneous's instances (the GPU's vs the CPU's) |
| **Response Time** (the least's request's time) | The fastest's instance | The heterogeneous's performance; the risk of the slow's start |

**The `ServiceInstanceListSupplier` (the caching's layer):**

```text
  the CACHING's hierarchy (the Spring Cloud's commons):
    ① the DiscoveryClient            (the raw's data — a network's call!)
    ② the CachingServiceInstanceListSupplier   (the cached's list, 35s's default)
    ③ the HealthCheckServiceInstanceListSupplier (the filtered's list)
    ④ the WeightedServiceInstanceListSupplier  (the weighted's list)
    ⑤ the ReweightedServiceInstanceListSupplier (the zone's aware's list)
```

**The `HealthCheckServiceInstanceListSupplier`** is the important one: it filters
the instance's list to the ones that pass the **health check** — which is how
the registry's "the instance is up" is distinguished from "the instance is
*serving*". The **health's checker's** options: the **ping** (a TCP or an HTTP
`HEAD` on a path), the **the heartbeat's state** (the Eureka's status), and the
**the composite** (both).

### 3.5 The Eureka

```text
  the EUREKA's architecture (the classic's client/server)
  ┌──────────────┐                                    ┌────────────────┐
  │ the SERVICE  │  ① REGISTER (heartbeat every 30s) │  the EUREKA    │
  │ INSTANCE     │───────────────────────────────────▶│  SERVER (peer  │
  │              │◀───────────────────────────────────│  to peer)      │
  │              │  ② the registry's list             └────────────────┘
  └──────────────┘                                        ▲   ▲
                                                         │   │
  ┌──────────────┐  ③ GET /apps/{name}  (cached 35s)     │   │
  │ the CLIENT  │──────────────────────────────────────┘   │
  └──────────────┘
```

**The Eureka's key's timings (the numbers to know):**

| The setting | The default | The meaning |
|-------------|-------------|-------------|
| `eureka.instance.renewal-interval-in-seconds` | 30 | The heartbeat's interval (the client's → the server's) |
| `eureka.instance.lease-expiration-duration-in-seconds` | 90 | The lease's expiry — the server's evicts the instance after this without a heartbeat |
| `eureka.instance.lease-renewal-threshold` | 0.85 | The renewal's threshold — the client renews the lease at 85% of the expiry |
| `eureka.client.registry-fetch-interval-seconds` | 30 | The client's registry's fetch's interval |
| `eureka.client.cache-resync-interval-seconds` | 35 | The client's cache's resync's interval |

**The renewal's threshold's maths (the interview's favourite):**

```text
  the LEASE's model:
    the CLIENT renews every 30s
    the SERVER evicts after 90s of silence
    the CLIENT renews again at 30s, 60s, 90s...

  ⚠ the PROBLEM: if the client's heartbeat is DELAYED (a GC pause, a network's
    partition), the server's 90s's clock starts. If the client's renewal
    arrives at 91s, the instance is ALREADY evicted → the client's registration
    is "rejected" and the instance must re-register.

  the FIX: the renewal's THRESHOLD (0.85) — the client renews when 85% of the
    lease has elapsed, i.e. at 76.5s, leaving 13.5s of margin for the delay.
    This is the "the renew's early" pattern: renew's often enough that a single
    missed's heartbeat doesn't kill the registration.

  ⚠ the CLIENT-SIDE's equivalent: the registry's fetch's interval (30s) vs the
    cache's resync (35s) — the client keeps serving the cached's list even if
    the registry's fetch fails, and it retries the next's cycle. THAT is why
    the Eureka's outage doesn't stop the traffic: the clients have a cached's
    list and the instances' health's checks keep filtering it.
```

**The Eureka's "the client is the load's balancer" point (the subtle one):**
The Eureka's server **does not** route the traffic — it hands the client the
instance's list and the client picks. That's why Eureka doesn't need to be in
the hot's path.

### 3.6 The Kubernetes's DNS

```text
  the K8s's SERVICE's DNS (the default's discovery)
  ┌──────────────────────────────────────────────────────────────────┐
  │  the CLIENT's POD                                            │
  │    $ curl http://catalogue.default.svc.cluster.local:8080      │
  │              │                                                 │
  │              ▼                                                 │
  │    the COREDNS's pod:                                          │
  │      catalogue.default.svc.cluster.local → 10.96.14.22 (the    │
  │      ClusterIP — a virtual's IP, not a real's one)             │
  │              │                                                 │
  │              ▼                                                 │
  │    the KUBE-PROXY (on the node) / the iptables's rules:        │
  │      10.96.14.22:8080 → 10.42.1.7:8080 (one of the pods)      │
  │      10.96.14.22:8080 → 10.42.2.3:8080 (the other)            │
  │      (the random's selection, per-connection)                  │
  └──────────────────────────────────────────────────────────────────┘
```

**The K8s's Service's types:**

| The type | The address | The use case |
|---------|-----------|-------------|
| **ClusterIP** | A virtual's IP, the DNS's name | The internal's default |
| **NodePort** | A port on every node (30000-32767) | The external's access without an Ingress |
| **LoadBalancer** | A cloud's LB's address | The cloud's provider (the AWS's ELB) |
| **Headless** | No virtual's IP; DNS returns **all the pod's IPs** | The client-side's load balancing, the stateful's sets, the gRPC's multiple's A records |

**The headless's Service is the interesting one for the microservices:** with a
headless's Service, the DNS returns **all the pod's addresses**, and the client
picks — that gives you **client-side's load balancing** with the K8s's
discovery, and it's what the **gRPC's** clients want (the gRPC's `round_robin`
policy needs the individual's addresses, and it can then do a **connection's
rebalancing** — the HTTP/2's connection's stickiness means a round's robin at
the DNS's level is unfair, but the gRPC's client can rebalance the existing's
connections itself).

**The K8s's headless's Service + the StatefulSet** — the other use case: a
stateful's service (the Kafka's broker, the Postgres's primary) needs a
**stable's identity** (the DNS's name resolves to the specific pod, in order),
and the headless's Service plus the `serviceName` gives exactly that.

### 3.7 The config's server

**The purpose:** externalise the configuration so you can change it without a
rebuild and a redeploy, and centralise the secrets.

```text
  the CONFIG's hierarchy (the Spring Cloud Config's precedence)
    ① the CLASS's path's properties / the REMOTE's config's server's
       high's priority's overrides
    ② the application's properties / the YAML
    ③ the PROFILE's file (the config's server's)
    ④ the application's YAML
    ⑤ the application's properties
    ⑥ the @PropertySource's annotation
    ⑦ the DEFAULT's properties
```

**The config's server's backends:**

| The backend | The source's truth | The notes |
|------------|-------------------|----------|
| **Git** | The repo's branch | The audit's trail, the PR's review, the rollback |
| **Vault** | The secret's store | The dynamic's secrets, the rotation's support |
| **JDBC** | The database's table | The dynamic's config, the admin's UI |
| **Native** | The local's filesystem | The development's mode |
| **Redis** | The key's value | The dynamic's config |

**The four's config's profiles (the ones to know):**

| The profile | The purpose | The example |
|-----------|-------------|-----------|
| `default` | The fallback's values | The port, the log's level |
| `dev` | The local's development | The H2, the H2's console, the mock's beans |
| `test` | The automated's tests | The embedded's DB, the stub's ports |
| `prod` | The production | The real's DB, the strict's logging |

**The refresh's mechanisms (the important's detail):**

```text
  the REFRESH's SCOPES (the Spring Cloud's Config):
    ① NEVER          — the properties are read once at startup (the default)
    ② ON_PROFILES   — the properties are re-read when the profile's changes
    ③ ON_REFRESH    — the properties are re-read when the /actuator/refresh
                       endpoint is called
    ④ PERIODIC      — the properties are re-read on a fixed's interval
```

**The `@RefreshScope`'s mechanism:** the beans that depend on the changed
properties are **destroyed and recreated lazily** on the next access — which means
a `@RefreshScope` bean's **state is lost** (any field's initialised at
construction is re-initialised). And the **gotcha**: a `@Value`-injected
property in a **non**-`@RefreshScope` bean is **not** updated.

### 3.8 The API's gateway

**The Spring Cloud Gateway's position in the stack:**

```text
   the CLIENT (the browser, the mobile, the partner)
        │  HTTPS
        ▼
   ┌──────────────────────────────────────────────┐
   │  the GATEWAY (the Spring Cloud Gateway)      │
   │    · the TLS's termination                    │
   │    · the routing (the path → the service)     │
   │    · the rate's limiting (the per's tenant)   │
   │    · the coarse's auth (the JWT's validation) │
   │    · the request's ID's generation            │
   │    · the CORS's policy                        │
   │    · the BFF's aggregation (the composition)  │
   └──────────────────────────────────────────────┘
        │  HTTP (or the mesh's mTLS)
        ▼
   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
   │  orders      │  │  catalogue   │  │  auth        │
   └──────────────┘  └──────────────┘  └──────────────┘
```

**The Gateway's route's configuration (the YAML's predicates and filters):**

```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 2000
        response-timeout: 5s
        pool:
          type: elastic
          max-idle-time: 30s
      routes:
        - id: orders-service
          uri: ${ORDERS_URL:http://orders-service}
          predicates:
            - Path=/api/orders/**
            - Method=GET,POST
          filters:
            - AddRequestHeader=X-Correlation-Id,{random.uuid}
            - name: CircuitBreaker
              args:
                name: ordersCB
                fallbackUri: forward:/fallback/orders
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@tenantKeyResolver}"
```

**The Gateway's route's predicates (the matching's rules):**

| The predicate | The matches |
|--------------|-----------|
| `Path` | The path's pattern (`/api/orders/**`) |
| `Method` | The HTTP's method |
| `Header` | The header's presence or value |
| `Host` | The host's name |
| `Query` | The query's parameter |
| `Cookie` | The cookie |
| `PathPattern` | The path's pattern (the newer's variant) |
| `RemoteAddr` | The client's IP |
| `Weight` | The traffic's split (the canary's routing!) |

**The Gateway's filters:**

| The filter | The purpose |
|-----------|-------------|
| `AddRequestHeader` / `AddResponseHeader` | The header's manipulation |
| `RemoveRequestHeader` | The header's stripping (the security's hygiene) |
| `RewritePath` | The path's rewriting (the legacy's `/api/v1/x` → the service's `/x`) |
| `StripPrefix` | The prefix's removal |
| `Retry` | The retry's policy (⚠ only for the idempotent's methods) |
| `CircuitBreaker` | The breaker's integration (the `fallbackUri`) |
| `RequestRateLimiter` | The rate's limiting (the Redis's token's bucket) |
| `RequestSize` | The payload's size's limit |
| `ModifyRequestBody` / `ModifyResponseBody` | The body's transformation |
| `SetPath` | The path's rewriting to a different's URL |
| `DedupeResponse` | The duplicate's body's caching |

**The WebFlux's reactivity (the crucial's detail):** the Spring Cloud Gateway is
built on **Spring WebFlux** — it's **reactive** (non-blocking). That has three
consequences the interview loves:

1. **You must NOT call a blocking's API** in a filter (a `jdbcTemplate.query()`,
   a `RestTemplate` call) — it will block the event's loop and destroy the
   throughput. Use the reactive's equivalents (`WebClient`, `R2DBC`) or
   `.subscribeOn(Schedulers.boundedElastic())`.
2. **The `Mono`/`Flux` types are in the signatures** — the filter's methods
   return `Mono<Void>` / `Mono<HttpResponse>`.
3. **The back-pressure** is real — the whole's chain is non-blocking, so a slow
   downstream doesn't hold a thread.

### 3.9 The gateway's cross-cutting's concerns

**The rate limiting's algorithms (the Gateway's Redis's implementation):**

```text
  the TOKEN's BUCKET (the Gateway's default)
    the bucket's capacity = burstCapacity
    the refill's rate = replenishRate per second
    → a burst of burstCapacity is allowed, then the rate's limit applies

  the SLIDING's WINDOW (the Redis's implementation of the Gateway)
    → a stricter's limit: exactly N requests in any rolling's window
```

**The authentication at the gateway's level:**

```text
  the JWT's VALIDATION at the gateway (the Spring Security's resource's server)
    · the signature's verification (the JWKS's fetch + the cache)
    · the exp / nbf / iss / aud's checks
    · the token's revocation's check (the blocklist, the `ver` claim)
    → the gateway's job: the ROUTE's protection (which paths need a token)
    → the service's job: the FINE's authorization (the row's ownership)
```

**The critical's point about the gateway's auth:** the gateway can answer
"**is this token valid?**" but it **cannot** answer "**is this user allowed to
see THIS order?**" — that requires the service's data. So the gateway's auth is
**authentication + coarse's authorization** (the route's level), and the
**service's** authorization (the resource's level) is **mandatory**. A common
and dangerous's bug: a team trusts the gateway's check and skips the service's
check — then any internal's caller (a compromised's service, a developer's
curl) can read any user's data.

### 3.10 The service's mesh

```text
  the MESH (the Istio, the Linkerd) — the sidecar's pattern
  ┌────────────────────────────────────────────────────┐
  │  the POD                                         │
  │  ┌────────────────┐    ┌──────────────────────┐  │
  │  │ the SIDECAR    │    │  the APPLICATION     │  │
  │  │ (Envoy/Linkerd)│◀──▶│  (your Spring Boot) │  │
  │  │ · the mTLS     │    │  (no discovery's     │  │
  │  │ · the routing  │    │   client, no retry,  │  │
  │  │ · the retries  │    │   no breaker, no     │  │
  │  │ · the breaker  │    │   telemetry's SDK)   │  │
  │  │ · the telemetry│    └──────────────────────┘  │
  │  └────────────────┘                              │
  └────────────────────────────────────────────────────┘
```

**The mesh's trade:**

| ✅ The mesh's gains | ❌ The mesh's costs |
|--------------------|---------------------|
| The **language's agnostic** (any's language) | The **sidecar's latency** (~1-2ms per hop, the CPU's cost) |
| The **uniform's policies** (the config's plane) | The **resource's overhead** (the sidecar's memory) |
| The **mTLS's everywhere** (the zero's trust) | The **debugging's complexity** (the sidecar's logs, the config's dump) |
| The **retries and the breakers** without the code | The **control's plane's availability** (the istiod's down = no new's config) |
| The **telemetry's automatic** (the spans without the SDK) | The **learning's curve** (the CRDs, the VirtualService, the DestinationRule) |
| The **traffic's management** (the canary's, the mirroring's) | The **two's configuration's systems** (the mesh's YAML + the app's YAML) |

---

## 4. Visualizations

### 4.1 The three moving's parts

```text
   ┌──────────────────┐
   │  the SERVICE     │──register──▶ ┌──────────────┐
   │  INSTANCE        │              │  the REGISTRY │
   │  (10.42.1.7)     │◀─heartbeat──│  (Eureka)    │
   └──────────────────┘              └──────┬───────┘
                                           │ the instance's list
   ┌──────────────────┐                     │
   │  the CLIENT      │◀────────────────────┘
   │  (cached, 35s)   │──connect──▶ ┌──────────────────┐
   └──────────────────┘             │  the INSTANCE     │
                                    │  (10.42.1.7:8080)│
                                    └──────────────────┘

   ┌──────────────────┐        ┌──────────────────┐
   │  the CONFIG's    │◀──git──│  the CONFIG's     │
   │  SERVER          │        │  REPO             │
   └────────┬─────────┘        └──────────────────┘
            │ the properties (the /{app}/{profile})
            ▼
   ┌──────────────────┐
   │  the CLIENT      │  (refreshes on the schedule / the webhook)
   └──────────────────┘

   ┌──────────┐   ┌──────────────────────────────────┐   ┌──────────┐
   │  the     │──▶│  the GATEWAY                     │──▶│  the     │
   │  CLIENT  │   │  TLS · rate · auth · routing · ID│   │  SERVICE │
   └──────────┘   └──────────────────────────────────┘   └──────────┘
```

*Look at: the registry's arrow goes **to the client**, not the traffic. The
registry is **out of the hot path** — that's the whole design.*

### 4.2 The client's vs the server's load balancing

```text
  the CLIENT's:  the client knows and picks
     [client] ──(registry query)──▶ [registry]
        │                              │
        │◀───── [a, b, c] ─────────────┘
        │
        └──▶ picks b ──▶ [b:8080]        ← no extra hop

  the SERVER's:  a proxy picks
     [client] ──▶ [LB:80] ──▶ [a:8080]
                     └──────▶ [b:8080]   ← one extra hop, one SPOF
```

### 4.3 The config's refresh's flow

```text
  the git's push
       │
       ▼
  the CONFIG's SERVER's webhook (or the poll's interval)
       │
       ▼
  the /actuator/refresh on the affected's services  (or the bus's event)
       │
       ▼
  the @RefreshScope's beans are DESTROYED
       │
       ▼
  the NEXT's access re-creates them with the new's values
       ⚠ the state is LOST (the fields' initialisers re-run)
       ⚠ the NON-@RefreshScope's beans keep the old's values
```

### 4.4 The gateway's filter's chain

```text
  the INBOUND's order (the Spring Cloud Gateway's):
    ① the RemoveCachedBodyFilter   (the body's caching for the later's filters)
    ② the AdaptCachedBodyGlobalFilter
    ③ the RouteToRequestUrlFilter   (the URI's rewriting to the route)
    ④ the ReactiveLoadBalancerClientFilter  (the load's balancing!)
    ⑤ the NettyRoutingFilter       (the actual's call — the proxying)
    ⑥ the NettyWriteResponseFilter (the response's writing)
    ⑦ the RemoveHopByHopHeadersFilter
    ⑧ the ForwardedHeadersFilter   (the X-Forwarded-*'s handling)
    ⑨ the XForwardedHeadersFilter
    ⑩ the RemoveHopByHopHeadersFilter (again, the second's pass)
    ⑪ the WebSocketRoutingFilter   (the WebSocket's upgrade)

  ⚠ the order's matter: the LB's filter (④) MUST come before the routing
    filter (⑤), and the RemoveCachedBody (①) MUST come before any filter
    that reads the body.
```

*Look at: the `ReactiveLoadBalancerClientFilter` is where the `http://service-name`
URI becomes a real host:port — the discovery's integration point.*

---

## 5. Worked Examples

### 5.1 The Eureka's lease's maths (the dry run)

```text
  the SETTINGS:
    the renewal's interval   = 30s
    the lease's expiration   = 90s
    the renewal's threshold  = 0.85

  the TIMELINE:
    t=0s    the instance's registers. the lease's expiry = t+90s
    t=30s   the heartbeat's renews. the lease's expiry = t+90s = 120s
    t=60s   the heartbeat's renews. the lease's expiry = 150s
    ...

  ⚠ the SCENARIO: a 20s's GC pause at t=61s
    t=61s   the GC's pause begins. the heartbeat's CANNOT be sent.
    t=76.5s (the threshold's trigger) — ⚠ the client can't renew, it's paused
    t=81s   the GC's pause ends. the heartbeat's sent.
            ✅ t=81s < 90s → the lease's still valid → NO eviction ✅

  ⚠ WITHOUT the threshold (the client renews only every 30s, at 0, 30, 60, 90):
    t=60s   the heartbeat's sent. the lease's expiry = 150s
    t=90s   the GC's pause (a 20s's pause from t=90 → t=110)
    t=110s  the heartbeat's sent — but the lease's expiry was 150s → ✅ still valid

  the REASON for the threshold: it makes the client's renewal's SCHEDULE's
  independent of the server's clock's phase. The client renews when 85% of the
  lease has elapsed (76.5s), so it has 13.5s of slack — one missed's heartbeat
  is survivable.
```

### 5.2 The registry's outage (the dry run)

```text
  14:00:00  the EUREKA's cluster goes down (all 3 nodes)

  the EXISTING's clients (the instance's list's cached):
    → the cached's list is still there (the last's fetch was at 13:59:30)
    → the resolution's returns the CACHED's list ✅
    → the traffic's continues ✅
    ⚠ but the cached's list's STALE — the instances that scaled up or down
      in the last 30s are missing/extra

  the NEW's registrations:
    → the registration's fails ⚠
    → the instance's logs "the registration's failed" and RETRIES
    → the new's service's pods are NOT in the registry → no traffic ❌
    → ⚠ the instance's OWN's health's check may still pass (the K8s's liveness
      is independent) → the pod's running but invisible → a "the ghost's pod"

  the DEGRADED's modes to design for:
    ① the cached's list's TTL — how long can you serve a stale's list?
       (the Eureka's default: the client keeps it until the next's fetch's
        success — ⚠ effectively forever, which is a risk: the IP's may be
        reassigned!)
    ② the registration's retry's backoff
    ③ the "the self's registration's" fallback — the instance's registers with
       a direct's peer, or the K8s's Service's DNS covers it

  the DESIGN's answer: the registry's cluster must be 3+ nodes across the AZs,
  and the clients' caches must be bounded. In practice, most estates run the
  registry's cluster and the services' discovery together in K8s — where the
  registry IS the K8s's API server, and the outage's blast's radius is the
  same as the cluster's.
```

### 5.3 The gateway's rate limiting (the dry run)

```text
  the CONFIG:
    replenishRate: 100      → 100 tokens per second, refilled continuously
    burstCapacity: 200       → the bucket's maximum of 200 tokens
    keyResolver: tenantId    → per-tenant's buckets

  the TENANT "acme" (the bucket's state: 200 tokens):
    t=0.00   200 requests arrive instantly → 200 tokens spent → 0 left
              ✅ all 200 served (the burst's allowed)
    t=0.10   10 more requests → only 10 tokens were refilled (100/s × 0.1s)
              → 10 served, 0 rejected
    t=1.00   100 tokens refilled → 100 served
    t=1.00   150 more requests arrive → 100 served, 50 rejected with 429

  the RESPONSE's headers (the client should honour them):
    HTTP/1.1 429 Too Many Requests
    X-RateLimit-Limit: 100
    X-RateLimit-Remaining: 0
    X-RateLimit-Reset: 1        ← the seconds until the next token
    Retry-After: 1              ← the standard's way to say "wait"

  ⚠ the KEY's resolution matters: a global's bucket (one key for all) means
    one tenant's traffic starves the others. The per-tenant's key is the
    correct's design for a multi-tenant's API.
```

### 5.4 The K8s's headless's Service (the dry run)

```text
  the SERVICE (headless, clusterIP: None):
    apiVersion: v1
    kind: Service
    metadata:
      name: catalogue
    spec:
      clusterIP: None
      selector:
        app: catalogue
      ports:
        - port: 8080

  the DNS's query from the client:
    $ nslookup catalogue.default.svc.cluster.local
    Name:    catalogue.default.svc.cluster.local
    Address: 10.42.1.7    ← the pod A
    Address: 10.42.2.3    ← the pod B
    Address: 10.42.3.9    ← the pod C

  ⚠ the DNS's TTL is 5s by default (the CoreDNS's configuration) — so the
    client's cached's list is refreshed every 5s, not 30s (Eureka).

  the CLIENT's behaviour (the Spring Cloud's LoadBalancer):
    → the DiscoveryClient returns ALL 3's addresses
    → the load's balancer picks one (the round's robin)
    → ⚠ BUT the HTTP's client keeps a persistent's connection (the keep-alive)
      → the same pod is used for MANY's requests → the load's balancing
        becomes STICKY and unfair ⚠

  the FIX for the gRPC's (and the HTTP/2's): the gRPC's client has its own's
  connection's rebalancing — the `round_robin` policy distributes the RPCs
  across the existing's connections. The gRPC's client + the headless's
  Service = the properly's balanced's solution. For the plain's HTTP, the
  StickySession is the common's workaround.
```

---

## 6. Code from Scratch — Production-Grade

### 6.1 The Spring Cloud's LoadBalancer (the modern's setup)

```java
package com.example.orders.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The LOAD's BALANCED's REST's CLIENT.
 * ⚠ The @LoadBalanced's qualifier is what makes the URI's "http://catalogue"
 *   resolvable to a real host:port via the discovery's client.
 * ⚠ The application should NOT have a plain's RestClient for the
 *   service-to-service calls — it would try to resolve "catalogue" as a
 *   DNS's name and fail.
 */
@Configuration
public class LoadBalancedClientConfig {

    @Bean
    @LoadBalanced
    public RestClient.Builder loadBalancedRestClientBuilder() {
        return RestClient.builder();
    }

    @Bean
    public RestClient catalogueRestClient(
            @LoadBalanced RestClient.Builder builder) {
        return builder
                .baseUrl("http://catalogue-service")   // ⚠ the LOGICAL's name
                .build();
    }
}
```

```java
package com.example.orders.config;

import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.client.loadbalancer.core.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.core.Request;
import org.springframework.cloud.client.loadbalancer.core.Response;
import org.springframework.cloud.client.loadbalancer.core.RoundRobinLoadBalancer;
import org.springframework.cloud.client.loadbalancer.core.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.reactor.ReactorLoadBalancer;
import org.springframework.cloud.client.loadbalancer.reactor.RequestData;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Random;

/**
 * The CUSTOM's LOAD's BALANCING's POLICY.
 * ⚠ The example: the WEIGHTED's algorithm — for the heterogeneous's fleet
 *   (the GPU's nodes, the ARM's nodes, the large's instances).
 */
@Configuration
public class CustomLoadBalancerConfig {

    /**
     * The standard's Spring Cloud LoadBalancer's SPI.
     * ⚠ The class must implement BOTH the blocking's and the reactive's
     *   interfaces — the reactive's one is what the Gateway's uses.
     */
    public static class WeightedLoadBalancer
            implements org.springframework.cloud.client.loadbalancer.core.LoadBalancer,
                       ReactorLoadBalancer<ServiceInstance> {

        private final Random random = new Random();

        @Override
        public ServiceInstance choose(Request request) {
            List<ServiceInstance> instances = request.getInstances();
            if (instances == null || instances.isEmpty()) {
                return null;
            }
            int totalWeight = instances.stream()
                    .mapToInt(i -> Integer.parseInt(i.getMetadata().getOrDefault("weight", "100")))
                    .sum();
            int randomWeight = random.nextInt(totalWeight);
            int cumulative = 0;
            for (ServiceInstance instance : instances) {
                cumulative += Integer.parseInt(instance.getMetadata().getOrDefault("weight", "100"));
                if (randomWeight < cumulative) {
                    return instance;
                }
            }
            return instances.get(instances.size() - 1);
        }

        @Override
        public Mono<Response<ServiceInstance>> choose(RequestData requestData) {
            // ⚠ the reactive's path — the Gateway's uses this one
            return Mono.just(Response.of(choose(
                    new Request(requestData.getServiceId(),
                            requestData.getHint(),
                            requestData.getInstances()))));
        }
    }
}
```

```java
package com.example.orders.config;

import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.cloud.client.loadbalancer.core.ServiceInstance;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The DISCOVERY's DEBUGGING's HELPER.
 * ⚠ This is the FIRST's thing to check when a service's resolution fails:
 *   "what does the registry's actually think the instance's list is?"
 */
@Component
public class DiscoveryDebugHelper {

    private final LoadBalancerClient loadBalancerClient;

    public DiscoveryDebugHelper(LoadBalancerClient loadBalancerClient) {
        this.loadBalancerClient = loadBalancerClient;
    }

    /**
     * The INSTANCE's LIST — the raw's truth from the discovery's client.
     */
    public List<ServiceInstance> getInstances(String serviceId) {
        return loadBalancerClient.getInstances(serviceId);
    }

    /**
     * The HEALTHY's instances — the list AFTER the health's check's filtering.
     * ⚠ The difference between this and getInstances() is the DIAGNOSIS:
     *   if the raw's list has 3 and the healthy's has 0 → the HEALTH's CHECK
     *   is failing (the wrong's path, the wrong's port, the check's timeout).
     */
    public List<ServiceInstance> getHealthyInstances(String serviceId) {
        return loadBalancerClient.getInstances(serviceId).stream()
                .filter(ServiceInstance::isHealthy)
                .toList();
    }

    /**
     * The RESOLUTION — what the client would actually pick.
     */
    public ServiceInstance resolve(String serviceId) {
        return loadBalancerClient.choose(serviceId);
    }
}
```

### 6.2 The Eureka's client's configuration

```yaml
# the EUREKA's CLIENT's configuration
eureka:
  client:
    # the REGISTRY's nodes — the list is tried in ORDER
    # ⚠ use MULTIPLE's nodes so a single's node's failure doesn't stop the app
    service-url:
      defaultZone: http://eureka-1:8761/eureka/,http://eureka-2:8761/eureka/,http://eureka-3:8761/eureka/
    # the registry's FETCH's interval — how often to refresh the instance's list
    registry-fetch-interval-seconds: 30
    # the CACHE's resync — the periodic's full's refresh
    cache-resync-interval-seconds: 35
  instance:
    # the heartbeat's interval — how often to tell the server "I'm alive"
    lease-renewal-interval-in-seconds: 30
    # the lease's expiration — the server's evicts after this of silence
    lease-expiration-duration-in-seconds: 90
    # the renewal's threshold — renew at 85% of the lease
    lease-renewal-threshold: 0.85
    # the instance's identification
    instance-id: ${spring.application.name}:${random.uuid}
    prefer-ip-address: true
    # the health's check's URL — the discovery's health's indicator
    health-check-url-path: /actuator/health
    # the metadata's weights — for the custom's load's balancer
    metadata-map.weight: "100"
  # ⚠ the DISABLE's flag — REQUIRED for the non-Eureka's discovery
  #   (e.g. when you use the K8s's discovery, the Eureka's client must be off
  #    or the app will try to reach a non-existent's Eureka)
  # enabled: false

spring:
  cloud:
    loadbalancer:
      # the health's check's configuration
      health-check:
        initial-delay: 5s
        interval: 15s
        # ⚠ the health's check's timeout must be SHORTER than the interval
        #   or you'll get overlapping's checks
        timeout: 5000
      # the caching's configuration
      cache:
        enabled: true
        ttl: 35s
        capacity: 256
```

### 6.3 The config's client (the Spring Cloud's Config)

```java
package com.example.orders.config;

import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The REFRESHABLE's BEAN.
 * ⚠ The @RefreshScope is what makes the properties' changes visible at
 *   runtime: the bean is DESTROYED and re-created lazily on the next access.
 * ⚠ The @Value must be on a METHOD's parameter or a field, and the bean must
 *   be the @RefreshScope's — a plain's singleton keeps the old's value.
 */
@Component
@RefreshScope
public class FeatureFlags {

    private final boolean newCheckoutEnabled;
    private final int maxCartItems;
    private final String pricingServiceUrl;

    public FeatureFlags(
            @Value("${features.new-checkout.enabled:false}") boolean newCheckoutEnabled,
            @Value("${features.cart.max-items:50}") int maxCartItems,
            @Value("${services.pricing.url:http://pricing-service}") String pricingServiceUrl) {
        this.newCheckoutEnabled = newCheckoutEnabled;
        this.maxCartItems = maxCartItems;
        this.pricingServiceUrl = pricingServiceUrl;
    }

    public boolean isNewCheckoutEnabled() { return newCheckoutEnabled; }
    public int getMaxCartItems() { return maxCartItems; }
    public String getPricingServiceUrl() { return pricingServiceUrl; }
}
```

```java
package com.example.orders.config;

import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The REFRESH's TRIGGER — the endpoint's variant.
 * ⚠ In production this MUST be protected (the admin's role, the network's
 *   policy, the mTLS) — it's a config's mutation's endpoint and an
 *   unauthenticated's one is a serious's vulnerability.
 */
@RestController
public class RefreshController {

    private final ContextRefresher contextRefresher;

    public RefreshController(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    @PostMapping("/actuator/refresh")
    public String[] refresh() {
        // ⚠ the returned array is the list of the CHANGED's keys
        return contextRefresher.refresh();
    }
}
```

```yaml
# the CONFIG's SERVER's configuration
spring:
  cloud:
    config:
      server:
        # the BACKEND's order — the FIRST's match wins
        # ⚠ the Git's backend must come FIRST, because it can serve the
        #   Vault's credentials (a "native" profile per the environment)
        git:
          uri: https://github.com/myorg/config-repo
          default-label: main
          search-paths: '{application}'
          # the Vault's bridge — the repo's file says "use Vault for the secrets"
          # clone-on-start: true
        vault:
          uri: https://vault.example.com:8200
          # the paths' order matters
          paths:
            - secret/data/application
            - secret/data/{application}/{profile}
          # the authentication's method
          token: ${VAULT_TOKEN}
        # the NATIVE's backend — the local's filesystem (the development's mode)
        native:
          search-locations: file://${user.home}/config-repo
        # the OVERRIDE's system's properties — the highest's priority
        override-none: false
        # the FAIL's fast — ⚠ false is the SAFE's default for the production
        #   (the app should start even if the config's server is down,
        #    using the last's known's or the local's values)
        fail-fast: false
```

```java
package com.example.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Min;

/**
 * The TYPED's CONFIGURATION — the @ConfigurationProperties.
 * ⚠ This is the right's way to bind the configuration: the type's safe, the
 *   relaxed's binding's supported, and the validation's declarative.
 * ⚠ The @Validated + the constraints mean a MISCONFIGURED's app FAILS AT
 *   STARTUP with a clear's message, instead of failing at runtime when the
 *   bad's value is first used. That's a huge's operability's win.
 */
@Component
@ConfigurationProperties(prefix = "orders")
@Validated
public class OrdersProperties {

    @NotBlank
    private String catalogueServiceUrl = "http://catalogue-service";

    @Min(1)
    private int maxItemsPerOrder = 100;

    private Duration checkoutTimeout = Duration.ofSeconds(3);

    // the getters and the setters
    public String getCatalogueServiceUrl() { return catalogueServiceUrl; }
    public void setCatalogueServiceUrl(String v) { this.catalogueServiceUrl = v; }
    public int getMaxItemsPerOrder() { return maxItemsPerOrder; }
    public void setMaxItemsPerOrder(int v) { this.maxItemsPerOrder = v; }
    public Duration getCheckoutTimeout() { return checkoutTimeout; }
    public void setCheckoutTimeout(Duration v) { this.checkoutTimeout = v; }
}
```

### 6.4 The API's gateway's configuration (the complete's route's setup)

```yaml
spring:
  cloud:
    gateway:
      # the DISCOVERY's locator — enables the automatic's routes for the
      # services in the registry (the serviceId → /serviceId/**)
      discovery:
        locator:
          enabled: true
          lower-case-service-id: true
          # ⚠ the predicates' order — the specific's routes come first
          predicates:
            - name: Path
              args:
                pattern: "'/'+serviceId.toLowerCase()+'/**'"

      # the HTTP's CLIENT's settings (the Gateway's → the service's)
      httpclient:
        connect-timeout: 2000
        response-timeout: 10s
        pool:
          type: elastic
          max-idle-time: 30s
      # the ROUTER's settings
      routes:
        # ① the AUTH's service (the prefix's routing, the simplest's route)
        - id: auth-service
          uri: ${AUTH_URL:http://auth-service}
          predicates:
            - Path=/api/auth/**
          filters:
            - RemoveRequestHeader=Cookie     # the security's hygiene

        # ② the ORDERS's service (the prefix's stripping, the rate's limit)
        - id: orders-service
          uri: ${ORDERS_URL:http://orders-service}
          predicates:
            - Path=/api/orders/**
          filters:
            - StripPrefix=1
            - AddRequestHeader=X-Correlation-Id,{random.uuid}
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200
                key-resolver: "#{@tenantKeyResolver}"
            - name: Retry
              args:
                retries: 2
                statuses: BAD_GATEWAY,SERVICE_UNAVAILABLE,GATEWAY_TIMEOUT
                methods: GET
                backoff:
                  firstBackoff: 100ms
                  maxBackoff: 1s
                  factor: 2
                  # ⚠ the JITTER — without it, every's client retries in lockstep
                  jitter: 0.5

        # ③ the REPORTING's service (the BFF's composition, the caching)
        - id: reporting-service
          uri: ${REPORTING_URL:http://reporting-service}
          predicates:
            - Path=/api/reports/**
          filters:
            - StripPrefix=1
            - name: Cache
              args:
                cacheName: reportsCache
                timeToLive: 30000
                cacheStatusCodes: 200

# the ACTUATOR's configuration
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway,refresh,prometheus
  endpoint:
    gateway:
      enabled: true
      access:
        # ⚠ the Gateway's routes' dump is SENSITIVE (it shows the internal's
        #   topology) — restrict it
        enabled: false

# the REDIS's rate's limiter
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379
```

```java
package com.example.gateway;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.Optional;

@Configuration
public class RateLimitConfig {

    /**
     * The PER-TENANT's key's resolver.
     * ⚠ The key's choice is a security's decision: a per-IP's key is easy
     *   to spoof (and punishes the NAT's users); a per-tenant's key is what
     *   the multi-tenant's API actually wants.
     */
    @Bean
    @Primary
    public KeyResolver tenantKeyResolver() {
        return exchange -> {
            String tenantId = exchange.getRequest().getHeaders().getFirst("X-Tenant-Id");
            if (tenantId != null) {
                return Mono.just(tenantId);
            }
            // the fallback's key — the client or the IP
            String clientId = exchange.getRequest().getHeaders().getFirst("X-Client-Id");
            if (clientId != null) {
                return Mono.just(clientId);
            }
            return Mono.just(
                    exchange.getRequest().getRemoteAddress() != null
                            ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                            : "unknown");
        };
    }
}
```

```java
package com.example.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * The GATEWAY's GLOBAL's FILTER — the request's ID's generation.
 * ⚠ The request's ID (the correlation's id) must be generated AT THE EDGE —
 *   that's the only place where the whole's journey's starting's point is
 *   known. Generating it deeper means the gateway's logs and the service's
 *   logs have different's ids.
 */
@Component
public class CorrelationIdGlobalFilter implements GatewayFilter, Ordered {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String correlationId = exchange.getRequest().getHeaders().getFirst(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        // the request's side
        exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER, correlationId))
                .build();
        // the response's side — so the client can quote it
        exchange.getResponse().getHeaders().set(HEADER, correlationId);

        return chain.filter(exchange);
    }

    /**
     * ⚠ The ORDER matters: this must run BEFORE the routing's filter so the
     *   header is present on the forwarded's request.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

```java
package com.example.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

/**
 * The GLOBAL's TIMEOUT's FILTER — the latency's budget's enforcement.
 * ⚠ The Gateway's `response-timeout` applies to the whole's response, but
 *   the CONNECT's phase needs a separate's budget. And the timeout's ERROR
 *   must be a proper's 504 — a client must be able to tell "the gateway
 *   gave up" from "the service's failed".
 */
@Component
public class GatewayTimeoutFilter implements GatewayFilter, Ordered {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Route route = exchange.getAttribute(ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
        if (route == null) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange)
                .timeout(TIMEOUT)
                .onErrorResume(TimeoutException.class, ex -> {
                    // ⚠ the 504's response must be the SAME's shape as every
                    //   other's error — the Problem's Details
                    exchange.getResponse().setStatusCode(org.springframework.http.HttpStatus.GATEWAY_TIMEOUT);
                    exchange.getResponse().getHeaders().add("Content-Type", "application/problem+json");
                    byte[] body = """
                            {"title":"The gateway's timeout","status":504,
                             "type":"https://api.example.com/problems/gateway-timeout",
                             "detail":"The downstream's service's did not respond in time"}"""
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    return exchange.getResponse().writeWith(
                            Mono.just(exchange.getResponse().bufferFactory().wrap(body)));
                });
    }

    @Override
    public int getOrder() {
        // ⚠ BEFORE the routing's filter — the timeout's clock's starts here
        return Ordered.HIGHEST_PRECEDENCE + 100;
    }
}
```

### 6.5 The Eureka's server (the HA's configuration)

```yaml
# the EUREKA's SERVER (the peer-to-peer's cluster) — the FIRST node
eureka:
  instance:
    hostname: eureka-1
  client:
    # ⚠ the PEER's URLs — each node's peers' list
    service-url:
      defaultZone: http://eureka-2:8761/eureka/,http://eureka-3:8761/eureka/
    # ⚠ the SERVER's own's address — the clients' fetch's from this
  server:
    # ⚠ the PEER's replication's settings
    peer-eureka-service-url:
      defaultZone: http://eureka-2:8761/eureka/,http://eureka-3:8761/eureka/
    # the self-preservation's mode — ⚠ see the note below
    enable-self-preservation: false
    # the eviction's interval — the periodic's task
    eviction-interval-timer-in-ms: 60000
    # the response's cache's disable
    disable-delta: true

---
# the SECOND node
eureka:
  instance:
    hostname: eureka-2
  client:
    service-url:
      defaultZone: http://eureka-1:8761/eureka/,http://eureka-3:8761/eureka/

---
# the THIRD node
eureka:
  instance:
    hostname: eureka-3
  client:
    service-url:
      defaultZone: http://eureka-1:8761/eureka/,http://eureka-2:8761/eureka/
```

> ⚠ **The self-preservation's note (a classic's interview's question):**
> Eureka's **self-preservation** mode keeps the registrations when the server
> thinks there was a mass's eviction (e.g. a network's partition). The
> **benefit**: a brief's network's blip doesn't evict every's instance and
> cause a **mass's re-registration's storm** (which would DDoS the registry).
> The **cost**: stale's instances stay in the list, and the client sends the
> traffic to dead's instances (the errors, until the health's check filters
> them). The **recommendation**: keep it **enabled** in production (the
> registry's stability matters more than the staleness, and the health's check
> filters the dead's instances anyway), and turn it **off** in the
> development (it hides the problems). Set the **renewal's threshold** so the
> threshold's window is larger than a brief's network's blip.

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | A single Eureka's node | 3+ nodes across the AZs, the peers' replicated | The registry's outage = the new's registrations' failure |
| 2 | The Eureka's client enabled on K8s with a K8s's discovery | Disable one of them | The double's registration's confusion |
| 3 | No timeouts on the Gateway's HTTP's client | Set the connect + the response's timeouts | The hung's downstream holds the request forever |
| 4 | A blocking call in a Gateway's filter | The reactive's APIs or `subscribeOn(boundedElastic())` | The blocking's call stalls the event's loop |
| 5 | A retry on the POST's methods in the Gateway | Only the idempotent's methods | The duplicate's orders |
| 6 | A retry without the jitter | The jitter's configuration | The retry's storm |
| 7 | The gateway's auth used as the service's authorization | The service's own's resource's checks | The privilege's escalation from any's internal's caller |
| 8 | The `@Value` in a non-`@RefreshScope` bean expecting a live's update | The `@RefreshScope` on the bean | The property's not refreshed |
| 9 | The `config's server` with `fail-fast: true` in production | `false` — the app's starts with the defaults | The config's server's outage stops every's deploy |
| 10 | The secrets in the config's repo's plain's text | The Vault's backend or the encrypted's values | The repo's clone's = the secret's leak |
| 11 | The rate limiting's key = the IP | The per-tenant / per-API's key | The NAT's users share the quota; the IP's spoofable |
| 12 | The Gateway's routes' actuator's dump exposed | Disable it or protect it | The internal's topology's disclosure |
| 13 | The rate limiting's Redis down → the API's down | The fail's open (with the alert) or the local's bucket's fallback | The Redis's outage becomes the API's outage |
| 14 | The instance's deregistering immediately on SIGTERM | Deregister **last**, drain first, with the preStop's sleep | The traffic's sent to a draining's instance |
| 15 | A single's instance of a critical's service (no redundancy) | ≥ 2's instances across the AZs | The single's point's failure |
| 16 | The K8s's ClusterIP service with the headless's Service's DNS | The headless's Service for the gRPC's client-side's balancing | The HTTP's keep-alive makes the LB's sticky |
| 17 | The `/actuator/refresh` exposed publicly | Protect it (the admin's role, the network's policy) | The config's mutation's endpoint |
| 18 | The gateway's route's order wrong (the catch-all's first) | The specific's routes first, the catch-all's last | The catch-all's swallows the specific's routes |
| 19 | The service's name in the `RestClient` without `@LoadBalanced` | The `@LoadBalanced`'s qualifier | The DNS's resolution of "catalogue" fails |
| 20 | The Eureka's self-preservation off in production | On (with the health's check's filtering) | The mass's eviction's storm on a blip |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: What is service discovery?**
**A:** The mechanism by which a service finds the network address of another
service's instances. In a static's world, it was DNS. In a dynamic's world
(containers, autoscaling, rolling deploys), the addresses change constantly, so
the service registers itself with a registry and the clients query the registry
(caching the result) to get the current list of healthy instances.

**Q: What does an API gateway do?**
**A:** It's the single entry point for the external traffic. It terminates TLS,
routes each request to the right service, applies cross-cutting policies
(rate limiting, coarse authentication, CORS), generates a request/correlation ID,
and can aggregate responses for the front-end clients. It should contain edge
policy, not business logic.

### 🟡 Intermediate
**Q: Client-side vs server-side load balancing — what are the trade-offs?**
**A:** Client-side: the caller queries the registry, gets the instance list, and
picks one itself. Fewer hops and more algorithm flexibility, but every client
carries the discovery library and the algorithm, and the client is responsible
for instance health. Server-side: a load balancer picks. The clients are simple
(one stable URL) and the policy is central, but there's an extra hop and the
balancer is a critical path (a SPOF unless scaled). On Kubernetes, you often
get a third option: the Service's stable ClusterIP with `kube-proxy` doing the
balancing, or a headless Service where the client gets all the pod IPs.

**Q: What is a config server for?**
**A:** Externalising the configuration so it can be changed without a rebuild and
a redeploy, and centralising the secrets. The Spring Cloud Config Server serves
from a Git repo, a Vault, a database, or the local filesystem, keyed by
`{application}-{profile}`. Clients fetch at startup and can refresh on a poll or
on a webhook. The `fail-fast` flag matters: in production it should be `false`
so a config-server outage doesn't prevent deployments.

### 🔴 Advanced / Senior
**Q: How do you make service discovery resilient to a registry outage?**
**A:** Layer it. (1) The clients cache the instance list locally and keep serving
it when the registry is unreachable — that's the core of the design; a registry
outage must not be in the request path. (2) Multiple registry nodes, peered, so
the clients have more than one to try. (3) Bounded cache TTL, because an
unbounded stale list can point at IPs that have been reassigned. (4) The
registrations retry with backoff, and the service is degraded (not invisible)
in the meantime. (5) On Kubernetes, the registry *is* the API server and the
Service's DNS provides the fallback — the service is reachable by its DNS name
even if the client's own discovery is broken. (6) Health checks filter the dead
instances out of the cached list, so a stale list is degraded but not fatal.
And the honest caveat: after a long registry outage, the cached lists diverge
between clients, so a **reconciliation** on recovery (re-register everything
and force a refresh) matters.

**Q: Why would you use a config server instead of environment variables?**
**A:** Environment variables are actually the *stronger* choice for secrets in
many cases — they're provided by the orchestrator, they don't live in git, and
there's no runtime's fetch. The config server's value is elsewhere: a **central,
versioned, auditable** source for the *non-secret* configuration, with
**profile-based** and **per-service** overrides, the **history** (the git's
rollback), and the **dynamic's refresh** (change a feature flag without a
redeploy). The best practice is a hybrid: the secrets come from the
orchestrator's secrets (or a Vault's integration), and the config's server
holds the feature's flags and the endpoints — and the config server's backend
can even *bridge* to the Vault, so the repo's file says "these keys come from
the Vault" without the values ever being in git.

**Q: How do you rate limit at the gateway?**
**A:** The Spring Cloud Gateway's `RequestRateLimiter` filter with the Redis's
token bucket: `replenishRate` tokens per second, `burstCapacity` as the bucket's
size. The key resolver decides the granularity — per API key or per tenant is
right for a multi-tenant API; per IP is easy to spoof and punishes users behind
a NAT. The rate-limited response should be a 429 with `Retry-After` and the
`X-RateLimit-*` headers so clients can back off correctly. Two production
concerns: the Redis is a dependency, so decide the fail mode explicitly (fail
open with an alert, or fail closed for the sensitive APIs), and the local
per-instance counting doesn't work — the rate must be global, which is why the
Redis's implementation exists.

### ⚫ Expert / Principal
**Q: "We have 40 microservices on Kubernetes. Is a service mesh worth it?"**
>
> **The honest answer: for the traffic's shape you have, probably not yet — and
> the decision's driver is the *operational's* maturity, not the service's
> count.** Let me take it in three's parts.
>
> **(1) The mesh's actual's value is the *uniformity* and the *zero's trust*.**
> The Istio/Linkerd give you mTLS between every service (the zero's trust,
> replacing the network's perimeter), the traffic's management (the canary's,
> the mirroring's, the fault's injection) declared in YAML rather than in code,
> and — this is the real's win — **the retries, the timeouts, the circuit's
> breakers, and the telemetry's instrumentation, applied to every service
> without a single line of application code**. The last one is huge: it's the
> difference between 40 services that all have consistent resilience and
> correlation IDs, and 40 services where each team implemented it slightly
> differently (or didn't).
>
> **(2) The mesh's costs are real and often underestimated.** The sidecar's
> **latency** (~1-2ms per hop, and at 5 hops that's 5-10ms of pure overhead on
> a p99 that's already 200ms), the **CPU and memory** per pod (the sidecar's
> often doubles the pod's resource's footprint — and people forget to halve
> the app's container's limits), the **control's plane's availability**
> (istiod's down = no new configuration's rollout), the **debugging's
> complexity** (when a request fails, is it the app, the sidecar, or the
> mesh's config?), and — the one I raise first with a team — the **two's
> configuration's systems**: the mesh's YAML and the application's YAML, and
> the questions of "which one owns the timeout?" and "why does the mesh's
> policy say 3s but the app's says 5s?". That last one is a real's operational
> trap.
>
> **(3) So, my actual's recommendation for your situation.** The question is
> what your **pain** is. If the pain is "the retries and the breakers are
> implemented inconsistently and the correlation IDs don't match across
> services" — that's a **library's** problem, not a mesh's problem. A
> **shared's resilience's library** (the Spring Cloud's Circuit Breaker with
> the common's configuration, a **correlation's ID's filter** in the platform's
> starter, the OpenTelemetry's SDK auto-instrumented) delivers 80% of the
> mesh's value for 5% of the cost and none of the operational's overhead. If
> the pain is "we need the mTLS's everywhere and the zero's trust's
> perimeter's model" — that's a genuine's mesh's case, and the Istio's
> ambient's mode (without a sidecar) has substantially reduced the overhead's
> argument. If the pain is "we need the canary's releases and the traffic's
> mirroring" — the Argo's Rollouts or Flagger with the Istio's integration
> (or a simpler's blue/green) does that without a full's mesh. **My
> sequence:** (a) the platform's team builds the **golden's path** — the
> service's template with the correlation's ID, the timeouts, the retries, the
> breaker, and the telemetry's SDK baked in — so every service is consistent
> *by default*; (b) the services' network's policies are tightened at the
> platform's level (the network's policies, the service's accounts, the
> admission's controller); (c) only then evaluate the mesh, and evaluate it
> against the **measured** cost: the sidecar's latency in your p99, the
> resource's overhead in your pods, and the operational's burden on your
> on-call. And the **one's warning**: don't adopt a mesh to solve a
> *microservices' boundary's* problem — if your services are chatty and
> coupled, a mesh will make the coupling's latency's *visible* and
> *expensive* without fixing the coupling.

---

## 9. Counter-Questions & Traps

### CQ1. "Why not just use DNS for service discovery? It's simpler."
- **Naive answer:** "DNS is enough." ← the trap
- **The trap:** The interviewer wants you to know the specific's gaps of DNS for
this use case.
- **Expert answer:** DNS *is* the answer in many setups — Kubernetes's Service's
DNS is the standard's mechanism, and the DNS's SRV records and the weighted
round-robin have been improving. The gaps are the reasons a dedicated registry
exists: (1) **The health's awareness** — DNS doesn't know if a backend is alive;
a DNS's record can point at a dead's server, and the client's DNS's cache keeps
it there for the TTL. (2) **The TTL's latency** — DNS's caches are aggressively
cached (and the negative's caching delays a new's registration's discovery), so
the scale-up's time-to-serve is the TTL's time. (3) **The rich's metadata** — a
registry carries the instance's zone, the weights, the version, the tags, the
metadata, the health's status — DNS's records carry none of that (you can
encode it in SRV's priorities, but it's a hack). (4) **The dynamic's
registration** — a pod's IP changes and the DNS's record must be updated; the
registries handle this with a push. (5) **The negative's caching** — a
not-yet-registered's service's name is cached as "doesn't exist" and clients
fail for the whole's TTL. So the honest's framing: "DNS is fine when the
topology is relatively's static and the K8s's Service's abstraction gives you
the DNS's name with a health's-aware's proxy behind it. You need a full
registry when you need the rich's metadata, the fast's re-registration, and
the health's state — which is what Eureka/Consul give you. In practice, most
modern's estates on K8s use **both**: the K8s's Service's DNS for the
addressing, and a registry (or the K8s's API) for the discovery's logic."

### CQ2. "Should the gateway do the authentication, or the services?"
- **Naive answer:** "The gateway does the auth, so the services don't have to."
← the trap
- **The trap:** The interviewer wants the **authentication vs authorization**
distinction, and the **defence in depth's** argument.
- **Expert answer:** Both, for different reasons. The **gateway** should do the
**authentication** (is this token valid? the signature, the expiry, the issuer,
the audience) and the **coarse's authorization** (is this route's prefix
protected? does this API key have access to this endpoint?). The **services**
must do the **resource's authorization** (is this user allowed to see THIS
order?) — because that requires the data, which only the service has. The
**danger** of trusting the gateway's check alone: any caller who can reach the
service's network — another service, a developer's curl, a compromised's pod —
bypasses the gateway entirely. In a flat's network, "the gateway did the auth"
is not a security's control; it's a convenience's assumption. The right's
model: the gateway is the **first** check (the fast's rejection of the invalid
tokens, the rate's limiting of the unauthenticated's floods, the single's
policy's point), and each service **re-validates** the token (cheap — the JWKS
is cached and the signature's verification is microseconds) and enforces its
own's resource's rules. And the **propagation's** detail: the identity's
context (the user id, the tenant, the roles) is passed as a **signed** header or
re-derived from the token, never trusted as a plain's header — otherwise a
caller can just set `X-User-Id: admin`.

### CQ3. "Why not use a global load balancer instead of service discovery?"
- **Naive answer:** "An F5/LB in front is simpler." ← the trap
- **The trap:** The interviewer wants you to see that the **global's LB** solves
a different's problem.
- **Expert answer:** A global's load balancer in front of the *whole* estate is
fine for the **ingress** (the external's traffic's entry) — that's what the
cloud's LB or the K8s's Ingress does. But it doesn't solve the **internal's
service-to-service's discovery**: the orders' service still needs to find the
catalogue's *specific's* instances, and it needs to know which ones are healthy
and which zone they're in. A single's global's LB can't route
"orders → catalogue" without a route per pair, which is O(n²) and unmaintainable.
And the **granularity's** problem: a global's LB balances across the *whole*
fleet, so a request from the orders' service could land on a pod in the same
zone (good) or across the zone (a 30ms penalty and a data's transfer's cost) —
whereas the service-aware's discovery lets the client pick the local's instance.
The **modern's synthesis** is exactly what Kubernetes does: a global's LB (or
the Ingress) for the **north-south** traffic and a per-service's ClusterIP
(south-south) for the **east-west**. The mesh's data plane extends the
east-west's awareness down to the protocol's level (the zone's awareness, the
outlier's detection).

### CQ4. "What's the risk of a service registry becoming a single point of failure?"
- **Naive answer:** "It's a single point of failure, so we run 3 of them." ← the
trap
- **The trap:** The interviewer wants you to notice that the **availability's
model** is the real's issue — 3 nodes that **fail together** are still one SPOF.
- **Expert answer:** Running 3 nodes is necessary but not sufficient. The real's
questions are: (1) **Do they fail independently?** If they're in the same
Kubernetes cluster, a cluster's failure takes all 3 — so the registry's HA must
span clusters, or the platform's level must be different (a managed's
registry's service). (2) **Is the registry in the request's path?** If every
request's resolution hits it, it's a hard's SPOF; the **caching** is what takes
it out of the path (the client holds the list for the TTL and serves it). (3)
**What happens during a partition?** This is the **split's brain** case — with
an AP's model (Eureka), the nodes disagree and the clients get different lists;
with a CP's model (Consul/etcd), the minority's partition's side is
**unavailable** for the registrations (which is correct — you don't want two
registrations' truth). (4) **The mass's eviction's storm** — the registry's
partition (or a GC's pause) makes the servers evict the instances; if the
clients then all re-register at once, the registry is **DDoS'd** by its own
clients. Eureka's **self-preservation** mode exists precisely for this. (5) The
**recovery's** requirement — after a long's outage, the caches are divergent, so
a **reconciliation's job** (re-register, force a refresh) is needed. So the
answer's structure: HA's replicas, independent's failure's domains, the
**caching** that takes it out of the hot's path, the self-preservation's mode,
and a reconciliation's runbook. And the honest's conclusion: on Kubernetes,
the registry *is* the API server, which is HA by the platform's design — and
that consolidation is a feature, not a coincidence.

### CQ5. "Why would you ever not use a config server?"
- **Naive answer:** "Config servers are always better." ← the trap
- **The trap:** The interviewer wants you to identify the **failure's mode** of
the config's server as a deployment's blocker.
- **Expert answer:** The config's server's **availability** is a **deployment's
dependency**: if it's down, no service can start with the correct's
configuration. That's a serious's operational's risk, and the mitigations are:
(1) **`fail-fast: false`** — the client starts with the local's/defaults if the
server's unreachable, so a config's server's outage doesn't block every's
deploy. (2) **The orchestrator's fallback** — bake the critical's configuration
into the image or the deployment's manifest, so there's a working's default.
(3) **The config's caching's resilience** — the client caches the last's known
good's values and refreshes in the background, so a runtime's outage is a
non-event (only a *cold start* is affected). The **reasons to use one**
anyway: the **feature's flags** (change without a redeploy), the **multi-
environment's promotion** (the same's image, the different's config), the
**centralised's secrets** (the Vault's integration), and the **audit's
trail** (the git's history). The **reasons not to**: the **runtime's
dependency** (as above), the **latency** of a fetch at startup, the **complexity**
of a second's system to operate, and the **coupling** to a git's repo's layout.
The **modern's middle**: the **orchestrator's environment's variables** for the
secrets and the critical's endpoints (no runtime's dependency, no fetch's
latency), and the **config's server** for the feature's flags and the
non-critical's settings (where a stale's value is acceptable). And the honest's
framing: "the config's server's *availability* is the cost; the *freshness* is
the benefit. Put the settings that **must** be correct at startup in the
environment's variables, and the settings that should change **without** a
redeploy in the config's server."

### CQ6. "How would you migrate from Eureka to Kubernetes DNS without downtime?"
- **Naive answer:** "Change the config and restart." ← the trap
- **The trap:** The interviewer wants the **migration's** mechanics — the
transition's period where both's mechanisms are active.
- **Expert answer:** The migration's path has an **overlap's phase**, and the
mechanics are the Spring Cloud's abstraction's job. Spring Cloud LoadBalancer
has a `ServiceInstanceListSupplier` hierarchy, so you can supply from **both**
sources and let the clients' discovery work in either's mode. The practical's
sequence: (1) **Deploy the K8s's services** with the Service's DNS, but keep
the Eureka's registration's enabled — the app registers with Eureka *and* is
reachable via the DNS. (2) **Migrate the callers** one at a time: switch the
caller's `spring.cloud.discovery.enabled` to `false` and
`spring.cloud.kubernetes.discovery.enabled` to `true`, so the caller resolves
via the K8s's API (the `ServiceInstanceListSupplier` reads the pods from the
API and builds the instance's list) while the callee is still registered with
Eureka. (3) **Verify** the traffic's shift (the caller's metrics, the
Eureka's registration's counts going to zero). (4) **Disable the Eureka's
client** on the callee, and shut the Eureka's cluster down. The **watch-outs**:
the two's systems can **disagree** about the instance's list (the K8s's pod's
readiness vs the Eureka's heartbeat) — so during the overlap, the *health's
semantics* must be aligned (the K8s's readiness's probe should reflect what the
Eureka's health's check checked). And the **rollback's** path: the config's
change is revertible, so keep the Eureka's alive until the **last** caller has
migrated and a full's business's cycle has passed. The **honest's framing**:
"the overlap's phase is where the bugs live — the two's sources of truth
disagreeing about the health — so I'd align the health's semantics first
(the K8s's readiness's probe = the same's check), migrate the callers
one-by-one with the metrics's verification, and only then retire the Eureka's."

### CQ7. "How do you prevent a client from hammering a service that's being
deployed?"
- **Naive answer:** "The service's registered as down during the deploy." ← the
trap
- **The trap:** The interviewer wants you to know about the **deregistration's
timing** and the **propagation's delay**.
- **Expert answer:** The **deregistration's order** is the key. On SIGTERM, the
instance should: (1) **stop accepting the new's traffic** — in Kubernetes, the
`preStop` hook (a `sleep 15` or an endpoint's removal's wait) and the
**readiness's probe's failure** do this; (2) **drain** the in-flight's
requests; (3) **deregister** from the registry **last**. If it deregisters
first, the registry's clients start sending to an instance that's still
draining (or worse, still alive but shutting down) — the errors. And the
**propagation's delay** is the real's subtlety: the deregistration must
**propagate** to all the clients' caches before the instance actually stops —
which takes the cache's TTL (30s for the Eureka's default). If the instance
stops immediately after deregistering, the clients that haven't yet seen the
deregistration will still send to it → the connection's refused → the errors.
The **solutions**: the **preStop's delay** (the standard's K8s's pattern — sleep
for longer than the propagation's delay), the **server's graceful's shutdown**
(the Spring Boot's `server.shutdown=graceful` with a `spring.lifecycle.timeout-
per-shutdown-phase`), and the **client's retry** (which absorbs the refused's
connections). And the **deploy's strategy's** choice: the **rolling's update**
with `maxUnavailable: 0` and `maxSurge: 1` guarantees the capacity throughout,
and the **blue/green** or the **canary** eliminates the cutover's window
entirely. The **honest's framing**: "the deregistration's *order* and the
*propagation's delay* are the two's mechanisms; a graceful's shutdown plus a
preStop's delay plus the client's retry covers it, and the real's fix is the
canary's rollout, which removes the cutover's window."

### CQ8. "Your gateway is slow. How do you profile it?"
- **Naive answer:** "Add more gateway instances." ← the trap
- **The trap:** The interviewer wants you to know that the Gateway is
**reactive** and that the common's bottleneck is the **reactive's blocking**.
- **Expert answer:** The first's step is the **reactive's** nature: the Gateway
runs on Netty's event's loop, and **any blocking's call** in a filter (a
`jdbcTemplate.query()`, a `RestTemplate` call, a `Thread.sleep`, a blocking's
file read) **stalls the event's loop** and destroys the throughput — one
blocking call can take down the whole's instance's concurrency. The classic's
symptom: the Gateway's p50 is fine but the p99 is seconds, and the CPU is
*low* (the threads are blocked, not busy) — a signature that points at blocking,
not at CPU. The **diagnosis**: the **reactor's** `BlockHound** integration (it
throws when a blocking's call is detected on a non-blocking's thread), the
**thread's dump** (`jcmd <pid> Thread.print` — the Netty's threads are in
`WAITING`/`TIMED_WAITING` on a socket's read), the **micrometer's** metrics on
the Gateway's filters (the `reactor.netty` metrics), and the **reactive's
metrics** (the pending's queue's depth). The **fixes**: the **reactive's APIs**
everywhere (`WebClient` over `RestTemplate`, the `R2DBC` over the `JdbcTemplate`),
or the **`subscribeOn(Schedulers.boundedElastic())`** for the unavoidable's
blocking (the database's lookup, the legacy's call). The **other's causes** of a
slow's Gateway: the **route's predicate's cost** (a complex's SpEL's predicate
is evaluated per-request), the **filter's count** (every filter is a
composable's step), the **body's buffering** (the `ModifyRequestBody` reads the
whole body into memory), the **connection's pool's exhaustion** (the Gateway's
HTTP's client to the downstreams), and the **gateway's single's thread** (the
Gateway is inherently a **single's threaded's event loop** per core — a
CPU-bound's filter will block everything). The **reactive's** answer to a
CPU-bound's filter: the `Mono.fromCallable(...).subscribeOn(boundedElastic())`
or the WebFlux's `WebClient`'s own's offloading.

### CQ9. "How do you handle the rate limiter's Redis going down?"
- **Naive answer:** "The rate limiting's off, so the API's unprotected." ← the
trap
- **The trap:** The interviewer wants the **fail-open vs fail-closed** decision
and its reasoning per API's class.
- **Expert answer:** The decision is **per API's class**, and it should be
explicit in the config. **Fail open** (the rate limiting is skipped, the
traffic's allowed) for the **read-only, public's APIs** — the availability's
value is higher than the protection's value, and the real's protection comes
from the upstream's WAF/CDN anyway. **Fail closed** (the requests are
rejected) for the **expensive, abusable's endpoints** — the ones where the
downstream's cost per request is high (the report's generation, the LLM's
call, the export's download) — because there the abuse's cost is real and
immediate. The **implementation's options**: the **local's fallback's bucket**
(each Gateway's instance enforces a per-instance's limit — a weaker but
non-zero's protection; the global's limit becomes the per-instance's limit ×
the instance's count, which is acceptable as a degraded's mode), the **circuit's
breaker** around the Redis's calls (the Redis's slowness becomes the
rate's limiting's slowness, and the breaker opens → the fail's open's path),
and the **fail's fast's cache** of the last's known's rate. And the
**observability's requirement**: the fail-open's path must be **loud** — a
metric for the "the rate's limiting's bypassed" counter and an alert, because a
silent's fail-open is a **free's tier** for the attackers. The **honest's
framing**: "the rate's limiter is a *protection*, not a *correctness* mechanism.
The default's fail's open is right for the reads, and the expensive's
endpoints need the fail's closed with a local's fallback's bucket as the
middle's ground. And the fail-open's path must be monitored, or you'll
discover the bypass's happening from the invoice."

### CQ10. "The service name is in the config, but the service is renamed. How do
you handle it?"
- **Naive answer:** "Update the config and redeploy." ← the trap
- **The trap:** The interviewer wants the **contract's** view — a service's name
is a contract, and the migration's plan is the same as any's contract's
evolution.
- **Expert answer:** The service's name is a **dependency's identifier** in the
consumers' config — so renaming it is a **breaking's change** for every
consumer, and the migration is the same's discipline as any's API's evolution:
(1) **The alias** — register the new's name *alongside* the old's for a
transition's period (in Eureka you can register multiple's names; in K8s you
create a second's Service pointing at the same's pods; with the Gateway's
routes you add a second's route). (2) **The telemetry's gate** — the
consumers' usage's dashboard: which consumers are still resolving the old's
name? You can't remove it until that list is empty. (3) **The dated's
communication** — the deprecation's notice with the date. (4) **The removal** —
delete the alias and the old's route only after the telemetry's proof. And
the **better's design's answer**: **don't put the service's names in the
config at all** — use a **logical's alias** (the `orders` → `orders-v2` mapping
in the Gateway's routes, or the Spring's `@LoadBalanced` with a stable's
interface's name) so the rename is the **provider's** problem, not the
consumers'. The **honest's framing**: "the service's name is a contract, and
the discipline is the same as for any's API: the alias during the overlap, the
telemetry's gate on the removal, and the dated's notice. The design's fix is
to put the alias in the *platform's* layer (the Gateway's route, the mesh's
virtual's service) rather than in every consumer's config — then a rename is
one's change, not fifty."

---

## 10. Rapid-Fire MCQs

**1.** The Eureka's client's registry's fetch's default's interval:
A) 5s  B) 30s  C) 60s  D) 120s
**✅ B**

**2.** The Eureka's lease's expiration's default:
A) 30s  B) 60s  C) 90s  D) 300s
**✅ C**

**3.** The Spring Cloud LoadBalancer's default's algorithm:
A) Random  B) Round Robin  C) Weighted  D) Least's response's time
**✅ B**

**4.** The K8s's headless's Service's DNS returns:
A) The ClusterIP  B) All the pod's IPs  C) The node's IPs  D) Nothing
**✅ B**

**5.** The Spring Cloud Gateway is built on:
A) Spring MVC  B) Spring WebFlux  C) Servlet 3.0  D) Netty's raw's API
**✅ B**

**6.** A blocking's call in a Gateway's filter:
A) Is fine  B) Blocks the event's loop  C) Is faster  D) Is auto's offloaded
**✅ B**

**7.** The `@RefreshScope` bean's state on a refresh:
A) Is preserved  B) Is lost (the bean is re-created)  C) Is persisted  D) Is shared
**✅ B**

**8.** The `fail-fast: false` in the config's server means:
A) Fail fast  B) Start with the defaults if the server's down  C) Ignore the config  D) Cache forever
**✅ B**

**9.** The gateway's rate limiting's key's resolver should be:
A) The IP  B) The tenant / the API's key  C) The URL  D) Random
**✅ B**

**10.** The Eureka's self-preservation's mode exists to prevent:
A) The slow's startup  B) The mass's eviction's storm  C) The split's brain  D) The DNS's failure
**✅ B**

**11.** In the K8s's Service's model, the service's ClusterIP is:
A) A real's pod's IP  B) A virtual's IP  C) The node's IP  D) The gateway's IP
**✅ B**

**12.** The gateway's `Retry` filter's `methods` should be:
A) All  B) Only the idempotent's methods  C) POST only  D) None
**✅ B**

**13.** The config's server's `fail-fast` should be `false` in production because:
A) It's faster  B) The config's server's outage shouldn't block the deploys  C) The config isn't needed  D) The Vault's down
**✅ B**

**14.** The client-side's load balancing's advantage is:
A) The simpler's client  B) The fewer's hops + the flexibility  C) The central's policy  D) The fewer's libraries
**✅ B**

**15.** The `X-Forwarded-For` header's purpose:
A) The auth  B) The original's client's IP behind the proxies  C) The routing  D) The caching
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The Eureka's lease's behaviour**
1. Run a Eureka's server and a client; watch the registration and the
   heartbeats in the Eureka's dashboard.
2. Stop the client (kill it); watch the eviction after 90s.
3. Enable the self-preservation; simulate a network's partition; watch the
   difference.

**🧪 Lab 2 — The Gateway's route's filter's order**
1. Create 2's routes (one specific, one catch-all) with the specific's first.
2. Swap the order; observe the catch-all's swallowing the specific's route.
3. Add a `RewritePath` and a `StripPrefix`; observe the path's transformation.

**🧪 Lab 3 — The blocking's call in the Gateway**
1. Add a filter that calls `jdbcTemplate.queryForObject(...)` (a blocking's
   call).
2. Send 100's concurrent's requests; observe the p99's explosion and the low
   CPU.
3. Replace it with a `WebClient` (reactive) and compare.

**🧪 Lab 4 — The config's refresh**
1. Set a property in the config's repo; call `/actuator/refresh`.
2. Observe the `@RefreshScope` bean's value changing; observe the non-scoped
   bean's value NOT changing.
3. Observe the `@RefreshScope` bean's **state** being lost.

---

## 12. Code Challenges

### Challenge 1 — The resilient's discovery's client (medium)
**Goal:** A `ServiceInstanceListSupplier` that combines the discovery's sources
with a **fallback**: the primary's list, the cached's list on failure, and a
**health's filter** that excludes the instances failing a custom's check.
**Requirements:** the caching's TTL, the metrics, the tests.

### Challenge 2 — The gateway's rate's limiter with the Redis's failover (hard)
**Goal:** A `RequestRateLimiter` that falls back to a **local's bucket** when
the Redis's is unavailable, with the **fail-open's** metric and the
per-API's-class's policy.
**Requirements:** the local's bucket (a Caffeine's cache with the expiry), the
metrics, the alert's wiring, the tests.

### Challenge 3 — The config's server's secret's bridge (hard)
**Goal:** A config's server that serves the non-secret's config from Git and
the secrets from Vault, with the **encrypted's values** in the repo (the
Jasypt's style) and the **refresh** on the Vault's change.
**Requirements:** the Vault's integration, the decryption's layer, the
per-environment's paths, the audit.

---

## 13. Scenario-Based Questions

**S1.** *"After deploying our services, we started seeing random 503s during
each deployment for about 2 minutes. The services are on Kubernetes with Spring
Cloud LoadBalancer. What's happening?"**
>
> **Ideal answer:** (1) **The framing:** this is the classic's **"the
deregistration's propagation's lag"** problem, and it's one of the most common
sources of deployment's flakiness in a Spring Cloud + K8s's setup. The 2 minutes
is the tell — it's not a random's bug, it's a **timing's window**. (2) **The
mechanism:** when a pod's terminating, the sequence is: the SIGTERM → the
`preStop` hook → the pod's endpoint's removal from the Service → the
kube-proxy's / CoreDNS's propagation → the clients' **discovery's cache's
refresh** (the `ServiceInstanceListSupplier`'s TTL — 35s by default) → and
**only then** does the client stop sending traffic to that pod. If the pod
**stops accepting connections** before the client's cache has refreshed, the
client sends to a dead's endpoint → the connection's refused → the `503` (or a
`500` from the WebClient's exception's mapping). The window is the **cache's
TTL** (35s) plus the **propagation's delay** — which is exactly the 2 minutes.
(3) **The diagnosis (confirm, don't guess):** look at the **deployment's
timeline** vs the **503's timeline** — they should overlap. Then look at the
**logs** of the failing's requests: the `503` should show a connection's refused
or a reset to a **pod's IP that was just terminated**. Check the **pod's
termination's log** for the pod's IP and compare. And the **readiness's probe's
behaviour**: if the pod's readiness's probe is failing only *after* the
`preStop` completes, the endpoint's removal's timing is wrong. (4) **The fixes,
in the order of the value:**
**Fix 1 — The `preStop` hook's delay (the standard's pattern).** Add a
`preStop: sleep 30` (or longer than the cache's TTL) to the pod's spec, so the
pod stays alive and serving for the propagation's window after the SIGTERM. The
K8s's endpoint's removal starts **when the pod's terminating** (not when the
container's exits), and this sleep keeps the process serving while the
endpoints' removal propagates.
**Fix 2 — The graceful's shutdown.** `server.shutdown=graceful` +
`spring.lifecycle.timeout-per-shutdown-phase=30s` in the application, so the
in-flight's requests are completed rather than cut. Without it, the JVM's exit
kills the connections mid-request.
**Fix 3 — The readiness's probe's failure on SIGTERM.** The `preStop` hook is
what makes the pod's endpoint's removal's *immediate* in K8s (the pod's
`Ready` condition flips to `False` as soon as it's terminating), so the
kube-proxy's removal's propagation starts early — and combined with the
`preStop`'s sleep, the pod keeps serving while it's being removed from the
endpoints.
**Fix 4 — The client's retry.** The `Retry` filter (or the WebClient's retry)
with the connection's errors included, for the idempotent's methods. This is
the **backstop** that absorbs the residual's window.
**Fix 5 — The `maxUnavailable: 0` and `maxSurge: 1`** on the deployment, so
the capacity never drops during the rolling's update. (5) **The prevention:**
the **canary's rollout** (or the **blue/green**) removes the cutover's window
entirely — the new's pods are fully ready before the old's are terminated. And
the **chaos's test** (the deliberate's killing of a pod during the load) proves
the behaviour's before the production's surprise. (6) The senior's framing:
"this is a **timing's** bug, not a random's one, and the two's numbers to
check are the **discovery's cache's TTL** (35s by default — that's the bulk of
the window) and the **propagation's delay** (the kube-proxy's and the DNS's).
The fix is the standard's trio: the **`preStop`'s sleep** to cover the
propagation, the **graceful's shutdown** to cover the in-flight's requests, and
the **readiness's failure** to start the removal's early. And the **architectural's**
fix is the **canary's rollout** — because the rolling's update, by's definition,
has a cutover's window, and the window's size is the discovery's cache's TTL.
The one thing I'd add that people forget: the **propagation's delay** means a
pod's endpoint's removal takes a few seconds *even with* the preStop's hook, so
the sleep's duration should be the *cache's TTL* (35s) and not 5s — teams
routinely set it too short. And the client's retry is the backstop that makes
the residual's window invisible."

**S2.** *"We run 12 microservices on Kubernetes. Our API gateway is a single
Spring Cloud Gateway deployment with 4 replicas. Last week it was the bottleneck
— the p99 for all our APIs went to 2 seconds during peak, while the services
themselves were at 50ms. Where do we look?"**
>
> **Ideal answer:** (1) **The framing:** the symptom's shape is the clue — the
**downstreams are fast (50ms) but the gateway's p99 is 2 seconds**. That's a
**40× amplification**, which points at the gateway's own's processing, not at the
services. The two's candidates: (a) the gateway is **CPU-bound or blocked**
(its own's work is slow), (b) the gateway is **saturated** (the queueing), or
(c) the gateway is **connection's limited** (the downstream's pool). (2) **The
diagnosis, in order:**
**(a) Is the gateway's CPU saturated?** The Gateway is **single's threaded's**
per core (Netty's event's loop) — 4's replicas × 1's core each (if the limit
is 1 CPU, which people often set from the container's convention) = 4's cores
total, which is very little for a gateway handling 1000s of RPS. The **check**:
the container's CPU usage (if it's at the limit and the throttling's metric is
non-zero, the gateway is CPU-bound). The **fix**: more CPU's limit per pod
(the Gateway is I/O-bound, so the limit should be modest but the *count* of
the replicas should be high), and the **profile's** check for what's burning it.
**(b) Is something BLOCKING the event's loop?** This is the Gateway's
signature's failure. Any blocking's call in a filter — a `RestTemplate` call, a
`JdbcTemplate` query, a `Thread.sleep`, a **synchronous's** logging to a slow
sink, a **synchronous's** `ObjectMapper` on a huge's body, a **Redis's**
blocking's call — stalls the whole's event's loop for that pod. The
**signature**: the p50 is fine (the fast's requests are fine), the p99 is
seconds (the blocked's requests queue behind), and the **CPU is LOW** (the
threads are waiting, not burning). The **check**: the **thread's dump** on a
slow's pod (`jcmd 1 Thread.print` — the Netty's threads will be in
`WAITING`/`TIMED_WAITING` on a socket's read) and the **reactor's** metrics.
**BlockHound** in the test's profile catches it definitively.
**(c) Is the connection's pool's exhausted?** The Gateway's HTTP's client to the
downstreams has a **pool** (`spring.cloud.gateway.httpclient.pool`). If the
pool's size is too small (or the type is fixed-size and the downstream is
slow), the requests queue for a connection. The **check**: the pool's metrics
(the `acquired`/`pending` counts) and the `response-timeout`.
**(d) Is a route's filter's expensive?** A complex's **SpEL's predicate**
(`Path=/api/{segment}` with a regex) is evaluated per-request; a
`ModifyRequestBody` buffers the whole body; a `RequestRateLimiter` with a
**remote's Redis** adds a network's hop *and* a blocking's risk. (3) **The
fix's plan:**
- **The immediate's triage**: scale out the gateway's replicas (it's stateless —
  the cheapest's win), raise the CPU's limit per pod, and check the metrics for
  the saturation's signature.
- **The blocking's call's removal**: this is the most common's real's cause.
Find it with the thread's dump and BlockHound; replace it with the reactive's
APIs (`WebClient`, the `R2DBC`) or offload it with
`.subscribeOn(Schedulers.boundedElastic())`.
- **The pool's sizing**: set the pool's type to `elastic` with a sane's
  `max-idle-time`, and set the `response-timeout` so a slow's downstream doesn't
  hold a connection.
- **The filters' audit**: remove the unnecessary's filters, simplify the SpEL's
  predicates (the path's matching is a hot's path), and consider the
  `LocalResponseCache` for the cacheable's routes.
- **The architectural's fix**: the gateway's **rate limiting's Redis** and the
  **header's processing** are edge's work; if the gateway's doing a lot, the
  question is whether it should be a **BFF** (per-client's aggregation) or
  whether the **mesh's sidecar** should take the cross-cutting's concerns off
  the gateway entirely. And the **observability's fix**: the gateway's p99
  should be **broken down by route** (the `spring.cloud.gateway` metrics are
  tagged with the route's id) — a single's route being slow changes the
  diagnosis entirely. (4) **The prevention:** the **load's test** in the CI (the
  gateway's throughput's ceiling with the real's route's configuration), the
  **blocking's call's ban** (a static's analysis's rule or a BlockHound's
  integration in the test's profile), the **per-route's SLO's dashboards**, and
  the **capacity's planning** that accounts for the gateway's CPU's cost per
  request. (5) The senior's framing: "the 40×'s amplification between the
  service's 50ms and the gateway's 2s is the diagnosis's key: the amplification
  is in the gateway, and an amplification of that's shape almost always means
  **blocking**, not saturation. A **saturated** CPU gives you a *uniformly*
  slow's p50 and p99 (everything is slow, because everything is queued). A
  **blocked** event's loop gives you a *good* p50 and a *terrible* p99 (the
  fast's requests are served instantly, the ones that hit the blocking's call
  queue behind it). So: check the **CPU's throttling's metric** and the
  **thread's dump** first — and if the threads are `WAITING` on a socket, you've
  found it. The fix is the **reactive's APIs** (or the `boundedElastic`'s
  offload), and the **architectural's lesson** is that the Gateway's
  single's-threaded's model means **any's blocking's code in any's filter is
  a fleet's-wide's incident**. That's worth a static's analysis's rule and a
  BlockHound's integration in the test's profile — because it will happen
  again, and it will happen on a Friday. And the second's lesson: the gateway
  is a **shared's bottleneck** — every request's latency is its latency, and
  every request's deploy is its deploy. At 12's services, the question of
  whether the **mesh's sidecar** should take the cross-cutting's concerns
  (the retries, the breakers, the mTLS) off the gateway's plate becomes worth
  re-examining, because the gateway is then only doing the routing and the
  edge's policy — which is its actual's job."

---

## 14. Tricky Interview Questions

**T1. "How does the Eureka's self-preservation mode work, and when should you
disable it?"**
> The self-preservation kicks in when the server detects that the **mass's
eviction's rate** is abnormally high (it computes the expected's renewals per
minute from the registered instances and compares it to the actual's renewals;
if the actual is below 85% of the expected, it's considered abnormal). In that
state, the server **stops evicting** the expired's instances — it keeps them in
the registry. The **benefit**: a network's partition or a brief's blip doesn't
mass-evict every's instance, which would cause every's client to re-register
simultaneously and **DDoS the registry** (a classic's death's spiral). The
**cost**: dead's instances stay in the list, and the clients send the traffic to
them → the errors, until the health's check filters them out. The
**recommendation**: **keep it enabled in production** — the registry's
stability matters more than the staleness, and the health's checks (which you
must have configured) handle the dead's instances. **Disable it in the
development** — otherwise a misconfigured's health's check is masked and you
debug the wrong's thing. And the **interview's nuance**: the self-preservation
is a **symptom's masker** — if it's firing in production, that's telling you
something real is wrong (a network's partition, a GC's pause across the fleet,
a full's disk on the registry's nodes), and you should be paging on that
signal, not just letting the mode's paper over it.

**T2. "What happens to the traffic when the config server is down and a service
restarts?"**
> Depends on the `fail-fast` flag and the client's caching. With
**`fail-fast: false`** (the production's correct's setting), the client logs the
failure and **starts with the local's/defaults** — the service comes up, but
with the **default's configuration**, which may point at the wrong's
environment's endpoints (a serious's risk: the staging's service's talking to
the production's database, or a default's password). With **`fail-fast: true`**
(the development's setting), the client **throws** and the app's startup fails —
which is the safe's behaviour in the development (a misconfigured's app should
not start) but means a config's server's outage blocks every's deployment in
production. The **real's answer** for production: `fail-fast: false` **plus**
the configuration that's baked into the deployment's manifest (the environment's
variables for the critical's endpoints), so the fallback is a **correct**
configuration and not the default's one. And the **monitoring**: a
config's fetch's failure must be **alerted** — a service running on the
fallback's configuration is a degraded's state that someone needs to know
about. The **honest's framing**: "the config's server's outage during a
restart is a *deployment's* disaster: the pods' come up with the wrong's
configuration. So the critical's settings come from the orchestrator (the
environment's variables), and the config's server's outage is a non-event for
the *existing's* pods (they've already fetched) and an *alert* for the new
ones. And `fail-fast: false` with a *correct* fallback, not the default's one."

**T3. "Why is the Spring Cloud Gateway better than writing your own routing
controller?"**
> Because the hard's parts of an edge are the **non-functional**: the
**connection's pooling** and the **back-pressure** (the reactive's streams), the
**TLS's termination** and the certificate's rotation, the **header's
manipulation** (the hop-by-hop's removal, the `X-Forwarded-*`'s handling), the
**circuit's breaker** and the **retry's** integration, the **rate's limiting**
with the distributed's bucket, the **routing's predicates** (the path, the
header, the weight's split), the **metrics** and the **tracing**'s hooks, and
the **actuator**'s introspection. Writing that by hand is weeks of work and
it's the kind of code where the bugs are in the failure's paths. The Gateway
gives it all declaratively, and it's built on WebFlux so the **non-blocking's**
model scales the connection's count far beyond the thread's model. The
**trade-offs** to name: it's **reactive**, so the **blocking's** APIs don't work
in the filters (the classic's trap), the **configuration's** YAML can become
opaque at scale (the fix: the **the DSL's API** in Java for the complex's
routes), and it's a **Spring Cloud's dependency** (the version's coupling). And
the **when's better's answer**: for a **simple's** API (a few routes, no
aggregation, no rate limiting), a **Spring MVC** controller with the
`WebClient` is lighter and easier to reason about; and for a **non-Spring**
stack, an **Envoy** or an **NGINX** does the edge's job with more performance
and less's ceremony.

**T4. "How do you decide the timeout for a gateway route?"**
> By the **budget**, derived from the end-to-end's SLO, not chosen. Take the
user-facing's SLO (say, the API's 99th percentile must be under 500ms), subtract
the edge's own's processing (the TLS, the filters, the serialization — measure
it, it's usually 1-5ms), and the remaining's budget belongs to the downstream.
From that, subtract the **downstream's p99** (measured) to get the **headroom**,
and set the timeout's ceiling a bit above the downstream's p99 (so a normal's
slow's request isn't failed) but well below the budget's total (so a hung's
downstream doesn't blow the user's SLO). The **rules**: the timeout's **floor**
must exceed the time the downstream needs to do the useful's work (otherwise
you systematically fail the slow-but-successful's requests — that's the timeout's
rate's metric to watch); the timeout's **ceiling** must bound the blast's
radius (a 30s's timeout with a small's pool's caps the throughput); and the
timeout's error must be a **`504`**, not a `500` — the client must be able to
tell "the upstream's slow" from "the upstream's broken", and only one of them
is worth a retry. And the **nested's rule**: if the gateway's route's timeout is
10s but the downstream's own's service's timeout to *its* downstream is 5s, the
outer's timeout is nearly meaningless — the timeouts must be **derived down the
chain** (each hop's timeout must be less than its caller's remaining's budget),
which is the **deadline's propagation** discipline.

**T5. "What's the difference between a Service's ClusterIP and a headless
Service, and when do you use each?"**
> The **ClusterIP** gives the Service a **virtual's IP** (a stable's address
that isn't any pod's IP), and `kube-proxy` programs `iptables`/`IPVS` rules on
every node to load's balance the traffic to the pods' real's IPs behind it. The
client connects to the ClusterIP and the **node's** proxy's picks a pod — so the
balancing is **server-side** (the node's proxy), and the client has no idea how
many pods there are. The **headless's Service** (`clusterIP: None`) has **no
virtual's IP** — the DNS returns **all the pod's addresses** (the A records),
and the client picks one — so the balancing is **client-side**. The **uses**:
the ClusterIP for the general's case; the headless's Service when you need
**the client's own's balancing** (the gRPC's clients, which can rebalance
their existing's HTTP/2's connections — a real's advantage over the
ClusterIP's stickiness), when you need **stable's per-pod's DNS's names**
(the StatefulSet's identity, the Kafka's broker's discovery, the Postgres's
primary's election), and when you need the **client's** to do the
**zone's awareness** or the **outlier's detection**. The **caveat** with the
headless's Service: the DNS's TTL governs the cache's freshness (5s by default
in CoreDNS), and the HTTP's keep-alive makes the load's balancing **sticky** —
so for the plain's HTTP, the headless's Service can be *worse* than the
ClusterIP, because the client may keep using one pod's connection forever. The
rule: **the headless's Service when you control the client and it can balance
(the gRPC's, or a client with a connection's pool's rotation); the ClusterIP
otherwise.**

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the discovery) → *"What happens if the registry is slow (not down)?"*
→ A **slow** registry is arguably worse than a **down** one, because the clients
that **block** on the fetch will have their request's latency inflated by the
registry's latency — and if the fetch is on the request's path (i.e. the client
fetches *per request* rather than *per TTL*), the registry's p99 becomes the
client's p99. The design's rule: **the fetch must be on a background's
schedule, never on the request's path.** The Spring Cloud's `CachingServiceInstanceListSupplier`
does this — it refreshes on a schedule and the request's path reads the cache.
The **failure's mode** of a slow's registry: the fetch's thread is blocked (the
Eureka's client's fetch uses the `EurekaHttpClient` with a configurable
timeout), and if the refresh's threads are exhausted, the cache's entries
**expire** (the TTL's semantics — some implementations return an empty list
rather than a stale one, which is the dangerous's case: a **stale's** list is
degraded, an **empty's** list is an outage). The **mitigations**: the fetch's
timeout must be short (a few's seconds); the client must **keep serving the
stale's list** on a fetch's failure; the **circuit's breaker** around the
registry's calls; and the **metrics** on the fetch's latency and the cache's
hit's rate (a falling hit's rate = the registry's trouble). And the
**observation**: a falling cache's hit's rate is one of the earliest's signals
of a registry's trouble — worth a dashboard and an alert.

**F2** (after the config's server) → *"How do you handle a config change that
requires a restart?"*
→ The refresh's mechanism can only update the **properties** — it can't change
the **structurally's** configuration (the bean graph, the connection's pools, the
thread's pools, the port's binding). So some changes genuinely need a restart,
and the honest's answer is to **classify** the changes: (1) **The refreshable's**
(the feature's flags, the endpoints, the timeouts' values, the rate's limits) —
these use the `@RefreshScope` and the `RefreshScope` works. (2) **The
rebuildable's** (the thread's pool's sizes, the connection's pool's sizing) —
these can be made refreshable by putting them behind a `RefreshScope` or a
`@Bean` with a `@ConfigurationProperties` that the code re-reads (but the
actual's pool's sizing needs a re-creation, so it's usually a restart). (3) **The
restart's required's** (the port, the server's identity, the security's
configuration) — these need a deploy. And the **operational's discipline**:
whatever's classification you choose, **document it** — a table of "which's
config's changes are safe to refresh" next to the config's repo. And the
**safer's alternative to a runtime's refresh** for the structural's changes:
the **restart is the deploy** — a rolling's restart is cheap and safe, and
you're not introducing the "the running's config's differs from the deployed's
config" ambiguity that a refresh creates. The **honest's framing**: "the refresh
is for the *values*; the *structure* is the deploy's job. And I'd deliberately
keep the refreshable's set *small* — every refreshable's bean is a bean whose
state is lost on a refresh, so the more you make refreshable, the more
surprising's state resets you're creating."

**F3** (after the gateway) → *"Should the gateway be in the same cluster as the
services?"*
→ Usually yes, for the **east-west's** reasons (the low's latency, the
network's policy's integration), with the **API's gateway** (the north-south's
ingress) possibly being a **global's** edge (the cloud's LB, the CDN's, the
WAF's). The **considerations** if they're in the same cluster: the **blast's
radius** (a cluster's failure takes the gateway and the services — but that's
already a shared's fate), the **scaling's independence** (the gateway's
scaling's profile is different from the services'), and the **network's
policy** (the gateway's Service's type — `LoadBalancer` for the external's
access). The **considerations** if they're separate: the **latency's** cost
(a cross-cluster's hop — sometimes 5-20ms, which matters at the p99), the
**failover's** complexity, and the **connectivity's** requirements (the mTLS's
trust, the network's policies). The **modern's trend** is to move the gateway's
data plane **out of the JVM entirely** — an **Envoy** or a **Kong** (or the
**mesh's ingress's gateway**) in front of the Spring services, because the
gateway's a **latency's sensitive, high-throughput's** component and the JVM's
warm-up, the GC's pauses, and the Netty's tuning are real's constraints. The
**JVM's gateway** is fine for the moderate's scale; the **Envoy's** is better
for the large's. And the **hybrid's** answer that's very common: the **cloud's
LB** or the **CDN's** for the static's and the coarse's edge, the **Envoy's**
or the **Gateway's** for the routing's and the policy's, and the **Spring's
Cloud's Gateway** only where the **aggregation's logic** (the BFF) is needed.

**F4** (after the mesh) → *"What does the mesh NOT give you?"*
→ Four things that are commonly and wrongly attributed to the mesh. (1) **The
business's logic** — the mesh handles the transport's concerns; the
authorisation's rules, the validation, and the domain's behaviour stay in the
application. (2) **The data's consistency** — the mesh's retries and the
timeouts don't make your cross-service's transaction any safer; you still need
the saga and the idempotency. (3) **The application's observability's
semantics** — the mesh gives you the *transport's* metrics and the spans, but
the *domain's* metrics (the orders' placed's count, the checkout's
conversion's rate) still have to be **emitted by the application**; a mesh's
dashboard cannot tell you the business's health. (4) **The team's
understanding** — the mesh's YAML is another's language to learn, and the
**failure's modes are harder to reason about** (the sidecar's logs, the
control's plane's state, the config's dump). And the most's important's one:
**the mesh's automation's traps** — a misconfigured's VirtualService or
DestinationRule can break the traffic in ways that look like an application's
bug, and the debugging's path is longer. So: the mesh is a **transport's**
layer, and the application's layers are still your's job.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We inherited a Spring Cloud app with Eureka, the Config Server, Zuul
gateway, and Ribbon. We're migrating to Kubernetes. What's the plan, and what
will bite us?"**
>
> **Ideal answer:** (1) **The framing:** this is a **four-component's
migration**, and the good's news is that they're **decoupled** — you can migrate
them independently, in the order of the **risk's**, and you should **not** do
them all at once. The plan is a sequence, and the order matters. (2) **The
migration's order (the risk's ascending):**
**Step 1 — The Zuul's → the Spring Cloud Gateway's (the smallest's change).**
Both are Spring's edge's gateways; the predicates and the filters translate
almost one-to-one (the `Zuul's` `pre`/`post` filters → the Gateway's filters;
the `Zuul's` `routes` → the Gateway's `routes`). The risk is the **filter's
semantics** — the Zuul's filters are pre/post and mutate the request/response
in place; the Gateway's filters are reactive and ordered. The **gotcha**: the
Zuul's filters that call **blocking** services (the `aggregator`'s service) will
block the Gateway's event's loop. **Migration's action**: move them to the
Gateway's, convert the blocking's calls to the `WebClient`, and test the
routing's parity (the same's URLs map to the same's backends).
**Step 2 — The Config Server's → the orchestrator's (the simplest's).** The
config's server's job is the config's delivery; on K8s, the natural's
replacement is the **ConfigMap** + the **Secret** (or the **External
Secrets' Operator** for the Vault's integration) + the **Spring Cloud
Kubernetes's Config** (`spring-cloud-starter-bootstrap` reads the ConfigMaps
and Secrets). The **advantages**: no runtime's dependency, the config's version
is the manifest's version (the git's diff), and the **rotation's** is
instant (the pod's restart). The **losses**: the **runtime's refresh** (you need
a pod's restart to change a ConfigMap, unless you use a **Reloader's** controller
that triggers a restart on the change — which is the standard's approach) and
the **profile's abstraction**. The **migration's action**: map each
`{application}-{profile}.yml` to a ConfigMap per's environment, move the
secrets to the Secret's store (or the Vault's), and add the **Reloader's**
annotation so a ConfigMap's change restarts the affected's pods. The
**migration's rule**: do this **before** the discovery's migration, because
the config's server's change is the most's disruptive to the developer's
workflow (the local's profile's behaviour changes).
**Step 3 — The Ribbon's + Eureka's → the K8s's Service's DNS (the core's
change).** The Spring Cloud LoadBalancer already abstracts the algorithm, and
the discovery's source is a `ServiceInstanceListSupplier` — so the
migration is a **configuration's change**: `spring.cloud.discovery.enabled=false`
and `spring.cloud.kubernetes.discovery.enabled=true`, plus the
`@LoadBalanced` RestClient. The **gotchas**, and these are the ones that
bites: (a) The **K8s's Service's DNS** resolves to the ClusterIP — a **stable
virtual's IP** — and the kube-proxy's balances. The old's Eureka's model gave
the client the **pod's IPs** and the client picked. The behavioural's difference:
the connection's stickiness (the HTTP's keep-alive means one's pod may serve a
disproportionate's share — the **headless's Service** restores the
client-side's balancing); the **zone's awareness** (the Eureka's model could
prefer the local's zone; the ClusterIP's iptables rules are
**random per-connection**, not zone's aware — the fix is the
**topology-aware's hints** or the **headless's Service** with the client's own's
balancing); and the **K8s's readiness's probe** becoming the health's
definition (the Eureka's health's check and the K8s's readiness's probe must
check the same's things, or the traffic's shift's semantics change). (b) The
**registration's removal** — with the Eureka's client disabled, the app stops
registering; you can run both for a while. (c) The **`spring.cloud.loadbalancer`**
configuration is **per-client** — some of the clients may still use the old's
patterns (the `@LoadBalanced` on a `RestTemplate` vs the new's `RestClient`).
**Step 4 — The Eureka's decommission (last).** Only after every's consumer has
migrated and a full's cycle has passed; keep the Eureka's cluster's runnable
until then, for the rollback's path. (3) **What will bite us (the honest's
list):** (a) The **zone's awareness's regression** — the users' p99 gets worse
if the traffic starts crossing the zones. (b) The **connection's stickiness** —
the load's balancing's fairness's regression, visible in the per-pod's CPU
metrics. (c) The **headless's Service's DNS's TTL** (5s) vs the Eureka's (35s)
— the scale-up's time-to-serve changes, and the scale-DOWN's traffic's
diversion is faster (good) but the scale-UP's is also faster (the pod might
receive the traffic before it's fully warm — the **readiness's probe's** job).
(d) The **config's server's removal** — the developer's local's setup changes
(the `native's profile's search-locations`). (e) The **old's Zuul's filters**
that nobody remembers (the custom's header's injection, the legacy's auth's
header) — the **inventory's step**: grep the Zuul's filters before you delete
them, and write the tests for the Gateway's replacements. (f) The
**dependencies' versions** — the Spring Cloud's version's matrix; the
upgrade's ordering (the Boot's version first, then the Cloud's).
(4) **The plan's structure (the sequence's summary):** the inventory's step
(every's route, every's filter, every's config's file, every's Eureka's usage)
→ the Gateway's migration (the smallest's blast's radius) → the config's
migration (the developer's workflow's change, with the team's communication) →
the discovery's migration (the core's one, with the zone's awareness's and the
stickiness's checks) → the Eureka's decommission. And the **one's rule**: one
migration's step at a time, each one with a **canary's rollout** and a
**rollback's plan** — never a big's bang across four's components.
(5) The senior's framing: "the migration's order is by the **blast's
radius**, and it's the opposite of what people assume: the **config** first
(dev's workflow, easily's rolled back), then the **gateway** (self-contained,
the filter's parity is testable), then the **discovery** (the core's risk, and
the one with the **behavioural's surprises** — the zone's awareness's
regression, the connection's stickiness's regression, and the health's
semantics's mismatch between the Eureka's check and the K8s's readiness's
probe). And the **inventory's step** is the one people skip and shouldn't: the
**Zuul's filters** are **undocumented's behaviour** that the business depends
on, and losing one is a production's incident with no's code's diff to point
at. I'd spend a day on that inventory before touching a config's file. And the
**two's things** I'd add that nobody expects: the **K8s's readiness's probe
must become the health's definition**, and if it doesn't check the same's things
the Eureka's health's check did, the traffic's semantics change silently
(the service's up but not serving → the 503s); and the **zone's awareness's
regression** is a real's p99's regression that looks like a "the K8s's network
is slower" mystery. The **headless's Service** restores the client-side's
balancing, and the **topology-aware's hints** restore the zone's preference —
both are the 'gotchas' I'd put in the runbook."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The 503s during the deployments | The deregistration's propagation's lag | The `preStop`'s delay, the graceful's shutdown, the retry |
| The intermittent's 503s with no's deploys | The stale's instance's list (a dead's pod) | The health's check, the shorter's TTL, the K8s's readiness |
| The service's can't start, "no instances available" | The Eureka's client is on but there's no Eureka | Disable the Eureka's client; check the K8s's discovery |
| The discovery's cache's hit's rate is falling | The registry's slow or down | The registry's HA, the fetch's timeout, the metrics |
| The gateway's p99 spikes, the CPU is low | A blocking's call in a filter | The reactive's APIs, the `boundedElastic`'s offload |
| The gateway's p99 spikes, the CPU is high | The gateway is saturated | Scale out, raise the CPU's limit, the routes' audit |
| The gateway's 429s are too aggressive | The key's resolver is per-IP (the NAT's users) | The per-tenant's key |
| The config's change doesn't take effect | The bean isn't `@RefreshScope` | The `@RefreshScope`; watch the state's loss |
| The config's change breaks the service | The refresh lost the bean's state | The idempotent's bean's construction, the `REQUIRES_NEW` |
| The rate's limiter's Redis's down → the API's down | The fail's closed's default | The fail's open (the reads) + the local's bucket + the alert |
| The instances' load's uneven | The connection's stickiness (the ClusterIP) | The headless's Service, the client's balancing, the StickySession |
| The new's pod's gets the traffic too early | The readiness's probe's too shallow | The readiness's check's the real's dependencies |
| The traffic's crosses the zones | The zone's awareness's lost | The topology-aware's hints, the headless's Service |
| The service's name's resolution's flaky | The DNS's TTL vs the cache's TTL | The K8s's discovery's client, the headless's Service |
| The 500s after a config's refresh | A `@RefreshScope` bean's state was lost | The idempotent's construction, the audit the refreshable's beans |

---

## 18. Debugging Scenarios

**D1. "The service's not being discovered — check the registry's view:"**
```bash
# the EUREKA's API — the instance's list
curl -s http://eureka-1:8761/eureka/apps/CATALOGUE | jq '.application.instance[] | {id, status, port}'
# the K8s's Service's endpoints
kubectl get endpoints catalogue -n default -o yaml
kubectl get pods -l app=catalogue -o wide
```

**D2. "The Gateway's routes and their health:"**
```bash
# the Gateway's actuator — the routes' dump (⚠ protect it in production)
curl -s http://gateway:8080/actuator/gateway/routes | jq '.[].routeId'
# the K8s's Service's endpoints — is the Gateway's receiving the traffic?
kubectl get endpoints gateway -n default
```

**D3. "Is the Gateway's blocked on something? (the thread's dump):"**
```bash
# the thread's dump — the Netty's threads
kubectl exec -it gateway-pod -- jcmd 1 Thread.print | grep -A 20 "reactor-http-nio"
# ⚠ look for: "the Netty's threads" in WAITING/TIMED_WAITING on a socket's read
#   = a blocking's call on the event's loop
```

**D4. "The config's server's state:"**
```bash
# the config's server's own's actuator — the environment's properties
curl -s http://config:8888/actuator/env/orders.maxItemsPerOrder | jq
# the client's view — which config's server did it use?
curl -s http://orders:8080/actuator/env | jq '.propertySources[] | {name, properties}'
```

**D5. "The rate's limiter's state (the Redis's):"**
```bash
redis-cli KEYS "request_rate_limiter*" 
redis-cli GET "request_rate_limiter.acme.requests_per_second.0"
# ⚠ the key's format: {key}.{policy}.{rate_per_second}.{burst_capacity}.{epoch}
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the discovery and config's layer for 25 microservices on
Kubernetes.**
> (1) The **K8s's native's answer** (the Service's DNS, the headless's for the
gRPC's, the Readiness's probe as the health's definition); (2) The **ConfigMap's
and Secret's** (the GitOps's flow, the Reloader's controller, the
External Secrets' Operator for the Vault's); (3) The **K8s's API** as the
registry's (the `ServiceInstanceListSupplier` over it); (4) The **namespace's
and the label's** conventions (the discovery's scope, the environment's
separation); (5) The **multi-cluster's** story (the global's LB, the
federation's DNS, the cell's architecture); (6) The **health's and the
readiness's** definition's consistency; (7) The **observability's** (the
discovery's latency's metric, the cache's hit's rate, the registration's
failures).

**Q2. 🏗️ Design the API's gateway for a public's API with 3 client types
(browser, mobile, partner's API).**
> (1) The **edge's layers** (the CDN/WAF → the LB → the gateway → the
services); (2) The **routing's by the client type** (the version's header, the
client's identification, the **canary's** weight's split); (3) The **per-client's
policies** (the rate's limits, the payload's limits, the CORS); (4) The **BFF's
aggregation** (the mobile's screen's data in one call, to save the round's
trips); (5) The **auth's model** (the JWT's validation at the edge, the
API's key's for the partners, the coarse's scopes); (6) The **version's
strategy** (the URI's versioning, the deprecation's headers); (7) The
**availability's** (the multi-AZ, the health's checks, the failover's
DNS); (8) The **observability's** (the per-route's RED's metrics, the
request's ID, the WAF's logs).

**Q3. 🏗️ Design the service's mesh's adoption for 30 services.**
> (1) The **pain's inventory** (what's actually's broken — the retries'
inconsistency, the mTLS's gap, the telemetry's gaps); (2) The **alternatives's
comparison** (the shared's library, the platform's starter, the mesh); (3) The
**decision's criteria** (the language's diversity, the policy's complexity, the
team's operational's maturity, the cost); (4) The **pilot's scope** (2-3's
services, the non-critical's path, the A/B's comparison of the latency's
overhead); (5) The **migration's plan** (the sidecar's injection, the
config's translation, the fallback's plan); (6) The **observability's**
(the mesh's metrics vs the app's, the double's counting's problem, the
tracing's continuity across the sidecar); (7) The **exit's criteria** (the
latency's overhead, the operational's burden, the coverage's rate).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the DISCOVERY: "the logical's name → the current's healthy's instances"     │
│  the CLIENT's: the caller knows and picks (few's hops, more's complexity)    │
│  the SERVER's: a proxy picks (simpler's clients, a SPOF, a extra's hop)      │
│  the K8s's: the Service's DNS (ClusterIP = server-side, headless = client)   │
│  ⚠ the registry's is OUT of the hot path — the clients' CACHE the list        │
├──────────────────────────────────────────────────────────────────────────────┤
│  the EUREKA's: the heartbeat 30s · the lease's expiry 90s · the threshold    │
│  0.85 (renew's early) · the client's fetch 30s · the cache's resync 35s      │
│  the self-preservation's ON in the production (prevents the eviction's storm) │
├──────────────────────────────────────────────────────────────────────────────┤
│  the CONFIG's: the Git's repo (the audit) + the Vault's (the secrets)        │
│  the profile's: default · dev · test · prod                                 │
│  the REFRESH's: NEVER · ON_PROFILES · ON_REFRESH · PERIODIC                 │
│  ⚠ @RefreshScope = the bean's state is LOST on a refresh                     │
│  ⚠ fail-fast: false in the production (the server's outage ≠ the deploy's)  │
├──────────────────────────────────────────────────────────────────────────────┤
│  the GATEWAY's: the routing · the TLS · the rate's limit · the coarse's auth │
│  the request's ID · the CORS · the BFF's aggregation — NOT the business      │
│  ⚠ the Gateway is REACTIVE (WebFlux) — a blocking's call stalls the loop     │
│  the rate's limiting: the Redis's token's bucket, the key's = the tenant      │
│  ⚠ the JWT's validation at the edge ≠ the resource's authorization's check   │
├──────────────────────────────────────────────────────────────────────────────┤
│  the MESH's: the mTLS, the retries, the breakers, the telemetry — the        │
│  language-agnostic's uniformity. The cost: the latency, the resources,        │
│  the two's config's systems, the control's plane's availability              │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning
- → [04 · Circuit Breaker, Retries & Resilience](./04-Circuit-Breaker-and-Resilience.md)
- → [05 · Distributed Tracing & Observability](./05-Distributed-Tracing-and-Observability.md)
- → [02 · Service Communication & Contracts](./02-Service-Communication-and-API-Contracts.md)
- → [14 · Deployment & DevOps](../14-Deployment-DevOps/README.md)

---
**Next Chapter:** → [04 · Circuit Breaker, Retries & Resilience](./04-Circuit-Breaker-and-Resilience.md)
**Previous Chapter:** ← [02 · Service Communication & Contracts](./02-Service-Communication-and-API-Contracts.md)
