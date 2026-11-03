package dsa.datastructures.arrays.basics;

/*
 * Problem: Container With Most Water
 * LeetCode: #11
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Bloomberg
 * 
 * Problem Statement:
 * You are given an integer array height of length n. There are n vertical lines drawn such 
 * that the two endpoints of the i-th line are (i, 0) and (i, height[i]).
 * Find two lines that together with the x-axis form a container, such that the container 
 * contains the most water. Return the maximum amount of water a container can store.
 *
 * Example 1:
 * Input: height = [1,8,6,2,5,4,8,3,7]
 * Output: 49
 * Explanation: The vertical lines are at indices 1 and 8 with heights 8 and 7.
 * Area = min(8, 7) * (8 - 1) = 7 * 7 = 49
 *
 * Example 2:
 * Input: height = [1,1]
 * Output: 1
 *
 * Constraints:
 * - n == height.length
 * - 2 <= n <= 10^5
 * - 0 <= height[i] <= 10^4
 *
 * Time Complexity: O(n)
 * Space Complexity:  O(1)
 *
 * Approach:
 * Use two pointers at both ends. The area is limited by the shorter line.
 * Move the pointer with smaller height inward to potentially find a taller line.
 */

public class ContainerWithMostWater_LC11 {

    // Optimal Two Pointer Solution
    public int maxArea(int[] height) {
        int left = 0;
        int right = height.length - 1;
        int maxArea = 0;

        while (left < right) {
            // Calculate current area
            int width = right - left;
            int h = Math.min(height[left], height[right]);
            int area = width * h;

            maxArea = Math.max(maxArea, area);

            // Move pointer with smaller height
            if (height[left] < height[right]) {
                left++;
            } else {
                right--;
            }
        }

        return maxArea;
    }

    // Alternative with explicit checks
    public int maxAreaExplicit(int[] height) {
        int maxArea = 0;
        int left = 0;
        int right = height.length - 1;

        while (left < right) {
            int leftHeight = height[left];
            int rightHeight = height[right];
            int width = right - left;

            // Area is determined by shorter line
            int currentArea = Math.min(leftHeight, rightHeight) * width;
            maxArea = Math.max(maxArea, currentArea);

            // Skip shorter lines that can't improve the result
            if (leftHeight < rightHeight) {
                left++;
            } else {
                right--;
            }
        }

        return maxArea;
    }

    // Brute Force - O(n^2) - For comparison
    public int maxAreaBruteForce(int[] height) {
        int maxArea = 0;

        for (int i = 0; i < height.length; i++) {
            for (int j = i + 1; j < height.length; j++) {
                int area = Math.min(height[i], height[j]) * (j - i);
                maxArea = Math.max(maxArea, area);
            }
        }

        return maxArea;
    }

    public static void main(String[] args) {
        ContainerWithMostWater_LC11 solution = new ContainerWithMostWater_LC11();

        // Test Case 1
        int[] height1 = { 1, 8, 6, 2, 5, 4, 8, 3, 7 };
        System.out.println("Test 1: " + solution.maxArea(height1)); // 49

        // Test Case 2
        int[] height2 = { 1, 1 };
        System.out.println("Test 2: " + solution.maxArea(height2)); // 1

        // Test Case 3
        int[] height3 = { 4, 3, 2, 1, 4 };
        System.out.println("Test 3: " + solution.maxArea(height3)); // 16

        // Test Case 4
        int[] height4 = { 1, 2, 1 };
        System.out.println("Test 4: " + solution.maxArea(height4)); // 2
    }
}

