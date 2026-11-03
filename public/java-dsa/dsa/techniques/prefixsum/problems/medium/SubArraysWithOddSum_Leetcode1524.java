package dsa.techniques.prefixsum.problems.medium;
import java.util.*;
/*
*   Author: Aritra Dutta
*   Created: Tuesday, 25.02.2025 11:25 pm
*/
public class SubArraysWithOddSum_Leetcode1524 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int size = sc.nextInt();
        int[] nums = new int[size];
        for(int i = 0; i < size; i++){
            nums[i] = sc.nextInt();
        }
        System.out.println(numOfSubArrays(nums));
    }

    static public int numOfSubArrays(int[] arr) {
        long MOD = 1000000007;
        long oddCount = 0;
        long evenCount = 1;
        long total = 0;
        long prefixSum = 0;

        for(var x : arr){
            prefixSum += x;
            if(prefixSum % 2 == 1){
                total = (total + evenCount) % MOD;
                oddCount++;
            }
            else{
                total = (total + oddCount) % MOD;
                evenCount++;
            }
        }
        return (int) total;
    }
}

