package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.util.*;

/**
 * HOUSE ROBBER - BASIC DECISION DP
 * Difficulty: Easy (Perfect for learning decision-based DP!)
 * Pattern: 1D DP with Decision Making
 * 
 * PROBLEM:
 * You are a professional robber planning to rob houses along a street. Each house has 
 * a certain amount of money stashed. The only constraint is that you cannot rob two 
 * adjacent houses. Given an integer array nums representing the amount of money of each house,
 * return the maximum amount of money you can rob tonight without alerting the police.
 * 
 * EXAMPLES:
 * Input: nums = [1,2,3,1]
 * Output: 4
 * Explanation: Rob house 0 (money = 1) then rob house 2 (money = 3). Total = 1 + 3 = 4.
 * 
 * Input: nums = [2,7,9,3,1]
 * Output: 12
 * Explanation: Rob house 0 (money = 2), house 2 (money = 9) and house 4 (money = 1). Total = 2 + 9 + 1 = 12.
 * 
 * WHY THIS IS PERFECT FOR LEARNING DECISION DP:
 * 1. Clear decision at each step: rob this house or not?
 * 2. Constraint creates dependency between decisions
 * 3. Shows optimal substructure clearly
 * 4. Natural progression from recursion to DP
 * 5. Easy to understand and visualize
 */
public class HouseRobber_BasicDecision {
    
    /**
     * APPROACH 1: Pure Recursion (Inefficient - for understanding)
     * Time: O(2^n) - Exponential!
     * Space: O(n) - Recursion depth
     * 
     * Shows the decision tree structure
     */
    public int rob_Recursion(int[] nums) {
        System.out.println("🔴 Pure Recursion - Robbing houses: " + Arrays.toString(nums));
        return robRecursive(nums, 0, 0);
    }
    
    private int robRecursive(int[] nums, int index, int depth) {
        String indent = "  ".repeat(depth);
        
        // Base case: no more houses
        if (index >= nums.length) {
            System.out.println(indent + "No more houses, returning 0");
            return 0;
        }
        
        System.out.println(indent + "At house " + index + " (value: " + nums[index] + ")");
        
        // Decision 1: Rob this house (skip next house)
        System.out.println(indent + "Option 1: Rob house " + index + " (+" + nums[index] + ")");
        int robCurrent = nums[index] + robRecursive(nums, index + 2, depth + 1);
        
        // Decision 2: Skip this house (can rob next house)
        System.out.println(indent + "Option 2: Skip house " + index);
        int skipCurrent = robRecursive(nums, index + 1, depth + 1);
        
        int maxMoney = Math.max(robCurrent, skipCurrent);
        System.out.println(indent + "Best from house " + index + ": max(" + robCurrent + ", " + skipCurrent + ") = " + maxMoney);
        
        return maxMoney;
    }
    
    /**
     * APPROACH 2: Recursion with Memoization (Top-Down DP)
     * Time: O(n) - Each house calculated once
     * Space: O(n) - Memoization array + recursion stack
     */
    public int rob_Memoization(int[] nums) {
        System.out.println("🟡 Memoization - Optimized recursion");
        System.out.println("Houses: " + Arrays.toString(nums));
        
        Integer[] memo = new Integer[nums.length];
        return robMemo(nums, 0, memo);
    }
    
    private int robMemo(int[] nums, int index, Integer[] memo) {
        // Base case
        if (index >= nums.length) {
            return 0;
        }
        
        // Check memo
        if (memo[index] != null) {
            System.out.println("Found in memo[" + index + "] = " + memo[index]);
            return memo[index];
        }
        
        // Calculate both options
        int robCurrent = nums[index] + robMemo(nums, index + 2, memo);
        int skipCurrent = robMemo(nums, index + 1, memo);
        
        memo[index] = Math.max(robCurrent, skipCurrent);
        System.out.println("Calculated memo[" + index + "] = max(" + robCurrent + ", " + skipCurrent + ") = " + memo[index]);
        
        return memo[index];
    }
    
