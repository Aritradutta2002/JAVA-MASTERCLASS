package JAVA_DSA.DYNAMIC_PROGRAMMING.java.two_dimensional;

import java.util.*;

/**
 * UNIQUE PATHS II - Leetcode 63
 * Difficulty: Medium
 * Pattern: DP on Grid with Obstacles
 * 
 * PROBLEM:
 * You are given an m x n integer array grid. There is a robot initially located at the 
 * top-left corner (i.e., grid[0][0]). The robot tries to move to the bottom-right corner 
 * (i.e., grid[m - 1][n - 1]). The robot can only move either down or right at any point in time.
 * An obstacle and space are marked as 1 and 0 respectively in grid. A path that the robot 
 * takes cannot include any square that is an obstacle.
 * Return the number of possible unique paths that the robot can take to reach the bottom-right corner.
 * 
 * EXAMPLES:
 * Input: obstacleGrid = [[0,0,0],[0,1,0],[0,0,0]]
 * Output: 2
 * Explanation: There is one obstacle in the middle of the 3x3 grid.
 * There are two ways to reach the bottom-right corner:
 * 1. Right -> Right -> Down -> Down
 * 2. Down -> Down -> Right -> Right
 */
public class UniquePathsII_Leetcode63 {
    
    /**
     * APPROACH 1: 2D DP (Standard)
     * Time: O(m * n), Space: O(m * n)
     */
    public int uniquePathsWithObstacles_2D(int[][] obstacleGrid) {
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        // If start or end is obstacle, no paths possible
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            return 0;
        }
        
        int[][] dp = new int[m][n];
        dp[0][0] = 1; // Starting position
        
        // Fill first row
        for (int j = 1; j < n; j++) {
            if (obstacleGrid[0][j] == 1) {
                dp[0][j] = 0;
            } else {
                dp[0][j] = dp[0][j-1];
            }
        }
        
        // Fill first column
        for (int i = 1; i < m; i++) {
            if (obstacleGrid[i][0] == 1) {
                dp[i][0] = 0;
            } else {
                dp[i][0] = dp[i-1][0];
            }
        }
        
