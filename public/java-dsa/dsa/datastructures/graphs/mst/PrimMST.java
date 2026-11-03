package dsa.datastructures.graphs.mst;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Prim's Minimum Spanning Tree (MST) Algorithm
 * 
 * Prim's algorithm finds a minimum spanning tree for a weighted, connected, undirected graph.
 * It works by growing the MST from a starting vertex, always adding the minimum weight edge
 * that connects a vertex in the MST to a vertex outside it.
 * 
 * Time Complexity: O((V + E) log V) with binary heap, O(V²) with array
 * Space Complexity: O(V + E) for adjacency list and priority queue
 * 
 * Key Properties:
 * - Greedy algorithm: always picks minimum weight edge crossing cut
 * - Grows MST from a single starting vertex
 * - Works best for dense graphs (many edges)
 * - Produces minimum total weight spanning tree
 * - Can start from any vertex
 * 
 * Algorithm Steps:
 * 1. Start with any vertex, mark it as visited
 * 2. Add all edges from visited vertices to priority queue
 * 3. Pick minimum weight edge that connects to unvisited vertex
 * 4. Mark that vertex as visited and add its edges
 * 5. Repeat until all vertices are visited
 * 
 * Prim's vs Kruskal's:
 * - Prim's: Better for dense graphs, grows from one vertex
 * - Kruskal's: Better for sparse graphs, considers all edges
 * 
 * Common MAANG Applications:
 * - Network design (minimum cable/wire length)
 * - Circuit design
 * - Clustering algorithms
 * - Approximation algorithms
 * - Infrastructure planning
 */
public class PrimMST {
    
    private static final int INF = Integer.MAX_VALUE;
    
