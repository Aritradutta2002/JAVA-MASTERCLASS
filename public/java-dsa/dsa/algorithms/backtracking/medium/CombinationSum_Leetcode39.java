package dsa.algorithms.backtracking.medium;

import java.util.*;

/**
 * COMBINATION SUM - LeetCode 39 (MEDIUM)
 * 
 * Problem: Given an array of distinct integers candidates and a target integer target,
 * return a list of all unique combinations of candidates where the chosen numbers sum to target.
 * The same number may be chosen from candidates an unlimited number of times.
 * 
 * Example:
 * Input: candidates = [2,3,6,7], target = 7
 * Output: [[2,2,3],[7]]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐⭐
 * - Extremely common in FAANG interviews
 * - Tests backtracking with constraints and optimization
 * - Gateway to other combination sum variants
 * 
 * Time Complexity: O(N^(T/M)) where N = candidates.length, T = target, M = minimal value
 * Space Complexity: O(T/M) - maximum depth of recursion
 */
public class CombinationSum_Leetcode39 {
    
    /**
     * APPROACH 1: BACKTRACKING WITH SORTING (RECOMMENDED)
     * Sort array for early termination optimization
     */
    public List<List<Integer>> combinationSum(int[] candidates, int target) {
        List<List<Integer>> result = new ArrayList<>();
        Arrays.sort(candidates); // Enable early termination
        backtrack(result, new ArrayList<>(), candidates, target, 0);
        return result;
    }
    
    private void backtrack(List<List<Integer>> result,
                          List<Integer> current,
                          int[] candidates,
                          int remaining,
                          int start) {
        // Base case: found valid combination
        if (remaining == 0) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = start; i < candidates.length; i++) {
            // Early termination: if current candidate > remaining, stop
            // (works because array is sorted)
            if (candidates[i] > remaining) {
                break;
            }
            
            current.add(candidates[i]);
            // Key insight: use i (not i+1) because we can reuse same element
            backtrack(result, current, candidates, remaining - candidates[i], i);
            current.remove(current.size() - 1); // Backtrack
        }
    }
    
    /**
     * APPROACH 2: BACKTRACKING WITHOUT SORTING
     * Basic version - less optimized but easier to understand
     */
    public List<List<Integer>> combinationSumBasic(int[] candidates, int target) {
        List<List<Integer>> result = new ArrayList<>();
        backtrackBasic(result, new ArrayList<>(), candidates, target, 0);
        return result;
    }
    
    private void backtrackBasic(List<List<Integer>> result,
                               List<Integer> current,
                               int[] candidates,
                               int remaining,
                               int start) {
        if (remaining < 0) {
            return; // Invalid path
        }
        
        if (remaining == 0) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = start; i < candidates.length; i++) {
            current.add(candidates[i]);
            backtrackBasic(result, current, candidates, remaining - candidates[i], i);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * APPROACH 3: FOR EACH CANDIDATE DECISION
     * Alternative perspective: for each candidate, decide how many times to use it
     */
    public List<List<Integer>> combinationSumAlternative(int[] candidates, int target) {
        List<List<Integer>> result = new ArrayList<>();
        backtrackAlternative(result, new ArrayList<>(), candidates, target, 0);
        return result;
    }
    
    private void backtrackAlternative(List<List<Integer>> result,
                                    List<Integer> current,
                                    int[] candidates,
                                    int remaining,
                                    int index) {
        if (remaining == 0) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        if (index >= candidates.length || remaining < 0) {
            return;
        }
        
        // Option 1: Don't use current candidate
        backtrackAlternative(result, current, candidates, remaining, index + 1);
        
        // Option 2: Use current candidate (can use multiple times)
        if (candidates[index] <= remaining) {
            current.add(candidates[index]);
            backtrackAlternative(result, current, candidates, remaining - candidates[index], index);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * APPROACH 4: DYNAMIC PROGRAMMING (FOR COMPARISON)
     * Bottom-up approach - less intuitive but good to know
     */
    public List<List<Integer>> combinationSumDP(int[] candidates, int target) {
        List<List<List<Integer>>> dp = new ArrayList<>();
        
        // Initialize dp array
        for (int i = 0; i <= target; i++) {
            dp.add(new ArrayList<>());
        }
        
        // Base case
        dp.get(0).add(new ArrayList<>());
        
        for (int candidate : candidates) {
            for (int i = candidate; i <= target; i++) {
                for (List<Integer> combination : dp.get(i - candidate)) {
                    List<Integer> newCombination = new ArrayList<>(combination);
                    newCombination.add(candidate);
                    dp.get(i).add(newCombination);
                }
            }
        }
        
        return dp.get(target);
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Key insights:
     *    - Can reuse same element multiple times
     *    - Need to avoid duplicate combinations [2,3] vs [3,2]
     *    - Use start index to maintain order and avoid duplicates
     * 
     * 2. Optimization strategies:
     *    - Sort array for early termination
     *    - Pruning when remaining < current candidate
     * 
     * 3. Why start index instead of used array?
     *    - We can reuse elements, so boolean used[] won't work
     *    - Start index ensures we don't create duplicate combinations
     * 
     * 4. Follow-up questions:
     *    - What if each number can only be used once? (Combination Sum II)
     *    - What if we want combinations of specific size k?
     *    - How to find the minimum number of coins? (Coin Change)
     * 
     * 5. Edge cases:
     *    - target = 0 (should return [[]])
     *    - No valid combinations (return [])
     *    - Single element array
     *    - All candidates > target
     */
    
    public static void main(String[] args) {
        CombinationSum_Leetcode39 solution = new CombinationSum_Leetcode39();
        
        // Test case 1
        int[] candidates1 = {2, 3, 6, 7};
        int target1 = 7;
        System.out.println("Input: " + Arrays.toString(candidates1) + ", target = " + target1);
        System.out.println("Output: " + solution.combinationSum(candidates1, target1));
        System.out.println();
        
        // Test case 2
        int[] candidates2 = {2, 3, 5};
        int target2 = 8;
        System.out.println("Input: " + Arrays.toString(candidates2) + ", target = " + target2);
        System.out.println("Output: " + solution.combinationSum(candidates2, target2));
        System.out.println();
        
        // Test case 3 - Edge case
        int[] candidates3 = {2};
        int target3 = 1;
        System.out.println("Input: " + Arrays.toString(candidates3) + ", target = " + target3);
        System.out.println("Output: " + solution.combinationSum(candidates3, target3));
        System.out.println();
        
        // Performance comparison
        System.out.println("Performance test:");
        int[] largeCandidates = {2, 3, 5, 7, 11, 13};
        int largeTarget = 30;
        
        long start1 = System.nanoTime();
        List<List<Integer>> result1 = solution.combinationSum(largeCandidates, largeTarget);
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        List<List<Integer>> result2 = solution.combinationSumBasic(largeCandidates, largeTarget);
        long end2 = System.nanoTime();
        
        System.out.println("Optimized time: " + (end1 - start1) + " ns");
        System.out.println("Basic time: " + (end2 - start2) + " ns");
        System.out.println("Results match: " + result1.equals(result2));
        System.out.println("Number of combinations: " + result1.size());
    }
}
