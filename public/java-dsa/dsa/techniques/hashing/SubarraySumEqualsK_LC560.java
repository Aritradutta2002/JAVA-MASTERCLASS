package dsa.techniques.hashing;

/*
 * Problem: Subarray Sum Equals K
 * LeetCode: #560
 * Difficulty: Medium
 * Companies: Facebook, Amazon, Google, Microsoft
 * 
 * Problem Statement:
 * Given an array of integers nums and an integer k, return the total number of 
 * subarrays whose sum equals to k.
 * A subarray is a contiguous non-empty sequence of elements within an array.
 *
 * Example 1:
 * Input: nums = [1,1,1], k = 2
 * Output: 2
 *
 * Example 2:
 * Input: nums = [1,2,3], k = 3
 * Output: 2
 *
 * Constraints:
 * - 1 <= nums.length <= 2 * 10^4
 * - -1000 <= nums[i] <= 1000
 * - -10^7 <= k <= 10^7
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 *
 * Approach:
 * Use prefix sum with HashMap. If prefixSum - k exists in map, those indices
 * form subarrays with sum k. Key insight: sum(i, j) = prefixSum[j] - prefixSum[i-1]
 */

import java.util.*;

public class SubarraySumEqualsK_LC560 {

    // Optimal solution using HashMap and prefix sum
    public int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1); // Base case: empty subarray

        int count = 0;
        int prefixSum = 0;

        for (int num : nums) {
            prefixSum += num;

            // If (prefixSum - k) exists, we found subarray(s) with sum k
            if (prefixSumCount.containsKey(prefixSum - k)) {
                count += prefixSumCount.get(prefixSum - k);
            }

            // Update prefix sum count
            prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
        }

        return count;
    }

    // Brute force for comparison - O(n^2)
    public int subarraySumBruteForce(int[] nums, int k) {
        int count = 0;

        for (int i = 0; i < nums.length; i++) {
            int sum = 0;
            for (int j = i; j < nums.length; j++) {
                sum += nums[j];
                if (sum == k) {
                    count++;
                }
            }
        }

        return count;
    }

    public static void main(String[] args) {
        SubarraySumEqualsK_LC560 solution = new SubarraySumEqualsK_LC560();

        // Test Case 1
        int[] nums1 = { 1, 1, 1 };
        System.out.println("Test 1: " + solution.subarraySum(nums1, 2)); // 2

        // Test Case 2
        int[] nums2 = { 1, 2, 3 };
        System.out.println("Test 2: " + solution.subarraySum(nums2, 3)); // 2

        // Test Case 3 - With negative numbers
        int[] nums3 = { 1, -1, 0 };
        System.out.println("Test 3: " + solution.subarraySum(nums3, 0)); // 3

        // Test Case 4
        int[] nums4 = { 1, 2, 1, 2, 1 };
        System.out.println("Test 4: " + solution.subarraySum(nums4, 3)); // 4

        // Test Case 5 - All zeros
        int[] nums5 = { 0, 0, 0 };
        System.out.println("Test 5: " + solution.subarraySum(nums5, 0)); // 6
    }
}

