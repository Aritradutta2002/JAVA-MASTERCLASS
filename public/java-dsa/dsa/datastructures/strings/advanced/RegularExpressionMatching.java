package dsa.datastructures.strings.advanced;
/**
 * REGULAR EXPRESSION MATCHING
 * LeetCode #10 - Hard
 * 
 * Companies: Facebook, Google, Amazon, Microsoft, Uber, Bloomberg
 * Frequency: VERY HIGH
 * 
 * Problem: Implement regex matching with '.' and '*'
 * '.' matches any single character
 * '*' matches zero or more of the preceding element
 * 
 * Key Technique: 2D DP or Recursion with Memoization
 */

import java.util.*;

public class RegularExpressionMatching {
    
    // APPROACH 1: 2D DP (Bottom-up)
    // Time: O(m*n), Space: O(m*n)
    public boolean isMatch(String s, String p) {
        int m = s.length();
        int n = p.length();
        
        boolean[][] dp = new boolean[m + 1][n + 1];
        dp[0][0] = true; // Empty matches empty
        
        // Handle patterns like a*, a*b*, a*b*c*
        for (int j = 2; j <= n; j++) {
            if (p.charAt(j - 1) == '*') {
                dp[0][j] = dp[0][j - 2];
            }
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                char sc = s.charAt(i - 1);
                char pc = p.charAt(j - 1);
                
                if (pc == '*') {
                    // Two cases:
                    // 1. Use * for zero occurrences: dp[i][j-2]
                    // 2. Use * for one or more: check if prev pattern char matches
                    char prevChar = p.charAt(j - 2);
                    dp[i][j] = dp[i][j - 2]; // Zero occurrence
                    
                    if (prevChar == '.' || prevChar == sc) {
                        dp[i][j] = dp[i][j] || dp[i - 1][j]; // One or more
                    }
                } else if (pc == '.' || pc == sc) {
                    dp[i][j] = dp[i - 1][j - 1];
                }
            }
        }
        
