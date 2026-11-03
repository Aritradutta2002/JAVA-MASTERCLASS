package dsa.fundamentals.collections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complete HashMap Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. HashMap internal implementation (most important!)
 * 2. Hash collision handling
 * 3. Load factor and rehashing
 * 4. Time complexity analysis
 * 5. HashMap vs Hashtable vs ConcurrentHashMap
 * 6. Common pitfalls and best practices
 */
public class HashMapDemo {
    
    public static void main(String[] args) {
        demonstrateBasicOperations();
        explainInternalImplementation();
        demonstrateHashCollisions();
        analyzePerformance();
        compareMapImplementations();
        demonstrateCommonInterviewScenarios();
        showBestPractices();
    }
    
    /**
     * Basic HashMap operations
     */
    public static void demonstrateBasicOperations() {
        System.out.println("=== HASHMAP BASIC OPERATIONS ===\n");
        
        // Different ways to create HashMap
        HashMap<String, Integer> map1 = new HashMap<>(); // Default capacity=16, load factor=0.75
        HashMap<String, Integer> map2 = new HashMap<>(32); // Initial capacity=32
        HashMap<String, Integer> map3 = new HashMap<>(32, 0.8f); // Custom load factor
        
        // Adding key-value pairs
        map1.put("Alice", 25);
        map1.put("Bob", 30);
        map1.put("Charlie", 35);
        map1.put("Alice", 26); // Overwrites previous value
        
        System.out.println("HashMap after additions: " + map1);
        
        // Accessing values
        System.out.println("Alice's age: " + map1.get("Alice"));
        System.out.println("Non-existent key: " + map1.get("David")); // Returns null
        System.out.println("With default value: " + map1.getOrDefault("David", 0));
        
        // Checking existence
        System.out.println("Contains key 'Bob': " + map1.containsKey("Bob"));
        System.out.println("Contains value 30: " + map1.containsValue(30));
        
        // Removing elements
        Integer removed = map1.remove("Charlie");
        System.out.println("Removed Charlie: " + removed);
        System.out.println("Map after removal: " + map1);
        
        // Conditional operations (Java 8+)
        map1.putIfAbsent("David", 40); // Only if key doesn't exist
        map1.computeIfAbsent("Eve", k -> k.length() * 10); // Compute value if absent
        map1.compute("Alice", (k, v) -> v + 1); // Always compute new value
        
        System.out.println("After conditional operations: " + map1);
        
        // Iteration methods
        System.out.println("\nIteration methods:");
        
        // 1. KeySet
        System.out.print("Keys: ");
        for (String key : map1.keySet()) {
            System.out.print(key + " ");
        }
        
        // 2. Values
        System.out.print("\nValues: ");
        for (Integer value : map1.values()) {
            System.out.print(value + " ");
        }
        
        // 3. EntrySet (most efficient)
        System.out.println("\nEntries:");
        for (Map.Entry<String, Integer> entry : map1.entrySet()) {
            System.out.println(entry.getKey() + " -> " + entry.getValue());
        }
        
        // 4. forEach (Java 8+)
        System.out.println("\nUsing forEach:");
        map1.forEach((key, value) -> System.out.println(key + " = " + value));
    }
    