        // Fill rest of the grid
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                if (obstacleGrid[i][j] == 1) {
                    dp[i][j] = 0;
                } else {
                    dp[i][j] = dp[i-1][j] + dp[i][j-1];
                }
            }
        }
        
        return dp[m-1][n-1];
    }
    
    /**
     * APPROACH 2: Space Optimized (1D DP)
     * Time: O(m * n), Space: O(n)
     */
    public int uniquePathsWithObstacles_1D(int[][] obstacleGrid) {
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            return 0;
        }
        
        int[] dp = new int[n];
        dp[0] = 1;
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (obstacleGrid[i][j] == 1) {
                    dp[j] = 0;
                } else if (j > 0) {
                    dp[j] += dp[j-1];
                }
            }
        }
        
        return dp[n-1];
    }
    
    /**
     * APPROACH 3: In-place modification (if allowed)
     * Time: O(m * n), Space: O(1)
     */
    public int uniquePathsWithObstacles_InPlace(int[][] obstacleGrid) {
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            return 0;
        }
        
        // Convert obstacles to -1 for clarity
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (obstacleGrid[i][j] == 1) {
                    obstacleGrid[i][j] = -1;
                }
            }
        }
        
        obstacleGrid[0][0] = 1;
        
        // Fill first row
        for (int j = 1; j < n; j++) {
            if (obstacleGrid[0][j] == -1) {
                obstacleGrid[0][j] = 0;
            } else {
                obstacleGrid[0][j] = obstacleGrid[0][j-1];
            }
        }
        
        // Fill first column
        for (int i = 1; i < m; i++) {
            if (obstacleGrid[i][0] == -1) {
                obstacleGrid[i][0] = 0;
            } else {
                obstacleGrid[i][0] = obstacleGrid[i-1][0];
            }
        }
        
        // Fill rest
        for (int i = 1; i < m; i++) {
            for (int j = 1; j < n; j++) {
                if (obstacleGrid[i][j] == -1) {
                    obstacleGrid[i][j] = 0;
                } else {
                    obstacleGrid[i][j] = obstacleGrid[i-1][j] + obstacleGrid[i][j-1];
                }
            }
        }
        
        return obstacleGrid[m-1][n-1];
    }
    
    /**
     * APPROACH 4: Recursive with Memoization (for understanding)
     * Time: O(m * n), Space: O(m * n)
     */
    public int uniquePathsWithObstacles_Recursive(int[][] obstacleGrid) {
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            return 0;
        }
        
        Integer[][] memo = new Integer[m][n];
        return dfs(obstacleGrid, 0, 0, memo);
    }
    
    private int dfs(int[][] grid, int i, int j, Integer[][] memo) {
        int m = grid.length, n = grid[0].length;
        
        // Out of bounds or obstacle
        if (i >= m || j >= n || grid[i][j] == 1) {
            return 0;
        }
        
        // Reached destination
        if (i == m - 1 && j == n - 1) {
            return 1;
        }
        
        if (memo[i][j] != null) {
            return memo[i][j];
        }
        
        // Move right or down
        int paths = dfs(grid, i + 1, j, memo) + dfs(grid, i, j + 1, memo);
        memo[i][j] = paths;
        
        return paths;
    }
    
    /**
     * Helper method to visualize the grid and DP table
     */
    public void visualizeGrid(int[][] obstacleGrid) {
        System.out.println("Obstacle Grid (1 = obstacle, 0 = free):");
        for (int[] row : obstacleGrid) {
            System.out.println("  " + Arrays.toString(row));
        }
        
        // Show DP progression
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            System.out.println("No paths possible (start or end is blocked)");
            return;
        }
        
        int[][] dp = new int[m][n];
        dp[0][0] = 1;
        
        // Fill DP table
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (i == 0 && j == 0) continue;
                
                if (obstacleGrid[i][j] == 1) {
                    dp[i][j] = 0;
                } else {
                    int fromTop = (i > 0) ? dp[i-1][j] : 0;
                    int fromLeft = (j > 0) ? dp[i][j-1] : 0;
                    dp[i][j] = fromTop + fromLeft;
                }
            }
        }
        
        System.out.println("\nDP Table (number of paths to each cell):");
        for (int[] row : dp) {
            System.out.print("  ");
            for (int val : row) {
                System.out.printf("%3d ", val);
            }
            System.out.println();
        }
    }
    
    /**
     * Method to find and print one valid path (if exists)
     */
    public List<String> findOnePath(int[][] obstacleGrid) {
        int m = obstacleGrid.length;
        int n = obstacleGrid[0].length;
        
        List<String> path = new ArrayList<>();
        
        if (obstacleGrid[0][0] == 1 || obstacleGrid[m-1][n-1] == 1) {
            return path; // No path possible
        }
        
        // Use DFS to find any valid path
        boolean[][] visited = new boolean[m][n];
        if (findPath(obstacleGrid, 0, 0, visited, path)) {
            return path;
        }
        
        return new ArrayList<>(); // No path found
    }
    
    private boolean findPath(int[][] grid, int i, int j, boolean[][] visited, List<String> path) {
        int m = grid.length, n = grid[0].length;
        
        if (i >= m || j >= n || grid[i][j] == 1 || visited[i][j]) {
            return false;
        }
        
        visited[i][j] = true;
        path.add("(" + i + "," + j + ")");
        
        if (i == m - 1 && j == n - 1) {
            return true; // Reached destination
        }
        
        // Try right first, then down
        if (findPath(grid, i, j + 1, visited, path) || 
            findPath(grid, i + 1, j, visited, path)) {
            return true;
        }
        
        // Backtrack
        path.remove(path.size() - 1);
        visited[i][j] = false;
        return false;
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        UniquePathsII_Leetcode63 solution = new UniquePathsII_Leetcode63();
        
        System.out.println("=== Unique Paths II (with Obstacles) ===");
        
        // Test case 1
        int[][] grid1 = {
            {0, 0, 0},
            {0, 1, 0},
            {0, 0, 0}
        };
        
        solution.visualizeGrid(grid1);
        System.out.println("Unique Paths (2D DP): " + solution.uniquePathsWithObstacles_2D(grid1.clone()));
        System.out.println("Unique Paths (1D DP): " + solution.uniquePathsWithObstacles_1D(grid1.clone()));
        System.out.println("Unique Paths (Recursive): " + solution.uniquePathsWithObstacles_Recursive(grid1.clone()));
        
        List<String> path = solution.findOnePath(grid1);
        System.out.println("One valid path: " + path);
        System.out.println();
        
        // Test case 2
        int[][] grid2 = {
            {0, 1},
            {0, 0}
        };
        System.out.println("Grid 2:");
        solution.visualizeGrid(grid2);
        System.out.println("Unique Paths: " + solution.uniquePathsWithObstacles_2D(grid2));
        System.out.println();
        
        // Test case 3 - No path
        int[][] grid3 = {
            {0, 0},
            {1, 1},
            {0, 0}
        };
        System.out.println("Grid 3 (blocked path):");
        solution.visualizeGrid(grid3);
        System.out.println("Unique Paths: " + solution.uniquePathsWithObstacles_2D(grid3));
        
        // Test case 4 - Start blocked
        int[][] grid4 = {{1}};
        System.out.println("\nGrid 4 (start blocked): " + solution.uniquePathsWithObstacles_2D(grid4));
    }
}