package dsa.algorithms.recursion.problems.medium;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CombinationSum - Find all unique combinations where candidates sum to target.
 *
 * Interview Angles:
 * - Demonstrates backtracking with pruning and repeated element usage
 * - Encourages complexity analysis (branching factor, depth)
 * - Gateway to variants like Combination Sum II and K-combination problems
 */
public class CombinationSum {

    public static List<List<Integer>> combinationSum(int[] candidates, int target) {
        Arrays.sort(candidates); // optional: helps prune earlier
        List<List<Integer>> result = new ArrayList<>();
        backtrack(candidates, target, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(int[] candidates, int target, int index, List<Integer> slate, List<List<Integer>> result) {
        if (target == 0) {
            result.add(new ArrayList<>(slate));
            return;
        }
        for (int i = index; i < candidates.length; i++) {
            int candidate = candidates[i];
            if (candidate > target) {
                break; // sorted array ensures no need to continue
            }
            slate.add(candidate);
            backtrack(candidates, target - candidate, i, slate, result); // i (not i+1) because reuse allowed
            slate.remove(slate.size() - 1);
        }
    }

    public static void main(String[] args) {
        int[] candidates = {2, 3, 6, 7};
        int target = 7;
        System.out.println("=== Combination Sum ===");
        System.out.println(combinationSum(candidates, target));
    }
}


