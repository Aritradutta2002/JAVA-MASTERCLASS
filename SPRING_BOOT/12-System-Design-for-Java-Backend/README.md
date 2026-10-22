# 12 — System Design for Java Backend

> **Level:** Advanced → Expert
> **Interview Frequency:** ★★★★★ (the round that decides senior vs staff)
> **Prerequisite:** [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md)
> **Last Updated:** 2026

---

## 🎯 Why This Topic Is Separate

Everything before this topic teaches you *components*. This topic teaches you how to **combine
them under constraint, out loud, in 45 minutes** — which is a different skill from knowing what
Kafka does.

The interview is not a knowledge quiz. It scores:

- **Structure** — do you follow a visible process instead of jumping to boxes?
- **Requirements** — do you narrow an ambiguous prompt to something buildable?
- **Arithmetic** — do your numbers justify your design, or are you asserting?
- **Trade-offs** — can you state what you gave up, and why?
- **Failure** — do you know what breaks, and what the system does then?
- **Communication** — would they want you in a design review?

The failure mode is **silence**. An unstated correct design scores worse than a stated design
with a flaw you can discuss.

---

## 📚 Chapters

| # | Chapter | Core Idea | Difficulty |
|---|---------|-----------|------------|
| 01 | [The System Design Framework](./01-System-Design-Framework.md) | The 8-phase clock, requirements, the out-of-scope list, communication habits | ⭐⭐⭐ |
| 02 | [Scalability, Availability & Reliability](./02-Scalability-Availability-and-Reliability.md) | Serial availability, the reliability stack, backpressure, multi-region | ⭐⭐⭐⭐ |
| 03 | [Classic Design Problems](./03-Classic-Design-Problems.md) | URL shortener, rate limiter, news feed, chat — and the one defining decision in each | ⭐⭐⭐⭐ |
| 04 | [Data-Intensive Designs](./04-Data-Intensive-Designs.md) | Search, analytics, leaderboards, geo, notifications — derived stores done right | ⭐⭐⭐⭐⭐ |
| 05 | [Capacity Estimation](./05-Capacity-Estimation-and-Back-of-Envelope.md) | The numbers to memorise, the six estimations, three worked examples | ⭐⭐⭐ |
| 06 | [End-to-End Case Studies](./06-End-to-End-Case-Studies.md) | Checkout, payments, ticket booking, metrics — each with a dominant difficulty | ⭐⭐⭐⭐⭐ |

---

## 🧠 The One Mental Model That Covers Everything

```text
        EVERY SYSTEM IS THE SAME SKELETON. ONLY THE BOTTLENECK DIFFERS.

   client ──► edge ──► gateway ──► service ──► cache
                                      │  │
                                      │  └──► queue ──► async workers
                                      └─────► DB (sharded)

   ┌───────────────────────────────────────────────────────────────┐
   │  The design work is answering ONE question:                   │
   │                                                               │
   │      WHICH RESOURCE SATURATES FIRST?                          │
   │                                                               │
   │   read QPS      → cache + replicas                            │
   │   write QPS     → batch, partition, shard                     │
   │   storage       → partition, cold tier                        │
   │   bandwidth     → CDN, direct-to-storage, dedupe              │
   │   connections   → gateway fleet                               │
   │   cardinality   → limits, downsampling                        │
   │   contention    → smaller critical section, admission control  │
   └───────────────────────────────────────────────────────────────┘
```

**Name the bottleneck in the first two minutes.** Everything after that is the story of how you
handled it.

---

## 📊 The Decision Matrix

| Question | Default | Alternative | Trade-off |
|---|---|---|---|
| **Sync or async?** | sync for reads | async for anything slow or unreliable | latency vs resilience |
| **Fan-out** | write | read | read speed vs write cost |
| **Consistency** | eventual for derived | strong for decisions | latency vs correctness |
| **Read scaling** | replicas + cache | — | staleness |
| **Write scaling** | vertical → partition → shard | — | permanent complexity |
| **State** | externalise (Redis/S3) | local | scalability |
| **Queue** | bounded + reject | unbounded | honesty vs OOM |
| **Retries** | bounded + jitter + budget | — | load amplification |
| **Failure** | degrade with a fallback | fail fast | UX vs complexity |
| **Derived stores** | one relational truth + rebuildable derived | — | consistency |

