# 05 · Capacity Estimation & Back-of-the-Envelope

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★☆ (the part candidates skip and then lose points on)
> **Prerequisite:** [04 · Data-Intensive Designs](./04-Data-Intensive-Designs.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. Why Estimate at All](#1-why-estimate-at-all)
- [2. The Numbers to Memorise](#2-the-numbers-to-memorise)
- [3. The Six Estimations](#3-the-six-estimations)
- [4. Worked Example — Twitter-like Feed](#4-worked-example--twitter-like-feed)
- [5. Worked Example — File Storage (Dropbox-like)](#5-worked-example--file-storage-dropbox-like)
- [6. Worked Example — Chat](#6-worked-example--chat)
- [7. From Numbers to Decisions](#7-from-numbers-to-decisions)
- [8. Sizing Tables](#8-sizing-tables)
- [9. Common Estimation Errors](#9-common-estimation-errors)
- [10. Drill Problems](#10-drill-problems)
- [11. Interview Questions & Model Answers](#11-interview-questions--model-answers)
- [12. Revision Card](#12-revision-card)

---

## 0. TL;DR — The 30-Second Answer

> You estimate to **justify the design**, not for the numbers themselves. Five numbers drive
> almost every architecture decision:
>
> **read QPS**, **write QPS**, **storage/year**, **bandwidth**, and **working-set size**.
>
> One formula each:
> ```
> QPS       = DAU × actions/user/day ÷ 86,400 × 3 (peak factor)
> storage   = writes/day × bytes × 365 × replication factor
> bandwidth = QPS × payload bytes
> cache     = hot fraction × daily reads × item size
> memory/conn = buffers + TLS state ≈ 10–50 KB per WebSocket
> ```
>
> The **conclusion** is the point: "7k read QPS ⇒ cache + 2 replicas; 700 write QPS ⇒ one
> primary; 7 TB/year ⇒ partition by month and cold-tier after 90 days." Numbers without a
> decision are trivia; decisions without numbers are assertions.

---

## 1. Why Estimate at All

Three reasons, and the third is the one interviewers score:

1. **It picks the architecture.** 200 write QPS is a single primary; 200k write QPS is sharded
   from day one. Without the number you can't choose.
2. **It sizes components.** "We need 3 Redis nodes" comes from working-set ÷ node memory, not
   from taste.
3. **It shows you can reason under uncertainty.** The interviewer knows your numbers are rough.
   What they're watching is whether you know *which* numbers matter and what they imply.

**The format that scores:**

```
"Let's estimate. 10M DAU, 20 reads each → 200M reads/day ≈ 2,300/s, peak 3x ≈ 7,000/s.
That's beyond one Postgres with a cold cache but trivial with caching. Writes are 200/s,
so one primary is fine. Storage is 20 GB/day → 7 TB/year, so I'd partition by month.
So: cache + 2 read replicas, single primary, monthly partitions. Sound right?"
```

Six lines. Every number feeds a decision. That's the whole skill.

---

## 2. The Numbers to Memorise

### 2.1 Time

| Quantity | Value | Memory hook |
|---|---|---|
| 1 day | 86,400 s | "~100k" |
| 1 month | ~2.6M s | 30 days |
| 1 year | ~31.5M s | 365 days |
| 1M/day | ~12/s | divide by 86,400 |
| 10M/day | ~116/s | |
| 100M/day | ~1,160/s | |
| 1B/day | ~11,600/s | |

**The single most useful conversion:** `X per day → X / 100,000 per second` (roughly). So 100M
per day ≈ 1,000/s. Get this reflex and you never fumble the arithmetic.

### 2.2 Latency (Jeff Dean-style orders of magnitude)

| Operation | Latency | Relative |
|---|---|---|
| L1 cache | 0.5 ns | 1x |
| Branch mispredict | 5 ns | 10x |
| L2 cache | 7 ns | 14x |
| Main memory | 100 ns | 200x |
| SSD random read | 100 µs | **200,000x** |
| Disk seek (HDD) | 10 ms | 20,000,000x |
| Network in-DC RTT | 0.5 ms | |
| Redis GET | 0.5–1 ms | |
| Postgres indexed read | 1–3 ms | |
| Postgres join/scan | 10–500 ms | |
| Same-continent RTT | 30 ms | |
| Cross-continent RTT | 150 ms | |

**The one to quote:** "memory is 100 ns, an SSD read is 100 µs, a cross-continent round trip is
150 ms — that's nine orders of magnitude, which is why caching works and why you can't hide a
network hop."

### 2.3 Throughput per node (rough, modern hardware)

| Component | Rough capacity |
|---|---|
| Spring Boot service (simple JSON) | 1k–10k req/s per instance |
| Postgres primary, simple writes | 5k–20k/s |
| Postgres primary, reads with good indexes | 10k–50k/s |
| Postgres, complex joins | 100–1k/s |
| Redis node | 100k+ ops/s, ~0.5 ms |
| Kafka broker | 10 MB/s–1 GB/s per broker; ~100k msg/s per partition |
| Elasticsearch node | 1k–5k queries/s |
| Node.js/Go equivalent | 5–50k req/s (for comparison) |
| Nginx/Envoy | 50k–100k+ req/s |
| WebSocket connections per node | 50k–100k |
| MySQL/Postgres connections | 100–500 (pool!) |

**Use these as sanity checks, not gospel.** The interviewer wants to see that 50k req/s on one
Spring instance is implausible and that 500 DB connections is a smell.

### 2.4 Size units

| Item | Size |
|---|---|
| `int` / `long` | 4 B / 8 B |
| UUID | 16 B |
| timestamp | 8 B |
| short text (50 chars) | ~50–100 B |
| URL (100 chars) | 100 B |
| JSON tweet (~300 chars + metadata) | ~500 B–1 KB |
| thumbnail image | 10–50 KB |
| photo | 1–5 MB |
| 1 minute of 1080p video | 50–150 MB |
| 1 row in a well-designed table (with overhead) | 100 B–1 KB |
| index overhead per row per index | ~30–50 B |
| Redis key overhead | ~50–80 B |
| Kafka record overhead | ~70 B |

**The rule for row size:** take the sum of the columns, then **add 20–50%** for row headers,
alignment, and index overhead. People consistently underestimate by 2x.

---

## 3. The Six Estimations

### 3.1 Traffic (QPS)

```
average QPS = users × actions/user/day ÷ 86,400
peak QPS    = average × peak factor
peak factor = 2–3 (consumer), 5–10 (B2B/business hours), 10–100 (event-driven spikes)
```

**Always separate reads from writes.** They scale differently and drive different decisions.

```
reads  = DAU × read actions ÷ 86,400 × peak
writes = DAU × write actions ÷ 86,400 × peak
ratio  = reads / writes          ← the number that picks replicas vs shards
```

### 3.2 Storage

```
daily storage = writes/day × avg record size
yearly        = daily × 365
with replicas = yearly × RF            (RF = 3 typical)
with indexes  = × 1.3–2.0
```

**Don't forget:** backups (another full copy), logs, and derived stores (search index ~same
size again, analytics ~10x compressed).

Practical formula:

```
total ≈ raw × RF(3) × index(1.5) × (1 + derived stores)(1.5) ≈ raw × 6–7
```

That multiplier surprises people, and quoting it is a strong signal.

### 3.3 Bandwidth

```
bandwidth = QPS × payload size

7,000 QPS × 1 KB = 7 MB/s ≈ 56 Mbps     ← trivial
7,000 QPS × 1 MB = 7 GB/s               ← needs CDN or sharding
```

Convert: `MB/s × 8 = Mbps`. A single 10 Gbps NIC ≈ 1.25 GB/s.

**Media changes everything.** A text-only feed at 7 MB/s is one node; a video feed at the same
QPS is a CDN problem, not a server problem.

### 3.4 Cache / working set

```
hot set = fraction of daily reads served from cache
cache size = daily read volume × hot fraction × avg item size
```

Example: 200M reads/day, 1 KB each, 20% hot → 40 GB. One Redis node is borderline (you want
headroom), so 2 shards.

**Sanity check:** if the working set doesn't fit in memory, caching still works — it just has a
lower hit rate and you need to size for the eviction policy, not for the whole set.

### 3.5 Connection capacity

```
concurrent connections = DAU × concurrent fraction
```

Rules of thumb:
- Chat/social: ~5–10% of DAU online at peak.
- 50M DAU → 3–5M concurrent → at 100k/node = **30–50 nodes**.
- Memory per connection: 10–50 KB (buffers + TLS) → 100k conns ≈ 2–5 GB.

### 3.6 Server count

```
instances = peak QPS ÷ per-instance capacity × 1.5 (headroom + N+1 redundancy)
```

Example: 7,000 read QPS at 1,000 QPS/instance = 7 instances, ×1.5 = ~10, and you want N+1 per
AZ → 12 across 3 AZs.

**Always add headroom.** Running at 100% of estimated capacity means the estimate being 20% off
is an outage. And per the queueing curve, ~70% utilization is the practical ceiling.

---

## 4. Worked Example — Twitter-like Feed

**Prompt:** design a Twitter-like feed. 300M DAU, each reads the timeline 30 times/day and posts
twice/day. Text plus occasional media.

### Requirements

```
DAU                      300M
posts/day                600M
timeline reads/day       9B
avg followers per user   ~200 (power-law: median 50, top 1% > 10k)
retention                5 years (posts), 7 days (timelines)
latency                  timeline p99 < 200 ms
```

### Traffic

```
write QPS = 600M / 86,400 ≈ 7,000/s, peak 3x ≈ 21,000/s
read QPS  = 9B / 86,400  ≈ 104,000/s, peak 3x ≈ 312,000/s

read:write ≈ 15:1
```

**Immediately:** 312k read QPS cannot hit Postgres. This is a cache-or-die design.

### Storage

```
post size: id 8 + author 8 + text 300 + metadata 100 ≈ 500 B
raw per day: 600M × 500 B = 300 GB/day
per year:   300 GB × 365 ≈ 110 TB/year
with RF 3 + index 1.5: ≈ 500 TB/year
5 years: ≈ 2.5 PB
```

**Implications:** sharding is mandatory (a single primary can't hold 110 TB/year sensibly), and
media must go to object storage (a 1 MB photo per post would be 600 TB/day — **2000x** the text
volume; that's the point of separating it).

### Fan-out

```
fan-out writes = posts/day × avg followers = 600M × 200 = 120B timeline writes/day
             ≈ 1.4M writes/s average, 4M/s peak
```

**That number is the whole design.** 4M writes/s is impossible with one system — so:

- Fan-out only for users under a threshold (say 10k followers).
- 1% of posts by high-follower accounts skip fan-out → read-time merge.
- Inactive users don't get fanned out (lazy materialisation on login).

With those three, the effective fan-out drops to a manageable ~200k writes/s, which is a Redis
cluster's job.

### Cache

```
timeline cache entry: user_id + post_id + ts + author ≈ 50 B per entry
entries: 300M users × 800 entries = 240B entries × 50 B ≈ 12 TB
```

**12 TB of timeline cache** is a big Redis fleet (~30 nodes at 500 GB each, with headroom). That
number is why the design uses:
- **Bounded timelines** (800 entries) — already accounted for.
- **Lazy materialisation** for inactive users (maybe only 20% active → 2.4 TB).
- **Short retention** on timelines — they're derived, so they can be dropped and rebuilt.

### Servers

```
read: 312k QPS ÷ 5k QPS/instance ≈ 62 instances, ×1.5 headroom ≈ 95, spread over 3 AZs
write: fan-out workers ~200k ops/s ÷ 5k ≈ 40 workers
```

### The summary paragraph

> "312k read QPS ⇒ cache-first with bounded Redis timelines. 21k write QPS and 500 TB/year ⇒
> shard the post store by author and partition by month. 4M/s naive fan-out ⇒ hybrid fan-out
> with a celebrity threshold. Media to S3 + CDN because it's 2000x the text volume. That's about
> 100 app instances, a ~2.5 TB Redis cluster after lazy materialisation, and a sharded Postgres
> or Cassandra for posts."

**That's the answer format**: number → implication → decision, repeated.

---

## 5. Worked Example — File Storage (Dropbox-like)

**Prompt:** 50M users, each stores 200 files averaging 1 MB, 10% daily active, 5 uploads and 20
downloads per active user per day.

### Storage

```
total files  = 50M × 200 = 10B files
total bytes  = 10B × 1 MB = 10 PB
with RF 3    = 30 PB          ← object storage, not a filesystem
yearly growth ≈ 20% → 2 PB/year raw
```

**Implication:** this is object storage (S3/GCS) with a metadata DB. A relational DB storing
blobs dies at ~1% of this.

### Traffic

```
DAU = 5M
uploads   = 5M × 5  = 25M/day ≈ 290/s avg, ~900/s peak
downloads = 5M × 20 = 100M/day ≈ 1,160/s avg, ~3,500/s peak
```

### Bandwidth — the real constraint

```
upload:   900/s × 1 MB = 0.9 GB/s ≈ 7.2 Gbps
download: 3,500/s × 1 MB = 3.5 GB/s ≈ 28 Gbps
```

**28 Gbps of egress** is the actual problem. Implications:
- **CDN / direct-to-S3 signed URLs** — the app server should never proxy file bytes.
- **Deduplication** — most files are shared/duplicated; content-addressed storage (hash) can cut
  storage 30–50%.
- **Chunking + delta sync** — only upload changed blocks (Dropbox's real trick).

### Metadata

```
file metadata: id 16 + path 200 + owner 8 + hash 32 + size 8 + ts 8 ≈ 300 B
10B files × 300 B = 3 TB of metadata
```

3 TB in a relational DB is fine **if sharded by owner**. And metadata QPS:

```
metadata ops = 25M + 100M = 125M/day ≈ 1,450/s avg, 4,300/s peak
```

That's comfortably a sharded Postgres with caching.

### The summary

> "10 PB of blobs ⇒ object storage with signed URLs, never through the app. 28 Gbps of egress ⇒
> CDN and dedupe. 3 TB of metadata with 4.3k QPS ⇒ sharded Postgres keyed by owner. The
> interesting problems are dedupe and delta sync, not the servers."

---

## 6. Worked Example — Chat

**Prompt:** 50M DAU, 40 messages/day each, 10% online at peak, group chats up to 500 members.

### Traffic

```
messages = 50M × 40 = 2B/day ≈ 23,000/s avg, peak 3x ≈ 70,000/s
```

### Connections

```
concurrent = 50M × 10% = 5M
per node   = 100k → 50 nodes
memory     = 5M × 30 KB ≈ 150 GB across the fleet
            ≈ 3 GB per node (plus app heap) → 8–16 GB nodes
```

**That's the defining number:** chat is a **memory/connection** problem, not a QPS problem.

### Storage

```
message size ≈ 200 B
2B/day × 200 B = 400 GB/day
per year = 146 TB raw, × 3 RF ≈ 440 TB
```

Implication: shard by `conversation_id`, partition by month, cold-tier after 90 days (nobody
reads year-old messages).

### Fan-out

```
avg group size ~10, but the tail matters: 500-member groups
fan-out = 70,000/s × average recipients (~3) = 210,000 deliveries/s
```

With 500-member groups it's spiky — so large groups use fan-out on read (subscribe to the
conversation topic) rather than 500 individual pushes.

### The summary

> "70k msg/s is fine for Kafka; 5M concurrent connections is the real constraint — 50 gateway
> nodes at 100k sockets each, so memory-bound rather than CPU-bound. 440 TB/year of messages
> means shard by conversation and cold-tier old messages. Fan-out averages 3 recipients per
> message but the 500-member tail means big groups must be read-fan-out."

---

## 7. From Numbers to Decisions

| If the number is… | Decision |
|---|---|
| read QPS >> write QPS (10:1+) | replicas + cache |
| write QPS > ~10k/s | batch, partition, consider sharding |
| storage > ~10 TB/year | shard + partition + cold tier |
| bandwidth > ~1 GB/s | CDN, direct-to-storage, dedupe |
| working set < 100 GB | single Redis node (with headroom) |
| working set > 1 TB | Redis cluster, or accept a lower hit rate |
| connections > 1M | dedicated gateway fleet, not the API servers |
| fan-out > 1M writes/s | hybrid fan-out (push + pull) |
| one query > 20% total DB time | fix the query, not the hardware |
| utilization > 70% | scale before latency collapses |

**The presentation trick:** state the number, then **immediately** the decision it forces. Never
leave a number hanging.

---

## 8. Sizing Tables

### Redis cluster

```
nodes = working_set_bytes × 1.5 (headroom) ÷ usable_per_node
usable_per_node ≈ 0.6 × instance_memory   (fragmentation + replication buffers)
```

Example: 2.4 TB working set → 3.6 TB with headroom → at 500 GB usable per node = **8 nodes**
(plus replicas).

### Postgres

```
primary capacity ≈ 5–20k simple writes/s, 10–50k reads/s
connections ≈ 2–4 × cores
storage per shard: keep under ~2 TB for backup/restore sanity
shards = total_storage ÷ 2 TB  (also check write throughput)
```

### Kafka

```
partitions = max(throughput ÷ per-partition throughput, consumer parallelism needed)
retention storage = daily_bytes × retention_days × RF
```

Example: 300 GB/day × 7 days × 3 = 6.3 TB per cluster, plus headroom.

### App instances

```
instances = peak_QPS ÷ per_instance_QPS × 1.5   (headroom)
          ≥ 3                                    (one per AZ)
```

---

## 9. Common Estimation Errors

| Error | Fix |
|---|---|
| Forgetting the peak factor | ×2–3 for consumer, ×10 for business hours |
| Forgetting replication in storage | ×3 (or quorum size) |
| Forgetting index overhead | ×1.3–2 |
| Forgetting backups and derived stores | ×1.5 more |
| Using average instead of peak for capacity | size on peak |
| Estimating only storage and not QPS | both drive different decisions |
| Estimating only QPS and not bandwidth | media changes everything |
| Ignoring fan-out amplification | posts × followers is the real write load |
| Treating a row as the sum of column sizes | add 20–50% |
| Forgetting that uniques don't sum | HLL sketches |
| Using 1 KB for an image | 1 MB is 1000x different |
| No conclusion | the number must force a decision |
| Fifteen minutes of arithmetic | 3 minutes, then move on |
| Rounding to implausible precision | "≈7k/s" not "6,944/s" |

---

## 10. Drill Problems

Do these in three minutes each, out loud:

1. **Instagram:** 500M DAU, 5 posts and 50 feed reads per day, 1 MB images. → write QPS, read
   QPS, storage/year, egress.
2. **URL shortener:** 100M new URLs/day, 10:1 read:write, 500 B per row, 5 years. → QPS both
   ways, storage.
3. **Notification service:** 50M DAU, 20 notifications/day, 1 KB payload. → QPS and daily bytes;
   what's the fan-out if 1% are broadcasts to 10M?
4. **Rate limiter:** 10k req/s across 5 gateways, 1M distinct users. → Redis ops/s, memory.
5. **S3-like store:** 100M users, 1 GB each. → storage, and how many nodes at 100 TB each?
6. **Search:** 50M documents × 2 KB, 5k queries/s. → index size (~1.5–2x raw), shards at 50 GB
   each, replicas for 5k QPS.

**Answers (rough):**

1. **Instagram** — writes 500M × 5 = 2.5B/day ≈ 29k/s, peak ~87k/s; reads 500M × 50 = 25B/day
   ≈ 290k/s, peak ~870k/s. Raw image storage at 1 MB uncompressed = 500 TB/day, which is why
   real systems compress to ~100 KB, resizing to several renditions and putting the bytes on a
   CDN. Conclusion: media is a CDN problem; metadata is the only thing your DB sees.
2. **URL shortener** — 1,157 writes/s and ~11,570 reads/s; 182B rows × 500 B ≈ 91 TB raw, ×~6
   with replication and indexes ≈ **550 TB**. Conclusion: shard by code, cache every code.
3. **Notifications** — 50M × 20 = 1B/day ≈ 11,570/s, ×1 KB ≈ **11 MB/s** of payload (trivial).
   The interesting number is the blast: 1% of 20/day as broadcasts ≈ 10M notifications per job,
   so batch at 10k users per message = **1,000 messages**, throttled to the provider's rate.
4. **Rate limiter** — 10k req/s ⇒ 10k Redis ops/s (or ~1k with local batch refill of 10 tokens
   per call); 1M users × ~100 B ≈ **100 MB** of Redis memory. Conclusion: one small Redis node.
5. **S3-like store** — 100M × 1 GB = **100 PB**. At 100 TB per node that's ~1,000 nodes before
   redundancy, which is why object stores use **erasure coding** (~1.4x) instead of 3x
   replication — 300 PB versus 140 PB is the difference between viable and not.
6. **Search** — 50M × 2 KB = 100 GB raw → ~150–200 GB indexed. At 50 GB per shard that's 4
   shards; 5k QPS needs replicas (one replica per shard handles roughly 1–5k QPS each), so
   **~12 nodes** with headroom.

---

## 11. Interview Questions & Model Answers

**Q1. How do you estimate QPS for a system with 10M DAU?**
> 10M users × actions per user per day, divided by 86,400 to get the average, then multiplied by
> a peak factor of 2–3 for consumer traffic or more for business hours. So 10M users × 20 reads =
> 200M/day ≈ 2,300/s average, ~7,000/s peak. I always compute reads and writes separately,
> because the ratio determines whether I need replicas or sharding.

**Q2. Why do you multiply storage by 3?**
> Replication. Three replicas means three copies. Then I add index overhead, typically 1.3–2x,
> and remember backups and derived stores like a search index. So a naive "raw data × 365" is
> usually off by a factor of 5–7, which changes the sharding decision.

**Q3. What's the most common estimation mistake?**
> Forgetting fan-out amplification. For a feed, the interesting number isn't posts per second,
> it's posts per second times followers — 7,000 posts/s with 200 followers each is 1.4M timeline
> writes/s. That number is what determines whether fan-out on write is viable at all.

**Q4. How do you convert a bandwidth estimate into a decision?**
> If it's under a few hundred MB/s, instances can serve it. Above ~1 GB/s I stop serving bytes
> from the app: signed URLs to object storage, a CDN in front, and deduplication. The classic
> error is a file service proxying bytes through Spring — 28 Gbps of egress through an app server
> is impossible.

**Q5. How many servers would you need for 300k QPS?**
> Depends on per-instance capacity, which I'd measure rather than assume — but roughly 1–5k
> simple req/s per Spring instance, so 60–100 instances, times 1.5 for headroom, spread across
> three AZs. And I'd check that whatever is downstream — the DB, cache, or a third party — can
> take 300k QPS, because the app tier is usually the easy part.

**Q6. How do you size a Redis cluster?**
> Working set in bytes × 1.5 for headroom, divided by usable memory per node, where usable is
> about 60% of instance memory to leave room for fragmentation and replication buffers. Then
> double for replicas if I want HA. And I'd verify with the actual hit rate afterwards, because a
> sizing based on "all data" instead of "hot data" over-provisions badly.

**Q7. Do you need exact numbers?**
> No — I need numbers good enough to choose between architectures, and I say they're rough. The
> decision boundaries are coarse: 1k vs 100k write QPS picks a different design, 1 TB vs 1 PB
> picks a different storage. Precision doesn't change the decision, so I'd rather spend the time
> on the design.

---

## 12. Revision Card

```
┌──────────── CAPACITY ESTIMATION — 60s RECALL ────────────────────────────────┐
│ QPS = DAU × actions/day ÷ 86,400 × peak(2–3 consumer, 5–10 business)         │
│ 1M/day ≈ 12/s · 100M/day ≈ 1,160/s · X/day ≈ X/100,000 per second            │
│                                                                              │
│ STORAGE = writes/day × size × 365 × RF(3) × index(1.5) × (1 + derived)       │
│         ⇒ naive estimates are off by 5–7x                                    │
│ BANDWIDTH = QPS × payload · MB/s × 8 = Mbps · >1 GB/s ⇒ CDN, not app        │
│ CACHE = hot fraction × daily reads × item size                               │
│ CONNECTIONS = DAU × 5–10% · 10–50 KB each · 100k per node                    │
│ SERVERS = peak QPS ÷ per-instance × 1.5 headroom, ≥3 (one per AZ)            │
│                                                                              │
│ CAPACITY: Spring 1–10k req/s · PG 5–20k writes · Redis 100k ops ·            │
│           WS 50–100k conns/node · connections ≈ 2–4 × cores                  │
│                                                                              │
│ LATENCY: mem 100 ns · SSD 100 µs · in-DC RTT 0.5 ms · cross-continent 150 ms │
│                                                                              │
│ FAN-OUT is the trap: posts/s × followers = the real write load.              │
│ ALWAYS end with the DECISION the number forces.                              │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [04 · Data-Intensive Designs](./04-Data-Intensive-Designs.md) · **Next:** [06 · End-to-End Case Studies](./06-End-to-End-Case-Studies.md)
