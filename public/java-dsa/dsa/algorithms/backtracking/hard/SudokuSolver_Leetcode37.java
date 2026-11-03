package dsa.algorithms.backtracking.hard;

import java.util.ArrayList;
import java.util.List;

/**
 * SUDOKU SOLVER - LeetCode 37 (HARD)
 *
 * Problem: Fill the 9x9 board so that each row, column and 3x3 sub-box contains digits 1-9 exactly once.
 *
 * Approach (Interview-ready):
 * - Backtracking with O(1) validity checks using bitmasks for rows, cols, and boxes.
 * - Pre-collect empty cells to minimize scanning.
 * - Try digits using bit operations (iterate set bits).
 *
 * Complexity:
 * - Worst-case exponential, but the pruning with masks is very fast in practice.
 * - Space: O(1) masks + recursion depth up to number of empties.
 */
public class SudokuSolver_Leetcode37 {

    // Bit masks for constraints (bit k means digit (k+1) is used)
    private int[] rowMask = new int[9];
    private int[] colMask = new int[9];
    private int[] boxMask = new int[9];
    private final List<int[]> empties = new ArrayList<>();

    /**
     * Solves the Sudoku in-place.
     */
    public void solveSudoku(char[][] board) {
        initialize(board);
        dfs(board, 0);
    }

    // Initialize masks and empty cell list
    private void initialize(char[][] board) {
        for (int r = 0; r < 9; r++) {
            for (int c = 0; c < 9; c++) {
                char ch = board[r][c];
                if (ch == '.') {
                    empties.add(new int[]{r, c});
                } else {
                    int d = ch - '0';              // 1..9
                    int bit = 1 << (d - 1);        // 0..8 bit
                    int b = boxIndex(r, c);
                    rowMask[r] |= bit;
                    colMask[c] |= bit;
                    boxMask[b] |= bit;
                }
            }
        }
    }

    // Depth-first search over empty cells
    private boolean dfs(char[][] board, int idx) {
        if (idx == empties.size()) {
            return true; // solved
        }

        int r = empties.get(idx)[0];
        int c = empties.get(idx)[1];
        int b = boxIndex(r, c);

        // bits available are those not used in row/col/box
        int used = rowMask[r] | colMask[c] | boxMask[b];
        int avail = (~used) & 0x1FF; // only low 9 bits relevant

        while (avail != 0) {
            int bit = avail & -avail;               // pick lowest available digit
            int d = Integer.numberOfTrailingZeros(bit) + 1; // 1..9

            place(r, c, b, bit, (char) ('0' + d), board);

            if (dfs(board, idx + 1)) return true;

            remove(r, c, b, bit, board);

            avail &= avail - 1; // remove picked bit
        }
        return false;
    }

    private void place(int r, int c, int b, int bit, char ch, char[][] board) {
        board[r][c] = ch;
        rowMask[r] |= bit;
        colMask[c] |= bit;
        boxMask[b] |= bit;
    }

    private void remove(int r, int c, int b, int bit, char[][] board) {
        board[r][c] = '.';
        rowMask[r] &= ~bit;
        colMask[c] &= ~bit;
        boxMask[b] &= ~bit;
    }

    private int boxIndex(int r, int c) {
        return (r / 3) * 3 + (c / 3);
    }

    // Utility: pretty print board
    private static void printBoard(char[][] board) {
        for (int r = 0; r < 9; r++) {
            if (r % 3 == 0 && r != 0) System.out.println("------+-------+------");
            for (int c = 0; c < 9; c++) {
                if (c % 3 == 0 && c != 0) System.out.print("| ");
                System.out.print(board[r][c] + " ");
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        char[][] board = {
                {'5','3','.','.','7','.','.','.','.'},
                {'6','.','.','1','9','5','.','.','.'},
                {'.','9','8','.','.','.','.','6','.'},
                {'8','.','.','.','6','.','.','.','3'},
                {'4','.','.','8','.','3','.','.','1'},
                {'7','.','.','.','2','.','.','.','6'},
                {'.','6','.','.','.','.','2','8','.'},
                {'.','.','.','4','1','9','.','.','5'},
                {'.','.','.','.','8','.','.','7','9'}
        };

        SudokuSolver_Leetcode37 solver = new SudokuSolver_Leetcode37();
        solver.solveSudoku(board);

        System.out.println("Solved Sudoku:");
        printBoard(board);
    }
}
