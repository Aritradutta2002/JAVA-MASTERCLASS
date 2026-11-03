package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.util.*;

/**
 * MIN COST CLIMBING STAIRS - Leetcode 746
 * Difficulty: Easy (Perfect for DP beginners!)
 * Pattern: Basic 1D DP with cost optimization
 * 
 * PROBLEM:
 * You are given an integer array cost where cost[i] is the cost of ith step on a staircase.
 * Once you pay the cost, you can either climb one or two steps.
 * You can either start from the step with index 0, or the step with index 1.
 * Return the minimum cost to reach the top of the floor.
 * 
 * EXAMPLES:
 * Input: cost = [10,15,20]
 * Output: 15
 * Explanation: You will start at index 1.
 * - Pay 15 and climb two steps to reach the top.
 * Total cost is 15.
 * 
 * Input: cost = [1,100,1,1,1,100,1,1,100,1]
 * Output: 6
 * Explanation: You will start at index 0.
 * - Pay 1 and climb two steps to reach index 2.
 * - Pay 1 and climb two steps to reach index 4.
 * - Pay 1 and climb two steps to reach index 6.
 * - Pay 1 and climb one step to reach index 7.
 * - Pay 1 and climb two steps to reach index 9.
 * - Pay 1 and climb one step to reach the top.
 * Total cost is 6.
 * 
 * WHY THIS IS PERFECT FOR LEARNING DP:
 * 1. Builds on basic stair climbing concept
 * 2. Introduces optimization aspect (minimum cost)
 * 3. Shows how to handle different starting conditions
 * 4. Demonstrates state transition with costs
 */
public class MinCostClimbingStairs_Leetcode746 {
    
    /**
     * APPROACH 1: Pure Recursion (Inefficient - for understanding)
     * Time: O(2^n) - Exponential, very slow
     * Space: O(n) - Recursion call stack
     * 
     * Shows the problem structure before optimization
     */
    public int minCostClimbingStairs_Recursion(int[] cost) {
        System.out.println("🔴 Pure Recursion - Finding minimum cost");
        System.out.println("Cost array: " + Arrays.toString(cost));
        
        int n = cost.length;
        // We can start from index 0 or 1, so take minimum of both
        int startFrom0 = solve(cost, 0);
        int startFrom1 = solve(cost, 1);
        
        System.out.println("Starting from index 0: " + startFrom0);
        System.out.println("Starting from index 1: " + startFrom1);
        
        return Math.min(startFrom0, startFrom1);
    }
    
    private int solve(int[] cost, int i) {
        // Base case: if we reach or exceed the top, no more cost
        if (i >= cost.length) {
            System.out.println("  Reached top from index " + i + ", cost = 0");
            return 0;
        }
        
        System.out.println("  At index " + i + ", cost = " + cost[i]);
        
        // We pay current cost and can go 1 or 2 steps
        int oneStep = cost[i] + solve(cost, i + 1);
        int twoSteps = cost[i] + solve(cost, i + 2);
        
        int minCost = Math.min(oneStep, twoSteps);
        System.out.println("  From index " + i + ": min(" + oneStep + ", " + twoSteps + ") = " + minCost);
        
        return minCost;
    }
    
    /**
     * APPROACH 2: Recursion with Memoization (Top-Down DP)
     * Time: O(n) - Each index calculated once
     * Space: O(n) - Memoization array + recursion stack
     */
    public int minCostClimbingStairs_Memoization(int[] cost) {
        System.out.println("🟡 Memoization - Optimized recursion");
        System.out.println("Cost array: " + Arrays.toString(cost));
        
        int n = cost.length;
        Integer[] memo = new Integer[n];
        
        // Can start from index 0 or 1
        int startFrom0 = solveMemo(cost, 0, memo);
        int startFrom1 = solveMemo(cost, 1, memo);
        
        System.out.println("Memo array: " + Arrays.toString(memo));
        System.out.println("Starting from index 0: " + startFrom0);
        System.out.println("Starting from index 1: " + startFrom1);
        
        return Math.min(startFrom0, startFrom1);
    }
    
    private int solveMemo(int[] cost, int i, Integer[] memo) {
        // Base case
        if (i >= cost.length) {
            return 0;
        }
        
        // Check memo
        if (memo[i] != null) {
            System.out.println("  Found in memo: index " + i + " = " + memo[i]);
            return memo[i];
        }
        
        // Calculate and store
        int oneStep = cost[i] + solveMemo(cost, i + 1, memo);
        int twoSteps = cost[i] + solveMemo(cost, i + 2, memo);
        
        memo[i] = Math.min(oneStep, twoSteps);
        System.out.println("  Calculated memo[" + i + "] = " + memo[i]);
        
        return memo[i];
    }
    
