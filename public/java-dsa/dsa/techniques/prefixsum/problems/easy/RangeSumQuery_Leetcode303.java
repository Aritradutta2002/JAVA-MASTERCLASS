package dsa.techniques.prefixsum.problems.easy;

/**
 * LeetCode 303: Range Sum Query - Immutable
 * Difficulty: Easy
 * 
 * Problem: Given an integer array nums, handle multiple queries of the following type:
 * Calculate the sum of the elements of nums between indices left and right inclusive.
 * 
 * Example:
 * Input: nums = [-2, 0, 3, -5, 2, -1]
 * sumRange(0, 2) -> 1 (sum of [-2, 0, 3])
 * sumRange(2, 5) -> -1 (sum of [3, -5, 2, -1])
 * 
 * Approach: Use prefix sum array for O(1) range queries
 * Time: O(n) preprocessing, O(1) per query
 * Space: O(n)
 */
public class RangeSumQuery_Leetcode303 {
    
    static class NumArray {
        private int[] prefixSum;
        
        public NumArray(int[] nums) {
            int n = nums.length;
            prefixSum = new int[n + 1]; // Extra space to handle edge cases
            
            // Build prefix sum array
            for (int i = 0; i < n; i++) {
                prefixSum[i + 1] = prefixSum[i] + nums[i];
            }
        }
        
        public int sumRange(int left, int right) {
            // prefixSum[right + 1] contains sum from 0 to right
            // prefixSum[left] contains sum from 0 to left-1
            return prefixSum[right + 1] - prefixSum[left];
        }
    }
    
    // Alternative implementation without extra space
    static class NumArrayOptimized {
        private int[] prefixSum;
        
        public NumArrayOptimized(int[] nums) {
            int n = nums.length;
            prefixSum = new int[n];
            
            if (n > 0) {
                prefixSum[0] = nums[0];
                for (int i = 1; i < n; i++) {
                    prefixSum[i] = prefixSum[i - 1] + nums[i];
                }
            }
        }
        
        public int sumRange(int left, int right) {
            if (left == 0) {
                return prefixSum[right];
            }
            return prefixSum[right] - prefixSum[left - 1];
        }
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {-2, 0, 3, -5, 2, -1};
        NumArray numArray = new NumArray(nums1);
        
        System.out.println("Array: " + java.util.Arrays.toString(nums1));
        System.out.println("Sum range (0, 2): " + numArray.sumRange(0, 2)); // Expected: 1
        System.out.println("Sum range (2, 5): " + numArray.sumRange(2, 5)); // Expected: -1
        System.out.println("Sum range (0, 5): " + numArray.sumRange(0, 5)); // Expected: -3
        
        // Test case 2 - Single element
        int[] nums2 = {5};
        NumArray numArray2 = new NumArray(nums2);
        System.out.println("Single element sum: " + numArray2.sumRange(0, 0)); // Expected: 5
        
        // Test optimized version
        NumArrayOptimized optimized = new NumArrayOptimized(nums1);
        System.out.println("Optimized sum range (1, 3): " + optimized.sumRange(1, 3)); // Expected: -2
    }
}
