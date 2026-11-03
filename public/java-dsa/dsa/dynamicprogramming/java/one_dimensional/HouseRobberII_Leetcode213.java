package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;

// Fixed package name to match folder structure

/**
 * HOUSE ROBBER II - Leetcode 213
 * Difficulty: Medium
 * Companies: Amazon, Microsoft, Apple, Google, Facebook
 * 
 * PROBLEM:
 * Similar to House Robber I, but houses are arranged in a CIRCLE.
 * This means the FIRST and LAST houses are adjacent.
 * You cannot rob both the first and last house.
 * 
 * EXAMPLES:
 * Input: nums = [2,3,2]
 * Output: 3
 * Explanation: You cannot rob house 1 (money=2) and house 3 (money=2) together, they're adjacent.
 * 
 * Input: nums = [1,2,3,1]
 * Output: 4
 * Explanation: Rob house 1 (money=1) and house 3 (money=3). Total = 4.
 * 
 * PATTERN: House Robber I + Constraint Handling
 * KEY INSIGHT: If houses form a circle, we have 2 scenarios:
 * 1. Rob houses from 0 to n-2 (exclude last house)
 * 2. Rob houses from 1 to n-1 (exclude first house)
 * Answer = max of these two scenarios
 */

public class HouseRobberII_Leetcode213 {
    
    // ========== MAIN SOLUTION ⭐ RECOMMENDED ==========
    // Time: O(n) - Two passes of O(n)
    // Space: O(1) - Constant space for each pass
    /**
     * BEGINNER'S EXPLANATION:
     * Since first and last are neighbors in a circle, we can't rob both.
     * So we split into 2 cases:
     * 
     * Case 1: Exclude last house
     *    Houses: [0, 1, 2, ..., n-2]  ← Regular House Robber I
     * 
     * Case 2: Exclude first house
     *    Houses: [1, 2, 3, ..., n-1]  ← Regular House Robber I
     * 
     * Visualization for [2,3,2]:
     *        2
     *       / \
     *      3   2  ← These are neighbors!
     * 
     * Case 1: Rob [2,3]     → max = 3
     * Case 2: Rob [3,2]     → max = 3
     * Final answer: max(3,3) = 3
     */
    public int rob(int[] nums) {
        int n = nums.length;
        
        // Edge cases
        if (n == 0) return 0;
        if (n == 1) return nums[0];
        if (n == 2) return Math.max(nums[0], nums[1]);
        
        // Case 1: Rob houses from 0 to n-2 (exclude last)
        int maxExcludingLast = robLinear(nums, 0, n - 2);
        
        // Case 2: Rob houses from 1 to n-1 (exclude first)
        int maxExcludingFirst = robLinear(nums, 1, n - 1);
        
        // Return maximum of both cases
        return Math.max(maxExcludingLast, maxExcludingFirst);
    }
    
    /**
     * Helper function: Regular House Robber I for a linear range
     * This is the space-optimized version from House Robber I
     */
    private int robLinear(int[] nums, int start, int end) {
        int prev2 = 0;  // Max money at i-2
        int prev1 = 0;  // Max money at i-1
        
        for (int i = start; i <= end; i++) {
            // Either rob current + prev2, OR skip current (take prev1)
            int current = Math.max(nums[i] + prev2, prev1);
            // Slide the window
            prev2 = prev1;
            prev1 = current;
        }
        
        return prev1;
    }
    
    
    // ========== APPROACH 2: WITH DP ARRAY (More Intuitive) ==========
    // Time: O(n)
    // Space: O(n)
    /**
     * BEGINNER'S EXPLANATION:
     * Same logic but using DP arrays for clarity.
     * Good for understanding before optimizing to O(1) space.
     */
    public int rob_WithDPArray(int[] nums) {
        int n = nums.length;
        if (n == 0) return 0;
        if (n == 1) return nums[0];
        if (n == 2) return Math.max(nums[0], nums[1]);
        
        // Case 1: Houses 0 to n-2
        int maxCase1 = robLinearDP(nums, 0, n - 2);
        
        // Case 2: Houses 1 to n-1
        int maxCase2 = robLinearDP(nums, 1, n - 1);
        
        return Math.max(maxCase1, maxCase2);
    }
    
