package dsa.algorithms.recursion.problems.hard;
import java.lang.*;
@SuppressWarnings("unused")

public class SudokuSolver {
    public static void main(String[] args) {

        char[][] board1 = {
            {'5', '3', '.', '.', '7', '.', '.', '.', '.'},
            {'6', '.', '.', '1', '9', '5', '.', '.', '.'},
            {'.', '9', '8', '.', '.', '.', '.', '6', '.'},
            {'8', '.', '.', '.', '6', '.', '.', '.', '3'},
            {'4', '.', '.', '8', '.', '3', '.', '.', '1'},
            {'7', '.', '.', '.', '2', '.', '.', '.', '6'},
            {'.', '6', '.', '.', '.', '.', '2', '8', '.'},
            {'.', '.', '.', '4', '1', '9', '.', '.', '5'},
            {'.', '.', '.', '.', '8', '.', '.', '7', '9'}
        };

        char[][] board2 = {
            {'.', '2', '.', '6', '.', '8', '.', '.', '.'},
            {'5', '8', '.', '.', '.', '9', '7', '.', '.'},
            {'.', '.', '.', '.', '4', '.', '.', '.', '.'},
            {'3', '7', '.', '.', '.', '.', '5', '.', '.'},
            {'6', '.', '.', '.', '.', '.', '.', '.', '4'},
            {'.', '.', '8', '.', '.', '.', '.', '1', '3'},
            {'.', '.', '.', '.', '2', '.', '.', '.', '.'},
            {'.', '.', '9', '8', '.', '.', '.', '3', '6'},
            {'.', '.', '.', '3', '.', '6', '.', '9', '.'}
        };

        System.out.println("Test Case 1:");
        System.out.println("Initial Board:");
        printBoard(board1);

        if (solveSudoku(board1)) {
            System.out.println("\nSolved Board:");
            printBoard(board1);
        } else {
            System.out.println("No solution found");
        }

        System.out.println("\nTest Case 2:");
        System.out.println("Initial Board:");
        printBoard(board2);

        if (solveSudoku(board2)) {
            System.out.println("\nSolved Board:");
            printBoard(board2);
        } else {
            System.out.println("No solution found");
        }
    }

    public static void printBoard(char[][] board) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
    }

    public static boolean solveSudoku(char[][] board) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                if (board[i][j] == '.') {
                    for (char c = '1'; c <= '9'; c++) {
                        if (isValid(board, i, j, c)) {
                            board[i][j] = c;
                            if (solveSudoku(board)) {
                                return true;
                            } else {
                                board[i][j] = '.';
                            }
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    public static boolean isValid(char[][] board, int row, int col, char num) {
        for (int i = 0; i < 9; i++) {
            if (board[i][col] == num) {
                return false;
            }
            if (board[row][i] == num) {
                return false;
            }
        }

        int startRow = row - row % 3;
        int startCol = col - col % 3;

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[i + startRow][j + startCol] == num) {
                    return false;
                }
            }
        }
        return true;
    }
}

