# 02 — Question Bank by Level

## Beginner

1. How do you start a system design interview?
2. What is the difference between latency and throughput?
3. Why cache an object?
4. What does a database index do?
5. What is idempotency?

**Answer pattern:** define, give one mechanism, one example, one limitation.

## Intermediate

1. How do you choose between SQL and NoSQL?
2. How do you keep a cache consistent with its source?
3. How do you scale reads differently from writes?
4. What is an outbox and which dual write does it prevent?
5. How do you propagate a request deadline?

**Answer pattern:** invariant → mechanism → failure → metric → rejected alternative.

## Advanced

1. How do you decide a consistency model?
2. How do you partition and detect hot keys?
3. How do you design retries without amplification?
4. When is a saga preferable to 2PC?
5. How do you design multi-AZ failover?

**Answer pattern:** quantify, bound, demonstrate recovery, name the safety/liveness trade-off.

## Senior / Staff

1. How would you migrate a monolith without a rewrite?
2. How do you balance team autonomy against operational cost?
3. What RTO/RPO does the business actually need?
4. How do you introduce multi-region writes?
5. How do you make an architecture decision reviewable?

**Answer pattern:** context, options, decision, consequences, evidence, revisit trigger.

## Expert / Counter Questions

| Naive answer | Trap | Strong answer |
|---|---|---|
| “Kafka gives exactly once.” | Business effect is not the broker boundary | Define the transaction scope and consumer dedup |
| “Redis makes reads fast.” | Fallback can overload the origin | Define miss/stampede/failure behavior |
| “Kubernetes provides HA.” | Pods may share a zone/dependency | Name failure domains and failover evidence |
| “Use a queue for spikes.” | Work still consumes downstream capacity | Bound queue age and consumer/database limits |
| “We can use eventual consistency.” | Some invariants cannot be stale | Name invariant and maximum staleness |

## 60-Second Prompt Bank

- URL shortener; ride matching; notification service; file upload; hotel inventory; rate limiter; chat; payment refund; search; feed.

For each, speak in this order: invariant, request path, data owner, scale, failure, trade-off.

## Spoken Follow-Ups

- “What breaks first if load is 10×?”
- “What if the third-party API is ambiguous?”
- “How do you detect corruption?”
- “What can you safely degrade?”
- “Which part would you simplify for an MVP?”
