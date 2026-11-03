package dsa.datastructures.graphs.traversal;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Depth-First Search (DFS) - Recursive Implementations
 * 
 * DFS explores as far as possible along each branch before backtracking.
 * This file provides multiple recursive DFS variants commonly used in MAANG interviews.
 * 
 * Time Complexity: O(V + E) where V = vertices, E = edges
 * Space Complexity: O(V) for recursion stack and visited array
 * 
 * Key Properties:
 * - Explores deep before exploring wide
 * - Uses recursion stack (implicit stack)
 * - Natural for tree-like problems
 * - Can detect cycles and find strongly connected components
 * 
 * Common MAANG Applications:
 * - Tree traversals
 * - Cycle detection
 * - Topological sorting
 * - Connected components
 * - Path finding with constraints
 * - Backtracking problems
 */
public class DFSRecursive {
    
    /**
     * APPROACH 1: Basic DFS Traversal
     * 
     * Standard recursive DFS that visits all reachable vertices from source.
     * Records the order in which vertices are visited (pre-order).
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V) for recursion stack
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @param visited Boolean array to track visited vertices
     * @param visitOrder List to record visit order
     */
    public static void dfsTraversal(Graph graph, int source, boolean[] visited, List<Integer> visitOrder) {
        // Mark current vertex as visited
        visited[source] = true;
        visitOrder.add(source);
        
        // Recursively visit all unvisited neighbors
        for (int neighbor : graph.adj.get(source)) {
            if (!visited[neighbor]) {
                dfsTraversal(graph, neighbor, visited, visitOrder);
            }
        }
    }
    