    /**
     * CRUCIAL: HashMap internal implementation explanation
     * This is the MOST ASKED interview question about HashMap!
     */
    public static void explainInternalImplementation() {
        System.out.println("\n=== HASHMAP INTERNAL IMPLEMENTATION ===\n");
        
        System.out.println("INTERNAL STRUCTURE:");
        System.out.println("1. HashMap uses an array of Node<K,V>[] called 'table'");
        System.out.println("2. Each array element is a bucket that can hold a linked list or tree");
        System.out.println("3. Default initial capacity: 16, Default load factor: 0.75");
        System.out.println("4. Hash function: key.hashCode() ^ (key.hashCode() >>> 16)");
        System.out.println("5. Index calculation: hash & (capacity - 1)");
        
        System.out.println("\nPUT OPERATION STEPS:");
        System.out.println("1. Calculate hash of the key");
        System.out.println("2. Find bucket index using hash & (capacity - 1)");
        System.out.println("3. If bucket is empty, create new node");
        System.out.println("4. If bucket has collision:");
        System.out.println("   - If key exists, update value");
        System.out.println("   - Else, add to linked list or tree");
        System.out.println("5. If bucket size > TREEIFY_THRESHOLD (8), convert to tree");
        System.out.println("6. If load factor exceeded, resize (double capacity)");
        
        System.out.println("\nGET OPERATION STEPS:");
        System.out.println("1. Calculate hash of the key");
        System.out.println("2. Find bucket index");
        System.out.println("3. If first node matches, return its value");
        System.out.println("4. Else, traverse linked list or search tree");
        
        // Demonstrate with custom class to show hashing
        demonstrateCustomHashCode();
    }
    
    /**
     * Demonstrates custom hashCode implementation
     */
    private static void demonstrateCustomHashCode() {
        System.out.println("\nCUSTOM HASHCODE DEMONSTRATION:");
        
        class Person {
            private String name;
            private int age;
            
            public Person(String name, int age) {
                this.name = name;
                this.age = age;
            }
            
            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                Person person = (Person) obj;
                return age == person.age && Objects.equals(name, person.name);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(name, age);
            }
            
            @Override
            public String toString() {
                return name + "(" + age + ")";
            }
        }
        
        HashMap<Person, String> personMap = new HashMap<>();
        Person p1 = new Person("John", 25);
        Person p2 = new Person("John", 25); // Same content, different object
        
        personMap.put(p1, "Engineer");
        
        System.out.println("p1.equals(p2): " + p1.equals(p2));
        System.out.println("p1.hashCode(): " + p1.hashCode());
        System.out.println("p2.hashCode(): " + p2.hashCode());
        System.out.println("Can retrieve using p2: " + personMap.get(p2));
        
