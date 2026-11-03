package dsa.algorithms.sorting.problems;
/**
 * MAXIMUM GAP
 * LeetCode #164 - Hard
 * 
 * Companies: Amazon, Microsoft, Bloomberg
 * Frequency: MEDIUM
 * 
 * Problem: Find maximum difference between successive elements in sorted form
 * Constraint: Must solve in O(n) time and O(n) space
 * 
 * Key Technique: BUCKET SORT + Pigeonhole Principle
 * Insight: Max gap must be >= ceiling((max-min)/(n-1))
 */

import java.util.*;

public class MaximumGap {
    
    // OPTIMAL: Bucket Sort
    // Time: O(n), Space: O(n)
    public int maximumGap(int[] nums) {
        if (nums == null || nums.length < 2) return 0;
        
        int n = nums.length;
        int minVal = Integer.MAX_VALUE;
        int maxVal = Integer.MIN_VALUE;
        
        // Find min and max
        for (int num : nums) {
            minVal = Math.min(minVal, num);
            maxVal = Math.max(maxVal, num);
        }
        
        // Edge case: all elements same
        if (minVal == maxVal) return 0;
        
        // Bucket size and count
        // Key insight: max gap >= ceiling((max-min)/(n-1))
        int bucketSize = Math.max(1, (maxVal - minVal) / (n - 1));
        int bucketCount = (maxVal - minVal) / bucketSize + 1;
        
        // Track min and max in each bucket
        int[] bucketMin = new int[bucketCount];
        int[] bucketMax = new int[bucketCount];
        Arrays.fill(bucketMin, Integer.MAX_VALUE);
        Arrays.fill(bucketMax, Integer.MIN_VALUE);
        
        // Place numbers in buckets
        for (int num : nums) {
            int idx = (num - minVal) / bucketSize;
            bucketMin[idx] = Math.min(bucketMin[idx], num);
            bucketMax[idx] = Math.max(bucketMax[idx], num);
        }
        
        // Find maximum gap between buckets
        int maxGap = 0;
        int prevMax = minVal;
        
        for (int i = 0; i < bucketCount; i++) {
            if (bucketMin[i] == Integer.MAX_VALUE) continue; // Empty bucket
            
            maxGap = Math.max(maxGap, bucketMin[i] - prevMax);
            prevMax = bucketMax[i];
        }
        
        return maxGap;
    }
    
    /**
     * APPROACH 2: Radix Sort
     * Time: O(n), Space: O(n)
     */
    public int maximumGap_RadixSort(int[] nums) {
        if (nums == null || nums.length < 2) return 0;
        
        radixSort(nums);
        
        int maxGap = 0;
        for (int i = 1; i < nums.length; i++) {
            maxGap = Math.max(maxGap, nums[i] - nums[i - 1]);
        }
        
        return maxGap;
    }
    
    private void radixSort(int[] nums) {
        int max = Arrays.stream(nums).max().getAsInt();
        
        for (int exp = 1; max / exp > 0; exp *= 10) {
            countingSort(nums, exp);
        }
    }
    
    private void countingSort(int[] nums, int exp) {
        int n = nums.length;
        int[] output = new int[n];
        int[] count = new int[10];
        
        for (int num : nums) {
            count[(num / exp) % 10]++;
        }
        
        for (int i = 1; i < 10; i++) {
            count[i] += count[i - 1];
        }
        
        for (int i = n - 1; i >= 0; i--) {
            int digit = (nums[i] / exp) % 10;
            output[count[digit] - 1] = nums[i];
            count[digit]--;
        }
        
        System.arraycopy(output, 0, nums, 0, n);
    }
    
    /**
     * FOLLOW-UP: Minimum Time Difference
     * LeetCode #539
     */
    public int findMinDifference(List<String> timePoints) {
        int n = timePoints.size();
        int[] minutes = new int[n];
        
        for (int i = 0; i < n; i++) {
            String[] parts = timePoints.get(i).split(":");
            minutes[i] = Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        }
        
        Arrays.sort(minutes);
        
        int minDiff = Integer.MAX_VALUE;
        for (int i = 1; i < n; i++) {
            minDiff = Math.min(minDiff, minutes[i] - minutes[i - 1]);
        }
        
        // Check circular difference (24:00 - first time + last time)
        minDiff = Math.min(minDiff, 24 * 60 - minutes[n - 1] + minutes[0]);
        
        return minDiff;
    }
    
