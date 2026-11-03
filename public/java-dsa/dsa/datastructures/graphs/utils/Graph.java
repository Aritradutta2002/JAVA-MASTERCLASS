package dsa.datastructures.graphs.utils;

import java.util.ArrayList;
import java.util.List;

/**
 * Graph Representation - Adjacency List Implementation
 * 
 * This class provides a flexible graph representation supporting both:
 * - Weighted and unweighted graphs
 * - Directed and undirected graphs
 * 
 * Time Complexity:
 * - Construction: O(V) where V = number of vertices
 * - Add Edge: O(1) amortized
 * - Space: O(V + E) where E = number of edges
 * 
 * Key Design Decisions:
 * - Uses adjacency lists for memory efficiency with sparse graphs
 * - Maintains both weighted and unweighted representations for flexibility
 * - Immutable vertex count and graph type for safety
 * 
 * Common Usage Patterns:
 * - BFS/DFS: Use unweighted adjacency list (adj)
 * - Shortest Path: Use weighted adjacency list (adjW)
 * - MST algorithms: Use weighted adjacency list (adjW)
 */
public class Graph {
    // Immutable properties for graph safety
    public final int vertexCount;              // Number of vertices (0 to n-1)
    public final boolean isDirected;           // Graph direction type
    
    // Adjacency representations
    public final List<List<Integer>> adj;      // Unweighted: [vertex] -> [neighbors]
    public final List<List<int[]>> adjW;       // Weighted: [vertex] -> [[neighbor, weight]]
    public int n;

    /**
     * Creates a new graph with specified properties
     * 
     * @param vertexCount Number of vertices (vertices will be 0 to vertexCount-1)
     * @param isDirected True for directed graph, false for undirected
     * @throws IllegalArgumentException if vertexCount < 0
     */
    public Graph(int vertexCount, boolean isDirected) {
        if (vertexCount < 0) {
            throw new IllegalArgumentException("Vertex count cannot be negative: " + vertexCount);
        }
        
        this.vertexCount = vertexCount;
        this.isDirected = isDirected;
        
        // Initialize adjacency lists with proper capacity
        this.adj = new ArrayList<>(vertexCount);
        this.adjW = new ArrayList<>(vertexCount);
        
        for (int i = 0; i < vertexCount; i++) {
            adj.add(new ArrayList<>());
            adjW.add(new ArrayList<>());
        }
    }
    
    /**
     * Adds an unweighted edge to the graph
     * 
     * @param from Source vertex
     * @param to Target vertex
     * @throws IllegalArgumentException if vertices are out of bounds
     */
    public void addEdge(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        
        adj.get(from).add(to);
        
        // For undirected graphs, add reverse edge
        if (!isDirected && from != to) { // Avoid duplicate self-loops
            adj.get(to).add(from);
        }
    }
    
    /**
     * Adds a weighted edge to the graph
     * 
     * @param from Source vertex
     * @param to Target vertex
     * @param weight Edge weight
     * @throws IllegalArgumentException if vertices are out of bounds
     */
    public void addWeightedEdge(int from, int to, int weight) {
        validateVertex(from);
        validateVertex(to);
        
        adjW.get(from).add(new int[]{to, weight});
        
        // For undirected graphs, add reverse edge
        if (!isDirected && from != to) { // Avoid duplicate self-loops
            adjW.get(to).add(new int[]{from, weight});
        }
    }
    
    /**
     * Validates if a vertex is within valid range
     * 
     * @param vertex Vertex to validate
     * @throws IllegalArgumentException if vertex is out of bounds
     */
    private void validateVertex(int vertex) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IllegalArgumentException(
                String.format("Vertex %d is out of bounds [0, %d)", vertex, vertexCount)
            );
        }
    }
    
    /**
     * Returns the number of edges in the graph
     * Time Complexity: O(V + E)
     */
    public int getEdgeCount() {
        int edgeCount = 0;
        for (List<Integer> neighbors : adj) {
            edgeCount += neighbors.size();
        }
        
        // For undirected graphs, each edge is counted twice
        return isDirected ? edgeCount : edgeCount / 2;
    }
    
    /**
     * Checks if the graph contains a specific edge
     * Time Complexity: O(degree of from vertex)
     */
    public boolean hasEdge(int from, int to) {
        validateVertex(from);
        validateVertex(to);
        
        return adj.get(from).contains(to);
    }
    
    /**
     * Returns string representation of the graph
     * Useful for debugging and visualization
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Graph: %d vertices, %s\n", 
                  vertexCount, isDirected ? "directed" : "undirected"));
        
        for (int i = 0; i < vertexCount; i++) {
            sb.append(String.format("Vertex %d: %s\n", i, adj.get(i)));
        }
        
        return sb.toString();
    }
}


