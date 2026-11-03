package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.util.*;

/**
 * UNIQUE PATHS - BASIC GRID DP
 * Difficulty: Easy (Perfect for learning 2D DP!)
 * Pattern: 2D Dynamic Programming on Grid
 * 
 * PROBLEM:
 * There is a robot on an m x n grid. The robot is initially located at the top-left corner.
 * The robot tries to move to the bottom-right corner. The robot can only move either down or right.
 * How many possible unique paths are there?
 * 
 * EXAMPLES:
 * Input: m = 3, n = 7
 * Output: 28
 * 
 * Input: m = 3, n = 2  
 * Output: 3
 * Explanation: From top-left to bottom-right, there are 3 ways:
 * 1. Right → Down → Down
 * 2. Down → Right → Down  
 * 3. Down → Down → Right
 * 
 * WHY THIS IS PERFECT FOR LEARNING 2D DP:
 * 1. Simple movement rules (only right and down)
 * 2. Clear base cases (first row and column)
 * 3. Obvious recurrence relation
 * 4. Natural progression to space optimization
 * 5. Easy to visualize and verify
 */
public class UniquePaths_BasicGrid {
    
    /**
     * APPROACH 1: Pure Recursion (Inefficient - for understanding)
     * Time: O(2^(m+n)) - Exponential!
     * Space: O(m+n) - Recursion depth
     * 
     * Shows the recursive structure before optimization
     */
    public int uniquePaths_Recursion(int m, int n) {
        System.out.println("🔴 Pure Recursion - Finding paths in " + m + "x" + n + " grid");
        return countPaths(0, 0, m, n, 0);
    }
    
    private int countPaths(int i, int j, int m, int n, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + "At position (" + i + ", " + j + ")");
        
        // Base case: reached destination
        if (i == m - 1 && j == n - 1) {
            System.out.println(indent + "Reached destination! Found 1 path");
            return 1;
        }
        
        // Out of bounds
        if (i >= m || j >= n) {
            System.out.println(indent + "Out of bounds, 0 paths");
            return 0;
        }
        
        // Recursive calls: go right or down
        System.out.println(indent + "Exploring: right to (" + i + ", " + (j+1) + ") and down to (" + (i+1) + ", " + j + ")");
        int rightPaths = countPaths(i, j + 1, m, n, depth + 1);
        int downPaths = countPaths(i + 1, j, m, n, depth + 1);
        
        int totalPaths = rightPaths + downPaths;
        System.out.println(indent + "From (" + i + ", " + j + "): " + rightPaths + " + " + downPaths + " = " + totalPaths + " paths");
        
