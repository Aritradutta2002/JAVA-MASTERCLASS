
package dsa.techniques.prefixsum.problems.medium;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * LeetCode 325: Maximum Size Subarray Sum Equals k
 * Difficulty: Medium
 * 
 * Problem:
 * Given an array nums and a target value k, find the maximum length of a subarray that sums to k.
 * If there isn't one, return 0 instead.
 *
 * Key Insight:
 * This is a classic prefix sum problem. The core idea is to use a HashMap to store the first time we see a particular prefix sum.
 * Let prefixSum[i] be the sum of elements from index 0 to i.
 * The sum of a subarray from index j to i (inclusive) is prefixSum[i] - prefixSum[j-1].
 * We are looking for a subarray sum equal to k. So, prefixSum[i] - prefixSum[j-1] = k.
 * Rearranging this, we get: prefixSum[j-1] = prefixSum[i] - k.
 *
 * So, for each index i, we calculate the current prefixSum and check if the HashMap contains (prefixSum - k).
 * If it does, it means we found a subarray ending at i that sums to k. The length of this subarray is i - map.get(prefixSum - k).
 * We want the *maximum* length, so we only store the *first* occurrence of a prefix sum in the map. This ensures that when we calculate the length, we are using the earliest possible start for the subarray, maximizing its length.
 *
 * Example:
 * nums = [1, -1, 5, -2, 3], k = 3
 * i=0, prefixSum=1. map={0:-1, 1:0}
 * i=1, prefixSum=0. map={0:-1, 1:0} (0 is already there)
 * i=2, prefixSum=5. map={0:-1, 1:0, 5:2}. Check for 5-3=2. Not in map.
 * i=3, prefixSum=3. map={0:-1, 1:0, 5:2, 3:3}. Check for 3-3=0. Found! len = 3 - map.get(0) = 3 - (-1) = 4. maxLen=4.
 * i=4, prefixSum=6. map={...}. Check for 6-3=3. Found! len = 4 - map.get(3) = 4 - 3 = 1. maxLen remains 4.
 *
 * Time Complexity: O(n), as we iterate through the array once.
 * Space Complexity: O(n) in the worst case, as the HashMap could store up to n distinct prefix sums.
 */
public class MaxSizeSubarraySumEqualsK_Leetcode325 {
    
    public static int maxSubArrayLen(int[] nums, int k) {
        int maxLen = 0;
        long prefixSum = 0; // Use long for prefixSum to avoid overflow, although not strictly necessary for this problem constraints
        
        // Map to store the first index where a prefix sum occurs.
        Map<Long, Integer> map = new HashMap<>();
        
        // Crucial Step: Initialize map with (0, -1).
        // This handles the case where the subarray with sum k starts from index 0.
        // For example, if nums=[3, 4] and k=3, at i=0, prefixSum=3. We check for (3-3)=0.
        // map.get(0) gives -1, so length = 0 - (-1) = 1.
        map.put(0L, -1);
        
        for (int i = 0; i < nums.length; i++) {
            // Update the running prefix sum.
            prefixSum += nums[i];
            
            // Check if a previous prefix sum exists that is `prefixSum - k`.
            long target = prefixSum - k;
            if (map.containsKey(target)) {
                // If found, we have a subarray that sums to k.
                // The length is the current index `i` minus the index of that previous prefix sum.
                int currentLength = i - map.get(target);
                maxLen = Math.max(maxLen, currentLength);
            }
            
            // If the current prefix sum is not already in the map, add it.
            // We do not update it if it exists, because we want the earliest (leftmost) index
            // to maximize the subarray length.
            if (!map.containsKey(prefixSum)) {
                map.put(prefixSum, i);
            }
        }
        
        return maxLen;
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, -1, 5, -2, 3};
        int k1 = 3;
        System.out.println("Nums: " + Arrays.toString(nums1) + ", k: " + k1);
        System.out.println("Max Length: " + maxSubArrayLen(nums1, k1)); // Expected: 4
        System.out.println("---");

        // Test case 2
        int[] nums2 = {-2, -1, 2, 1};
        int k2 = 1;
        System.out.println("Nums: " + Arrays.toString(nums2) + ", k: " + k2);
        System.out.println("Max Length: " + maxSubArrayLen(nums2, k2)); // Expected: 2
        System.out.println("---");

        // Test case 3: No such subarray
        int[] nums3 = {1, 2, 3, 4};
        int k3 = 15;
        System.out.println("Nums: " + Arrays.toString(nums3) + ", k: " + k3);
        System.out.println("Max Length: " + maxSubArrayLen(nums3, k3)); // Expected: 0
        System.out.println("---");
    }
}

