package dsa.datastructures.arrays.basics;

/*
 * Problem: Maximum Subarray (Kadane's Algorithm)
 * LeetCode: #53
 * Difficulty: Medium
 * Companies: Amazon, Google, Microsoft, Facebook, Apple, LinkedIn, Bloomberg
 * 
 * Problem Statement:
 * Given an integer array nums, find the subarray with the largest sum, and return its sum.
 *
 * Example 1:
 * Input: nums = [-2,1,-3,4,-1,2,1,-5,4]
 * Output: 6
 * Explanation: The subarray [4,-1,2,1] has the largest sum 6.
 *
 * Example 2:
 * Input: nums = [1]
 * Output: 1
 *
 * Example 3:
 * Input: nums = [5,4,-1,7,8]
 * Output: 23
 *
 * Constraints:
 * - 1 <= nums.length <= 10^5
 * - -10^4 <= nums[i] <= 10^4
 *
 * Follow up: If you have figured out the O(n) solution, try coding another solution 
 * using the divide and conquer approach.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 *
 * Approach:
 * Kadane's Algorithm - At each position, decide whether to extend current subarray
 * or start a new one. Choose whichever gives larger sum.
 */

public class MaximumSubarray_LC53 {

    // Kadane's Algorithm - Optimal O(n) solution
    public int maxSubArray(int[] nums) {
        int maxSum = nums[0];
        int currentSum = nums[0];

        for (int i = 1; i < nums.length; i++) {
            // Either extend current subarray or start new one
            currentSum = Math.max(nums[i], currentSum + nums[i]);
            maxSum = Math.max(maxSum, currentSum);
        }

        return maxSum;
    }

    // Alternative: Explicit Kadane's
    public int maxSubArrayExplicit(int[] nums) {
        int maxSoFar = Integer.MIN_VALUE;
        int maxEndingHere = 0;

        for (int num : nums) {
            maxEndingHere += num;
            maxSoFar = Math.max(maxSoFar, maxEndingHere);

            // Reset if sum becomes negative
            if (maxEndingHere < 0) {
                maxEndingHere = 0;
            }
        }

        return maxSoFar;
    }

    // Divide and Conquer - O(n log n)
    public int maxSubArrayDivideConquer(int[] nums) {
        return maxSubArrayHelper(nums, 0, nums.length - 1);
    }

    private int maxSubArrayHelper(int[] nums, int left, int right) {
        if (left == right) {
            return nums[left];
        }

        int mid = left + (right - left) / 2;

        int leftMax = maxSubArrayHelper(nums, left, mid);
        int rightMax = maxSubArrayHelper(nums, mid + 1, right);
        int crossMax = maxCrossingSum(nums, left, mid, right);

        return Math.max(Math.max(leftMax, rightMax), crossMax);
    }

    private int maxCrossingSum(int[] nums, int left, int mid, int right) {
        int leftSum = Integer.MIN_VALUE;
        int sum = 0;

        for (int i = mid; i >= left; i--) {
            sum += nums[i];
            leftSum = Math.max(leftSum, sum);
        }

        int rightSum = Integer.MIN_VALUE;
        sum = 0;

        for (int i = mid + 1; i <= right; i++) {
            sum += nums[i];
            rightSum = Math.max(rightSum, sum);
        }

        return leftSum + rightSum;
    }

    // Returns subarray indices along with max sum
    public int[] maxSubArrayWithIndices(int[] nums) {
        int maxSum = nums[0];
        int currentSum = nums[0];
        int start = 0, end = 0, tempStart = 0;

        for (int i = 1; i < nums.length; i++) {
            if (nums[i] > currentSum + nums[i]) {
                currentSum = nums[i];
                tempStart = i;
            } else {
                currentSum = currentSum + nums[i];
            }

            if (currentSum > maxSum) {
                maxSum = currentSum;
                start = tempStart;
                end = i;
            }
        }

        return new int[] { maxSum, start, end };
    }

    public static void main(String[] args) {
        MaximumSubarray_LC53 solution = new MaximumSubarray_LC53();

        // Test Case 1
        int[] nums1 = { -2, 1, -3, 4, -1, 2, 1, -5, 4 };
        System.out.println("Test 1: " + solution.maxSubArray(nums1)); // 6

        // Test Case 2
        int[] nums2 = { 1 };
        System.out.println("Test 2: " + solution.maxSubArray(nums2)); // 1

        // Test Case 3
        int[] nums3 = { 5, 4, -1, 7, 8 };
        System.out.println("Test 3: " + solution.maxSubArray(nums3)); // 23

        // Test Case 4 - All negative
        int[] nums4 = { -2, -1, -3 };
        System.out.println("Test 4: " + solution.maxSubArray(nums4)); // -1

        // Test with indices
        int[] result = solution.maxSubArrayWithIndices(nums1);
        System.out.println("\nWith indices: Sum=" + result[0] + ", Start=" + result[1] + ", End=" + result[2]);
    }
}

