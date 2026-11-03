package dsa.algorithms.recursion.theory;

import java.util.ArrayList;
import java.util.List;

/**
 * BacktrackingTemplate - Canonical pattern for exploring decision trees.
 *
 * Interview use case: quickly derive solutions for permutations, combinations,
 * subsets, and constraint satisfaction problems (N-Queens, Sudoku, etc.).
 */
public final class BacktrackingTemplate {

    private BacktrackingTemplate() {
        // Utility class
    }

    /**
     * Generates all subsets of an array to illustrate the backtracking skeleton.
     * Time Complexity: O(2^n), Space: O(n) for recursion depth.
     */
    public static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(int[] nums, int index, List<Integer> slate, List<List<Integer>> result) {
        result.add(new ArrayList<>(slate)); // record current slate

        for (int i = index; i < nums.length; i++) {
            slate.add(nums[i]);            // choose
            backtrack(nums, i + 1, slate, result); // explore
            slate.remove(slate.size() - 1); // un-choose
        }
    }

    public static void main(String[] args) {
        int[] sample = {1, 2, 3};
        System.out.println("=== Backtracking Subsets Template ===");
        System.out.println(subsets(sample));

        System.out.println("\nUse this template to transition to:");
        System.out.println("- Permutations: iterate i index differently, track used flags");
        System.out.println("- Combination Sum: add pruning checks before recursion");
        System.out.println("- Constraint problems: add validity checks before choose");
    }
}


