package dsa.fundamentals.collections;

import java.util.*;

/**
 * Complete Collection Framework Hierarchy Demonstration
 * 
 * Collection (Interface)
 * ├── List (Interface) - Ordered, allows duplicates
 * │   ├── ArrayList (Class) - Dynamic array, fast random access
 * │   ├── LinkedList (Class) - Doubly linked list, fast insertion/deletion
 * │   ├── Vector (Class) - Synchronized ArrayList (legacy)
 * │   └── Stack (Class) - LIFO operations (legacy)
 * │
 * ├── Set (Interface) - No duplicates
 * │   ├── HashSet (Class) - Hash table, no ordering
 * │   ├── LinkedHashSet (Class) - Hash table + linked list, insertion order
 * │   └── TreeSet (Class) - Red-black tree, sorted order
 * │
 * └── Queue (Interface) - FIFO operations
 *     ├── PriorityQueue (Class) - Heap-based priority queue
 *     ├── Deque (Interface) - Double-ended queue
 *     │   ├── ArrayDeque (Class) - Resizable array implementation
 *     │   └── LinkedList (Class) - Also implements Deque
 *     └── BlockingQueue (Interface) - Thread-safe queues
 * 
 * Map (Interface) - Key-value pairs (not part of Collection hierarchy)
 * ├── HashMap (Class) - Hash table, no ordering
 * ├── LinkedHashMap (Class) - Hash table + linked list, insertion/access order
 * ├── TreeMap (Class) - Red-black tree, sorted by keys
 * ├── ConcurrentHashMap (Class) - Thread-safe HashMap
 * └── Hashtable (Class) - Synchronized HashMap (legacy)
 */
public class CollectionHierarchy {
    
    public static void main(String[] args) {
        demonstrateCollectionHierarchy();
        demonstrateMapHierarchy();
        showKeyInterfaces();
    }
    
    /**
     * Demonstrates Collection interface hierarchy
     */
    public static void demonstrateCollectionHierarchy() {
        System.out.println("=== COLLECTION HIERARCHY DEMONSTRATION ===\n");
        
        // List Interface - Ordered, allows duplicates
        System.out.println("1. LIST INTERFACE:");
        List<String> arrayList = new ArrayList<>();
        List<String> linkedList = new LinkedList<>();
        List<String> vector = new Vector<>();
        Stack<String> stack = new Stack<>();
        
        // Adding elements
        arrayList.addAll(Arrays.asList("A", "B", "C", "A")); // Duplicates allowed
        linkedList.addAll(Arrays.asList("X", "Y", "Z", "X"));
        
        System.out.println("ArrayList: " + arrayList);
        System.out.println("LinkedList: " + linkedList);
        System.out.println("Index-based access (ArrayList): " + arrayList.get(1));
        
        // Set Interface - No duplicates
        System.out.println("\n2. SET INTERFACE:");
        Set<String> hashSet = new HashSet<>();
        Set<String> linkedHashSet = new LinkedHashSet<>();
        Set<String> treeSet = new TreeSet<>();
        
        // Adding elements (duplicates will be ignored)
        String[] elements = {"C", "A", "B", "A", "D"};
        
        hashSet.addAll(Arrays.asList(elements));
        linkedHashSet.addAll(Arrays.asList(elements));
        treeSet.addAll(Arrays.asList(elements));
        
        System.out.println("HashSet (no order): " + hashSet);
        System.out.println("LinkedHashSet (insertion order): " + linkedHashSet);
        System.out.println("TreeSet (sorted): " + treeSet);
        
        // Queue Interface - FIFO operations
        System.out.println("\n3. QUEUE INTERFACE:");
        Queue<Integer> priorityQueue = new PriorityQueue<>();
        Deque<String> arrayDeque = new ArrayDeque<>();
        
        // PriorityQueue - natural ordering (min-heap)
        priorityQueue.addAll(Arrays.asList(5, 2, 8, 1, 9));
        System.out.println("PriorityQueue elements: " + priorityQueue);
        System.out.println("Poll from PriorityQueue: " + priorityQueue.poll()); // Gets minimum
        
        // ArrayDeque - double-ended queue
        arrayDeque.addFirst("First");
        arrayDeque.addLast("Last");
        arrayDeque.addFirst("New First");
        System.out.println("ArrayDeque: " + arrayDeque);
    }
    
