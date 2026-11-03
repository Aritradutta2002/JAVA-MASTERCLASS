package dsa.techniques.prefixsum.problems.hard;
import java.util.*;

/**
 * LeetCode 992: Subarrays with K Different Integers
 * Difficulty: Hard
 * 
 * Problem: Given an integer array nums and an integer k, return the number of 
 * good subarrays of nums. A good subarray is a subarray where the number of 
 * different integers in it is exactly equal to k.
 * 
 * Example:
 * Input: nums = [1,2,1,2,3], k = 2
 * Output: 7
 * Explanation: Subarrays with exactly 2 distinct integers: 
 * [1,2], [2,1], [1,2], [2,3], [1,2,1], [2,1,2], [1,2,1,2]
 * 
 * Key Insight: exactlyK(nums, k) = atMostK(nums, k) - atMostK(nums, k-1)
 * 
 * Time: O(n)
 * Space: O(k)
 */
public class SubarrayWithKDistinct_Leetcode992 {
    
    /**
     * Main solution using sliding window technique
     */
    public static int subarraysWithKDistinct(int[] nums, int k) {
        return atMostK(nums, k) - atMostK(nums, k - 1);
    }
    
    /**
     * Helper function to count subarrays with at most k distinct integers
     */
    private static int atMostK(int[] nums, int k) {
        if (k == 0) return 0;
        
        Map<Integer, Integer> count = new HashMap<>();
        int left = 0;
        int result = 0;
        
        for (int right = 0; right < nums.length; right++) {
            // Add current element to window
            count.put(nums[right], count.getOrDefault(nums[right], 0) + 1);
            
            // Shrink window if we have more than k distinct elements
            while (count.size() > k) {
                count.put(nums[left], count.get(nums[left]) - 1);
                if (count.get(nums[left]) == 0) {
                    count.remove(nums[left]);
                }
                left++;
            }
            
            // Add count of all subarrays ending at 'right'
            result += right - left + 1;
        }
        
        return result;
    }
    
    /**
     * Alternative approach using prefix sum concept with frequency tracking
     */
    public static int subarraysWithKDistinctAlternative(int[] nums, int k) {
        return countWithExactlyK(nums, k);
    }
    
    private static int countWithExactlyK(int[] nums, int k) {
        int n = nums.length;
        int result = 0;
        
        for (int i = 0; i < n; i++) {
            Map<Integer, Integer> distinctCount = new HashMap<>();
            for (int j = i; j < n; j++) {
                distinctCount.put(nums[j], distinctCount.getOrDefault(nums[j], 0) + 1);
                
                if (distinctCount.size() == k) {
                    result++;
                } else if (distinctCount.size() > k) {
                    break; // No point in extending further
                }
            }
        }
        
        return result;
    }
    
    /**
     * Helper method to print all subarrays with exactly k distinct integers
     */
    public static void printSubarraysWithKDistinct(int[] nums, int k) {
        System.out.println("Subarrays with exactly " + k + " distinct integers:");
        int n = nums.length;
        int count = 0;
        
        for (int i = 0; i < n; i++) {
            Set<Integer> distinct = new HashSet<>();
            for (int j = i; j < n; j++) {
                distinct.add(nums[j]);
                
                if (distinct.size() == k) {
                    System.out.print("Subarray [" + i + ", " + j + "]: ");
                    for (int x = i; x <= j; x++) {
                        System.out.print(nums[x] + " ");
                    }
                    System.out.println();
                    count++;
                } else if (distinct.size() > k) {
                    break;
                }
            }
        }
        
        System.out.println("Total count: " + count);
    }
    
    public static void main(String[] args) {
        // Test case 1
        int[] nums1 = {1, 2, 1, 2, 3};
        int k1 = 2;
        System.out.println("Array: " + Arrays.toString(nums1) + ", k = " + k1);
        System.out.println("Count (Sliding Window): " + subarraysWithKDistinct(nums1, k1)); // Expected: 7
        System.out.println("Count (Alternative): " + subarraysWithKDistinctAlternative(nums1, k1));
        printSubarraysWithKDistinct(nums1, k1);
        System.out.println();
        
        // Test case 2
        int[] nums2 = {1, 2, 1, 3, 4};
        int k2 = 3;
        System.out.println("Array: " + Arrays.toString(nums2) + ", k = " + k2);
        System.out.println("Count (Sliding Window): " + subarraysWithKDistinct(nums2, k2)); // Expected: 3
        printSubarraysWithKDistinct(nums2, k2);
        System.out.println();
        
        // Test case 3
        int[] nums3 = {1, 2, 3, 4, 5};
        int k3 = 1;
        System.out.println("Array: " + Arrays.toString(nums3) + ", k = " + k3);
        System.out.println("Count (Sliding Window): " + subarraysWithKDistinct(nums3, k3)); // Expected: 5
        
        // Performance test
        int[] largeArray = new int[1000];
        for (int i = 0; i < 1000; i++) {
            largeArray[i] = i % 100 + 1; // Numbers from 1 to 100
        }
        
        long startTime = System.currentTimeMillis();
        int result = subarraysWithKDistinct(largeArray, 10);
        long endTime = System.currentTimeMillis();
        
        System.out.println("Performance test (1000 elements, k=10): " + result + 
                          " (Time: " + (endTime - startTime) + "ms)");
    }
}
