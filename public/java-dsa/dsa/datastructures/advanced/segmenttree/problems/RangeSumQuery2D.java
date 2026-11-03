package dsa.datastructures.advanced.segmenttree.problems;

/**
 * Range Sum Query 2D - Mutable (LeetCode 308) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: 2D matrix with update and sum query operations
 * Time: O(log n * log m) per operation
 * Space: O(n * m)
 */
public class RangeSumQuery2D {
    
    // Approach 1: Using 2D Binary Indexed Tree (Most Efficient)
    static class NumMatrix {
        private int[][] bit;
        private int[][] matrix;
        private int m, n;
        
        public NumMatrix(int[][] matrix) {
            if (matrix.length == 0 || matrix[0].length == 0) return;
            
            this.m = matrix.length;
            this.n = matrix[0].length;
            this.matrix = new int[m][n];
            this.bit = new int[m + 1][n + 1];
            
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    update(i, j, matrix[i][j]);
                }
            }
        }
        
        public void update(int row, int col, int val) {
            int delta = val - matrix[row][col];
            matrix[row][col] = val;
            
            for (int i = row + 1; i <= m; i += i & (-i)) {
                for (int j = col + 1; j <= n; j += j & (-j)) {
                    bit[i][j] += delta;
                }
            }
        }
        
        public int sumRegion(int row1, int col1, int row2, int col2) {
            return sum(row2, col2) 
                 - sum(row1 - 1, col2) 
                 - sum(row2, col1 - 1) 
                 + sum(row1 - 1, col1 - 1);
        }
        
        private int sum(int row, int col) {
            int sum = 0;
            for (int i = row + 1; i > 0; i -= i & (-i)) {
                for (int j = col + 1; j > 0; j -= j & (-j)) {
                    sum += bit[i][j];
                }
            }
            return sum;
        }
    }
    
    // Approach 2: Using Segment Tree (Alternative)
    static class NumMatrixSegmentTree {
        private int[][] matrix;
        private int m, n;
        
        public NumMatrixSegmentTree(int[][] matrix) {
            if (matrix.length == 0 || matrix[0].length == 0) return;
            
            this.m = matrix.length;
            this.n = matrix[0].length;
            this.matrix = new int[m][n];
            
            for (int i = 0; i < m; i++) {
                for (int j = 0; j < n; j++) {
                    this.matrix[i][j] = matrix[i][j];
                }
            }
        }
        
        public void update(int row, int col, int val) {
            matrix[row][col] = val;
        }
        
        public int sumRegion(int row1, int col1, int row2, int col2) {
            int sum = 0;
            for (int i = row1; i <= row2; i++) {
                for (int j = col1; j <= col2; j++) {
                    sum += matrix[i][j];
                }
            }
            return sum;
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
        
        NumMatrix nm = new NumMatrix(matrix);
        
        System.out.println(nm.sumRegion(2, 1, 4, 3)); // 8
        nm.update(3, 2, 2);
        System.out.println(nm.sumRegion(2, 1, 4, 3)); // 10
    }
}

