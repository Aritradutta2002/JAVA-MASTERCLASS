package dsa.techniques.hashing;

/*
 * Problem: 4Sum II
 * LeetCode: #454
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google
 * 
 * Problem Statement:
 * Given four integer arrays nums1, nums2, nums3, and nums4 all of length n, 
 * return the number of tuples (i, j, k, l) such that:
 * - 0 <= i, j, k, l < n
 * - nums1[i] + nums2[j] + nums3[k] + nums4[l] == 0
 *
 * Example 1:
 * Input: nums1 = [1,2], nums2 = [-2,-1], nums3 = [-1,2], nums4 = [0,2]
 * Output: 2
 * Explanation:
 * 1. (0, 0, 0, 1) -> nums1[0] + nums2[0] + nums3[0] + nums4[1] = 1 + (-2) + (-1) + 2 = 0
 * 2. (1, 1, 0, 0) -> nums1[1] + nums2[1] + nums3[0] + nums4[0] = 2 + (-1) + (-1) + 0 = 0
 *
 * Example 2:
 * Input: nums1 = [0], nums2 = [0], nums3 = [0], nums4 = [0]
 * Output: 1
 *
 * Constraints:
 * - n == nums1.length == nums2.length == nums3.length == nums4.length
 * - 1 <= n <= 200
 * - -2^28 <= nums1[i], nums2[i], nums3[i], nums4[i] <= 2^28
 *
 * Time Complexity: O(n^2)
 * Space Complexity: O(n^2)
 *
 * Approach:
 * Split into two 2-sum problems. Store all possible sums from first two arrays
 * in HashMap, then check if -(sum of third and fourth) exists.
 */

import java.util.*;

public class FourSumII_LC454 {

    // Optimal solution using HashMap - O(n^2)
    public int fourSumCount(int[] nums1, int[] nums2, int[] nums3, int[] nums4) {
        Map<Integer, Integer> map = new HashMap<>();

        // Store all possible sums from nums1 and nums2
        for (int a : nums1) {
            for (int b : nums2) {
                int sum = a + b;
                map.put(sum, map.getOrDefault(sum, 0) + 1);
            }
        }

        int count = 0;

        // Find complements from nums3 and nums4
        for (int c : nums3) {
            for (int d : nums4) {
                int sum = c + d;
                count += map.getOrDefault(-sum, 0);
            }
        }

        return count;
    }

    // Alternative: More explicit version
    public int fourSumCountExplicit(int[] nums1, int[] nums2, int[] nums3, int[] nums4) {
        Map<Integer, Integer> sumCount = new HashMap<>();
        int n = nums1.length;

        // Count all sums of pairs from first two arrays
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int sum = nums1[i] + nums2[j];
                sumCount.put(sum, sumCount.getOrDefault(sum, 0) + 1);
            }
        }

        int result = 0;

        // For each pair from last two arrays, find complement
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                int sum = nums3[i] + nums4[j];
                int target = -sum;

                if (sumCount.containsKey(target)) {
                    result += sumCount.get(target);
                }
            }
        }

        return result;
    }

    public static void main(String[] args) {
        FourSumII_LC454 solution = new FourSumII_LC454();

        // Test Case 1
        int[] nums1 = { 1, 2 };
        int[] nums2 = { -2, -1 };
        int[] nums3 = { -1, 2 };
        int[] nums4 = { 0, 2 };
        System.out.println("Test 1: " + solution.fourSumCount(nums1, nums2, nums3, nums4));
        // Output: 2

        // Test Case 2
        int[] nums1_2 = { 0 };
        int[] nums2_2 = { 0 };
        int[] nums3_2 = { 0 };
        int[] nums4_2 = { 0 };
        System.out.println("Test 2: " + solution.fourSumCount(nums1_2, nums2_2, nums3_2, nums4_2));
        // Output: 1

        // Test Case 3
        int[] nums1_3 = { -1, -1 };
        int[] nums2_3 = { -1, 1 };
        int[] nums3_3 = { -1, 1 };
        int[] nums4_3 = { 1, -1 };
        System.out.println("Test 3: " + solution.fourSumCount(nums1_3, nums2_3, nums3_3, nums4_3));
        // Output: 6
    }
}

