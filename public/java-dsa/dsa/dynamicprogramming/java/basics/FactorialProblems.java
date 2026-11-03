package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Thursday, 27.02.2025  04:25 pm
 */
public class FactorialProblems {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int n = sc.nextInt();
        System.out.println("Factorial of " + n + " is : " + factorialNumber(n));
    }
    /* Recursive approach and most Brute Force Approach --> n!  = n * (n - 1)!
     *   Time Complexity -- O(N)
     *   Space Complexity -- O(N)
     */
    public static long factorialNumber(int n) {
        if (n == 0 || n == 1) {
            return 1;
        }
        return n * factorialNumber(n - 1);
    }
    /* Improving with Dynamic Programming -> Like N! is already calculated, and We have to calculate M! = ?
     *   Time Complexity -- O(N)
     *   Space Complexity -- O(N)
     */
    public static long factorialDp(int n) {
        if (n == 0 || n == 1) {
            return 1;
        }
        long[] dp = new long[n + 1];
        dp[0] = 1;
        dp[1] = 1;

        for (int i = 2; i <= n; i++) {
            dp[i] = dp[i - 1] * i;
        }
        return dp[n];
    }
}
