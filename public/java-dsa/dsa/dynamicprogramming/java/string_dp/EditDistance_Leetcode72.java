package JAVA_DSA.DYNAMIC_PROGRAMMING.java.string_dp;

/**
 * EDIT DISTANCE (Levenshtein Distance) - Leetcode 72
 * Difficulty: Hard
 * Companies: Google, Amazon, Microsoft, Facebook, Bloomberg, Uber, Apple
 * 
 * PROBLEM:
 * Given two strings word1 and word2, return the minimum number of operations
 * required to convert word1 to word2.
 * 
 * You have 3 operations permitted on a word:
 * 1. Insert a character
 * 2. Delete a character
 * 3. Replace a character
 * 
 * EXAMPLES:
 * Input: word1 = "horse", word2 = "ros"
 * Output: 3
 * Explanation: 
 *   horse -> rorse (replace 'h' with 'r')
 *   rorse -> rose (remove 'r')
 *   rose -> ros (remove 'e')
 * 
 * Input: word1 = "intention", word2 = "execution"
 * Output: 5
 * 
 * PATTERN: 2D String DP
 * This is a CLASSIC DP problem - Must know for MAANG interviews!
 */

public class EditDistance_Leetcode72 {
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(3^(m+n)) - 3 branches at each step
    // Space: O(m+n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * At each position, we have a choice:
     * 
     * If characters match:
     *   - No operation needed, move to next characters
     * 
     * If characters don't match:
     *   - INSERT: Add char to word1, move pointer in word2
     *   - DELETE: Remove char from word1, move pointer in word1
     *   - REPLACE: Change char in word1, move both pointers
     * 
     * Try all 3 and pick minimum!
     */
    public int minDistance_Recursive(String word1, String word2) {
        return minDistRec(word1, word2, 0, 0);
    }
    
    private int minDistRec(String word1, String word2, int i, int j) {
        // Base cases
        if (i == word1.length()) return word2.length() - j; // Insert remaining
        if (j == word2.length()) return word1.length() - i; // Delete remaining
        
        // If characters match, no operation needed
        if (word1.charAt(i) == word2.charAt(j)) {
            return minDistRec(word1, word2, i + 1, j + 1);
        }
        
        // If don't match, try all 3 operations
        int insert = 1 + minDistRec(word1, word2, i, j + 1);      // Insert char
        int delete = 1 + minDistRec(word1, word2, i + 1, j);      // Delete char
        int replace = 1 + minDistRec(word1, word2, i + 1, j + 1); // Replace char
        
        return Math.min(insert, Math.min(delete, replace));
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(m*n) - Each state calculated once
    // Space: O(m*n) - Memo array + recursion stack
    /**
     * BEGINNER'S EXPLANATION:
     * Same as recursion but cache results in 2D array.
     * memo[i][j] = min operations to convert word1[i:] to word2[j:]
     */
    public int minDistance_Memoization(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        Integer[][] memo = new Integer[m + 1][n + 1];
        return minDistMemo(word1, word2, 0, 0, memo);
    }
    
    private int minDistMemo(String word1, String word2, int i, int j, Integer[][] memo) {
        // Base cases
        if (i == word1.length()) return word2.length() - j;
        if (j == word2.length()) return word1.length() - i;
        
        // Check memo
        if (memo[i][j] != null) return memo[i][j];
        
        // If match
        if (word1.charAt(i) == word2.charAt(j)) {
            memo[i][j] = minDistMemo(word1, word2, i + 1, j + 1, memo);
        } else {
            // Try all 3 operations
            int insert = 1 + minDistMemo(word1, word2, i, j + 1, memo);
            int delete = 1 + minDistMemo(word1, word2, i + 1, j, memo);
            int replace = 1 + minDistMemo(word1, word2, i + 1, j + 1, memo);
            memo[i][j] = Math.min(insert, Math.min(delete, replace));
        }
        
        return memo[i][j];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(m*n)
    // Space: O(m*n)
    /**
     * BEGINNER'S EXPLANATION:
     * Build a 2D table where dp[i][j] represents:
     * "Minimum operations to convert first i characters of word1 to first j characters of word2"
     * 
     * DP Table for word1="horse", word2="ros":
     * 
     *       ""  r  o  s
     *    "" 0   1  2  3
     *    h  1   1  2  3
     *    o  2   2  1  2
     *    r  3   2  2  2
     *    s  4   3  3  2
     *    e  5   4  4  3
     *           ↑
     *    Answer = 3
     * 
     * How to fill each cell:
     * 
     * If word1[i-1] == word2[j-1]:
     *    dp[i][j] = dp[i-1][j-1]  (no operation needed)
     * 
     * Else:
     *    dp[i][j] = 1 + min(
     *        dp[i-1][j],    // DELETE from word1
     *        dp[i][j-1],    // INSERT into word1
     *        dp[i-1][j-1]   // REPLACE in word1
     *    )
     */
    public int minDistance(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        // Create DP table
        int[][] dp = new int[m + 1][n + 1];
        
        // Base cases
        // Converting empty string to word2
        for (int j = 0; j <= n; j++) {
            dp[0][j] = j; // Need j insertions
        }
        
        // Converting word1 to empty string
        for (int i = 0; i <= m; i++) {
            dp[i][0] = i; // Need i deletions
        }
        
        // Fill DP table
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                // If characters match
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    // Try all 3 operations and pick minimum
                    int delete = dp[i - 1][j];     // Delete from word1
                    int insert = dp[i][j - 1];     // Insert to word1
                    int replace = dp[i - 1][j - 1]; // Replace in word1
                    
                    dp[i][j] = 1 + Math.min(delete, Math.min(insert, replace));
                }
            }
        }
        
