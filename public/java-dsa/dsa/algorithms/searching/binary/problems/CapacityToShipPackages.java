package dsa.algorithms.searching.binary.problems;
/**
 * CAPACITY TO SHIP PACKAGES WITHIN D DAYS - BS on Answer
 * LeetCode #1011
 */

import java.util.*;

public class CapacityToShipPackages {
    public int shipWithinDays(int[] weights, int days) {
        int left = Arrays.stream(weights).max().getAsInt();
        int right = Arrays.stream(weights).sum();
        int ans = right;
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (canShip(weights, days, mid)) {
                ans = mid;
                right = mid - 1;
            } else left = mid + 1;
        }
        return ans;
    }

    private boolean canShip(int[] w, int days, int cap) {
        int d = 1, cur = 0;
        for (int x : w) {
            if (cur + x > cap) { d++; cur = 0; }
            cur += x;
        }
        return d <= days;
    }

    public static void main(String[] args) {
        CapacityToShipPackages s = new CapacityToShipPackages();
        System.out.println(s.shipWithinDays(new int[]{1,2,3,4,5,6,7,8,9,10}, 5) + " (expected 15)");
    }
}

