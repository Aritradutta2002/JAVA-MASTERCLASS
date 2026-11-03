package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 463: Island Perimeter
 * 
 * Problem: You are given row x col grid representing a map where grid[i][j] = 1 
 * represents land and grid[i][j] = 0 represents water.
 * Grid cells are connected horizontally/vertically (not diagonally). 
 * The grid is completely surrounded by water, and there is exactly one island.
 * One cell is a square with side length 1. Determine the perimeter of the island.
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(1)
 */
public class IslandPerimeter {
    
    /**
     * Optimized solution - count land cells and subtract shared edges
     */
    public int islandPerimeter(int[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        int perimeter = 0;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 1) {
                    // Each land cell contributes 4 to perimeter initially
                    perimeter += 4;
                    
                    // Subtract 2 for each adjacent land cell (shared edge)
                    if (i > 0 && grid[i-1][j] == 1) perimeter -= 2;
                    if (j > 0 && grid[i][j-1] == 1) perimeter -= 2;
                }
            }
        }
        
        return perimeter;
    }
    
    /**
     * Alternative solution - count exposed edges directly
     */
    public int islandPerimeterDFS(int[][] grid) {
        if (grid == null || grid.length == 0) return 0;
        
        int rows = grid.length;
        int cols = grid[0].length;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 1) {
                    return dfs(grid, i, j);
                }
            }
        }
        
        return 0;
    }
    
    private int dfs(int[][] grid, int row, int col) {
        // Out of bounds or water contributes 1 to perimeter
        if (row < 0 || row >= grid.length || 
            col < 0 || col >= grid[0].length || 
            grid[row][col] == 0) {
            return 1;
        }
        
        // Already visited
        if (grid[row][col] == -1) {
            return 0;
        }
        
        // Mark as visited
        grid[row][col] = -1;
        
        // Count perimeter from all 4 directions
        return dfs(grid, row + 1, col) + 
               dfs(grid, row - 1, col) + 
               dfs(grid, row, col + 1) + 
               dfs(grid, row, col - 1);
    }
    
    // Test method
    public static void main(String[] args) {
        IslandPerimeter solution = new IslandPerimeter();
        
        int[][] grid = {
            {0,1,0,0},
            {1,1,1,0},
            {0,1,0,0},
            {1,1,0,0}
        };
        
        System.out.println("Island perimeter: " + solution.islandPerimeter(grid)); // Output: 16
    }
}

