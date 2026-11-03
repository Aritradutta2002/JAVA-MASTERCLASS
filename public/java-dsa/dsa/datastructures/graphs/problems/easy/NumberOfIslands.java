package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 200: Number of Islands
 * 
 * Problem: Given an m x n 2D binary grid which represents a map of '1's (land) 
 * and '0's (water), return the number of islands.
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(m * n) for recursion stack in worst case
 */
public class NumberOfIslands {
    
    /**
     * Main method to count number of islands using DFS
     */
    public int numIslands(char[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        int islands = 0;
        
        // Iterate through each cell in the grid
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                // If we find a '1', it's the start of a new island
                if (grid[i][j] == '1') {
                    islands++;
                    dfs(grid, i, j); // Mark all connected land as visited
                }
            }
        }
        
        return islands;
    }
    
    /**
     * DFS to mark all connected land cells as visited
     */
    private void dfs(char[][] grid, int row, int col) {
        // Check bounds and if current cell is water or already visited
        if (row < 0 || row >= grid.length || 
            col < 0 || col >= grid[0].length || 
            grid[row][col] == '0') {
            return;
        }
        
        // Mark current cell as visited by changing '1' to '0'
        grid[row][col] = '0';
        
        // Explore all 4 directions
        dfs(grid, row + 1, col); // Down
        dfs(grid, row - 1, col); // Up
        dfs(grid, row, col + 1); // Right
        dfs(grid, row, col - 1); // Left
    }
    
    /**
     * Alternative BFS solution
     */
    public int numIslandsBFS(char[][] grid) {
        if (grid == null || grid.length == 0) return 0;
        
        int rows = grid.length;
        int cols = grid[0].length;
        int islands = 0;
        
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == '1') {
                    islands++;
                    
                    // BFS to mark all connected land
                    java.util.Queue<int[]> queue = new java.util.LinkedList<>();
                    queue.offer(new int[]{i, j});
                    grid[i][j] = '0';
                    
                    while (!queue.isEmpty()) {
                        int[] current = queue.poll();
                        int row = current[0];
                        int col = current[1];
                        
                        for (int[] dir : directions) {
                            int newRow = row + dir[0];
                            int newCol = col + dir[1];
                            
                            if (newRow >= 0 && newRow < rows && 
                                newCol >= 0 && newCol < cols && 
                                grid[newRow][newCol] == '1') {
                                grid[newRow][newCol] = '0';
                                queue.offer(new int[]{newRow, newCol});
                            }
                        }
                    }
                }
            }
        }
        
        return islands;
    }
    
    // Test method
    public static void main(String[] args) {
        NumberOfIslands solution = new NumberOfIslands();
        
        char[][] grid1 = {
            {'1','1','1','1','0'},
            {'1','1','0','1','0'},
            {'1','1','0','0','0'},
            {'0','0','0','0','0'}
        };
        
        System.out.println("Number of islands: " + solution.numIslands(grid1)); // Output: 1
        
        char[][] grid2 = {
            {'1','1','0','0','0'},
            {'1','1','0','0','0'},
            {'0','0','1','0','0'},
            {'0','0','0','1','1'}
        };
        
        System.out.println("Number of islands: " + solution.numIslandsBFS(grid2)); // Output: 3
    }
}

