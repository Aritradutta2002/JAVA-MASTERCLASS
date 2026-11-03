package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 918: MAXIMUM SUM CIRCULAR SUBARRAY
 * Difficulty: Medium
 * Pattern: Kadane's Algorithm (Modified)
 * 
 * PROBLEM STATEMENT:
 * Given a circular integer array nums of length n, return the maximum possible sum 
 * of a non-empty subarray of nums.
 * 
 * A circular array means the end of the array connects to the beginning of the array.
 * Formally, the next element of nums[i] is nums[(i + 1) % n] and the previous 
 * element of nums[i] is nums[(i - 1 + n) % n].
 * 
 * A subarray may only include each element of the fixed buffer nums at most once.
 * 
 * EXAMPLE 1:
 * Input: nums = [1,-2,3,-2]
 * Output: 3
 * Explanation: Subarray [3] has maximum sum 3
 * 
 * EXAMPLE 2:
 * Input: nums = [5,-3,5]
 * Output: 10
 * Explanation: Subarray [5,5] (wrapping around) has maximum sum 5 + 5 = 10
 * 
 * EXAMPLE 3:
 * Input: nums = [-3,-2,-3]
 * Output: -2
 * Explanation: Subarray [-2] has maximum sum -2
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE TO UNDERSTANDING THIS PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHAT IS A CIRCULAR ARRAY?
 * -------------------------
 * Normal array:  [1, -2, 3, -2]
 *                 ↑           ↑
 *               start        end
 * 
 * Circular array: [1, -2, 3, -2] → wraps around to [1, -2, 3, -2, 1, -2, 3, -2...]
 *                  ↑_______________↑
 *                  end connects to start
 * 
 * Think of it like a circular track where you can start anywhere and 
 * potentially wrap around to the beginning.
 * 
 * WHAT MAKES THIS DIFFERENT FROM REGULAR MAX SUBARRAY?
 * ----------------------------------------------------
 * In regular max subarray: Only consider subarrays WITHIN the array
 * In circular max subarray: Also consider subarrays that WRAP AROUND
 * 
 * Example: [5, -3, 5]
 * Regular subarrays: [5], [-3], [5], [5,-3], [-3,5], [5,-3,5]
 * Circular subarrays: ALL of above + [5,5] (wrapping around)
 *                                      ↑___↑
 *                                      wraps around -3
 * 
 * WHY IS THIS CHALLENGING?
 * ------------------------
 * We need to consider TWO cases:
 * Case 1: Maximum subarray DOESN'T wrap around (regular Kadane's)
 * Case 2: Maximum subarray WRAPS around (this is the tricky part!)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * PREREQUISITE: KADANE'S ALGORITHM (MAXIMUM SUBARRAY)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Before solving circular, understand basic Kadane's Algorithm:
 * 
 * Problem: Find maximum sum of any contiguous subarray
 * 
 * Example: [−2, 1, −3, 4, −1, 2, 1, −5, 4]
 * Answer: [4, −1, 2, 1] has the largest sum = 6
 * 
 * Kadane's Intuition:
 * -------------------
 * At each position, decide:
 * - Should I extend the current subarray? (add current element)
 * - Should I start a new subarray? (current element alone)
 * 
 * Rule: current_max = max(nums[i], current_max + nums[i])
 * 
 * If adding current element makes sum worse than starting fresh,
 * then start fresh!
 * 
 * Dry Run:
 * --------
 * nums = [-2, 1, -3, 4, -1, 2, 1, -5, 4]
 * 
 * i=0: current = -2, max_so_far = -2
 * i=1: current = max(1, -2+1=-1) = 1, max_so_far = 1
 * i=2: current = max(-3, 1-3=-2) = -2, max_so_far = 1
 * i=3: current = max(4, -2+4=2) = 4, max_so_far = 4
 * i=4: current = max(-1, 4-1=3) = 3, max_so_far = 4
 * i=5: current = max(2, 3+2=5) = 5, max_so_far = 5
 * i=6: current = max(1, 5+1=6) = 6, max_so_far = 6
 * i=7: current = max(-5, 6-5=1) = 1, max_so_far = 6
 * i=8: current = max(4, 1+4=5) = 5, max_so_far = 6
 * 
 * Answer: 6
 */

import java.util.*;

