# SYSTEM_DESIGN — Curriculum Standard

> **Contract:** every chapter under `SYSTEM_DESIGN/**` must let a backend engineer understand the topic, explain the trade-offs, diagnose a production symptom, and answer an interviewer at the stated level.

## 1. Non-negotiable rules

1. **No stubs.** A finished chapter contains explanation, mechanisms, examples, failure analysis, and interview preparation. Unfinished markers, placeholder sections, and link-only chapters do not count.
2. **Self-contained understanding.** External links are optional references, never the explanation itself. A reader must not need another chapter to interpret the central argument.
3. **Numbers over adjectives.** Give concrete units, timeouts, percentages, RPS, storage, or error budgets. State that a number is an illustrative planning assumption when it is not a universal limit.
4. **Explain why.** Every important recommendation follows the form “because X, therefore Y, at the cost of Z.”
5. **Trade-offs are mandatory.** Name the selected option, the rejected option, and the condition under which the choice should change.
6. **Correctness before simplicity.** CAP, consistency, availability, exactly-once, consensus, and database classifications must be scoped to their exact assumptions. Never claim that a product is universally CP or AP.
7. **Visuals explain decisions.** Use fenced Mermaid for architecture, sequence, state, and data-flow views; use fenced text for timelines and compact mental models. Every visual needs a caption stating what to notice.
8. **Failure is part of the design.** Each production architecture section covers dependency loss, overload, stale data, partial deployment, retry amplification, and recovery where applicable.
9. **Java targets JDK 17.** Runnable Java examples use `java`, compile under `--release 17`, avoid invented APIs, and prefer `java.util` concurrency primitives where they teach the mechanism.
10. **Preserve existing content.** Upgrade the existing hub files; do not delete correct useful material merely to replace wording.
11. **No external knowledge required.** Define acronyms on first use. Keep SQL, API, and configuration examples executable enough to reason about.
12. **Avoid false precision.** Proprietary internals are labelled as a reference architecture or publicly documented behavior, not confirmed internal implementation.

## 2. Concept chapter template

```markdown
# NN · Topic

> **Level:** Beginner | Intermediate | Advanced | Expert
> **Interview Frequency:** ★★★★☆
> **Prerequisites:** [specific chapter](relative-link.md)
> **Last Updated:** 2026

## 📋 Table of Contents
## 0. TL;DR — The 30-Second Answer
## 1. What & Why — The Problem This Solves
## 2. Mental Model — The One Picture
## 3. Deep Dive — Beginner to Expert
## 4. Internal Working / Request or Event Lifecycle
## 5. Production Architecture
## 6. Visual Learning
## 7. Worked Examples and Capacity Reasoning
## 8. Real-World Examples and Case Studies
## 9. Failure Scenarios and Debugging
## 10. Scaling Problems and Optimization
## 11. Security and Operational Considerations
## 12. Common Mistakes and Trade-Offs
## 13. Interview Preparation
### Beginner / Intermediate / Advanced / FAANG or Expert
## 14. Counter-, Cross-, Follow-Up, and Tricky Questions
## 15. Production Issue and Architecture Review
## 16. Hands-On Exercises
## 17. Rapid-Fire Review / MCQ
## 18. Revision Card and Related Reading
```

Minimum quality:

- One architecture/decision diagram, one lifecycle or sequence diagram, and one comparison/decision table.
- At least three worked examples; at least one includes arithmetic and a sanity check.
- At least two realistic failure scenarios, each with symptom, diagnostic evidence, mitigation, and recovery.
- At least four questions per level band, including “why not X?” counter-questions.
- At least three exercises with explicit acceptance criteria.
- At least eight trade-off statements or a comprehensive trade-off table.

## 3. HLD case-study template

Every case study includes:

1. Framing and explicit assumptions
2. Functional requirements
3. Non-functional requirements and out-of-scope list
4. Capacity estimate with units and sensitivity
5. API design and error semantics
6. Data model and ownership
7. Component breakdown
8. Detailed Mermaid architecture diagram
9. Critical request sequence and asynchronous path
10. Read/write paths and consistency behavior
11. Scalability and hotspot strategy
12. Bottleneck, failure, and recovery analysis
13. Security and abuse controls
14. Observability, SLOs, and runbooks
15. Trade-offs and rejected alternatives
16. Production considerations and staged improvements
17. Interview questions, follow-ups, and mock-interview prompts
18. Revision card

The estimate must state assumptions. The dominant difficulty must be explicit. A reference architecture is not presented as verified proprietary implementation.

## 4. LLD problem template

Every LLD problem includes:

- Functional/non-functional requirements and constraints
- Use cases and class responsibilities
- Mermaid class diagram and sequence diagram
- State machine where state changes are non-trivial
- Pattern selection and why alternatives were rejected
- Complete Java 17 source under `12-LLD-Preparation/implementations/`
- Deterministic demonstration/test code
- Time/space and concurrency analysis
- Extension points and common mistakes
- LLD-to-HLD evolution
- Leveled interview questions, counter-questions, and exercises

LLD implementations must model the domain rather than implement a single algorithm. Public APIs must be documented by behavior, and concurrency guarantees must be explicit.

## 5. Visual rules

| Purpose | Preferred form |
|---|---|
| Component architecture | `flowchart LR` with labelled subgraphs |
| Request/event order | `sequenceDiagram` with `autonumber` |
| Decision | `flowchart TD` with labelled yes/no edges |
| State machine | `stateDiagram-v2` |
| Data flow | `flowchart LR` with data labels on edges |
| Compact mental model | fenced `text` diagram |

Every visual is followed by a line beginning **Caption:**. Diagram boxes name responsibilities, not merely product logos.

## 6. Interview quality bar

An ideal answer:

- states the invariant or requirement first;
- distinguishes correctness from availability and latency;
- gives the mechanism, not only a product name;
- names one failure mode and its recovery;
- gives a measurable SLO/metric;
- states the rejected alternative and cost of the chosen design.

Counter-questions must follow:

```text
Naive answer → the trap → expert answer
```

## 7. Navigation rules

- Every section folder has a `README.md` with ordered chapter links and recommended use.
- The root `README.md` is the canonical map of the curriculum.
- Links are relative and point to the exact file when discussing a specific topic.
- Specialized implementation depth may link to `SPRING_BOOT`, `DATABASE_CACHING`, `CLOUD_DEVOPS`, and `CORE_JAVA`, but the hub chapter still explains the architectural decision.
- The Java DSA system-design folder remains the home for algorithmic data-structure drills; this hub links to it rather than duplicating it.

## 8. Definition of done

- [ ] All planned files exist and are linked from a parent README.
- [ ] No unfinished marker, placeholder, or truncated content.
- [ ] All relative links and heading anchors resolve.
- [ ] Code fences are balanced and visuals have captions.
- [ ] Every concept chapter satisfies the concept quality bar.
- [ ] Every HLD study has requirements, estimate, API, data, architecture, sequence, bottlenecks, failures, trade-offs, and interview prompts.
- [ ] Every LLD implementation compiles with `javac --release 17` and has a deterministic demonstration.
- [ ] Technical claims distinguish guarantees from configuration and probability.
- [ ] The validator passes with zero errors.

