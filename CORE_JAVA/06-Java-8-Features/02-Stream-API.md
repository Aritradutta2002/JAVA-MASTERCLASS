# Java 8 Features - Stream API

## What is Stream?

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

## Stream Creation

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

## Stream Pipeline Internals (Senior Interview Favorite)

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

## Intermediate Operations (Lazy)

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

## Terminal Operations

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

## Primitive Streams

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

## Parallel Streams

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

## Java 9+ Stream API Additions (Highly Asked in Interviews)

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

## Exception Handling in Streams (Critical Interview Topic)

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

## Performance & Benchmarking (Senior-Level Interview Questions)

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

## Common Patterns

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

## Interview Q&A

### Basic Level

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

### Intermediate Level

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

### Advanced Level

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

## Advanced Tricky Interview Scenarios (Senior/Architect Level)

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

## Common Traps

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

## Quick Reference Cheat Sheet (Interview Revision)

| Operation | Type | Short-Circuit? | Stateless? | Returns |
|-----------|------|---------------|------------|---------|
| `filter()` | Intermediate | No | Yes | `Stream<T>` |
| `map()` | Intermediate | No | Yes | `Stream<R>` |
| `flatMap()` | Intermediate | No | Yes | `Stream<R>` |
| `distinct()` | Intermediate | No | No | `Stream<T>` |
| `sorted()` | Intermediate | No | No | `Stream<T>` |
| `peek()` | Intermediate | No | Yes | `Stream<T>` |
| `limit()` | Intermediate | Yes | No | `Stream<T>` |
| `skip()` | Intermediate | No | No | `Stream<T>` |
| `takeWhile()` | Intermediate | Yes | No | `Stream<T>` |
| `dropWhile()` | Intermediate | Yes | No | `Stream<T>` |
| `forEach()` | Terminal | No | N/A | `void` |
| `collect()` | Terminal | No | N/A | `R` |
| `reduce()` | Terminal | No | N/A | `Optional<T>` or `T` |
| `count()` | Terminal | No | N/A | `long` |
| `findFirst()` | Terminal | Yes | N/A | `Optional<T>` |
| `findAny()` | Terminal | Yes | N/A | `Optional<T>` |
| `anyMatch()` | Terminal | Yes | N/A | `boolean` |
| `allMatch()` | Terminal | Yes | N/A | `boolean` |
| `noneMatch()` | Terminal | Yes | N/A | `boolean` |
| `toArray()` | Terminal | No | N/A | `Object[]` or `A[]` |
| `min()`/`max()` | Terminal | No | N/A | `Optional<T>` |

---

## Related Topics

- [Lambda & Functional Interfaces](./01-Lambda-Functional-Interfaces.md) - Foundation for stream operations
- [Optional & CompletableFuture](./03-Optional-CompletableFuture.md) - Handling optional results from streams
- [Collections Framework](../04-Collections-Framework/01-List-Implementations.md) - Source of streams
- [Generics](../05-Generics/01-Generics.md) - Type safety in streams
- [Multithreading & Concurrency](../08-Multithreading-Concurrency/01-Thread-Basics.md) - Parallel stream execution
- [Exception Handling](../03-Exception-Handling/01-Exception-Handling.md) - Handling exceptions in stream operations

---

*Last Updated: February 2026*
*Java Version: 8, 11, 17, 21*
