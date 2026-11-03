package dsa.fundamentals.collections;

import java.util.*;

/**
 * Complete LinkedList Demonstration for Interview Preparation
 * 
 * INTERVIEW TOPICS COVERED:
 * 1. LinkedList internal structure (doubly linked list)
 * 2. LinkedList vs ArrayList performance comparison
 * 3. Deque interface implementation
 * 4. Stack and Queue operations
 * 5. When to use LinkedList vs ArrayList
 * 6. Memory overhead considerations
 */
public class LinkedListDemo {
    
    public static void main(String[] args) {
        demonstrateBasicOperations();
        explainInternalStructure();
        demonstrateDequeOperations();
        compareWithArrayList();
        analyzePerformance();
        demonstrateStackQueueOperations();
        showBestPractices();
    }
    
    /**
     * Basic LinkedList operations
     */
    public static void demonstrateBasicOperations() {
        System.out.println("=== LINKEDLIST BASIC OPERATIONS ===\n");
        
        LinkedList<String> linkedList = new LinkedList<>();
        
        // Adding elements
        linkedList.add("First");
        linkedList.add("Second");
        linkedList.add("Third");
        linkedList.addFirst("New First");    // Add at beginning
        linkedList.addLast("New Last");      // Add at end
        linkedList.add(2, "Inserted");      // Add at specific index
        
        System.out.println("After additions: " + linkedList);
        
        // Accessing elements
        System.out.println("First element: " + linkedList.getFirst());
        System.out.println("Last element: " + linkedList.getLast());
        System.out.println("Element at index 2: " + linkedList.get(2));
        
        // Peeking (without removal)
        System.out.println("Peek first: " + linkedList.peekFirst());
        System.out.println("Peek last: " + linkedList.peekLast());
        
        // Removing elements
        String removedFirst = linkedList.removeFirst();
        String removedLast = linkedList.removeLast();
        String removedAtIndex = linkedList.remove(1);
        
        System.out.println("Removed first: " + removedFirst);
        System.out.println("Removed last: " + removedLast);
        System.out.println("Removed at index 1: " + removedAtIndex);
        System.out.println("After removals: " + linkedList);
        
        // Searching
        System.out.println("Contains 'Second': " + linkedList.contains("Second"));
        System.out.println("Index of 'Third': " + linkedList.indexOf("Third"));
        System.out.println("Last index of 'Third': " + linkedList.lastIndexOf("Third"));
    }
    
    /**
     * Internal structure explanation
     */
    public static void explainInternalStructure() {
        System.out.println("\n=== LINKEDLIST INTERNAL STRUCTURE ===\n");
        
        System.out.println("INTERNAL IMPLEMENTATION:");
        System.out.println("1. LinkedList is implemented as a doubly linked list");
        System.out.println("2. Each node contains: data, next pointer, previous pointer");
        System.out.println("3. Maintains references to first and last nodes");
        System.out.println("4. No capacity limit (grows dynamically)");
        System.out.println("5. Non-contiguous memory allocation");
        
        System.out.println("\nNODE STRUCTURE:");
        System.out.println("┌─────────────┐");
        System.out.println("│    Node     │");
        System.out.println("├─────────────┤");
        System.out.println("│ prev: Node* │");
        System.out.println("│ item: E     │");
        System.out.println("│ next: Node* │");
        System.out.println("└─────────────┘");
        
        System.out.println("\nLINKED LIST VISUALIZATION:");
        System.out.println("first ──┐");
        System.out.println("        ▼");
        System.out.println("┌─────────────┐    ┌─────────────┐    ┌─────────────┐");
        System.out.println("│ null│A│next │◄──►│ prev│B│next │◄──►│ prev│C│null │");
        System.out.println("└─────────────┘    └─────────────┘    └─────────────┘");
        System.out.println("                                              ▲");
        System.out.println("                                           last ──┘");
        
        System.out.println("\nKEY CHARACTERISTICS:");
        System.out.println("✓ Implements List, Deque, Queue interfaces");
        System.out.println("✓ Allows null values and duplicates");
        System.out.println("✓ Not synchronized (not thread-safe)");
        System.out.println("✓ Maintains insertion order");
        System.out.println("✓ Supports bidirectional iteration");
        
        // Demonstrate memory overhead
        demonstrateMemoryOverhead();
    }
    
