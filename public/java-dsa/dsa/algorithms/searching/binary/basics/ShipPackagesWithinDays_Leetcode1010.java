package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Capacity To Ship Packages Within D Days (Leetcode 1010)
 *   Difficulty: Medium
 *   Companies: Amazon, Microsoft, Google, Facebook
 */
public class ShipPackagesWithinDays_Leetcode1010 {
    public static void main(String[] args) {
        int[] weights = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        int days = 5;
        System.out.println("Minimum capacity: " + shipWithinDays(weights, days));
    }
    
    public static int shipWithinDays(int[] weights, int days) {
        int left = 0, right = 0;
        
        for (int weight : weights) {
            left = Math.max(left, weight);
            right += weight;
        }
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (canShip(weights, days, mid)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    private static boolean canShip(int[] weights, int days, int capacity) {
        int daysNeeded = 1;
        int currentWeight = 0;
        
        for (int weight : weights) {
            if (currentWeight + weight > capacity) {
                daysNeeded++;
                currentWeight = 0;
            }
            currentWeight += weight;
        }
        
        return daysNeeded <= days;
    }
}

