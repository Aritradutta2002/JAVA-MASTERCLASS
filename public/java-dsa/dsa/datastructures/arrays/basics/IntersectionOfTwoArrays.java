package dsa.datastructures.arrays.basics;
import java.util.Arrays;
import java.util.HashSet;

public class IntersectionOfTwoArrays {
       static  public void intersection(int[] nums1, int[] nums2) {
           HashSet<Integer> set = new HashSet<>();

           for (int num : nums1) {
               if (BinarySearch(nums2, num)) {
                   set.add(num);
               }
           }

           System.out.println(set);
       }

       static boolean BinarySearch(int[] nums, int target) {
           Arrays.sort(nums);
           int low = 0;
           int high = nums.length - 1;
           while (low <= high) {
               int mid = (low + high) / 2;
               if (nums[mid] == target) {
                   return true;
               }
               if (nums[mid] > target) {
                   high = mid - 1;
               }
               else if (nums[mid] < target) {
                   low = mid + 1;
               }
           }
           return false;
       }
    public static void main(String[] args) {
            int [] nums1 = {4,9,2,2,2};
            int [] nums2 = {9,4,9,8,2,2,2};
            intersection(nums1, nums2);
    }
}

