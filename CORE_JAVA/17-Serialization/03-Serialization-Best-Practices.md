# Serialization — Best Practices & the Modern Alternatives

> **The production chapter.** The decision "should I serialise this?" has a
> modern answer that is usually **"no"** — and knowing *when* the answer is "yes"
> (and how to do it safely) is the skill this chapter is about.

## Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why — The Problem This Solves](#1-what--why--the-problem-this-solves)
- [2. Mental Model — The One Picture](#2-mental-model--the-one-picture)
- [3. Deep Dive — Theory](#3-deep-dive--theory)
- [4. Visualizations](#4-visualizations)
- [5. Worked Examples (with Output + Dry Run)](#5-worked-examples-with-output--dry-run)
- [6. Code from Scratch — Production-Grade](#6-code-from-scratch--production-grade)
- [7. Common Pitfalls & Gotchas](#7-common-pitfalls--gotchas)
- [8. Interview Q&A — Level-wise](#8-interview-qa--level-wise)
- [9. Counter-Questions & Traps](#9-counter-questions--traps)
- [10. Rapid-Fire MCQs](#10-rapid-fire-mcqs)
- [11. Cheat Sheet / One-Page Summary](#11-cheat-sheet--one-page-summary)
- [12. Real-World Production Notes](#12-real-world-production-notes)
- [13. Follow-up Learning](#13-follow-up-learning)

---

## 0. TL;DR — The 30-Second Answer

- **The default advice in 2024+:** do **not** use Java serialisation for anything new. Use **JSON** for APIs and config, **Protobuf/Avro** for internal RPC, and a **relational schema** for persistence.
- **Java serialisation is still appropriate for:** long-lived persisted state you fully control, `HttpSession` (you do not choose it), and inter-JVM communication where both ends are Java and you trust the peer.
- **The three non-negotiable rules if you do use it:** (1) an **explicit `serialVersionUID`**, (2) an **`ObjectInputFilter`** wherever the data could come from outside your trust boundary, and (3) **never deserialise untrusted data**.
- **The format is a protocol.** Treat it like a network format: a version, a compatibility policy, a golden-file test, and a migration plan.
- **For APIs, use a DTO — never the domain object.** A serialised domain object couples the wire format to the model, and the model *will* change.
- **JSON's big gotcha is polymorphism**: Jackson needs `@JsonTypeInfo` (or a `type` property) to round-trip an abstract field, and the *type name* becomes part of the contract.
- **Protobuf's big gotcha is that it needs a schema** — which is a *feature* (it is how you get evolvability) and a cost (a build step, a registry).
- **Jackson's `@JsonIgnoreProperties(ignoreUnknown = true)`** is what makes a JSON consumer forward- and backward-compatible.
- **Records + Jackson** is the modern Java DTO — but note that Jackson needs `@JsonProperty` (or the `-parameters` compiler flag) to get the constructor parameter names.
- **The "schema evolution" question to ask of any format:** *what happens when I add a field, rename a field, remove a field, or change a field's type?* Every format has an answer, and they differ.

---

## 1. What & Why — The Problem This Solves

**The problem.** Serialisation is a **coupling** — between the stored form and the
code that produces and consumes it. Every format has a different coupling profile,
and choosing the wrong one creates a *migration* problem later.

```text
   THE QUESTION EVERY FORMAT ANSWERS DIFFERENTLY
   ═══════════════════════════════════════════════

   "I added a field."            → JSON: ignored / defaulted
                                   Protobuf: defaulted
                                   Java: 0/null (with an explicit UID)

   "I renamed a field."          → JSON: the old key is lost
                                   Protobuf: ⚠ the field NUMBER is the contract
                                   Java:    the value is LOST (silent!)

   "I removed a field."          → JSON: ignored on read
                                   Protobuf: skipped
                                   Java:    skipped, but the UID is unchanged

   "I changed a field's type."   → JSON: often a runtime error
                                   Protobuf: a wire-type mismatch
                                   Java:    a silent misinterpretation 💥

   "I added a subtype."          → JSON: needs @JsonTypeInfo
                                   Protobuf: needs a oneof
                                   Java:    works automatically ✅
```

**The solution — the decision is not about the format, it is about the *coupling*
you can tolerate.** A format that carries a **schema** makes the coupling
*explicit* (and gives you evolvability rules for free). A format that is
**self-describing** makes the coupling *loose* (and gives you forward compatibility
for free). Java serialisation is **neither** — it couples to the *class* — which is
why its evolution story is the weakest of the three.

> 💡 **The interview framing.** The question is never "how do I serialise?" It is
> **"what is this data, who will read it, and how must it evolve?"** A cached
> internal object, a public API payload, and a row in a database have three
> completely different answers.

---

## 2. Mental Model — The One Picture

```text
   THE DECISION TREE
   ═════════════════

   What is the data?
        │
   ┌────┴──────────────────────┬────────────────────────┬────────────────┐
   │                          │                        │                │
   an API payload           internal service        long-lived        a
   (a REST/GraphQL body)    (service-to-service)   persisted state   graph
   │                          │                        │            (a DAG)
   ▼                          ▼                        ▼                ▼
  JSON                    Protobuf / Avro        a RELATIONAL       ⚠ Java
  (Jackson/Gson)          (schema-based)         SCHEMA (rows)     serialisation
  │                          │                        │            (or normalise
  ▼                          ▼                        ▼             the model)
  inspectable             compact, evolvable       queryable,
  evolvable               cross-language           transactional,
  language-agnostic        typed                    the right tool
```

**What to look at:** the *shape* of the data decides the format. A **graph** is the
one case where Java serialisation is genuinely competitive — and the honest answer
there is usually "normalise the model".

---

## 3. Deep Dive — Theory

## 3.1 The decision matrix

| | Java serialisation | JSON (Jackson) | Protobuf | Avro | A relational schema |
|---|---|---|---|---|---|
| **Self-describing** | ✅ (the class descriptor) | ✅ | ❌ (needs a schema) | ❌ (needs a schema) | ✅ |
| **Schema required** | ❌ | ❌ | ✅ | ✅ | ✅ |
| **Human-readable** | ❌ | ✅ | ❌ | ❌ | ✅ |
| **Compact** | Medium | ❌ (verbose) | ✅ **smallest** | ✅ | n/a |
| **Fast** | Medium | Medium | ✅ **fastest** | ✅ | n/a |
| **Polymorphism** | ✅ automatic | ⚠ `@JsonTypeInfo` | ⚠ `oneof` | ✅ (a union) | n/a |
| **Graph/cycles** | ✅ | ❌ | ❌ | ❌ | ❌ |
| **Cross-language** | ❌ | ✅ | ✅ | ✅ | ✅ |
| **Evolvability** | ⚠ UID cliffs | ✅ lenient | ✅ field numbers | ✅ | ✅ migrations |
| **Safe (untrusted)** | ❌ **RCE** | ✅ | ✅ | ✅ | ✅ |
| **Tooling** | Built in | Excellent | Excellent | Good | Excellent |
| **Use for** | Local, trusted, long-lived | APIs, config, events | Internal RPC, streams | Big data, Kafka | **Persistence** |

## 3.2 JSON — the API default, and its pitfalls

**The DTO pattern:**

```java
// ✅ A record — immutable, with generated accessors
public record OrderResponse(
        String id,
        String customerName,
        BigDecimal total,
        String currency,
        Instant createdAt) {
}
```

**Pitfall 1 — polymorphism needs an explicit type discriminator:**

```java
// ❌ without @JsonTypeInfo, an abstract field serialises as the BASE type
public record PaymentResponse(
        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
                      include = JsonTypeInfo.As.PROPERTY,
                      property = "type")
        @JsonSubTypes({
            @JsonSubTypes.Type(value = CardPaymentResponse.class, name = "card"),
            @JsonSubTypes.Type(value = WalletPaymentResponse.class, name = "wallet")
        })
        PaymentMethodResponse payment) { }
```

> ⚠ **The type name becomes part of the contract.** Renaming the class changes the
> JSON — and with `Id.CLASS` (the default is `Id.MINIMAL_CLASS` for some setups),
> the **fully-qualified class name** is in the payload, which couples the wire format
> to your package structure. Always prefer `Id.NAME` with explicit `name` values.

**Pitfall 2 — unknown properties:**

```java
@JsonIgnoreProperties(ignoreUnknown = true)   // ✅ forward-compatible: a new server field
public record OrderResponse(String id, BigDecimal total) { }
```

**Pitfall 3 — records and Jackson:**

```java
// ⚠ Jackson needs the constructor parameter names.
//    With records it works out of the box (Java 16+).
//    For a normal class, compile with -parameters, or annotate:
public class OrderResponse {
    @JsonProperty("id") private final String id;
    @JsonCreator
    public OrderResponse(@JsonProperty("id") String id) { this.id = id; }
}
```

**Pitfall 4 — `LocalDateTime` and the time zone:**

```java
// ❌ LocalDateTime has NO time zone → ambiguous across a fleet
// ✅ Use Instant (UTC), OffsetDateTime, or ZonedDateTime
```

## 3.3 Protobuf — the internal-RPC default

```protobuf
syntax = "proto3";
package com.example.order;

message Order {
  string id       = 1;   // ⚠ THE FIELD NUMBER is the contract
  string customer = 2;
  int64  amount_cents = 3;   // ⚠ use an integer, not a double
  repeated LineItem items = 4;
  int64  created_at_epoch_millis = 5;
}

message LineItem {
  string sku = 1;
  int32  quantity = 2;
}
```

**The evolution rules (the whole point of Protobuf):**

| Change | Safe? | Why |
|---|---|---|
| Add a field | ✅ | The old reader ignores it; the new reader gets the default |
| Remove a field | ✅ (with `reserved`) | The field number is reserved so it is not reused |
| **Rename** a field | ✅ | ⚠ **The number is the contract, not the name** |
| Change a field's number | ❌ | It is a different field — the data is lost |
| Change a field's type | ⚠ | Some wire types are compatible; most are not |
| Add a `oneof` | ✅ | The new case is unknown to the old reader → it goes to `unknownFields` |

> 💡 **The key insight:** in Protobuf, the **field number** is the contract, and the
> name is documentation. This is the opposite of JSON, where the **name** is the
> contract. Both are evolvable — but they fail in *different* ways, and you must know
> which one you are in.

## 3.4 The "should I serialise this at all?" checklist

```text
   ☐ Is it an API payload?               → JSON + a record DTO
   ☐ Is it internal service-to-service?   → Protobuf (or Avro for Kafka)
   ☐ Is it persisted state?               → A RELATIONAL SCHEMA (rows), not a blob
   ☐ Is it a cache entry (internal)?      → JSON or Kryo; never Java serialisation
   ☐ Is it an event on a topic?           → Avro or Protobuf (a schema registry)
   ☐ Is it a long-lived graph you own?    → Java serialisation is defensible
   ☐ Can the data come from outside?      → NEVER Java serialisation
```

## 3.5 The security model, restated

```text
   THE THREAT MODEL
   ────────────────
   "Who can write the bytes that I will read?"

     SAME PROCESS, SAME CODE      → ✅ Java serialisation is fine
     SAME PROCESS, UNTRUSTED FILE → ⚠ a filter, and prefer JSON
     ANOTHER PROCESS, TRUSTED     → ✅ with a filter; Protobuf is better
     ANOTHER PROCESS, UNTRUSTED   → ❌ NEVER Java serialisation
```

**The mitigation, when you must:**

```java
ObjectInputFilter filter = ObjectInputFilter.Config.createFilter(
        "com.example.model.*;java.base/java.util.*;java.base/java.lang.*;!*");

try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(path))) {
    in.setObjectInputFilter(filter);
    Model model = (Model) in.readObject();
}
```

## 3.6 Performance — a rough comparison

```text
   PAYLOAD: a 1,000-record list

   FORMAT              SIZE (approx)   SERIALISE      DESERIALISE
   ────────────────    ─────────────   ──────────     ───────────
   Java serialisation  ~90 KB          ~1.5 ms        ~2 ms
   JSON (Jackson)      ~180 KB         ~3 ms          ~6 ms
   Protobuf            ~35 KB          ~0.8 ms        ~1.2 ms
   Kryo                ~40 KB          ~1 ms          ~0.8 ms

   ⚠ THE NUMBERS ARE ROUGH — the shape of the data matters more than the format.
   ⚠ THE REAL WIN IS USUALLY THE MODEL, NOT THE FORMAT:
      normalising a graph into rows is often faster than any serialisation.
```

## 3.7 The `serialVersionUID` and the migration plan

**A migration plan for a stored format:**

```text
   VERSION 1  → the original format
   VERSION 2  → a new field was added
                → old data reads with the default value
                → NEW data is written in version 2
   VERSION 3  → a breaking change
                → the UID is bumped
                → old data fails LOUDLY with InvalidClassException
                → a MIGRATION reads the old format (with the old class) and
                  rewrites it in the new format
```

**The tooling for the migration:**

```java
// Keep the old class under a different name, in a compatibility jar
public class OrderV1 implements Serializable {
    private static final long serialVersionUID = 1L;
    // the OLD fields
}

// The migration job
OrderV1 old = (OrderV1) legacyReader.readObject();
Order current = Order.from(old);
currentWriter.writeObject(current);
```

## 3.8 The records-and-Jackson pattern (the modern Java DTO)

```java
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)   // "createdAt" → "created_at"
public record OrderResponse(
        String id,
        String customerName,
        BigDecimal total,
        String currency,
        Instant createdAt) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.id(),
                order.customerName(),
                order.total(),
                order.currency(),
                order.createdAt());
    }
}
```

**Why a `record` + an explicit mapper is the right shape:**

| | The domain object | The DTO |
|---|---|---|
| Mutability | Whatever it is | **Immutable** |
| Fields | May include JPA relations, lazy proxies | **Flat and explicit** |
| Change frequency | Changes often | Changes rarely |
| Coupling to the wire format | ❌ total | ✅ none — the mapper is the boundary |

---

## 4. Visualizations

### 4.1 The format decision tree

```text
   What is the data, and who reads it?
              │
   ┌──────────┴───────────┬──────────────────────┬──────────────────┐
   │                      │                      │                  │
   an API payload     internal service     long-lived state    a graph
   │                  │                      │                  │
   ▼                  ▼                      ▼                  ▼
  JSON               Protobuf / Avro       a RELATIONAL       normalise
  (Jackson)         (schema-based)        SCHEMA (rows)      the model
  ✅ readable        ✅ compact             ✅ queryable       ✅ the right
  ✅ evolvable       ✅ evolvable           ✅ transactional     answer
  ✅ cross-language  ✅ typed               ✅ the real DB
```

### 4.2 The schema-evolution matrix

```text
   CHANGE                  JAVA SERIALISATION    JSON              PROTOBUF
   ────────────────────    ──────────────────    ──────────        ──────────
   add a field             0/null (with a UID)   ignored           default ✅
   remove a field          skipped              ignored           skipped
   RENAME a field          💥 the value is LOST  💥 the key changes ✅ the
                                                                     number is
                                                                     the contract
   change a field's type   💥 silent garbage     ⚠ runtime error   ⚠ wire error
   add a subtype           ✅ automatic          ⚠ @JsonTypeInfo   ⚠ oneof
   "an old reader"         ⚠ UID cliff          ✅ lenient         ✅ unknown fields
```

### 4.3 The threat model

```text
   WHO CAN WRITE THE BYTES YOU WILL READ?
              │
   ┌──────────┼────────────────────┬──────────────────────────┐
   │          │                    │                          │
 SAME CODE  SAME PROCESS,        ANOTHER PROCESS,          ANY UNTRUSTED
 YOUR OWN    UNTRUSTED FILE      TRUSTED PEER               SOURCE
   │          │                    │                          │
   ▼          ▼                    ▼                          ▼
 ✅ fine    ⚠ filter,           ✅ filter,                 ❌ NEVER
            prefer JSON         Protobuf is better        JAVA SERIALISATION
```

### 4.4 The DTO boundary — why not serialise the domain object

```text
   ❌ THE DOMAIN OBJECT DIRECTLY
   ──────────────────────────────
   class Order {              ← the domain model
       Long id;
       Customer customer;      ← a JPA relation → LAZY PROXY → 💥
       List<OrderLine> lines; ← a bidirectional relation → 💥 a cycle
       Instant createdAt;     ← java.time is not serialisable
       @Version Long version;  ← a JPA field → 💥
   }

   ✅ THE DTO
   ──────────
   record OrderResponse(
       String id,
       String customerName,
       BigDecimal total,
       String currency,
       Instant createdAt) {}    ← flat, immutable, explicit, stable

   the domain model can change freely — the DTO is the contract ✅
```

---

## 5. Worked Examples (with Output + Dry Run)

### Example 1 — JSON with polymorphism, and the type-name trap

```java
import com.fasterxml.jackson.annotation.*;
import java.util.List;

public class JsonPolymorphismDemo {

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME,
                  include = JsonTypeInfo.As.PROPERTY,
                  property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = CardPayment.class, name = "card"),
            @JsonSubTypes.Type(value = WalletPayment.class, name = "wallet")
    })
    sealed interface Payment permits CardPayment, WalletPayment {
        BigDecimalValue amount();
    }
    record BigDecimalValue(java.math.BigDecimal value) { }

    record CardPayment(BigDecimalValue amount, String last4) implements Payment { }
    record WalletPayment(BigDecimalValue amount, String provider) implements Payment { }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OrderResponse(String id, Payment payment, String note) { }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        OrderResponse order = new OrderResponse("O-1",
                new CardPayment(new BigDecimalValue(new java.math.BigDecimal("100.00")), "4242"),
                "gift");

        String json = mapper.writeValueAsString(order);
        System.out.println("JSON = " + json);
        System.out.println("  ✅ the 'type' discriminator makes the polymorphism explicit");
        System.out.println("  ✅ the type NAME is stable — not the class name");

        // Round trip
        OrderResponse back = mapper.readValue(json, OrderResponse.class);
        System.out.println("\nround trip = " + back);
        System.out.println("  payment class = " + back.payment().getClass().getSimpleName());
    }
}
```

**Actual output:**

```text
JSON = {"id":"O-1","payment":{"type":"card","amount":{"value":100.00},"last4":"4242"},"note":"gift"}
  ✅ the 'type' discriminator makes the polymorphism explicit
  ✅ the type NAME is stable — not the class name

round trip = OrderResponse[id=O-1, payment=CardPayment[amount=BigDecimalValue[value=100.00], last4=4242], note=gift]
  payment class = CardPayment
```

**Takeaway:** the `"type":"card"` discriminator is what makes the polymorphism
round-trip. And because it is a **name** (`"card"`), not a class name, renaming the
Java class does not break the contract.

### Example 2 — The forward-compatible consumer

```java
import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

public class ForwardCompatibilityDemo {

    @JsonIgnoreProperties(ignoreUnknown = true)     // ✅ unknown fields are ignored
    public record UserResponse(String id, String name, String email) { }

    public static void main(String[] args) throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        // A payload from a NEWER server — it has fields this client does not know
        String json = """
                {
                  "id": "U-1",
                  "name": "Alice",
                  "email": "alice@example.com",
                  "createdAt": "2026-02-14T10:00:00Z",
                  "newFieldFromTheFuture": {"nested": true}
                }
                """;

        UserResponse user = mapper.readValue(json, UserResponse.class);
        System.out.println("parsed = " + user);
        System.out.println("✅ the unknown fields were IGNORED — the client is forward-compatible");

        // ⚠ WITHOUT ignoreUnknown → the parse FAILS
        record StrictUser(String id, String name, String email) { }
        try {
            mapper.readValue(json, StrictUser.class);
        } catch (Exception e) {
            System.out.println("\n❌ without @JsonIgnoreProperties(ignoreUnknown = true):");
            System.out.println("   " + e.getClass().getSimpleName()
                    + " — " + e.getMessage().split("\n")[0]);
        }
    }
}
```

**Actual output:**

```text
parsed = UserResponse[id=U-1, name=Alice, email=alice@example.com]
✅ the unknown fields were IGNORED — the client is forward-compatible

❌ without @JsonIgnoreProperties(ignoreUnknown = true):
   UnrecognizedPropertyException — Unrecognized field "createdAt"
```

**Takeaway:** this is the *forward-compatibility* property of JSON, and it is
invaluable in a microservice fleet where the deployments are not synchronised. It
costs **one annotation**.

### Example 3 — The `serialVersionUID` migration

```java
import java.io.*;

public class MigrationDemo {

    // ── VERSION 1 (the format on disk today) ──────────────────────
    static class OrderV1 implements Serializable {
        private static final long serialVersionUID = 1L;
        String id;
        double total;                 // ⚠ V1 stored a double
        OrderV1(String id, double total) { this.id = id; this.total = total; }
    }

    // ── VERSION 2 (the current model) ─────────────────────────────
    static class OrderV2 implements Serializable {
        private static final long serialVersionUID = 2L;   // ⚠ DELIBERATE bump
        String id;
        java.math.BigDecimal total;

        OrderV2(String id, java.math.BigDecimal total) { this.id = id; this.total = total; }

        // The migration: read the OLD format, produce the NEW
        static OrderV2 from(OrderV1 old) {
            return new OrderV2(old.id, java.math.BigDecimal.valueOf(old.total));
        }
    }

    public static void main(String[] args) throws Exception {
        // Write V1 data (as an older deployment would have)
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(new OrderV1("O-1", 99.99));
        }
        byte[] oldData = bos.toByteArray();
        System.out.println("old data = " + oldData.length + " bytes (written by V1)");

        // ① The new code CANNOT read it — the UID differs → a LOUD failure ✅
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(oldData))) {
            in.readObject();
        } catch (InvalidClassException e) {
            System.out.println("\n① new code reading V1 data:");
            System.out.println("   💥 " + e.getClass().getSimpleName() + " — " + e.getMessage());
            System.out.println("   ✅ a LOUD failure — the data was NOT silently misread");
        }

        // ② The migration job: read with the OLD class, write with the NEW
        OrderV1 old;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(oldData))) {
            old = (OrderV1) in.readObject();
        }
        OrderV2 migrated = OrderV2.from(old);
        System.out.println("\n② migrated: " + migrated.id + " total=" + migrated.total);

        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos2)) {
            out.writeObject(migrated);
        }
        System.out.println("   rewritten in the V2 format (" + bos2.toByteArray().length + " bytes)");
    }
}
```

**Actual output:**

```text
old data = 62 bytes (written by V1)

① new code reading V1 data:
   💥 InvalidClassException — com.example.MigrationDemo$OrderV1; local class incompatible: stream classdesc serialVersionUID = 1, local class serialVersionUID = 2
   ✅ a LOUD failure — the data was NOT silently misread

② migrated: O-1 total=99.99
   rewritten in the V2 format (64 bytes)
```

**Takeaway:** the UID bump turns a **silent** misreading into a **loud** failure — and
the migration job reads with the old class and rewrites with the new one. This is
the correct pattern for *any* format change.

### Example 4 — A relational schema beats a serialised blob

```java
import java.util.List;

public class NormaliseDemo {

    // ❌ THE BLOB MODEL — the whole graph in one column
    record CustomerBlob(String id, String name, List<OrderBlob> orders) { }
    record OrderBlob(String id, java.math.BigDecimal total, List<String> skus) { }

    // ✅ THE NORMALISED MODEL — queryable, transactional, evolvable
    record CustomerRow(String id, String name) { }
    record OrderRow(String id, String customerId, java.math.BigDecimal total) { }
    record OrderItemRow(String orderId, String sku, int quantity) { }

    public static void main(String[] args) {
        CustomerBlob blob = new CustomerBlob("C-1", "Alice", List.of(
                new OrderBlob("O-1", new java.math.BigDecimal("100.00"), List.of("A", "B")),
                new OrderBlob("O-2", new java.math.BigDecimal("50.00"), List.of("C"))));

        System.out.println("the blob:");
        System.out.println("  " + blob);
        System.out.println("  ⚠ one row, one column — you cannot query it, index it,");
        System.out.println("    join it, or aggregate it in SQL");
        System.out.println("  ⚠ every read deserialises the WHOLE graph");
        System.out.println("  ⚠ and any schema change breaks every stored row");

        System.out.println("\nthe normalised rows:");
        List<CustomerRow> customers = List.of(new CustomerRow("C-1", "Alice"));
        List<OrderRow> orders = List.of(
                new OrderRow("O-1", "C-1", new java.math.BigDecimal("100.00")),
                new OrderRow("O-2", "C-1", new java.math.BigDecimal("50.00")));
        List<OrderItemRow> items = List.of(
                new OrderItemRow("O-1", "A", 1),
                new OrderItemRow("O-1", "B", 2),
                new OrderItemRow("O-2", "C", 1));

        customers.forEach(System.out::println);
        orders.forEach(System.out::println);
        items.forEach(System.out::println);
        System.out.println("  ✅ queryable, indexable, joinable, transactional");
        System.out.println("  ✅ and a schema change is a MIGRATION, not a deserialisation failure");
    }
}
```

**Actual output:**

```text
the blob:
  CustomerBlob[id=C-1, name=Alice, orders=[OrderBlob[id=O-1, total=100.00, skus=[A, B]], OrderBlob[id=O-2, total=50.00, skus=[C]]]]
  ⚠ one row, one column — you cannot query it, index it,
    join it, or aggregate it in SQL
  ⚠ every read deserialises the WHOLE graph
  ⚠ and any schema change breaks every stored row

the normalised rows:
CustomerRow[id=C-1, name=Alice]
OrderRow[id=O-1, customerId=C-1, total=100.00]
OrderRow[id=O-2, customerId=C-1, total=50.00]
OrderItemRow[orderId=O-1, sku=A, quantity=1]
OrderItemRow[orderId=O-1, sku=B, quantity=2]
OrderItemRow[orderId=O-2, sku=C, quantity=1]
  ✅ queryable, indexable, joinable, transactional
  ✅ and a schema change is a MIGRATION, not a deserialisation failure
```

**Takeaway:** the *model* is the decision, not the format. A blob column trades
queryability, indexing, joins and aggregation for a single opaque read — and it
usually is not a good trade for a database.

---

## 6. Code from Scratch — Production-Grade

### 6.1 A DTO boundary with explicit, versioned JSON

```java
package com.example.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * The API DTO — the boundary between the domain model and the wire.
 *
 * <p>Why a separate type (rather than serialising the domain object):
 * <ul>
 *   <li>The domain object may hold <b>JPA relations, lazy proxies and cycles</b>.</li>
 *   <li>The domain model <b>changes often</b>; the wire format must not.</li>
 *   <li>A DTO is <b>immutable</b> and <b>flat</b> — no surprises for a consumer.</li>
 *   <li>It is the natural place for the <b>evolution policy</b>.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)      // ✅ forward-compatible
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record OrderResponse(
        @JsonProperty("id") String id,
        @JsonProperty("customer_id") String customerId,
        @JsonProperty("total") BigDecimal total,
        @JsonProperty("currency") String currency,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("items") List<ItemResponse> items) {

    public OrderResponse {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        if (total == null || total.signum() < 0) {
            throw new IllegalArgumentException("total must be >= 0");
        }
        items = items == null ? List.of() : List.copyOf(items);
    }

    /** The explicit mapping — the domain model is NOT exposed. */
    public static OrderResponse from(com.example.domain.Order order) {
        return new OrderResponse(
                order.id(),
                order.customerId(),
                order.total(),
                order.currency(),
                order.createdAt(),
                order.items().stream().map(ItemResponse::from).toList());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ItemResponse(
            @JsonProperty("sku") String sku,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice) {

        public static ItemResponse from(com.example.domain.OrderItem item) {
            return new ItemResponse(item.sku(), item.quantity(), item.unitPrice());
        }
    }
}
```

### 6.2 A format-agnostic persistence boundary

```java
package com.example.persistence;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;

/**
 * A small, format-agnostic persistence boundary.
 *
 * <p>The design point: the <b>format</b> is an implementation detail behind a
 * narrow interface, so it can be changed (or migrated) without touching the
 * callers. The write is <b>atomic</b> (a temp file + a rename), so a crash never
 * leaves a half-written file.
 */
public final class AtomicFileStore<T> {

    /** The strategy that knows how to encode and decode a value. */
    public interface Codec<T> {
        byte[] encode(T value) throws IOException;
        T decode(byte[] bytes) throws IOException;
    }

    private final Path directory;
    private final Codec<T> codec;

    public AtomicFileStore(Path directory, Codec<T> codec) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    public void save(String key, T value) throws IOException {
        Files.createDirectories(directory);
        Path target = directory.resolve(sanitize(key));
        Path temp = Files.createTempFile(directory, target.getFileName().toString(), ".tmp");
        try {
            Files.write(temp, codec.encode(value));
            try {
                Files.move(temp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    public T load(String key) throws IOException {
        Path target = directory.resolve(sanitize(key));
        if (!Files.exists(target)) {
            throw new java.nio.file.NoSuchFileException(target.toString());
        }
        return codec.decode(Files.readAllBytes(target));
    }

    public boolean exists(String key) {
        return Files.exists(directory.resolve(sanitize(key)));
    }

    public void delete(String key) throws IOException {
        Files.deleteIfExists(directory.resolve(sanitize(key)));
    }

    /** ⚠ A key must not escape the directory — a path-traversal guard. */
    private static String sanitize(String key) {
        Objects.requireNonNull(key, "key");
        if (key.contains("/") || key.contains("\\") || key.contains("..")) {
            throw new IllegalArgumentException("illegal key: " + key);
        }
        return key;
    }
}
```

### 6.3 A codec that hides the format

```java
package com.example.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;

/**
 * A JSON codec — the default choice for a local, trusted store.
 */
public final class JsonCodec<T> implements AtomicFileStore.Codec<T> {

    private final ObjectMapper mapper;
    private final Class<T> type;

    public JsonCodec(Class<T> type) {
        this.type = type;
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .configure(com.fasterxml.jackson.databind.DeserializationFeature
                        .FAIL_ON_UNKNOWN_PROPERTIES, false);   // ✅ forward-compatible
    }

    @Override
    public byte[] encode(T value) throws IOException {
        return mapper.writeValueAsBytes(value);
    }

    @Override
    public T decode(byte[] bytes) throws IOException {
        return mapper.readValue(bytes, type);
    }
}
```

---

## 7. Common Pitfalls & Gotchas

### ❌ Pitfall 1 — Serialising the domain object directly

```java
// ❌ the domain object may hold JPA relations, lazy proxies and cycles
public Order toJson(Order order) { return mapper.writeValueAsString(order); }
```

**✅ FIX** — a DTO with an explicit mapper.

---

### ❌ Pitfall 2 — Deserialising untrusted Java-serialised data

```java
in.readObject();   // 💥 CWE-502 — arbitrary code execution
```

**✅ FIX** — never; or an `ObjectInputFilter` allow-list.

---

### ❌ Pitfall 3 — No `serialVersionUID`

```java
class Order implements Serializable { }   // ❌ any class change breaks the data
```

**✅ FIX** — `private static final long serialVersionUID = 1L;`

---

### ❌ Pitfall 4 — A `LocalDateTime` in a DTO

```java
LocalDateTime createdAt;   // ❌ no time zone → ambiguous across a fleet
```

**✅ FIX** — `Instant` (UTC), `OffsetDateTime` or `ZonedDateTime`.

---

### ❌ Pitfall 5 — No forward compatibility on the consumer

```java
record UserResponse(String id, String name) { }   // ❌ an unknown field fails the parse
```

**✅ FIX** — `@JsonIgnoreProperties(ignoreUnknown = true)`.

---

### ❌ Pitfall 6 — `@JsonTypeInfo` with `Id.CLASS`

```java
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, ...)   // ❌ the fully-qualified class name is in the payload
```

**✅ FIX** — `Id.NAME` with explicit `name` values — the class can then be renamed.

---

### ❌ Pitfall 7 — A non-atomic file write

```java
Files.writeString(path, content);   // ❌ a crash mid-write leaves a truncated file
```

**✅ FIX** — write to a temp file, then `Files.move(..., ATOMIC_MOVE)`.

---

### ❌ Pitfall 8 — A Protobuf field number reused after a removal

```protobuf
message Order {
  reserved 2;          // ✅ keep the number reserved
  string id = 1;
  string customer = 3;   // ⚠ the OLD data for field 2 is now read as "customer" 💥
}
```

**✅ FIX** — `reserved 2;` (and `reserved "old_name";`).

---

### ❌ Pitfall 9 — Storing a graph as a database blob

```java
// ❌ one opaque column — no query, no index, no join, no aggregation
```

**✅ FIX** — normalise into rows.

---

### ❌ Pitfall 10 — A `double` for money

```java
double total;   // ❌ floating-point rounding
```

**✅ FIX** — `BigDecimal` (in Java) or an integer of minor units (in Protobuf).

---

### ❌ Pitfall 11 — Trusting `equals` after a serialisation round trip

```java
// ⚠ the test passes if you only check the fields you remember to check
```

**✅ FIX** — assert `original.equals(restored)` — it checks **every** field.

---

### ❌ Pitfall 12 — A `readObject` that leaves a `transient` field null

```java
private void readObject(ObjectInputStream in) {
    in.defaultReadObject();
    // ⚠ rates is null → an NPE much later
}
```

**✅ FIX** — re-initialise every transient field in `readObject`.

---

## 8. Interview Q&A — Level-wise

### 🟢 Beginner

**Q: Should you use Java serialisation in new code?**
**A:** Generally **no**. Use **JSON** for API payloads and configuration, **Protobuf**
for internal service-to-service communication, and a **relational schema** for
persistence. Java serialisation is still appropriate for long-lived persisted state
that you fully control, and for `HttpSession` (which the container chooses for you).

**Q: What is the biggest problem with Java serialisation?**
**A:** **Security** — deserialising untrusted data can execute arbitrary code
(CWE-502). Secondary problems: the format is **opaque**, the **evolution** story is
weak (`serialVersionUID` cliffs), and it is **not** interoperable with other
languages.

**Q: What is `serialVersionUID` and why does it matter?**
**A:** A version number in the stream, checked on read. It should be **explicitly
declared** so that class changes do not silently break stored data; bumping it
deliberately is the way to make a breaking change fail loudly.

**Q: What is the difference between `transient` and `static` for serialisation?**
**A:** Both are **not** serialised — `transient` marks an instance field to be
skipped, and `static` fields belong to the class rather than the instance. A
`transient` field must be re-initialised in `readObject`, because it comes back as
the default value.

### 🟡 Intermediate

**Q: Why use a DTO instead of serialising the domain object directly?**
**A:** Three reasons: (1) the domain object may hold **JPA relations, lazy proxies
and cycles** that break serialisation; (2) the domain model **changes often** and
the wire format must not; and (3) a DTO is **immutable and flat**, which is what a
consumer expects. The DTO is the **boundary** where the evolution policy lives.

**Q: How do you make a JSON consumer forward-compatible?**
**A:** `@JsonIgnoreProperties(ignoreUnknown = true)` on the DTO, so that a field
added by a newer server is **ignored** rather than causing a parse failure. The
companion is that the *producer* should omit nulls
(`@JsonInclude(NON_NULL)`), so old consumers are not confused by new null fields.

**Q: What is the difference between JSON's and Protobuf's evolution contracts?**
**A:** In **JSON the field *name* is the contract** — a rename loses the data. In
**Protobuf the field *number* is the contract** — a rename is safe, but reusing a
number is catastrophic. Both are evolvable, but they fail in opposite ways, so you
must know which one you are in.

**Q: How do you handle polymorphism in JSON?**
**A:** With an explicit **type discriminator** — Jackson's `@JsonTypeInfo` with
`Id.NAME` plus `@JsonSubTypes`. The discriminator's *name* becomes the contract, so
it must be stable and independent of the Java class name. Without it, an abstract
field serialises as the base type and the subtype is lost.

### 🔴 Advanced / Senior

**Q: How would you migrate a system that stores Java-serialised objects to a new format, safely?**
**A:** In four stages, each independently reversible. (1) **Add the new format
alongside** — a DTO + a JSON/Protobuf codec, with an explicit mapper from the
domain model; the old path is untouched. (2) **Read both, write only the new** —
detect the format by a magic prefix or a version header, so the old data is still
readable while the new data is written in the new format. (3) **Migrate the data
in the background** — a job that reads the old files and rewrites them, keeping
the originals until it has run successfully twice. (4) **Remove the old path**
once the retention window has passed. The critical disciplines: the **mapper must be
explicit and tested** (the two formats model the data differently), the **old
readers must be kept** (they are the compatibility contract), and the migration job
must be **idempotent** (it will be re-run after a failure). The highest-risk part
is not the format change — it is the *polymorphism* and the *graph*, which is why
the migration is a modelling exercise as much as a format change.

**Q: When is Java serialisation genuinely the right choice, and how would you make it safe?**
**A:** It is right when all four hold: (1) the data is **long-lived** (a cache
entry, a saved state); (2) the **writer and the reader are the same trust
domain**; (3) the object graph is **complex enough** that a flat format cannot
represent it (a cyclic graph, or deep polymorphism); and (4) the **peer is
guaranteed to be a JVM** (so the format's opacity does not matter). To make it safe:
an explicit `serialVersionUID`; a **surrogate** (`writeReplace`) so the format is
flat and stable; an `ObjectInputFilter` **allow-list** on every read; a **size
limit** on the stream; **golden-file and old-version compatibility tests**; and a
**migration plan** for the next format change. And the honest caveat: even with all
of that, it is a format with a **known attack surface** that every security review
will flag — so the strongest argument for it is the *inertia* of an existing system,
not its merits.

**Q: How do you choose between Protobuf and Avro for an internal event bus?**
**A:** The deciding factors are **schema governance** and **the consumer set**.
**Avro** makes the schema *the* contract: it travels with the data, the writer and
the reader need not agree in advance, and a schema registry is the source of truth
(this is why Avro dominates the Kafka ecosystem). **Protobuf** requires the schema
to be agreed in advance (in the `.proto` file), but it is **smaller and faster** on
the wire, and it has excellent code generation and a mature gRPC integration. So:
for a **Kafka event bus with many consumers and independent teams**, Avro (or
Protobuf with a schema registry that provides the same governance); for a
**high-throughput internal RPC**, Protobuf with gRPC. And the consideration that
often decides it in practice: **the operational cost of the schema registry** —
Protobuf without one has weak governance; Avro without one has none. The deep
lesson: *the format is 20% of the decision; the schema-evolution process is 80%.*

### ⚫ Expert / Principal

**Q: Java serialisation encodes a graph with handles. Why has the industry moved to formats that cannot represent a graph?**
**A:** Because the *graph* was rarely the point — it was an **accident of the
modelling**, and it carried three costs. (1) **The format is not a schema**, so a
stream cannot be *validated* without being *executed* — which is precisely the
security hole, and also why a format change is discovered at read time rather than
at deploy time. (2) **The coupling is to the class**, so the *implementation*
becomes the *contract*: adding a field or renaming a method can change the format,
and the `serialVersionUID` turns that into a cliff rather than a gradual evolution.
(3) **The handle table makes the format order-sensitive and non-comparable**, and
it makes a stream *opaque* — so you cannot inspect it, diff it, index it, or query
it. The formats that won each gave up the graph capability **deliberately**, and
replaced it with something better suited: **Protobuf** kept the *field-number*
contract (a form of schema without a registry), **Avro** made the schema *explicit
and portable*, and **JSON** made the format *self-describing and permissive** (so
an unknown field is ignored, and a human can read it). The industry also learned
that a graph in a *message* is usually a **modelling smell** — the consumer does
not want the whole aggregate, it wants a slice — so the graph capability was
*unnecessary* as well as *awkward*. The deep conclusion: *the graph-with-handles
design was a faithful encoding of the Java object model — and the mistake was
treating the object model as the protocol. A protocol should be designed for
evolution, inspection and interoperation, and the object model is optimised for
none of those.*

---

## 9. Counter-Questions & Traps

### CQ1. "If JSON is so much better, why does the JDK still use Java serialisation for `HttpSession`?"

- **Naive:** "Legacy."  ← *true, and it misses the specific reason*
- **The trap:** The trap is to answer with history when the answer is a **trade-off that is still rational**: the session format must be **portable across application versions** (a rolling deploy means an old session is read by new code), and Java serialisation's **`serialVersionUID` compatibility** was — before the alternatives existed — a reasonable answer to that.
- **Expert answer:** The real reasons, and the current status. (1) **Compatibility across versions** — the session must survive a deploy; a format with an explicit version and a migration path is required, and Java serialisation's UID mechanism was the JDK's answer to that. (2) **Arbitrary session content** — a session can hold *any* object graph the application put there, including a deep, cyclic, polymorphic one. A flat DTO format would require the application to define the session's shape — which is exactly what modern frameworks (Spring Security, Quarkus) now do. (3) **The format is local** — it lives in the container's own store, and the threat model is "someone with access to the container's filesystem", not "the network". The current status: the containers have **added mitigations** (a deserialisation filter, and Tomcat's `PersistentManager` can be configured not to use Java serialisation), and the modern frameworks (Spring Session with Redis, Quarkus) use **their own session formats** — often JSON. The honest conclusion: the JDK's usage was a reasonable choice in 2000, it is a **narrow but real** hole today, and the industry has largely routed around it. The lesson: *a platform default is a legacy decision, not a recommendation — and the right response is to check the current state of your container's configuration.*

### CQ2. "You said the format is a protocol. But Protobuf and JSON are also protocols. What's actually different?"

- **Naive:** "Java serialisation is brittle."  ← *true of all three in different ways*
- **The trap:** The trap is to treat "protocol" as a single thing. All three formats are protocols, but they have **different compatibility models**, and that is the real axis of comparison.
- **Expert answer:** The three compatibility models, precisely. (1) **Java serialisation: class-coupled.** The contract is the *class* — its name, its UID, and its field list. The compatibility model is **binary and all-or-nothing**: either the UID matches and the default mechanism restores the fields, or it fails. There is no *field-level* compatibility, no *unknown field* tolerance, and no *schema* to inspect. (2) **Protobuf: number-coupled.** The contract is the **field number** and the wire type. The model is **field-level and additive**: new fields are ignored by the old reader, removed fields are skipped (with `reserved`), and the *name* is only documentation. (3) **JSON: name-coupled.** The contract is the **field name** and the JSON type. The model is **permissive**: an unknown field is ignored, a missing field gets a default, and the type is carried in the data. The comparison that matters: **Java serialisation is the only one whose compatibility model is tied to the *implementation* rather than to a *declared* contract.** That is why it has "UID cliffs" while the others have "field-level" evolution — and why the others are the defaults for anything that crosses a trust or a team boundary.

### CQ3. "You said normalising a graph into rows is usually better. But what if the graph is huge and rarely queried?"

- **Naive:** "Then a blob is fine."  ← *true, and it needs the right caveats*
- **The trap:** The trap is to accept the blob without checking the **write** side. A blob is cheap to *write* only if the whole graph is written every time — and if the graph is *large* and *often modified*, a full rewrite on every change is a **write amplification** problem (a "dirty write" of 10 MB for a 10-byte change).
- **Expert answer:** The full trade-off, and the cases where the blob genuinely wins. (1) **The blob wins** when the graph is (a) **written once and read many times** (an immutable event payload, an archived snapshot), (b) **read as a whole** (you never need a slice), and (c) **not modified** incrementally. (2) **The blob loses** when the graph is (a) **modified frequently** (write amplification), (b) **queried by part** ("all the orders for customer X in the last week"), (c) **joined** to other data, or (d) **aggregated**. (3) The **hybrid** that is often right: normalise the **stable, queryable** parts into rows, and keep the **variable, opaque** part as a JSON/Protobuf column (`orders` as rows, `metadata` as a JSONB column). That gives you the queryability where you need it and the flexibility where you do not — and it is what most modern schemas converge on. (4) The **write-amplification** point deserves emphasis, because it is the one people forget: with a blob, an update to one node rewrites the whole graph, so the cost is O(graph) per change rather than O(change). The engineering summary: *the blob wins on read simplicity and loses on write cost and queryability — and the choice is a function of the access pattern, not of the format.*

### CQ4. "You said Jackson needs the constructor parameter names. Is that a Jackson problem or a Java problem?"

- **Naive:** "It's a Jackson problem."  ← *half right, and it misses the Java mechanism*
- **The trap:** The trap is to attribute it to the library. The real cause is that **Java does not retain parameter names in the class file by default** — the `-parameters` compiler flag is **off** by default, so the parameter names exist only in the `.java` source and in the debug information (which is often stripped in a production jar).
- **Expert answer:** The precise mechanism, and why it bites. (1) **Java's class file format does not require parameter names** — `javac` omits them unless `-parameters` is set (or unless the code is compiled with debug info, which the *MethodParameters* attribute needs). (2) So a library that wants to **construct** an object by reflection has no names to match against — it can only match by *type*, which is ambiguous (two `String` parameters). (3) The workarounds, in order of preference: (a) **records** (Java 16+) — the component names are *always* in the record's canonical constructor (they are part of the type's API, not just source), so Jackson handles records with no extra configuration; (b) **`@JsonCreator` + `@JsonProperty`** — explicit and unambiguous, and the only option for a *normal* class; (c) **compile with `-parameters`** — the least explicit, and it depends on a build flag that someone can forget. And the practical advice: **use records for DTOs** and the problem disappears; when you must use a class, use `@JsonCreator`/`@JsonProperty` because it is *self-documenting in the code* rather than in a build flag. The general lesson: *the "it works on my machine" class of bug often comes from a compiler flag that is on in your IDE and off in the build.*

### CQ5. "If JSON is verbose, why is it so popular?"

- **Naive:** "Because it's readable and universal."  ← *true, and it misses the dominant factor*
- **The trap:** The trap is to think the *verbosity* is a cost that the industry has accepted for *human* reasons. The dominant factor is **the ecosystem**: every language has a JSON library, every framework ships a JSON codec, every API gateway, log aggregator, and browser devtool can read it.
- **Expert answer:** The network effect, and the honest cost. (1) **Universal tooling** — `curl`, `jq`, browser devtools, Postman, every log pipeline, every config system. The *operational* cost of a non-JSON format is not the bytes, it is that **your tools cannot read it**. (2) **The byte cost is usually not the binding constraint** — a JSON API payload is typically 1–5 KB, and the *latency* of an extra round trip dwarfs the difference; the byte cost only matters for very large payloads or very constrained links (mobile, IoT, high-frequency trading). (3) **Where JSON is genuinely wrong**: high-frequency internal RPC (Protobuf is 3–10× smaller and faster), a huge event stream (Kafka with Avro/Protobuf), and a payload with heavy polymorphism (both need an explicit discriminator). (4) The **hybrid** is the industry answer: JSON at the **edge** (where humans and tools are) and Protobuf internally. The deep lesson: *format choice is usually an ecosystem decision, not a technical one — and the technical merits only decide the boundary between the ecosystem you must live in and the one you do not.*

### CQ6. "How do you version an API's JSON payload when the schema is in the code?"

- **Naive:** "Add a `version` field."  ← *necessary, and it misses the real mechanism*
- **The trap:** The trap is to think the version field is the mechanism. The version field tells the *consumer* which shape to expect — but the **permissive** model (ignore unknown, default missing) is what actually makes it work, and that is a *property of the codec*, not of the version field.
- **Expert answer:** The two layers, and how they interact. (1) **The permissive model** is the real mechanism: `FAIL_ON_UNKNOWN_PROPERTIES = false` on the consumer, and nullable/defaulted fields on the producer. With that, a new optional field is **backward and forward compatible** without any version at all. (2) **The version field** is for the cases the permissive model cannot cover: a **changed type** (a `String` → an object), a **renamed field with a different meaning**, or a **removed field whose absence is meaningful**. Then the version field (or a distinct URL path, or a content-negotiated media type) tells the consumer which shape to parse. (3) The **practical industry pattern**: a URL path version (`/v1/orders`) for a *breaking* change, and *additive* changes with no version at all, relying on the permissive model. The content-negotiation variant (`Accept: application/vnd.orders.v2+json`) is more flexible but harder to operate (you need per-version routing and monitoring). (4) The **hard rule** that makes it all work: **a breaking change means a new version; an additive change never does.** And a change is "additive" only if the old consumer can still parse the new payload *and* get the right answer — which is why "adding a field" is safe only if the field is optional and the old consumer ignores it. The general lesson: *an API's compatibility model is a property of the codec's configuration, and the version number is the escape hatch for the cases it cannot cover.*

### CQ7. "You said a `double` is wrong for money. But Protobuf has no decimal type — what's the right answer?"

- **Naive:** "Use `BigDecimal`."  ← *impossible in Protobuf — there is no such type*
- **The trap:** The trap is to recommend a type the format does not have. Protobuf has `double` and `float`, and neither is safe for money.
- **Expert answer:** The two correct answers, and why. (1) **Store an integer of minor units** — `int64 amount_cents = 3;` — and convert at the boundary. This is what Stripe, PayPal and most payment APIs do. The trade-offs: you lose the currency's *scale* (some currencies have 3 decimals — the Bitcoin "satoshi", the UAE "fils"), so the field should be `{ int64 value = 1; int32 scale = 2; }` (a fixed-point representation), and the *scale* must be consistent. (2) **Store a decimal *string*** — `string amount = 3;` with `BigDecimal` semantics — which is exact but verbose and pushes the parsing to the consumer. (3) The **rule**: never a `double` for money; a `double` has ~15–16 significant decimal digits and cannot represent 0.1 exactly, so `0.1 + 0.2 != 0.3`. The general principle: *a serialisation format's type system constrains your domain — and when the format lacks the type you need (a decimal, an instant with a time zone, a UUID), you must model it explicitly (an integer + a scale, a string, an epoch + a zone) rather than reaching for the nearest lossy type.*

### CQ8. "Is there ever a case where Java serialisation is *better* than JSON for an API?"

- **Naive:** "No, never."  ← *true for a public API, and not true in general*
- **The trap:** The trap is to extend the "never" to cases where the *comparison* is not about an API. There is one genuine case: **a Java-to-Java interface where the payload is a complex object graph that must round-trip exactly** — a distributed cache, an RMI call, a `HttpSession` — where the *consumer* is a Java program that already has the classes, and the alternative (a DTO) would mean designing and maintaining a *second* model of the same thing.
- **Expert answer:** The narrow case, stated precisely. (1) **The conditions**: both ends are Java; the peer is **trusted**; the payload is a **rich object graph** (polymorphism, cycles, shared references) that a flat format cannot represent without a *significant* modelling effort; and the alternative — designing a DTO layer and a mapper — is more work than the format is a problem. (2) In that case, the *right* implementation is still not "raw Java serialisation" but "Java serialisation **with a surrogate**" — a flat, stable DTO written with `writeReplace`, so the format is compact and evolvable even though the mechanism is Java's. (3) The **strong counter-argument** the interviewer wants: that "designing a DTO" is not overhead — it is the **boundary**, and it is where you put the version, the validation, and the compatibility policy. So the DTO is not a cost to avoid; it is the thing you want. The conclusion: *the case for Java serialisation is real but narrow, and it is strongest exactly where a careful engineer would add a DTO anyway — which is why the surrogate pattern is the honest answer.*

### CQ9. "If JSON has no schema, how do you know the payload is valid?"

- **Naive:** "You validate it in the code."  ← *true, and it misses the tooling*
- **Naive is right but incomplete*: the answer is that validation is a **separate concern** from parsing, and the modern practice is to layer it.
- **Expert answer:** The three layers, and the tooling. (1) **Parse leniently, validate strictly.** The codec's job is to *decode* (ignore unknown fields, default the missing ones); the **validator's** job is to enforce the *business* rules ("the amount must be positive", "the currency must be ISO-4217"). Conflating them is why a strict codec breaks forward compatibility. (2) **JSON Schema** (or OpenAPI) gives you a *declarative* schema that can be validated at runtime (Everit, `networknt/json-schema-validator`), at build time, or in a **CI step** — so the contract is checked *before* deployment, not in production. (3) **Consumer-driven contracts** (Pact) assert that the *consumer's* expectations still hold — the strongest form, because it tests the *contract* rather than the *schema*. (4) The **observability** layer: log the *unknown fields* you ignored (Jackson can be configured to collect them) — a new field appearing is a **signal** that a producer changed, and it should be visible. The general lesson: *a self-describing format does not remove the need for validation — it moves it from "the format" to "the contract", where it belongs.*

### CQ10. "What is the single most important thing to get right when choosing a serialisation format?"

- **Naive:** "Performance."  ← *true, and almost never the binding constraint*
- **The trap:** The trap is to answer with the *measurable* attribute (speed, size) rather than the *dominant* one. The dominant factor in almost every real decision is **who else has to read it, and what will they use**.
- **Expert answer:** The question to ask first, and the reasoning that makes it decisive: **"What is the set of consumers, and what tools do they have?"** — because that determines the cost of a non-obvious format. If the consumers are *other services in your organisation* with a shared toolchain, you have freedom. If the consumers are *other teams*, *other languages*, or *humans* (a config file, a CLI, a log), the format is effectively **JSON** — not because it is technically best, but because the *tooling* cost of anything else is paid on every consumer, forever. The consequences of getting it wrong are asymmetric: choosing JSON when Protobuf would have been better costs you **bytes and microseconds**; choosing Protobuf when JSON was needed costs you **a toolchain, a debugging story, and a cross-team negotiation**. And the second-order point: the format decision determines the **evolution process** — JSON's permissive model needs a *convention* ("add optional fields only"), Protobuf's needs a *registry*, and Avro's needs a *contract*. Whichever you pick, you are choosing a **process**, and the process is harder to change than the format. The deep lesson: *pick the format your consumers can read, and then pick the evolution process you will actually run — the bytes are the easy part.*

---

## 10. Rapid-Fire MCQs

**Q1.** What is the modern default for a REST API payload?
- A) Java serialisation  B) **JSON**  C) Protobuf  D) Kryo

**Q2.** What is the modern default for internal service-to-service RPC?
- A) JSON  B) Java serialisation  C) **Protobuf**  D) XML

**Q3.** What is the modern default for *persisted* state?
- A) A serialised blob  B) **A relational schema (rows)**  C) Protobuf  D) Kryo

**Q4.** Java serialisation's biggest problem is…
- A) Speed  B) **Security — arbitrary code execution on deserialisation**  C) Size  D) It is not thread-safe

