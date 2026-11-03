package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #994 - Rotting Oranges (Medium)
 *   Problem: Find minimum time for all oranges to rot (Multi-source BFS)
 *   Time Complexity: O(m*n), Space Complexity: O(m*n)
 */

public class RottenOranges {
    public static void main(String[] args) {
        RottenOranges solution = new RottenOranges();
        
        int[][] grid1 = {{2, 1, 1}, {1, 1, 0}, {0, 1, 1}};
        System.out.println("Test 1 - Minutes needed: " + solution.orangesRotting(grid1));
        // Expected: 4
        
        int[][] grid2 = {{2, 1, 1}, {0, 1, 1}, {1, 0, 1}};
        System.out.println("Test 2 - Minutes needed: " + solution.orangesRotting(grid2));
        // Expected: -1 (impossible)
        
        int[][] grid3 = {{0, 2}};
        System.out.println("Test 3 - Minutes needed: " + solution.orangesRotting(grid3));
        // Expected: 0
    }
    
    public int orangesRotting(int[][] grid) {
        if (grid == null || grid.length == 0) {
            return 0;
        }
        
        int rows = grid.length;
        int cols = grid[0].length;
        Queue<int[]> queue = new LinkedList<>();
        int freshCount = 0;
        
        // Add all rotten oranges to queue and count fresh oranges
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (grid[i][j] == 2) {
                    queue.offer(new int[]{i, j});
                } else if (grid[i][j] == 1) {
                    freshCount++;
                }
            }
        }
        
        // If no fresh oranges, return 0
        if (freshCount == 0) {
            return 0;
        }
        
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        int minutes = 0;
        
        // BFS
        while (!queue.isEmpty()) {
            int size = queue.size();
            boolean hasRotten = false;
            
            for (int i = 0; i < size; i++) {
                int[] pos = queue.poll();
                int row = pos[0];
                int col = pos[1];
                
                for (int[] dir : directions) {
                    int newRow = row + dir[0];
                    int newCol = col + dir[1];
                    
                    if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < cols 
                        && grid[newRow][newCol] == 1) {
                        grid[newRow][newCol] = 2;
                        queue.offer(new int[]{newRow, newCol});
                        freshCount--;
                        hasRotten = true;
                    }
                }
            }
            
            if (hasRotten) {
                minutes++;
            }
        }
        
        return freshCount == 0 ? minutes : -1;
    }
}

