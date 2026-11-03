package dsa.datastructures.graphs.problems.hard;
/**
 * LeetCode #329 - Longest Increasing Path in a Matrix
 * 
 * Given an m x n integers matrix, return the length of the longest increasing path in matrix.
 * From each cell, you can move in four directions: left, right, up, or down. You cannot move
 * diagonally or move outside the boundary (i.e., wrap-around is not allowed).
 * 
 * Time Complexity: O(M * N)
 * Space Complexity: O(M * N)
 */
public class LongestIncreasingPath {
    int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
    int rows, cols;
    
    public int longestIncreasingPath(int[][] matrix) {
        if (matrix == null || matrix.length == 0) return 0;
        
        rows = matrix.length;
        cols = matrix[0].length;
        
        // cache[r][c] stores the LIP starting from (r,c)
        int[][] cache = new int[rows][cols];
        int maxPath = 0;
        
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                maxPath = Math.max(maxPath, dfs(matrix, r, c, cache));
            }
        }
        
        return maxPath;
    }
    
    // Returns the LIP starting from (r, c)
    private int dfs(int[][] matrix, int r, int c, int[][] cache) {
        // If already computed, return it
        if (cache[r][c] > 0) {
            return cache[r][c];
        }
        
        // My own path is at least 1 (myself)
        int myPath = 1;
        
        for (int[] dir : directions) {
            int nr = r + dir[0];
            int nc = c + dir[1];
            
            // Check bounds and if neighbor is larger
            if (nr >= 0 && nr < rows && nc >= 0 && nc < cols && matrix[nr][nc] > matrix[r][c]) {
                // My path is 1 + the longest path from that neighbor
                myPath = Math.max(myPath, 1 + dfs(matrix, nr, nc, cache));
            }
        }
        
        // Store in cache and return
        cache[r][c] = myPath;
        return myPath;
    }
}