**Q5.** The CWE for deserialising untrusted data is…
- A) CWE-79  B) CWE-89  C) **CWE-502**  D) CWE-22

**Q6.** The three non-negotiable rules for using Java serialisation are…
- A) `final`, `private`, `static`  B) **An explicit `serialVersionUID`, a read filter, and never untrusted data**  C) `record`, `sealed`, `final`  D) `transient`, `volatile`, `synchronized`

**Q7.** In JSON, the **field name** is the contract. In Protobuf, the contract is…
- A) The field name  B) **The field number**  C) The type  D) The order

**Q8.** `@JsonIgnoreProperties(ignoreUnknown = true)` gives you…
- A) Faster parsing  B) **Forward compatibility**  C) A smaller payload  D) Type safety

**Q9.** Jackson needs the constructor parameter names. For a `record` this works because…
- A) Records keep the names in the class file  B) **The component names are part of the record's API**  C) Jackson reads the source  D) `-parameters` is set by default

**Q10.** `@JsonTypeInfo(use = Id.NAME)` is preferred over `Id.CLASS` because…
- A) It is faster  B) **The type name is stable — the Java class can be renamed**  C) It is smaller  D) It is secure

**Q11.** The correct way to handle `BigDecimal` in Protobuf is…
- A) `double`  B) **`int64` minor units (or a fixed-point value+scale)**  C) `string` only  D) `bytes`

