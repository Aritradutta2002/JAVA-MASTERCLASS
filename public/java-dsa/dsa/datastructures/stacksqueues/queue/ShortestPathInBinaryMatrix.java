package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #1091 - Shortest Path in Binary Matrix (Medium)
 *   Problem: Find shortest path from top-left to bottom-right (BFS)
 *   Time Complexity: O(n^2), Space Complexity: O(n^2)
 */

public class ShortestPathInBinaryMatrix {
    public static void main(String[] args) {
        ShortestPathInBinaryMatrix solution = new ShortestPathInBinaryMatrix();
        
        int[][] grid1 = {{0, 1}, {1, 0}};
        System.out.println("Grid 1 - Shortest path length: " + solution.shortestPathBinaryMatrix(grid1));
        // Expected: 2
        
        int[][] grid2 = {{0, 0, 0}, {1, 1, 0}, {1, 1, 0}};
        System.out.println("Grid 2 - Shortest path length: " + solution.shortestPathBinaryMatrix(grid2));
        // Expected: 4
        
        int[][] grid3 = {{1, 0, 0}, {1, 1, 0}, {1, 1, 0}};
        System.out.println("Grid 3 - Shortest path length: " + solution.shortestPathBinaryMatrix(grid3));
        // Expected: -1 (no path)
    }
    
    public int shortestPathBinaryMatrix(int[][] grid) {
        int n = grid.length;
        
        // Check if start or end is blocked
        if (grid[0][0] == 1 || grid[n - 1][n - 1] == 1) {
            return -1;
        }
        
        // 8 directions: up, down, left, right, and 4 diagonals
        int[][] directions = {
            {-1, -1}, {-1, 0}, {-1, 1},
            {0, -1},           {0, 1},
            {1, -1},  {1, 0},  {1, 1}
        };
        
        Queue<int[]> queue = new LinkedList<>();
        queue.offer(new int[]{0, 0, 1}); // row, col, distance
        grid[0][0] = 1; // mark as visited
        
        while (!queue.isEmpty()) {
            int[] curr = queue.poll();
            int row = curr[0];
            int col = curr[1];
            int dist = curr[2];
            
            // Check if reached destination
            if (row == n - 1 && col == n - 1) {
                return dist;
            }
            
            // Explore all 8 directions
            for (int[] dir : directions) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < n && newCol >= 0 && newCol < n 
                    && grid[newRow][newCol] == 0) {
                    queue.offer(new int[]{newRow, newCol, dist + 1});
                    grid[newRow][newCol] = 1; // mark as visited
                }
            }
        }
        
        return -1; // no path found
    }
}

