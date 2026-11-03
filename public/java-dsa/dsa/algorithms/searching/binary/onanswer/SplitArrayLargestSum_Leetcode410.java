package dsa.algorithms.searching.binary.onanswer;

/*
 * LEETCODE 410: SPLIT ARRAY LARGEST SUM
 * Difficulty: Hard
 * Pattern: Binary Search on Answer (Resource Allocation)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROBLEM STATEMENT:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Given an integer array nums and an integer k, split nums into k non-empty 
 * subarrays such that the largest sum of any subarray is minimized.
 * 
 * Return the minimized largest sum of the split.
 * 
 * A subarray is a contiguous part of the array.
 * 
 * EXAMPLE 1:
 * Input: nums = [7,2,5,10,8], k = 2
 * Output: 18
 * Explanation:
 * - [7,2,5] | [10,8] → max = max(14, 18) = 18 ✓
 * - [7] | [2,5,10,8] → max = max(7, 25) = 25
 * - [7,2] | [5,10,8] → max = max(9, 23) = 23
 * 
 * The best split is [7,2,5] and [10,8] with largest sum 18.
 * 
 * EXAMPLE 2:
 * Input: nums = [1,2,3,4,5], k = 2
 * Output: 9
 * Explanation: [1,2,3,4] | [5] → max(10, 5) = 10
 *              [1,2,3] | [4,5] → max(6, 9) = 9 ✓
 * 
 * EXAMPLE 3:
 * Input: nums = [1,4,4], k = 3
 * Output: 4
 * Explanation: Each element becomes a subarray: [1], [4], [4]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * NOTE: This is IDENTICAL to "Allocate Books" problem!
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Book Allocation → Split Array
 * --------------   --------------
 * pages[]       → nums[]
 * students      → k (subarrays)
 * books         → elements
 * 
 * Same logic, different story!
 * 
 * TIME: O(n * log(sum - max))
 * SPACE: O(1)
 */

import java.util.*;

public class SplitArrayLargestSum_Leetcode410 {
    
    public int splitArray(int[] nums, int k) {
        // Search space
        int left = getMax(nums);   // Can't split to less than max element
        int right = getSum(nums);  // All in one subarray
        int result = right;
        
        // Binary search on the answer
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            // Can we split into k subarrays with max sum = mid?
            if (canSplit(nums, k, mid)) {
                result = mid;
                right = mid - 1;   // Try smaller maximum
            } else {
                left = mid + 1;    // Need larger maximum
            }
        }
        
        return result;
    }
    
    // Can we split into at most k subarrays with max sum <= maxSum?
    private boolean canSplit(int[] nums, int k, int maxSum) {
        int subarrays = 1;
        int currentSum = 0;
        
        for (int num : nums) {
            if (currentSum + num > maxSum) {
                // Start new subarray
                subarrays++;
                currentSum = num;
                
                if (subarrays > k) {
                    return false;
                }
            } else {
                currentSum += num;
            }
        }
        
        return true;
    }
    
    private int getMax(int[] nums) {
        int max = nums[0];
        for (int num : nums) {
            max = Math.max(max, num);
        }
        return max;
    }
    
    private int getSum(int[] nums) {
        int sum = 0;
        for (int num : nums) {
            sum += num;
        }
        return sum;
    }
    
    public static void main(String[] args) {
        SplitArrayLargestSum_Leetcode410 solution = new SplitArrayLargestSum_Leetcode410();
        
        int[] nums1 = {7, 2, 5, 10, 8};
        System.out.println("Input: " + Arrays.toString(nums1) + ", k=2");
        System.out.println("Output: " + solution.splitArray(nums1, 2));
        
        int[] nums2 = {1, 2, 3, 4, 5};
        System.out.println("\nInput: " + Arrays.toString(nums2) + ", k=2");
        System.out.println("Output: " + solution.splitArray(nums2, 2));
    }
}

/*
 * This problem demonstrates that once you understand the pattern,
 * you can solve many "different" problems with the same approach!
 * 
 * Master Book Allocation → You've mastered this too!
 */


