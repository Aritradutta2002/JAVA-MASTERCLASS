package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Aggressive Cows (SPOJ/GFG Classic)
 *   Difficulty: Medium
 *   Companies: Amazon, Google, Microsoft (Very Popular)
 */
import java.util.Arrays;

public class AggressiveCows_Classic {
    public static void main(String[] args) {
        int[] stalls = {1, 2, 4, 8, 9};
        int cows = 3;
        System.out.println("Maximum minimum distance: " + aggressiveCows(stalls, cows));
    }
    
    public static int aggressiveCows(int[] stalls, int cows) {
        Arrays.sort(stalls);
        int left = 1;
        int right = stalls[stalls.length - 1] - stalls[0];
        int result = 0;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            if (canPlaceCows(stalls, cows, mid)) {
                result = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        return result;
    }
    
    private static boolean canPlaceCows(int[] stalls, int cows, int minDist) {
        int count = 1;
        int lastPos = stalls[0];
        
        for (int i = 1; i < stalls.length; i++) {
            if (stalls[i] - lastPos >= minDist) {
                count++;
                lastPos = stalls[i];
                if (count >= cows) {
                    return true;
                }
            }
        }
        
        return false;
    }
}

