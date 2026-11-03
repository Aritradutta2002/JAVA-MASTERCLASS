package dsa.techniques.hashing;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Tuesday, 25.02.2025  11:20 pm
*/
public class SubarraySumEqualsK_Leetcode560 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int size = sc.nextInt();
        int[] arr = new int[size];
        for(int i = 0; i < size; i++){
            arr[i] = sc.nextInt();
        }
        int k = sc.nextInt();
        System.out.println(subarraySum(arr, k));
    }

    static public int subarraySum(int[] arr, int k) {
        HashMap<Integer,Integer>map=new HashMap<>();
        map.put(0,1);
        int prefixSum=0;
        int totalSubarray=0;
        for (int j : arr) {
            prefixSum = prefixSum + j;
            int find = prefixSum - k;
            totalSubarray = totalSubarray + map.getOrDefault(find, 0);
            map.put(prefixSum, map.getOrDefault(prefixSum, 0) + 1);
        }
        return totalSubarray;
    }
}