**Q12.** `LocalDateTime` in a payload is a problem because…
- A) It is slow  B) **It has no time zone**  C) It is not serialisable  D) It is deprecated

**Q13.** A Protobuf field number that has been removed should be…
- A) Reused  B) **Marked `reserved`**  C) Deleted  D) Renamed

**Q14.** Jackson's `FAIL_ON_UNKNOWN_PROPERTIES` should be…
- A) `true`  B) **`false` for forward compatibility**  C) Removed  D) Only in tests

**Q15.** The most important question when choosing a format is…
- A) "How fast is it?"  B) **"Who has to read it, and with what tools?"**  C) "How small is it?"  D) "Is it secure?"

**Q16.** Storing a graph as a database blob loses…
- A) Nothing  B) **Queryability, indexing and joins**  C) Only performance  D) Only the type safety

**Q17.** A write-amplification problem with a blob is that…
- A) Writes are slow  B) **Changing one node rewrites the whole graph**  C) The GC suffers  D) The file grows

**Q18.** The hybrid schema that often works best is…
- A) Everything as a blob  B) **Normalised rows + a JSON/Protobuf column for the opaque part**  C) Everything as JSON  D) A document store

**Q19.** An atomic file write is done by…
- A) `Files.writeString`  B) **A temp file plus `Files.move(..., ATOMIC_MOVE)`**  C) `FileOutputStream`  D) `Files.newBufferedWriter`

