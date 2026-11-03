package dsa.algorithms.searching.binary.basics;

import java.util.Arrays;

/*
 *   Author  : Aritra Dutta
 *   Problem : Magnetic Force Between Two Balls (Leetcode 1552)
 *   Difficulty: Medium
 *   Companies: Amazon, Google, Microsoft
 */
public class MagneticForceBetweenBalls_Leetcode1552 {
    public static void main(String[] args) {
        int[] position = {1, 2, 3, 4, 7};
        int m = 3;
        System.out.println("Maximum minimum distance: " + maxDistance(position, m));
    }
    
    public static int maxDistance(int[] position, int m) {
        Arrays.sort(position);
        int left = 1;
        int right = position[position.length - 1] - position[0];
        int result = 0;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            if (canPlaceBalls(position, m, mid)) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
    
    private static boolean canPlaceBalls(int[] position, int m, int minDist) {
        int count = 1;
        int lastPos = position[0];
        
        for (int i = 1; i < position.length; i++) {
            if (position[i] - lastPos >= minDist) {
                count++;
                lastPos = position[i];
                if (count >= m) {
                    return true;
                }
            }
        }
        
        return false;
    }
}

