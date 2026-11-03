package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;

// Fixed package name to match folder structure

/**
 * COIN CHANGE 2 - Leetcode 518
 * Difficulty: Medium
 * Companies: Google, Amazon, Facebook, Microsoft
 * 
 * PROBLEM:
 * You are given an integer array coins representing coins of different denominations
 * and an integer amount representing a total amount of money.
 * Return the NUMBER OF COMBINATIONS that make up that amount.
 * You have an INFINITE supply of each coin.
 * 
 * EXAMPLES:
 * Input: amount = 5, coins = [1,2,5]
 * Output: 4
 * Explanation: 4 ways to make amount 5:
 * - 5 = 5
 * - 5 = 2+2+1
 * - 5 = 2+1+1+1
 * - 5 = 1+1+1+1+1
 * 
 * Input: amount = 3, coins = [2]
 * Output: 0
 * Explanation: 3 cannot be made with only coin 2
 * 
 * Input: amount = 10, coins = [10]
 * Output: 1
 * 
 * PATTERN: Unbounded Knapsack (Combination Sum)
 * DIFFERENCE from Coin Change I: Count combinations, not minimum coins
 */

public class CoinChange2_Leetcode518 {
    
    // ========== KEY DIFFERENCE: COIN CHANGE I vs II ==========
    /**
     * Coin Change I (Leetcode 322):
     * - Find MINIMUM number of coins needed
     * - Return -1 if impossible
     * - Example: amount=11, coins=[1,2,5] → 3 coins (5+5+1)
     * 
     * Coin Change II (Leetcode 518):
     * - Count NUMBER OF WAYS to make amount
     * - Return 0 if impossible
     * - Example: amount=5, coins=[1,2,5] → 4 ways
     * 
     * Both are unbounded knapsack (unlimited coins)!
     */
    
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(amount^coins.length) - Exponential
    // Space: O(amount) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * For each coin, we can use it 0, 1, 2, ... times
     * Try all possibilities and count combinations.
     */
    public int change_Recursive(int amount, int[] coins) {
        return countWays(amount, coins, 0);
    }
    
    private int countWays(int amount, int[] coins, int index) {
        // Base cases
        if (amount == 0) return 1;  // Found one way!
        if (amount < 0 || index >= coins.length) return 0;
        
        // Include current coin (can use again) OR skip it
        return countWays(amount - coins[index], coins, index) +  // Use coin
               countWays(amount, coins, index + 1);              // Skip coin
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(amount * n) where n = number of coins
    // Space: O(amount * n)
    /**
     * BEGINNER'S EXPLANATION:
     * Cache results for (remaining amount, current coin index)
     */
    public int change_Memoization(int amount, int[] coins) {
        Integer[][] memo = new Integer[amount + 1][coins.length];
        return countWaysMemo(amount, coins, 0, memo);
    }
    
    private int countWaysMemo(int amount, int[] coins, int index, Integer[][] memo) {
        if (amount == 0) return 1;
        if (amount < 0 || index >= coins.length) return 0;
        
        if (memo[amount][index] != null) return memo[amount][index];
        
        memo[amount][index] = 
            countWaysMemo(amount - coins[index], coins, index, memo) +
            countWaysMemo(amount, coins, index + 1, memo);
        
        return memo[amount][index];
    }
    
    
    // ========== APPROACH 3: 2D DP (TABULATION) ==========
    // Time: O(amount * n)
    // Space: O(amount * n)
    /**
     * BEGINNER'S EXPLANATION:
     * dp[i][j] = number of ways to make amount j using first i coins
     * 
     * Recurrence:
     * - Don't use coin i: dp[i-1][j]
     * - Use coin i: dp[i][j - coins[i-1]]
     * - Total: dp[i][j] = dp[i-1][j] + dp[i][j - coins[i-1]]
     * 
     * DP Table for amount=5, coins=[1,2,5]:
     * 
     *          Amount: 0  1  2  3  4  5
     *       []        1  0  0  0  0  0
     *       [1]       1  1  1  1  1  1
     *       [1,2]     1  1  2  2  3  3
     *       [1,2,5]   1  1  2  2  3  4
     *                                ↑ Answer
     * 
     * Explanation:
     * - dp[0][j] = 0 for j>0 (no coins, can't make amount)
     * - dp[i][0] = 1 (one way: use 0 coins)
     * 
     * For dp[2][3] (coins [1,2], amount 3):
     * - Don't use coin 2: dp[1][3] = 1 way (1+1+1)
     * - Use coin 2: dp[2][1] = 1 way (2+1)
     * - Total: 2 ways
     */
    public int change_2D_DP(int amount, int[] coins) {
        int n = coins.length;
        int[][] dp = new int[n + 1][amount + 1];
        
        // Base case: amount 0 can be made in 1 way (use no coins)
        for (int i = 0; i <= n; i++) {
            dp[i][0] = 1;
        }
        
        // Fill DP table
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= amount; j++) {
                // Don't use current coin
                dp[i][j] = dp[i - 1][j];
                
                // Use current coin (if it fits)
                if (j >= coins[i - 1]) {
                    dp[i][j] += dp[i][j - coins[i - 1]];
                }
            }
        }
        