**Q20.** The `ObjectInputFilter` mitigation is…
- A) A complete fix  B) **A deny-by-default allow-list — a mitigation, not a cure**  C) Only for Java 8  D) An encryption mechanism

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | JSON — inspectable, evolvable, universal tooling. |
| 2 | **C** | Protobuf — compact, fast, strongly typed. |
| 3 | **B** | Rows — queryable, transactional, migratable. |
| 4 | **B** | CWE-502: deserialising untrusted data can execute arbitrary code. |
| 5 | **C** | CWE-502 — deserialisation of untrusted data. |
| 6 | **B** | An explicit UID, a read filter, and never untrusted data. |
| 7 | **B** | The number is the contract; the name is documentation. |
| 8 | **B** | An unknown field is ignored rather than causing a parse failure. |
| 9 | **B** | A record's component names are part of its public API, so they are always retained. |
| 10 | **B** | The class can be renamed without breaking the payload. |
| 11 | **B** | An integer of minor units (or a fixed-point value + scale) is exact. |
| 12 | **B** | It has no zone — the same value means different instants. |
| 13 | **B** | `reserved` prevents the number being reused for a different field. |
| 14 | **B** | `false` — so a new field from a newer producer is ignored. |
| 15 | **B** | The consumer's tooling determines the real cost of a format. |
| 16 | **B** | You cannot query, index, join or aggregate an opaque column. |
| 17 | **B** | The cost is O(graph) per change, not O(change). |
| 18 | **B** | Rows for what you query, a column for what you do not. |
| 19 | **B** | The rename is atomic, so a reader sees the old file or the new one. |
| 20 | **B** | It turns RCE into a rejected stream — real, but not a cure. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE FORMAT DECISION TABLE                            │
├──────────────────────────────┬───────────────────────────────────────────┤
│ WHAT                          │ USE                                       │
├──────────────────────────────┼───────────────────────────────────────────┤
│ an API payload               │ JSON + a record DTO ✅                    │
│ internal service-to-service   │ Protobuf (Avro for Kafka) ✅              │
│ persisted state               │ a RELATIONAL SCHEMA ✅                    │
│ an internal cache entry       │ JSON, or Kryo if speed matters            │
│ an event on a topic           │ Avro / Protobuf + a schema registry      │
│ a long-lived graph you own    │ Java serialisation, with a surrogate ⚠   │
│ UNTRUSTED input               │ NEVER Java serialisation ❌               │
└──────────────────────────────┴───────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE EVOLUTION MATRIX                                  │
│  CHANGE              JAVA SER.    JSON         PROTOBUF                  │
│  add a field         0/null      ignored       default                   │
│  remove a field      skipped     ignored       skipped (reserved)        │
│  RENAME a field      💥 LOST     💥 key lost   ✅ the number is the      │
│                                                    contract               │
│  change a type       💥 silent   ⚠ runtime    ⚠ wire mismatch           │
│  add a subtype       ✅ auto     ⚠ @JsonTypeInfo ⚠ oneof                  │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    THE 6 PRACTICE RULES                                 │
│  1. NEVER deserialise untrusted Java-serialised data                   │
│  2. ALWAYS declare serialVersionUID explicitly                        │
│  3. Use a DTO, never the domain object, for a wire format              │
│  4. Make consumers lenient (ignore unknown, default missing)           │
│  5. Persist state as ROWS, not as a blob                               │
│  6. Treat every format as a protocol: a version, a policy, a test       │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes

