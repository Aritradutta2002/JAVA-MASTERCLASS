package dsa.datastructures.graphs.problems.hard;
import java.util.PriorityQueue;

/**
 * LeetCode #778 - Swim in Rising Water
 * 
 * You are given an n x n integer matrix grid where grid[i][j] represents the elevation at that point.
 * The rain starts to fall. At time t, the depth of the water everywhere is t. You can swim from a
 * square to another 4-directionally adjacent square if and only if the elevation of both squares
 * is at most t.
 * 
 * You can start at (0, 0) at time 0. Return the least time t such that you can reach (n-1, n-1).
 * 
 * Time Complexity: O(N^2 log N)
 * Space Complexity: O(N^2)
 */
public class SwimInRisingWater {
    public int swimInWater(int[][] grid) {
        int n = grid.length;
        
        // Min-heap stores {max_height_on_path, row, col}
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        boolean[][] visited = new boolean[n][n];
        
        // Start at (0, 0). The max_height so far is just its own height.
        pq.offer(new int[]{grid[0][0], 0, 0});
        visited[0][0] = true;
        
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int max_h = current[0];
            int r = current[1];
            int c = current[2];
            
            // If we reached the end, we're done.
            // The PQ guarantees this is the path with the minimum 'max_height'.
            if (r == n - 1 && c == n - 1) {
                return max_h;
            }
            
            for (int[] dir : directions) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                
                // Check bounds and if unvisited
                if (nr >= 0 && nr < n && nc >= 0 && nc < n && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    
                    // The "cost" of this new path is the max of the
                    // old path's cost and the new cell's height.
                    int new_max_h = Math.max(max_h, grid[nr][nc]);
                    pq.offer(new int[]{new_max_h, nr, nc});
                }
            }
        }
        
        return -1; // Unreachable
    }
}

