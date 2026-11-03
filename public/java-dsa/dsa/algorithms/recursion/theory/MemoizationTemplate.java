package dsa.algorithms.recursion.theory;

import java.util.HashMap;
import java.util.Map;

/**
 * MemoizationTemplate - Blueprint for top-down dynamic programming.
 *
 * Interview Angles:
 * - Shows how to cache overlapping subproblems (Fibonacci, Climb Stairs, Grid paths)
 * - Encourages discussion on time/space trade-offs and when to prefer bottom-up DP
 * - Demonstrates memo key design for multi-parameter recursion
 */
public final class MemoizationTemplate {

    private MemoizationTemplate() {
        // Utility class
    }

    /**
     * Climb Stairs: number of unique ways to reach step n taking 1 or 2 steps.
     * Time: O(n), Space: O(n) memo.
     */
    public static int climbStairs(int n) {
        return climbStairsMemo(n, new HashMap<>());
    }

    private static int climbStairsMemo(int n, Map<Integer, Integer> memo) {
        if (n <= 2) {
            return n;
        }
        if (memo.containsKey(n)) {
            return memo.get(n);
        }
        int result = climbStairsMemo(n - 1, memo) + climbStairsMemo(n - 2, memo);
        memo.put(n, result);
        return result;
    }

    /**
     * Grid paths example: unique paths from (0,0) to (rows-1, cols-1) moving right/down.
     * Demonstrates multi-parameter memo key.
     */
    public static int gridUniquePaths(int rows, int cols) {
        Map<String, Integer> memo = new HashMap<>();
        return gridPathsHelper(0, 0, rows, cols, memo);
    }

    private static int gridPathsHelper(int r, int c, int rows, int cols, Map<String, Integer> memo) {
        if (r == rows - 1 && c == cols - 1) {
            return 1;
        }
        if (r >= rows || c >= cols) {
            return 0;
        }
        String key = r + "," + c;
        if (memo.containsKey(key)) {
            return memo.get(key);
        }
        int paths = gridPathsHelper(r + 1, c, rows, cols, memo) + gridPathsHelper(r, c + 1, rows, cols, memo);
        memo.put(key, paths);
        return paths;
    }

    public static void main(String[] args) {
        System.out.println("=== Climb Stairs (Memoized) ===");
        System.out.println("n = 5 -> " + climbStairs(5));

        System.out.println("\n=== Grid Paths (Memoized) ===");
        System.out.println("3x3 grid -> " + gridUniquePaths(3, 3));

        System.out.println("\nTips:");
        System.out.println("- Choose memo keys carefully (tuples for multi-parameter problems)");
        System.out.println("- Memoization trades space for time; clear cache if reusing between test cases");
    }
}


