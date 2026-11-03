package dsa.fundamentals.collections;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Collection Framework Interview Problems and Solutions
 * 
 * COMMON INTERVIEW PROBLEMS COVERED:
 * 1. Find duplicates in collections
 * 2. Merge and sort multiple collections
 * 3. Implement LRU Cache
 * 4. Find intersection/union of collections
 * 5. Custom collection implementations
 * 6. Memory-efficient solutions
 * 7. Thread-safe collection problems
 */
public class CollectionInterviewProblems {
    
    public static void main(String[] args) {
        System.out.println("=== COLLECTION FRAMEWORK INTERVIEW PROBLEMS ===\n");
        
        problem1_FindDuplicates();
        problem2_MergeAndSort();
        problem3_LRUCache();
        problem4_SetOperations();
        problem5_FrequencyCount();
        problem6_CustomStack();
        problem7_GroupAnagrams();
        problem8_TopKElements();
        problem9_IteratorDesign();
        problem10_ConcurrentAccess();
    }
    
    /**
     * Problem 1: Find duplicates in a list
     * Multiple approaches with different time/space complexities
     */
    public static void problem1_FindDuplicates() {
        System.out.println("PROBLEM 1: Find Duplicates in List\n");
        
        List<Integer> numbers = Arrays.asList(1, 2, 3, 2, 4, 5, 3, 6, 1);
        System.out.println("Input: " + numbers);
        
        // Approach 1: Using HashSet (O(n) time, O(n) space)
        Set<Integer> seen = new HashSet<>();
        Set<Integer> duplicates = new HashSet<>();
        
        for (Integer num : numbers) {
            if (!seen.add(num)) {
                duplicates.add(num);
            }
        }
        System.out.println("Duplicates (HashSet): " + duplicates);
        
        // Approach 2: Using frequency map
        Map<Integer, Integer> frequency = new HashMap<>();
        numbers.forEach(num -> frequency.merge(num, 1, Integer::sum));
        
        Set<Integer> duplicatesFromMap = frequency.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        
        System.out.println("Duplicates (Frequency Map): " + duplicatesFromMap);
        
        // Approach 3: Using Collections.frequency (O(n²) time)
        Set<Integer> duplicatesFreq = numbers.stream()
                .filter(num -> Collections.frequency(numbers, num) > 1)
                .collect(Collectors.toSet());
        
        System.out.println("Duplicates (Collections.frequency): " + duplicatesFreq);
        
        // Approach 4: Find first duplicate
        Integer firstDuplicate = findFirstDuplicate(numbers);
        System.out.println("First duplicate: " + firstDuplicate);
        
        System.out.println();
    }
    
    private static Integer findFirstDuplicate(List<Integer> list) {
        Set<Integer> seen = new HashSet<>();
        for (Integer num : list) {
            if (!seen.add(num)) {
                return num;
            }
        }
        return null;
    }
    
    /**
     * Problem 2: Merge and sort multiple collections
     */
    public static void problem2_MergeAndSort() {
        System.out.println("PROBLEM 2: Merge and Sort Multiple Collections\n");
        
        List<Integer> list1 = Arrays.asList(3, 7, 1, 9);
        List<Integer> list2 = Arrays.asList(2, 8, 4);
        List<Integer> list3 = Arrays.asList(6, 5, 10);
        
        System.out.println("List 1: " + list1);
        System.out.println("List 2: " + list2);
        System.out.println("List 3: " + list3);
        
        // Approach 1: Simple merge and sort
        List<Integer> merged = new ArrayList<>();
        merged.addAll(list1);
        merged.addAll(list2);
        merged.addAll(list3);
        Collections.sort(merged);
        
        System.out.println("Merged and sorted: " + merged);
        
        // Approach 2: Using streams
        List<Integer> streamMerged = Stream.of(list1, list2, list3)
                .flatMap(Collection::stream)
                .sorted()
                .collect(Collectors.toList());
        
        System.out.println("Stream merged: " + streamMerged);
        
        // Approach 3: Merge pre-sorted lists efficiently
        List<Integer> sortedList1 = Arrays.asList(1, 4, 7);
        List<Integer> sortedList2 = Arrays.asList(2, 5, 8);
        List<Integer> sortedList3 = Arrays.asList(3, 6, 9);
        
        List<Integer> efficientMerged = mergeSortedLists(sortedList1, sortedList2, sortedList3);
        System.out.println("Efficiently merged sorted lists: " + efficientMerged);
        
        System.out.println();
    }
    
