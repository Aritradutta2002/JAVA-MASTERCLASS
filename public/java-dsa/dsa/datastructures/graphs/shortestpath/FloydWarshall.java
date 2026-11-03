package dsa.datastructures.graphs.shortestpath;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Floyd-Warshall Algorithm - All-Pairs Shortest Path
 * 
 * Floyd-Warshall finds shortest paths between ALL pairs of vertices.
 * It can handle negative edge weights (but not negative cycles).
 * 
 * Time Complexity: O(V³)
 * Space Complexity: O(V²)
 * 
 * Key Properties:
 * - Works with negative edge weights
 * - Detects negative cycles
 * - Dynamic programming approach
 * - Computes all-pairs shortest paths at once
 * - Simple implementation with 3 nested loops
 * 
 * When to Use:
 * - Need shortest paths between ALL pairs of vertices
 * - Dense graphs where V³ is acceptable
 * - Graph may have negative weights
 * - Need to detect negative cycles
 * 
 * Common MAANG Applications:
 * - Network latency analysis
 * - Finding transitive closure
 * - Game theory (checking all possible paths)
 * - Testing graph connectivity
 */
public class FloydWarshall {
    
    private static final int INF = Integer.MAX_VALUE / 2; // Avoid overflow
    
    /**
     * APPROACH 1: Basic Floyd-Warshall (In-Place)
     * 
     * Standard implementation that modifies the input matrix directly.
     * Uses dynamic programming with intermediate vertices.
     * 
     * Core Idea: For each pair (i,j), try all possible intermediate vertices k
     * and see if going through k gives a shorter path.
     * 
     * Time Complexity: O(V³)
     * Space Complexity: O(1) - modifies input in-place
     * 
     * @param dist Distance matrix where dist[i][j] = weight of edge i→j
     *             Use INF for non-existent edges, 0 for same vertex
     * @return Modified dist matrix with shortest paths
     */
    public static int[][] floydWarshallBasic(int[][] dist) {
        if (dist == null || dist.length == 0) {
            throw new IllegalArgumentException("Distance matrix cannot be null or empty");
        }
        
        int n = dist.length;
        
        // For each intermediate vertex k
        for (int k = 0; k < n; k++) {
            // For each source vertex i
            for (int i = 0; i < n; i++) {
                // For each destination vertex j
                for (int j = 0; j < n; j++) {
                    // Check if path i→k→j is shorter than current i→j
                    if (dist[i][k] != INF && dist[k][j] != INF) {
                        if (dist[i][k] + dist[k][j] < dist[i][j]) {
                            dist[i][j] = dist[i][k] + dist[k][j];
                        }
                    }
                }
            }
        }
        
        return dist;
    }
    
