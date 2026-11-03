package JAVA_DSA.DYNAMIC_PROGRAMMING.java.classic_problems;

import java.util.*;

/**
 * BURST BALLOONS - Leetcode 312
 * Difficulty: Hard
 * Pattern: Matrix Chain Multiplication (MCM) / Interval DP
 * 
 * PROBLEM:
 * You have n balloons, indexed from 0 to n - 1. Each balloon is painted with a number 
 * on it represented by array nums. You are asked to burst all the balloons.
 * If you burst balloon i, you will get nums[i - 1] * nums[i] * nums[i + 1] coins.
 * If i - 1 or i + 1 goes out of bounds, treat it as if there is a balloon with a 1 painted on it.
 * Return the maximum coins you can collect by bursting the balloons wisely.
 * 
 * EXAMPLES:
 * Input: nums = [3,1,5,8]
 * Output: 167
 * Explanation:
 * nums = [3,1,5,8] --> [3,5,8] --> [3,8] --> [8] --> []
 * coins =  3*1*5    +   3*5*8   +  1*3*8  + 1*8*1 = 15 + 120 + 24 + 8 = 167
 * 
 * KEY INSIGHT:
 * Think backwards - instead of which balloon to burst first, 
 * think about which balloon to burst LAST in a range [left, right].
 * When we burst balloon k last, we get left * k * right coins.
 */
public class BurstBalloons_Leetcode312 {
    
    /**
     * APPROACH 1: Top-Down DP with Memoization
     * Time: O(n^3), Space: O(n^2)
     */
    public int maxCoins_TopDown(int[] nums) {
        int n = nums.length;
        // Add 1s at boundaries to handle edge cases
        int[] balloons = new int[n + 2];
        balloons[0] = balloons[n + 1] = 1;
        for (int i = 1; i <= n; i++) {
            balloons[i] = nums[i - 1];
        }
        
        Integer[][] memo = new Integer[n + 2][n + 2];
        return dfs(balloons, 0, n + 1, memo);
    }
    
    private int dfs(int[] balloons, int left, int right, Integer[][] memo) {
        // Base case: no balloons between left and right
        if (left + 1 == right) return 0;
        
        if (memo[left][right] != null) {
            return memo[left][right];
        }
        
        int maxCoins = 0;
        // Try bursting each balloon k last in range (left, right)
        for (int k = left + 1; k < right; k++) {
            int coins = balloons[left] * balloons[k] * balloons[right];
            coins += dfs(balloons, left, k, memo);  // Left subproblem
            coins += dfs(balloons, k, right, memo); // Right subproblem
            maxCoins = Math.max(maxCoins, coins);
        }
        
        memo[left][right] = maxCoins;
        return maxCoins;
    }
    
    /**
     * APPROACH 2: Bottom-Up DP
     * Time: O(n^3), Space: O(n^2)
     */
    public int maxCoins_BottomUp(int[] nums) {
        int n = nums.length;
        // Add 1s at boundaries
        int[] balloons = new int[n + 2];
        balloons[0] = balloons[n + 1] = 1;
        for (int i = 1; i <= n; i++) {
            balloons[i] = nums[i - 1];
        }
        
        // dp[left][right] = max coins from bursting balloons in (left, right)
        int[][] dp = new int[n + 2][n + 2];
        
        // Length of the interval
        for (int len = 2; len <= n + 1; len++) {
            for (int left = 0; left <= n + 1 - len; left++) {
                int right = left + len;
                
                // Try bursting each balloon k last in range (left, right)
                for (int k = left + 1; k < right; k++) {
                    int coins = balloons[left] * balloons[k] * balloons[right];
                    coins += dp[left][k] + dp[k][right];
                    dp[left][right] = Math.max(dp[left][right], coins);
                }
            }
        }
        
        return dp[0][n + 1];
    }
    
    /**
     * APPROACH 3: Space-Optimized (for understanding MCM pattern)
     * Note: This particular problem doesn't easily optimize space due to dependencies
     */
    public int maxCoins_Explained(int[] nums) {
        System.out.println("=== Burst Balloons Step by Step ===");
        System.out.println("Original array: " + Arrays.toString(nums));
        
        int n = nums.length;
        int[] balloons = new int[n + 2];
        balloons[0] = balloons[n + 1] = 1;
        for (int i = 1; i <= n; i++) {
            balloons[i] = nums[i - 1];
        }
        System.out.println("With boundaries: " + Arrays.toString(balloons));
        
        int[][] dp = new int[n + 2][n + 2];
        
        for (int len = 2; len <= n + 1; len++) {
            System.out.println("\nLength " + len + " intervals:");
            for (int left = 0; left <= n + 1 - len; left++) {
                int right = left + len;
                System.out.printf("  Interval (%d, %d): ", left, right);
                
                for (int k = left + 1; k < right; k++) {
                    int coins = balloons[left] * balloons[k] * balloons[right];
                    coins += dp[left][k] + dp[k][right];
                    
                    System.out.printf("k=%d: %d*%d*%d + %d + %d = %d; ", 
                        k, balloons[left], balloons[k], balloons[right], 
                        dp[left][k], dp[k][right], coins);
                    
                    dp[left][right] = Math.max(dp[left][right], coins);
                }
                System.out.printf("max = %d\n", dp[left][right]);
            }
        }
        
        return dp[0][n + 1];
    }
    
