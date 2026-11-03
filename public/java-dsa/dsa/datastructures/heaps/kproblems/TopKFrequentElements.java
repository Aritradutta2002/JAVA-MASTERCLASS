package dsa.datastructures.heaps.kproblems;
/**
 * TOP K FREQUENT ELEMENTS
 * 
 * LeetCode #347 - Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Bloomberg
 * 
 * Problem: Given an array, return k most frequent elements.
 * 
 * Solutions:
 * 1. Min Heap - O(n log k) time, O(n) space
 * 2. Bucket Sort - O(n) time, O(n) space
 * 3. Quick Select - O(n) average time
 */

import java.util.*;

public class TopKFrequentElements {
    
    // APPROACH 1: Min Heap (Most Common Interview Solution)
    public int[] topKFrequent_Heap(int[] nums, int k) {
        // Step 1: Count frequencies
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }
        
        // Step 2: Min heap of size k based on frequency
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(
            (a, b) -> freqMap.get(a) - freqMap.get(b)
        );
        
        for (int num : freqMap.keySet()) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        // Step 3: Build result
        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = minHeap.poll();
        }
        
        return result;
    }
    
    // APPROACH 2: Bucket Sort (Optimal O(n))
    public int[] topKFrequent_Bucket(int[] nums, int k) {
        // Step 1: Count frequencies
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }
        
        // Step 2: Bucket sort - index is frequency
        List<Integer>[] bucket = new List[nums.length + 1];
        for (int num : freqMap.keySet()) {
            int freq = freqMap.get(num);
            if (bucket[freq] == null) {
                bucket[freq] = new ArrayList<>();
            }
            bucket[freq].add(num);
        }
        
        // Step 3: Collect top k from highest frequency
        int[] result = new int[k];
        int idx = 0;
        
        for (int i = bucket.length - 1; i >= 0 && idx < k; i--) {
            if (bucket[i] != null) {
                for (int num : bucket[i]) {
                    result[idx++] = num;
                    if (idx == k) break;
                }
            }
        }
        
        return result;
    }
    
    // APPROACH 3: Max Heap (Alternative)
    public int[] topKFrequent_MaxHeap(int[] nums, int k) {
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }
        
        // Max heap based on frequency
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(
            (a, b) -> freqMap.get(b) - freqMap.get(a)
        );
        
        maxHeap.addAll(freqMap.keySet());
        
        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = maxHeap.poll();
        }
        
        return result;
    }
    
    // FOLLOW-UP: Top K Frequent Words (String version)
    public List<String> topKFrequentWords(String[] words, int k) {
        Map<String, Integer> freqMap = new HashMap<>();
        for (String word : words) {
            freqMap.put(word, freqMap.getOrDefault(word, 0) + 1);
        }
        
        // Min heap: first by frequency, then lexicographically
        PriorityQueue<String> minHeap = new PriorityQueue<>((a, b) -> {
            int freqCompare = freqMap.get(a) - freqMap.get(b);
            if (freqCompare != 0) return freqCompare;
            return b.compareTo(a); // Reverse lexicographical for min heap
        });
        
        for (String word : freqMap.keySet()) {
            minHeap.offer(word);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        List<String> result = new ArrayList<>();
        while (!minHeap.isEmpty()) {
            result.add(0, minHeap.poll()); // Add to front for correct order
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        TopKFrequentElements solution = new TopKFrequentElements();
        
        // Test Case 1
        int[] nums1 = {1, 1, 1, 2, 2, 3};
        int k1 = 2;
        System.out.println("Array: [1,1,1,2,2,3], k=2");
        System.out.println("Top K Frequent (Heap): " + Arrays.toString(solution.topKFrequent_Heap(nums1, k1)));
        System.out.println("Top K Frequent (Bucket): " + Arrays.toString(solution.topKFrequent_Bucket(nums1, k1)));
        
        // Test Case 2
        int[] nums2 = {4, 1, -1, 2, -1, 2, 3};
        int k2 = 2;
        System.out.println("\nArray: [4,1,-1,2,-1,2,3], k=2");
        System.out.println("Top K Frequent: " + Arrays.toString(solution.topKFrequent_Heap(nums2, k2)));
        
        // Test Case 3: Top K Frequent Words
        String[] words = {"i", "love", "leetcode", "i", "love", "coding"};
        int k3 = 2;
        System.out.println("\nWords: [i, love, leetcode, i, love, coding], k=2");
        System.out.println("Top K Frequent Words: " + solution.topKFrequentWords(words, k3));
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Heap approach: O(n log k) - Best for small k");
        System.out.println("2. Bucket sort: O(n) - Best when k is large");
        System.out.println("3. Always clarify: order matters? lexicographical?");
        System.out.println("4. Min heap of size k is memory efficient");
        System.out.println("5. Practice both integer and string versions");
    }
}

