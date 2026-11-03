package JAVA_DSA.DYNAMIC_PROGRAMMING.java.stock_problems;

/**
 * BEST TIME TO BUY AND SELL STOCK - Leetcode 121
 * Difficulty: Easy
 * Companies: Amazon, Microsoft, Bloomberg, Facebook, Google, Apple, Uber
 * 
 * PROBLEM:
 * You are given an array prices where prices[i] is the price of a given stock on the ith day.
 * You want to maximize your profit by choosing a single day to buy one stock and choosing 
 * a different day in the future to sell that stock.
 * Return the maximum profit you can achieve. If you cannot achieve any profit, return 0.
 * 
 * CONSTRAINTS:
 * - You can complete at most ONE transaction
 * - You must buy before you sell
 * 
 * EXAMPLES:
 * Input: prices = [7,1,5,3,6,4]
 * Output: 5
 * Explanation: Buy on day 2 (price = 1) and sell on day 5 (price = 6), profit = 6-1 = 5.
 * 
 * Input: prices = [7,6,4,3,1]
 * Output: 0
 * Explanation: No transaction is done and the max profit = 0.
 * 
 * PATTERN: One-pass tracking
 * KEY INSIGHT: Track minimum price so far and maximum profit
 */

public class BestTimeToBuySellStock_Leetcode121 {
    
