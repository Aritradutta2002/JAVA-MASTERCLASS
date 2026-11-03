package dsa.algorithms.recursion.problems.medium;

import java.util.ArrayList;
import java.util.List;

/**
 * Subsets - Power set generation using backtracking.
 *
 * Interview Angles:
 * - Reinforces inclusion/exclusion decisions
 * - Easily extendable to handle duplicates (Subsets II)
 * - Useful stepping stone before tackling permutation style problems
 */
public class Subsets {

    public static List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(nums, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(int[] nums, int index, List<Integer> slate, List<List<Integer>> result) {
        if (index == nums.length) {
            result.add(new ArrayList<>(slate));
            return;
        }

        // Exclude current element
        backtrack(nums, index + 1, slate, result);

        // Include current element
        slate.add(nums[index]);
        backtrack(nums, index + 1, slate, result);
        slate.remove(slate.size() - 1);
    }

    public static void main(String[] args) {
        int[] nums = {1, 2, 3};
        System.out.println("=== Subsets ===");
        System.out.println(subsets(nums));
    }
}


