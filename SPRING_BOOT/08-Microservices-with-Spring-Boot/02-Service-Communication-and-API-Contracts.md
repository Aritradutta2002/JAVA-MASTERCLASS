# 02 · Service Communication, APIs & Contracts

> **Level:** Intermediate → Advanced
> **Interview Frequency:** ★★★★☆ ("How do your services talk to each other?")
> **Prerequisite:** [01 · Monolith vs Microservices](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md)
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- [2. Mental Model — The Four Ways to Talk](#2-mental-model--the-four-ways-to-talk)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
  - [3.1 The communication's taxonomy](#31-the-communications-taxonomy)
  - [3.2 The sync vs async decision](#32-the-sync-vs-async-decision)
  - [3.3 The REST's contract](#33-the-rests-contract)
  - [3.4 The gRPC's contract](#34-the-grpcs-contract)
  - [3.5 The GraphQL's contract](#35-the-graphqls-contract)
  - [3.6 The event's contract](#36-the-events-contract)
  - [3.7 The version's strategy](#37-the-versions-strategy)
  - [3.8 The error's contract](#38-the-errors-contract)
  - [3.9 The idempotency's contract](#39-the-idempotencys-contract)
  - [3.10 The pagination's contract](#310-the-paginations-contract)
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

- **Three's styles, one rule:** **sync** (the caller waits) for the **user-facing's
  reads** and the **must-succeed's decisions**; **async** (fire-and-forget, the
  consumer reads later) for everything the user **doesn't wait for** (the
  notification, the reporting, the audit, the index's update).
- **Every's call across a network must assume it will fail.** The failure's modes:
  the **timeout**, the **partial's failure** (the callee succeeded, the response was
  lost), the **duplicate** (the retry re-executes), and the **ordering** violation.
- **The contract is the API, not the code.** The producer and the consumer deploy
  **independently**, so the contract must be **backward's compatible**: **add**
  fields, **never remove or rename** them; **add** optional fields with defaults;
  **never change** a field's type or meaning.
- **HTTP is not a remote method call.** Don't expose your Java interface over HTTP
  with the exceptions and the stack's traces intact. The API's contract is
  **data**, and the error's contract is a **standard's format** (the RFC 9457's
  Problem Details, or a uniform's envelope).
- **Idempotency is not optional** for any **write** over an unreliable's network.
  The `Idempotency-Key` header + a server-side's store + a unique's constraint is
  the standard's pattern.
- **Choose the protocol by the shape of the interaction**, not by fashion: REST for
  the public's API and the resource's CRUD; **gRPC** for the internal's
  high-throughput, typed, streaming's calls; **GraphQL** when the clients need
  different's shapes of the same's data; **events** for the fan-out's
  notifications.
- **The contract's test** (the Pact, the consumer's driven's contracts) is what
  catches the breaking's change **before** the deploy — it's the single's
  highest-value's test in a microservices' estate.

---

## 1. What & Why

### The scenario

```text
  "The checkout's service needs the product's price and the stock's level.
   It calls the catalogue's service and the inventory's service.
   Then it calls the payment's service. Then it publishes an 'OrderPlaced' event.

   How would you design this?"
```

This question is asked in **almost every** senior backend interview, and it exposes
whether you understand that **every network's boundary is a contract's boundary** —
and that contracts are the thing that actually breaks when teams deploy
independently.

### The core problem

In a monolith, calling `catalogueService.getPrice(sku)` is a **method call**: the
type system enforces the signature, the compiler catches the break, the stack
trace points at the line, and the exception propagates normally.

Over a network, **none of that is true**:

| The monolith's guarantee | Over a network |
|--------------------------|----------------|
| The compiler checks the signature | The contract is a **runtime** agreement |
| The call is fast (nanoseconds) | The call is **milliseconds**, and can **hang** |
| The exception is a stack's trace | The exception is a **serialised** payload |
| The types are shared | The types are **copied** — and they **drift** |
| The deploy is atomic | The deploys are **independent** — the versions differ |

**The whole discipline of this chapter is: how do you preserve the monolith's
guarantees when the compiler and the atomic deploy are gone?**

---

## 2. Mental Model — The Four Ways to Talk

```text
  ┌───────────────────────────────────────────────────────────────────┐
  │  ① the REQUEST's / RESPONSE's (sync)                             │
  │     the caller blocks. the coupling: the availability + the time. │
  │     use for: the user-facing's reads, the must-succeed's decisions│
  ├───────────────────────────────────────────────────────────────────┤
  │  ② the EVENT's (async, one-way)                                  │
  │     the publisher doesn't wait. the coupling: the schema only.    │
  │     use for: the notifications, the reporting, the audit, the     │
  │              index's updates, the cross-team's workflows          │
  ├───────────────────────────────────────────────────────────────────┤
  │  ③ the STREAM's (sync, long-lived)                                │
  │     the server pushes. use for: the events' feeds, the metrics,   │
  │     the notifications' push, the gRPC's server-streaming          │
  ├───────────────────────────────────────────────────────────────────┤
  │  ④ the BATCH's (async, scheduled)                                │
  │     the one-off's bulk job. use for: the migrations, the backfills│
  └───────────────────────────────────────────────────────────────────┘

  the CHOOSING's heuristic:
    ┌────────────────────────────────────────────────────────────┐
    │  does the USER wait for the result?                        │
    │     YES → the sync's call (and budget the time)            │
    │     NO  → the event (and make the consumer's idempotent)   │
    └────────────────────────────────────────────────────────────┘
```

**The one-line expert framing:**

> "The **request/response** is for the questions the user is waiting on; the
> **event** is for the statements the system wants to broadcast. If you find
> yourself putting a notification on the request's path, you've made a
> **statement** look like a **question** — and that's the latency's tax and the
> availability's coupling for nothing."

---

## 3. Deep Dive — Theory

### 3.1 The communication's taxonomy

```text
                    THE SERVICE's COMMUNICATION
                            │
        ┌───────────────────┴───────────────────┐
   the SYNC's (the caller waits)          the ASYNC's (the caller doesn't)
        │                                       │
   ┌────┴─────┬──────────┬──────────┐   ┌────┴─────┬──────────┐
   the HTTP's │ the gRPC's│ the JMS's│   the EVENT's│ the BATCH's│
   REST/JSON  │ protobuf  │ (sync)  │   (Kafka,    │ (the ETL,  │
             │           │          │   RabbitMQ)  │  the jobs) │
   the PUBLIC's  the INTERNAL's  the QUEUE's   the FAN-OUT's  the OFFLINE's
   the human's   the typed's   the WORK's   the "this     the "the
   the readable's the fast's    the queue's  happened"     history's
   the loose     the binary's  the legacy's the decoupled's the bulk's
```

**The trade's table:**

| Dimension | The REST's (HTTP/JSON) | The gRPC's (protobuf) | The event's (Kafka/Rabbit) |
|-----------|------------------------|------------------------|---------------------------|
| The payload's size | Large (the JSON's overhead) | Small (the binary's encoding) | Varies |
| The parsing's cost | High (the Jackson's reflection) | Very low (the pre-parsed's) | Medium |
| The latency's overhead | ~1-5ms (the HTTP's stack) | ~0.1-1ms (the HTTP/2's) | ~1ms+ (the broker's hop) |
| The coupling | Loose (the JSON's schema) | Tight (the generated's stubs) | Loose (the schema only) |
| The debugging | Easy (the curl, the browser) | Harder (needs the grpcurl) | Medium (the event's payload) |
| The browser's support | Native | Needs the gRPC-Web's proxy | N/A |
| The streaming | Limited | Native (the server/client's) | Native (the Kafka's) |
| The load's balancing | The client's | The client's | The consumer's group's |
| The error's semantics | The HTTP's status's codes | The gRPC's status's codes | The dead-letter's queue |

### 3.2 The sync vs async decision

**The decision's tree:**

```text
  a NEW's inter-service's interaction
        │
        ├── does the USER's request wait for it?
        │     │
        │     ├── YES → the SYNC's call
        │     │         · but FIRST ask: is the result needed for the
        │     │           IMMEDIATE's response, or can it be DEFERRED
        │     │           (the "the response can come later" pattern)?
        │     │
        │     └── NO → the EVENT
        │
        ├── is the callee's failure FATAL to the caller's correctness?
        │     │
        │     ├── YES (the payment's authorisation) → the SYNC's call
        │     │         with the strict's timeout + the idempotency's key
        │     │
        │     └── NO (the indexing, the email) → the EVENT
        │
        ├── how many consumers?
        │     · ONE → the SYNC's call (or the queue)
        │     · MANY → the EVENT (the fan-out's broker)
        │
        └── does the interaction need a TRANSACTION across the boundary?
              → the EVENT + the saga (the 2PC is almost never the answer)
```

**The critical's insight — the "the user's response can be deferred":**

Many teams default to the sync's call because "the user is waiting". But often the
user is waiting for a **response that doesn't need the downstream's result**:

```text
  ❌ the SYNC's chain (the user waits for everything)
     POST /orders
       → catalogue (price)      80ms
       → inventory (reserve)   120ms
       → payment (charge)      450ms   ← the gateway's slow
       → notification (email)   300ms   ← the user doesn't need this
       → accounting (ledger)    90ms   ← the user doesn't need this
     TOTAL: 1040ms   😞

  ✅ the HYBRID (the user waits for only what it must)
     POST /orders
       → catalogue (price)      80ms
       → inventory (reserve)   120ms
       → payment (charge)      450ms
       → publish OrderPlaced    2ms
     TOTAL: 652ms   ✅  and the rest happens asynchronously
```

**The rule:** the user's request's path should touch **1-3 services**. Everything
else belongs in the **background** — and the background's work must be
**idempotent** and **retryable**, because the event's delivery is
**at-least-once**.

### 3.3 The REST's contract

**The REST's contract is the HTTP's semantics + the JSON's schema.**

**The HTTP's methods' semantics (the ones that matter for a contract):**

| Method | The semantics | The idempotent? | The safe? | The body? |
|--------|--------------|-----------------|-----------|----------|
| `GET` | The read | ✅ | ✅ | No |
| `POST` | The create / the action | ❌ | ❌ | Yes |
| `PUT` | The full's replace | ✅ | ✅ | Yes |
| `PATCH` | The partial's update | ❌* | ❌ | Yes |
| `DELETE` | The removal | ✅ | ✅ | Optional |
| `HEAD` | The metadata | ✅ | ✅ | No |
| `OPTIONS` | The capabilities | ✅ | ✅ | No |

\* `PATCH` is **not** idempotent in the strict's sense (a JSON-Patch's `add` applied
twice duplicates), but the **merge-patch** semantics (setting a field to a value)
**are** idempotent. Use the merge-patch style if you want the property.

**The status's codes that are part of the contract:**

| Code | The meaning | The client's action |
|------|-------------|---------------------|
| `200` | The success with a body | — |
| `201` | The created (with the `Location` header) | — |
| `202` | The accepted (the async's work) | Poll the status's URL |
| `204` | The success, no body | — |
| `301`/`308` | The permanent's redirect | Update the client's URL |
| `400` | The malformed's request | Fix the client — **never retry** |
| `401` | The unauthenticated | Refresh the token, then retry **once** |
| `403` | The unauthorized (the identity's fine, the permission's not) | **Never retry** |
| `404` | The not found | Sometimes (the eventual's consistency) |
| `409` | The conflict (the version's mismatch, the duplicate) | Re-read and retry with the new state |
| `422` | The semantically's invalid (the validation's failed) | Fix the payload — **never retry** |
| `429` | The rate's limited | Retry after the `Retry-After` |
| `500` | The server's error | Retry with the backoff **maybe** |
| `502`/`503`/`504` | The upstream's failure | Retry with the backoff |
| `504` | The gateway's timeout | Retry (the idempotent's ones) |

**The `Retry-After` header** and **the `429`** are the standard's way for a server
to tell a client how long to back off — and most clients ignore them. That's a
free's win.

### 3.4 The gRPC's contract

```text
  the PROTOBUF's contract (the .proto file)
  ┌──────────────────────────────────────────────────────────────┐
  │  syntax = "proto3";                                          │
  │  package com.example.catalogue.v1;                           │
  │                                                              │
  │  service CatalogueService {                                  │
  │    rpc GetProduct (GetProductRequest) returns (Product);    │
  │    rpc ListProducts (ListProductsRequest)                   │
  │        returns (stream Product);   // the SERVER's streaming │
  │    rpc WatchPrice (WatchPriceRequest)                       │
  │        returns (stream PriceUpdate);                        │
  │  }                                                           │
  │                                                              │
  │  message Product {                                           │
  │    string sku_id = 1;   // the field's NUMBER = the version  │
  │    string name   = 2;                                       │
  │    int64  price_cents = 3;                                  │
  │    bool   available = 4;                                    │
  │  }                                                           │
  └──────────────────────────────────────────────────────────────┘
```

**The protobuf's field-number rule (the single's most important's detail):**

> The field's **number** is the **identity**; the field's **name** is the
> **readability**. Renaming a field is a **wire-compatible** change (the number
> stays); **changing or reusing a number** is a **breaking** change.

```text
  ✅ SAFE:
     message Product {
       string sku_id = 1;         // the number unchanged
       string name   = 2;
       int64  price_cents = 3;
       bool   available = 4;
       string colour = 5;         // the NEW's field = 5 ✅
     }

  ❌ BREAKING:
     message Product {
       string sku_id = 1;
       string name   = 2;
       int64  price_cents = 3;
       bool   available = 4;
       int64  price = 5;          // ⚠ reusing 5 for a DIFFERENT's meaning
     }                             //   (or changing 3's type)
```

**The gRPC's four's call's types:**

| The type | The client's behaviour | The use case |
|----------|------------------------|-------------|
| **Unary** | One request, one response | The CRUD (the majority) |
| **Server's streaming** | One request, many's responses | The list's pages, the server's push |
| **Client's streaming** | Many's requests, one response | The bulk's upload |
| **Bidirectional** | Both's streaming | The real-time's chat, the interactive's query |

**The gRPC's deadlines and the cancellation:**

```java
// the DEADLINE is the hard's rule — the server MUST stop when it expires
Deadline deadline = Deadline.after(2, TimeUnit.SECONDS);
Product product = stub.getProduct(request, deadline);
```

**The gRPC's status's codes (the contract's error's model):**

| The code | The meaning | The mapping to HTTP |
|---------|-------------|---------------------|
| `OK` | The success | `200` |
| `INVALID_ARGUMENT` | The bad's request (the validation) | `400` |
| `NOT_FOUND` | The missing's resource | `404` |
| `ALREADY_EXISTS` | The duplicate | `409` |
| `PERMISSION_DENIED` | The unauthorized | `403` |
| `UNAUTHENTICATED` | The unauthenticated | `401` |
| `RESOURCE_EXHAUSTED` | The quota / the rate's limit | `429` |
| `FAILED_PRECONDITION` | The wrong's state | `400`/`409` |
| `DEADLINE_EXCEEDED` | The timeout's expired | `504` |
| `UNAVAILABLE` | The transient's failure (the retry's candidate) | `503` |
| `INTERNAL` | The bug | `500` |

**The gRPC's built-in's features that REST has no equivalent for:**
- **The deadline's propagation** — the timeout's budget flows down the call's chain
  automatically.
- **The health's checking** (`grpc.health.v1.Health`) — the standard's protocol.
- **The reflection's service** — the runtime's discovery of the schemas.
- **The retry's policy** and the **load's balancing** in the gRPC's ecosystem
  (the `grpc-java`'s `RetryPolicy`, the round's robin's, the pick's first).
- **The interceptors** — the server-side and client-side's middleware.

### 3.5 The GraphQL's contract

**When it's the right's choice:**

```text
  ✅ GraphQL's fits:
     · MANY's clients with DIFFERENT's needs from the SAME's data
       (the mobile's app needs 5 fields, the web's app needs 20, the
        report's tool needs 40 — with REST that's 3's endpoints)
     · the "the over-fetching" and "the under-fetching" problem
     · the rapidly's evolving's client requirements
     · the complex's, connected's graph of the data

  ❌ GraphQL's doesn't fit:
     · the simple's CRUD with a known's shape
     · the public's API (the caching's at the CDN's edge is much easier with REST)
     · the file's uploads (the REST's multipart is still better)
     · the high's write's throughput (the REST's batching is simpler)
     · the team's skill (the schema's design, the N+1's problem, the
       the query's depth's limits — a real's learning's curve)
```

**The GraphQL's N+1's problem (the biggest's gotcha):**

```graphql
query {
  orders {          # 1 query
    id
    customer { name }   # ⚠ N more queries, one per order!
  }
}
```

**The fix — the DataLoader (the batching's of the same-request's field's resolution):**

```java
// the DataLoader batches the N's individual's loads into ONE
public class OrderController {

    private final DataLoader<String, Customer> customerLoader;

    public OrderController(DataLoader<String, Customer> customerLoader) {
        this.customerLoader = customerLoader;
    }

    @QueryMapping
    public List<Order> orders() {
        return orderService.findAll();
        // ⚠ the customer's field's resolver must use the loader,
        //   NOT a direct's repository's call
    }

    @SchemaMapping
    public CompletableFuture<Customer> customer(Order order) {
        return customerLoader.load(order.getCustomerId());
        // the loader's dispatch's queue coalesces all the ids into ONE's query
    }
}
```

**The GraphQL's other's gotchas:** the **query's depth's limits** (a nested query
can explode), the **complexity's analysis** (the cost's estimation before the
execution), the **the persisted's queries** (the allow-list, to prevent the
arbitrary's queries), and the **the N+1** again from the **the SQL's joins being
hidden**.

### 3.6 The event's contract

**The event's schema is the loosest and the most's dangerous's contract** — because
the **producer and every's consumer** are coupled through it, and the consumers
**deploy at their own's pace**.

```json
{
  "eventId": "01J8ZQ4K7Y9M3N2P8R5T6V7W8X",
  "eventType": "com.example.orders.v1.OrderPlaced",
  "eventVersion": 1,
  "occurredAt": "2026-10-21T10:15:30.123Z",
  "correlationId": "corr-abc-123",
  "causationId": "cmd-xyz-789",
  "producer": "orders-service",
  "tenantId": "acme-corp",
  "data": {
    "orderId": "ord-12345",
    "customerId": "cus-67890",
    "total": {"amount": 12999, "currency": "USD"},
    "items": [
      {"skuId": "SKU-1", "quantity": 2, "unitPrice": {"amount": 6499, "currency": "USD"}}
    ]
  }
}
```

**The event's envelope's fields — every's one has a's purpose:**

| The field | The purpose | Without it |
|----------|-------------|------------|
| `eventId` | The **idempotency's key** (the unique's constraint) | The duplicate's processing |
| `eventType` + `eventVersion` | The **schema's routing and the version's negotiation** | The wrong's handler, the version's ambiguity |
| `occurredAt` | The **event's time** (vs the processing's time) | The out-of-order's reasoning is impossible |
| `correlationId` | The **whole's journey** across the services | The distributed's debugging is impossible |
| `causationId` | The **command's id** that caused this (the audit's chain) | The "why" is lost |
| `producer` | The **source's service** (the routing, the debugging) | The ambiguous's origin |
| `tenantId` | The **multi-tenant's isolation** | The data's leak between the tenants |
| `data` | The **payload** | — |

**The event's contract's rules (the non-negotiable's ones):**

1. **The event's names are past's tense** — `OrderPlaced`, `PaymentCaptured`,
   `OrderShipped`. Not `PlaceOrder`. (The event's names describe **something that
   already happened**.)
2. **The events are the domain's facts**, not the API's commands. `OrderPlaced` is
   a fact; `CreateOrderRequest` is a command.
3. **The event's payload is self-contained** — the consumer shouldn't have to call
   back to fetch the related's data (that's the chatty's dependency). The
   consumer **may** keep a **denormalised's copy**.
4. **The event's schema is versioned** — `com.example.orders.v1.OrderPlaced`. The
   version is **in the type**, so both's versions can be on the wire at once.
5. **The schema's evolution is backward's compatible** — add the optional's fields
   with the defaults; **never remove or rename**; **never change the type**.
6. **The events are immutable** — you can't "update" a published event. You
   publish a **new** event (the `OrderCancelled` after the `OrderPlaced`).
7. **The consumers are idempotent** — the delivery is **at-least-once**.

### 3.7 The version's strategy

**The three's approaches:**

| The strategy | How | The pros | The cons |
|-------------|-----|----------|----------|
| **URI's versioning** | `/v1/orders`, `/v2/orders` | The obvious, the cacheable, the explorable | The URLs multiply; the clients must know the version |
| **The header's versioning** | `Accept: application/vnd.orders.v2+json` | The URI's stable; the content's negotiation | The less's obvious; the proxy's cache's key must include the header |
| **The media's type's versioning** | `application/vnd.orders.v2+json` | The self-describing | The verbose's URLs |

**The four's strategies (the one's that actually matter):**

```text
  ① the EXPAND's & CONTRACT's (the schema's evolution)   ← the daily's driver
     add the field → backfill → read from the new → remove the old
     ✅ the clients' deploys stay independent
     ⚠ the removal's window must exceed the clients' deploy's lag

  ② the RUNNING's in PARALLEL (the API's versioning)      ← the rare's driver
     serve v1 and v2 from the same's code
     ✅ the old's clients keep working
     ❌ the branch's hell, the double's maintenance

  ③ the BREAKING's CHANGE (the "the clients' will adapt")  ← the startups' driver
     just change it, tell the clients
     ✅ zero's debt
     ❌ only works if you **own** every's client

  ④ the EXPAND's & MIGRATE's & CONTRACT's (the full's lifecycle)
     the three's phases, explicitly
     ✅ the most's rigorous
     ⚠ the slowest
```

**The "the clients' deploy's lag" is the key's number.** In a microservices'
estate, the **unknown's consumer's deploy's cycle** is the constraint. If your
**internal's consumers** deploy **daily** and your **external's API's clients**
deploy **quarterly**, the removal's window for an external's field is **a
quarter + a buffer**, not "the next's release".

**The deprecation's process (do this properly):**

```text
  1. ANNOUNCE — the docs, the changelog, the dashboard, the email.
                 the field is marked @Deprecated with the removal's date.
  2. TELEMETRY — track the usage per's field per's client.
                 "is the v1's endpoint still being called? by whom?"
  3. MIGRATE — the clients's teams get the support.
                 ⚠ the tracking's data is what makes this work
  4. OBSERVE — wait until the usage hits ZERO for a full's cycle.
  5. REMOVE  — the actual's deletion, in a separate's commit.
```

### 3.8 The error's contract

**The RFC 9457's Problem Details (the standard's format for HTTP's errors):**

```json
{
  "type": "https://api.example.com/problems/insufficient-stock",
  "title": "The order could not be fulfilled",
  "status": 409,
  "detail": "The SKU-42 has only 2 units available, but 5 were requested.",
  "instance": "/api/orders/ord-12345",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "skuId": "SKU-42",
  "requested": 5,
  "available": 2,
  "timestamp": "2026-10-21T10:15:30.123Z"
}
```

**Why this matters for a microservices' system:**
- The **client** can branch on the `type` (a **stable's machine-readable's
  identifier**) rather than parsing the human's `detail` string.
- The **detail** is for **humans**; the **type** is for **code**.
- The **traceId** lets the client report a problem and the support's team can find
  the whole's journey.
- The **extra's properties** (the `skuId`, the `requested`) are the
  **contextual's data** — structured, not string-parsed.

**The error's taxonomy (the design's rule):**

| The category | The status | The client's action | The example |
|-------------|-----------|--------------------|------------|
| The **client's fault** (the input's bad) | `400`, `422` | Fix and **don't retry** | The validation's failed |
| The **auth's problem** | `401`, `403` | Re-authenticate (401) / give up (403) | The token's expired |
| The **state's conflict** | `409` | Re-read, then decide | The version's mismatch |
| The **not's found** | `404` | Give up (or the eventual's retry) | The order's gone |
| The **rate's limit** | `429` | Back off, honour the `Retry-After` | The quota's exceeded |
| The **dependency's failure** | `502`, `503`, `504` | Retry with the backoff | The inventory's down |
| The **server's bug** | `500` | Retry with the backoff, then report | The NPE |
| The **unexpected's** (the catch-all) | `500` | Retry | — |

**The critical's rule: the `5xx` means "the server's problem, a retry might help";
the `4xx` means "the client's problem, a retry won't help".** A service that
returns `500` for a validation's failure is a service whose client will build a
**retry's storm** against it.

**The two's failure's models, the contract's decision:**

```text
  ❌ the "the error's object" (the envelope's pattern)
     { "success": false, "error": { "code": "X", "message": "..." } }
     ⚠ the HTTP's status is ALWAYS 200 → the proxies, the caches, the
        the monitoring's tools all think it's a success. BROKEN.

  ✅ the HTTP's status + the Problem's Details (the REST's way)
     the status carries the class, the body carries the detail.
     ✅ the proxies, the caches, the load's balancers, the dashboards
        all work correctly.
```

### 3.9 The idempotency's contract

**The problem it solves:**

```text
  the CLIENT sends POST /orders
       │
       ├── the SERVER processes it, creates the order, returns 201
       │     ⚠ but the RESPONSE is LOST (the network's timeout, the
       │        LB's restart, the connection's reset)
       │
       └── the CLIENT sees a timeout → "did it work?" → RETRIES
              │
              └── the SERVER processes it AGAIN → TWO orders ⚠
```

**This is the most common's bug in a microservices' world**, and the "the call
succeeded but the response was lost" is the **root** of it. The network is not
reliable, and **a successful write is not observable**.

**The pattern:**

```text
  ① the CLIENT generates a key (a UUID) per the logical's operation
     → the Idempotency-Key: 8f14e45f-ceea-467a-9a2b-1d3f4a5b6c7d
     ⚠ the key must be the same for the RETRIES of the SAME's operation,
       and different for a genuinely's new's operation

  ② the SERVER stores (key → the response) with a TTL
     · the atomic's check-and-set (the unique's constraint)
     · the request's fingerprint (the body's hash) to detect the
       key's reuse with a DIFFERENT's payload → 422

  ③ on a repeat with the SAME's key:
     · in-flight  → 409 (or wait and return the same's result)
     · completed  → return the STORED's response verbatim (the same's
                    status's code, the same's body)
```

**The server-side's implementation (the atomic's part):**

```java
@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyRepository repository;

    /**
     * The ATOMIC's claim: the insert either succeeds (we're the first) or
     * fails with a duplicate's key (someone else got here first).
     * ⚠ The unique's constraint is what makes this CORRECT — a
     *   "SELECT then INSERT" has a RACE.
     */
    @Transactional
    public Optional<IdempotencyRecord> tryClaim(String key, String requestFingerprint, String userId) {
        try {
            IdempotencyRecord record = IdempotencyRecord.inProgress(key, requestFingerprint, userId);
            repository.saveAndFlush(record);   // the flush forces the INSERT's constraint's check NOW
            return Optional.empty();           // we're the first ✅
        } catch (DataIntegrityViolationException e) {
            return Optional.of(repository.findById(key).orElseThrow());
        }
    }
}
```

**The rules:**
- The key's scope is the **client + the endpoint** (not global) — a different user's
  key must never collide, and the same key on a different endpoint is a different
  operation.
- The **fingerprint** (the body's hash) detects the "the same's key, a different's
  payload" — a **client's bug** — and returns `422`.
- The **TTL** is the operation's natural's window (e.g. 24h for the payments).
  Not forever (the table's growth), not too short (the retry's window).
- The **response's storage** is the whole's point — you must return the **same**
  response, not just "the operation's already done".

### 3.10 The pagination's contract

**The three's styles:**

| The style | The request | The response | The notes |
|----------|-----------|-------------|----------|
| **The offset's pagination** | `?page=2&size=20` | `{content, page, totalPages}` | The simplest; **skips/duplicates** on the concurrent's inserts; the slow's deep's pages |
| **The keyset's (cursor's) pagination** | `?after=ord_12345&limit=20` | `{content, nextCursor, hasMore}` | ✅ **Stable** under the concurrent's changes; the infinite's scroll's default |
| **The page's token** | `?cursor=eyJ...` | `{content, nextPageToken}` | Opaque; the server's control; the offset's hidden |

**The offset's pagination's problems (the ones that get asked):**

```text
  the OFFSET's problem:
    SELECT * FROM orders ORDER BY created_at DESC LIMIT 20 OFFSET 10000
     · the DB still SCANS 10,020 rows to SKIP 10,000 → the deep's pages are slow
     · if a row's INSERTED at the front, everything's SHIFTS → you SEE a duplicate
       and MISS an order
     · the total's COUNT(*) on a big's table is expensive
```

**The keyset's pagination's fix:**

```sql
SELECT * FROM orders
WHERE (created_at, id) < (:lastCreatedAt, :lastId)   -- the tuple's comparison
ORDER BY created_at DESC, id DESC
LIMIT 20;
```

⚠ The **`ORDER BY` must be unique and deterministic** — append the tie-breaker's
`id`. Otherwise the keyset's pagination can skip or repeat rows when the sort's key
ties.

**The keyset's pagination's trade-offs:** you can't **jump to page 47** (no
random's access) and you can't show the "total's count" (or you need a separate,
expensive's query). For an infinite's scroll's feed, that's exactly what you want.

---

## 4. Visualizations

### 4.1 The four's ways to talk

```text
  ┌──────────────────────┐         ┌──────────────────────┐
  │  the CALLER          │         │  the CALLEE          │
  │                      │  sync   │                      │
  │  ████████████████    │────────▶│  ████████████████    │
  │  █  BLOCKED       █  │  HTTP/  │  █  PROCESSING    █  │
  │  █  (holding a    █  │  gRPC   │  █               █  │
  │  █   thread)      █  │◀────────│  ████████████████    │
  │  ████████████████    │  resp   │                      │
  └──────────────────────┘         └──────────────────────┘
        ⚠ the availability's coupling
        ⚠ the time's coupling (the timeout's budget)


  ┌──────────────────────┐         ┌──────────────────────┐
  │  the PUBLISHER       │         │  the CONSUMER        │
  │                      │  async  │                      │
  │  ████████████████    │  event  │  ████████████████    │
  │  █  publish ────────┼────────▶│  █  CONSUMES LATER █  │
  │  █  (does NOT wait) █  │  to a  │  █               █  │
  │  ████████████████    │  broker │  ████████████████    │
  └──────────────────────┘         └──────────────────────┘
        ✅ the availability's DECOUPLED
        ✅ the time's DECOUPLED
        ⚠ the DATA's consistency is EVENTUAL
```

*Look at: the caller's thread — in the sync's case it is blocked; in the async's
case it isn't. That one difference drives every's other's difference.*

### 4.2 The sync's chain vs the hybrid's

```text
  ❌ the SYNC's chain                          ✅ the HYBRID
  ┌──────┐  ┌───────┐  ┌────────┐  ┌──────┐  ┌──────┐
  │USER  │─▶│CASH   │─▶│PAYMENT │─▶│EMAIL │  │USER  │─▶│CASH   │─▶│PAYMENT│
  │      │  │80ms   │  │450ms   │  │300ms │  │      │  │80ms   │  │450ms   │
  └──────┘  └───────┘  └────────┘  └──────┘  └──────┘  └───────┘  └────────┘
                                    │                            │
                              1040ms total                 532ms total
                                                       + OrderPlaced ──▶ email/accounting
```

*Look at: the two calls that left the request's path (the email, the accounting)
are exactly the two the user never waited for anyway.*

### 4.3 The event's envelope's anatomy

```text
  ┌──────────────────────────────────────────────────────────────────┐
  │  the ENVELOPE's (the transport's metadata — the SAME for all)   │
  │    eventId · eventType · eventVersion · occurredAt                │
  │    correlationId · causationId · producer · tenantId             │
  ├──────────────────────────────────────────────────────────────────┤
  │  the DATA's (the payload — the business's meaning)               │
  │    { "orderId": "...", "total": {...}, "items": [...] }          │
  └──────────────────────────────────────────────────────────────────┘
     ▲ the envelope's changes → the CONSUMERS' routing's logic
     ▲ the data's changes → the CONSUMERS' business's logic
     ⚠ they evolve at DIFFERENT's rates — that's why they're separate
```

*Look at: the envelope is infrastructure; the data is the domain. Mixing them
means every's payload's change forces a re-deploy of every's consumer's routing.*

### 4.4 The version's evolution's timeline

```text
  time ──────────────────────────────────────────────────────────▶

  the SCHEMA's v1:  { orderId, customerId, total }
                    ▲
                    ├── the PRODUCER adds `discount` (the optional's field)
  the SCHEMA's v2:  { orderId, customerId, total, discount? }
                    ▲
                    ├── the PRODUCER starts POPULATING it
                    ├── the CONSUMER's v1's code: ignores the unknown's field ✅
                    ├── the CONSUMER's v2's code: reads it ✅
                    │
                    ├── the wait: the full's consumer's deploy's cycle (90 days?)
                    │   + the monitoring's proof: the usage = 0
                    ▼
  the SCHEMA's v3:  { orderId, customerId, total }   ← the field's REMOVED
                    ⚠ ONLY after the telemetry's proof
```

*Look at: the removal is the only dangerous step, and it's the one that must wait
for the telemetry.*

---

## 5. Worked Examples

### 5.1 The checkout's flow (the dry run)

```text
  POST /api/orders
    Idempotency-Key: 8f14e45f-ceea-467a-9a2b-1d3f4a5b6c7d
    Content-Type: application/json
    { "customerId": "cus-1", "items": [{"skuId":"SKU-1","quantity":2}] }

  ┌── the ORDERS's service ──────────────────────────────────────────┐
  │ 1. the IDEMPOTENCY's claim                                        │
  │    INSERT INTO idempotency (key, fingerprint, status)             │
  │    → 201 (the first) → continue                                  │
  │    → duplicate → SELECT the stored's → return the SAME's response  │
  │                                                                    │
  │ 2. the CATALOGUE's call (the sync — the price's needed)           │
  │    GET /api/products/SKU-1  (the 150ms timeout)                   │
  │    → 200 { "skuId":"SKU-1", "priceCents":6499, "available":true } │
  │                                                                    │
  │ 3. the INVENTORY's call (the sync — the reserve's needed)          │
  │    POST /api/reservations  (the 250ms timeout)                    │
  │    → 200 { "reservationId":"res-9", "expiresAt":"..." }           │
  │                                                                    │
  │ 4. the PAYMENT's call (the sync — the money's needed)             │
  │    POST /api/payments  (the 2s timeout)                           │
  │    → 200 { "paymentId":"pay-3", "status":"CAPTURED" }             │
  │                                                                    │
  │ 5. the ORDER's insert + the OUTBOX's event  (ONE transaction)      │
  │    INSERT INTO orders (...)                                       │
  │    INSERT INTO outbox (event_id, type, payload)  ← the SAME's tx   │
  │    COMMIT                                                        │
  │                                                                    │
  │ 6. the RESPONSE                                                   │
  │    201 { "orderId":"ord-1", "status":"CONFIRMED" }                │
  │    Location: /api/orders/ord-1                                   │
  └────────────────────────────────────────────────────────────────────┘
                 │
                 ▼  (the outbox's relay publishes it)
  ┌── the KAFKA's topic: orders.v1 ──────────────────────────────────┐
  │  OrderPlaced { orderId, customerId, total, items }               │
  │  ├──▶ the NOTIFICATION's service  → the email                    │
  │  ├──▶ the ACCOUNTING's service    → the ledger's entry            │
  │  ├──▶ the SEARCH's service        → the index                    │
  │  └──▶ the ANALYTICS's service     → the warehouse                │
  └────────────────────────────────────────────────────────────────────┘

  ⚠ the OUTBOX's pattern: the order and the event are committed in ONE
    transaction, so the event CANNOT be lost. The relay's publisher
    reads the outbox and publishes to Kafka. If the app crashes after
    the DB's commit but before the Kafka's publish, the relay's poll
    picks it up. (The duplicate's risk remains → the consumers are
    idempotent.)
```

### 5.2 The retry's storm (the dry run)

```text
  the CATALOGUE's service goes down at 14:00:00

  14:00:00.000  the ORDERS's service starts timing out
  14:00:00.100  50 requests in flight, all timing out after 150ms
  14:00:00.150  50 requests fail → the CLIENTs retry IMMEDIATELY
                → 50 more requests arrive → the catalogue's thread's pool
                  is now FULL of the retrying's requests
  14:00:00.200  the catalogue's pool is exhausted → it can't even accept
                new's connections → the 1st's wave's callers ALSO fail
                (⚠ the failure's spreads to the healthy's callers)
  14:00:01.000  200 requests/second hitting a service that's down

  ❌ WITHOUT the jitter: all the clients retry at 150ms, 300ms, 600ms...
     → the traffic's shape is a SPIKE, not a wave

  ✅ WITH the jitter: full_jitter = random(0, backoff)
     → the retries's spread is SMOOTH → the recovering's service sees a
       RAMP instead of a SPIKE
```

*Look at: the retry without the jitter converts a 1× problem into a 50×
problem, and the exhausted's pool spreads the failure from the down's
dependency to the healthy's callers.*

### 5.3 The contract's break (the dry run)

```text
  the ORDERS's service deploys v2.1 at 10:00, changing the Order's DTO:

    v1: { "id": "ord-1", "total": 129.99 }
    v2: { "id": "ord-1", "total": { "amount": 12999, "currency": "USD" } }
         ⚠ the `total`'s TYPE changed

  10:00 the ORDERS's service rolls out (the 3 of 12's pods are updated)
  10:01 the REPORTING's service (not yet updated) receives the new's shape
  10:01 the REPORTING's service does:
          BigDecimal total = order.get("total");   // ⚠ ClassCastException
  10:01 → 500s → the alerting fires → the on-call's pager goes off

  THE FIX (the three's options):
  ① the EXPAND's & CONTRACT's:
     v2: { "id": "ord-1", "total": 129.99, "totalMoney": {"amount":12999} }
     the consumer's code reads `total` → still works ✅
     the producer's code writes BOTH → the old's consumers are happy
     ...later, after the telemetry's proof... remove `total`

  ② the CONTRACT's TEST (the Pact):
     the REPORTING's service's contract says "the total is a number"
     the ORDERS's service's build fails the contract's verification ✅
     → the break NEVER reaches production

  ③ the SCHEMA's REGISTRY's compatibility's check:
     the schema's registry rejects the incompatible's change at the
     publish's time ✅
```

*Look at: the break happened because a **field's type** changed. The rule is:
**never change a field's type or meaning — add a new one.***

---

## 6. Code from Scratch — Production-Grade

### 6.1 The typed's REST's client (the Spring's `RestClient`)

```java
package com.example.orders.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

/**
 * The TYPED's HTTP's CLIENT — the production's shape.
 * ⚠ Every's production's concern is ENCODED here:
 *   · the timeouts (the connect + the read) — the default's is infinite!
 *   · the error's mapping (the 4xx vs the 5xx)
 *   · the correlation's id's propagation
 *   · the metrics's instrumentation
 */
@Component
@Slf4j
public class CatalogueClient {

    private final RestClient restClient;

    public CatalogueClient(RestClient.Builder builder) {
        ClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // ⚠ CRITICAL: the default's timeouts are INFINITE. Without these,
        //   a hung's downstream will hold the thread FOREVER and the
        //   whole's service will grind to a halt.
        factory.setConnectTimeout(Duration.ofSeconds(2));   // the TCP + TLS's handshake
        factory.setReadTimeout(Duration.ofSeconds(3));      // the server's think's time

        this.restClient = builder
                .baseUrl("http://catalogue-service")
                .requestFactory(factory)
                .defaultHeader("Accept", "application/json")
                .build();
    }

    public Product getProduct(String skuId) {
        try {
            return restClient.get()
                    .uri("/api/products/{skuId}", skuId)
                    .retrieve()
                    .body(Product.class);
        } catch (RestClientResponseException e) {
            // the 4xx = the CLIENT's fault → do NOT retry
            if (e.getStatusCode().is4xxClientError()) {
                throw new CatalogueClientException(
                        "The catalogue's rejected the request: " + e.getStatusCode(), e);
            }
            // the 5xx = the DEPENDENCY's fault → the caller may retry
            throw new CatalogueUnavailableException(
                    "The catalogue's service is unavailable", e);
        } catch (ResourceAccessException e) {
            // the TIMEOUT / the connection's failure → the retry's candidate
            throw new CatalogueUnavailableException(
                    "The catalogue's service did not respond in time", e);
        }
    }

    // the DTO — ⚠ NOT the JPA's entity! the contract's shape is SEPARATE
    // from the persistence's shape.
    public record Product(String skuId, String name, long priceCents, boolean available) {}
}
```

### 6.2 The correlation's id's propagation (the interceptor)

```java
package com.example.orders.client;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * The CORRELATION's ID's PROPAGATION — the distributed's tracing's glue.
 * ⚠ The header's name must be the SAME across every's service — it's a
 *   CROSS-TEAM's CONVENTION, which is why it belongs in the platform's
 *   template, not in each's service.
 */
@Component
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body,
                                         ClientHttpRequestExecution execution) throws IOException {
        // the INCOMING's id (from the MDC — set by the inbound's filter)
        // or a NEW one. The id is PRESERVED so the whole's journey is joinable.
        String correlationId = MDC.get(MDC_KEY);
        if (correlationId == null) {
            correlationId = UUID.randomUUID().toString();
            MDC.put(MDC_KEY, correlationId);
        }

        return execution.execute(request, body, (req, res) -> {
            // ⚠ the id must also go on the RESPONSE — so the CLIENT can
            //   quote it in a support's ticket
            res.getHeaders().add(HEADER, correlationId);
            return res;
        });
    }
}
```

```java
package com.example.orders.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * The INBOUND's correlation's id — the entry's point of the chain.
 * ⚠ The @Order(HIGHEST_PRECEDENCE) so it runs BEFORE the logging's
 *   filter (the MDC must be populated before anything logs).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put("correlationId", correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // ⚠ ALWAYS clear the MDC — the thread's pool is reused, and a
            //   leaked's id would CORRELATE two unrelated's requests!
            MDC.remove("correlationId");
        }
    }
}
```

### 6.3 The gRPC's client and the server (the real's Spring's APIs)

```java
package com.example.orders.grpc;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.Metadata;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * The gRPC's CHANNEL — the connection's pool to ONE's target.
 * ⚠ A channel is expensive (the HTTP/2's connection + the multiplexing).
 *   Create ONE per's target and REUSE it — never per-request.
 */
@Slf4j
public class GrpcChannelFactory {

    /**
     * The channel's options:
     * · keepAlive — detects the dead's peers (the NAT's timeouts, the LB's
     *   idle's timeouts) which otherwise show up as a HUNG's request
     * · maxInboundMessageSize — the 4MB default can reject a big's payload
     */
    public ManagedChannel create(String host, int port) {
        return ManagedChannelBuilder.forAddress(host, port)
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveWithoutCalls(false)
                .maxInboundMessageSize(16 * 1024 * 1024)
                .build();
    }

    /**
     * The METADATA's propagation — the correlation's id over gRPC.
     * ⚠ The metadata's key must be lowercase (the HTTP/2's requirement).
     */
    public static Metadata withCorrelationId(String correlationId) {
        Metadata metadata = new Metadata();
        metadata.put(Metadata.Key.of("x-correlation-id", Metadata.ASCII_STRING_MARSHALLER),
                correlationId);
        return metadata;
    }
}
```

```java
package com.example.orders.grpc;

import com.example.catalogue.v1.CatalogueServiceGrpc;
import com.example.catalogue.v1.GetProductRequest;
import com.example.catalogue.v1.Product;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class CatalogueGrpcClient {

    private final CatalogueServiceGrpc.CatalogueServiceBlockingStub stub;

    /**
     * The DEADLINE is the timeout's BUDGET.
     * ⚠ The gRPC's deadline is PROACTIVE: the client cancels at the deadline
     *   AND the server is TOLD to stop (the context's cancellation) — unlike
     *   the HTTP's timeout, which just abandons the socket.
     */
    public Product getProduct(String skuId, String correlationId) {
        try {
            return stub
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(
                            GrpcChannelFactory.withCorrelationId(correlationId)))
                    .withDeadlineAfter(2, TimeUnit.SECONDS)
                    .getProduct(GetProductRequest.newBuilder().setSkuId(skuId).build());
        } catch (StatusRuntimeException e) {
            // the UNAVAILABLE = the transient's failure → the retry's candidate
            // the DEADLINE_EXCEEDED = the timeout
            // the INVALID_ARGUMENT = the client's fault → no retry
            if (e.getStatus().getCode() == io.grpc.Status.Code.UNAVAILABLE) {
                throw new CatalogueUnavailableException("The catalogue's unavailable", e);
            }
            throw new CatalogueClientException("The catalogue's call failed: " + e.getStatus(), e);
        }
    }
}
```

```protobuf
// the CONTRACT's definition — the .proto file
syntax = "proto3";

package com.example.catalogue.v1;

option java_multiple_files = true;
option java_package = "com.example.catalogue.v1";
option java_outer_classname = "CatalogueProto";

service CatalogueService {
  // the unary's call
  rpc GetProduct(GetProductRequest) returns (Product);

  // the server's streaming — the large's result's pages
  rpc ListProducts(ListProductsRequest) returns (stream Product);

  // the health's checking — the standard's protocol
  // (implemented by the grpc's health's service, auto-registered)
}

message GetProductRequest {
  string sku_id = 1;
}

message Product {
  // ⚠ the field's NUMBERS are the contract. Never reuse a number.
  // ⚠ Never change a field's type — add a new's number instead.
  string sku_id   = 1;
  string name     = 2;
  int64  price_cents = 3;
  bool   available = 4;
  string currency = 5;          // the NEW's field — additive ✅
}

message ListProductsRequest {
  int32 page_size = 1;
  string page_token = 2;        // the keyset's pagination
}
```

### 6.4 The event's publisher (the outbox's pattern)

```java
package com.example.orders.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * The OUTBOX's PATTERN — the reliable's event's publishing.
 *
 * The PROBLEM it solves:
 *   if you publish to Kafka INSIDE a @Transactional method, you have
 *   TWO resources with NO shared's transaction:
 *     · the Kafka's publish succeeds, the DB's transaction rolls back
 *       → the consumers' act on an event for a thing that never's existed
 *     · the DB's transaction commits, the Kafka's publish fails
 *       → the event is LOST FOREVER
 *
 * The SOLUTION: write the event to an OUTBOX's TABLE in the SAME's
 * transaction as the business's data, then a RELAY publishes it to Kafka
 * separately. The relay's poll → publish → mark's loop is AT-LEAST-ONCE,
 * so the consumers MUST be idempotent.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {

    private final OutboxRepository outboxRepository;

    /**
     * ⚠ MUST be called from within the SAME transaction as the business's write.
     *   The @Transactional here is a no-op if the caller already has one
     *   (the default's propagation is REQUIRED).
     */
    @Transactional
    public void publish(String eventType, int eventVersion, Object payload) {
        OutboxEvent event = OutboxEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .eventType(eventType)
                .eventVersion(eventVersion)
                .payload(JsonSerializer.serialize(payload))
                .occurredAt(Instant.now())
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .build();
        outboxRepository.save(event);
    }
}
```

```java
package com.example.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The OUTBOX's RELAY — the publisher's loop.
 * ⚠ It is a SEPARATE's process (or a separate's deployment) in production —
 *   running it in the same's app means the app's restart's stalls the relay.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * The POLLING's loop.
     * ⚠ Use the SKIP-LOCKED's query so multiple's relay's instances don't
     *   publish the same's event twice (the duplicates are tolerated by the
     *   idempotent's consumers, but the fewer's the better).
     */
    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void relay() {
        var events = outboxRepository.findPendingForUpdate(
                PageRequest.of(0, 100));   // the SKIP LOCKED

        for (OutboxEvent event : events) {
            try {
                // the KEY = the AGGREGATE's id → the Kafka's partition's ordering
                // ⚠ the ordering's guarantee is per-partition, so the key's
                //   choice IS the ordering's design.
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);   // ⚠ the synchronous's send so the
                                                     //   failure is caught HERE
                event.markPublished();
                outboxRepository.save(event);
            } catch (Exception e) {
                event.markFailed(e.getMessage());
                outboxRepository.save(event);
                log.warn("The outbox's publish's failed for {}", event.getEventId(), e);
            }
        }
    }
}
```

```java
package com.example.orders.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The EVENT's SERIALIZER — the shared's format.
 * ⚠ The version is IN the type's name (com.example.orders.v1.OrderPlaced),
 *   so the versioned's schema can live side by side.
 */
@Component
@Slf4j
public class EventSerializer {

    private final ObjectMapper objectMapper;

    public EventSerializer() {
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * The ENVELOPE's wrapping — the metadata's transport + the payload.
     */
    public String serialize(EventEnvelope<?> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new EventSerializationException("Could not serialize " + envelope.getEventType(), e);
        }
    }
}
```

### 6.5 The idempotent's consumer

```java
package com.example.orders.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The IDEMPOTENT's CONSUMER — the at-least-once's handling.
 *
 * ⚠ The delivery is AT-LEAST-ONCE, so the SAME's event will arrive twice
 *   (the retry, the relay's redelivery, the rebalance's replay).
 *   The consumer MUST be idempotent.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OrderPlacedConsumer {

    private final ProcessedEventRepository processedRepository;
    private final OrderService orderService;

    @KafkaListener(topics = "orders.v1", groupId = "inventory-service")
    @Transactional
    public void onOrderPlaced(String rawEvent) {
        EventEnvelope<OrderPlacedEvent> envelope =
                EventSerializer.deserialize(rawEvent, OrderPlacedEvent.class);

        // ① the IDEMPOTENCY's GUARD — the atomic's check
        try {
            processedRepository.saveAndFlush(ProcessedEvent.builder()
                    .eventId(envelope.getEventId())
                    .eventType(envelope.getEventType())
                    .processedAt(java.time.Instant.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // the duplicate's key → we already's processed this → ACK and move on
            log.debug("The event's already's processed: {}", envelope.getEventId());
            return;
        }

        // ② the BUSINESS's logic — in the SAME's transaction as the guard
        orderService.reserveStock(envelope.getData());
    }
}
```

### 6.6 The Problem's Details (the error's contract)

```java
package com.example.orders.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The UNIFIED's ERROR's CONTRACT — the RFC 9457's Problem's Details.
 * ⚠ The HTTP's status carries the CLASS; the body carries the DETAIL.
 *   NEVER return 200 with an error's body — it breaks the proxies, the
 *   caches, the monitoring's dashboards, and every's client's retry's logic.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * The VALIDATION's failure → 422 with the field's errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        pd.setTitle("The request's validation's failed");
        pd.setType(URI.create("https://api.example.com/problems/validation-failed"));

        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of(
                        "field", fe.getField(),
                        "message", fe.getDefaultMessage() == null ? "is invalid" : fe.getDefaultMessage(),
                        "rejectedValue", String.valueOf(fe.getRejectedValue())))
                .collect(Collectors.toList());
        pd.setProperty("errors", errors);
        return pd;
    }

    /**
     * The NOT's FOUND → 404 with the identifier's context.
     */
    @ExceptionHandler(OrderNotFoundException.class)
    ProblemDetail handleNotFound(OrderNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("The order's not found");
        pd.setType(URI.create("https://api.example.com/problems/order-not-found"));
        pd.setProperty("orderId", ex.getOrderId());
        pd.setProperty("traceId", org.slf4j.MDC.get("correlationId"));
        return pd;
    }

    /**
     * The DOWNSTREAM's FAILURE → 503 with a RETRY's hint.
     * ⚠ The 5xx means "the server's problem, a retry might help".
     */
    @ExceptionHandler(DownstreamUnavailableException.class)
    ProblemDetail handleDownstream(DownstreamUnavailableException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        pd.setTitle("A downstream's service's unavailable");
        pd.setType(URI.create("https://api.example.com/problems/downstream-unavailable"));
        pd.setProperty("retryable", true);
        pd.setProperty("dependency", ex.getDependency());
        return pd;
    }

    /**
     * The CATCH-ALL → 500. ⚠ Never leak the stack's trace or the internal's
     *   message to the client — it's an information's disclosure.
     */
    @ExceptionHandler(Exception.class)
    ProblemDetail handleUnexpected(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        pd.setTitle("An unexpected's error's occurred");
        pd.setType(URI.create("https://api.example.com/problems/internal-error"));
        pd.setProperty("traceId", org.slf4j.MDC.get("correlationId"));
        pd.setProperty("timestamp", Instant.now().toString());
        // ⚠ the detail is generic; the REAL's error's logged with the trace's id
        return pd;
    }
}
```

### 6.7 The contract's test (the Pact, the consumer's driven's contracts)

```java
package com.example.reporting.contract;

import au.com.dius.pact.consumer.dsl.PactDslWithProvider;
import au.com.dius.pact.core.model.RequestResponsePact;
import au.com.dius.pact.core.model.annotations.Pact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import au.com.dius.pact.provider.junit5.PactVerificationSpringProvider;
import au.com.dius.pact.core.model.PactDslJsonBody;

import java.util.Map;

/**
 * The CONSUMER's driven's CONTRACT.
 * ⚠ The consumer (the reporting's service) DECLARES what it expects.
 *   The producer's (the orders's service) pipeline VERIFIES against it.
 *   → the breaking's change is caught at the producer's BUILD's time,
 *     BEFORE the deploy, and the producer's team is told exactly what broke.
 */
@ExtendWith(PactVerificationSpringProvider.class)
@PactTestFor(providerName = "orders-service")
class OrdersContractTest {

    /**
     * The TOTAL's field — ⚠ the type is the CONTRACT. If the producer
     * changes `total` from a number to an object, this test FAILS and
     * the producer's build breaks. That's the whole's point.
     */
    @Pact(consumer = "reporting-service")
    public RequestResponsePact orderTotal(PactDslWithProvider builder) {
        return builder
                .given("an order's exists with the total of 129.99")
                .uponReceiving("a request for the order")
                .path("/api/orders/ord-1")
                .method("GET")
                .headers(Map.of("Accept", "application/json"))
                .willRespondWith()
                .status(200)
                .headers(Map.of("Content-Type", "application/json"))
                .body(new PactDslJsonBody()
                        .stringType("id", "ord-1")
                        .stringType("customerId", "cus-1")
                        .stringType("status", "CONFIRMED")
                        .decimalType("total", 129.99)     // ⚠ the type's the contract
                        .stringType("currency", "USD"))
                .toPact();
    }

    /**
     * The NEW's field's addition — the backward's compatibility's proof.
     * ⚠ If the producer REMOVES `total` or changes its type, this fails.
     */
    @Pact(consumer = "reporting-service")
    public RequestResponsePact orderWithDiscount(PactDslWithProvider builder) {
        return builder
                .given("an order's exists with a discount")
                .uponReceiving("a request for the order")
                .path("/api/orders/ord-2")
                .method("GET")
                .willRespondWith()
                .status(200)
                .body(new PactDslJsonBody()
                        .stringType("id", "ord-2")
                        .decimalType("total", 99.99)
                        .decimalType("discount", 10.00))   // the optional's field
                .toPact();
    }
}
```

### 6.8 The idempotency's filter (the server's side)

```java
package com.example.orders.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;

/**
 * The SERVER's IDEMPOTENCY's IMPLEMENTATION.
 * ⚠ The filter's approach: intercept the request, claim the key, and either
 *   pass through (the first's time) or return the STORED's response.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdempotencyFilter extends OncePerRequestFilter {

    private static final String HEADER = "Idempotency-Key";

    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // only the WRITES need it
        return !("POST".equals(request.getMethod()) || "PUT".equals(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String key = request.getHeader(HEADER);
        if (key == null || key.isBlank()) {
            // no key → the operation is not idempotent's protected → pass through
            filterChain.doFilter(request, response);
            return;
        }

        String fingerprint = fingerprint(request);
        Optional<IdempotencyRecord> existing = idempotencyService.tryClaim(key, fingerprint, currentUserId());

        if (existing.isPresent()) {
            IdempotencyRecord record = existing.get();

            // the SAME's key + a DIFFERENT's payload → the client's bug
            if (!record.getRequestFingerprint().equals(fingerprint)) {
                response.setStatus(HttpStatus.UNPROCESSABLE_ENTITY.value());
                response.setContentType("application/problem+json");
                response.getWriter().write("""
                        {"title":"The idempotency's key's already's used with a different's payload",
                         "status":422,"type":"https://api.example.com/problems/idempotency-key-reuse"}""");
                return;
            }

            // the IN-FLIGHT's duplicate → the client should retry later
            if (record.getStatus() == IdempotencyStatus.IN_PROGRESS) {
                response.setStatus(HttpStatus.CONFLICT.value());
                response.setHeader("Retry-After", "1");
                return;
            }

            // the COMPLETED's duplicate → return the STORED's response VERBATIM
            response.setStatus(record.getResponseStatus());
            response.setContentType("application/json");
            response.getWriter().write(record.getResponseBody());
            return;
        }

        // the FIRST's time → pass through, and CAPTURE the response for the store
        ResponseCaptureWrapper wrapper = new ResponseCaptureWrapper(response);
        filterChain.doFilter(request, wrapper);
        idempotencyService.complete(key, wrapper.getStatus(), wrapper.getBody());
    }

    /**
     * The FINGERPRINT — the body's hash.
     * ⚠ Hash the RAW's body (read it once, then re-expose it), not the
     *   parsed's object — the parse's order and the number's formats would
     *   produce different's hashes for the same's logical's request.
     */
    private String fingerprint(HttpServletRequest request) throws IOException {
        byte[] body = request.getInputStream().readAllBytes();
        // re-expose the body for the downstream's filter
        request = new HttpServletRequestWrapper(request) {
            @Override public jakarta.servlet.ServletInputStream getInputStream() {
                return new jakarta.servlet.ServletInputStream() {
                    private final java.io.ByteArrayInputStream delegate =
                            new java.io.ByteArrayInputStream(body);
                    @Override public int read() { return delegate.read(); }
                    @Override public boolean isFinished() { return delegate.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(jakarta.servlet.ReadListener l) { }
                };
            }
        };
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(request.getMethod().getBytes(StandardCharsets.UTF_8));
        digest.update(request.getRequestURI().getBytes(StandardCharsets.UTF_8));
        digest.update(body);
        return HexFormat.of().formatHex(digest.digest());
    }
}
```

---

## 7. Common Pitfalls & Gotchas

| # | ❌ Wrong | ✅ Right | Why |
|---|----------|----------|-----|
| 1 | A timeout's default (infinite) on the HTTP's client | Always set the connect + the read's timeouts | A hung's downstream holds the thread forever |
| 2 | No `Idempotency-Key` on the writes | The key + the server's store + the unique's constraint | The "the success that's lost" → the duplicates |
| 3 | The error's envelope with a `200` | The HTTP's status + the Problem's Details | The proxies, the caches, the monitoring's tools break |
| 4 | The stack's trace in the error's response | A generic's detail + a `traceId` | The information's disclosure |
| 5 | Changing a field's type in the contract | Add a new's field, deprecate the old | The consumers' break |
| 6 | Removing a field without the telemetry's proof | The expand/contract's lifecycle | The consumers' deploy's lag is unbounded |
| 7 | The offset's pagination on a big's table | The keyset's (cursor's) pagination | The skipped/duplicated's rows, the slow's deep's pages |
| 8 | `ORDER BY` without a unique's tie-breaker | Add the `id` to the `ORDER BY` | The keyset's pagination skips/duplicates on the ties |
| 9 | Publishing to Kafka inside the `@Transactional` | The outbox's pattern | The lost's events, the phantom's events |
| 10 | The consumer's logic without the idempotency's guard | The `eventId`'s unique's constraint | The at-least-once's delivery duplicates |
| 11 | The retry without the jitter | The full's jitter | The thundering herd re-creates the outage |
| 12 | The retry on a `4xx` | Only on the `5xx`/`429`/the timeouts | The pointless's retries, the account's lockout |
| 13 | The unbounded's retry's budget | The retry's budget < the caller's remaining's deadline | The deadline's violation upstream |
| 14 | The chatty's chain in the request's path | The events for the non-interactive's paths | The latency's tax, the availability's coupling |
| 15 | The N+1 in the GraphQL's resolvers | The `DataLoader` | The N queries per request |
| 16 | The `grpc` channel per request | One per target, reused | The HTTP/2's connection is expensive |
| 17 | The protobuf's field's number reused | Never reuse a number | The silent's data's corruption |
| 18 | The MDC's id not cleared in the filter | `MDC.remove()` in the `finally` | The thread's pool is reused — the wrong's correlation |
| 19 | The correlation's id only in the logs | The header + the MDC + the events + the response | The debuggability depends on the join's key |
| 20 | The `total` as a `double` in the money's DTOs | The minor's units (`long` cents) or a `BigDecimal` | The floating-point's rounding |

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner
**Q: How do microservices communicate with each other?**
**A:** Two main styles. **Synchronous**: the caller makes a request and waits — REST
over HTTP or gRPC. **Asynchronous**: the caller publishes an event to a broker and
returns; the consumer processes it later. The rule of thumb: if the user is
waiting for the result, use sync; if the work is a side effect (an email, a report,
an index update), use async.

**Q: What's the difference between REST and gRPC?**
**A:** REST over HTTP/JSON is human-readable, universally supported, and easy to
debug with curl. gRPC uses Protocol Buffers — a compact binary format with
generated client stubs, so both sides get compile-time type safety, and it supports
streaming natively. gRPC is usually chosen for internal service-to-service calls
where performance and type safety matter; REST for public APIs.

### 🟡 Intermediate
**Q: How do you handle a service call that times out?**
**A:** Set explicit timeouts (never rely on the default, which is infinite), and
make the caller's budget explicit — the total timeout must be less than the
caller's own deadline, and it must be propagated down the chain. Then decide the
failure mode: if the operation is idempotent (or has an idempotency key), retry
with an exponential backoff and full jitter, bounded by a retry budget. If it
isn't idempotent, don't retry — fail fast and let the caller decide. Wrap it in a
circuit breaker so a sustained outage fails fast instead of consuming the thread
pool, and a bulkhead so one dependency can't exhaust your resources.

**Q: How do you version an API?**
**A:** Prefer additive, backward-compatible evolution: add new optional fields with
defaults, never remove or rename existing ones, never change a type. Consumers
ignore unknown fields; producers must not assume consumers know new ones. For
breaking changes, run v1 and v2 side by side and migrate consumers, using usage
telemetry to know when a version is truly unused. For gRPC, the field *number* is
the contract — never reuse a number, and a renamed field is wire-compatible.

**Q: What is idempotency and why does it matter?**
**A:** An operation is idempotent if repeating it has the same effect as doing it
once. Over a network, "the server processed it but the response was lost" is
normal, so the client can't know whether to retry. The client sends an
`Idempotency-Key`; the server stores the key with the response and returns the
stored response on a repeat. The server-side claim must be atomic — a unique
constraint on the key, not a SELECT-then-INSERT, which races.

### 🔴 Advanced / Senior
**Q: How do you design the contract between two services so they can deploy
independently?**
**A:** Four things. (1) **The contract is data, not a remote method call** — a
versioned JSON schema or protobuf, with the error model standardised (RFC 9457
Problem Details). (2) **The evolution is additive and backward compatible** — the
consumer must tolerate unknown fields and the producer must not require new ones
to be understood. (3) **The contract is verified in CI** — the consumer's driven
contract tests (Pact) run against the producer in the producer's pipeline, so a
breaking change fails the producer's build rather than production. (4) **The
removal is telemetry-gated** — you only remove a deprecated field after the usage
metrics show zero usage for a full consumer deploy cycle plus a buffer. For
events, the same applies, plus the schema registry's compatibility check and the
version in the event type name.

**Q: How do you decide sync vs async for a given interaction?**
**A:** Three questions. (1) Does the user wait for the result? If not, async. (2) Is
the callee's failure fatal to the caller's correctness? If the payment authorisation
fails, the checkout must fail — so that's sync with a strict timeout. If the
indexing fails, it isn't — async. (3) How many consumers? One consumer and a
need for the answer → sync; many consumers and a broadcast → event. And the
subtle fourth: even when the user *does* wait, ask whether the *immediate* response
needs the downstream's result or only the *eventual* one. "Order received, payment
processing" is often better than blocking on a slow payment gateway. The rule:
the user-facing request path should touch 1-3 services; everything else is a
background concern with an idempotent consumer.

**Q: A consumer is processing the same event twice and creating duplicate
orders. How do you fix it?**
**A:** First, the consumers must be idempotent — the delivery guarantee is
at-least-once, so a duplicate is expected, not exceptional. The standard fix is
a processed-events table with a unique constraint on the event ID, written in the
same transaction as the business effect. The duplicate insert fails the constraint,
the consumer catches it and acknowledges without reprocessing. If the duplication
is because the effect isn't naturally idempotent (e.g. "increment a counter"),
redesign it as a set operation or an upsert keyed by the event ID. Then check the
producer: if it's publishing directly rather than via the transactional outbox,
you can get duplicates from the publish-retry path too. The outbox plus idempotent
consumers gives you at-least-once end to end, which is the practical equivalent of
exactly-once.

### ⚫ Expert / Principal
**Q: "How do you guarantee exactly-once processing across a Kafka pipeline?"**
>
> **The honest answer: you don't, and the "exactly-once" claim is usually a
> category error.** The end-to-end exactly-once *illusion* is built from two
> at-least-once halves plus a deduplication layer: (1) The **producer's outbox**
> — the business write and the event write commit in one DB transaction, so the
> event can't be lost, and the relay publishes at-least-once (it may publish
> twice if it crashes after publishing but before marking the row). (2) The
> **consumer's idempotency** — a processed-events table with a unique constraint
> on the event ID, in the same transaction as the business effect. The consumer
> may receive a duplicate; the constraint makes the second one a no-op. (3) The
> **transactional producer** settings (`enable.idempotence=true`, `acks=all`,
> `max.in.flight.requests.per.connection=5`) give you idempotence *within* the
> producer's retries, and the **read_committed** isolation mode on the consumer
> side hides the aborted records. Kafka's own transactions give exactly-once
> *within* Kafka (consume-transform-produce atomically), but they don't extend
> across your database — and the moment you write to Postgres, you're back to
> at-least-once plus a dedup layer. So the honest framing: **"we deliver
> at-least-once end to end and make the consumers idempotent, which gives the
> same observable result as exactly-once with far less machinery."** The
> exceptions where true exactly-once matters are rare (money movement), and
> there the answer is usually an idempotency key at the API boundary plus a
> ledger with unique constraints, not a messaging guarantee. And the second
> part of the answer — which is what a principal-level candidate adds — is the
> **cost of the dedup layer**: the processed-events table grows (you need
> partitioning and TTL — and the TTL must exceed the maximum redelivery
> window, which for a DLQ-retry can be days), it adds a write per event
> (which is a real throughput cost at high rates), and it must be transactional
> with the business write, which means the idempotency guard and the business
> logic must share a transaction boundary — a design constraint that shows up
> in how you structure the consumer."

---

## 9. Counter-Questions & Traps

### CQ1. "Why not just use REST for everything? gRPC adds complexity."
- **Naive answer:** "gRPC is faster, so use it." ← the trap
- **The trap:** The interviewer wants you to weigh the **whole's trade**, not just
the latency.
- **Expert answer:** REST is the right default for anything user-facing or
publicly consumed — it's debuggable with `curl`, the tooling is universal, the
HTTP semantics (the status codes, the caching headers, the content negotiation)
are free, and the CDN/edge caching works. gRPC earns its keep on **internal,
high-volume, typed** calls where the payload's serialization and the network's
bytes matter, where you want **compile-time** client stubs, or where you need
**streaming** (a bidirectional stream has no clean REST equivalent). The honest
framing: "gRPC is a **bought** optimization — you pay for it in the
**tooling's** complexity (the debugging's proxies, the browser's support, the
load's balancing's config) and you get the **latency's** and **bytes'**
improvement. In a system where the p99 is dominated by the **database**, the gRPC's
switch buys you nothing — I'd measure first. The **hybrid** is common and
sane: REST at the edge (the public's API, the browser's clients) and gRPC
internally, with the **translation's layer** at the boundary."

### CQ2. "Should every inter-service call be retried?"
- **Naive answer:** "Yes, retries make it resilient." ← the trap
- **The trap:** The interviewer wants you to know that a retry can **duplicate**
side effects and can **amplify** an outage.
- **Expert answer:** Only for **transient** failures and only for **idempotent**
operations (or ones with an idempotency key). The failures worth retrying: a
connection reset, a `503`, a `429` (honouring `Retry-After`), a deadlock victim.
The failures never worth retrying: any `4xx` except `429` — a `400` will never
succeed, and retrying a `401` can lock an account. The mechanics: exponential
backoff with **full jitter** (without jitter, every client retries at the same
moments and re-creates the thundering herd), a **bounded** number of attempts, and
a **retry budget** — the total retry time must be less than the caller's remaining
deadline, and the budget should be propagated down the chain so a deep call
doesn't retry past the top's deadline. And the pairing: a retry without a
circuit breaker is dangerous, because when the dependency is truly down, every
caller holds a thread and a connection for the full backoff, exhausting the
pool — the retry without a breaker converts a slow failure into a fast,
total failure. The ordering: timeout first, then retry, then breaker, then
bulkhead.

### CQ3. "You said the error contract should use Problem Details. What about a unified
error envelope?"
- **Naive answer:** "A consistent envelope is simpler for the client." ← the trap
- **The trap:** The interviewer wants you to notice that the envelope usually comes
with **HTTP 200**, which is a serious's bug.
- **Expert answer:** The unified envelope is fine — as long as the HTTP status
still carries the class. The trap is the "envelope with 200 OK" pattern, which
breaks: the **load balancers** and **circuit breakers** can't see the failure, the
**caches** cache the error, the **monitoring** dashboards show 100% success while
the app is failing, the **client's retry libraries** won't retry, and the
**service meshes** report success. Problem Details is the standard's answer
because it does both: the status carries the class (4xx = the client's fault, don't
retry; 5xx = the server's fault, maybe retry) and the body carries the machine-
readable `type` (a stable URI the client branches on) plus the human-readable
`detail` and any structured properties. A client should branch on the `type` or
the status, never on parsing the `detail` string. And the team-wide rule worth
stating: **`4xx` = fix and don't retry; `5xx` = the server's problem, retry with
backoff.** A service that returns `500` for a validation failure will get a
retry storm from well-behaved clients.

### CQ4. "How do you handle a service that's slow but not down?"
- **Naive answer:** "Increase the timeout." ← the trap
- **The trap:** The interviewer wants you to distinguish the **slow** from the
**dead** — and to know that raising the timeout shrinks the whole's capacity.
- **Expert answer:** A slow dependency is often worse than a dead one, because a
dead one fails fast and a slow one consumes your threads and connections. The
first question: is the slowness in *your* service or *theirs*? The second: what
does the latency budget look like? A thread held for 30 seconds is 30 seconds of
your concurrency capacity gone — with a 200-thread pool and a 30s timeout, your
maximum throughput collapses to about 6 requests/second no matter how fast the
dependency usually is. The fixes, in order: (1) **Set a real timeout** — an
unbounded one is a thread leak; (2) **Find the actual bottleneck** — is it the
dependency's DB, its pool, its GC, or a lock? A p99 that's 10× the p50 usually
means contention or a cache miss storm; (3) **Add a circuit breaker** so the
sustained slowness fails fast; (4) **Use the hedging** — send a second request to
another replica after ~50% of your p99 budget has elapsed and take the first
response; the extra load is small and the tail improvement is large (the
"tail at scale" trick); (5) **Bulkhead** the dependency so its slowness can't
consume the threads other dependencies need; (6) **Cache or denormalise** so the
call isn't needed on the hot path at all. And the deadline discipline: the
timeout must be *derived* from the caller's budget, not chosen — "the remaining
deadline minus the estimated processing time", so a deep call chain doesn't
exceed the top-level deadline.

### CQ5. "Why not use GraphQL everywhere to avoid over-fetching?"
- **Naive answer:** "GraphQL is more flexible." ← the trap
- **The trap:** The interviewer wants the **cost** side — GraphQL moves complexity,
it doesn't remove it.
- **Expert answer:** GraphQL solves a real problem — many clients with different
needs over the same data — and it's genuinely the wrong answer for a simple CRUD
API, for a public API (edge caching is much easier with REST), for file uploads,
and for high-throughput writes. The costs that bite: the **N+1** problem (a nested
query becomes N queries unless you use `DataLoader` batching), the **query depth
and complexity** (a nested query can be an exponential blow-up — you need depth
limits and cost analysis, and you need to persist/allow-list queries, or you're
running an arbitrary-cost query engine in production), the **HTTP caching** becomes
much harder (every request is a POST to the same URL), the **schema's design** is
itself an API-design exercise with its own version's rules, and the **monitoring**
is harder (one endpoint, many operations — you need per-field metrics). The
interview's framing: "GraphQL is worth it when you have **multiple clients with
genuinely different shapes** over **one connected's domain** — a mobile app, a web
app, and a partner's API over the same's catalogue. If you have one client or a
stable shape, REST with a well-designed resource model is simpler and faster. And
whichever you pick, the **N+1** is the thing you must engineer against."

### CQ6. "How do you version an event's schema when consumers are external?"
- **Naive answer:** "Add a version field to the payload." ← the trap
- **The trap:** The interviewer wants you to know the **compatibility's rules** and
that a version field alone doesn't solve the problem.
- **Expert answer:** A version field in the payload is one option, but the
stronger one is the **version in the type's name** — `com.example.orders.v1.OrderPlaced`
vs `v2` — so both versions can exist simultaneously on the wire and the consumers
route on the type. The compatibility's rules are the same as for any schema:
additive only (new optional fields with defaults), never remove or rename, never
change a type, and consumers must ignore unknown fields. The removal is the
dangerous part: it must be telemetry-gated — you instrument the consumption of
each field/version, and you only remove when the usage is zero for a full consumer
deploy cycle plus a buffer. For external consumers you cannot deploy, that window
is long (a quarter or more) and it must be *contractual* — a published
deprecation policy with a date, communicated in advance, with the old version
still served until the date. A schema registry (the Confluent, the Apicurio) with
compatibility checks enforced in the producer's CI catches the accidental breaking
change; the *policy* catches the deliberate one. And the migration's tooling: a
version's translation at the boundary (the consumer adapts v1 to v2 internally) so
the producer can retire the old version one consumer at a time.

### CQ7. "You publish to Kafka inside a `@Transactional` method. What's wrong?"
- **Naive answer:** "Nothing, the transaction handles it." ← the trap
- **The trap:** The interviewer wants you to identify the **cross-resource's
transaction's problem**.
- **Expert answer:** The transaction covers the **database** only. Kafka is not
 enlisted in it, so you get two failure modes. (1) The **Kafka publish succeeds,
the DB rolls back** → the consumers act on an event for something that never
existed (the phantom event). (2) The **DB commits, the Kafka publish fails** (or
the app crashes between them) → the event is lost forever, and the consumer never
knows. The standard fix is the **transactional outbox**: write the event to an
outbox table in the *same* transaction as the business data, then a separate relay
process polls the outbox and publishes to Kafka, marking the row as published. The
event can no longer be lost (it's committed with the data) and the phantom is
eliminated. The remaining guarantee is **at-least-once** — the relay may publish
twice if it crashes after publishing and before marking — so the consumers must be
idempotent. The same pattern applies to any side effect in the transaction: the
email, the webhook, the cache invalidation. The general rule: **the transaction
should only contain writes to the same transactional store; everything else is
deferred to an outbox, a follow-up, or a retryable mechanism.**

### CQ8. "How do you test that a service's contract won't break?"
- **Naive answer:** "Integration tests in CI." ← the trap
- **The trap:** The interviewer wants the **consumer's driven** approach and the
reason the producer's-driven approach fails.
- **Expert answer:** Producer-driven contract tests (the consumer's code is the
fixture) are brittle and slow — they break when the consumer's implementation
changes even if the contract didn't, and they require running the consumer. The
**consumer-driven** approach (Pact) inverts it: the **consumer** declares what it
expects in a **pact file**, publishes that pact, and the **producer's** pipeline
runs the provider's verification against it — so the producer's build fails if
they've broken the contract, and the failure message tells them exactly what
changed. The practical requirements: the pact's file is a **versioned's artifact**
(checked into the provider's repo or published to a pact broker), the CI must run
the provider's verification, and the pact's broker's matrix shows which consumers
are affected by a change. The complementary tools: a **schema registry** with
compatibility checks for the event's contracts, and **consumer's driven** tests for
the events too (the Pact supports the message's pacts). And the honest's caveat:
contract tests verify the *shape*, not the *behaviour* — a provider can satisfy
the contract and still be wrong, so you still need the integration tests for the
critical's paths. The contract's tests are what stop the *shape's* breaks, which
are the majority of the cross-team's incidents.

### CQ9. "How do you handle a service that's being deprecated?"
- **Naive answer:** "Turn it off and hope no one's using it." ← the trap
- **The trap:** The interviewer wants the **migration's discipline**.
- **Expert answer:** A service's deprecation is an **API's deprecation** with
extra steps, and the "hope" approach fails at 3am. The process: (1) **Instrument
first** — you can't deprecate what you can't see, so the usage's telemetry
(per-endpoint, per-consumer) must exist *before* you start; (2) **Communicate
early and formally** — the deprecation's notice with the date, the migration's
guide, and the tracking's dashboard, sent to every known consumer; (3) **Provide
the migration's path** — a facade, a compatibility's shim, or a parallel's run, so
the consumers' migration is a config's change, not a rewrite; (4) **Track and
nag** — the usage's dashboard, the per-consumer's report, the automated's
notification when a deprecated endpoint is called (and *who* called it — that's
the last mile of the migration); (5) **Enforce the deadline** — the deprecated
endpoint returns a `Deprecation` and a `Sunset` header, and eventually starts
returning `410 Gone`; (6) **Decommission properly** — delete the code, the
database's tables, the infrastructure, the dashboards, the alerts, the
secrets. The step people skip is (6): the dead's code keeps confusing people,
keeps the security's attack's surface, and keeps the cost. And the "owner" is
key: a deprecation without a named owner and a date is a wish.

### CQ10. "Why not make every operation idempotent and skip the idempotency keys?"
- **Naive answer:** "Idempotent operations don't need keys." ← the trap
- **The trap:** The interviewer wants you to know that **idempotency's key** and
**idempotent's semantics** solve different problems.
- **Expert answer:** They solve different problems, and conflating them is the
mistake. **Idempotent semantics** mean "doing it twice is safe" — `PUT /orders/123`
with the same body, or `DELETE /orders/123`. That's a property of the *operation's
design*. **The idempotency key** solves the problem of a *non-idempotent* operation
(the create, the payment) being retried because the response was lost. Making
every operation idempotent isn't possible — the create is inherently "add one", and
the payment's capture genuinely charges money. The key is how you make the
non-idempotent's operation *behave* idempotently across the retry's boundary: the
server recognises the key, returns the *original* response rather than executing
again. The two's requirements for the key's mechanism: it must be **atomic** (the
unique's constraint, not a check-then-act), it must store the **response** (not
just "already done" — the client needs the same status and body), and it must
detect the **key's reuse with a different's payload** (a fingerprint of the
request's body; the same key with a different body is a client's bug → `422`).
And the scope: the key is scoped to the client and the endpoint, not global.

### CQ11. "How do you keep a chatty service-to-service chain from becoming a latency
problem?"
- **Naive answer:** "Make the calls parallel." ← the trap
- **The trap:** The interviewer wants you to know that **parallelism doesn't fix
the tail**.
- **Expert answer:** Parallelism helps the p50 but not the p99, and the chain's
problem is the p99. If each call has a p50 of 10ms and a p99 of 200ms, five
serialised calls have a p99 in the *seconds*, because the probability that at
least one call hits its tail compounds (1 - 0.99⁵ ≈ 5% of requests hit a slow
path in some call). Parallelising makes it 1 - 0.99 ≈ 1% instead of ~5% — better,
but still bad. The real fixes are structural: (1) **Remove the calls** — a
denormalised copy of what you need (the customer name in the order's service),
which is a data's duplication with a staleness's cost, not a network's hop;
(2) **Batch** — a `POST /api/products/batch` with 20 ids instead of 20 calls;
(3) **Move it off the path** — the events for anything the user doesn't wait for;
(4) **Collapse the services** — if two services are always called together,
always change together, and always fail together, they're one service (the
over-splitting's fix); (5) **Cache** the enrichment with a short TTL, accepting
the eventual's consistency. And the latency's budget: give each hop a slice of
the user's deadline, so a chain that can't fit the budget fails fast instead of
timing out at the edge. The structural point: the number of network hops in a
user's request path is a **design's metric** — track it (the trace's depth) and
set a budget on it.

### CQ12. "How do you handle a contract's change where you can't change the producer?"
- **Naive answer:** "Ask the provider to fix it." ← the trap
- **The trap:** The interviewer wants the **consumer's side's** options — because
sometimes the provider is another team, or a third party.
- **Expert answer:** When you can't change the provider, the consumer's side has
four moves. (1) **The adapter/anti-corruption's layer** — wrap the provider's
response in your own domain's model, so the provider's shape is contained in one
class and the rest of your code is insulated. This is the most important one and
it's also the cheapest. (2) **The version's negotiation** — ask for a specific
version via a header or a path (`/v2/...`), and use the version you can handle;
providers that support the expand/contract pattern will have one. (3) **The
feature's flag** — detect the new shape at runtime and handle both (a
Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)` plus a tolerant's
reader), so you can deploy before the provider's change and survive both. (4)
**The shadow's read** — run the new's shape in parallel and compare the outputs
before you switch. The rule: **never let the provider's shape leak past your
boundary** — one adapter class, one place to fix, one place to test. And the
process: the provider's change's notice should come through a contract's
notification (the pact's broker, the schema's registry, the changelog with a
date), and the consumer's CI should run a **contract's verification** that fails
when the provider's shape drifts — so you find out in the build, not in
production.

---

## 10. Rapid-Fire MCQs

**1.** The default's timeout of the Spring's `RestClient`:
A) 5 seconds  B) 30 seconds  C) Infinite  D) 1 second
**✅ C** — you must set it explicitly

**2.** The idempotency's key's scope:
A) Global  B) Per client + per endpoint  C) Per session  D) Per day
**✅ B**

**3.** The protobuf's field's identity is:
A) The name  B) The number  C) The type  D) The order
**✅ B**

**4.** The HTTP's status for a validation's failure:
A) 400  B) 422  C) 500  D) 200
**✅ B** (400 is for a malformed's request; 422 for semantically's invalid)

**5.** The Kafka's ordering's guarantee is:
A) Global  B) Per topic  C) Per partition  D) Per consumer
**✅ C**

**6.** The "exactly-once" is achievable:
A) End to end across a DB  B) Within Kafka only  C) With the idempotent's consumers  D) Never
**✅ D** — end-to-end requires the outbox + the idempotent's consumers

**7.** The keyset's pagination's requirement:
A) A unique's tie-breaker in the `ORDER BY`  B) The `OFFSET`  C) A `COUNT(*)`  D) The `JOIN`
**✅ A**

**8.** The outbox's pattern's guarantee:
A) Exactly-once  B) At-least-once  C) At-most-once  D) None
**✅ B**

**9.** The `X-Correlation-Id` must be propagated through:
A) The logs only  B) The logs, the calls, the events, the response  C) The response only  D) The DB
**✅ B**

**10.** The gRPC's `DEADLINE_EXCEEDED` maps to:
A) 400  B) 404  C) 504  D) 500
**✅ C**

**11.** The retry's jitter's purpose:
A) The randomness's test  B) The thundering herd's prevention  C) The encryption  D) The ordering
**✅ B**

**12.** The `4xx` vs `5xx` contract:
A) `4xx` = retry, `5xx` = don't  B) `4xx` = don't retry, `5xx` = maybe  C) Both retry  D) Neither
**✅ B**

**13.** The GraphQL's N+1's fix:
A) The `JOIN`  B) The `DataLoader`  C) The pagination  D) The caching
**✅ B**

**14.** The `Sunset` header's purpose:
A) The caching  B) The deprecation's notice  C) The auth  D) The routing
**✅ B**

**15.** The key's removal from a contract's schema requires:
A) The version's bump  B) The telemetry's proof of zero's usage  C) The team's approval  D) The feature's flag
**✅ B**

---

## 11. Hands-On Exercises

**🧪 Lab 1 — The timeout's discipline**
1. Write a `RestClient` with **no** timeouts; call a deliberately slow endpoint.
2. Observe the request hanging; check the thread's dump (`jstack`) — the thread is
   parked.
3. Add the connect + read's timeouts; observe the failure at 3s.
4. Add a `Retry` with the full's jitter; observe the failed attempts' spread.

**🧪 Lab 2 — The idempotency's proof**
1. POST an order with an `Idempotency-Key`; get a `201`.
2. POST again with the same key and body → assert the **same** response body.
3. POST with the same key and a **different** body → assert the `422`.
4. Kill the server between the commit and the response → retry → assert one order.

**🧪 Lab 3 — The outbox's proof**
1. Publish an event inside a `@Transactional` method; force the Kafka's publish
   to fail.
2. Observe: the DB's transaction rolls back → the event is lost (the bug).
3. Move to the outbox; observe: the event's committed and the relay publishes it
   later.
4. Kill the relay between the publish and the mark → observe the duplicate → prove
   the consumer's idempotency absorbs it.

**🧪 Lab 4 — The contract's break**
1. Write a Pact's test on the consumer's side for the `total` as a number.
2. Change the producer's DTO to an object; run the provider's verification.
3. Observe: the producer's build fails with the exact's diff.

---

## 12. Code Challenges

### Challenge 1 — The resilient's HTTP's client (medium)
**Goal:** A production's `RestClient` wrapper with the timeouts, the retries with
the full's jitter, the circuit's breaker, the bulkhead, the correlation's id, and
the error's mapping — all configurable, all observable.
**Requirements:** the config's properties, the metrics, the tests (a WireMock
for the slow's/failing's responses).

### Challenge 2 — The event's contract's pipeline (hard)
**Goal:** The outbox's producer + the relay + the idempotent's consumer + the
schema's registry's compatibility's check, with the dead-letter's queue and the
replay's tooling.
**Requirements:** the outbox's schema, the relay's metrics (the lag, the
failures), the consumer's dedup's table, the replay's endpoint.

### Challenge 3 — The API's evolution's linter (hard)
**Goal:** A CI check that fails the build if a REST's DTO's field is **removed**,
**renamed**, or **retyped** without a deprecation's window — a schema's diff
against the last's release's OpenAPI's spec, enforcing the backward's
compatibility's rules.
**Requirements:** the OpenAPI's diff, the deprecation's annotation's processor, the
CI's integration.

---

## 13. Scenario-Based Questions

**S1.** *"Service A calls Service B. B's p99 latency is 2 seconds and A's timeout
is 5 seconds. A's p99 is 6 seconds — its clients are timing out. What do you do?"**
>
> **Ideal answer:** (1) **The framing:** this is a classic **timeout's
miscalculation** — A's timeout (5s) is longer than B's p99 (2s), but A's *total*
p99 is 6s, which means **A is doing more than one call**, or the calls are
**serialised**, or the timeout is being **exceeded by the retry's budget**. Before
changing anything, measure the **span's breakdown** — the trace shows exactly
where the 6s goes. (2) **The likely's causes, in order:**
- (a) **A makes multiple serialised calls to B** (e.g. a loop that calls B per
  item). The fix: the **batch's endpoint** (one call with 20 ids) or the
  **denormalised's copy**. The N+1's over the network is the single's most common
  cause of a "slow" service that "isn't slow".
- (b) **A's retries are stacking**: with a 5s timeout and 2 retries, the worst
  case is 15s. The rule: **the total's retry's budget must be less than the
  caller's remaining's deadline**, and the per-attempt's timeout must be
  `remaining_budget / (attempts + 1)`. The timeout's budget must be **derived**,
  not chosen.
- (c) **The timeouts aren't propagated** — A's own upstream (a gateway with a 3s
  timeout) gives up while A is still waiting 5s. The fix: the **deadline's
  propagation** — A should read the incoming deadline and derive its own
  timeouts from it.
- (d) **B's p99 is a symptom of a queue** — B is slow because it's overloaded and
  its requests are queuing. A's circuit's breaker should be failing fast rather
  than waiting the full 5s. With the breaker, A's p99 would drop to near the
  breaker's fail-fast time.
(3) **The fixes, in the order of the value:**
- **The timeout's budget's discipline**: set the per-call's timeout from the
  remaining's deadline, with a **minimum** for the actual's work and a **maximum**
  for the dependency's patience. Propagate the deadline.
- **The circuit's breaker**: with B's p99 at 2s and a 1s threshold, the breaker
  opens and A fails fast with a `503` (plus a fallback if one exists) instead of
  holding a thread for 5s.
- **The bulkhead**: limit the concurrency to B so B's slowness can't consume A's
  whole's thread's pool. This is what stops the cascade.
- **The batching/denormalisation**: remove the N+1's calls.
- **The hedging**: send a second's request to another replica after 50% of the
  budget — cheap insurance on the tail.
- **The caching**: if the data's tolerance allows, cache B's response for a short
  TTL and serve it on the slow's path.
(4) **The deeper's question:** *why* is B's p99 2 seconds? Is B's DB the
bottleneck? Is B's pool's size too small? Is B's downstream slow (and B's
timeout's budget is the real's problem)? A's fix at the client is defensive; the
**root's cause** is B's. The honest's answer: "I'd fix A's timeout's discipline
and add the breaker and the bulkhead — those are correct regardless — but I need
B's profile to fix the actual's latency." (5) The senior's framing: "the p99
6s with a 5s timeout means the *timeout isn't the constraint* — something is
exceeding it. The two's candidates: the retries (the budget's violation) and the
serialised's calls (the N+1 or the chain's depth). The trace's breakdown answers
it in ten minutes, and I'd do that before changing any's code. Then the timeout's
discipline: the per-attempt's timeout must be derived from the *remaining's
deadline* and the *attempt's count*, not picked — '5 seconds, three times' is
15 seconds of latency the client never sees. And the pattern's rule I'd state:
**the total's time's budget of a user's request must be the sum of the hops'
budgets, and each hop's budget must leave room for the retries** — otherwise
you're designing a system that *cannot* meet its SLO under a partial's failure.
Finally, the **bulkhead** is what turns a slow dependency into a degraded service
rather than an outage: without it, B's 2s p99 at 100 RPS means 200 threads
blocked, and A's 200-thread pool is exhausted — a slow dependency becomes a
total outage, which is the coupling's cascade that the microservices' architecture
is supposed to *reduce*."

**S2.** *"You deploy a new version of the orders service that adds a field to the
Order DTO. Twenty minutes later, the reporting service starts throwing
ClassCastExceptions. How should this have been prevented?"**
>
> **Ideal answer:** (1) **The framing:** the field's *addition* shouldn't break
anyone — this is a **backward's compatibility's violation**, and the fact that it
broke means the consumer was **not tolerant of the schema's evolution** (which is
itself the deeper's bug). The immediate question: was the *type* of an existing
field changed, or was a new field added that the consumer *interpreted* as
something else? (2) **The diagnosis (in the first 10 minutes):** compare the two
schemas (the old's and the new's DTO); find the exact's field whose type
changed; find the consumer's line that assumed the old's type. The
`ClassCastException` is the *symptom*; the *cause* is the schema's break. (3) **The
immediate's fix:** roll back the producer (if it's still mid-rollout, halt it) —
or, if the field's addition is genuinely needed, **rename the new's field** and
roll forward with a corrected's build. (4) **The prevention — four's layers, and
they're cumulative:**
**(a) The contract's design's rule (the strongest).** The producer's contract must
be **additive**: new fields get a **new name** if the semantic's changed, and
**never** change an existing field's type. If the DTO's `total` was a `double`
and it needed to be a `BigDecimal`, the correct move is to add `totalMoney` (a
new's field) and deprecate `total` — not to change the type in place. The
producer's team should have a **linting's rule** (the OpenAPI's diff, the
schema's compatibility's check in the CI) that makes the breaking's change
**impossible to merge**.
**(b) The consumer's tolerance (the defensive's layer).** The consumer should
**ignore the unknown's fields** (`@JsonIgnoreProperties(ignoreUnknown = true)`) —
it should never blow up on a field it doesn't know. And the consumer's parsing
should be **tolerant of the type's variations** where the contract allows it (the
Jackson's `ACCEPT_SINGLE_VALUE_AS_ARRAY`, the `String` → `BigDecimal`'s coercion).
A consumer that crashes on an unknown's field is a **brittle's consumer**, and
that's the bug that made the 20-minute's outage possible.
**(c) The contract's test (the CI's gate).** The **consumer's driven's contracts**
(Pact) — the reporting's service declares "the `total` is a number"; the orders's
service's pipeline runs the provider's verification and **fails the build** on
the break. This is the mechanism that turns a production's incident into a
failed's build. For the events, the **schema's registry's compatibility's check**
in the producer's publish's path.
**(d) The deployment's safety's net.** The **canary's rollout** (1% → 10% → 50% →
100%, with the automated's metric's check at each step) would have caught the
`ClassCastException` on 1% of the traffic in 2 minutes instead of 20% in 20. And
the **feature's flag** would have let you disable the new's field's population
without a deploy. (5) **The process's fix (the organisational's part):** the
producer's change's **notification**. The reporting's team was *surprised* — which
means there was no contract's change's process. The **API's changelog** (with
the dated's entries), the **deprecation's policy**, and the **consumers'
subscription's list** (the pact's broker, the schema's registry's metadata) turn
"surprise" into "the reporting's team knows, has a dashboard, and has migrated."
(6) **The prevention's checklist (the concrete's one):** the producer's CI: the
OpenAPI's diff's check (the backward's compatibility), the Pact's provider's
verification, the schema's registry's check. The consumer's CI: a **mutation's
test** that injects an unknown's field into the fixture and asserts the consumer
still works (this is the "tolerate the unknown's field" rule, made
executable). The platform's: the canary's rollout by default. The team's: the
API's changelog with the dated's entries and the consumers' list. (7) The senior's
framing: "the 20-minute's outage is the *symptom*; the *cause* is that the
system had **no contract's change's enforcement** — the producer was free to
change a field's type, and the consumer was brittle enough to crash on a schema
it didn't recognise. The fix is four's layers, and the order matters: first make
the **breaking's change impossible** (the schema's diff's check in the producer's
CI) — that's the *prevention*; then make the **consumer's tolerant** (the
unknown's fields are ignored, and the mutation's test proves it) — that's the
*defence*; then the **contract's test** — that's the *detection* at the build's
time; then the **canary's rollout** — that's the *blast's radius's* limit. And
the rule I'd put in the team's playbook: **a field's type's change is never
allowed; you add a new's field and deprecate the old's one** — with the
deprecation's window, the telemetry, and the dated's changelog. And the one
that's usually missed: **the consumer's test must include an unknown's field** —
because 'I tolerate what I don't know' is a *behaviour* that rots unless it's
tested."

---

## 14. Tricky Interview Questions

**T1. "You said don't retry a 4xx. What about a 409 Conflict?"**
> A `409` is a **state's conflict** and the answer depends on the cause. If it's
a **version's mismatch** (the optimistic's locking) — the client read the entity,
someone else changed it, so the client re-reads, re-applies, and retries — then
**yes**, retry, but with a **re-read** in between (not a blind's retry; a blind's
retry of the same stale payload will conflict again). If it's a **duplicate's
creation** (the same's idempotency's key, the same's unique's constraint) — the
client should treat it as **success** if it's the idempotency's key (return the
stored's response) or as a **conflict** it must resolve. If it's a **business's
state's conflict** ("the order is already cancelled") — **never retry**; the
caller must change the state or the user must decide. The rule: the `409`'s
`type` tells you which, and the client's behaviour is **type-specific**, not
status-specific. That's precisely why the machine-readable `type` matters more
than the status.

**T2. "How do you handle the case where a service needs data from two other
services for one response?"**
> Three options, and the choice is about **coupling**, not code. (1) **The BFF's
composition** — the calling service fetches both and combines them. The coupling:
the caller's availability is now the intersection of both, and the latency is the
sum. Fine for 2-3 stable's dependencies. (2) **The denormalised's copy** — the
calling service keeps its own copy of what it needs, updated by the **events**.
The coupling: the schema's contract (the events) and the staleness (the eventual's
consistency). This is the right's answer for the **read-heavy** paths — it's why
the orders' service has the customer's name without calling the customer service
on every request. (3) **The merge the services** — if the two services are always
fetched together, always change together, and always fail together, they're one
service's boundary. The coupling: you lose the independence (which you probably
never had). The **decision's heuristic**: if the data's **changes** at the same
rate and is **always** needed together, denormalise; if the two services have
**independent** lifecycles and the combination is the exception, compose at the
call site. And the **operational's answer** regardless of which you pick: the
**cache** with a short TTL for the common case, and the **timeout's budget** for
the slow case (fetch the critical's data with a strict timeout, degrade the
enrichment with a cached or default's value, and return the partial's result
rather than failing the whole request).

**T3. "What's the difference between a timeout and a deadline?"**
> A **timeout** is a **duration** — "wait 3 seconds". A **deadline** is an
**absolute point in time** — "give up at 14:05:30.123". The deadline is the
correct model for a distributed call chain, and the reason is propagation: with
timeouts, a chain of depth N has a worst-case latency of N × timeout, and a
timeout chosen for the *fast* case will blow the caller's budget in the *slow*
case. With a deadline, the **remaining** time is computed at each hop and passed
down, so the total is bounded by the top-level deadline regardless of depth. The
mechanics: the client sends the deadline (the HTTP's `timeout` header, the
gRPC's `grpc-timeout`); each service computes `remaining = deadline - now` and
uses it as its own budget; if `remaining` is below the minimum viable's time, fail
fast with a `504`. gRPC's deadline is **proactive** — the client cancels at the
deadline *and* the server is notified via the context's cancellation, so the
server can stop working — which is a real advantage over the HTTP's timeout, where
the server may keep working on a request whose client has gone away (the
"wasted's work"). The practical's rule: the **top-level's deadline** comes from
the user's SLO (or the gateway's timeout), and every hop derives from it. And
the **timeout's floor**: if the remaining budget is too small to do the work
usefully, fail immediately rather than starting something you can't finish.

**T4. "Do you need to version your events if you have a schema registry?"**
> The registry gives you the **compatibility's check** (it will reject an
incompatible change at publish time if configured) and the **discovery**, but it
doesn't give you the **policy's** answer to "the old's version's consumers are
still out there". Two things remain. (1) The **removal's window** — a registry
knows a schema is deprecated, but it can't know that all the consumers have
migrated; that needs the **usage's telemetry** (which consumers are reading which
version) and a **dated's policy**. (2) The **parallel's versions** — the
registry supports multiple versions of a schema, so `v1` and `v2` can coexist;
but the *consumers'* routing must handle both, and the **event's type's name**
should carry the version (`com.example.orders.v1.OrderPlaced`) so the routing is
explicit rather than inferred from a metadata field. So: the registry is the
**mechanism**, the versioned's naming is the **routing**, and the telemetry's
policy is the **deprecation's lifecycle**. A well-run system has all three.

**T5. "What is the 'N+1 over the network' and how do you detect it?**
> It's when a service issues one call per item in a result — returning 20 orders
and calling the catalogue 20 times. It's the network's version of the JPA's N+1
and it's the most common cause of "the microservices is slow". **Detection**: the
**trace's span's count** — a single request producing dozens of spans to the same
dependency is the signal (you can alert on "spans per request" and "span count by
target"); the **client's metrics** — the call's count per request (a histogram of
calls-per-request, not just latency); and the **client's logs** — the repeated
target in a burst. **Fixes, in order of value**: the **batch's endpoint** (one
call, N ids — the change to the provider's API, but the biggest's win); the
**denormalised's copy** (the caller keeps the data — the best's answer for a
read-heavy's path); the **caching** with a short TTL; the **GraphQL's DataLoader**
(if you're on GraphQL); and the **merge the services** (if they're always called
together, they were one service). And the **prevention**: the client should
**not** call a dependency inside a loop over results — a review's rule, or an
architecture's test (an ArchUnit-style rule banning the repository/c client's
usage inside a `forEach`).

---

## 15. Follow-Up Questions Asked by Interviewers

**F1** (after the sync vs async) → *"How do you decide the timeout's value?"*
→ Never by guessing. Derive it from the **budget**: the top-level's deadline (the
user's SLO, e.g. 3s) minus the estimated time for the rest of the request, split
across the hops. And take the **p99 of the dependency** (not the mean) as the
input — a timeout at the p50 means a third of your calls fail spuriously. Then
apply the **rule of thumb**: the timeout should be comfortably above the
dependency's p99 (so normal's variance doesn't trip it) but well below the
budget's total. A good practice is to **measure and adjust**: start at
`p99 × 1.5`, watch the timeout's rate in the metrics, and tune. The **timeout's
floor**: it must exceed the time the dependency needs to do the *useful* work, or
you're systematically failing the slow-but-successful's requests. And the
**timeout's ceiling**: it must be low enough that the failure's blast's radius is
bounded (a 30s timeout with a 200-thread pool caps you at ~6 RPS). The **error's
contract's part**: the timeout's error must be a `504`/`UNAVAILABLE` — a
*retryable* error — not a `500`, so the client knows to try again.

**F2** (after the idempotency) → *"What if the client doesn't send an
Idempotency-Key?"*
→ Then you can't distinguish a retry from a new request, and you're relying on
the operation being naturally idempotent. Your options: (1) **Require the header**
for the unsafe's methods — return `400` if it's missing. This is the right's
answer for the money's operations (the payments) and it forces the client's
correctness. (2) **Derive an idempotency's key from the payload** — e.g. the
`customerId + skuId + timestamp` hash. This works when the *same* logical's
operation is naturally uniquely identifiable, but it fails when the user genuinely
wants to buy the same thing twice. (3) **Make the operation's idempotent by
design** — the `PUT` with a client-generated's ID, or the unique's constraint on
a natural's key (the order's `id` is the client's generated's UUID, so the second
insert fails the constraint → return the first's result). Option (3) is
underrated: **generating the resource's ID on the client** makes the create
naturally idempotent without any extra machinery. The **honest's answer**: "for
the operations where the money or the inventory's involved, I'd **require** the
key — the cost of a missing key is a duplicate's order, and the cost of the
header is one line of client code. For the rest, the client-generated's ID is the
cheapest's defence."

**F3** (after the contract's versioning) → *"How do you handle a change that
genuinely can't be backward compatible?"*
→ The order of the options: (1) **Add, don't change** — is the change really
breaking, or can you express it additively? "The `total` changes from a number to
a money object" → add `totalMoney`, keep `total` (deprecated). "The field
becomes required" → add it as optional first, then enforce it once the consumers
have migrated. (2) **Version the endpoint** — `/v2/orders` alongside `/v1/orders`,
with the old's version delegating to the new's implementation (so there's one
implementation, two shapes) — the **branch by abstraction** applied to the API.
(3) **The consumer's adaptation** — if you can't change the producer, the
consumer's adapter (the anti-corruption's layer) absorbs the change, and the
contract's negotiation (the `Accept` header) lets them ask for the version they
can handle. (4) **The coordinated's migration** — the deprecation's window with
the dated's notice, the consumers' dashboard, and the final `410 Gone`. The
**honest's note**: the real's cost of a breaking's change is not the code — it's
the **coordination** with every's consumer. If you can find an additive's
expression of the same's change, you avoid that entire's cost, which is why
"add, don't change" is the first's option, not the last's.

**F4** (after the gRPC) → *"What happens if the gRPC server is slower than the
client's deadline?"*
→ The gRPC's deadline is **proactive**, which is the nice part. The client
cancels at the deadline and the server's `Context` is cancelled — so a
well-written server that checks the context (or passes it to the downstream's
calls) stops working. The gaps: (1) A server that ignores the cancellation keeps
processing — the work is wasted, and if it's a DB write, it may commit *after* the
client gave up, which is exactly the "the success that's lost" that the
idempotency's key exists for. (2) The server must **propagate** the deadline to
its own's downstream's calls, or the chain's depth exceeds the top-level's budget.
(3) The **status's code** is `DEADLINE_EXCEEDED`, and the client should map it to
a `504` — and it must be **retryable** only if the operation is idempotent. (4)
The **retry's policy** in gRPC is configured on the channel (the `RetryPolicy`),
and it respects the deadline — the retries share the deadline's budget, so
gRPC's built-in retry is deadline-aware, which is a genuine advantage over
hand-rolled retry loops that can exceed the caller's budget. (5) The **server's
graceful's shutdown** matters here too: on a rolling deploy, the server should
stop accepting new calls and let the in-flight's ones finish (the
`GracefulShutdown` on the server's builder, and the SIGTERM's handler) so
the deploy doesn't cause a burst of `UNAVAILABLE`s.

---

## 16. Real 3–5 Year Scenarios

**R1. *"We have 12 services. Last quarter we had 3 incidents caused by
API contract breaks between teams. The CTO asks how to stop this. What do you
propose?"**
>
> **Ideal answer:** (1) **The framing:** this is a **process's problem**, and the
right's answer is not "be careful" — it's **make the break impossible or
detectable before production**. Three incidents in a quarter across 12 services
means the break's rate is roughly one per 12 services per quarter, which is
exactly what you'd expect from **independent deploys with no contract's
enforcement**. The cost isn't the incident — it's the **trust**: once teams
discover they can break each other, the coordination's overhead explodes, and
teams start avoiding deploys, which is worse than the microservices ever were.
(2) **The diagnosis first (a week):** for each of the 3's incidents, find the
mechanism:
- Was it a **field's type** or **removal** changed? (The additive's rule's
violation.)
- Was it a **behaviour's change** (the same's shape, different's meaning)? (The
worst kind — no schema's diff catches it, only a **contract's test** with the
**semantic's expectations** catches it.)
- Was the consumer **brittle** (it crashed on an unknown's field)? (The
consumer's fault, and the more's dangerous's one.)
- Was there **no notification** (the consumer didn't know)? (The process's
fault.)
The **break's taxonomy** determines the fix's ordering. My guess: mostly (a) and
(d), with some (c). (3) **The four's fixes, in the order I'd implement them:**
**Fix 1 — The contract's compatibility's gate (the CI's enforcement).** Every
service's PR that changes a **published's contract** (a REST's DTO, an event's
schema) runs a **schema's diff** against the last's released's version (the
OpenAPI's spec's diff, the JSON's Schema's diff, the Avro's/Pact's
compatibility's check). The rules: **additive only**; a removal or a type's
change requires an **explicit's override** with a **deprecation's ticket** (a
dated's plan, a named's owner, the consumers' list). This is the **highest's
value** — it makes the *accidental's* break impossible, and it's cheap (a CI
step, an hour's work). For gRPC, the **buf's breaking** check does this natively
(it's field's number-aware — which the OpenAPI's diff isn't).
**Fix 2 — The consumer's driven's contracts (the Pact's adoption).** Each
**consumer** declares what it needs; each **producer** verifies. The pact's
broker's matrix shows the dependency's graph — and that matrix is itself
invaluable as the **architecture's documentation** (you discover the
dependencies nobody knew about). The **priority's order**: start with the top 5
pairs by the incident's history and the traffic's volume, because a full's
rollout across 12's services is a quarter's project. The **producer's
verification** must run in the producer's CI — that's the whole's point.
**Fix 3 — The consumer's tolerance (the defensive's coding).** Every consumer
must **ignore the unknown's fields** and be **tolerant of the type's
variations**. Make it **executable**: a **mutation's test** that adds a random's
unknown's field to the fixture and asserts the consumer still works. A
convention without a test is a convention that rots.
**Fix 4 — The process (the dated's deprecation's policy).** A **contract's
change's process**: the **changelog** (the dated's entries, the versioned's
links), the **notification** (the pact's broker's webhook, the schema's
registry's subscribers), the **deprecation's window** (a documented's minimum,
scaled by the consumer's type: internal = 2 deploy cycles, external = 90 days),
and the **Sunset** header + `410 Gone` at the end. Named's owners, dated's
windows, and the **telemetry's gate** on the removal.
(4) **The rollout's plan (the sequencing matters):** Week 1: the **CI's gate** on
the one service with the most's consumers. Week 2: the **changelog** and the
**notification's** process. Weeks 3-4: the **Pact's** adoption for the top 5's
pairs. Week 4+: the **tolerance's tests** in the shared's client library (one
place, applied everywhere). Then the **measure**: the **break's incident's count**
per month (target: 0), the **contract's PR's rejection's rate** (the gate's
catches), the **pact's coverage** (the % of the contracts with a pact),
the **deprecation's ageing** (the open's deprecations and their windows). The
**honest's framing**: "the goal isn't zero's changes — it's zero's **surprises**.
A **deliberate's** breaking's change with a dated's notice and a migration's
window is a fine's outcome; an **accidental's** one is the incident. The CI's gate
converts the accidental's into a deliberate's, which is the whole's value."
(5) **The senior's framing:** "three incidents in a quarter is the *baseline* for
twelve's services with independent deploys and no contract's enforcement — it's
not a's bad's luck, it's a's system's design. The fix has to be **mechanical**,
because a's process's rule that humans follow under's deadline's pressure will
lose to the deadline. So: the **CI's gate** (the schema's diff, with a
**deliberate's override's path** that requires a dated's ticket) — that converts
accidental's breaks into deliberate's ones. Then the **Pact's adoption**, started
where the pain is, because the pact's broker's matrix doubles as the
dependency's map. Then the **consumer's tolerance's tests** — because the
consumer's brittleness is what turned a schema's change into a 500, and a
tolerant's consumer turns it into a 200 with a defaulted's field. And the
**process's layer** — the changelog, the dated's deprecation's window scaled by
the consumer's type, and the **telemetry's gate** on the removal. And the one
metric I'd hold the organisation to: **the number of *surprising* contract's
breaks per month, and the median's time from the producer's deploy to the
consumer's break** — that second one is the real's signal, and if it drops to
zero, the programme's working."

**R2.** *"Our checkout service makes 4 sequential calls to 3 different services
and its p99 is 8 seconds. We've already added a 3-second timeout. The business
is complaining. Walk me through your analysis."**
>
> **Ideal answer:** (1) **The framing:** 8 seconds with a 3-second timeout means
**something is exceeding the timeout** — the retries (3s × attempts), the
**serialised** calls (4 × the per-call's time), or the timeout's **not being
enforced** on some's path. Before any's code, I need the **trace's breakdown** —
a single's request's spans, with the timings. This is a 10-minute's diagnostic
and it tells us everything. (2) **The likely's arithmetic:** 4 sequential calls ×
3s = 12s worst case, so an 8s p99 with a 3s timeout suggests roughly 2-3 calls
timing out in a chain (plus the fast ones). The **retries** make it worse: with 2
retries, a single's failing call costs 9s, and the chain can hit 30s+ — but the
*reported* p99 is 8s, so the retries are probably **not** on the worst's path yet
(the budget isn't enforced) or there are no retries. (3) **The diagnosis (the
tracer's breakdown will name it):**
- (a) **The serialised's chain (the most's likely).** 4 hops, each's
  p99-contributing, means the chain's p99 is the *sum* of the hops' tails. The
maths: if each hop has a 10ms p50 and a 200ms p99, four's serialised hops give a
p99 in the *hundreds* of ms; if one hop is slow and retries, it's seconds. The
**fix**: identify the hop that dominates and **remove it from the path** (the
event, the denormalisation, the cache) — not just make it faster.
- (b) **The N+1 over the network.** One of the 4 "calls" is actually a loop —
the 20 items × a catalogue's call. The trace will show 20's spans to the same
target. The **fix**: the **batch's endpoint** or the **denormalised's copy**.
- (c) **The timeout's budget's violation.** The retries are stacking past the
caller's budget. The **fix**: the derived's timeouts (the budget ÷ the attempts)
and the propagated's deadline.
- (d) **The payment's gateway's slow (a 3s+ provider).** The fix: the
**circuit's breaker** + the **fail's fast** + a **degraded's mode** ("the
payment's pending, we'll confirm by webhook").
- (e) **The connection's pool's exhaustion.** HikariCP's `pending` count climbing
→ the calls are queueing *before* they hit the network. The trace shows the
wait, not the call. The **fix**: the pool's sizing, the bulkhead, and reducing
the concurrency's demand.
(4) **The fix's plan, in the order of the value:**
- **Remove the hops from the path** (the structural's fix): the events for the
notification; the denormalisation for the customer; the cache for the catalogue.
Target: **1-2 hops** in the user's path.
- **Parallelise the independent's branches** (the immediate's win): if calls 2
and 3 are independent of 1, run them concurrently — `CompletableFuture.allOf`
with the **bounded's executor** (not the common's pool — that would starve the
rest of the app). The **timeout's budget** still applies to the whole.
- **The circuit's breaker + the bulkhead** (the containment): the slow
dependency fails fast and can't consume the pool.
- **The deadline's propagation + the derived's timeouts** (the discipline).
- **The caching** with a short TTL for the catalogue's read.
- **The graceful's degradation**: the payment's pending's state with a webhook
confirmation, rather than a blocking's call. **This is the biggest's UX win**
and it's the "the user's response can be deferred" pattern.
(5) **The measurement's plan:** the target is a **p99 under 1 second** and a
**p50 under 100ms**. Instrument: the **spans per request** (target ≤ 3), the
**dependency's call's count per request** (target: 1 per dependency), the
**timeout's rate** (should be ~0.1% — if it's 5%, your timeout's value is wrong
or the dependency is broken), and the **p99 by dependency** (which one is the
problem). The **SLO's error's budget**: if the checkout's SLO is 99.9% under
2s, and the dependency's tail is 8s, the checkout's SLO is **structurally
unachievable** — no amount of tuning fixes that; you have to change the
architecture's path. (6) The senior's framing: "eight seconds with a three-
second timeout is the *arithmetic* telling us the budget isn't being enforced —
four sequential hops at three seconds each is twelve, and something is stacking
on top. But the real's story is that the checkout's path has **four's network
hops**, and that number is the design's bug: no amount of timeout's tuning makes
a four-hop chain fast, because the p99 of a serialised chain is the *sum* of the
hops' tails. So the fix's order is **structural first, tuning second**:
**collapse the path to one or two hops** (the events for the notification, the
denormalisation for the customer, the cache for the catalogue), **then** the
resilience's pattern (the breaker, the bulkhead, the derived's timeouts), and
**only then** the tuning. And the thing I'd insist on as the principal: **if the
dependency's tail is 8 seconds and your SLO is 2 seconds, the SLO is
structurally unachievable through that dependency** — you can only fail fast and
degrade, not "optimise" it. The real's deliverable is a **degraded's mode** (the
payment's pending, the cached's catalogue, the default's recommendation) and a
**budget's rule** that the checkout's path never spends more than X on the
downstream. The second's thing: I'd add a **CI's check** that the trace's depth
doesn't grow — a **budget's metric** on the spans per request, so the chain
can't silently regrow."

---

## 17. Frequently Asked Production Issues

| Symptom | Cause | Fix |
|---------|-------|-----|
| The service's slow, the dependency's fine | The serialised's chain, the N+1's calls | The events, the batching, the denormalisation |
| The p99 >> the p50 | The tail's latency, the cold's starts, the lock's contention | The hedging, the pool's pre-warming, the profiling |
| The timeouts's rate is high (5%+) | The timeout's value is too low, or the dependency is degraded | Measure the dependency's p99; re-tune; the breaker |
| The duplicate's orders appear | The response's lost, the client's retry | The `Idempotency-Key` + the atomic's claim |
| The 500s after a deploy | The contract's break | The schema's diff, the Pact's, the canary |
| The `ClassCastException` in a consumer | The field's type changed, or an unknown's field | The additive's rule, the consumer's tolerance |
| The event's lost | The publish inside the transaction, the relay's crash | The outbox's pattern, the relay's monitoring |
| The duplicate's effects from a consumer | The at-least-once's delivery | The processed-event's unique's constraint |
| The thread's pool's exhausted | The downstream's slow, the unbounded's timeout | The bulkhead, the timeouts, the breaker |
| The `ClassCastException` in the JSON's parse | A number's format, a `BigDecimal` vs `double` | The money as minor's units, the tolerant's reader |
| The retry's storm on a recovery | The retry without the jitter | The full's jitter, the retry's budget |
| The 429s from a service | The rate's limiting | The `Retry-After`, the client's backoff, the quota's design |
| The 410 Gone's surprise | The deprecation's window wasn't respected | The telemetry's gate, the dated's notice |

---

## 18. Debugging Scenarios

**D1. "Find the slow's hop in a chain (the trace's breakdown):"**
```bash
# the OpenTelemetry's span's durations (the Tempo/Jaeger)
curl -s "http://localhost:16686/api/traces/$TRACE_ID" | \
  jq '.[] | {name, duration: .duration/1000, service: .process.serviceName}'

# the application's own's RED's metrics
curl -s "http://localhost:8080/actuator/metrics/http.server.requests" | jq
```

**D2. "Detect the N+1 over the network (the spans per request):"**
```promql
# the spans per request, by the target's service
sum by (target_service) (rate(http_client_requests_seconds_count[5m]))
  /
sum(rate(http_server_requests_seconds_count[5m]))
# ⚠ > 1 for a target = the N+1's pattern
```

**D3. "Check the idempotency's table's growth and the hits:"**
```sql
SELECT status, COUNT(*), MIN(created_at), MAX(created_at)
FROM idempotency_records
GROUP BY status;
-- the IN_PROGRESS's count should be ~0 (the stuck's ones = the crashed's requests)
```

**D4. "Verify the contract's compatibility in the CI:"**
```bash
# the OpenAPI's breaking's diff
npx @redocly/cli lint openapi.yaml --extends=breaking
# the buf's breaking's check (the gRPC)
buf breaking --against '.git#branch=main'
# the Pact's verification
mvn pact-verifier -DpactBrokerUrl=$PACT_URL
```

**D5. "Find the consumer's unknown's field's crash:"**
```bash
# the log's pattern
grep -E "UnrecognizedPropertyException|ClassCastException" app.log | head -20
# the correlation's id → the whole's journey
grep "corr-abc123" app.log | jq -r '"\(.timestamp) \(.service) \(.message)"'
```

---

## 19. Architecture Design Questions

**Q1. 🏗️ Design the contract's layer for 15 microservices.**
> (1) The **contract's standards** (the REST's JSON's schema + the RFC 9457's
Problems, the protobuf's for the gRPC, the Avro's for the events); (2) The
**version's policy** (the additive's rule, the dated's deprecation's window, the
`Sunset` header); (3) The **enforcement's stack** (the schema's diff in the CI,
the Pact's broker, the schema's registry, the consumer's tolerance's tests);
(4) The **notification's process** (the changelog, the webhook's subscriptions);
(5) The **observability's conventions** (the correlation's id, the standard's
error's shape, the service's name's field); (6) The **ownership** (who owns the
contract's evolution, the review's SLA, the break's process).

**Q2. 🏗️ Design the checkout's interaction with 5 downstream's services.**
> (1) The **path's analysis** (which of the 5 must be in the user's path and
which are the background's); (2) The **sync vs async** per's interaction (the
price's read = sync + cached, the stock's reserve = sync with a strict's
timeout, the payment = sync with a circuit's breaker or a pending's state, the
email/accounting/index = events); (3) The **latency's budget** (the total's
SLO split across the hops, the derived's timeouts); (4) The **failure's handling**
per's dependency (the breaker, the bulkhead, the fallback, the degradation);
(5) The **idempotency** (the key's scope, the storage, the fingerprint); (6) The
**contract's definitions** (the OpenAPI's specs, the event's schemas, the
version's policy).

**Q3. 🏗️ Design the migration from REST to gRPC for 8 internal's services.**
> (1) The **criteria** (which services? the internal's high-volume's typed
calls; the public's APIs stay REST); (2) The **incremental's migration** (the
**strangler at the protocol's level** — the gateway routes the internal's traffic
to the gRPC's backend while the REST's frontend stays); (3) The **contract's
equivalence** (the Pact's tests that assert the REST and the gRPC's versions
return the same's results — the **consumer's driven** parity); (4) The
**observability's parity** (the same's metrics, the same's traces, the same's
correlation's id across both protocols — otherwise you lose the ability to
compare); (5) The **the team's learning's curve** (the grpcurl, the debugging's
proxies, the codegen's setup); (6) The **the fallback** (the flag to route back
to REST).

---

## 20. Cheat Sheet — One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────────┐
│  the CHOOSING:   user's waiting? → SYNC.   not waiting? → EVENT.             │
│  the USER's path should touch 1-3 services.                                   │
├──────────────────────────────────────────────────────────────────────────────┤
│  the SYNC's:  the timeouts (ALWAYS), the retries (idempotent's only, the      │
│               jitter), the breaker, the bulkhead, the correlation's id         │
│  the EVENT's: the outbox, the idempotent's consumer, the versioned's schema   │
├──────────────────────────────────────────────────────────────────────────────┤
│  the CONTRACT's RULES:  ADD only. Never remove/rename/retype.               │
│  the field's NUMBER is the protobuf's contract. Never reuse a number.        │
│  the removal's gate = the telemetry's proof + the dated's window             │
│  the ENFORCEMENT: the schema's diff, the Pact's broker, the registry         │
├──────────────────────────────────────────────────────────────────────────────┤
│  the ERROR's: the HTTP's status (4xx = fix, 5xx = maybe retry) +             │
│               the RFC 9457's Problem's Details (the `type` is for the code)  │
│  the IDEMPOTENCY: the client's key + the server's atomic's claim + the        │
│                   stored's response + the fingerprint's check                 │
├────────────────────────────────────────────────────────────────────────────┤
│  the OUTBOX: the business's write + the event's write in ONE's transaction    │
│  the CONSUMER: the unique's constraint on the event's id = the idempotency   │
│  the guarantee: AT-LEAST-ONCE end to end (the exactly-once's illusion)      │
├────────────────────────────────────────────────────────────────────────────┤
│  the PAGINATION: the keyset (the cursor) for the big's lists; the ORDER BY    │
│                   must have a unique's tie-breaker                             │
│  the DEADLINE: propagate it; the timeout's budget < the caller's remaining   │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## 21. Follow-up Learning
- → [03 · Service Discovery, Config & API Gateway](./03-Service-Discovery-Config-and-API-Gateway.md)
- → [04 · Circuit Breaker, Retries & Resilience](./04-Circuit-Breaker-and-Resilience.md)
- → [09 · Messaging Systems](../09-Messaging-Systems/README.md)
- → [06 · JWT Authentication](../06-JWT-Authentication/README.md)

---
**Next Chapter:** → [03 · Service Discovery, Config & API Gateway](./03-Service-Discovery-Config-and-API-Gateway.md)
**Previous Chapter:** ← [01 · Monolith vs Microservices](./01-Monolith-vs-Microservices-and-When-to-Use-Which.md)
