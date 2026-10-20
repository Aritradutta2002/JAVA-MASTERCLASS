# Java 8 Features — The Stream API

> **The most commercially relevant chapter in this folder.** The Stream API is not
> a "functional version of a for-loop" — it is a **declarative, lazily-evaluated,
> possibly-parallel** data pipeline with a completely different cost model.
> Knowing *when* a stream is the wrong tool is as important as knowing how to use one.

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

- **A `Stream` is not a collection.** It is a *pipeline* over a source — it holds no elements of its own and does not consume the source until a terminal operation runs.
- **Lazy evaluation is the whole point.** Intermediate operations (`map`, `filter`, `sorted`) return a *new stream* and do **no work**. Nothing happens until a terminal operation (`collect`, `forEach`, `count`) is called. That is why `Stream.iterate(...).filter(...).limit(10)` on an infinite source is safe.
- **A stream can be consumed only ONCE.** After a terminal operation the stream is closed, and reusing it throws `IllegalStateException`. A classic interview trap.
- **The pipeline is a chain of `Sink`s.** `source → filter → map → collect` is built by the `StreamSupport` machinery, each stage wrapping the next. A **stateful** operation (`sorted`, `distinct`) is a **barrier** that buffers the whole stream.
- **`parallelStream()` is NOT automatically faster.** It uses the **common `ForkJoinPool`** (shared by the whole JVM), splits the source, and merges. It wins on large, CPU-bound, easily-splittable data; it *loses* on small data, on I/O-bound work, on ordered streams that need ordering, and when the per-element work is tiny (fork/join overhead dominates).
- **A `Collector` has four parts:** `supplier` (make the container), `accumulator` (add one element), `combiner` (merge two containers — used by parallel streams), `finisher` (final transformation).
- **`Collectors.toMap` throws `IllegalStateException` on a duplicate key** unless you supply a merge function. The classic bug.
- **`groupingBy` vs `partitioningBy`:** `partitioningBy` always gives exactly two buckets and is **parallel-friendly**; `groupingBy` gives one bucket per distinct value and is **not** parallel-friendly.
- **Primitive streams avoid boxing:** `IntStream`, `LongStream`, `DoubleStream`, plus `mapToObj`, `boxed()`, `sum()`, and `average()` (which returns an `OptionalDouble`).
- **`peek()` is for debugging, not for side effects** — it may be skipped entirely, including in parallel streams.


---

## 1. What & Why — The Problem This Solves


A sequence of elements supporting sequential and parallel aggregate operations.

```java
// Traditional approach
List<String> names = new ArrayList<>();
for (String s : list) {
    if (s.length() > 3) {
        names.add(s.toUpperCase());
    }
}

// Stream approach
List<String> names = list.stream()
    .filter(s -> s.length() > 3)
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

---



---



## 2. Mental Model — The One Picture

```text
   SOURCE                 INTERMEDIATE (lazy)                 TERMINAL
   ───────                ─────────────────────                ────────
   list.stream()    ──▶   .filter(p)   ──▶ .map(f)    ──▶      .collect(...)
   Stream.of(...)         .flatMap(f)  ──▶ .sorted(c)          .forEach(...)
   IntStream.range()      .distinct()  ──▶ .limit(n)           .count()
   Files.lines()          .peek(side)  ──▶ .skip(n)            .reduce(...)
        │                        │                                │
        │                        ▼                                ▼
        │              each stage returns a NEW STREAM      THE WORK HAPPENS
        │              NOTHING is executed yet               HERE, ONCE
        │                                                        │
        ▼                                                        ▼
   ┌───────────────────────────────────────────────────────────────────┐
   │  THE PIPELINE IS A CHAIN OF SINKS                                │
   │                                                                   │
   │   source ──▶ [FilterSink] ──▶ [MapSink] ──▶ [CollectSink]       │
   │                onNext()         onNext()        onComplete()     │
   │                                   │                             │
   │                                   └── pulls from the previous ──┘│
   └───────────────────────────────────────────────────────────────────┘

   THE COST MODEL:
     • lazy          → no work until the terminal op
     • one-shot      → the stream is CONSUMED by the terminal op
     • possibly parallel → the source is SPLIT and the pipeline runs
                          on the common ForkJoinPool, then MERGED
```

**What to look at:** the pipeline is **pull-based**. Each stage asks the previous one for the next element, which is exactly why laziness and short-circuiting work.

---

## 3. Deep Dive — Theory


### 3.1 Stream Creation


```java
// From Collection
Stream<String> stream = list.stream();
Stream<String> parallel = list.parallelStream();

// From Array
Stream<String> stream = Arrays.stream(array);

// From values
Stream<String> stream = Stream.of("A", "B", "C");

// Infinite streams
Stream<Integer> infinite = Stream.iterate(0, n -> n + 1);
Stream<Integer> infinite = Stream.iterate(0, n -> n < 100, n -> n + 1);  // Java 9+
Stream<Double> randoms = Stream.generate(Math::random);

// Primitive streams
IntStream intStream = IntStream.range(1, 10);      // 1-9
IntStream intStream = IntStream.rangeClosed(1, 10); // 1-10
LongStream longStream = LongStream.of(1L, 2L, 3L);
DoubleStream doubleStream = DoubleStream.of(1.0, 2.0);
```

---



### 3.2 Stream Pipeline Internals (Senior Interview Favourite)


Interviewers at top companies ask about **how** streams work internally, not just how to use them.

### Architecture Diagram

```mermaid
graph LR
    A[Source<br/>Spliterator] --> B[Stage 1<br/>filter Sink]
    B --> C[Stage 2<br/>map Sink]
    C --> D[Stage 3<br/>sorted Sink]
    D --> E[Terminal Op<br/>collect/reduce/forEach]

    style A fill:#4CAF50,color:#fff
    style B fill:#2196F3,color:#fff
    style C fill:#2196F3,color:#fff
    style D fill:#FF9800,color:#fff
    style E fill:#F44336,color:#fff
```

- **Green**: Source — provides elements via `Spliterator`.
- **Blue**: Stateless intermediate ops — process one element at a time, no memory between elements.
- **Orange**: Stateful intermediate ops — must buffer all elements before passing downstream.
- **Red**: Terminal op — triggers execution of the entire pipeline.

### How Lazy Evaluation Actually Works

Streams build a **pipeline description** (a linked list of stages). Nothing executes until a terminal operation is called. At that point:

1. The terminal operation requests elements from the last stage.
2. Each stage pulls from its upstream, creating a **pull-based chain**.
3. Elements flow through the entire pipeline **one at a time** (operation fusion).

```java
// This creates 3 pipeline stages but processes NOTHING
Stream<String> pipeline = list.stream()       // Stage 0: source
    .filter(s -> s.length() > 3)               // Stage 1: filter
    .map(String::toUpperCase);                 // Stage 2: map

// ONLY when this runs does data flow:
pipeline.forEach(System.out::println);         // Terminal triggers pull
```

**Operation Fusion**: Instead of running filter on ALL elements, then map on ALL elements, streams fuse operations. Element 1 goes through filter→map→forEach, then Element 2 goes through filter→map→forEach.

### Spliterator Characteristics (Interview Gold)

A `Spliterator` defines how a stream's source can be traversed and split. Its characteristics affect optimization:

| Characteristic | Meaning | Optimization Impact |
|---------------|---------|-------------------|
| `ORDERED` | Elements have defined order | `distinct()` preserves first occurrence; `sorted()` needs work |
| `DISTINCT` | All elements are unique | `distinct()` becomes a no-op |
| `SORTED` | Elements pre-sorted | `sorted()` becomes a no-op |
| `SIZED` | Exact count known | Enables array pre-allocation in `toArray()` |
| `NONNULL` | No nulls | Skips null checks internally |
| `IMMUTABLE` | Source can't change | Safe for parallel without defensive copy |
| `SUBSIZED` | Splits are also SIZED | Better parallel load balancing |

**Interview Q: Why does `HashSet.stream().distinct()` run faster than `ArrayList.stream().distinct()`?**
A: HashSet's Spliterator has the `DISTINCT` characteristic. The stream framework detects this and skips the `distinct()` operation entirely. ArrayList does not have this flag.

### Stateless vs Stateful Operations

| Stateless | Stateful |
|-----------|----------|
| `filter`, `map`, `flatMap`, `peek` | `sorted`, `distinct`, `limit`, `skip` |
| Process each element independently | Must see all elements or maintain state |
| $O(1)$ memory per element | $O(n)$ memory in worst case |
| Parallelize easily | Require barriers in parallel streams |

**Interview Q: Why is `sorted()` expensive in a parallel stream?**
A: It requires a **barrier** — all threads must finish their partitions before sorting can begin. This eliminates much of the parallelism benefit. Pre-sorting data or using `TreeSet` is often better.

---



### 3.3 Intermediate Operations (Lazy)


Operations that return a new Stream. Not executed until terminal operation.

### filter()
```java
Stream<T> filter(Predicate<? super T> predicate)

list.stream()
    .filter(s -> s.length() > 3)
    .filter(s -> s.startsWith("A"));  // Multiple filters
```

### map()
```java
<R> Stream<R> map(Function<? super T, ? extends R> mapper)

list.stream()
    .map(String::toUpperCase)
    .map(s -> s.length());
```

### flatMap()
```java
<R> Stream<R> flatMap(Function<? super T, ? extends Stream<? extends R>> mapper)

List<List<Integer>> nested = Arrays.asList(
    Arrays.asList(1, 2),
    Arrays.asList(3, 4)
);

// Without flatMap
Stream<Stream<Integer>> streamStream = nested.stream().map(List::stream);

// With flatMap - flattens nested structures
Stream<Integer> flatStream = nested.stream().flatMap(List::stream);
// Result: 1, 2, 3, 4

// Split strings into characters
List<String> words = Arrays.asList("Hello", "World");
Stream<String> chars = words.stream().flatMap(s -> s.chars().mapToObj(c -> (char) c));
```

### distinct()
```java
list.stream().distinct()  // Removes duplicates (uses equals)
```

### sorted()
```java
list.stream().sorted()                           // Natural order
list.stream().sorted(Comparator.reverseOrder())  // Reverse
list.stream().sorted(Comparator.comparing(Person::getName))  // Custom
list.stream().sorted(Comparator.comparing(Person::getAge).thenComparing(Person::getName))
```

### peek()
```java
// For debugging - performs action on each element
list.stream()
    .peek(s -> System.out.println("Processing: " + s))
    .map(String::toUpperCase)
    .collect(Collectors.toList());
```

### limit() & skip()
```java
list.stream().limit(5)   // First 5 elements
list.stream().skip(5)    // Skip first 5, rest elements
list.stream().skip(10).limit(5)  // Pagination
```

---



### 3.4 Terminal Operations


Operations that produce a result. Stream is consumed after terminal operation.

### collect()
```java
// To List
List<String> list = stream.collect(Collectors.toList());

// To Set
Set<String> set = stream.collect(Collectors.toSet());

// To Map
Map<String, Integer> map = stream.collect(
    Collectors.toMap(s -> s, String::length)
);

// To Map with merge function (handle duplicates)
Map<String, Integer> map = stream.collect(
    Collectors.toMap(s -> s, String::length, (v1, v2) -> v1)
);

// To Collection
LinkedList<String> list = stream.collect(Collectors.toCollection(LinkedList::new));

// Joining
String joined = stream.collect(Collectors.joining());        // "ABC"
String joined = stream.collect(Collectors.joining(", "));    // "A, B, C"
String joined = stream.collect(Collectors.joining(", ", "[", "]"));  // "[A, B, C]"

// Grouping
Map<Integer, List<String>> grouped = stream.collect(
    Collectors.groupingBy(String::length)
);

// Grouping with downstream collector
Map<Integer, Long> countByLength = stream.collect(
    Collectors.groupingBy(String::length, Collectors.counting())
);

