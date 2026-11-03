package dsa.datastructures.graphs.shortestpath;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Bellman-Ford Shortest Path Algorithm - Multiple Implementations
 * 
 * Bellman-Ford algorithm finds shortest paths from a source vertex to all other vertices
 * in a weighted graph, and can handle negative edge weights (unlike Dijkstra).
 * 
 * Time Complexity: O(V × E)
 * Space Complexity: O(V)
 * 
 * Key Properties:
 * - Works with negative edge weights
 * - Detects negative weight cycles
 * - Uses dynamic programming approach
 * - Relaxes edges V-1 times for shortest paths
 * - One more iteration detects negative cycles
 * 
 * Common MAANG Applications:
 * - Currency arbitrage detection
 * - Network routing with cost considerations
 * - Game theory (finding best strategies)
 * - Resource allocation with penalties
 * - Financial modeling with negative costs
 * - Distance vector routing protocols
 */
public class BellmanFord {
    
    private static final int INFINITY = Integer.MAX_VALUE / 2; // Avoid overflow
    
    /**
     * APPROACH 1: Classic Bellman-Ford Algorithm
     * 
     * Standard implementation that relaxes all edges V-1 times,
     * then checks for negative cycles.
     * 
     * Algorithm:
     * 1. Initialize distances: source = 0, others = infinity
     * 2. Relax all edges V-1 times
     * 3. Check for negative cycles by doing one more iteration
     * 
     * Time Complexity: O(V × E)
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param edges List of edges in the graph
     * @param source Starting vertex
     * @return BellmanFordResult containing distances and negative cycle info
     */
    public static BellmanFordResult bellmanFordClassic(int vertexCount, List<Edge> edges, int source) {
        if (edges == null) {
            throw new IllegalArgumentException("Edges list cannot be null");
        }
        
        validateVertex(source, vertexCount);
        
        // Initialize distances
        int[] distances = new int[vertexCount];
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        // Relax edges V-1 times
        for (int iteration = 0; iteration < vertexCount - 1; iteration++) {
            boolean hasUpdate = false;
            
            for (Edge edge : edges) {
                if (distances[edge.from] != INFINITY && 
                    distances[edge.from] + edge.weight < distances[edge.to]) {
                    distances[edge.to] = distances[edge.from] + edge.weight;
                    hasUpdate = true;
                }
            }
            
            // Early termination if no updates in this iteration
            if (!hasUpdate) {
                break;
            }
        }
        
        // Check for negative cycles
        Set<Integer> negativeCycleVertices = new HashSet<>();
        for (Edge edge : edges) {
            if (distances[edge.from] != INFINITY && 
                distances[edge.from] + edge.weight < distances[edge.to]) {
                negativeCycleVertices.add(edge.to);
            }
        }
        
        return new BellmanFordResult(distances, negativeCycleVertices.isEmpty(), negativeCycleVertices);
    }
    
    /**
     * APPROACH 2: Bellman-Ford with Graph Object
     * 
     * Convenient wrapper that works with our Graph class.
     * Converts adjacency list to edge list internally.
     * 
     * Time Complexity: O(V × E)
     * Space Complexity: O(V + E)
     * 
     * @param graph Input weighted graph
     * @param source Starting vertex
     * @return BellmanFordResult containing distances and negative cycle info
     */
    public static BellmanFordResult bellmanFordGraph(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        
        // Convert graph to edge list
        List<Edge> edges = new ArrayList<>();
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            for (int[] edge : graph.adjW.get(vertex)) {
                int neighbor = edge[0];
                int weight = edge[1];
                edges.add(new Edge(vertex, neighbor, weight));
            }
        }
        
