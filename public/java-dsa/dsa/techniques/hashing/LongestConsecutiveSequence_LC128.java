package dsa.techniques.hashing;

/*
 * Problem: Longest Consecutive Sequence
 * LeetCode: #128
 * Difficulty: Medium
 * Companies: Google, Amazon, Facebook, Microsoft, Bloomberg, Uber
 * 
 * Problem Statement:
 * Given an unsorted array of integers nums, return the length of the longest 
 * consecutive elements sequence.
 * You must write an algorithm that runs in O(n) time.
 *
 * Example 1:
 * Input: nums = [100,4,200,1,3,2]
 * Output: 4
 * Explanation: The longest consecutive sequence is [1, 2, 3, 4]. Length is 4.
 *
 * Example 2:
 * Input: nums = [0,3,7,2,5,8,4,6,0,1]
 * Output: 9
 *
 * Constraints:
 * - 0 <= nums.length <= 10^5
 * - -10^9 <= nums[i] <= 10^9
 *
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 *
 * Approach:
 * Use HashSet for O(1) lookup. For each number, check if it's the start of a sequence
 * (i.e., num-1 is not in set). If so, count the length of consecutive sequence.
 */

import java.util.*;

public class LongestConsecutiveSequence_LC128 {

    // Optimal Solution using HashSet - O(n) time
    public int longestConsecutive(int[] nums) {
        if (nums.length == 0)
            return 0;

        Set<Integer> numSet = new HashSet<>();
        for (int num : nums) {
            numSet.add(num);
        }

        int maxLength = 0;

        for (int num : numSet) {
            // Check if this is the start of a sequence
            if (!numSet.contains(num - 1)) {
                int currentNum = num;
                int currentLength = 1;

                // Count consecutive numbers
                while (numSet.contains(currentNum + 1)) {
                    currentNum++;
                    currentLength++;
                }

                maxLength = Math.max(maxLength, currentLength);
            }
        }

        return maxLength;
    }

    // Alternative: Sorting approach - O(n log n) time
    public int longestConsecutiveSorting(int[] nums) {
        if (nums.length == 0)
            return 0;

        Arrays.sort(nums);

        int maxLength = 1;
        int currentLength = 1;

        for (int i = 1; i < nums.length; i++) {
            if (nums[i] == nums[i - 1]) {
                continue; // Skip duplicates
            } else if (nums[i] == nums[i - 1] + 1) {
                currentLength++;
            } else {
                maxLength = Math.max(maxLength, currentLength);
                currentLength = 1;
            }
        }

        return Math.max(maxLength, currentLength);
    }

    public static void main(String[] args) {
        LongestConsecutiveSequence_LC128 solution = new LongestConsecutiveSequence_LC128();

        // Test Case 1
        int[] nums1 = { 100, 4, 200, 1, 3, 2 };
        System.out.println("Test 1: " + solution.longestConsecutive(nums1)); // 4

        // Test Case 2
        int[] nums2 = { 0, 3, 7, 2, 5, 8, 4, 6, 0, 1 };
        System.out.println("Test 2: " + solution.longestConsecutive(nums2)); // 9

        // Test Case 3 - Empty array
        int[] nums3 = {};
        System.out.println("Test 3: " + solution.longestConsecutive(nums3)); // 0

        // Test Case 4 - Single element
        int[] nums4 = { 1 };
        System.out.println("Test 4: " + solution.longestConsecutive(nums4)); // 1

        // Test Case 5 - Duplicates
        int[] nums5 = { 1, 2, 0, 1 };
        System.out.println("Test 5: " + solution.longestConsecutive(nums5)); // 3
    }
}

