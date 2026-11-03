package JAVA_DSA.DYNAMIC_PROGRAMMING.java.classic_problems;

import java.util.*;

/**
 * MATRIX CHAIN MULTIPLICATION
 * Difficulty: Hard
 * Pattern: Interval DP / MCM (Matrix Chain Multiplication)
 * 
 * PROBLEM:
 * Given a sequence of matrices, find the most efficient way to multiply these matrices.
 * The problem is not to perform the multiplication, but to determine the order of 
 * multiplications that minimizes the number of scalar multiplications.
 * 
 * Given array p[] which represents the chain of matrices such that the ith matrix 
 * has dimensions p[i-1] x p[i].
 * 
 * EXAMPLES:
 * Input: p[] = {1, 2, 3, 4}
 * Matrices: A1(1x2), A2(2x3), A3(3x4)
 * Output: 18
 * Explanation: 
 * ((A1*A2)*A3) = (1*2*3) + (1*3*4) = 6 + 12 = 18
 * (A1*(A2*A3)) = (2*3*4) + (1*2*4) = 24 + 8 = 32
 * 
 * KEY INSIGHT:
 * For matrices from i to j, try all possible split points k.
 * Cost = cost(i,k) + cost(k+1,j) + p[i-1]*p[k]*p[j]
 */
public class MatrixChainMultiplication {
    
    /**
     * APPROACH 1: Top-Down DP with Memoization
     * Time: O(n³), Space: O(n²)
     */
    public int matrixChainOrder_TopDown(int[] p) {
        int n = p.length;
        Integer[][] memo = new Integer[n][n];
        return mcm(p, 1, n - 1, memo);
    }
    
    private int mcm(int[] p, int i, int j, Integer[][] memo) {
        if (i >= j) return 0; // Single matrix or invalid range
        
        if (memo[i][j] != null) {
            return memo[i][j];
        }
        
        int minCost = Integer.MAX_VALUE;
        
        // Try all possible split points k
        for (int k = i; k < j; k++) {
            int cost = mcm(p, i, k, memo) + mcm(p, k + 1, j, memo) + p[i - 1] * p[k] * p[j];
            minCost = Math.min(minCost, cost);
        }
        
        memo[i][j] = minCost;
        return minCost;
    }
    
    /**
     * APPROACH 2: Bottom-Up DP (Standard)
     * Time: O(n³), Space: O(n²)
     */
    public int matrixChainOrder_BottomUp(int[] p) {
        int n = p.length;
        // dp[i][j] = minimum cost to multiply matrices from i to j
        int[][] dp = new int[n][n];
        
        // Length of chain
        for (int len = 2; len < n; len++) {
            for (int i = 1; i < n - len + 1; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                
                // Try all split points k
                for (int k = i; k < j; k++) {
                    int cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j];
                    dp[i][j] = Math.min(dp[i][j], cost);
                }
            }
        }
        
