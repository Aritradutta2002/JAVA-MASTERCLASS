package dsa.algorithms.sorting.problems;
/**
 * TOP K FREQUENT ELEMENTS
 * LeetCode #347 - Medium
 * 
 * Companies: Amazon, Facebook, Microsoft, Uber, Bloomberg
 * Frequency: VERY HIGH
 * 
 * Key Techniques:
 * 1. Bucket Sort - O(n) OPTIMAL!
 * 2. Min Heap - O(n log k)
 * 3. Quick Select - O(n) average
 */

import java.util.*;

public class TopKFrequentElements {
    
    // APPROACH 1: Bucket Sort (OPTIMAL!)
    // Time: O(n), Space: O(n)
    public int[] topKFrequent(int[] nums, int k) {
        // Count frequencies
        Map<Integer, Integer> count = new HashMap<>();
        for (int num : nums) {
            count.put(num, count.getOrDefault(num, 0) + 1);
        }
        
        // Bucket sort: index = frequency
        List<Integer>[] bucket = new List[nums.length + 1];
        for (int key : count.keySet()) {
            int freq = count.get(key);
            if (bucket[freq] == null) {
                bucket[freq] = new ArrayList<>();
            }
            bucket[freq].add(key);
        }
        
        // Collect k most frequent
        int[] result = new int[k];
        int index = 0;
        
        for (int i = bucket.length - 1; i >= 0 && index < k; i--) {
            if (bucket[i] != null) {
                for (int num : bucket[i]) {
                    result[index++] = num;
                    if (index == k) return result;
                }
            }
        }
        
        return result;
    }
    
    // APPROACH 2: Min Heap
    // Time: O(n log k), Space: O(n)
    public int[] topKFrequent_Heap(int[] nums, int k) {
        // Count frequencies
        Map<Integer, Integer> count = new HashMap<>();
        for (int num : nums) {
            count.put(num, count.getOrDefault(num, 0) + 1);
        }
        
        // Min heap: keep k most frequent
        PriorityQueue<int[]> minHeap = new PriorityQueue<>((a, b) -> a[1] - b[1]);
        
        for (Map.Entry<Integer, Integer> entry : count.entrySet()) {
            minHeap.offer(new int[]{entry.getKey(), entry.getValue()});
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        // Extract result
        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = minHeap.poll()[0];
        }
        
        return result;
    }
    
    // APPROACH 3: Quick Select (Alternative)
    // Time: O(n) average, Space: O(n)
    public int[] topKFrequent_QuickSelect(int[] nums, int k) {
        Map<Integer, Integer> count = new HashMap<>();
        for (int num : nums) {
            count.put(num, count.getOrDefault(num, 0) + 1);
        }
        
        int n = count.size();
        int[] unique = new int[n];
        int i = 0;
        for (int num : count.keySet()) {
            unique[i++] = num;
        }
        
        quickSelect(unique, 0, n - 1, n - k, count);
        
        return Arrays.copyOfRange(unique, n - k, n);
    }
    
    private void quickSelect(int[] nums, int left, int right, int kSmallest, Map<Integer, Integer> count) {
        if (left == right) return;
        
        int pivotIndex = partition(nums, left, right, count);
        
        if (kSmallest == pivotIndex) {
            return;
        } else if (kSmallest < pivotIndex) {
            quickSelect(nums, left, pivotIndex - 1, kSmallest, count);
        } else {
            quickSelect(nums, pivotIndex + 1, right, kSmallest, count);
        }
    }
    
