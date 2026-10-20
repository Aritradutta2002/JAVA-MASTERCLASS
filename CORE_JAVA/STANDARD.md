# CORE_JAVA — Interview Notes Standard (Engineering Guideline)

Every chapter file under `CORE_JAVA/**` MUST follow the structure below.
This is the contract that makes these notes interview-ready rather than textbook dumps.

---

## 0. Non-negotiable rules

1. **Never delete existing correct content.** We are *upgrading*, not rewriting. Merge, deepen,
   reorganise, add. Preserve any existing good ASCII art, tables, and code.
2. **Examples must be runnable and correct.** Code blocks marked `java` must be logically correct
   and, where self-contained, must compile with `javac` (JDK 17+). No pseudo-code with fake APIs.
3. **Explain WHY, not just WHAT.** Every rule needs the reasoning a senior interviewer is
   listening for: *"because ... , therefore ..."*.
4. **Visuals must be ASCII/Unicode art or Mermaid** — always inside fenced code blocks with a
   language tag (`text` for art, `mermaid` for graphs). Never ASCII art without a fence.
5. **No external links required to understand the topic.** Links are a bonus, not the answer.
6. Length target: **400–900 lines per chapter**, scaled to topic importance
   (Collections / Concurrency / JVM / Streams can exceed 900 lines).
7. Use **emoji section markers** consistently (see below) so scanning a 900-line file is fast.
8. Anchor links in the Table of Contents must match heading text exactly (GitHub slug rules:
   lowercase, spaces → `-`, drop punctuation).

---

## 1. Required file skeleton

```markdown
# <Chapter Title>

> **Level:** Beginner | Intermediate | Advanced | Expert
> **Interview Frequency:** ★★★★☆ (1–2 lines)
> **Prerequisite:** link to earlier chapter
> **Last Updated:** 2026

---

## 📋 Table of Contents
- [0. TL;DR — The 30-Second Answer](#0-tldr--the-30-second-answer)
- [1. What & Why](#1-what--why)
- ...

---

## 0. TL;DR — The 30-Second Answer
(5–10 bullets a candidate can literally recite)

## 1. What & Why — The Problem This Solves
(problem statement first, then the solution; analogy)

## 2. Mental Model / Core Concept
(the "one picture" explanation — usually ASCII art)

## 3. Deep Dive — Theory
(numbered sub-sections; mechanics, internals, bytecode, JDK-version differences)

## 4. Visualizations
(ASCII diagrams, mermaid graphs, tables, timelines, memory layouts)

## 5. Worked Examples (with Output + Dry Run)
(each example: problem → code → actual output → step-by-step dry run → takeaway)

## 6. Code from Scratch — Production-Grade
(realistic, complete, compilable code with comments)

## 7. Common Pitfalls & Gotchas
(❌ wrong → ✅ right, with WHY)

## 8. Interview Q&A — Level-wise
### 🟢 Beginner
### 🟡 Intermediate
### 🔴 Advanced / Senior
### ⚫ Expert / Principal
(each: **Q:** … **A:** … (2–6 sentences, the "ideal answer"))

## 9. Counter-Questions & Traps
(The interviewer pushes back — "isn't X better?", "what if…?", "why not…?")
Each with: the naive answer, the trap, and the correct expert answer.

## 10. Rapid-Fire MCQs
10–15 questions, options A/B/C/D, then an answer key with one-line explanations.

## 11. Cheat Sheet / One-Page Summary
(table or bullet list — the page to screenshot)

## 12. Real-World Production Notes
(where it is used in banking/e-commerce systems, tuning knobs, metrics)

## 13. Follow-up Learning
(links to the next chapter + related topics)

---
**Next Chapter:** → [link](...)
**Previous Chapter:** ← [link](...)
```

---

## 2. The "Counter-Questions" section — what interviewers really do

A good interviewer does not stop at the first answer. They probe. Each counter-question entry
must follow this shape:

```markdown
### CQ1. "Isn't `HashMap` enough? Why ConcurrentHashMap?"

- **Naive answer:** "HashMap is not thread-safe, so we use ConcurrentHashMap."  ← incomplete
- **The trap:** CHM's real win is *lock-free reads + fine-grained locking on writes*,
  not merely "thread safety". Also, `ConcurrentHashMap` **does not allow null keys/values**
  because `get(key)==null` is ambiguous with an absent key.
- **Expert answer:** <full paragraph>
```

Include at least **8 counter-questions** per chapter, mixing:
- *"Why not X?"* (design alternatives)
- *"What happens if…?"* (failure modes)
- *"How would you find out?"* (debugging/observability)
- *"Is that true?"* (challenging a wrong premise — e.g. "Is `final` needed for thread safety?")

---

## 3. Visualisation rules

| Purpose | Use |
|---|---|
| Memory layout | ASCII with `Stack` / `Heap` / `Metaspace` columns and arrows |
| Call flow | Numbered arrows between boxes |
| Class relationships | ASCII tree + a note that it is *not* to scale |
| State machine | Box + labelled arrows |
| Sequence / ordering | Timeline with `──▶` and time markers |
| Collections internals | Bucket/bucket-index diagrams with real index numbers |
| Comparison | Markdown table, then a "when to pick which" decision tree |

Every diagram must have a one-line caption below it saying **what to look at**.

---

## 4. Folder `README.md` template (one per topic folder)

```markdown
# <Folder> — Interview Notes Index

| # | File | Topic | Level | Read Time |
|---|------|-------|-------|-----------|
| 01 | [Name](01-Name.md) | … | 🟢🟡 | 12 min |

## Recommended Order
## Most Asked in Interviews
## Weakest Areas to Brush Up
## Self-Check Checklist
```

---

## 5. Review checklist before a file is considered done

- [ ] TOC anchors resolve
- [ ] Every section from the skeleton exists
- [ ] ≥ 8 counter-questions
- [ ] ≥ 10 MCQs with answer key
- [ ] ≥ 3 ASCII/mermaid visuals, each with a caption
- [ ] ≥ 3 worked examples with real output
- [ ] Java snippets compile (self-contained ones)
- [ ] No truncated sentences, no TODO placeholders, no "as described above" without a reference
- [ ] Prior content preserved
