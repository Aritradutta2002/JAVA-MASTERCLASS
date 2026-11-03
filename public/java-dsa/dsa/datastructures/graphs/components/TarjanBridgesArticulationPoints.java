package dsa.datastructures.graphs.components;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Tarjan's Algorithm - Bridges and Articulation Points
 * 
 * Tarjan's algorithm finds critical edges (bridges) and critical vertices 
 * (articulation points/cut vertices) in an undirected graph using DFS.
 * 
 * Time Complexity: O(V + E) - single DFS traversal
 * Space Complexity: O(V) for recursion stack and arrays
 * 
 * Key Concepts:
 * - Discovery Time: When vertex is first visited in DFS
 * - Low Value: Earliest discovered vertex reachable from subtree
 * - Bridge: Edge (u,v) where low[v] > disc[u]
 * - Articulation Point: Vertex whose removal increases connected components
 * 
 * Bridge Condition:
 * - An edge (u,v) is a bridge if there's no back edge from v or its descendants
 *   that can reach u or any ancestor of u
 * - Formally: low[v] > disc[u]
 * 
 * Articulation Point Conditions:
 * 1. Root of DFS tree with 2+ children
 * 2. Non-root vertex u with child v where low[v] >= disc[u]
 * 
 * Common MAANG Applications:
 * - Network reliability analysis
 * - Finding single points of failure
 * - Road/bridge infrastructure analysis
 * - Social network analysis
 * - Circuit design
 */
public class TarjanBridgesArticulationPoints {
    
    /**
     * APPROACH 1: Find All Bridges (Critical Edges)
     * 
     * A bridge is an edge whose removal increases the number of connected components.
     * Uses Tarjan's algorithm with DFS to identify bridges in O(V + E) time.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param n Number of vertices
     * @param adj Adjacency list (undirected graph)
     * @return List of bridges as [u, v] pairs
     */
    public static List<int[]> findBridges(int n, List<List<Integer>> adj) {
        if (adj == null || adj.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        int[] discoveryTime = new int[n];
        int[] lowValue = new int[n];
        boolean[] visited = new boolean[n];
        
        Arrays.fill(discoveryTime, -1);
        Arrays.fill(lowValue, -1);
        
        List<int[]> bridges = new ArrayList<>();
        int[] time = {0}; // Using array to pass by reference
        
        // Run DFS from all unvisited vertices (handles disconnected graphs)
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsBridges(i, -1, adj, discoveryTime, lowValue, visited, bridges, time);
            }
        }
        
