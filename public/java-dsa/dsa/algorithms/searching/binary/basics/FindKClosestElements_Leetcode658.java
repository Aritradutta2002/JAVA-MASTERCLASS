package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Find K Closest Elements (Leetcode 658)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Google, Microsoft
 */
import java.util.*;

public class FindKClosestElements_Leetcode658 {
    public static void main(String[] args) {
        int[] arr = {1, 2, 3, 4, 5};
        int k = 4;
        int x = 3;
        System.out.println("K closest elements: " + findClosestElements(arr, k, x));
    }
    
    public static List<Integer> findClosestElements(int[] arr, int k, int x) {
        int left = 0, right = arr.length - k;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (x - arr[mid] > arr[mid + k] - x) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        List<Integer> result = new ArrayList<>();
        for (int i = left; i < left + k; i++) {
            result.add(arr[i]);
        }
        
        return result;
    }
}