    // ========== APPROACH 1: BRUTE FORCE (TLE) ==========
    // Time: O(n^2) - Check all pairs
    // Space: O(1)
    /**
     * BEGINNER'S EXPLANATION:
     * Try all possible buy-sell pairs where buy comes before sell.
     * For each buy day, check all possible future sell days.
     * 
     * For [7,1,5,3,6,4]:
     * Buy=7: sell=1,5,3,6,4 → max profit = -1 (loss)
     * Buy=1: sell=5,3,6,4 → max profit = 5 ✓
     * Buy=5: sell=3,6,4 → max profit = 1
     * Buy=3: sell=6,4 → max profit = 3
     * Buy=6: sell=4 → max profit = -2 (loss)
     * Buy=4: no future days
     */
    public int maxProfit_BruteForce(int[] prices) {
        int maxProfit = 0;
        
        for (int i = 0; i < prices.length; i++) {
            for (int j = i + 1; j < prices.length; j++) {
                int profit = prices[j] - prices[i];
                maxProfit = Math.max(maxProfit, profit);
            }
        }
        
        return maxProfit;
    }
    
    
    // ========== APPROACH 2: ONE PASS ⭐ OPTIMAL ==========
    // Time: O(n) - Single pass
    // Space: O(1) - Only 2 variables
    /**
     * BEGINNER'S EXPLANATION:
     * Key insight: To maximize profit, we want to:
     * - Buy at the LOWEST price so far
     * - Sell at current price
     * 
     * As we traverse:
     * - Track the minimum price seen so far (best buy price)
     * - Calculate profit if we sell today (current - min)
     * - Update maximum profit
     * 
     * Visualization for [7,1,5,3,6,4]:
     * 
     * Day  Price  MinSoFar  Profit  MaxProfit
     *  0     7       7        0         0
     *  1     1       1        0         0
     *  2     5       1        4         4
     *  3     3       1        2         4
     *  4     6       1        5         5  ✓
     *  5     4       1        3         5
     * 
     * Think of it as: "If I buy at the cheapest price so far 
     * and sell today, what's my profit?"
     */
    public int maxProfit(int[] prices) {
        int minPrice = Integer.MAX_VALUE;  // Lowest price seen so far
        int maxProfit = 0;                  // Maximum profit so far
        
        for (int price : prices) {
            // Update minimum price
            if (price < minPrice) {
                minPrice = price;
            }
            // Calculate profit if we sell at current price
            else {
                int profit = price - minPrice;
                maxProfit = Math.max(maxProfit, profit);
            }
        }
        
        return maxProfit;
    }
    
    
    // ========== APPROACH 3: ONE PASS (MORE CONCISE) ==========
    // Time: O(n)
    // Space: O(1)
    /**
     * Same logic, more compact code
     */
    public int maxProfit_Concise(int[] prices) {
        int minPrice = Integer.MAX_VALUE;
        int maxProfit = 0;
        
        for (int price : prices) {
            minPrice = Math.min(minPrice, price);
            maxProfit = Math.max(maxProfit, price - minPrice);
        }
        
        return maxProfit;
    }
    
    
    // ========== APPROACH 4: KADANE'S ALGORITHM VARIATION ==========
    // Time: O(n)
    // Space: O(1)
    /**
     * ADVANCED EXPLANATION:
     * This problem is similar to maximum subarray sum!
     * 
     * Transform prices to daily profit array:
     * prices = [7,1,5,3,6,4]
     * profits = [-, -6, 4, -2, 3, -2]
     *               ↑ day1-day0
     * 
     * Now find maximum subarray sum!
     * maxProfit = 4 + (-2) + 3 = 5 ✓
     * 
     * This is Kadane's algorithm!
     */
    public int maxProfit_Kadane(int[] prices) {
        if (prices.length < 2) return 0;
        
        int maxCurrent = 0;  // Max profit ending at current position
        int maxGlobal = 0;   // Overall max profit
        
        for (int i = 1; i < prices.length; i++) {
            // Daily profit (can be negative)
            int dailyProfit = prices[i] - prices[i - 1];
            
            // Either add to current profit or start fresh
            maxCurrent = Math.max(0, maxCurrent + dailyProfit);
            
            // Update global max
            maxGlobal = Math.max(maxGlobal, maxCurrent);
        }
        
        return maxGlobal;
    }
    
    
    // ========== APPROACH 5: DP STATE MACHINE ==========
    // Time: O(n)
    // Space: O(1)
    /**
     * ADVANCED EXPLANATION:
     * Think of it as a state machine with 2 states:
     * - State 0: Don't own stock
     * - State 1: Own stock
     * 
     * This generalizes to harder stock problems!
     * Good foundation for Stock II, III, IV, etc.
     */
    public int maxProfit_StateMachine(int[] prices) {
        int notOwn = 0;         // Max profit when not owning stock
        int own = -prices[0];   // Max profit when owning stock (bought at day 0)
        
        for (int i = 1; i < prices.length; i++) {
            // To not own stock today:
            // Either didn't own yesterday, OR sell today
            int newNotOwn = Math.max(notOwn, own + prices[i]);
            
            // To own stock today:
            // Either owned yesterday, OR buy today
            // (Can only buy once, so if we buy today, previous profit was 0)
            int newOwn = Math.max(own, -prices[i]);
            
            notOwn = newNotOwn;
            own = newOwn;
        }
        
        // At the end, not owning stock gives max profit
        return notOwn;
    }
    
    
    // ========== VISUALIZATION HELPER ==========
    public void visualizeProfit(int[] prices) {
        int minPrice = Integer.MAX_VALUE;
        int maxProfit = 0;
        int buyDay = 0, sellDay = 0;
        
        System.out.println("Day\tPrice\tMinPrice\tProfit\t\tMaxProfit");
        System.out.println("---\t-----\t--------\t------\t\t---------");
        
        for (int i = 0; i < prices.length; i++) {
            if (prices[i] < minPrice) {
                minPrice = prices[i];
                buyDay = i;
            }
            
            int profit = prices[i] - minPrice;
            if (profit > maxProfit) {
                maxProfit = profit;
                sellDay = i;
            }
            
            System.out.printf("%d\t%d\t%d\t\t%d\t\t%d\n", 
                            i, prices[i], minPrice, profit, maxProfit);
        }
        
        System.out.println("\nBest Strategy:");
        System.out.println("Buy on day " + buyDay + " at price " + prices[buyDay]);
        System.out.println("Sell on day " + sellDay + " at price " + prices[sellDay]);
        System.out.println("Profit: " + maxProfit);
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        BestTimeToBuySellStock_Leetcode121 solution = new BestTimeToBuySellStock_Leetcode121();
        
        // Test case 1
        int[] prices1 = {7, 1, 5, 3, 6, 4};
        System.out.println("Prices: [7,1,5,3,6,4]");
        System.out.println("Max Profit: " + solution.maxProfit(prices1)); // 5
        solution.visualizeProfit(prices1);
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 2
        int[] prices2 = {7, 6, 4, 3, 1};
        System.out.println("Prices: [7,6,4,3,1]");
        System.out.println("Max Profit: " + solution.maxProfit(prices2)); // 0
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 3
        int[] prices3 = {2, 4, 1};
        System.out.println("Prices: [2,4,1]");
        System.out.println("Max Profit: " + solution.maxProfit(prices3)); // 2
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. THIS IS THE FOUNDATION PROBLEM:
 *    - Must solve this before attempting Stock II, III, IV
 *    - Teaches fundamental concept of tracking state
 *    - Very common in phone screens
 * 
 * 2. KEY INSIGHT:
 *    - To maximize profit = maximize (sell price - buy price)
 *    - This means buy at minimum and sell at maximum AFTER that minimum
 *    - Track minimum price so far and calculate potential profit at each step
 * 
 * 3. WHY GREEDY WORKS HERE:
 *    - We want the global minimum as buy price
 *    - And any future price as sell price
 *    - Single pass is sufficient
 * 
 * 4. CONNECTION TO KADANE'S ALGORITHM:
 *    - Transform to daily profit array
 *    - Find maximum subarray sum
 *    - This insight helps with harder stock problems
 * 
 * 5. STATE MACHINE APPROACH:
 *    - 2 states: own stock, don't own stock
 *    - Transitions: buy (not own → own), sell (own → not own)
 *    - This generalizes to all stock problems!
 * 
 * 6. FOLLOW-UP QUESTIONS:
 *    - Unlimited transactions? → Stock II (Leetcode 122)
 *    - At most 2 transactions? → Stock III (Leetcode 123)
 *    - At most k transactions? → Stock IV (Leetcode 188)
 *    - With cooldown? → Stock with Cooldown (Leetcode 309)
 *    - With transaction fee? → Stock with Fee (Leetcode 714)
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 121: Best Time to Buy and Sell Stock (this)
 *    - Leetcode 122: Best Time to Buy and Sell Stock II
 *    - Leetcode 123: Best Time to Buy and Sell Stock III
 *    - Leetcode 188: Best Time to Buy and Sell Stock IV
 *    - Leetcode 309: Best Time to Buy and Sell Stock with Cooldown
 *    - Leetcode 714: Best Time to Buy and Sell Stock with Transaction Fee
 * 
 * 8. INTERVIEW STRATEGY:
 *    - Start with brute force (show you understand problem)
 *    - Explain the insight about tracking minimum
 *    - Code the one-pass solution
 *    - Explain time and space complexity
 *    - Mention state machine for generalization
 * 
 * 9. COMMON MISTAKES:
 *    - Trying to track both buy and sell indices (not needed!)
 *    - Selling before buying (check order in nested loops)
 *    - Not initializing minPrice properly
 *    - Overthinking the problem
 * 
 * 10. COMPLEXITY ANALYSIS:
 *     - Time: O(n) - Single pass through array
 *     - Space: O(1) - Only using 2 variables
 *     - This is optimal! Can't do better.
 */

