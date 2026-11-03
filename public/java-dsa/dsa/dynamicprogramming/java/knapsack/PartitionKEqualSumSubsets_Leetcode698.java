package JAVA_DSA.DYNAMIC_PROGRAMMING.java.knapsack;

import java.util.*;

/**
 * PARTITION TO K EQUAL SUM SUBSETS - Leetcode 698
 * Difficulty: Medium
 * Pattern: Backtracking + DP with Bitmask
 * 
 * PROBLEM:
 * Given an integer array nums and an integer k, return true if it is possible 
 * to divide this array into k non-empty subsets whose sums are all equal.
 * 
 * EXAMPLES:
 * Input: nums = [4,3,2,3,5,2,1], k = 4
 * Output: true
 * Explanation: It's possible to divide into 4 subsets (5), (1,4), (2,3), (2,3) with equal sums.
 * 
 * Input: nums = [1,2,3,4], k = 3
 * Output: false
 * 
 * KEY INSIGHTS:
 * 1. Target sum for each subset = total_sum / k
 * 2. If total_sum % k != 0, return false
 * 3. Use backtracking with pruning optimizations
 * 4. Can also use bitmask DP for optimization
 */
public class PartitionKEqualSumSubsets_Leetcode698 {
    
    /**
     * APPROACH 1: Backtracking with Optimizations
     * Time: O(k * 2^n), Space: O(n)
     */
    public boolean canPartitionKSubsets_Backtrack(int[] nums, int k) {
        int sum = Arrays.stream(nums).sum();
        if (sum % k != 0) return false;
        
        int target = sum / k;
        // Sort in descending order for better pruning
        Arrays.sort(nums);
        reverse(nums);
        
        // Early pruning: if any number > target, impossible
        if (nums[0] > target) return false;
        
        boolean[] used = new boolean[nums.length];
        return backtrack(nums, used, 0, k, 0, target);
    }
    
    private boolean backtrack(int[] nums, boolean[] used, int start, 
                             int k, int currentSum, int target) {
        // If we've formed k-1 subsets, the last one is automatically valid
        if (k == 1) return true;
        
        // If current subset sum equals target, start new subset
        if (currentSum == target) {
            return backtrack(nums, used, 0, k - 1, 0, target);
        }
        
        for (int i = start; i < nums.length; i++) {
            if (used[i] || currentSum + nums[i] > target) continue;
            
            used[i] = true;
            if (backtrack(nums, used, i + 1, k, currentSum + nums[i], target)) {
                return true;
            }
            used[i] = false;
            
            // Pruning: if this number doesn't work and currentSum is 0,
            // no point trying other numbers of same value
            if (currentSum == 0) break;
        }
        
        return false;
    }
    
    /**
     * APPROACH 2: Bitmask DP
     * Time: O(n * 2^n), Space: O(2^n)
     * Better for smaller arrays with repeated calls
     */
    public boolean canPartitionKSubsets_Bitmask(int[] nums, int k) {
        int sum = Arrays.stream(nums).sum();
        if (sum % k != 0) return false;
        
        int target = sum / k;
        int n = nums.length;
        
        // dp[mask] = true if we can partition the subset represented by mask
        boolean[] dp = new boolean[1 << n];
        int[] subsetSum = new int[1 << n];
        
        dp[0] = true; // Empty set is always valid
        
        for (int mask = 0; mask < (1 << n); mask++) {
            if (!dp[mask]) continue;
            
            // Calculate sum of current subset
            int currentSum = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    currentSum += nums[i];
                }
            }
            subsetSum[mask] = currentSum % target;
            
            // Try adding each unused number
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) continue; // Already used
                
                int newMask = mask | (1 << i);
                if (dp[newMask]) continue; // Already computed
                
                if (subsetSum[mask] + nums[i] <= target) {
                    dp[newMask] = true;
                }
            }
        }
        
        return dp[(1 << n) - 1]; // All numbers used
    }
    
    /**
     * APPROACH 3: Optimized Backtracking (Bucket-filling)
     * Time: O(k^n), Space: O(k)
     * Fill k buckets simultaneously
     */
    public boolean canPartitionKSubsets_Buckets(int[] nums, int k) {
        int sum = Arrays.stream(nums).sum();
        if (sum % k != 0) return false;
        
        int target = sum / k;
        Arrays.sort(nums);
        reverse(nums);
        
        if (nums[0] > target) return false;
        
        int[] buckets = new int[k];
        return fillBuckets(nums, buckets, 0, target);
    }
    
    private boolean fillBuckets(int[] nums, int[] buckets, int index, int target) {
        if (index == nums.length) {
            // Check if all buckets have target sum
            for (int bucket : buckets) {
                if (bucket != target) return false;
            }
            return true;
        }
        
        int num = nums[index];
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] + num <= target) {
                buckets[i] += num;
                if (fillBuckets(nums, buckets, index + 1, target)) {
                    return true;
                }
                buckets[i] -= num;
                
                // Optimization: if bucket is empty, no point trying other empty buckets
                if (buckets[i] == 0) break;
            }
        }
        
        return false;
    }
    
    // Helper method to reverse array
    private void reverse(int[] nums) {
        int left = 0, right = nums.length - 1;
        while (left < right) {
            int temp = nums[left];
            nums[left] = nums[right];
            nums[right] = temp;
            left++;
            right--;
        }
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        PartitionKEqualSumSubsets_Leetcode698 solution = new PartitionKEqualSumSubsets_Leetcode698();
        
        System.out.println("=== Partition to K Equal Sum Subsets ===");
        
        // Test case 1
        int[] nums1 = {4, 3, 2, 3, 5, 2, 1};
        int k1 = 4;
        System.out.println("Array: " + Arrays.toString(nums1));
        System.out.println("K: " + k1);
        System.out.println("Can partition (Backtrack): " + solution.canPartitionKSubsets_Backtrack(nums1.clone(), k1));
        System.out.println("Can partition (Bitmask): " + solution.canPartitionKSubsets_Bitmask(nums1.clone(), k1));
        System.out.println("Can partition (Buckets): " + solution.canPartitionKSubsets_Buckets(nums1.clone(), k1));
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1, 2, 3, 4};
        int k2 = 3;
        System.out.println("Array: " + Arrays.toString(nums2));
        System.out.println("K: " + k2);
        System.out.println("Can partition (Backtrack): " + solution.canPartitionKSubsets_Backtrack(nums2.clone(), k2));
        System.out.println();
        
        // Test case 3
        int[] nums3 = {2, 2, 2, 2, 3, 3, 3, 3};
        int k3 = 4;
        System.out.println("Array: " + Arrays.toString(nums3));
        System.out.println("K: " + k3);
        System.out.println("Can partition (Buckets): " + solution.canPartitionKSubsets_Buckets(nums3.clone(), k3));
    }
}