package dsa.techniques.hashing;

/*
 * Problem: Top K Frequent Elements
 * LeetCode: #347
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Apple, Bloomberg, Uber
 * 
 * Problem Statement:
 * Given an integer array nums and an integer k, return the k most frequent elements.
 * You may return the answer in any order.
 *
 * Example 1:
 * Input: nums = [1,1,1,2,2,3], k = 2
 * Output: [1,2]
 *
 * Example 2:
 * Input: nums = [1], k = 1
 * Output: [1]
 *
 * Constraints:
 * - 1 <= nums.length <= 10^5
 * - -10^4 <= nums[i] <= 10^4
 * - k is in the range [1, the number of unique elements in the array].
 * - It is guaranteed that the answer is unique.
 *
 * Time Complexity: O(n log k) for heap, O(n) for bucket sort
 * Space Complexity: O(n)
 *
 * Approach:
 * 1. Count frequency of each element using HashMap
 * 2. Use min-heap of size k to keep track of top k frequent elements
 * Alternative: Bucket sort for O(n) time complexity
 */

import java.util.*;

public class TopKFrequentElements_LC347 {

    // Solution 1: Using Min-Heap - O(n log k)
    public int[] topKFrequent(int[] nums, int k) {
        // Count frequencies
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }

        // Min-heap based on frequency
        PriorityQueue<Integer> minHeap = new PriorityQueue<>(
                (a, b) -> freqMap.get(a) - freqMap.get(b));

        // Keep only top k frequent elements
        for (int num : freqMap.keySet()) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }

        // Build result
        int[] result = new int[k];
        for (int i = k - 1; i >= 0; i--) {
            result[i] = minHeap.poll();
        }

        return result;
    }

    // Solution 2: Using Bucket Sort - O(n)
    public int[] topKFrequentBucketSort(int[] nums, int k) {
        // Count frequencies
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }

        // Create buckets
        List<Integer>[] buckets = new List[nums.length + 1];
        for (int i = 0; i < buckets.length; i++) {
            buckets[i] = new ArrayList<>();
        }

        // Fill buckets
        for (int num : freqMap.keySet()) {
            int freq = freqMap.get(num);
            buckets[freq].add(num);
        }

        // Collect top k elements
        int[] result = new int[k];
        int idx = 0;

        for (int i = buckets.length - 1; i >= 0 && idx < k; i--) {
            for (int num : buckets[i]) {
                result[idx++] = num;
                if (idx == k) {
                    return result;
                }
            }
        }

        return result;
    }

    // Solution 3: Using Max-Heap - simpler but O(n log n)
    public int[] topKFrequentMaxHeap(int[] nums, int k) {
        Map<Integer, Integer> freqMap = new HashMap<>();
        for (int num : nums) {
            freqMap.put(num, freqMap.getOrDefault(num, 0) + 1);
        }

        PriorityQueue<Integer> maxHeap = new PriorityQueue<>(
                (a, b) -> freqMap.get(b) - freqMap.get(a));

        maxHeap.addAll(freqMap.keySet());

        int[] result = new int[k];
        for (int i = 0; i < k; i++) {
            result[i] = maxHeap.poll();
        }

        return result;
    }

    public static void main(String[] args) {
        TopKFrequentElements_LC347 solution = new TopKFrequentElements_LC347();

        // Test Case 1
        int[] nums1 = { 1, 1, 1, 2, 2, 3 };
        int k1 = 2;
        System.out.println("Test 1: " + Arrays.toString(solution.topKFrequent(nums1, k1)));
        // Output: [1, 2]

        // Test Case 2
        int[] nums2 = { 1 };
        int k2 = 1;
        System.out.println("Test 2: " + Arrays.toString(solution.topKFrequent(nums2, k2)));
        // Output: [1]

        // Test Case 3
        int[] nums3 = { 4, 1, -1, 2, -1, 2, 3 };
        int k3 = 2;
        System.out.println("Test 3: " + Arrays.toString(solution.topKFrequent(nums3, k3)));
        // Output: [-1, 2]

        // Test bucket sort solution
        System.out.println("\nBucket Sort Solution:");
        System.out.println("Test 1: " + Arrays.toString(solution.topKFrequentBucketSort(nums1, k1)));
    }
}

