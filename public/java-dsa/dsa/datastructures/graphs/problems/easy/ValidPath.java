package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 1971: Find if Path Exists in Graph
 * 
 * Problem: There is a bi-directional graph with n vertices, where each vertex 
 * is labeled from 0 to n - 1. Given edges and two integers source and destination,
 * return true if there is a valid path from source to destination, or false otherwise.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V + E) for adjacency list
 */
public class ValidPath {
    
    /**
     * DFS Solution
     */
    public boolean validPath(int n, int[][] edges, int source, int destination) {
        if (source == destination) return true;
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        boolean[] visited = new boolean[n];
        return dfs(graph, visited, source, destination);
    }
    
    private boolean dfs(List<List<Integer>> graph, boolean[] visited, int current, int destination) {
        if (current == destination) return true;
        
        visited[current] = true;
        
        for (int neighbor : graph.get(current)) {
            if (!visited[neighbor] && dfs(graph, visited, neighbor, destination)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * BFS Solution
     */
    public boolean validPathBFS(int n, int[][] edges, int source, int destination) {
        if (source == destination) return true;
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(source);
        visited[source] = true;
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            
            if (current == destination) return true;
            
            for (int neighbor : graph.get(current)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.offer(neighbor);
                }
            }
        }
        
        return false;
    }
    
    /**
     * Union-Find Solution
     */
    public boolean validPathUnionFind(int n, int[][] edges, int source, int destination) {
        UnionFind uf = new UnionFind(n);
        
        for (int[] edge : edges) {
            uf.union(edge[0], edge[1]);
        }
        
        return uf.connected(source, destination);
    }
    
    class UnionFind {
        private int[] parent;
        private int[] rank;
        
        public UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }
        
        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }
        
        public void union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            
            if (rootX != rootY) {
                if (rank[rootX] < rank[rootY]) {
                    parent[rootX] = rootY;
                } else if (rank[rootX] > rank[rootY]) {
                    parent[rootY] = rootX;
                } else {
                    parent[rootY] = rootX;
                    rank[rootX]++;
                }
            }
        }
        
        public boolean connected(int x, int y) {
            return find(x) == find(y);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        ValidPath solution = new ValidPath();
        
        // Test case 1
        int n1 = 3;
        int[][] edges1 = {{0,1},{1,2},{2,0}};
        int source1 = 0, destination1 = 2;
        
        System.out.println("Test 1 - DFS: " + solution.validPath(n1, edges1, source1, destination1)); // true
        System.out.println("Test 1 - BFS: " + solution.validPathBFS(n1, edges1, source1, destination1)); // true
        System.out.println("Test 1 - Union-Find: " + solution.validPathUnionFind(n1, edges1, source1, destination1)); // true
        
        // Test case 2
        int n2 = 6;
        int[][] edges2 = {{0,1},{0,2},{3,5},{5,4},{4,3}};
        int source2 = 0, destination2 = 5;
        
        System.out.println("Test 2 - DFS: " + solution.validPath(n2, edges2, source2, destination2)); // false
    }
}