        System.out.println("\nKEY RULE: If a.equals(b) is true, then a.hashCode() == b.hashCode()");
    }
    
    /**
     * Demonstrates hash collisions and how HashMap handles them
     */
    public static void demonstrateHashCollisions() {
        System.out.println("\n=== HASH COLLISION HANDLING ===\n");
        
        System.out.println("COLLISION RESOLUTION:");
        System.out.println("1. Chaining: Use linked list (or tree) for same bucket");
        System.out.println("2. Tree conversion: When chain length > 8, convert to Red-Black tree");
        System.out.println("3. Tree to list: When tree size < 6 during removal, convert back");
        
        // Create objects that will cause collisions
        class CollisionDemo {
            private int value;
            
            public CollisionDemo(int value) {
                this.value = value;
            }
            
            @Override
            public int hashCode() {
                return value % 4; // Force collisions
            }
            
            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                CollisionDemo that = (CollisionDemo) obj;
                return value == that.value;
            }
            
            @Override
            public String toString() {
                return "CD(" + value + ")";
            }
        }
        
        HashMap<CollisionDemo, String> collisionMap = new HashMap<>();
        
        // These will have same hash codes
        for (int i = 0; i < 12; i++) {
            CollisionDemo key = new CollisionDemo(i);
            collisionMap.put(key, "Value" + i);
            System.out.println("Added " + key + " with hashCode: " + key.hashCode());
        }
        
        System.out.println("\nMap size: " + collisionMap.size());
        System.out.println("Multiple keys have same hash codes, causing collisions");
        
        System.out.println("\nPERFORMANCE IMPACT OF COLLISIONS:");
        System.out.println("- Good hash function: O(1) average access time");
        System.out.println("- Poor hash function: O(n) worst case (all keys in same bucket)");
        System.out.println("- Tree optimization: O(log n) worst case when bucket becomes tree");
    }
    
    /**
     * Performance analysis and time complexity
     */
    public static void analyzePerformance() {
        System.out.println("\n=== PERFORMANCE ANALYSIS ===\n");
        
        System.out.println("TIME COMPLEXITY:");
        System.out.println("┌─────────────────┬──────────────┬─────────────────┐");
        System.out.println("│ Operation       │ Average Case │ Worst Case      │");
        System.out.println("├─────────────────┼──────────────┼─────────────────┤");
        System.out.println("│ get()           │ O(1)         │ O(n) / O(log n) │");
        System.out.println("│ put()           │ O(1)         │ O(n) / O(log n) │");
        System.out.println("│ remove()        │ O(1)         │ O(n) / O(log n) │");
        System.out.println("│ containsKey()   │ O(1)         │ O(n) / O(log n) │");
        System.out.println("│ containsValue() │ O(n)         │ O(n)            │");
        System.out.println("└─────────────────┴──────────────┴─────────────────┘");
        
        System.out.println("\nSPACE COMPLEXITY: O(n) where n is number of key-value pairs");
        
        System.out.println("\nLOAD FACTOR IMPACT:");
        System.out.println("- Load Factor = size / capacity");
        System.out.println("- Default load factor: 0.75 (good balance of time vs space)");
        System.out.println("- Higher load factor: More space efficient, more collisions");
        System.out.println("- Lower load factor: Less collisions, more memory usage");
        System.out.println("- Rehashing occurs when load factor threshold is exceeded");
        
        // Demonstrate performance
        HashMap<Integer, String> perfMap = new HashMap<>();
        
        long startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            perfMap.put(i, "Value" + i);
        }
        long endTime = System.nanoTime();
        System.out.println("\nTime to put 100,000 elements: " + 
                          (endTime - startTime) / 1_000_000 + " ms");
        
        startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            perfMap.get(i);
        }
        endTime = System.nanoTime();
        System.out.println("Time to get 100,000 elements: " + 
                          (endTime - startTime) / 1_000_000 + " ms");
    }
    
    /**
     * Compare different Map implementations
     */
    public static void compareMapImplementations() {
        System.out.println("\n=== MAP IMPLEMENTATIONS COMPARISON ===\n");
        
        System.out.println("HASHMAP vs HASHTABLE vs CONCURRENTHASHMAP:");
        System.out.println("┌──────────────────┬─────────────┬─────────────┬─────────────────────┐");
        System.out.println("│ Feature          │ HashMap     │ Hashtable   │ ConcurrentHashMap   │");
        System.out.println("├──────────────────┼─────────────┼─────────────┼─────────────────────┤");
        System.out.println("│ Thread Safety    │ No          │ Yes         │ Yes                 │");
        System.out.println("│ Null Keys        │ One allowed │ Not allowed │ Not allowed         │");
        System.out.println("│ Null Values      │ Allowed     │ Not allowed │ Not allowed         │");
        System.out.println("│ Synchronization  │ None        │ Method level│ Segment level       │");
        System.out.println("│ Performance      │ Best        │ Slow        │ Better than Hashtable│");
        System.out.println("│ Since Version    │ Java 1.2    │ Java 1.0    │ Java 1.5            │");
        System.out.println("└──────────────────┴─────────────┴─────────────┴─────────────────────┘");
        
        // Demonstrate null handling
        HashMap<String, String> hashMap = new HashMap<>();
        Hashtable<String, String> hashTable = new Hashtable<>();
        ConcurrentHashMap<String, String> concurrentMap = new ConcurrentHashMap<>();
        
        hashMap.put(null, "null key allowed");
        hashMap.put("null value", null);
        System.out.println("HashMap with nulls: " + hashMap);
        
        try {
            hashTable.put(null, "test");
        } catch (NullPointerException e) {
            System.out.println("Hashtable doesn't allow null keys");
        }
        
        try {
            concurrentMap.put("test", null);
        } catch (NullPointerException e) {
            System.out.println("ConcurrentHashMap doesn't allow null values");
        }
    }
    
    /**
     * Common interview scenarios
     */
    public static void demonstrateCommonInterviewScenarios() {
        System.out.println("\n=== COMMON INTERVIEW SCENARIOS ===\n");
        
        // 1. Why override hashCode() and equals()?
        System.out.println("1. WHY OVERRIDE HASHCODE() AND EQUALS()?");
        class BadKey {
            private String name;
            
            public BadKey(String name) {
                this.name = name;
            }
            
            // Only equals, no hashCode override
            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                BadKey badKey = (BadKey) obj;
                return Objects.equals(name, badKey.name);
            }
            
            @Override
            public String toString() {
                return name;
            }
        }
        
        HashMap<BadKey, String> badMap = new HashMap<>();
        BadKey key1 = new BadKey("test");
        BadKey key2 = new BadKey("test");
        
        badMap.put(key1, "value1");
        System.out.println("key1.equals(key2): " + key1.equals(key2));
        System.out.println("key1.hashCode(): " + key1.hashCode());
        System.out.println("key2.hashCode(): " + key2.hashCode());
        System.out.println("Can retrieve with key2: " + badMap.get(key2)); // null!
        
        // 2. Immutable keys recommendation
        System.out.println("\n2. WHY USE IMMUTABLE KEYS?");
        class MutableKey {
            private String value;
            
            public MutableKey(String value) {
                this.value = value;
            }
            
            public void setValue(String value) {
                this.value = value;
            }
            
            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                MutableKey that = (MutableKey) obj;
                return Objects.equals(value, that.value);
            }
            
            @Override
            public int hashCode() {
                return Objects.hash(value);
            }
            
            @Override
            public String toString() {
                return value;
            }
        }
        
        HashMap<MutableKey, String> mutableMap = new HashMap<>();
        MutableKey mutableKey = new MutableKey("original");
        mutableMap.put(mutableKey, "stored value");
        
        System.out.println("Before mutation: " + mutableMap.get(mutableKey));
        mutableKey.setValue("modified"); // Mutate the key!
        System.out.println("After mutation: " + mutableMap.get(mutableKey)); // Lost!
        
        System.out.println("Lesson: Use immutable objects as keys (String, Integer, etc.)");
    }
    
    /**
     * Best practices and tips
     */
    public static void showBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("1. INITIALIZATION:");
        System.out.println("✓ Specify initial capacity if size is known");
        System.out.println("✓ Use appropriate load factor based on use case");
        
        // Good practice examples
        Map<String, Integer> goodMap1 = new HashMap<>(100); // Known size
        Map<String, Integer> goodMap2 = new HashMap<>(100, 0.6f); // Memory-critical
        
        System.out.println("\n2. KEY DESIGN:");
        System.out.println("✓ Use immutable objects as keys");
        System.out.println("✓ Override both equals() and hashCode()");
        System.out.println("✓ Ensure hashCode() contract is maintained");
        System.out.println("✗ Don't use mutable objects as keys");
        
        System.out.println("\n3. PERFORMANCE OPTIMIZATION:");
        System.out.println("✓ Use entrySet() for iteration (most efficient)");
        System.out.println("✓ Consider initial capacity for large maps");
        System.out.println("✓ Use computeIfAbsent() for complex value creation");
        System.out.println("✗ Avoid containsValue() for large maps (O(n))");
        
        System.out.println("\n4. THREAD SAFETY:");
        System.out.println("✓ Use ConcurrentHashMap for concurrent access");
        System.out.println("✓ Use Collections.synchronizedMap() with external sync");
        System.out.println("✗ Don't use Hashtable (legacy, poor performance)");
        
        // Example of efficient patterns
        Map<String, List<String>> groupMap = new HashMap<>();
        
        // Old way
        String key = "group1";
        if (!groupMap.containsKey(key)) {
            groupMap.put(key, new ArrayList<>());
        }
        groupMap.get(key).add("item1");
        
        // New way (Java 8+)
        groupMap.computeIfAbsent("group2", k -> new ArrayList<>()).add("item2");
        
        System.out.println("\nEfficient grouping: " + groupMap);
    }
}
