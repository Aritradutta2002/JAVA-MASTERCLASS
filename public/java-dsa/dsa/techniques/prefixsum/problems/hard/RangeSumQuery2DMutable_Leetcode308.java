
package dsa.techniques.prefixsum.problems.hard;

/**
 * LeetCode 308: Range Sum Query 2D - Mutable
 * Difficulty: Hard
 * 
 * Problem:
 * Given a 2D matrix, handle two types of queries:
 * 1. update(row, col, val): Update the value at (row, col) to val.
 * 2. sumRegion(row1, col1, row2, col2): Find the sum of the submatrix defined by the top-left (row1, col1) and bottom-right (row2, col2).
 *
 * Key Insight:
 * This problem is a classic application of a 2D Binary Indexed Tree (BIT), also known as a Fenwick Tree.
 * A standard 2D prefix sum array would allow for O(1) `sumRegion` queries, but `update` would take O(R*C) time.
 * A 2D BIT provides a balance, allowing both `update` and `sumRegion` queries to be performed in logarithmic time.
 *
 * How a 2D BIT works:
 * - Just like a 1D BIT, a 2D BIT stores prefix sums in a tree-like structure, but in two dimensions.
 * - `update(row, col, val)`: When a value at `(row, col)` changes, we calculate the `delta` (new value - old value).
 *   We then propagate this `delta` to all cells in the BIT that are responsible for `(row, col)`. This is done by following the BIT's update logic (i += i & -i) in both dimensions.
 * - `getSum(row, col)`: This function returns the sum of the rectangle from (0,0) to (row, col). It works by traversing "down and left" in the BIT, summing up the values (i -= i & -i).
 * - `sumRegion(r1, c1, r2, c2)`: This can be calculated using the `getSum` function and the principle of inclusion-exclusion, identical to how it's done with a 2D prefix sum array:
 *   `sum = getSum(r2, c2) - getSum(r1-1, c2) - getSum(r2, c1-1) + getSum(r1-1, c1-1)`.
 *   (Note: The code uses 1-based indexing for the BIT, so the formula looks slightly different but the principle is the same).
 *
 * Time Complexity:
 *  - Constructor: O(R * C * log R * log C), because it calls `update` for each of the R*C cells.
 *  - update: O(log R * log C), where R and C are the number of rows and columns.
 *  - sumRegion: O(log R * log C).
 * Space Complexity: O(R * C) to store the BIT and a copy of the matrix.
 */
public class RangeSumQuery2DMutable_Leetcode308 {

    private int[][] matrix; // Store the original matrix to calculate deltas for updates.
    private int[][] bit;    // The 2D Binary Indexed Tree.
    private int rows, cols;

    public RangeSumQuery2DMutable_Leetcode308(int[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return;
        }
        this.rows = matrix.length;
        this.cols = matrix[0].length;
        this.matrix = new int[rows][cols];
        // BIT is 1-indexed, so we need one extra row and column.
        this.bit = new int[rows + 1][cols + 1];
        
        // Initialize the BIT by "updating" every cell from the initial matrix.
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                update(i, j, matrix[i][j]);
            }
        }
    }

    /**
     * Updates the value of a cell and propagates the change through the BIT.
     */
    public void update(int row, int col, int val) {
        int delta = val - matrix[row][col];
        matrix[row][col] = val;
        
        // BIT operations use 1-based indexing.
        for (int i = row + 1; i <= rows; i += i & -i) {
            for (int j = col + 1; j <= cols; j += j & -j) {
                bit[i][j] += delta;
            }
        }
    }

    /**
     * Calculates the sum of the region using the principle of inclusion-exclusion.
     */
    public int sumRegion(int row1, int col1, int row2, int col2) {
        // Sum(ABCD) = Sum(OD) - Sum(OB) - Sum(OC) + Sum(OA)
        // where O is origin, A=(r1-1,c1-1), B=(r1-1,c2), C=(r2,c1-1), D=(r2,c2)
        return getSum(row2 + 1, col2 + 1) - getSum(row1, col2 + 1) - getSum(row2 + 1, col1) + getSum(row1, col1);
    }

    /**
     * Helper function to get the prefix sum of the rectangle from (0,0) to (row-1, col-1).
     */
    private int getSum(int row, int col) {
        int sum = 0;
        // BIT operations use 1-based indexing.
        for (int i = row; i > 0; i -= i & -i) {
            for (int j = col; j > 0; j -= j & -j) {
                sum += bit[i][j];
            }
        }
        return sum;
    }

    public static void main(String[] args) {
        int[][] matrix = {
            {3, 0, 1, 4, 2},
            {5, 6, 3, 2, 1},
            {1, 2, 0, 1, 5},
            {4, 1, 0, 1, 7},
            {1, 0, 3, 0, 5}
        };

        RangeSumQuery2DMutable_Leetcode308 numMatrix = new RangeSumQuery2DMutable_Leetcode308(matrix);
        System.out.println("Initial Matrix setup complete.");
        System.out.println("Sum of region (2,1) to (4,3): " + numMatrix.sumRegion(2, 1, 4, 3)); // Expected: 8
        System.out.println("---");
        
        numMatrix.update(3, 2, 2);
        System.out.println("Updated matrix at (3,2) to 2.");
        System.out.println("Sum of region (2,1) to (4,3) after update: " + numMatrix.sumRegion(2, 1, 4, 3)); // Expected: 10
        System.out.println("---");
    }
}

