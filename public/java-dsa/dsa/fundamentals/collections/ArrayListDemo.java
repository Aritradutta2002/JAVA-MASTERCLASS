package dsa.fundamentals.collections;

import java.util.*;

/**
 * Complete ArrayList Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. ArrayList internal implementation
 * 2. Dynamic resizing mechanism
 * 3. Time complexity analysis
 * 4. ArrayList vs Array vs LinkedList
 * 5. Common operations and best practices
 * 6. Memory considerations
 */
public class ArrayListDemo {
    
    public static void main(String[] args) {
        demonstrateBasicOperations();
        demonstrateInternalWorking();
        demonstratePerformanceAnalysis();
        demonstrateCommonInterviewScenarios();
        demonstrateBestPractices();
    }
    
    /**
     * Basic ArrayList operations
     */
    public static void demonstrateBasicOperations() {
        System.out.println("=== ARRAYLIST BASIC OPERATIONS ===\n");
        
        // Different ways to create ArrayList
        ArrayList<String> list1 = new ArrayList<>(); // Default capacity = 10
        ArrayList<String> list2 = new ArrayList<>(20); // Initial capacity = 20
        ArrayList<String> list3 = new ArrayList<>(Arrays.asList("A", "B", "C")); // From collection
        
        System.out.println("Empty ArrayList: " + list1);
        System.out.println("ArrayList with initial capacity: " + list2);
        System.out.println("ArrayList from collection: " + list3);
        
        // Adding elements
        list1.add("First");           // Add at end
        list1.add("Second");
        list1.add(1, "Middle");       // Add at specific index
        list1.addAll(Arrays.asList("Fourth", "Fifth")); // Add collection
        
        System.out.println("After additions: " + list1);
        
        // Accessing elements
        System.out.println("Element at index 0: " + list1.get(0));
        System.out.println("First element: " + list1.get(0));
        System.out.println("Last element: " + list1.get(list1.size() - 1));
        
        // Searching elements
        System.out.println("Index of 'Middle': " + list1.indexOf("Middle"));
        System.out.println("Contains 'Second': " + list1.contains("Second"));
        
        // Modifying elements
        list1.set(2, "Modified");     // Replace element at index
        System.out.println("After modification: " + list1);
        
        // Removing elements
        list1.remove("First");        // Remove by object
        list1.remove(0);             // Remove by index
        System.out.println("After removals: " + list1);
        
        // Size and capacity
        System.out.println("Size: " + list1.size());
        System.out.println("Is empty: " + list1.isEmpty());
    }
    
    /**
     * Demonstrates how ArrayList works internally
     * CRUCIAL FOR INTERVIEWS!
     */
    public static void demonstrateInternalWorking() {
        System.out.println("\n=== ARRAYLIST INTERNAL WORKING ===\n");
        
        System.out.println("INTERNAL IMPLEMENTATION:");
        System.out.println("1. ArrayList is backed by an Object[] array");
        System.out.println("2. Default initial capacity is 10");
        System.out.println("3. When capacity is exceeded, array is resized");
        System.out.println("4. New capacity = oldCapacity + (oldCapacity >> 1) [1.5x growth]");
        System.out.println("5. Elements are copied to new array using System.arraycopy()");
        
        // Demonstrating capacity growth
        ArrayList<Integer> numbers = new ArrayList<>();
        System.out.println("\nCapacity Growth Demonstration:");
        
        for (int i = 1; i <= 15; i++) {
            numbers.add(i);
            if (i == 10 || i == 15) {
                System.out.println("After adding " + i + " elements: " + numbers);
                System.out.println("Internal array would have been resized at element " + 
                                 (i == 15 ? "11 (10 -> 15)" : "1 (0 -> 10)"));
            }
        }
        
        System.out.println("\nKEY POINTS FOR INTERVIEWS:");
        System.out.println("- ArrayList implements RandomAccess interface");
        System.out.println("- Not synchronized (not thread-safe)");
        System.out.println("- Allows null values and duplicates");
        System.out.println("- Maintains insertion order");
        System.out.println("- Implements Serializable and Cloneable");
    }
    
    /**
     * Time complexity analysis - VERY IMPORTANT FOR INTERVIEWS
     */
    public static void demonstratePerformanceAnalysis() {
        System.out.println("\n=== PERFORMANCE ANALYSIS ===\n");
        
        System.out.println("TIME COMPLEXITY:");
        System.out.println("┌─────────────────────┬──────────────┬─────────────────┐");
        System.out.println("│ Operation           │ Best/Average │ Worst Case      │");
        System.out.println("├─────────────────────┼──────────────┼─────────────────┤");
        System.out.println("│ get(index)          │ O(1)         │ O(1)            │");
        System.out.println("│ set(index, element) │ O(1)         │ O(1)            │");
        System.out.println("│ add(element)        │ O(1)         │ O(n) [resize]   │");
        System.out.println("│ add(index, element) │ O(n)         │ O(n)            │");
        System.out.println("│ remove(index)       │ O(n)         │ O(n)            │");
        System.out.println("│ remove(object)      │ O(n)         │ O(n)            │");
        System.out.println("│ contains(object)    │ O(n)         │ O(n)            │");
        System.out.println("│ indexOf(object)     │ O(n)         │ O(n)            │");
        System.out.println("└─────────────────────┴──────────────┴─────────────────┘");
        
        System.out.println("\nSPACE COMPLEXITY: O(n) where n is the number of elements");
        
        // Performance demonstration
        ArrayList<Integer> list = new ArrayList<>();
        
        // Fast operations
        long startTime = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            list.add(i); // O(1) amortized
        }
        long endTime = System.nanoTime();
        System.out.println("Time to add 100,000 elements at end: " + 
                          (endTime - startTime) / 1_000_000 + " ms");
        
