package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;

import java.util.*;

/**
 * MAXIMUM LENGTH OF REPEATED SUBARRAY - Leetcode 718
 * Difficulty: Medium
 * Pattern: LCS (Longest Common Substring variant)
 * 
 * PROBLEM:
 * Given two integer arrays nums1 and nums2, return the maximum length of a subarray 
 * that appears in both arrays.
 * 
 * EXAMPLES:
 * Input: nums1 = [1,2,3,2,1], nums2 = [3,2,1]
 * Output: 3
 * Explanation: The repeated subarray with maximum length is [3,2,1].
 * 
 * Input: nums1 = [0,0,0,0,0], nums2 = [0,0,0,0,0]
 * Output: 5
 * 
 * KEY INSIGHT:
 * This is LCS for subarrays (contiguous), not subsequences.
 * We need to reset when characters don't match.
 */
public class MaximumLengthRepeatedSubarray_Leetcode718 {
    
    /**
     * APPROACH 1: 2D DP (Standard)
     * Time: O(m * n), Space: O(m * n)
     */
    public int findLength_2D(int[] nums1, int[] nums2) {
        int m = nums1.length, n = nums2.length;
        int[][] dp = new int[m + 1][n + 1];
        int maxLength = 0;
        
        for (int i = 1; i <= m; i++) {
            for (int j = 1; j <= n; j++) {
                if (nums1[i - 1] == nums2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                    maxLength = Math.max(maxLength, dp[i][j]);
                }
                // Note: else dp[i][j] = 0 (default), different from LCS
            }
        }
        
        return maxLength;
    }
    
    /**
     * APPROACH 2: Space Optimized DP
     * Time: O(m * n), Space: O(min(m, n))
     */
    public int findLength_Optimized(int[] nums1, int[] nums2) {
        // Make nums1 the smaller array for space optimization
        if (nums1.length > nums2.length) {
            return findLength_Optimized(nums2, nums1);
        }
        
        int m = nums1.length, n = nums2.length;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        int maxLength = 0;
        
        for (int j = 1; j <= n; j++) {
            for (int i = 1; i <= m; i++) {
                if (nums1[i - 1] == nums2[j - 1]) {
                    curr[i] = prev[i - 1] + 1;
                    maxLength = Math.max(maxLength, curr[i]);
                } else {
                    curr[i] = 0;
                }
            }
            int[] temp = prev;
            prev = curr;
            curr = temp;
            Arrays.fill(curr, 0); // Reset for next iteration
        }
        
        return maxLength;
    }
    