        return dp[m][n];
    }
    
    // APPROACH 2: Recursion with Memoization (Top-down)
    // Time: O(m*n), Space: O(m*n)
    private Boolean[][] memo;
    
    public boolean isMatch_Recursive(String s, String p) {
        memo = new Boolean[s.length() + 1][p.length() + 1];
        return dfs(s, p, 0, 0);
    }
    
    private boolean dfs(String s, String p, int i, int j) {
        if (j == p.length()) {
            return i == s.length();
        }
        
        if (memo[i][j] != null) {
            return memo[i][j];
        }
        
        boolean firstMatch = i < s.length() && 
                           (p.charAt(j) == '.' || p.charAt(j) == s.charAt(i));
        
        boolean result;
        
        if (j + 1 < p.length() && p.charAt(j + 1) == '*') {
            // Two choices: use * for zero or use * for one/more
            result = dfs(s, p, i, j + 2) || // Zero occurrence
                    (firstMatch && dfs(s, p, i + 1, j)); // One or more
        } else {
            result = firstMatch && dfs(s, p, i + 1, j + 1);
        }
        
        memo[i][j] = result;
        return result;
    }
    
    /**
     * FOLLOW-UP 1: Wildcard Matching
     * LeetCode #44 - Similar but with '?' and '*'
     * '*' matches any sequence (not just preceding char)
     */
    public boolean isWildcardMatch(String s, String p) {
        int m = s.length();
        int n = p.length();
        
        boolean[][] dp = new boolean[m + 1][n + 1];
        dp[0][0] = true;
        
        // Handle leading *
        for (int j = 1; j <= n; j++) {
            if (p.charAt(j - 1) == '*') {
                dp[0][j] = dp[0][j - 1];
            }
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                char sc = s.charAt(i - 1);
                char pc = p.charAt(j - 1);
                
                if (pc == '*') {
                    // Match empty or match one or more chars
                    dp[i][j] = dp[i][j - 1] || dp[i - 1][j];
                } else if (pc == '?' || pc == sc) {
                    dp[i][j] = dp[i - 1][j - 1];
                }
            }
        }
        
        return dp[m][n];
    }
    
    /**
     * FOLLOW-UP 2: String Matching with * only
     * Simplified version
     */
    public boolean matchWithStar(String s, String p) {
        int i = 0, j = 0;
        int starIdx = -1, match = 0;
        
        while (i < s.length()) {
            if (j < p.length() && (p.charAt(j) == '?' || p.charAt(j) == s.charAt(i))) {
                i++;
                j++;
            } else if (j < p.length() && p.charAt(j) == '*') {
                starIdx = j;
                match = i;
                j++;
            } else if (starIdx != -1) {
                j = starIdx + 1;
                match++;
                i = match;
            } else {
                return false;
            }
        }
        
        while (j < p.length() && p.charAt(j) == '*') {
            j++;
        }
        
        return j == p.length();
    }
    
    /**
     * HELPER: Visualize DP table
     */
    public void printDPTable(String s, String p) {
        int m = s.length();
        int n = p.length();
        
        boolean[][] dp = new boolean[m + 1][n + 1];
        dp[0][0] = true;
        
        for (int j = 2; j <= n; j++) {
            if (p.charAt(j - 1) == '*') {
                dp[0][j] = dp[0][j - 2];
            }
        }
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                char sc = s.charAt(i - 1);
                char pc = p.charAt(j - 1);
                
                if (pc == '*') {
                    char prevChar = p.charAt(j - 2);
                    dp[i][j] = dp[i][j - 2];
                    if (prevChar == '.' || prevChar == sc) {
                        dp[i][j] = dp[i][j] || dp[i - 1][j];
                    }
                } else if (pc == '.' || pc == sc) {
                    dp[i][j] = dp[i - 1][j - 1];
                }
            }
        }
        
        // Print table
        System.out.print("      ");
        for (char c : p.toCharArray()) {
            System.out.print(c + "  ");
        }
        System.out.println();
        
        for (int i = 0; i <= m; i++) {
            if (i == 0) System.out.print("''  ");
            else System.out.print(s.charAt(i - 1) + "   ");
            
            for (int j = 0; j <= n; j++) {
                System.out.print((dp[i][j] ? "T" : "F") + "  ");
            }
            System.out.println();
        }
    }
    
    public static void main(String[] args) {
        RegularExpressionMatching sol = new RegularExpressionMatching();
        
        // Test 1: Basic matching
        System.out.println("=== Test 1: Basic Cases ===");
        System.out.println("'aa', 'a': " + sol.isMatch("aa", "a"));
        System.out.println("'aa', 'a*': " + sol.isMatch("aa", "a*"));
        System.out.println("'ab', '.*': " + sol.isMatch("ab", ".*"));
        System.out.println();
        
        // Test 2: Complex patterns
        System.out.println("=== Test 2: Complex Patterns ===");
        System.out.println("'aab', 'c*a*b': " + sol.isMatch("aab", "c*a*b"));
        System.out.println("'mississippi', 'mis*is*p*.': " + sol.isMatch("mississippi", "mis*is*p*."));
        System.out.println("'aaa', 'a*a': " + sol.isMatch("aaa", "a*a"));
        System.out.println();
        
        // Test 3: Recursive approach
        System.out.println("=== Test 3: Recursive Approach ===");
        System.out.println("'aa', 'a': " + sol.isMatch_Recursive("aa", "a"));
        System.out.println("'aa', 'a*': " + sol.isMatch_Recursive("aa", "a*"));
        System.out.println();
        
        // Test 4: Wildcard matching
        System.out.println("=== Test 4: Wildcard Matching ===");
        System.out.println("'aa', '*': " + sol.isWildcardMatch("aa", "*"));
        System.out.println("'adceb', '*a*b': " + sol.isWildcardMatch("adceb", "*a*b"));
        System.out.println("'acdcb', 'a*c?b': " + sol.isWildcardMatch("acdcb", "a*c?b"));
        System.out.println();
        
        // Test 5: DP Table visualization
        System.out.println("=== Test 5: DP Table for 'aab' vs 'c*a*b' ===");
        sol.printDPTable("aab", "c*a*b");
        System.out.println();
        
        // Test 6: Edge cases
        System.out.println("=== Test 6: Edge Cases ===");
        System.out.println("'', '': " + sol.isMatch("", ""));
        System.out.println("'', 'a*': " + sol.isMatch("", "a*"));
        System.out.println("'a', '': " + sol.isMatch("a", ""));
        System.out.println();
        
        // Explain key patterns
        System.out.println("=== KEY PATTERNS EXPLAINED ===");
        System.out.println("Pattern 'a*':");
        System.out.println("  - Can match '', 'a', 'aa', 'aaa', ...");
        System.out.println("Pattern '.*':");
        System.out.println("  - Can match any string");
        System.out.println("Pattern 'a*b':");
        System.out.println("  - Matches 'b', 'ab', 'aab', 'aaab', ...");
        System.out.println("Pattern 'c*a*b':");
        System.out.println("  - Matches 'b', 'ab', 'aab', 'cb', 'cab', ...");
        System.out.println();
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. VERY HARD problem - Don't panic if stuck!");
        System.out.println("2. Key insight: Handle '*' by considering 0 or more");
        System.out.println("3. dp[i][j] = does s[0..i-1] match p[0..j-1]?");
        System.out.println("4. For '*': Check dp[i][j-2] (zero) OR dp[i-1][j] (one+)");
        System.out.println("5. Facebook loves this - practice extensively");
        System.out.println("6. Similar: Wildcard Matching (#44), simpler version");
        System.out.println("7. Recursion easier to understand, DP more efficient");
    }
}

