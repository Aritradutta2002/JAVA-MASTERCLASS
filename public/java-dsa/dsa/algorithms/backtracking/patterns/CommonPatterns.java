package dsa.algorithms.backtracking.patterns;

import java.util.*;

/**
 * COMMON BACKTRACKING PATTERNS FOR INTERVIEWS
 * 
 * This file contains the most frequently asked backtracking patterns
 * Master these patterns and you can solve 90% of backtracking interview questions
 */
public class CommonPatterns {

    /**
     * PATTERN 1: GENERATE ALL SUBSETS/COMBINATIONS
     * Problems: Subsets, Combination Sum, Letter Combinations
     * 
     * Key Insight: At each element, we have 2 choices - include or exclude
     */
    public static class SubsetPattern {
        
        public List<List<Integer>> subsets(int[] nums) {
            List<List<Integer>> result = new ArrayList<>();
            backtrack(result, new ArrayList<>(), nums, 0);
            return result;
        }
        
        private void backtrack(List<List<Integer>> result, 
                             List<Integer> current, 
                             int[] nums, 
                             int start) {
            // Add current subset to result
            result.add(new ArrayList<>(current));
            
            // Try adding each remaining element
            for (int i = start; i < nums.length; i++) {
                current.add(nums[i]);           // Choose
                backtrack(result, current, nums, i + 1);  // Recurse
                current.remove(current.size() - 1);       // Backtrack
            }
        }
        
        // Alternative: Choose/Don't Choose approach
        public List<List<Integer>> subsetsChoosePattern(int[] nums) {
            List<List<Integer>> result = new ArrayList<>();
            chooseBacktrack(result, new ArrayList<>(), nums, 0);
            return result;
        }
        
