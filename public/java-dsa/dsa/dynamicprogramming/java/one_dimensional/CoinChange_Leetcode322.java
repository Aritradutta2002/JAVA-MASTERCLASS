package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;
import java.util.*;
/*
*   Author  : Aritra Dutta
*   Created : Saturday, 08.03.2025  09:53 pm
*/
public class CoinChange_Leetcode322 {
    public static void main(String[] args){
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        int [] arr = new int[n];
        for(int i=0;i<n;i++){
            arr[i] = sc.nextInt();
        }
        int amount = sc.nextInt();

        System.out.println(coinChangeTop(arr,amount));
        System.out.println(coinChangeBottom(arr,amount));
    }


    /* Using Dynamic Programming Top-Down Approach */
    static HashMap<Integer, Integer> memo = new HashMap<>();

    public static int dfs(int amount, int[] coins) {
        if (amount == 0) return 0;
        if (memo.containsKey(amount))
            return memo.get(amount);

        int res = Integer.MAX_VALUE;
        for (int coin : coins) {
            if (amount - coin >= 0) {
                int result = dfs(amount - coin, coins);
                if (result != Integer.MAX_VALUE) {
                    res = Math.min(res, 1 + result);
                }
            }
        }

        memo.put(amount, res);
        return res;
    }
    public static int coinChangeTop(int[] coins, int amount) {
        int minCoins = dfs(amount, coins);
        return minCoins == Integer.MAX_VALUE ? -1 : minCoins;
    }



    /* Using Dynamic Programming Bottom Down Approach */
    public static int coinChangeBottom(int[] coins, int amount) {
        int[] dp = new int[amount + 1];
        Arrays.fill(dp, amount + 1);
        dp[0] = 0;
        for (int i = 1; i <= amount; i++) {
            for (int coin : coins) {
                if (coin <= i) {
                    dp[i] = Math.min(dp[i], dp[i - coin] + 1);
                }
            }
        }
        return dp[amount] > amount ? -1 : dp[amount];
    }
}