    /**
     * APPROACH 3: Rolling Hash (Advanced)
     * Time: O((m + n) * min(m, n) * log(min(m, n))), Space: O(m + n)
     * Uses binary search + rolling hash
     */
    public int findLength_RollingHash(int[] nums1, int[] nums2) {
        int left = 0, right = Math.min(nums1.length, nums2.length);
        int result = 0;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (hasCommonSubarray(nums1, nums2, mid)) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
    
    private boolean hasCommonSubarray(int[] nums1, int[] nums2, int length) {
        if (length == 0) return true;
        
        Set<String> set = new HashSet<>();
        
        // Add all subarrays of nums1 with given length
        for (int i = 0; i <= nums1.length - length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < i + length; j++) {
                sb.append(nums1[j]).append(",");
            }
            set.add(sb.toString());
        }
        
        // Check if any subarray of nums2 exists in set
        for (int i = 0; i <= nums2.length - length; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < i + length; j++) {
                sb.append(nums2[j]).append(",");
            }
            if (set.contains(sb.toString())) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 4: Brute Force with Optimizations
     * Time: O(m * n * min(m, n)), Space: O(1)
     * Good for understanding the problem
     */
    public int findLength_BruteForce(int[] nums1, int[] nums2) {
        int maxLength = 0;
        
        for (int i = 0; i < nums1.length; i++) {
            for (int j = 0; j < nums2.length; j++) {
                if (nums1[i] == nums2[j]) {
                    int length = 0;
                    int x = i, y = j;
                    
                    while (x < nums1.length && y < nums2.length && nums1[x] == nums2[y]) {
                        length++;
                        x++;
                        y++;
                    }
                    
                    maxLength = Math.max(maxLength, length);
                }
            }
        }
        
        return maxLength;
    }
    
    /**
     * Helper method to print DP table for visualization
     */
    public void printDPTable(int[] nums1, int[] nums2) {
        int m = nums1.length, n = nums2.length;
        int[][] dp = new int[m + 1][n + 1];
        
        System.out.println("DP Table for Maximum Length Repeated Subarray:");
        System.out.print("      ");
        for (int num : nums2) {
            System.out.printf("%2d ", num);
        }
        System.out.println();
        
        for (int i = 0; i <= m; i++) {
            if (i == 0) System.out.print("   ");
            else System.out.printf("%2d ", nums1[i - 1]);
            
            for (int j = 0; j <= n; j++) {
                if (i > 0 && j > 0 && nums1[i - 1] == nums2[j - 1]) {
                    dp[i][j] = dp[i - 1][j - 1] + 1;
                }
                System.out.printf("%2d ", dp[i][j]);
            }
            System.out.println();
        }
    }
    
    /**
     * Method to find and print all common subarrays of maximum length
     */
    public void findAllMaxLengthSubarrays(int[] nums1, int[] nums2) {
        int maxLength = findLength_2D(nums1, nums2);
        System.out.println("Maximum length: " + maxLength);
        
        if (maxLength == 0) {
            System.out.println("No common subarrays found.");
            return;
        }
        
        System.out.println("Common subarrays of maximum length:");
        Set<String> found = new HashSet<>();
        
        for (int i = 0; i < nums1.length; i++) {
            for (int j = 0; j < nums2.length; j++) {
                if (nums1[i] == nums2[j]) {
                    int length = 0;
                    int x = i, y = j;
                    List<Integer> subarray = new ArrayList<>();
                    
                    while (x < nums1.length && y < nums2.length && nums1[x] == nums2[y]) {
                        subarray.add(nums1[x]);
                        length++;
                        x++;
                        y++;
                    }
                    
                    if (length == maxLength) {
                        String subarrayStr = subarray.toString();
                        if (!found.contains(subarrayStr)) {
                            found.add(subarrayStr);
                            System.out.println("  " + subarray + " (nums1[" + i + ":" + (i + length) + "], nums2[" + j + ":" + (j + length) + "])");
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        MaximumLengthRepeatedSubarray_Leetcode718 solution = new MaximumLengthRepeatedSubarray_Leetcode718();
        
        System.out.println("=== Maximum Length of Repeated Subarray ===");
        
        // Test case 1
        int[] nums1 = {1, 2, 3, 2, 1};
        int[] nums2 = {3, 2, 1};
        System.out.println("Array 1: " + Arrays.toString(nums1));
        System.out.println("Array 2: " + Arrays.toString(nums2));
        System.out.println("Max Length (2D DP): " + solution.findLength_2D(nums1, nums2));
        System.out.println("Max Length (Optimized): " + solution.findLength_Optimized(nums1, nums2));
        System.out.println("Max Length (Brute Force): " + solution.findLength_BruteForce(nums1, nums2));
        solution.printDPTable(nums1, nums2);
        solution.findAllMaxLengthSubarrays(nums1, nums2);
        System.out.println();
        
        // Test case 2
        int[] nums3 = {0, 0, 0, 0, 0};
        int[] nums4 = {0, 0, 0, 0, 0};
        System.out.println("Array 1: " + Arrays.toString(nums3));
        System.out.println("Array 2: " + Arrays.toString(nums4));
        System.out.println("Max Length: " + solution.findLength_2D(nums3, nums4));
        System.out.println();
        
        // Test case 3
        int[] nums5 = {1, 2, 3, 4, 5};
        int[] nums6 = {6, 7, 8, 9, 10};
        System.out.println("Array 1: " + Arrays.toString(nums5));
        System.out.println("Array 2: " + Arrays.toString(nums6));
        System.out.println("Max Length: " + solution.findLength_2D(nums5, nums6));
    }
}