    private static List<Integer> mergeSortedLists(List<Integer>... lists) {
        PriorityQueue<ListIterator<Integer>> pq = new PriorityQueue<>(
            Comparator.comparing(it -> it.hasNext() ? it.next() : Integer.MAX_VALUE)
        );
        
        // Initialize priority queue with iterators
        for (List<Integer> list : lists) {
            ListIterator<Integer> it = list.listIterator();
            if (it.hasNext()) {
                pq.offer(it);
            }
        }
        
        List<Integer> result = new ArrayList<>();
        while (!pq.isEmpty()) {
            ListIterator<Integer> it = pq.poll();
            it.previous(); // Go back to get the actual value
            result.add(it.next());
            
            if (it.hasNext()) {
                pq.offer(it);
            }
        }
        
        return result;
    }
    
    /**
     * Problem 3: Implement LRU Cache
     */
    public static void problem3_LRUCache() {
        System.out.println("PROBLEM 3: Implement LRU Cache\n");
        
        LRUCache<String, Integer> cache = new LRUCache<>(3);
        
        cache.put("A", 1);
        cache.put("B", 2);
        cache.put("C", 3);
        System.out.println("After adding A, B, C: " + cache);
        
        cache.get("A"); // Access A
        System.out.println("After accessing A: " + cache);
        
        cache.put("D", 4); // Should evict B
        System.out.println("After adding D (should evict B): " + cache);
        
        cache.put("E", 5); // Should evict C
        System.out.println("After adding E (should evict C): " + cache);
        
        System.out.println("Get A: " + cache.get("A"));
        System.out.println("Get B: " + cache.get("B")); // Should be null
        
        System.out.println();
    }
    
    /**
     * Problem 4: Set operations (Union, Intersection, Difference)
     */
    public static void problem4_SetOperations() {
        System.out.println("PROBLEM 4: Set Operations\n");
        
        Set<String> set1 = new HashSet<>(Arrays.asList("A", "B", "C", "D"));
        Set<String> set2 = new HashSet<>(Arrays.asList("C", "D", "E", "F"));
        
        System.out.println("Set 1: " + set1);
        System.out.println("Set 2: " + set2);
        
        // Union
        Set<String> union = new HashSet<>(set1);
        union.addAll(set2);
        System.out.println("Union: " + union);
        
        // Intersection
        Set<String> intersection = new HashSet<>(set1);
        intersection.retainAll(set2);
        System.out.println("Intersection: " + intersection);
        
        // Difference (set1 - set2)
        Set<String> difference = new HashSet<>(set1);
        difference.removeAll(set2);
        System.out.println("Difference (set1 - set2): " + difference);
        
        // Symmetric difference
        Set<String> symmetricDiff = new HashSet<>(set1);
        Set<String> temp = new HashSet<>(set2);
        temp.removeAll(set1);
        symmetricDiff.removeAll(set2);
        symmetricDiff.addAll(temp);
        System.out.println("Symmetric difference: " + symmetricDiff);
        
        // Using streams for operations
        Set<String> streamUnion = Stream.concat(set1.stream(), set2.stream())
                .collect(Collectors.toSet());
        System.out.println("Union (stream): " + streamUnion);
        
        System.out.println();
    }
    
    /**
     * Problem 5: Count frequency of elements
     */
    public static void problem5_FrequencyCount() {
        System.out.println("PROBLEM 5: Count Element Frequency\n");
        
        List<String> words = Arrays.asList("apple", "banana", "apple", "cherry", "banana", "apple");
        System.out.println("Words: " + words);
        
        // Approach 1: Using HashMap
        Map<String, Integer> frequency = new HashMap<>();
        for (String word : words) {
            frequency.put(word, frequency.getOrDefault(word, 0) + 1);
        }
        System.out.println("Frequency (HashMap): " + frequency);
        
        // Approach 2: Using merge()
        Map<String, Integer> frequencyMerge = new HashMap<>();
        words.forEach(word -> frequencyMerge.merge(word, 1, Integer::sum));
        System.out.println("Frequency (merge): " + frequencyMerge);
        
        // Approach 3: Using streams
        Map<String, Long> frequencyStream = words.stream()
                .collect(Collectors.groupingBy(word -> word, Collectors.counting()));
        System.out.println("Frequency (stream): " + frequencyStream);
        
        // Find most frequent element
        String mostFrequent = frequency.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
        System.out.println("Most frequent: " + mostFrequent);
        
        // Find elements with frequency > 1
        List<String> duplicates = frequency.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        System.out.println("Elements with frequency > 1: " + duplicates);
        
        System.out.println();
    }
    
