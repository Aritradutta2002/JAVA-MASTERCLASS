package dsa.datastructures.arrays.basics;

/*
 * Problem: Trapping Rain Water
 * LeetCode: #42
 * Difficulty: Hard
 * Companies: Amazon, Google, Facebook, Microsoft, Apple, Bloomberg
 * 
 * Problem Statement:
 * Given n non-negative integers representing an elevation map where the width of each bar is 1,
 * compute how much water it can trap after raining.
 *
 * Example 1:
 * Input: height = [0,1,0,2,1,0,1,3,2,1,2,1]
 * Output: 6
 * Explanation: The elevation map traps 6 units of rain water.
 *
 * Example 2:
 * Input: height = [4,2,0,3,2,5]
 * Output: 9
 *
 * Constraints:
 * - n == height.length
 * - 1 <= n <= 2 * 10^4
 * - 0 <= height[i] <= 10^5
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1) for two-pointer, O(n) for others
 *
 * Approach:
 * Water trapped at each position = min(max_left, max_right) - height[i]
 * Multiple approaches: DP with arrays, stack, two pointers (optimal)
 */

import java.util.*;

public class TrappingRainWater_LC42 {

    // Optimal Two Pointer Solution - O(n) time, O(1) space
    public int trap(int[] height) {
        if (height == null || height.length == 0) {
            return 0;
        }

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

    // DP Solution - O(n) time, O(n) space
    public int trapDP(int[] height) {
        if (height.length == 0)
            return 0;

        int n = height.length;
        int[] leftMax = new int[n];
        int[] rightMax = new int[n];

        // Fill leftMax
        leftMax[0] = height[0];
        for (int i = 1; i < n; i++) {
            leftMax[i] = Math.max(leftMax[i - 1], height[i]);
        }

        // Fill rightMax
        rightMax[n - 1] = height[n - 1];
        for (int i = n - 2; i >= 0; i--) {
            rightMax[i] = Math.max(rightMax[i + 1], height[i]);
        }

        // Calculate water
        int water = 0;
        for (int i = 0; i < n; i++) {
            water += Math.min(leftMax[i], rightMax[i]) - height[i];
        }

        return water;
    }

    // Stack Solution - O(n) time, O(n) space
    public int trapStack(int[] height) {
        Stack<Integer> stack = new Stack<>();
        int water = 0;

        for (int i = 0; i < height.length; i++) {
            while (!stack.isEmpty() && height[i] > height[stack.peek()]) {
                int top = stack.pop();

                if (stack.isEmpty()) {
                    break;
                }

                int distance = i - stack.peek() - 1;
                int boundedHeight = Math.min(height[i], height[stack.peek()]) - height[top];
                water += distance * boundedHeight;
            }
            stack.push(i);
        }

        return water;
    }

    public static void main(String[] args) {
        TrappingRainWater_LC42 solution = new TrappingRainWater_LC42();

        // Test Case 1
        int[] height1 = { 0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1 };
        System.out.println("Test 1: " + solution.trap(height1)); // 6

        // Test Case 2
        int[] height2 = { 4, 2, 0, 3, 2, 5 };
        System.out.println("Test 2: " + solution.trap(height2)); // 9

        // Test Case 3
        int[] height3 = { 3, 0, 2, 0, 4 };
        System.out.println("Test 3: " + solution.trap(height3)); // 7

        // Test Case 4
        int[] height4 = { 3, 0, 0, 2, 0, 4 };
        System.out.println("Test 4: " + solution.trap(height4)); // 10

        System.out.println("\nDP Solution:");
        System.out.println("Test 1: " + solution.trapDP(height1)); // 6

        System.out.println("\nStack Solution:");
        System.out.println("Test 1: " + solution.trapStack(height1)); // 6
    }
}