    /**
     * Deque interface operations
     */
    public static void demonstrateDequeOperations() {
        System.out.println("\n=== DEQUE OPERATIONS ===\n");
        
        Deque<Integer> deque = new LinkedList<>();
        
        System.out.println("DEQUE (Double Ended Queue) operations:");
        System.out.println("Can add/remove from both ends efficiently");
        
        // Adding to both ends
        deque.addFirst(2);
        deque.addFirst(1);
        deque.addLast(3);
        deque.addLast(4);
        
        System.out.println("After adding to both ends: " + deque);
        
        // Offering (similar to add, but returns boolean)
        deque.offerFirst(0);
        deque.offerLast(5);
        
        System.out.println("After offering: " + deque);
        
        // Peeking (no removal)
        System.out.println("Peek first: " + deque.peekFirst());
        System.out.println("Peek last: " + deque.peekLast());
        
        // Polling (with removal)
        System.out.println("Poll first: " + deque.pollFirst());
        System.out.println("Poll last: " + deque.pollLast());
        System.out.println("After polling: " + deque);
        
        // Stack operations (LIFO)
        System.out.println("\nUsing as Stack (LIFO):");
        deque.push(10);  // Add to front
        deque.push(20);  // Add to front
        System.out.println("After pushes: " + deque);
        System.out.println("Pop: " + deque.pop()); // Remove from front
        System.out.println("After pop: " + deque);
        
        // Queue operations (FIFO)
        System.out.println("\nUsing as Queue (FIFO):");
        deque.offer(30); // Add to rear
        deque.offer(40); // Add to rear
        System.out.println("After offers: " + deque);
        System.out.println("Poll: " + deque.poll()); // Remove from front
        System.out.println("After poll: " + deque);
    }
    
    /**
     * Performance comparison with ArrayList
     */
    public static void compareWithArrayList() {
        System.out.println("\n=== LINKEDLIST vs ARRAYLIST COMPARISON ===\n");
        
        System.out.println("OPERATION COMPLEXITY COMPARISON:");
        System.out.println("┌─────────────────────┬─────────────┬─────────────┐");
        System.out.println("│ Operation           │ ArrayList   │ LinkedList  │");
        System.out.println("├─────────────────────┼─────────────┼─────────────┤");
        System.out.println("│ get(index)          │ O(1)        │ O(n)        │");
        System.out.println("│ set(index, element) │ O(1)        │ O(n)        │");
        System.out.println("│ add(element)        │ O(1)*       │ O(1)        │");
        System.out.println("│ add(0, element)     │ O(n)        │ O(1)        │");
        System.out.println("│ add(index, element) │ O(n)        │ O(n)        │");
        System.out.println("│ remove(0)           │ O(n)        │ O(1)        │");
        System.out.println("│ remove(index)       │ O(n)        │ O(n)        │");
        System.out.println("│ Iterator.remove()   │ O(n)        │ O(1)        │");
        System.out.println("└─────────────────────┴─────────────┴─────────────┘");
        System.out.println("* Amortized O(1), worst case O(n) due to resizing");
        
        System.out.println("\nMEMORY USAGE:");
        System.out.println("ArrayList: Contiguous memory, better cache locality");
        System.out.println("LinkedList: Non-contiguous, higher memory overhead per element");
        
        System.out.println("\nWHEN TO USE WHICH:");
        System.out.println("Use ArrayList when:");
        System.out.println("✓ Frequent random access (get/set by index)");
        System.out.println("✓ Iteration is primary operation");
        System.out.println("✓ Memory efficiency is important");
        System.out.println("✓ Cache performance matters");
        
        System.out.println("\nUse LinkedList when:");
        System.out.println("✓ Frequent insertions/deletions at beginning/end");
        System.out.println("✓ Implementing stack/queue operations");
        System.out.println("✓ Unknown or highly variable size");
        System.out.println("✓ No random access needed");
    }
    
