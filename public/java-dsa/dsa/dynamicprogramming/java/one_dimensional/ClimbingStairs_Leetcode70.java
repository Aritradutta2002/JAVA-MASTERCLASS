package JAVA_DSA.DYNAMIC_PROGRAMMING.java.one_dimensional;

// Fixed package name to match folder structure

/**
 * CLIMBING STAIRS - Leetcode 70
 * Difficulty: Easy
 * Companies: Amazon, Microsoft, Google, Adobe, Apple
 * 
 * PROBLEM:
 * You are climbing a staircase with n steps. Each time you can climb 1 or 2 steps.
 * In how many distinct ways can you reach the top?
 * 
 * EXAMPLES:
 * Input: n = 2
 * Output: 2
 * Explanation: 1+1, 2
 * 
 * Input: n = 3
 * Output: 3
 * Explanation: 1+1+1, 1+2, 2+1
 * 
 * PATTERN: Fibonacci Pattern
 * This is a classic DP problem that follows Fibonacci sequence!
 */

public class ClimbingStairs_Leetcode70 {
    
    // ========== APPROACH 1: RECURSION (TLE - Time Limit Exceeded) ==========
    // Time: O(2^n) - Each call branches into 2 more calls
    // Space: O(n) - Recursion stack depth
    /**
     * BEGINNER'S EXPLANATION:
     * Think of it like this: To reach step n, you could have come from:
     * - Step (n-1) by taking 1 step, OR
     * - Step (n-2) by taking 2 steps
     * So: ways(n) = ways(n-1) + ways(n-2)
     * This is exactly the Fibonacci sequence!
     */
    public int climbStairs_Recursive(int n) {
        // Base cases
        if (n <= 2) return n;
        
        // Recursive case: sum of previous two
        return climbStairs_Recursive(n - 1) + climbStairs_Recursive(n - 2);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(n) - Each state calculated once
    // Space: O(n) - DP array + recursion stack
    /**
     * BEGINNER'S EXPLANATION:
     * Same as recursion but we save results to avoid recalculating.
     * Think of it as writing down answers so you don't solve same problem twice!
     */
    public int climbStairs_Memoization(int n) {
        int[] memo = new int[n + 1];
        return climbStairsMemo(n, memo);
    }
    
    private int climbStairsMemo(int n, int[] memo) {
        // Base cases
        if (n <= 2) return n;
        
        // If already calculated, return saved result
        if (memo[n] != 0) return memo[n];
        
        // Calculate and save
        memo[n] = climbStairsMemo(n - 1, memo) + climbStairsMemo(n - 2, memo);
        return memo[n];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(n) - Single loop through n
    // Space: O(n) - DP array
    /**
     * BEGINNER'S EXPLANATION:
     * Instead of top-down, we build from bottom-up.
     * Start from step 0, calculate step 1, then 2, then 3... until n
     * Like filling a table from left to right!
     * 
     * DP Table Visualization for n=5:
     * Step:  0  1  2  3  4  5
     * Ways:  1  1  2  3  5  8
     *           ↑  ↑
     *           └──┴─ Sum to get next
     */
    public int climbStairs_Tabulation(int n) {
        if (n <= 2) return n;
        
        // Create DP table
        int[] dp = new int[n + 1];
        
        // Base cases
        dp[0] = 1; // 1 way to stay at ground (do nothing)
        dp[1] = 1; // 1 way to reach step 1
        
        // Fill table using recurrence relation
        for (int i = 2; i <= n; i++) {
            dp[i] = dp[i - 1] + dp[i - 2];
        }
        
        return dp[n];
    }
    
    
    // ========== APPROACH 4: SPACE OPTIMIZED ⭐ BEST FOR INTERVIEWS ==========
    // Time: O(n) - Single loop
    // Space: O(1) - Only 2 variables!
    /**
     * BEGINNER'S EXPLANATION:
     * Notice we only need previous 2 values, not entire array!
     * Like remembering only last 2 Fibonacci numbers.
     * 
     * Think of it as a sliding window of size 2:
     * [prev2, prev1] → [prev1, current]
     */
    public int climbStairs(int n) {
        if (n <= 2) return n;
        
        // Only need previous 2 values
        int prev2 = 1; // Ways to reach step i-2
        int prev1 = 2; // Ways to reach step i-1
        
        // Calculate from step 3 to n
        for (int i = 3; i <= n; i++) {
            int current = prev1 + prev2;
            // Slide the window
            prev2 = prev1;
            prev1 = current;
        }
        
        return prev1;
    }
    
    
    // ========== BONUS: FIBONACCI FORMULA (Math Approach) ==========
    // Time: O(1) - Just formula
    // Space: O(1)
    /**
     * Using Binet's formula for nth Fibonacci number
     * Not recommended for interviews (precision issues with large n)
     * But cool to know! 🎯
     */
    public int climbStairs_Formula(int n) {
        double phi = (1 + Math.sqrt(5)) / 2; // Golden ratio
        double psi = (1 - Math.sqrt(5)) / 2;
        return (int) ((Math.pow(phi, n + 1) - Math.pow(psi, n + 1)) / Math.sqrt(5));
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        ClimbingStairs_Leetcode70 solution = new ClimbingStairs_Leetcode70();
        
        // Test case 1
        System.out.println("n = 2: " + solution.climbStairs(2)); // Output: 2
        
        // Test case 2
        System.out.println("n = 3: " + solution.climbStairs(3)); // Output: 3
        
        // Test case 3
        System.out.println("n = 5: " + solution.climbStairs(5)); // Output: 8
        
        // Test case 4
        System.out.println("n = 10: " + solution.climbStairs(10)); // Output: 89
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. PATTERN RECOGNITION:
 *    - When you can take k steps, check if it's Fibonacci-like
 *    - Formula: dp[i] = dp[i-1] + dp[i-2] + ... + dp[i-k]
 * 
 * 2. APPROACH TO PRESENT:
 *    - Start: Mention recursive solution (brute force)
 *    - Optimize: Add memoization
 *    - Better: Show tabulation
 *    - Best: Space optimize to O(1)
 * 
 * 3. FOLLOW-UP QUESTIONS:
 *    - What if you can take 1, 2, or 3 steps? → Tribonacci
 *    - What if steps have costs? → Similar to min cost climbing stairs
 *    - What if you can skip k steps? → Generalized Fibonacci
 * 
 * 4. INTERVIEW TIPS:
 *    - Always explain the recurrence relation clearly
 *    - Draw a small example (n=5) to visualize
 *    - Mention space optimization shows strong DP skills
 * 
 * 5. RELATED PROBLEMS:
 *    - Leetcode 746: Min Cost Climbing Stairs
 *    - Leetcode 1137: N-th Tribonacci Number
 *    - Leetcode 509: Fibonacci Number
 */

