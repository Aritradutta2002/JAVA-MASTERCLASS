package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Koko Eating Bananas (Leetcode 875)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Google, Microsoft
 */
public class KokoEatingBananas_Leetcode875 {
    public static void main(String[] args) {
        int[] piles = {3, 6, 7, 11};
        int h = 8;
        System.out.println("Minimum eating speed: " + minEatingSpeed(piles, h));
    }
    
    public static int minEatingSpeed(int[] piles, int h) {
        int left = 1;
        int right = getMax(piles);
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (canFinish(piles, mid, h)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    private static boolean canFinish(int[] piles, int k, int h) {
        long hours = 0;
        for (int pile : piles) {
            hours += (pile + k - 1) / k; // Ceiling division
        }
        return hours <= h;
    }
    
    private static int getMax(int[] piles) {
        int max = 0;
        for (int pile : piles) {
            max = Math.max(max, pile);
        }
        return max;
    }
}