        return dp[1][n - 1];
    }
    
    /**
     * APPROACH 3: Print Optimal Parenthesization
     * Time: O(n³), Space: O(n²)
     */
    public String getOptimalParenthesization(int[] p) {
        int n = p.length;
        int[][] dp = new int[n][n];
        int[][] split = new int[n][n]; // Store optimal split points
        
        for (int len = 2; len < n; len++) {
            for (int i = 1; i < n - len + 1; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                
                for (int k = i; k < j; k++) {
                    int cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j];
                    if (cost < dp[i][j]) {
                        dp[i][j] = cost;
                        split[i][j] = k;
                    }
                }
            }
        }
        
        return constructParentheses(split, 1, n - 1);
    }
    
    private String constructParentheses(int[][] split, int i, int j) {
        if (i == j) {
            return "M" + i;
        }
        
        int k = split[i][j];
        return "(" + constructParentheses(split, i, k) + " × " + 
               constructParentheses(split, k + 1, j) + ")";
    }
    
    /**
     * APPROACH 4: Detailed Step-by-Step Solution
     * Shows the DP table construction process
     */
    public int matrixChainOrder_Detailed(int[] p) {
        int n = p.length;
        int[][] dp = new int[n][n];
        int[][] split = new int[n][n];
        
        System.out.println("=== Matrix Chain Multiplication - Step by Step ===");
        System.out.println("Matrix dimensions: " + Arrays.toString(p));
        
        // Print matrix information
        System.out.println("Matrices:");
        for (int i = 1; i < n; i++) {
            System.out.println("  M" + i + ": " + p[i-1] + " × " + p[i]);
        }
        System.out.println();
        
        for (int len = 2; len < n; len++) {
            System.out.println("Chain length " + len + ":");
            
            for (int i = 1; i < n - len + 1; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                
                System.out.printf("  M%d to M%d: ", i, j);
                
                for (int k = i; k < j; k++) {
                    int cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j];
                    
                    System.out.printf("k=%d: %d + %d + %d*%d*%d = %d; ", 
                        k, dp[i][k], dp[k + 1][j], p[i-1], p[k], p[j], cost);
                    
                    if (cost < dp[i][j]) {
                        dp[i][j] = cost;
                        split[i][j] = k;
                    }
                }
                
                System.out.printf("min = %d (split at %d)\n", dp[i][j], split[i][j]);
            }
            System.out.println();
        }
        
        printDPTable(dp, n);
        
        String parentheses = constructParentheses(split, 1, n - 1);
        System.out.println("Optimal parenthesization: " + parentheses);
        
        return dp[1][n - 1];
    }
    
    /**
     * Helper method to print DP table
     */
    private void printDPTable(int[][] dp, int n) {
        System.out.println("DP Table:");
        System.out.print("   ");
        for (int j = 1; j < n; j++) {
            System.out.printf("%5d", j);
        }
        System.out.println();
        
        for (int i = 1; i < n; i++) {
            System.out.printf("%2d ", i);
            for (int j = 1; j < n; j++) {
                if (j >= i) {
                    System.out.printf("%5d", dp[i][j]);
                } else {
                    System.out.print("    -");
                }
            }
            System.out.println();
        }
        System.out.println();
    }
    
    /**
     * VARIANT: Count number of ways to parenthesize
     */
    public long countWaysToParenthesize(int n) {
        if (n <= 1) return 1;
        
        // This is actually Catalan number C(n-1)
        long[] catalan = new long[n];
        catalan[0] = 1;
        if (n > 1) catalan[1] = 1;
        
        for (int i = 2; i < n; i++) {
            catalan[i] = 0;
            for (int j = 0; j < i; j++) {
                catalan[i] += catalan[j] * catalan[i - 1 - j];
            }
        }
        
        return catalan[n - 1];
    }
    
    /**
     * VARIANT: Matrix Chain with different cost functions
     */
    public int matrixChainWithDifferentCost(int[] p, String costType) {
        int n = p.length;
        int[][] dp = new int[n][n];
        
        for (int len = 2; len < n; len++) {
            for (int i = 1; i < n - len + 1; i++) {
                int j = i + len - 1;
                dp[i][j] = Integer.MAX_VALUE;
                
                for (int k = i; k < j; k++) {
                    int cost;
                    switch (costType) {
                        case "multiplications":
                            cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j];
                            break;
                        case "additions":
                            cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[j] * (p[k] - 1);
                            break;
                        default:
                            cost = dp[i][k] + dp[k + 1][j] + p[i - 1] * p[k] * p[j];
                    }
                    dp[i][j] = Math.min(dp[i][j], cost);
                }
            }
        }
        
        return dp[1][n - 1];
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        MatrixChainMultiplication solution = new MatrixChainMultiplication();
        
        // Test case 1
        int[] p1 = {1, 2, 3, 4};
        System.out.println("=== Matrix Chain Multiplication ===");
        System.out.println("Dimensions: " + Arrays.toString(p1));
        System.out.println("Min cost (Top-Down): " + solution.matrixChainOrder_TopDown(p1));
        System.out.println("Min cost (Bottom-Up): " + solution.matrixChainOrder_BottomUp(p1));
        System.out.println("Optimal parenthesization: " + solution.getOptimalParenthesization(p1));
        System.out.println();
        
        // Test case 2 with detailed explanation
        int[] p2 = {1, 2, 3, 4, 5};
        System.out.println("Detailed solution for: " + Arrays.toString(p2));
        int result = solution.matrixChainOrder_Detailed(p2);
        System.out.println("Final result: " + result);
        System.out.println();
        
        // Test case 3 - Larger example
        int[] p3 = {5, 4, 6, 2, 7};
        System.out.println("Example 3: " + Arrays.toString(p3));
        System.out.println("Min cost: " + solution.matrixChainOrder_BottomUp(p3));
        System.out.println("Optimal parenthesization: " + solution.getOptimalParenthesization(p3));
        System.out.println();
        
        // Variants
        System.out.println("=== Variants ===");
        System.out.println("Ways to parenthesize 4 matrices: " + solution.countWaysToParenthesize(4));
        System.out.println("Ways to parenthesize 5 matrices: " + solution.countWaysToParenthesize(5));
        
        // Different cost functions
        System.out.println("Cost with multiplications: " + solution.matrixChainWithDifferentCost(p1, "multiplications"));
        System.out.println("Cost with additions: " + solution.matrixChainWithDifferentCost(p1, "additions"));
    }
}