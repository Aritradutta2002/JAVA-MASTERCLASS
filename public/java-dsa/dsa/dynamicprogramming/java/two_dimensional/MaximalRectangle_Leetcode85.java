package JAVA_DSA.DYNAMIC_PROGRAMMING.java.two_dimensional;

import java.util.*;

/**
 * MAXIMAL RECTANGLE - Leetcode 85
 * Difficulty: Hard
 * Pattern: DP on Grid + Stack (Largest Rectangle in Histogram)
 * 
 * PROBLEM:
 * Given a rows x cols binary matrix filled with 0's and 1's, find the largest rectangle 
 * containing only 1's and return its area.
 * 
 * EXAMPLES:
 * Input: matrix = [["1","0","1","0","0"],
 *                  ["1","0","1","1","1"],
 *                  ["1","1","1","1","1"],
 *                  ["1","0","0","1","0"]]
 * Output: 6
 * Explanation: The maximal rectangle is shown in the matrix.
 * 
 * KEY INSIGHT:
 * Transform to "Largest Rectangle in Histogram" for each row.
 * For each row, calculate heights of consecutive 1's ending at that row.
 */
public class MaximalRectangle_Leetcode85 {
    
    /**
     * APPROACH 1: DP + Stack (Optimal)
     * Time: O(rows * cols), Space: O(cols)
     */
    public int maximalRectangle(char[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return 0;
        }
        
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[] heights = new int[cols];
        int maxArea = 0;
        
        for (int i = 0; i < rows; i++) {
            // Update heights for current row
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    heights[j]++;
                } else {
                    heights[j] = 0;
                }
            }
            
