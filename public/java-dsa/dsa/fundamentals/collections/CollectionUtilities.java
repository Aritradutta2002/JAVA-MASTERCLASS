package dsa.fundamentals.collections;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Collections Class Utilities and Thread-Safe Collections
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. Collections utility class methods
 * 2. Synchronized vs Concurrent collections
 * 3. Unmodifiable vs Immutable collections
 * 4. Factory methods (Java 9+)
 * 5. Thread safety considerations
 * 6. Performance implications
 */
public class CollectionUtilities {
    
    public static void main(String[] args) {
        demonstrateCollectionsUtilityMethods();
        demonstrateSynchronizedCollections();
        demonstrateConcurrentCollections();
        demonstrateUnmodifiableCollections();
        demonstrateFactoryMethods();
        demonstrateSearchingAndSorting();
        demonstrateCollectionConversions();
        analyzeThreadSafety();
        showBestPractices();
    }
    
    /**
     * Collections utility class methods
     */
    public static void demonstrateCollectionsUtilityMethods() {
        System.out.println("=== COLLECTIONS UTILITY METHODS ===\n");
        
        List<Integer> numbers = new ArrayList<>(Arrays.asList(3, 1, 4, 1, 5, 9, 2, 6));
        System.out.println("Original list: " + numbers);
        
        // Sorting
        Collections.sort(numbers);
        System.out.println("After sort: " + numbers);
        
        Collections.sort(numbers, Collections.reverseOrder());
        System.out.println("Reverse sorted: " + numbers);
        
        // Shuffling
        Collections.shuffle(numbers);
        System.out.println("After shuffle: " + numbers);
        
        // Min and Max
        System.out.println("Min: " + Collections.min(numbers));
        System.out.println("Max: " + Collections.max(numbers));
        
        // Binary search (requires sorted list)
        Collections.sort(numbers);
        int index = Collections.binarySearch(numbers, 5);
        System.out.println("Binary search for 5: index " + index);
        
        // Frequency
        List<String> words = Arrays.asList("apple", "banana", "apple", "cherry", "apple");
        System.out.println("Frequency of 'apple': " + Collections.frequency(words, "apple"));
        
        // Replace all
        List<String> fruits = new ArrayList<>(Arrays.asList("apple", "banana", "apple", "cherry"));
        Collections.replaceAll(fruits, "apple", "orange");
        System.out.println("After replace: " + fruits);
        
        // Reverse
        Collections.reverse(fruits);
        System.out.println("After reverse: " + fruits);
        
        // Rotate
        Collections.rotate(fruits, 2);
        System.out.println("After rotate by 2: " + fruits);
        
        // Swap
        Collections.swap(fruits, 0, 2);
        System.out.println("After swap index 0 and 2: " + fruits);
        
        // Fill
        List<String> fillList = new ArrayList<>(Arrays.asList("a", "b", "c", "d"));
        Collections.fill(fillList, "X");
        System.out.println("After fill with X: " + fillList);
        
        // Copy
        List<String> source = Arrays.asList("one", "two", "three");
        List<String> dest = new ArrayList<>(Arrays.asList("a", "b", "c"));
        Collections.copy(dest, source);
        System.out.println("After copy: " + dest);
        
        // Disjoint
        List<Integer> list1 = Arrays.asList(1, 2, 3);
        List<Integer> list2 = Arrays.asList(4, 5, 6);
        List<Integer> list3 = Arrays.asList(3, 4, 5);
        System.out.println("list1 and list2 disjoint: " + Collections.disjoint(list1, list2));
        System.out.println("list1 and list3 disjoint: " + Collections.disjoint(list1, list3));
        
        // Singleton collections
        Set<String> singletonSet = Collections.singleton("single");
        List<String> singletonList = Collections.singletonList("single");
        Map<String, String> singletonMap = Collections.singletonMap("key", "value");
        
        System.out.println("Singleton set: " + singletonSet);
        System.out.println("Singleton list: " + singletonList);
        System.out.println("Singleton map: " + singletonMap);
        
        // Empty collections
        List<String> emptyList = Collections.emptyList();
        Set<String> emptySet = Collections.emptySet();
        Map<String, String> emptyMap = Collections.emptyMap();
        
        System.out.println("Empty collections created (type-safe)");
    }
    