    /**
     * FOLLOW-UP: H-Index II (Sorted array)
     * LeetCode #275
     */
    public int hIndex(int[] citations) {
        int n = citations.length;
        int left = 0, right = n - 1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            int h = n - mid;
            
            if (citations[mid] >= h) {
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        return n - left;
    }
    
    public static void main(String[] args) {
        MaximumGap sol = new MaximumGap();
        
        // Test 1: Basic case
        int[] nums1 = {3, 6, 9, 1};
        System.out.println("=== Test 1: Basic ===");
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Bucket Sort: " + sol.maximumGap(nums1));
        System.out.println("Radix Sort: " + sol.maximumGap_RadixSort(nums1));
        System.out.println("Expected: 3 (between 6 and 9)\n");
        
        // Test 2: Larger gap
        int[] nums2 = {1, 10000000};
        System.out.println("=== Test 2: Large Gap ===");
        System.out.println("Input: " + Arrays.toString(nums2));
        System.out.println("Result: " + sol.maximumGap(nums2));
        System.out.println("Expected: 9999999\n");
        
        // Test 3: All same
        int[] nums3 = {1, 1, 1, 1};
        System.out.println("=== Test 3: All Same ===");
        System.out.println("Input: " + Arrays.toString(nums3));
        System.out.println("Result: " + sol.maximumGap(nums3));
        System.out.println("Expected: 0\n");
        
        // Test 4: Already sorted
        int[] nums4 = {1, 3, 6, 10, 15};
        System.out.println("=== Test 4: Already Sorted ===");
        System.out.println("Input: " + Arrays.toString(nums4));
        System.out.println("Result: " + sol.maximumGap(nums4));
        System.out.println("Expected: 5 (between 10 and 15)\n");
        
        // Test 5: Min Time Difference
        System.out.println("=== Test 5: Min Time Difference ===");
        List<String> times = Arrays.asList("23:59", "00:00", "12:30");
        System.out.println("Times: " + times);
        System.out.println("Min Difference: " + sol.findMinDifference(times) + " minutes");
        System.out.println("Expected: 1 (23:59 to 00:00)\n");
        
        // Test 6: H-Index
        System.out.println("=== Test 6: H-Index ===");
        int[] citations = {0, 1, 3, 5, 6};
        System.out.println("Citations: " + Arrays.toString(citations));
        System.out.println("H-Index: " + sol.hIndex(citations));
        System.out.println("Explanation: 3 papers with >= 3 citations\n");
        
        // Explain Pigeonhole Principle
        System.out.println("=== PIGEONHOLE PRINCIPLE EXPLAINED ===");
        System.out.println("For array [1, 9]: range=8, n=2");
        System.out.println("Average gap = 8/(2-1) = 8");
        System.out.println("Max gap must be >= average gap");
        System.out.println("So max gap is NOT within buckets!");
        System.out.println("It's BETWEEN buckets\n");
        
        // Performance comparison
        System.out.println("=== PERFORMANCE TEST ===");
        int[] large = new int[100000];
        Random rand = new Random();
        for (int i = 0; i < large.length; i++) {
            large[i] = rand.nextInt(1000000);
        }
        
        long start = System.nanoTime();
        sol.maximumGap(large.clone());
        long bucketTime = System.nanoTime() - start;
        
        start = System.nanoTime();
        sol.maximumGap_RadixSort(large.clone());
        long radixTime = System.nanoTime() - start;
        
        System.out.println("Array size: 100000");
        System.out.println("Bucket Sort: " + bucketTime / 1_000_000.0 + " ms");
        System.out.println("Radix Sort: " + radixTime / 1_000_000.0 + " ms\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. Naive O(n log n) sorting doesn't meet O(n) requirement");
        System.out.println("2. Key insight: Use bucket/radix sort for O(n)");
        System.out.println("3. Pigeonhole: max gap >= ceiling((max-min)/(n-1))");
        System.out.println("4. Max gap cannot be WITHIN a bucket");
        System.out.println("5. Only need min/max of each bucket, not all elements");
        System.out.println("6. Amazon asks: 'How to sort in O(n)?'");
    }
}

