package JAVA_DSA.DYNAMIC_PROGRAMMING.java.knapsack;

import java.util.*;

/**
 * TARGET SUM - Leetcode 494
 * Difficulty: Medium
 * Pattern: 0/1 Knapsack Variant
 * 
 * PROBLEM:
 * You are given an integer array nums and an integer target.
 * You want to build an expression out of nums by adding one of the symbols '+' and '-' 
 * before each integer in nums and then concatenate all the integers.
 * Return the number of different expressions that you can build, which evaluates to target.
 * 
 * EXAMPLES:
 * Input: nums = [1,1,1,1,1], target = 3
 * Output: 5
 * Explanation: There are 5 ways to assign symbols to make the sum of nums be target 3.
 * -1 + 1 + 1 + 1 + 1 = 3
 * +1 - 1 + 1 + 1 + 1 = 3
 * +1 + 1 - 1 + 1 + 1 = 3
 * +1 + 1 + 1 - 1 + 1 = 3
 * +1 + 1 + 1 + 1 - 1 = 3
 * 
 * KEY INSIGHT:
 * Let P = sum of positive numbers, N = sum of negative numbers
 * P + N = sum (total sum of array)
 * P - N = target (what we want)
 * Solving: P = (sum + target) / 2
 * Problem becomes: "Count subsets with sum = P"
 */
public class TargetSum_Leetcode494 {
    
    /**
     * APPROACH 1: Recursion with Memoization
     * Time: O(n * sum), Space: O(n * sum)
     */
    public int findTargetSumWays_Memoization(int[] nums, int target) {
        Map<String, Integer> memo = new HashMap<>();
        return dfs(nums, 0, 0, target, memo);
    }
    
    private int dfs(int[] nums, int index, int currentSum, int target, Map<String, Integer> memo) {
        // Base case
        if (index == nums.length) {
            return currentSum == target ? 1 : 0;
        }
        
        // Check memo
        String key = index + "," + currentSum;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        
        // Try both + and - for current number
        int add = dfs(nums, index + 1, currentSum + nums[index], target, memo);
        int subtract = dfs(nums, index + 1, currentSum - nums[index], target, memo);
        
        int result = add + subtract;
        memo.put(key, result);
        return result;
    }
    
    /**
     * APPROACH 2: Subset Sum DP (Optimized)
     * Time: O(n * sum), Space: O(sum)
     * 
     * Mathematical transformation:
     * P + N = sum, P - N = target
     * => P = (sum + target) / 2
     * Count subsets with sum = P
     */
    public int findTargetSumWays_DP(int[] nums, int target) {
        int sum = Arrays.stream(nums).sum();
        
        // Edge cases
        if (target > sum || target < -sum || (sum + target) % 2 == 1) {
            return 0;
        }
        
        int targetSum = (sum + target) / 2;
        return countSubsetsWithSum(nums, targetSum);
    }
    
    private int countSubsetsWithSum(int[] nums, int targetSum) {
        int[] dp = new int[targetSum + 1];
        dp[0] = 1; // One way to make sum 0: select nothing
        
        for (int num : nums) {
            // Traverse backwards to avoid using updated values
            for (int j = targetSum; j >= num; j--) {
                dp[j] += dp[j - num];
            }
        }
        
        return dp[targetSum];
    }
    
    /**
     * APPROACH 3: 2D DP (For better understanding)
     * Time: O(n * sum), Space: O(n * sum)
     */
    public int findTargetSumWays_2D(int[] nums, int target) {
        int sum = Arrays.stream(nums).sum();
        if (target > sum || target < -sum || (sum + target) % 2 == 1) {
            return 0;
        }
        
        int targetSum = (sum + target) / 2;
        int n = nums.length;
        
        // dp[i][j] = number of ways to get sum j using first i numbers
        int[][] dp = new int[n + 1][targetSum + 1];
        dp[0][0] = 1; // Base case: one way to get sum 0 with 0 numbers
        
        for (int i = 1; i <= n; i++) {
            for (int j = 0; j <= targetSum; j++) {
                // Don't include current number
                dp[i][j] = dp[i-1][j];
                
                // Include current number if possible
                if (j >= nums[i-1]) {
                    dp[i][j] += dp[i-1][j - nums[i-1]];
                }
            }
        }
        
        return dp[n][targetSum];
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        TargetSum_Leetcode494 solution = new TargetSum_Leetcode494();
        
        // Test case 1
        int[] nums1 = {1, 1, 1, 1, 1};
        int target1 = 3;
        System.out.println("=== Target Sum Problem ===");
        System.out.println("Array: " + Arrays.toString(nums1));
        System.out.println("Target: " + target1);
        System.out.println("Ways (Memoization): " + solution.findTargetSumWays_Memoization(nums1, target1));
        System.out.println("Ways (DP): " + solution.findTargetSumWays_DP(nums1, target1));
        System.out.println("Ways (2D DP): " + solution.findTargetSumWays_2D(nums1, target1));
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1};
        int target2 = 1;
        System.out.println("Array: " + Arrays.toString(nums2));
        System.out.println("Target: " + target2);
        System.out.println("Ways (DP): " + solution.findTargetSumWays_DP(nums2, target2));
        System.out.println();
        
        // Test case 3
        int[] nums3 = {1, 0};
        int target3 = 1;
        System.out.println("Array: " + Arrays.toString(nums3));
        System.out.println("Target: " + target3);
        System.out.println("Ways (DP): " + solution.findTargetSumWays_DP(nums3, target3));
    }
}