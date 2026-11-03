package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 934: Shortest Bridge
 * 
 * Problem: You are given an n x n binary matrix grid where 1 represents land and 0 represents water.
 * An island is a 4-directionally connected group of 1's not connected to any other 1's. 
 * There are exactly two islands in grid.
 * You may change 0's to 1's to connect the two islands to form one island.
 * Return the smallest number of 0's you must flip to connect the two islands.
 * 
 * Time Complexity: O(n^2)
 * Space Complexity: O(n^2)
 */
public class ShortestBridge {
    
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    /**
     * DFS + BFS solution
     */
    public int shortestBridge(int[][] grid) {
        int n = grid.length;
        Queue<int[]> queue = new LinkedList<>();
        
        // Step 1: Find first island using DFS and mark all its cells
        boolean found = false;
        for (int i = 0; i < n && !found; i++) {
            for (int j = 0; j < n && !found; j++) {
                if (grid[i][j] == 1) {
                    dfs(grid, i, j, queue);
                    found = true;
                }
            }
        }
        
        // Step 2: BFS from first island to find shortest path to second island
        int steps = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int[] current = queue.poll();
                int row = current[0];
                int col = current[1];
                
                for (int[] dir : DIRECTIONS) {
                    int newRow = row + dir[0];
                    int newCol = col + dir[1];
                    
                    if (newRow >= 0 && newRow < n && newCol >= 0 && newCol < n) {
                        if (grid[newRow][newCol] == 1) {
                            // Found second island
                            return steps;
                        } else if (grid[newRow][newCol] == 0) {
                            // Mark as visited and add to queue
                            grid[newRow][newCol] = 2;
                            queue.offer(new int[]{newRow, newCol});
                        }
                    }
                }
            }
            
            steps++;
        }
        
        return -1; // Should never reach here
    }
    
    /**
     * DFS to mark first island and add boundary cells to queue
     */
    private void dfs(int[][] grid, int row, int col, Queue<int[]> queue) {
        int n = grid.length;
        
        if (row < 0 || row >= n || col < 0 || col >= n || grid[row][col] != 1) {
            return;
        }
        
        grid[row][col] = 2; // Mark as part of first island
        queue.offer(new int[]{row, col}); // Add to BFS queue
        
        // Continue DFS to mark entire first island
        for (int[] dir : DIRECTIONS) {
            dfs(grid, row + dir[0], col + dir[1], queue);
        }
    }
    
    /**
     * Alternative solution using separate visited array
     */
    public int shortestBridgeAlternative(int[][] grid) {
        int n = grid.length;
        boolean[][] visited = new boolean[n][n];
        Queue<int[]> queue = new LinkedList<>();
        
        // Find and mark first island
        boolean found = false;
        for (int i = 0; i < n && !found; i++) {
            for (int j = 0; j < n && !found; j++) {
                if (grid[i][j] == 1) {
                    dfsAlternative(grid, i, j, visited, queue);
                    found = true;
                }
            }
        }
        
        // BFS to find shortest bridge
        int steps = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int[] current = queue.poll();
                int row = current[0];
                int col = current[1];
                
                for (int[] dir : DIRECTIONS) {
                    int newRow = row + dir[0];
                    int newCol = col + dir[1];
                    
                    if (newRow >= 0 && newRow < n && newCol >= 0 && newCol < n && !visited[newRow][newCol]) {
                        if (grid[newRow][newCol] == 1) {
                            return steps;
                        }
                        
                        visited[newRow][newCol] = true;
                        queue.offer(new int[]{newRow, newCol});
                    }
                }
            }
            
            steps++;
        }
        
        return -1;
    }
    
    private void dfsAlternative(int[][] grid, int row, int col, boolean[][] visited, Queue<int[]> queue) {
        int n = grid.length;
        
        if (row < 0 || row >= n || col < 0 || col >= n || visited[row][col] || grid[row][col] == 0) {
            return;
        }
        
        visited[row][col] = true;
        queue.offer(new int[]{row, col});
        
        for (int[] dir : DIRECTIONS) {
            dfsAlternative(grid, row + dir[0], col + dir[1], visited, queue);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        ShortestBridge solution = new ShortestBridge();
        
        // Test case 1: [[0,1],[1,0]]
        int[][] grid1 = {{0,1},{1,0}};
        System.out.println("Shortest bridge: " + solution.shortestBridge(grid1)); // 1
        
        // Test case 2: [[0,1,0],[0,0,0],[0,0,1]]
        int[][] grid2 = {{0,1,0},{0,0,0},{0,0,1}};
        System.out.println("Shortest bridge: " + solution.shortestBridgeAlternative(grid2)); // 2
    }
}