        // Slow operation
        startTime = System.nanoTime();
        for (int i = 0; i < 1000; i++) {
            list.add(0, i); // O(n) - shifts all elements
        }
        endTime = System.nanoTime();
        System.out.println("Time to add 1,000 elements at beginning: " + 
                          (endTime - startTime) / 1_000_000 + " ms");
        
        System.out.println("\nConclusion: Avoid frequent insertions at beginning/middle for large lists");
    }
    
    /**
     * Common interview scenarios and questions
     */
    public static void demonstrateCommonInterviewScenarios() {
        System.out.println("\n=== COMMON INTERVIEW SCENARIOS ===\n");
        
        // 1. ArrayList vs Array
        System.out.println("1. ARRAYLIST VS ARRAY:");
        System.out.println("ArrayList: Dynamic size, rich API, object wrapper overhead");
        System.out.println("Array: Fixed size, primitive support, better memory efficiency");
        
        // 2. ArrayList vs LinkedList
        System.out.println("\n2. ARRAYLIST VS LINKEDLIST:");
        System.out.println("ArrayList: Better for random access, iteration");
        System.out.println("LinkedList: Better for frequent insertions/deletions");
        
        // 3. Converting ArrayList to Array and vice versa
        ArrayList<String> list = new ArrayList<>(Arrays.asList("A", "B", "C"));
        
        // ArrayList to Array
        String[] array1 = list.toArray(new String[0]); // Preferred way
        String[] array2 = list.toArray(new String[list.size()]); // Traditional way
        Object[] array3 = list.toArray(); // Returns Object[]
        
        System.out.println("\n3. CONVERSION EXAMPLES:");
        System.out.println("ArrayList: " + list);
        System.out.println("To String[]: " + Arrays.toString(array1));
        
        // Array to ArrayList
        String[] sourceArray = {"X", "Y", "Z"};
        ArrayList<String> fromArray = new ArrayList<>(Arrays.asList(sourceArray));
        System.out.println("Array to ArrayList: " + fromArray);
        
        // 4. Removing elements while iterating (common mistake)
        System.out.println("\n4. SAFE REMOVAL DURING ITERATION:");
        ArrayList<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        
        // Wrong way - ConcurrentModificationException
        try {
            for (Integer num : numbers) {
                if (num % 2 == 0) {
                    numbers.remove(num); // This will throw exception
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("ConcurrentModificationException caught!");
        }
        
        // Correct ways
        numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        
        // Using Iterator
        Iterator<Integer> it = numbers.iterator();
        while (it.hasNext()) {
            if (it.next() % 2 == 0) {
                it.remove();
            }
        }
        System.out.println("After removal using Iterator: " + numbers);
        
        // Using removeIf (Java 8+)
        numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        numbers.removeIf(num -> num % 2 == 0);
        System.out.println("After removal using removeIf: " + numbers);
    }
    
    /**
     * Best practices and optimization tips
     */
    public static void demonstrateBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("1. INITIALIZATION:");
        System.out.println("✓ Specify initial capacity if size is known");
        System.out.println("✓ Use Arrays.asList() for fixed data");
        System.out.println("✗ Don't use raw types");
        
        // Good practices
        ArrayList<String> good1 = new ArrayList<>(100); // Known capacity
        List<String> good2 = new ArrayList<>(); // Program to interface
        List<String> good3 = Arrays.asList("A", "B", "C"); // Immutable list
        
        System.out.println("\n2. MEMORY OPTIMIZATION:");
        ArrayList<Integer> list = new ArrayList<>(1000);
        for (int i = 0; i < 100; i++) {
            list.add(i);
        }
        list.trimToSize(); // Reduce capacity to current size
        System.out.println("✓ Use trimToSize() to reduce memory footprint");
        
        System.out.println("\n3. THREAD SAFETY:");
        List<String> synchronizedList = Collections.synchronizedList(new ArrayList<>());
        System.out.println("✓ Use Collections.synchronizedList() for thread safety");
        System.out.println("✓ Or use CopyOnWriteArrayList for read-heavy scenarios");
        
        System.out.println("\n4. PERFORMANCE TIPS:");
        System.out.println("✓ Use ensureCapacity() before bulk operations");
        System.out.println("✓ Prefer addAll() over multiple add() calls");
        System.out.println("✓ Use subList() for range operations");
        System.out.println("✗ Avoid frequent insertions at beginning/middle");
        
        // SubList example
        List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<Integer> subList = numbers.subList(2, 7); // View of original list
        System.out.println("Original: " + numbers);
        System.out.println("SubList (2-6): " + subList);
        
        subList.clear(); // Affects original list
        System.out.println("After subList.clear(): " + numbers);
    }
}
