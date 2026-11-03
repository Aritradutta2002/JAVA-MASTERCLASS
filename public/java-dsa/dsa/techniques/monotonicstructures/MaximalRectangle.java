package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Maximal Rectangle (LeetCode 85) - HARD
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Problem: Find largest rectangle containing only 1's in binary matrix
 * Time: O(m*n), Space: O(n)
 * 
 * Key: Reduce to Largest Rectangle in Histogram for each row
 */
public class MaximalRectangle {
    
    public int maximalRectangle(char[][] matrix) {
        if (matrix == null || matrix.length == 0) return 0;
        
        int m = matrix.length;
        int n = matrix[0].length;
        int[] heights = new int[n];
        int maxArea = 0;
        
        for (int i = 0; i < m; i++) {
            // Update heights array
            for (int j = 0; j < n; j++) {
                if (matrix[i][j] == '1') {
                    heights[j]++;
                } else {
                    heights[j] = 0;
                }
            }
            
            // Find max rectangle for current histogram
            maxArea = Math.max(maxArea, largestRectangleArea(heights));
        }
        
        return maxArea;
    }
    
    // Helper: Largest Rectangle in Histogram using Monotonic Stack
    private int largestRectangleArea(int[] heights) {
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
    
    // Alternative: Using DP approach
    public int maximalRectangleDP(char[][] matrix) {
        if (matrix == null || matrix.length == 0) return 0;
        
        int m = matrix.length;
        int n = matrix[0].length;
        int[] left = new int[n];
        int[] right = new int[n];
        int[] height = new int[n];
        Arrays.fill(right, n);
        
        int maxArea = 0;
        
        for (int i = 0; i < m; i++) {
            int curLeft = 0, curRight = n;
            
            // Update height
            for (int j = 0; j < n; j++) {
                if (matrix[i][j] == '1') height[j]++;
                else height[j] = 0;
            }
            
            // Update left
            for (int j = 0; j < n; j++) {
                if (matrix[i][j] == '1') {
                    left[j] = Math.max(left[j], curLeft);
                } else {
                    left[j] = 0;
                    curLeft = j + 1;
                }
            }
            
            // Update right
            for (int j = n - 1; j >= 0; j--) {
                if (matrix[i][j] == '1') {
                    right[j] = Math.min(right[j], curRight);
                } else {
                    right[j] = n;
                    curRight = j;
                }
            }
            
            // Calculate area
            for (int j = 0; j < n; j++) {
                maxArea = Math.max(maxArea, (right[j] - left[j]) * height[j]);
            }
        }
        
        return maxArea;
    }
    
    public static void main(String[] args) {
        MaximalRectangle solution = new MaximalRectangle();
        
        char[][] matrix = {
            {'1', '0', '1', '0', '0'},
            {'1', '0', '1', '1', '1'},
            {'1', '1', '1', '1', '1'},
            {'1', '0', '0', '1', '0'}
        };
        
        System.out.println("Maximal Rectangle Area: " + solution.maximalRectangle(matrix)); // 6
    }
}

