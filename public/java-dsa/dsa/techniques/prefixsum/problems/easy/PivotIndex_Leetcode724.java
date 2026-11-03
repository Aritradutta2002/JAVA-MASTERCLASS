package dsa.techniques.prefixsum.problems.easy;

/**
 * LeetCode 724: Find Pivot Index
 * Difficulty: Easy
 * 
 * Problem: Given an array of integers nums, calculate the pivot index of this array.
 * The pivot index is the index where the sum of all numbers strictly to the left
 * equals the sum of all numbers strictly to the right.
 * 
 * Example:
 * Input: nums = [1,7,3,6,5,6]
 * Output: 3
 * Explanation: Left sum = 1+7+3 = 11, Right sum = 5+6 = 11
 * 
 * Time: O(n)
 * Space: O(1)
 */
public class PivotIndex_Leetcode724 {
    
    /**
     * Approach 1: Using total sum and left sum
     * Key insight: rightSum = totalSum - leftSum - nums[i]
     */
    public static int pivotIndex(int[] nums) {
        int totalSum = 0;
        for (int num : nums) {
            totalSum += num;
        }
        
        int leftSum = 0;
        for (int i = 0; i < nums.length; i++) {
            int rightSum = totalSum - leftSum - nums[i];
            
            if (leftSum == rightSum) {
                return i;
            }
            
            leftSum += nums[i];
        }
        
        return -1; // No pivot index found
    }
    
    /**
     * Approach 2: Using prefix sum array (alternative approach)
     */
    public static int pivotIndexWithPrefixSum(int[] nums) {
        int n = nums.length;
        int[] prefixSum = new int[n + 1];
        
        // Build prefix sum
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + nums[i];
        }
        
        for (int i = 0; i < n; i++) {
            int leftSum = prefixSum[i];
            int rightSum = prefixSum[n] - prefixSum[i + 1];
            
            if (leftSum == rightSum) {
                return i;
            }
        }
        
        return -1;
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, 7, 3, 6, 5, 6};
        System.out.println("Array: " + java.util.Arrays.toString(nums1));
        System.out.println("Pivot Index: " + pivotIndex(nums1)); // Expected: 3
        
        // Test case 2
        int[] nums2 = {1, 2, 3};
        System.out.println("Array: " + java.util.Arrays.toString(nums2));
        System.out.println("Pivot Index: " + pivotIndex(nums2)); // Expected: -1
        
        // Test case 3
        int[] nums3 = {2, 1, -1};
        System.out.println("Array: " + java.util.Arrays.toString(nums3));
        System.out.println("Pivot Index: " + pivotIndex(nums3)); // Expected: 0
        
        // Test case 4 - Single element
        int[] nums4 = {5};
        System.out.println("Array: " + java.util.Arrays.toString(nums4));
        System.out.println("Pivot Index: " + pivotIndex(nums4)); // Expected: 0
        
        // Test with prefix sum approach
        System.out.println("Using prefix sum approach: " + pivotIndexWithPrefixSum(nums1));
    }
}