    /**
     * Helper method to print DP table
     */
    public void printDPTable(int[] nums) {
        int n = nums.length;
        int[] balloons = new int[n + 2];
        balloons[0] = balloons[n + 1] = 1;
        for (int i = 1; i <= n; i++) {
            balloons[i] = nums[i - 1];
        }
        
        int[][] dp = new int[n + 2][n + 2];
        
        for (int len = 2; len <= n + 1; len++) {
            for (int left = 0; left <= n + 1 - len; left++) {
                int right = left + len;
                for (int k = left + 1; k < right; k++) {
                    int coins = balloons[left] * balloons[k] * balloons[right];
                    coins += dp[left][k] + dp[k][right];
                    dp[left][right] = Math.max(dp[left][right], coins);
                }
            }
        }
        
        System.out.println("\nDP Table:");
        System.out.print("   ");
        for (int j = 0; j < n + 2; j++) {
            System.out.printf("%4d", j);
        }
        System.out.println();
        
        for (int i = 0; i < n + 2; i++) {
            System.out.printf("%2d ", i);
            for (int j = 0; j < n + 2; j++) {
                System.out.printf("%4d", dp[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * Method to reconstruct the optimal bursting sequence
     */
    public List<Integer> getOptimalSequence(int[] nums) {
        int n = nums.length;
        int[] balloons = new int[n + 2];
        balloons[0] = balloons[n + 1] = 1;
        for (int i = 1; i <= n; i++) {
            balloons[i] = nums[i - 1];
        }
        
        int[][] dp = new int[n + 2][n + 2];
        int[][] choice = new int[n + 2][n + 2]; // Track optimal choice
        
        for (int len = 2; len <= n + 1; len++) {
            for (int left = 0; left <= n + 1 - len; left++) {
                int right = left + len;
                for (int k = left + 1; k < right; k++) {
                    int coins = balloons[left] * balloons[k] * balloons[right];
                    coins += dp[left][k] + dp[k][right];
                    if (coins > dp[left][right]) {
                        dp[left][right] = coins;
                        choice[left][right] = k;
                    }
                }
            }
        }
        
        List<Integer> sequence = new ArrayList<>();
        reconstructSequence(choice, 0, n + 1, sequence);
        return sequence;
    }
    
    private void reconstructSequence(int[][] choice, int left, int right, List<Integer> sequence) {
        if (left + 1 >= right) return;
        
        int k = choice[left][right];
        sequence.add(k - 1); // Convert back to original indexing
        
        reconstructSequence(choice, left, k, sequence);
        reconstructSequence(choice, k, right, sequence);
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        BurstBalloons_Leetcode312 solution = new BurstBalloons_Leetcode312();
        
        // Test case 1
        int[] nums1 = {3, 1, 5, 8};
        System.out.println("=== Burst Balloons Problem ===");
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Max Coins (Top-Down): " + solution.maxCoins_TopDown(nums1));
        System.out.println("Max Coins (Bottom-Up): " + solution.maxCoins_BottomUp(nums1));
        
        solution.printDPTable(nums1);
        
        List<Integer> sequence = solution.getOptimalSequence(nums1);
        System.out.println("Optimal bursting sequence (0-indexed): " + sequence);
        System.out.println();
        
        // Test case 2 with detailed explanation
        int[] nums2 = {1, 5};
        System.out.println("=== Simple Example ===");
        System.out.println("Max Coins: " + solution.maxCoins_Explained(nums2));
        System.out.println();
        
        // Test case 3
        int[] nums3 = {3, 1, 5};
        System.out.println("Input: " + Arrays.toString(nums3));
        System.out.println("Max Coins: " + solution.maxCoins_BottomUp(nums3));
        List<Integer> seq3 = solution.getOptimalSequence(nums3);
        System.out.println("Optimal sequence: " + seq3);
    }
}