    /**
     * Synchronized collections demonstration
     */
    public static void demonstrateSynchronizedCollections() {
        System.out.println("\n=== SYNCHRONIZED COLLECTIONS ===\n");
        
        // Creating synchronized collections
        List<String> syncList = Collections.synchronizedList(new ArrayList<>());
        Set<String> syncSet = Collections.synchronizedSet(new HashSet<>());
        Map<String, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        
        System.out.println("SYNCHRONIZED COLLECTIONS CHARACTERISTICS:");
        System.out.println("✓ Thread-safe individual operations");
        System.out.println("✓ All methods synchronized");
        System.out.println("✗ Compound operations need external synchronization");
        System.out.println("✗ Iteration needs external synchronization");
        System.out.println("✗ Performance overhead due to synchronization");
        
        // Adding elements
        syncList.add("Item1");
        syncList.add("Item2");
        syncList.add("Item3");
        
        syncSet.add("SetItem1");
        syncSet.add("SetItem2");
        
        syncMap.put("Key1", 1);
        syncMap.put("Key2", 2);
        
        System.out.println("Synchronized list: " + syncList);
        System.out.println("Synchronized set: " + syncSet);
        System.out.println("Synchronized map: " + syncMap);
        
        // Iteration requires external synchronization
        System.out.println("\nSAFE ITERATION PATTERN:");
        synchronized (syncList) {
            for (String item : syncList) {
                System.out.println("Processing: " + item);
            }
        }
        
        // Demonstrate thread safety issue without synchronization
        demonstrateSynchronizationNeed();
    }
    
    /**
     * Concurrent collections demonstration
     */
    public static void demonstrateConcurrentCollections() {
        System.out.println("\n=== CONCURRENT COLLECTIONS ===\n");
        
        System.out.println("CONCURRENT COLLECTION TYPES:");
        System.out.println("1. ConcurrentHashMap - Thread-safe HashMap");
        System.out.println("2. CopyOnWriteArrayList - Thread-safe ArrayList for read-heavy scenarios");
        System.out.println("3. CopyOnWriteArraySet - Thread-safe Set");
        System.out.println("4. ConcurrentLinkedQueue - Lock-free queue");
        System.out.println("5. BlockingQueue implementations");
        
        // ConcurrentHashMap
        Map<String, Integer> concurrentMap = new java.util.concurrent.ConcurrentHashMap<>();
        concurrentMap.put("A", 1);
        concurrentMap.put("B", 2);
        concurrentMap.put("C", 3);
        
        // Atomic operations
        concurrentMap.putIfAbsent("D", 4);
        concurrentMap.replace("A", 1, 10); // Replace if current value is 1
        concurrentMap.compute("E", (key, value) -> (value == null) ? 1 : value + 1);
        
        System.out.println("ConcurrentHashMap: " + concurrentMap);
        
        // CopyOnWriteArrayList
        List<String> cowList = new CopyOnWriteArrayList<>();
        cowList.add("Item1");
        cowList.add("Item2");
        cowList.add("Item3");
        
        System.out.println("CopyOnWriteArrayList: " + cowList);
        
        // Safe iteration without external synchronization
        System.out.println("Iterating CopyOnWriteArrayList (no external sync needed):");
        for (String item : cowList) {
            System.out.println("- " + item);
        }
        
        System.out.println("\nCONCURRENT vs SYNCHRONIZED:");
        System.out.println("┌─────────────────────┬─────────────────────┬─────────────────────┐");
        System.out.println("│ Feature             │ Synchronized        │ Concurrent          │");
        System.out.println("├─────────────────────┼─────────────────────┼─────────────────────┤");
        System.out.println("│ Locking mechanism   │ Coarse-grained      │ Fine-grained/Lock-free│");
        System.out.println("│ Performance         │ Poor under contention│ Better scalability  │");
        System.out.println("│ Iteration safety    │ External sync needed│ Built-in safety     │");
        System.out.println("│ Null values         │ Depends on underlying│ Usually not allowed │");
        System.out.println("│ Fail-fast iterators│ Yes                 │ No (fail-safe)      │");
        System.out.println("└─────────────────────┴─────────────────────┴─────────────────────┘");
        
        // Demonstrate performance difference
        demonstrateConcurrentPerformance();
    }
    
