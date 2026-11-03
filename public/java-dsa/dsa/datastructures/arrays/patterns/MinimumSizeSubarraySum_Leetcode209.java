package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 209: MINIMUM SIZE SUBARRAY SUM
 * Difficulty: Medium
 * Pattern: Sliding Window
 * 
 * PROBLEM STATEMENT:
 * Given an array of positive integers nums and a positive integer target, 
 * return the minimal length of a subarray whose sum is greater than or equal 
 * to target. If there is no such subarray, return 0 instead.
 * 
 * EXAMPLE 1:
 * Input: target = 7, nums = [2,3,1,2,4,3]
 * Output: 2
 * Explanation: The subarray [4,3] has the minimal length under the problem constraint.
 * 
 * EXAMPLE 2:
 * Input: target = 4, nums = [1,4,4]
 * Output: 1
 * 
 * EXAMPLE 3:
 * Input: target = 11, nums = [1,1,1,1,1,1,1,1]
 * Output: 0
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: BRUTE FORCE
 * ═══════════════════════════════════════════════════════════════════════════════
 * TIME: O(n²), SPACE: O(1)
 */

import java.util.*;

class MinSubarraySum_BruteForce {
    public int minSubArrayLen(int target, int[] nums) {
        int n = nums.length;
        int minLen = Integer.MAX_VALUE;
        
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int j = i; j < n; j++) {
                sum += nums[j];
                if (sum >= target) {
                    minLen = Math.min(minLen, j - i + 1);
                    break;
                }
            }
        }
        
        return minLen == Integer.MAX_VALUE ? 0 : minLen;
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: SLIDING WINDOW (OPTIMAL)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * Use two pointers (left, right) to form a window:
 * - Expand window (right++) until sum >= target
 * - Contract window (left++) while maintaining sum >= target
 * - Track minimum length
 * 
 * EXAMPLE: target=7, nums=[2,3,1,2,4,3]
 * 
 * Step 1: [2] sum=2 < 7, expand
 * Step 2: [2,3] sum=5 < 7, expand
 * Step 3: [2,3,1] sum=6 < 7, expand
 * Step 4: [2,3,1,2] sum=8 >= 7 ✓ minLen=4
 *         Contract: [3,1,2] sum=6 < 7, stop
 * Step 5: [3,1,2,4] sum=10 >= 7 ✓ minLen=4
 *         Contract: [1,2,4] sum=7 >= 7 ✓ minLen=3
 *         Contract: [2,4] sum=6 < 7, stop
 * Step 6: [2,4,3] sum=9 >= 7 ✓
 *         Contract: [4,3] sum=7 >= 7 ✓ minLen=2
 *         Contract: [3] sum=3 < 7, stop
 * 
 * Answer: 2
 * 
 * TIME: O(n) - Each element visited at most twice
 * SPACE: O(1)
 */

public class MinimumSizeSubarraySum_Leetcode209 {
    
    public int minSubArrayLen(int target, int[] nums) {
        int n = nums.length;
        int left = 0;
        int sum = 0;
        int minLen = Integer.MAX_VALUE;
        
        for (int right = 0; right < n; right++) {
            // Expand window
            sum += nums[right];
            
            // Contract window while sum >= target
            while (sum >= target) {
                minLen = Math.min(minLen, right - left + 1);
                sum -= nums[left];
                left++;
            }
        }
        
        return minLen == Integer.MAX_VALUE ? 0 : minLen;
    }
    
    public static void main(String[] args) {
        MinimumSizeSubarraySum_Leetcode209 solution = new MinimumSizeSubarraySum_Leetcode209();
        
        int[] nums1 = {2,3,1,2,4,3};
        System.out.println("Input: target=7, nums=" + Arrays.toString(nums1));
        System.out.println("Output: " + solution.minSubArrayLen(7, nums1));
        
        int[] nums2 = {1,4,4};
        System.out.println("\nInput: target=4, nums=" + Arrays.toString(nums2));
        System.out.println("Output: " + solution.minSubArrayLen(4, nums2));
    }
}

/*
 * KEY TAKEAWAYS:
 * 1. Sliding window perfect for contiguous subarray problems
 * 2. Expand to meet condition, contract to optimize
 * 3. O(n) time because each element visited at most twice
 * 4. Works only with positive numbers (monotonic sum)
 */


