package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Count of Smaller Numbers After Self (Leetcode 315)
 *   Difficulty: Hard
 *   Companies: Amazon, Google, Microsoft, Facebook
 */
import java.util.*;

public class CountSmallerNumbersAfterSelf_Leetcode315 {
    public static void main(String[] args) {
        int[] nums = {5, 2, 6, 1};
        System.out.println("Counts: " + countSmaller(nums));
    }
    
    public static List<Integer> countSmaller(int[] nums) {
        List<Integer> result = new ArrayList<>();
        List<Integer> sorted = new ArrayList<>();
        
        for (int i = nums.length - 1; i >= 0; i--) {
            int index = findIndex(sorted, nums[i]);
            result.add(0, index);
            sorted.add(index, nums[i]);
        }
        
        return result;
    }
    
    private static int findIndex(List<Integer> sorted, int target) {
        if (sorted.isEmpty()) {
            return 0;
        }
        
        int left = 0, right = sorted.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (sorted.get(mid) < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
}

