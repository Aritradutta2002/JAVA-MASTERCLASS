package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Trapping Rain Water (LeetCode 42) - HARD
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Calculate how much water can be trapped after raining
 * Multiple approaches with different trade-offs
 */
public class TrappingRainWater {
    
    // Approach 1: Monotonic Stack - O(n) time, O(n) space
    public int trapMonotonicStack(int[] height) {
        Stack<Integer> stack = new Stack<>();
        int water = 0;
        
        for (int i = 0; i < height.length; i++) {
            while (!stack.isEmpty() && height[i] > height[stack.peek()]) {
                int top = stack.pop();
                
                if (stack.isEmpty()) break;
                
                int distance = i - stack.peek() - 1;
                int boundedHeight = Math.min(height[i], height[stack.peek()]) - height[top];
                water += distance * boundedHeight;
            }
            stack.push(i);
        }
        
        return water;
    }
    
    // Approach 2: Two Pointers - O(n) time, O(1) space (Most Optimal)
    public int trap(int[] height) {
        if (height == null || height.length == 0) return 0;
        
        int left = 0, right = height.length - 1;
        int leftMax = 0, rightMax = 0;
        int water = 0;
        
        while (left < right) {
            if (height[left] < height[right]) {
                if (height[left] >= leftMax) {
                    leftMax = height[left];
                } else {
                    water += leftMax - height[left];
                }
                left++;
            } else {
                if (height[right] >= rightMax) {
                    rightMax = height[right];
                } else {
                    water += rightMax - height[right];
                }
                right--;
            }
        }
        
        return water;
    }
    
    // Approach 3: Dynamic Programming - O(n) time, O(n) space
    public int trapDP(int[] height) {
        if (height == null || height.length == 0) return 0;
        
        int n = height.length;
        int[] leftMax = new int[n];
        int[] rightMax = new int[n];
        
        leftMax[0] = height[0];
        for (int i = 1; i < n; i++) {
            leftMax[i] = Math.max(leftMax[i - 1], height[i]);
        }
        
        rightMax[n - 1] = height[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            rightMax[i] = Math.max(rightMax[i + 1], height[i]);
        }
        
        int water = 0;
        for (int i = 0; i < n; i++) {
            water += Math.min(leftMax[i], rightMax[i]) - height[i];
        }
        
        return water;
    }
    
    public static void main(String[] args) {
        TrappingRainWater solution = new TrappingRainWater();
        
        // Test Case 1
        int[] height1 = {0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1};
        System.out.println("Water Trapped (Two Pointers): " + solution.trap(height1)); // 6
        System.out.println("Water Trapped (Stack): " + solution.trapMonotonicStack(height1)); // 6
        
        // Test Case 2
        int[] height2 = {4, 2, 0, 3, 2, 5};
        System.out.println("Water Trapped: " + solution.trap(height2)); // 9
    }
}