    /**
     * Demonstrates Map interface hierarchy
     */
    public static void demonstrateMapHierarchy() {
        System.out.println("\n=== MAP HIERARCHY DEMONSTRATION ===\n");
        
        Map<String, Integer> hashMap = new HashMap<>();
        Map<String, Integer> linkedHashMap = new LinkedHashMap<>();
        Map<String, Integer> treeMap = new TreeMap<>();
        
        // Adding key-value pairs
        String[] keys = {"Charlie", "Alice", "Bob", "David"};
        for (int i = 0; i < keys.length; i++) {
            hashMap.put(keys[i], i + 1);
            linkedHashMap.put(keys[i], i + 1);
            treeMap.put(keys[i], i + 1);
        }
        
        System.out.println("HashMap (no order): " + hashMap);
        System.out.println("LinkedHashMap (insertion order): " + linkedHashMap);
        System.out.println("TreeMap (sorted by keys): " + treeMap);
    }
    
    /**
     * Shows key interfaces and their characteristics
     */
    public static void showKeyInterfaces() {
        System.out.println("\n=== KEY INTERFACE CHARACTERISTICS ===\n");
        
        System.out.println("COLLECTION INTERFACE:");
        System.out.println("- Root interface for List, Set, Queue");
        System.out.println("- Basic operations: add(), remove(), contains(), size(), isEmpty()");
        System.out.println("- Iteration: iterator(), forEach()");
        
        System.out.println("\nLIST INTERFACE:");
        System.out.println("- Ordered collection (sequence)");
        System.out.println("- Allows duplicates");
        System.out.println("- Index-based access: get(), set(), add(index), remove(index)");
        System.out.println("- Search operations: indexOf(), lastIndexOf()");
        
        System.out.println("\nSET INTERFACE:");
        System.out.println("- Collection with no duplicate elements");
        System.out.println("- Mathematical set abstraction");
        System.out.println("- Same methods as Collection interface");
        
        System.out.println("\nQUEUE INTERFACE:");
        System.out.println("- Collection for holding elements before processing");
        System.out.println("- FIFO (First In, First Out) ordering");
        System.out.println("- Operations: offer(), poll(), peek(), element()");
        
        System.out.println("\nMAP INTERFACE:");
        System.out.println("- Key-value pair mapping");
        System.out.println("- No duplicate keys allowed");
        System.out.println("- Operations: put(), get(), remove(), containsKey(), containsValue()");
        System.out.println("- Views: keySet(), values(), entrySet()");
        
        // Demonstrate some key methods
        demonstrateCommonOperations();
    }
    
    private static void demonstrateCommonOperations() {
        System.out.println("\n=== COMMON OPERATIONS DEMO ===\n");
        
        Collection<String> collection = new ArrayList<>();
        collection.add("Java");
        collection.add("Python");
        collection.add("JavaScript");
        
        System.out.println("Collection: " + collection);
        System.out.println("Size: " + collection.size());
        System.out.println("Contains 'Java': " + collection.contains("Java"));
        System.out.println("Is empty: " + collection.isEmpty());
        
        // Iteration methods
        System.out.println("\nIteration methods:");
        
        // 1. Enhanced for loop
        System.out.print("Enhanced for: ");
        for (String item : collection) {
            System.out.print(item + " ");
        }
        
        // 2. Iterator
        System.out.print("\nIterator: ");
        Iterator<String> iterator = collection.iterator();
        while (iterator.hasNext()) {
            System.out.print(iterator.next() + " ");
        }
        
        // 3. forEach (Java 8+)
        System.out.print("\nforEach: ");
        collection.forEach(item -> System.out.print(item + " "));
        
        System.out.println("\n");
    }
}
