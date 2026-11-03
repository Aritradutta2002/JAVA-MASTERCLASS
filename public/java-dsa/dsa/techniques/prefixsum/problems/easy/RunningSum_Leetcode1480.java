package dsa.techniques.prefixsum.problems.easy;

/**
 * LeetCode 1480: Running Sum of 1d Array
 * Difficulty: Easy
 * 
 * Problem: Given an array nums, return the running sum of nums.
 * Running sum is defined as runningSum[i] = sum(nums[0]…nums[i]).
 * 
 * Example:
 * Input: nums = [1,2,3,4]
 * Output: [1,3,6,10]
 * Explanation: Running sum is [1, 1+2, 1+2+3, 1+2+3+4] = [1,3,6,10]
 * 
 * Time: O(n)
 * Space: O(1) if modifying input, O(n) for new array
 */
public class RunningSum_Leetcode1480 {
    
    /**
     * Approach 1: Create new array for running sum
     */
    public static int[] runningSum(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        
        result[0] = nums[0];
        for (int i = 1; i < n; i++) {
            result[i] = result[i - 1] + nums[i];
        }
        
        return result;
    }
    
    /**
     * Approach 2: In-place modification (Space optimized)
     */
    public static int[] runningSumInPlace(int[] nums) {
        for (int i = 1; i < nums.length; i++) {
            nums[i] += nums[i - 1];
        }
        return nums;
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, 2, 3, 4};
        int[] result1 = runningSum(nums1.clone());
        System.out.println("Input: " + java.util.Arrays.toString(nums1));
        System.out.println("Running Sum: " + java.util.Arrays.toString(result1));
        
        // Test case 2
        int[] nums2 = {1, 1, 1, 1, 1};
        int[] result2 = runningSum(nums2.clone());
        System.out.println("Input: " + java.util.Arrays.toString(nums2));
        System.out.println("Running Sum: " + java.util.Arrays.toString(result2));
        
        // Test case 3 - Single element
        int[] nums3 = {5};
        int[] result3 = runningSum(nums3.clone());
        System.out.println("Input: " + java.util.Arrays.toString(nums3));
        System.out.println("Running Sum: " + java.util.Arrays.toString(result3));
        
        // Test in-place version
        int[] nums4 = {3, 1, 2, 10, 1};
        System.out.println("Original: " + java.util.Arrays.toString(nums4.clone()));
        runningSumInPlace(nums4);
        System.out.println("In-place Running Sum: " + java.util.Arrays.toString(nums4));
    }
}
