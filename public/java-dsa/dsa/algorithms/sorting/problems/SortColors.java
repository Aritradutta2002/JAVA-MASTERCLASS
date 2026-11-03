package dsa.algorithms.sorting.problems;
/**
 * SORT COLORS (Dutch National Flag)
 * LeetCode #75 - Medium
 * Companies: Facebook, Microsoft, Amazon
 * 
 * Sort array with only 0s, 1s, 2s in ONE PASS
 * Pattern: THREE POINTERS
 */

public class SortColors {
    public void sortColors(int[] nums) {
        int low = 0, mid = 0, high = nums.length - 1;
        
        while (mid <= high) {
            if (nums[mid] == 0) {
                swap(nums, low++, mid++);
            } else if (nums[mid] == 1) {
                mid++;
            } else {
                swap(nums, mid, high--);
            }
        }
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
}