        return dp[m][n];
    }
    
    
    // ========== APPROACH 4: SPACE OPTIMIZED ==========
    // Time: O(m*n)
    // Space: O(min(m,n)) - Only need 2 rows
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous row to compute current row.
     * So instead of full 2D array, use 2 1D arrays!
     */
    public int minDistance_SpaceOptimized(String word1, String word2) {
        int m = word1.length();
        int n = word2.length();
        
        // Make sure word2 is shorter (for space efficiency)
        if (m < n) {
            return minDistance_SpaceOptimized(word2, word1);
        }
        
        // Only need 2 rows
        int[] prev = new int[n + 1];
        int[] curr = new int[n + 1];
        
        // Base case
        for (int j = 0; j <= n; j++) {
            prev[j] = j;
        }
        
        // Fill row by row
        for (int i = 1; i <= m; i++) {
            curr[0] = i; // Base case for each row
            
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    curr[j] = prev[j - 1];
                } else {
                    curr[j] = 1 + Math.min(prev[j],           // Delete
                                           Math.min(curr[j - 1], // Insert
                                                   prev[j - 1])); // Replace
                }
            }
            
            // Swap arrays
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }
        
        return prev[n];
    }
    
    
    // ========== HELPER: Print Operations (Bonus) ==========
    /**
     * Backtracks through DP table to print actual operations
     * Great to show in interviews for extra points!
     */
    public void printOperations(String word1, String word2) {
        int m = word1.length(), n = word2.length();
        int[][] dp = new int[m + 1][n + 1];
        
        // Fill DP table (same as before)
        for (int j = 0; j <= n; j++) dp[0][j] = j;
        for (int i = 0; i <= m; i++) dp[i][0] = i;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                    dp[i][j] = dp[i - 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i - 1][j], 
                                           Math.min(dp[i][j - 1], dp[i - 1][j - 1]));
                }
            }
        }
        
        // Backtrack to find operations
        System.out.println("Operations needed: " + dp[m][n]);
        int i = m, j = n;
        while (i > 0 && j > 0) {
            if (word1.charAt(i - 1) == word2.charAt(j - 1)) {
                System.out.println("Match '" + word1.charAt(i - 1) + "'");
                i--; j--;
            } else {
                int replace = dp[i - 1][j - 1];
                int delete = dp[i - 1][j];
                int insert = dp[i][j - 1];
                
                if (replace <= delete && replace <= insert) {
                    System.out.println("Replace '" + word1.charAt(i - 1) + 
                                     "' with '" + word2.charAt(j - 1) + "'");
                    i--; j--;
                } else if (delete <= insert) {
                    System.out.println("Delete '" + word1.charAt(i - 1) + "'");
                    i--;
                } else {
                    System.out.println("Insert '" + word2.charAt(j - 1) + "'");
                    j--;
                }
            }
        }
        while (i > 0) {
            System.out.println("Delete '" + word1.charAt(i - 1) + "'");
            i--;
        }
        while (j > 0) {
            System.out.println("Insert '" + word2.charAt(j - 1) + "'");
            j--;
        }
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        EditDistance_Leetcode72 solution = new EditDistance_Leetcode72();
        
        // Test case 1
        System.out.println("Test 1: word1='horse', word2='ros'");
        System.out.println("Result: " + solution.minDistance("horse", "ros")); // 3
        solution.printOperations("horse", "ros");
        System.out.println();
        
        // Test case 2
        System.out.println("Test 2: word1='intention', word2='execution'");
        System.out.println("Result: " + solution.minDistance("intention", "execution")); // 5
        System.out.println();
        
        // Test case 3
        System.out.println("Test 3: word1='', word2='abc'");
        System.out.println("Result: " + solution.minDistance("", "abc")); // 3
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. THIS IS A MUST-KNOW PROBLEM:
 *    - One of top 10 DP problems for interviews
 *    - Tests 2D DP understanding
 *    - Real-world applications (spell check, DNA sequencing, diff tools)
 * 
 * 2. RECURRENCE RELATION (Memorize this!):
 *    If word1[i] == word2[j]:
 *        dp[i][j] = dp[i-1][j-1]
 *    Else:
 *        dp[i][j] = 1 + min(
 *            dp[i-1][j],    // DELETE
 *            dp[i][j-1],    // INSERT
 *            dp[i-1][j-1]   // REPLACE
 *        )
 * 
 * 3. VISUALIZATION HELPS:
 *    - Always draw the DP table for small example
 *    - Show how each cell is computed
 *    - Demonstrate base cases clearly
 * 
 * 4. FOLLOW-UP QUESTIONS:
 *    - What if operations have different costs? → Weighted edit distance
 *    - Find the actual operations? → Backtrack through DP table
 *    - Check if strings are similar? → Compare distance to threshold
 *    - Multiple strings? → Generalize to 3D DP (very advanced)
 * 
 * 5. RELATED PROBLEMS:
 *    - Leetcode 72: Edit Distance (this)
 *    - Leetcode 583: Delete Operation for Two Strings
 *    - Leetcode 712: Minimum ASCII Delete Sum
 *    - Leetcode 1143: Longest Common Subsequence
 *    - Leetcode 161: One Edit Distance
 * 
 * 6. INTERVIEW STRATEGY:
 *    - Start with recursive explanation
 *    - Draw small DP table example
 *    - Explain recurrence relation clearly
 *    - Code tabulation approach
 *    - Mention space optimization if time permits
 * 
 * 7. COMMON MISTAKES:
 *    - Off-by-one errors with string indices
 *    - Forgetting base cases (empty strings)
 *    - Confusing i-1 with i in dp array
 *    - Not handling edge cases (empty strings)
 */

