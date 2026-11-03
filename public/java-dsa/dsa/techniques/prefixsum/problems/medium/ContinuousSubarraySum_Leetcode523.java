package dsa.techniques.prefixsum.problems.medium;
import java.util.*;

/**
 * LeetCode 523: Continuous Subarray Sum
 * Difficulty: Medium
 * 
 * Problem: Given an integer array nums and an integer k, return true if nums has 
 * a continuous subarray of size at least two whose elements sum up to a multiple of k.
 * 
 * Example:
 * Input: nums = [23,2,4,6,7], k = 6
 * Output: true
 * Explanation: [2, 4] is a continuous subarray of size 2 whose elements sum up to 6.
 * 
 * Key Insight: If (prefixSum[i] % k) == (prefixSum[j] % k), then subarray from i+1 to j
 * has sum divisible by k. We need length >= 2, so j - i >= 2.
 * 
 * Time: O(n)
 * Space: O(min(n, k))
 */
public class ContinuousSubarraySum_Leetcode523 {
    
    /**
     * Optimal solution using modular arithmetic and HashMap
     */
    public static boolean checkSubarraySum(int[] nums, int k) {
        Map<Integer, Integer> remainderIndexMap = new HashMap<>();
        remainderIndexMap.put(0, -1); // Handle case where subarray starts from index 0
        
        int prefixSum = 0;
        
        for (int i = 0; i < nums.length; i++) {
            prefixSum += nums[i];
            int remainder = prefixSum % k;
            
            // Handle negative remainders (in case of negative numbers)
            if (remainder < 0) {
                remainder += k;
            }
            
            if (remainderIndexMap.containsKey(remainder)) {
                // Check if subarray length is at least 2
                if (i - remainderIndexMap.get(remainder) > 1) {
                    return true;
                }
            } else {
                remainderIndexMap.put(remainder, i);
            }
        }
        
        return false;
    }
    
    /**
     * Brute force approach for comparison (O(n²))
     */
    public static boolean checkSubarraySumBruteForce(int[] nums, int k) {
        int n = nums.length;
        
        for (int i = 0; i < n - 1; i++) {
            int sum = nums[i];
            for (int j = i + 1; j < n; j++) {
                sum += nums[j];
                if (sum % k == 0) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method to find and print all valid subarrays
     */
    public static void findAllValidSubarrays(int[] nums, int k) {
        System.out.println("Valid subarrays with sum divisible by " + k + ":");
        int n = nums.length;
        boolean found = false;
        
        for (int i = 0; i < n - 1; i++) {
            int sum = nums[i];
            for (int j = i + 1; j < n; j++) {
                sum += nums[j];
                if (sum % k == 0) {
                    System.out.print("Subarray [" + i + ", " + j + "]: ");
                    for (int x = i; x <= j; x++) {
                        System.out.print(nums[x] + " ");
                    }
                    System.out.println("(sum = " + sum + ")");
                    found = true;
                }
            }
        }
        
        if (!found) {
            System.out.println("No valid subarrays found.");
        }
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {23, 2, 4, 6, 7};
        int k1 = 6;
        System.out.println("Array: " + Arrays.toString(nums1) + ", k = " + k1);
        System.out.println("Has valid subarray (Optimal): " + checkSubarraySum(nums1, k1)); // Expected: true
        System.out.println("Has valid subarray (Brute Force): " + checkSubarraySumBruteForce(nums1, k1));
        findAllValidSubarrays(nums1, k1);
        System.out.println();
        
        // Test case 2
        int[] nums2 = {23, 2, 4, 6, 6};
        int k2 = 7;
        System.out.println("Array: " + Arrays.toString(nums2) + ", k = " + k2);
        System.out.println("Has valid subarray (Optimal): " + checkSubarraySum(nums2, k2)); // Expected: true
        findAllValidSubarrays(nums2, k2);
        System.out.println();
        
        // Test case 3 - No valid subarray
        int[] nums3 = {23, 2, 4, 6, 7};
        int k3 = 13;
        System.out.println("Array: " + Arrays.toString(nums3) + ", k = " + k3);
        System.out.println("Has valid subarray (Optimal): " + checkSubarraySum(nums3, k3)); // Expected: false
        findAllValidSubarrays(nums3, k3);
        System.out.println();
        
        // Test case 4 - Edge case with zeros
        int[] nums4 = {5, 0, 0};
        int k4 = 3;
        System.out.println("Array: " + Arrays.toString(nums4) + ", k = " + k4);
        System.out.println("Has valid subarray (Optimal): " + checkSubarraySum(nums4, k4)); // Expected: true
        findAllValidSubarrays(nums4, k4);
    }
}
