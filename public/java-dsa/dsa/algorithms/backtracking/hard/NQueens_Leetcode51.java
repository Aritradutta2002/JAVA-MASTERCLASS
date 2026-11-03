package dsa.algorithms.backtracking.hard;

import java.util.*;

/**
 * N-QUEENS - LeetCode 51 (HARD)
 * 
 * Problem: The n-queens puzzle is the problem of placing n chess queens on an n×n 
 * chessboard such that no two queens attack each other. Given an integer n, 
 * return all distinct solutions to the n-queens puzzle.
 * 
 * Example:
 * Input: n = 4
 * Output: [[".Q..","...Q","Q...","..Q."],["..Q.","Q...","...Q",".Q.."]]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐⭐
 * - Classic backtracking problem asked by top companies
 * - Tests understanding of constraint satisfaction
 * - Excellent for discussing optimizations
 * 
 * Time Complexity: O(N!) - much better with optimizations
 * Space Complexity: O(N²) for the board
 */
public class NQueens_Leetcode51 {
    
    /**
     * APPROACH 1: BASIC BACKTRACKING WITH 2D BOARD
     * Clear and intuitive implementation
     */
    public List<List<String>> solveNQueens(int n) {
        List<List<String>> result = new ArrayList<>();
        char[][] board = new char[n][n];
        
        // Initialize board with dots
        for (int i = 0; i < n; i++) {
            Arrays.fill(board[i], '.');
        }
        
        backtrack(result, board, 0);
        return result;
    }
    
    private void backtrack(List<List<String>> result, char[][] board, int row) {
        // Base case: placed all queens
        if (row == board.length) {
            result.add(constructSolution(board));
            return;
        }
        
        // Try placing queen in each column of current row
        for (int col = 0; col < board.length; col++) {
            if (isValid(board, row, col)) {
                board[row][col] = 'Q';              // Choose
                backtrack(result, board, row + 1);  // Recurse
                board[row][col] = '.';              // Backtrack
            }
        }
    }
    
    private boolean isValid(char[][] board, int row, int col) {
        int n = board.length;
        
        // Check column
        for (int i = 0; i < row; i++) {
            if (board[i][col] == 'Q') {
                return false;
            }
        }
        
        // Check diagonal (top-left to bottom-right)
        for (int i = row - 1, j = col - 1; i >= 0 && j >= 0; i--, j--) {
            if (board[i][j] == 'Q') {
                return false;
            }
        }
        
        // Check anti-diagonal (top-right to bottom-left)
        for (int i = row - 1, j = col + 1; i >= 0 && j < n; i--, j++) {
            if (board[i][j] == 'Q') {
                return false;
            }
        }
        
        return true;
    }
    
    private List<String> constructSolution(char[][] board) {
        List<String> solution = new ArrayList<>();
        for (char[] row : board) {
            solution.add(new String(row));
        }
        return solution;
    }
    
    /**
     * APPROACH 2: OPTIMIZED WITH SETS FOR O(1) CONFLICT DETECTION
     * Use sets to track conflicts instead of scanning board
     */
    public List<List<String>> solveNQueensOptimized(int n) {
        List<List<String>> result = new ArrayList<>();
        Set<Integer> cols = new HashSet<>();
        Set<Integer> diag1 = new HashSet<>(); // row - col
        Set<Integer> diag2 = new HashSet<>(); // row + col
        
        backtrackOptimized(result, new ArrayList<>(), n, 0, cols, diag1, diag2);
        return result;
    }
    
    private void backtrackOptimized(List<List<String>> result,
                                   List<Integer> queens,
                                   int n,
                                   int row,
                                   Set<Integer> cols,
                                   Set<Integer> diag1,
                                   Set<Integer> diag2) {
        if (row == n) {
            result.add(constructSolutionFromQueens(queens, n));
            return;
        }
        
        for (int col = 0; col < n; col++) {
            // Check if placement is valid using sets
            if (cols.contains(col) || 
                diag1.contains(row - col) || 
                diag2.contains(row + col)) {
                continue;
            }
            
            // Place queen
            queens.add(col);
            cols.add(col);
            diag1.add(row - col);
            diag2.add(row + col);
            
            // Recurse
            backtrackOptimized(result, queens, n, row + 1, cols, diag1, diag2);
            
            // Backtrack
            queens.remove(queens.size() - 1);
            cols.remove(col);
            diag1.remove(row - col);
            diag2.remove(row + col);
        }
    }
    
    private List<String> constructSolutionFromQueens(List<Integer> queens, int n) {
        List<String> solution = new ArrayList<>();
        for (int queenCol : queens) {
            StringBuilder row = new StringBuilder();
            for (int col = 0; col < n; col++) {
                row.append(col == queenCol ? 'Q' : '.');
            }
            solution.add(row.toString());
        }
        return solution;
    }
    
