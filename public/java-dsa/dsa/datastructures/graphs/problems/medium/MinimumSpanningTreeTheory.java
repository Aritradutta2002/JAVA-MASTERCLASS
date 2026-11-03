package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * Minimum Spanning Tree (MST) Theory and Implementation
 * 
 * A Minimum Spanning Tree is a subset of edges in a weighted, connected, undirected graph
 * that connects all vertices together with the minimum possible total edge weight.
 * 
 * Key Properties:
 * 1. Has exactly V-1 edges for V vertices
 * 2. No cycles
 * 3. All vertices are connected
 * 4. Minimum total weight among all possible spanning trees
 */
public class MinimumSpanningTreeTheory {
    
    /**
     * Edge class for representing weighted edges
     */
    static class Edge implements Comparable<Edge> {
        int src, dest, weight;
        
        Edge(int src, int dest, int weight) {
            this.src = src;
            this.dest = dest;
            this.weight = weight;
        }
        
        @Override
        public int compareTo(Edge other) {
            return Integer.compare(this.weight, other.weight);
        }
        
        @Override
        public String toString() {
            return "(" + src + "-" + dest + ", " + weight + ")";
        }
    }
    
    /**
     * Kruskal's Algorithm - Greedy approach using Union-Find
     * Time Complexity: O(E log E) due to sorting
     * Space Complexity: O(V) for Union-Find structure
     */
    public List<Edge> kruskalMST(int vertices, List<Edge> edges) {
        List<Edge> result = new ArrayList<>();
        
        // Sort edges by weight
        Collections.sort(edges);
        
        // Initialize Union-Find
        UnionFind uf = new UnionFind(vertices);
        
        for (Edge edge : edges) {
            // If including this edge doesn't create a cycle
            if (uf.find(edge.src) != uf.find(edge.dest)) {
                result.add(edge);
                uf.union(edge.src, edge.dest);
                
                // MST has V-1 edges
                if (result.size() == vertices - 1) {
                    break;
                }
            }
        }
        
        return result;
    }
    
