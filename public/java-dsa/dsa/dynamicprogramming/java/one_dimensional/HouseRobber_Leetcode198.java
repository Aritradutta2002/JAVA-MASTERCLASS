package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;

// Fixed package name to match folder structure

/**
 * HOUSE ROBBER - Leetcode 198
 * Difficulty: Medium
 * Companies: Amazon, Google, Microsoft, Apple, Bloomberg
 * 
 * PROBLEM:
 * You are a robber planning to rob houses along a street. Each house has money.
 * Adjacent houses have security systems, so you CANNOT rob two adjacent houses.
 * Given an array of money, return maximum amount you can rob without alerting police.
 * 
 * EXAMPLES:
 * Input: nums = [1,2,3,1]
 * Output: 4
 * Explanation: Rob house 1 (money=1) then rob house 3 (money=3). Total = 1+3 = 4.
 * 
 * Input: nums = [2,7,9,3,1]
 * Output: 12
 * Explanation: Rob house 1 (money=2), house 3 (money=9), and house 5 (money=1). Total = 2+9+1 = 12.
 * 
 * PATTERN: Choose or Skip Pattern
 * At each house: Either ROB it (skip previous) OR SKIP it (take previous max)
 */

public class HouseRobber_Leetcode198 {
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(2^n) - Each house has 2 choices
    // Space: O(n) - Recursion stack
    /**
     * BEGINNER'S EXPLANATION:
     * At each house you have 2 choices:
     * 1. ROB this house → Add its money + max from houses before previous
     * 2. SKIP this house → Take max from previous house
     * 
     * Visualization for [2,7,9,3,1]:
     *                     rob(0)
     *                    /      \
     *            rob(2)              rob(1)
     *           /      \            /      \
     *      rob(4)    rob(3)    rob(3)    rob(2)
     *         ...       ...       ...       ...
     */
    public int rob_Recursive(int[] nums) {
        return robRecursive(nums, 0);
    }
    
