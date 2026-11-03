package dsa.algorithms.recursion.problems.hard;

/**
 * Tower of Hanoi problem solver using recursion.
 *
 * The Tower of Hanoi is a classic problem where we have three rods and n disks of different sizes
 * which can slide onto any rod. The puzzle starts with the disks in a neat stack in ascending order
 * of size on one rod, the smallest at the top, thus making a conical shape.
 *
 * The objective is to move the entire stack to another rod, obeying the following rules:
 * 1. Only one disk can be moved at a time.
 * 2. Each move consists of taking the upper disk from one of the stacks and placing it on top of another stack.
 * 3. No disk may be placed on top of a smaller disk.
 *
 * This implementation uses recursion to solve the problem and records all moves.
 *
 * @author Aritra Dutta
 * @created Sunday, 02.03.2025 12:18 am
 */
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class TowerOfHanoi {

    // List to store the sequence of moves
    private static final List<String> moves = new ArrayList<>();

    /**
     * Recursive function to solve Tower of Hanoi.
     *
     * @param n         Number of disks
     * @param from      Source rod
     * @param auxiliary Auxiliary rod
     * @param to        Destination rod
     */
    public static void towerOfHanoi(int n, int from, int auxiliary, int to) {
        if (n == 1) {
            moves.add(from + " " + to);
            return;
        }

        // Move n-1 disks from source to auxiliary using destination as helper
        towerOfHanoi(n - 1, from, to, auxiliary);

        // Move the nth disk from source to destination
        moves.add(from + " " + to);

        // Move n-1 disks from auxiliary to destination using source as helper
        towerOfHanoi(n - 1, auxiliary, from, to);
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.print("Enter the number of disks: ");
        int n = scanner.nextInt();
        scanner.close();

        // Solve Tower of Hanoi with rods labeled 1, 2, 3
        towerOfHanoi(n, 1, 2, 3);

        // Output the results
        System.out.println("Total moves: " + moves.size());
        System.out.println("Moves:");
        for (String move : moves) {
            System.out.println(move);
        }
    }

    // Additional classic recursion problems

    /**
     * Calculates the factorial of a number using recursion.
     * Factorial of n (n!) = n * (n-1) * ... * 1
     *
     * @param n the number to calculate factorial for
     * @return factorial of n
     */
    public static long factorial(int n) {
        if (n == 0 || n == 1) {
            return 1;
        }
        return n * factorial(n - 1);
    }

    /**
     * Calculates the nth Fibonacci number using recursion.
     * Fibonacci sequence: 0, 1, 1, 2, 3, 5, 8, ...
     *
     * @param n the position in Fibonacci sequence
     * @return nth Fibonacci number
     */
    public static int fibonacci(int n) {
        if (n <= 1) {
            return n;
        }
        return fibonacci(n - 1) + fibonacci(n - 2);
    }

    /**
     * Calculates the sum of digits of a number using recursion.
     *
     * @param n the number
     * @return sum of digits
     */
    public static int sumOfDigits(int n) {
        if (n == 0) {
            return 0;
        }
        return (n % 10) + sumOfDigits(n / 10);
    }

    /**
     * Reverses a number using recursion.
     *
     * @param n the number to reverse
     * @return reversed number
     */
    public static int reverseNumber(int n) {
        return reverseHelper(n, 0);
    }

    private static int reverseHelper(int n, int reversed) {
        if (n == 0) {
            return reversed;
        }
        return reverseHelper(n / 10, reversed * 10 + n % 10);
    }

    /**
     * Performs binary search on a sorted array using recursion.
     *
     * @param arr the sorted array
     * @param target the element to search for
     * @param low the low index
     * @param high the high index
     * @return index of target if found, -1 otherwise
     */
    public static int binarySearch(int[] arr, int target, int low, int high) {
        if (low > high) {
            return -1;
        }
        int mid = low + (high - low) / 2;
        if (arr[mid] == target) {
            return mid;
        } else if (arr[mid] > target) {
            return binarySearch(arr, target, low, mid - 1);
        } else {
            return binarySearch(arr, target, mid + 1, high);
        }
    }

    /**
     * Generates all permutations of a string using recursion.
     *
     * @param str the input string
     * @param l the starting index
     * @param r the ending index
     */
    public static void permute(String str, int l, int r) {
        if (l == r) {
            System.out.println(str);
        } else {
            for (int i = l; i <= r; i++) {
                str = swap(str, l, i);
                permute(str, l + 1, r);
                str = swap(str, l, i); // backtrack
            }
        }
    }

    private static String swap(String str, int i, int j) {
        char[] charArray = str.toCharArray();
        char temp = charArray[i];
        charArray[i] = charArray[j];
        charArray[j] = temp;
        return String.valueOf(charArray);
    }

    /**
     * Generates all subsets of a set using recursion.
     *
     * @param set the input set
     * @param index the current index
     * @param current the current subset
     */
    public static void generateSubsets(int[] set, int index, List<Integer> current) {
        if (index == set.length) {
            System.out.println(current);
            return;
        }
        // Include the current element
        current.add(set[index]);
        generateSubsets(set, index + 1, current);
        // Exclude the current element (backtrack)
        current.remove(current.size() - 1);
        generateSubsets(set, index + 1, current);
    }

    // More advanced recursion problems

    /**
     * Solves the N-Queens problem using backtracking recursion.
     * Places N queens on an N x N chessboard so that no two queens attack each other.
     *
     * @param n the number of queens and board size
     */
    public static void solveNQueens(int n) {
        int[][] board = new int[n][n];
        if (solveNQueensUtil(board, 0, n)) {
            printBoard(board);
        } else {
            System.out.println("Solution does not exist");
        }
    }

    private static boolean solveNQueensUtil(int[][] board, int col, int n) {
        if (col >= n) {
            return true;
        }
        for (int i = 0; i < n; i++) {
            if (isSafe(board, i, col, n)) {
                board[i][col] = 1;
                if (solveNQueensUtil(board, col + 1, n)) {
                    return true;
                }
                board[i][col] = 0; // backtrack
            }
        }
        return false;
    }

    private static boolean isSafe(int[][] board, int row, int col, int n) {
        // Check row on left side
        for (int i = 0; i < col; i++) {
            if (board[row][i] == 1) {
                return false;
            }
        }
        // Check upper diagonal on left side
        for (int i = row, j = col; i >= 0 && j >= 0; i--, j--) {
            if (board[i][j] == 1) {
                return false;
            }
        }
        // Check lower diagonal on left side
        for (int i = row, j = col; j >= 0 && i < n; i++, j--) {
            if (board[i][j] == 1) {
                return false;
            }
        }
        return true;
    }

    private static void printBoard(int[][] board) {
        for (int[] row : board) {
            for (int cell : row) {
                System.out.print(cell + " ");
            }
            System.out.println();
        }
    }

    /**
     * Solves the Rat in a Maze problem using backtracking recursion.
     * Finds a path from (0,0) to (n-1,n-1) in a maze where 1 is path and 0 is wall.
     *
     * @param maze the maze grid
     * @param n the size of the maze
     */
    public static void solveRatInMaze(int[][] maze, int n) {
        int[][] solution = new int[n][n];
        if (solveRatInMazeUtil(maze, 0, 0, solution, n)) {
            printBoard(solution);
        } else {
            System.out.println("Solution does not exist");
        }
    }

    private static boolean solveRatInMazeUtil(int[][] maze, int x, int y, int[][] solution, int n) {
        if (x == n - 1 && y == n - 1 && maze[x][y] == 1) {
            solution[x][y] = 1;
            return true;
        }
        if (isSafeMaze(maze, x, y, n)) {
            solution[x][y] = 1;
            // Move right
            if (solveRatInMazeUtil(maze, x, y + 1, solution, n)) {
                return true;
            }
            // Move down
            if (solveRatInMazeUtil(maze, x + 1, y, solution, n)) {
                return true;
            }
            // Backtrack
            solution[x][y] = 0;
            return false;
        }
        return false;
    }

    private static boolean isSafeMaze(int[][] maze, int x, int y, int n) {
        return (x >= 0 && x < n && y >= 0 && y < n && maze[x][y] == 1);
    }

    /**
     * Solves the Subset Sum problem using recursion.
     * Determines if there is a subset of the given set with sum equal to given sum.
     *
     * @param set the input set
     * @param n the size of set
     * @param sum the target sum
     * @return true if subset with given sum exists
     */
    public static boolean subsetSum(int[] set, int n, int sum) {
        if (sum == 0) {
            return true;
        }
        if (n == 0) {
            return false;
        }
        if (set[n - 1] > sum) {
            return subsetSum(set, n - 1, sum);
        }
        return subsetSum(set, n - 1, sum) || subsetSum(set, n - 1, sum - set[n - 1]);
    }

    /**
     * Calculates the Greatest Common Divisor (GCD) using recursive Euclidean algorithm.
     *
     * @param a first number
     * @param b second number
     * @return GCD of a and b
     */
    public static int gcd(int a, int b) {
        if (b == 0) {
            return a;
        }
        return gcd(b, a % b);
    }

    /**
     * Solves Sudoku using backtracking recursion.
     * Fills the board with digits 1-9 such that each row, column, and 3x3 subgrid contains each digit exactly once.
     *
     * @param board the 9x9 Sudoku board
     * @return true if solvable
     */
    public static boolean solveSudoku(char[][] board) {
        for (int row = 0; row < 9; row++) {
            for (int col = 0; col < 9; col++) {
                if (board[row][col] == '.') {
                    for (char num = '1'; num <= '9'; num++) {
                        if (isValidSudoku(board, row, col, num)) {
                            board[row][col] = num;
                            if (solveSudoku(board)) {
                                return true;
                            }
                            board[row][col] = '.'; // backtrack
                        }
                    }
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean isValidSudoku(char[][] board, int row, int col, char num) {
        for (int i = 0; i < 9; i++) {
            if (board[row][i] == num || board[i][col] == num) {
                return false;
            }
        }
        int startRow = row - row % 3;
        int startCol = col - col % 3;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (board[startRow + i][startCol + j] == num) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Prints the Sudoku board.
     *
     * @param board the 9x9 board
     */
    public static void printSudokuBoard(char[][] board) {
        for (int i = 0; i < 9; i++) {
            for (int j = 0; j < 9; j++) {
                System.out.print(board[i][j] + " ");
            }
            System.out.println();
        }
    }
}
