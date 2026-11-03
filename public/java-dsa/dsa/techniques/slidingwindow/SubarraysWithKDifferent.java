package dsa.techniques.slidingwindow;

import java.util.*;

/**
 * Subarrays with K Different Integers (LeetCode 992) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Count subarrays with exactly k different integers
 * Time: O(n), Space: O(k)
 * 
 * Key Trick: exactlyK = atMostK - atMost(K-1)
 */
public class SubarraysWithKDifferent {
    
    public int subarraysWithKDistinct(int[] nums, int k) {
        return atMostK(nums, k) - atMostK(nums, k - 1);
    }
    
    private int atMostK(int[] nums, int k) {
        Map<Integer, Integer> map = new HashMap<>();
        int left = 0, count = 0;
        
        for (int right = 0; right < nums.length; right++) {
            map.put(nums[right], map.getOrDefault(nums[right], 0) + 1);
            
            while (map.size() > k) {
                map.put(nums[left], map.get(nums[left]) - 1);
                if (map.get(nums[left]) == 0) {
                    map.remove(nums[left]);
                }
                left++;
            }
            
            // All subarrays ending at right with at most k distinct
            count += right - left + 1;
        }
        
        return count;
    }
    
    // Alternative: One-pass solution with two pointers
    public int subarraysWithKDistinctOnePass(int[] nums, int k) {
        return new Window(nums, k).count();
    }
    
    static class Window {
        int[] nums;
        int k;
        Map<Integer, Integer> map;
        int left1, left2; // Two left pointers
        
        Window(int[] nums, int k) {
            this.nums = nums;
            this.k = k;
            this.map = new HashMap<>();
            this.left1 = 0;
            this.left2 = 0;
        }
        
        int count() {
            int result = 0;
            
            for (int right = 0; right < nums.length; right++) {
                map.put(nums[right], map.getOrDefault(nums[right], 0) + 1);
                
                // Shrink left2 to maintain exactly k distinct
                while (map.size() > k) {
                    map.put(nums[left2], map.get(nums[left2]) - 1);
                    if (map.get(nums[left2]) == 0) {
                        map.remove(nums[left2]);
                    }
                    left2++;
                    left1 = left2;
                }
                
                // Shrink left1 to maintain at least k distinct
                while (map.get(nums[left1]) > 1) {
                    map.put(nums[left1], map.get(nums[left1]) - 1);
                    left1++;
                }
                
                if (map.size() == k) {
                    result += left1 - left2 + 1;
                }
            }
            
            return result;
        }
    }
    
    public static void main(String[] args) {
        SubarraysWithKDifferent solution = new SubarraysWithKDifferent();
        
        // Test Case 1
        int[] nums1 = {1, 2, 1, 2, 3};
        System.out.println(solution.subarraysWithKDistinct(nums1, 2)); // 7
        
        // Test Case 2
        int[] nums2 = {1, 2, 1, 3, 4};
        System.out.println(solution.subarraysWithKDistinct(nums2, 3)); // 3
    }
}