    /**
     * Performance analysis with actual measurements
     */
    public static void analyzePerformance() {
        System.out.println("\n=== PERFORMANCE ANALYSIS ===\n");
        
        final int SIZE = 100000;
        List<Integer> arrayList = new ArrayList<>();
        List<Integer> linkedList = new LinkedList<>();
        
        // Test 1: Adding at the end
        long startTime = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(i);
        }
        long arrayListAddTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (int i = 0; i < SIZE; i++) {
            linkedList.add(i);
        }
        long linkedListAddTime = System.nanoTime() - startTime;
        
        System.out.println("Adding " + SIZE + " elements at end:");
        System.out.println("ArrayList: " + arrayListAddTime / 1_000_000 + " ms");
        System.out.println("LinkedList: " + linkedListAddTime / 1_000_000 + " ms");
        
        // Test 2: Adding at the beginning
        arrayList.clear();
        linkedList.clear();
        
        final int SMALL_SIZE = 10000; // Smaller size for beginning insertion
        
        startTime = System.nanoTime();
        for (int i = 0; i < SMALL_SIZE; i++) {
            arrayList.add(0, i);
        }
        long arrayListInsertTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (int i = 0; i < SMALL_SIZE; i++) {
            linkedList.add(0, i);
        }
        long linkedListInsertTime = System.nanoTime() - startTime;
        
        System.out.println("\nAdding " + SMALL_SIZE + " elements at beginning:");
        System.out.println("ArrayList: " + arrayListInsertTime / 1_000_000 + " ms");
        System.out.println("LinkedList: " + linkedListInsertTime / 1_000_000 + " ms");
        
        // Test 3: Random access
        // Repopulate lists
        arrayList.clear();
        linkedList.clear();
        for (int i = 0; i < SIZE; i++) {
            arrayList.add(i);
            linkedList.add(i);
        }
        
