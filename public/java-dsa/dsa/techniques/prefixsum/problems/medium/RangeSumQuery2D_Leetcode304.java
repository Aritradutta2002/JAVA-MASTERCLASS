package dsa.techniques.prefixsum.problems.medium;

/**
 * LeetCode 304: Range Sum Query 2D - Immutable
 * Difficulty: Medium
 * 
 * Problem: Given a 2D matrix, handle multiple queries of the following type:
 * Calculate the sum of the elements inside the rectangle defined by its 
 * upper left corner (row1, col1) and lower right corner (row2, col2).
 * 
 * Example:
 * matrix = [
 *   [3, 0, 1, 4, 2],
 *   [5, 6, 3, 2, 1],
 *   [1, 2, 0, 1, 5],
 *   [4, 1, 0, 1, 7],
 *   [1, 0, 3, 0, 5]
 * ]
 * sumRegion(2, 1, 4, 3) -> 8 (sum of red rectangle)
 * 
 * Time: O(m*n) preprocessing, O(1) per query
 * Space: O(m*n)
 */
public class RangeSumQuery2D_Leetcode304 {
    
    static class NumMatrix {
        private int[][] prefixSum;
        
        public NumMatrix(int[][] matrix) {
            int rows = matrix.length;
            int cols = matrix[0].length;
            
            // Create (rows+1) x (cols+1) prefix sum matrix to handle edge cases
            prefixSum = new int[rows + 1][cols + 1];
            
            // Build 2D prefix sum
            for (int i = 1; i <= rows; i++) {
                for (int j = 1; j <= cols; j++) {
                    prefixSum[i][j] = matrix[i-1][j-1] 
                                    + prefixSum[i-1][j] 
                                    + prefixSum[i][j-1] 
                                    - prefixSum[i-1][j-1];
                }
            }
        }
        
        public int sumRegion(int row1, int col1, int row2, int col2) {
            // Convert to 1-indexed coordinates for our prefix sum matrix
            row1++; col1++; row2++; col2++;
            
            return prefixSum[row2][col2] 
                 - prefixSum[row1-1][col2] 
                 - prefixSum[row2][col1-1] 
                 + prefixSum[row1-1][col1-1];
        }
    }
    
    /**
     * Alternative implementation without extra padding
     */
    static class NumMatrixAlternative {
        private int[][] prefixSum;
        private int rows, cols;
        
        public NumMatrixAlternative(int[][] matrix) {
            rows = matrix.length;
            cols = matrix[0].length;
            prefixSum = new int[rows][cols];
            
            // Build prefix sum matrix
            for (int i = 0; i < rows; i++) {
                for (int j = 0; j < cols; j++) {
                    prefixSum[i][j] = matrix[i][j];
                    
                    if (i > 0) prefixSum[i][j] += prefixSum[i-1][j];
                    if (j > 0) prefixSum[i][j] += prefixSum[i][j-1];
                    if (i > 0 && j > 0) prefixSum[i][j] -= prefixSum[i-1][j-1];
                }
            }
        }
        
        public int sumRegion(int row1, int col1, int row2, int col2) {
            int result = prefixSum[row2][col2];
            
            if (row1 > 0) result -= prefixSum[row1-1][col2];
            if (col1 > 0) result -= prefixSum[row2][col1-1];
            if (row1 > 0 && col1 > 0) result += prefixSum[row1-1][col1-1];
            
            return result;
        }
    }
    
    // Helper method to print matrix for visualization
    public static void printMatrix(int[][] matrix) {
        for (int[] row : matrix) {
            for (int val : row) {
                System.out.printf("%3d ", val);
            }
            System.out.println();
        }
    }
    
    public static void main(String[] args) {
        int[][] matrix = {
            {3, 0, 1, 4, 2},
            {5, 6, 3, 2, 1},
            {1, 2, 0, 1, 5},
            {4, 1, 0, 1, 7},
            {1, 0, 3, 0, 5}
        };
        
        System.out.println("Original Matrix:");
        printMatrix(matrix);
        System.out.println();
        
        NumMatrix numMatrix = new NumMatrix(matrix);
        
        // Test various range queries
        System.out.println("Range sum queries:");
        System.out.println("sumRegion(2, 1, 4, 3): " + numMatrix.sumRegion(2, 1, 4, 3)); // Expected: 8
        System.out.println("sumRegion(1, 1, 2, 2): " + numMatrix.sumRegion(1, 1, 2, 2)); // Expected: 11
        System.out.println("sumRegion(1, 2, 1, 4): " + numMatrix.sumRegion(1, 2, 1, 4)); // Expected: 6
        System.out.println("sumRegion(0, 0, 0, 0): " + numMatrix.sumRegion(0, 0, 0, 0)); // Expected: 3
        System.out.println("sumRegion(0, 0, 4, 4): " + numMatrix.sumRegion(0, 0, 4, 4)); // Expected: 58 (entire matrix)
        
        // Test alternative implementation
        NumMatrixAlternative alternative = new NumMatrixAlternative(matrix);
        System.out.println("\nUsing alternative implementation:");
        System.out.println("sumRegion(2, 1, 4, 3): " + alternative.sumRegion(2, 1, 4, 3)); // Expected: 8
    }
}