    /**
     * Unmodifiable collections demonstration
     */
    public static void demonstrateUnmodifiableCollections() {
        System.out.println("\n=== UNMODIFIABLE COLLECTIONS ===\n");
        
        // Create mutable collections
        List<String> mutableList = new ArrayList<>(Arrays.asList("A", "B", "C"));
        Set<String> mutableSet = new HashSet<>(Arrays.asList("X", "Y", "Z"));
        Map<String, Integer> mutableMap = new HashMap<>();
        mutableMap.put("One", 1);
        mutableMap.put("Two", 2);
        
        // Create unmodifiable views
        List<String> unmodifiableList = Collections.unmodifiableList(mutableList);
        Set<String> unmodifiableSet = Collections.unmodifiableSet(mutableSet);
        Map<String, Integer> unmodifiableMap = Collections.unmodifiableMap(mutableMap);
        
        System.out.println("Unmodifiable list: " + unmodifiableList);
        System.out.println("Unmodifiable set: " + unmodifiableSet);
        System.out.println("Unmodifiable map: " + unmodifiableMap);
        
        // Attempt to modify (will throw UnsupportedOperationException)
        try {
            unmodifiableList.add("D");
        } catch (UnsupportedOperationException e) {
            System.out.println("Cannot modify unmodifiable list: " + e.getClass().getSimpleName());
        }
        
        // But original collection can still be modified
        mutableList.add("D");
        System.out.println("After modifying original: " + unmodifiableList); // Reflects change!
        
        System.out.println("\nUNMODIFIABLE vs IMMUTABLE:");
        System.out.println("Unmodifiable: View of original collection, changes in original are reflected");
        System.out.println("Immutable: Completely separate copy, original changes don't affect it");
        
        // Creating truly immutable collections (defensive copy)
        List<String> immutableList = Collections.unmodifiableList(new ArrayList<>(mutableList));
        System.out.println("Immutable copy: " + immutableList);
        
        mutableList.add("E");
        System.out.println("Original after adding E: " + mutableList);
        System.out.println("Immutable copy unchanged: " + immutableList);
    }
    
    /**
     * Factory methods (Java 9+) demonstration
     */
    public static void demonstrateFactoryMethods() {
        System.out.println("\n=== FACTORY METHODS (Java 9+) ===\n");
        
        // List factory methods
        List<String> list1 = List.of("A", "B", "C");
        List<Integer> list2 = List.of(1, 2, 3, 4, 5);
        
        System.out.println("List.of(): " + list1);
        System.out.println("List.of() with numbers: " + list2);
        
        // Set factory methods
        Set<String> set1 = Set.of("Apple", "Banana", "Cherry");
        Set<Integer> set2 = Set.of(1, 2, 3, 4, 5);
        
        System.out.println("Set.of(): " + set1);
        System.out.println("Set.of() with numbers: " + set2);
        
        // Map factory methods
        Map<String, Integer> map1 = Map.of("A", 1, "B", 2, "C", 3);
        Map<String, Integer> map2 = Map.of(
            "Apple", 5,
            "Banana", 3,
            "Cherry", 8
        );
        
        System.out.println("Map.of(): " + map1);
        System.out.println("Map.of() with pairs: " + map2);
        
        // Map.ofEntries for larger maps
        Map<String, Integer> map3 = Map.ofEntries(
            Map.entry("One", 1),
            Map.entry("Two", 2),
            Map.entry("Three", 3),
            Map.entry("Four", 4),
            Map.entry("Five", 5)
        );
        System.out.println("Map.ofEntries(): " + map3);
        
        System.out.println("\nFACTORY METHOD CHARACTERISTICS:");
        System.out.println("✓ Truly immutable (not just unmodifiable views)");
        System.out.println("✓ Null values/keys not allowed");
        System.out.println("✓ No duplicate elements in Set.of()");
        System.out.println("✓ Space efficient implementations");
        System.out.println("✓ Fail-fast on modification attempts");
        
        // Attempt to modify
        try {
            list1.add("D");
        } catch (UnsupportedOperationException e) {
            System.out.println("Factory collections are immutable: " + e.getClass().getSimpleName());
        }
        
        // Null handling
        try {
            List<String> nullList = List.of("A", null, "C");
        } catch (NullPointerException e) {
            System.out.println("Factory methods don't allow nulls: " + e.getClass().getSimpleName());
        }
    }
    