// Helper class to demonstrate basic Kadane's Algorithm
class KadaneBasic {
    public int maxSubArray(int[] nums) {
        int currentMax = nums[0];  // Max sum ending at current position
        int globalMax = nums[0];   // Overall max sum found so far
        
        for (int i = 1; i < nums.length; i++) {
            // Either extend current subarray or start new one
            currentMax = Math.max(nums[i], currentMax + nums[i]);
            // Update global max if current is better
            globalMax = Math.max(globalMax, currentMax);
        }
        
        return globalMax;
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: BRUTE FORCE (CHECKING ALL SUBARRAYS)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * Check all possible subarrays in the circular array:
 * - For each starting position
 * - For each length (1 to n)
 * - Calculate sum using circular indexing
 * 
 * TIME COMPLEXITY: O(n²) - Too slow for large inputs
 * SPACE COMPLEXITY: O(1)
 * 
 * NOT RECOMMENDED - Just for understanding
 */

class MaxCircularSubarray_BruteForce {
    public int maxSubarraySumCircular(int[] nums) {
        int n = nums.length;
        int maxSum = nums[0];
        
        // Try all starting positions
        for (int start = 0; start < n; start++) {
            int currentSum = 0;
            // Try all lengths from current start
            for (int len = 1; len <= n; len++) {
                int index = (start + len - 1) % n;  // Circular index
                currentSum += nums[index];
                maxSum = Math.max(maxSum, currentSum);
            }
        }
        
        return maxSum;
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: KADANE'S ALGORITHM (OPTIMAL SOLUTION)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * KEY INSIGHT:
 * ------------
 * Maximum circular subarray can be in TWO forms:
 * 
 * Case 1: Doesn't wrap around
 * ===========================
 *    [5, -3, 5]
 *     ^      ^
 *     └──────┘ Maximum subarray [5] or [5,-3,5]
 * 
 * This is just regular Kadane's algorithm!
 * 
 * Case 2: Wraps around
 * ====================
 *    [5, -3, 5]
 *     ^       ^
 *     └───────┘ wraps around, [5, 5] = 10
 * 
 * Think differently: If max subarray wraps around, 
 * then the MIDDLE part is the MINIMUM subarray!
 * 
 * Visual:
 * -------
 * Array: [5, -3, 5]
 *         └──┘ This middle part is NOT included
 *         └──┘ This is MINIMUM subarray!
 * 
 * So: Max Circular Sum = Total Sum - Min Subarray Sum
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * nums = [5, -3, 5]
 * 
 * Step 1: Calculate max subarray (Case 1 - no wrap)
 *   Using Kadane's: max([5], [-3], [5], [5,-3], [-3,5], [5,-3,5])
 *   maxNoWrap = 7 (subarray [5,-3,5])
 * 
 * Step 2: Calculate min subarray
 *   Using modified Kadane's for minimum: min(all subarrays)
 *   minSubarray = -3 (subarray [-3])
 * 
 * Step 3: Calculate max with wrap (Case 2)
 *   totalSum = 5 + (-3) + 5 = 7
 *   maxWithWrap = totalSum - minSubarray = 7 - (-3) = 10
 *   This represents [5, 5] wrapping around!
 * 
 * Step 4: Take maximum of both cases
 *   answer = max(maxNoWrap, maxWithWrap) = max(7, 10) = 10
 * 
 * ANOTHER EXAMPLE:
 * ----------------
 * nums = [1, -2, 3, -2]
 * 
 * maxNoWrap = 3 (subarray [3])
 * minSubarray = -2 (subarray [-2])
 * totalSum = 0
 * maxWithWrap = 0 - (-2) = 2
 * answer = max(3, 2) = 3
 * 
 * EDGE CASE - ALL NEGATIVE:
 * -------------------------
 * nums = [-3, -2, -3]
 * 
 * maxNoWrap = -2 (best subarray)
 * minSubarray = -8 (entire array, sum of all)
 * totalSum = -8
 * maxWithWrap = -8 - (-8) = 0
 * 
 * Problem: maxWithWrap = 0 means empty subarray (invalid!)
 * Solution: If maxWithWrap = 0 (or totalSum = minSubarray), 
 *           return maxNoWrap only
 * 
 * TIME COMPLEXITY: O(n) - Three passes through array (Kadane's max, Kadane's min, total sum)
 * SPACE COMPLEXITY: O(1) - Only using a few variables
 */

public class MaximumSumCircularSubarray_Leetcode918 {
    
    public int maxSubarraySumCircular(int[] nums) {
        // Edge case: single element
        if (nums.length == 1) {
            return nums[0];
        }
        
        // Case 1: Maximum subarray without wrapping (standard Kadane's)
        int maxNoWrap = kadaneMax(nums);
        
        // Case 2: Maximum subarray with wrapping
        // Key insight: max with wrap = total sum - min subarray sum
        int totalSum = 0;
        for (int num : nums) {
            totalSum += num;
        }
        
        int minSubarray = kadaneMin(nums);
        int maxWithWrap = totalSum - minSubarray;
        
        // Edge case: All negative numbers
        // If maxWithWrap = 0, it means we'd have empty subarray (invalid)
        // In this case, return maxNoWrap
        if (maxWithWrap == 0) {
            return maxNoWrap;
        }
        
        // Return maximum of both cases
        return Math.max(maxNoWrap, maxWithWrap);
    }
    
    // Standard Kadane's Algorithm - Find Maximum Subarray Sum
    private int kadaneMax(int[] nums) {
        int currentMax = nums[0];
        int globalMax = nums[0];
        
        for (int i = 1; i < nums.length; i++) {
            currentMax = Math.max(nums[i], currentMax + nums[i]);
            globalMax = Math.max(globalMax, currentMax);
        }
        
        return globalMax;
    }
    
    // Modified Kadane's Algorithm - Find Minimum Subarray Sum
    private int kadaneMin(int[] nums) {
        int currentMin = nums[0];
        int globalMin = nums[0];
        
        for (int i = 1; i < nums.length; i++) {
            currentMin = Math.min(nums[i], currentMin + nums[i]);
            globalMin = Math.min(globalMin, currentMin);
        }
        
        return globalMin;
    }
    
    // Test the solution
    public static void main(String[] args) {
        MaximumSumCircularSubarray_Leetcode918 solution = new MaximumSumCircularSubarray_Leetcode918();
        
        // Test Case 1: Answer doesn't wrap
        int[] nums1 = {1, -2, 3, -2};
        System.out.println("Test Case 1:");
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Output: " + solution.maxSubarraySumCircular(nums1));
        System.out.println("Explanation: Subarray [3] has maximum sum 3");
        System.out.println();
        
        // Test Case 2: Answer wraps around
        int[] nums2 = {5, -3, 5};
        System.out.println("Test Case 2:");
        System.out.println("Input: " + Arrays.toString(nums2));
        System.out.println("Output: " + solution.maxSubarraySumCircular(nums2));
        System.out.println("Explanation: Subarray [5, 5] (wrapping) has maximum sum 10");
        System.out.println();
        
        // Test Case 3: All negative
        int[] nums3 = {-3, -2, -3};
        System.out.println("Test Case 3:");
        System.out.println("Input: " + Arrays.toString(nums3));
        System.out.println("Output: " + solution.maxSubarraySumCircular(nums3));
        System.out.println("Explanation: Subarray [-2] has maximum sum -2");
        System.out.println();
        
        // Test Case 4: Complex wrapping
        int[] nums4 = {8, -1, 3, 4};
        System.out.println("Test Case 4:");
        System.out.println("Input: " + Arrays.toString(nums4));
        System.out.println("Output: " + solution.maxSubarraySumCircular(nums4));
        System.out.println("Explanation: Subarray [8, -1, 3, 4] or wrapping gives same result");
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR BEGINNERS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. MASTER BASIC KADANE'S FIRST:
 *    - Understand regular maximum subarray problem (Leetcode 53)
 *    - Practice Kadane's algorithm until it's second nature
 *    - Then tackle circular variant
 * 
 * 2. TWO CASES APPROACH:
 *    - Always consider: wrapped vs non-wrapped
 *    - Max circular = max(no wrap case, wrap case)
 *    - No wrap: standard Kadane's
 *    - Wrap: total - min subarray
 * 
 * 3. WHY "TOTAL - MIN" WORKS:
 *    Visualize the array as circular:
 *    
 *    [a, b, c, d, e]
 *     └──────────┘
 *     Wrapping subarray [a, e] = [a] + [e]
 *    
 *    If we take [a] and [e], we're NOT taking [b, c, d]
 *    So: [a] + [e] = Total - ([b] + [c] + [d])
 *    To maximize [a] + [e], minimize [b] + [c] + [d]
 * 
 * 4. EDGE CASE - ALL NEGATIVE:
 *    - If all numbers negative, min subarray = entire array
 *    - total - min = 0 (empty subarray - invalid!)
 *    - Always check if maxWithWrap = 0
 *    - Return maxNoWrap in this case
 * 
 * 5. COMMON MISTAKES:
 *    - Forgetting the all-negative edge case
 *    - Using wrong comparison in Kadane's (max vs min)
 *    - Not understanding why "total - min" works
 *    - Trying to track actual subarray indices (not needed)
 * 
 * 6. INTERVIEW STRATEGY:
 *    Step 1: Explain basic Kadane's algorithm
 *    Step 2: Explain two cases (wrap vs no-wrap)
 *    Step 3: Derive the "total - min" insight
 *    Step 4: Code both Kadane variants
 *    Step 5: Handle edge case (all negative)
 *    Step 6: Return max of both cases
 * 
 * 7. COMPLEXITY ANALYSIS:
 *    - Time: O(n) - Three linear passes
 *    - Space: O(1) - Only few variables
 *    - Optimal solution!
 * 
 * 8. RELATED PROBLEMS:
 *    - Maximum Subarray (Leetcode 53) - Learn Kadane's
 *    - Maximum Product Subarray (Leetcode 152)
 *    - Maximum Sum of Rectangle No Larger Than K (Leetcode 363)
 *    - Longest Turbulent Subarray (Leetcode 978)
 * 
 * 9. VARIATIONS TO PRACTICE:
 *    - Minimum circular subarray
 *    - Circular array with at most K operations
 *    - Maximum average circular subarray
 * 
 * 10. VISUALIZATION TIP:
 *     Draw the array as a circle!
 *     
 *          1
 *       /    \
 *     -2      -2
 *       \    /
 *          3
 *     
 *     Makes it easier to see wrapping subarrays
 */

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPLEXITY COMPARISON:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * | Approach         | Time Complexity | Space Complexity | Recommended? |
 * |------------------|-----------------|------------------|--------------|
 * | Brute Force      | O(n²)           | O(1)            | No           |
 * | Kadane's (Smart) | O(n)            | O(1)            | **Yes!**     |
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */


