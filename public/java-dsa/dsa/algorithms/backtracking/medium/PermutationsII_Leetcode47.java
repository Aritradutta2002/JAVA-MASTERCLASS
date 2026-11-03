package dsa.algorithms.backtracking.medium;

import java.util.*;

/**
 * PERMUTATIONS II - LeetCode 47 (MEDIUM)
 *
 * Problem: Given a collection of numbers that may contain duplicates, return all possible unique permutations.
 *
 * Key ideas:
 * - Sort input to group duplicates
 * - Use boolean[] used to track chosen indices
 * - Skip duplicates at the same recursion level: if nums[i] == nums[i-1] && !used[i-1] continue
 *
 * Time Complexity: O(n! * n)
 * Space Complexity: O(n) recursion stack + O(n) used
 */
public class PermutationsII_Leetcode47 {

    /**
     * Approach 1: Backtracking with used[] and duplicate skip
     * - Sort the array
     * - For each position, iterate indices; skip an index i if:
     *   - it's already used OR
     *   - nums[i] == nums[i-1] and the previous duplicate is not used in this level (to avoid same-level duplicates)
     */
    public List<List<Integer>> permuteUnique(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        Arrays.sort(nums);
        boolean[] used = new boolean[nums.length];
        backtrack(nums, used, new ArrayList<>(), res);
        return res;
    }

    private void backtrack(int[] nums, boolean[] used, List<Integer> path, List<List<Integer>> res) {
        if (path.size() == nums.length) {
            res.add(new ArrayList<>(path));
            return;
        }
        for (int i = 0; i < nums.length; i++) {
            if (used[i]) continue;
            // Skip duplicates at the same recursion level
            if (i > 0 && nums[i] == nums[i - 1] && !used[i - 1]) continue;

            used[i] = true;
            path.add(nums[i]);

            backtrack(nums, used, path, res);

            path.remove(path.size() - 1);
            used[i] = false;
        }
    }

    /**
     * Approach 2: Swap-based backtracking with per-level de-duplication using a HashSet.
     * - Also sorts the array so identical values are adjacent (not strictly required for this method).
     * - Uses a Set at each index to ensure we don't place the same value at position 'index' more than once.
     */
    public List<List<Integer>> permuteUniqueSwap(int[] nums) {
        List<List<Integer>> res = new ArrayList<>();
        Arrays.sort(nums);
        permuteSwap(nums, 0, res);
        return res;
    }

    private void permuteSwap(int[] nums, int index, List<List<Integer>> res) {
        if (index == nums.length) {
            List<Integer> list = new ArrayList<>(nums.length);
            for (int v : nums) list.add(v);
            res.add(list);
            return;
        }
        Set<Integer> seen = new HashSet<>();
        for (int i = index; i < nums.length; i++) {
            if (seen.contains(nums[i])) continue; // avoid using same value at position 'index'
            seen.add(nums[i]);

            swap(nums, index, i);
            permuteSwap(nums, index + 1, res);
            swap(nums, index, i); // backtrack
        }
    }

    private void swap(int[] a, int i, int j) {
        int t = a[i];
        a[i] = a[j];
        a[j] = t;
    }

    public static void main(String[] args) {
        PermutationsII_Leetcode47 solver = new PermutationsII_Leetcode47();

        int[] nums1 = {1, 1, 2};
        System.out.println("Input: [1,1,2]");
        System.out.println("Unique permutations: " + solver.permuteUnique(nums1));

        int[] nums2 = {2, 2, 1, 1};
        System.out.println("Input: [2,2,1,1]");
        System.out.println("Unique permutations: " + solver.permuteUnique(nums2));
    }
}
