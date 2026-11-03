package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Find Peak Element (Leetcode 162)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Microsoft, Google, Apple
 */
public class FindPeakElement_Leetcode162 {
    public static void main(String[] args) {
        int[] nums = {1, 2, 3, 1};
        System.out.println("Peak element index: " + findPeakElement(nums));
    }
    
    public static int findPeakElement(int[] nums) {
        int left = 0, right = nums.length - 1;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (nums[mid] > nums[mid + 1]) {
                // Peak is in left half (including mid)
                right = mid;
            } else {
                // Peak is in right half
                left = mid + 1;
            }
        }
        
        return left;
    }
}

