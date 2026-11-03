package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Largest Rectangle in Histogram (LeetCode 84) - HARD
 * FAANG Frequency: Very High (Google, Amazon, Facebook, Microsoft)
 * 
 * Problem: Find the largest rectangle area in a histogram
 * Time: O(n), Space: O(n)
 * 
 * Key Pattern: Monotonic Stack to find previous/next smaller elements
 */
public class LargestRectangleHistogram {
    
    // Approach 1: Monotonic Stack (Optimal)
    public int largestRectangleArea(int[] heights) {
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
    
    // Approach 2: Using Previous and Next Smaller Elements
    public int largestRectangleAreaAlternate(int[] heights) {
        int n = heights.length;
        int[] left = new int[n];  // Previous smaller
        int[] right = new int[n]; // Next smaller
        
        // Fill left array
        Stack<Integer> stack = new Stack<>();
        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && heights[stack.peek()] >= heights[i]) {
                stack.pop();
            }
            left[i] = stack.isEmpty() ? 0 : stack.peek() + 1;
            stack.push(i);
        }
        
        // Fill right array
        stack.clear();
        for (int i = n - 1; i >= 0; i--) {
            while (!stack.isEmpty() && heights[stack.peek()] >= heights[i]) {
                stack.pop();
            }
            right[i] = stack.isEmpty() ? n - 1 : stack.peek() - 1;
            stack.push(i);
        }
        
        // Calculate max area
        int maxArea = 0;
        for (int i = 0; i < n; i++) {
            int width = right[i] - left[i] + 1;
            maxArea = Math.max(maxArea, heights[i] * width);
        }
        
        return maxArea;
    }
    
    public static void main(String[] args) {
        LargestRectangleHistogram solution = new LargestRectangleHistogram();
        
        // Test Case 1
        int[] heights1 = {2, 1, 5, 6, 2, 3};
        System.out.println("Max Rectangle Area: " + solution.largestRectangleArea(heights1)); // 10
        
        // Test Case 2
        int[] heights2 = {2, 4};
        System.out.println("Max Rectangle Area: " + solution.largestRectangleArea(heights2)); // 4
    }
}

