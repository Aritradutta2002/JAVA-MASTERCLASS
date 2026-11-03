package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Thursday, 27.02.2025  11:01 am
*/
public class LengthOfLongestFibonacciSubsequence_Leetcode873 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for(int i = 0; i < n; i++){
            arr[i] = sc.nextInt();
        }
        System.out.println(lenLongestFibSubseq1(arr));
        System.out.println(lenLongestFibSubseq2(arr));
    }
/*      Brute Force Approach using Hashmap
        Time Complexity - O(N * N * logM)
        Space Complexity - O(N)
*/
    static public int lenLongestFibSubseq1(int[] arr) {
        Set <Integer> find = new HashSet<>();
        int maxLen = 0;
        for(var x : arr) {
            find.add(x);
        }

        for(int i = 0; i < arr.length; i++){
            for(int j = i + 1; j < arr.length; j++){
                int prev = arr[i];
                int curr = arr[i] + arr[j];
                int currLen = 2;
                while(find.contains(prev + curr)){
                    int temp = curr;
                    curr += prev;
                    prev = temp;

                    maxLen = Math.max(maxLen, ++currLen);
                }
            }
        }
        return maxLen;
    }

    /*  Using Dynamic Programming and Two Pointer Approach
    Time Complexity -   O(N*N)
    Space Complexity -  O(N*N)
     */

    static public int lenLongestFibSubseq2(int[] arr) {
        int n = arr.length;
        int [][] dp = new int[n][n];
        int maxLen=0;
        for(int cur=2;cur<n;cur++){
            int start = 0;
            int end = cur-1;
            while(start<end){
                int sum = arr[start] + arr[end];
                if(sum < arr[cur]){
                    start++;
                }else if(sum > arr[cur]){
                    end--;
                }else{
                    dp[end][cur] = dp[start][end] + 1;
                    maxLen = Math.max(maxLen, dp[end][cur]);
                    start++;
                    end--;
                }
            }
        }
        return (maxLen==0)?0:maxLen + 2;
    }
}
