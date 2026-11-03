package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 1466: Reorder Routes to Make All Paths Lead to City Zero
 * 
 * Problem: There are n cities numbered from 0 to n - 1 and n - 1 roads such that there is only one way to travel 
 * between two different cities (this network form a tree). Last year, The ministry of transport decided to orient 
 * the roads in one direction because they are too narrow.
 * Roads are represented by connections where connections[i] = [ai, bi] represents a road from city ai to city bi.
 * This year, there will be a big event in the capital (city 0), and many people want to travel to this city.
 * Your task consists of reorienting some roads such that each city can visit the city 0. 
 * Return the minimum number of edges changed.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class ReorderRoutes {
    
    /**
     * DFS solution
     */
    public int minReorder(int n, int[][] connections) {
        // Build bidirectional graph with edge direction information
        List<List<int[]>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] connection : connections) {
            int from = connection[0];
            int to = connection[1];
            
            // Add both directions with direction flag
            graph.get(from).add(new int[]{to, 1});   // Original direction (needs change)
            graph.get(to).add(new int[]{from, 0});   // Reverse direction (correct)
        }
        
        boolean[] visited = new boolean[n];
        return dfs(graph, 0, visited);
    }
    
    private int dfs(List<List<int[]>> graph, int node, boolean[] visited) {
        visited[node] = true;
        int changes = 0;
        
        for (int[] edge : graph.get(node)) {
            int neighbor = edge[0];
            int needChange = edge[1];
            
            if (!visited[neighbor]) {
                changes += needChange + dfs(graph, neighbor, visited);
            }
        }
        
        return changes;
    }
    
    /**
     * BFS solution
     */
    public int minReorderBFS(int n, int[][] connections) {
        // Build graph
        List<List<int[]>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] connection : connections) {
            int from = connection[0];
            int to = connection[1];
            
            graph.get(from).add(new int[]{to, 1});   // Original direction
            graph.get(to).add(new int[]{from, 0});   // Reverse direction
        }
        
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(0);
        visited[0] = true;
        
        int changes = 0;
        
        while (!queue.isEmpty()) {
            int node = queue.poll();
            
            for (int[] edge : graph.get(node)) {
                int neighbor = edge[0];
                int needChange = edge[1];
                
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    changes += needChange;
                    queue.offer(neighbor);
                }
            }
        }
        
        return changes;
    }
    
    /**
     * Alternative solution using Set for original edges
     */
    public int minReorderAlternative(int n, int[][] connections) {
        // Store original edges in a set
        Set<String> originalEdges = new HashSet<>();
        List<List<Integer>> graph = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] connection : connections) {
            int from = connection[0];
            int to = connection[1];
            
            originalEdges.add(from + "," + to);
            graph.get(from).add(to);
            graph.get(to).add(from);
        }
        
        boolean[] visited = new boolean[n];
        return dfsAlternative(graph, originalEdges, 0, visited);
    }
    
    private int dfsAlternative(List<List<Integer>> graph, Set<String> originalEdges, int node, boolean[] visited) {
        visited[node] = true;
        int changes = 0;
        
        for (int neighbor : graph.get(node)) {
            if (!visited[neighbor]) {
                // If edge from current node to neighbor exists in original, we need to change it
                if (originalEdges.contains(node + "," + neighbor)) {
                    changes++;
                }
                changes += dfsAlternative(graph, originalEdges, neighbor, visited);
            }
        }
        
        return changes;
    }
    
    // Test method
    public static void main(String[] args) {
        ReorderRoutes solution = new ReorderRoutes();
        
        // Test case 1: n = 6, connections = [[0,1],[1,3],[2,3],[4,0],[4,5]]
        int[][] connections1 = {{0,1},{1,3},{2,3},{4,0},{4,5}};
        System.out.println("Min reorder (DFS): " + solution.minReorder(6, connections1)); // 3
        
        // Test case 2: n = 5, connections = [[1,0],[1,2],[3,2],[3,4]]
        int[][] connections2 = {{1,0},{1,2},{3,2},{3,4}};
        System.out.println("Min reorder (BFS): " + solution.minReorderBFS(5, connections2)); // 2
        
        System.out.println("Min reorder (Alternative): " + solution.minReorderAlternative(6, connections1)); // 3
    }
}

