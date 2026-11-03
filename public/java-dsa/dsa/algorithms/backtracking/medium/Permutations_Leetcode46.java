package dsa.algorithms.backtracking.medium;

import java.util.*;

/**
 * PERMUTATIONS - LeetCode 46 (MEDIUM)
 *
 * Given an array of distinct integers, return all permutations.
 * Interview-ready solutions:
 *  - Swap-based backtracking (in-place, optimal space)
 *  - Boolean used[] backtracking (clean and intuitive)
 *
 * Time: O(n * n!) to build copies
 * Space: O(n) recursion stack (+ O(1) extra for swap variant)
 */
public class Permutations_Leetcode46 {

    // Approach 1: In-place backtracking with swapping
    public List<List<Integer>> permute(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        backtrackSwap(0, nums, res);
        return res;
    }

    private void backtrackSwap(int index, int[] nums, List<List<Integer>> res) {
        if (index == nums.length) {
            List<Integer> list = new ArrayList<>(nums.length);
            for (int v : nums) list.add(v);
            res.add(list);
            return;
        }
        for (int i = index; i < nums.length; i++) {
            swap(nums, index, i);
            backtrackSwap(index + 1, nums, res);
            swap(nums, index, i); // backtrack
        }
    }

    private void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    // Approach 2: Backtracking with boolean used[] (no in-place mutation)
    public List<List<Integer>> permuteUsed(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        boolean[] used = new boolean[nums.length];
        backtrackUsed(nums, used, new ArrayList<>(), res);
        return res;
    }

    private void backtrackUsed(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> res) {
        if (path.size() == nums.length) {
            res.add(new ArrayList<>(path));
            return;
        }
        for (int i = 0; i < nums.length; i++) {
            if (used[i]) continue;
            used[i] = true;
            path.add(nums[i]);
            backtrackUsed(nums, used, path, res);
            path.remove(path.size() - 1);
            used[i] = false;
        }
    }

    public static void main(String[] args) {
        Permutations_Leetcode46 solver = new Permutations_Leetcode46();
        int[] arr = {1, 2, 3};

        System.out.println("Swap-based permutations:");
        List<List<Integer>> res1 = solver.permute(arr.clone());
        for (List<Integer> p : res1) System.out.println(p);

        System.out.println("\nUsed[]-based permutations:");
        List<List<Integer>> res2 = solver.permuteUsed(arr);
        for (List<Integer> p : res2) System.out.println(p);
    }
}

