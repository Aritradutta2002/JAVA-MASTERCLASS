package dsa.techniques.slidingwindow;
/**
 * SUBARRAY PRODUCT LESS THAN K - Sliding Window
 * LeetCode #713
 */

import java.util.*;

public class SubarrayProductLessThanK {
    public int numSubarrayProductLessThanK(int[] nums, int k) {
        if (k <= 1) return 0;
        int left = 0; long prod = 1; int ans = 0;
        for (int right = 0; right < nums.length; right++) {
            prod *= nums[right];
            while (prod >= k) prod /= nums[left++];
            ans += right - left + 1;
        }
        return ans;
    }

    public static void main(String[] args) {
        SubarrayProductLessThanK s = new SubarrayProductLessThanK();
        System.out.println(s.numSubarrayProductLessThanK(new int[]{10,5,2,6}, 100) + " (expected 8)");
    }
}