    private int partition(int[] nums, int left, int right, Map<Integer, Integer> count) {
        int pivotFreq = count.get(nums[right]);
        int storeIndex = left;
        
        for (int i = left; i < right; i++) {
            if (count.get(nums[i]) < pivotFreq) {
                swap(nums, storeIndex++, i);
            }
        }
        
        swap(nums, right, storeIndex);
        return storeIndex;
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    /**
     * FOLLOW-UP 1: Top K Frequent Words
     * LeetCode #692
     */
    public List<String> topKFrequentWords(String[] words, int k) {
        Map<String, Integer> count = new HashMap<>();
        for (String word : words) {
            count.put(word, count.getOrDefault(word, 0) + 1);
        }
        
        // Max heap: sort by frequency DESC, then lexicographically ASC
        PriorityQueue<String> maxHeap = new PriorityQueue<>((a, b) -> {
            int freqCompare = count.get(b) - count.get(a);
            return freqCompare != 0 ? freqCompare : a.compareTo(b);
        });
        
        maxHeap.addAll(count.keySet());
        
        List<String> result = new ArrayList<>();
        for (int i = 0; i < k; i++) {
            result.add(maxHeap.poll());
        }
        
        return result;
    }
    
    /**
     * FOLLOW-UP 2: K Closest Points to Origin
     * LeetCode #973
     */
    public int[][] kClosest(int[][] points, int k) {
        // Max heap: keep k closest
        PriorityQueue<int[]> maxHeap = new PriorityQueue<>((a, b) -> 
            (b[0] * b[0] + b[1] * b[1]) - (a[0] * a[0] + a[1] * a[1])
        );
        
        for (int[] point : points) {
            maxHeap.offer(point);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        
        int[][] result = new int[k][2];
        for (int i = 0; i < k; i++) {
            result[i] = maxHeap.poll();
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        TopKFrequentElements sol = new TopKFrequentElements();
        
        // Test 1: Basic case
        int[] nums1 = {1, 1, 1, 2, 2, 3};
        int k1 = 2;
        System.out.println("=== Test 1: Top K Frequent ===");
        System.out.println("Array: " + Arrays.toString(nums1) + ", k=" + k1);
        System.out.println("Bucket Sort: " + Arrays.toString(sol.topKFrequent(nums1, k1)));
        System.out.println("Min Heap: " + Arrays.toString(sol.topKFrequent_Heap(nums1, k1)));
        System.out.println("Quick Select: " + Arrays.toString(sol.topKFrequent_QuickSelect(nums1, k1)));
        
        // Test 2: Single element
        int[] nums2 = {1};
        System.out.println("\n=== Test 2: Single Element ===");
        System.out.println("Array: " + Arrays.toString(nums2) + ", k=1");
        System.out.println("Result: " + Arrays.toString(sol.topKFrequent(nums2, 1)));
        
        // Test 3: All same frequency
        int[] nums3 = {1, 2, 3, 4, 5};
        System.out.println("\n=== Test 3: All Same Frequency ===");
        System.out.println("Array: " + Arrays.toString(nums3) + ", k=3");
        System.out.println("Result: " + Arrays.toString(sol.topKFrequent(nums3, 3)));
        
        // Test 4: Top K Frequent Words
        String[] words = {"i", "love", "leetcode", "i", "love", "coding"};
        System.out.println("\n=== Test 4: Top K Frequent Words ===");
        System.out.println("Words: " + Arrays.toString(words) + ", k=2");
        System.out.println("Result: " + sol.topKFrequentWords(words, 2));
        
        // Test 5: K Closest Points
        int[][] points = {{1, 3}, {-2, 2}, {5, 8}, {0, 1}};
        System.out.println("\n=== Test 5: K Closest Points ===");
        System.out.println("k=2 closest points:");
        int[][] closest = sol.kClosest(points, 2);
        for (int[] point : closest) {
            System.out.println(Arrays.toString(point));
        }
        
        // Performance comparison
        System.out.println("\n=== PERFORMANCE ANALYSIS ===");
        int[] large = new int[100000];
        Random rand = new Random();
        for (int i = 0; i < large.length; i++) {
            large[i] = rand.nextInt(1000);
        }
        
        long start = System.nanoTime();
        sol.topKFrequent(large.clone(), 10);
        long bucketTime = System.nanoTime() - start;
        
        start = System.nanoTime();
        sol.topKFrequent_Heap(large.clone(), 10);
        long heapTime = System.nanoTime() - start;
        
        System.out.println("Array size: 100000");
        System.out.println("Bucket Sort: " + bucketTime / 1_000_000.0 + " ms");
        System.out.println("Heap: " + heapTime / 1_000_000.0 + " ms");
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Bucket Sort is OPTIMAL: O(n) time!");
        System.out.println("2. Heap is easier to implement: O(n log k)");
        System.out.println("3. Amazon asks: 'Can you do better than O(n log n)?'");
        System.out.println("4. Pattern: Top K → Think Heap or Bucket Sort");
        System.out.println("5. Follow-ups: Words (lexicographic), Points (distance)");
    }
}

