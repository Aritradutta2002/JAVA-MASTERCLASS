package dsa.algorithms.searching.binary.problems;
/**
 * FIND MIN IN ROTATED SORTED ARRAY
 * LeetCode #153 (no duplicates)
 */

public class FindMinInRotatedArray {
    public int findMin(int[] nums) {
        int left = 0, right = nums.length - 1;
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (nums[mid] > nums[right]) left = mid + 1; // min in right half
            else right = mid; // mid could be min
        }
        return nums[left];
    }

    public static void main(String[] args) {
        FindMinInRotatedArray s = new FindMinInRotatedArray();
        System.out.println(s.findMin(new int[]{3,4,5,1,2}) + " (expected 1)");
        System.out.println(s.findMin(new int[]{4,5,6,7,0,1,2}) + " (expected 0)");
    }
}

