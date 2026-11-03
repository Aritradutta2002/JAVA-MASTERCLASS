package dsa.techniques.hashing;

/*
 * Problem: Two Sum
 * LeetCode: #1
 * Difficulty: Easy
 * Companies: Google, Amazon, Facebook, Microsoft, Apple, Adobe, Bloomberg
 * 
 * Problem Statement:
 * Given an array of integers nums and an integer target, return indices of the 
 * two numbers such that they add up to target.
 * You may assume that each input would have exactly one solution, and you may 
 * not use the same element twice.
 * You can return the answer in any order.
 *
 * Example 1:
 * Input: nums = [2,7,11,15], target = 9
 * Output: [0,1]
 * Explanation: Because nums[0] + nums[1] == 9, we return [0, 1].
 *
 * Example 2:
 * Input: nums = [3,2,4], target = 6
 * Output: [1,2]
 *
 * Example 3:
 * Input: nums = [3,3], target = 6
 * Output: [0,1]
 *
 * Constraints:
 * - 2 <= nums.length <= 10^4
 * - -10^9 <= nums[i] <= 10^9
 * - -10^9 <= target <= 10^9
 * - Only one valid answer exists.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 *
 * Approach:
 * Use a HashMap to store the numbers we've seen along with their indices.
 * For each number, check if (target - current number) exists in the map.
 * If it does, we found our pair. Otherwise, add current number to the map.
 */

import java.util.*;

public class TwoSum_LC1 {

    // Optimal Solution using HashMap
    public int[] twoSum(int[] nums, int target) {
        Map<Integer, Integer> map = new HashMap<>();

        for (int i = 0; i < nums.length; i++) {
            int complement = target - nums[i];

            if (map.containsKey(complement)) {
                return new int[] { map.get(complement), i };
            }

            map.put(nums[i], i);
        }

        // Should never reach here given problem constraints
        return new int[] { -1, -1 };
    }

    // Brute Force Solution - O(n^2) time, O(1) space
    public int[] twoSumBruteForce(int[] nums, int target) {
        for (int i = 0; i < nums.length; i++) {
            for (int j = i + 1; j < nums.length; j++) {
                if (nums[i] + nums[j] == target) {
                    return new int[] { i, j };
                }
            }
        }
        return new int[] { -1, -1 };
    }

    public static void main(String[] args) {
        TwoSum_LC1 solution = new TwoSum_LC1();

        // Test Case 1
        int[] nums1 = { 2, 7, 11, 15 };
        int target1 = 9;
        int[] result1 = solution.twoSum(nums1, target1);
        System.out.println("Test 1: [" + result1[0] + ", " + result1[1] + "]"); // [0, 1]

        // Test Case 2
        int[] nums2 = { 3, 2, 4 };
        int target2 = 6;
        int[] result2 = solution.twoSum(nums2, target2);
        System.out.println("Test 2: [" + result2[0] + ", " + result2[1] + "]"); // [1, 2]

        // Test Case 3
        int[] nums3 = { 3, 3 };
        int target3 = 6;
        int[] result3 = solution.twoSum(nums3, target3);
        System.out.println("Test 3: [" + result3[0] + ", " + result3[1] + "]"); // [0, 1]

        // Test Case 4 - Negative numbers
        int[] nums4 = { -1, -2, -3, -4, -5 };
        int target4 = -8;
        int[] result4 = solution.twoSum(nums4, target4);
        System.out.println("Test 4: [" + result4[0] + ", " + result4[1] + "]"); // [2, 4]
    }
}

