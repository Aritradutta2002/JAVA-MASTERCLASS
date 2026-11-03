package dsa.fundamentals.collections;

import java.util.*;

/**
 * Complete Iterator Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. Iterator vs ListIterator vs Enumeration
 * 2. Fail-fast vs Fail-safe iterators
 * 3. ConcurrentModificationException
 * 4. Safe removal during iteration
 * 5. Custom iterator implementation
 * 6. Enhanced for-loop vs Iterator
 */
public class IteratorDemo {
    
    public static void main(String[] args) {
        demonstrateBasicIterator();
        demonstrateListIterator();
        demonstrateEnumeration();
        explainFailFastFailSafe();
        demonstrateConcurrentModification();
        showSafeRemovalTechniques();
        demonstrateCustomIterator();
        compareIterationMethods();
    }
    
    /**
     * Basic Iterator demonstration
     */
    public static void demonstrateBasicIterator() {
        System.out.println("=== BASIC ITERATOR ===\n");
        
        List<String> fruits = new ArrayList<>(Arrays.asList("Apple", "Banana", "Cherry", "Date"));
        
        // Basic iterator usage
        Iterator<String> iterator = fruits.iterator();
        
        System.out.println("Iterator methods:");
        System.out.println("hasNext() - checks if more elements exist");
        System.out.println("next() - returns next element");
        System.out.println("remove() - removes last returned element");
        
        System.out.println("\nIterating through fruits:");
        while (iterator.hasNext()) {
            String fruit = iterator.next();
            System.out.println("Current fruit: " + fruit);
            
            // Safe removal using iterator
            if (fruit.equals("Banana")) {
                iterator.remove();
                System.out.println("Removed Banana safely");
            }
        }
        
        System.out.println("Fruits after removal: " + fruits);
        
        // Iterator characteristics
        System.out.println("\nITERATOR CHARACTERISTICS:");
        System.out.println("✓ Forward-only traversal");
        System.out.println("✓ Can remove elements safely");
        System.out.println("✓ Fail-fast (throws ConcurrentModificationException)");
        System.out.println("✗ Cannot add elements");
        System.out.println("✗ Cannot go backwards");
    }
    
    /**
     * ListIterator demonstration - enhanced iterator for Lists
     */
    public static void demonstrateListIterator() {
        System.out.println("\n=== LISTITERATOR ===\n");
        
        List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        
        // ListIterator provides bidirectional traversal
        ListIterator<Integer> listIterator = numbers.listIterator();
        
        System.out.println("ListIterator additional methods:");
        System.out.println("hasPrevious() - checks if previous element exists");
        System.out.println("previous() - returns previous element");
        System.out.println("add() - adds element at current position");
        System.out.println("set() - replaces last returned element");
        System.out.println("nextIndex() / previousIndex() - returns indices");
        
        // Forward traversal with modification
        System.out.println("\nForward traversal with doubling:");
        while (listIterator.hasNext()) {
            int num = listIterator.next();
            listIterator.set(num * 2); // Replace with doubled value
        }
        System.out.println("After doubling: " + numbers);
        
        // Backward traversal
        System.out.println("\nBackward traversal:");
        while (listIterator.hasPrevious()) {
            int num = listIterator.previous();
            System.out.print(num + " ");
        }
        
        // Adding elements during iteration
        System.out.println("\n\nAdding elements during iteration:");
        listIterator = numbers.listIterator();
        while (listIterator.hasNext()) {
            int num = listIterator.next();
            if (num % 4 == 0) { // After elements divisible by 4
                listIterator.add(999); // Add 999
                System.out.println("Added 999 after " + num);
            }
        }
        System.out.println("After additions: " + numbers);
        
        // Starting from specific position
        System.out.println("\nStarting iteration from index 2:");
        ListIterator<Integer> positionIterator = numbers.listIterator(2);
        while (positionIterator.hasNext()) {
            System.out.print(positionIterator.next() + " ");
        }
        System.out.println();
    }
    
    /**
     * Enumeration demonstration - legacy iterator
     */
    public static void demonstrateEnumeration() {
        System.out.println("\n=== ENUMERATION (Legacy) ===\n");
        
        Vector<String> vector = new Vector<>(Arrays.asList("A", "B", "C", "D"));
        Hashtable<String, Integer> hashtable = new Hashtable<>();
        hashtable.put("One", 1);
        hashtable.put("Two", 2);
        hashtable.put("Three", 3);
        
        // Enumeration for Vector
        System.out.println("Vector enumeration:");
        Enumeration<String> vectorEnum = vector.elements();
        while (vectorEnum.hasMoreElements()) {
            System.out.println(vectorEnum.nextElement());
        }
        
        // Enumeration for Hashtable
        System.out.println("\nHashtable key enumeration:");
        Enumeration<String> keyEnum = hashtable.keys();
        while (keyEnum.hasMoreElements()) {
            String key = keyEnum.nextElement();
            System.out.println(key + " = " + hashtable.get(key));
        }
        
        System.out.println("\nENUMERATION vs ITERATOR:");
        System.out.println("┌─────────────────┬─────────────┬──────────────┐");
        System.out.println("│ Feature         │ Enumeration │ Iterator     │");
        System.out.println("├─────────────────┼─────────────┼──────────────┤");
        System.out.println("│ Remove support  │ No          │ Yes          │");
        System.out.println("│ Method names    │ Longer      │ Shorter      │");
        System.out.println("│ Since version   │ JDK 1.0     │ JDK 1.2      │");
        System.out.println("│ Fail-fast       │ No          │ Yes          │");
        System.out.println("│ Thread safety   │ Yes         │ No           │");
        System.out.println("└─────────────────┴─────────────┴──────────────┘");
    }
    
