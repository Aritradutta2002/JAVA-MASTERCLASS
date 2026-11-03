package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 304: RANGE SUM QUERY 2D - IMMUTABLE
 * Difficulty: Medium
 * Pattern: 2D Prefix Sum
 * 
 * PROBLEM STATEMENT:
 * Given a 2D matrix, handle multiple queries of the following type:
 * Calculate the sum of elements inside rectangle defined by 
 * top-left (row1, col1) and bottom-right (row2, col2).
 * 
 * EXAMPLE:
 * matrix = [
 *   [3, 0, 1, 4, 2],
 *   [5, 6, 3, 2, 1],
 *   [1, 2, 0, 1, 5],
 *   [4, 1, 0, 1, 7],
 *   [1, 0, 3, 0, 5]
 * ]
 * 
 * sumRegion(2,1,4,3) → 8
 * sumRegion(1,1,2,2) → 11
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH: 2D PREFIX SUM
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * Build prefix sum matrix where:
 * prefixSum[i][j] = sum of all elements from (0,0) to (i,j)
 * 
 * To find sum of rectangle:
 * sum = prefixSum[r2][c2]
 *     - prefixSum[r1-1][c2]     (remove top)
 *     - prefixSum[r2][c1-1]     (remove left)
 *     + prefixSum[r1-1][c1-1]   (add back overlap)
 * 
 * VISUAL:
 *     ┌─────────┬───┐
 *     │    A    │ B │
 *     ├─────────┼───┤
 *     │    C    │ D │ ← Target region
 *     └─────────┴───┘
 * 
 * D = Total - A - B + Overlap
 * 
 * TIME: 
 * - Constructor: O(m*n)
 * - Query: O(1)
 * SPACE: O(m*n)
 */

import java.util.*;

public class RangeSumQuery2D_Leetcode304 {
    private int[][] prefixSum;
    
    public RangeSumQuery2D_Leetcode304(int[][] matrix) {
        if (matrix == null || matrix.length == 0) {
            return;
        }
        
        int m = matrix.length;
        int n = matrix[0].length;
        
        // Build prefix sum with padding (1-indexed)
        prefixSum = new int[m + 1][n + 1];
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                prefixSum[i][j] = matrix[i-1][j-1]
                                + prefixSum[i-1][j]      // top
                                + prefixSum[i][j-1]      // left
                                - prefixSum[i-1][j-1];   // remove overlap
            }
        }
    }
    
    public int sumRegion(int row1, int col1, int row2, int col2) {
        // Convert to 1-indexed
        row1++; col1++; row2++; col2++;
        
        return prefixSum[row2][col2]
             - prefixSum[row1-1][col2]
             - prefixSum[row2][col1-1]
             + prefixSum[row1-1][col1-1];
    }
    
    public static void main(String[] args) {
        int[][] matrix = {
            {3, 0, 1, 4, 2},
            {5, 6, 3, 2, 1},
            {1, 2, 0, 1, 5},
            {4, 1, 0, 1, 7},
            {1, 0, 3, 0, 5}
        };
        
        RangeSumQuery2D_Leetcode304 solution = new RangeSumQuery2D_Leetcode304(matrix);
        System.out.println("sumRegion(2,1,4,3) = " + solution.sumRegion(2,1,4,3)); // 8
        System.out.println("sumRegion(1,1,2,2) = " + solution.sumRegion(1,1,2,2)); // 11
    }
}

/*
 * KEY TAKEAWAYS:
 * 1. 2D prefix sum enables O(1) range queries
 * 2. Use padding to avoid edge case checks
 * 3. Inclusion-exclusion principle for rectangle sum
 * 4. Perfect for immutable matrix with many queries
 */


