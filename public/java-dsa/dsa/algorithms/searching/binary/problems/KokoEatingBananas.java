package dsa.algorithms.searching.binary.problems;
/**
 * KOKO EATING BANANAS - Binary Search on Answer Space
 * LeetCode #875
 */

import java.util.*;

public class KokoEatingBananas {
    public int minEatingSpeed(int[] piles, int h) {
        int left = 1;
        int right = Arrays.stream(piles).max().getAsInt();
        int ans = right;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (canFinish(piles, h, mid)) {
                ans = mid;
                right = mid - 1;
            } else left = mid + 1;
        }
        return ans;
    }

    private boolean canFinish(int[] piles, int h, int k) {
        long hours = 0;
        for (int p : piles) {
            hours += (p + k - 1) / k;
            if (hours > h) return false;
        }
        return hours <= h;
    }

    public static void main(String[] args) {
        KokoEatingBananas s = new KokoEatingBananas();
        System.out.println(s.minEatingSpeed(new int[]{3,6,7,11}, 8) + " (expected 4)");
        System.out.println(s.minEatingSpeed(new int[]{30,11,23,4,20}, 5) + " (expected 30)");
    }
}

