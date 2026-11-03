package dsa.algorithms.backtracking.gametheory;
/*
 Problem: Can I Win (Leetcode 464)
 Category: Game Theory + Backtracking with Memoization (Bitmask DP)
 
 Rules:
   - Players alternate picking integers from 1..M (each number used at most once).
   - Running total increases by picked number; player who reaches or exceeds desiredTotal wins.
 
 Approach:
   - State compression with bitmask (mask bit i indicates usage of number i+1).
   - DFS with memo on mask and remaining target; if any move leads the opponent to a losing state, current state is winning.
   - Pruning:
       * If sum(1..M) < desiredTotal -> impossible for first player.
       * If M >= desiredTotal -> first player can win immediately by taking desiredTotal.
       * Short-circuit on remain - x <= 0 (immediate win).

 Complexity:
   - Time: O(2^M * M) worst case, M <= 20.
   - Space: O(2^M) memo + O(M) recursion depth.
*/
import java.util.Arrays;

public class CanIWin_Leetcode464 {

    public boolean canIWin(int maxChoosableInteger, int desiredTotal) {
        // Trivial cases
        if (desiredTotal <= 0) return true;
        int M = maxChoosableInteger;
        // If the largest single number suffices, first player wins immediately
        if (M >= desiredTotal) return true;

        // If total sum is insufficient, no one can reach desiredTotal
        long sum = (long) M * (M + 1) / 2;
        if (sum < desiredTotal) return false;

        // Memoization over masks (2^M); null = unvisited
        int size = 1 << M; // bit i corresponds to number (i+1)
        Boolean[] memo = new Boolean[size];
        return dfs(0, desiredTotal, M, memo);
    }

    // Returns true if current player can force a win from this state
    private boolean dfs(int mask, int remain, int M, Boolean[] memo) {
        if (remain <= 0) {
            // If we arrive here, it means the previous player has already reached/exceeded target
            return false;
        }
        if (memo[mask] != null) return memo[mask];

        // Iterative move ordering: try larger picks first (often prunes quicker)
        for (int x = M; x >= 1; x--) {
            int bit = 1 << (x - 1);
            if ((mask & bit) != 0) continue; // already used
            if (x >= remain) {
                // Immediate win by reaching/exceeding target
                return memo[mask] = true;
            }
            // If opponent cannot win after we pick x, we can force a win
            if (!dfs(mask | bit, remain - x, M, memo)) {
                return memo[mask] = true;
            }
        }
        return memo[mask] = false;
    }

    // Optional helper to visualize mask to set (for debugging)
    private static String usedNumbers(int mask, int M) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        boolean first = true;
        for (int x = 1; x <= M; x++) {
            int bit = 1 << (x - 1);
            if ((mask & bit) != 0) {
                if (!first) sb.append(", ");
                sb.append(x);
                first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }

    // Demo
    public static void main(String[] args) {
        CanIWin_Leetcode464 solver = new CanIWin_Leetcode464();

        // Standard examples
        System.out.println("M=10, desired=11 -> " + solver.canIWin(10, 11)); // false
        System.out.println("M=10, desired=0  -> " + solver.canIWin(10, 0));  // true
        System.out.println("M=10, desired=1  -> " + solver.canIWin(10, 1));  // true
        System.out.println("M=10, desired=40 -> " + solver.canIWin(10, 40)); // false (sum=55 OK but known losing)

        // Edge/interesting cases
        System.out.println("M=20, desired=210 -> " + solver.canIWin(20, 210)); // false (sum=210 exactly; first loses)
        System.out.println("M=20, desired=209 -> " + solver.canIWin(20, 209)); // true
    }
}
