package dsa.algorithms.recursion.problems.hard;

/**
 * WordSearch - Determine if a word exists in a 2D board.
 *
 * Interview Angles:
 * - Depth-first search with backtracking and visited state tracking
 * - Highlights pruning and boundary checks
 * - Good segue into variations like finding all words (Trie + DFS)
 */
public class WordSearch {

    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    public static boolean exist(char[][] board, String word) {
        int rows = board.length;
        int cols = board[0].length;
        boolean[][] visited = new boolean[rows][cols];

        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (board[r][c] == word.charAt(0) && dfs(board, word, r, c, 0, visited)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean dfs(char[][] board, String word, int row, int col, int index, boolean[][] visited) {
        if (index == word.length()) {
            return true;
        }
        if (row < 0 || row >= board.length || col < 0 || col >= board[0].length) {
            return false;
        }
        if (visited[row][col] || board[row][col] != word.charAt(index)) {
            return false;
        }

        visited[row][col] = true;
        for (int[] dir : DIRECTIONS) {
            int nextRow = row + dir[0];
            int nextCol = col + dir[1];
            if (dfs(board, word, nextRow, nextCol, index + 1, visited)) {
                visited[row][col] = false; // backtrack before returning
                return true;
            }
        }
        visited[row][col] = false; // backtrack
        return false;
    }

    public static void main(String[] args) {
        char[][] board = {
                {'A', 'B', 'C', 'E'},
                {'S', 'F', 'C', 'S'},
                {'A', 'D', 'E', 'E'}
        };
        String word = "ABCCED";
        System.out.println("=== Word Search ===");
        System.out.println("Exists? " + exist(board, word));
    }
}


