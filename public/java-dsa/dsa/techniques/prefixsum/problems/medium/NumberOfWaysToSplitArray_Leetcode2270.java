
package dsa.techniques.prefixsum.problems.medium;

import java.util.Arrays;

/**
 * LeetCode 2270: Number of Ways to Split Array
 * Difficulty: Medium
 * 
 * Problem:
 * You are given a 0-indexed integer array `nums` of length `n`.
 * A split at index `i` is valid if the sum of the first `i + 1` elements is greater than or equal to
 * the sum of the last `n - i - 1` elements. The split is not allowed at the last element.
 * Return the number of valid splits.
 *
 * Key Insight:
 * This problem requires comparing the sum of a left part of an array with the sum of a right part for every possible split point.
 * A naive approach would be to recalculate the left and right sums for each split, which would be inefficient (O(n^2)).
 * A much better approach is to use the concept of prefix sums.
 * 1. First, calculate the `totalSum` of the entire array.
 * 2. Iterate through the array from `i = 0` to `n-2` (as we can't split at the last element).
 * 3. Maintain a `leftSum` as we iterate. For each index `i`, `leftSum` is the sum of elements from 0 to `i`.
 * 4. The `rightSum` can then be calculated in O(1) time by using the formula: `rightSum = totalSum - leftSum`.
 * 5. Compare `leftSum` and `rightSum` and increment a counter if the condition is met.
 * Note: Using `long` for sums is important to prevent integer overflow with large inputs.
 *
 * Example:
 * nums = [10, 4, -8, 7], totalSum = 13
 * i=0: leftSum=10. rightSum = 13-10=3. 10 >= 3. Valid. count=1.
 * i=1: leftSum=10+4=14. rightSum = 13-14=-1. 14 >= -1. Valid. count=2.
 * i=2: leftSum=14-8=6. rightSum = 13-6=7. 6 < 7. Invalid.
 * Final count = 2.
 *
 * Time Complexity: O(n), because we have one pass to calculate the total sum and another pass to check the splits.
 * Space Complexity: O(1), as we only use a few variables to store the sums and the count.
 */
public class NumberOfWaysToSplitArray_Leetcode2270 {
    
    public static int waysToSplitArray(int[] nums) {
        // Use long to prevent potential integer overflow when summing up elements.
        long totalSum = 0;
        for (int num : nums) {
            totalSum += num;
        }
        
        int validSplits = 0;
        long leftSum = 0;
        
        // We can only split up to the second to last element.
        // A split at index `i` involves `nums[0...i]` and `nums[i+1...n-1]`.
        for (int i = 0; i < nums.length - 1; i++) {
            // Add the current element to the left sum.
            leftSum += nums[i];
            
            // Calculate the right sum efficiently using the total sum.
            long rightSum = totalSum - leftSum;
            
            // Check if the split is valid.
            if (leftSum >= rightSum) {
                validSplits++;
            }
        }
        
        return validSplits;
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {10, 4, -8, 7};
        System.out.println("Nums: " + Arrays.toString(nums1));
        System.out.println("Valid Splits: " + waysToSplitArray(nums1)); // Expected: 2
        System.out.println("---");

        // Test case 2
        int[] nums2 = {2, 3, 1, 0};
        System.out.println("Nums: " + Arrays.toString(nums2));
        System.out.println("Valid Splits: " + waysToSplitArray(nums2)); // Expected: 2
        System.out.println("---");

        // Test case 3: All elements are the same
        int[] nums3 = {1, 1, 1, 1, 1, 1};
        System.out.println("Nums: " + Arrays.toString(nums3));
        System.out.println("Valid Splits: " + waysToSplitArray(nums3)); // Expected: 5
        System.out.println("---");
    }
}

