package dsa.algorithms.backtracking.gametheory;
/*
 Problem: Predict the Winner (Leetcode 486)
 Category: Game Theory + Backtracking (Minimax) with Memoization (Score-Difference DP)
 
 Approach (Score-Difference DP):
   - Convert the two-player optimal-play game into a single function that returns the maximum
     score difference the current player can secure from subarray [l..r].
   - Recurrence:
       diff(l, r) = max(
           nums[l] - diff(l+1, r),
           nums[r] - diff(l, r-1)
       )
     The term we subtract is the opponent's best achievable difference after our move.
   - Base case: l == r => nums[l]
   - Answer: diff(0, n-1) >= 0 implies Player 1 can tie or win.
 
 Why this is Backtracking:
   - The naive solution considers both choices (take left or right) at each state (l, r),
     which is a binary decision tree (backtracking). Memoization prunes repeated subproblems,
     turning exponential branching into O(n^2).

 Complexity:
   - Time: O(n^2) states, O(1) per state => O(n^2)
   - Space: O(n^2) memo + O(n) recursion depth

 Also included:
   - Bottom-up DP variant yielding the same result for completeness.
*/

import java.util.*;

public class PredictTheWinner_Leetcode486 {

    // Top-Down with Memoization (Backtracking + DP on intervals)
    public boolean PredictTheWinner(int[] nums) {
        int n = nums.length;
        Integer[][] memo = new Integer[n][n];
        int diff = dfs(0, n - 1, nums, memo);
        return diff >= 0;
    }

    // Returns the max score difference current player can achieve from nums[l..r]
    private int dfs(int l, int r, int[] nums, Integer[][] memo) {
        if (l == r) return nums[l];
        if (memo[l][r] != null) return memo[l][r];

        int takeLeft = nums[l] - dfs(l + 1, r, nums, memo);
        int takeRight = nums[r] - dfs(l, r - 1, nums, memo);

        return memo[l][r] = Math.max(takeLeft, takeRight);
    }

    // Bottom-Up DP (optional alternative)
    public boolean PredictTheWinnerBottomUp(int[] nums) {
        int n = nums.length;
        int[][] dp = new int[n][n];
        for (int i = 0; i < n; i++) dp[i][i] = nums[i];

        for (int len = 2; len <= n; len++) {
            for (int l = 0; l + len - 1 < n; l++) {
                int r = l + len - 1;
                int takeLeft = nums[l] - dp[l + 1][r];
                int takeRight = nums[r] - dp[l][r - 1];
                dp[l][r] = Math.max(takeLeft, takeRight);
            }
        }
        return dp[0][n - 1] >= 0;
    }

    // Utility: A straightforward Minimax (without memo) for illustration on small n (exponential).
    // Not used in final solution due to performance, but retained to show the backtracking tree.
    public boolean PredictTheWinnerMinimax(int[] nums) {
        return minimax(nums, 0, nums.length - 1, true) >= 0;
    }

    // Returns score difference assuming 'isP1' when we call this function indicates whose turn it is.
    private int minimax(int[] nums, int l, int r, boolean isP1) {
        if (l == r) return isP1 ? nums[l] : -nums[l];
        if (isP1) {
            // P1 maximizes difference
            int takeLeft = nums[l] + minimax(nums, l + 1, r, false);
            int takeRight = nums[r] + minimax(nums, l, r - 1, false);
            return Math.max(takeLeft, takeRight);
        } else {
            // P2 minimizes P1's advantage => subtract the value P2 takes
            int takeLeft = -nums[l] + minimax(nums, l + 1, r, true);
            int takeRight = -nums[r] + minimax(nums, l, r - 1, true);
            return Math.max(takeLeft, takeRight); // maximizing P1's perspective
        }
    }

    // Simple demo tests
    public static void main(String[] args) {
        PredictTheWinner_Leetcode486 solver = new PredictTheWinner_Leetcode486();

        int[][] tests = {
            {1, 5, 2},          // false
            {1, 5, 233, 7},     // true
            {1, 3, 1},          // true (tie allows first to win)
            {2, 2, 2, 2},       // true (even length sequences: first can always tie at least)
            {7},                // true
            {0, 0, 7, 15, 5, 3, 8, 9, 12, 20} // varied case
        };

        for (int i = 0; i < tests.length; i++) {
            boolean ansTopDown = solver.PredictTheWinner(tests[i]);
            boolean ansBottomUp = solver.PredictTheWinnerBottomUp(tests[i]);
            System.out.println("Test " + (i + 1) + " nums=" + Arrays.toString(tests[i]));
            System.out.println("  Top-Down (memo): " + ansTopDown);
            System.out.println("  Bottom-Up:       " + ansBottomUp);
            System.out.println();
        }
    }
}