    /**
     * Problem 6: Implement a custom Stack with min() operation
     */
    public static void problem6_CustomStack() {
        System.out.println("PROBLEM 6: Stack with Min Operation\n");
        
        MinStack stack = new MinStack();
        
        stack.push(5);
        stack.push(2);
        stack.push(8);
        stack.push(1);
        
        System.out.println("Stack: " + stack);
        System.out.println("Min: " + stack.min());
        
        stack.pop();
        System.out.println("After pop: " + stack);
        System.out.println("Min: " + stack.min());
        
        stack.push(0);
        System.out.println("After pushing 0: " + stack);
        System.out.println("Min: " + stack.min());
        
        System.out.println();
    }
    
    /**
     * Problem 7: Group anagrams
     */
    public static void problem7_GroupAnagrams() {
        System.out.println("PROBLEM 7: Group Anagrams\n");
        
        List<String> words = Arrays.asList("eat", "tea", "tan", "ate", "nat", "bat");
        System.out.println("Words: " + words);
        
        // Group anagrams using sorted string as key
        Map<String, List<String>> anagramGroups = words.stream()
                .collect(Collectors.groupingBy(word -> {
                    char[] chars = word.toCharArray();
                    Arrays.sort(chars);
                    return new String(chars);
                }));
        
        System.out.println("Anagram groups:");
        anagramGroups.values().forEach(group -> System.out.println("  " + group));
        
        // Alternative approach using character frequency
        Map<Map<Character, Integer>, List<String>> freqGroups = words.stream()
                .collect(Collectors.groupingBy(CollectionInterviewProblems::getCharacterFrequency));
        
        System.out.println("Groups by character frequency:");
        freqGroups.values().forEach(group -> System.out.println("  " + group));
        
        System.out.println();
    }
    
    private static Map<Character, Integer> getCharacterFrequency(String word) {
        Map<Character, Integer> freq = new HashMap<>();
        for (char c : word.toCharArray()) {
            freq.merge(c, 1, Integer::sum);
        }
        return freq;
    }
    
    /**
     * Problem 8: Find top K frequent elements
     */
    public static void problem8_TopKElements() {
        System.out.println("PROBLEM 8: Top K Frequent Elements\n");
        
        List<Integer> numbers = Arrays.asList(1, 1, 1, 2, 2, 3, 4, 4, 4, 4);
        int k = 3;
        
        System.out.println("Numbers: " + numbers);
        System.out.println("Find top " + k + " frequent elements");
        
        // Count frequencies
        Map<Integer, Integer> frequency = new HashMap<>();
        numbers.forEach(num -> frequency.merge(num, 1, Integer::sum));
        
        // Use priority queue to find top k
        PriorityQueue<Map.Entry<Integer, Integer>> pq = new PriorityQueue<>(
            Comparator.comparing(Map.Entry<Integer, Integer>::getValue).reversed()
        );
        pq.addAll(frequency.entrySet());
        
        List<Integer> topK = new ArrayList<>();
        for (int i = 0; i < k && !pq.isEmpty(); i++) {
            topK.add(pq.poll().getKey());
        }
        
        System.out.println("Top " + k + " frequent: " + topK);
        
        // Alternative: Using stream with sorted
        List<Integer> topKStream = frequency.entrySet().stream()
                .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
        
        System.out.println("Top " + k + " frequent (stream): " + topKStream);
        
        System.out.println();
    }
    
    /**
     * Problem 9: Design a custom iterator
     */
    public static void problem9_IteratorDesign() {
        System.out.println("PROBLEM 9: Custom Iterator Design\n");
        
        // Iterator that flattens nested lists
        List<List<Integer>> nestedList = Arrays.asList(
            Arrays.asList(1, 2),
            Arrays.asList(3, 4, 5),
            Arrays.asList(6),
            Arrays.asList(7, 8, 9)
        );
        
        FlattenIterator<Integer> flatIterator = new FlattenIterator<>(nestedList);
        
        System.out.println("Nested list: " + nestedList);
        System.out.print("Flattened iteration: ");
        while (flatIterator.hasNext()) {
            System.out.print(flatIterator.next() + " ");
        }
        System.out.println();
        
        // Iterator that filters elements
        List<Integer> numbers = Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        FilterIterator<Integer> evenIterator = new FilterIterator<>(numbers, n -> n % 2 == 0);
        
        System.out.print("Even numbers only: ");
        while (evenIterator.hasNext()) {
            System.out.print(evenIterator.next() + " ");
        }
        System.out.println();
        
        System.out.println();
    }
    
