
package dsa.techniques.prefixsum.problems.hard;

import java.util.Arrays;

/**
 * LeetCode 689: Maximum Sum of 3 Non-Overlapping Subarrays
 * Difficulty: Hard
 * 
 * Problem:
 * Given an integer array `nums` and an integer `k`, find three non-overlapping subarrays of length `k` with the maximum total sum.
 * Return the starting indices of these three subarrays. If there are multiple answers, return the lexicographically smallest one.
 *
 * Key Insight:
 * This problem can be solved using dynamic programming and prefix sums. The main idea is to fix the middle subarray and find the best possible left and right subarrays.
 * Let the middle subarray start at index `i`. This subarray spans from `i` to `i+k-1`.
 * Since the subarrays cannot overlap, the left subarray must end at or before `i-1`, and the right subarray must start at or after `i+k`.
 *
 * 1. Pre-computation:
 *    - Calculate a `prefixSum` array to quickly find the sum of any subarray in O(1).
 *    - Create a `left` DP array. `left[i]` stores the starting index of the best (max sum) single subarray of length `k` in the range `[0, i]`.
 *    - Create a `right` DP array. `right[i]` stores the starting index of the best single subarray of length `k` in the range `[i, n-1]`.
 *
 * 2. Main Logic:
 *    - Iterate through all possible starting positions `i` for the middle subarray. The middle subarray can start from index `k` up to `n - 2*k`.
 *    - For each middle subarray starting at `i`:
 *        a. The best left subarray must be in the range `[0, i-1]`. We can find its starting index from our pre-computed `left` array: `left[i-1]`.
 *        b. The best right subarray must be in the range `[i+k, n-1]`. We can find its starting index from our pre-computed `right` array: `right[i+k]`.
 *        c. Calculate the total sum of these three subarrays (left, middle, right).
 *        d. If this total sum is greater than the max sum found so far, update the max sum and store the starting indices of these three subarrays.
 *
 * This approach ensures we check every valid combination of three non-overlapping subarrays in an efficient O(n) manner after the initial O(n) pre-computation.
 *
 * Time Complexity: O(n). We have four separate loops, each running in O(n) time.
 * Space Complexity: O(n) to store the prefix sums and the two DP arrays (`left` and `right`).
 */
public class MaxSumOf3NonOverlappingSubarrays_Leetcode689 {

    public static int[] maxSumOfThreeSubarrays(int[] nums, int k) {
        int n = nums.length;
        
        // 1. Calculate prefix sums for O(1) subarray sum lookups.
        long[] prefixSum = new long[n + 1];
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + nums[i];
        }

        // 2. Compute `posLeft` array.
        // posLeft[i] = starting index of the max-sum subarray of length k in the range [0, i].
        int[] posLeft = new int[n];
        long maxLeftSum = 0;
        for (int i = k - 1; i < n; i++) {
            long currentSum = prefixSum[i + 1] - prefixSum[i - k + 1];
            if (i == k - 1) {
                posLeft[i] = i - k + 1;
                maxLeftSum = currentSum;
            } else {
                posLeft[i] = posLeft[i - 1];
                if (currentSum > maxLeftSum) {
                    maxLeftSum = currentSum;
                    posLeft[i] = i - k + 1;
                }
            }
        }

        // 3. Compute `posRight` array.
        // posRight[i] = starting index of the max-sum subarray of length k in the range [i, n-1].
        int[] posRight = new int[n];
        long maxRightSum = 0;
        for (int i = n - k; i >= 0; i--) {
            long currentSum = prefixSum[i + k] - prefixSum[i];
            if (i == n - k) {
                posRight[i] = i;
                maxRightSum = currentSum;
            } else {
                posRight[i] = posRight[i + 1];
                // Use >= to ensure we get the lexicographically smallest index in case of a tie.
                if (currentSum >= maxRightSum) {
                    maxRightSum = currentSum;
                    posRight[i] = i;
                }
            }
        }

        // 4. Find the optimal middle subarray position.
        int[] result = new int[3];
        long maxSum = 0;

        // Iterate through all possible start indices `i` for the middle subarray.
        // The middle subarray must be between the left and right subarrays.
        for (int i = k; i <= n - 2 * k; i++) {
            // Get the best start indices for the left and right subarrays.
            int l = posLeft[i - 1];
            int r = posRight[i + k];
            
            // Calculate the total sum of the three subarrays.
            long totalSum = (prefixSum[l + k] - prefixSum[l]) + 
                            (prefixSum[i + k] - prefixSum[i]) + 
                            (prefixSum[r + k] - prefixSum[r]);

            // If this combination gives a new max sum, update the result.
            if (totalSum > maxSum) {
                maxSum = totalSum;
                result[0] = l;
                result[1] = i;
                result[2] = r;
            }
        }

        return result;
    }

    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, 2, 1, 2, 6, 7, 5, 1};
        int k1 = 2;
        System.out.println("Nums: " + Arrays.toString(nums1) + ", k: " + k1);
        System.out.println("Result: " + Arrays.toString(maxSumOfThreeSubarrays(nums1, k1))); // Expected: [0, 3, 5]
        System.out.println("---");

        // Test case 2
        int[] nums2 = {1, 2, 1, 2, 1, 2, 1, 2, 1};
        int k2 = 2;
        System.out.println("Nums: " + Arrays.toString(nums2) + ", k: " + k2);
        System.out.println("Result: " + Arrays.toString(maxSumOfThreeSubarrays(nums2, k2))); // Expected: [0, 2, 4]
        System.out.println("---");
    }
}