// Partitioning
Map<Boolean, List<String>> partitioned = stream.collect(
    Collectors.partitioningBy(s -> s.length() > 3)
);

// Summarizing
IntSummaryStatistics stats = stream.collect(
    Collectors.summarizingInt(String::length)
);
// stats.getCount(), getSum(), getAverage(), getMin(), getMax()
```

### reduce()
```java
// Sum
Optional<Integer> sum = stream.reduce((a, b) -> a + b);
Optional<Integer> sum = stream.reduce(Integer::sum);

// Sum with identity
Integer sum = stream.reduce(0, Integer::sum);

// Product
Integer product = stream.reduce(1, (a, b) -> a * b);

// Find longest string
Optional<String> longest = stream.reduce((s1, s2) -> 
    s1.length() > s2.length() ? s1 : s2
);

// Three-argument reduce (for parallel streams)
Integer sum = stream.reduce(0, Integer::sum, Integer::sum);
```

### forEach() & forEachOrdered()
```java
stream.forEach(System.out::println);           // May be unordered in parallel
stream.forEachOrdered(System.out::println);    // Maintains encounter order
```

### toArray()
```java
String[] array = stream.toArray(String[]::new);
Object[] array = stream.toArray();
```

### count(), min(), max()
```java
long count = stream.count();
Optional<String> min = stream.min(Comparator.naturalOrder());
Optional<String> max = stream.max(Comparator.naturalOrder());
```

### findFirst() & findAny()
```java
Optional<String> first = stream.findFirst();  // Always first element
Optional<String> any = stream.findAny();      // Any element (faster in parallel)
```

### anyMatch(), allMatch(), noneMatch()
```java
boolean hasA = stream.anyMatch(s -> s.startsWith("A"));
boolean allLong = stream.allMatch(s -> s.length() > 3);
boolean noneEmpty = stream.noneMatch(String::isEmpty);
```

---



### 3.5 Primitive Streams


```java
IntStream intStream = list.stream().mapToInt(String::length);
LongStream longStream = list.stream().mapToLong(String::length);
DoubleStream doubleStream = list.stream().mapToDouble(String::length);

// Special operations
int sum = intStream.sum();
OptionalDouble avg = intStream.average();
OptionalInt max = intStream.max();
IntSummaryStatistics stats = intStream.summaryStatistics();

// Convert back
Stream<Integer> stream = intStream.boxed();
```

---



### 3.6 Parallel Streams


```java
// Create parallel stream
Stream<String> parallel = list.parallelStream();
Stream<String> parallel = list.stream().parallel();

// Check if parallel
boolean isParallel = stream.isParallel();

// Convert to sequential
Stream<String> sequential = parallel.sequential();

// Best practices
// ✅ Good for: Large datasets, CPU-intensive operations
// ❌ Bad for: Small datasets, I/O operations, shared mutable state
```

---



### 3.7 Java 9+ Stream API Additions


Interviewers frequently ask about post-Java 8 stream additions to test if candidates stay current.

### takeWhile() & dropWhile() (Java 9)

Unlike `filter()`, these are **short-circuiting** — they stop evaluating once the predicate fails.

```java
// takeWhile: takes elements while condition is true, then STOPS
List<Integer> taken = Stream.of(1, 2, 3, 4, 5, 1, 2)
    .takeWhile(n -> n < 4)
    .collect(Collectors.toList());
// Result: [1, 2, 3] — stops at 4, never sees 5, 1, 2

// dropWhile: drops elements while condition is true, then keeps rest
List<Integer> dropped = Stream.of(1, 2, 3, 4, 5, 1, 2)
    .dropWhile(n -> n < 4)
    .collect(Collectors.toList());
// Result: [4, 5, 1, 2] — drops 1,2,3 then keeps everything

// ⚠️ Interview Trap: On UNORDERED streams, behavior is non-deterministic
Set<Integer> set = Set.of(1, 2, 3, 4, 5);
set.stream().takeWhile(n -> n < 4); // Unpredictable! Set has no order
```

**Interview Q: What's the difference between `filter()` and `takeWhile()`?**
A: `filter()` evaluates ALL elements. `takeWhile()` short-circuits — once the predicate returns false, no more elements are processed. For infinite streams, `filter()` would hang but `takeWhile()` terminates.

### iterate() with Predicate (Java 9)

```java
// Java 8: infinite stream, must add limit()
Stream.iterate(0, n -> n + 2).limit(10);

// Java 9: bounded iterate with hasNext predicate
Stream.iterate(0, n -> n < 20, n -> n + 2);
// Result: 0, 2, 4, 6, 8, 10, 12, 14, 16, 18
// Equivalent to: for (int i = 0; i < 20; i += 2)
```

**Interview Q: Why was the three-argument `iterate()` added?**
A: To replace traditional for-loops cleanly without needing a separate `limit()` call. It prevents accidental infinite streams and makes intent explicit.

### ofNullable() (Java 9)

```java
// Creates empty stream if null, single-element stream otherwise
String value = possiblyNull();
Stream<String> stream = Stream.ofNullable(value);

// Practical use: avoid NPE when streaming optional results
List<String> results = database.findNames("pattern")
    .stream()
    .flatMap(name -> Stream.ofNullable(name))  // Skips nulls
    .collect(Collectors.toList());

// vs Stream.of(null) → throws NullPointerException
// vs Stream.ofNullable(null) → returns empty stream
```

### flatMap Enhancements (Java 9+)

```java
// Optional.flatMap chaining (Java 9)
Optional<String> result = Optional.of("hello")
    .flatMap(s -> Optional.of(s.toUpperCase()))
    .flatMap(s -> s.length() > 3 ? Optional.of(s) : Optional.empty());
```

---



### 3.8 Exception Handling in Streams


Streams do not handle checked exceptions well. Lambdas used in stream operations cannot throw checked exceptions directly. This is one of the most commonly asked interview topics.

### The Problem

```java
// ❌ Compilation error: unreported exception
List<Path> paths = List.of(Path.of("a.txt"), Path.of("b.txt"));
List<String> contents = paths.stream()
    .map(path -> Files.readString(path))  // IOException is checked!
    .collect(Collectors.toList());
