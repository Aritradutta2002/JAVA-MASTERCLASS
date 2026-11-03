package dsa.datastructures.arrays.leetcode;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Thursday, 27.02.2025  07:32 pm
*/
public class BestTimeToBuyAndSellStock_Leetcode121 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int[] arr = new int[n];
        for(int i = 0; i < n; i++){
            arr[i] = sc.nextInt();
        }
        System.out.println(maxProfit(arr));
    }
    public static int maxProfit(int[] prices) {
        int maxProfit = 0;
        int minProfit = prices[0];

        for(var price : prices){
            maxProfit = Math.max(maxProfit, (price  - minProfit));
            minProfit = Math.min(minProfit, price);
        }
        return maxProfit;
    }
}

