package dsa.techniques.hashing;

/*
 * Problem: Intersection of Two Arrays
 * LeetCode: #349
 * Difficulty: Easy
 * Companies: Amazon, Google, Microsoft
 * 
 * Problem Statement:
 * Given two integer arrays nums1 and nums2, return an array of their intersection.
 * Each element in the result must be unique and you may return the result in any order.
 *
 * Example 1:
 * Input: nums1 = [1,2,2,1], nums2 = [2,2]
 * Output: [2]
 *
 * Example 2:
 * Input: nums1 = [4,9,5], nums2 = [9,4,9,8,4]
 * Output: [9,4] or [4,9]
 *
 * Constraints:
 * - 1 <= nums1.length, nums2.length <= 1000
 * - 0 <= nums1[i], nums2[i] <= 1000
 *
 * Time Complexity: O(n + m)
 * Space Complexity: O(min(n, m))
 *
 * Approach:
 * Use HashSet to store elements from first array, then check second array.
 */

import java.util.*;

public class IntersectionTwoArrays_LC349 {

    // Solution using HashSet
    public int[] intersection(int[] nums1, int[] nums2) {
        Set<Integer> set1 = new HashSet<>();
        Set<Integer> result = new HashSet<>();

        // Add all elements from nums1 to set
        for (int num : nums1) {
            set1.add(num);
        }

        // Find intersection
        for (int num : nums2) {
            if (set1.contains(num)) {
                result.add(num);
            }
        }

        // Convert to array
        int[] intersection = new int[result.size()];
        int i = 0;
        for (int num : result) {
            intersection[i++] = num;
        }

        return intersection;
    }

    // Using streams (more concise)
    public int[] intersectionStreams(int[] nums1, int[] nums2) {
        Set<Integer> set1 = new HashSet<>();
        for (int num : nums1)
            set1.add(num);

        Set<Integer> set2 = new HashSet<>();
        for (int num : nums2)
            set2.add(num);

        set1.retainAll(set2);

        return set1.stream().mapToInt(Integer::intValue).toArray();
    }

    // Alternative: Two pointers after sorting
    public int[] intersectionTwoPointers(int[] nums1, int[] nums2) {
        Arrays.sort(nums1);
        Arrays.sort(nums2);

        Set<Integer> result = new HashSet<>();
        int i = 0, j = 0;

        while (i < nums1.length && j < nums2.length) {
            if (nums1[i] < nums2[j]) {
                i++;
            } else if (nums1[i] > nums2[j]) {
                j++;
            } else {
                result.add(nums1[i]);
                i++;
                j++;
            }
        }

        int[] intersection = new int[result.size()];
        int idx = 0;
        for (int num : result) {
            intersection[idx++] = num;
        }

        return intersection;
    }

    public static void main(String[] args) {
        IntersectionTwoArrays_LC349 solution = new IntersectionTwoArrays_LC349();

        // Test Case 1
        int[] nums1_1 = { 1, 2, 2, 1 };
        int[] nums2_1 = { 2, 2 };
        System.out.println("Test 1: " + Arrays.toString(solution.intersection(nums1_1, nums2_1)));
        // Output: [2]

        // Test Case 2
        int[] nums1_2 = { 4, 9, 5 };
        int[] nums2_2 = { 9, 4, 9, 8, 4 };
        System.out.println("Test 2: " + Arrays.toString(solution.intersection(nums1_2, nums2_2)));
        // Output: [4, 9] or [9, 4]

        // Test Case 3
        int[] nums1_3 = { 1, 2, 3 };
        int[] nums2_3 = { 4, 5, 6 };
        System.out.println("Test 3: " + Arrays.toString(solution.intersection(nums1_3, nums2_3)));
        // Output: []
    }
}

