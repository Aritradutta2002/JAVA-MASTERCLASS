package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 2290: Minimum Obstacle Removal to Reach Corner
 * 
 * Problem: You are given a 0-indexed 2D integer array grid of size m x n. Each cell has one of two values:
 * - 0 represents an empty cell
 * - 1 represents an obstacle that may be removed
 * You can move up, down, left, or right from and to an empty cell.
 * Return the minimum number of obstacles to remove so you can move from the upper left corner (0, 0) 
 * to the lower right corner (m - 1, n - 1).
 * 
 * Time Complexity: O(m * n * log(m * n))
 * Space Complexity: O(m * n)
 */
public class MinimumObstacleRemoval {
    
    private static final int[][] DIRECTIONS = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
    
    /**
     * Dijkstra's algorithm solution
     */
    public int minimumObstacles(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        // Priority queue: [cost, row, col]
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[0] - b[0]);
        boolean[][] visited = new boolean[m][n];
        
        pq.offer(new int[]{0, 0, 0});
        
        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int cost = current[0];
            int row = current[1];
            int col = current[2];
            
            if (row == m - 1 && col == n - 1) {
                return cost;
            }
            
            if (visited[row][col]) {
                continue;
            }
            visited[row][col] = true;
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n && !visited[newRow][newCol]) {
                    int newCost = cost + grid[newRow][newCol];
                    pq.offer(new int[]{newCost, newRow, newCol});
                }
            }
        }
        
        return -1; // Should never reach here
    }
    
    /**
     * 0-1 BFS solution (more efficient for this specific problem)
     */
    public int minimumObstaclesDeque(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        Deque<int[]> deque = new ArrayDeque<>();
        boolean[][] visited = new boolean[m][n];
        
        deque.offerFirst(new int[]{0, 0, 0}); // [row, col, cost]
        
        while (!deque.isEmpty()) {
            int[] current = deque.pollFirst();
            int row = current[0];
            int col = current[1];
            int cost = current[2];
            
            if (row == m - 1 && col == n - 1) {
                return cost;
            }
            
            if (visited[row][col]) {
                continue;
            }
            visited[row][col] = true;
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n && !visited[newRow][newCol]) {
                    if (grid[newRow][newCol] == 0) {
                        // No cost, add to front
                        deque.offerFirst(new int[]{newRow, newCol, cost});
                    } else {
                        // Cost 1, add to back
                        deque.offerLast(new int[]{newRow, newCol, cost + 1});
                    }
                }
            }
        }
        
        return -1;
    }
    
    /**
     * Alternative implementation with distance array
     */
    public int minimumObstaclesDistanceArray(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        int[][] dist = new int[m][n];
        for (int i = 0; i < m; i++) {
            Arrays.fill(dist[i], Integer.MAX_VALUE);
        }
        
        Deque<int[]> deque = new ArrayDeque<>();
        deque.offer(new int[]{0, 0});
        dist[0][0] = 0;
        
        while (!deque.isEmpty()) {
            int[] current = deque.poll();
            int row = current[0];
            int col = current[1];
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n) {
                    int newDist = dist[row][col] + grid[newRow][newCol];
                    
                    if (newDist < dist[newRow][newCol]) {
                        dist[newRow][newCol] = newDist;
                        
                        if (grid[newRow][newCol] == 0) {
                            deque.offerFirst(new int[]{newRow, newCol});
                        } else {
                            deque.offerLast(new int[]{newRow, newCol});
                        }
                    }
                }
            }
        }
        
        return dist[m - 1][n - 1];
    }
    
    /**
     * Binary search + BFS solution
     */
    public int minimumObstaclesBinarySearch(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        int left = 0, right = m * n;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (canReach(grid, mid)) {
                right = mid;
            } else {
                left = mid + 1;
            }
        }
        
        return left;
    }
    
    private boolean canReach(int[][] grid, int maxObstacles) {
        int m = grid.length;
        int n = grid[0].length;
        
        if (grid[0][0] > maxObstacles) return false;
        
        Queue<int[]> queue = new LinkedList<>();
        boolean[][] visited = new boolean[m][n];
        
        queue.offer(new int[]{0, 0, grid[0][0]});
        visited[0][0] = true;
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int row = current[0];
            int col = current[1];
            int obstacles = current[2];
            
            if (row == m - 1 && col == n - 1) {
                return true;
            }
            
            for (int[] dir : DIRECTIONS) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n && !visited[newRow][newCol]) {
                    int newObstacles = obstacles + grid[newRow][newCol];
                    
                    if (newObstacles <= maxObstacles) {
                        visited[newRow][newCol] = true;
                        queue.offer(new int[]{newRow, newCol, newObstacles});
                    }
                }
            }
        }
        
        return false;
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumObstacleRemoval solution = new MinimumObstacleRemoval();
        
        // Test case 1: [[0,1,1],[1,1,0],[1,1,0]]
        int[][] grid1 = {{0,1,1},{1,1,0},{1,1,0}};
        System.out.println("Min obstacles (Dijkstra): " + solution.minimumObstacles(grid1)); // 2
        
        // Test case 2: [[0,1,0,0,0],[0,1,0,1,0],[0,0,0,1,0]]
        int[][] grid2 = {{0,1,0,0,0},{0,1,0,1,0},{0,0,0,1,0}};
        System.out.println("Min obstacles (0-1 BFS): " + solution.minimumObstaclesDeque(grid2)); // 0
        
        System.out.println("Min obstacles (Distance Array): " + solution.minimumObstaclesDistanceArray(grid1)); // 2
        System.out.println("Min obstacles (Binary Search): " + solution.minimumObstaclesBinarySearch(grid1)); // 2
    }
}