    /**
     * Problem 10: Concurrent access patterns
     */
    public static void problem10_ConcurrentAccess() {
        System.out.println("PROBLEM 10: Concurrent Access Patterns\n");
        
        // Problem: Multiple threads updating a shared counter
        Map<String, Integer> concurrentMap = new java.util.concurrent.ConcurrentHashMap<>();
        List<Thread> threads = new ArrayList<>();
        
        // Create threads that increment counters
        for (int i = 0; i < 5; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                for (int j = 0; j < 1000; j++) {
                    String key = "counter" + (j % 3);
                    concurrentMap.compute(key, (k, v) -> (v == null) ? 1 : v + 1);
                }
                System.out.println("Thread " + threadId + " completed");
            });
            threads.add(thread);
            thread.start();
        }
        
        // Wait for all threads to complete
        threads.forEach(thread -> {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        System.out.println("Final counter values: " + concurrentMap);
        
        // Demonstrate thread-safe operations
        java.util.concurrent.ConcurrentHashMap<String, String> safeMap = 
            new java.util.concurrent.ConcurrentHashMap<>();
        
        safeMap.put("key1", "value1");
        safeMap.putIfAbsent("key1", "newValue"); // Won't replace
        safeMap.replace("key1", "value1", "replacedValue"); // Will replace
        
        System.out.println("Thread-safe operations result: " + safeMap);
        
        System.out.println();
    }
}

// Supporting classes for the problems

/**
 * LRU Cache implementation using LinkedHashMap
 */
class LRUCache<K, V> extends LinkedHashMap<K, V> {
    private final int capacity;
    
    public LRUCache(int capacity) {
        super(16, 0.75f, true); // access-order = true
        this.capacity = capacity;
    }
    
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}

/**
 * Stack with O(1) min() operation
 */
class MinStack {
    private Stack<Integer> stack = new Stack<>();
    private Stack<Integer> minStack = new Stack<>();
    
    public void push(int value) {
        stack.push(value);
        if (minStack.isEmpty() || value <= minStack.peek()) {
            minStack.push(value);
        }
    }
    
    public Integer pop() {
        if (stack.isEmpty()) return null;
        
        Integer popped = stack.pop();
        if (popped.equals(minStack.peek())) {
            minStack.pop();
        }
        return popped;
    }
    
    public Integer peek() {
        return stack.isEmpty() ? null : stack.peek();
    }
    
    public Integer min() {
        return minStack.isEmpty() ? null : minStack.peek();
    }
    
    public boolean isEmpty() {
        return stack.isEmpty();
    }
    
    @Override
    public String toString() {
        return stack.toString();
    }
}

/**
 * Iterator that flattens nested collections
 */
class FlattenIterator<T> implements Iterator<T> {
    private Iterator<? extends Iterable<T>> outerIterator;
    private Iterator<T> innerIterator;
    
    public FlattenIterator(Iterable<? extends Iterable<T>> nested) {
        this.outerIterator = nested.iterator();
        this.innerIterator = Collections.emptyIterator();
    }
    
    @Override
    public boolean hasNext() {
        while (!innerIterator.hasNext() && outerIterator.hasNext()) {
            innerIterator = outerIterator.next().iterator();
        }
        return innerIterator.hasNext();
    }
    
    @Override
    public T next() {
        if (!hasNext()) {
            throw new NoSuchElementException();
        }
        return innerIterator.next();
    }
}

/**
 * Iterator that filters elements based on predicate
 */
class FilterIterator<T> implements Iterator<T> {
    private Iterator<T> iterator;
    private java.util.function.Predicate<T> predicate;
    private T nextElement;
    private boolean hasNext;
    
    public FilterIterator(Iterable<T> iterable, java.util.function.Predicate<T> predicate) {
        this.iterator = iterable.iterator();
        this.predicate = predicate;
        findNext();
    }
    
    private void findNext() {
        hasNext = false;
        while (iterator.hasNext()) {
            T element = iterator.next();
            if (predicate.test(element)) {
                nextElement = element;
                hasNext = true;
                break;
            }
        }
    }
    
    @Override
    public boolean hasNext() {
        return hasNext;
    }
    
    @Override
    public T next() {
        if (!hasNext) {
            throw new NoSuchElementException();
        }
        T result = nextElement;
        findNext();
        return result;
    }
}