    /**
     * APPROACH 3: Bottom-Up DP (Classic approach)
     * Time: O(n) - Single pass through array
     * Space: O(n) - DP array
     */
    public int rob_BottomUp(int[] nums) {
        System.out.println("🟢 Bottom-Up DP - Building solution step by step");
        System.out.println("Houses: " + Arrays.toString(nums));
        
        if (nums.length == 0) return 0;
        if (nums.length == 1) return nums[0];
        
        int n = nums.length;
        
        // dp[i] = maximum money that can be robbed from houses 0 to i
        int[] dp = new int[n];
        
        // Base cases
        dp[0] = nums[0];                           // Only one house, rob it
        dp[1] = Math.max(nums[0], nums[1]);        // Two houses, rob the better one
        
        System.out.println("Base cases: dp[0] = " + dp[0] + ", dp[1] = " + dp[1]);
        
        // Fill the DP array
        for (int i = 2; i < n; i++) {
            // Two choices:
            // 1. Rob house i: get nums[i] + dp[i-2] (best we could do up to house i-2)
            // 2. Don't rob house i: get dp[i-1] (best we could do up to house i-1)
            
            int robCurrent = nums[i] + dp[i - 2];
            int skipCurrent = dp[i - 1];
            
            dp[i] = Math.max(robCurrent, skipCurrent);
            
            System.out.printf("dp[%d] = max(rob house %d, skip house %d) = max(%d + %d, %d) = max(%d, %d) = %d\n",
                i, i, i, nums[i], dp[i-2], dp[i-1], robCurrent, skipCurrent, dp[i]);
        }
        
        System.out.println("Final DP array: " + Arrays.toString(dp));
        return dp[n - 1];
    }
    
    /**
     * APPROACH 4: Space-Optimized DP
     * Time: O(n) - Single pass
     * Space: O(1) - Only track last 2 values
     */
    public int rob_SpaceOptimized(int[] nums) {
        System.out.println("🔵 Space Optimized - Only tracking last 2 values");
        System.out.println("Houses: " + Arrays.toString(nums));
        
        if (nums.length == 0) return 0;
        if (nums.length == 1) return nums[0];
        
        // Only need to track previous 2 values
        int prev2 = nums[0];                      // Best we could do 2 houses ago
        int prev1 = Math.max(nums[0], nums[1]);   // Best we could do 1 house ago
        
        System.out.println("Starting: prev2 = " + prev2 + ", prev1 = " + prev1);
        
        for (int i = 2; i < nums.length; i++) {
            int robCurrent = nums[i] + prev2;      // Rob current house
            int skipCurrent = prev1;               // Skip current house
            
            int current = Math.max(robCurrent, skipCurrent);
            
            System.out.printf("House %d: current = max(rob %d + %d, skip %d) = max(%d, %d) = %d\n",
                i, nums[i], prev2, prev1, robCurrent, skipCurrent, current);
            
            // Update for next iteration
            prev2 = prev1;
            prev1 = current;
            
            System.out.println("  Updated: prev2 = " + prev2 + ", prev1 = " + prev1);
        }
        
        return prev1;
    }
    
    /**
     * VISUALIZATION: Show which houses to rob for optimal solution
     */
    public void showOptimalStrategy(int[] nums) {
        System.out.println("\n🎯 OPTIMAL STRATEGY VISUALIZATION");
        
        if (nums.length == 0) return;
        if (nums.length == 1) {
            System.out.println("Only one house: Rob house 0 (value: " + nums[0] + ")");
            return;
        }
        
        int n = nums.length;
        int[] dp = new int[n];
        boolean[] robbed = new boolean[n];
        
        // Build DP array with choice tracking
        dp[0] = nums[0];
        robbed[0] = true;
        
        if (nums[0] > nums[1]) {
            dp[1] = nums[0];
            robbed[1] = false; // Don't rob house 1, keep house 0
        } else {
            dp[1] = nums[1];
            robbed[0] = false; // Don't rob house 0
            robbed[1] = true;  // Rob house 1
        }
        
        for (int i = 2; i < n; i++) {
            int robCurrent = nums[i] + dp[i - 2];
            int skipCurrent = dp[i - 1];
            
            if (robCurrent > skipCurrent) {
                dp[i] = robCurrent;
                robbed[i] = true;
            } else {
                dp[i] = skipCurrent;
                robbed[i] = false;
            }
        }
        
        // Backtrack to find actual houses robbed
        List<Integer> robbedHouses = new ArrayList<>();
        boolean[] finalRobbed = new boolean[n];
        
        int i = n - 1;
        while (i >= 0) {
            if (i == 0 || (i >= 2 && dp[i] == nums[i] + dp[i - 2] && dp[i] > dp[i - 1])) {
                finalRobbed[i] = true;
                robbedHouses.add(i);
                i -= 2; // Skip next house
            } else {
                i--;
            }
        }
        
        Collections.reverse(robbedHouses);
        
        // Display results
        System.out.println("House values: " + Arrays.toString(nums));
        System.out.print("Strategy:     ");
        for (int j = 0; j < n; j++) {
            if (finalRobbed[j]) {
                System.out.print("ROB   ");
            } else {
                System.out.print("SKIP  ");
            }
        }
        System.out.println();
        
        System.out.println("Houses robbed: " + robbedHouses);
        int totalMoney = robbedHouses.stream().mapToInt(house -> nums[house]).sum();
        System.out.println("Total money: " + totalMoney);
        
        // Verify no adjacent houses
        System.out.print("Verification: ");
        boolean valid = true;
        for (int j = 0; j < robbedHouses.size() - 1; j++) {
            if (robbedHouses.get(j + 1) - robbedHouses.get(j) == 1) {
                valid = false;
                break;
            }
        }
        System.out.println(valid ? "✅ No adjacent houses robbed" : "❌ Adjacent houses robbed!");
    }
    
