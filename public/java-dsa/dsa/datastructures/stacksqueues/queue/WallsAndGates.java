package dsa.datastructures.stacksqueues.queue;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.Queue;

/*
 *   Author : Aritra
 *   LeetCode #286 - Walls and Gates (Medium - Premium)
 *   Problem: Fill each empty room with distance to nearest gate (Multi-source BFS)
 *   Time Complexity: O(m*n), Space Complexity: O(m*n)
 */

public class WallsAndGates {
    private static final int EMPTY = Integer.MAX_VALUE;
    private static final int GATE = 0;
    private static final int WALL = -1;
    
    public static void main(String[] args) {
        WallsAndGates solution = new WallsAndGates();
        
        int[][] rooms = {
            {EMPTY, WALL, GATE, EMPTY},
            {EMPTY, EMPTY, EMPTY, WALL},
            {EMPTY, WALL, EMPTY, WALL},
            {GATE, WALL, EMPTY, EMPTY}
        };
        
        System.out.println("Before:");
        printGrid(rooms);
        
        solution.wallsAndGates(rooms);
        
        System.out.println("\nAfter:");
        printGrid(rooms);
    }
    
    public void wallsAndGates(int[][] rooms) {
        if (rooms == null || rooms.length == 0) {
            return;
        }
        
        int rows = rooms.length;
        int cols = rooms[0].length;
        Queue<int[]> queue = new LinkedList<>();
        
        // Add all gates to queue (multi-source BFS)
        for (int i = 0; i < rows; i++) {
            for (int j = 0; j < cols; j++) {
                if (rooms[i][j] == GATE) {
                    queue.offer(new int[]{i, j});
                }
            }
        }
        
        int[][] directions = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        
        while (!queue.isEmpty()) {
            int[] pos = queue.poll();
            int row = pos[0];
            int col = pos[1];
            
            for (int[] dir : directions) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                // Only process empty rooms
                if (newRow >= 0 && newRow < rows && newCol >= 0 && newCol < cols 
                    && rooms[newRow][newCol] == EMPTY) {
                    rooms[newRow][newCol] = rooms[row][col] + 1;
                    queue.offer(new int[]{newRow, newCol});
                }
            }
        }
    }
    
    private static void printGrid(int[][] grid) {
        for (int[] row : grid) {
            for (int cell : row) {
                if (cell == Integer.MAX_VALUE) {
                    System.out.print("INF ");
                } else {
                    System.out.printf("%3d ", cell);
                }
            }
            System.out.println();
        }
    }
}

