package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Find Minimum in Rotated Sorted Array II (Leetcode 154)
 *   Difficulty: Hard
 *   Companies: Amazon, Microsoft, Google
 */
public class FindMinInRotatedArrayII_Leetcode154 {
    public static void main(String[] args) {
        int[] nums = {2, 2, 2, 0, 1};
        System.out.println("Minimum element: " + findMin(nums));
    }
    
    public static int findMin(int[] nums) {
        int left = 0, right = nums.length - 1;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (nums[mid] > nums[right]) {
                left = mid + 1;
            } else if (nums[mid] < nums[right]) {
                right = mid;
            } else {
                // nums[mid] == nums[right], reduce search space
                right--;
            }
        }
        
        return nums[left];
    }
}