    /**
     * STEP-BY-STEP DECISION TREE for small examples
     */
    public void showDecisionTree(int[] nums) {
        if (nums.length > 4) {
            System.out.println("Decision tree too large for visualization (length > 4)");
            return;
        }
        
        System.out.println("\n🌳 DECISION TREE VISUALIZATION");
        System.out.println("Houses: " + Arrays.toString(nums));
        System.out.println("At each house, we decide: ROB or SKIP");
        System.out.println();
        
        showDecisionNode(nums, 0, "", 0, new ArrayList<>());
    }
    
    private void showDecisionNode(int[] nums, int index, String indent, int currentMoney, List<Integer> robbedHouses) {
        if (index >= nums.length) {
            System.out.println(indent + "END: Total = " + currentMoney + ", Houses: " + robbedHouses);
            return;
        }
        
        System.out.println(indent + "House " + index + " (value: " + nums[index] + ")");
        
        // Option 1: Rob this house
        List<Integer> robbedWithCurrent = new ArrayList<>(robbedHouses);
        robbedWithCurrent.add(index);
        System.out.println(indent + "├─ ROB house " + index);
        showDecisionNode(nums, index + 2, indent + "│  ", currentMoney + nums[index], robbedWithCurrent);
        
        // Option 2: Skip this house
        System.out.println(indent + "└─ SKIP house " + index);
        showDecisionNode(nums, index + 1, indent + "   ", currentMoney, new ArrayList<>(robbedHouses));
    }
    
    /**
     * EDUCATIONAL: Explain the intuition
     */
    public void explainIntuition(int[] nums) {
        System.out.println("\n📚 UNDERSTANDING THE INTUITION");
        System.out.println("Houses: " + Arrays.toString(nums));
        System.out.println();
        
        System.out.println("Key insight: For each house, we have a choice:");
        System.out.println("1. ROB this house → Can't rob previous house");
        System.out.println("2. SKIP this house → Keep whatever we had from previous house");
        System.out.println();
        
        System.out.println("The recurrence relation:");
        System.out.println("dp[i] = max(nums[i] + dp[i-2], dp[i-1])");
        System.out.println("        max(rob current  ,  skip current)");
        System.out.println();
        
        System.out.println("Why this works:");
        System.out.println("- If we rob house i, best we could have done is dp[i-2] (from house i-2)");
        System.out.println("- If we skip house i, best we could have done is dp[i-1] (from house i-1)");
        System.out.println("- We take the maximum of these two options");
    }
    
