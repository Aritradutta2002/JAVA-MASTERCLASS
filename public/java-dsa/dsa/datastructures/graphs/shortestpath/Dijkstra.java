package dsa.datastructures.graphs.shortestpath;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Dijkstra's Shortest Path Algorithm - Multiple Implementations
 * 
 * Dijkstra's algorithm finds shortest paths from a source vertex to all other vertices
 * in a weighted graph with non-negative edge weights.
 * 
 * Time Complexity: O((V + E) log V) with binary heap, O(V² + E) with array
 * Space Complexity: O(V)
 * 
 * Key Properties:
 * - Works only with non-negative edge weights
 * - Greedy algorithm - always picks minimum distance vertex
 * - Uses priority queue for efficient minimum extraction
 * - Guarantees optimal solution for non-negative weights
 * 
 * Common MAANG Applications:
 * - Network routing protocols
 * - GPS navigation systems
 * - Social network analysis (shortest connection paths)
 * - Game AI pathfinding
 * - Flight/transportation scheduling
 */
public class Dijkstra {
    
    private static final int INFINITY = Integer.MAX_VALUE / 2; // Avoid overflow
    
    /**
     * APPROACH 1: Classic Dijkstra with Priority Queue
     * 
     * Standard implementation using binary heap (PriorityQueue).
     * Most commonly used version in interviews.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V)
     * 
     * @param graph Input weighted graph
     * @param source Starting vertex
     * @return Array of shortest distances from source
     */
    public static int[] dijkstraClassic(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        
        // Initialize distances and visited array
        int[] distances = new int[vertexCount];
        boolean[] visited = new boolean[vertexCount];
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        // Priority queue: [vertex, distance]
        PriorityQueue<int[]> priorityQueue = new PriorityQueue<>(
            Comparator.comparingInt(edge -> edge[1])
        );
        priorityQueue.offer(new int[]{source, 0});
        
        while (!priorityQueue.isEmpty()) {
            int[] current = priorityQueue.poll();
            int currentVertex = current[0];
            int currentDistance = current[1];
            
            // Skip if already processed (handles duplicate entries)
            if (visited[currentVertex]) {
                continue;
            }
            
            visited[currentVertex] = true;
            
            // Relax all adjacent edges
            for (int[] edge : graph.adjW.get(currentVertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (!visited[neighbor] && currentDistance + weight < distances[neighbor]) {
                    distances[neighbor] = currentDistance + weight;
                    priorityQueue.offer(new int[]{neighbor, distances[neighbor]});
                }
            }
        }
        
        return distances;
    }
    
    /**
     * APPROACH 2: Dijkstra with Path Reconstruction
     * 
     * Enhanced version that not only finds shortest distances but also
     * reconstructs the actual shortest paths.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V)
     * 
     * @param graph Input weighted graph
     * @param source Starting vertex
     * @return DijkstraResult containing distances and parent information
     */
    public static DijkstraResult dijkstraWithPaths(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        int[] parent = new int[vertexCount];
        boolean[] visited = new boolean[vertexCount];
        
        Arrays.fill(distances, INFINITY);
        Arrays.fill(parent, -1);
        distances[source] = 0;
        
        PriorityQueue<int[]> priorityQueue = new PriorityQueue<>(
            Comparator.comparingInt(edge -> edge[1])
        );
        priorityQueue.offer(new int[]{source, 0});
        
        while (!priorityQueue.isEmpty()) {
            int[] current = priorityQueue.poll();
            int currentVertex = current[0];
            int currentDistance = current[1];
            
            if (visited[currentVertex]) {
                continue;
            }
            
            visited[currentVertex] = true;
            
            for (int[] edge : graph.adjW.get(currentVertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (!visited[neighbor] && currentDistance + weight < distances[neighbor]) {
                    distances[neighbor] = currentDistance + weight;
                    parent[neighbor] = currentVertex;
                    priorityQueue.offer(new int[]{neighbor, distances[neighbor]});
                }
            }
        }
        
        return new DijkstraResult(distances, parent);
    }
    
    /**
     * APPROACH 3: Early Termination Dijkstra
     * 
     * Optimized version that stops as soon as the target vertex is found.
     * Useful when you only need shortest path to a specific target.
     * 
     * Time Complexity: O((V + E) log V) worst case, can be much better
     * Space Complexity: O(V)
     * 
     * @param graph Input weighted graph
     * @param source Starting vertex
     * @param target Target vertex
     * @return Shortest distance from source to target, or INFINITY if unreachable
     */
    public static int dijkstraToTarget(Graph graph, int source, int target) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        validateVertex(target, vertexCount);
        
        if (source == target) {
            return 0;
        }
        
        int[] distances = new int[vertexCount];
        boolean[] visited = new boolean[vertexCount];
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        PriorityQueue<int[]> priorityQueue = new PriorityQueue<>(
            Comparator.comparingInt(edge -> edge[1])
        );
        priorityQueue.offer(new int[]{source, 0});
        
        while (!priorityQueue.isEmpty()) {
            int[] current = priorityQueue.poll();
            int currentVertex = current[0];
            int currentDistance = current[1];
            
            // Early termination when target is reached
            if (currentVertex == target) {
                return currentDistance;
            }
            
            if (visited[currentVertex]) {
                continue;
            }
            
            visited[currentVertex] = true;
            
            for (int[] edge : graph.adjW.get(currentVertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (!visited[neighbor] && currentDistance + weight < distances[neighbor]) {
                    distances[neighbor] = currentDistance + weight;
                    priorityQueue.offer(new int[]{neighbor, distances[neighbor]});
                }
            }
        }
        
        return INFINITY; // Target unreachable
    }
    
    /**
     * APPROACH 4: Array-Based Dijkstra (for Dense Graphs)
     * 
     * Uses simple array instead of priority queue. More efficient for dense graphs
     * where V² dominates (V + E) log V.
     * 
     * Time Complexity: O(V²)
     * Space Complexity: O(V)
     * 
     * @param graph Input weighted graph
     * @param source Starting vertex
     * @return Array of shortest distances from source
     */
    public static int[] dijkstraArray(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        boolean[] visited = new boolean[vertexCount];
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        for (int count = 0; count < vertexCount; count++) {
            // Find minimum distance vertex among unvisited vertices
            int minVertex = findMinDistanceVertex(distances, visited);
            
            if (minVertex == -1 || distances[minVertex] == INFINITY) {
                break; // No more reachable vertices
            }
            
            visited[minVertex] = true;
            
            // Update distances of adjacent vertices
            for (int[] edge : graph.adjW.get(minVertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (!visited[neighbor] && 
                    distances[minVertex] + weight < distances[neighbor]) {
                    distances[neighbor] = distances[minVertex] + weight;
                }
            }
        }
        
        return distances;
    }
    
    /**
     * APPROACH 5: All-Pairs Dijkstra
     * 
     * Runs Dijkstra from every vertex to compute all-pairs shortest paths.
     * Alternative to Floyd-Warshall for sparse graphs.
     * 
     * Time Complexity: O(V * (V + E) log V)
     * Space Complexity: O(V²)
     * 
     * @param graph Input weighted graph
     * @return 2D array where result[i][j] is shortest distance from i to j
     */
    public static int[][] dijkstraAllPairs(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[][] allDistances = new int[vertexCount][vertexCount];
        
        // Run Dijkstra from each vertex
        for (int source = 0; source < vertexCount; source++) {
            allDistances[source] = dijkstraClassic(graph, source);
        }
        
        return allDistances;
    }
    
    /**
     * Helper method to find vertex with minimum distance among unvisited vertices
     * Used in array-based implementation
     */
    private static int findMinDistanceVertex(int[] distances, boolean[] visited) {
        int minDistance = INFINITY;
        int minVertex = -1;
        
        for (int vertex = 0; vertex < distances.length; vertex++) {
            if (!visited[vertex] && distances[vertex] < minDistance) {
                minDistance = distances[vertex];
                minVertex = vertex;
            }
        }
        
        return minVertex;
    }
    
    /**
     * Utility method to reconstruct shortest path from source to target
     * 
     * @param parent Parent array from Dijkstra with paths
     * @param source Source vertex
     * @param target Target vertex
     * @return List representing shortest path from source to target
     */
    public static List<Integer> reconstructPath(int[] parent, int source, int target) {
        if (parent[target] == -1 && target != source) {
            return new ArrayList<>(); // No path exists
        }
        
        List<Integer> path = new ArrayList<>();
        for (int vertex = target; vertex != -1; vertex = parent[vertex]) {
            path.add(vertex);
        }
        
        Collections.reverse(path);
        return path;
    }
    
    /**
     * Utility method to check if a vertex is reachable
     */
    public static boolean isReachable(int[] distances, int vertex) {
        return distances[vertex] != INFINITY;
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
     * Result class for Dijkstra with path reconstruction
     */
    public static class DijkstraResult {
        public final int[] distances;
        public final int[] parent;
        
        public DijkstraResult(int[] distances, int[] parent) {
            this.distances = distances;
            this.parent = parent;
        }
        
        public List<Integer> getPath(int source, int target) {
            return reconstructPath(parent, source, target);
        }
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        // Create sample weighted graph
        //     1    2
        //  0 ---> 1 ---> 3
        //  |      |      |
        //  |4     |1     |3
        //  v      v      v
        //  2 ---> 4 ---> 5
        //     2       1
        
        Graph graph = new Graph(6, true); // Directed graph
        graph.addWeightedEdge(0, 1, 1);
        graph.addWeightedEdge(0, 2, 4);
        graph.addWeightedEdge(1, 3, 2);
        graph.addWeightedEdge(1, 4, 1);
        graph.addWeightedEdge(2, 4, 2);
        graph.addWeightedEdge(3, 5, 3);
        graph.addWeightedEdge(4, 5, 1);
        
        System.out.println("=== Dijkstra Algorithm Demonstrations ===");
        
        // Test 1: Classic Dijkstra
        int[] distances = dijkstraClassic(graph, 0);
        System.out.println("Shortest distances from vertex 0: " + Arrays.toString(distances));
        
        // Test 2: Dijkstra with path reconstruction
        DijkstraResult result = dijkstraWithPaths(graph, 0);
        System.out.println("Distances with paths from vertex 0: " + Arrays.toString(result.distances));
        
        // Show paths to all vertices
        for (int target = 0; target < graph.vertexCount; target++) {
            List<Integer> path = result.getPath(0, target);
            if (!path.isEmpty()) {
                System.out.println("Path to " + target + ": " + path + 
                                 " (distance: " + result.distances[target] + ")");
            }
        }
        
        // Test 3: Early termination to specific target
        int distanceToTarget = dijkstraToTarget(graph, 0, 5);
        System.out.println("Shortest distance from 0 to 5: " + distanceToTarget);
        
        // Test 4: Array-based Dijkstra (for comparison)
        int[] arrayDistances = dijkstraArray(graph, 0);
        System.out.println("Array-based distances: " + Arrays.toString(arrayDistances));
        
        // Test 5: Verify results are consistent
        boolean consistent = Arrays.equals(distances, arrayDistances);
        System.out.println("Results consistent: " + consistent);
    }
}



