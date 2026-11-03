package JAVA_DSA.DYNAMIC_PROGRAMMING.java.knapsack;

import java.util.*;

/**
 * PARTITION EQUAL SUBSET SUM - Leetcode 416
 * Difficulty: Medium
 * Companies: Amazon, Google, Facebook, Microsoft, Bloomberg
 * 
 * PROBLEM:
 * Given a non-empty array nums containing only positive integers, find if the array
 * can be partitioned into two subsets such that the sum of elements in both subsets is equal.
 * 
 * EXAMPLES:
 * Input: nums = [1,5,11,5]
 * Output: true
 * Explanation: [1,5,5] and [11] both sum to 11
 * 
 * Input: nums = [1,2,3,5]
 * Output: false
 * Explanation: Cannot partition into two equal sum subsets
 * 
 * PATTERN: 0/1 Knapsack
 * KEY INSIGHT: If total sum is odd, impossible. Otherwise, find if subset with sum = total/2 exists.
 */

public class PartitionEqualSubsetSum_Leetcode416 {
    
    // ========== PROBLEM REDUCTION ==========
    /**
     * BEGINNER'S EXPLANATION:
     * 
     * This problem can be reduced to: "Can we find a subset with sum = total/2?"
     * 
     * Why?
     * - If array can be split into two equal subsets
     * - Each subset must have sum = total/2
     * - So we just need to find ONE subset with sum = total/2
     * - The remaining elements automatically form the other subset!
     * 
     * Example: [1,5,11,5]
     * Total = 22, Target = 11
     * Find subset with sum 11: {1,5,5} ✓
     * Remaining: {11} also sums to 11 ✓
     * 
     * This is the SUBSET SUM PROBLEM - a classic 0/1 Knapsack variant!
     */
    
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(2^n) - Each element has 2 choices
    // Space: O(n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * For each element, we have 2 choices:
     * 1. INCLUDE it in the subset
     * 2. EXCLUDE it from the subset
     * 
     * Base cases:
     * - If target becomes 0, we found a valid subset!
     * - If no elements left or target negative, impossible
     */
    public boolean canPartition_Recursive(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        // If odd sum, impossible to partition equally
        if (total % 2 != 0) return false;
        
        int target = total / 2;
        return canMakeSum(nums, 0, target);
    }
    
