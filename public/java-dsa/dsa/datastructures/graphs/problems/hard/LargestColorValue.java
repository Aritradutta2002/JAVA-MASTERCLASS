package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1857: Largest Color Value in a Directed Graph
 * 
 * Problem: There is a directed graph of n colored nodes and m edges. The nodes are numbered from 0 to n - 1.
 * You are given a string colors where colors[i] is a lowercase English letter representing the color of
 * the ith node in this graph (0-indexed). You are also given a 2D array edges where edges[j] = [aj, bj] 
 * indicates that there is a directed edge from node aj to node bj.
 * A valid path in the graph is a sequence of nodes x1 -> x2 -> x3 -> ... -> xk such that there is a directed
 * edge from xi to xi+1 for every 1 <= i < k. The color value of the path is the number of nodes that are
 * colored the most frequently occurring color along that path.
 * Return the largest color value of any valid path in the given graph, or -1 if the graph contains a cycle.
 * 
 * Time Complexity: O(n + m) * 26
 * Space Complexity: O(n * 26)
 */
public class LargestColorValue {
    
    /**
     * Topological sort with DP solution
     */
    public int largestPathValue(String colors, int[][] edges) {
        int n = colors.length();
        
        // Build graph and calculate in-degrees
        List<List<Integer>> graph = new ArrayList<>();
        int[] indegree = new int[n];
        
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            indegree[edge[1]]++;
        }
        
        // dp[i][c] = maximum count of color c in any path ending at node i
        int[][] dp = new int[n][26];
        Queue<Integer> queue = new LinkedList<>();
        
        // Initialize nodes with no incoming edges
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                queue.offer(i);
                dp[i][colors.charAt(i) - 'a'] = 1;
            }
        }
        
        int processed = 0;
        int maxColorValue = 0;
        
        while (!queue.isEmpty()) {
            int node = queue.poll();
            processed++;
            
            // Update maximum color value
            for (int c = 0; c < 26; c++) {
                maxColorValue = Math.max(maxColorValue, dp[node][c]);
            }
            
            for (int neighbor : graph.get(node)) {
                // Update DP values for neighbor
                for (int c = 0; c < 26; c++) {
                    int colorCount = dp[node][c];
                    if (c == colors.charAt(neighbor) - 'a') {
                        colorCount++;
                    }
                    dp[neighbor][c] = Math.max(dp[neighbor][c], colorCount);
                }
                
                indegree[neighbor]--;
                if (indegree[neighbor] == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // Check if there's a cycle
        return processed == n ? maxColorValue : -1;
    }
    
    /**
     * DFS with memoization and cycle detection
     */
    public int largestPathValueDFS(String colors, int[][] edges) {
        int n = colors.length();
        
        // Build graph
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
        }
        
        // dp[i][c] = maximum count of color c in any path starting from node i
        int[][] dp = new int[n][26];
        for (int i = 0; i < n; i++) {
            Arrays.fill(dp[i], -1);
        }
        
        int[] state = new int[n]; // 0: unvisited, 1: visiting, 2: visited
        
        int maxColorValue = 0;
        
        for (int i = 0; i < n; i++) {
            for (int c = 0; c < 26; c++) {
                int result = dfs(graph, colors, i, c, dp, state);
                if (result == -1) return -1; // Cycle detected
                maxColorValue = Math.max(maxColorValue, result);
            }
        }
        
        return maxColorValue;
    }
    
    private int dfs(List<List<Integer>> graph, String colors, int node, int color, int[][] dp, int[] state) {
        if (state[node] == 1) return -1; // Cycle detected
        if (dp[node][color] != -1) return dp[node][color];
        
        state[node] = 1; // Mark as visiting
        
        int maxCount = colors.charAt(node) - 'a' == color ? 1 : 0;
        
        for (int neighbor : graph.get(node)) {
            int result = dfs(graph, colors, neighbor, color, dp, state);
            if (result == -1) return -1; // Cycle detected
            maxCount = Math.max(maxCount, result + (colors.charAt(node) - 'a' == color ? 1 : 0));
        }
        
        state[node] = 2; // Mark as visited
        dp[node][color] = maxCount;
        return maxCount;
    }
    
    // Test method
    public static void main(String[] args) {
        LargestColorValue solution = new LargestColorValue();
        
        // Test case 1: colors = "abaca", edges = [[0,1],[0,2],[2,3],[3,4]]
        String colors1 = "abaca";
        int[][] edges1 = {{0,1},{0,2},{2,3},{3,4}};
        System.out.println("Largest color value (Topological): " + solution.largestPathValue(colors1, edges1)); // 3
        
        // Test case 2: colors = "a", edges = [[0,0]]
        String colors2 = "a";
        int[][] edges2 = {{0,0}};
        System.out.println("Largest color value (DFS): " + solution.largestPathValueDFS(colors2, edges2)); // -1
    }
}

