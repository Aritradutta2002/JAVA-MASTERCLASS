package dsa.techniques.hashing;

/*
 * Problem: Contains Duplicate
 * LeetCode: #217
 * Difficulty: Easy
 * Companies: Amazon, Google, Microsoft, Apple, Facebook
 * 
 * Problem Statement:
 * Given an integer array nums, return true if any value appears at least twice 
 * in the array, and return false if every element is distinct.
 *
 * Example 1:
 * Input: nums = [1,2,3,1]
 * Output: true
 *
 * Example 2:
 * Input: nums = [1,2,3,4]
 * Output: false
 *
 * Example 3:
 * Input: nums = [1,1,1,3,3,4,3,2,4,2]
 * Output: true
 *
 * Constraints:
 * - 1 <= nums.length <= 10^5
 * - -10^9 <= nums[i] <= 10^9
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 *
 * Approach:
 * Use a HashSet to track seen elements. If we encounter an element that's 
 * already in the set, we found a duplicate.
 */

import java.util.*;

public class ContainsDuplicate_LC217 {

    // Optimal Solution using HashSet
    public boolean containsDuplicate(int[] nums) {
        Set<Integer> seen = new HashSet<>();

        for (int num : nums) {
            if (seen.contains(num)) {
                return true;
            }
            seen.add(num);
        }

        return false;
    }

    // Alternative: Using HashSet.add() return value
    public boolean containsDuplicateAlt(int[] nums) {
        Set<Integer> seen = new HashSet<>();

        for (int num : nums) {
            if (!seen.add(num)) { // add() returns false if element already exists
                return true;
            }
        }

        return false;
    }

    // Alternative: Sorting approach - O(n log n) time, O(1) space
    public boolean containsDuplicateSorting(int[] nums) {
        Arrays.sort(nums);

        for (int i = 1; i < nums.length; i++) {
            if (nums[i] == nums[i - 1]) {
                return true;
            }
        }

        return false;
    }

    public static void main(String[] args) {
        ContainsDuplicate_LC217 solution = new ContainsDuplicate_LC217();

        // Test Case 1
        int[] nums1 = { 1, 2, 3, 1 };
        System.out.println("Test 1: " + solution.containsDuplicate(nums1)); // true

        // Test Case 2
        int[] nums2 = { 1, 2, 3, 4 };
        System.out.println("Test 2: " + solution.containsDuplicate(nums2)); // false

        // Test Case 3
        int[] nums3 = { 1, 1, 1, 3, 3, 4, 3, 2, 4, 2 };
        System.out.println("Test 3: " + solution.containsDuplicate(nums3)); // true

        // Test Case 4 - Single element
        int[] nums4 = { 1 };
        System.out.println("Test 4: " + solution.containsDuplicate(nums4)); // false
    }
}

