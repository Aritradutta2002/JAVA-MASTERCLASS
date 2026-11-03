package dsa.techniques.twopointers.problems.medium;

import java.util.*;

public class PartitionArrayAccordingToPivot_Leetcode2161 {
    public static int[] pivotArray(int[] nums, int pivot) {
        int n = nums.length;
        int[] res = new int[n];
        int idx = 0, countPivot = 0;
        for (int x : nums) if (x < pivot) res[idx++] = x;
        for (int x : nums) if (x == pivot) countPivot++;
        for (int i = 0; i < countPivot; i++) res[idx++] = pivot;
        for (int x : nums) if (x > pivot) res[idx++] = x;
        return res;
    }
}

