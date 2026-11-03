package JAVA_DSA.DYNAMIC_PROGRAMMING.java.two_dimensional;

// Fixed package name to match folder structure

/**
 * MINIMUM PATH SUM - Leetcode 64
 * Difficulty: Medium
 * Companies: Amazon, Google, Facebook, Microsoft, Apple, Bloomberg
 * 
 * PROBLEM:
 * Given a m x n grid filled with non-negative numbers, find a path from 
 * top left to bottom right which minimizes the sum of all numbers along its path.
 * Note: You can only move either down or right at any point in time.
 * 
 * EXAMPLES:
 * Input: grid = [[1,3,1],
 *                [1,5,1],
 *                [4,2,1]]
 * Output: 7
 * Explanation: Path 1→3→1→1→1 minimizes sum (1+3+1+1+1=7)
 * 
 * Input: grid = [[1,2,3],
 *                [4,5,6]]
 * Output: 12
 * Explanation: Path 1→2→3→6 (1+2+3+6=12)
 * 
 * PATTERN: 2D Grid DP with Cost
 * Similar to Unique Paths but minimizing cost instead of counting paths
 */

public class MinimumPathSum_Leetcode64 {
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(2^(m+n)) - Two choices at each cell
    // Space: O(m+n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * At each cell, we can go right or down.
     * Try both and pick the path with minimum cost.
     */
    public int minPathSum_Recursive(int[][] grid) {
        return minPathRec(grid, 0, 0);
    }
    
    private int minPathRec(int[][] grid, int row, int col) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Base case: reached destination
        if (row == m - 1 && col == n - 1) {
            return grid[row][col];
        }
        
        // Out of bounds
        if (row >= m || col >= n) {
            return Integer.MAX_VALUE;
        }
        
        // Try going down and right
        int down = minPathRec(grid, row + 1, col);
        int right = minPathRec(grid, row, col + 1);
        
        // Current cell + minimum of two paths
        return grid[row][col] + Math.min(down, right);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(m*n) - Each cell calculated once
    // Space: O(m*n) - Memo array + recursion
    /**
     * BEGINNER'S EXPLANATION:
     * Cache results to avoid recalculating same cell.
     */
    public int minPathSum_Memoization(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        int[][] memo = new int[m][n];
        for (int[] row : memo) java.util.Arrays.fill(row, -1);
        return minPathMemo(grid, 0, 0, memo);
    }
    
    private int minPathMemo(int[][] grid, int row, int col, int[][] memo) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Base cases
        if (row == m - 1 && col == n - 1) return grid[row][col];
        if (row >= m || col >= n) return Integer.MAX_VALUE;
        
        // Check memo
        if (memo[row][col] != -1) return memo[row][col];
        
        // Calculate and store
        int down = minPathMemo(grid, row + 1, col, memo);
        int right = minPathMemo(grid, row, col + 1, memo);
        
