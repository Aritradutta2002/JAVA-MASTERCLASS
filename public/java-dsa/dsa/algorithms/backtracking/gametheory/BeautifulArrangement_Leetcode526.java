package dsa.algorithms.backtracking.gametheory;
/*
 Problem: Beautiful Arrangement (Leetcode 526)
 Category: Constraint Backtracking (Game-like placement with rules)

 Definition:
   Count permutations of numbers 1..n such that for position i (1-indexed),
   either (num % i == 0) or (i % num == 0).

 Approach:
   - Backtracking with bitmask to track used numbers.
   - DFS(pos, mask): try placing each unused number at position 'pos'
     if it satisfies the divisibility constraint, then recurse.
   - Prune naturally via constraints; ordering small to large is fine.

 Complexity:
   - Worst-case branching is factorial, but constraints reduce the search space a lot.
   - Time: O(2^n * n) in typical bitmask implementation (each state is mask with n choices),
           practically far less due to strong pruning.
   - Space: O(2^n) for memo if added, but here pure backtracking uses O(n) recursion stack.

 Notes:
   - A memoization by (pos, mask) is possible for counting, but constraints make memo less impactful.
   - For n <= 15 (problem constraints), this backtracking runs comfortably.
*/

import java.util.*;

public class BeautifulArrangement_Leetcode526 {

    // Core backtracking using bitmask
    public int countArrangement(int n) {
        // Precompute valid numbers for each position to reduce checks
        List<List<Integer>> valid = new ArrayList<>();
        for (int pos = 1; pos <= n; pos++) {
            List<Integer> list = new ArrayList<>();
            for (int num = 1; num <= n; num++) {
                if (num % pos == 0 || pos % num == 0) {
                    list.add(num);
                }
            }
            valid.add(list);
        }
        return dfs(1, 0, n, valid);
    }

    // Returns number of valid arrangements from position 'pos' given used numbers 'mask'
    private int dfs(int pos, int mask, int n, List<List<Integer>> valid) {
        if (pos > n) return 1;
        int count = 0;

        // Try only numbers valid at this position
        for (int num : valid.get(pos - 1)) {
            int bit = 1 << (num - 1);
            if ((mask & bit) == 0) {
                count += dfs(pos + 1, mask | bit, n, valid);
            }
        }
        return count;
    }

    // Alternative: Simple version without precomputed lists (direct constraint checking)
    public int countArrangementSimple(int n) {
        return dfsSimple(1, 0, n);
    }

    private int dfsSimple(int pos, int mask, int n) {
        if (pos > n) return 1;
        int count = 0;
        for (int num = 1; num <= n; num++) {
            int bit = 1 << (num - 1);
            if ((mask & bit) == 0 && (num % pos == 0 || pos % num == 0)) {
                count += dfsSimple(pos + 1, mask | bit, n);
            }
        }
        return count;
    }

    // Small heuristic: try numbers with more future compatibility first (optional)
    // Here we simply sort by count of divisibility partners, but gains are modest.
    public int countArrangementHeuristic(int n) {
        int[] compat = new int[n + 1];
        for (int num = 1; num <= n; num++) {
            int c = 0;
            for (int pos = 1; pos <= n; pos++) {
                if (num % pos == 0 || pos % num == 0) c++;
            }
            compat[num] = c;
        }
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) order[i] = i + 1;
        Arrays.sort(order, (a, b) -> Integer.compare(compat[b], compat[a])); // try more compatible numbers earlier
        return dfsHeuristic(1, 0, n, order);
    }

    private int dfsHeuristic(int pos, int mask, int n, Integer[] order) {
        if (pos > n) return 1;
        int count = 0;
        for (int num : order) {
            int bit = 1 << (num - 1);
            if ((mask & bit) == 0 && (num % pos == 0 || pos % num == 0)) {
                count += dfsHeuristic(pos + 1, mask | bit, n, order);
            }
        }
        return count;
    }

    // Demo
    public static void main(String[] args) {
        BeautifulArrangement_Leetcode526 solver = new BeautifulArrangement_Leetcode526();

        int[] tests = {1, 2, 3, 4, 5, 7, 10, 15};
        for (int n : tests) {
            int ans = solver.countArrangement(n);
            int ansSimple = solver.countArrangementSimple(n);
            System.out.println("n=" + n + " -> count=" + ans + " (simple=" + ansSimple + ")");
        }

        // Heuristic comparison for mid values
        int n = 15;
        long t1 = System.currentTimeMillis();
        int a1 = solver.countArrangement(n);
        long t2 = System.currentTimeMillis();
        int a2 = solver.countArrangementHeuristic(n);
        long t3 = System.currentTimeMillis();
        System.out.println("n=" + n + " -> baseline=" + a1 + " in " + (t2 - t1) + "ms, heuristic=" + a2 + " in " + (t3 - t2) + "ms");
    }
}
