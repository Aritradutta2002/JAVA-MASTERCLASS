package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 417: Pacific Atlantic Water Flow
 * 
 * Problem: There is an m x n rectangular island that borders both the Pacific Ocean 
 * and Atlantic Ocean. The Pacific Ocean touches the island's left and top edges, 
 * and the Atlantic Ocean touches the island's right and bottom edges.
 * 
 * Return a 2D list of grid coordinates result where result[i] = [ri, ci] denotes 
 * that rain water can flow from cell (ri, ci) to both the Pacific and Atlantic oceans.
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(m * n)
 */
public class PacificAtlanticWaterFlow {
    
    /**
     * DFS solution starting from ocean borders
     */
    public List<List<Integer>> pacificAtlantic(int[][] heights) {
        List<List<Integer>> result = new ArrayList<>();
        if (heights == null || heights.length == 0) return result;
        
        int rows = heights.length;
        int cols = heights[0].length;
        
        boolean[][] pacific = new boolean[rows][cols];
        boolean[][] atlantic = new boolean[rows][cols];
        
        // DFS from Pacific borders (top and left)
        for (int i = 0; i < rows; i++) {
            dfs(heights, pacific, i, 0, heights[i][0]); // Left border
            dfs(heights, atlantic, i, cols - 1, heights[i][cols - 1]); // Right border
        }
        
        for (int j = 0; j < cols; j++) {
            dfs(heights, pacific, 0, j, heights[0][j]); // Top border
            dfs(heights, atlantic, rows - 1, j, heights[rows - 1][j]); // Bottom border
        }
        
        // Find cells that can reach both oceans
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (pacific[i][j] && atlantic[i][j]) {
                    result.add(Arrays.asList(i, j));
                }
            }
        }
        
        return result;
    }
    
    private void dfs(int[][] heights, boolean[][] visited, int row, int col, int prevHeight) {
        // Check bounds, visited status, and height condition
        if (row < 0 || row >= heights.length || 
            col < 0 || col >= heights[0].length || 
            visited[row][col] || heights[row][col] < prevHeight) {
            return;
        }
        
        visited[row][col] = true;
        
        // Explore all 4 directions
        dfs(heights, visited, row + 1, col, heights[row][col]);
        dfs(heights, visited, row - 1, col, heights[row][col]);
        dfs(heights, visited, row, col + 1, heights[row][col]);
        dfs(heights, visited, row, col - 1, heights[row][col]);
    }
    
    /**
     * BFS solution
     */
    public List<List<Integer>> pacificAtlanticBFS(int[][] heights) {
        List<List<Integer>> result = new ArrayList<>();
        if (heights == null || heights.length == 0) return result;
        
        int rows = heights.length;
        int cols = heights[0].length;
        
        boolean[][] pacific = new boolean[rows][cols];
        boolean[][] atlantic = new boolean[rows][cols];
        
        Queue<int[]> pacificQueue = new LinkedList<>();
        Queue<int[]> atlanticQueue = new LinkedList<>();
        
        // Add border cells to respective queues
        for (int i = 0; i < rows; i++) {
            pacificQueue.offer(new int[]{i, 0});
            pacific[i][0] = true;
            
            atlanticQueue.offer(new int[]{i, cols - 1});
            atlantic[i][cols - 1] = true;
        }
        
        for (int j = 0; j < cols; j++) {
            pacificQueue.offer(new int[]{0, j});
            pacific[0][j] = true;
            
            atlanticQueue.offer(new int[]{rows - 1, j});
            atlantic[rows - 1][j] = true;
        }
        
        // BFS from Pacific and Atlantic
        bfs(heights, pacificQueue, pacific);
        bfs(heights, atlanticQueue, atlantic);
        
        // Find intersection
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (pacific[i][j] && atlantic[i][j]) {
                    result.add(Arrays.asList(i, j));
                }
            }
        }
        
        return result;
    }
    
    private void bfs(int[][] heights, Queue<int[]> queue, boolean[][] visited) {
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            
            for (int[] dir : directions) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < heights.length && 
                    newCol >= 0 && newCol < heights[0].length && 
                    !visited[newRow][newCol] && 
                    heights[newRow][newCol] >= heights[row][col]) {
                    
                    visited[newRow][newCol] = true;
                    queue.offer(new int[]{newRow, newCol});
                }
            }
        }
    }
    
    // Test method
    public static void main(String[] args) {
        PacificAtlanticWaterFlow solution = new PacificAtlanticWaterFlow();
        
        int[][] heights = {
            {1, 2, 2, 3, 5},
            {3, 2, 3, 4, 4},
            {2, 4, 5, 3, 1},
            {6, 7, 1, 4, 5},
            {5, 1, 1, 2, 4}
        };
        
        System.out.println("Heights matrix:");
        for (int[] row : heights) {
            System.out.println(Arrays.toString(row));
        }
        
        List<List<Integer>> result = solution.pacificAtlantic(heights);
        System.out.println("Cells that can reach both oceans: " + result);
        
        List<List<Integer>> resultBFS = solution.pacificAtlanticBFS(heights);
        System.out.println("BFS result: " + resultBFS);
    }
}

