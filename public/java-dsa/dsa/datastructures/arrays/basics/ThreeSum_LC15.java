package dsa.datastructures.arrays.basics;

/*
 * Problem: 3Sum
 * LeetCode: #15
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Apple, Bloomberg, Adobe
 * 
 * Problem Statement:
 * Given an integer array nums, return all the triplets [nums[i], nums[j], nums[k]] such 
 * that i != j, i != k, and j != k, and nums[i] + nums[j] + nums[k] == 0.
 * Notice that the solution set must not contain duplicate triplets.
 *
 * Example 1:
 * Input: nums = [-1,0,1,2,-1,-4]
 * Output: [[-1,-1,2],[-1,0,1]]
 *
 * Example 2:
 * Input: nums = [0,1,1]
 * Output: []
 *
 * Example 3:
 * Input: nums = [0,0,0]
 * Output: [[0,0,0]]
 *
 * Constraints:
 * - 3 <= nums.length <= 3000
 * - -10^5 <= nums[i] <= 10^5
 *
 * Time Complexity: O(n^2)
 * Space Complexity: O(1) excluding output
 *
 * Approach:
 * Sort array first. For each number, use two pointers to find pairs that sum to -num. 
 * Skip duplicates to avoid duplicate triplets.
 */

import java.util.*;

public class ThreeSum_LC15 {

    // Optimal solution using sorting + two pointers
    public List<List<Integer>> threeSum(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            // Skip duplicates for first element
            if (i > 0 && nums[i] == nums[i - 1]) {
                continue;
            }

            int left = i + 1;
            int right = nums.length - 1;
            int target = -nums[i];

            while (left < right) {
                int sum = nums[left] + nums[right];

                if (sum == target) {
                    result.add(Arrays.asList(nums[i], nums[left], nums[right]));

                    // Skip duplicates for second element
                    while (left < right && nums[left] == nums[left + 1]) {
                        left++;
                    }
                    // Skip duplicates for third element
                    while (left < right && nums[right] == nums[right - 1]) {
                        right--;
                    }

                    left++;
                    right--;
                } else if (sum < target) {
                    left++;
                } else {
                    right--;
                }
            }
        }

        return result;
    }

    // Alternative using HashSet to avoid duplicates
    public List<List<Integer>> threeSumHashSet(int[] nums) {
        Set<List<Integer>> resultSet = new HashSet<>();
        Arrays.sort(nums);

        for (int i = 0; i < nums.length - 2; i++) {
            int left = i + 1;
            int right = nums.length - 1;

            while (left < right) {
                int sum = nums[i] + nums[left] + nums[right];

                if (sum == 0) {
                    resultSet.add(Arrays.asList(nums[i], nums[left], nums[right]));
                    left++;
                    right--;
                } else if (sum < 0) {
                    left++;
                } else {
                    right--;
                }
            }
        }

        return new ArrayList<>(resultSet);
    }

    public static void main(String[] args) {
        ThreeSum_LC15 solution = new ThreeSum_LC15();

        // Test Case 1
        int[] nums1 = { -1, 0, 1, 2, -1, -4 };
        System.out.println("Test 1: " + solution.threeSum(nums1));
        // Output: [[-1, -1, 2], [-1, 0, 1]]

        // Test Case 2
        int[] nums2 = { 0, 1, 1 };
        System.out.println("Test 2: " + solution.threeSum(nums2));
        // Output: []

        // Test Case 3
        int[] nums3 = { 0, 0, 0 };
        System.out.println("Test 3: " + solution.threeSum(nums3));
        // Output: [[0, 0, 0]]

        // Test Case 4
        int[] nums4 = { -2, 0, 1, 1, 2 };
        System.out.println("Test 4: " + solution.threeSum(nums4));
        // Output: [[-2, 0, 2], [-2, 1, 1]]
    }
}