        return bellmanFordClassic(graph.vertexCount, edges, source);
    }
    
    /**
     * APPROACH 3: Bellman-Ford with Path Reconstruction
     * 
     * Enhanced version that also tracks parent information for path reconstruction.
     * Useful when you need the actual shortest paths, not just distances.
     * 
     * Time Complexity: O(V × E)
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param edges List of edges
     * @param source Starting vertex
     * @return BellmanFordPathResult with distances, parents, and cycle info
     */
    public static BellmanFordPathResult bellmanFordWithPaths(int vertexCount, List<Edge> edges, int source) {
        if (edges == null) {
            throw new IllegalArgumentException("Edges list cannot be null");
        }
        
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(distances, INFINITY);
        Arrays.fill(parent, -1);
        distances[source] = 0;
        
        // Relax edges V-1 times
        for (int iteration = 0; iteration < vertexCount - 1; iteration++) {
            boolean hasUpdate = false;
            
            for (Edge edge : edges) {
                if (distances[edge.from] != INFINITY && 
                    distances[edge.from] + edge.weight < distances[edge.to]) {
                    distances[edge.to] = distances[edge.from] + edge.weight;
                    parent[edge.to] = edge.from;
                    hasUpdate = true;
                }
            }
            
            if (!hasUpdate) {
                break;
            }
        }
        
        // Check for negative cycles and track affected vertices
        Set<Integer> negativeCycleVertices = new HashSet<>();
        for (Edge edge : edges) {
            if (distances[edge.from] != INFINITY && 
                distances[edge.from] + edge.weight < distances[edge.to]) {
                negativeCycleVertices.add(edge.to);
            }
        }
        
        return new BellmanFordPathResult(distances, parent, negativeCycleVertices.isEmpty(), negativeCycleVertices);
    }
    
    /**
     * APPROACH 4: SPFA (Shortest Path Faster Algorithm)
     * 
     * Optimized version of Bellman-Ford that uses a queue to process
     * only vertices whose distance has been updated.
     * 
     * Average case is much faster than O(V × E), worst case is still O(V × E).
     * 
     * Time Complexity: O(V × E) worst case, often much better in practice
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param adjacencyList Adjacency list representation [vertex] -> [(neighbor, weight)]
     * @param source Starting vertex
     * @return BellmanFordResult containing distances and negative cycle info
     */
    public static BellmanFordResult spfa(int vertexCount, List<List<int[]>> adjacencyList, int source) {
        if (adjacencyList == null) {
            throw new IllegalArgumentException("Adjacency list cannot be null");
        }
        
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        int[] updateCount = new int[vertexCount]; // Track how many times each vertex is updated
        boolean[] inQueue = new boolean[vertexCount];
        
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(source);
        inQueue[source] = true;
        
        while (!queue.isEmpty()) {
            int current = queue.poll();
            inQueue[current] = false;
            
            // If a vertex is updated more than V times, negative cycle exists
            if (++updateCount[current] > vertexCount) {
                return new BellmanFordResult(distances, false, Set.of(current));
            }
            
            for (int[] edge : adjacencyList.get(current)) {
                int neighbor = edge[0];
                int weight = edge[1];
                
                if (distances[current] + weight < distances[neighbor]) {
                    distances[neighbor] = distances[current] + weight;
                    
                    if (!inQueue[neighbor]) {
                        queue.offer(neighbor);
                        inQueue[neighbor] = true;
                    }
                }
            }
        }
        
        return new BellmanFordResult(distances, true, new HashSet<>());
    }
    
    /**
     * APPROACH 5: Detect Negative Cycle Only
     * 
     * Simplified version when you only need to know if negative cycles exist,
     * not the actual shortest distances.
     * 
     * Time Complexity: O(V × E)
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param edges List of edges
     * @param source Starting vertex
     * @return true if negative cycle is reachable from source
     */
    public static boolean hasNegativeCycle(int vertexCount, List<Edge> edges, int source) {
        if (edges == null) {
            throw new IllegalArgumentException("Edges list cannot be null");
        }
        
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        Arrays.fill(distances, INFINITY);
        distances[source] = 0;
        
        // Relax edges V-1 times
        for (int iteration = 0; iteration < vertexCount - 1; iteration++) {
            for (Edge edge : edges) {
                if (distances[edge.from] != INFINITY && 
                    distances[edge.from] + edge.weight < distances[edge.to]) {
                    distances[edge.to] = distances[edge.from] + edge.weight;
                }
            }
        }
        
        // Check if any edge can still be relaxed
        for (Edge edge : edges) {
            if (distances[edge.from] != INFINITY && 
                distances[edge.from] + edge.weight < distances[edge.to]) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 6: Find Negative Cycle
     * 
     * Not only detects negative cycles but also returns one of them.
     * Uses parent tracking to reconstruct the cycle.
     * 
     * Time Complexity: O(V × E)
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param edges List of edges
     * @param source Starting vertex
     * @return List representing a negative cycle, empty if none exists
     */
    public static List<Integer> findNegativeCycle(int vertexCount, List<Edge> edges, int source) {
        if (edges == null) {
            throw new IllegalArgumentException("Edges list cannot be null");
        }
        
        validateVertex(source, vertexCount);
        
        int[] distances = new int[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(distances, INFINITY);
        Arrays.fill(parent, -1);
        distances[source] = 0;
        
        // Relax edges V-1 times
        for (int iteration = 0; iteration < vertexCount - 1; iteration++) {
            for (Edge edge : edges) {
                if (distances[edge.from] != INFINITY && 
                    distances[edge.from] + edge.weight < distances[edge.to]) {
                    distances[edge.to] = distances[edge.from] + edge.weight;
                    parent[edge.to] = edge.from;
                }
            }
        }
        
        // Find a vertex in negative cycle
        int cycleVertex = -1;
        for (Edge edge : edges) {
            if (distances[edge.from] != INFINITY && 
                distances[edge.from] + edge.weight < distances[edge.to]) {
                cycleVertex = edge.to;
                break;
            }
        }
        
        if (cycleVertex == -1) {
            return new ArrayList<>(); // No negative cycle
        }
        
        // Move V steps to ensure we're inside the cycle
        for (int i = 0; i < vertexCount; i++) {
            cycleVertex = parent[cycleVertex];
        }
        
        // Reconstruct the cycle
        List<Integer> cycle = new ArrayList<>();
        int current = cycleVertex;
        do {
            cycle.add(current);
            current = parent[current];
        } while (current != cycleVertex);
        
        Collections.reverse(cycle);
        return cycle;
    }
    
    /**
     * Utility method to reconstruct shortest path
     * 
     * @param parent Parent array from Bellman-Ford with paths
     * @param source Source vertex
     * @param target Target vertex
     * @return Shortest path from source to target
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
     * Edge class for representing weighted edges
     */
    public static class Edge {
        public final int from;
        public final int to;
        public final int weight;
        
        public Edge(int from, int to, int weight) {
            this.from = from;
            this.to = to;
            this.weight = weight;
        }
        
        @Override
        public String toString() {
            return String.format("(%d -> %d, weight: %d)", from, to, weight);
        }
    }
    
    /**
     * Result class for basic Bellman-Ford
     */
    public static class BellmanFordResult {
        public final int[] distances;
        public final boolean hasNegativeCycle;
        public final Set<Integer> negativeCycleVertices;
        
        public BellmanFordResult(int[] distances, boolean hasNegativeCycle, Set<Integer> negativeCycleVertices) {
            this.distances = distances;
            this.hasNegativeCycle = !hasNegativeCycle;
            this.negativeCycleVertices = negativeCycleVertices;
        }
        
        public boolean isReachable(int vertex) {
            return distances[vertex] != INFINITY;
        }
    }
    
    /**
     * Enhanced result class with path reconstruction capability
     */
    public static class BellmanFordPathResult extends BellmanFordResult {
        public final int[] parent;
        
        public BellmanFordPathResult(int[] distances, int[] parent, boolean hasNegativeCycle, Set<Integer> negativeCycleVertices) {
            super(distances, hasNegativeCycle, negativeCycleVertices);
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
        System.out.println("=== Bellman-Ford Algorithm Demonstrations ===");
        
        // Test Case 1: Graph without negative cycle
        List<Edge> edges1 = Arrays.asList(
            new Edge(0, 1, 4),
            new Edge(0, 2, 2),
            new Edge(1, 3, 3),
            new Edge(2, 1, -2),
            new Edge(2, 3, 4),
            new Edge(3, 4, 1)
        );
        
        System.out.println("Graph without negative cycle:");
        BellmanFordResult result1 = bellmanFordClassic(5, edges1, 0);
        System.out.println("Distances: " + Arrays.toString(result1.distances));
        System.out.println("Has negative cycle: " + result1.hasNegativeCycle);
        
        // Test Case 2: Graph with negative cycle
        List<Edge> edges2 = Arrays.asList(
            new Edge(0, 1, 1),
            new Edge(1, 2, -3),
            new Edge(2, 3, 2),
            new Edge(3, 1, -1)  // Creates negative cycle: 1 -> 2 -> 3 -> 1
        );
        
        System.out.println("\nGraph with negative cycle:");
        BellmanFordResult result2 = bellmanFordClassic(4, edges2, 0);
        System.out.println("Has negative cycle: " + result2.hasNegativeCycle);
        System.out.println("Affected vertices: " + result2.negativeCycleVertices);
        
        List<Integer> cycle = findNegativeCycle(4, edges2, 0);
        System.out.println("Negative cycle: " + cycle);
        
        // Test Case 3: Path reconstruction
        System.out.println("\nPath reconstruction:");
        BellmanFordPathResult pathResult = bellmanFordWithPaths(5, edges1, 0);
        for (int target = 0; target < 5; target++) {
            List<Integer> path = pathResult.getPath(0, target);
            if (!path.isEmpty()) {
                System.out.println("Path to " + target + ": " + path + 
                                 " (distance: " + pathResult.distances[target] + ")");
            }
        }
        
        // Test Case 4: SPFA comparison
        System.out.println("\nSPFA Algorithm:");
        List<List<int[]>> adjList = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            adjList.add(new ArrayList<>());
        }
        
        // Convert edge list to adjacency list for SPFA
        for (Edge edge : edges1) {
            adjList.get(edge.from).add(new int[]{edge.to, edge.weight});
        }
        
        BellmanFordResult spfaResult = spfa(5, adjList, 0);
        System.out.println("SPFA distances: " + Arrays.toString(spfaResult.distances));
        System.out.println("Results match: " + Arrays.equals(result1.distances, spfaResult.distances));
    }
}