    /**
     * Fail-fast vs Fail-safe explanation
     */
    public static void explainFailFastFailSafe() {
        System.out.println("\n=== FAIL-FAST vs FAIL-SAFE ===\n");
        
        System.out.println("FAIL-FAST ITERATORS:");
        System.out.println("- Throw ConcurrentModificationException if collection is modified");
        System.out.println("- Used by: ArrayList, HashMap, HashSet, etc.");
        System.out.println("- Detect structural modifications during iteration");
        System.out.println("- Use modCount to track modifications");
        
        System.out.println("\nFAIL-SAFE ITERATORS:");
        System.out.println("- Work on copy of collection");
        System.out.println("- Don't throw ConcurrentModificationException");
        System.out.println("- Used by: ConcurrentHashMap, CopyOnWriteArrayList, etc.");
        System.out.println("- May not reflect latest changes");
        
        // Demonstrate fail-fast
        System.out.println("\nFAIL-FAST EXAMPLE:");
        List<String> failFastList = new ArrayList<>(Arrays.asList("A", "B", "C"));
        
        try {
            for (String item : failFastList) {
                System.out.println("Processing: " + item);
                if (item.equals("B")) {
                    failFastList.add("D"); // This will cause ConcurrentModificationException
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("ConcurrentModificationException caught!");
        }
        
        // Demonstrate fail-safe
        System.out.println("\nFAIL-SAFE EXAMPLE:");
        List<String> failSafeList = new java.util.concurrent.CopyOnWriteArrayList<>(
            Arrays.asList("A", "B", "C"));
        
        for (String item : failSafeList) {
            System.out.println("Processing: " + item);
            if (item.equals("B")) {
                failSafeList.add("D"); // This works fine
                System.out.println("Added D safely");
            }
        }
        System.out.println("Final list: " + failSafeList);
    }
    
    /**
     * Concurrent modification scenarios
     */
    public static void demonstrateConcurrentModification() {
        System.out.println("\n=== CONCURRENT MODIFICATION SCENARIOS ===\n");
        
        List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        
        System.out.println("Original list: " + numbers);
        
        // Scenario 1: Direct modification during enhanced for-loop
        System.out.println("\n1. DIRECT MODIFICATION (Will throw exception):");
        try {
            for (Integer num : numbers) {
                if (num % 2 == 0) {
                    numbers.remove(num); // ConcurrentModificationException
                }
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("Exception caught: " + e.getClass().getSimpleName());
        }
        
        // Scenario 2: Modification in different thread
        System.out.println("\n2. MODIFICATION FROM DIFFERENT THREAD:");
        List<String> sharedList = Collections.synchronizedList(new ArrayList<>(
            Arrays.asList("Item1", "Item2", "Item3", "Item4", "Item5")));
        
        Thread modifierThread = new Thread(() -> {
            try {
                Thread.sleep(100); // Let iteration start
                sharedList.add("NewItem");
                System.out.println("Added item from another thread");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        modifierThread.start();
        
        try {
            for (String item : sharedList) {
                System.out.println("Reading: " + item);
                Thread.sleep(50); // Slow iteration
            }
        } catch (ConcurrentModificationException e) {
            System.out.println("Concurrent modification detected!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            modifierThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    /**
     * Safe removal techniques
     */
    public static void showSafeRemovalTechniques() {
        System.out.println("\n=== SAFE REMOVAL TECHNIQUES ===\n");
        
        // Technique 1: Iterator.remove()
        List<Integer> list1 = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        System.out.println("Original: " + list1);
        
        Iterator<Integer> it = list1.iterator();
        while (it.hasNext()) {
            Integer num = it.next();
            if (num % 2 == 0) {
                it.remove(); // Safe removal
            }
        }
        System.out.println("After iterator removal (even numbers): " + list1);
        
        // Technique 2: removeIf() (Java 8+)
        List<Integer> list2 = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        list2.removeIf(num -> num % 3 == 0); // Remove multiples of 3
        System.out.println("After removeIf (multiples of 3): " + list2);
        
        // Technique 3: Collect to new list (Java 8+)
        List<Integer> list3 = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        List<Integer> filtered = list3.stream()
                                     .filter(num -> num % 2 != 0)
                                     .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        System.out.println("Stream filtering (odd numbers): " + filtered);
        
        // Technique 4: Reverse iteration for index-based removal
        List<String> list4 = new ArrayList<>(Arrays.asList("A", "B", "C", "D", "E"));
        for (int i = list4.size() - 1; i >= 0; i--) {
            if (list4.get(i).compareTo("C") >= 0) {
                list4.remove(i); // Safe because we're going backwards
            }
        }
        System.out.println("Reverse iteration removal: " + list4);
        
        // Technique 5: Two-pass approach
        List<String> list5 = new ArrayList<>(Arrays.asList("apple", "banana", "cherry", "date"));
        List<String> toRemove = new ArrayList<>();
        
        // First pass: identify elements to remove
        for (String item : list5) {
            if (item.length() > 5) {
                toRemove.add(item);
            }
        }
        
        // Second pass: remove identified elements
        list5.removeAll(toRemove);
        System.out.println("Two-pass removal (length > 5): " + list5);
    }
    
    /**
     * Custom iterator implementation
     */
    public static void demonstrateCustomIterator() {
        System.out.println("\n=== CUSTOM ITERATOR ===\n");
        
        // Custom collection with iterator
        NumberContainer container = new NumberContainer();
        container.add(1);
        container.add(2);
        container.add(3);
        container.add(4);
        container.add(5);
        
        System.out.println("Custom iterator (even numbers only):");
        for (Integer num : container) {
            System.out.println(num);
        }
        
        // Manual iterator usage
        System.out.println("\nManual iterator usage:");
        Iterator<Integer> customIt = container.iterator();
        while (customIt.hasNext()) {
            Integer num = customIt.next();
            System.out.println("Retrieved: " + num);
            if (num == 4) {
                customIt.remove();
                System.out.println("Removed: " + num);
            }
        }
        
        System.out.println("After removal:");
        for (Integer num : container) {
            System.out.println(num);
        }
    }
    
    /**
     * Compare different iteration methods
     */
    public static void compareIterationMethods() {
        System.out.println("\n=== ITERATION METHODS COMPARISON ===\n");
        
        List<String> data = new ArrayList<>();
        for (int i = 0; i < 100000; i++) {
            data.add("Item" + i);
        }
        
        // Method 1: Traditional for loop
        long startTime = System.nanoTime();
        for (int i = 0; i < data.size(); i++) {
            String item = data.get(i);
            // Process item
        }
        long endTime = System.nanoTime();
        System.out.println("Traditional for loop: " + (endTime - startTime) / 1_000_000 + " ms");
        
        // Method 2: Enhanced for loop
        startTime = System.nanoTime();
        for (String item : data) {
            // Process item
        }
        endTime = System.nanoTime();
        System.out.println("Enhanced for loop: " + (endTime - startTime) / 1_000_000 + " ms");
        
        // Method 3: Iterator
        startTime = System.nanoTime();
        Iterator<String> iterator = data.iterator();
        while (iterator.hasNext()) {
            String item = iterator.next();
            // Process item
        }
        endTime = System.nanoTime();
        System.out.println("Iterator: " + (endTime - startTime) / 1_000_000 + " ms");
        
        // Method 4: Stream forEach (Java 8+)
        startTime = System.nanoTime();
        data.forEach(item -> {
            // Process item
        });
        endTime = System.nanoTime();
        System.out.println("Stream forEach: " + (endTime - startTime) / 1_000_000 + " ms");
        
        System.out.println("\nCHOOSING ITERATION METHOD:");
        System.out.println("✓ Enhanced for-loop: General purpose, readable");
        System.out.println("✓ Iterator: When you need to remove elements");
        System.out.println("✓ Traditional for: When you need index access");
        System.out.println("✓ Stream forEach: Functional style, parallel processing");
    }
}

/**
 * Custom collection with custom iterator
 */
class NumberContainer implements Iterable<Integer> {
    private List<Integer> numbers = new ArrayList<>();
    
    public void add(Integer number) {
        numbers.add(number);
    }
    
    @Override
    public Iterator<Integer> iterator() {
        return new EvenNumberIterator();
    }
    
    /**
     * Custom iterator that only returns even numbers
     */
    private class EvenNumberIterator implements Iterator<Integer> {
        private int currentIndex = 0;
        private int lastReturnedIndex = -1;
        
        @Override
        public boolean hasNext() {
            // Find next even number
            while (currentIndex < numbers.size()) {
                if (numbers.get(currentIndex) % 2 == 0) {
                    return true;
                }
                currentIndex++;
            }
            return false;
        }
        
        @Override
        public Integer next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            
            lastReturnedIndex = currentIndex;
            Integer result = numbers.get(currentIndex);
            currentIndex++;
            return result;
        }
        
        @Override
        public void remove() {
            if (lastReturnedIndex == -1) {
                throw new IllegalStateException("next() must be called before remove()");
            }
            
            numbers.remove(lastReturnedIndex);
            currentIndex = lastReturnedIndex; // Adjust index after removal
            lastReturnedIndex = -1;
        }
    }
}
