package dsa.datastructures.graphs.components;

import java.util.*;

/**
 * Maximum Flow Algorithms - Ford-Fulkerson & Variants
 * 
 * Maximum flow finds the maximum amount of flow that can be sent from a source
 * to a sink in a flow network with capacity constraints.
 * 
 * Common Algorithms:
 * 1. Ford-Fulkerson (with DFS) - O(E * max_flow)
 * 2. Edmonds-Karp (with BFS) - O(V * E²)
 * 3. Dinic's Algorithm - O(V² * E)
 * 
 * Key Concepts:
 * - Residual Graph: Graph with remaining capacities
 * - Augmenting Path: Path from source to sink with available capacity
 * - Cut: Partition of vertices into two sets
 * - Min-Cut Max-Flow Theorem: Max flow = Min cut capacity
 * 
 * Common MAANG Applications:
 * - Network bandwidth optimization
 * - Bipartite matching
 * - Image segmentation
 * - Airline scheduling
 * - Project selection
 * - Circulation with demands
 */
public class MaximumFlow {
    
    /**
     * APPROACH 1: Edmonds-Karp Algorithm (BFS-based Ford-Fulkerson)
     * 
     * Uses BFS to find augmenting paths, guaranteeing polynomial time.
     * Most practical and commonly used in interviews.
     * 
     * Time Complexity: O(V * E²)
     * Space Complexity: O(V²) for adjacency matrix
     * 
     * @param capacity Capacity matrix
     * @param source Source vertex
     * @param sink Sink vertex
     * @return Maximum flow value
     */
    public static int edmondsKarp(int[][] capacity, int source, int sink) {
        int n = capacity.length;
        int[][] residual = new int[n][n];
        
        // Copy capacity to residual graph
        for (int i = 0; i < n; i++) {
            residual[i] = Arrays.copyOf(capacity[i], n);
        }
        
        int maxFlow = 0;
        int[] parent = new int[n];
        
        // While there exists an augmenting path from source to sink
        while (bfs(residual, source, sink, parent)) {
            // Find minimum capacity along the path
            int pathFlow = Integer.MAX_VALUE;
            for (int v = sink; v != source; v = parent[v]) {
                int u = parent[v];
                pathFlow = Math.min(pathFlow, residual[u][v]);
            }
            
            // Update residual capacities
            for (int v = sink; v != source; v = parent[v]) {
                int u = parent[v];
                residual[u][v] -= pathFlow;
                residual[v][u] += pathFlow; // Reverse edge
            }
            
            maxFlow += pathFlow;
        }
        
        return maxFlow;
    }
    
