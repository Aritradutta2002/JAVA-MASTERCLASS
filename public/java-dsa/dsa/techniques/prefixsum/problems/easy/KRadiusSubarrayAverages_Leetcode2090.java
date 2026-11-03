
package dsa.techniques.prefixsum.problems.easy;

import java.util.Arrays;

/**
 * LeetCode 2090: K Radius Subarray Averages
 * Difficulty: Easy
 * 
 * Problem:
 * You are given a 0-indexed array nums of n integers, and an integer k.
 * The k-radius average for a subarray of nums centered at index i is the average of all elements
 * from index i - k to i + k (inclusive). If there are fewer than k elements before or after index i,
 * the k-radius average is -1.
 *
 * Key Insight:
 * This problem can be efficiently solved using a sliding window approach, which is a variation of prefix sums.
 * A window of size (2*k + 1) is required to calculate the average for a center element.
 * 1. We first calculate the sum of the initial window.
 * 2. Then, we slide this window across the array, updating the sum by subtracting the element that's leaving the window
 *    and adding the element that's entering.
 * This avoids recalculating the sum for each subarray, reducing the time complexity from O(n*k) to O(n).
 *
 * Example:
 * nums = [7,4,3,9,1,8,5,2,6], k = 3
 * Window size = 2*3 + 1 = 7
 * - The first possible center is at index 3. The window is [7,4,3,9,1,8,5]. Sum = 37. Average = 37/7 = 5.
 * - For indices 0, 1, 2, there are not enough elements to the left, so the average is -1.
 * - For indices 4, 5, 6, 7, 8, there are not enough elements to the right, so the average is -1.
 *
 * Time Complexity: O(n), because we iterate through the array once to build the initial sum and then once more to slide the window.
 * Space Complexity: O(n) to store the result array. If we can modify the input array, it could be O(1).
 */
 
public class KRadiusSubarrayAverages_Leetcode2090 {
    
    public static int[] getAverages(int[] nums, int k) {
        int n = nums.length;
        int[] averages = new int[n];
        // Initialize the result array with -1, as per the problem description.
        Arrays.fill(averages, -1);
        
        // If k is 0, the average is just the element itself.
        if (k == 0) {
            return nums;
        }
        
        // The size of the window required to calculate an average.
        int windowSize = 2 * k + 1;
        
        // If the window size is larger than the array, no valid average can be computed.
        if (windowSize > n) {
            return averages;
        }
        
        // Use 'long' for windowSum to prevent integer overflow, as sums can be large.
        long windowSum = 0;
        
        // --- Initial Window Calculation ---
        // Calculate the sum of the first window.
        for (int i = 0; i < windowSize; i++) {
            windowSum += nums[i];
        }
        
        // The first average can be calculated for the element at index k.
        averages[k] = (int) (windowSum / windowSize);
        
        // --- Sliding Window ---
        // Iterate from the end of the first window to the end of the array.
        for (int i = windowSize; i < n; i++) {
            // Slide the window: subtract the element leaving, add the element entering.
            windowSum = windowSum - nums[i - windowSize] + nums[i];
            
            // The center of the new window is at index (i - k).
            averages[i - k] = (int) (windowSum / windowSize);
        }
        
        return averages;
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {7, 4, 3, 9, 1, 8, 5, 2, 6};
        int k1 = 3;
        System.out.println("Nums: " + Arrays.toString(nums1) + ", k: " + k1);
        System.out.println("Averages: " + Arrays.toString(getAverages(nums1, k1))); // Expected: [-1, -1, -1, 5, -1, -1, -1]
        System.out.println("---");

        // Test case 2
        int[] nums2 = {1, 2, 3, 4, 5};
        int k2 = 1;
        System.out.println("Nums: " + Arrays.toString(nums2) + ", k: " + k2);
        System.out.println("Averages: " + Arrays.toString(getAverages(nums2, k2))); // Expected: [-1, 2, 3, 4, -1]
        System.out.println("---");

        // Test case 3: k is too large
        int[] nums3 = {1, 2, 3};
        int k3 = 2;
        System.out.println("Nums: " + Arrays.toString(nums3) + ", k: " + k3);
        System.out.println("Averages: " + Arrays.toString(getAverages(nums3, k3))); // Expected: [-1, -1, -1]
        System.out.println("---");
    }
}