    /**
     * APPROACH 2: Floyd-Warshall with Path Reconstruction
     * 
     * Enhanced version that not only finds shortest distances but also
     * reconstructs the actual shortest paths between any pair of vertices.
     * 
     * Time Complexity: O(V³)
     * Space Complexity: O(V²) for parent matrix
     * 
     * @param dist Distance matrix
     * @return FloydWarshallResult containing distances and parent information
     */
    public static FloydWarshallResult floydWarshallWithPaths(int[][] dist) {
        if (dist == null || dist.length == 0) {
            throw new IllegalArgumentException("Distance matrix cannot be null or empty");
        }
        
        int n = dist.length;
        
        // Create copies to avoid modifying input
        int[][] distances = new int[n][n];
        int[][] parent = new int[n][n];
        
        // Initialize
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distances[i][j] = dist[i][j];
                if (i != j && dist[i][j] != INF) {
                    parent[i][j] = i; // Direct edge: parent of j is i
                } else {
                    parent[i][j] = -1; // No path initially
                }
            }
        }
        
        // Floyd-Warshall with path tracking
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (distances[i][k] != INF && distances[k][j] != INF) {
                        if (distances[i][k] + distances[k][j] < distances[i][j]) {
                            distances[i][j] = distances[i][k] + distances[k][j];
                            parent[i][j] = parent[k][j]; // Update parent
                        }
                    }
                }
            }
        }
        
        return new FloydWarshallResult(distances, parent);
    }
    
    /**
     * APPROACH 3: Floyd-Warshall with Negative Cycle Detection
     * 
     * Extended version that detects if graph contains negative cycles.
     * A negative cycle exists if dist[i][i] < 0 for any vertex i.
     * 
     * Time Complexity: O(V³)
     * Space Complexity: O(V²)
     * 
     * @param dist Distance matrix
     * @return FloydWarshallResult with negative cycle information
     */
    public static FloydWarshallResult floydWarshallWithNegativeCycleDetection(int[][] dist) {
        if (dist == null || dist.length == 0) {
            throw new IllegalArgumentException("Distance matrix cannot be null or empty");
        }
        
        int n = dist.length;
        int[][] distances = new int[n][n];
        
        // Copy input
        for (int i = 0; i < n; i++) {
            distances[i] = Arrays.copyOf(dist[i], n);
        }
        
        // Run Floyd-Warshall
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    if (distances[i][k] != INF && distances[k][j] != INF) {
                        distances[i][j] = Math.min(distances[i][j], 
                                                   distances[i][k] + distances[k][j]);
                    }
                }
            }
        }
        
        // Check for negative cycles
        boolean hasNegativeCycle = false;
        for (int i = 0; i < n; i++) {
            if (distances[i][i] < 0) {
                hasNegativeCycle = true;
                break;
            }
        }
        
        FloydWarshallResult result = new FloydWarshallResult(distances, null);
        result.hasNegativeCycle = hasNegativeCycle;
        return result;
    }
    
    /**
     * APPROACH 4: Space-Optimized Floyd-Warshall
     * 
     * Memory-efficient version for when you only need distances,
     * not paths. Works in-place on a copy of input.
     * 
     * Time Complexity: O(V³)
     * Space Complexity: O(V²) for distance matrix copy
     * 
     * @param dist Distance matrix
     * @return 2D array of shortest distances
     */
    public static int[][] floydWarshallOptimized(int[][] dist) {
        int n = dist.length;
        int[][] d = new int[n][n];
        
        // Copy input
        for (int i = 0; i < n; i++) {
            d[i] = Arrays.copyOf(dist[i], n);
        }
        
        // Core algorithm - most concise version
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    d[i][j] = Math.min(d[i][j], d[i][k] + d[k][j]);
                }
            }
        }
        
        return d;
    }
    
    /**
     * APPROACH 5: Transitive Closure (Modified Floyd-Warshall)
     * 
     * Determines reachability between all pairs of vertices.
     * Returns boolean matrix where reach[i][j] = true if path exists from i to j.
     * 
     * Time Complexity: O(V³)
     * Space Complexity: O(V²)
     * 
     * @param adj Adjacency matrix (true if edge exists)
     * @return Reachability matrix
     */
    public static boolean[][] transitiveClosure(boolean[][] adj) {
        int n = adj.length;
        boolean[][] reach = new boolean[n][n];
        
        // Initialize: copy adjacency matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                reach[i][j] = adj[i][j];
            }
            reach[i][i] = true; // Vertex can reach itself
        }
        
        // Floyd-Warshall for reachability
        for (int k = 0; k < n; k++) {
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    reach[i][j] = reach[i][j] || (reach[i][k] && reach[k][j]);
                }
            }
        }
        
        return reach;
    }
    
    /**
     * Utility: Reconstruct path from source to target
     * 
     * @param parent Parent matrix from Floyd-Warshall
     * @param source Source vertex
     * @param target Target vertex
     * @return List representing path from source to target
     */
    public static List<Integer> reconstructPath(int[][] parent, int source, int target) {
        if (parent[source][target] == -1) {
            return new ArrayList<>(); // No path exists
        }
        
        List<Integer> path = new ArrayList<>();
        path.add(source);
        
        while (source != target) {
            source = parent[source][target];
            path.add(source);
        }
        
        return path;
    }
    
    /**
     * Utility: Create distance matrix from edge list
     * 
     * @param n Number of vertices
     * @param edges List of edges [from, to, weight]
     * @param directed Whether graph is directed
     * @return Distance matrix initialized for Floyd-Warshall
     */
    public static int[][] createDistanceMatrix(int n, List<int[]> edges, boolean directed) {
        int[][] dist = new int[n][n];
        
        // Initialize with INF
        for (int i = 0; i < n; i++) {
            Arrays.fill(dist[i], INF);
            dist[i][i] = 0; // Distance to self is 0
        }
        
        // Add edges
        for (int[] edge : edges) {
            int from = edge[0];
            int to = edge[1];
            int weight = edge[2];
            
            dist[from][to] = weight;
            if (!directed) {
                dist[to][from] = weight;
            }
        }
        
        return dist;
    }
    
    /**
     * Utility: Print distance matrix in readable format
     */
    public static void printMatrix(int[][] dist) {
        int n = dist.length;
        System.out.println("Distance Matrix:");
        System.out.print("     ");
        for (int j = 0; j < n; j++) {
            System.out.printf("%5d ", j);
        }
        System.out.println();
        
        for (int i = 0; i < n; i++) {
            System.out.printf("%3d: ", i);
            for (int j = 0; j < n; j++) {
                if (dist[i][j] == INF) {
                    System.out.print("  INF ");
                } else {
                    System.out.printf("%5d ", dist[i][j]);
                }
            }
            System.out.println();
        }
    }
    
    /**
     * Result class containing all Floyd-Warshall computation results
     */
    public static class FloydWarshallResult {
        public final int[][] distances;
        public final int[][] parent;
        public boolean hasNegativeCycle;
        
        public FloydWarshallResult(int[][] distances, int[][] parent) {
            this.distances = distances;
            this.parent = parent;
            this.hasNegativeCycle = false;
        }
        
        public List<Integer> getPath(int source, int target) {
            if (parent == null) {
                throw new UnsupportedOperationException("Path reconstruction not available");
            }
            return reconstructPath(parent, source, target);
        }
        
        public boolean isReachable(int source, int target) {
            return distances[source][target] != INF;
        }
    }
    
    /**
     * Demonstration and Testing
     */
    public static void main(String[] args) {
        System.out.println("=== Floyd-Warshall Algorithm Demonstrations ===\n");
        
        // Example 1: Basic shortest paths
        System.out.println("Example 1: Basic Shortest Paths");
        System.out.println("Graph: 0→1(3), 0→2(8), 1→3(1), 2→1(4), 3→0(2), 3→2(-5)");
        
        int[][] dist1 = {
            {0, 3, 8, INF},
            {INF, 0, INF, 1},
            {INF, 4, 0, INF},
            {2, INF, -5, 0}
        };
        
        int[][] result1 = floydWarshallBasic(Arrays.stream(dist1)
                                            .map(int[]::clone)
                                            .toArray(int[][]::new));
        printMatrix(result1);
        System.out.println();
        
        // Example 2: Path reconstruction
        System.out.println("Example 2: Path Reconstruction");
        int[][] dist2 = {
            {0, 5, INF, 10},
            {INF, 0, 3, INF},
            {INF, INF, 0, 1},
            {INF, INF, INF, 0}
        };
        
        FloydWarshallResult pathResult = floydWarshallWithPaths(dist2);
        System.out.println("Path from 0 to 3: " + pathResult.getPath(0, 3));
        System.out.println("Distance from 0 to 3: " + pathResult.distances[0][3]);
        System.out.println();
        
        // Example 3: Negative cycle detection
        System.out.println("Example 3: Negative Cycle Detection");
        int[][] dist3 = {
            {0, 1, INF},
            {INF, 0, -1},
            {-1, INF, 0}
        };
        
        FloydWarshallResult cycleResult = floydWarshallWithNegativeCycleDetection(dist3);
        System.out.println("Has negative cycle: " + cycleResult.hasNegativeCycle);
        System.out.println();
        
        // Example 4: Transitive closure
        System.out.println("Example 4: Transitive Closure");
        boolean[][] adj = {
            {false, true, false, false},
            {false, false, true, false},
            {false, false, false, true},
            {true, false, false, false}
        };
        
        boolean[][] reach = transitiveClosure(adj);
        System.out.println("Reachability Matrix:");
        for (int i = 0; i < reach.length; i++) {
            System.out.println(Arrays.toString(reach[i]));
        }
    }
}



