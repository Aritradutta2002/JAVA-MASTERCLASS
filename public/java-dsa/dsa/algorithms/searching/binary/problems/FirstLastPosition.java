package dsa.algorithms.searching.binary.problems;
/**
 * FIND FIRST AND LAST POSITION OF ELEMENT IN SORTED ARRAY
 * LeetCode #34 - Binary search boundaries
 */

import java.util.*;

public class FirstLastPosition {
    public int[] searchRange(int[] nums, int target) {
        return new int[]{lowerBound(nums, target), upperBound(nums, target)};
    }

    private int lowerBound(int[] a, int x) {
        int l = 0, r = a.length - 1, ans = -1;
        while (l <= r) {
            int m = l + (r - l) / 2;
            if (a[m] >= x) { if (a[m] == x) ans = m; r = m - 1; }
            else l = m + 1;
        }
        return ans;
    }

    private int upperBound(int[] a, int x) {
        int l = 0, r = a.length - 1, ans = -1;
        while (l <= r) {
            int m = l + (r - l) / 2;
            if (a[m] <= x) { if (a[m] == x) ans = m; l = m + 1; }
            else r = m - 1;
        }
        return ans;
    }

    public static void main(String[] args) {
        FirstLastPosition s = new FirstLastPosition();
        System.out.println(Arrays.toString(s.searchRange(new int[]{5,7,7,8,8,10}, 8)) + " (expected [3,4])");
        System.out.println(Arrays.toString(s.searchRange(new int[]{5,7,7,8,8,10}, 6)) + " (expected [-1,-1])");
    }
}