            // Find max rectangle in current histogram
            maxArea = Math.max(maxArea, largestRectangleInHistogram(heights));
        }
        
        return maxArea;
    }
    
    /**
     * Helper: Largest Rectangle in Histogram using Stack
     * Time: O(n), Space: O(n)
     */
    private int largestRectangleInHistogram(int[] heights) {
        Stack<Integer> stack = new Stack<>();
        int maxArea = 0;
        int n = heights.length;
        
        for (int i = 0; i <= n; i++) {
            int h = (i == n) ? 0 : heights[i];
            
            while (!stack.isEmpty() && h < heights[stack.peek()]) {
                int height = heights[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, height * width);
            }
            
            stack.push(i);
        }
        
        return maxArea;
    }
    
    /**
     * APPROACH 2: DP with Left/Right Arrays
     * Time: O(rows * cols), Space: O(cols)
     */
    public int maximalRectangle_DP(char[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return 0;
        }
        
        int rows = matrix.length;
        int cols = matrix[0].length;
        
        int[] heights = new int[cols];
        int[] left = new int[cols];    // Left boundary of rectangle at each position
        int[] right = new int[cols];   // Right boundary of rectangle at each position
        Arrays.fill(right, cols);
        
        int maxArea = 0;
        
        for (int i = 0; i < rows; i++) {
            int currentLeft = 0, currentRight = cols;
            
            // Update heights
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    heights[j]++;
                } else {
                    heights[j] = 0;
                }
            }
            
            // Update left boundaries
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    left[j] = Math.max(left[j], currentLeft);
                } else {
                    left[j] = 0;
                    currentLeft = j + 1;
                }
            }
            
            // Update right boundaries
            for (int j = cols - 1; j >= 0; j--) {
                if (matrix[i][j] == '1') {
                    right[j] = Math.min(right[j], currentRight);
                } else {
                    right[j] = cols;
                    currentRight = j;
                }
            }
            
            // Calculate max area for current row
            for (int j = 0; j < cols; j++) {
                maxArea = Math.max(maxArea, (right[j] - left[j]) * heights[j]);
            }
        }
        
        return maxArea;
    }
    
    /**
     * APPROACH 3: Brute Force with DP preprocessing
     * Time: O(rows * cols^2), Space: O(rows * cols)
     */
    public int maximalRectangle_BruteForce(char[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return 0;
        }
        
        int rows = matrix.length;
        int cols = matrix[0].length;
        
        // dp[i][j] = number of consecutive 1's ending at (i, j) in row i
        int[][] dp = new int[rows][cols];
        
        // Fill DP table
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    dp[i][j] = (j == 0) ? 1 : dp[i][j - 1] + 1;
                }
            }
        }
        
        int maxArea = 0;
        
        // For each cell, try to form rectangles
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    int minWidth = dp[i][j];
                    
                    // Extend rectangle downwards
                    for (int k = i; k < rows && matrix[k][j] == '1'; k++) {
                        minWidth = Math.min(minWidth, dp[k][j]);
                        int area = minWidth * (k - i + 1);
                        maxArea = Math.max(maxArea, area);
                    }
                }
            }
        }
        
        return maxArea;
    }
    
    /**
     * Helper method to visualize the matrix and solution
     */
    public void visualizeMatrix(char[][] matrix) {
        System.out.println("Input Matrix:");
        for (char[] row : matrix) {
            System.out.println("  " + Arrays.toString(row));
        }
        
        // Show heights for each row
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[] heights = new int[cols];
        
        System.out.println("\nHeights histogram for each row:");
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    heights[j]++;
                } else {
                    heights[j] = 0;
                }
            }
            System.out.println("Row " + i + ": " + Arrays.toString(heights) + 
                             " -> Max Rectangle: " + largestRectangleInHistogram(heights.clone()));
        }
    }
    
    /**
     * Method to find coordinates of the maximal rectangle
     */
    public int[] findMaximalRectangleCoordinates(char[][] matrix) {
        if (matrix == null || matrix.length == 0 || matrix[0].length == 0) {
            return new int[]{0, 0, 0, 0, 0}; // {area, top, left, bottom, right}
        }
        
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[] heights = new int[cols];
        int maxArea = 0;
        int[] result = new int[5]; // {area, top, left, bottom, right}
        
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (matrix[i][j] == '1') {
                    heights[j]++;
                } else {
                    heights[j] = 0;
                }
            }
            
            int[] rectInfo = largestRectangleWithCoordinates(heights, i);
            if (rectInfo[0] > maxArea) {
                maxArea = rectInfo[0];
                result = rectInfo.clone();
            }
        }
        
        return result;
    }
    
    private int[] largestRectangleWithCoordinates(int[] heights, int currentRow) {
        Stack<Integer> stack = new Stack<>();
        int maxArea = 0;
        int[] result = new int[5]; // {area, top, left, bottom, right}
        int n = heights.length;
        
        for (int i = 0; i <= n; i++) {
            int h = (i == n) ? 0 : heights[i];
            
            while (!stack.isEmpty() && h < heights[stack.peek()]) {
                int height = heights[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                int area = height * width;
                
                if (area > maxArea) {
                    maxArea = area;
                    int left = stack.isEmpty() ? 0 : stack.peek() + 1;
                    int right = i - 1;
                    int top = currentRow - height + 1;
                    int bottom = currentRow;
                    
                    result = new int[]{area, top, left, bottom, right};
                }
            }
            
            stack.push(i);
        }
        
        return result;
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        MaximalRectangle_Leetcode85 solution = new MaximalRectangle_Leetcode85();
        
        System.out.println("=== Maximal Rectangle Problem ===");
        
        // Test case 1
        char[][] matrix1 = {
            {'1','0','1','0','0'},
            {'1','0','1','1','1'},
            {'1','1','1','1','1'},
            {'1','0','0','1','0'}
        };
        
        solution.visualizeMatrix(matrix1);
        System.out.println("\nMaximal Rectangle Area (Stack): " + solution.maximalRectangle(matrix1));
        System.out.println("Maximal Rectangle Area (DP): " + solution.maximalRectangle_DP(matrix1));
        System.out.println("Maximal Rectangle Area (Brute Force): " + solution.maximalRectangle_BruteForce(matrix1));
        
        int[] coords = solution.findMaximalRectangleCoordinates(matrix1);
        System.out.println("Maximal Rectangle Details:");
        System.out.println("  Area: " + coords[0]);
        System.out.println("  Coordinates: (" + coords[1] + "," + coords[2] + ") to (" + coords[3] + "," + coords[4] + ")");
        System.out.println();
        
        // Test case 2
        char[][] matrix2 = {
            {'0','1'},
            {'1','0'}
        };
        System.out.println("Matrix 2:");
        solution.visualizeMatrix(matrix2);
        System.out.println("Maximal Rectangle Area: " + solution.maximalRectangle(matrix2));
        System.out.println();
        
        // Test case 3
        char[][] matrix3 = {
            {'1','1','1','1'},
            {'1','1','1','1'},
            {'1','1','1','1'}
        };
        System.out.println("Matrix 3 (All 1s):");
        solution.visualizeMatrix(matrix3);
        System.out.println("Maximal Rectangle Area: " + solution.maximalRectangle(matrix3));
    }
}