package dsa.datastructures.arrays.basics;

/*
 * Problem: Maximum Product Subarray
 * LeetCode: #152
 * Difficulty: Medium
 * Companies: Amazon, Google, Microsoft, Facebook, Apple, Adobe
 *
 * Problem Statement:
 * Given an integer array nums, find a subarray that has the largest product,
 * and return the product.
 *
 * Example 1:
 * Input: nums = [2,3,-2,4]
 * Output: 6
 * Explanation: [2,3] has the largest product 6.
 *
 * Example 2:
 * Input: nums = [-2,0,-1]
 * Output: 0
 * Explanation: The result cannot be 2, because [-2,-1] is not a subarray.
 *
 * Constraints:
 * - 1 <= nums.length <= 2 * 10^4
 * - -10 <= nums[i] <= 10
 * - The product of any prefix or suffix of nums is guaranteed to fit in a 32-bit integer.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 *
 * Approach:
 * Keep track of both the maximum and minimum product ending at current index.
 * A negative number can turn the current minimum into the next maximum.
 */

public class MaximumProductSubarray_LC152 {

    // Optimal O(n) solution using rolling max/min products
    public int maxProduct(int[] nums) {
        int maxEndingHere = nums[0];
        int minEndingHere = nums[0];
        int answer = nums[0];

        for (int i = 1; i < nums.length; i++) {
            int current = nums[i];

            // Negative value swaps the role of max and min product
            if (current < 0) {
                int temp = maxEndingHere;
                maxEndingHere = minEndingHere;
                minEndingHere = temp;
            }

            maxEndingHere = Math.max(current, maxEndingHere * current);
            minEndingHere = Math.min(current, minEndingHere * current);

            answer = Math.max(answer, maxEndingHere);
        }

        return answer;
    }

    // Alternative approach using left-to-right and right-to-left scans
    public int maxProductPrefixSuffix(int[] nums) {
        int answer = Integer.MIN_VALUE;
        int prefixProduct = 1;
        int suffixProduct = 1;
        int n = nums.length;

        for (int i = 0; i < n; i++) {
            prefixProduct = (prefixProduct == 0) ? nums[i] : prefixProduct * nums[i];
            suffixProduct = (suffixProduct == 0) ? nums[n - 1 - i] : suffixProduct * nums[n - 1 - i];

            answer = Math.max(answer, Math.max(prefixProduct, suffixProduct));
        }

        return answer;
    }

    public static void main(String[] args) {
        MaximumProductSubarray_LC152 solution = new MaximumProductSubarray_LC152();

        // Test Case 1
        int[] nums1 = { 2, 3, -2, 4 };
        System.out.println("Test 1: " + solution.maxProduct(nums1)); // 6

        // Test Case 2
        int[] nums2 = { -2, 0, -1 };
        System.out.println("Test 2: " + solution.maxProduct(nums2)); // 0

        // Test Case 3 - Negative numbers create larger product
        int[] nums3 = { -2, 3, -4 };
        System.out.println("Test 3: " + solution.maxProduct(nums3)); // 24

        // Test Case 4 - Single element
        int[] nums4 = { -2 };
        System.out.println("Test 4: " + solution.maxProduct(nums4)); // -2

        // Test Case 5 - Zero splits the product chain
        int[] nums5 = { -2, -3, 0, -2, -40 };
        System.out.println("Test 5: " + solution.maxProduct(nums5)); // 80
    }
}

