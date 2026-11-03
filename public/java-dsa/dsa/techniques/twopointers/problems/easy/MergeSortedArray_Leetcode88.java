package dsa.techniques.twopointers.problems.easy;

public class MergeSortedArray_Leetcode88 {
    // merge nums2 into nums1 (size m+n) from the back
    public static void merge(int[] nums1, int m, int[] nums2, int n) {
        int i = m - 1, j = n - 1, w = m + n - 1;
        while (j >= 0) {
            if (i >= 0 && nums1[i] > nums2[j]) nums1[w--] = nums1[i--];
            else nums1[w--] = nums2[j--];
        }
    }
}

