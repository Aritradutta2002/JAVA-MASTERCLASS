package dsa.techniques.prefixsum.problems.easy;
import java.util.*;

/**
 * LeetCode 560: Subarray Sum Equals K
 * Difficulty: Medium (but fundamental for prefix sum interviews)
 * 
 * Problem: Given an array of integers nums and an integer k, 
 * return the total number of continuous subarrays whose sum equals to k.
 * 
 * Example:
 * Input: nums = [1,1,1], k = 2
 * Output: 2
 * 
 * Key Insight: If prefixSum[j] - prefixSum[i] = k, then subarray from i+1 to j has sum k
 * 
 * Time: O(n)
 * Space: O(n)
 */
public class SubarraySum_Leetcode560 {
    
    /**
     * Optimal approach using HashMap to store prefix sum frequencies
     */
    public static int subarraySum(int[] nums, int k) {
        Map<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1); // Empty prefix has sum 0
        
        int prefixSum = 0;
        int count = 0;
        
        for (int num : nums) {
            prefixSum += num;
            
            // Check if (prefixSum - k) exists in map
            // This means there's a subarray ending at current position with sum k
            if (prefixSumCount.containsKey(prefixSum - k)) {
                count += prefixSumCount.get(prefixSum - k);
            }
            
            // Add current prefix sum to map
            prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
        }
        
        return count;
    }
    
    /**
     * Brute force approach for comparison (O(n²))
     */
    public static int subarraySumBruteForce(int[] nums, int k) {
        int count = 0;
        int n = nums.length;
        
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int j = i; j < n; j++) {
                sum += nums[j];
                if (sum == k) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * Helper method to print all subarrays with sum k (for understanding)
     */
    public static void printSubarraysWithSum(int[] nums, int k) {
        System.out.println("Subarrays with sum " + k + ":");
        int n = nums.length;
        
        for (int i = 0; i < n; i++) {
            int sum = 0;
            for (int j = i; j < n; j++) {
                sum += nums[j];
                if (sum == k) {
                    System.out.print("Subarray [" + i + ", " + j + "]: ");
                    for (int x = i; x <= j; x++) {
                        System.out.print(nums[x] + " ");
                    }
                    System.out.println();
                }
            }
        }
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, 1, 1};
        int k1 = 2;
        System.out.println("Array: " + Arrays.toString(nums1) + ", k = " + k1);
        System.out.println("Count (HashMap): " + subarraySum(nums1, k1)); // Expected: 2
        System.out.println("Count (Brute Force): " + subarraySumBruteForce(nums1, k1));
        printSubarraysWithSum(nums1, k1);
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1, 2, 3};
        int k2 = 3;
        System.out.println("Array: " + Arrays.toString(nums2) + ", k = " + k2);
        System.out.println("Count (HashMap): " + subarraySum(nums2, k2)); // Expected: 2
        printSubarraysWithSum(nums2, k2);
        System.out.println();
        
        // Test case 3 - with negative numbers
        int[] nums3 = {1, -1, 0};
        int k3 = 0;
        System.out.println("Array: " + Arrays.toString(nums3) + ", k = " + k3);
        System.out.println("Count (HashMap): " + subarraySum(nums3, k3)); // Expected: 3
        printSubarraysWithSum(nums3, k3);
    }
}
