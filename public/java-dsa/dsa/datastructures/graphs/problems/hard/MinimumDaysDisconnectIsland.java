package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1568: Minimum Number of Days to Disconnect Island
 * 
 * Problem: You are given an m x n binary grid grid where 1 represents land and 0 represents water. An island is
 * a maximal 4-directionally (horizontal or vertical) connected group of 1's.
 * The grid is said to be connected if we have exactly one island, otherwise is said disconnected.
 * In one day, we are allowed to change any single land cell (1) into a water cell (0).
 * Return the minimum number of days to disconnect the grid.
 * 
 * Time Complexity: O(m^2 * n^2)
 * Space Complexity: O(m * n)
 */
public class MinimumDaysDisconnectIsland {
    
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    /**
     * Main solution - try 0, 1, then 2 days
     */
    public int minDays(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Check if already disconnected
        if (countIslands(grid) != 1) {
            return 0;
        }
        
        // Try removing each land cell (1 day)
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    grid[i][j] = 0;
                    if (countIslands(grid) != 1) {
                        return 1;
                    }
                    grid[i][j] = 1; // Restore
                }
            }
        }
        
        // If we can't disconnect in 1 day, answer is 2
        // (We can always disconnect by removing 2 adjacent land cells)
        return 2;
    }
    
    /**
     * Count number of islands in the grid
     */
    private int countIslands(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        boolean[][] visited = new boolean[m][n];
        int islands = 0;
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1 && !visited[i][j]) {
                    islands++;
                    dfs(grid, i, j, visited);
                }
            }
        }
        
        return islands;
    }
    
    private void dfs(int[][] grid, int row, int col, boolean[][] visited) {
        int m = grid.length;
        int n = grid[0].length;
        
        if (row < 0 || row >= m || col < 0 || col >= n || 
            visited[row][col] || grid[row][col] == 0) {
            return;
        }
        
        visited[row][col] = true;
        
        for (int[] dir : DIRECTIONS) {
            dfs(grid, row + dir[0], col + dir[1], visited);
        }
    }
    
    /**
     * Optimized solution using articulation points
     */
    public int minDaysOptimized(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Check if already disconnected
        if (countIslands(grid) != 1) {
            return 0;
        }
        
        // Check if we can disconnect by removing articulation points
        List<int[]> landCells = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    landCells.add(new int[]{i, j});
                }
            }
        }
        
        // If only 1 or 2 land cells, removing them disconnects
        if (landCells.size() <= 2) {
            return landCells.size();
        }
        
        // Check articulation points
        for (int[] cell : landCells) {
            if (isArticulationPoint(grid, cell[0], cell[1])) {
                return 1;
            }
        }
        
        return 2;
    }
    
    private boolean isArticulationPoint(int[][] grid, int row, int col) {
        grid[row][col] = 0;
        boolean result = countIslands(grid) != 1;
        grid[row][col] = 1;
        return result;
    }
    
    /**
     * Tarjan's algorithm for finding articulation points
     */
    public int minDaysTarjan(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        if (countIslands(grid) != 1) {
            return 0;
        }
        
        // Find all land cells
        List<int[]> landCells = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    landCells.add(new int[]{i, j});
                }
            }
        }
        
        if (landCells.size() <= 2) {
            return landCells.size();
        }
        
        // Use Tarjan's algorithm to find articulation points
        int[][] id = new int[m][n];
        int[][] low = new int[m][n];
        boolean[][] visited = new boolean[m][n];
        boolean[][] isAP = new boolean[m][n];
        
        int idCounter = 0;
        
        // Initialize
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (grid[i][j] == 1) {
                    id[i][j] = -1;
                    low[i][j] = -1;
                }
            }
        }
        
        // Find articulation points using Tarjan's algorithm
        for (int[] start : landCells) {
            if (!visited[start[0]][start[1]]) {
                tarjan(grid, start[0], start[1], -1, -1, id, low, visited, isAP, idCounter);
                break; // Only need to start from one component
            }
        }
        
        // Check if any articulation point exists
        for (int[] cell : landCells) {
            if (isAP[cell[0]][cell[1]]) {
                return 1;
            }
        }
        
        return 2;
    }
    
    private int tarjan(int[][] grid, int row, int col, int parentRow, int parentCol,
                      int[][] id, int[][] low, boolean[][] visited, boolean[][] isAP, int idCounter) {
        int m = grid.length;
        int n = grid[0].length;
        
        visited[row][col] = true;
        id[row][col] = low[row][col] = idCounter++;
        
        int children = 0;
        
        for (int[] dir : DIRECTIONS) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            
            if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n && 
                grid[newRow][newCol] == 1) {
                
                if (newRow == parentRow && newCol == parentCol) {
                    continue; // Skip parent
                }
                
                if (!visited[newRow][newCol]) {
                    children++;
                    idCounter = tarjan(grid, newRow, newCol, row, col, id, low, visited, isAP, idCounter);
                    low[row][col] = Math.min(low[row][col], low[newRow][newCol]);
                    
                    // Check articulation point conditions
                    if ((parentRow == -1 && children > 1) || 
                        (parentRow != -1 && low[newRow][newCol] >= id[row][col])) {
                        isAP[row][col] = true;
                    }
                } else {
                    low[row][col] = Math.min(low[row][col], id[newRow][newCol]);
                }
            }
        }
        
        return idCounter;
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumDaysDisconnectIsland solution = new MinimumDaysDisconnectIsland();
        
        // Test case 1: [[0,1,1,0],[0,1,1,0],[0,0,0,0]]
        int[][] grid1 = {{0,1,1,0},{0,1,1,0},{0,0,0,0}};
        System.out.println("Min days: " + solution.minDays(grid1)); // 2
        
        // Test case 2: [[1,1]]
        int[][] grid2 = {{1,1}};
        System.out.println("Min days (Optimized): " + solution.minDaysOptimized(grid2)); // 2
        
        // Test case 3: [[1,0,1,0]]
        int[][] grid3 = {{1,0,1,0}};
        System.out.println("Min days (Tarjan): " + solution.minDaysTarjan(grid3)); // 0
    }
}