    /**
     * APPROACH 1: Classic Prim's with Priority Queue (OPTIMIZED)
     * 
     * Standard implementation using binary heap (PriorityQueue).
     * Most efficient for sparse to medium-density graphs.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices (0 to n-1)
     * @param adjW Adjacency list with weights: adjW[u] = list of [v, weight]
     * @return Total weight of MST, or Integer.MAX_VALUE if disconnected
     */
    public static int primOptimized(int n, List<List<int[]>> adjW) {
        if (adjW == null || adjW.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        boolean[] visited = new boolean[n];
        
        // Priority queue: [vertex, edge_weight_to_reach_it]
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        
        // Start from vertex 0 with weight 0
        pq.offer(new int[]{0, 0});
        
        int totalWeight = 0;
        int verticesInMST = 0;
        
        while (!pq.isEmpty() && verticesInMST < n) {
            int[] current = pq.poll();
            int vertex = current[0];
            int weight = current[1];
            
            // Skip if already visited (handles duplicate entries)
            if (visited[vertex]) {
                continue;
            }
            
            // Add vertex to MST
            visited[vertex] = true;
            totalWeight += weight;
            verticesInMST++;
            
            // Add all edges from this vertex to unvisited neighbors
            for (int[] edge : adjW.get(vertex)) {
                int neighbor = edge[0];
                int edgeWeight = edge[1];
                
                if (!visited[neighbor]) {
                    pq.offer(new int[]{neighbor, edgeWeight});
                }
            }
        }
        
        // Check if MST is complete (graph must be connected)
        return verticesInMST == n ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * APPROACH 2: Prim's with MST Edge Tracking
     * 
     * Enhanced version that returns the actual edges included in the MST,
     * not just the total weight.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param adjW Adjacency list with weights
     * @return PrimResult containing MST edges and total weight
     */
    public static PrimResult primWithEdges(int n, List<List<int[]>> adjW) {
        if (adjW == null || adjW.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        boolean[] visited = new boolean[n];
        int[] parent = new int[n];
        Arrays.fill(parent, -1);
        
        // PQ: [vertex, weight, parent_vertex]
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        pq.offer(new int[]{0, 0, -1});
        
        List<Edge> mstEdges = new ArrayList<>();
        int totalWeight = 0;
        int verticesInMST = 0;
        
        while (!pq.isEmpty() && verticesInMST < n) {
            int[] current = pq.poll();
            int vertex = current[0];
            int weight = current[1];
            int par = current[2];
            
            if (visited[vertex]) {
                continue;
            }
            
            visited[vertex] = true;
            totalWeight += weight;
            verticesInMST++;
            
            // Add edge to MST (skip the first vertex)
            if (par != -1) {
                mstEdges.add(new Edge(par, vertex, weight));
            }
            
            // Add edges to neighbors
            for (int[] edge : adjW.get(vertex)) {
                int neighbor = edge[0];
                int edgeWeight = edge[1];
                
                if (!visited[neighbor]) {
                    pq.offer(new int[]{neighbor, edgeWeight, vertex});
                }
            }
        }
        
        boolean isConnected = (verticesInMST == n);
        return new PrimResult(mstEdges, totalWeight, isConnected);
    }
    
    /**
     * APPROACH 3: Array-Based Prim's (for Dense Graphs)
     * 
     * Uses simple array instead of priority queue.
     * More efficient for dense graphs where E ≈ V².
     * 
     * Time Complexity: O(V²)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param adjW Adjacency list with weights
     * @return Total weight of MST
     */
    public static int primArray(int n, List<List<int[]>> adjW) {
        if (adjW == null || adjW.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        boolean[] visited = new boolean[n];
        int[] minWeight = new int[n];
        Arrays.fill(minWeight, INF);
        
        minWeight[0] = 0; // Start from vertex 0
        int totalWeight = 0;
        
        for (int count = 0; count < n; count++) {
            // Find minimum weight vertex among unvisited
            int minVertex = -1;
            int minW = INF;
            
            for (int v = 0; v < n; v++) {
                if (!visited[v] && minWeight[v] < minW) {
                    minW = minWeight[v];
                    minVertex = v;
                }
            }
            
            // If no vertex found, graph is disconnected
            if (minVertex == -1) {
                return Integer.MAX_VALUE;
            }
            
            // Add vertex to MST
            visited[minVertex] = true;
            totalWeight += minW;
            
            // Update weights of adjacent vertices
            for (int[] edge : adjW.get(minVertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (!visited[neighbor] && weight < minWeight[neighbor]) {
                    minWeight[neighbor] = weight;
                }
            }
        }
        
        return totalWeight;
    }
    
    /**
     * APPROACH 4: Prim's with Starting Vertex Option
     * 
     * Allows choosing the starting vertex.
     * Useful for understanding that Prim's works from any starting point.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param adjW Adjacency list with weights
     * @param start Starting vertex
     * @return Total weight of MST
     */
    public static int primFromVertex(int n, List<List<int[]>> adjW, int start) {
        if (start < 0 || start >= n) {
            throw new IllegalArgumentException("Invalid starting vertex");
        }
        
        boolean[] visited = new boolean[n];
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[1]));
        
        pq.offer(new int[]{start, 0});
        int totalWeight = 0;
        int verticesInMST = 0;
        
        while (!pq.isEmpty() && verticesInMST < n) {
            int[] current = pq.poll();
            int vertex = current[0];
            int weight = current[1];
            
            if (visited[vertex]) {
                continue;
            }
            
            visited[vertex] = true;
            totalWeight += weight;
            verticesInMST++;
            
            for (int[] edge : adjW.get(vertex)) {
                int neighbor = edge[0];
                int edgeWeight = edge[1];
                
                if (!visited[neighbor]) {
                    pq.offer(new int[]{neighbor, edgeWeight});
                }
            }
        }
        
        return verticesInMST == n ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * APPROACH 5: Lazy Prim's Algorithm
     * 
     * Simpler implementation that doesn't track visited status in PQ.
     * Easier to understand but may process same vertex multiple times.
     * 
     * Time Complexity: O(E log E) - may add more edges to PQ
     * Space Complexity: O(E)
     * 
     * @param n Number of vertices
     * @param adjW Adjacency list with weights
     * @return Total weight of MST
     */
    public static int primLazy(int n, List<List<int[]>> adjW) {
        boolean[] visited = new boolean[n];
        PriorityQueue<int[]> pq = new PriorityQueue<>(Comparator.comparingInt(a -> a[2]));
        
        // Start from vertex 0
        visited[0] = true;
        
        // Add all edges from vertex 0
        for (int[] edge : adjW.get(0)) {
            pq.offer(new int[]{0, edge[0], edge[1]});
        }
        
        int totalWeight = 0;
        int edgesAdded = 0;
        
        while (!pq.isEmpty() && edgesAdded < n - 1) {
            int[] edge = pq.poll();
            // int from = edge[0]; // Not needed for this approach
            int to = edge[1];
            int weight = edge[2];
            
            // Skip if both endpoints already in MST
            if (visited[to]) {
                continue;
            }
            
            // Add edge to MST
            visited[to] = true;
            totalWeight += weight;
            edgesAdded++;
            
            // Add all edges from newly added vertex
            for (int[] nextEdge : adjW.get(to)) {
                if (!visited[nextEdge[0]]) {
                    pq.offer(new int[]{to, nextEdge[0], nextEdge[1]});
                }
            }
        }
        
        return edgesAdded == n - 1 ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * APPROACH 6: Prim's for Maximum Spanning Tree
     * 
     * Variation that finds maximum spanning tree instead of minimum.
     * Simply uses max-heap instead of min-heap.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param adjW Adjacency list with weights
     * @return Total weight of maximum spanning tree
     */
    public static int primMaximumST(int n, List<List<int[]>> adjW) {
        boolean[] visited = new boolean[n];
        
        // Max-heap: negate weights for maximum
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> Integer.compare(b[1], a[1]));
        
        pq.offer(new int[]{0, 0});
        int totalWeight = 0;
        int verticesInMST = 0;
        
        while (!pq.isEmpty() && verticesInMST < n) {
            int[] current = pq.poll();
            int vertex = current[0];
            int weight = current[1];
            
            if (visited[vertex]) {
                continue;
            }
            
            visited[vertex] = true;
            totalWeight += weight;
            verticesInMST++;
            
            for (int[] edge : adjW.get(vertex)) {
                int neighbor = edge[0];
                int edgeWeight = edge[1];
                
                if (!visited[neighbor]) {
                    pq.offer(new int[]{neighbor, edgeWeight});
                }
            }
        }
        
        return verticesInMST == n ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * Utility: Create adjacency list from edge list
     */
    public static List<List<int[]>> createAdjacencyList(int n, List<Edge> edges) {
        List<List<int[]>> adjW = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjW.add(new ArrayList<>());
        }
        
        for (Edge edge : edges) {
            adjW.get(edge.from).add(new int[]{edge.to, edge.weight});
            adjW.get(edge.to).add(new int[]{edge.from, edge.weight}); // Undirected
        }
        
        return adjW;
    }
    
    /**
     * Edge class representing a weighted edge
     */
    public static class Edge {
        public int from;
        public int to;
        public int weight;
        
        public Edge(int from, int to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
        
        @Override
        public String toString() {
            return String.format("(%d-%d, w=%d)", from, to, weight);
        }
    }
    
    /**
     * Result class for Prim's algorithm
     */
    public static class PrimResult {
        public final List<Edge> mstEdges;
        public final int totalWeight;
        public final boolean isConnected;
        
        public PrimResult(List<Edge> mstEdges, int totalWeight, boolean isConnected) {
            this.mstEdges = mstEdges;
            this.totalWeight = totalWeight;
            this.isConnected = isConnected;
        }
        
        @Override
        public String toString() {
            return String.format("MST(weight=%d, edges=%d, connected=%b)", 
                               totalWeight, mstEdges.size(), isConnected);
        }
    }
    
    /**
     * Demonstration and Testing
     */
    public static void main(String[] args) {
        System.out.println("=== Prim's MST Algorithm Demonstrations ===\n");
        
        // Example graph:
        //       0
        //      /|\
        //    1/ | \4
        //    /  |3 \
        //   1   |   2
        //    \  |  /
        //    2\ | /5
        //      \|/
        //       3
        
        List<Edge> edges = new ArrayList<>();
        edges.add(new Edge(0, 1, 1));
        edges.add(new Edge(0, 2, 3));
        edges.add(new Edge(0, 3, 4));
        edges.add(new Edge(1, 3, 2));
        edges.add(new Edge(2, 3, 5));
        
        int n = 4;
        List<List<int[]>> adjW = createAdjacencyList(n, edges);
        
        // Test 1: Optimized Prim's
        System.out.println("Test 1: Optimized Prim's (Priority Queue)");
        int mstWeight = primOptimized(n, adjW);
        System.out.println("MST Total Weight: " + mstWeight);
        System.out.println();
        
        // Test 2: Prim's with edges
        System.out.println("Test 2: Prim's with Edge Details");
        PrimResult result = primWithEdges(n, adjW);
        System.out.println(result);
        System.out.println("MST Edges:");
        for (Edge edge : result.mstEdges) {
            System.out.println("  " + edge);
        }
        System.out.println();
        
        // Test 3: Array-based Prim's
        System.out.println("Test 3: Array-based Prim's (for Dense Graphs)");
        int arrayMST = primArray(n, adjW);
        System.out.println("MST Total Weight: " + arrayMST);
        System.out.println();
        
        // Test 4: Prim's from different starting vertices
        System.out.println("Test 4: Prim's from Different Starting Vertices");
        for (int start = 0; start < n; start++) {
            int weight = primFromVertex(n, adjW, start);
            System.out.println("Starting from vertex " + start + ": " + weight);
        }
        System.out.println();
        
        // Test 5: Lazy Prim's
        System.out.println("Test 5: Lazy Prim's");
        int lazyMST = primLazy(n, adjW);
        System.out.println("MST Total Weight: " + lazyMST);
        System.out.println();
        
        // Test 6: Maximum Spanning Tree
        System.out.println("Test 6: Maximum Spanning Tree");
        int maxST = primMaximumST(n, adjW);
        System.out.println("Maximum ST Weight: " + maxST);
        System.out.println();
        
        // Test 7: Consistency check
        System.out.println("Test 7: Algorithm Consistency");
        System.out.println("All methods produce same MST weight: " + 
                         (mstWeight == arrayMST && mstWeight == lazyMST));
    }
}