    /**
     * PERFORMANCE COMPARISON
     */
    public void performanceComparison(int[] nums) {
        System.out.println("\n⚡ PERFORMANCE COMPARISON");
        
        if (nums.length <= 8) {
            long start = System.nanoTime();
            int result1 = rob_Recursion_Silent(nums);
            long time1 = System.nanoTime() - start;
            System.out.println("Pure Recursion: " + result1 + " (Time: " + time1/1000000.0 + " ms)");
        } else {
            System.out.println("Pure Recursion: Skipped (too slow for large arrays)");
        }
        
        long start = System.nanoTime();
        int result2 = rob_Memoization_Silent(nums);
        long time2 = System.nanoTime() - start;
        System.out.println("Memoization:    " + result2 + " (Time: " + time2/1000000.0 + " ms)");
        
        start = System.nanoTime();
        int result3 = rob_SpaceOptimized_Silent(nums);
        long time3 = System.nanoTime() - start;
        System.out.println("Space Optimized: " + result3 + " (Time: " + time3/1000000.0 + " ms)");
    }
    
    // Silent versions for performance testing
    private int rob_Recursion_Silent(int[] nums) {
        return robRecursive_Silent(nums, 0);
    }
    
    private int robRecursive_Silent(int[] nums, int index) {
        if (index >= nums.length) return 0;
        return Math.max(nums[index] + robRecursive_Silent(nums, index + 2),
                       robRecursive_Silent(nums, index + 1));
    }
    
    private int rob_Memoization_Silent(int[] nums) {
        Integer[] memo = new Integer[nums.length];
        return robMemo_Silent(nums, 0, memo);
    }
    
    private int robMemo_Silent(int[] nums, int index, Integer[] memo) {
        if (index >= nums.length) return 0;
        if (memo[index] != null) return memo[index];
        memo[index] = Math.max(nums[index] + robMemo_Silent(nums, index + 2, memo),
                              robMemo_Silent(nums, index + 1, memo));
        return memo[index];
    }
    
    private int rob_SpaceOptimized_Silent(int[] nums) {
        if (nums.length == 0) return 0;
        if (nums.length == 1) return nums[0];
        
        int prev2 = nums[0];
        int prev1 = Math.max(nums[0], nums[1]);
        
        for (int i = 2; i < nums.length; i++) {
            int current = Math.max(nums[i] + prev2, prev1);
            prev2 = prev1;
            prev1 = current;
        }
        
        return prev1;
    }
    
    /**
     * COMPREHENSIVE DEMONSTRATION
     */
    public static void main(String[] args) {
        HouseRobber_BasicDecision solution = new HouseRobber_BasicDecision();
        
        System.out.println("🎓 HOUSE ROBBER - COMPLETE DECISION DP TUTORIAL");
        System.out.println("=" .repeat(85));
        
        // Example 1: Simple case
        int[] nums1 = {1, 2, 3, 1};
        System.out.println("\n📝 EXAMPLE 1: " + Arrays.toString(nums1));
        
        solution.explainIntuition(nums1);
        
        System.out.println("SOLUTIONS:");
        System.out.println("Pure Recursion: " + solution.rob_Recursion(nums1.clone()));
        System.out.println();
        
        System.out.println("Memoization: " + solution.rob_Memoization(nums1.clone()));
        System.out.println();
        
        System.out.println("Bottom-Up DP: " + solution.rob_BottomUp(nums1.clone()));
        System.out.println();
        
        System.out.println("Space Optimized: " + solution.rob_SpaceOptimized(nums1.clone()));
        
        solution.showOptimalStrategy(nums1);
        solution.showDecisionTree(nums1);
        
        // Example 2: More complex case
        System.out.println("\n" + "=" .repeat(85));
        int[] nums2 = {2, 7, 9, 3, 1};
        System.out.println("\n📝 EXAMPLE 2: " + Arrays.toString(nums2));
        
        System.out.println("Bottom-Up DP: " + solution.rob_BottomUp(nums2));
        solution.showOptimalStrategy(nums2);
        
        // Performance comparison
        solution.performanceComparison(nums2);
        
        System.out.println("\n🎯 KEY LEARNING POINTS:");
        System.out.println("1. Decision DP: at each step, make optimal choice between options");
        System.out.println("2. Constraint creates dependency: can't rob adjacent houses");
        System.out.println("3. State: maximum money robbed up to house i");
        System.out.println("4. Transition: dp[i] = max(rob current, skip current)");
        System.out.println("5. Space optimization possible when only need previous values");
        
        System.out.println("\n✅ Outstanding! You've mastered decision-based Dynamic Programming!");
    }
}