        return bridges;
    }
    
    /**
     * DFS helper for finding bridges
     * 
     * @param u Current vertex
     * @param parent Parent vertex in DFS tree (-1 if root)
     * @param adj Adjacency list
     * @param disc Discovery times
     * @param low Low values
     * @param visited Visited array
     * @param bridges List to store bridges
     * @param time Current time counter
     */
    private static void dfsBridges(int u, int parent, List<List<Integer>> adj,
                                   int[] disc, int[] low, boolean[] visited,
                                   List<int[]> bridges, int[] time) {
        // Mark vertex as visited
        visited[u] = true;
        
        // Initialize discovery time and low value
        disc[u] = low[u] = time[0]++;
        
        // Explore all neighbors
        for (int v : adj.get(u)) {
            // Skip parent edge (undirected graph has back reference)
            if (v == parent) {
                continue;
            }
            
            if (!visited[v]) {
                // Tree edge: recursively visit child
                dfsBridges(v, u, adj, disc, low, visited, bridges, time);
                
                // Update low value based on child's low value
                low[u] = Math.min(low[u], low[v]);
                
                // Bridge condition: no back edge from v to u's ancestors
                if (low[v] > disc[u]) {
                    bridges.add(new int[]{u, v});
                }
            } else {
                // Back edge: update low value based on discovery time
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }
    
    /**
     * APPROACH 2: Find All Articulation Points (Critical Vertices)
     * 
     * An articulation point is a vertex whose removal increases the number 
     * of connected components. Uses Tarjan's algorithm.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param n Number of vertices
     * @param adj Adjacency list (undirected graph)
     * @return Set of articulation points
     */
    public static Set<Integer> findArticulationPoints(int n, List<List<Integer>> adj) {
        if (adj == null || adj.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        int[] discoveryTime = new int[n];
        int[] lowValue = new int[n];
        boolean[] visited = new boolean[n];
        boolean[] isArticulationPoint = new boolean[n];
        
        Arrays.fill(discoveryTime, -1);
        Arrays.fill(lowValue, -1);
        
        int[] time = {0};
        
        // Run DFS from all unvisited vertices
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsArticulationPoints(i, -1, adj, discoveryTime, lowValue, 
                                    visited, isArticulationPoint, time);
            }
        }
        
        // Collect all articulation points
        Set<Integer> articulationPoints = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (isArticulationPoint[i]) {
                articulationPoints.add(i);
            }
        }
        
        return articulationPoints;
    }
    
    /**
     * DFS helper for finding articulation points
     */
    private static void dfsArticulationPoints(int u, int parent, List<List<Integer>> adj,
                                             int[] disc, int[] low, boolean[] visited,
                                             boolean[] isAP, int[] time) {
        visited[u] = true;
        disc[u] = low[u] = time[0]++;
        
        int children = 0; // Count of children in DFS tree
        
        for (int v : adj.get(u)) {
            if (v == parent) {
                continue;
            }
            
            if (!visited[v]) {
                children++;
                
                dfsArticulationPoints(v, u, adj, disc, low, visited, isAP, time);
                
                low[u] = Math.min(low[u], low[v]);
                
                // Articulation point conditions:
                
                // Condition 1: u is root of DFS tree with 2+ children
                if (parent == -1 && children > 1) {
                    isAP[u] = true;
                }
                
                // Condition 2: u is not root and low[v] >= disc[u]
                // (child v cannot reach any ancestor of u without going through u)
                if (parent != -1 && low[v] >= disc[u]) {
                    isAP[u] = true;
                }
            } else {
                // Back edge
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }
    
    /**
     * APPROACH 3: Find Bridges and Articulation Points Together
     * 
     * Combined approach that finds both bridges and articulation points
     * in a single DFS traversal for better efficiency.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param n Number of vertices
     * @param adj Adjacency list
     * @return CriticalElementsResult containing both bridges and articulation points
     */
    public static CriticalElementsResult findCriticalElements(int n, List<List<Integer>> adj) {
        if (adj == null || adj.isEmpty()) {
            throw new IllegalArgumentException("Adjacency list cannot be null or empty");
        }
        
        int[] disc = new int[n];
        int[] low = new int[n];
        boolean[] visited = new boolean[n];
        boolean[] isAP = new boolean[n];
        
        Arrays.fill(disc, -1);
        
        List<int[]> bridges = new ArrayList<>();
        int[] time = {0};
        
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsCombined(i, -1, adj, disc, low, visited, isAP, bridges, time);
            }
        }
        
        Set<Integer> articulationPoints = new HashSet<>();
        for (int i = 0; i < n; i++) {
            if (isAP[i]) {
                articulationPoints.add(i);
            }
        }
        
        return new CriticalElementsResult(bridges, articulationPoints);
    }
    
    /**
     * Combined DFS for both bridges and articulation points
     */
    private static void dfsCombined(int u, int parent, List<List<Integer>> adj,
                                    int[] disc, int[] low, boolean[] visited,
                                    boolean[] isAP, List<int[]> bridges, int[] time) {
        visited[u] = true;
        disc[u] = low[u] = time[0]++;
        int children = 0;
        
        for (int v : adj.get(u)) {
            if (v == parent) {
                continue;
            }
            
            if (!visited[v]) {
                children++;
                dfsCombined(v, u, adj, disc, low, visited, isAP, bridges, time);
                
                low[u] = Math.min(low[u], low[v]);
                
                // Check for bridge
                if (low[v] > disc[u]) {
                    bridges.add(new int[]{u, v});
                }
                
                // Check for articulation point
                if (parent == -1 && children > 1) {
                    isAP[u] = true;
                }
                if (parent != -1 && low[v] >= disc[u]) {
                    isAP[u] = true;
                }
            } else {
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }
    
    /**
     * APPROACH 4: Count Components After Vertex Removal
     * 
     * Alternative brute-force approach to verify articulation points.
     * For each vertex, temporarily remove it and count components.
     * 
     * Time Complexity: O(V * (V + E)) - DFS for each vertex
     * Space Complexity: O(V)
     * 
     * Note: This is NOT optimal, but helps understand the concept.
     * 
     * @param n Number of vertices
     * @param adj Adjacency list
     * @param vertex Vertex to check
     * @return true if vertex is an articulation point
     */
    public static boolean isArticulationPointBruteForce(int n, List<List<Integer>> adj, int vertex) {
        // Count components without removing vertex
        int originalComponents = countComponents(n, adj, -1);
        
        // Count components after removing vertex
        int componentsAfterRemoval = countComponents(n, adj, vertex);
        
        // Articulation point if components increase
        return componentsAfterRemoval > originalComponents;
    }
    
    /**
     * Helper: Count connected components (excluding removedVertex)
     */
    private static int countComponents(int n, List<List<Integer>> adj, int removedVertex) {
        boolean[] visited = new boolean[n];
        if (removedVertex != -1) {
            visited[removedVertex] = true; // Pretend it's already visited
        }
        
        int components = 0;
        
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsCount(i, adj, visited, removedVertex);
                components++;
            }
        }
        
        return components;
    }
    
    /**
     * Helper: DFS for counting components
     */
    private static void dfsCount(int u, List<List<Integer>> adj, boolean[] visited, int removedVertex) {
        visited[u] = true;
        
        for (int v : adj.get(u)) {
            if (!visited[v] && v != removedVertex) {
                dfsCount(v, adj, visited, removedVertex);
            }
        }
    }
    
    /**
     * Utility: Create adjacency list from edge list
     */
    public static List<List<Integer>> createAdjacencyList(int n, List<int[]> edges) {
        List<List<Integer>> adj = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adj.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            adj.get(u).add(v);
            adj.get(v).add(u); // Undirected
        }
        
        return adj;
    }
    
    /**
     * Result class containing critical elements
     */
    public static class CriticalElementsResult {
        public final List<int[]> bridges;
        public final Set<Integer> articulationPoints;
        
        public CriticalElementsResult(List<int[]> bridges, Set<Integer> articulationPoints) {
            this.bridges = bridges;
            this.articulationPoints = articulationPoints;
        }
        
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Critical Elements:\n");
            sb.append("  Bridges: ").append(bridges.size()).append("\n");
            for (int[] bridge : bridges) {
                sb.append("    ").append(bridge[0]).append(" - ").append(bridge[1]).append("\n");
            }
            sb.append("  Articulation Points: ").append(articulationPoints).append("\n");
            return sb.toString();
        }
    }
    
    /**
     * Demonstration and Testing
     */
    public static void main(String[] args) {
        System.out.println("=== Tarjan's Algorithm - Bridges and Articulation Points ===\n");
        
        // Example 1: Simple graph with bridge
        //     0 --- 1 --- 2
        //     |           |
        //     3 --------- 4
        
        System.out.println("Example 1: Graph with Bridge");
        List<int[]> edges1 = Arrays.asList(
            new int[]{0, 1},
            new int[]{0, 3},
            new int[]{1, 2},
            new int[]{2, 4},
            new int[]{3, 4}
        );
        
        List<List<Integer>> adj1 = createAdjacencyList(5, edges1);
        
        List<int[]> bridges1 = findBridges(5, adj1);
        System.out.println("Bridges found: " + bridges1.size());
        for (int[] bridge : bridges1) {
            System.out.println("  Bridge: " + bridge[0] + " - " + bridge[1]);
        }
        
        Set<Integer> ap1 = findArticulationPoints(5, adj1);
        System.out.println("Articulation Points: " + ap1);
        System.out.println();
        
        // Example 2: Star graph (center is articulation point)
        //       1
        //       |
        //   2 - 0 - 3
        //       |
        //       4
        
        System.out.println("Example 2: Star Graph");
        List<int[]> edges2 = Arrays.asList(
            new int[]{0, 1},
            new int[]{0, 2},
            new int[]{0, 3},
            new int[]{0, 4}
        );
        
        List<List<Integer>> adj2 = createAdjacencyList(5, edges2);
        
        List<int[]> bridges2 = findBridges(5, adj2);
        System.out.println("Bridges found: " + bridges2.size());
        for (int[] bridge : bridges2) {
            System.out.println("  Bridge: " + bridge[0] + " - " + bridge[1]);
        }
        
        Set<Integer> ap2 = findArticulationPoints(5, adj2);
        System.out.println("Articulation Points: " + ap2);
        System.out.println();
        
        // Example 3: Combined approach
        System.out.println("Example 3: Combined Analysis");
        CriticalElementsResult result = findCriticalElements(5, adj2);
        System.out.println(result);
        
        // Example 4: Cycle (no bridges or articulation points)
        System.out.println("Example 4: Cycle Graph");
        List<int[]> edges4 = Arrays.asList(
            new int[]{0, 1},
            new int[]{1, 2},
            new int[]{2, 3},
            new int[]{3, 0}
        );
        
        List<List<Integer>> adj4 = createAdjacencyList(4, edges4);
        List<int[]> bridges4 = findBridges(4, adj4);
        Set<Integer> ap4 = findArticulationPoints(4, adj4);
        
        System.out.println("Bridges: " + bridges4.size());
        System.out.println("Articulation Points: " + ap4.size());
        System.out.println();
        
        // Example 5: Brute force verification
        System.out.println("Example 5: Brute Force Verification for Example 2");
        for (int i = 0; i < 5; i++) {
            boolean isAP = isArticulationPointBruteForce(5, adj2, i);
            System.out.println("Vertex " + i + " is articulation point: " + isAP);
        }
    }
}