    /**
     * Prim's Algorithm - Greedy approach starting from a vertex
     * Time Complexity: O(E log V) with priority queue
     * Space Complexity: O(V + E) for adjacency list and priority queue
     */
    public List<Edge> primMST(int vertices, List<Edge> edges) {
        List<Edge> result = new ArrayList<>();
        
        // Build adjacency list
        Map<Integer, List<Edge>> graph = new HashMap<>();
        for (int i = 0; i < vertices; i++) {
            graph.put(i, new ArrayList<>());
        }
        
        for (Edge edge : edges) {
            graph.get(edge.src).add(edge);
            graph.get(edge.dest).add(new Edge(edge.dest, edge.src, edge.weight));
        }
        
        // Priority queue to get minimum weight edge
        PriorityQueue<Edge> pq = new PriorityQueue<>();
        boolean[] inMST = new boolean[vertices];
        
        // Start from vertex 0
        inMST[0] = true;
        for (Edge edge : graph.get(0)) {
            pq.offer(edge);
        }
        
        while (!pq.isEmpty() && result.size() < vertices - 1) {
            Edge edge = pq.poll();
            
            if (inMST[edge.dest]) {
                continue; // Skip if destination is already in MST
            }
            
            // Add edge to MST
            result.add(edge);
            inMST[edge.dest] = true;
            
            // Add all edges from newly added vertex
            for (Edge nextEdge : graph.get(edge.dest)) {
                if (!inMST[nextEdge.dest]) {
                    pq.offer(nextEdge);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Borůvka's Algorithm - Parallel-friendly MST algorithm
     * Time Complexity: O(E log V)
     * Space Complexity: O(V + E)
     */
    public List<Edge> boruvkaMST(int vertices, List<Edge> edges) {
        List<Edge> result = new ArrayList<>();
        UnionFind uf = new UnionFind(vertices);
        
        // Build adjacency list
        Map<Integer, List<Edge>> graph = new HashMap<>();
        for (int i = 0; i < vertices; i++) {
            graph.put(i, new ArrayList<>());
        }
        
        for (Edge edge : edges) {
            graph.get(edge.src).add(edge);
            graph.get(edge.dest).add(new Edge(edge.dest, edge.src, edge.weight));
        }
        
        int components = vertices;
        
        while (components > 1) {
            Edge[] cheapest = new Edge[vertices];
            
            // Find cheapest edge for each component
            for (Edge edge : edges) {
                int comp1 = uf.find(edge.src);
                int comp2 = uf.find(edge.dest);
                
                if (comp1 != comp2) {
                    if (cheapest[comp1] == null || edge.weight < cheapest[comp1].weight) {
                        cheapest[comp1] = edge;
                    }
                    if (cheapest[comp2] == null || edge.weight < cheapest[comp2].weight) {
                        cheapest[comp2] = edge;
                    }
                }
            }
            
            // Add cheapest edges to MST
            for (int i = 0; i < vertices; i++) {
                if (cheapest[i] != null) {
                    int comp1 = uf.find(cheapest[i].src);
                    int comp2 = uf.find(cheapest[i].dest);
                    
                    if (comp1 != comp2) {
                        result.add(cheapest[i]);
                        uf.union(comp1, comp2);
                        components--;
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * Union-Find data structure with path compression and union by rank
     */
    static class UnionFind {
        private int[] parent;
        private int[] rank;
        
        public UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }
        
        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // Path compression
            }
            return parent[x];
        }
        
        public void union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            
            if (rootX != rootY) {
                // Union by rank
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
    }
    
    /**
     * Calculate total weight of MST
     */
    public int calculateMSTWeight(List<Edge> mst) {
        return mst.stream().mapToInt(edge -> edge.weight).sum();
    }
    
    /**
     * Verify if the given edges form a valid MST
     */
    public boolean isValidMST(int vertices, List<Edge> edges, List<Edge> mst) {
        if (mst.size() != vertices - 1) {
            return false;
        }
        
        // Check if all vertices are connected
        UnionFind uf = new UnionFind(vertices);
        for (Edge edge : mst) {
            uf.union(edge.src, edge.dest);
        }
        
        int root = uf.find(0);
        for (int i = 1; i < vertices; i++) {
            if (uf.find(i) != root) {
                return false;
            }
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumSpanningTreeTheory mst = new MinimumSpanningTreeTheory();
        
        // Create test graph
        List<Edge> edges = Arrays.asList(
            new Edge(0, 1, 10),
            new Edge(0, 2, 6),
            new Edge(0, 3, 5),
            new Edge(1, 3, 15),
            new Edge(2, 3, 4)
        );
        
        int vertices = 4;
        
        System.out.println("Original edges: " + edges);
        
        // Test Kruskal's algorithm
        List<Edge> kruskalResult = mst.kruskalMST(vertices, new ArrayList<>(edges));
        System.out.println("Kruskal's MST: " + kruskalResult);
        System.out.println("Kruskal's MST weight: " + mst.calculateMSTWeight(kruskalResult));
        
        // Test Prim's algorithm
        List<Edge> primResult = mst.primMST(vertices, new ArrayList<>(edges));
        System.out.println("Prim's MST: " + primResult);
        System.out.println("Prim's MST weight: " + mst.calculateMSTWeight(primResult));
        
        // Test Borůvka's algorithm
        List<Edge> boruvkaResult = mst.boruvkaMST(vertices, new ArrayList<>(edges));
        System.out.println("Borůvka's MST: " + boruvkaResult);
        System.out.println("Borůvka's MST weight: " + mst.calculateMSTWeight(boruvkaResult));
        
        // Verify MSTs
        System.out.println("Kruskal's MST valid: " + mst.isValidMST(vertices, edges, kruskalResult));
        System.out.println("Prim's MST valid: " + mst.isValidMST(vertices, edges, primResult));
        System.out.println("Borůvka's MST valid: " + mst.isValidMST(vertices, edges, boruvkaResult));
    }
}