    private int robRecursive(int[] nums, int index) {
        // Base case: reached end
        if (index >= nums.length) return 0;
        
        // Choice 1: Rob current house (skip next, go to index+2)
        int robCurrent = nums[index] + robRecursive(nums, index + 2);
        
        // Choice 2: Skip current house (go to index+1)
        int skipCurrent = robRecursive(nums, index + 1);
        
        // Return maximum of two choices
        return Math.max(robCurrent, skipCurrent);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(n) - Each index calculated once
    // Space: O(n) - Memo array + recursion stack
    /**
     * BEGINNER'S EXPLANATION:
     * Same logic as recursion but save results to avoid recalculation.
     * Like keeping a cheat sheet of houses you've already considered!
     */
    public int rob_Memoization(int[] nums) {
        int[] memo = new int[nums.length];
        java.util.Arrays.fill(memo, -1); // -1 means not calculated yet
        return robMemo(nums, 0, memo);
    }
    
    private int robMemo(int[] nums, int index, int[] memo) {
        // Base case
        if (index >= nums.length) return 0;
        
        // If already calculated, return saved result
        if (memo[index] != -1) return memo[index];
        
        // Calculate and save
        int robCurrent = nums[index] + robMemo(nums, index + 2, memo);
        int skipCurrent = robMemo(nums, index + 1, memo);
        
        memo[index] = Math.max(robCurrent, skipCurrent);
        return memo[index];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(n) - Single pass
    // Space: O(n) - DP array
    /**
     * BEGINNER'S EXPLANATION:
     * Build answer from ground up, house by house.
     * 
     * DP Definition:
     * dp[i] = maximum money we can rob from houses 0 to i
     * 
     * Recurrence Relation:
     * dp[i] = max(nums[i] + dp[i-2], dp[i-1])
     *              ↑ Rob current       ↑ Skip current
     * 
     * DP Table Visualization for [2,7,9,3,1]:
     * Index:    0   1   2   3   4
     * Money:    2   7   9   3   1
     * DP:       2   7  11  11  12
     *           ↑   ↑   ↑
     *           2   7   2+9=11
     */
    public int rob_Tabulation(int[] nums) {
        int n = nums.length;
        if (n == 0) return 0;
        if (n == 1) return nums[0];
        
        // Create DP table
        int[] dp = new int[n];
        
        // Base cases
        dp[0] = nums[0];                          // Only house 0
        dp[1] = Math.max(nums[0], nums[1]);       // Better of first 2 houses
        
        // Fill DP table
        for (int i = 2; i < n; i++) {
            // Either rob current + skip previous, OR skip current
            dp[i] = Math.max(nums[i] + dp[i - 2], dp[i - 1]);
        }
        
        return dp[n - 1];
    }
    
    
    // ========== APPROACH 4: SPACE OPTIMIZED ⭐ BEST FOR INTERVIEWS ==========
    // Time: O(n) - Single pass
    // Space: O(1) - Only 2 variables!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous 2 values (i-1 and i-2).
     * So instead of entire array, just track last 2 results!
     * 
     * Visualization of sliding window:
     * [2, 7, 9, 3, 1]
     *  ↑  ↑
     *  prev2 prev1  → current = max(9+2, 7) = 11
     * 
     *     ↑  ↑
     *     prev2 prev1  → current = max(3+7, 11) = 11
     */
    public int rob(int[] nums) {
        int n = nums.length;
        if (n == 0) return 0;
        if (n == 1) return nums[0];
        
        // Track previous 2 values
        int prev2 = nums[0];                      // Max at i-2
        int prev1 = Math.max(nums[0], nums[1]);   // Max at i-1
        
        // Process remaining houses
        for (int i = 2; i < n; i++) {
            int current = Math.max(nums[i] + prev2, prev1);
            // Slide the window
            prev2 = prev1;
            prev1 = current;
        }
        
        return prev1;
    }
    
    
    // ========== APPROACH 5: EVEN MORE CONCISE ==========
    /**
     * Super clean version using two variables representing:
     * - rob: max money if we rob current house
     * - notRob: max money if we don't rob current house
     */
    public int rob_Elegant(int[] nums) {
        int rob = 0;      // Max if we rob current
        int notRob = 0;   // Max if we don't rob current
        
        for (int num : nums) {
            int newRob = notRob + num;      // Rob current + previous notRob
            notRob = Math.max(rob, notRob); // Best of previous rob or notRob
            rob = newRob;
        }
        
        return Math.max(rob, notRob);
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        HouseRobber_Leetcode198 solution = new HouseRobber_Leetcode198();
        
        // Test case 1
        int[] nums1 = {1, 2, 3, 1};
        System.out.println("Input: [1,2,3,1]");
        System.out.println("Output: " + solution.rob(nums1)); // Output: 4
        
        // Test case 2
        int[] nums2 = {2, 7, 9, 3, 1};
        System.out.println("\nInput: [2,7,9,3,1]");
        System.out.println("Output: " + solution.rob(nums2)); // Output: 12
        
        // Test case 3
        int[] nums3 = {2, 1, 1, 2};
        System.out.println("\nInput: [2,1,1,2]");
        System.out.println("Output: " + solution.rob(nums3)); // Output: 4
        
        // Edge cases
        int[] nums4 = {5};
        System.out.println("\nInput: [5]");
        System.out.println("Output: " + solution.rob(nums4)); // Output: 5
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. PATTERN: Choose or Skip
 *    - Common in problems where you can't take adjacent elements
 *    - At each step: max(take current + previous skip, skip current)
 * 
 * 2. DP STATE DEFINITION:
 *    - dp[i] = maximum value using elements from 0 to i
 *    - OR two states: rob[i] and notRob[i]
 * 
 * 3. RECURRENCE RELATION:
 *    - dp[i] = max(nums[i] + dp[i-2], dp[i-1])
 *    - Explain this clearly in interviews!
 * 
 * 4. FOLLOW-UP QUESTIONS:
 *    - Houses in a circle? → House Robber II (Leetcode 213)
 *    - Houses in a tree? → House Robber III (Leetcode 337)
 *    - k consecutive houses can't be robbed? → Generalize the pattern
 * 
 * 5. INTERVIEW STRATEGY:
 *    - Start with recursive explanation
 *    - Show memoization
 *    - Implement tabulation
 *    - Optimize space (shows expertise!)
 * 
 * 6. RELATED PROBLEMS:
 *    - Leetcode 213: House Robber II (circular)
 *    - Leetcode 337: House Robber III (binary tree)
 *    - Leetcode 740: Delete and Earn
 *    - Leetcode 2320: Count Number of Ways to Place Houses
 * 
 * 7. COMMON MISTAKES:
 *    - Forgetting to compare nums[1] with nums[0] in base case
 *    - Off-by-one errors in space optimized version
 *    - Not handling edge cases (empty array, single element)
 */

