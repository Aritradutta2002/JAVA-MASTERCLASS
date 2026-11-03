package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Find Minimum in Rotated Sorted Array (Leetcode 153)
 *   Difficulty: Medium
 *   Companies: Amazon, Microsoft, Facebook, Google, Bloomberg
 */
public class FindMinInRotatedArray_Leetcode153 {
    public static void main(String[] args) {
        int[] nums = {3, 4, 5, 1, 2};
        System.out.println("Minimum element: " + findMin(nums));
    }
    
    public static int findMin(int[] nums) {
        int left = 0, right = nums.length - 1;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (nums[mid] > nums[right]) {
                // Minimum is in right half
                left = mid + 1;
            } else {
                // Minimum is in left half or at mid
                right = mid;
            }
        }
        
        return nums[left];
    }
}

