package dsa.algorithms.searching.binary.basics;

/*
 *   Author  : Aritra Dutta
 *   Problem : Split Array Largest Sum (Leetcode 410)
 *   Difficulty: Hard
 *   Companies: Amazon, Google, Facebook, Microsoft
 */
public class SplitArrayLargestSum_Leetcode410 {
    public static void main(String[] args) {
        int[] nums = {7, 2, 5, 10, 8};
        int k = 2;
        System.out.println("Largest sum: " + splitArray(nums, k));
    }
    
    public static int splitArray(int[] nums, int k) {
        int left = 0, right = 0;
        
        for (int num : nums) {
            left = Math.max(left, num);
            right += num;
        }
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (canSplit(nums, k, mid)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    private static boolean canSplit(int[] nums, int k, int maxSum) {
        int count = 1;
        int currentSum = 0;
        
        for (int num : nums) {
            if (currentSum + num > maxSum) {
                count++;
                currentSum = num;
                if (count > k) return false;
            } else {
                currentSum += num;
            }
        }
        
        return true;
    }
}

