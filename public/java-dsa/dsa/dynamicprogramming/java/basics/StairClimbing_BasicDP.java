package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.util.*;

/**
 * STAIR CLIMBING - BASIC DP FOUNDATION
 * Difficulty: Easy (Perfect for beginners!)
 * Pattern: Basic 1D Dynamic Programming
 * 
 * PROBLEM:
 * You are climbing a staircase. It takes n steps to reach the top.
 * Each time you can either climb 1 or 2 steps. In how many distinct ways can you climb to the top?
 * 
 * EXAMPLES:
 * Input: n = 2
 * Output: 2
 * Explanation: There are two ways: (1+1) and (2)
 * 
 * Input: n = 3  
 * Output: 3
 * Explanation: There are three ways: (1+1+1), (1+2), and (2+1)
 * 
 * WHY THIS IS PERFECT FOR LEARNING DP:
 * 1. Simple problem statement - easy to understand
 * 2. Clear recursive structure - builds on smaller subproblems
 * 3. Obvious overlapping subproblems - shows need for memoization
 * 4. Natural progression from recursion → memoization → bottom-up DP
 */
public class StairClimbing_BasicDP {
    
    /**
     * APPROACH 1: Pure Recursion (Inefficient - for understanding only)
     * Time: O(2^n) - Exponential! Very slow for large n
     * Space: O(n) - Recursion call stack
     * 
     * This shows WHY we need DP - without it, we recalculate same values many times!
     */
    public int climbStairs_PureRecursion(int n) {
        System.out.println("🔴 Pure Recursion - Computing climbStairs(" + n + ")");
        
        // Base cases - these are the simplest cases we can solve directly
        if (n <= 2) {
            System.out.println("  Base case: n=" + n + ", returning " + n);
            return n;
        }
        
        // Recursive case - break problem into smaller subproblems
        System.out.println("  Breaking down: climbStairs(" + n + ") = climbStairs(" + (n-1) + ") + climbStairs(" + (n-2) + ")");
        
        int result = climbStairs_PureRecursion(n - 1) + climbStairs_PureRecursion(n - 2);
        System.out.println("  Computed climbStairs(" + n + ") = " + result);
        
        return result;
    }
    
    /**
     * APPROACH 2: Recursion with Memoization (Top-Down DP)
     * Time: O(n) - Each subproblem calculated only once
     * Space: O(n) - Memoization array + recursion stack
     * 
     * This is our first step into DP - we store results to avoid recalculation!
     */
    public int climbStairs_Memoization(int n) {
        System.out.println("🟡 Memoization - Starting with memo array");
        
        // Create memoization array - memo[i] will store result for climbStairs(i)
        int[] memo = new int[n + 1];
        Arrays.fill(memo, -1); // -1 means "not calculated yet"
        
        return climbStairsMemo(n, memo, 0);
    }
    
    private int climbStairsMemo(int n, int[] memo, int depth) {
        String indent = "  ".repeat(depth);
        System.out.println(indent + "Computing climbStairs(" + n + ")");
        
        // Base cases
        if (n <= 2) {
            System.out.println(indent + "Base case: n=" + n + ", returning " + n);
            return n;
        }
        
        // Check if already calculated (memoization check)
        if (memo[n] != -1) {
            System.out.println(indent + "Found in memo: climbStairs(" + n + ") = " + memo[n]);
            return memo[n];
        }
        
        // Calculate and store result
        System.out.println(indent + "Not in memo, calculating...");
        memo[n] = climbStairsMemo(n - 1, memo, depth + 1) + climbStairsMemo(n - 2, memo, depth + 1);
        
        System.out.println(indent + "Stored in memo: climbStairs(" + n + ") = " + memo[n]);
        return memo[n];
    }
    
    /**
     * APPROACH 3: Bottom-Up DP (Iterative)
     * Time: O(n) - Single loop through all values
     * Space: O(n) - DP array
     * 
     * This is classic DP - build solution from smallest subproblems up to final answer!
     */
    public int climbStairs_BottomUp(int n) {
        System.out.println("🟢 Bottom-Up DP - Building solution step by step");
        
        if (n <= 2) return n;
        
        // Create DP array - dp[i] represents "ways to reach step i"
        int[] dp = new int[n + 1];
        
        // Initialize base cases
        dp[1] = 1; // 1 way to reach step 1: (1)
        dp[2] = 2; // 2 ways to reach step 2: (1+1) or (2)
        
        System.out.println("Initialized: dp[1] = " + dp[1] + ", dp[2] = " + dp[2]);
        
        // Fill the DP array bottom-up
        for (int i = 3; i <= n; i++) {
            // Key insight: To reach step i, we can come from step (i-1) or step (i-2)
            dp[i] = dp[i - 1] + dp[i - 2];
            System.out.println("dp[" + i + "] = dp[" + (i-1) + "] + dp[" + (i-2) + "] = " + dp[i-1] + " + " + dp[i-2] + " = " + dp[i]);
        }
        
        System.out.println("Final DP array: " + Arrays.toString(dp));
        return dp[n];
    }
    
