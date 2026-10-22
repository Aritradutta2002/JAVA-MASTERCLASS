# 01 · The System Design Framework

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (every senior loop has one)
> **Prerequisite:** [11 · Database Design & Optimisation](../11-Database-Design-Optimization/README.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What the Interview Is Actually Testing](#1-what-the-interview-is-actually-testing)
- [2. The 45-Minute Clock](#2-the-45-minute-clock)
- [3. Step 1 — Requirements](#3-step-1--requirements)
- [4. Step 2 — Back-of-the-Envelope](#4-step-2--back-of-the-envelope)
- [5. Step 3 — The API](#5-step-3--the-api)
- [6. Step 4 — The Data Model](#6-step-4--the-data-model)
- [7. Step 5 — High-Level Design](#7-step-5--high-level-design)
- [8. Step 6 — Deep Dive](#8-step-6--deep-dive)
- [9. Step 7 — Bottlenecks & Failure](#9-step-7--bottlenecks--failure)
- [10. Step 8 — Trade-offs & Wrap-Up](#10-step-8--trade-offs--wrap-up)
- [11. Communication Patterns That Score](#11-communication-patterns-that-score)
- [12. Anti-Patterns](#12-anti-patterns)
- [13. A Worked Skeleton](#13-a-worked-skeleton)
- [14. Self-Check & Revision Card](#14-self-check--revision-card)

---

## 0. TL;DR — The 30-Second Answer

> A system design interview is **not** a knowledge quiz. It tests whether you can take an
> ambiguous problem, **narrow it to something buildable**, make **explicit trade-offs**, and
> reason about **what breaks at scale** — out loud, collaboratively.
>
> The framework: **Requirements (5 min) → Estimate (3) → API (4) → Data model (5) →
> High-level design (10) → Deep dive (12) → Bottlenecks (4) → Wrap-up (2).**
>
> The three moves that separate strong candidates: (1) **ask before building** — functional vs
> non-functional, and what's explicitly out of scope; (2) **do the arithmetic** — QPS, storage,
> bandwidth, so the design is justified rather than asserted; (3) **name the trade-off every
> time you make a choice**, including the one you rejected.
>
> Silence is the failure mode. An unstated correct design scores worse than a stated design
> with a flaw you can discuss.

---

## 1. What the Interview Is Actually Testing

| What they're scoring | How it shows up |
|---|---|
| **Structured thinking** | you follow a visible process instead of jumping to boxes |
| **Requirement clarification** | you ask, and you write the answers down |
| **Estimation** | numbers, with units, sanity-checked |
| **Breadth** | you know caching, queues, sharding, CDN, consistency models |
| **Depth** | in one area you go two levels below the surface |
| **Trade-off reasoning** | "X because Y, at the cost of Z" |
| **Failure thinking** | what happens when a dependency dies |
| **Communication** | they'd want you in a design review |
| **Collaboration** | you take hints and incorporate them |

**What they're not scoring:** whether you match the "reference" design. There isn't one. Two
strong candidates can produce opposite designs and both pass — if the reasoning is sound.

**What fails:** silence, unexplained boxes, jumping to sharding in minute 3, refusing to
commit to a choice, or ignoring the hint the interviewer gives you (a hint is a rescue rope —
grab it).

---

## 2. The 45-Minute Clock

```
 0 ──────────────────────────────────────────────────────────────── 45
 │ reqs  │ est │ api │ data │  high-level   │  deep dive  │ fail │ wrap │
 │  5    │  3  │  4  │  5   │      10       │     12      │  4   │  2   │
 └───────┴─────┴─────┴──────┴───────────────┴─────────────┴──────┴──────┘
```

| Phase | Output | Common failure |
|---|---|---|
| **Requirements** | 3–4 functional, 3–4 non-functional, out-of-scope list | starting to draw immediately |
| **Estimate** | QPS read/write, storage/year, bandwidth | skipping it, or 20 minutes on it |
| **API** | 4–6 endpoints with shapes | designing 30 endpoints |
| **Data model** | tables/collections + keys + access patterns | forgetting the access patterns |
| **High-level** | boxes, arrows, where data lives | no data flow direction |
| **Deep dive** | 1–2 components in real detail | staying shallow everywhere |
| **Bottlenecks** | what breaks first, and the fix | claiming it scales infinitely |
| **Wrap-up** | trade-offs made, what you'd do next | running out of time silently |

**Time discipline:** announce the plan at minute 0 ("I'll spend five minutes on requirements,
then estimate, then sketch — stop me if you want to go deeper anywhere"). Interviewers will
redirect you, and that's good.

---

## 3. Step 1 — Requirements

### 3.1 Functional — the core user journeys

Ask, then write. Three to five is right; more means you're designing too much.

```
Design Twitter.
  ✅ "Post a tweet (280 chars, optional media)"
  ✅ "Follow / unfollow a user"
  ✅ "Home timeline: recent tweets from people I follow"
  ✅ "User timeline: my own tweets"
  ❌ "DMs, ads, search, trending, notifications" — explicitly defer
```

**The deferral is itself a scored move.** "I'll exclude DMs and search; if we have time I'll
note how search would attach" shows scope control.

### 3.2 Non-functional — the constraints that shape the design

| Dimension | Question to ask | Why it matters |
|---|---|---|
| **Scale** | DAU, reads/writes per user per day | decides caching, sharding |
| **Latency** | p99 target (100 ms? 1 s?) | decides sync vs async |
| **Availability** | 99.9% vs 99.99% | decides replication, multi-region |
| **Consistency** | is a stale read acceptable? for how long? | decides the whole data strategy |
| **Durability** | can we lose a write? | decides sync replication / outbox |
| **Read:write ratio** | 100:1 or 1:50? | decides replica vs shard |
| **Retention** | how long is data kept? | decides cold storage, partitioning |
| **Geography** | one region or global? | decides CDN, multi-region writes |

**The one to always ask:** read:write ratio. It's the single biggest determinant of the
architecture, and asking it signals you know that.

**The one candidates forget:** consistency. "Can a user tolerate seeing a follower count that's
5 seconds stale?" — the answer shapes everything, and asking it is a strong signal.

### 3.3 The out-of-scope list

Write it down explicitly:

```
OUT OF SCOPE: authentication details, payments, admin tooling, analytics pipeline,
              multi-language, spam detection, media transcoding.
```

This buys you time and shows judgement. It also protects you from the interviewer saying "what
about X?" — you can say "noted, deprioritised for time."

---

## 4. Step 2 — Back-of-the-Envelope

### 4.1 The numbers you must know

| Quantity | Value | Note |
|---|---|---|
| 1 day | 86,400 s | "round to 100k" |
| 1M requests/day | ~12 req/s | divide by 86,400 |
| 1M requests/day | ~1.2k per 100 s | — |
| SSD random read | ~100 µs | |
| Network in-DC RTT | ~0.5 ms | |
| Cross-region RTT | ~30–150 ms | |
| Redis GET | ~0.5 ms | |
| Postgres indexed read | ~1 ms | |
| Postgres simple write | ~1 ms, ~1–5k/s per node | |
| Kafka per partition | ~10 MB/s, ~100k msg/s | |
| Node/Spring instance | ~1–10k simple req/s | |
| Memory for 1M small objects | ~100–300 MB | |

**Rule of thumb to state:** one Postgres primary handles **~5–20k simple writes/s** and
**~10–50k simple reads/s** with good indexes. Beyond that, cache for reads and shard for
writes.

### 4.2 The estimation script

```
GIVEN:  10M DAU, each reads 20 items and writes 2 per day
READS:  10M × 20 = 200M/day ≈ 2,300/s average, peak ≈ 3× ≈ 7,000/s
WRITES: 10M × 2  =  20M/day ≈    230/s average, peak ≈ 700/s

STORAGE: each write 1 KB → 20M × 1 KB = 20 GB/day → 7 TB/year
         (with a 3× replication factor: 21 TB/year)

BANDWIDTH: 7,000/s × 1 KB = 7 MB/s read, 0.7 MB/s write

CACHE: hot 20% of a day's reads → 40M items × 1 KB = 40 GB → one Redis node is
       borderline; 2 shards comfortable.
```

**Always conclude with what the numbers imply:** "7,000 read QPS needs caching and probably two
replicas; 700 write QPS is fine on one primary; 7 TB/year means I want partitioning by month
and a cold-storage tier after 90 days."

Numbers without a conclusion are trivia. **Numbers that drive decisions are the point.**

### 4.3 Sanity checks

- Does the daily write volume exceed one primary's capacity? (then plan sharding or batching)
- Does the working set fit in memory? (then caching is easy)
- Is the bandwidth within one NIC? (then CDN or sharding)
- Is the read:write ratio high enough that a replica pays for itself?

---

## 5. Step 3 — The API

Define 4–6 endpoints. REST is fine; say so and move on.

```
POST /v1/tweets
  body: { text, mediaIds[] }
  → 201 { id, createdAt }

GET /v1/timeline/home?cursor=<opaque>&limit=50
  → 200 { items: [...], nextCursor }

POST /v1/users/{id}/follow
  → 204

GET /v1/users/{id}/tweets?cursor=&limit=
  → 200 { items: [...], nextCursor }
```

Design notes worth saying out loud:

- **Cursor pagination, not offset** — every feed at scale needs it
  ([Topic 11 ch 03](../11-Database-Design-Optimization/03-Query-Optimization-and-Execution-Plans.md)).
- **Idempotency key** on the write — retries are inevitable.
- **Opaque cursors** — you can change the pagination strategy later without breaking clients.
- **Versioned path** (`/v1`) — you will break something eventually.
- **Return ids, not embedded graphs** — keeps payloads bounded.

---

## 6. Step 4 — The Data Model

### 6.1 Start from access patterns, not from entities

```
ACCESS PATTERNS (write them down before the tables):
  A1  read home timeline for a user, paginated, newest first      (hot, 90% of reads)
  A2  read a user's own tweets, paginated                          (hot)
  A3  insert a tweet                                               (moderate)
  A4  insert a follow edge                                         (low)
  A5  count followers of a user                                    (moderate, can be stale)
```

**Each access pattern must map to a specific index or table.** If one doesn't, you've found the
part of the design that needs a cache, a denormalisation, or a different store — which is
exactly the deep-dive material.

### 6.2 Sketch the schema

```sql
users        (id, handle, display_name, created_at)
follows      (follower_id, followee_id, created_at)   -- PK (follower_id, followee_id)
tweets       (id, author_id, text, created_at)        -- shard by author_id
timelines    (user_id, tweet_id, author_id, created_at) -- precomputed feed, PK (user_id, created_at desc)
```

Then state the crucial decision:

> "`tweets` is sharded by `author_id` because a user-timeline read is a per-author range scan.
> The home timeline is *precomputed* into `timelines` — fan-out on write — because 90% of reads
> can't afford to merge N follows at read time."

That sentence is the whole Twitter design. Everything else is plumbing.

### 6.3 Say where it lives

| Data | Store | Why |
|---|---|---|
| Tweets (source of truth) | Postgres, sharded by author | range scans per author |
| Home timelines | Redis sorted sets / Cassandra | precomputed, bounded to N entries |
| Follow graph | Postgres (or a graph store) | small per-user, needs both directions |
| Media | S3 + CDN | blobs don't belong in a DB |
| Counts | Redis counters, reconciled | approximate is fine |

---

## 7. Step 5 — High-Level Design

Draw, left to right, and **say the direction of every arrow**.

```
  client
    │ HTTPS
    ▼
 ┌───────┐   ┌──────────┐   ┌───────────────────────────────────┐
 │ CDN   │──►│ Gateway  │──►│  services                          │
 └───────┘   │ authN,   │   │  ┌────────┐  ┌─────────┐  ┌──────┐ │
             │ rate lim │   │  │ tweet  │  │ timeline│  │follow│ │
             └──────────┘   │  └───┬────┘  └────┬────┘  └──┬───┘ │
                            └──────┼────────────┼──────────┼─────┘
                                   │            │          │
                            ┌──────▼───┐  ┌─────▼────┐  ┌──▼─────┐
                            │ Postgres │  │  Redis   │  │ Postgres│
                            │ tweets   │  │ timeline │  │ follows │
                            └────┬─────┘  └──────────┘  └─────────┘
                                 │ outbox
                            ┌────▼─────┐    ┌──────────────────┐
                            │  Kafka   │───►│ fan-out workers  │──► write timelines
                            └──────────┘    └──────────────────┘
```

Rules for the diagram:

- **Every box has a reason to exist.** If you can't justify it in one sentence, remove it.
- **Every arrow is labelled** with what flows (sync HTTP, async event, read).
- **Show where the data lives**, not just the compute.
- **Mark the sync path** — the request path should be as short as possible.

---

## 8. Step 6 — Deep Dive

The interviewer will pick one or two areas. You should also **propose** one: "the interesting
part here is the fan-out — can I go into that?"

Good deep-dive targets by problem class:

| Problem | Deep dive |
|---|---|
| Twitter/Instagram | fan-out on write vs read, celebrity problem |
| URL shortener | key generation, collision handling, redirect latency |
| Chat | delivery guarantees, ordering, presence, offline queue |
| Rate limiter | algorithm choice, distributed counting, clock skew |
| Ride-hailing | geo-indexing, matching, location updates |
| Payments | idempotency, exactly-once illusion, ledger design |
| Notification | fan-out, dedupe, provider failover, retry |
| Search | indexing pipeline, ranking, query latency |

**How to go deep:** state the problem → naive solution → why it fails at scale → the real
solution → its cost → the metric you'd watch.

```
"The naive fan-out writes a timeline entry for every follower at tweet time.
For a user with 10M followers that's 10M writes per tweet — a celebrity tweet
would melt the system. So I'd use a hybrid: fan-out on write for normal users,
and for high-follower accounts I store their tweets once in a 'pull' list and
merge at read time. The cost is a more complex read path and a threshold to
tune; I'd tune it on write latency p99, targeting under 200 ms."
```

That paragraph is worth more than three correctly drawn boxes.

---

## 9. Step 7 — Bottlenecks & Failure

Walk the request path and ask "what breaks first?"

| Component | Breaks when | Mitigation |
|---|---|---|
| Gateway | traffic spike | autoscale, rate limit, CDN offload |
| Service | CPU-bound fan-out | horizontal scale, async offload |
| Cache | hot key, eviction storm | L1, key replication, bigger node |
| DB primary | write throughput | batch, partition, shard |
| DB replica | lag under reporting load | dedicated replica, read-your-writes routing |
| Queue | consumer slower than producer | scale consumers, DLQ, backpressure |
| Object storage | — (rarely the bottleneck) | — |
| Third-party API | outage, rate limits | circuit breaker, fallback, queue |

**Say the failure behaviour, not just the mitigation:**

> "If Redis is down, the timeline read falls back to merging from the tweet store — slower
> (maybe 800 ms instead of 50), so I'd add a circuit breaker that returns a cached-but-stale
> timeline for up to 60 seconds rather than melting the DB."

That's the difference between "add a cache" and engineering.

---

## 10. Step 8 — Trade-offs & Wrap-Up

Close with a summary the interviewer can write down:

```
SUMMARY
  Built: tweet service, precomputed home timelines, follow graph
  Chose: fan-out on write with a pull path for celebrities
  Cost:  more write amplification, a tuning threshold, a more complex read
  Gave up: real-time consistency of follower counts (5s staleness accepted)
  Would add next: multi-region read replicas; media transcoding pipeline;
                  a dedicated search index fed by CDC
```

**Then invite the next question:** "If you want, I can go deeper on the fan-out threshold, or on
how I'd handle the timeline cache going cold after a deploy."

Ending with an invitation shows you know there's more — and it lets the interviewer probe where
*they* care.

---

## 11. Communication Patterns That Score

| Do | Don't |
|---|---|
| Announce the plan at minute 0 | start drawing silently |
| Write requirements on the (virtual) board | keep them in your head |
| Say "I'll assume X — stop me if that's wrong" | assume silently |
| Number your components | point vaguely |
| Say the direction of each arrow | unlabelled arrows |
| State a trade-off with every choice | present decisions as obvious |
| Take hints immediately and visibly | ignore a hint and keep going |
| Say "I don't know, but here's how I'd find out" | bluff a technology |
| Leave 2 minutes to summarise | run out of time mid-sentence |

**The single highest-value habit:** explicitly mention the option you rejected and why.

> "I could use Kafka for the timeline write, but the volume is 200/s per shard — a direct write
> is simpler and I'd only reach for Kafka if I needed replay for rebuilding timelines."

Rejected alternatives are the most legible evidence of judgement.

---

## 12. Anti-Patterns

| Anti-pattern | Why it fails |
|---|---|
| **Buzzword salad** — Kubernetes, Kafka, Cassandra, microservices with no reason | reads as memorisation, not design |
| **Jumping to the database schema** before requirements | you'll model the wrong thing |
| **Sharding at minute 3** | premature; you haven't shown the arithmetic |
| **No numbers** | every scale claim is unverifiable |
| **Ignoring the interviewer's hint** | they're steering you to the scored area |
| **Designing 15 components shallowly** | no depth anywhere |
| **Refusing to choose** ("it depends") | true but useless without a decision |
| **Ignoring failure modes** | the difference between mid and senior |
| **Skipping the summary** | the interviewer writes their feedback from it |
| **Arguing with the interviewer** | you can disagree, but engage, don't defend |

---

## 13. A Worked Skeleton

For any prompt, this is the fill-in-the-blanks:

```
1. CLARIFY (5 min)
   Functional:  [3-4 core journeys]           Out of scope: [...]
   Non-functional: scale [DAU], latency [p99], availability [%],
                   consistency [staleness budget], read:write [ratio]

2. ESTIMATE (3 min)
   read QPS = DAU × reads/user / 86,400 × 3 (peak)
   write QPS = DAU × writes/user / 86,400 × 3
   storage/year = writes/day × bytes × 365 × replication
   ⇒ implication: [cache? replica? shard? partition?]

3. API (4 min)
   [4-6 endpoints, cursor pagination, idempotency on writes]

4. DATA (5 min)
   access patterns A1..An
   [tables] + [index per access pattern] + [which datastore and why]
   key decision: [the one that defines the design]

5. HIGH LEVEL (10 min)
   [client → edge → services → stores + async path]
   every box justified in one sentence

6. DEEP DIVE (12 min)
   [the hard part] — naive → why it breaks → real solution → cost → metric

7. FAILURE (4 min)
   [component] dies → [behaviour] → [mitigation]

8. WRAP (2 min)
   chose / cost / gave up / next
```

---

## 14. Self-Check & Revision Card

- [ ] I can state the 8 phases and their time budget from memory.
- [ ] I can list the non-functional dimensions to ask about.
- [ ] I can do QPS/storage/bandwidth arithmetic in under three minutes.
- [ ] I can name five "numbers every engineer should know".
- [ ] I can derive a data model from access patterns.
- [ ] I can say a trade-off and a rejected alternative for each decision.
- [ ] I can describe failure behaviour for each component.
- [ ] I can close with a summary the interviewer can quote.

```
┌────────────────── SYSTEM DESIGN — 60s RECALL ────────────────────────────────┐
│ reqs(5) → est(3) → api(4) → data(5) → high-level(10) → deep(12) → fail(4)    │
│                                                                              │
│ ASK: scale · latency p99 · availability · CONSISTENCY (staleness budget) ·   │
│      READ:WRITE RATIO · retention · geography. Write out-of-scope down.      │
│                                                                              │
│ ESTIMATE and CONCLUDE: "7k read QPS ⇒ cache + 2 replicas; 700 write QPS ⇒    │
│ one primary; 7 TB/yr ⇒ partition by month."                                  │
│                                                                              │
│ DATA from ACCESS PATTERNS; each pattern ⇒ an index or a store decision.      │
│ Every box justified in one sentence. Every arrow labelled.                   │
│                                                                              │
│ DEEP DIVE: naive → why it breaks → real solution → cost → metric.            │
│ FAILURE: name the behaviour when each dependency dies.                       │
│ WRAP: chose / cost / gave up / next.                                         │
│                                                                              │
│ Silence is the failure mode. Take the hint. Reject an option explicitly.     │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Next:** [02 · Scalability, Availability & Reliability](./02-Scalability-Availability-and-Reliability.md)