    /**
     * APPROACH 3: Bottom-Up DP (Classic approach)
     * Time: O(n) - Single pass through array
     * Space: O(n) - DP array
     * 
     * Build solution from smallest subproblems up
     */
    public int minCostClimbingStairs_BottomUp(int[] cost) {
        System.out.println("🟢 Bottom-Up DP - Building solution iteratively");
        System.out.println("Cost array: " + Arrays.toString(cost));
        
        int n = cost.length;
        
        // dp[i] = minimum cost to reach step i
        int[] dp = new int[n + 1];
        
        // Base cases: we can start from step 0 or 1 with their respective costs
        dp[0] = cost[0];  // Cost to "be at" step 0
        dp[1] = cost[1];  // Cost to "be at" step 1
        
        System.out.println("Base cases: dp[0] = " + dp[0] + ", dp[1] = " + dp[1]);
        
        // Fill the DP array
        for (int i = 2; i < n; i++) {
            // To reach step i, we can come from step (i-1) or (i-2)
            // We take the cheaper option and add current step's cost
            dp[i] = cost[i] + Math.min(dp[i - 1], dp[i - 2]);
            System.out.println("dp[" + i + "] = cost[" + i + "] + min(dp[" + (i-1) + "], dp[" + (i-2) + "]) = " + 
                             cost[i] + " + min(" + dp[i-1] + ", " + dp[i-2] + ") = " + dp[i]);
        }
        
        // To reach the top (beyond last step), we can come from last or second-last step
        dp[n] = Math.min(dp[n - 1], dp[n - 2]);
        System.out.println("dp[" + n + "] (top) = min(dp[" + (n-1) + "], dp[" + (n-2) + "]) = min(" + 
                         dp[n-1] + ", " + dp[n-2] + ") = " + dp[n]);
        
        System.out.println("Final DP array: " + Arrays.toString(dp));
        return dp[n];
    }
    
    /**
     * APPROACH 4: Space-Optimized DP
     * Time: O(n) - Single pass
     * Space: O(1) - Only store last 2 values
     */
    public int minCostClimbingStairs_SpaceOptimized(int[] cost) {
        System.out.println("🔵 Space Optimized - Only tracking last 2 values");
        System.out.println("Cost array: " + Arrays.toString(cost));
        
        int n = cost.length;
        
        // Only need to track previous 2 values
        int prev2 = cost[0];  // Minimum cost to reach step (i-2)
        int prev1 = cost[1];  // Minimum cost to reach step (i-1)
        
        System.out.println("Starting: prev2 = " + prev2 + ", prev1 = " + prev1);
        
        for (int i = 2; i < n; i++) {
            int current = cost[i] + Math.min(prev1, prev2);
            System.out.println("Step " + i + ": current = " + cost[i] + " + min(" + prev1 + ", " + prev2 + ") = " + current);
            
            // Update for next iteration
            prev2 = prev1;
            prev1 = current;
            System.out.println("  Updated: prev2 = " + prev2 + ", prev1 = " + prev1);
        }
        
        // Final answer: minimum cost to reach beyond the array
        int result = Math.min(prev1, prev2);
        System.out.println("Final result: min(" + prev1 + ", " + prev2 + ") = " + result);
        
        return result;
    }
    
    /**
     * APPROACH 5: Alternative Bottom-Up (Different perspective)
     * Think of dp[i] as "minimum cost to reach the top starting from step i"
     */
    public int minCostClimbingStairs_Alternative(int[] cost) {
        System.out.println("🟣 Alternative DP - 'Cost from step i to top'");
        System.out.println("Cost array: " + Arrays.toString(cost));
        
        int n = cost.length;
        int[] dp = new int[n + 2]; // Extra space for bounds
        
        // Base cases: cost to reach top from beyond array is 0
        dp[n] = 0;
        dp[n + 1] = 0;
        
        // Work backwards
        for (int i = n - 1; i >= 0; i--) {
            dp[i] = cost[i] + Math.min(dp[i + 1], dp[i + 2]);
            System.out.println("dp[" + i + "] = cost[" + i + "] + min(dp[" + (i+1) + "], dp[" + (i+2) + "]) = " + 
                             cost[i] + " + min(" + dp[i+1] + ", " + dp[i+2] + ") = " + dp[i]);
        }
        
        System.out.println("DP array: " + Arrays.toString(Arrays.copyOf(dp, n + 1)));
        
        // Answer: minimum of starting from step 0 or step 1
        int result = Math.min(dp[0], dp[1]);
        System.out.println("Result: min(dp[0], dp[1]) = min(" + dp[0] + ", " + dp[1] + ") = " + result);
        
        return result;
    }
    