    /**
     * APPROACH 4: Space-Optimized DP
     * Time: O(n) - Same as bottom-up
     * Space: O(1) - Only store last 2 values
     * 
     * The ultimate optimization - we only need the last 2 values!
     */
    public int climbStairs_SpaceOptimized(int n) {
        System.out.println("🔵 Space Optimized - Only tracking last 2 values");
        
        if (n <= 2) return n;
        
        // Instead of array, just track previous 2 values
        int prev2 = 1; // Ways to reach step (i-2)
        int prev1 = 2; // Ways to reach step (i-1)
        
        System.out.println("Starting: prev2 = " + prev2 + " (step 1), prev1 = " + prev1 + " (step 2)");
        
        for (int i = 3; i <= n; i++) {
            int current = prev1 + prev2; // Ways to reach step i
            System.out.println("Step " + i + ": current = prev1 + prev2 = " + prev1 + " + " + prev2 + " = " + current);
            
            // Update for next iteration
            prev2 = prev1;
            prev1 = current;
            System.out.println("  Updated: prev2 = " + prev2 + ", prev1 = " + prev1);
        }
        
        return prev1;
    }
    
    /**
     * EDUCATIONAL METHOD: Shows the relationship to Fibonacci
     * This helps students understand the mathematical pattern
     */
    public void showFibonacciConnection(int n) {
        System.out.println("\n📚 EDUCATIONAL: Connection to Fibonacci Numbers");
        System.out.println("Fibonacci sequence: 1, 1, 2, 3, 5, 8, 13, 21, ...");
        System.out.println("Stair climbing:    1, 2, 3, 5, 8, 13, 21, 34, ...");
        System.out.println("Notice: climbStairs(n) = fibonacci(n+1)");
        
        System.out.println("\nWhy? Because both follow the same recurrence relation:");
        System.out.println("F(n) = F(n-1) + F(n-2)  [Fibonacci]");
        System.out.println("C(n) = C(n-1) + C(n-2)  [Climbing Stairs]");
        
        System.out.println("\nComparison for first " + Math.min(n, 8) + " values:");
        for (int i = 1; i <= Math.min(n, 8); i++) {
            int stairs = climbStairs_SpaceOptimized_Silent(i);
            int fib = fibonacci(i + 1);
            System.out.println("n=" + i + ": stairs=" + stairs + ", fib(" + (i+1) + ")=" + fib);
        }
    }
    
    // Helper method for silent calculation (no prints)
    private int climbStairs_SpaceOptimized_Silent(int n) {
        if (n <= 2) return n;
        int prev2 = 1, prev1 = 2;
        for (int i = 3; i <= n; i++) {
            int current = prev1 + prev2;
            prev2 = prev1;
            prev1 = current;
        }
        return prev1;
    }
    
    // Helper method for fibonacci calculation
    private int fibonacci(int n) {
        if (n <= 2) return 1;
        int prev2 = 1, prev1 = 1;
        for (int i = 3; i <= n; i++) {
            int current = prev1 + prev2;
            prev2 = prev1;
            prev1 = current;
        }
        return prev1;
    }
    
    /**
     * VISUALIZATION: Shows all possible ways to climb stairs
     * This helps beginners understand what the number represents
     */
    public void showAllWaysToClimb(int n) {
        if (n > 6) {
            System.out.println("Skipping visualization for n > 6 (too many combinations)");
            return;
        }
        
        System.out.println("\n🎯 VISUALIZATION: All ways to climb " + n + " stairs");
        List<List<Integer>> allWays = new ArrayList<>();
        findAllWays(n, new ArrayList<>(), allWays);
        
        System.out.println("Total ways: " + allWays.size());
        for (int i = 0; i < allWays.size(); i++) {
            List<Integer> way = allWays.get(i);
            System.out.print("Way " + (i + 1) + ": ");
            for (int j = 0; j < way.size(); j++) {
                System.out.print(way.get(j));
                if (j < way.size() - 1) System.out.print(" + ");
            }
            System.out.println(" = " + way.stream().mapToInt(Integer::intValue).sum());
        }
    }
    
