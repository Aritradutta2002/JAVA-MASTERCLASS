package dsa.algorithms.backtracking.easy;

import java.util.*;

/**
 * SUBSETS - LeetCode 78 (EASY)
 * 
 * Problem: Given an integer array nums of unique elements, return all possible subsets.
 * The solution set must not contain duplicate subsets. Return the solution in any order.
 * 
 * Example:
 * Input: nums = [1,2,3]
 * Output: [[],[1],[2],[1,2],[3],[1,3],[2,3],[1,2,3]]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐⭐
 * - Fundamental backtracking problem
 * - Tests understanding of choose/don't choose pattern
 * - Gateway to harder combination problems
 * 
 * Time Complexity: O(2^N * N) - 2^N subsets, N time to copy each
 * Space Complexity: O(N) - recursion depth
 */
public class Subsets_Leetcode78 {
    
    /**
     * APPROACH 1: BACKTRACKING - MOST INTUITIVE
     * Think: At each index, we can either include or exclude the element
     */
    public List<List<Integer>> subsets(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        backtrack(result, new ArrayList<>(), nums, 0);
        return result;
    }
    
    private void backtrack(List<List<Integer>> result, 
                          List<Integer> current, 
                          int[] nums, 
                          int start) {
        // Add current subset (including empty subset)
        result.add(new ArrayList<>(current));
        
        // Try including each remaining element
        for (int i = start; i < nums.length; i++) {
            current.add(nums[i]);                    // Choose
            backtrack(result, current, nums, i + 1); // Recurse with next index
            current.remove(current.size() - 1);      // Backtrack
        }
    }
    
    /**
     * APPROACH 2: CHOOSE/DON'T CHOOSE PATTERN
     * Alternative backtracking approach - very clear logic
     */
    public List<List<Integer>> subsetsChoosePattern(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        chooseBacktrack(result, new ArrayList<>(), nums, 0);
        return result;
    }
    
    private void chooseBacktrack(List<List<Integer>> result,
                               List<Integer> current,
                               int[] nums,
                               int index) {
        // Base case: processed all elements
        if (index == nums.length) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        // Choice 1: Don't include current element
        chooseBacktrack(result, current, nums, index + 1);
        
        // Choice 2: Include current element
        current.add(nums[index]);
        chooseBacktrack(result, current, nums, index + 1);
        current.remove(current.size() - 1); // Backtrack
    }
    
    /**
     * APPROACH 3: ITERATIVE BIT MANIPULATION
     * For comparison - shows different thinking approach
     */
    public List<List<Integer>> subsetsIterative(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        int n = nums.length;
        
        // Generate all possible bitmasks from 0 to 2^n - 1
        for (int mask = 0; mask < (1 << n); mask++) {
            List<Integer> subset = new ArrayList<>();
            
            // Check each bit position
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(nums[i]);
                }
            }
            
            result.add(subset);
        }
        
        return result;
    }
    
    /**
     * APPROACH 4: CASCADING
     * Build subsets incrementally - good for understanding
     */
    public List<List<Integer>> subsetsCascading(int[] nums) {
        List<List<Integer>> result = new ArrayList<>();
        result.add(new ArrayList<>()); // Start with empty subset
        
        for (int num : nums) {
            List<List<Integer>> newSubsets = new ArrayList<>();
            
            // For each existing subset, create new subset by adding current number
            for (List<Integer> subset : result) {
                List<Integer> newSubset = new ArrayList<>(subset);
                newSubset.add(num);
                newSubsets.add(newSubset);
            }
            
            result.addAll(newSubsets);
        }
        
        return result;
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Why backtracking works here?
     *    - We need to explore all possibilities
     *    - Decision tree: include/exclude at each position
     * 
     * 2. Time complexity breakdown:
     *    - 2^N possible subsets (each element can be included or not)
     *    - N time to copy each subset to result
     *    - Total: O(2^N * N)
     * 
     * 3. Space complexity:
     *    - Recursion depth: O(N)
     *    - Result space: O(2^N * N) but doesn't count toward space complexity
     * 
     * 4. Follow-up questions:
     *    - What if array has duplicates? (Subsets II)
     *    - Can you do it iteratively? (Yes, shown above)
     *    - What if we only want subsets of size k? (Combinations)
     */
    
    public static void main(String[] args) {
        Subsets_Leetcode78 solution = new Subsets_Leetcode78();
        
        // Test case 1
        int[] nums1 = {1, 2, 3};
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Subsets: " + solution.subsets(nums1));
        
        // Test case 2
        int[] nums2 = {0};
        System.out.println("\nInput: " + Arrays.toString(nums2));
        System.out.println("Subsets: " + solution.subsets(nums2));
        
        // Test case 3 - Edge case: empty array
        int[] nums3 = {};
        System.out.println("\nInput: " + Arrays.toString(nums3));
        System.out.println("Subsets: " + solution.subsets(nums3));
        
        // Compare different approaches
        System.out.println("\nTesting different approaches:");
        System.out.println("Approach 1 (Backtrack): " + solution.subsets(nums1));
        System.out.println("Approach 2 (Choose): " + solution.subsetsChoosePattern(nums1));
        System.out.println("Approach 3 (Iterative): " + solution.subsetsIterative(nums1));
        System.out.println("Approach 4 (Cascading): " + solution.subsetsCascading(nums1));
    }
}