    private int robLinearDP(int[] nums, int start, int end) {
        int len = end - start + 1;
        if (len == 1) return nums[start];
        
        int[] dp = new int[len];
        dp[0] = nums[start];
        dp[1] = Math.max(nums[start], nums[start + 1]);
        
        for (int i = 2; i < len; i++) {
            dp[i] = Math.max(nums[start + i] + dp[i - 2], dp[i - 1]);
        }
        
        return dp[len - 1];
    }
    
    
    // ========== STEP-BY-STEP EXPLANATION ==========
    /**
     * Let's trace through example: [2,3,2]
     * 
     * Houses arranged in circle:
     *      [2] --- [3]
     *       \     /
     *        [2]
     * 
     * Step 1: Identify the constraint
     * - First (index 0) and Last (index 2) are neighbors
     * - Can't rob both
     * 
     * Step 2: Split into 2 scenarios
     * Scenario A: Rob from index 0 to n-2 (exclude last)
     *   Array: [2, 3]
     *   Max: 3 (rob house with 3)
     * 
     * Scenario B: Rob from index 1 to n-1 (exclude first)
     *   Array: [3, 2]
     *   Max: 3 (rob house with 3)
     * 
     * Step 3: Take maximum
     * Answer: max(3, 3) = 3
     * 
     * 
     * Another example: [1,2,3,1]
     * 
     * Houses arranged in circle:
     *      [1] --- [2]
     *       |       |
     *      [1] --- [3]
     * 
     * Scenario A: Rob [1,2,3] (exclude last 1)
     *   DP: [1, 2, 4]
     *   Max: 4 (rob 1 and 3)
     * 
     * Scenario B: Rob [2,3,1] (exclude first 1)
     *   DP: [2, 3, 3]
     *   Max: 3 (rob 2 or 3)
     * 
     * Answer: max(4, 3) = 4
     */
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        HouseRobberII_Leetcode213 solution = new HouseRobberII_Leetcode213();
        
        // Test case 1
        int[] nums1 = {2, 3, 2};
        System.out.println("Input: [2,3,2]");
        System.out.println("Output: " + solution.rob(nums1)); // Output: 3
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1, 2, 3, 1};
        System.out.println("Input: [1,2,3,1]");
        System.out.println("Output: " + solution.rob(nums2)); // Output: 4
        System.out.println();
        
        // Test case 3
        int[] nums3 = {1, 2, 3};
        System.out.println("Input: [1,2,3]");
        System.out.println("Output: " + solution.rob(nums3)); // Output: 3
        System.out.println();
        
        // Edge case: single house
        int[] nums4 = {5};
        System.out.println("Input: [5]");
        System.out.println("Output: " + solution.rob(nums4)); // Output: 5
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. CIRCULAR CONSTRAINT HANDLING:
 *    - When array is circular and first/last are neighbors
 *    - Strategy: Split into 2 linear subproblems
 *    - Common pattern in circular array problems
 * 
 * 2. PROBLEM REDUCTION:
 *    - Reduce complex problem to simpler known problem
 *    - House Robber II → Two instances of House Robber I
 *    - Shows strong problem-solving skills in interviews!
 * 
 * 3. EDGE CASES TO MENTION:
 *    - Empty array
 *    - Single house (no circle constraint)
 *    - Two houses (just pick max)
 * 
 * 4. FOLLOW-UP QUESTIONS:
 *    - Houses in a tree structure? → House Robber III (Leetcode 337)
 *    - Can rob k houses apart? → Modify the robLinear function
 *    - Multiple circles? → Apply same split strategy
 * 
 * 5. INTERVIEW STRATEGY:
 *    Step 1: Recognize it's House Robber with constraint
 *    Step 2: Identify the circular constraint
 *    Step 3: Propose splitting strategy
 *    Step 4: Implement robLinear helper
 *    Step 5: Combine results with max()
 * 
 * 6. COMPLEXITY ANALYSIS:
 *    - Time: O(n) - Two passes through array
 *    - Space: O(1) - Only using variables
 *    - Better than O(n) space of DP array
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 198: House Robber (prerequisite)
 *    - Leetcode 337: House Robber III (tree version)
 *    - Leetcode 740: Delete and Earn
 *    - Leetcode 213: This problem!
 * 
 * 8. COMMON MISTAKES:
 *    - Forgetting to handle edge case n=2
 *    - Incorrect range for start and end indices
 *    - Not recognizing this reduces to House Robber I
 *    - Trying to solve with complex circular DP (overcomplicating)
 * 
 * 9. WHY THIS APPROACH WORKS:
 *    - If we rob first house, we CANNOT rob last
 *      → So consider range [0, n-2]
 *    - If we rob last house, we CANNOT rob first
 *      → So consider range [1, n-1]
 *    - If we rob neither first nor last
 *      → This case is included in both scenarios above!
 *    - Taking max of both covers all possibilities
 */

