package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1377: Frog Position After T Seconds
 * 
 * Problem: Given an undirected tree consisting of n vertices numbered from 1 to n. A frog starts jumping from
 * vertex 1. In one second, the frog jumps from its current vertex to another unvisited vertex if they are
 * directly connected. The frog can not jump back to a visited vertex. In case the frog can jump to several
 * vertices, it jumps randomly to one of them with the same probability. Otherwise, when the frog can not
 * jump to any unvisited vertex, it jumps forever on the same vertex.
 * 
 * The edges of the undirected tree are given in the array edges, where edges[i] = [ai, bi] means that
 * exists an edge connecting the vertices ai and bi.
 * Return the probability that after t seconds the frog is on the vertex target. Answers within 10^-5 of the
 * actual answer will be accepted.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class FrogPositionAfterTSeconds {
    
    /**
     * DFS solution
     */
    public double frogPosition(int n, int[][] edges, int t, int target) {
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        boolean[] visited = new boolean[n + 1];
        return dfs(graph, 1, target, t, visited);
    }
    
    private double dfs(List<List<Integer>> graph, int current, int target, int time, boolean[] visited) {
        visited[current] = true;
        
        // Count unvisited neighbors
        int unvisitedCount = 0;
        for (int neighbor : graph.get(current)) {
            if (!visited[neighbor]) {
                unvisitedCount++;
            }
        }
        
        // Base cases
        if (time == 0) {
            return current == target ? 1.0 : 0.0;
        }
        
        if (current == target) {
            // If we're at target and have no unvisited neighbors, frog stays here
            return unvisitedCount == 0 ? 1.0 : 0.0;
        }
        
        if (unvisitedCount == 0) {
            // No more moves possible, frog stays at current position
            return 0.0;
        }
        
        // Explore unvisited neighbors
        double probability = 0.0;
        for (int neighbor : graph.get(current)) {
            if (!visited[neighbor]) {
                probability += dfs(graph, neighbor, target, time - 1, visited) / unvisitedCount;
            }
        }
        
        visited[current] = false; // Backtrack
        return probability;
    }
    
    /**
     * BFS solution
     */
    public double frogPositionBFS(int n, int[][] edges, int t, int target) {
        if (n == 1) return 1.0;
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        Queue<int[]> queue = new LinkedList<>(); // [node, time]
        Map<Integer, Double> probabilities = new HashMap<>();
        boolean[] visited = new boolean[n + 1];
        
        queue.offer(new int[]{1, 0});
        probabilities.put(1, 1.0);
        visited[1] = true;
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int node = current[0];
            int time = current[1];
            
            if (time == t) {
                if (node == target) {
                    return probabilities.get(node);
                }
                continue;
            }
            
            // Count unvisited neighbors
            List<Integer> unvisitedNeighbors = new ArrayList<>();
            for (int neighbor : graph.get(node)) {
                if (!visited[neighbor]) {
                    unvisitedNeighbors.add(neighbor);
                }
            }
            
            if (unvisitedNeighbors.isEmpty()) {
                // Frog stays at current position
                if (node == target && time <= t) {
                    return probabilities.get(node);
                }
                continue;
            }
            
            // Distribute probability among unvisited neighbors
            double currentProb = probabilities.get(node);
            double neighborProb = currentProb / unvisitedNeighbors.size();
            
            for (int neighbor : unvisitedNeighbors) {
                visited[neighbor] = true;
                probabilities.put(neighbor, neighborProb);
                queue.offer(new int[]{neighbor, time + 1});
            }
        }
        
        return 0.0;
    }
    
    /**
     * Optimized DFS with path tracking
     */
    public double frogPositionOptimized(int n, int[][] edges, int t, int target) {
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i <= n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        boolean[] visited = new boolean[n + 1];
        double[] result = new double[1];
        
        dfsOptimized(graph, 1, target, t, 1.0, visited, result);
        return result[0];
    }
    
    private void dfsOptimized(List<List<Integer>> graph, int current, int target, int time, 
                             double probability, boolean[] visited, double[] result) {
        visited[current] = true;
        
        if (current == target) {
            // Count unvisited neighbors
            int unvisitedCount = 0;
            for (int neighbor : graph.get(current)) {
                if (!visited[neighbor]) {
                    unvisitedCount++;
                }
            }
            
            // If no unvisited neighbors or exactly at time t
            if (unvisitedCount == 0 || time == 0) {
                result[0] = probability;
                visited[current] = false;
                return;
            }
        }
        
        if (time > 0) {
            // Count and explore unvisited neighbors
            List<Integer> unvisitedNeighbors = new ArrayList<>();
            for (int neighbor : graph.get(current)) {
                if (!visited[neighbor]) {
                    unvisitedNeighbors.add(neighbor);
                }
            }
            
            if (!unvisitedNeighbors.isEmpty()) {
                double neighborProb = probability / unvisitedNeighbors.size();
                for (int neighbor : unvisitedNeighbors) {
                    dfsOptimized(graph, neighbor, target, time - 1, neighborProb, visited, result);
                    if (result[0] > 0) break; // Found target
                }
            }
        }
        
        visited[current] = false;
    }
    
    // Test method
    public static void main(String[] args) {
        FrogPositionAfterTSeconds solution = new FrogPositionAfterTSeconds();
        
        // Test case 1: n = 7, edges = [[1,2],[1,3],[1,7],[2,4],[2,6],[3,5]], t = 2, target = 4
        int[][] edges1 = {{1,2},{1,3},{1,7},{2,4},{2,6},{3,5}};
        System.out.println("Frog position (DFS): " + solution.frogPosition(7, edges1, 2, 4)); // 0.16666666666666666
        
        // Test case 2: n = 7, edges = [[1,2],[1,3],[1,7],[2,4],[2,6],[3,5]], t = 1, target = 7
        System.out.println("Frog position (BFS): " + solution.frogPositionBFS(7, edges1, 1, 7)); // 0.3333333333333333
        
        System.out.println("Frog position (Optimized): " + solution.frogPositionOptimized(7, edges1, 2, 4)); // 0.16666666666666666
    }
}

