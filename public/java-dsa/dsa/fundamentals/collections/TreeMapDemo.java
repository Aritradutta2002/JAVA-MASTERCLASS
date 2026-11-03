package dsa.fundamentals.collections;

import java.util.*;

/**
 * Complete TreeMap Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. TreeMap vs HashMap vs LinkedHashMap
 * 2. NavigableMap and SortedMap operations
 * 3. Red-Black tree implementation details
 * 4. Custom comparators with TreeMap
 * 5. Range operations and views
 * 6. Performance characteristics
 */
public class TreeMapDemo {
    
    public static void main(String[] args) {
        demonstrateBasicOperations();
        compareMapImplementations();
        demonstrateNavigableMapOperations();
        demonstrateSortedMapOperations();
        demonstrateCustomComparators();
        demonstrateRangeOperations();
        analyzePerformance();
        demonstratePracticalUseCases();
        showBestPractices();
    }
    
    /**
     * Basic TreeMap operations
     */
    public static void demonstrateBasicOperations() {
        System.out.println("=== TREEMAP BASIC OPERATIONS ===\n");
        
        TreeMap<String, Integer> ages = new TreeMap<>();
        
        // Adding key-value pairs
        ages.put("Alice", 25);
        ages.put("Charlie", 35);
        ages.put("Bob", 30);
        ages.put("David", 28);
        
        System.out.println("TreeMap (automatically sorted by keys): " + ages);
        
        // Basic operations
        System.out.println("Alice's age: " + ages.get("Alice"));
        System.out.println("Contains key 'Bob': " + ages.containsKey("Bob"));
        System.out.println("Contains value 30: " + ages.containsValue(30));
        
        // Size and emptiness
        System.out.println("Size: " + ages.size());
        System.out.println("Is empty: " + ages.isEmpty());
        
        // Iteration (in sorted order)
        System.out.println("\nIteration in sorted order:");
        for (Map.Entry<String, Integer> entry : ages.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
        
        // Key, value, and entry sets
        System.out.println("Keys (sorted): " + ages.keySet());
        System.out.println("Values: " + ages.values());
        
        System.out.println("\nTREEMAP CHARACTERISTICS:");
        System.out.println("✓ Red-Black tree implementation");
        System.out.println("✓ Keys sorted in natural order or by Comparator");
        System.out.println("✓ O(log n) time for basic operations");
        System.out.println("✓ No null keys allowed (null values are allowed)");
        System.out.println("✓ NavigableMap and SortedMap operations");
    }
    
    /**
     * Compare different Map implementations
     */
    public static void compareMapImplementations() {
        System.out.println("\n=== MAP IMPLEMENTATIONS COMPARISON ===\n");
        
        // Same data in different map implementations
        String[] keys = {"Charlie", "Alice", "Bob", "David"};
        Integer[] values = {35, 25, 30, 28};
        
        Map<String, Integer> hashMap = new HashMap<>();
        Map<String, Integer> linkedHashMap = new LinkedHashMap<>();
        Map<String, Integer> treeMap = new TreeMap<>();
        
        // Add same data to all maps
        for (int i = 0; i < keys.length; i++) {
            hashMap.put(keys[i], values[i]);
            linkedHashMap.put(keys[i], values[i]);
            treeMap.put(keys[i], values[i]);
        }
        
        System.out.println("Input order: " + Arrays.toString(keys));
        System.out.println("HashMap (no order): " + hashMap);
        System.out.println("LinkedHashMap (insertion order): " + linkedHashMap);
        System.out.println("TreeMap (sorted order): " + treeMap);
        
        System.out.println("\nFEATURE COMPARISON:");
        System.out.println("┌─────────────────┬─────────────┬─────────────────┬─────────────┐");
        System.out.println("│ Feature         │ HashMap     │ LinkedHashMap   │ TreeMap     │");
        System.out.println("├─────────────────┼─────────────┼─────────────────┼─────────────┤");
        System.out.println("│ Ordering        │ None        │ Insertion/Access│ Sorted      │");
        System.out.println("│ Get/Put/Remove  │ O(1) avg    │ O(1) avg        │ O(log n)    │");
        System.out.println("│ Null keys       │ One allowed │ One allowed     │ Not allowed │");
        System.out.println("│ Null values     │ Allowed     │ Allowed         │ Allowed     │");
        System.out.println("│ Interface       │ Map         │ Map             │ NavigableMap│");
        System.out.println("│ Internal struct │ Hash table  │ Hash + LinkedList│ Red-Black Tree│");
        System.out.println("│ Memory usage    │ Low         │ Medium          │ High        │");
        System.out.println("└─────────────────┴─────────────┴─────────────────┴─────────────┘");
        
        // Demonstrate null key handling
        hashMap.put(null, 999);
        linkedHashMap.put(null, 999);
        System.out.println("\nHashMap with null key: " + hashMap.keySet());
        System.out.println("LinkedHashMap with null key: " + linkedHashMap.keySet());
        
        try {
            treeMap.put(null, 999);
        } catch (NullPointerException e) {
            System.out.println("TreeMap doesn't allow null keys: " + e.getClass().getSimpleName());
        }
    }
    
    /**
     * NavigableMap operations demonstration
     */
    public static void demonstrateNavigableMapOperations() {
        System.out.println("\n=== NAVIGABLEMAP OPERATIONS ===\n");
        
        NavigableMap<Integer, String> scores = new TreeMap<>();
        scores.put(85, "Alice");
        scores.put(92, "Bob");
        scores.put(78, "Charlie");
        scores.put(95, "David");
        scores.put(88, "Eve");
        scores.put(72, "Frank");
        
        System.out.println("Student scores: " + scores);
        
        // Navigation methods
        System.out.println("\nNAVIGATION METHODS:");
        System.out.println("lowerEntry(85): " + scores.lowerEntry(85));     // Greatest entry < 85
        System.out.println("floorEntry(87): " + scores.floorEntry(87));     // Greatest entry <= 87
        System.out.println("ceilingEntry(87): " + scores.ceilingEntry(87)); // Smallest entry >= 87
        System.out.println("higherEntry(85): " + scores.higherEntry(85));   // Smallest entry > 85
        
        // Key navigation
        System.out.println("\nKEY NAVIGATION:");
        System.out.println("lowerKey(85): " + scores.lowerKey(85));
        System.out.println("floorKey(87): " + scores.floorKey(87));
        System.out.println("ceilingKey(87): " + scores.ceilingKey(87));
        System.out.println("higherKey(85): " + scores.higherKey(85));
        
        // First and last entries
        System.out.println("\nFIRST AND LAST:");
        System.out.println("firstEntry(): " + scores.firstEntry());
        System.out.println("lastEntry(): " + scores.lastEntry());
        System.out.println("firstKey(): " + scores.firstKey());
        System.out.println("lastKey(): " + scores.lastKey());
        
        // Polling operations (with removal)
        NavigableMap<Integer, String> copyScores = new TreeMap<>(scores);
        System.out.println("\nPOLLING OPERATIONS:");
        System.out.println("pollFirstEntry(): " + copyScores.pollFirstEntry());
        System.out.println("pollLastEntry(): " + copyScores.pollLastEntry());
        System.out.println("After polling: " + copyScores);
        
        // Descending map
        NavigableMap<Integer, String> descendingMap = scores.descendingMap();
        System.out.println("\nDESCENDING MAP:");
        System.out.println("Original: " + scores.keySet());
        System.out.println("Descending: " + descendingMap.keySet());
        
        // Descending key set
        NavigableSet<Integer> descendingKeys = scores.descendingKeySet();
        System.out.println("Descending keys: " + descendingKeys);
    }
    
    /**
     * SortedMap operations demonstration
     */
    public static void demonstrateSortedMapOperations() {
        System.out.println("\n=== SORTEDMAP OPERATIONS ===\n");
        
        SortedMap<String, Double> prices = new TreeMap<>();
        prices.put("Apple", 1.50);
        prices.put("Banana", 0.75);
        prices.put("Cherry", 3.00);
        prices.put("Date", 2.25);
        prices.put("Elderberry", 4.50);
        
        System.out.println("Fruit prices: " + prices);
        
        // Comparator
        Comparator<? super String> comparator = prices.comparator();
        System.out.println("Comparator: " + (comparator == null ? "Natural ordering" : comparator));
        
        // First and last keys
        System.out.println("First key: " + prices.firstKey());
        System.out.println("Last key: " + prices.lastKey());
        
        // Submaps
        System.out.println("\nSUBMAP OPERATIONS:");
        System.out.println("headMap('Cherry'): " + prices.headMap("Cherry")); // < Cherry
        System.out.println("tailMap('Cherry'): " + prices.tailMap("Cherry")); // >= Cherry
        System.out.println("subMap('Banana', 'Date'): " + prices.subMap("Banana", "Date")); // [Banana, Date)
        
        // Modifying through submap
        SortedMap<String, Double> subMap = prices.subMap("Banana", "Date");
        subMap.put("Coconut", 2.75); // This affects the original map
        System.out.println("After adding to submap: " + prices);
        
        // Range operations with bounds
        NavigableMap<String, Double> navPrices = (NavigableMap<String, Double>) prices;
        System.out.println("\nRANGE OPERATIONS WITH BOUNDS:");
        System.out.println("headMap('Cherry', false): " + navPrices.headMap("Cherry", false)); // < Cherry
        System.out.println("headMap('Cherry', true): " + navPrices.headMap("Cherry", true));   // <= Cherry
        System.out.println("tailMap('Cherry', false): " + navPrices.tailMap("Cherry", false)); // > Cherry
        System.out.println("tailMap('Cherry', true): " + navPrices.tailMap("Cherry", true));   // >= Cherry
        System.out.println("subMap('Banana', true, 'Date', false): " + 
                          navPrices.subMap("Banana", true, "Date", false)); // [Banana, Date)
    }
    
    /**
     * Custom comparators with TreeMap
     */
    public static void demonstrateCustomComparators() {
        System.out.println("\n=== CUSTOM COMPARATORS ===\n");
        
        // 1. Natural ordering (default)
        TreeMap<String, Integer> naturalOrder = new TreeMap<>();
        naturalOrder.put("Zebra", 1);
        naturalOrder.put("Apple", 2);
        naturalOrder.put("Mouse", 3);
        System.out.println("Natural order: " + naturalOrder.keySet());
        
        // 2. Reverse order
        TreeMap<String, Integer> reverseOrder = new TreeMap<>(Collections.reverseOrder());
        reverseOrder.putAll(naturalOrder);
        System.out.println("Reverse order: " + reverseOrder.keySet());
        
        // 3. Case-insensitive ordering
        TreeMap<String, Integer> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.put("apple", 1);
        caseInsensitive.put("Banana", 2);
        caseInsensitive.put("cherry", 3);
        System.out.println("Case-insensitive: " + caseInsensitive);
        
        // 4. Custom comparator by length
        TreeMap<String, Integer> byLength = new TreeMap<>(Comparator.comparing(String::length)
                                                                   .thenComparing(String::compareTo));
        byLength.put("Cat", 1);
        byLength.put("Elephant", 2);
        byLength.put("Dog", 3);
        byLength.put("Butterfly", 4);
        System.out.println("By length then alphabetically: " + byLength);
        
        // 5. Complex object sorting
        TreeMap<Student, String> studentGrades = new TreeMap<>(
            Comparator.comparing(Student::getGrade).reversed() // Highest grade first
                     .thenComparing(Student::getName)          // Then by name
        );
        
        studentGrades.put(new Student("Alice", 85), "B");
        studentGrades.put(new Student("Bob", 92), "A");
        studentGrades.put(new Student("Charlie", 85), "B");
        studentGrades.put(new Student("David", 78), "C");
        
        System.out.println("\nStudents by grade (desc) then name:");
        studentGrades.forEach((student, grade) -> 
            System.out.println(student + " -> " + grade));
        
        // 6. TreeMap with custom objects as values
        TreeMap<Integer, List<String>> scoreGroups = new TreeMap<>();
        scoreGroups.put(90, Arrays.asList("Alice", "Bob"));
        scoreGroups.put(85, Arrays.asList("Charlie", "David"));
        scoreGroups.put(95, Arrays.asList("Eve"));
        
        System.out.println("\nScore groups: " + scoreGroups);
    }
    
    /**
     * Range operations and views
     */
    public static void demonstrateRangeOperations() {
        System.out.println("\n=== RANGE OPERATIONS ===\n");
        
        NavigableMap<Integer, String> timeline = new TreeMap<>();
        timeline.put(1990, "Birth of World Wide Web");
        timeline.put(1995, "JavaScript created");
        timeline.put(2000, "Dot-com bubble");
        timeline.put(2004, "Facebook founded");
        timeline.put(2007, "iPhone released");
        timeline.put(2010, "Instagram launched");
        timeline.put(2015, "Let's Encrypt launched");
        timeline.put(2020, "COVID-19 pandemic");
        
        System.out.println("Tech timeline: " + timeline);
        
        // Events before 2000
        SortedMap<Integer, String> beforeY2K = timeline.headMap(2000);
        System.out.println("\nEvents before 2000: " + beforeY2K);
        
        // Events from 2000 onwards
        SortedMap<Integer, String> fromY2K = timeline.tailMap(2000);
        System.out.println("Events from 2000 onwards: " + fromY2K);
        
        // Events in 2000s decade
        NavigableMap<Integer, String> decade2000s = timeline.subMap(2000, true, 2010, false);
        System.out.println("Events in 2000s: " + decade2000s);
        
        // Find events around a specific year
        int targetYear = 2005;
        Integer before = timeline.lowerKey(targetYear);
        Integer after = timeline.higherKey(targetYear);
        
        System.out.println("\nEvents around " + targetYear + ":");
        if (before != null) {
            System.out.println("Before: " + before + " - " + timeline.get(before));
        }
        if (after != null) {
            System.out.println("After: " + after + " - " + timeline.get(after));
        }
        
        // Range counting
        System.out.println("\nRange statistics:");
        System.out.println("Total events: " + timeline.size());
        System.out.println("Events before 2005: " + timeline.headMap(2005).size());
        System.out.println("Events after 2005: " + timeline.tailMap(2005, false).size());
        
        // Live view demonstration
        System.out.println("\nLIVE VIEW DEMONSTRATION:");
        NavigableMap<Integer, String> recentEvents = timeline.tailMap(2010, true);
        System.out.println("Recent events: " + recentEvents);
        
        timeline.put(2023, "ChatGPT mainstream adoption");
        System.out.println("After adding 2023 event: " + recentEvents); // View updates automatically
    }
    
    /**
     * Performance analysis
     */
    public static void analyzePerformance() {
        System.out.println("\n=== PERFORMANCE ANALYSIS ===\n");
        
        final int SIZE = 100000;
        Random random = new Random();
        
        // Test data
        Integer[] keys = new Integer[SIZE];
        String[] values = new String[SIZE];
        for (int i = 0; i < SIZE; i++) {
            keys[i] = random.nextInt(SIZE * 2);
            values[i] = "Value" + i;
        }
        
        // TreeMap insertion
        long startTime = System.nanoTime();
        TreeMap<Integer, String> treeMap = new TreeMap<>();
        for (int i = 0; i < SIZE; i++) {
            treeMap.put(keys[i], values[i]);
        }
        long treeMapInsertTime = System.nanoTime() - startTime;
        
        // HashMap insertion for comparison
        startTime = System.nanoTime();
        HashMap<Integer, String> hashMap = new HashMap<>();
        for (int i = 0; i < SIZE; i++) {
            hashMap.put(keys[i], values[i]);
        }
        long hashMapInsertTime = System.nanoTime() - startTime;
        
        System.out.println("Inserting " + SIZE + " elements:");
        System.out.println("TreeMap: " + treeMapInsertTime / 1_000_000 + " ms");
        System.out.println("HashMap: " + hashMapInsertTime / 1_000_000 + " ms");
        
        // Lookup performance
        Integer[] lookupKeys = new Integer[10000];
        for (int i = 0; i < 10000; i++) {
            lookupKeys[i] = random.nextInt(SIZE * 2);
        }
        
        // TreeMap lookup
        startTime = System.nanoTime();
        for (Integer key : lookupKeys) {
            treeMap.get(key);
        }
        long treeMapLookupTime = System.nanoTime() - startTime;
        
        // HashMap lookup
        startTime = System.nanoTime();
        for (Integer key : lookupKeys) {
            hashMap.get(key);
        }
        long hashMapLookupTime = System.nanoTime() - startTime;
        
        System.out.println("\n10,000 lookup operations:");
        System.out.println("TreeMap: " + treeMapLookupTime / 1_000_000 + " ms");
        System.out.println("HashMap: " + hashMapLookupTime / 1_000_000 + " ms");
        
        // Iteration performance
        startTime = System.nanoTime();
        for (Map.Entry<Integer, String> entry : treeMap.entrySet()) {
            // Just iterate
        }
        long treeMapIterTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (Map.Entry<Integer, String> entry : hashMap.entrySet()) {
            // Just iterate
        }
        long hashMapIterTime = System.nanoTime() - startTime;
        
        System.out.println("\nIterating through all entries:");
        System.out.println("TreeMap: " + treeMapIterTime / 1_000_000 + " ms (sorted order)");
        System.out.println("HashMap: " + hashMapIterTime / 1_000_000 + " ms (no order)");
        
        System.out.println("\nFinal map sizes:");
        System.out.println("TreeMap: " + treeMap.size());
        System.out.println("HashMap: " + hashMap.size());
    }
    
    /**
     * Practical use cases for TreeMap
     */
    public static void demonstratePracticalUseCases() {
        System.out.println("\n=== PRACTICAL USE CASES ===\n");
        
        // 1. Leaderboard system
        System.out.println("1. LEADERBOARD SYSTEM:");
        NavigableMap<Integer, String> leaderboard = new TreeMap<>(Collections.reverseOrder());
        leaderboard.put(1500, "Alice");
        leaderboard.put(1200, "Bob");
        leaderboard.put(1800, "Charlie");
        leaderboard.put(1350, "David");
        leaderboard.put(1600, "Eve");
        
        System.out.println("Top 3 players:");
        leaderboard.entrySet().stream().limit(3).forEach(entry -> 
            System.out.println(entry.getValue() + ": " + entry.getKey() + " points"));
        
        // Find players above certain score
        int threshold = 1400;
        NavigableMap<Integer, String> topPlayers = leaderboard.tailMap(threshold, true);
        System.out.println("Players with score >= " + threshold + ": " + topPlayers.values());
        
        // 2. Event scheduling
        System.out.println("\n2. EVENT SCHEDULING:");
        TreeMap<LocalTime, String> schedule = new TreeMap<>();
        schedule.put(LocalTime.of(9, 0), "Team Meeting");
        schedule.put(LocalTime.of(11, 30), "Code Review");
        schedule.put(LocalTime.of(14, 0), "Lunch Break");
        schedule.put(LocalTime.of(15, 30), "Client Call");
        schedule.put(LocalTime.of(17, 0), "Day End");
        
        LocalTime currentTime = LocalTime.of(12, 0);
        System.out.println("Current time: " + currentTime);
        
        // Next event
        Map.Entry<LocalTime, String> nextEvent = schedule.ceilingEntry(currentTime);
        if (nextEvent != null) {
            System.out.println("Next event: " + nextEvent.getValue() + " at " + nextEvent.getKey());
        }
        
        // Previous event
        Map.Entry<LocalTime, String> prevEvent = schedule.floorEntry(currentTime);
        if (prevEvent != null) {
            System.out.println("Previous event: " + prevEvent.getValue() + " at " + prevEvent.getKey());
        }
        
        // 3. Range queries
        System.out.println("\n3. TEMPERATURE READINGS:");
        TreeMap<Integer, Double> temperatures = new TreeMap<>(); // Hour -> Temperature
        temperatures.put(0, 15.5);   // Midnight
        temperatures.put(6, 12.3);   // 6 AM
        temperatures.put(12, 25.7);  // Noon
        temperatures.put(18, 22.1);  // 6 PM
        temperatures.put(23, 18.4);  // 11 PM
        
        // Average temperature during day hours (6 AM to 6 PM)
        NavigableMap<Integer, Double> dayReadings = temperatures.subMap(6, true, 18, true);
        double avgDayTemp = dayReadings.values().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        System.out.println("Average day temperature (6 AM - 6 PM): " + String.format("%.1f°C", avgDayTemp));
        
        // Find temperature closest to target
        double targetTemp = 20.0;
        Map.Entry<Integer, Double> closest = null;
        double minDiff = Double.MAX_VALUE;
        
        for (Map.Entry<Integer, Double> entry : temperatures.entrySet()) {
            double diff = Math.abs(entry.getValue() - targetTemp);
            if (diff < minDiff) {
                minDiff = diff;
                closest = entry;
            }
        }
        
        if (closest != null) {
            System.out.println("Temperature closest to " + targetTemp + "°C: " + 
                             closest.getValue() + "°C at hour " + closest.getKey());
        }
    }
    
    /**
     * Best practices and recommendations
     */
    public static void showBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("WHEN TO USE TREEMAP:");
        System.out.println("✓ Need sorted keys");
        System.out.println("✓ Range operations required");
        System.out.println("✓ NavigableMap/SortedMap features needed");
        System.out.println("✓ Consistent O(log n) performance preferred");
        
        System.out.println("\nWHEN TO AVOID TREEMAP:");
        System.out.println("✗ Simple key-value lookups (use HashMap)");
        System.out.println("✗ Keys don't have natural ordering");
        System.out.println("✗ Performance is critical (HashMap is faster)");
        System.out.println("✗ Need to store null keys");
        
        System.out.println("\nBEST PRACTICES:");
        System.out.println("✓ Use NavigableMap interface for flexibility");
        System.out.println("✓ Implement Comparable or provide Comparator");
        System.out.println("✓ Make key objects immutable");
        System.out.println("✓ Use subMap views for range operations");
        System.out.println("✓ Consider initial capacity for better performance");
        
        System.out.println("\nCOMMON PITFALLS:");
        System.out.println("✗ Modifying key objects after insertion");
        System.out.println("✗ Using mutable objects as keys");
        System.out.println("✗ Inconsistent compareTo() and equals()");
        System.out.println("✗ Not handling ClassCastException with incompatible types");
        
        // Example of proper key design
        System.out.println("\nPROPER KEY DESIGN EXAMPLE:");
        TreeMap<ImmutableKey, String> properMap = new TreeMap<>();
        properMap.put(new ImmutableKey("A", 1), "Value A1");
        properMap.put(new ImmutableKey("B", 2), "Value B2");
        properMap.put(new ImmutableKey("A", 2), "Value A2");
        
        System.out.println("TreeMap with immutable keys: " + properMap);
    }
}

