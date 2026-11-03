package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;

import java.util.*;

/**
 * COIN CHANGE 2 - Leetcode 518 (Unlimited Coins)
 * Difficulty: Medium
 * Pattern: Unbounded Knapsack / Coin Change
 * 
 * PROBLEM:
 * You are given an integer array coins representing coins of different denominations 
 * and an integer amount representing a total amount of money.
 * Return the number of combinations that make up that amount. 
 * If that amount of money cannot be made up by any combination of the coins, return 0.
 * You may assume that you have an infinite number of each kind of coin.
 * 
 * EXAMPLES:
 * Input: amount = 5, coins = [1,2,5]
 * Output: 4
 * Explanation: there are four ways to make up the amount:
 * 5=5
 * 5=2+2+1
 * 5=2+1+1+1
 * 5=1+1+1+1+1
 */
public class CoinChangeUnlimited_Leetcode518 {
    
    /**
     * APPROACH 1: 2D DP (For understanding)
     * Time: O(n * amount), Space: O(n * amount)
     */
    public int change_2D(int amount, int[] coins) {
        int n = coins.length;
        // dp[i][j] = ways to make amount j using first i coins
        int[][] dp = new int[n + 1][amount + 1];
        
        // Base case: one way to make amount 0 (use no coins)
        for (int i = 0; i <= n; i++) {
            dp[i][0] = 1;
        }
        
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= amount; j++) {
                // Don't use current coin
                dp[i][j] = dp[i - 1][j];
                
                // Use current coin (if possible)
                if (j >= coins[i - 1]) {
                    dp[i][j] += dp[i][j - coins[i - 1]]; // Note: dp[i], not dp[i-1]
                }
            }
        }
        
        return dp[n][amount];
    }
    
    /**
     * APPROACH 2: 1D DP Optimized
     * Time: O(n * amount), Space: O(amount)
     */
    public int change_1D(int amount, int[] coins) {
        int[] dp = new int[amount + 1];
        dp[0] = 1; // One way to make amount 0
        
        // Process each coin type
        for (int coin : coins) {
            // For each amount from coin to target amount
            for (int j = coin; j <= amount; j++) {
                dp[j] += dp[j - coin];
            }
        }
        
        return dp[amount];
    }
    
    /**
     * APPROACH 3: Top-Down DP with Memoization
     * Time: O(n * amount), Space: O(n * amount)
     */
    public int change_TopDown(int amount, int[] coins) {
        Integer[][] memo = new Integer[coins.length][amount + 1];
        return dfs(coins, 0, amount, memo);
    }
    
    private int dfs(int[] coins, int index, int remaining, Integer[][] memo) {
        // Base cases
        if (remaining == 0) return 1;
        if (remaining < 0 || index >= coins.length) return 0;
        
        if (memo[index][remaining] != null) {
            return memo[index][remaining];
        }
        
        // Don't use current coin + Use current coin
        int result = dfs(coins, index + 1, remaining, memo) + 
                    dfs(coins, index, remaining - coins[index], memo);
        
        memo[index][remaining] = result;
        return result;
    }
    
    /**
     * APPROACH 4: Detailed step-by-step for understanding
     */
    public int change_Detailed(int amount, int[] coins) {
        System.out.println("=== Coin Change 2 - Step by Step ===");
        System.out.println("Amount: " + amount);
        System.out.println("Coins: " + Arrays.toString(coins));
        
        int[] dp = new int[amount + 1];
        dp[0] = 1;
        
        System.out.println("\nInitial DP: " + Arrays.toString(dp));
        
        for (int i = 0; i < coins.length; i++) {
            int coin = coins[i];
            System.out.println("\nProcessing coin: " + coin);
            
            for (int j = coin; j <= amount; j++) {
                int oldValue = dp[j];
                dp[j] += dp[j - coin];
                System.out.printf("  dp[%d] = %d + dp[%d] = %d + %d = %d\n", 
                    j, oldValue, j - coin, oldValue, dp[j - coin], dp[j]);
            }
            
            System.out.println("  DP after coin " + coin + ": " + Arrays.toString(dp));
        }
        
        return dp[amount];
    }
    
    /**
     * Helper: Generate all combinations (for small amounts)
     */
    public List<List<Integer>> generateAllCombinations(int amount, int[] coins) {
        List<List<Integer>> result = new ArrayList<>();
        generateCombinations(coins, 0, amount, new ArrayList<>(), result);
        return result;
    }
    
    private void generateCombinations(int[] coins, int index, int remaining, 
                                    List<Integer> current, List<List<Integer>> result) {
        if (remaining == 0) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        if (remaining < 0 || index >= coins.length) {
            return;
        }
        
        // Don't use current coin
        generateCombinations(coins, index + 1, remaining, current, result);
        
        // Use current coin
        current.add(coins[index]);
        generateCombinations(coins, index, remaining - coins[index], current, result);
        current.remove(current.size() - 1);
    }
    
    /**
     * Variant: Minimum coins needed (Coin Change 1)
     */
    public int coinChange_MinCoins(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1); // Initialize with impossible value
        dp[0] = 0;
        
        for (int i = 1; i <= amount; i++) {
            for (int coin : coins) {
                if (i >= coin) {
                    dp[i] = Math.min(dp[i], dp[i - coin] + 1);
                }
            }
        }
        
        return dp[amount] > amount ? -1 : dp[amount];
    }
    
    /**
     * Variant: Print one solution
     */
    public List<Integer> getOneSolution(int amount, int[] coins) {
        int[] dp = new int[amount + 1];
        int[] parent = new int[amount + 1];
        Arrays.fill(parent, -1);
        dp[0] = 1;
        
        for (int coin : coins) {
            for (int j = coin; j <= amount; j++) {
                if (dp[j - coin] > 0 && dp[j] == 0) {
                    dp[j] = 1;
                    parent[j] = coin;
                }
            }
        }
        
        if (dp[amount] == 0) return new ArrayList<>();
        
        List<Integer> solution = new ArrayList<>();
        int current = amount;
        while (current > 0) {
            int coin = parent[current];
            solution.add(coin);
            current -= coin;
        }
        
        return solution;
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        CoinChangeUnlimited_Leetcode518 solution = new CoinChangeUnlimited_Leetcode518();
        
        // Test case 1
        int[] coins1 = {1, 2, 5};
        int amount1 = 5;
        System.out.println("=== Coin Change 2 (Unlimited) ===");
        System.out.println("Coins: " + Arrays.toString(coins1));
        System.out.println("Amount: " + amount1);
        System.out.println("Ways (2D DP): " + solution.change_2D(amount1, coins1));
        System.out.println("Ways (1D DP): " + solution.change_1D(amount1, coins1));
        System.out.println("Ways (Top-Down): " + solution.change_TopDown(amount1, coins1));
        System.out.println();
        
        // Show all combinations for small example
        List<List<Integer>> combinations = solution.generateAllCombinations(amount1, coins1);
        System.out.println("All combinations:");
        for (int i = 0; i < combinations.size(); i++) {
            System.out.println("  " + (i + 1) + ": " + combinations.get(i) + 
                             " (sum: " + combinations.get(i).stream().mapToInt(Integer::intValue).sum() + ")");
        }
        System.out.println();
        
        // Detailed step-by-step
        solution.change_Detailed(amount1, coins1);
        System.out.println();
        
        // Test case 2
        int[] coins2 = {2};
        int amount2 = 3;
        System.out.println("Coins: " + Arrays.toString(coins2));
        System.out.println("Amount: " + amount2);
        System.out.println("Ways: " + solution.change_1D(amount2, coins2));
        System.out.println();
        
        // Test case 3 - Comparison with min coins
        int[] coins3 = {1, 3, 4};
        int amount3 = 6;
        System.out.println("=== Comparison: Ways vs Min Coins ===");
        System.out.println("Coins: " + Arrays.toString(coins3));
        System.out.println("Amount: " + amount3);
        System.out.println("Number of ways: " + solution.change_1D(amount3, coins3));
        System.out.println("Minimum coins needed: " + solution.coinChange_MinCoins(coins3, amount3));
        System.out.println("One solution: " + solution.getOneSolution(amount3, coins3));
        
        List<List<Integer>> allCombinations = solution.generateAllCombinations(amount3, coins3);
        System.out.println("All combinations:");
        for (List<Integer> combo : allCombinations) {
            System.out.println("  " + combo + " (length: " + combo.size() + ")");
        }
    }
}