        memo[row][col] = grid[row][col] + Math.min(down, right);
        return memo[row][col];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(m*n)
    // Space: O(m*n)
    /**
     * BEGINNER'S EXPLANATION:
     * Build DP table where dp[i][j] = minimum path sum to reach (i,j)
     * 
     * Key insight:
     * - To reach (i,j), we come from (i-1,j) or (i,j-1)
     * - dp[i][j] = grid[i][j] + min(dp[i-1][j], dp[i][j-1])
     * 
     * DP Table for grid = [[1,3,1],
     *                      [1,5,1],
     *                      [4,2,1]]:
     * 
     *     0   1   2
     * 0 [ 1   4   5 ]   ← First row: cumulative sum going right
     * 1 [ 2   7   6 ]
     * 2 [ 6   8   7 ]   ← Answer at bottom-right
     *              ↑
     * 
     * How dp[1][1] = 7 is calculated:
     * - From top: dp[0][1] = 4
     * - From left: dp[1][0] = 2
     * - Current cell: grid[1][1] = 5
     * - dp[1][1] = 5 + min(4, 2) = 7
     * 
     * Path visualization:
     * [1] → [3] → [1]
     *              ↓
     *             [1]
     *              ↓
     *             [1]  = 1+3+1+1+1 = 7
     */
    public int minPathSum_Tabulation(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Create DP table
        int[][] dp = new int[m][n];
        
        // Starting cell
        dp[0][0] = grid[0][0];
        
        // Fill first row (can only come from left)
        for (int j = 1; j < n; j++) {
            dp[0][j] = dp[0][j - 1] + grid[0][j];
        }
        
        // Fill first column (can only come from top)
        for (int i = 1; i < m; i++) {
            dp[i][0] = dp[i - 1][0] + grid[i][0];
        }
        
        // Fill rest of the table
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                // Minimum of coming from top or left
                dp[i][j] = grid[i][j] + Math.min(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        
        return dp[m - 1][n - 1];
    }
    
    
    // ========== APPROACH 4: IN-PLACE MODIFICATION ==========
    // Time: O(m*n)
    // Space: O(1) - Modify input grid directly
    /**
     * BEGINNER'S EXPLANATION:
     * If we can modify the input grid, use it as DP table!
     * This saves space but destroys original grid.
     */
    public int minPathSum_InPlace(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Fill first row
        for (int j = 1; j < n; j++) {
            grid[0][j] += grid[0][j - 1];
        }
        
        // Fill first column
        for (int i = 1; i < m; i++) {
            grid[i][0] += grid[i - 1][0];
        }
        
        // Fill rest
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                grid[i][j] += Math.min(grid[i - 1][j], grid[i][j - 1]);
            }
        }
        
