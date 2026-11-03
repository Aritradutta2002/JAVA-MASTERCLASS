package JAVA_DSA.DYNAMIC_PROGRAMMING.java.two_dimensional;

// Fixed package name to match folder structure

/**
 * UNIQUE PATHS - Leetcode 62
 * Difficulty: Medium
 * Companies: Amazon, Google, Microsoft, Facebook, Bloomberg, Apple
 * 
 * PROBLEM:
 * A robot is located at the top-left corner of a m x n grid.
 * The robot can only move either DOWN or RIGHT at any point in time.
 * The robot is trying to reach the bottom-right corner.
 * How many possible unique paths are there?
 * 
 * EXAMPLES:
 * Input: m = 3, n = 7
 * Output: 28
 * 
 * Input: m = 3, n = 2
 * Output: 3
 * Explanation: From top-left, there are 3 ways to reach bottom-right:
 * 1. Right -> Down -> Down
 * 2. Down -> Down -> Right
 * 3. Down -> Right -> Down
 * 
 * PATTERN: 2D Grid DP
 * KEY INSIGHT: Paths to cell (i,j) = paths to (i-1,j) + paths to (i,j-1)
 */

public class UniquePaths_Leetcode62 {
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(2^(m+n)) - Each cell has 2 choices
    // Space: O(m+n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * At each cell, robot can go right or down.
     * Total paths = paths going right + paths going down
     * 
     * Grid visualization (3x3):
     * S → → ↓
     * ↓ ↘ ↓ ↓
     * ↓ ↓ ↘ E
     * 
     * Where S = start, E = end
     */
    public int uniquePaths_Recursive(int m, int n) {
        return pathsRec(0, 0, m, n);
    }
    
