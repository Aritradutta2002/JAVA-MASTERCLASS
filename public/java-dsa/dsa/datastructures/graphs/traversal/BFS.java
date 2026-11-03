package dsa.datastructures.graphs.traversal;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Breadth-First Search (BFS) - Multiple Implementations
 * 
 * BFS explores nodes level by level, guaranteeing shortest path in unweighted graphs.
 * This file provides multiple BFS variants commonly asked in MAANG interviews.
 * 
 * Time Complexity: O(V + E) where V = vertices, E = edges
 * Space Complexity: O(V) for queue and distance array
 * 
 * Key Properties:
 * - Finds shortest path in unweighted graphs
 * - Explores nodes in order of their distance from source
 * - Uses queue (FIFO) data structure
 * - Can detect unreachable nodes
 * 
 * Common MAANG Applications:
 * - Shortest path in unweighted graphs
 * - Level-order traversal
 * - Connected components
 * - Bipartite graph checking
 * - Multi-source BFS problems
 */
public class BFS {
    
    private static final int UNREACHABLE = Integer.MAX_VALUE;
    
    /**
     * APPROACH 1: Basic BFS - Distance Calculation
     * 
     * Most fundamental BFS implementation that calculates shortest distances
     * from source to all reachable vertices in unweighted graph.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @return Array of shortest distances (UNREACHABLE for unreachable vertices)
     */
    public static int[] bfsDistances(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        
        // Initialize distances - all vertices are initially unreachable
        int[] distances = new int[vertexCount];
        Arrays.fill(distances, UNREACHABLE);
        
        // BFS using queue (FIFO)
        Queue<Integer> queue = new ArrayDeque<>();
        distances[source] = 0;
        queue.offer(source);
        
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            
            // Explore all neighbors of current vertex
            for (int neighbor : graph.adj.get(currentVertex)) {
                // If neighbor hasn't been visited yet
                if (distances[neighbor] == UNREACHABLE) {
                    distances[neighbor] = distances[currentVertex] + 1;
                    queue.offer(neighbor);
                }
            }
        }
        