---

## ⚠️ The Traps

| Trap | Wrong answer | Right answer |
|---|---|---|
| "Let me start drawing" | immediately sketches boxes | **requirements first**, 5 minutes |
| "It scales" | no numbers | QPS, storage, bandwidth, then a decision |
| "Shard it" at minute 3 | premature | follow the ladder; most cases end at indexes + cache |
| "Add a bigger DB" | hardware | find the dominant resource first |
| "`SELECT ... FOR UPDATE` everywhere" | correctness | atomic conditional updates; locks only when needed |
| "Retry the payment" | on timeout | **query by idempotency key** — the charge may have succeeded |
| "Exactly-once" | "with Kafka" | at-least-once + idempotency; say the caveat |
| "Availability is 99.9% × 5" | "≈99.5%, fine" | 3.6 h/month — remove deps from the critical path |
| "Cache the stock count" | "it's fast" | the cached value lies; the authoritative check is at reserve time |
| "Unbounded labels are fine" | in a metrics design | cardinality is what kills TSDBs |
| "Percentiles average" | compute p99 from p99s | store histograms/sketches |
| "We'd just add servers" | for contention | admission control beats hardware |
| No summary at the end | runs out of time | "chose / cost / gave up / next" |

---

## 🎤 The 25 Questions to Expect

| # | Question | Where |
|---|---|---|
| 1 | Walk me through how you'd approach a design question | [Ch 01](./01-System-Design-Framework.md) |
| 2 | What non-functional requirements do you ask about? | [Ch 01](./01-System-Design-Framework.md) |
| 3 | How do you estimate QPS for 10M DAU? | [Ch 05](./05-Capacity-Estimation-and-Back-of-Envelope.md) |
| 4 | Availability vs reliability vs resilience? | [Ch 02](./02-Scalability-Availability-and-Reliability.md) |
| 5 | How do you compute availability with 5 dependencies? | [Ch 02](./02-Scalability-Availability-and-Reliability.md) |
| 6 | How do you make a service horizontally scalable? | [Ch 02](./02-Scalability-Availability-and-Reliability.md) |
| 7 | Design a URL shortener | [Ch 03](./03-Classic-Design-Problems.md) |
| 8 | How do you generate short codes without collisions? | [Ch 03](./03-Classic-Design-Problems.md) |
| 9 | Design a distributed rate limiter | [Ch 03](./03-Classic-Design-Problems.md) |
| 10 | Why not a fixed-window limiter? | [Ch 03](./03-Classic-Design-Problems.md) |
| 11 | Design a news feed | [Ch 03](./03-Classic-Design-Problems.md) |
| 12 | Fan-out on write or read? What about celebrities? | [Ch 03](./03-Classic-Design-Problems.md) |
| 13 | Design a chat system | [Ch 03](./03-Classic-Design-Problems.md) |
| 14 | How do you handle 50M WebSocket connections? | [Ch 03](./03-Classic-Design-Problems.md) |
| 15 | Design search | [Ch 04](./04-Data-Intensive-Designs.md) |
| 16 | Keep the search index in sync with the DB | [Ch 04](./04-Data-Intensive-Designs.md) |
| 17 | Design an analytics pipeline | [Ch 04](./04-Data-Intensive-Designs.md) |
| 18 | How do you compute unique users? | [Ch 04](./04-Data-Intensive-Designs.md) |
| 19 | Design a leaderboard | [Ch 04](./04-Data-Intensive-Designs.md) |
| 20 | Design "restaurants near me" | [Ch 04](./04-Data-Intensive-Designs.md) |
| 21 | Design notifications | [Ch 04](./04-Data-Intensive-Designs.md) |
| 22 | Design checkout — how do you avoid overselling? | [Ch 06](./06-End-to-End-Case-Studies.md) |
| 23 | Design a payment system | [Ch 06](./06-End-to-End-Case-Studies.md) |
| 24 | Design ticket booking for a 100k-user on-sale | [Ch 06](./06-End-to-End-Case-Studies.md) |
| 25 | Design a metrics platform | [Ch 06](./06-End-to-End-Case-Studies.md) |

