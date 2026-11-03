package dsa.techniques.prefixsum.problems.hard;
import java.util.*;

/**
 * Maximum Sum Rectangle in 2D Array (Kadane's Algorithm Extension)
 * Difficulty: Hard
 * 
 * Problem: Given a 2D array, find the rectangle with the maximum sum.
 * This is an extension of the maximum subarray problem to 2D.
 * 
 * Example:
 * matrix = [
 *   [ 1,  2, -1, -4, -20],
 *   [-8, -3,  4,  2,   1],
 *   [ 3,  8, 10,  1,   3],
 *   [-4, -1,  1,  7,  -6]
 * ]
 * Output: Maximum sum rectangle has sum 29 (from [1][2] to [2][4])
 * 
 * Approach: For each pair of rows, compress the matrix to 1D and apply Kadane's algorithm
 * Time: O(rows² * cols)
 * Space: O(cols)
 */
public class MaxSumRectangle_Classic {
    
    static class Result {
        int maxSum;
        int topRow, bottomRow, leftCol, rightCol;
        
        Result(int sum, int top, int bottom, int left, int right) {
            this.maxSum = sum;
            this.topRow = top;
            this.bottomRow = bottom;
            this.leftCol = left;
            this.rightCol = right;
        }
        
        @Override
        public String toString() {
            return String.format("Max Sum: %d, Rectangle: [%d,%d] to [%d,%d]", 
                               maxSum, topRow, leftCol, bottomRow, rightCol);
        }
    }
    
    /**
     * Find maximum sum rectangle using Kadane's algorithm extension
     */
    public static Result maxSumRectangle(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        
        int maxSum = Integer.MIN_VALUE;
        int finalTop = 0, finalBottom = 0, finalLeft = 0, finalRight = 0;
        
        // Try all pairs of rows
        for (int top = 0; top < rows; top++) {
            int[] temp = new int[cols];
            
            for (int bottom = top; bottom < rows; bottom++) {
                // Add current row to temp array
                for (int i = 0; i < cols; i++) {
                    temp[i] += matrix[bottom][i];
                }
                
                // Apply Kadane's algorithm on temp array
                KadaneResult kadaneResult = kadaneWithIndices(temp);
                
                if (kadaneResult.maxSum > maxSum) {
                    maxSum = kadaneResult.maxSum;
                    finalTop = top;
                    finalBottom = bottom;
                    finalLeft = kadaneResult.start;
                    finalRight = kadaneResult.end;
                }
            }
        }
        
        return new Result(maxSum, finalTop, finalBottom, finalLeft, finalRight);
    }
    
    static class KadaneResult {
        int maxSum, start, end;
        
        KadaneResult(int sum, int s, int e) {
            this.maxSum = sum;
            this.start = s;
            this.end = e;
        }
    }
    
    /**
     * Kadane's algorithm that also returns start and end indices
     */
    private static KadaneResult kadaneWithIndices(int[] arr) {
        int maxSum = Integer.MIN_VALUE;
        int currentSum = 0;
        int start = 0, end = 0, tempStart = 0;
        
        for (int i = 0; i < arr.length; i++) {
            currentSum += arr[i];
            
            if (currentSum > maxSum) {
                maxSum = currentSum;
                start = tempStart;
                end = i;
            }
            
            if (currentSum < 0) {
                currentSum = 0;
                tempStart = i + 1;
            }
        }
        
        return new KadaneResult(maxSum, start, end);
    }
    
    /**
     * Alternative approach using prefix sums (for educational purposes)
     */
    public static Result maxSumRectanglePrefix(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        
        // Build 2D prefix sum
        int[][] prefixSum = new int[rows + 1][cols + 1];
        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= cols; j++) {
                prefixSum[i][j] = matrix[i-1][j-1] + prefixSum[i-1][j] 
                                + prefixSum[i][j-1] - prefixSum[i-1][j-1];
            }
        }
        
        int maxSum = Integer.MIN_VALUE;
        int bestTop = 0, bestBottom = 0, bestLeft = 0, bestRight = 0;
        
        // Try all possible rectangles
        for (int top = 0; top < rows; top++) {
            for (int bottom = top; bottom < rows; bottom++) {
                for (int left = 0; left < cols; left++) {
                    for (int right = left; right < cols; right++) {
                        int sum = prefixSum[bottom + 1][right + 1] 
                                - prefixSum[top][right + 1] 
                                - prefixSum[bottom + 1][left] 
                                + prefixSum[top][left];
                        
                        if (sum > maxSum) {
                            maxSum = sum;
                            bestTop = top;
                            bestBottom = bottom;
                            bestLeft = left;
                            bestRight = right;
                        }
                    }
                }
            }
        }
        
        return new Result(maxSum, bestTop, bestBottom, bestLeft, bestRight);
    }
    
    /**
     * Helper method to print matrix with highlighting
     */
    public static void printMatrixWithRectangle(int[][] matrix, Result result) {
        System.out.println("Matrix with maximum rectangle highlighted:");
        for (int i = 0; i < matrix.length; i++) {
            for (int j = 0; j < matrix[0].length; j++) {
                boolean inRectangle = (i >= result.topRow && i <= result.bottomRow && 
                                     j >= result.leftCol && j <= result.rightCol);
                if (inRectangle) {
                    System.out.printf("[%3d]", matrix[i][j]);
                } else {
                    System.out.printf(" %3d ", matrix[i][j]);
                }
            }
            System.out.println();
        }
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[][] matrix1 = {
            { 1,  2, -1, -4, -20},
            {-8, -3,  4,  2,   1},
            { 3,  8, 10,  1,   3},
            {-4, -1,  1,  7,  -6}
        };
        
        System.out.println("Test Case 1:");
        Result result1 = maxSumRectangle(matrix1);
        System.out.println(result1);
        printMatrixWithRectangle(matrix1, result1);
        System.out.println();
        
        // Test case 2 - All negative
        int[][] matrix2 = {
            {-1, -2, -3},
            {-4, -5, -6},
            {-7, -8, -9}
        };
        
        System.out.println("Test Case 2 (All negative):");
        Result result2 = maxSumRectangle(matrix2);
        System.out.println(result2);
        printMatrixWithRectangle(matrix2, result2);
        System.out.println();
        
        // Test case 3 - Single positive element
        int[][] matrix3 = {
            {-1, -2,  5},
            {-4, -3, -6},
            {-7, -8, -9}
        };
        
        System.out.println("Test Case 3 (Single positive):");
        Result result3 = maxSumRectangle(matrix3);
        System.out.println(result3);
        printMatrixWithRectangle(matrix3, result3);
        System.out.println();
        
        // Performance comparison
        long start1 = System.nanoTime();
        maxSumRectangle(matrix1);
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        maxSumRectanglePrefix(matrix1);
        long end2 = System.nanoTime();
        
        System.out.println("Performance Comparison:");
        System.out.println("Kadane approach: " + (end1 - start1) + " ns");
        System.out.println("Prefix sum approach: " + (end2 - start2) + " ns");
    }
}
