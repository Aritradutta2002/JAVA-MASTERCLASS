package dsa.techniques.twopointers.problems.easy;

public class SquaresOfSortedArray_Leetcode977 {
    public static int[] sortedSquares(int[] nums) {
        int n = nums.length, l = 0, r = n - 1, w = n - 1;
        int[] res = new int[n];
        while (l <= r) {
            int L = nums[l] * nums[l], R = nums[r] * nums[r];
            if (L > R) { res[w--] = L; l++; } else { res[w--] = R; r--; }
        }
        return res;
    }
}

