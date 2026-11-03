package dsa.algorithms.backtracking.gametheory;
/*
 Problem: Tic-Tac-Toe (Minimax + Alpha-Beta)
 Approach:
   - Classic adversarial backtracking using Minimax with Alpha-Beta pruning.
   - Evaluation:
       * +10 - depth: AI (maximizer) has a winning line (prefer faster wins).
       * -10 + depth: Human (minimizer) has a winning line (prefer slower losses).
       * 0: Draw/terminal with no winner.
   - Move generation over empty cells; recursion alternates players.
   - Demonstration in main(): shows best moves for sample boards.

 Time Complexity:
   - O(b^d) in general where b is branching factor (~ available cells), d is depth (<= 9).
   - Alpha-Beta pruning improves practical performance significantly.

 Space:
   - O(d) recursion depth + board state (3x3).
*/
import java.util.Arrays;

public class TicTacToe_Minimax {

    private static final int N = 3;
    private final char[][] board;
    private final char ai;     // Maximizer player symbol ('X' or 'O')
    private final char human;  // Minimizer player symbol

    public TicTacToe_Minimax(char ai) {
        this.ai = ai;
        this.human = (ai == 'X') ? 'O' : 'X';
        this.board = new char[N][N];
        for (int i = 0; i < N; i++) {
            Arrays.fill(this.board[i], ' ');
        }
    }

    public TicTacToe_Minimax() {
        this('X'); // Default AI is 'X'
    }

    // Returns true if there are moves left on the board
    private boolean isMovesLeft() {
        for (int r = 0; r < N; r++) {
            for (int c = 0; c < N; c++) {
                if (board[r][c] == ' ') return true;
            }
        }
        return false;
    }

    // Returns 'X' or 'O' if someone won; otherwise '\0'
    private char checkWinner() {
        // Rows
        for (int r = 0; r < N; r++) {
            if (board[r][0] != ' ' && board[r][0] == board[r][1] && board[r][1] == board[r][2]) {
                return board[r][0];
            }
        }
        // Columns
        for (int c = 0; c < N; c++) {
            if (board[0][c] != ' ' && board[0][c] == board[1][c] && board[1][c] == board[2][c]) {
                return board[0][c];
            }
        }
        // Diagonals
        if (board[0][0] != ' ' && board[0][0] == board[1][1] && board[1][1] == board[2][2]) {
            return board[0][0];
        }
        if (board[0][2] != ' ' && board[0][2] == board[1][1] && board[1][1] == board[2][0]) {
            return board[0][2];
        }
        return '\0';
    }

    // Minimax with Alpha-Beta pruning
    private int minimax(int depth, boolean isMax, int alpha, int beta) {
        char winner = checkWinner();
        if (winner == ai) {
            return 10 - depth; // faster wins are better
        } else if (winner == human) {
            return -10 + depth; // slower losses are better
        } else if (!isMovesLeft()) {
            return 0; // draw
        }

        if (isMax) {
            int best = Integer.MIN_VALUE;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (board[r][c] == ' ') {
                        board[r][c] = ai;
                        int val = minimax(depth + 1, false, alpha, beta);
                        board[r][c] = ' ';
                        best = Math.max(best, val);
                        alpha = Math.max(alpha, best);
                        if (alpha >= beta) return best; // beta cut-off
                    }
                }
            }
            return best;
        } else {
            int best = Integer.MAX_VALUE;
            for (int r = 0; r < N; r++) {
                for (int c = 0; c < N; c++) {
                    if (board[r][c] == ' ') {
                        board[r][c] = human;
                        int val = minimax(depth + 1, true, alpha, beta);
                        board[r][c] = ' ';
                        best = Math.min(best, val);
                        beta = Math.min(beta, best);
                        if (alpha >= beta) return best; // alpha cut-off
                    }
                }
            }
            return best;
        }
    }

    // Finds the best move for AI; returns {row, col}
    public int[] findBestMove() {
        int bestVal = Integer.MIN_VALUE;
        int[] bestMove = {-1, -1};

        // Simple move ordering heuristic: center, corners, edges
        int[][] order = {
                {1,1}, {0,0}, {0,2}, {2,0}, {2,2}, {0,1}, {1,0}, {1,2}, {2,1}
        };

        for (int[] cell : order) {
            int r = cell[0], c = cell[1];
            if (board[r][c] == ' ') {
                board[r][c] = ai;
                int moveVal = minimax(0, false, Integer.MIN_VALUE, Integer.MAX_VALUE);
                board[r][c] = ' ';
                if (moveVal > bestVal) {
                    bestVal = moveVal;
                    bestMove[0] = r;
                    bestMove[1] = c;
                }
            }
        }
        return bestMove;
    }

    public boolean makeMove(int r, int c, char player) {
        if (r < 0 || r >= N || c < 0 || c >= N) return false;
        if (board[r][c] != ' ') return false;
        board[r][c] = player;
        return true;
    }

    public void printBoard() {
        System.out.println("-------------");
        for (int r = 0; r < N; r++) {
            System.out.print("|");
            for (int c = 0; c < N; c++) {
                System.out.print(" " + board[r][c] + " |");
            }
            System.out.println();
            System.out.println("-------------");
        }
    }

    public static void main(String[] args) {
        // Demo 1: Empty board, AI = 'X'
        TicTacToe_Minimax game1 = new TicTacToe_Minimax('X');
        System.out.println("Demo 1: Empty board, AI = 'X'");
        game1.printBoard();
        int[] mv1 = game1.findBestMove();
        System.out.println("Best move for X on empty board: " + Arrays.toString(mv1));
        game1.makeMove(mv1[0], mv1[1], 'X');
        game1.printBoard();

        // Demo 2: Mid-game, AI = 'O'
        TicTacToe_Minimax game2 = new TicTacToe_Minimax('O');
        System.out.println("Demo 2: Mid-game, AI = 'O'");
        game2.makeMove(0, 0, 'X');
        game2.makeMove(1, 1, 'O');
        game2.makeMove(0, 1, 'X');
        game2.printBoard();
        int[] mv2 = game2.findBestMove();
        System.out.println("Best move for O: " + Arrays.toString(mv2));
        game2.makeMove(mv2[0], mv2[1], 'O');
        game2.printBoard();

        // You can continue playing by alternating moves and calling findBestMove() for AI.
    }
}
