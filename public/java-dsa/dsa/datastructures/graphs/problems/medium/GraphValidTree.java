package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 261: Graph Valid Tree
 * 
 * Problem: You have a graph of n nodes labeled from 0 to n - 1. You are given an integer n and a list of edges 
 * where edges[i] = [ai, bi] indicates that there is an undirected edge between nodes ai and bi in the graph.
 * Return true if the edges of the given graph make up a valid tree, and false otherwise.
 * 
 * Time Complexity: O(n + m) where m is number of edges
 * Space Complexity: O(n)
 */
public class GraphValidTree {
    
    /**
     * DFS solution - check connectivity and cycle detection
     */
    public boolean validTree(int n, int[][] edges) {
        // Tree must have exactly n-1 edges
        if (edges.length != n - 1) {
            return false;
        }
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        // Check if all nodes are connected using DFS
        boolean[] visited = new boolean[n];
        dfs(graph, 0, visited);
        
        // All nodes should be visited
        for (boolean v : visited) {
            if (!v) return false;
        }
        
        return true;
    }
    
    private void dfs(List<List<Integer>> graph, int node, boolean[] visited) {
        visited[node] = true;
        for (int neighbor : graph.get(node)) {
            if (!visited[neighbor]) {
                dfs(graph, neighbor, visited);
            }
        }
    }
    
    /**
     * Union-Find solution
     */
    public boolean validTreeUnionFind(int n, int[][] edges) {
        // Tree must have exactly n-1 edges
        if (edges.length != n - 1) {
            return false;
        }
        
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        
        // Check for cycles using Union-Find
        for (int[] edge : edges) {
            int root1 = find(parent, edge[0]);
            int root2 = find(parent, edge[1]);
            
            if (root1 == root2) {
                return false; // Cycle detected
            }
            
            parent[root1] = root2; // Union
        }
        
        return true;
    }
    
    private int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]); // Path compression
        }
        return parent[x];
    }
    
    /**
     * BFS solution
     */
    public boolean validTreeBFS(int n, int[][] edges) {
        if (edges.length != n - 1) {
            return false;
        }
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        // BFS to check connectivity
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(0);
        visited[0] = true;
        
        while (!queue.isEmpty()) {
            int node = queue.poll();
            for (int neighbor : graph.get(node)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.offer(neighbor);
                }
            }
        }
        
        // All nodes should be visited
        for (boolean v : visited) {
            if (!v) return false;
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        GraphValidTree solution = new GraphValidTree();
        
        // Test case 1: n = 5, edges = [[0,1],[0,2],[0,3],[1,4]]
        int[][] edges1 = {{0,1},{0,2},{0,3},{1,4}};
        System.out.println("Valid tree (DFS): " + solution.validTree(5, edges1)); // true
        
        // Test case 2: n = 5, edges = [[0,1],[1,2],[2,3],[1,3],[1,4]]
        int[][] edges2 = {{0,1},{1,2},{2,3},{1,3},{1,4}};
        System.out.println("Valid tree (Union-Find): " + solution.validTreeUnionFind(5, edges2)); // false
    }
}