    /**
     * APPROACH 3: BITWISE OPTIMIZATION (ADVANCED)
     * Use bitwise operations for ultimate performance
     */
    public List<List<String>> solveNQueensBitwise(int n) {
        List<List<String>> result = new ArrayList<>();
        backtrackBitwise(result, new ArrayList<>(), n, 0, 0, 0, 0);
        return result;
    }
    
    private void backtrackBitwise(List<List<String>> result,
                                 List<Integer> queens,
                                 int n,
                                 int row,
                                 int cols,
                                 int diag1,
                                 int diag2) {
        if (row == n) {
            result.add(constructSolutionFromQueens(queens, n));
            return;
        }
        
        // Available positions = positions not under attack
        int available = ((1 << n) - 1) & ~(cols | diag1 | diag2);
        
        while (available != 0) {
            // Get rightmost available position
            int pos = available & -available;
            available &= available - 1; // Remove this position
            
            int col = Integer.bitCount(pos - 1); // Convert position to column index
            queens.add(col);
            
            backtrackBitwise(result, queens, n, row + 1,
                           cols | pos,
                           (diag1 | pos) << 1,
                           (diag2 | pos) >> 1);
            
            queens.remove(queens.size() - 1);
        }
    }
    
    /**
     * APPROACH 4: COUNT SOLUTIONS ONLY (N-QUEENS II)
     * When we only need count, not actual solutions
     */
    public int totalNQueens(int n) {
        return countSolutions(0, 0, 0, 0, n);
    }
    
    private int countSolutions(int row, int cols, int diag1, int diag2, int n) {
        if (row == n) {
            return 1;
        }
        
        int count = 0;
        int available = ((1 << n) - 1) & ~(cols | diag1 | diag2);
        
        while (available != 0) {
            int pos = available & -available;
            available &= available - 1;
            
            count += countSolutions(row + 1,
                                  cols | pos,
                                  (diag1 | pos) << 1,
                                  (diag2 | pos) >> 1,
                                  n);
        }
        
        return count;
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Problem constraints:
     *    - No two queens in same row (guaranteed by our approach)
     *    - No two queens in same column
     *    - No two queens in same diagonal
     *    - No two queens in same anti-diagonal
     * 
     * 2. Optimization strategies:
     *    - Use sets for O(1) conflict detection
     *    - Bitwise operations for maximum performance
     *    - Early pruning when no valid positions available
     * 
     * 3. Mathematical insights:
     *    - Diagonal constraint: row - col is constant
     *    - Anti-diagonal constraint: row + col is constant
     *    - Solution count follows known sequence (1, 0, 0, 2, 10, 4, 40, 92...)
     * 
     * 4. Follow-up questions:
     *    - How many solutions exist for N queens? (Count only)
     *    - What if we want just one solution?
     *    - Can we solve it iteratively?
     *    - What about N-Rooks problem?
     * 
     * 5. Real-world applications:
     *    - Resource allocation with constraints
     *    - Scheduling problems
     *    - Graph coloring
     *    - Any constraint satisfaction problem
     */
    
    public static void main(String[] args) {
        NQueens_Leetcode51 solution = new NQueens_Leetcode51();
        
        // Test different values of N
        for (int n = 1; n <= 8; n++) {
            System.out.println("N = " + n);
            
            long start = System.nanoTime();
            List<List<String>> solutions = solution.solveNQueens(n);
            long end = System.nanoTime();
            
            System.out.println("Number of solutions: " + solutions.size());
            System.out.println("Time taken: " + (end - start) / 1000000.0 + " ms");
            
            if (n <= 4 && !solutions.isEmpty()) {
                System.out.println("First solution:");
                for (String row : solutions.get(0)) {
                    System.out.println(row);
                }
            }
            System.out.println();
        }
        
        // Performance comparison
        int testN = 8;
        System.out.println("Performance comparison for N = " + testN + ":");
        
        long start1 = System.nanoTime();
        List<List<String>> result1 = solution.solveNQueens(testN);
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        List<List<String>> result2 = solution.solveNQueensOptimized(testN);
        long end2 = System.nanoTime();
        
        long start3 = System.nanoTime();
        List<List<String>> result3 = solution.solveNQueensBitwise(testN);
        long end3 = System.nanoTime();
        
        long start4 = System.nanoTime();
        int count = solution.totalNQueens(testN);
        long end4 = System.nanoTime();
        
        System.out.println("Basic: " + (end1 - start1) / 1000000.0 + " ms");
        System.out.println("Optimized: " + (end2 - start2) / 1000000.0 + " ms");
        System.out.println("Bitwise: " + (end3 - start3) / 1000000.0 + " ms");
        System.out.println("Count only: " + (end4 - start4) / 1000000.0 + " ms");
        System.out.println("All give same count: " + 
                          (result1.size() == result2.size() && 
                           result2.size() == result3.size() && 
                           result3.size() == count));
    }
}
