package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1368: Minimum Cost to Make at Least One Valid Path in a Grid
 * 
 * Problem: Given an m x n grid. Each cell of the grid has a sign pointing to the next cell you should visit if you are
 * currently in this cell. The sign of grid[i][j] can be:
 * 1 which means go to the cell to the right (grid[i][j + 1])
 * 2 which means go to the cell to the left (grid[i][j - 1])
 * 3 which means go to the lower cell (grid[i + 1][j])
 * 4 which means go to the upper cell (grid[i - 1][j])
 * 
 * You will initially start at the upper left cell (0, 0). A valid path in the grid is a path that starts from the
 * upper left cell (0, 0) and ends at the bottom-right cell (m - 1, n - 1) following the signs on the grid.
 * You can modify the sign on a cell with cost = 1. You can modify the sign on a cell one time only.
 * Return the minimum cost to make the grid have at least one valid path.
 * 
 * Time Complexity: O(m * n)
 * Space Complexity: O(m * n)
 */
public class MinimumCostValidPath {
    
    private static final int[][] DIRECTIONS = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
    
    /**
     * 0-1 BFS solution using deque
     */
    public int minCost(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        int[][] cost = new int[m][n];
        for (int i = 0; i < m; i++) {
            Arrays.fill(cost[i], Integer.MAX_VALUE);
        }
        
        Deque<int[]> deque = new ArrayDeque<>();
        deque.offer(new int[]{0, 0});
        cost[0][0] = 0;
        
        while (!deque.isEmpty()) {
            int[] current = deque.poll();
            int row = current[0];
            int col = current[1];
            
            if (row == m - 1 && col == n - 1) {
                return cost[row][col];
            }
            
            // Try all 4 directions
            for (int dir = 0; dir < 4; dir++) {
                int newRow = row + DIRECTIONS[dir][0];
                int newCol = col + DIRECTIONS[dir][1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n) {
                    // Cost is 0 if we follow the current direction, 1 if we change it
                    int newCost = cost[row][col] + (grid[row][col] == dir + 1 ? 0 : 1);
                    
                    if (newCost < cost[newRow][newCol]) {
                        cost[newRow][newCol] = newCost;
                        
                        if (grid[row][col] == dir + 1) {
                            // No cost, add to front
                            deque.offerFirst(new int[]{newRow, newCol});
                        } else {
                            // Cost 1, add to back
                            deque.offerLast(new int[]{newRow, newCol});
                        }
                    }
                }
            }
        }
        
        return cost[m - 1][n - 1];
    }
    
    /**
     * Dijkstra's algorithm solution
     */
    public int minCostDijkstra(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        int[][] cost = new int[m][n];
        for (int i = 0; i < m; i++) {
            Arrays.fill(cost[i], Integer.MAX_VALUE);
        }
        
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[2] - b[2]);
        pq.offer(new int[]{0, 0, 0});
        cost[0][0] = 0;
        
        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            int row = current[0];
            int col = current[1];
            int currentCost = current[2];
            
            if (row == m - 1 && col == n - 1) {
                return currentCost;
            }
            
            if (currentCost > cost[row][col]) {
                continue;
            }
            
            for (int dir = 0; dir < 4; dir++) {
                int newRow = row + DIRECTIONS[dir][0];
                int newCol = col + DIRECTIONS[dir][1];
                
                if (newRow >= 0 && newRow < m && newCol >= 0 && newCol < n) {
                    int newCost = currentCost + (grid[row][col] == dir + 1 ? 0 : 1);
                    
                    if (newCost < cost[newRow][newCol]) {
                        cost[newRow][newCol] = newCost;
                        pq.offer(new int[]{newRow, newCol, newCost});
                    }
                }
            }
        }
        
        return cost[m - 1][n - 1];
    }
    
    /**
     * BFS with modification tracking
     */
    public int minCostBFS(int[][] grid) {
        int m = grid.length;
        int n = grid[0].length;
        
        Queue<int[]> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(new int[]{0, 0, 0}); // row, col, cost
        visited.add("0,0");
        
        int cost = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                int[] current = queue.poll();
                int row = current[0];
                int col = current[1];
                
                if (row == m - 1 && col == n - 1) {
                    return cost;
                }
                
                // Follow the natural direction first (cost 0)
                dfs(grid, row, col, queue, visited);
            }
            
            cost++;
        }
        
        return -1;
    }
    
    private void dfs(int[][] grid, int row, int col, Queue<int[]> queue, Set<String> visited) {
        if (row < 0 || row >= grid.length || col < 0 || col >= grid[0].length || 
            visited.contains(row + "," + col)) {
            return;
        }
        
        visited.add(row + "," + col);
        
        // Follow natural direction with cost 0
        int dir = grid[row][col] - 1;
        int newRow = row + DIRECTIONS[dir][0];
        int newCol = col + DIRECTIONS[dir][1];
        
        if (newRow >= 0 && newRow < grid.length && newCol >= 0 && newCol < grid[0].length &&
            !visited.contains(newRow + "," + newCol)) {
            dfs(grid, newRow, newCol, queue, visited);
        }
        
        // Add all other directions to queue for next level (cost 1)
        for (int i = 0; i < 4; i++) {
            if (i != dir) {
                int nextRow = row + DIRECTIONS[i][0];
                int nextCol = col + DIRECTIONS[i][1];
                
                if (nextRow >= 0 && nextRow < grid.length && nextCol >= 0 && nextCol < grid[0].length &&
                    !visited.contains(nextRow + "," + nextCol)) {
                    queue.offer(new int[]{nextRow, nextCol});
                }
            }
        }
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumCostValidPath solution = new MinimumCostValidPath();
        
        // Test case 1: [[1,1,1,1],[2,2,2,2],[1,1,1,1],[2,2,2,2]]
        int[][] grid1 = {{1,1,1,1},{2,2,2,2},{1,1,1,1},{2,2,2,2}};
        System.out.println("Min cost (0-1 BFS): " + solution.minCost(grid1)); // 3
        
        // Test case 2: [[1,1,3],[3,2,2],[1,1,4]]
        int[][] grid2 = {{1,1,3},{3,2,2},{1,1,4}};
        System.out.println("Min cost (Dijkstra): " + solution.minCostDijkstra(grid2)); // 0
        
        System.out.println("Min cost (BFS): " + solution.minCostBFS(grid1)); // 3
    }
}

