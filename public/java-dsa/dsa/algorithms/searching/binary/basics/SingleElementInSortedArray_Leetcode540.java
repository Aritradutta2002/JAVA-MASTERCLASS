package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Single Element in a Sorted Array (Leetcode 540)
 *   Difficulty: Medium
 *   Companies: Amazon, Google, Microsoft, Facebook
 */
public class SingleElementInSortedArray_Leetcode540 {
    public static void main(String[] args) {
        int[] nums = {1, 1, 2, 3, 3, 4, 4, 8, 8};
        System.out.println("Single element: " + singleNonDuplicate(nums));
    }
    
    public static int singleNonDuplicate(int[] nums) {
        int left = 0, right = nums.length - 1;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            // Ensure mid is even
            if (mid % 2 == 1) {
                mid--;
            }
            
            if (nums[mid] == nums[mid + 1]) {
                // Single element is on right side
                left = mid + 2;
            } else {
                // Single element is on left side
                right = mid;
            }
        }
        
        return nums[left];
    }
}

