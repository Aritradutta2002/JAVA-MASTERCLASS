package dsa.techniques.slidingwindow;
/**
 * MAXIMUM SUM SUBARRAY OF SIZE K - Sliding Window (Fixed size)
 */

import java.util.*;

public class MaxSumSubarrayOfSizeK {
    public int maxSumSubarray(int[] nums, int k) {
        int n = nums.length;
        if (n < k) return 0;
        int window = 0;
        for (int i = 0; i < k; i++) window += nums[i];
        int max = window;
        for (int i = k; i < n; i++) {
            window += nums[i] - nums[i - k];
            max = Math.max(max, window);
        }
        return max;
    }

    public static void main(String[] args) {
        MaxSumSubarrayOfSizeK s = new MaxSumSubarrayOfSizeK();
        System.out.println(s.maxSumSubarray(new int[]{2,1,5,1,3,2}, 3) + " (expected 9)");
        System.out.println(s.maxSumSubarray(new int[]{2,3,4,1,5}, 2) + " (expected 7)");
    }
}

