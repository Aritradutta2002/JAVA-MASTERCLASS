package dsa.datastructures.graphs.mst;

import com.dsa.datastructures.graphs.utils.UnionFind;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Kruskal's Minimum Spanning Tree (MST) Algorithm
 * 
 * Kruskal's algorithm finds a minimum spanning tree for a weighted, connected, undirected graph.
 * It works by sorting all edges by weight and greedily adding edges that don't create cycles.
 * 
 * Time Complexity: O(E log E) or O(E log V) - dominated by edge sorting
 * Space Complexity: O(V + E) for Union-Find and edge storage
 * 
 * Key Properties:
 * - Greedy algorithm: always picks minimum weight edge that doesn't form cycle
 * - Uses Union-Find (Disjoint Set) for efficient cycle detection
 * - Works best for sparse graphs (fewer edges)
 * - Produces minimum total weight spanning tree
 * - An MST has exactly V-1 edges for V vertices
 * 
 * Algorithm Steps:
 * 1. Sort all edges by weight (ascending order)
 * 2. Initialize Union-Find for all vertices
 * 3. For each edge (u,v) in sorted order:
 *    - If u and v are in different components (no cycle):
 *      - Add edge to MST
 *      - Union the components
 * 4. Stop when MST has V-1 edges
 * 
 * Common MAANG Applications:
 * - Network design (minimum cable length)
 * - Clustering algorithms
 * - Circuit design
 * - Road/pipeline networks
 * - Approximation algorithms for NP-hard problems
 */
public class KruskalMST {
    
    /**
     * APPROACH 1: Basic Kruskal's Algorithm
     * 
     * Standard implementation using edge list and Union-Find.
     * Returns the total weight of MST.
     * 
     * Time Complexity: O(E log E) for sorting + O(E α(V)) for union-find
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices (0 to n-1)
     * @param edges List of edges in the graph
     * @return Total weight of MST, or Integer.MAX_VALUE if graph is disconnected
     */
    public static int kruskalBasic(int n, List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Edge list cannot be null or empty");
        }
        
        // Step 1: Sort edges by weight (ascending order)
        edges.sort(Comparator.comparingInt(e -> e.weight));
        
        // Step 2: Initialize Union-Find
        UnionFind unionFind = new UnionFind(n);
        
        int totalWeight = 0;
        int edgesUsed = 0;
        
        // Step 3: Process each edge in sorted order
        for (Edge edge : edges) {
            // If vertices are in different components (no cycle created)
            if (unionFind.union(edge.from, edge.to)) {
                totalWeight += edge.weight;
                edgesUsed++;
                
                // Early termination: MST has exactly V-1 edges
                if (edgesUsed == n - 1) {
                    break;
                }
            }
        }
        
        // Check if MST is complete (graph must be connected)
        return edgesUsed == n - 1 ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * APPROACH 2: Kruskal's with MST Edge Tracking
     * 
     * Enhanced version that returns the actual edges included in the MST,
     * not just the total weight.
     * 
     * Time Complexity: O(E log E)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param edges List of edges
     * @return KruskalResult containing MST edges and total weight
     */
    public static KruskalResult kruskalWithEdges(int n, List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Edge list cannot be null or empty");
        }
        
