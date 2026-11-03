package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 847: Shortest Path Visiting All Nodes
 * 
 * Problem: You have an undirected, connected graph of n nodes labeled from 0 to n - 1. 
 * You are given an array graph where graph[i] is a list of all the nodes connected with node i by an edge.
 * Return the length of the shortest path that visits every node. You may start and stop at any node, 
 * you may revisit nodes multiple times, and you may reuse edges.
 * 
 * Time Complexity: O(n^2 * 2^n)
 * Space Complexity: O(n * 2^n)
 */
public class ShortestPathVisitingAllNodes {
    
    /**
     * BFS with bitmask to track visited nodes
     */
    public int shortestPathLength(int[][] graph) {
        int n = graph.length;
        if (n == 1) return 0;
        
        int targetMask = (1 << n) - 1; // All nodes visited
        
        // Queue stores [node, mask, distance]
        Queue<int[]> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        // Start from every node
        for (int i = 0; i < n; i++) {
            int mask = 1 << i;
            queue.offer(new int[]{i, mask, 0});
            visited.add(i + "," + mask);
        }
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int node = current[0];
            int mask = current[1];
            int dist = current[2];
            
            // Explore neighbors
            for (int neighbor : graph[node]) {
                int newMask = mask | (1 << neighbor);
                
                // If we've visited all nodes, return distance
                if (newMask == targetMask) {
                    return dist + 1;
                }
                
                String state = neighbor + "," + newMask;
                if (!visited.contains(state)) {
                    visited.add(state);
                    queue.offer(new int[]{neighbor, newMask, dist + 1});
                }
            }
        }
        
        return -1; // Should never reach here
    }
    
    /**
     * Dynamic Programming solution
     */
    public int shortestPathLengthDP(int[][] graph) {
        int n = graph.length;
        if (n == 1) return 0;
        
        int targetMask = (1 << n) - 1;
        
        // dp[mask][i] = minimum distance to reach state where mask nodes are visited and we end at node i
        int[][] dp = new int[1 << n][n];
        
        // Initialize with infinity
        for (int i = 0; i < (1 << n); i++) {
            Arrays.fill(dp[i], Integer.MAX_VALUE);
        }
        
        // Base case: start from each node
        for (int i = 0; i < n; i++) {
            dp[1 << i][i] = 0;
        }
        
        for (int mask = 0; mask < (1 << n); mask++) {
            for (int u = 0; u < n; u++) {
                if (dp[mask][u] == Integer.MAX_VALUE) continue;
                
                for (int v : graph[u]) {
                    int newMask = mask | (1 << v);
                    dp[newMask][v] = Math.min(dp[newMask][v], dp[mask][u] + 1);
                }
            }
        }
        
        int result = Integer.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            result = Math.min(result, dp[targetMask][i]);
        }
        
        return result;
    }
    
    /**
     * Optimized BFS with 2D visited array
     */
    public int shortestPathLengthOptimized(int[][] graph) {
        int n = graph.length;
        if (n == 1) return 0;
        
        int targetMask = (1 << n) - 1;
        
        // visited[node][mask] = true if we've been to node with this mask
        boolean[][] visited = new boolean[n][1 << n];
        
        Queue<int[]> queue = new LinkedList<>();
        
        // Start from every node
        for (int i = 0; i < n; i++) {
            queue.offer(new int[]{i, 1 << i, 0});
            visited[i][1 << i] = true;
        }
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int node = current[0];
            int mask = current[1];
            int dist = current[2];
            
            if (mask == targetMask) {
                return dist;
            }
            
            for (int neighbor : graph[node]) {
                int newMask = mask | (1 << neighbor);
                
                if (!visited[neighbor][newMask]) {
                    visited[neighbor][newMask] = true;
                    queue.offer(new int[]{neighbor, newMask, dist + 1});
                }
            }
        }
        
        return -1;
    }
    
    // Test method
    public static void main(String[] args) {
        ShortestPathVisitingAllNodes solution = new ShortestPathVisitingAllNodes();
        
        // Test case 1: [[1,2,3],[0],[0],[0]]
        int[][] graph1 = {{1,2,3},{0},{0},{0}};
        System.out.println("Shortest path (BFS): " + solution.shortestPathLength(graph1)); // 4
        
        // Test case 2: [[1],[0,2,4],[1,3,4],[2],[1,2]]
        int[][] graph2 = {{1},{0,2,4},{1,3,4},{2},{1,2}};
        System.out.println("Shortest path (DP): " + solution.shortestPathLengthDP(graph2)); // 4
        
        System.out.println("Shortest path (Optimized): " + solution.shortestPathLengthOptimized(graph1)); // 4
    }
}

