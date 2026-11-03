package dsa.techniques.prefixsum.patterns;
import java.util.*;

/**
 * Common Prefix Sum Patterns for Technical Interviews
 * 
 * This file contains the most frequently asked patterns and templates
 * that appear in coding interviews at top tech companies.
 */
public class CommonPatterns {
    
    /**
     * PATTERN 1: Basic Range Sum Query
     * Use Case: Multiple range sum queries on static array
     * Template: Build prefix sum array, then answer queries in O(1)
     */
    public static class RangeSumTemplate {
        private int[] prefixSum;
        
        public RangeSumTemplate(int[] nums) {
            int n = nums.length;
            prefixSum = new int[n + 1];
            for (int i = 0; i < n; i++) {
                prefixSum[i + 1] = prefixSum[i] + nums[i];
            }
        }
        
        public int rangeSum(int left, int right) {
            return prefixSum[right + 1] - prefixSum[left];
        }
    }
    
    /**
     * PATTERN 2: Subarray Sum with HashMap
     * Use Case: Count subarrays with specific sum/property
     * Key Insight: prefixSum[j] - prefixSum[i] = target
     */
    public static int countSubarraysWithSum(int[] nums, int target) {
        Map<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1); // Empty prefix
        
        int prefixSum = 0, count = 0;
        for (int num : nums) {
            prefixSum += num;
            count += prefixSumCount.getOrDefault(prefixSum - target, 0);
            prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
        }
        return count;
    }
    
    /**
     * PATTERN 3: Modular Arithmetic with Prefix Sum
     * Use Case: Subarrays divisible by k, or with specific remainder
     */
    public static int countSubarraysDivisibleByK(int[] nums, int k) {
        Map<Integer, Integer> remainderCount = new HashMap<>();
        remainderCount.put(0, 1);
        
        int prefixSum = 0, count = 0;
        for (int num : nums) {
            prefixSum += num;
            int remainder = ((prefixSum % k) + k) % k; // Handle negative numbers
            count += remainderCount.getOrDefault(remainder, 0);
            remainderCount.put(remainder, remainderCount.getOrDefault(remainder, 0) + 1);
        }
        return count;
    }
    
    /**
     * PATTERN 4: 2D Prefix Sum Template
     * Use Case: Rectangle sum queries in matrix
     */
    public static class Matrix2DSum {
        private int[][] prefixSum;
        
        public Matrix2DSum(int[][] matrix) {
            int rows = matrix.length, cols = matrix[0].length;
            prefixSum = new int[rows + 1][cols + 1];
            
            for (int i = 1; i <= rows; i++) {
                for (int j = 1; j <= cols; j++) {
                    prefixSum[i][j] = matrix[i-1][j-1] + prefixSum[i-1][j] 
                                    + prefixSum[i][j-1] - prefixSum[i-1][j-1];
                }
            }
        }
        
        public int sumRegion(int row1, int col1, int row2, int col2) {
            row1++; col1++; row2++; col2++;
            return prefixSum[row2][col2] - prefixSum[row1-1][col2] 
                 - prefixSum[row2][col1-1] + prefixSum[row1-1][col1-1];
        }
    }
    
    /**
     * PATTERN 5: Difference Array for Range Updates
     * Use Case: Multiple range updates, then queries
     */
    public static class RangeUpdateTemplate {
        private int[] diff;
        private int n;
        
        public RangeUpdateTemplate(int[] nums) {
            n = nums.length;
            diff = new int[n + 1];
            diff[0] = nums[0];
            for (int i = 1; i < n; i++) {
                diff[i] = nums[i] - nums[i-1];
            }
        }
        
        public void rangeUpdate(int left, int right, int val) {
            diff[left] += val;
            if (right + 1 < n) diff[right + 1] -= val;
        }
        
        public int[] getArray() {
            int[] result = new int[n];
            result[0] = diff[0];
            for (int i = 1; i < n; i++) {
                result[i] = result[i-1] + diff[i];
            }
            return result;
        }
    }
    
    /**
     * PATTERN 6: Binary Search with Prefix Sum
     * Use Case: Find longest/shortest subarray with specific property
     */
    public static int shortestSubarrayWithSumAtLeastK(int[] nums, int k) {
        int n = nums.length;
        long[] prefixSum = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + nums[i];
        }
        
        Deque<Integer> deque = new ArrayDeque<>();
        int minLength = Integer.MAX_VALUE;
        
        for (int i = 0; i <= n; i++) {
            while (!deque.isEmpty() && prefixSum[i] - prefixSum[deque.peekFirst()] >= k) {
                minLength = Math.min(minLength, i - deque.pollFirst());
            }
            while (!deque.isEmpty() && prefixSum[i] <= prefixSum[deque.peekLast()]) {
                deque.pollLast();
            }
            deque.addLast(i);
        }
        
        return minLength == Integer.MAX_VALUE ? -1 : minLength;
    }
    
    /**
     * PATTERN 7: Prefix Sum with Sliding Window
     * Use Case: Maximum/minimum sum subarray of specific length
     */
    public static double findMaxAverage(int[] nums, int k) {
        int sum = 0;
        for (int i = 0; i < k; i++) {
            sum += nums[i];
        }
        
        int maxSum = sum;
        for (int i = k; i < nums.length; i++) {
            sum += nums[i] - nums[i - k];
            maxSum = Math.max(maxSum, sum);
        }
        
        return (double) maxSum / k;
    }
    
    /**
     * Common Edge Cases to Remember:
     * 1. Empty arrays or single elements
     * 2. All negative numbers
     * 3. Overflow with large sums (use long)
     * 4. Zero values in array
     * 5. Negative modulus results
     */
    
    public static void main(String[] args) {
        // Test Pattern 1: Range Sum
        int[] nums1 = {1, 2, 3, 4, 5};
        RangeSumTemplate rst = new RangeSumTemplate(nums1);
        System.out.println("Range sum [1,3]: " + rst.rangeSum(1, 3)); // Expected: 9
        
        // Test Pattern 2: Subarray Sum
        int[] nums2 = {1, 1, 1};
        System.out.println("Subarrays with sum 2: " + countSubarraysWithSum(nums2, 2)); // Expected: 2
        
        // Test Pattern 3: Divisible by K
        int[] nums3 = {4, 5, 0, -2, -3, 1};
        System.out.println("Subarrays divisible by 5: " + countSubarraysDivisibleByK(nums3, 5)); // Expected: 7
        
        // Test Pattern 6: Shortest subarray
        int[] nums6 = {1, 2, 3, 4};
        System.out.println("Shortest subarray with sum >= 6: " + shortestSubarrayWithSumAtLeastK(nums6, 6)); // Expected: 2
        
        // Test Pattern 7: Max average
        int[] nums7 = {1, 12, -5, -6, 50, 3};
        System.out.println("Max average of length 4: " + findMaxAverage(nums7, 4)); // Expected: 12.75
    }
}
