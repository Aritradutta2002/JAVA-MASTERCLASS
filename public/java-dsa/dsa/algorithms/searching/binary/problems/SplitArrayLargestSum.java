package dsa.algorithms.searching.binary.problems;
/**
 * SPLIT ARRAY LARGEST SUM - BS on Answer + Greedy check
 * LeetCode #410
 */

import java.util.*;

public class SplitArrayLargestSum {
    public int splitArray(int[] nums, int m) {
        int left = Arrays.stream(nums).max().getAsInt();
        int right = Arrays.stream(nums).sum();
        int ans = right;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (canSplit(nums, m, mid)) {
                ans = mid;
                right = mid - 1;
            } else left = mid + 1;
        }
        return ans;
    }

    private boolean canSplit(int[] nums, int m, int maxSum) {
        int count = 1, cur = 0;
        for (int x : nums) {
            if (cur + x > maxSum) { count++; cur = 0; }
            cur += x;
        }
        return count <= m;
    }

    public static void main(String[] args) {
        SplitArrayLargestSum s = new SplitArrayLargestSum();
        System.out.println(s.splitArray(new int[]{7,2,5,10,8}, 2) + " (expected 18)");
    }
}