        return totalPaths;
    }
    
    /**
     * APPROACH 2: Recursion with Memoization (Top-Down DP)
     * Time: O(m*n) - Each cell calculated once
     * Space: O(m*n) - Memoization table + recursion stack
     */
    public int uniquePaths_Memoization(int m, int n) {
        System.out.println("🟡 Memoization - Optimized recursion for " + m + "x" + n + " grid");
        Integer[][] memo = new Integer[m][n];
        return countPathsMemo(0, 0, m, n, memo);
    }
    
    private int countPathsMemo(int i, int j, int m, int n, Integer[][] memo) {
        // Base cases
        if (i == m - 1 && j == n - 1) return 1;
        if (i >= m || j >= n) return 0;
        
        // Check memo
        if (memo[i][j] != null) {
            System.out.println("Found in memo[" + i + "][" + j + "] = " + memo[i][j]);
            return memo[i][j];
        }
        
        // Calculate and store
        int rightPaths = countPathsMemo(i, j + 1, m, n, memo);
        int downPaths = countPathsMemo(i + 1, j, m, n, memo);
        memo[i][j] = rightPaths + downPaths;
        
        System.out.println("Calculated memo[" + i + "][" + j + "] = " + rightPaths + " + " + downPaths + " = " + memo[i][j]);
        return memo[i][j];
    }
    
    /**
     * APPROACH 3: Bottom-Up DP (Classic 2D approach)
     * Time: O(m*n) - Fill entire grid once
     * Space: O(m*n) - DP table
     */
    public int uniquePaths_BottomUp(int m, int n) {
        System.out.println("🟢 Bottom-Up 2D DP - Building " + m + "x" + n + " solution");
        
        // Create DP table: dp[i][j] = number of paths to reach (i,j)
        int[][] dp = new int[m][n];
        
        // Base case: First row - only one way (keep going right)
        for (int j = 0; j < n; j++) {
            dp[0][j] = 1;
        }
        System.out.println("Initialized first row: " + Arrays.toString(dp[0]));
        
        // Base case: First column - only one way (keep going down)
        for (int i = 0; i < m; i++) {
            dp[i][0] = 1;
        }
        System.out.println("Initialized first column");
        
        // Fill the DP table
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                // To reach (i,j), we can come from (i-1,j) or (i,j-1)
                dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
                System.out.printf("dp[%d][%d] = dp[%d][%d] + dp[%d][%d] = %d + %d = %d\n", 
                    i, j, i-1, j, i, j-1, dp[i-1][j], dp[i][j-1], dp[i][j]);
            }
        }
        
        // Print final DP table
        printGrid(dp, "Final DP Table");
        
        return dp[m - 1][n - 1];
    }
    
    /**
     * APPROACH 4: Space-Optimized DP (1D array)
     * Time: O(m*n) - Same computation
     * Space: O(n) - Only one row needed
     */
    public int uniquePaths_SpaceOptimized(int m, int n) {
        System.out.println("🔵 Space Optimized - Using only 1D array for " + m + "x" + n + " grid");
        
        // We only need the previous row to calculate current row
        int[] dp = new int[n];
        
        // Initialize: first row has 1 path to each cell
        Arrays.fill(dp, 1);
        System.out.println("Initial row: " + Arrays.toString(dp));
        
        // Process each row
        for (int i = 1; i < m; i++) {
            System.out.println("\nProcessing row " + i + ":");
            
            for (int j = 1; j < n; j++) {
                // dp[j] currently has value from previous row (coming from above)
                // dp[j-1] has value from current row (coming from left)
                int fromAbove = dp[j];
                int fromLeft = dp[j - 1];
                dp[j] = fromAbove + fromLeft;
                
                System.out.printf("  dp[%d] = fromAbove + fromLeft = %d + %d = %d\n", 
                    j, fromAbove, fromLeft, dp[j]);
            }
            
            System.out.println("  Row " + i + " completed: " + Arrays.toString(dp));
        }
        
        return dp[n - 1];
    }
    
    /**
     * APPROACH 5: Mathematical Solution (Combinatorics)
     * Time: O(min(m,n)) - For calculating combinations
     * Space: O(1) - No extra space
     * 
     * This is the most optimal but less educational for DP learning
     */
    public int uniquePaths_Mathematical(int m, int n) {
        System.out.println("🟣 Mathematical - Using combinatorics");
        System.out.println("Total moves needed: " + (m + n - 2));
        System.out.println("Down moves needed: " + (m - 1));
        System.out.println("Right moves needed: " + (n - 1));
        System.out.println("Problem: Choose " + (m - 1) + " positions out of " + (m + n - 2) + " for down moves");
        System.out.println("Formula: C(" + (m + n - 2) + ", " + (m - 1) + ")");
        
        // Calculate C(m+n-2, m-1) = (m+n-2)! / ((m-1)! * (n-1)!)
        // Optimized to avoid large factorials
        long result = 1;
        int k = Math.min(m - 1, n - 1);
        
        for (int i = 0; i < k; i++) {
            result = result * (m + n - 2 - i) / (i + 1);
            System.out.println("Step " + (i + 1) + ": result = " + result);
        }
        
        return (int) result;
    }
    
    /**
     * VISUALIZATION: Show the grid and path building process
     */
    public void visualizeGridConstruction(int m, int n) {
        System.out.println("\n🎯 GRID CONSTRUCTION VISUALIZATION");
        
        int[][] dp = new int[m][n];
        
        // Initialize first row and column
        for (int j = 0; j < n; j++) dp[0][j] = 1;
        for (int i = 0; i < m; i++) dp[i][0] = 1;
        
        System.out.println("After initializing borders:");
        printGridWithCoordinates(dp);
        
        // Fill step by step
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                dp[i][j] = dp[i - 1][j] + dp[i][j - 1];
                System.out.println("\nAfter computing cell (" + i + ", " + j + "):");
                System.out.println("dp[" + i + "][" + j + "] = dp[" + (i-1) + "][" + j + "] + dp[" + i + "][" + (j-1) + "] = " + 
                                 dp[i-1][j] + " + " + dp[i][j-1] + " = " + dp[i][j]);
                printGridWithCoordinates(dp);
            }
        }
    }
    
    /**
     * SHOW ALL POSSIBLE PATHS (for small grids)
     */
    public void showAllPaths(int m, int n) {
        if (m > 4 || n > 4) {
            System.out.println("Skipping path enumeration for large grids (m=" + m + ", n=" + n + ")");
            return;
        }
        
        System.out.println("\n🗺️ ALL POSSIBLE PATHS in " + m + "x" + n + " grid:");
        List<String> allPaths = new ArrayList<>();
        generateAllPaths(0, 0, m - 1, n - 1, "", allPaths);
        
        System.out.println("Total paths: " + allPaths.size());
        for (int i = 0; i < allPaths.size(); i++) {
            System.out.println("Path " + (i + 1) + ": " + allPaths.get(i));
        }
    }
    
    private void generateAllPaths(int i, int j, int targetI, int targetJ, String path, List<String> allPaths) {
        if (i == targetI && j == targetJ) {
            allPaths.add(path + "(" + i + "," + j + ")");
            return;
        }
        
        if (i > targetI || j > targetJ) return;
        
        String currentPath = path + "(" + i + "," + j + ") → ";
        
        // Go right
        generateAllPaths(i, j + 1, targetI, targetJ, currentPath, allPaths);
        
        // Go down
        generateAllPaths(i + 1, j, targetI, targetJ, currentPath, allPaths);
    }
    
    /**
     * Helper methods for visualization
     */
    private void printGrid(int[][] grid, String title) {
        System.out.println("\n" + title + ":");
        for (int[] row : grid) {
            System.out.print("  ");
            for (int val : row) {
                System.out.printf("%4d", val);
            }
            System.out.println();
        }
    }
    
    private void printGridWithCoordinates(int[][] grid) {
        int m = grid.length, n = grid[0].length;
        
        // Print column headers
        System.out.print("     ");
        for (int j = 0; j < n; j++) {
            System.out.printf("%4d", j);
        }
        System.out.println();
        
        // Print grid with row headers
        for (int i = 0; i < m; i++) {
            System.out.printf("%3d: ", i);
            for (int j = 0; j < n; j++) {
                System.out.printf("%4d", grid[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * PERFORMANCE COMPARISON
     */
    public void performanceComparison(int m, int n) {
        System.out.println("\n⚡ PERFORMANCE COMPARISON for " + m + "x" + n + " grid:");
        
        if (m <= 3 && n <= 3) {
            long start = System.nanoTime();
            int result1 = uniquePaths_Recursion_Silent(m, n);
            long time1 = System.nanoTime() - start;
            System.out.println("Pure Recursion: " + result1 + " (Time: " + time1/1000000.0 + " ms)");
        } else {
            System.out.println("Pure Recursion: Skipped (too slow for large grids)");
        }
        
        long start = System.nanoTime();
        int result2 = uniquePaths_Memoization_Silent(m, n);
        long time2 = System.nanoTime() - start;
        System.out.println("Memoization:    " + result2 + " (Time: " + time2/1000000.0 + " ms)");
        
        start = System.nanoTime();
        int result3 = uniquePaths_SpaceOptimized_Silent(m, n);
        long time3 = System.nanoTime() - start;
        System.out.println("Space Optimized: " + result3 + " (Time: " + time3/1000000.0 + " ms)");
        
        start = System.nanoTime();
        int result4 = uniquePaths_Mathematical(m, n);
        long time4 = System.nanoTime() - start;
        System.out.println("Mathematical:   " + result4 + " (Time: " + time4/1000000.0 + " ms)");
    }
    
    // Silent versions for performance testing
    private int uniquePaths_Recursion_Silent(int m, int n) {
        return countPaths_Silent(0, 0, m, n);
    }
    
    private int countPaths_Silent(int i, int j, int m, int n) {
        if (i == m - 1 && j == n - 1) return 1;
        if (i >= m || j >= n) return 0;
        return countPaths_Silent(i, j + 1, m, n) + countPaths_Silent(i + 1, j, m, n);
    }
    
    private int uniquePaths_Memoization_Silent(int m, int n) {
        Integer[][] memo = new Integer[m][n];
        return countPathsMemo_Silent(0, 0, m, n, memo);
    }
    
    private int countPathsMemo_Silent(int i, int j, int m, int n, Integer[][] memo) {
        if (i == m - 1 && j == n - 1) return 1;
        if (i >= m || j >= n) return 0;
        if (memo[i][j] != null) return memo[i][j];
        memo[i][j] = countPathsMemo_Silent(i, j + 1, m, n, memo) + countPathsMemo_Silent(i + 1, j, m, n, memo);
        return memo[i][j];
    }
    
    private int uniquePaths_SpaceOptimized_Silent(int m, int n) {
        int[] dp = new int[n];
        Arrays.fill(dp, 1);
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                dp[j] += dp[j - 1];
            }
        }
        return dp[n - 1];
    }
    
    /**
     * COMPREHENSIVE DEMONSTRATION
     */
    public static void main(String[] args) {
        UniquePaths_BasicGrid solution = new UniquePaths_BasicGrid();
        
        System.out.println("🎓 UNIQUE PATHS - COMPLETE 2D DP TUTORIAL");
        System.out.println("=" .repeat(80));
        
        // Small example for detailed learning
        int m = 3, n = 3;
        System.out.println("\n📝 EXAMPLE: " + m + "x" + n + " grid");
        
        // Show all approaches
        System.out.println("\n1️⃣ PURE RECURSION:");
        System.out.println("Result: " + solution.uniquePaths_Recursion(m, n));
        
        System.out.println("\n2️⃣ MEMOIZATION:");
        System.out.println("Result: " + solution.uniquePaths_Memoization(m, n));
        
        System.out.println("\n3️⃣ BOTTOM-UP 2D DP:");
        System.out.println("Result: " + solution.uniquePaths_BottomUp(m, n));
        
        System.out.println("\n4️⃣ SPACE OPTIMIZED:");
        System.out.println("Result: " + solution.uniquePaths_SpaceOptimized(m, n));
        
        System.out.println("\n5️⃣ MATHEMATICAL:");
        System.out.println("Result: " + solution.uniquePaths_Mathematical(m, n));
        
        // Visualizations
        solution.visualizeGridConstruction(m, n);
        solution.showAllPaths(m, n);
        
        // Performance comparison
        solution.performanceComparison(4, 4);
        
        System.out.println("\n🎯 KEY LEARNING POINTS:");
        System.out.println("1. 2D DP extends 1D concepts to grids/matrices");
        System.out.println("2. Base cases: initialize borders of the grid");
        System.out.println("3. Recurrence: dp[i][j] = dp[i-1][j] + dp[i][j-1]");
        System.out.println("4. Space optimization: only need previous row");
        System.out.println("5. Alternative solution: combinatorics (for this specific problem)");
        
        System.out.println("\n✅ Excellent! You now understand 2D Dynamic Programming!");
    }
}