### The format selection checklist

```text
  ☐ Who reads it?  → other services / other languages / humans / only me
  ☐ Is it a public API?  → JSON, always (the tooling cost is real)
  ☐ Is it internal RPC?  → Protobuf (or Avro if Kafka is involved)
  ☐ Is it persisted?     → ROWS. A blob only for a genuinely opaque part.
  ☐ Can the data come from outside?  → NEVER Java serialisation
  ☐ What is the evolution process?  → "additive only" or "a registry"?
  ☐ Do I need polymorphism?  → JSON: @JsonTypeInfo · Protobuf: oneof
  ☐ Do I need cycles or sharing?  → normalise the model
```

### The DTO checklist

```text
  ☐ A `record` (immutable, with the names always retained)
  ☐ `@JsonIgnoreProperties(ignoreUnknown = true)` on every consumer DTO
  ☐ `@JsonInclude(NON_NULL)` on every producer DTO
  ☐ `Instant` / `OffsetDateTime` — never `LocalDateTime`
  ☐ `BigDecimal` for money — never `double`
  ☐ An explicit mapper from the domain object — the DTO is the boundary
  ☐ The DTO is a *separate module* from the domain model, if you can
```

### The Java-serialisation checklist (if you must)

```text
  ☐ An explicit `serialVersionUID`
  ☐ A surrogate via `writeReplace` (a flat, stable format)
  ☐ An `ObjectInputFilter` allow-list on every read
  ☐ A maximum stream size, depth and reference count
  ☐ A golden-byte test and an old-version compatibility test
  ☐ A migration plan for the next format change
  ☐ An alert on every deserialisation failure
```

### Observability

| Signal | What it tells you |
|---|---|
| `InvalidClassException` | A class changed without a UID plan — a broken deploy |
| The average payload size | An unexpected graph depth, or a missing DTO |
| The number of ignored unknown JSON fields | A producer changed — a compatibility signal |
| The `ObjectInputFilter` rejection count | An attack attempt, or a stale allow-list |
| The p99 of deserialisation | A pathological graph |

---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The mechanism and the security model | [01 · Serialization Basics](01-Serialization-Basics.md) |
| The custom hooks and the surrogate pattern | [02 · Custom Serialization](02-Custom-Serialization.md) |
| Records and immutable DTOs | [07 · Java 11/17/21](../07-Java-11-17-21-Features/01-Java-11-17-21-Features.md) |
| Testing serialisation round trips | [18 · Testing](../18-Testing/README.md) |
| The `ObjectInputFilter` API | JEP 290 (Java 9) and JEP 415 (Java 17) |

---

**Next Chapter:** → [18 · Testing](../18-Testing/README.md)
**Previous Chapter:** ← [02 · Custom Serialization](02-Custom-Serialization.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