        Random random = new Random();
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            int index = random.nextInt(SIZE);
            arrayList.get(index);
        }
        long arrayListAccessTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (int i = 0; i < 10000; i++) {
            int index = random.nextInt(SIZE);
            linkedList.get(index);
        }
        long linkedListAccessTime = System.nanoTime() - startTime;
        
        System.out.println("\n10,000 random access operations:");
        System.out.println("ArrayList: " + arrayListAccessTime / 1_000_000 + " ms");
        System.out.println("LinkedList: " + linkedListAccessTime / 1_000_000 + " ms");
        
        // Test 4: Iteration
        startTime = System.nanoTime();
        for (Integer value : arrayList) {
            // Just iterate
        }
        long arrayListIterTime = System.nanoTime() - startTime;
        
        startTime = System.nanoTime();
        for (Integer value : linkedList) {
            // Just iterate
        }
        long linkedListIterTime = System.nanoTime() - startTime;
        
        System.out.println("\nIterating through " + SIZE + " elements:");
        System.out.println("ArrayList: " + arrayListIterTime / 1_000_000 + " ms");
        System.out.println("LinkedList: " + linkedListIterTime / 1_000_000 + " ms");
    }
    
    /**
     * Memory overhead demonstration
     */
    private static void demonstrateMemoryOverhead() {
        System.out.println("\nMEMORY OVERHEAD ANALYSIS:");
        System.out.println("ArrayList:");
        System.out.println("- Stores elements in Object[] array");
        System.out.println("- Memory overhead: array metadata + unused capacity");
        System.out.println("- Per element: just the reference (8 bytes on 64-bit)");
        
        System.out.println("\nLinkedList:");
        System.out.println("- Each element wrapped in Node object");
        System.out.println("- Node contains: element reference + 2 pointers + object header");
        System.out.println("- Per element: ~40 bytes (vs ~8 bytes for ArrayList)");
        System.out.println("- 5x more memory per element!");
    }
    
    /**
     * Stack and Queue operations
     */
    public static void demonstrateStackQueueOperations() {
        System.out.println("\n=== STACK AND QUEUE OPERATIONS ===\n");
        
        // Using LinkedList as Stack
        System.out.println("LINKEDLIST AS STACK (LIFO):");
        Deque<String> stack = new LinkedList<>();
        
        // Push operations
        stack.push("First");
        stack.push("Second");
        stack.push("Third");
        System.out.println("Stack after pushes: " + stack);
        
        // Pop operations
        while (!stack.isEmpty()) {
            System.out.println("Popped: " + stack.pop());
        }
        
        // Using LinkedList as Queue
        System.out.println("\nLINKEDLIST AS QUEUE (FIFO):");
        Queue<String> queue = new LinkedList<>();
        
        // Enqueue operations
        queue.offer("First");
        queue.offer("Second");
        queue.offer("Third");
        System.out.println("Queue after offers: " + queue);
        
        // Dequeue operations
        while (!queue.isEmpty()) {
            System.out.println("Polled: " + queue.poll());
        }
        
        // Practical example: Browser history
        System.out.println("\nPRACTICAL EXAMPLE - Browser History:");
        BrowserHistory history = new BrowserHistory();
        history.visit("google.com");
        history.visit("stackoverflow.com");
        history.visit("github.com");
        
        System.out.println("Current page: " + history.getCurrentPage());
        System.out.println("Go back: " + history.back());
        System.out.println("Go back: " + history.back());
        System.out.println("Go forward: " + history.forward());
    }
    
    /**
     * Best practices and recommendations
     */
    public static void showBestPractices() {
        System.out.println("\n=== BEST PRACTICES ===\n");
        
        System.out.println("GENERAL GUIDELINES:");
        System.out.println("✓ Prefer ArrayList for most use cases");
        System.out.println("✓ Use LinkedList only when you need frequent insertions/deletions");
        System.out.println("✓ Consider ArrayDeque instead of LinkedList for stack/queue operations");
        System.out.println("✓ Be aware of memory overhead with LinkedList");
        
        System.out.println("\nWHEN LINKEDLIST IS APPROPRIATE:");
        System.out.println("1. Implementing LRU cache");
        System.out.println("2. Undo/Redo functionality");
        System.out.println("3. Browser history");
        System.out.println("4. Music playlist with frequent additions/removals");
        
        System.out.println("\nPERFORMANCE TIPS:");
        System.out.println("✓ Use enhanced for-loop or iterator for traversal");
        System.out.println("✓ Avoid get(index) in loops - use iterator instead");
        System.out.println("✓ Use ListIterator for bidirectional traversal");
        System.out.println("✗ Don't use LinkedList for frequent random access");
        
        // Good vs Bad patterns
        System.out.println("\nGOOD vs BAD PATTERNS:");
        
        LinkedList<Integer> list = new LinkedList<>();
        for (int i = 0; i < 10; i++) {
            list.add(i);
        }
        
        System.out.println("❌ BAD - Using index-based loop:");
        System.out.println("for (int i = 0; i < list.size(); i++) {");
        System.out.println("    process(list.get(i)); // O(n²) overall!");
        System.out.println("}");
        
        System.out.println("\n✅ GOOD - Using enhanced for-loop:");
        System.out.println("for (Integer item : list) {");
        System.out.println("    process(item); // O(n) overall");
        System.out.println("}");
        
        System.out.println("\n✅ ALSO GOOD - Using iterator:");
        System.out.println("Iterator<Integer> it = list.iterator();");
        System.out.println("while (it.hasNext()) {");
        System.out.println("    process(it.next());");
        System.out.println("}");
    }
}

/**
 * Practical example: Browser history implementation
 */
class BrowserHistory {
    private LinkedList<String> history = new LinkedList<>();
    private int currentIndex = -1;
    
    public void visit(String url) {
        // Remove forward history when visiting new page
        while (history.size() > currentIndex + 1) {
            history.removeLast();
        }
        
        history.add(url);
        currentIndex++;
        System.out.println("Visited: " + url);
    }
    
    public String back() {
        if (currentIndex > 0) {
            currentIndex--;
            return history.get(currentIndex);
        }
        return getCurrentPage();
    }
    
    public String forward() {
        if (currentIndex < history.size() - 1) {
            currentIndex++;
            return history.get(currentIndex);
        }
        return getCurrentPage();
    }
    
    public String getCurrentPage() {
        if (currentIndex >= 0 && currentIndex < history.size()) {
            return history.get(currentIndex);
        }
        return "No page";
    }
}
