# 01 · Containerising Spring Boot with Docker

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("Walk me through your Dockerfile")
> **Prerequisite:** [13 · Testing](../13-Testing/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Why Containers](#1-why-containers)
- [2. Mental Model — Layers and the Build Cache](#2-mental-model--layers-and-the-build-cache)
- [3. The Dockerfile, Line by Line](#3-the-dockerfile-line-by-line)
- [4. Layered JARs & Buildpacks](#4-layered-jars--buildpacks)
- [5. JVM in a Container](#5-jvm-in-a-container)
- [6. Image Size & Security Hardening](#6-image-size--security-hardening)
- [7. Local Development with Compose](#7-local-development-with-compose)
- [8. Common Mistakes](#8-common-mistakes)
- [9. Code Examples](#9-code-examples)
- [10. Interview Questions & Model Answers](#10-interview-questions--model-answers)
- [11. Self-Check & Revision Card](#11-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> A container is a **process with an isolated view of the filesystem, network and PIDs** — not a
> VM. No hypervisor, no guest kernel; it's namespaces + cgroups on the host kernel. So it starts
> in milliseconds and the image is just a tarball of layers.
>
> **Order your Dockerfile from least- to most-frequently-changing** so the layer cache survives.
> Dependencies before application code. That single decision turns a 60-second rebuild into 5
> seconds.
>
> **Use a multi-stage build**: compile with a JDK image, run on a slim JRE image as a non-root
> user. Or skip Dockerfiles entirely with **Spring Boot buildpacks** (`./mvnw spring-boot:build-image`),
> which produce a layered, non-root, reproducible image with no maintenance.
>
> **Two JVM rules in containers:** let the JVM see the container's memory limits (it does since
> Java 10+, via `UseContainerSupport`), and set `-XX:MaxRAMPercentage` rather than a fixed `-Xmx`.
>
> **Don't run as root, don't bake secrets into layers, and pin your base image.**

---

## 1. Why Containers

### 1.1 What a container actually is

```
   VM                                   CONTAINER
   ┌───────────────┐                    ┌───────────────┐
   │ app           │                    │ app           │
   ├───────────────┤                    ├───────────────┤
   │ guest OS      │  ← full kernel     │ libs (image)  │
   │ + libs        │    per VM          └───────┬───────┘
   ├───────────────┤                            │ namespaces + cgroups
   │ hypervisor    │                    ┌───────▼───────┐
   ├───────────────┤                    │ HOST KERNEL   │
   │ host OS       │                    └───────────────┘
   └───────────────┘
   GBs, seconds to start                MBs, milliseconds to start
```

A container image is:
- **layers** — each Dockerfile instruction adds one, cached and content-addressed
- **metadata** — entrypoint, env, user, ports
- **NOT** a running process

A container is one **process tree** with its own filesystem view (`namespace`) and resource limits
(`cgroups`), sharing the host kernel. **This is why the base image's kernel version doesn't matter
and the host's does.**

### 1.2 Why it matters for a Java service

| Without containers | With containers |
|---|---|
| "works on my machine" JDK version | the JDK is in the image |
| config drift between environments | the same artifact everywhere |
| manual deploy scripts | the image is the deployable unit |
| hard to run 5 services locally | docker compose up |
| slow, risky rollback | run the previous image tag |

**The real benefit isn't packaging — it's that the artifact is immutable and identical across
environments.** That's what makes rollback and reproducibility possible
([ch 03](./03-CICD-and-Release-Strategies.md)).

---

## 2. Mental Model — Layers and the Build Cache

```
┌──────────────────────────────────────┐
│ COPY app.jar            ← changes every build      ← rebuilt
├──────────────────────────────────────┤
│ COPY dependencies/      ← changes weekly            ← usually cached
├──────────────────────────────────────┤
│ RUN addgroup/adduser    ← rarely changes            ← cached
├──────────────────────────────────────┤
│ FROM eclipse-temurin:21-jre ← monthly               ← cached
└──────────────────────────────────────┘
```

**The rule:** instructions are cached until the first one whose inputs changed; everything after
that is rebuilt. So **put the volatile things last**.

```dockerfile
# ❌ every code change reinstalls dependencies
COPY . .
RUN ./mvnw dependency:go-offline
RUN ./mvnw package

# ✅ dependencies layer survives code changes
COPY pom.xml .
RUN ./mvnw dependency:go-offline
COPY src ./src
RUN ./mvnw package -DskipTests
```

**`.dockerignore` matters as much as `.gitignore`:**

```
target/
.git/
.idea/
*.iml
node_modules/
.env
```

Without it, `COPY . .` invalidates the cache on every `target/` change and ships your `.git`
directory (and possibly `.env`) in the image.

---

## 3. The Dockerfile, Line by Line

### 3.1 The production multi-stage Dockerfile

```dockerfile
# ---------- stage 1: build ----------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# 1. only the POM first, so the dependency layer caches
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN ./mvnw -B -q dependency:go-offline

# 2. only now the source, which changes most often
COPY src src
RUN ./mvnw -B -q package -DskipTests

# ---------- stage 2: runtime ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# 3. non-root user — the single most important security line
RUN addgroup -S app && adduser -S app -G app
USER app

# 4. the artifact, owned by the runtime user
COPY --from=build --chown=app:app /workspace/target/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

**Why each choice:**

| Line | Reason |
|---|---|
| `-jdk-alpine` in build | small, but has the full JDK for compilation |
| `-jre-alpine` at runtime | no compiler, no `javac`, half the size |
| `COPY pom.xml` first | dependency cache survives code changes |
| `dependency:go-offline` | resolves everything once |
| `-DskipTests` | tests run in CI, not in the image build |
| `adduser` + `USER app` | a compromised app isn't root |
| `--chown` | no `chown -R` layer, correct ownership immediately |
| `MaxRAMPercentage` | respects the container limit |
| `ENTRYPOINT` exec form | the JVM is PID 1 and receives signals |

**Why not PID 1 as the JVM's problem:** if the JVM is PID 1 it receives `SIGTERM`, which Spring
Boot handles with a graceful shutdown. Using the shell form (`ENTRYPOINT java -jar ...`) makes a
shell PID 1, which **doesn't forward signals** — the container gets `SIGKILL` after the grace
period and in-flight requests die. Always use the JSON exec form.

### 3.2 Layer caching with buildkit

```dockerfile
# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests
```

`--mount=type=cache` keeps the Maven repository **between builds** without baking it into a layer.
This is the difference between a 90-second and a 20-second rebuild on a fresh runner.

---

## 4. Layered JARs & Buildpacks

### 4.1 Why layered JARs

A Spring Boot fat JAR is one file: any code change invalidates the whole layer. Splitting it into
layers means dependencies, Spring Boot loader, and application classes are separate:

```xml
<plugin>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-maven-plugin</artifactId>
  <configuration>
    <layers>
      <enabled>true</enabled>
    </layers>
  </configuration>
</plugin>
```

```bash
java -Djarmode=tools -jar app.jar extract --layers --destination extracted
```

```
extracted/
  dependencies/         ← rarely changes, ~60 MB
  spring-boot-loader/   ← almost never changes
  snapshot-dependencies/
  application/          ← changes every build, ~2 MB
```

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

**Result:** a code-only change rebuilds ~2 MB instead of ~60 MB, so pushing to a registry takes
seconds instead of a minute. That directly shortens deploy time.

### 4.2 Buildpacks — no Dockerfile at all

```bash
./mvnw spring-boot:build-image \
  -Dspring-boot.build-image.imageName=acme/orders:1.2.3
```

Or with the Gradle task `bootBuildImage`. Paketo buildpacks produce:
- a **layered** OCI image (dependencies/spring-boot-loader/application) automatically
- a **non-root** user out of the box
- **JVM memory** defaults based on the container
- a **reproducible** build with a BOM-controlled base image
- **no Dockerfile to maintain**

| | Dockerfile | Buildpacks |
|---|---|---|
| Control | total | constrained |
| Maintenance | yours | the platform's |
| CVE patching | rebuild with a new `FROM` | rebase onto a new stack |
| Build speed | depends on your caching | good, cached |
| Non-root by default | no | **yes** |
| Learning curve | low | low, if you trust it |

**The interview answer:** "For a standard Spring Boot service I'd use buildpacks — non-root,
layered and memory-aware by default, with nothing to maintain. A custom Dockerfile when I need
something buildpacks don't do: an agent, a native binary, a specific base image, or extra OS
packages."

### 4.3 Jib

```bash
./mvnw compile com.google.cloud.tools:jib-maven-plugin:build \
  -Dimage=acme/orders:1.2.3
```

Jib builds the image **without Docker at all** (directly from Maven), and does layer splitting
reproducibly. Good for CI environments where DinD is awkward.

---

## 5. JVM in a Container

### 5.1 Memory

**Before Java 10**, the JVM read the *host's* memory and set `-Xmx` as a quarter of it, ignoring the
container limit — a container with 512 MB and a 6 GB heap, then the OOM killer. Since Java 10
(`UseContainerSupport`, default), the JVM reads cgroup limits correctly.

```dockerfile
# ✅ percentage of the container limit
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]

# ❌ hard-coded, ignores limits, breaks when you resize the container
ENTRYPOINT ["java", "-Xmx512m", "-jar", "app.jar"]
```

**Why 75%?** The rest is for metaspace, code cache, thread stacks, direct/native buffers, and
JIT. 75–80% is the usual band. Going to 95% produces native OOM kills that look like random
container restarts.

### 5.2 CPU

```yaml
resources:
  requests: { cpu: "500m", memory: "1Gi" }
  limits:   { cpu: "2",    memory: "1Gi" }
```

The JVM sizes its thread pools (`ForkJoinPool.commonPool`, GC threads) from the **CPU limit**. A
low CPU limit therefore silently reduces parallelism. On older JVMs also set:

```dockerfile
-XX:ActiveProcessorCount=2
```

### 5.3 The other flags that matter

```dockerfile
ENTRYPOINT ["java",
  "-XX:MaxRAMPercentage=75",
  "-XX:+UseG1GC",
  "-XX:+ExitOnOutOfMemoryError",          # fail fast instead of a zombie JVM
  "-XX:+HeapDumpOnOutOfMemoryError",
  "-XX:HeapDumpPath=/tmp",
  "-Djava.security.egd=file:/dev/./urandom",
  "-jar", "/app/app.jar"]
```

| Flag | Why |
|---|---|
| `ExitOnOutOfMemoryError` | a JVM after OOM is unreliable; let the orchestrator restart it |
| `HeapDumpOnOutOfMemoryError` | the dump is the only way to diagnose it later |
| `UseG1GC` | the sensible default for services; ZGC for very large heaps |
| `java.security.egd` | avoid `/dev/random` blocking on entropy (older containers) |
| `-XX:+UseContainerSupport` | default in modern JVMs; worth knowing it exists |

### 5.4 Graceful shutdown

```yaml
server:
  shutdown: graceful
spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s
```

```
SIGTERM → stop accepting new requests → finish in-flight (up to 30s) → exit
```

**And the orchestrator must wait longer than the app's grace period.** In Kubernetes,
`terminationGracePeriodSeconds` must exceed `timeout-per-shutdown-phase`, and a
`preStop` sleep is often needed so the load balancer stops sending traffic before the shutdown
starts ([ch 02](./02-Kubernetes-for-Java-Developers.md)).

---

## 6. Image Size & Security Hardening

### 6.1 Size

| Base | Size | Note |
|---|---|---|
| `eclipse-temurin:21-jre` (Ubuntu) | ~280 MB | glibc, most compatible |
| `eclipse-temurin:21-jre-alpine` | ~180 MB | musl; watch native libs |
| `eclipse-temurin:21-jre-jammy` | ~250 MB | LTS base |
| `distroless/java21` | ~180 MB | **no shell**, smallest attack surface |
| `alpine` + custom JRE (`jlink`) | ~80 MB | smallest, most work |
| GraalVM native image | ~80 MB, ~50 ms startup | see ch 02 |

**Don't chase size for its own sake** — but do care about:
- **pull time** (a 300 MB image × 20 pods on a cold node is slow)
- **attack surface** (no shell, no package manager, no compiler)
- **CVE count** (fewer packages, fewer vulnerabilities to patch)

### 6.2 Hardening checklist

```dockerfile
FROM eclipse-temurin:21-jre-jammy

# 1. non-root
RUN groupadd -r app && useradd -r -g app app
USER app
WORKDIR /app

# 2. read-only filesystem friendly: write only where needed
#    (Kubernetes: securityContext.readOnlyRootFilesystem: true + emptyDir for /tmp)

# 3. no package manager, no shell (distroless) where possible
# 4. no secrets in layers — they're readable by anyone with the image
COPY --chown=app:app target/app.jar app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
```

| Risk | Mitigation |
|---|---|
| Running as root | `USER app`, `runAsNonRoot: true` |
| Secrets baked into the image | env vars at runtime / secret mounts; **never** `ENV DB_PASSWORD` |
| Mutable base image | pin a **digest**: `eclipse-temurin@sha256:...` |
| Vulnerable OS packages | minimal base, regular rebuilds, image scanning |
| Write access to the root FS | `readOnlyRootFilesystem: true` + a `/tmp` emptyDir |
| Privilege escalation | `allowPrivilegeEscalation: false`, drop all capabilities |
| Unused ports/tools | distroless |

```yaml
# Kubernetes securityContext
securityContext:
  runAsNonRoot: true
  runAsUser: 1000
  allowPrivilegeEscalation: false
  readOnlyRootFilesystem: true
  capabilities: { drop: ["ALL"] }
```

### 6.3 Scanning

```bash
trivy image --severity HIGH,CRITICAL --exit-code 1 acme/orders:1.2.3
grype acme/orders:1.2.3
```

**A scan that fails the build on HIGH is the only scan that matters.** A daily report nobody reads
changes nothing.

---

## 7. Local Development with Compose

```yaml
services:
  app:
    build: .
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: local
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/app
      SPRING_DATA_REDIS_HOST: redis
    depends_on:
      db:    { condition: service_healthy }
      redis: { condition: service_started }
    healthcheck:
      test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]
      interval: 5s
      timeout: 2s
      retries: 10

  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: app
      POSTGRES_USER: app
      POSTGRES_PASSWORD: app
    ports: ["5432:5432"]
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app"]
      interval: 3s
      retries: 10
    volumes: ["pgdata:/var/lib/postgresql/data"]

  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]

volumes:
  pgdata:
```

**`depends_on: condition: service_healthy`** is the difference between a flaky startup race and a
reliable one. Without a healthcheck it just starts the containers in order and hopes.

**Bind mounts for hot reload (dev only):**

```yaml
volumes:
  - ./target/classes:/app/classes
```

Or use a devtools-enabled image that restarts on class change. Never do this in production.

---

## 8. Common Mistakes

| Mistake | Consequence | Fix |
|---|---|---|
| `COPY . .` before dependency resolution | full rebuild on every code change | copy POM first |
| No `.dockerignore` | `.git`, `target/`, `.env` in the image; cache busted | add it |
| Running as root | container escape = host root | `USER app` |
| Shell-form `ENTRYPOINT` | no signal forwarding, hard kills | exec form JSON array |
| `-Xmx512m` hard-coded | ignores the limit, or wastes it | `MaxRAMPercentage` |
| `MaxRAMPercentage=95` | native OOM, random restarts | 75–80% |
| Latest tag base image | unreproducible builds, surprise breakage | pin a digest |
| Fat JAR single layer | 60 MB push per deploy | layered JARs / buildpacks |
| Secrets in `ENV`/`ARG` | readable from the image history | secrets at runtime |
| No healthcheck | traffic sent before the app is ready | `/actuator/health` + readiness |
| `terminationGracePeriodSeconds` = 0 | in-flight requests killed | set it above the app's grace |
| Building on every environment | dev ≠ prod artifact | build once, promote |
| Tests inside the image build | slow, and tests need infra | tests in CI, `-DskipTests` |
| No image scan | known CVEs in production | Trivy/Grype in CI |

---

## 9. Code Examples

### 9.1 The complete production Dockerfile

```dockerfile
# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/*.jar extract --layers --destination extracted

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/tmp"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
```

### 9.2 `application.yml` for containers

```yaml
server:
  port: 8080
  shutdown: graceful
  tomcat:
    threads:
      max: 50                     # don't over-thread a 2-CPU container
    accept-count: 100

spring:
  lifecycle:
    timeout-per-shutdown-phase: 30s

management:
  endpoint.health:
    probes:
      enabled: true               # /health/liveness and /health/readiness
  health:
    livenessstate.enabled: true
    readinessstate.enabled: true
  endpoints.web.exposure.include: health,info,prometheus,metrics
```

**Two different probes matter:**

```
liveness  → is the JVM alive?  (failure ⇒ restart)
readiness → can it serve traffic? (failure ⇒ remove from the LB, don't restart)
```

A DB outage should fail **readiness**, not liveness — otherwise Kubernetes restarts healthy pods
during a database incident and turns a degradation into an outage.

### 9.3 Health indicator that distinguishes the two

```java
@Component
class DbReadiness implements HealthIndicator {
    private final DataSource ds;

    @Override
    public Health health() {
        try (var c = ds.getConnection()) {
            return c.isValid(1) ? Health.up().build() : Health.down().build();
        } catch (Exception e) {
            return Health.down().withDetail("reason", e.getClass().getSimpleName()).build();
        }
    }
}
```

### 9.4 `.dockerignore`

```
target/
.git/
.gitignore
.idea/
*.iml
*.log
.env*
node_modules/
Dockerfile
docker-compose*.yml
```

### 9.5 Multi-arch build (Apple Silicon → amd64 cluster)

```bash
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  -t acme/orders:1.2.3 --push .
```

A mismatch (`arm64` image on an `amd64` cluster) gives `exec format error` — worth mentioning as a
real failure mode.

---

## 10. Interview Questions & Model Answers

**Q1. What's the difference between a container and a VM?**
> A VM runs a full guest OS with its own kernel under a hypervisor. A container is just a process
> on the host kernel with isolated namespaces and cgroup limits — so it starts in milliseconds and
> the image is megabytes. The consequence is that containers share the host kernel, so kernel
> isolation is weaker than a hypervisor, and the host's kernel version is what matters.

**Q2. Walk me through your Dockerfile and why each line is there.**
> Multi-stage: a JDK image compiles, a JRE image runs, so the runtime image has no compiler. In the
> build stage I copy the POM and warm the dependency cache **before** copying source, so a code
> change doesn't re-download dependencies. At runtime I create a non-root user, copy the layered
> JARs, and use the exec-form entrypoint so the JVM is PID 1 and receives SIGTERM for graceful
> shutdown. Memory is a percentage of the container limit, not a fixed `-Xmx`.

**Q3. Why does Docker rebuild everything when you change one line of code?**
> Because the cache is invalidated from the first changed instruction onward. If `COPY . .` comes
> before dependency resolution, any source change invalidates the dependency layer and everything
> after it. Order from stable to volatile: POM, dependencies, then source.

**Q4. How do you make a Spring Boot image smaller?**
> Multi-stage with a JRE base instead of a JDK, `alpine` or distroless, exclude test dependencies,
> and consider `jlink` to build a custom runtime with only the modules I need. GraalVM native
> images go further — tens of MB and ~50 ms startup — at the cost of build complexity and some
> reflection configuration.

**Q5. Why does `-Xmx` break in containers?**
> On old JVMs the ergonomics read host memory and ignored the cgroup limit, so a 512 MB container
> got a multi-gigabyte heap and the OOM killer restarted it. Since Java 10 `UseContainerSupport` is
> on by default, so it reads the limit — and then `MaxRAMPercentage` is better than a fixed `-Xmx`
> because it adapts when I resize the container.

**Q6. How do you handle SIGTERM so deploys don't drop requests?**
> Graceful shutdown in Spring Boot, an exec-form entrypoint so the JVM receives the signal, and a
> termination grace period longer than the shutdown timeout. In Kubernetes I also add a `preStop`
> sleep so the load balancer stops routing before shutdown begins — otherwise there's a window
> where traffic still arrives at a shutting-down pod.

**Q7. Buildpacks or a Dockerfile?**
> Buildpacks for a standard service: layered, non-root, memory-aware by default, and nothing to
> maintain. A Dockerfile when I need control buildpacks don't offer — a specific base image, an
> APM agent installed, native binaries, or extra OS packages. Either way, the important properties
> are the same: layered caching, non-root, pinned base, and no secrets in the image.

**Q8. How do you secure a container image?**
> Non-root user, read-only root filesystem with an emptyDir for temp, dropped capabilities, no
> package manager or shell where possible (distroless), no secrets in `ENV` or `ARG` because they're
> visible in the image metadata, a pinned digest for the base image, and a scanner in CI that fails
> the build on high or critical CVEs. Then rebuild regularly so base-image patches actually reach
> production.

---

## 11. Self-Check & Revision Card

- [ ] I can explain container vs VM in terms of the kernel.
- [ ] I can explain layer caching and why POM comes before source.
- [ ] I can write a multi-stage Dockerfile from memory.
- [ ] I can explain layered JARs and why they speed up deploys.
- [ ] I can compare buildpacks and Dockerfiles.
- [ ] I know the JVM container flags and why `MaxRAMPercentage` beats `-Xmx`.
- [ ] I can explain liveness vs readiness for a DB outage.
- [ ] I can list the hardening checklist.

```
┌───────────────── DOCKER FOR SPRING — 60s RECALL ────────────────────────────┐
│ Container = process + namespaces + cgroups on the HOST KERNEL (not a VM).    │
│                                                                              │
│ LAYER CACHE: stable → volatile. POM → deps → source.                         │
│   .dockerignore is mandatory (.git, target/, .env).                          │
│                                                                              │
│ MULTI-STAGE: JDK builds, JRE runs. NON-ROOT user. EXEC-form ENTRYPOINT       │
│   (shell form swallows SIGTERM ⇒ hard kills).                                │
│                                                                              │
│ LAYERED JAR / BUILDPACKS: a code change pushes ~2 MB not ~60 MB.             │
│ Buildpacks give layers + non-root + memory defaults with no Dockerfile.      │
│                                                                              │
│ JVM: MaxRAMPercentage=75 (not -Xmx), ExitOnOutOfMemoryError,                 │
│   HeapDumpOnOutOfMemoryError, graceful shutdown + terminationGracePeriod.    │
│                                                                              │
│ PROBES: liveness = restart · readiness = remove from LB.                     │
│   A DB outage must fail READINESS, never liveness.                           │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Kubernetes for Java Developers](./02-Kubernetes-for-Java-Developers.md)