        private void chooseBacktrack(List<List<Integer>> result,
                                   List<Integer> current,
                                   int[] nums,
                                   int index) {
            if (index == nums.length) {
                result.add(new ArrayList<>(current));
                return;
            }
            
            // Don't choose current element
            chooseBacktrack(result, current, nums, index + 1);
            
            // Choose current element
            current.add(nums[index]);
            chooseBacktrack(result, current, nums, index + 1);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * PATTERN 2: GENERATE ALL PERMUTATIONS
     * Problems: Permutations, Next Permutation, Palindrome Permutation
     * 
     * Key Insight: At each position, try all unused elements
     */
    public static class PermutationPattern {
        
        public List<List<Integer>> permute(int[] nums) {
            List<List<Integer>> result = new ArrayList<>();
            backtrack(result, new ArrayList<>(), nums);
            return result;
        }
        
        private void backtrack(List<List<Integer>> result,
                             List<Integer> current,
                             int[] nums) {
            if (current.size() == nums.length) {
                result.add(new ArrayList<>(current));
                return;
            }
            
            for (int i = 0; i < nums.length; i++) {
                if (current.contains(nums[i])) continue; // Skip used elements
                
                current.add(nums[i]);                    // Choose
                backtrack(result, current, nums);        // Recurse
                current.remove(current.size() - 1);      // Backtrack
            }
        }
        
        // Optimized version using boolean array
        public List<List<Integer>> permuteOptimized(int[] nums) {
            List<List<Integer>> result = new ArrayList<>();
            boolean[] used = new boolean[nums.length];
            backtrackOptimized(result, new ArrayList<>(), nums, used);
            return result;
        }
        
        private void backtrackOptimized(List<List<Integer>> result,
                                      List<Integer> current,
                                      int[] nums,
                                      boolean[] used) {
            if (current.size() == nums.length) {
                result.add(new ArrayList<>(current));
                return;
            }
            
            for (int i = 0; i < nums.length; i++) {
                if (used[i]) continue;
                
                current.add(nums[i]);
                used[i] = true;
                backtrackOptimized(result, current, nums, used);
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }
    
    /**
     * PATTERN 3: CONSTRAINT SATISFACTION
     * Problems: N-Queens, Sudoku, Graph Coloring
     * 
     * Key Insight: Check constraints before making choices
     */
    public static class ConstraintPattern {
        
        // Example: N-Queens simplified
        public List<List<String>> solveNQueens(int n) {
            List<List<String>> result = new ArrayList<>();
            char[][] board = new char[n][n];
            
            // Initialize board
            for (int i = 0; i < n; i++) {
                Arrays.fill(board[i], '.');
            }
            
            backtrack(result, board, 0);
            return result;
        }
        
        private void backtrack(List<List<String>> result, char[][] board, int row) {
            if (row == board.length) {
                result.add(constructSolution(board));
                return;
            }
            
            for (int col = 0; col < board.length; col++) {
                if (isValid(board, row, col)) {
                    board[row][col] = 'Q';                    // Choose
                    backtrack(result, board, row + 1);        // Recurse
                    board[row][col] = '.';                    // Backtrack
                }
            }
        }
        
        private boolean isValid(char[][] board, int row, int col) {
            // Check column
            for (int i = 0; i < row; i++) {
                if (board[i][col] == 'Q') return false;
            }
            
            // Check diagonal
            for (int i = row - 1, j = col - 1; i >= 0 && j >= 0; i--, j--) {
                if (board[i][j] == 'Q') return false;
            }
            
            // Check anti-diagonal
            for (int i = row - 1, j = col + 1; i >= 0 && j < board.length; i--, j++) {
                if (board[i][j] == 'Q') return false;
            }
            
            return true;
        }
        
        private List<String> constructSolution(char[][] board) {
            List<String> result = new ArrayList<>();
            for (char[] row : board) {
                result.add(new String(row));
            }
            return result;
        }
    }
    
    /**
     * PATTERN 4: PARTITIONING PROBLEMS
     * Problems: Palindrome Partitioning, Word Break II
     * 
     * Key Insight: Try all possible ways to split the input
     */
    public static class PartitionPattern {
        
        public List<List<String>> partition(String s) {
            List<List<String>> result = new ArrayList<>();
            backtrack(result, new ArrayList<>(), s, 0);
            return result;
        }
        
        private void backtrack(List<List<String>> result,
                             List<String> current,
                             String s,
                             int start) {
            if (start >= s.length()) {
                result.add(new ArrayList<>(current));
                return;
            }
            
            for (int end = start; end < s.length(); end++) {
                String substring = s.substring(start, end + 1);
                if (isPalindrome(substring)) {
                    current.add(substring);                           // Choose
                    backtrack(result, current, s, end + 1);           // Recurse
                    current.remove(current.size() - 1);               // Backtrack
                }
            }
        }
        
        private boolean isPalindrome(String s) {
            int left = 0, right = s.length() - 1;
            while (left < right) {
                if (s.charAt(left++) != s.charAt(right--)) {
                    return false;
                }
            }
            return true;
        }
    }
    
    /**
     * PATTERN 5: TARGET SUM PROBLEMS
     * Problems: Combination Sum, Combination Sum II, Expression Add Operators
     * 
     * Key Insight: Build solution incrementally while tracking sum
     */
    public static class TargetSumPattern {
        
        public List<List<Integer>> combinationSum(int[] candidates, int target) {
            List<List<Integer>> result = new ArrayList<>();
            Arrays.sort(candidates); // For optimization
            backtrack(result, new ArrayList<>(), candidates, target, 0);
            return result;
        }
        
        private void backtrack(List<List<Integer>> result,
                             List<Integer> current,
                             int[] candidates,
                             int remaining,
                             int start) {
            if (remaining < 0) return; // Pruning - exceeded target
            
            if (remaining == 0) {
                result.add(new ArrayList<>(current));
                return;
            }
            
            for (int i = start; i < candidates.length; i++) {
                current.add(candidates[i]);
                // Note: i (not i+1) because we can reuse same element
                backtrack(result, current, candidates, remaining - candidates[i], i);
                current.remove(current.size() - 1);
            }
        }
    }
    
    /**
     * INTERVIEW SUCCESS FRAMEWORK
     * 
     * 1. IDENTIFY THE PATTERN
     *    - What are we generating? (subsets, permutations, paths)
     *    - What constraints do we have?
     *    - Can we build solution incrementally?
     * 
     * 2. CHOOSE THE RIGHT TEMPLATE
     *    - Subsets: include/exclude or start index
     *    - Permutations: try all unused elements
     *    - Constraints: validate before choosing
     *    - Partitions: try all split points
     *    - Target sum: track remaining sum
     * 
     * 3. IMPLEMENT WITH CARE
     *    - Clear base case
     *    - Proper choose-recurse-backtrack
     *    - Create new copies when adding to result
     *    - Handle duplicates if needed
     * 
     * 4. OPTIMIZE IF NEEDED
     *    - Sort for pruning opportunities
     *    - Early termination conditions
     *    - Skip duplicates efficiently
     */
    
    public static void main(String[] args) {
        System.out.println("Master these 5 patterns and you'll ace backtracking interviews!");
        
        // Test subset pattern
        SubsetPattern sp = new SubsetPattern();
        System.out.println("Subsets of [1,2,3]: " + sp.subsets(new int[]{1,2,3}));
        
        // Test permutation pattern
        PermutationPattern pp = new PermutationPattern();
        System.out.println("Permutations of [1,2]: " + pp.permute(new int[]{1,2}));
    }
}