        return grid[m - 1][n - 1];
    }
    
    
    // ========== APPROACH 5: SPACE OPTIMIZED ⭐ BEST FOR INTERVIEWS ==========
    // Time: O(m*n)
    // Space: O(n) - Only one row!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous row to compute current row.
     * Use 1D array and update in-place!
     * 
     * Evolution for grid [[1,3,1],
     *                     [1,5,1],
     *                     [4,2,1]]:
     * 
     * Initial (first row):  [1, 4, 5]
     * After row 1:          [2, 7, 6]
     * After row 2:          [6, 8, 7]
     *                             ↑ Answer
     */
    public int minPathSum(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Use 1D array
        int[] dp = new int[n];
        
        // Initialize with first row
        dp[0] = grid[0][0];
        for (int j = 1; j < n; j++) {
            dp[j] = dp[j - 1] + grid[0][j];
        }
        
        // Process remaining rows
        for (int i = 1; i < m; i++) {
            // First column of current row
            dp[0] += grid[i][0];
            
            // Rest of the columns
            for (int j = 1; j < n; j++) {
                // dp[j] has value from previous row (top)
                // dp[j-1] has value from current row (left)
                dp[j] = grid[i][j] + Math.min(dp[j], dp[j - 1]);
            }
        }
        
        return dp[n - 1];
    }
    
    
    // ========== HELPER: PRINT PATH ==========
    /**
     * Backtracks through DP table to print actual path
     */
    public void printPath(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        int[][] dp = new int[m][n];
        
        // Build DP table
        dp[0][0] = grid[0][0];
        for (int j = 1; j < n; j++) dp[0][j] = dp[0][j - 1] + grid[0][j];
        for (int i = 1; i < m; i++) dp[i][0] = dp[i - 1][0] + grid[i][0];
        
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                dp[i][j] = grid[i][j] + Math.min(dp[i - 1][j], dp[i][j - 1]);
            }
        }
        
        // Backtrack to find path
        System.out.println("Minimum Path Sum: " + dp[m - 1][n - 1]);
        System.out.print("Path: ");
        
        java.util.List<String> path = new java.util.ArrayList<>();
        int i = m - 1, j = n - 1;
        path.add("(" + i + "," + j + ")=" + grid[i][j]);
        
        while (i > 0 || j > 0) {
            if (i == 0) {
                j--;
            } else if (j == 0) {
                i--;
            } else {
                // Choose direction with smaller value
                if (dp[i - 1][j] < dp[i][j - 1]) {
                    i--;
                } else {
                    j--;
                }
            }
            path.add("(" + i + "," + j + ")=" + grid[i][j]);
        }
        
        java.util.Collections.reverse(path);
        System.out.println(String.join(" → ", path));
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        MinimumPathSum_Leetcode64 solution = new MinimumPathSum_Leetcode64();
        
        // Test case 1
        int[][] grid1 = {
            {1, 3, 1},
            {1, 5, 1},
            {4, 2, 1}
        };
        System.out.println("Grid 1:");
        printGrid(grid1);
        solution.printPath(grid1);
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 2
        int[][] grid2 = {
            {1, 2, 3},
            {4, 5, 6}
        };
        System.out.println("Grid 2:");
        printGrid(grid2);
        solution.printPath(grid2);
    }
    
    private static void printGrid(int[][] grid) {
        for (int[] row : grid) {
            System.out.println(java.util.Arrays.toString(row));
        }
        System.out.println();
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. PATTERN RECOGNITION:
 *    - Grid path problem with cost minimization
 *    - Similar to Unique Paths but with min instead of sum
 *    - Common pattern: min/max cost path in grid
 * 
 * 2. DP RECURRENCE:
 *    dp[i][j] = grid[i][j] + min(dp[i-1][j], dp[i][j-1])
 *    (Current cost + minimum of coming from top or left)
 * 
 * 3. BASE CASES:
 *    - First cell: dp[0][0] = grid[0][0]
 *    - First row: Can only come from left
 *    - First column: Can only come from top
 * 
 * 4. SPACE OPTIMIZATION:
 *    - Only need previous row for current row
 *    - Can reduce O(m*n) to O(n) space
 *    - Or modify input grid in-place for O(1) space
 * 
 * 5. FOLLOW-UP QUESTIONS:
 *    - Can move in 4 directions? → Use BFS/Dijkstra
 *    - Print actual path? → Backtrack through DP table
 *    - With obstacles? → Set obstacles to infinity
 *    - Maximize path sum? → Change min to max
 *    - With negative numbers? → Same solution works
 * 
 * 6. RELATED PROBLEMS:
 *    - Leetcode 64: Minimum Path Sum (this)
 *    - Leetcode 62: Unique Paths (count paths)
 *    - Leetcode 63: Unique Paths II (with obstacles)
 *    - Leetcode 120: Triangle
 *    - Leetcode 931: Minimum Falling Path Sum
 *    - Leetcode 174: Dungeon Game (reverse DP)
 * 
 * 7. INTERVIEW STRATEGY:
 *    - Start with brute force recursion
 *    - Show 2D DP solution
 *    - Draw small example and fill table
 *    - Optimize to 1D space (impressive!)
 *    - Mention in-place modification option
 * 
 * 8. COMMON MISTAKES:
 *    - Forgetting to initialize first row/column
 *    - Using max instead of min
 *    - Not handling edge cases (1x1 grid)
 *    - Confusing indices in space-optimized version
 * 
 * 9. COMPLEXITY COMPARISON:
 *    Recursion:        O(2^(m+n)) time, O(m+n) space
 *    Memoization:      O(m*n) time, O(m*n) space
 *    2D DP:            O(m*n) time, O(m*n) space
 *    1D DP:            O(m*n) time, O(n) space ⭐
 *    In-place:         O(m*n) time, O(1) space 🚀
 * 
 * 10. WHY THIS PROBLEM IS IMPORTANT:
 *     - Tests understanding of grid DP
 *     - Shows ability to optimize space
 *     - Foundation for harder path problems
 *     - Common in MAANG phone screens
 */

