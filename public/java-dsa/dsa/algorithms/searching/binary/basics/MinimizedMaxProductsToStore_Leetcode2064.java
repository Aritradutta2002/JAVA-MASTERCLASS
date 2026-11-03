package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Minimized Maximum of Products Distributed to Any Store (Leetcode 2064)
 *   Difficulty: Medium
 *   Companies: Amazon, Google
 */
public class MinimizedMaxProductsToStore_Leetcode2064 {
    public static void main(String[] args) {
        int n = 6;
        int[] quantities = {11, 6};
        System.out.println("Minimized maximum: " + minimizedMaximum(n, quantities));
    }
    
    public static int minimizedMaximum(int n, int[] quantities) {
        int left = 1;
        int right = 0;
        
        for (int q : quantities) {
            right = Math.max(right, q);
        }
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (canDistribute(n, quantities, mid)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    private static boolean canDistribute(int n, int[] quantities, int x) {
        int stores = 0;
        
        for (int q : quantities) {
            stores += (q + x - 1) / x;  // Ceiling division
            if (stores > n) {
                return false;
            }
        }
        
        return true;
    }
}