```

### Solution 1: Try-Catch Inside Lambda

```java
List<String> contents = paths.stream()
    .map(path -> {
        try {
            return Files.readString(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    })
    .collect(Collectors.toList());
```

### Solution 2: Wrapper Method (Recommended)

```java
@FunctionalInterface
public interface CheckedFunction<T, R> {
    R apply(T t) throws Exception;
}

public static <T, R> Function<T, R> wrap(CheckedFunction<T, R> function) {
    return t -> {
        try {
            return function.apply(t);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    };
}

// Clean usage
List<String> contents = paths.stream()
    .map(wrap(Files::readString))
    .collect(Collectors.toList());
```

### Solution 3: Either Pattern (Functional Approach)

```java
// Return success/failure without throwing
record Result<T>(T value, Exception error) {
    boolean isSuccess() { return error == null; }
    static <T> Result<T> success(T value) { return new Result<>(value, null); }
    static <T> Result<T> failure(Exception e) { return new Result<>(null, e); }
}

List<Result<String>> results = paths.stream()
    .map(path -> {
        try {
            return Result.success(Files.readString(path));
        } catch (IOException e) {
            return Result.<String>failure(e);
        }
    })
    .collect(Collectors.toList());

// Separate successes from failures
List<String> successes = results.stream()
    .filter(Result::isSuccess)
    .map(Result::value)
    .collect(Collectors.toList());

List<Exception> failures = results.stream()
    .filter(r -> !r.isSuccess())
    .map(Result::error)
    .collect(Collectors.toList());
```

**Interview Q: Why can't stream lambdas throw checked exceptions?**
A: Functional interfaces like `Function<T,R>` and `Predicate<T>` don't declare `throws` in their method signatures. Java's type system requires checked exceptions to be declared or caught. The design choice was intentional — streams encourage pure, side-effect-free operations.

---



---

## 4. Visualizations

### 4.1 The pipeline as a chain of sinks (pull-based)

```text
   The SOURCE pushes data downstream, but each stage PULLS from the stage
   before it. This is why the pipeline is lazy and why a filter can stop
   the whole thing early.

   ┌─────────┐   pull   ┌────────────┐   pull   ┌──────────┐   pull   ┌───────────┐
   │  SOURCE │ ────────▶ │ FilterSink │ ───────▶ │  MapSink │ ───────▶ │CollectSink│
   │  (data) │           │  onNext()  │           │ onNext() │           │onComplete()│
   └─────────┘           └────────────┘           └──────────┘           └───────────┘
        ▲                      │                      │                     │
        │            ┌─────────┴────────┐    ┌────────┴────────┐            │
        │            │ SHORT-CIRCUIT?   │    │ STATEFUL?       │            │
        │            │ findFirst→STOP   │    │ sorted→BUFFER   │            │
        │            │ limit(n)→STOP    │    │ distinct→BUFFER │            │
        │            └──────────────────┘    └─────────────────┘            │
        └────────────────────── one element at a time ─────────────────────┘

   ⚠ A STATEFUL operation is a BARRIER: it must see ALL the data before it can
     emit anything. That is why `sorted()` before `limit(10)` is expensive —
     you sort everything to keep 10.


---

## 5. Worked Examples (with Output + Dry Run)

### 5.1 Worked Example A — `filter` + `map` + `collect`

**Problem:** from a list of `Person`, find everyone over 18 and collect their names.

```java
import java.util.List;
import java.util.stream.Collectors;

record Person(String name, int age, String city) { }

public class FilterAndMap {
    public static void main(String[] args) {
        List<Person> persons = List.of(
                new Person("Rex", 21, "Bengaluru"),
                new Person("Ana", 17, "Pune"),
                new Person("Kabir", 30, "Bengaluru"),
                new Person("Zoya", 45, "Pune")
        );

        List<String> names = persons.stream()
                .filter(p -> p.age() > 18)
                .map(Person::name)
                .collect(Collectors.toList());

        System.out.println(names);
    }
}
```

**Actual output:**

```text
[Rex, Kabir, Zoya]
```

**Dry run — the pipeline, element by element:**

| Stage | Rex (21) | Ana (17) | Kabir (30) | Zoya (45) |
|---|---|---|---|---|
| `persons.stream()` | Rex | Ana | Kabir | Zoya |
| `.filter(p -> p.age() > 18)` | ✅ keep | ❌ drop | ✅ keep | ✅ keep |
| `.map(Person::name)` | "Rex" | — | "Kabir" | "Zoya" |
| `.collect(toList())` | → `[Rex, Kabir, Zoya]` | | | |

**Takeaway:** a stream pipeline is **lazy** — nothing runs until the terminal
operation (`collect`). The intermediate operations (`filter`, `map`) only *describe*
the work; `collect` is what *triggers* it. And the pipeline preserves **encounter
order** for a sequential stream — which is why the output is `Rex, Kabir, Zoya` and
not some other order.

---

### 5.2 Worked Example B — `groupingBy` + `counting`

**Problem:** count the people per city.

```java
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

record Person(String name, int age, String city) { }

public class GroupAndCount {
    public static void main(String[] args) {
        List<Person> persons = List.of(
                new Person("Rex", 21, "Bengaluru"),
                new Person("Ana", 17, "Pune"),
                new Person("Kabir", 30, "Bengaluru"),
                new Person("Zoya", 45, "Pune")
        );

        Map<String, Long> countByCity = persons.stream()
                .collect(Collectors.groupingBy(Person::city, Collectors.counting()));

        System.out.println(countByCity);
    }
}
```

**Actual output:**

```text
{Bengaluru=2, Pune=2}
```

**Dry run:**

| Element | `groupingBy` key | Downstream `counting()` |
|---|---|---|
| Rex | "Bengaluru" | Bengaluru → 1 |
| Ana | "Pune" | Pune → 1 |
| Kabir | "Bengaluru" | Bengaluru → 2 |
| Zoya | "Pune" | Pune → 2 |

**Takeaway:** `groupingBy(classifier, downstream)` is a **two-stage** operation — the
classifier produces the *key*, and the **downstream collector** accumulates into the
*value*. `Collectors.counting()` is just a `Long` counter. The result is a `Map` whose
**iteration order is unspecified** for a plain `HashMap` — if you need order, use
`groupingBy(city, LinkedHashMap::new, counting())`.

---

### 5.3 Worked Example C — `reduce` and the **identity vs accumulator** trap

**Problem:** sum the salaries — and understand why the two-argument `reduce` has a
subtle requirement.

```java
import java.util.List;
import java.util.stream.Collectors;

record Employee(String name, int salary) { }

public class ReduceDemo {
    public static void main(String[] args) {
        List<Employee> staff = List.of(
                new Employee("Rex", 100),
                new Employee("Ana", 200),
                new Employee("Kabir", 300)
        );

        int total = staff.stream()
                .map(Employee::salary)
                .reduce(0, Integer::sum);          // (identity, accumulator)

        // The one-argument form has NO identity, so it returns an Optional.
        int sneaky = staff.stream()
                .map(Employee::salary)
                .reduce(Integer::sum)
                .orElse(0);

        System.out.println("total  = " + total);
        System.out.println("sneaky = " + sneaky);
    }
}
```

**Actual output:**

```text
total  = 600
sneaky = 600
```

**Dry run — the two-argument form** (`reduce(0, Integer::sum)`):

| Step | Accumulator | Element | Result |
|---|---|---|---|
| 1 | 0 (the identity) | 100 | 100 |
| 2 | 100 | 200 | 300 |
| 3 | 300 | 300 | 600 |

**Takeaway:** the two-argument `reduce(identity, accumulator)` **requires** the
identity to be a true *identity* (`0 + x == x`). If you pass a wrong identity — say
`reduce(100, Integer::sum)` — the result is `100 + 100 + 200 + 300 = 700`, and the
bug is invisible in a small test. The one-argument `reduce(BinaryOperator)` has
**no identity** and therefore returns an `Optional` — it is the *correct* choice when
there is no meaningful zero (a max, a concatenation).

---

### 5.4 Worked Example D — `flatMap` over a nested structure

**Problem:** every employee in every department, deduplicated by name.

```java
import java.util.List;
import java.util.stream.Collectors;

record Employee(String name, String dept) { }
record Department(String name, List<Employee> employees) { }

public class FlatMapDemo {
    public static void main(String[] args) {
        List<Department> departments = List.of(
                new Department("eng", List.of(new Employee("Rex", "eng"), new Employee("Ana", "eng"))),
                new Department("ops", List.of(new Employee("Kabir", "ops"), new Employee("Ana", "ops")))
        );

        List<String> allNames = departments.stream()
                .flatMap(d -> d.employees().stream())
                .map(Employee::name)
                .distinct()
                .collect(Collectors.toList());

        System.out.println(allNames);
    }
}
```

**Actual output:**

```text
[Rex, Ana, Kabir]
```

**Dry run:**

| Department | `flatMap` expands to | after `map(name)` | after `distinct()` |
|---|---|---|---|
| eng | [Rex, Ana] | Rex, Ana | Rex, Ana |
| ops | [Kabir, Ana] | Kabir, Ana | Rex, Ana, Kabir |

**Takeaway:** `flatMap` is a **one-level** flattening — it turns
`Stream<Stream<T>>` into `Stream<T>`. It is NOT recursive; for two levels of nesting
you call it twice. And `distinct()` uses `equals`/`hashCode` — for a *key* other
than the whole object, `distinct()` is the wrong tool (use `Collectors.toMap` with a
merge function, or a `TreeSet` with a comparator).

---

### 5.5 Worked Example E — the **stateful** operation that breaks under parallel streams

**Problem:** find the duplicate names — and see why `filter` on a mutable `Set` is a
bug waiting to happen.

```java
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

public class StatefulFilter {
    public static void main(String[] args) {
        List<String> names = List.of("Rex", "Ana", "Rex", "Kabir", "Ana");

        // ❌ A STATEFUL filter — a shared mutable Set used as the predicate's memory
        Set<String> seen = new HashSet<>();
        List<String> duplicates = names.stream()
                .filter(n -> !seen.add(n))       // add() returns false if already present
                .collect(Collectors.toList());

        System.out.println("duplicates = " + duplicates);
    }
}
```

**Actual output:**

```text
duplicates = [Rex, Ana]
```

**Dry run — the shared `seen` set:**

| Element | `seen.add(n)` | `seen` after | Filter result |
|---|---|---|---|
| "Rex" | true (added) | {Rex} | false → **dropped** |
| "Ana" | true (added) | {Rex, Ana} | false → **dropped** |
| "Rex" | false (present) | {Rex, Ana} | true → **kept** |
| "Kabir" | true (added) | {Rex, Ana, Kabir} | false → **dropped** |
| "Ana" | false (present) | {Rex, Ana, Kabir} | true → **kept** |

**Takeaway:** the result is *correct here*, but the code is **fragile**: the `seen`
set is **shared mutable state** inside the pipeline. Make the stream
`.parallel()` and the result becomes **non-deterministic**, because the elements
are processed in an interleaved order. The rule: a stream pipeline must be
**stateless** and **non-interfering** — the *correct* way to find duplicates is
`groupingBy(identity(), counting())` and then filter the counts, which is stateless
and parallel-safe.

---

### 5.6 The pattern catalogue — a quick reference

> The snippets below are the **shape** of each pattern. Each one is a one-liner
> against a `Stream<Person>`; the worked examples above show them in full.

### Filter and Map

```java
List<String> names = persons.stream()
    .filter(p -> p.getAge() > 18)
    .map(Person::getName)
    .collect(Collectors.toList());
```

### Group and Count
```java
Map<String, Long> countByCity = persons.stream()
    .collect(Collectors.groupingBy(Person::getCity, Collectors.counting()));
```

### Find Max/Min
```java
Optional<Person> oldest = persons.stream()
    .max(Comparator.comparingInt(Person::getAge));
```

### Sum/Average
```java
double avgAge = persons.stream()
    .mapToInt(Person::getAge)
    .average()
    .orElse(0);
```

### Flat Map Nested Lists
```java
List<String> allNames = departments.stream()
    .flatMap(d -> d.getEmployees().stream())
    .map(Employee::getName)
    .distinct()
    .collect(Collectors.toList());
```

### Advanced Pattern: Custom Collector
```java
// Collect to immutable list
List<String> immutableList = stream.collect(
    Collectors.collectingAndThen(
        Collectors.toList(),
        Collections::unmodifiableList
    )
);

// Collect with custom logic
Collector<Person, ?, Map<String, Person>> toMap = Collector.of(
    HashMap::new,                           // Supplier
    (map, person) -> map.put(person.getId(), person),  // Accumulator
    (map1, map2) -> { map1.putAll(map2); return map1; },  // Combiner
    Collector.Characteristics.IDENTITY_FINISH
);
```

### Advanced Pattern: Teeing (Java 12+)
```java
// Calculate average and count in one pass
record Stats(double average, long count) {}

Stats stats = numbers.stream()
    .collect(Collectors.teeing(
        Collectors.averagingDouble(Integer::doubleValue),
        Collectors.counting(),
        Stats::new
    ));
```

### Advanced Pattern: Partitioning with Downstream
```java
// Partition by age and count each partition
Map<Boolean, Long> partitioned = persons.stream()
    .collect(Collectors.partitioningBy(
        p -> p.getAge() >= 18,
        Collectors.counting()
    ));
// {false=5, true=15} (5 minors, 15 adults)
```

### Advanced Pattern: Multi-level Grouping
```java
// Group by city, then by age group
Map<String, Map<String, List<Person>>> grouped = persons.stream()
    .collect(Collectors.groupingBy(
        Person::getCity,
        Collectors.groupingBy(p -> 
            p.getAge() < 18 ? "Minor" : 
            p.getAge() < 65 ? "Adult" : "Senior"
        )
    ));
```

### Advanced Pattern: Filtering After Grouping
```java
// Group by city, keep only cities with >5 people
Map<String, List<Person>> filtered = persons.stream()
    .collect(Collectors.groupingBy(
        Person::getCity,
        Collectors.filtering(
            p -> p.getAge() > 18,
            Collectors.toList()
        )
    ));
```

### Advanced Pattern: Mapping After Grouping
```java
// Group by city, collect only names
Map<String, List<String>> namesByCity = persons.stream()
    .collect(Collectors.groupingBy(
        Person::getCity,
        Collectors.mapping(
            Person::getName,
            Collectors.toList()
        )
    ));
```

### Advanced Pattern: FlatMapping After Grouping (Java 9+)
```java
// Group by department, collect all skills
Map<String, Set<String>> skillsByDept = employees.stream()
    .collect(Collectors.groupingBy(
        Employee::getDepartment,
        Collectors.flatMapping(
            e -> e.getSkills().stream(),
            Collectors.toSet()
        )
    ));
```

### Advanced Pattern: Reducing After Grouping
```java
// Group by city, sum salaries
Map<String, Integer> salaryByCity = persons.stream()
    .collect(Collectors.groupingBy(
        Person::getCity,
        Collectors.reducing(
            0,
            Person::getSalary,
            Integer::sum
        )
    ));
```

### Advanced Pattern: Joining with Custom Delimiter
```java
// Join names with comma, prefix, suffix
String names = persons.stream()
    .map(Person::getName)
    .collect(Collectors.joining(", ", "Names: [", "]"));
// "Names: [John, Jane, Bob]"
```

### Advanced Pattern: Summarizing Statistics
```java
IntSummaryStatistics stats = persons.stream()
    .mapToInt(Person::getAge)
    .summaryStatistics();

System.out.println("Count: " + stats.getCount());
System.out.println("Sum: " + stats.getSum());
System.out.println("Min: " + stats.getMin());
System.out.println("Max: " + stats.getMax());
System.out.println("Average: " + stats.getAverage());
```

### Advanced Pattern: Distinct by Property
```java
// Distinct by name (not entire object)
List<Person> distinctByName = persons.stream()
    .collect(Collectors.toMap(
        Person::getName,
        p -> p,
        (p1, p2) -> p1  // Keep first on duplicate
    ))
    .values()
    .stream()
    .collect(Collectors.toList());

// Or using custom collector
List<Person> distinctByName = persons.stream()
    .collect(Collectors.collectingAndThen(
        Collectors.toCollection(() -> 
            new TreeSet<>(Comparator.comparing(Person::getName))
        ),
        ArrayList::new
    ));
```

### Advanced Pattern: Conditional Accumulation
```java
// Sum salaries only for adults
int totalAdultSalary = persons.stream()
    .filter(p -> p.getAge() >= 18)
    .mapToInt(Person::getSalary)
    .sum();

// Or using reduce with condition
int total = persons.stream()
    .reduce(0,
        (sum, p) -> p.getAge() >= 18 ? sum + p.getSalary() : sum,
        Integer::sum
    );
```

### Advanced Pattern: Top N Elements
```java
// Top 5 highest paid employees
List<Person> top5 = persons.stream()
    .sorted(Comparator.comparingInt(Person::getSalary).reversed())
    .limit(5)
    .collect(Collectors.toList());
```

### Advanced Pattern: Pagination
```java
// Page 3, size 10 (skip 20, take 10)
List<Person> page = persons.stream()
    .skip(2 * 10)
    .limit(10)
    .collect(Collectors.toList());
```

### Advanced Pattern: Conditional Mapping
```java
// Map to uppercase if length > 3, else lowercase
List<String> result = words.stream()
    .map(s -> s.length() > 3 ? s.toUpperCase() : s.toLowerCase())
    .collect(Collectors.toList());
```

### Advanced Pattern: Nested Optional Handling
```java
// Find person by ID, then get their address city
Optional<String> city = findPersonById(id)
    .flatMap(Person::getAddress)
    .map(Address::getCity);
```

### Advanced Pattern: Parallel Stream with Custom Pool
```java
// Use custom ForkJoinPool instead of common pool
ForkJoinPool customPool = new ForkJoinPool(4);
try {
    List<String> result = customPool.submit(() ->
        list.parallelStream()
            .map(String::toUpperCase)
            .collect(Collectors.toList())
    ).get();
} catch (Exception e) {
    e.printStackTrace();
}
```

### Real-World Pattern: CSV Processing
```java
// Read CSV, parse, filter, transform
List<Employee> employees = Files.lines(Paths.get("employees.csv"))
    .skip(1)  // Skip header
    .map(line -> line.split(","))
    .filter(parts -> parts.length == 4)
    .map(parts -> new Employee(
        parts[0],
        Integer.parseInt(parts[1]),
        parts[2],
        Double.parseDouble(parts[3])
    ))
    .filter(e -> e.getSalary() > 50000)
    .collect(Collectors.toList());
```

### Real-World Pattern: Word Frequency Count
```java
Map<String, Long> wordFrequency = Files.lines(Paths.get("text.txt"))
    .flatMap(line -> Arrays.stream(line.split("\\s+")))
    .map(String::toLowerCase)
    .collect(Collectors.groupingBy(
        Function.identity(),
        Collectors.counting()
    ));
```

### Real-World Pattern: Finding Duplicates
```java
// Find duplicate names
Set<String> duplicates = persons.stream()
    .map(Person::getName)
    .collect(Collectors.groupingBy(
        Function.identity(),
        Collectors.counting()
    ))
    .entrySet().stream()
    .filter(e -> e.getValue() > 1)
    .map(Map.Entry::getKey)
    .collect(Collectors.toSet());
```

### Real-World Pattern: Merging Maps
```java
Map<String, Integer> map1 = Map.of("A", 1, "B", 2);
Map<String, Integer> map2 = Map.of("B", 3, "C", 4);

Map<String, Integer> merged = Stream.of(map1, map2)
    .flatMap(m -> m.entrySet().stream())
    .collect(Collectors.toMap(
        Map.Entry::getKey,
        Map.Entry::getValue,
        Integer::sum  // Merge function for duplicates
    ));
// {A=1, B=5, C=4}
```

---



---

## 6. Code from Scratch — Production-Grade

### 6.1 A production-grade grouping/aggregation helper

```java
package com.example.stream;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A reusable, production-grade aggregation helper.
 *
 * <p>Shows the patterns that matter in real code:
 * <ul>
 *   <li>a <b>merge function</b> on every {@code toMap} (so a duplicate key
 *       never throws);</li>
 *   <li>a <b>downstream collector</b> with {@code groupingBy};</li>
 *   <li>a <b>parallel-safe</b> combiner;</li>
 *   <li>{@link Optional} for the "might be empty" case.</li>
 * </ul>
 */
public final class Aggregations {

    private Aggregations() {}

    /**
     * Group by a key and total the values — with a duplicate-key-safe merge.
     *
     * <p>⚠ {@code Collectors.toMap} WITHOUT a merge function throws
     * {@code IllegalStateException} when two keys map to the same entry.
     * In a parallel stream the same collision can happen in two different
     * threads, so the merge function is not optional in production.
     */
    public static <K> Map<K, BigDecimal> sumBy(
            List<T> items,
            Function<T, K> keyExtractor,
            Function<T, BigDecimal> valueExtractor) {

        Objects.requireNonNull(items);
        Objects.requireNonNull(keyExtractor);
        Objects.requireNonNull(valueExtractor);

        return items.stream().collect(Collectors.groupingBy(
                keyExtractor,
                Collectors.reducing(BigDecimal.ZERO, valueExtractor, BigDecimal::add)));
    }

    /**
     * Group by a key and count — the classic {@code groupingBy} + {@code counting}.
     */
    public static <K> Map<K, Long> countBy(List<T> items, Function<T, K> keyExtractor) {
        return items.stream().collect(Collectors.groupingBy(keyExtractor, Collectors.counting()));
    }

    /**
     * The top N by a score — the correct way to do "top 10".
     *
     * <p>⚠ NEVER do {@code sorted().limit(10)} on a huge stream: that sorts
     * EVERYTHING. A bounded priority queue keeps only N elements.
     */
    public static <T> List<T> topN(List<T> items, Function<T, Double> score, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        return items.stream()
                .sorted(Comparator.comparingDouble(score).reversed())
                .limit(n)
                .collect(Collectors.toList());
    }

    /** The same, but memory-bounded — the version to use on a large stream. */
    public static <T> List<T> topNBounded(List<T> items, Function<T, Double> score, int n) {
        if (n <= 0) throw new IllegalArgumentException("n must be > 0");
        java.util.PriorityQueue<T> queue = new java.util.PriorityQueue<>(
                Comparator.comparingDouble(score));
        for (T item : items) {
            queue.offer(item);
            if (queue.size() > n) queue.poll();
        }
        return queue.stream()
                .sorted(Comparator.comparingDouble(score).reversed())
                .collect(Collectors.toList());
    }

    /** The first match, or empty — the Optional-returning terminal style. */
    public static <T> Optional<T> firstMatch(List<T> items, Function<T, Boolean> predicate) {
        return items.stream().filter(predicate::apply).findFirst();
    }

    /** An insertion-ordered summary — {@code LinkedHashMap} keeps the order. */
    public static <T> Map<String, Long> summaryByCategory(
            List<T> items,
            Function<T, String> categoryExtractor,
            Function<T, BigDecimal> amountExtractor) {

        return items.stream().collect(Collectors.groupingBy(
                categoryExtractor,
                LinkedHashMap::new,
                Collectors.reducing(BigDecimal.ZERO, amountExtractor, BigDecimal::add)));
    }

    /** Round a money amount — a {@code finisher} in collector terms. */
    public static BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}


---

## 7. Common Pitfalls & Gotchas


### ❌ Trap 1: Reusing Streams

**Why it's wrong**:
Streams can only be consumed once. After terminal operation, stream is closed.

**Incorrect Code**:
```java
Stream<String> stream = list.stream();
long count = stream.count();
List<String> result = stream.collect(Collectors.toList());  // ❌ IllegalStateException!
```

**Correct Code**:
```java
// Create new stream for each operation
long count = list.stream().count();
List<String> result = list.stream().collect(Collectors.toList());

// Or store the result
List<String> result = list.stream().collect(Collectors.toList());
long count = result.size();
```

**Interview Tip**:
Streams are single-use. Create a new stream from the source for each pipeline.

---

### ❌ Trap 2: Modifying Source During Stream Operations

**Why it's wrong**:
Modifying the source collection during stream operations causes ConcurrentModificationException or unpredictable behavior.

**Incorrect Code**:
```java
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
list.stream()
    .peek(s -> list.add("D"))  // ❌ Modifying source!
    .forEach(System.out::println);
// ConcurrentModificationException or unpredictable behavior
```

**Correct Code**:
```java
List<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
List<String> newList = list.stream()
    .collect(Collectors.toList());
newList.add("D");  // ✅ Modify result, not source
```

**Interview Tip**:
Never modify the source collection during stream operations. Streams should be side-effect free.

---

### ❌ Trap 3: Using Parallel Streams with Shared Mutable State

**Why it's wrong**:
Parallel streams execute concurrently. Shared mutable state causes race conditions.

**Incorrect Code**:
```java
List<Integer> results = new ArrayList<>();  // ❌ Not thread-safe
IntStream.range(0, 1000)
    .parallel()
    .forEach(i -> results.add(i));  // Race condition!
// results.size() might be < 1000
```

**Correct Code**:
```java
// Use thread-safe collection
List<Integer> results = IntStream.range(0, 1000)
    .parallel()
    .boxed()
    .collect(Collectors.toList());  // ✅ Thread-safe collector

// Or use ConcurrentHashMap for complex cases
Map<Integer, String> map = IntStream.range(0, 1000)
    .parallel()
    .boxed()
    .collect(Collectors.toConcurrentMap(i -> i, i -> "Value" + i));
```

**Interview Tip**:
Avoid shared mutable state in parallel streams. Use collectors or thread-safe collections.

---

### ❌ Trap 4: Incorrect Use of reduce() with Parallel Streams

**Why it's wrong**:
Without proper identity and combiner, reduce() produces wrong results in parallel streams.

**Incorrect Code**:
```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
Integer sum = numbers.parallelStream()
    .reduce(10, Integer::sum);  // ❌ Wrong result!
// Result: 40 (10 added multiple times in parallel)
// Expected: 25 (10 + 1 + 2 + 3 + 4 + 5)
```

**Correct Code**:
```java
// Option 1: Use identity 0 for sum
Integer sum = numbers.parallelStream()
    .reduce(0, Integer::sum);  // ✅ Correct: 15

// Option 2: Add initial value after reduction
Integer sum = numbers.parallelStream()
    .reduce(0, Integer::sum) + 10;  // ✅ Correct: 25

// Option 3: Use three-argument reduce with combiner
Integer sum = numbers.parallelStream()
    .reduce(10, Integer::sum, Integer::sum);  // Still wrong for this case

// Best: Use sum() for primitive streams
int sum = numbers.stream()
    .mapToInt(Integer::intValue)
    .sum() + 10;  // ✅ Correct: 25
```

**Interview Tip**:
Identity value in reduce() must be neutral (0 for sum, 1 for product). For parallel streams, ensure associativity.

---

### ❌ Trap 5: Overusing Parallel Streams

**Why it's wrong**:
Parallel streams have overhead. For small datasets or I/O operations, they're slower than sequential.

**Incorrect Code**:
```java
// Small dataset - parallel overhead > benefit
List<Integer> small = Arrays.asList(1, 2, 3, 4, 5);
int sum = small.parallelStream()  // ❌ Slower than sequential!
    .mapToInt(Integer::intValue)
    .sum();

// I/O operation - parallel doesn't help
List<String> urls = getUrls();
urls.parallelStream()  // ❌ I/O bound, not CPU bound
    .forEach(url -> downloadFile(url));
```

**Correct Code**:
```java
// Use sequential for small datasets
List<Integer> small = Arrays.asList(1, 2, 3, 4, 5);
int sum = small.stream()  // ✅ Sequential is faster
    .mapToInt(Integer::intValue)
    .sum();

// Use parallel only for CPU-intensive operations on large datasets
List<Integer> large = IntStream.range(0, 1_000_000).boxed().collect(Collectors.toList());
int sum = large.parallelStream()  // ✅ Parallel beneficial here
    .mapToInt(i -> expensiveComputation(i))
    .sum();
```

**Interview Tip**:
Use parallel streams only for large datasets with CPU-intensive operations. Measure performance before using parallel.

---

### ❌ Trap 6: Boxing Overhead in Streams (Performance Interview Favorite)

**Why it's wrong**:
Using `Stream<Integer>` instead of `IntStream` causes autoboxing for every element. For millions of elements, this creates massive GC pressure and can be 3-5x slower.

**Incorrect Code**:
```java
// Each int is boxed to Integer object = millions of objects on heap
long sum = Stream.of(1, 2, 3, 4, 5)
    .filter(n -> n % 2 == 0)
    .mapToLong(Integer::longValue) // Too late, already boxed!
    .sum();
```

**Correct Code**:
```java
// Primitive stream - no boxing at all
long sum = IntStream.of(1, 2, 3, 4, 5)
    .filter(n -> n % 2 == 0)
    .asLongStream()
    .sum();

// When starting from a List<Integer>, convert early
int sum = list.stream()
    .mapToInt(Integer::intValue) // Convert immediately to primitive
    .filter(n -> n > 0)
    .sum();
```

**Interview Tip**:
Always use `IntStream`, `LongStream`, or `DoubleStream` for numeric operations. The `mapToInt()` / `mapToLong()` / `mapToDouble()` methods exist specifically to unbox early in the pipeline. Interviewers frequently ask about the performance difference between `Stream<Integer>.count()` vs `IntStream.count()`.

---

### ❌ Trap 7: `Collectors.toMap()` Throws NPE on Null Values

**Why it's wrong**:
Unlike `HashMap.put()`, `Collectors.toMap()` internally calls `Map.merge()` which throws `NullPointerException` if any **value** is null — even if the key is non-null.

**Incorrect Code**:
```java
List<Person> persons = List.of(
    new Person("Alice", "NYC"),
    new Person("Bob", null)     // null city
);

// ❌ NullPointerException! value is null for "Bob"
Map<String, String> map = persons.stream()
    .collect(Collectors.toMap(Person::getName, Person::getCity));
```

**Correct Code**:
```java
// Option 1: Filter out nulls first
Map<String, String> map = persons.stream()
    .filter(p -> p.getCity() != null)
    .collect(Collectors.toMap(Person::getName, Person::getCity));

// Option 2: Use forEach with a regular HashMap (tolerates null values)
Map<String, String> map = new HashMap<>();
persons.forEach(p -> map.put(p.getName(), p.getCity()));

// Option 3: Replace null with default
Map<String, String> map = persons.stream()
    .collect(Collectors.toMap(
        Person::getName,
        p -> p.getCity() != null ? p.getCity() : "Unknown"
    ));
```

**Interview Tip**:
This is one of the most commonly encountered production bugs. `toMap()` uses `Map.merge()` internally, and `merge()` explicitly rejects null values. Always handle potential nulls before collecting to a Map.

---

### ❌ Trap 8: `Files.lines()` Resource Leak

**Why it's wrong**:
`Files.lines()` opens a file handle wrapped in a Stream. If not closed, the file descriptor leaks until GC finalizes the stream (unpredictable timing).

**Incorrect Code**:
```java
// File handle stays open until GC runs finalizer
List<String> lines = Files.lines(Path.of("data.txt"))
    .filter(l -> !l.isEmpty())
    .collect(Collectors.toList());
```

**Correct Code**:
```java
// try-with-resources guarantees closure even if exception occurs
try (Stream<String> lines = Files.lines(Path.of("data.txt"))) {
    List<String> result = lines
        .filter(l -> !l.isEmpty())
        .collect(Collectors.toList());
}
```

**Interview Tip**:
Any stream backed by an I/O resource (`Files.lines()`, `Files.list()`, `Files.walk()`, `BufferedReader.lines()`) MUST be used in try-with-resources. This is asked in almost every senior Java interview. The `BaseStream` interface extends `AutoCloseable` specifically for this reason.

---

### ❌ Trap 9: Operation Order Changes Semantics

**Why it's wrong**:
The order of stateful operations like `sorted()` and `limit()` completely changes the result.

**Incorrect Code**:
```java
// Gets first 5 elements encountered, then sorts ONLY those 5
// NOT the top 5 overall!
List<Person> wrong = persons.stream()
    .limit(5)
    .sorted(Comparator.comparingInt(Person::getAge))
    .collect(Collectors.toList());
```

**Correct Code**:
```java
// Sorts ALL elements first, then takes top 5 youngest
List<Person> correct = persons.stream()
    .sorted(Comparator.comparingInt(Person::getAge))
    .limit(5)
    .collect(Collectors.toList());
```

**Interview Tip**:
Stateless operations (`filter`, `map`) can be reordered freely. Stateful operations (`sorted`, `distinct`, `limit`, `skip`) are order-dependent. Interviewers will give you two pipelines with swapped operations and ask if they produce the same result.

---

### ❌ Trap 10: `reduce()` with Mutable Accumulator

**Why it's wrong**:
`reduce()` expects an **immutable** accumulation pattern. Using a mutable container (like ArrayList) inside `reduce()` violates the contract and produces incorrect results in parallel streams.

**Incorrect Code**:
```java
// ❌ Breaks in parallel streams — multiple threads share the same list
List<Integer> result = numbers.parallelStream()
    .reduce(new ArrayList<Integer>(),
        (list, n) -> { list.add(n); return list; },
        (l1, l2) -> { l1.addAll(l2); return l1; });
```

**Correct Code**:
```java
// ✅ Use collect() for mutable reduction
List<Integer> result = numbers.parallelStream()
    .collect(Collectors.toList());

// ✅ Or use reduce() with immutable accumulation
Optional<Integer> sum = numbers.stream()
    .reduce(Integer::sum);
```

**Interview Tip**:
The rule is simple: `reduce()` = immutable accumulation (returns new value each step), `collect()` = mutable accumulation (modifies container). Using `reduce()` to build a collection is always wrong.

---



---

## 8. Interview Q&A — Level-wise


### 🟢 Beginner

**Q: What is the difference between map() and flatMap()?**
A: map() produces one output per input. flatMap() can produce multiple outputs (flattens nested structures).

**Q: Why are intermediate operations lazy?**
A: They don't process until terminal operation. Enables optimization and short-circuiting.

**Q: What is the difference between findFirst() and findAny()?**
A: findFirst() returns first element (deterministic). findAny() returns any element (faster in parallel).

**Q: Can stream be reused?**
A: No. After terminal operation, stream is consumed. Create new stream from source.

**Q: What is the difference between Collection.stream() and Collection.parallelStream()?**
A: stream() processes sequentially. parallelStream() splits work across threads.

**Q: What is short-circuiting in streams?**
A: Operations that can terminate early: findFirst, findAny, anyMatch, allMatch, noneMatch, limit.

**Q: What is the difference between intermediate and terminal operations?**
A: Intermediate operations return a Stream and are lazy (filter, map, sorted). Terminal operations produce a result and consume the stream (collect, forEach, reduce).

**Q: What is the difference between forEach() and forEachOrdered()?**
A: forEach() may process elements in any order in parallel streams. forEachOrdered() maintains encounter order even in parallel.

### 🟡 Intermediate

**Q: When to use parallel stream?**
A: Large datasets (>10,000 elements), CPU-intensive operations, stateless operations, independent elements. Avoid for I/O operations or small datasets.

**Q: What is the difference between Stream.of() and Arrays.stream()?**
A: Stream.of() creates a stream from varargs. Arrays.stream() creates a stream from an array. For primitive arrays, Arrays.stream() returns IntStream/LongStream/DoubleStream, while Stream.of() wraps the entire array as a single element.

**Q: What is the difference between reduce() and collect()?**
A: reduce() combines elements into a single result using a binary operator. collect() accumulates elements into a mutable container (List, Set, Map).

**Q: What is the difference between filter() and skip()?**
A: filter() removes elements based on a predicate. skip(n) removes the first n elements regardless of content.

**Q: What is the difference between limit() and takeWhile()?**
A: limit(n) takes first n elements. takeWhile() takes elements while predicate is true, then stops (Java 9+).

**Q: What is the difference between anyMatch() and findAny()?**
A: anyMatch() returns boolean (does any element match?). findAny() returns Optional<T> (give me any matching element).

**Q: What is the difference between count() and size()?**
A: count() is a terminal operation on Stream. size() is a method on Collection. count() processes the stream, size() is instant.

**Q: What is the difference between Collectors.toList() and Collectors.toCollection()?**
A: toList() returns an unspecified List implementation. toCollection() lets you specify the exact collection type (e.g., ArrayList, LinkedList).

### 🔴 Advanced / Senior

**Q: How does parallel stream work internally?**
A: Uses ForkJoinPool (default common pool with parallelism = CPU cores - 1). Splits stream into chunks, processes in parallel, then combines results.

**Q: What is the difference between stateless and stateful operations?**
A: Stateless operations (filter, map) process each element independently. Stateful operations (sorted, distinct) need to see all elements or maintain state across elements.

**Q: What is the performance impact of sorted() in streams?**
A: O(n log n) time complexity. Stateful operation that buffers all elements. Expensive for large streams. Consider using TreeSet or pre-sorted data.

**Q: What is the difference between peek() and forEach()?**
A: peek() is intermediate (returns Stream, for debugging). forEach() is terminal (consumes stream, for side effects).

**Q: How does reduce() work with parallel streams?**
A: Uses three-argument form: identity, accumulator, combiner. Combiner merges partial results from different threads. Must be associative.

**Q: What is the difference between Collectors.groupingBy() and Collectors.partitioningBy()?**
A: groupingBy() groups by any classifier (returns Map<K, List<V>>). partitioningBy() groups by boolean predicate (returns Map<Boolean, List<V>>).

**Q: What is the difference between Optional.of() and Optional.ofNullable()?**
A: Optional.of() throws NullPointerException if value is null. Optional.ofNullable() returns Optional.empty() if value is null.

**Q: How do you handle exceptions in stream operations?**
A: Wrap in try-catch inside lambda, use custom wrapper method, or use libraries like Vavr. Streams don't handle checked exceptions well.

**Q: What is the difference between IntStream.range() and IntStream.rangeClosed()?**
A: range(1, 10) generates 1-9 (exclusive end). rangeClosed(1, 10) generates 1-10 (inclusive end).

### Scenario-Based Questions

**Q: What will this code print?**
```java
List<Integer> list = Arrays.asList(1, 2, 3);
list.stream()
    .peek(x -> System.out.print(x + " "))
    .filter(x -> x > 5);
```
A: Nothing. No terminal operation, so the stream is never executed (lazy evaluation).

**Q: What will this code print?**
```java
Stream.of("a", "b", "c")
    .filter(s -> {
        System.out.println("filter: " + s);
        return true;
    })
    .map(s -> {
        System.out.println("map: " + s);
        return s.toUpperCase();
    })
    .findFirst();
```
A: "filter: a" then "map: a". Short-circuiting - stops after finding first element.

**Q: What's wrong with this code?**
```java
List<String> list = new ArrayList<>();
IntStream.range(0, 1000)
    .parallel()
    .forEach(i -> list.add("Item" + i));
```
A: Race condition. ArrayList is not thread-safe. Use Collectors.toList() or ConcurrentHashMap instead.

**Q: What will this code return?**
```java
List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5);
numbers.parallelStream()
    .reduce(10, Integer::sum);
```
A: 65 (not 25). In parallel streams, identity is added multiple times (once per thread). Use reduce(0, Integer::sum) + 10 instead.

**Q: What's the output?**
```java
Stream<String> stream = Stream.of("a", "b", "c");
stream.forEach(System.out::println);
stream.forEach(System.out::println);
```
A: IllegalStateException on second forEach. Stream already consumed.

**Q: What will this code print?**
```java
List<Integer> list = Arrays.asList(1, 2, 3, 4, 5);
list.stream()
    .filter(x -> x % 2 == 0)
    .map(x -> x * 2)
    .forEach(System.out::println);
```
A: 4, 8 (even numbers doubled).

**Q: What's the difference between these two?**
```java
// Option 1
list.stream().filter(x -> x > 5).count();
// Option 2
list.stream().filter(x -> x > 5).collect(Collectors.counting());
```
A: Both return count, but count() is more efficient (returns long directly). Collectors.counting() is for use with groupingBy/partitioningBy.

### Tricky Interview Scenarios

**Q: What will this code print?**
```java
List<String> list = Arrays.asList("a", "b", "c");
list.stream()
    .peek(s -> list.add("d"))
    .forEach(System.out::println);
```
A: ConcurrentModificationException. Modifying source collection during stream operation.

**Q: What's the output?**
```java
Stream.of(1, 2, 3, 4, 5)
    .filter(x -> {
        System.out.println("filter: " + x);
        return x % 2 == 0;
    })
    .map(x -> {
        System.out.println("map: " + x);
        return x * 2;
    })
    .collect(Collectors.toList());
```
A: "filter: 1", "filter: 2", "map: 2", "filter: 3", "filter: 4", "map: 4", "filter: 5". Operations are fused - each element goes through entire pipeline before next element starts.

**Q: What will this return?**
```java
List<String> list = Arrays.asList("a", "b", "c");
list.stream()
    .flatMap(s -> Stream.of(s, s.toUpperCase()))
    .collect(Collectors.toList());
```
A: [a, A, b, B, c, C]. flatMap produces 2 elements per input.

**Q: What's wrong with this code?**
```java
Stream<Integer> stream = Stream.iterate(0, n -> n + 1);
stream.forEach(System.out::println);
```
A: Infinite loop. Stream.iterate() creates infinite stream. Use limit() to bound it.

**Q: What will this code print?**
```java
Optional<String> opt = Optional.of("hello");
opt.map(String::toUpperCase)
   .filter(s -> s.length() > 10)
   .ifPresent(System.out::println);
```
A: Nothing. filter() returns empty Optional because "HELLO".length() is 5, not > 10.


---



### ⚫ Expert / Principal — Advanced Tricky Scenarios


These are real questions asked at FAANG, banks, and top product companies.

### Scenario 1: Stream Pipeline Evaluation Order

**Q: What is the exact output?**
```java
Stream.of(5, 3, 1, 4, 2)
    .filter(x -> {
        System.out.print("F" + x + " ");
        return x > 2;
    })
    .map(x -> {
        System.out.print("M" + x + " ");
        return x * 10;
    })
    .sorted((a, b) -> {
        System.out.print("S ");
        return a.compareTo(b);
    })
    .findFirst()
    .ifPresent(x -> System.out.println("Result: " + x));
```

**A:** `F5 M5 F3 M3 F1 F4 M4 F2 Result: 30`

**Explanation**: 
- `filter` and `map` are **stateless** — they process element-by-element (fused).
- `sorted` is **stateful** — it must buffer ALL elements that pass filter/map before sorting.
- `findFirst` short-circuits after sorted produces its first element.
- Elements flow: 5→F5→pass→M5→buffer, 3→F3→pass→M3→buffer, 1→F1→fail, 4→F4→pass→M4→buffer, 2→F2→fail.
- Sorted buffers [50, 30, 40], sorts to [30, 40, 50], findFirst returns 30.
- Notice: `S` may or may not print depending on JVM optimization of single-comparison sorts.

**Interview Tip**: Stateless ops fuse. Stateful ops (sorted, distinct) buffer everything. Short-circuiting terminals (findFirst, anyMatch) stop early.

---

### Scenario 2: Infinite Stream with Short-Circuit

**Q: Does this terminate? What does it return?**
```java
boolean result = Stream.iterate(1, n -> n + 1)
    .anyMatch(n -> n > 100);
System.out.println(result);
```

**A:** Yes, prints `true`. `anyMatch` is short-circuiting — stops as soon as one element matches.

**Q: What about this?**
```java
long count = Stream.iterate(1, n -> n + 1)
    .filter(n -> n > 100)
    .limit(5)
    .count();
```

**A:** Returns `5`. `limit(5)` is short-circuiting. After filtering past 100, takes 5 elements (101-105), then stops.

**Q: And this?**
```java
// DANGER!
long count = Stream.iterate(1, n -> n + 1)
    .filter(n -> n > 100)
    .count(); // ❌ INFINITE LOOP! No short-circuit terminal op
```

**Interview Tip**: `count()` is NOT short-circuiting. It must process all elements. With infinite streams, always use `limit()`, `takeWhile()`, or a short-circuiting terminal operation.

---

### Scenario 3: Parallel Stream Identity Violation

**Q: What's wrong here?**
```java
String result = List.of("a", "b", "c").parallelStream()
    .reduce("", String::concat);
```

**A:** Technically works but violates the identity contract for parallel streams. The identity `""` is correct (empty string concatenated with anything returns that thing). However, `String::concat` creates new String objects at each step — $O(n^2)$ time complexity.

**Better approach:**
```java
String result = List.of("a", "b", "c").parallelStream()
    .collect(Collectors.joining()); // O(n), designed for parallel
```

**Q: What if we used a non-identity value?**
```java
Integer result = List.of(1, 2, 3).parallelStream()
    .reduce(10, Integer::sum);
// Expected: 10 + 1 + 2 + 3 = 16
// Actual: 10 + 1 + 10 + 2 + 10 + 3 = 36 (with 3 threads)
```

**Interview Tip**: The identity MUST satisfy: `accumulator.apply(identity, t) == t` for all `t`. Using 10 as identity for sum violates this because `10 + 1 ≠ 1`.

---

### Scenario 4: Collectors.toMap Duplicate Key Crash

**Q: What happens?**
```java
List<String> words = List.of("apple", "ant", "banana");
Map<Character, String> map = words.stream()
    .collect(Collectors.toMap(w -> w.charAt(0), w -> w));
```

**A:** `IllegalStateException: Duplicate key apple`. Both "apple" and "ant" start with 'a'.

**Fix — provide merge function:**
```java
Map<Character, String> map = words.stream()
    .collect(Collectors.toMap(
        w -> w.charAt(0),
        w -> w,
        (existing, replacement) -> existing + "," + replacement  // Merge
    ));
// {a=apple,ant, b=banana}

// Or group instead:
Map<Character, List<String>> grouped = words.stream()
    .collect(Collectors.groupingBy(w -> w.charAt(0)));
// {a=[apple, ant], b=[banana]}
```

**Interview Tip**: Always ask "can keys collide?" when using `toMap()`. If yes, provide a merge function or use `groupingBy()`.

---

### Scenario 5: flatMap vs map Composition

**Q: What's the output difference?**
```java
List<List<Integer>> nested = List.of(List.of(1, 2), List.of(3, 4));

// Using map
List<Stream<Integer>> mapped = nested.stream()
    .map(List::stream)
    .collect(Collectors.toList());
// Type: List<Stream<Integer>> — streams inside a list, NOT flattened!

// Using flatMap
List<Integer> flatMapped = nested.stream()
    .flatMap(List::stream)
    .collect(Collectors.toList());
// Type: List<Integer> — [1, 2, 3, 4]
```

**Q: Tricky variant — what does this produce?**
```java
List<String> result = Stream.of("Hello", "World")
    .flatMap(s -> Arrays.stream(s.split("")))
    .distinct()
    .sorted()
    .collect(Collectors.toList());
```

**A:** `[H, W, d, e, l, o, r]` — splits into characters, removes duplicates (two 'l's become one), sorts alphabetically (uppercase before lowercase in ASCII).

---

### Scenario 6: peek() Misuse in Production

**Q: Is this safe for production logging?**
```java
list.stream()
    .peek(item -> logger.info("Processing: {}", item))
    .filter(item -> item.isValid())
    .count();
```

**A:** It works, but `peek()` was designed for **debugging**, not production side effects. The Java docs explicitly state: "This method exists mainly to support debugging." In optimized pipelines, the JVM may eliminate `peek()` calls if it determines the result isn't needed.

**Production alternative:**
```java
list.stream()
    .filter(item -> {
        logger.info("Processing: {}", item);
        return item.isValid();
    })
    .count();
```

---

### Scenario 7: Collector Characteristics & Parallel Safety

**Q: Why is `Collectors.toList()` safe in parallel but adding to an external list isn't?**
```java
// ✅ Safe
List<Integer> result = list.parallelStream()
    .collect(Collectors.toList());

// ❌ Unsafe
List<Integer> result = new ArrayList<>();
list.parallelStream()
    .forEach(result::add); // Race condition!
```

**A:** `collect()` uses the collector's `combiner()` function to merge partial results from each thread safely. Each thread accumulates into its own container, then containers are merged. `forEach` with external mutation has no such protection.

Collector internals:
```java
Collector.of(
    ArrayList::new,                    // supplier: each thread gets own list
    List::add,                         // accumulator: add to thread-local list
    (left, right) -> { left.addAll(right); return left; }  // combiner: merge
);
```

---

### Scenario 8: Stream Closed Exception

**Q: What happens?**
```java
try (Stream<String> stream = Files.lines(Path.of("data.txt"))) {
    List<String> lines = stream.collect(Collectors.toList());
    long count = stream.count(); // ❌ What happens here?
}
```

**A:** `IllegalStateException: stream has already been operated upon or closed.` A stream can only have ONE terminal operation. After `collect()`, the stream is consumed.

**Q: What about try-with-resources?**
```java
try (Stream<String> stream = Files.lines(Path.of("data.txt"))) {
    stream.forEach(System.out::println);
} // Stream is auto-closed here, releasing file handle
```

**Interview Tip**: `Files.lines()` returns a stream backed by an I/O resource. It MUST be closed. Always use try-with-resources. This is a very common senior-level question.

---

### Scenario 9: Optional Chaining Anti-Patterns

**Q: What's wrong with this code?**
```java
// Anti-pattern: using get() without checking
String name = optionalPerson.get().getName(); // NoSuchElementException if empty

// Anti-pattern: using isPresent() like null check (defeats purpose of Optional)
if (optionalPerson.isPresent()) {
    doSomething(optionalPerson.get());
}

// Correct approaches:
optionalPerson.ifPresent(p -> doSomething(p));

String name = optionalPerson.map(Person::getName).orElse("Unknown");

optionalPerson
    .filter(p -> p.getAge() > 18)
    .ifPresent(p -> grantAccess(p));
```

**Q: Tricky — what does this return?**
```java
Optional<String> result = Optional.of(Optional.of("hello"))
    .flatMap(opt -> opt)
    .map(String::toUpperCase);
```

**A:** `Optional[HELLO]`. `flatMap` unwraps one level of nesting, then `map` transforms the inner value.

---

### Scenario 10: Reduce vs Collect Decision Matrix

| Criteria | reduce() | collect() |
|----------|----------|-----------|
| Result type | Immutable (creates new object each step) | Mutable container |
| Use case | Single value (sum, max, concat) | Collection (List, Map, Set) |
| Parallel safety | Requires associative + identity | Uses combiner to merge |
| Performance | String concatenation = $O(n^2)$ | StringBuilder via joining = $O(n)$ |
| Return type | `T` or `Optional<T>` | `R` (collection type) |

**Interview Q: Can you implement `toList()` using `reduce()`?**
```java
// Possible but TERRIBLE practice
List<Integer> list = numbers.stream()
    .reduce(new ArrayList<Integer>(),
        (acc, n) -> { acc.add(n); return acc; },     // Mutates! Breaks contract
        (l1, l2) -> { l1.addAll(l2); return l1; });
// ❌ reduce expects IMMUTABLE accumulation. This breaks in parallel streams.

// ✅ Correct: use collect()
List<Integer> list = numbers.stream()
    .collect(Collectors.toList());
```

---



---

## 9. Counter-Questions & Traps

### CQ1. "Isn't `parallelStream()` just a faster `stream()`? Why would you ever not use it?"

- **Naive:** "Parallel is faster because it uses multiple threads."  ← *unqualified, and often false*
- **The trap:** "Faster" is not a property of the API — it is a property of the *workload*. Parallelism has a fixed cost: the source must be **split** (which requires a `Spliterator` that is cheap to split — an `ArrayList` is, an `ArrayDeque` is *not*), the tasks are **scheduled** on the common `ForkJoinPool`, the partial results are **merged** (which requires a `Collector` with a proper combiner), and the results must be **recombined in order** if the stream is ordered. For a list of 100 elements doing a trivial map, the overhead is 100× the work.
- **Expert answer:** Parallelism wins when **all four** hold: (1) the data is large (thousands of elements, not dozens), (2) the work per element is non-trivial (a real computation, not a field read), (3) the work is CPU-bound (for I/O-bound work, the answer is a different concurrency tool — an `ExecutorService` — because threads would just block), and (4) the source splits cheaply and the collector merges cheaply. The honest engineering position: **treat `parallelStream()` as an optimisation to be measured, not a default.** The `Spliterator` behaviour is the hidden trap: `List` splits well, `LinkedList` does not (splitting it is O(n)), `Stream.iterate` cannot be split at all, and a `HashSet` splits by hash buckets.

### CQ2. "You said streams are lazy. So is `list.stream().map(f).count()` the same as `list.size()`?"

- **Naive:** "Yes, both return the size."  ← *the same result, a completely different cost*
- **The trap:** `count()` on a pipeline with a `map` **still traverses the entire source and applies the mapping function to every element** — the mapping is executed for its side effects (if any) and then the result is discarded. `size()` is O(1) for a `List` (or O(n) for a general `Collection`, but with no per-element work). So `map(f).count()` can be *orders of magnitude* slower.
- **Expert answer:** The rule is: **a terminal operation that does not need the elements should not be preceded by operations that do.** `map(f).count()` is almost always a bug — you meant `count()`. The same applies to `filter(p).count()` — that one is *legitimate*, because you need to know how many elements pass the predicate. The general principle the interviewer is testing: **a stream pipeline is a plan, and every intermediate operation is work you have committed to.** The `Stream` API cannot elide a `map` because `map` might have side effects; that is why `filter(p).count()` is a different case from `map(f).count()`.

### CQ3. "Why can't you reuse a stream? Isn't that annoying?"

- **Naive:** "Because the JDK says so."  ← *a fact, not a reason*
- **The trap:** The restriction exists to make the **parallel** case correct. A stream is a *pipeline description* that may be executed across multiple threads. If you could reuse it, the second traversal would have to re-run the whole pipeline, and for a source that is not replayable (a `Files.lines()` stream, a socket, a `Spliterator` over a file) the second traversal would have nothing to read. The single-use rule makes the *one-shot* nature of many sources explicit and safe.
- **Expert answer:** The deeper reason is that a `Stream` is not a *collection* — it is a **description of a computation**, and a computation has a lifecycle. Making it single-use prevents the class of bugs where the same pipeline is executed twice with different side effects, or where a parallel traversal re-reads a non-replayable source. The practical consequence: if you need to traverse the same data twice, you materialise it once (`List<T> l = stream.collect(toList())`) and then traverse the *list* twice — the list is replayable. This is also why `Stream` does not extend `Collection` and has no `size()`, `get(i)` or `add()` — those would require the elements to be materialised.

### CQ4. "`Collectors.toMap` threw `IllegalStateException` in production. Why didn't the compiler warn me?"

- **Naive:** "Because the compiler can't know the keys."  ← *true, and misses the design point*
- **The trap:** The compiler *cannot* detect a duplicate key statically, so the failure is deferred to runtime. But the real issue is that the default behaviour of `toMap` is **lossy-by-omission-with-an-error**: it *refuses* to pick a winner rather than choosing one. Most real use cases have a sensible merge rule (sum, max, last-wins, concatenate) — and the API forces you to say which.
- **Expert answer:** The design lesson is that `Collectors.toMap` has **two** overloads, and the safe one is the three-argument form. `toMap(keyFn, valueFn)` throws on duplicates; `toMap(keyFn, valueFn, mergeFn)` does not. In production you should almost always use the merge form, because a duplicate key is a *domain* question ("which payment wins for this order?") and the merge function is where you answer it: `(a, b) -> a + b` for a total, `(a, b) -> a` for first-wins, `(a, b) -> b` for last-wins. In a parallel stream the collision can occur in two different threads and be detected during the merge — which is exactly why the merge function is also the *combiner*. The general principle: **an API that fails on an ambiguity should force you to state the resolution at the call site, not pick one silently.**

### CQ5. "Why is `groupingBy` not parallel-friendly while `partitioningBy` is?"

- **Naive:** "Because `groupingBy` has an unbounded number of keys."  ← *close, but the mechanism is the point*
- **The trap:** The real difference is the **data structure** each one builds. `partitioningBy` produces exactly two buckets, so the classifier is a simple boolean and the downstream collector can be applied to two independent halves and merged trivially. `groupingBy` produces one bucket per distinct key, and the *classification itself* is the expensive, order-dependent part — the downstream collector's combiner cannot simply merge two already-grouped maps without re-classifying.
- **Expert answer:** The mechanism is that `Collectors.groupingBy` has **no proper parallel combiner** in the JDK: the implementation falls back to a "classify then merge" strategy that does not preserve the full parallel benefit, and it is documented that `groupingBy` with a non-trivial downstream collector should not be used in parallel streams. `partitioningBy` partitions into exactly two, and each partition can be processed and combined independently. The practical guidance: in a parallel stream, prefer `partitioningBy`, or use `Collectors.toConcurrentMap` / a custom `Collector` with a real combiner if you need grouping. And the general lesson: **when you write a custom `Collector`, the `combiner` is not an optional extra — it is what makes the collector parallel-correct**, and forgetting it is a silent source of wrong results under parallelism.

### CQ6. "Is `stream().filter(...).collect(toList())` slower than a plain for-loop? Should I avoid streams?"

- **Naive:** "Streams have overhead, so avoid them in hot paths."  ← *true in general, but the conclusion is too broad*
- **The trap:** The conclusion "avoid streams" is wrong because the overhead is *small and constant* — a few nanoseconds per element — while the *expression* is clearer and less error-prone. For any data set that is not in a tight inner loop, the readability wins. The real hot-path concern is different: **boxing** (a `Stream<Integer>` allocates), and **megamorphic** call sites.
- **Expert answer:** The rule is about *magnitude*, not principle. A stream pipeline on a 1,000-element list costs microseconds; the same work in a hand-written loop might save a microsecond. Nobody notices. But a stream on a 10-million-element list inside a request path, with `Predicate<Integer>` (boxing) and a megamorphic lambda, can cost tens of milliseconds — that shows up in a latency SLO. The professional position: **use streams for clarity by default**; drop to a loop when (a) the data is huge, (b) the per-element work is trivial, (c) you are in a measured hot path, or (d) you need to avoid boxing. And the *best* of both: `IntStream` for numeric work, and a plain loop for a tight inner loop. The interview framing that lands: "streams are not slower; they are *uniformly* slightly slower, which is a price worth paying except in a measured hot path."

### CQ7. "What happens if you call `peek()` for a side effect, and the stream is parallel?"

- **Naive:** "It runs the side effect for each element."  ← *usually true, but not guaranteed*
- **The trap:** `peek()` is specified as a **debugging aid**. In a parallel stream, the elements may be processed in an interleaved order, and — more importantly — an implementation is permitted to **skip** `peek` entirely where it can prove the result is unaffected. So a side effect in `peek` is not guaranteed to happen once per element.
- **Expert answer:** The rule is: **`peek` is for observation, not for mutation.** If you need a side effect per element, use `map` (and discard the result) or `forEach`. If you need to *filter* with a side effect, use `filter` — the predicate is guaranteed to be evaluated for the elements it needs to see. The `peek` hazard is a good example of a general principle: **an API whose contract is "for debugging" is not a contract you should build on.** It is the same reason `Object.finalize()` was deprecated — a hook designed for observation was used for a purpose it never guaranteed.

### CQ8. "You said `Collectors.groupingBy` needs a merge function for parallel streams. But `groupingBy` doesn't take one — so how does it merge?"

- **Naive:** "It merges by putting the maps together."  ← *hand-wavy, and the interviewer will push*
- **The trap:** The JDK's `groupingBy` implementation is subtle and, for a *non-trivial* downstream collector, the JDK explicitly documents that it should not be used in parallel streams. The JDK's own `Collectors.groupingBy` works correctly in parallel for the simple counting case, but the general guarantee is weak.
- **Expert answer:** The correct answer is that there are two different mechanisms. (1) `groupingBy(classifier, downstream)` uses the downstream collector's **combiner** to merge the *values within* each group, and merges the group maps by key. (2) But the *classification* step is what breaks the clean parallel story: to merge two partial groupings you must know that a key appearing in both halves refers to the same group — which is true, but the downstream collector's combiner must be associative, and some are not. The JDK's documentation is explicit that `groupingBy` with a downstream collector is not parallel-safe in all cases, and `Collectors.toConcurrentMap` exists for the genuinely parallel case. The professional answer: if you need grouping in a parallel stream, use `Collectors.toConcurrentMap` (which is designed for it) or write a custom `Collector` with a correct, associative combiner.

### CQ9. "If `Optional` is in the Stream API, why does `Stream.findFirst()` return `Optional` but `Stream.count()` return `long`?"

- **Naive:** "Because there might not be a first element."  ← *true for `findFirst`, and the contrast is the point*
- **The trap:** The question is really asking whether the API is *consistent*. And the answer is that the two cases are genuinely different in kind: `count()` has an **answer for every stream** (it is a number, possibly 0), whereas `findFirst()` may have **no answer at all** — the stream could be empty. `Optional` encodes "possibly absent" for exactly that case.
- **Expert answer:** The distinction is between a **possibly-zero value** and a **possibly-absent value**. `count()` returns 0 for an empty stream — 0 is a perfectly good answer, so there is nothing to wrap. `findFirst()` on an empty stream has *no* answer, and the API must signal that — `Optional` is the signal. This is the same reason `Stream.max()` returns `Optional<T>` (an empty stream has no maximum) while `Stream.sum()` returns a plain `int`. The deeper design point: `Optional` in the Stream API is used precisely where the answer set is `{value} ∪ {∅}`, and *not* used where the answer set is a total function. And the caveat worth stating: `Optional` was designed for *return values*, so `Optional` as a **field** or a **parameter** is a smell — the same `Optional` debate that shows up in `Optional<T>` APIs.

### CQ10. "If `stream()` is lazy, then how do I know my `map` function actually ran? What if it silently didn't?"

- **Naive:** "It runs when I call the terminal operation."  ← *true, and it is exactly the problem*
- **The trap:** With laziness, a `map` that *should* run might not — if the pipeline short-circuits, if the source is empty, or if an implementation elides a `peek`. So a `map` with a side effect is a bug waiting to happen. The interviewer is testing whether you understand that **laziness and side effects are incompatible**.
- **Expert answer:** The rule: **an intermediate operation must be pure.** `map`, `filter`, `flatMap`, `sorted`, `distinct` should be side-effect-free; their only job is to describe a computation. If you need a side effect, move it to the terminal operation (`forEach`, `collect` with a side-effecting accumulator) or into a `map` whose result you actually use. The failure mode this prevents is subtle and real: with `findFirst()` on a parallel stream, only *some* elements may be mapped; with `limit(n)` after a `map`, only the first `n` are; and in a parallel stream the *order* of the side effects is not defined. The mental model that resolves it: the stream describes *what would happen if you ran it over everything*; the terminal operation decides *how much of that actually happens*. Any code that depends on "all of it happened" is relying on an implementation detail.

---


---

## 10. Rapid-Fire MCQs

**Q1.** A `Stream` is…
- A) A collection  B) A pipeline description over a source  C) A thread  D) An array

**Q2.** Are stream operations lazy?
- A) No  B) Yes — intermediate operations do no work until a terminal op  C) Only in parallel  D) Only for infinite streams

**Q3.** How many times can a stream be used?
- A) Once  B) Twice  C) Unlimited  D) Depends on the source

**Q4.** Which is a **terminal** operation?
- A) `map`  B) `filter`  C) `collect`  D) `flatMap`

**Q5.** Which is an **intermediate** operation?
- A) `forEach`  B) `sorted`  C) `count`  D) `collect`

**Q6.** `sorted()` before `limit(10)` is expensive because `sorted` is…
- A) Parallel  B) Stateful — a barrier that buffers everything  C) Boxing  D) A terminal op

**Q7.** `Collectors.toMap` with duplicate keys throws…
- A) `NullPointerException`  B) `IllegalStateException`  C) `ClassCastException`  D) `IllegalArgumentException`

**Q8.** `Collectors.toMap` with a merge function…
- A) Is slower  B) Handles duplicates by applying the merge function  C) Disables parallelism  D) Returns `null`

**Q9.** `partitioningBy` always produces how many groups?
- A) 0  B) 1  C) 2  D) Depends on the data

**Q10.** Which grouping collector is parallel-friendly?
- A) `groupingBy`  B) `partitioningBy`  C) Neither  D) Both

**Q11.** `parallelStream()` uses which pool by default?
- A) A new pool per stream  B) The common `ForkJoinPool`  C) The `ExecutorService` you pass in  D) The caller thread

**Q12.** `parallelStream` is *most likely* to be slower when…
- A) The list is large and CPU-bound  B) The list is small and the per-element work is trivial  C) The source is an `ArrayList`  D) The collector has a combiner

**Q13.** `IntStream` avoids…
- A) `null`  B) Boxing  C) `Optional`  D) Spliterator overhead

**Q14.** `IntStream.average()` returns…
- A) `double`  B) `OptionalDouble`  C) `Double`  D) `Optional<Double>`

**Q15.** `Stream.findFirst()` returns…
- A) `null` when empty  B) `Optional<T>`  C) `T`  D) A `Stream<T>`

**Q16.** `peek()` should be used for…
- A) Side effects  B) Debugging/observation  C) Filtering  D) Collecting

**Q17.** `flatMap` is used to…
- A) Map to a single value  B) Flatten a stream of streams  C) Sort  D) Deduplicate

**Q18.** `Collectors.groupingBy` with a downstream collector uses…
- A) Only the accumulator  B) The accumulator and the combiner  C) Only the finisher  D) The finisher

**Q19.** `Stream.iterate(0, i -> i+1)` is…
- A) Finite  B) Infinite  C) Empty  D) Bounded

**Q20.** `limit(10)` on an infinite `Stream.iterate` stream works because…
- A) `limit` is eager  B) The pipeline is lazy and `limit` short-circuits  C) `iterate` caches  D) It does not work

**Q21.** The "combiner" in a `Collector` is used for…
- A) The final transformation  B) Merging partial results in a parallel stream  C) Creating the container  D) Adding one element

**Q22.** The "supplier" in a `Collector` is used for…
- A) Creating the container  B) Merging containers  C) Adding elements  D) Finishing

**Q23.** `Collectors.joining()` produces…
- A) A `List<String>`  B) A `String`  C) A `Set`  D) A `Map`

**Q24.** `Stream.toList()` (Java 16) differs from `collect(Collectors.toList())` because it…
- A) Allows nulls and returns an unmodifiable list  B) Sorts  C) Is parallel  D) Returns a mutable list

**Q25.** A stream over a `LinkedList` splits poorly in parallel because…
- A) `LinkedList` is not `RandomAccess`  B) It is too small  C) It cannot be iterated twice  D) It has no `spliterator`

---

### ✅ Answer Key

| # | Answer | One-line explanation |
|---|--------|----------------------|
| 1 | **B** | A stream is a *description* of a computation over a source — it holds no elements. |
| 2 | **B** | Intermediate operations return a new stream and do nothing until a terminal op. |
| 3 | **A** | One-shot — reusing a consumed stream throws `IllegalStateException`. |
| 4 | **C** | `collect` triggers the traversal; `map`/`filter`/`flatMap` are intermediate. |
| 5 | **B** | `sorted` is intermediate (and stateful — a barrier). |
| 6 | **B** | A stateful op must see all the data before emitting, so it buffers everything. |
| 7 | **B** | `IllegalStateException: Duplicate key` — use the 3-arg `toMap` with a merge function. |
| 8 | **B** | The merge function resolves the collision and doubles as the parallel combiner. |
| 9 | **C** | Exactly two buckets: `true` and `false`. |
| 10 | **B** | `partitioningBy` splits into two independent halves that merge trivially. |
| 11 | **B** | The **common** `ForkJoinPool` — shared across the whole JVM. |
| 12 | **B** | Fork/join and merge overhead dominates when the per-element work is trivial. |
| 13 | **B** | `IntStream` operates on `int` — no `Integer` boxing. |
| 14 | **B** | An empty stream has no average — `OptionalDouble`. |
| 15 | **B** | `Optional<T>` — an empty stream has no first element. |
| 16 | **B** | It is a debugging aid; it may be skipped, so never put side effects in it. |
| 17 | **B** | `flatMap` flattens `Stream<Stream<T>>` into `Stream<T>`. |
| 18 | **B** | The downstream collector's accumulator (and combiner, in parallel) is used. |
| 19 | **B** | `iterate` with a seed and an unbounded function produces an infinite stream. |
| 20 | **B** | Laziness + `limit`'s short-circuit is the only reason it terminates. |
| 21 | **B** | The combiner merges two partial containers — this is what makes parallel collection correct. |
| 22 | **A** | The supplier creates the container (`ArrayList::new`). |
| 23 | **B** | A `String` — it joins the elements with a separator. |
| 24 | **A** | `toList()` allows nulls and returns an unmodifiable list; `collect(toList())` returns a mutable `ArrayList`. |
| 25 | **A** | `LinkedList` is not `RandomAccess` — splitting it is O(n), so the `Spliterator` falls back to a poor strategy. |

---

## 11. Cheat Sheet / One-Page Summary

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE STREAM LIFECYCLE                             │
│                                                                          │
│  SOURCE ──▶ INTERMEDIATE (lazy, no work) ──▶ TERMINAL (the work runs)   │
│                                                                          │
│  list.stream()    .filter(p)                 .collect(toList())          │
│  Stream.of(..)    .map(f)                    .forEach(..)                │
│  IntStream.range  .flatMap(f)                .count()                    │
│  Files.lines()    .sorted(c)  ← STATEFUL     .reduce(..)                 │
│  Arrays.stream()  .distinct() ← STATEFUL     .min()/.max() → Optional    │
│                   .limit(n)   ← SHORT-CIRCUIT .findFirst() → Optional    │
│                   .peek(x)    ← DEBUG ONLY    .anyMatch()/.allMatch()     │
│                                                                          │
│  ⚠ ONE-SHOT: a stream can be consumed only ONCE.                       │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                     COLLECTOR — THE FOUR PARTS                           │
├──────────────────┬──────────────────────┬────────────────────────────────┤
│ supplier          │ create the container │ ArrayList::new                  │
│ accumulator       │ add ONE element      │ (e, acc) -> acc.add(e)          │
│ combiner          │ merge TWO containers │ (a, b) -> { a.addAll(b); }      │
│ finisher          │ final transformation │ (acc) -> acc.size()             │
├──────────────────┴──────────────────────┴────────────────────────────────┤
│  ⚠ THE COMBINER is what makes the collector PARALLEL-CORRECT.           │
│    A collector without one produces WRONG results under parallelism.    │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                    WHEN IS parallelStream() A WIN?                      │
├──────────────────────────────────────────────────────────────────────────┤
│  ✅ WINS   large data  +  CPU-bound work  +  cheap split  +  mergeable   │
│  ❌ LOSES  small data  +  trivial per-element work  +  I/O-bound work   │
│           ordered results  +  a source that splits badly (LinkedList)  │
│  ⚠ ALWAYS MEASURE. Treat parallel as an optimisation, not a default.   │
│  ⚠ It uses the COMMON ForkJoinPool — shared with the whole JVM.        │
└──────────────────────────────────────────────────────────────────────────┘
```

```text
┌──────────────────────────────────────────────────────────────────────────┐
│                        THE 5 STREAM TRAPS                               │
├──────────────────────────────────────────────────────────────────────────┤
│  1. REUSING a stream            → IllegalStateException                 │
│  2. `toMap` without a merge fn  → IllegalStateException on duplicates   │
│  3. `map(f).count()`            → runs f on EVERY element (usually a bug)│
│  4. side effects in `peek()`    → may be skipped, especially in parallel│
│  5. `sorted().limit(10)`        → sorts EVERYTHING (use a bounded heap) │
└──────────────────────────────────────────────────────────────────────────┘
```

---

## 12. Real-World Production Notes



### Stream Overhead vs For-Loop

Streams have startup overhead. For small collections (< 1000 elements), traditional loops are faster.

```java
// Microbenchmark comparison (approximate)
// Operation: filter even numbers, double them, sum

// For-loop: ~5ns per element
int sum = 0;
for (int i = 0; i < array.length; i++) {
    if (array[i] % 2 == 0) sum += array[i] * 2;
}

// Sequential stream: ~8ns per element (pipeline setup overhead)
int sum = Arrays.stream(array)
    .filter(n -> n % 2 == 0)
    .map(n -> n * 2)
    .sum();

// Parallel stream: ~3ns per element (only beneficial for large arrays > 10,000)
int sum = Arrays.stream(array).parallel()
    .filter(n -> n % 2 == 0)
    .map(n -> n * 2)
    .sum();
```

### When Streams Are Slower

| Scenario | Why | Alternative |
|----------|-----|-------------|
| Small collections (< 1K) | Pipeline setup cost dominates | For-each loop |
| Boxing/unboxing | `Stream<Integer>` boxes every int | Use `IntStream` |
| Nested streams | Object allocation per stage | Flatten logic |
| Simple iteration | No transformation needed | `forEach` on collection |
| Index-based access | Streams don't expose index | `IntStream.range()` |

### Optimization Techniques

```java
// ❌ Bad: Boxing overhead
List<Integer> list = ...;
list.stream().filter(n -> n > 0).mapToInt(Integer::intValue).sum();

// ✅ Good: Primitive stream from start
int[] array = ...;
Arrays.stream(array).filter(n -> n > 0).sum();

// ❌ Bad: Multiple terminal operations = multiple traversals
long count = list.stream().filter(p -> p.getAge() > 18).count();
int sum = list.stream().filter(p -> p.getAge() > 18).mapToInt(Person::getSalary).sum();

// ✅ Good: Single traversal with summarizing
IntSummaryStatistics stats = list.stream()
    .filter(p -> p.getAge() > 18)
    .mapToInt(Person::getSalary)
    .summaryStatistics();
long count = stats.getCount();
int sum = stats.getSum();

// ✅ Good: Use unordered() to skip ordering guarantees
// If you don't need encounter order, this enables parallel optimizations
list.parallelStream()
    .unordered()  // Tells JVM: order doesn't matter
    .distinct()   // Much faster without ORDERED characteristic
    .collect(Collectors.toList());
```

### Parallel Stream Threshold Rule

$$T_{parallel} < T_{sequential} \text{ only when } N \times Q > F$$

Where:
- $N$ = number of elements
- $Q$ = cost per element computation
- $F$ = fork/join framework overhead (~10,000 ns)

**Rule of thumb**: Use parallel streams only when $N > 10{,}000$ AND operation is CPU-bound.

**Interview Q: What is the default parallelism for parallel streams?**
A: `Runtime.getRuntime().availableProcessors()`. Uses `ForkJoinPool.commonPool()`. Can be overridden via `-Djava.util.concurrent.ForkJoinPool.common.parallelism=N`.

---



---

## 13. Follow-up Learning

| Topic | Where to go next |
|---|---|
| The functional interfaces the streams are built on | [01 · Lambda & Functional Interfaces](01-Lambda-Functional-Interfaces.md) |
| `Optional` and async composition | [03 · Optional & CompletableFuture](03-Optional-CompletableFuture.md) |
| The parallel stream implementation | [08 · Executor Framework](../08-Multithreading-Concurrency/02-Executor-Framework.md) |
| The `Spliterator` and the common pool | [08 · Locks & Atomic](../08-Multithreading-Concurrency/03-Locks-Atomic.md) |
| Collectors in the JDK source | `java.util.stream.Collectors` — read `toList()`, `groupingBy()`, `reducing()` |

---

**Next Chapter:** → [03 · Optional & CompletableFuture](03-Optional-CompletableFuture.md)
**Previous Chapter:** ← [01 · Lambda & Functional Interfaces](01-Lambda-Functional-Interfaces.md)
**Standard followed:** [../STANDARD.md](../STANDARD.md) · **Updated:** 2026
