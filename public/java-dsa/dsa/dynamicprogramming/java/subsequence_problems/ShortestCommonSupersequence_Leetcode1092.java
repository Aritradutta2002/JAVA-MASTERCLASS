package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;

/**
 * SHORTEST COMMON SUPERSEQUENCE - Leetcode 1092
 * Difficulty: Hard
 * Pattern: LCS (Longest Common Subsequence) Extension
 * 
 * PROBLEM:
 * Given two strings str1 and str2, return the shortest string that has both str1 and str2 as subsequences.
 * If multiple answers exist, you may return any of them.
 * 
 * EXAMPLES:
 * Input: str1 = "abac", str2 = "cab"
 * Output: "cabac"
 * Explanation: 
 * str1 = "abac" is a subsequence of "cabac" because we can delete the first "c".
 * str2 = "cab" is a subsequence of "cabac" because we can delete the last "ac".
 * 
 * KEY INSIGHT:
 * Length of SCS = len(str1) + len(str2) - len(LCS)
 * We need to construct the actual string, not just find length.
 */
public class ShortestCommonSupersequence_Leetcode1092 {
    
    /**
     * APPROACH 1: DP + Backtracking to construct string
     * Time: O(m * n), Space: O(m * n)
     */
    public String shortestCommonSupersequence(String str1, String str2) {
        int m = str1.length(), n = str2.length();
        
        // Step 1: Build LCS DP table
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        
        // Step 2: Backtrack to construct SCS
        StringBuilder result = new StringBuilder();
        int i = m, j = n;
        
        while (i > 0 && j > 0) {
            if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                // Common character - add once
                result.append(str1.charAt(i - 1));
                i--;
                j--;
            } else if (dp[i - 1][j] > dp[i][j - 1]) {
                // Character from str1
                result.append(str1.charAt(i - 1));
                i--;
            } else {
                // Character from str2
                result.append(str2.charAt(j - 1));
                j--;
            }
        }
        
        // Add remaining characters
        while (i > 0) {
            result.append(str1.charAt(i - 1));
            i--;
        }
        while (j > 0) {
            result.append(str2.charAt(j - 1));
            j--;
        }
        
        return result.reverse().toString();
    }
    
    /**
     * APPROACH 2: Space Optimized (for length only)
     * Time: O(m * n), Space: O(min(m, n))
     */
    public int shortestCommonSupersequenceLength(String str1, String str2) {
        int lcsLength = longestCommonSubsequenceLength(str1, str2);
        return str1.length() + str2.length() - lcsLength;
    }
    
    private int longestCommonSubsequenceLength(String text1, String text2) {
        int m = text1.length(), n = text2.length();
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (text1.charAt(i - 1) == text2.charAt(j - 1)) {
                    curr[j] = prev[j - 1] + 1;
                } else {
                    curr[j] = Math.max(prev[j], curr[j - 1]);
                }
            }
            prev = curr.clone();
        }
        
        return curr[n];
    }
    
    /**
     * APPROACH 3: Recursive with Memoization (for understanding)
     * Time: O(m * n), Space: O(m * n)
     */
    public String shortestCommonSupersequence_Recursive(String str1, String str2) {
        String[][] memo = new String[str1.length()][str2.length()];
        return helper(str1, str2, 0, 0, memo);
    }
    
    private String helper(String str1, String str2, int i, int j, String[][] memo) {
        // Base cases
        if (i == str1.length()) {
            return str2.substring(j);
        }
        if (j == str2.length()) {
            return str1.substring(i);
        }
        
        if (memo[i][j] != null) {
            return memo[i][j];
        }
        
        String result;
        if (str1.charAt(i) == str2.charAt(j)) {
            // Common character
            result = str1.charAt(i) + helper(str1, str2, i + 1, j + 1, memo);
        } else {
            // Try both options and pick shorter
            String option1 = str1.charAt(i) + helper(str1, str2, i + 1, j, memo);
            String option2 = str2.charAt(j) + helper(str1, str2, i, j + 1, memo);
            result = option1.length() <= option2.length() ? option1 : option2;
        }
        
        memo[i][j] = result;
        return result;
    }
    
    /**
     * Helper method to print DP table for visualization
     */
    public void printLCSTable(String str1, String str2) {
        int m = str1.length(), n = str2.length();
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (str1.charAt(i - 1) == str2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                } else {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        
        System.out.println("LCS DP Table:");
        System.out.print("    ");
        for (char c : str2.toCharArray()) {
            System.out.print(c + " ");
        }
        System.out.println();
        
        for (int i = 0; i <= m; i++) {
            if (i == 0) System.out.print("  ");
            else System.out.print(str1.charAt(i - 1) + " ");
            
            for (int j = 0; j <= n; j++) {
                System.out.print(dp[i][j] + " ");
            }
            System.out.println();
        }
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        ShortestCommonSupersequence_Leetcode1092 solution = new ShortestCommonSupersequence_Leetcode1092();
        
        System.out.println("=== Shortest Common Supersequence ===");
        
        // Test case 1
        String str1 = "abac";
        String str2 = "cab";
        System.out.println("String 1: " + str1);
        System.out.println("String 2: " + str2);
        System.out.println("SCS: " + solution.shortestCommonSupersequence(str1, str2));
        System.out.println("SCS Length: " + solution.shortestCommonSupersequenceLength(str1, str2));
        solution.printLCSTable(str1, str2);
        System.out.println();
        
        // Test case 2
        str1 = "aaaaaaaa";
        str2 = "aaaa";
        System.out.println("String 1: " + str1);
        System.out.println("String 2: " + str2);
        System.out.println("SCS: " + solution.shortestCommonSupersequence(str1, str2));
        System.out.println();
        
        // Test case 3 - Demonstrate step by step
        str1 = "abac";
        str2 = "cab";
        System.out.println("=== Step by Step Analysis ===");
        System.out.println("String 1: " + str1 + " (length: " + str1.length() + ")");
        System.out.println("String 2: " + str2 + " (length: " + str2.length() + ")");
        
        int lcsLen = solution.longestCommonSubsequenceLength(str1, str2);
        int scsLen = solution.shortestCommonSupersequenceLength(str1, str2);
        
        System.out.println("LCS Length: " + lcsLen);
        System.out.println("SCS Length: " + scsLen);
        System.out.println("Formula: " + str1.length() + " + " + str2.length() + " - " + lcsLen + " = " + scsLen);
        System.out.println("SCS String: " + solution.shortestCommonSupersequence(str1, str2));
    }
}