---

## 🛠️ What You Actually Produce

| Artifact | Contents |
|---|---|
| Requirements block | 3–4 functional, 3–4 non-functional, explicit out-of-scope |
| Estimation block | read QPS, write QPS, storage/year, bandwidth, working set, and the implication |
| API | 4–6 endpoints, cursor pagination, idempotency keys |
| Data model | access patterns first, then tables + index per pattern + store per data type |
| High-level diagram | client → edge → services → stores + async path; every box justified |
| Deep dive | naive → why it breaks → real solution → cost → metric |
| Failure table | component dies → behaviour → mitigation |
| Summary | chose / cost / gave up / next |

---

## 📐 The Reference Skeleton

```text
   client
     │
     ▼
 ┌────────┐   ┌──────────┐   ┌──────────────────────────────────────┐
 │  CDN   │──►│ gateway  │──►│ services (stateless)                 │
 └────────┘   │ authN    │   │  ┌────────┐ ┌────────┐ ┌──────────┐  │
              │ rate lim │   │  │ core   │ │ derived│ │ async    │  │
              └──────────┘   │  └───┬────┘ └───┬────┘ └────┬─────┘  │
                             └──────┼──────────┼───────────┼────────┘
                                    │          │           │
                             ┌──────▼───┐ ┌────▼─────┐ ┌───▼─────┐
                             │ DB       │ │ cache    │ │ Kafka   │
                             │ (truth)  │ │ (hot set)│ │ (buffer)│
                             └──────────┘ └──────────┘ └───┬─────┘
                                                           │
                                            ┌──────────────▼──────────────┐
                                            │ derived stores (search,     │
                                            │ analytics, geo, leaderboard)│
                                            │ REBUILDABLE from the truth  │
                                            └─────────────────────────────┘
```

**The invariant to state every time:** *"One relational source of truth; every other store is
derived, asynchronous, and rebuildable."*

---

## ✅ The Self-Check

- [ ] I can state the 8 phases and their time budget.
- [ ] I can list the non-functional requirements to ask about, including read:write ratio.
- [ ] I can estimate QPS/storage/bandwidth in three minutes and **conclude with a decision**.
- [ ] I can compute serial and parallel availability.
- [ ] I can state the reliability stack in the right order.
- [ ] For each classic problem, I can name the defining decision.
- [ ] I can explain fan-out on write vs read and the celebrity exception.
- [ ] I can explain why a derived store must be rebuildable.
- [ ] I can name the cardinality trap and why percentiles don't average.
- [ ] For each case study, I can name the dominant difficulty.
- [ ] I can state the trade-off I rejected, every time.
- [ ] I always end with a summary the interviewer can quote.

---

## 🧪 The Practice Drill

Set a 45-minute timer and do one of these, out loud, recording yourself:

- [ ] Design Twitter for 300M DAU.
- [ ] Design a URL shortener for 100M URLs/day.
- [ ] Design a distributed rate limiter for 10k req/s.
- [ ] Design a chat system for 50M concurrent users.
- [ ] Design "restaurants near me".
- [ ] Design an analytics pipeline ingesting 500k events/s.
- [ ] Design checkout for Black Friday.
- [ ] Design a payment system with reconciliation.
- [ ] Design ticket booking for a 100k-user on-sale.
- [ ] Design a metrics platform at 1M samples/s.

**Then score yourself against the twelve cross-cutting checks in
[ch 06 §5](./06-End-to-End-Case-Studies.md#5-the-cross-cutting-checklist).** Whatever you missed
is your next revision target.

---

**Related folders:** ← [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md) · → [13 · Testing](../13-Testing/README.md)

**Standard followed:** [../STANDARD.md](../STANDARD.md)
