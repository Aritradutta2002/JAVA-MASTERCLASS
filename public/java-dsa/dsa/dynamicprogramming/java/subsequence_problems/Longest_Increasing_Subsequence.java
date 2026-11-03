package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;

import java.util.Scanner;

/*
*   Author  : Aritra Dutta
*   Created : Monday, 07.04.2025  12:51 am
*/
public class Longest_Increasing_Subsequence {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for(int i = 0; i < n; i++){
            arr[i] = sc.nextInt();
        }
        System.out.println(LongestIncreasingSubsequence(arr));;
    }
    public static int LongestIncreasingSubsequence(int[] nums) {

        return solveLIS(0, -1, nums);

    }

    public static int solveLIS(int index, int prev, int [] nums){
        int n = nums.length;
        int len = 0;
        if(index >= n){
            return 0;
        }
        int notTakingElement = solveLIS(index+1, prev, nums);
        if(prev == -1 || nums[index] > nums[prev]){
            int takingElement = 1 + solveLIS(index+1, index, nums);
            len = Math.max(notTakingElement, takingElement);
        }
        return len;
    }
}