    private int pathsRec(int row, int col, int m, int n) {
        // Base case: reached destination
        if (row == m - 1 && col == n - 1) return 1;
        
        // Out of bounds
        if (row >= m || col >= n) return 0;
        
        // Go right + go down
        return pathsRec(row + 1, col, m, n) + pathsRec(row, col + 1, m, n);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(m*n) - Each cell calculated once
    // Space: O(m*n) - Memo array + recursion
    /**
     * BEGINNER'S EXPLANATION:
     * Cache results to avoid recalculating same cell multiple times.
     */
    public int uniquePaths_Memoization(int m, int n) {
        int[][] memo = new int[m][n];
        return pathsMemo(0, 0, m, n, memo);
    }
    
    private int pathsMemo(int row, int col, int m, int n, int[][] memo) {
        // Base cases
        if (row == m - 1 && col == n - 1) return 1;
        if (row >= m || col >= n) return 0;
        
        // Check memo
        if (memo[row][col] != 0) return memo[row][col];
        
        // Calculate and store
        memo[row][col] = pathsMemo(row + 1, col, m, n, memo) + 
                         pathsMemo(row, col + 1, m, n, memo);
        return memo[row][col];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(m*n)
    // Space: O(m*n)
    /**
     * BEGINNER'S EXPLANATION:
     * Build 2D table where dp[i][j] = number of paths to reach cell (i,j)
     * 
     * Key insight:
     * - Can only reach (i,j) from (i-1,j) or (i,j-1)
     * - So paths to (i,j) = paths to (i-1,j) + paths to (i,j-1)
     * 
     * DP Table for m=3, n=3:
     *     0   1   2
     * 0 [ 1   1   1 ]
     * 1 [ 1   2   3 ]
     * 2 [ 1   3   6 ]
     *              ↑
     *         Answer = 6
     * 
     * Explanation:
     * - First row all 1s (only one way: keep going right)
     * - First column all 1s (only one way: keep going down)
     * - dp[1][1] = dp[0][1] + dp[1][0] = 1 + 1 = 2
     * - dp[1][2] = dp[0][2] + dp[1][1] = 1 + 2 = 3
     * - dp[2][1] = dp[1][1] + dp[2][0] = 2 + 1 = 3
     * - dp[2][2] = dp[1][2] + dp[2][1] = 3 + 3 = 6
     */
    public int uniquePaths_Tabulation(int m, int n) {
        // Create DP table
        int[][] dp = new int[m][n];
        
        // Base case: first row - only one way (keep going right)
        for (int j = 0; j < n; j++) {
            dp[0][j] = 1;
        }
        
        // Base case: first column - only one way (keep going down)
        for (int i = 0; i < m; i++) {
            dp[i][0] = 1;
        }
        
        // Fill DP table
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                // Paths from top + paths from left
                dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
            }
        }
        
        return dp[m - 1][n - 1];
    }
    
    
    // ========== APPROACH 4: SPACE OPTIMIZED ⭐ BEST FOR INTERVIEWS ==========
    // Time: O(m*n)
    // Space: O(n) - Only need one row!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous row to compute current row.
     * So use single array and update in-place!
     * 
     * Evolution of array for m=3, n=3:
     * Initial:  [1, 1, 1]
     * After row 1: [1, 2, 3]
     * After row 2: [1, 3, 6]
     *                     ↑ Answer
     */
    public int uniquePaths(int m, int n) {
        // Use 1D array
        int[] dp = new int[n];
        
        // Initialize first row
        for (int j = 0; j < n; j++) {
            dp[j] = 1;
        }
        
        // Process remaining rows
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                // dp[j] already contains value from previous row (top)
                // dp[j-1] contains value from current row (left)
                dp[j] = dp[j] + dp[j - 1];
            }
        }
        
        return dp[n - 1];
    }
    
    
    // ========== BONUS: COMBINATORICS APPROACH (Math) ==========
    // Time: O(min(m,n))
    // Space: O(1)
    /**
     * BEGINNER'S EXPLANATION:
     * This is actually a combinatorics problem!
     * 
     * To go from (0,0) to (m-1,n-1):
     * - Need exactly (m-1) DOWN moves
     * - Need exactly (n-1) RIGHT moves
     * - Total moves = (m-1) + (n-1) = m+n-2
     * 
     * Question becomes: In how many ways can we arrange 
     * (m-1) D's and (n-1) R's?
     * 
     * Answer: C(m+n-2, m-1) = (m+n-2)! / ((m-1)! * (n-1)!)
     * 
     * Example: m=3, n=3
     * Need 2 D's and 2 R's
     * Total moves = 4
     * Ways = C(4,2) = 4!/(2!*2!) = 6
     * 
     * Possible arrangements:
     * DDRR, DRDR, DRRD, RDDR, RDRD, RRDD
     */
    public int uniquePaths_Math(int m, int n) {
        // Calculate C(m+n-2, m-1)
        int N = m + n - 2; // Total moves
        int k = m - 1;     // Choose m-1 (or equivalently n-1)
        
        long result = 1;
        
        // Calculate C(N, k) = N! / (k! * (N-k)!)
        // Optimized: C(N,k) = (N * (N-1) * ... * (N-k+1)) / (k * (k-1) * ... * 1)
        for (int i = 1; i <= k; i++) {
            result = result * (N - k + i) / i;
        }
        
        return (int) result;
    }
    
    
    // ========== HELPER: Print All Paths (Bonus) ==========
    /**
     * For understanding: Print all possible paths
     * Not efficient but great for learning!
     */
    public void printAllPaths(int m, int n) {
        printPathsHelper(0, 0, m, n, "");
    }
    
    private void printPathsHelper(int row, int col, int m, int n, String path) {
        if (row == m - 1 && col == n - 1) {
            System.out.println(path);
            return;
        }
        
        if (row < m - 1) {
            printPathsHelper(row + 1, col, m, n, path + "D");
        }
        if (col < n - 1) {
            printPathsHelper(row, col + 1, m, n, path + "R");
        }
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        UniquePaths_Leetcode62 solution = new UniquePaths_Leetcode62();
        
        // Test case 1
        System.out.println("Grid: 3x7");
        System.out.println("Unique Paths: " + solution.uniquePaths(3, 7)); // 28
        System.out.println();
        
        // Test case 2
        System.out.println("Grid: 3x2");
        System.out.println("Unique Paths: " + solution.uniquePaths(3, 2)); // 3
        System.out.println();
        
        // Test case 3
        System.out.println("Grid: 3x3");
        System.out.println("Unique Paths: " + solution.uniquePaths(3, 3)); // 6
        System.out.println("All paths:");
        solution.printAllPaths(3, 3);
        System.out.println();
        
        // Compare approaches
        System.out.println("Grid: 5x5");
        System.out.println("DP Solution: " + solution.uniquePaths(5, 5));
        System.out.println("Math Solution: " + solution.uniquePaths_Math(5, 5));
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. THIS IS A FUNDAMENTAL GRID DP PROBLEM:
 *    - Foundation for many other problems
 *    - Multiple solution approaches possible
 *    - Great for demonstrating DP optimization skills
 * 
 * 2. DP RECURRENCE RELATION:
 *    dp[i][j] = dp[i-1][j] + dp[i][j-1]
 *    (Paths from top + paths from left)
 * 
 * 3. PROGRESSION TO SHOW IN INTERVIEW:
 *    1. Start with recursive explanation
 *    2. Add memoization
 *    3. Show 2D DP table
 *    4. Optimize to 1D array (impressive!)
 *    5. Mention math solution (bonus points!)
 * 
 * 4. WHY SPACE OPTIMIZATION WORKS:
 *    - Only need previous row to compute current row
 *    - Update array in-place from left to right
 *    - dp[j] keeps previous row value until updated
 * 
 * 5. FOLLOW-UP QUESTIONS:
 *    - Add obstacles? → Unique Paths II (Leetcode 63)
 *    - Find minimum path sum? → Minimum Path Sum (Leetcode 64)
 *    - Can move in all 4 directions? → Different approach (BFS/DFS)
 *    - 3D grid? → Generalize to 3D DP
 * 
 * 6. RELATED PROBLEMS:
 *    - Leetcode 62: Unique Paths (this)
 *    - Leetcode 63: Unique Paths II (with obstacles)
 *    - Leetcode 64: Minimum Path Sum
 *    - Leetcode 174: Dungeon Game
 *    - Leetcode 931: Minimum Falling Path Sum
 * 
 * 7. INTERVIEW STRATEGY:
 *    - Always start with small grid (2x2 or 3x3)
 *    - Draw the grid and show base cases
 *    - Trace through DP table filling
 *    - Explain space optimization clearly
 *    - Mention combinatorics if interviewer interested
 * 
 * 8. COMMON MISTAKES:
 *    - Forgetting to initialize first row/column
 *    - Off-by-one errors in indices
 *    - In space-optimized: updating from right to left (wrong order!)
 *    - Not handling edge cases (m=1 or n=1)
 * 
 * 9. WHY COMBINATORICS WORKS:
 *    - This is choosing m-1 positions for D from m+n-2 total positions
 *    - Once you place D's, R's positions are determined
 *    - Classic stars and bars / combinations problem
 *    - Formula: C(m+n-2, m-1) = C(m+n-2, n-1)
 * 
 * 10. COMPLEXITY COMPARISON:
 *     Recursion:      O(2^(m+n)) time, O(m+n) space
 *     Memoization:    O(m*n) time, O(m*n) space
 *     2D DP:          O(m*n) time, O(m*n) space
 *     1D DP:          O(m*n) time, O(n) space ⭐
 *     Combinatorics:  O(min(m,n)) time, O(1) space 🚀
 */