    /**
     * VISUALIZATION: Show the optimal path
     */
    public void showOptimalPath(int[] cost) {
        System.out.println("\n🎯 OPTIMAL PATH VISUALIZATION");
        
        int n = cost.length;
        int[] dp = new int[n + 1];
        int[] parent = new int[n + 1]; // Track where we came from
        
        // Build DP with parent tracking
        dp[0] = cost[0];
        dp[1] = cost[1];
        parent[0] = -1; // Starting point
        parent[1] = -1; // Starting point
        
        for (int i = 2; i < n; i++) {
            if (dp[i - 1] < dp[i - 2]) {
                dp[i] = cost[i] + dp[i - 1];
                parent[i] = i - 1;
            } else {
                dp[i] = cost[i] + dp[i - 2];
                parent[i] = i - 2;
            }
        }
        
        // Determine final step
        if (dp[n - 1] < dp[n - 2]) {
            dp[n] = dp[n - 1];
            parent[n] = n - 1;
        } else {
            dp[n] = dp[n - 2];
            parent[n] = n - 2;
        }
        
        // Reconstruct path
        List<Integer> path = new ArrayList<>();
        int current = n;
        while (current != -1 && parent[current] != -1) {
            path.add(parent[current]);
            current = parent[current];
        }
        Collections.reverse(path);
        
        System.out.println("Optimal path: " + path);
        System.out.println("Path costs: ");
        int totalCost = 0;
        for (int i = 0; i < path.size(); i++) {
            int step = path.get(i);
            System.out.print("Step " + step + " (cost " + cost[step] + ")");
            totalCost += cost[step];
            if (i < path.size() - 1) {
                int nextStep = path.get(i + 1);
                int jump = nextStep - step;
                System.out.print(" → +" + jump + " → ");
            }
        }
        System.out.println(" → TOP");
        System.out.println("Total cost: " + totalCost);
    }
    
    /**
     * STEP-BY-STEP WALKTHROUGH for beginners
     */
    public void stepByStepWalkthrough(int[] cost) {
        System.out.println("\n📚 STEP-BY-STEP WALKTHROUGH");
        System.out.println("Understanding the problem:");
        System.out.println("1. We have stairs with costs: " + Arrays.toString(cost));
        System.out.println("2. We can start at step 0 or step 1");
        System.out.println("3. From any step, we can go +1 or +2 steps");
        System.out.println("4. Goal: reach beyond the array with minimum cost");
        System.out.println();
        
        // Show what each position means
        System.out.println("Position meanings:");
        for (int i = 0; i < cost.length; i++) {
            System.out.println("  Position " + i + ": costs " + cost[i] + " to step on");
        }
        System.out.println("  Position " + cost.length + ": TOP (our goal)");
        System.out.println();
        
        // Show possible moves from each position
        System.out.println("Possible moves from each position:");
        for (int i = 0; i < cost.length; i++) {
            System.out.print("  From position " + i + ": can go to ");
            List<String> moves = new ArrayList<>();
            if (i + 1 < cost.length) moves.add("position " + (i + 1));
            if (i + 2 < cost.length) moves.add("position " + (i + 2));
            if (i + 1 >= cost.length) moves.add("TOP");
            if (i + 2 >= cost.length && !moves.contains("TOP")) moves.add("TOP");
            System.out.println(String.join(" or ", moves));
        }
    }
    
    /**
     * COMPREHENSIVE DEMONSTRATION
     */
    public static void main(String[] args) {
        MinCostClimbingStairs_Leetcode746 solution = new MinCostClimbingStairs_Leetcode746();
        
        System.out.println("🎓 MIN COST CLIMBING STAIRS - COMPLETE TUTORIAL");
        System.out.println("=" .repeat(70));
        
        // Example 1: Simple case
        int[] cost1 = {10, 15, 20};
        System.out.println("\n📝 EXAMPLE 1: " + Arrays.toString(cost1));
        
        solution.stepByStepWalkthrough(cost1);
        
        System.out.println("SOLUTIONS:");
        System.out.println("Pure Recursion: " + solution.minCostClimbingStairs_Recursion(cost1.clone()));
        System.out.println();
        
        System.out.println("Memoization: " + solution.minCostClimbingStairs_Memoization(cost1.clone()));
        System.out.println();
        
        System.out.println("Bottom-Up DP: " + solution.minCostClimbingStairs_BottomUp(cost1.clone()));
        System.out.println();
        
        System.out.println("Space Optimized: " + solution.minCostClimbingStairs_SpaceOptimized(cost1.clone()));
        System.out.println();
        
        System.out.println("Alternative DP: " + solution.minCostClimbingStairs_Alternative(cost1.clone()));
        
        solution.showOptimalPath(cost1);
        
        // Example 2: More complex case
        System.out.println("\n" + "=" .repeat(70));
        int[] cost2 = {1, 100, 1, 1, 1, 100, 1, 1, 100, 1};
        System.out.println("\n📝 EXAMPLE 2: " + Arrays.toString(cost2));
        
        System.out.println("Bottom-Up DP: " + solution.minCostClimbingStairs_BottomUp(cost2));
        solution.showOptimalPath(cost2);
        
        System.out.println("\n🎯 KEY LEARNING POINTS:");
        System.out.println("1. This extends basic stair climbing with cost optimization");
        System.out.println("2. We need to track minimum cost, not just number of ways");
        System.out.println("3. Multiple starting points require comparing different options");
        System.out.println("4. State transition: dp[i] = cost[i] + min(dp[i-1], dp[i-2])");
        System.out.println("5. Space optimization works because we only need last 2 values");
        
        System.out.println("\n✅ Great job! You understand cost-based DP now!");
    }
}