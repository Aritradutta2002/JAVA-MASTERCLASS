package dsa.datastructures.graphs.problems.hard;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Arrays;

/**
 * LeetCode #1293 - Shortest Path in a Grid with Obstacles Elimination
 * 
 * You are given an m x n integer matrix grid where grid[i][j] is either 0 (empty) or 1 (obstacle).
 * You can move up, down, left, or right from and to an empty cell in one step.
 * 
 * Return the minimum number of steps to walk from the upper-left corner (0, 0) to the
 * lower-right corner (m-1, n-1) given that you can eliminate at most k obstacles.
 * 
 * If it is not possible, return -1.
 * 
 * Time Complexity: O(M * N * k)
 * Space Complexity: O(M * N * k)
 */
public class ShortestPathObstacleElimination {
    public int shortestPath(int[][] grid, int k) {
        int rows = grid.length;
        int cols = grid[0].length;
        
        // This 'visited' array stores the max eliminations left
        // we've had when visiting this cell. -1 means unvisited.
        int[][] visited = new int[rows][cols];
        for (int[] row : visited) {
            Arrays.fill(row, -1);
        }
        
        // Queue stores {row, col, k_left, steps}
        Queue<int[]> queue = new LinkedList<>();
        
        // Start at (0, 0) with 'k' eliminations and 0 steps
        queue.offer(new int[]{0, 0, k, 0});
        visited[0][0] = k;
        
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int r = current[0];
            int c = current[1];
            int k_left = current[2];
            int steps = current[3];
            
            // Reached the end
            if (r == rows - 1 && c == cols - 1) {
                return steps;
            }
            
            // Explore neighbors
            for (int[] dir : directions) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                
                // Check bounds
                if (nr >= 0 && nr < rows && nc >= 0 && nc < cols) {
                    int next_k = k_left - grid[nr][nc];
                    
                    // If we have enough eliminations for this cell AND
                    // we haven't visited this cell before with more
                    // or equal eliminations left...
                    if (next_k >= 0 && next_k > visited[nr][nc]) {
                        visited[nr][nc] = next_k;
                        queue.offer(new int[]{nr, nc, next_k, steps + 1});
                    }
                }
            }
        }
        
        return -1; // No path found
    }
}