        // Sort edges by weight
        List<Edge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator.comparingInt(e -> e.weight));
        
        UnionFind unionFind = new UnionFind(n);
        List<Edge> mstEdges = new ArrayList<>();
        int totalWeight = 0;
        
        // Process each edge
        for (Edge edge : sortedEdges) {
            if (unionFind.union(edge.from, edge.to)) {
                mstEdges.add(edge);
                totalWeight += edge.weight;
                
                // MST complete
                if (mstEdges.size() == n - 1) {
                    break;
                }
            }
        }
        
        // Check if graph is connected
        boolean isConnected = (mstEdges.size() == n - 1);
        
        return new KruskalResult(mstEdges, totalWeight, isConnected);
    }
    
    /**
     * APPROACH 3: Modified Kruskal's for Maximum Spanning Tree
     * 
     * Variation that finds maximum spanning tree instead of minimum.
     * Simply sorts edges in descending order by weight.
     * 
     * Time Complexity: O(E log E)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param edges List of edges
     * @return Total weight of maximum spanning tree
     */
    public static int kruskalMaximumST(int n, List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Edge list cannot be null or empty");
        }
        
        // Sort edges by weight in DESCENDING order
        edges.sort((e1, e2) -> Integer.compare(e2.weight, e1.weight));
        
        UnionFind unionFind = new UnionFind(n);
        int totalWeight = 0;
        int edgesUsed = 0;
        
        for (Edge edge : edges) {
            if (unionFind.union(edge.from, edge.to)) {
                totalWeight += edge.weight;
                edgesUsed++;
                
                if (edgesUsed == n - 1) {
                    break;
                }
            }
        }
        
        return edgesUsed == n - 1 ? totalWeight : Integer.MAX_VALUE;
    }
    
    /**
     * APPROACH 4: Kruskal's with Component Tracking
     * 
     * Tracks the number of connected components as edges are added.
     * Useful for understanding how the MST is built step by step.
     * 
     * Time Complexity: O(E log E)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param edges List of edges
     * @return List of states showing MST construction progress
     */
    public static List<MSTState> kruskalWithProgress(int n, List<Edge> edges) {
        if (edges == null || edges.isEmpty()) {
            throw new IllegalArgumentException("Edge list cannot be null or empty");
        }
        
        edges.sort(Comparator.comparingInt(e -> e.weight));
        
        UnionFind unionFind = new UnionFind(n);
        List<MSTState> progress = new ArrayList<>();
        int totalWeight = 0;
        
        // Initial state
        progress.add(new MSTState(0, totalWeight, n, new ArrayList<>()));
        
        // Process each edge and record state
        for (Edge edge : edges) {
            if (unionFind.union(edge.from, edge.to)) {
                totalWeight += edge.weight;
                List<Edge> currentEdges = new ArrayList<>();
                
                // This is inefficient but demonstrates progress
                for (MSTState state : progress) {
                    if (!state.mstEdges.isEmpty()) {
                        currentEdges.addAll(state.mstEdges);
                    }
                }
                currentEdges.add(edge);
                
                progress.add(new MSTState(
                    progress.size(),
                    totalWeight,
                    unionFind.getComponentCount(),
                    new ArrayList<>(currentEdges)
                ));
                
                if (unionFind.getComponentCount() == 1) {
                    break; // MST complete
                }
            }
        }
        
        return progress;
    }
    
    /**
     * APPROACH 5: Kruskal's for Second-Best MST
     * 
     * Finds the second minimum spanning tree (for analysis and alternatives).
     * 
     * Time Complexity: O(E log E + V²)
     * Space Complexity: O(V + E)
     * 
     * @param n Number of vertices
     * @param edges List of edges
     * @return Weight of second-best MST
     */
    public static int secondBestMST(int n, List<Edge> edges) {
        // First, find the original MST
        KruskalResult mst = kruskalWithEdges(n, edges);
        
        if (!mst.isConnected) {
            return Integer.MAX_VALUE;
        }
        
        int secondBest = Integer.MAX_VALUE;
        
        // Try removing each MST edge and finding new MST
        for (Edge removedEdge : mst.mstEdges) {
            List<Edge> modifiedEdges = new ArrayList<>();
            
            // Add all edges except the removed one
            for (Edge edge : edges) {
                if (!edge.equals(removedEdge)) {
                    modifiedEdges.add(edge);
                }
            }
            
            int altMSTWeight = kruskalBasic(n, modifiedEdges);
            if (altMSTWeight != Integer.MAX_VALUE && altMSTWeight < secondBest) {
                secondBest = altMSTWeight;
            }
        }
        
        return secondBest;
    }
    
    /**
     * Utility: Create edge list from adjacency matrix
     */
    public static List<Edge> createEdgesFromMatrix(int[][] adjMatrix) {
        List<Edge> edges = new ArrayList<>();
        int n = adjMatrix.length;
        
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) { // Avoid duplicates in undirected graph
                if (adjMatrix[i][j] != 0 && adjMatrix[i][j] != Integer.MAX_VALUE) {
                    edges.add(new Edge(i, j, adjMatrix[i][j]));
                }
            }
        }
        
        return edges;
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
        
        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof Edge)) return false;
            Edge other = (Edge) obj;
            return (from == other.from && to == other.to && weight == other.weight) ||
                   (from == other.to && to == other.from && weight == other.weight);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(Math.min(from, to), Math.max(from, to), weight);
        }
    }
    
    /**
     * Result class for Kruskal's algorithm
     */
    public static class KruskalResult {
        public final List<Edge> mstEdges;
        public final int totalWeight;
        public final boolean isConnected;
        
        public KruskalResult(List<Edge> mstEdges, int totalWeight, boolean isConnected) {
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
     * State class for tracking MST construction progress
     */
    public static class MSTState {
        public final int step;
        public final int currentWeight;
        public final int componentsRemaining;
        public final List<Edge> mstEdges;
        
        public MSTState(int step, int currentWeight, int componentsRemaining, List<Edge> mstEdges) {
            this.step = step;
            this.currentWeight = currentWeight;
            this.componentsRemaining = componentsRemaining;
            this.mstEdges = mstEdges;
        }
        
        @Override
        public String toString() {
            return String.format("Step %d: Weight=%d, Components=%d, Edges=%d", 
                               step, currentWeight, componentsRemaining, mstEdges.size());
        }
    }
    
    /**
     * Demonstration and Testing
     */
    public static void main(String[] args) {
        System.out.println("=== Kruskal's MST Algorithm Demonstrations ===\n");
        
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
        
        // Test 1: Basic Kruskal's
        System.out.println("Test 1: Basic MST");
        int mstWeight = kruskalBasic(n, new ArrayList<>(edges));
        System.out.println("MST Total Weight: " + mstWeight);
        System.out.println();
        
        // Test 2: MST with edges
        System.out.println("Test 2: MST with Edge Details");
        KruskalResult result = kruskalWithEdges(n, new ArrayList<>(edges));
        System.out.println(result);
        System.out.println("MST Edges:");
        for (Edge edge : result.mstEdges) {
            System.out.println("  " + edge);
        }
        System.out.println();
        
        // Test 3: Maximum Spanning Tree
        System.out.println("Test 3: Maximum Spanning Tree");
        int maxST = kruskalMaximumST(n, new ArrayList<>(edges));
        System.out.println("Maximum ST Weight: " + maxST);
        System.out.println();
        
        // Test 4: MST Construction Progress
        System.out.println("Test 4: MST Construction Progress");
        List<MSTState> progress = kruskalWithProgress(n, new ArrayList<>(edges));
        for (MSTState state : progress) {
            System.out.println(state);
        }
        System.out.println();
        
        // Test 5: Second-Best MST
        System.out.println("Test 5: Second-Best MST");
        int secondBest = secondBestMST(n, new ArrayList<>(edges));
        System.out.println("Second-Best MST Weight: " + secondBest);
        System.out.println();
        
        // Test 6: Disconnected graph
        System.out.println("Test 6: Disconnected Graph");
        List<Edge> disconnectedEdges = new ArrayList<>();
        disconnectedEdges.add(new Edge(0, 1, 1));
        disconnectedEdges.add(new Edge(2, 3, 2));
        int disconnectedMST = kruskalBasic(4, disconnectedEdges);
        System.out.println("MST Weight (should be MAX): " + 
                         (disconnectedMST == Integer.MAX_VALUE ? "DISCONNECTED" : disconnectedMST));
    }
}