    private boolean canMakeSum(int[] nums, int index, int target) {
        // Base cases
        if (target == 0) return true;  // Found valid subset!
        if (index >= nums.length || target < 0) return false;
        
        // Choice 1: Include current element
        if (canMakeSum(nums, index + 1, target - nums[index])) {
            return true;
        }
        
        // Choice 2: Exclude current element
        return canMakeSum(nums, index + 1, target);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(n * sum) - Each state calculated once
    // Space: O(n * sum) - Memo array + recursion
    /**
     * BEGINNER'S EXPLANATION:
     * Same as recursion but cache results.
     * memo[index][target] = can we make target sum using elements from index onwards?
     */
    public boolean canPartition_Memoization(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        if (total % 2 != 0) return false;
        
        int target = total / 2;
        Boolean[][] memo = new Boolean[nums.length][target + 1];
        return canMakeSumMemo(nums, 0, target, memo);
    }
    
    private boolean canMakeSumMemo(int[] nums, int index, int target, Boolean[][] memo) {
        // Base cases
        if (target == 0) return true;
        if (index >= nums.length || target < 0) return false;
        
        // Check memo
        if (memo[index][target] != null) return memo[index][target];
        
        // Include or exclude
        boolean include = canMakeSumMemo(nums, index + 1, target - nums[index], memo);
        boolean exclude = canMakeSumMemo(nums, index + 1, target, memo);
        
        memo[index][target] = include || exclude;
        return memo[index][target];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(n * sum)
    // Space: O(n * sum)
    /**
     * BEGINNER'S EXPLANATION:
     * Build a 2D table where dp[i][j] means:
     * "Can we make sum j using first i elements?"
     * 
     * DP Table for nums=[1,5,11,5], target=11:
     * 
     *         Sum: 0  1  2  3  4  5  6  7  8  9  10  11
     *    []       T  F  F  F  F  F  F  F  F  F  F   F
     *    [1]      T  T  F  F  F  F  F  F  F  F  F   F
     *    [1,5]    T  T  F  F  F  T  T  F  F  F  F   F
     *    [1,5,11] T  T  F  F  F  T  T  F  F  F  F   T
     *    [1,5,11,5] T T  F  F  F  T  T  F  F  F  T   T ✓
     * 
     * Recurrence:
     * dp[i][j] = dp[i-1][j]  (exclude nums[i-1])
     *         OR dp[i-1][j-nums[i-1]]  (include nums[i-1])
     */
    public boolean canPartition_Tabulation(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        if (total % 2 != 0) return false;
        
        int target = total / 2;
        int n = nums.length;
        
        // dp[i][j] = can make sum j using first i elements
        boolean[][] dp = new boolean[n + 1][target + 1];
        
        // Base case: sum 0 is always possible (empty subset)
        for (int i = 0; i <= n; i++) {
            dp[i][0] = true;
        }
        
        // Fill DP table
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= target; j++) {
                // Exclude current element
                dp[i][j] = dp[i - 1][j];
                
                // Include current element (if it fits)
                if (j >= nums[i - 1]) {
                    dp[i][j] = dp[i][j] || dp[i - 1][j - nums[i - 1]];
                }
            }
        }
        
        return dp[n][target];
    }
    
    
    // ========== APPROACH 4: SPACE OPTIMIZED ⭐ BEST FOR INTERVIEWS ==========
    // Time: O(n * sum)
    // Space: O(sum) - Only 1D array!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous row to compute current row.
     * So use 1D array!
     * 
     * IMPORTANT: Traverse from RIGHT to LEFT in inner loop!
     * Why? To avoid using updated values from current iteration.
     * 
     * Evolution for nums=[1,5,11,5], target=11:
     * 
     * Initial:  [T F F F F F F F F F F F]
     * After 1:  [T T F F F F F F F F F F]
     * After 5:  [T T F F F T T F F F F F]
     * After 11: [T T F F F T T F F F F T]
     * After 5:  [T T F F F T T F F F T T] ✓
     */
    public boolean canPartition(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        if (total % 2 != 0) return false;
        
        int target = total / 2;
        boolean[] dp = new boolean[target + 1];
        dp[0] = true;  // Sum 0 is always possible
        
        // For each number
        for (int num : nums) {
            // Traverse from right to left (IMPORTANT!)
            for (int j = target; j >= num; j--) {
                dp[j] = dp[j] || dp[j - num];
            }
        }
        
        return dp[target];
    }
    
    
    // ========== APPROACH 5: USING BIT MANIPULATION (Advanced) ==========
    /**
     * ADVANCED EXPLANATION:
     * Use a BitSet to represent all possible sums.
     * Each bit i represents whether sum i is achievable.
     * For each number, left shift the bitset by that number and OR.
     * 
     * This is a cool optimization but stick to DP in interviews!
     */
    public boolean canPartition_BitSet(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        if (total % 2 != 0) return false;
        
        int target = total / 2;
        BitSet bits = new BitSet(target + 1);
        bits.set(0);  // Sum 0 is possible
        
        for (int num : nums) {
            // For each existing sum, we can also make (sum + num)
            BitSet newBits = (BitSet) bits.clone();
            // Create a new BitSet for the shifted values
            BitSet shifted = new BitSet();
            for (int i = bits.nextSetBit(0); i >= 0 && i + num <= target; i = bits.nextSetBit(i + 1)) {
                shifted.set(i + num);
            }
            newBits.or(shifted);
            bits = newBits;
        }
        
        return bits.get(target);
    }
    
    
    // ========== HELPER: Print Which Elements Form the Subset ==========
    public void printSubset(int[] nums) {
        int total = 0;
        for (int num : nums) total += num;
        
        if (total % 2 != 0) {
            System.out.println("Cannot partition (odd sum)");
            return;
        }
        
        int target = total / 2;
        int n = nums.length;
        boolean[][] dp = new boolean[n + 1][target + 1];
        
        for (int i = 0; i <= n; i++) dp[i][0] = true;
        
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= target; j++) {
                dp[i][j] = dp[i - 1][j];
                if (j >= nums[i - 1]) {
                    dp[i][j] = dp[i][j] || dp[i - 1][j - nums[i - 1]];
                }
            }
        }
        
        if (!dp[n][target]) {
            System.out.println("Cannot partition");
            return;
        }
        
        // Backtrack to find elements
        List<Integer> subset1 = new ArrayList<>();
        List<Integer> subset2 = new ArrayList<>();
        int i = n, j = target;
        
        while (i > 0 && j > 0) {
            // If came from including current element
            if (j >= nums[i - 1] && dp[i - 1][j - nums[i - 1]]) {
                subset1.add(nums[i - 1]);
                j -= nums[i - 1];
            } else {
                subset2.add(nums[i - 1]);
            }
            i--;
        }
        
        // Remaining elements go to subset2
        while (i > 0) {
            subset2.add(nums[i - 1]);
            i--;
        }
        
        System.out.println("Subset 1: " + subset1 + " (sum = " + target + ")");
        System.out.println("Subset 2: " + subset2 + " (sum = " + target + ")");
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        PartitionEqualSubsetSum_Leetcode416 solution = new PartitionEqualSubsetSum_Leetcode416();
        
        // Test case 1
        int[] nums1 = {1, 5, 11, 5};
        System.out.println("Array: [1,5,11,5]");
        System.out.println("Can Partition: " + solution.canPartition(nums1)); // true
        solution.printSubset(nums1);
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1, 2, 3, 5};
        System.out.println("Array: [1,2,3,5]");
        System.out.println("Can Partition: " + solution.canPartition(nums2)); // false
        System.out.println();
        
        // Test case 3
        int[] nums3 = {1, 2, 5};
        System.out.println("Array: [1,2,5]");
        System.out.println("Can Partition: " + solution.canPartition(nums3)); // false
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. PROBLEM REDUCTION IS KEY:
 *    - Original: Partition into 2 equal subsets
 *    - Reduced: Find subset with sum = total/2
 *    - This is Subset Sum Problem (0/1 Knapsack variant)
 * 
 * 2. EARLY PRUNING:
 *    - If total sum is odd → impossible
 *    - If max element > total/2 → impossible
 *    - These checks save computation!
 * 
 * 3. 0/1 KNAPSACK PATTERN:
 *    - Each element: include or exclude
 *    - DP state: dp[i][sum] = possible using first i elements
 *    - Recurrence: dp[i][j] = dp[i-1][j] OR dp[i-1][j-nums[i]]
 * 
 * 4. SPACE OPTIMIZATION:
 *    - 2D → 1D by using only previous row
 *    - CRUCIAL: Iterate from RIGHT to LEFT
 *    - This prevents using updated values incorrectly
 * 
 * 5. WHY RIGHT TO LEFT?
 *    - Left to right: dp[j-num] might already be updated in current iteration
 *    - Right to left: dp[j-num] still has previous iteration's value
 *    - Example: If num=2, j=5:
 *      - Left to right: dp[5] uses potentially updated dp[3]
 *      - Right to left: dp[5] uses old dp[3] ✓
 * 
 * 6. FOLLOW-UP QUESTIONS:
 *    - Print the actual subsets? → Backtrack through DP table
 *    - Count number of partitions? → Modify to count instead of boolean
 *    - Partition into k equal subsets? → Leetcode 698 (harder!)
 *    - Minimize difference? → Minimize Subset Sum Difference
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 416: Partition Equal Subset Sum (this)
 *    - Leetcode 494: Target Sum
 *    - Leetcode 698: Partition to K Equal Sum Subsets
 *    - Leetcode 1049: Last Stone Weight II
 *    - Leetcode 2035: Partition Array Into Two Arrays to Minimize Sum Difference
 * 
 * 8. INTERVIEW STRATEGY:
 *    - Start with problem reduction
 *    - Explain connection to Subset Sum
 *    - Show 2D DP solution
 *    - Optimize to 1D (impressive!)
 *    - Explain RIGHT to LEFT iteration clearly
 * 
 * 9. COMMON MISTAKES:
 *    - Forgetting to check if sum is odd
 *    - Iterating left to right in space-optimized version
 *    - Not initializing dp[0] = true
 *    - Confusing indices (dp[i] vs nums[i-1])
 * 
 * 10. COMPLEXITY:
 *     - Time: O(n * sum) where sum = total/2
 *     - Space: O(sum) with optimization
 *     - Cannot do better than O(n * sum) for general case
 *     - Note: This is pseudo-polynomial (depends on sum value)
 */