        return dp[n][amount];
    }
    
    
    // ========== APPROACH 4: 1D DP ⭐ OPTIMAL ==========
    // Time: O(amount * n)
    // Space: O(amount) - Only 1D array!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous row, so use 1D array!
     * 
     * Evolution for amount=5, coins=[1,2,5]:
     * 
     * Initial:       [1, 0, 0, 0, 0, 0]
     * After coin 1:  [1, 1, 1, 1, 1, 1]
     * After coin 2:  [1, 1, 2, 2, 3, 3]
     * After coin 5:  [1, 1, 2, 2, 3, 4]
     *                                 ↑ Answer
     * 
     * How dp[5] becomes 4 after coin 5:
     * - Without coin 5: 3 ways (from previous)
     * - With coin 5: dp[5-5] = dp[0] = 1 way
     * - Total: 3 + 1 = 4 ways
     * 
     * IMPORTANT: Iterate coins in outer loop!
     * This ensures we count COMBINATIONS not PERMUTATIONS.
     */
    public int change(int amount, int[] coins) {
        int[] dp = new int[amount + 1];
        dp[0] = 1;  // One way to make 0: use no coins
        
        // For each coin
        for (int coin : coins) {
            // Update all amounts that can use this coin
            for (int j = coin; j <= amount; j++) {
                dp[j] += dp[j - coin];
            }
        }
        
        return dp[amount];
    }
    
    
    // ========== IMPORTANT: COMBINATIONS vs PERMUTATIONS ==========
    /**
     * BEGINNER'S EXPLANATION:
     * 
     * For amount=3, coins=[1,2]:
     * 
     * COMBINATIONS (this problem):
     * - {1,1,1}, {1,2}
     * - 2 ways
     * - Order doesn't matter: {1,2} and {2,1} are same
     * 
     * PERMUTATIONS (different problem):
     * - {1,1,1}, {1,2}, {2,1}
     * - 3 ways
     * - Order matters: {1,2} and {2,1} are different
     * 
     * How to code each:
     * 
     * COMBINATIONS: Iterate coins in OUTER loop
     * for (int coin : coins) {
     *     for (int j = coin; j <= amount; j++) {
     *         dp[j] += dp[j - coin];
     *     }
     * }
     * 
     * PERMUTATIONS: Iterate amount in OUTER loop
     * for (int j = 1; j <= amount; j++) {
     *     for (int coin : coins) {
     *         if (j >= coin) {
     *             dp[j] += dp[j - coin];
     *         }
     *     }
     * }
     */
    
    
    // ========== HELPER: PRINT ALL COMBINATIONS ==========
    /**
     * For understanding: Print all valid combinations
     */
    public void printAllCombinations(int amount, int[] coins) {
        System.out.println("All combinations for amount " + amount + ":");
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        findCombinations(amount, coins, 0, new java.util.ArrayList<>(), result);
        for (java.util.List<Integer> combo : result) {
            System.out.println("  " + combo);
        }
        System.out.println("Total: " + result.size() + " ways");
    }
    
    private void findCombinations(int amount, int[] coins, int start, 
                                 java.util.List<Integer> current, 
                                 java.util.List<java.util.List<Integer>> result) {
        if (amount == 0) {
            result.add(new java.util.ArrayList<>(current));
            return;
        }
        if (amount < 0) return;
        
        for (int i = start; i < coins.length; i++) {
            current.add(coins[i]);
            findCombinations(amount - coins[i], coins, i, current, result);
            current.remove(current.size() - 1);
        }
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        CoinChange2_Leetcode518 solution = new CoinChange2_Leetcode518();
        
        // Test case 1
        int amount1 = 5;
        int[] coins1 = {1, 2, 5};
        System.out.println("Amount: " + amount1 + ", Coins: [1,2,5]");
        System.out.println("Number of ways: " + solution.change(amount1, coins1)); // 4
        solution.printAllCombinations(amount1, coins1);
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 2
        int amount2 = 3;
        int[] coins2 = {2};
        System.out.println("Amount: " + amount2 + ", Coins: [2]");
        System.out.println("Number of ways: " + solution.change(amount2, coins2)); // 0
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 3
        int amount3 = 10;
        int[] coins3 = {10};
        System.out.println("Amount: " + amount3 + ", Coins: [10]");
        System.out.println("Number of ways: " + solution.change(amount3, coins3)); // 1
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. UNBOUNDED KNAPSACK PATTERN:
 *    - Can use each item unlimited times
 *    - Different from 0/1 knapsack (use each once)
 *    - Key: After using item, stay at same index
 * 
 * 2. COMBINATIONS vs PERMUTATIONS:
 *    - Combinations: {1,2} = {2,1}, order doesn't matter
 *    - Permutations: {1,2} ≠ {2,1}, order matters
 *    - Control by loop order: coins outer = combinations
 * 
 * 3. WHY COIN OUTER LOOP FOR COMBINATIONS:
 *    - Process coins in order
 *    - Once we move to next coin, never go back
 *    - This prevents counting same combo in different orders
 *    - Example: After processing coin 2, we won't add 1 before 2
 * 
 * 4. DP RECURRENCE:
 *    dp[i] = number of ways to make amount i
 *    For each coin:
 *        dp[i] += dp[i - coin]  (ways using this coin)
 * 
 * 5. FOLLOW-UP QUESTIONS:
 *    - Count permutations instead? → Swap loop order
 *    - Limited coins (0/1 knapsack)? → Different approach
 *    - Print all combinations? → Use backtracking
 *    - Find minimum coins? → Coin Change I (Leetcode 322)
 * 
 * 6. RELATED PROBLEMS:
 *    - Leetcode 518: Coin Change 2 (this)
 *    - Leetcode 322: Coin Change (minimum coins)
 *    - Leetcode 377: Combination Sum IV (permutations!)
 *    - Leetcode 39: Combination Sum
 *    - Leetcode 40: Combination Sum II
 * 
 * 7. INTERVIEW STRATEGY:
 *    - Clarify: combinations or permutations?
 *    - Start with recursive explanation
 *    - Show 2D DP for clarity
 *    - Optimize to 1D (impressive!)
 *    - Explain loop order significance
 * 
 * 8. COMMON MISTAKES:
 *    - Wrong loop order (permutations instead of combinations)
 *    - Forgetting dp[0] = 1
 *    - Confusing with Coin Change I (minimum coins)
 *    - Not handling amount = 0 case
 *    - Starting inner loop from 0 instead of coin value
 * 
 * 9. COMPLEXITY:
 *    - Time: O(amount * n) where n = number of coins
 *    - Space: O(amount) with 1D DP
 *    - Cannot do better as we need to check all amounts
 * 
 * 10. DEBUGGING TIP:
 *     If answer seems wrong:
 *     - Check if counting combinations or permutations
 *     - Verify loop order
 *     - Trace small example by hand
 *     - Print intermediate DP array
 */