        return distances;
    }
    
    /**
     * APPROACH 2: BFS with Path Reconstruction
     * 
     * Enhanced BFS that not only finds distances but also reconstructs
     * the actual shortest path to any vertex.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @param target Target vertex
     * @return List containing shortest path from source to target (empty if no path)
     */
    public static List<Integer> bfsShortestPath(Graph graph, int source, int target) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        validateVertex(target, vertexCount);
        
        // If source equals target, return path containing just the source
        if (source == target) {
            return Arrays.asList(source);
        }
        
        // Track parent of each vertex for path reconstruction
        int[] parent = new int[vertexCount];
        boolean[] visited = new boolean[vertexCount];
        Arrays.fill(parent, -1);
        
        Queue<Integer> queue = new ArrayDeque<>();
        visited[source] = true;
        queue.offer(source);
        
        // Standard BFS with parent tracking
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    parent[neighbor] = currentVertex;
                    queue.offer(neighbor);
                    
                    // Early termination when target is found
                    if (neighbor == target) {
                        return reconstructPath(parent, source, target);
                    }
                }
            }
        }
        
        // No path found
        return new ArrayList<>();
    }
    
    /**
     * APPROACH 3: Multi-Source BFS
     * 
     * Starts BFS from multiple sources simultaneously. Useful for problems
     * like "Rotting Oranges" or finding nearest exit in a maze.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param sources List of starting vertices
     * @return Array of distances from nearest source to each vertex
     */
    public static int[] multiSourceBFS(Graph graph, List<Integer> sources) {
        if (graph == null || sources == null || sources.isEmpty()) {
            throw new IllegalArgumentException("Invalid input parameters");
        }
        
        int vertexCount = graph.vertexCount;
        int[] distances = new int[vertexCount];
        Arrays.fill(distances, UNREACHABLE);
        
        Queue<Integer> queue = new ArrayDeque<>();
        
        // Initialize all sources with distance 0
        for (int source : sources) {
            validateVertex(source, vertexCount);
            distances[source] = 0;
            queue.offer(source);
        }
        
        // Standard BFS from multiple sources
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (distances[neighbor] == UNREACHABLE) {
                    distances[neighbor] = distances[currentVertex] + 1;
                    queue.offer(neighbor);
                }
            }
        }
        
        return distances;
    }
    
    /**
     * APPROACH 4: Level-Order BFS
     * 
     * Processes vertices level by level, useful when you need to know
     * which vertices are at each distance from source.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @return List of lists, where each inner list contains vertices at that level
     */
    public static List<List<Integer>> bfsLevelOrder(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        
        List<List<Integer>> levels = new ArrayList<>();
        boolean[] visited = new boolean[vertexCount];
        Queue<Integer> queue = new ArrayDeque<>();
        
        visited[source] = true;
        queue.offer(source);
        
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            List<Integer> currentLevel = new ArrayList<>();
            
            // Process all vertices at current level
            for (int i = 0; i < levelSize; i++) {
                int currentVertex = queue.poll();
                currentLevel.add(currentVertex);
                
                // Add all unvisited neighbors to next level
                for (int neighbor : graph.adj.get(currentVertex)) {
                    if (!visited[neighbor]) {
                        visited[neighbor] = true;
                        queue.offer(neighbor);
                    }
                }
            }
            
            levels.add(currentLevel);
        }
        
        return levels;
    }
    
    /**
     * Helper method to reconstruct path from parent array
     * 
     * @param parent Parent array from BFS
     * @param source Source vertex
     * @param target Target vertex
     * @return Path from source to target
     */
    private static List<Integer> reconstructPath(int[] parent, int source, int target) {
        List<Integer> path = new ArrayList<>();
        
        // Build path backwards from target to source
        for (int vertex = target; vertex != -1; vertex = parent[vertex]) {
            path.add(vertex);
        }
        
        // Reverse to get path from source to target
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Utility method to check if a vertex is reachable
     * 
     * @param distances Distance array from BFS
     * @param vertex Vertex to check
     * @return true if vertex is reachable from source
     */
    public static boolean isReachable(int[] distances, int vertex) {
        return distances[vertex] != UNREACHABLE;
    }
    
    /**
     * Utility method to get count of reachable vertices
     * 
     * @param distances Distance array from BFS
     * @return Number of vertices reachable from source
     */
    public static int countReachableVertices(int[] distances) {
        int count = 0;
        for (int distance : distances) {
            if (distance != UNREACHABLE) {
                count++;
            }
        }
        return count;
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
        // Create sample graph: 0-1-3-4
        //                      |   |   |
        //                      2---+   5
        Graph graph = new Graph(6, false);
        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(2, 3);
        graph.addEdge(3, 4);
        graph.addEdge(4, 5);
        
        System.out.println("=== BFS Demonstrations ===");
        
        // Test 1: Basic distance calculation
        int[] distances = bfsDistances(graph, 0);
        System.out.println("Distances from vertex 0: " + Arrays.toString(distances));
        System.out.println("Reachable vertices: " + countReachableVertices(distances));
        
        // Test 2: Shortest path
        List<Integer> path = bfsShortestPath(graph, 0, 5);
        System.out.println("Shortest path from 0 to 5: " + path);
        
        // Test 3: Multi-source BFS
        List<Integer> sources = Arrays.asList(0, 4);
        int[] multiDistances = multiSourceBFS(graph, sources);
        System.out.println("Multi-source distances: " + Arrays.toString(multiDistances));
        
        // Test 4: Level-order traversal
        List<List<Integer>> levels = bfsLevelOrder(graph, 0);
        System.out.println("Level-order from vertex 0:");
        for (int i = 0; i < levels.size(); i++) {
            System.out.println("  Level " + i + ": " + levels.get(i));
        }
    }
}