    /**
     * Searching and sorting utilities
     */
    public static void demonstrateSearchingAndSorting() {
        System.out.println("\n=== SEARCHING AND SORTING UTILITIES ===\n");
        
        // Searching
        List<String> sortedNames = Arrays.asList("Alice", "Bob", "Charlie", "David", "Eve");
        
        int index = Collections.binarySearch(sortedNames, "Charlie");
        System.out.println("Binary search for 'Charlie': " + index);
        
        index = Collections.binarySearch(sortedNames, "Frank");
        System.out.println("Binary search for 'Frank': " + index + " (negative = insertion point)");
        
        // Custom comparator search
        List<String> caseInsensitiveList = Arrays.asList("apple", "Banana", "cherry", "Date");
        Collections.sort(caseInsensitiveList, String.CASE_INSENSITIVE_ORDER);
        System.out.println("Case-insensitive sorted: " + caseInsensitiveList);
        
        index = Collections.binarySearch(caseInsensitiveList, "CHERRY", String.CASE_INSENSITIVE_ORDER);
        System.out.println("Case-insensitive search for 'CHERRY': " + index);
        
        // Sorting with different comparators
        List<String> words = new ArrayList<>(Arrays.asList("elephant", "cat", "butterfly", "dog"));
        
        // Sort by length
        Collections.sort(words, Comparator.comparing(String::length));
        System.out.println("Sorted by length: " + words);
        
        // Sort by length then alphabetically
        Collections.sort(words, Comparator.comparing(String::length)
                                          .thenComparing(String::compareTo));
        System.out.println("Sorted by length then alphabetically: " + words);
        
        // Finding min/max with custom comparator
        String shortest = Collections.min(words, Comparator.comparing(String::length));
        String longest = Collections.max(words, Comparator.comparing(String::length));
        System.out.println("Shortest word: " + shortest);
        System.out.println("Longest word: " + longest);
        
        // Stable sort demonstration
        List<Person> people = Arrays.asList(
            new Person("Alice", 25),
            new Person("Bob", 30),
            new Person("Charlie", 25),
            new Person("David", 30)
        );
        
        System.out.println("Original order: " + people);
        Collections.sort(people, Comparator.comparing(Person::getAge));
        System.out.println("Sorted by age (stable): " + people);
    }
    
    /**
     * Collection conversion utilities
     */
    public static void demonstrateCollectionConversions() {
        System.out.println("\n=== COLLECTION CONVERSIONS ===\n");
        
        // Array to List
        String[] array = {"A", "B", "C", "D"};
        List<String> listFromArray = Arrays.asList(array);
        List<String> mutableList = new ArrayList<>(Arrays.asList(array));
        
        System.out.println("Array to List (fixed size): " + listFromArray);
        System.out.println("Array to mutable List: " + mutableList);
        
        // List to Array
        List<String> list = Arrays.asList("X", "Y", "Z");
        String[] arrayFromList = list.toArray(new String[0]);
        Object[] objectArray = list.toArray();
        
        System.out.println("List to String[]: " + Arrays.toString(arrayFromList));
        System.out.println("List to Object[]: " + Arrays.toString(objectArray));
        
        // Set to List and vice versa
        Set<Integer> numberSet = new HashSet<>(Arrays.asList(3, 1, 4, 1, 5));
        List<Integer> listFromSet = new ArrayList<>(numberSet);
        Set<Integer> setFromList = new HashSet<>(Arrays.asList(1, 2, 2, 3, 3, 4));
        
        System.out.println("Set to List: " + listFromSet);
        System.out.println("List to Set (removes duplicates): " + setFromList);
        
        // Map keys/values to collections
        Map<String, Integer> map = Map.of("A", 1, "B", 2, "C", 3);
        List<String> keys = new ArrayList<>(map.keySet());
        List<Integer> values = new ArrayList<>(map.values());
        
        System.out.println("Map keys to List: " + keys);
        System.out.println("Map values to List: " + values);
        
        // String to Character List
        String text = "Hello";
        List<Character> charList = text.chars()
                                      .mapToObj(c -> (char) c)
                                      .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        System.out.println("String to Character List: " + charList);
        
        // Enum to List
        List<DayOfWeek> weekdays = Arrays.asList(DayOfWeek.values());
        System.out.println("Enum values to List: " + weekdays);
    }
    
