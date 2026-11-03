package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 785: Is Graph Bipartite?
 * 
 * Problem: There is an undirected graph with n nodes, where each node is numbered between 0 and n - 1. 
 * You are given a 2D array graph, where graph[u] is an array of nodes that node u is adjacent to.
 * A graph is bipartite if the nodes can be partitioned into two independent sets A and B such that 
 * every edge in the graph connects a node in set A and a node in set B.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V)
 */
public class IsGraphBipartite {
    
    /**
     * DFS solution using 2-coloring
     */
    public boolean isBipartite(int[][] graph) {
        int n = graph.length;
        int[] colors = new int[n]; // 0: uncolored, 1: red, -1: blue
        
        // Check each component (graph might be disconnected)
        for (int i = 0; i < n; i++) {
            if (colors[i] == 0) {
                if (!dfs(graph, i, 1, colors)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    private boolean dfs(int[][] graph, int node, int color, int[] colors) {
        colors[node] = color;
        
        for (int neighbor : graph[node]) {
            if (colors[neighbor] == 0) {
                // Color neighbor with opposite color
                if (!dfs(graph, neighbor, -color, colors)) {
                    return false;
                }
            } else if (colors[neighbor] == color) {
                // Same color as current node - not bipartite
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * BFS solution using 2-coloring
     */
    public boolean isBipartiteBFS(int[][] graph) {
        int n = graph.length;
        int[] colors = new int[n];
        
        for (int i = 0; i < n; i++) {
            if (colors[i] == 0) {
                Queue<Integer> queue = new LinkedList<>();
                queue.offer(i);
                colors[i] = 1;
                
                while (!queue.isEmpty()) {
                    int node = queue.poll();
                    
                    for (int neighbor : graph[node]) {
                        if (colors[neighbor] == 0) {
                            colors[neighbor] = -colors[node];
                            queue.offer(neighbor);
                        } else if (colors[neighbor] == colors[node]) {
                            return false;
                        }
                    }
                }
            }
        }
        
        return true;
    }
    
    /**
     * Union-Find solution
     */
    public boolean isBipartiteUnionFind(int[][] graph) {
        int n = graph.length;
        int[] parent = new int[2 * n]; // 2n nodes: original and "opposite" nodes
        
        // Initialize parent array
        for (int i = 0; i < 2 * n; i++) {
            parent[i] = i;
        }
        
        for (int i = 0; i < n; i++) {
            for (int neighbor : graph[i]) {
                // If node i and neighbor are in same set, not bipartite
                if (find(parent, i) == find(parent, neighbor)) {
                    return false;
                }
                
                // Union i with neighbor's opposite, and neighbor with i's opposite
                union(parent, i, neighbor + n);
                union(parent, neighbor, i + n);
            }
        }
        
        return true;
    }
    
    private int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]);
        }
        return parent[x];
    }
    
    private void union(int[] parent, int x, int y) {
        int rootX = find(parent, x);
        int rootY = find(parent, y);
        if (rootX != rootY) {
            parent[rootX] = rootY;
        }
    }
    
    // Test method
    public static void main(String[] args) {
        IsGraphBipartite solution = new IsGraphBipartite();
        
        // Test case 1: [[1,3],[0,2],[1,3],[0,2]] - bipartite
        int[][] graph1 = {{1,3},{0,2},{1,3},{0,2}};
        System.out.println("Is bipartite (DFS): " + solution.isBipartite(graph1)); // true
        
        // Test case 2: [[1,2,3],[0,2],[0,1,3],[0,2]] - not bipartite
        int[][] graph2 = {{1,2,3},{0,2},{0,1,3},{0,2}};
        System.out.println("Is bipartite (BFS): " + solution.isBipartiteBFS(graph2)); // false
        
        System.out.println("Is bipartite (Union-Find): " + solution.isBipartiteUnionFind(graph1)); // true
    }
}