    /**
     * APPROACH 2: Complete DFS of All Components
     * 
     * Performs DFS on all connected components in the graph.
     * Essential for disconnected graphs where some vertices might be unreachable.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return List containing all vertices in DFS order across all components
     */
    public static List<Integer> dfsAllComponents(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        List<Integer> completeOrder = new ArrayList<>();
        
        // Visit all unvisited vertices (handles disconnected components)
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                dfsTraversal(graph, vertex, visited, completeOrder);
            }
        }
        
        return completeOrder;
    }
    
    /**
     * APPROACH 3: DFS with Path Tracking
     * 
     * Enhanced DFS that tracks the current path from source to current vertex.
     * Useful for finding all paths, detecting cycles, or path-based constraints.
     * 
     * Time Complexity: O(V + E) for simple traversal, O(V!) worst case for all paths
     * Space Complexity: O(V) for path and recursion stack
     * 
     * @param graph Input graph
     * @param current Current vertex
     * @param target Target vertex
     * @param visited Boolean array to track visited vertices
     * @param currentPath Current path from source to current vertex
     * @param allPaths List to store all found paths
     */
    public static void dfsAllPaths(Graph graph, int current, int target, 
                                   boolean[] visited, List<Integer> currentPath, 
                                   List<List<Integer>> allPaths) {
        // Add current vertex to path
        currentPath.add(current);
        visited[current] = true;
        
        // If we reached the target, save the path
        if (current == target) {
            allPaths.add(new ArrayList<>(currentPath));
        } else {
            // Continue exploring neighbors
            for (int neighbor : graph.adj.get(current)) {
                if (!visited[neighbor]) {
                    dfsAllPaths(graph, neighbor, target, visited, currentPath, allPaths);
                }
            }
        }
        
        // Backtrack: remove current vertex from path and mark as unvisited
        currentPath.remove(currentPath.size() - 1);
        visited[current] = false;
    }
    
    /**
     * APPROACH 4: DFS with Pre and Post Order
     * 
     * Records both when we first visit a vertex (pre-order) and when we finish
     * processing it (post-order). Critical for topological sorting and SCC.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @param visited Boolean array to track visited vertices
     * @param preOrder List to record pre-order visits
     * @param postOrder List to record post-order visits
     */
    public static void dfsPrePost(Graph graph, int source, boolean[] visited,
                                  List<Integer> preOrder, List<Integer> postOrder) {
        visited[source] = true;
        preOrder.add(source);  // Record when we first visit
        
        for (int neighbor : graph.adj.get(source)) {
            if (!visited[neighbor]) {
                dfsPrePost(graph, neighbor, visited, preOrder, postOrder);
            }
        }
        
        postOrder.add(source);  // Record when we finish processing
    }
    
    /**
     * APPROACH 5: DFS for Cycle Detection (Undirected Graph)
     * 
     * Detects cycles in undirected graphs using DFS.
     * A cycle exists if we encounter a visited vertex that's not the parent.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input undirected graph
     * @param current Current vertex
     * @param parent Parent vertex (to avoid false cycle detection)
     * @param visited Boolean array to track visited vertices
     * @return true if cycle is detected
     */
    public static boolean hasCycleUndirected(Graph graph, int current, int parent, boolean[] visited) {
        visited[current] = true;
        
        for (int neighbor : graph.adj.get(current)) {
            if (!visited[neighbor]) {
                // Recursively check neighbors
                if (hasCycleUndirected(graph, neighbor, current, visited)) {
                    return true;
                }
            } else if (neighbor != parent) {
                // Found a visited vertex that's not the parent - cycle detected
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 6: DFS for Connected Components
     * 
     * Finds all connected components in an undirected graph.
     * Each DFS call explores one complete component.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return List of components, each component is a list of vertices
     */
    public static List<List<Integer>> findConnectedComponents(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        List<List<Integer>> components = new ArrayList<>();
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                List<Integer> component = new ArrayList<>();
                dfsTraversal(graph, vertex, visited, component);
                components.add(component);
            }
        }
        
        return components;
    }
    
    /**
     * Utility method to find all paths between two vertices
     * 
     * @param graph Input graph
     * @param source Source vertex
     * @param target Target vertex
     * @return List of all paths from source to target
     */
    public static List<List<Integer>> findAllPaths(Graph graph, int source, int target) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        validateVertex(target, graph.vertexCount);
        
        List<List<Integer>> allPaths = new ArrayList<>();
        boolean[] visited = new boolean[graph.vertexCount];
        List<Integer> currentPath = new ArrayList<>();
        
        dfsAllPaths(graph, source, target, visited, currentPath, allPaths);
        return allPaths;
    }
    
    /**
     * Utility method to check if graph has cycle (for undirected graphs)
     * 
     * @param graph Input undirected graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycle(Graph graph) {
        if (graph == null || graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        boolean[] visited = new boolean[graph.vertexCount];
        
        // Check each component for cycles
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (hasCycleUndirected(graph, vertex, -1, visited)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Validates if vertex is within valid range
     */
    private static void validateVertex(int vertex, int vertexCount) {
        if (vertex < 0 || vertex >= vertexCount) {
            throw new IllegalArgumentException(
                String.format("Vertex %d is out of bounds [0, %d)", vertex, vertexCount)
            );
        }
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        // Create sample graph: 0-1-3
        //                      |   |
        //                      2-4 5
        Graph graph = new Graph(6, false);
        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(2, 4);
        graph.addEdge(3, 5);
        
        System.out.println("=== DFS Recursive Demonstrations ===");
        
        // Test 1: Complete DFS traversal
        List<Integer> dfsOrder = dfsAllComponents(graph);
        System.out.println("DFS traversal order: " + dfsOrder);
        
        // Test 2: Connected components
        List<List<Integer>> components = findConnectedComponents(graph);
        System.out.println("Connected components: " + components);
        
        // Test 3: All paths between vertices
        List<List<Integer>> allPaths = findAllPaths(graph, 0, 5);
        System.out.println("All paths from 0 to 5: " + allPaths);
        
        // Test 4: Pre-order and Post-order
        boolean[] visited = new boolean[graph.vertexCount];
        List<Integer> preOrder = new ArrayList<>();
        List<Integer> postOrder = new ArrayList<>();
        dfsPrePost(graph, 0, visited, preOrder, postOrder);
        System.out.println("Pre-order: " + preOrder);
        System.out.println("Post-order: " + postOrder);
        
        // Test 5: Cycle detection
        System.out.println("Has cycle: " + hasCycle(graph));
        
        // Add cycle and test again
        graph.addEdge(4, 5);
        System.out.println("After adding edge 4-5, has cycle: " + hasCycle(graph));
    }
}