    /**
     * Thread safety analysis
     */
    public static void analyzeThreadSafety() {
        System.out.println("\n=== THREAD SAFETY ANALYSIS ===\n");
        
        System.out.println("THREAD SAFETY LEVELS:");
        System.out.println("1. Thread-safe: Can be safely accessed by multiple threads");
        System.out.println("2. Conditionally thread-safe: Safe for some operations");
        System.out.println("3. Not thread-safe: Requires external synchronization");
        
        System.out.println("\nCOLLECTION THREAD SAFETY:");
        System.out.println("┌─────────────────────┬─────────────────┬─────────────────────┐");
        System.out.println("│ Collection          │ Thread Safety   │ Alternative         │");
        System.out.println("├─────────────────────┼─────────────────┼─────────────────────┤");
        System.out.println("│ ArrayList           │ Not safe        │ CopyOnWriteArrayList│");
        System.out.println("│ LinkedList          │ Not safe        │ Collections.sync... │");
        System.out.println("│ HashMap             │ Not safe        │ ConcurrentHashMap   │");
        System.out.println("│ HashSet             │ Not safe        │ ConcurrentHashMap   │");
        System.out.println("│ TreeMap             │ Not safe        │ Collections.sync... │");
        System.out.println("│ Vector              │ Synchronized    │ CopyOnWriteArrayList│");
        System.out.println("│ Hashtable           │ Synchronized    │ ConcurrentHashMap   │");
        System.out.println("│ Collections.sync... │ Synchronized    │ Concurrent versions │");
        System.out.println("└─────────────────────┴─────────────────┴─────────────────────┘");
        
        System.out.println("\nCHOOSING THREAD-SAFE COLLECTIONS:");
        System.out.println("Read-heavy scenarios: CopyOnWriteArrayList, CopyOnWriteArraySet");
        System.out.println("Write-heavy scenarios: ConcurrentHashMap, ConcurrentLinkedQueue");
        System.out.println("Legacy compatibility: Collections.synchronizedXxx()");
        System.out.println("Producer-consumer: BlockingQueue implementations");
        
        // Performance comparison
        demonstrateThreadSafetyPerformance();
    }
    
    /**
     * Best practices and recommendations
     */
    public static void showBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("COLLECTIONS UTILITY USAGE:");
        System.out.println("✓ Use Collections.emptyXxx() instead of new XxxCollection()");
        System.out.println("✓ Use Collections.singletonXxx() for single-element collections");
        System.out.println("✓ Use Collections.unmodifiableXxx() for defensive copying");
        System.out.println("✓ Prefer List.of(), Set.of(), Map.of() for immutable collections (Java 9+)");
        
        System.out.println("\nTHREAD SAFETY:");
        System.out.println("✓ Use concurrent collections over synchronized collections");
        System.out.println("✓ Consider read/write patterns when choosing thread-safe collections");
        System.out.println("✓ Synchronize compound operations even with thread-safe collections");
        System.out.println("✗ Don't use Vector or Hashtable in new code");
        
        System.out.println("\nPERFORMANCE CONSIDERATIONS:");
        System.out.println("✓ Initialize collections with appropriate capacity");
        System.out.println("✓ Use appropriate collection type for your access patterns");
        System.out.println("✓ Consider memory overhead of thread-safe collections");
        System.out.println("✗ Don't synchronize if you don't need thread safety");
        
        System.out.println("\nCOMMON PITFALLS:");
        System.out.println("✗ Modifying collection during iteration without proper synchronization");
        System.out.println("✗ Using Collections.synchronizedXxx() without external sync for iteration");
        System.out.println("✗ Assuming unmodifiable means immutable");
        System.out.println("✗ Using null with factory methods or concurrent collections");
        
