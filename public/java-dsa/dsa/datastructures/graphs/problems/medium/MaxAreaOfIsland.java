package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 695: Max Area of Island
 * 
 * Problem: You are given an m x n binary matrix grid. An island is a group of 1's (representing land) 
 * connected 4-directionally (horizontal or vertical). You may assume all four edges of the grid are 
 * surrounded by water.
 * The area of an island is the number of cells with a value 1 in the island.
 * Return the maximum area of an island in grid. If there is no island, return 0.
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(m * n) for recursion stack in worst case
 */
public class MaxAreaOfIsland {
    
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    /**
     * DFS solution
     */
    public int maxAreaOfIsland(int[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        int maxArea = 0;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 1) {
                    int area = dfs(grid, i, j);
                    maxArea = Math.max(maxArea, area);
                }
            }
        }
        
        return maxArea;
    }
    
    private int dfs(int[][] grid, int row, int col) {
        // Check bounds and if current cell is water or already visited
        if (row < 0 || row >= grid.length || 
            col < 0 || col >= grid[0].length || 
            grid[row][col] == 0) {
            return 0;
        }
        
        // Mark current cell as visited
        grid[row][col] = 0;
        
        // Count current cell + all connected cells
        int area = 1;
        for (int[] dir : DIRECTIONS) {
            area += dfs(grid, row + dir[0], col + dir[1]);
        }
        
        return area;
    }
    
    /**
     * BFS solution
     */
    public int maxAreaOfIslandBFS(int[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        int maxArea = 0;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 1) {
                    int area = bfs(grid, i, j);
                    maxArea = Math.max(maxArea, area);
                }
            }
        }
        
        return maxArea;
    }
    
    private int bfs(int[][] grid, int startRow, int startCol) {
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{startRow, startCol});
        grid[startRow][startCol] = 0; // Mark as visited
        
        int area = 0;
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            area++;
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < grid.length && 
                    newCol >= 0 && newCol < grid[0].length && 
                    grid[newRow][newCol] == 1) {
                    
                    grid[newRow][newCol] = 0; // Mark as visited
                    queue.offer(new int[]{newRow, newCol});
                }
            }
        }
        
        return area;
    }
    
    /**
     * Solution preserving original grid
     */
    public int maxAreaOfIslandPreserveGrid(int[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        boolean[][] visited = new boolean[rows][cols];
        int maxArea = 0;
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 1 && !visited[i][j]) {
                    int area = dfsPreserve(grid, i, j, visited);
                    maxArea = Math.max(maxArea, area);
                }
            }
        }
        
        return maxArea;
    }
    
    private int dfsPreserve(int[][] grid, int row, int col, boolean[][] visited) {
        if (row < 0 || row >= grid.length || 
            col < 0 || col >= grid[0].length || 
            grid[row][col] == 0 || visited[row][col]) {
            return 0;
        }
        
        visited[row][col] = true;
        
        int area = 1;
        for (int[] dir : DIRECTIONS) {
            area += dfsPreserve(grid, row + dir[0], col + dir[1], visited);
        }
        
        return area;
    }
    
    // Test method
    public static void main(String[] args) {
        MaxAreaOfIsland solution = new MaxAreaOfIsland();
        
        // Test case
        int[][] grid = {
            {0,0,1,0,0,0,0,1,0,0,0,0,0},
            {0,0,0,0,0,0,0,1,1,1,0,0,0},
            {0,1,1,0,1,0,0,0,0,0,0,0,0},
            {0,1,0,0,1,1,0,0,1,0,1,0,0},
            {0,1,0,0,1,1,0,0,1,1,1,0,0},
            {0,0,0,0,0,0,0,0,0,0,1,0,0},
            {0,0,0,0,0,0,0,1,1,1,0,0,0},
            {0,0,0,0,0,0,0,1,1,0,0,0,0}
        };
        
        // Make copies for different solutions
        int[][] grid1 = copyGrid(grid);
        int[][] grid2 = copyGrid(grid);
        
        System.out.println("Max area (DFS): " + solution.maxAreaOfIsland(grid1)); // 6
        System.out.println("Max area (BFS): " + solution.maxAreaOfIslandBFS(grid2)); // 6
        System.out.println("Max area (Preserve): " + solution.maxAreaOfIslandPreserveGrid(grid)); // 6
    }
    
    private static int[][] copyGrid(int[][] original) {
        int[][] copy = new int[original.length][original[0].length];
        for (int i = 0; i < original.length; i++) {
            System.arraycopy(original[i], 0, copy[i], 0, original[i].length);
        }
        return copy;
    }
}

