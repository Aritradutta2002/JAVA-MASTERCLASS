package dsa.datastructures.stacksqueues.queue;

import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #909 - Snakes and Ladders (Medium)
 *   Problem: Find minimum moves to reach end of board (BFS)
 *   Time Complexity: O(n^2), Space Complexity: O(n^2)
 */

public class SnakesAndLadders {
    public static void main(String[] args) {
        SnakesAndLadders solution = new SnakesAndLadders();
        
        int[][] board1 = {
            {-1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1, -1},
            {-1, 35, -1, -1, 13, -1},
            {-1, -1, -1, -1, -1, -1},
            {-1, 15, -1, -1, -1, -1}
        };
        System.out.println("Minimum moves: " + solution.snakesAndLadders(board1));
        // Expected: 4
        
        int[][] board2 = {
            {-1, -1},
            {-1, 3}
        };
        System.out.println("Minimum moves: " + solution.snakesAndLadders(board2));
        // Expected: 1
    }
    
    public int snakesAndLadders(int[][] board) {
        int n = board.length;
        int target = n * n;
        
        boolean[] visited = new boolean[target + 1];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(1);
        visited[1] = true;
        int moves = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int curr = queue.poll();
                
                if (curr == target) {
                    return moves;
                }
                
                // Try all 6 dice rolls
                for (int dice = 1; dice <= 6; dice++) {
                    int next = curr + dice;
                    
                    if (next > target) {
                        break;
                    }
                    
                    // Get actual position (considering snakes/ladders)
                    int[] pos = getCoordinates(next, n);
                    int row = pos[0];
                    int col = pos[1];
                    
                    if (board[row][col] != -1) {
                        next = board[row][col];
                    }
                    
                    if (!visited[next]) {
                        visited[next] = true;
                        queue.offer(next);
                    }
                }
            }
            moves++;
        }
        
        return -1;
    }
    
    private int[] getCoordinates(int square, int n) {
        square--; // Convert to 0-indexed
        int row = n - 1 - (square / n);
        int col = square % n;
        
        // Alternate rows go right to left
        if ((n - 1 - row) % 2 == 1) {
            col = n - 1 - col;
        }
        
        return new int[]{row, col};
    }
}