// Supporting classes

/**
 * Helper class for time representation
 */
class LocalTime implements Comparable<LocalTime> {
    private final int hour;
    private final int minute;
    
    private LocalTime(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
    }
    
    public static LocalTime of(int hour, int minute) {
        return new LocalTime(hour, minute);
    }
    
    @Override
    public int compareTo(LocalTime other) {
        int hourCompare = Integer.compare(this.hour, other.hour);
        return hourCompare != 0 ? hourCompare : Integer.compare(this.minute, other.minute);
    }
    
    @Override
    public String toString() {
        return String.format("%02d:%02d", hour, minute);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof LocalTime)) return false;
        LocalTime localTime = (LocalTime) obj;
        return hour == localTime.hour && minute == localTime.minute;
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(hour, minute);
    }
}

/**
 * Immutable key class for TreeMap
 */
final class ImmutableKey implements Comparable<ImmutableKey> {
    private final String category;
    private final int priority;
    
    public ImmutableKey(String category, int priority) {
        this.category = Objects.requireNonNull(category);
        this.priority = priority;
    }
    
    @Override
    public int compareTo(ImmutableKey other) {
        int categoryCompare = this.category.compareTo(other.category);
        return categoryCompare != 0 ? categoryCompare : Integer.compare(this.priority, other.priority);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof ImmutableKey)) return false;
        ImmutableKey that = (ImmutableKey) obj;
        return priority == that.priority && Objects.equals(category, that.category);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(category, priority);
    }
    
    @Override
    public String toString() {
        return category + ":" + priority;
    }
}