    // Helper method to find all ways using backtracking
    private void findAllWays(int remaining, List<Integer> current, List<List<Integer>> allWays) {
        if (remaining == 0) {
            allWays.add(new ArrayList<>(current));
            return;
        }
        
        // Try taking 1 step
        if (remaining >= 1) {
            current.add(1);
            findAllWays(remaining - 1, current, allWays);
            current.remove(current.size() - 1);
        }
        
        // Try taking 2 steps
        if (remaining >= 2) {
            current.add(2);
            findAllWays(remaining - 2, current, allWays);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * PERFORMANCE COMPARISON: Shows why DP is necessary
     */
    public void performanceComparison(int n) {
        System.out.println("\n⚡ PERFORMANCE COMPARISON for n = " + n);
        
        if (n <= 10) {
            // Pure recursion (only for small n due to exponential time)
            long start = System.nanoTime();
            int result1 = climbStairs_PureRecursion_Silent(n);
            long time1 = System.nanoTime() - start;
            System.out.println("Pure Recursion: " + result1 + " (Time: " + time1/1000000.0 + " ms)");
        } else {
            System.out.println("Pure Recursion: Skipped (too slow for n > 10)");
        }
        
        // Memoization
        long start = System.nanoTime();
        int result2 = climbStairs_Memoization_Silent(n);
        long time2 = System.nanoTime() - start;
        System.out.println("Memoization:    " + result2 + " (Time: " + time2/1000000.0 + " ms)");
        
        // Bottom-up DP
        start = System.nanoTime();
        int result3 = climbStairs_SpaceOptimized_Silent(n);
        long time3 = System.nanoTime() - start;
        System.out.println("Space Optimized: " + result3 + " (Time: " + time3/1000000.0 + " ms)");
    }
    
    // Silent versions for performance testing
    private int climbStairs_PureRecursion_Silent(int n) {
        if (n <= 2) return n;
        return climbStairs_PureRecursion_Silent(n - 1) + climbStairs_PureRecursion_Silent(n - 2);
    }
    
    private int climbStairs_Memoization_Silent(int n) {
        int[] memo = new int[n + 1];
        Arrays.fill(memo, -1);
        return climbStairsMemo_Silent(n, memo);
    }
    
    private int climbStairsMemo_Silent(int n, int[] memo) {
        if (n <= 2) return n;
        if (memo[n] != -1) return memo[n];
        memo[n] = climbStairsMemo_Silent(n - 1, memo) + climbStairsMemo_Silent(n - 2, memo);
        return memo[n];
    }
    
    /**
     * COMPREHENSIVE DEMONSTRATION
     */
    public static void main(String[] args) {
        StairClimbing_BasicDP solution = new StairClimbing_BasicDP();
        
        System.out.println("🎓 STAIR CLIMBING - COMPLETE DP TUTORIAL");
        System.out.println("=" .repeat(60));
        
        int n = 5; // Perfect size for learning
        
        // Show all approaches for small n
        System.out.println("\n1️⃣ PURE RECURSION (Shows the problem):");
        System.out.println("Result: " + solution.climbStairs_PureRecursion(n));
        
        System.out.println("\n2️⃣ MEMOIZATION (First DP step):");
        System.out.println("Result: " + solution.climbStairs_Memoization(n));
        
        System.out.println("\n3️⃣ BOTTOM-UP DP (Classic approach):");
        System.out.println("Result: " + solution.climbStairs_BottomUp(n));
        
        System.out.println("\n4️⃣ SPACE OPTIMIZED (Final optimization):");
        System.out.println("Result: " + solution.climbStairs_SpaceOptimized(n));
        
        // Educational content
        solution.showFibonacciConnection(n);
        solution.showAllWaysToClimb(n);
        solution.performanceComparison(n);
        
        System.out.println("\n🎯 KEY TAKEAWAYS:");
        System.out.println("1. Pure recursion has overlapping subproblems (inefficient)");
        System.out.println("2. Memoization eliminates redundant calculations");
        System.out.println("3. Bottom-up DP builds solution iteratively");
        System.out.println("4. Space optimization reduces memory usage");
        System.out.println("5. Pattern recognition helps solve similar problems");
        
        System.out.println("\n✅ You've learned the foundation of Dynamic Programming!");
    }
}