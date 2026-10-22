# 03 · Classic Design Problems

> **Level:** Intermediate → Expert
> **Interview Frequency:** ★★★★★ (these are asked verbatim)
> **Prerequisite:** [02 · Scalability, Availability & Reliability](./02-Scalability-Availability-and-Reliability.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. How to Use This Chapter](#0-how-to-use-this-chapter)
- [1. URL Shortener](#1-url-shortener)
- [2. Distributed Rate Limiter](#2-distributed-rate-limiter)
- [3. News Feed (Twitter/Instagram)](#3-news-feed-twitterinstagram)
- [4. Chat / Messaging](#4-chat--messaging)
- [5. The Common Skeleton](#5-the-common-skeleton)
- [6. Interview Questions Across All Four](#6-interview-questions-across-all-four)
- [7. Revision Card](#7-revision-card)

---

## 0. How to Use This Chapter

Each problem is presented in the same shape, so you can reuse the structure:

```
PROBLEM → REQUIREMENTS → ESTIMATES → API → DATA → HIGH-LEVEL → DEEP DIVE → FAILURE → TRADE-OFFS
```

**Do not memorise the designs.** Memorise the **one defining decision** per problem, because
that's what the interviewer is probing:

| Problem | The defining decision |
|---|---|
| URL shortener | **how you generate the key** (counter+base62 vs hash vs pre-generated) |
| Rate limiter | **the algorithm and where the counter lives** (atomic, distributed) |
| News feed | **fan-out on write vs read**, and the celebrity/hot-key exception |
| Chat | **delivery semantics**: ordering, offline queue, presence, exactly-once illusion |

Everything else is standard plumbing you can derive on the spot.

---

## 1. URL Shortener

### 1.1 Requirements

**Functional**
- `POST /shorten { longUrl }` → `{ shortUrl }`
- `GET /{code}` → `302` redirect to the original
- Optional: custom alias, expiry, click analytics

**Non-functional**
- 100M new URLs/day, 10:1 read:write → **~1,200 writes/s, ~12,000 reads/s** average;
  peak 3x → 3.6k writes/s, 36k reads/s
- Redirect p99 < 20 ms (it's in the browser's critical path)
- 99.99% availability (a dead link is a visible failure)
- Read-heavy, tiny objects, extremely cacheable
- 5-year retention → 100M × 365 × 5 = **182B rows**; at ~500 B/row ≈ **90 TB**

### 1.2 The defining decision: key generation

| Approach | How | Pros | Cons |
|---|---|---|---|
| **Hash + collision check** | MD5/SHA of the URL, take 7 chars | deterministic, idempotent for the same URL | **collisions** need retry; same URL may get different codes if you salt |
| **Counter + base62** | global counter → base62 | no collisions, short, ordered | predictable (enumerable), needs a distributed counter |
| **Pre-generated pool** | offline generator fills a key table; workers take batches | no runtime coordination, no collisions | extra storage, batch coordination |
| **Random + retry** | random 7 chars, insert, retry on conflict | simple, unguessable | wasted attempts as the space fills |

**The answer to give:** a **counter encoded in base62**, with the counter allocated in batches
by a coordination service (or a Snowflake-style id), so no two nodes ever collide and no network
round trip is needed per write.

```
62^7 = 3.5 trillion keys   ← 7 chars is enough for 182B with huge headroom
62^6 = 56.8 billion        ← too tight for 182B
```

**The follow-ups they will ask:**

- *"What about enumeration?"* — a pure counter leaks volume and lets anyone scrape. Fixes: a
  fixed random offset added to the counter, or a Feistel/XOR permutation of the id (a
  bijection, so still collision-free), or a signed/HMAC suffix.
- *"How do you avoid a single point of failure on the counter?"* — pre-allocate ranges (node A
  gets 1M–2M, node B gets 2M–3M) and persist the allocation. A crash wastes a range rather than
  causing collisions.
- *"What if the same long URL is submitted twice?"* — either accept duplicate codes (simplest;
  storage is cheap) or keep a hash index on `long_url` and return the existing code
  (idempotent, needs a unique constraint and a race-safe insert).

### 1.3 Data model

```sql
CREATE TABLE urls (
  code       char(7)     PRIMARY KEY,       -- or bigint id + a generated code column
  long_url   text        NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz,
  owner_id   bigint,
  hits       bigint      NOT NULL DEFAULT 0
);
CREATE INDEX urls_long_url_idx ON urls (long_url);   -- only if you want idempotent shorten
```

**Sharding:** shard by `code` (hash). Redirects are pure key lookups, so any shard works given
the key. 182B rows / 16 shards ≈ 11B per shard — comfortable with partitioning by
`created_at` on top for retention.

### 1.4 High-level

```
  POST /shorten
  client ──► gateway ──► shorten-service ──► key allocator (range cache)
                                 │                │
                                 └──► DB write ◄──┘
                                 └──► cache.set(code → url)

  GET /{code}   ← the hot path, 12k rps
  client ──► CDN/edge ──► redirector ──► Redis (code → url)
                                  │  miss
                                  └──► DB ──► cache.set
                       └──► 302 Location: longUrl
```

**Why 302 not 301:** a `301` is cached by the browser forever, so you lose analytics and can
never change the destination. Use `302`/`307` and cache at your own layer instead. (Mention this
— it's a small detail interviewers like.)

### 1.5 Deep dive: redirect latency

Target p99 < 20 ms. Budget:

```
TLS handshake (resumed)   5 ms
edge/gateway              1 ms
Redis lookup              0.5 ms
serialisation + 302       0.5 ms
network                   5 ms
─────────────────────────────
                          ~12 ms  ← comfortable
DB fallback adds         +2 ms (only on 1–2% of requests)
```

So: **cache every code in Redis with a long TTL** (a code's target rarely changes, and if it
does you can delete it). Do **not** increment the hit counter synchronously on the redirect
path — that's a write per read and turns a 12k rps read workload into a 12k rps write workload.
Buffer counts in memory or Redis and flush in batches.

**That's the depth answer:** "the analytics counter is the dangerous part of this design; I'd
never put it on the redirect path."

### 1.6 Failure

| Failure | Behaviour |
|---|---|
| Redis down | redirects fall back to DB — slower (p99 ~40 ms) but working; circuit breaker + DB read replicas |
| Key allocator down | each node has a pre-allocated range; writes continue until it's exhausted |
| DB primary down | shorten fails; redirects keep serving from cache (a great property to state) |
| Analytics pipeline down | counts are best-effort; never block the redirect |

**Trade-off summary:** chose a counter+base62 for collision-free short keys, at the cost of
predictability (mitigated by permutation); chose cache-first redirects so a DB outage doesn't
break existing links.

---

## 2. Distributed Rate Limiter

### 2.1 Requirements

- Limit requests per client (user, API key, IP) across **N gateway instances**
- Typical: 100 req/min per user, 10k req/s global
- Must be **fast** (< 2 ms overhead) and **accurate enough** (small over-admission acceptable)
- Must survive node failure and not lose limits on restart
- Must return `429` with `Retry-After` and `X-RateLimit-*` headers

### 2.2 Algorithms

| Algorithm | Burst | Accuracy | Memory | Note |
|---|---|---|---|---|
| Fixed window counter | 2x at boundaries | low | O(1) | simplest |
| Sliding window log | none | exact | O(requests) | memory-heavy |
| Sliding window counter | small | ~good | O(1) | **the good default** |
| **Token bucket** | up to capacity | good | O(1) | allows controlled bursts |
| Leaky bucket | none | good | O(1) | smooths output |

**The boundary burst problem** with fixed windows:

```
limit 100/min
13:00:59 → 100 requests (window 13:00)
13:01:00 → 100 requests (window 13:01)
200 requests in 2 seconds — the limit was "100 per minute"
```

**Token bucket** is the practical answer: capacity `C`, refill `R`/s, each request takes a token.
Bursts up to `C` are allowed, sustained rate is `R`. That matches how APIs actually want to
behave.

### 2.3 Where the counter lives

| Option | Pros | Cons |
|---|---|---|
| **In-process (Guava `RateLimiter`)** | zero latency, no coordination | limits are **per instance** — N instances means N× the limit |
| **Redis (shared)** | correct across instances, fast (~1 ms) | extra hop, hot key risk, Redis becomes critical |
| **Local + periodic sync** | fast, approximately global | overshoot up to `N × batchSize` |
| **Gateway-native** (Envoy/Kong) | no app code | less flexible logic |

**The answer:** Redis with a Lua script for atomicity, plus **local token bucket smoothing** in
front to avoid a Redis round trip on every request.

### 2.4 The Redis implementation (atomic via Lua)

```lua
-- KEYS[1] = rate key;  ARGV = rate/s, capacity, now_ms, cost
local rate  = tonumber(ARGV[1])
local cap   = tonumber(ARGV[2])
local now   = tonumber(ARGV[3])
local cost  = tonumber(ARGV[4])

local s = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(s[1]) or cap
local ts     = tonumber(s[2]) or now

tokens = math.min(cap, tokens + ((now - ts) / 1000.0) * rate)
if tokens < cost then
  redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
  redis.call('PEXPIRE', KEYS[1], math.ceil(cap / rate * 1000) + 1000)
  return {0, math.floor(tokens)}
end
redis.call('HMSET', KEYS[1], 'tokens', tokens - cost, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil(cap / rate * 1000) + 1000)
return {1, math.floor(tokens - cost)}
```

One round trip, atomic, correct across all instances.

### 2.5 Scale concerns

- **Hot key** — one abusive user's key becomes a hot shard. Mitigate with a local bucket in
  front (batch tokens from Redis, e.g. take 10 tokens per call) so Redis sees 1/10 the traffic.
- **Large key space** — 10M users × ~100 B = 1 GB. Fine for Redis, but set TTLs so idle users
  expire.
- **Redis down** — decide the policy **in advance**: fail-open (allow, log) or fail-closed
  (reject). For abuse protection, fail-open with a conservative local limit; for billing, fail
  closed. **Say which and why** — this is the scored question.
- **Clock skew** — never use wall-clock deltas across nodes; pass `now` from one source, or use
  Redis `TIME`.

### 2.6 Layering

```
edge/CDN      → coarse IP limits (protects the platform)
gateway       → per-API-key limits (protects per-tenant fairness)
service       → per-user business limits (protects the DB)
per-dependency→ concurrency limits (bulkheads)
```

Rate limiting isn't one limiter; it's a hierarchy, each with a different key and purpose.

**Trade-off summary:** token bucket in Redis with local batching — accurate to within one batch
of tokens, one Redis call per 10 requests, with a documented fail-open policy.

---

## 3. News Feed (Twitter/Instagram)

### 3.1 Requirements

**Functional**
- Post (text + media)
- Follow/unfollow
- Home timeline: recent posts from followees, paginated
- User timeline: own posts

**Non-functional**
- 300M DAU, 2 posts/day each → **7k writes/s** peak
- 50 timeline reads/user/day → **~170k reads/s** peak — **read:write ≈ 25:1, but the absolute
  read number is the problem**
- Timeline p99 < 200 ms
- Eventual consistency OK for timelines (a few seconds), but a user must see their **own** post
  immediately
- Availability 99.99% for reads

### 3.2 The defining decision: fan-out on write vs read

```
FAN-OUT ON WRITE (push)                    FAN-OUT ON READ (pull)
post ──► write to every follower's          post ──► store once
         timeline (N writes)                        │
                                            read ──► query all followees'
                                                     recent posts, merge, sort
✅ read is a single lookup (fast)           ✅ write is O(1) (fast)
❌ write is O(followers)                    ❌ read is O(followees) + a merge
❌ celebrity with 50M followers =           ❌ read p99 explodes with 1,000 followees
   50M writes per post
```

**The answer: a hybrid.**

- **Normal users (< ~100k followers)** → fan-out on **write**. Their post is pushed into their
  followers' timeline caches.
- **Celebrities (≥ threshold)** → fan-out on **read**. Their posts are stored once and merged
  into the reader's timeline at read time.
- **Inactive users** → don't fan out at all; materialise their timeline on their next login
  (lazy).

```
post by @normal (500 followers)
  └─► Kafka ──► fan-out workers ──► ZADD timeline:{follower} for 500 followers

post by @celeb (50M followers)
  └─► stored in celeb_posts:{celeb}; readers merge it in at read time
```

**The threshold is a tunable parameter** — tune it on write p99 latency vs read p99 latency.
Saying "it's a knob I'd tune with data" is better than naming an arbitrary number.

### 3.3 Data model

```sql
-- source of truth, sharded by author_id
posts (id, author_id, text, media_ids, created_at)
  PRIMARY KEY (id)
  index (author_id, created_at DESC)

-- follow graph, sharded by follower_id
follows (follower_id, followee_id, created_at)
  PRIMARY KEY (follower_id, followee_id)
  index (followee_id)   -- to find followers for fan-out
```

```
-- timeline cache, Redis sorted set per user, capped at ~800 entries
ZADD timeline:{user_id} <post_timestamp> <post_id>
ZREVRANGEBYSCORE timeline:{user_id} +inf <cursor> LIMIT 0 50
ZREMRANGEBYRANK timeline:{user_id} 0 -801     -- keep it bounded
```

**Bounded timelines are essential** — an infinite timeline for a user following 1M accounts is
unbounded memory. Keep the most recent ~800 and fall back to a DB query for the deep tail (which
almost nobody reads).

### 3.4 Read path

```
GET /timeline/home?cursor=
  1. read timeline:{me} from Redis (ZREVRANGEBYSCORE, 50 ids)
  2. for each celebrity I follow: read celeb_posts:{celeb}, merge by timestamp
  3. hydrate post ids → post objects via a multi-get (Redis or a batch DB read)
  4. filter (blocked users, deleted posts) — do this at hydration, not in the cache
  5. return items + opaque cursor
```

**Hydration** (ids → full objects) is usually the slowest step; batch it with `MGET` and cache
hot posts separately, because a viral post is read by millions.

### 3.5 Write path

```
POST /posts
 1. write to posts table (sharded by author_id) — source of truth
 2. write outbox row (same transaction)                        ← never dual-write
 3. return 201 immediately (user sees their own post from the source of truth)
 4. Kafka → fan-out workers:
      a. read followers (paginated, from follows index)
      b. for each active follower: ZADD timeline:{follower} + cap
      c. if follower count > threshold: skip, mark as celebrity
```

**Step 3 matters:** the user must see their own post instantly, so their own timeline read
merges their own recent posts from the source of truth — don't wait for fan-out.

### 3.6 Failure

| Failure | Behaviour |
|---|---|
| Fan-out workers down | timelines stale; Kafka buffers; reads still work (stale) |
| Redis timeline down | fall back to a DB merge query — slow, rate-limited, degraded |
| Follow graph read slow | fan-out lags; posts still appear for the author |
| Celebrity post | read-time merge; no fan-out storm |

**Trade-off summary:** fan-out on write for 99% of users (fast reads), read-merge for celebrities
(bounded writes), bounded timelines (bounded memory), outbox (atomic publish).

---

## 4. Chat / Messaging

### 4.1 Requirements

**Functional**
- 1:1 and group messages
- Delivery to online users in real time
- Offline users receive queued messages on reconnect
- Read receipts, typing indicators, presence
- History, paginated

**Non-functional**
- 50M concurrent connections, 100k messages/s
- Latency p99 < 200 ms for delivery
- **Ordering per conversation** (not global)
- **No message loss** — at-least-once delivery + client-side dedupe
- Availability 99.99%

### 4.2 The defining decision: connection handling

The hard part isn't storage — it's **holding 50M live sockets**.

```
50M connections / 100k per node = 500 nodes      ← chat is a connection problem
```

| Transport | Note |
|---|---|
| **WebSocket** | bidirectional, the default for modern chat |
| SSE | server→client only, simpler, works through proxies |
| Long polling | fallback only; expensive |
| MQTT | if you're doing IoT-style fan-out |

**Per connection cost:** ~10–50 KB (buffers + TLS state) → 100k connections ≈ 2–5 GB per node.
That's why chat services are memory-bound and use fewer, fatter nodes than a typical REST
service.

### 4.3 Architecture

```
   sender ──WS──► chat-gateway (node A) ──► message service ──► DB (durable)
                        │                        │
                        │                        └─► Kafka (message bus)
                        ▼                              │
              presence/registry                        ▼
              (user → node)                    delivery workers
                                                    │
                                    ┌───────────────┼───────────────┐
                                    ▼               ▼               ▼
                            recipient online?  offline?        push notif
                              → route to node  → queue/store    (APNs/FCM)
```

**The registry** maps `user_id → gateway_node` so a message from node A can reach a socket on
node B. In Redis: `SET presence:{user} {node} EX 30` refreshed by heartbeats. Node-to-node
delivery is a direct internal RPC or a per-node Kafka topic.

### 4.4 Message flow

```
1. Sender → WS → gateway (node A)
2. Gateway assigns a per-conversation sequence number     ← ordering
3. Gateway persists to DB, asynchronously (batched)
4. Publishes to Kafka (partitioned by conversation_id)    ← ordering preserved
5. Delivery worker:
     a. look up recipient's node in the registry
     b. if online → forward to that node → push over WS
     c. if offline → leave in an offline queue + push notification
6. Recipient acks → sender shows "delivered"
7. Client stores locally with the sequence number; dedupes on (conversation, seq)
```

**Per-conversation sequence numbers** are the ordering primitive. Not a global one — that would
serialise the whole system through one counter.

### 4.5 Data model

```sql
-- messages, partitioned by conversation_id and time
messages (
  conversation_id  bigint,
  seq              bigint,
  sender_id        bigint,
  body             text,
  created_at       timestamptz,
  PRIMARY KEY (conversation_id, seq)
);

conversations (id, type, created_at);
conversation_members (conversation_id, user_id, joined_at, last_read_seq);
```

**Sharding:** by `conversation_id`. Every query is scoped to a conversation, so this gives perfect
locality and no cross-shard joins.

**Read receipts:** `last_read_seq` per member. A receipt for seq 120 implies all earlier
messages are read — one integer instead of a row per message.

### 4.6 The hard parts

**Ordering vs latency.** Assigning a global sequence requires coordination. Per-conversation
sequences can be assigned by the conversation's owner shard (`messages` is sharded by
conversation, so the seq assignment is local).

**Exactly-once.** Doesn't exist. The client dedupes on `(conversation_id, seq)`. The server
retries on WebSocket failure; the client's dedupe makes it invisible.

**Group fan-out.** A 500-person group message = 500 deliveries. For large groups, use a
fan-out-on-read model: store the message once, and each member's client subscribes to the
conversation topic.

**Presence.** Heartbeats every 15–30 s with a 60 s TTL in Redis. Presence is inherently
eventually consistent — nobody should build a feature on it being exact.

**Typing indicators** must be **ephemeral and lossy** — never persist them, expire in 5 s, drop
under load. Making them reliable is a classic over-engineering mistake.

**History pagination** — keyset on `(conversation_id, seq DESC)`. Never offset.

### 4.7 Failure

| Failure | Behaviour |
|---|---|
| Gateway node dies | 100k clients reconnect to other nodes; registry entries expire; messages queued |
| Registry (Redis) down | fall back to: persist + let clients poll on reconnect |
| Kafka down | buffer in the gateway, then persist; delivery delayed, not lost |
| DB down | messages are durable in Kafka; delivery can proceed, history reads degrade |
| Push provider down | notifications delayed; in-app delivery unaffected |

**Trade-off summary:** WebSocket gateways with a presence registry, per-conversation sequence
numbers for ordering, dedupe at the client, Kafka for durability and delivery decoupling.

---

## 5. The Common Skeleton

All four problems reduce to the same skeleton with different bottlenecks:

```
                      ┌───────────────┐
   client ───────────►│ edge (CDN/LB) │
                      └───────┬───────┘
                              ▼
                      ┌───────────────┐
                      │ API / gateway │  authN · rate limit · routing
                      └───────┬───────┘
                              ▼
                      ┌───────────────┐        ┌───────────────┐
                      │   service     │◄──────►│  cache        │
                      └───┬───────┬───┘        └───────────────┘
                          │       │
                  ┌───────▼──┐  ┌─▼──────────┐
                  │  DB      │  │  queue      │──► async workers
                  │ (sharded)│  │  (Kafka)    │
                  └──────────┘  └─────────────┘
```

| Problem | The bottleneck | The fix |
|---|---|---|
| URL shortener | read QPS on redirect | cache everything; never write on the read path |
| Rate limiter | distributed atomic counter | Redis + Lua; local batching |
| News feed | read amplification across follows | precompute (fan-out on write) + celebrity exception |
| Chat | concurrent connections | WebSocket gateways + registry + async delivery |

**The pattern to state:** "Every one of these is the same architecture; the difference is which
resource saturates first, and that determines the one component I'd design carefully."

---

## 6. Interview Questions Across All Four

**Q1. Why 302 and not 301 for a URL shortener?**
> A 301 is cached permanently by the browser, so I lose analytics and can never change the
> destination. A 302 keeps the redirect under my control, and I cache it at my own layer where I
> can invalidate.

**Q2. How do you generate short codes without collisions?**
> A monotonic counter in base62 — collision-free by construction. I'd allocate counter ranges in
> batches per node so there's no per-write coordination, and permute the id with a bijection
> (Feistel or multiply-mod) so codes aren't trivially enumerable. Hashing needs collision retries
> and makes enumeration impossible but costs determinism.

**Q3. Why not a fixed-window rate limiter?**
> Because of the boundary: 100 requests at 13:00:59 and 100 at 13:01:00 passes a "100 per minute"
> limit with 200 requests in two seconds. A token bucket allows controlled bursts up to capacity
> and enforces the sustained rate, which is what APIs actually want.

**Q4. Rate limiter in-process or in Redis?**
> In-process is free but per-instance, so N instances means N times the intended limit. Redis is
> correct but adds a round trip. I'd do both: a local token bucket that refills from Redis in
> batches, so Redis sees one call per batch and the limit is globally accurate to within one
> batch.

**Q5. What happens if Redis is down during rate limiting?**
> I decide the policy in advance and state it: fail-open with a conservative local limit for
> abuse protection, because a total outage is worse than some over-admission; fail-closed for
> anything with billing or contractual consequences. Either way it's a documented decision, not
> an emergent behaviour.

**Q6. Why is fan-out on write a problem for celebrities?**
> A user with 50M followers means 50M timeline writes per post. At even 10 µs per write that's
> 500 seconds of work for one post, and it would saturate the fan-out workers. So I hybridise:
> fan out normally, and for accounts above a follower threshold store the post once and merge it
> at read time. The threshold is a tunable knob on write vs read p99.

**Q7. How do you keep timelines bounded?**
> Cap the cached timeline at a few hundred entries and truncate with `ZREMRANGEBYRANK`. Deep
> history is served from the post store with keyset pagination for the tiny fraction of users who
> scroll that far. An unbounded timeline is unbounded memory per user.

**Q8. How do you guarantee message ordering in chat?**
> Per-conversation sequence numbers, assigned by the shard that owns the conversation, which is
> possible because messages are sharded by conversation id. Kafka partitioning by
> conversation_id preserves that order through the pipeline. A global sequence would serialise
> everything, so it's deliberately per-conversation.

**Q9. How do you handle 50M WebSocket connections?**
> It's a connection problem, not a storage problem: roughly 10–50 KB per connection, so 100k
> connections per node means several GB of memory and hundreds of nodes. I'd use a presence
> registry to route between nodes, heartbeats with TTLs for liveness, and horizontal scaling with
> connection-aware load balancing (least-connections, not round robin).

**Q10. Is exactly-once delivery achievable in chat?**
> No. I do at-least-once with a per-conversation sequence number and client-side dedupe, so the
> user perceives exactly-once. Server-side, a resent message is idempotent because
> `(conversation_id, seq)` is the primary key.

---

## 7. Revision Card

```
┌───────────── CLASSIC DESIGNS — 60s RECALL ───────────────────────────────────┐
│                                                                              │
│ URL SHORTENER   defining decision = KEY GENERATION                          │
│   counter+base62 (collision-free) + batch allocation + bijection for privacy │
│   redirect: cache-first, 302 not 301, NEVER write analytics on the hot path  │
│   DB down ⇒ existing links still work from cache                            │
│                                                                              │
│ RATE LIMITER    defining decision = ALGORITHM + WHERE THE COUNTER LIVES      │
│   token bucket (bursts + sustained rate) in Redis via Lua (atomic)           │
│   local bucket refilled in batches to avoid a round trip per request         │
│   Redis down ⇒ declared policy: fail-open (abuse) / fail-closed (billing)    │
│                                                                              │
│ NEWS FEED       defining decision = FAN-OUT ON WRITE vs READ                 │
│   hybrid: push for normal users, pull-merge for celebrities,                 │
│   bounded timelines (~800 entries), own posts from source of truth,          │
│   outbox → Kafka → fan-out workers (never dual-write)                        │
│                                                                              │
│ CHAT            defining decision = CONNECTIONS + ORDERING                   │
│   WebSocket gateways (100k/node), presence registry (user→node, TTL),        │
│   per-conversation seq (sharded by conversation), client dedupe,             │
│   offline queue + push, typing indicators are ephemeral and lossy            │
│                                                                              │
│ ALL FOUR = same skeleton; the difference is WHICH RESOURCE SATURATES FIRST.   │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

**Previous:** [02 · Scalability, Availability & Reliability](./02-Scalability-Availability-and-Reliability.md) · **Next:** [04 · Data-Intensive Designs](./04-Data-Intensive-Designs.md)