    /**
     * BFS to find augmenting path in residual graph
     */
    private static boolean bfs(int[][] residual, int source, int sink, int[] parent) {
        int n = residual.length;
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(source);
        visited[source] = true;
        parent[source] = -1;
        
        while (!queue.isEmpty()) {
            int u = queue.poll();
            
            for (int v = 0; v < n; v++) {
                if (!visited[v] && residual[u][v] > 0) {
                    visited[v] = true;
                    parent[v] = u;
                    queue.offer(v);
                    
                    if (v == sink) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 2: Ford-Fulkerson with DFS
     * 
     * Original Ford-Fulkerson using DFS to find augmenting paths.
     * Simpler but can be slow for certain graphs.
     * 
     * Time Complexity: O(E * max_flow) - can be exponential
     * Space Complexity: O(V²)
     * 
     * @param capacity Capacity matrix
     * @param source Source vertex
     * @param sink Sink vertex
     * @return Maximum flow value
     */
    public static int fordFulkerson(int[][] capacity, int source, int sink) {
        int n = capacity.length;
        int[][] residual = new int[n][n];
        
        for (int i = 0; i < n; i++) {
            residual[i] = Arrays.copyOf(capacity[i], n);
        }
        
        int maxFlow = 0;
        int[] parent = new int[n];
        
        while (dfs(residual, source, sink, new boolean[n], parent)) {
            int pathFlow = Integer.MAX_VALUE;
            
            for (int v = sink; v != source; v = parent[v]) {
                int u = parent[v];
                pathFlow = Math.min(pathFlow, residual[u][v]);
            }
            
            for (int v = sink; v != source; v = parent[v]) {
                int u = parent[v];
                residual[u][v] -= pathFlow;
                residual[v][u] += pathFlow;
            }
            
            maxFlow += pathFlow;
        }
        
        return maxFlow;
    }
    
    /**
     * DFS to find augmenting path
     */
    private static boolean dfs(int[][] residual, int u, int sink, 
                              boolean[] visited, int[] parent) {
        if (u == sink) {
            return true;
        }
        
        visited[u] = true;
        
        for (int v = 0; v < residual.length; v++) {
            if (!visited[v] && residual[u][v] > 0) {
                parent[v] = u;
                if (dfs(residual, v, sink, visited, parent)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 3: Dinic's Algorithm (Advanced)
     * 
     * Uses level graph and blocking flows for better performance.
     * Best algorithm for maximum flow in practice.
     * 
     * Time Complexity: O(V² * E)
     * Space Complexity: O(V + E)
     * 
     * @param adjList Adjacency list with Edge objects
     * @param n Number of vertices
     * @param source Source vertex
     * @param sink Sink vertex
     * @return Maximum flow value
     */
    public static int dinicsAlgorithm(List<List<Edge>> adjList, int n, int source, int sink) {
        int maxFlow = 0;
        int[] level = new int[n];
        
        // While there exists an augmenting path (level graph)
        while (bfsLevel(adjList, source, sink, level)) {
            int[] iter = new int[n]; // Iterator for each vertex
            
            // Find blocking flows
            int flow;
            while ((flow = dfsBlocking(adjList, source, sink, Integer.MAX_VALUE, 
                                     level, iter)) > 0) {
                maxFlow += flow;
            }
        }
        
        return maxFlow;
    }
    
    /**
     * BFS to construct level graph
     */
    private static boolean bfsLevel(List<List<Edge>> adj, int source, int sink, int[] level) {
        Arrays.fill(level, -1);
        level[source] = 0;
        
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(source);
        
        while (!queue.isEmpty()) {
            int u = queue.poll();
            
            for (Edge edge : adj.get(u)) {
                if (level[edge.to] < 0 && edge.flow < edge.capacity) {
                    level[edge.to] = level[u] + 1;
                    queue.offer(edge.to);
                }
            }
        }
        
        return level[sink] >= 0;
    }
    
    /**
     * DFS to find blocking flow
     */
    private static int dfsBlocking(List<List<Edge>> adj, int u, int sink, int pushed, 
                                  int[] level, int[] iter) {
        if (u == sink || pushed == 0) {
            return pushed;
        }
        
        for (; iter[u] < adj.get(u).size(); iter[u]++) {
            Edge edge = adj.get(u).get(iter[u]);
            
            if (level[u] + 1 != level[edge.to]) {
                continue;
            }
            
            int tr = dfsBlocking(adj, edge.to, sink, 
                               Math.min(pushed, edge.capacity - edge.flow), 
                               level, iter);
            
            if (tr > 0) {
                edge.flow += tr;
                edge.rev.flow -= tr;
                return tr;
            }
        }
        
        return 0;
    }
    
    /**
     * APPROACH 4: Min-Cut from Max-Flow
     * 
     * After finding max flow, determines which edges form the minimum cut.
     * Uses BFS on residual graph from source.
     * 
     * @param residual Residual graph after max flow
     * @param source Source vertex
     * @return Set of vertices reachable from source in residual graph
     */
    public static Set<Integer> findMinCut(int[][] residual, int source) {
        int n = residual.length;
        boolean[] visited = new boolean[n];
        Queue<Integer> queue = new LinkedList<>();
        
        queue.offer(source);
        visited[source] = true;
        
        while (!queue.isEmpty()) {
            int u = queue.poll();
            
            for (int v = 0; v < n; v++) {
                if (!visited[v] && residual[u][v] > 0) {
                    visited[v] = true;
                    queue.offer(v);
                }
            }
        }
        
        Set<Integer> minCut = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (visited[i]) {
                minCut.add(i);
            }
        }
        
        return minCut;
    }
    
    /**
     * APPROACH 5: Maximum Bipartite Matching using Max Flow
     * 
     * Converts bipartite matching problem to max flow.
     * Add source connecting to left set, sink connecting from right set.
     * 
     * @param bipartite Adjacency list of bipartite graph
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return Maximum matching size
     */
    public static int maxBipartiteMatching(List<List<Integer>> bipartite, 
                                          int leftSize, int rightSize) {
        int n = leftSize + rightSize + 2; // +2 for source and sink
        int source = 0;
        int sink = n - 1;
        
        int[][] capacity = new int[n][n];
        
        // Source to left set (capacity 1)
        for (int i = 1; i <= leftSize; i++) {
            capacity[source][i] = 1;
        }
        
        // Left set to right set (capacity 1)
        for (int i = 0; i < leftSize; i++) {
            for (int j : bipartite.get(i)) {
                capacity[i + 1][leftSize + 1 + j] = 1;
            }
        }
        
        // Right set to sink (capacity 1)
        for (int i = 1; i <= rightSize; i++) {
            capacity[leftSize + i][sink] = 1;
        }
        
        return edmondsKarp(capacity, source, sink);
    }
    
    /**
     * Edge class for Dinic's algorithm
     */
    static class Edge {
        int to;
        int capacity;
        int flow;
        Edge rev; // Reverse edge
        
        Edge(int to, int capacity) {
            this.to = to;
            this.capacity = capacity;
            this.flow = 0;
        }
    }
    
    /**
     * Helper to add edge for Dinic's
     */
    public static void addEdgeDinics(List<List<Edge>> adj, int from, int to, int capacity) {
        Edge forward = new Edge(to, capacity);
        Edge backward = new Edge(from, 0);
        forward.rev = backward;
        backward.rev = forward;
        adj.get(from).add(forward);
        adj.get(to).add(backward);
    }
    
    /**
     * Demonstration
     */
    public static void main(String[] args) {
        System.out.println("=== Maximum Flow Algorithms ===\n");
        
        // Example: Classic max flow problem
        //        10
        //    0 -----> 1
        //    |  \     |
        //  10|   \10  |10
        //    |    \   |
        //    v     v  v
        //    2 ---> 3
        //       10
        
        int[][] capacity = {
            {0, 10, 10, 0},
            {0, 0, 0, 10},
            {0, 0, 0, 10},
            {0, 0, 0, 0}
        };
        
        System.out.println("Graph capacity matrix:");
        for (int[] row : capacity) {
            System.out.println(Arrays.toString(row));
        }
        
        int source = 0;
        int sink = 3;
        
        // Test Edmonds-Karp
        int maxFlow1 = edmondsKarp(capacity, source, sink);
        System.out.println("\nEdmonds-Karp Max Flow: " + maxFlow1);
        
        // Test Ford-Fulkerson
        int maxFlow2 = fordFulkerson(capacity, source, sink);
        System.out.println("Ford-Fulkerson Max Flow: " + maxFlow2);
        
        // Test Dinic's
        int n = 4;
        List<List<Edge>> adjList = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjList.add(new ArrayList<>());
        }
        
        addEdgeDinics(adjList, 0, 1, 10);
        addEdgeDinics(adjList, 0, 2, 10);
        addEdgeDinics(adjList, 1, 3, 10);
        addEdgeDinics(adjList, 2, 3, 10);
        
        int maxFlow3 = dinicsAlgorithm(adjList, n, source, sink);
        System.out.println("Dinic's Algorithm Max Flow: " + maxFlow3);
        
        // Test Bipartite Matching
        System.out.println("\n=== Bipartite Matching ===");
        List<List<Integer>> bipartite = new ArrayList<>();
        bipartite.add(Arrays.asList(0, 1));     // Job 0 can do tasks 0, 1
        bipartite.add(Arrays.asList(1, 2));     // Job 1 can do tasks 1, 2
        bipartite.add(Arrays.asList(0, 2));     // Job 2 can do tasks 0, 2
        
        int matching = maxBipartiteMatching(bipartite, 3, 3);
        System.out.println("Maximum Bipartite Matching: " + matching);
    }
}


