package dsa.datastructures.graphs.problems.hard;
import java.util.PriorityQueue;

/**
 * LeetCode #407 - Trapping Rain Water II
 * 
 * Given an m x n integer matrix heightMap representing the height of each unit cell in a 2D
 * elevation map, return the volume of water it can trap after raining.
 * 
 * Time Complexity: O(M*N log(M*N))
 * Space Complexity: O(M * N)
 */
public class TrapRainWaterII {
    public int trapRainWater(int[][] heightMap) {
        if (heightMap == null || heightMap.length < 3 || heightMap[0].length < 3) {
            return 0;
        }
        
        int rows = heightMap.length;
        int cols = heightMap[0].length;
        boolean[][] visited = new boolean[rows][cols];
        
        // Min-heap stores {height, row, col}
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        
        // Add all border cells
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (r == 0 || r == rows - 1 || c == 0 || c == cols - 1) {
                    pq.offer(new int[]{heightMap[r][c], r, c});
                    visited[r][c] = true;
                }
            }
        }
        
        int totalWater = 0;
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        // Process from the "lowest wall" inward
        while (!pq.isEmpty()) {
            int[] cell = pq.poll();
            int h = cell[0];
            int r = cell[1];
            int c = cell[2];
            
            for (int[] dir : directions) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                
                // Check bounds and if unvisited
                if (nr > 0 && nr < rows - 1 && nc > 0 && nc < cols - 1 && !visited[nr][nc]) {
                    visited[nr][nc] = true;
                    
                    // The water trapped is the difference between the
                    // current wall height and the cell's floor
                    totalWater += Math.max(0, h - heightMap[nr][nc]);
                    
                    // Add the new cell to the "wall", using its effective height
                    pq.offer(new int[]{Math.max(h, heightMap[nr][nc]), nr, nc});
                }
            }
        }
        
        return totalWater;
    }
}

