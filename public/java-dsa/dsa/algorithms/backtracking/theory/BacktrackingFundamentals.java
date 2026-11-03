package dsa.algorithms.backtracking.theory;

import java.util.*;

/**
 * BACKTRACKING FUNDAMENTALS - Complete Theory Guide
 * 
 * Author: Interview Ready Java
 * Purpose: Master backtracking concepts for technical interviews
 * 
 * WHAT IS BACKTRACKING?
 * - Algorithmic technique for finding all (or one) solutions to computational problems
 * - Incrementally builds candidates to solutions
 * - Abandons candidates ("backtracks") when they cannot lead to valid solution
 * 
 * WHEN TO USE BACKTRACKING?
 * 1. Generate all possible combinations/permutations
 * 2. Find paths in grids/graphs with constraints
 * 3. Solve constraint satisfaction problems (Sudoku, N-Queens)
 * 4. Partition problems with specific conditions
 */
public class BacktrackingFundamentals {

    /**
     * TEMPLATE 1: BASIC BACKTRACKING STRUCTURE
     * Most common pattern used in 80% of backtracking problems
     */
    public static void basicBacktrackTemplate(List<List<Integer>> result, 
                                            List<Integer> current, 
                                            int[] nums, 
                                            int startIndex) {
        // Base case - when to add current solution
        if (isValidSolution(current)) {
            result.add(new ArrayList<>(current)); // IMPORTANT: Create new copy
            return;
        }
        
        // Try all possible choices from current state
        for (int i = startIndex; i < nums.length; i++) {
            // Choose - add element to current solution
            current.add(nums[i]);
            
            // Recurse - explore with this choice
            basicBacktrackTemplate(result, current, nums, i + 1);
            
            // Backtrack - remove element (undo choice)
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * TEMPLATE 2: BACKTRACKING WITH CONSTRAINTS
     * Used when we need to check constraints before making choices
     */
    public static void constraintBacktrack(List<List<Integer>> result,
                                         List<Integer> current,
                                         int[] nums,
                                         boolean[] used,
                                         int targetSum) {
        // Base case
        if (current.size() == nums.length) {
            if (getCurrentSum(current) == targetSum) {
                result.add(new ArrayList<>(current));
            }
            return;
        }
        
        for (int i = 0; i < nums.length; i++) {
            // Constraint checking before making choice
            if (!used[i] && isValidChoice(current, nums[i])) {
                // Choose
                current.add(nums[i]);
                used[i] = true;
                
                // Recurse
                constraintBacktrack(result, current, nums, used, targetSum);
                
                // Backtrack
                current.remove(current.size() - 1);
                used[i] = false;
            }
        }
    }
    
    /**
     * TEMPLATE 3: GRID/BOARD BACKTRACKING
     * Common for problems like N-Queens, Sudoku, Word Search
     */
    public static boolean gridBacktrack(char[][] board, int row, int col) {
        // Base case - reached end of board
        if (row == board.length) {
            return true; // Found valid solution
        }
        
        // Calculate next position
        int nextRow = (col == board[0].length - 1) ? row + 1 : row;
        int nextCol = (col == board[0].length - 1) ? 0 : col + 1;
        
        // If current cell is already filled, move to next
        if (board[row][col] != '.') {
            return gridBacktrack(board, nextRow, nextCol);
        }
        
        // Try all possible values
        for (char c = '1'; c <= '9'; c++) {
            if (isValidPlacement(board, row, col, c)) {
                // Choose
                board[row][col] = c;
                
                // Recurse
                if (gridBacktrack(board, nextRow, nextCol)) {
                    return true;
                }
                
                // Backtrack
                board[row][col] = '.';
            }
        }
        
        return false; // No valid solution found
    }
    
    /**
     * OPTIMIZATION TECHNIQUES
     */
    
    // 1. PRUNING - Skip invalid branches early
    public static void pruningExample(List<List<Integer>> result,
                                    List<Integer> current,
                                    int[] nums,
                                    int startIndex,
                                    int targetSum,
                                    int currentSum) {
        // Pruning condition - if current sum exceeds target, no point continuing
        if (currentSum > targetSum) {
            return; // Early termination
        }
        
        if (currentSum == targetSum) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int i = startIndex; i < nums.length; i++) {
            current.add(nums[i]);
            pruningExample(result, current, nums, i + 1, targetSum, currentSum + nums[i]);
            current.remove(current.size() - 1);
        }
    }
    
    // 2. SORTING FOR OPTIMIZATION - Often helps with pruning
    public static List<List<Integer>> optimizedBacktrack(int[] nums) {
        Arrays.sort(nums); // Sorting enables better pruning
        List<List<Integer>> result = new ArrayList<>();
        backtrackWithSorting(result, new ArrayList<>(), nums, 0);
        return result;
    }
    
    private static void backtrackWithSorting(List<List<Integer>> result,
                                           List<Integer> current,
                                           int[] nums,
                                           int start) {
        result.add(new ArrayList<>(current));
        
        for (int i = start; i < nums.length; i++) {
            // Skip duplicates - important optimization
            if (i > start && nums[i] == nums[i - 1]) {
                continue;
            }
            
            current.add(nums[i]);
            backtrackWithSorting(result, current, nums, i + 1);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * COMMON MISTAKES TO AVOID
     */
    
    // MISTAKE 1: Not creating new ArrayList when adding to result
    public void wrongWay(List<List<Integer>> result, List<Integer> current) {
        result.add(current); // WRONG! - All entries will point to same list
    }
    
    public void correctWay(List<List<Integer>> result, List<Integer> current) {
        result.add(new ArrayList<>(current)); // CORRECT! - Creates new copy
    }
    
    // MISTAKE 2: Forgetting to backtrack
    public void forgettingBacktrack(List<Integer> current, int[] nums, int i) {
        current.add(nums[i]);
        // recurse...
        // FORGOT: current.remove(current.size() - 1);
    }
    
    // MISTAKE 3: Wrong base case
    public void wrongBaseCase(List<Integer> current, int targetSize) {
        if (current.size() >= targetSize) { // WRONG! Should be ==
            // This might add invalid solutions
        }
    }
    
    /**
     * COMPLEXITY ANALYSIS
     * 
     * TIME COMPLEXITY:
     * - Permutations: O(N! * N) - N! permutations, N time to copy each
     * - Combinations: O(2^N * N) - 2^N subsets, N time to copy each
     * - Sudoku: O(9^(N*N)) in worst case
     * - N-Queens: O(N!) - but much better with pruning
     * 
     * SPACE COMPLEXITY:
     * - Recursion depth: O(N) for most problems
     * - Additional space for storing results varies by problem
     */
    
    // Helper methods (implement based on specific problem)
    private static boolean isValidSolution(List<Integer> current) {
        // Problem-specific validation logic
        return current.size() > 0; // Example condition
    }
    
    private static boolean isValidChoice(List<Integer> current, int choice) {
        // Problem-specific constraint checking
        return true; // Example
    }
    
    private static int getCurrentSum(List<Integer> current) {
        return current.stream().mapToInt(Integer::intValue).sum();
    }
    
    private static boolean isValidPlacement(char[][] board, int row, int col, char value) {
        // Problem-specific validation (e.g., Sudoku rules)
        return true; // Example
    }
    
    /**
     * INTERVIEW TIPS:
     * 
     * 1. ALWAYS explain your approach before coding
     * 2. Draw the recursion tree for complex problems
     * 3. Discuss time/space complexity
     * 4. Mention optimization opportunities (pruning, sorting)
     * 5. Handle edge cases (empty input, single element)
     * 6. Test with simple examples first
     */
    
    public static void main(String[] args) {
        System.out.println("Backtracking Fundamentals - Study this file thoroughly!");
        System.out.println("Practice the templates with different problems");
        System.out.println("Focus on understanding the choose-recurse-backtrack pattern");
    }
}