        // Example of best practices
        demonstrateBestPracticeExamples();
    }
    
    // Helper methods
    
    private static void demonstrateSynchronizationNeed() {
        System.out.println("\nDEMONSTRATING SYNCHRONIZATION NEED:");
        
        List<Integer> unsafeList = new ArrayList<>();
        
        // This would cause issues in real multi-threaded environment
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 1000; i++) {
                unsafeList.add(i);
            }
        });
        
        Thread thread2 = new Thread(() -> {
            for (int i = 1000; i < 2000; i++) {
                unsafeList.add(i);
            }
        });
        
        try {
            thread1.start();
            thread2.start();
            thread1.join();
            thread2.join();
            
            System.out.println("Unsafe list final size: " + unsafeList.size() + " (may vary due to race conditions)");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private static void demonstrateConcurrentPerformance() {
        System.out.println("\nPERFORMANCE COMPARISON:");
        
        // This is a simplified demonstration
        Map<String, Integer> syncMap = Collections.synchronizedMap(new HashMap<>());
        Map<String, Integer> concurrentMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        final int OPERATIONS = 10000;
        
        // Synchronized map
        long startTime = System.nanoTime();
        for (int i = 0; i < OPERATIONS; i++) {
            syncMap.put("key" + i, i);
        }
        long syncTime = System.nanoTime() - startTime;
        
        // Concurrent map
        startTime = System.nanoTime();
        for (int i = 0; i < OPERATIONS; i++) {
            concurrentMap.put("key" + i, i);
        }
        long concurrentTime = System.nanoTime() - startTime;
        
        System.out.println("Synchronized map: " + syncTime / 1_000_000 + " ms");
        System.out.println("Concurrent map: " + concurrentTime / 1_000_000 + " ms");
    }
    
    private static void demonstrateThreadSafetyPerformance() {
        System.out.println("\nTHREAD SAFETY PERFORMANCE DEMO:");
        
        // Single-threaded performance
        List<String> arrayList = new ArrayList<>();
        List<String> syncList = Collections.synchronizedList(new ArrayList<>());
        List<String> cowList = new CopyOnWriteArrayList<>();
        
        final int SIZE = 10000;
        
        // ArrayList (not thread-safe, baseline)
        long startTime = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add("item" + i);
        }
        long arrayListTime = System.nanoTime() - startTime;
        
        // Synchronized list
        startTime = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            syncList.add("item" + i);
        }
        long syncListTime = System.nanoTime() - startTime;
        
        // CopyOnWriteArrayList
        startTime = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            cowList.add("item" + i);
        }
        long cowListTime = System.nanoTime() - startTime;
        
        System.out.println("ArrayList (not thread-safe): " + arrayListTime / 1_000_000 + " ms");
        System.out.println("Synchronized List: " + syncListTime / 1_000_000 + " ms");
        System.out.println("CopyOnWriteArrayList: " + cowListTime / 1_000_000 + " ms");
    }
    
    private static void demonstrateBestPracticeExamples() {
        System.out.println("\nBEST PRACTICE EXAMPLES:");
        
        // Good: Using factory methods
        List<String> immutableList = List.of("A", "B", "C");
        Set<Integer> immutableSet = Set.of(1, 2, 3);
        
        // Good: Defensive copying
        List<String> original = new ArrayList<>(Arrays.asList("X", "Y", "Z"));
        List<String> defensiveCopy = Collections.unmodifiableList(new ArrayList<>(original));
        
        // Good: Using appropriate thread-safe collection
        Map<String, String> threadSafeMap = new java.util.concurrent.ConcurrentHashMap<>();
        
        // Good: Proper synchronization for compound operations
        List<String> synchronizedList = Collections.synchronizedList(new ArrayList<>());
        synchronized (synchronizedList) {
            if (!synchronizedList.contains("item")) {
                synchronizedList.add("item");
            }
        }
        
        System.out.println("✓ Used factory methods for immutable collections");
        System.out.println("✓ Created defensive copies properly");
        System.out.println("✓ Chose appropriate thread-safe collection");
        System.out.println("✓ Synchronized compound operations");
    }
}

// Supporting classes

enum DayOfWeek {
    MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY
}

class Person {
    private final String name;
    private final int age;
    
    public Person(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    public String getName() { return name; }
    public int getAge() { return age; }
    
    @Override
    public String toString() {
        return name + "(" + age + ")";
    }
}
