package dsa.datastructures.strings.advanced;
/**
 * EDIT DISTANCE (Levenshtein Distance)
 * LeetCode #72 - Hard
 * 
 * Companies: Google, Facebook, Amazon, Microsoft, Bloomberg, Uber
 * Frequency: VERY HIGH
 * 
 * Problem: Min operations to convert word1 to word2
 * Operations: Insert, Delete, Replace
 * 
 * Key Technique: 2D DP
 * dp[i][j] = min operations to convert word1[0..i-1] to word2[0..j-1]
 */

import java.util.*;

public class EditDistance {
    
    // APPROACH 1: 2D DP (Classic)
    // Time: O(m*n), Space: O(m*n)
    public int minDistance(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        // Base cases
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i; // Delete all from word1
        }
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j; // Insert all from word2
        }
        
        // Fill DP table
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1]; // No operation needed
                } else {
                    dp[i][j] = 1 + Math.min(
                        dp[i - 1][j],      // Delete from word1
                        Math.min(
                            dp[i][j - 1],  // Insert to word1
                            dp[i - 1][j - 1] // Replace
                        )
                    );
                }
            }
        }
        
        return dp[m][n];
    }
    
    // APPROACH 2: Space Optimized DP
    // Time: O(m*n), Space: O(n)
    public int minDistance_Optimized(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        
        for (int i = 1; i <= m; i++) {
            curr[0] = i;
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j], Math.min(curr[j - 1], prev[j - 1]));
                }
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        
        return prev[n];
    }
    
    /**
     * FOLLOW-UP 1: Delete Operation for Two Strings
     * LeetCode #583 - Only delete allowed
     */
    public int minDeleteOperations(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], dp[i][j - 1]);
                }
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * FOLLOW-UP 2: One Edit Distance
     * LeetCode #161 - Check if strings are one edit apart
     */
    public boolean isOneEditDistance(String s, String t) {
        int m = s.length();
        int n = t.length();
        
        if (Math.abs(m - n) > 1) return false;
        if (s.equals(t)) return false;
        
        int i = 0, j = 0;
        int edits = 0;
        
        while (i < m && j < n) {
            if (s.charAt(i) != t.charAt(j)) {
                edits++;
                if (edits > 1) return false;
                
                if (m > n) {
                    i++;
                } else if (n > m) {
                    j++;
                } else {
                    i++;
                    j++;
                }
            } else {
                i++;
                j++;
            }
        }
        
        return edits == 1 || i != m || j != n;
    }
    
    /**
     * FOLLOW-UP 3: Minimum ASCII Delete Sum
     * LeetCode #712 - Minimize ASCII sum of deleted chars
     */
    public int minimumDeleteSum(String s1, String s2) {
        int m = s1.length();
        int n = s2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            dp[i][0] = dp[i - 1][0] + s1.charAt(i - 1);
        }
        
        for (int j = 1; j <= n; j++) {
            dp[0][j] = dp[0][j - 1] + s2.charAt(j - 1);
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (s1.charAt(i - 1) == s2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j] + s1.charAt(i - 1),
                        dp[i][j - 1] + s2.charAt(j - 1)
                    );
                }
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * HELPER: Print actual edit operations
     */
    public List<String> getEditOperations(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        int[][] dp = new int[m + 1][n + 1];
        
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        
        // Backtrack to find operations
        List<String> ops = new ArrayList<>();
        int i = m, j = n;
        
        while (i > 0 || j > 0) {
            if (i == 0) {
                ops.add("Insert '" + word2.charAt(j - 1) + "'");
                j--;
            } else if (j == 0) {
                ops.add("Delete '" + word1.charAt(i - 1) + "'");
                i--;
            } else if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                i--;
                j--;
            } else {
                int minOp = Math.min(dp[i - 1][j], Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                
                if (minOp == dp[i - 1][j - 1]) {
                    ops.add("Replace '" + word1.charAt(i - 1) + "' with '" + word2.charAt(j - 1) + "'");
                    i--;
                    j--;
                } else if (minOp == dp[i - 1][j]) {
                    ops.add("Delete '" + word1.charAt(i - 1) + "'");
                    i--;
                } else {
                    ops.add("Insert '" + word2.charAt(j - 1) + "'");
                    j--;
                }
            }
        }
        
        Collections.reverse(ops);
        return ops;
    }
    
    public static void main(String[] args) {
        EditDistance sol = new EditDistance();
        
        // Test 1: Basic case
        String word1 = "horse";
        String word2 = "ros";
        System.out.println("=== Test 1: Basic ===");
        System.out.println("'" + word1 + "' → '" + word2 + "'");
        System.out.println("Min edits: " + sol.minDistance(word1, word2));
        System.out.println("Operations: " + sol.getEditOperations(word1, word2));
        System.out.println("Expected: 3 (replace 'h' → 'r', remove 'r', remove 'e')\n");
        
        // Test 2: Complete transformation
        word1 = "intention";
        word2 = "execution";
        System.out.println("=== Test 2: Complex ===");
        System.out.println("'" + word1 + "' → '" + word2 + "'");
        System.out.println("Min edits: " + sol.minDistance(word1, word2));
        System.out.println("Optimized: " + sol.minDistance_Optimized(word1, word2));
        System.out.println("Operations: " + sol.getEditOperations(word1, word2) + "\n");
        
        // Test 3: Empty strings
        System.out.println("=== Test 3: Edge Cases ===");
        System.out.println("'' → 'abc': " + sol.minDistance("", "abc"));
        System.out.println("'abc' → '': " + sol.minDistance("abc", ""));
        System.out.println("'abc' → 'abc': " + sol.minDistance("abc", "abc") + "\n");
        
        // Test 4: One Edit Distance
        System.out.println("=== Test 4: One Edit Distance ===");
        System.out.println("'ab', 'acb': " + sol.isOneEditDistance("ab", "acb"));
        System.out.println("'cab', 'ad': " + sol.isOneEditDistance("cab", "ad"));
        System.out.println("'1203', '1213': " + sol.isOneEditDistance("1203", "1213") + "\n");
        
        // Test 5: Delete Operations
        System.out.println("=== Test 5: Delete Operations Only ===");
        word1 = "sea";
        word2 = "eat";
        System.out.println("'" + word1 + "' → '" + word2 + "'");
        System.out.println("Min deletes: " + sol.minDeleteOperations(word1, word2));
        System.out.println("Expected: 2 ('s' from word1, 't' from word2)\n");
        
        // Test 6: ASCII Delete Sum
        System.out.println("=== Test 6: Minimum ASCII Delete Sum ===");
        word1 = "delete";
        word2 = "leet";
        System.out.println("'" + word1 + "' → '" + word2 + "'");
        System.out.println("Min ASCII sum: " + sol.minimumDeleteSum(word1, word2) + "\n");
        
        // Explain DP table
        System.out.println("=== DP TABLE EXPLAINED ===");
        System.out.println("For 'horse' → 'ros':");
        System.out.println("    ''  r   o   s");
        System.out.println("''   0   1   2   3");
        System.out.println("h    1   1   2   3");
        System.out.println("o    2   2   1   2");
        System.out.println("r    3   2   2   2");
        System.out.println("s    4   3   3   2");
        System.out.println("e    5   4   4   3\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. CLASSIC DP problem - Must know for FAANG!");
        System.out.println("2. Three operations: Insert, Delete, Replace");
        System.out.println("3. If chars match: dp[i][j] = dp[i-1][j-1]");
        System.out.println("4. Else: 1 + min(insert, delete, replace)");
        System.out.println("5. Space can be optimized to O(n)");
        System.out.println("6. Google asks: Print actual operations");
        System.out.println("7. Used in: Spell checkers, DNA sequence alignment");
    }
}

