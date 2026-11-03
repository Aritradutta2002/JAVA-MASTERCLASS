package dsa.datastructures.arrays.basics;

/*
 * Problem: Best Time to Buy and Sell Stock
 * LeetCode: #121
 * Difficulty: Easy
 * Companies: Amazon, Facebook, Google, Microsoft, Apple, Bloomberg, Uber
 * 
 * Problem Statement:
 * You are given an array prices where prices[i] is the price of a given stock on the i-th day.
 * You want to maximize your profit by choosing a single day to buy one stock and choosing 
 * a different day in the future to sell that stock.
 * Return the maximum profit you can achieve from this transaction. If you cannot achieve 
 * any profit, return 0.
 *
 * Example 1:
 * Input: prices = [7,1,5,3,6,4]
 * Output: 5
 * Explanation: Buy on day 2 (price = 1) and sell on day 5 (price = 6), profit = 6-1 = 5.
 *
 * Example 2:
 * Input: prices = [7,6,4,3,1]
 * Output: 0
 *
 * Constraints:
 * - 1 <= prices.length <= 10^5
 * - 0 <= prices[i] <= 10^4
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1)
 *
 * Approach:
 * Track minimum price seen so far and maximum profit. For each price, calculate profit
 * if we bought at minimum and sell now.
 */

public class BestTimeToBuySellStock_LC121 {

    // Optimal one-pass solution
    public int maxProfit(int[] prices) {
        if (prices == null || prices.length == 0) {
            return 0;
        }

        int minPrice = Integer.MAX_VALUE;
        int maxProfit = 0;

        for (int price : prices) {
            // Update minimum price
            minPrice = Math.min(minPrice, price);

            // Update maximum profit
            maxProfit = Math.max(maxProfit, price - minPrice);
        }

        return maxProfit;
    }

    // Alternative: Explicit tracking
    public int maxProfitExplicit(int[] prices) {
        if (prices.length < 2)
            return 0;

        int buyDay = 0;
        int maxProfit = 0;

        for (int i = 1; i < prices.length; i++) {
            // If current price is lower, update buy day
            if (prices[i] < prices[buyDay]) {
                buyDay = i;
            } else {
                // Calculate profit if selling today
                maxProfit = Math.max(maxProfit, prices[i] - prices[buyDay]);
            }
        }

        return maxProfit;
    }

    // Brute Force - O(n^2)
    public int maxProfitBruteForce(int[] prices) {
        int maxProfit = 0;

        for (int i = 0; i < prices.length; i++) {
            for (int j = i + 1; j < prices.length; j++) {
                maxProfit = Math.max(maxProfit, prices[j] - prices[i]);
            }
        }

        return maxProfit;
    }

    public static void main(String[] args) {
        BestTimeToBuySellStock_LC121 solution = new BestTimeToBuySellStock_LC121();

        // Test Case 1
        int[] prices1 = { 7, 1, 5, 3, 6, 4 };
        System.out.println("Test 1: " + solution.maxProfit(prices1)); // 5

        // Test Case 2
        int[] prices2 = { 7, 6, 4, 3, 1 };
        System.out.println("Test 2: " + solution.maxProfit(prices2)); // 0

        // Test Case 3
        int[] prices3 = { 1, 2, 3, 4, 5 };
        System.out.println("Test 3: " + solution.maxProfit(prices3)); // 4

        // Test Case 4
        int[] prices4 = { 2, 4, 1 };
        System.out.println("Test 4: " + solution.maxProfit(prices4)); // 2
    }
}

