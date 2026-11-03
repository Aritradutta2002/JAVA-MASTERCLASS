package dsa.datastructures.graphs.components;
import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Bipartite Graph Detection - Multiple Implementations
 * 
 * A bipartite graph is a graph whose vertices can be divided into two disjoint sets
 * such that no two vertices within the same set are adjacent.
 * 
 * Key Properties:
 * - Can be colored with exactly 2 colors
 * - Contains no odd-length cycles
 * - Every tree is bipartite
 * - Complete bipartite graphs (K_{m,n}) are important special cases
 * 
 * Time Complexity: O(V + E) for all approaches
 * Space Complexity: O(V)
 * 
 * Common MAANG Applications:
 * - Matching problems (stable marriage, job assignment)
 * - Network flow (source-sink separation)
 * - Conflict resolution (resource allocation)
 * - Social network analysis (two-party systems)
 * - Scheduling problems (alternating constraints)
 * - Graph coloring optimizations
 */
public class BipartiteCheck {
    
    // Color constants for better readability
    private static final int UNCOLORED = -1;
    private static final int COLOR_0 = 0;
    private static final int COLOR_1 = 1;
    
    /**
     * APPROACH 1: BFS-based Bipartite Check
     * 
     * Uses breadth-first search to color vertices level by level.
     * Natural choice as BFS explores neighbors systematically.
     * 
     * Algorithm:
     * 1. For each uncolored component, start BFS with color 0
     * 2. Color neighbors with alternate color
     * 3. If neighbor already has same color, graph is not bipartite
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph (can be directed or undirected)
     * @return true if graph is bipartite
     */
    public static boolean isBipartiteBFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        // Check each connected component
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == UNCOLORED) {
                if (!bfsColorComponent(graph, vertex, color)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Helper method for BFS-based coloring of a single component
     * 
     * @param graph Input graph
     * @param startVertex Starting vertex for this component
     * @param color Color array to update
     * @return true if component is bipartite
     */
    private static boolean bfsColorComponent(Graph graph, int startVertex, int[] color) {
        Queue<Integer> queue = new ArrayDeque<>();
        queue.offer(startVertex);
        color[startVertex] = COLOR_0;
        
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            int currentColor = color[currentVertex];
            int neighborColor = (currentColor == COLOR_0) ? COLOR_1 : COLOR_0;
            
            // Check all neighbors
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (color[neighbor] == UNCOLORED) {
                    // Color the neighbor with alternate color
                    color[neighbor] = neighborColor;
                    queue.offer(neighbor);
                } else if (color[neighbor] == currentColor) {
                    // Conflict: neighbor has same color
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * APPROACH 2: DFS-based Bipartite Check
     * 
     * Uses depth-first search for coloring. Often more memory efficient
     * than BFS for deep graphs.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V) including recursion stack
     * 
     * @param graph Input graph
     * @return true if graph is bipartite
     */
    public static boolean isBipartiteDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        // Check each connected component
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == UNCOLORED) {
                if (!dfsColorComponent(graph, vertex, COLOR_0, color)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Helper method for DFS-based coloring
     * 
     * @param graph Input graph
     * @param vertex Current vertex
     * @param vertexColor Color to assign to current vertex
     * @param color Color array
     * @return true if subtree rooted at vertex can be colored properly
     */
    private static boolean dfsColorComponent(Graph graph, int vertex, int vertexColor, int[] color) {
        color[vertex] = vertexColor;
        int neighborColor = (vertexColor == COLOR_0) ? COLOR_1 : COLOR_0;
        
        for (int neighbor : graph.adj.get(vertex)) {
            if (color[neighbor] == UNCOLORED) {
                // Recursively color uncolored neighbor
                if (!dfsColorComponent(graph, neighbor, neighborColor, color)) {
                    return false;
                }
            } else if (color[neighbor] == vertexColor) {
                // Conflict: neighbor has same color
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * APPROACH 3: Iterative DFS (Stack-based)
     * 
     * Uses explicit stack to avoid recursion limits for very deep graphs.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return true if graph is bipartite
     */
    public static boolean isBipartiteIterativeDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        // Check each connected component
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == UNCOLORED) {
                if (!iterativeDfsColorComponent(graph, vertex, color)) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * Helper method for iterative DFS coloring
     */
    private static boolean iterativeDfsColorComponent(Graph graph, int startVertex, int[] color) {
        Deque<Integer> stack = new ArrayDeque<>();
        stack.push(startVertex);
        color[startVertex] = COLOR_0;
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            int currentColor = color[currentVertex];
            int neighborColor = (currentColor == COLOR_0) ? COLOR_1 : COLOR_0;
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (color[neighbor] == UNCOLORED) {
                    color[neighbor] = neighborColor;
                    stack.push(neighbor);
                } else if (color[neighbor] == currentColor) {
                    return false;
                }
            }
        }
        
        return true;
    }
    
    /**
     * APPROACH 4: Bipartite Check with Partition Return
     * 
     * Not only checks if graph is bipartite but also returns the actual partition
     * if it exists. Useful for problems that need the actual sets.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return BipartiteResult containing the result and partition if bipartite
     */
    public static BipartiteResult getBipartitePartition(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        // Try to color the graph
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == UNCOLORED) {
                if (!bfsColorComponent(graph, vertex, color)) {
                    return new BipartiteResult(false, null, null);
                }
            }
        }
        
        // If we reach here, graph is bipartite - create partitions
        List<Integer> set0 = new ArrayList<>();
        List<Integer> set1 = new ArrayList<>();
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == COLOR_0) {
                set0.add(vertex);
            } else if (color[vertex] == COLOR_1) {
                set1.add(vertex);
            }
        }
        
        return new BipartiteResult(true, set0, set1);
    }
    
    /**
     * APPROACH 5: Odd Cycle Detection
     * 
     * Alternative approach: a graph is bipartite if and only if it contains no odd cycles.
     * This method detects odd cycles using DFS.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return true if graph is bipartite (no odd cycles)
     */
    public static boolean isBipartiteOddCycleDetection(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == UNCOLORED) {
                if (hasOddCycle(graph, vertex, COLOR_0, color)) {
                    return false; // Found odd cycle
                }
            }
        }
        
        return true; // No odd cycles found
    }
    
    /**
     * Helper method to detect odd cycles using DFS
     */
    private static boolean hasOddCycle(Graph graph, int vertex, int vertexColor, int[] color) {
        color[vertex] = vertexColor;
        
        for (int neighbor : graph.adj.get(vertex)) {
            if (color[neighbor] == UNCOLORED) {
                int neighborColor = (vertexColor == COLOR_0) ? COLOR_1 : COLOR_0;
                if (hasOddCycle(graph, neighbor, neighborColor, color)) {
                    return true;
                }
            } else if (color[neighbor] == vertexColor) {
                return true; // Same color = odd cycle
            }
        }
        
        return false;
    }
    
    /**
     * Utility method to find all connected components and check each
     * 
     * @param graph Input graph
     * @return List of component bipartiteness results
     */
    public static List<Boolean> checkComponentsBipartiteness(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        List<Boolean> componentResults = new ArrayList<>();
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                // Find component using BFS
                List<Integer> component = new ArrayList<>();
                Queue<Integer> queue = new ArrayDeque<>();
                queue.offer(vertex);
                visited[vertex] = true;
                
                while (!queue.isEmpty()) {
                    int current = queue.poll();
                    component.add(current);
                    
                    for (int neighbor : graph.adj.get(current)) {
                        if (!visited[neighbor]) {
                            visited[neighbor] = true;
                            queue.offer(neighbor);
                        }
                    }
                }
                
                // Check if this component is bipartite
                boolean isComponentBipartite = checkSingleComponent(graph, component);
                componentResults.add(isComponentBipartite);
            }
        }
        
        return componentResults;
    }
    
    /**
     * Helper method to check if a single component is bipartite
     */
    private static boolean checkSingleComponent(Graph graph, List<Integer> component) {
        if (component.isEmpty()) return true;
        
        int[] color = new int[graph.vertexCount];
        Arrays.fill(color, UNCOLORED);
        
        return bfsColorComponent(graph, component.get(0), color);
    }
    
    /**
     * Utility method to count the maximum size of independent sets
     * In a bipartite graph, this is the size of the larger partition
     * 
     * @param graph Input graph
     * @return Maximum independent set size, -1 if not bipartite
     */
    public static int maxIndependentSetSize(Graph graph) {
        BipartiteResult result = getBipartitePartition(graph);
        
        if (!result.isBipartite) {
            return -1; // Not bipartite
        }
        
        return Math.max(result.set0.size(), result.set1.size());
    }
    
    /**
     * Result class for bipartite partition
     */
    public static class BipartiteResult {
        public final boolean isBipartite;
        public final List<Integer> set0;
        public final List<Integer> set1;
        
        public BipartiteResult(boolean isBipartite, List<Integer> set0, List<Integer> set1) {
            this.isBipartite = isBipartite;
            this.set0 = set0;
            this.set1 = set1;
        }
        
        @Override
        public String toString() {
            if (!isBipartite) {
                return "Graph is not bipartite";
            }
            return String.format("Bipartite: Set0=%s, Set1=%s", set0, set1);
        }
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        System.out.println("=== Bipartite Graph Detection Demonstrations ===");
        
        // Test Case 1: Simple bipartite graph (square)
        // 0 -- 1
        // |    |
        // 3 -- 2
        Graph bipartiteGraph = new Graph(4, false);
        bipartiteGraph.addEdge(0, 1);
        bipartiteGraph.addEdge(1, 2);
        bipartiteGraph.addEdge(2, 3);
        bipartiteGraph.addEdge(3, 0);
        
        System.out.println("Bipartite Graph (Square) Tests:");
        System.out.println("BFS Check: " + isBipartiteBFS(bipartiteGraph));
        System.out.println("DFS Check: " + isBipartiteDFS(bipartiteGraph));
        System.out.println("Iterative DFS: " + isBipartiteIterativeDFS(bipartiteGraph));
        System.out.println("Odd Cycle Detection: " + isBipartiteOddCycleDetection(bipartiteGraph));
        
        BipartiteResult partition = getBipartitePartition(bipartiteGraph);
        System.out.println("Partition: " + partition);
        System.out.println("Max Independent Set Size: " + maxIndependentSetSize(bipartiteGraph));
        
        // Test Case 2: Non-bipartite graph (triangle)
        // 0 -- 1
        //  \ /
        //   2
        Graph nonBipartiteGraph = new Graph(3, false);
        nonBipartiteGraph.addEdge(0, 1);
        nonBipartiteGraph.addEdge(1, 2);
        nonBipartiteGraph.addEdge(2, 0);
        
        System.out.println("\nNon-Bipartite Graph (Triangle) Tests:");
        System.out.println("BFS Check: " + isBipartiteBFS(nonBipartiteGraph));
        System.out.println("DFS Check: " + isBipartiteDFS(nonBipartiteGraph));
        System.out.println("Odd Cycle Detection: " + isBipartiteOddCycleDetection(nonBipartiteGraph));
        
        BipartiteResult nonBipartitePartition = getBipartitePartition(nonBipartiteGraph);
        System.out.println("Partition: " + nonBipartitePartition);
        
        // Test Case 3: Disconnected graph with mixed components
        Graph mixedGraph = new Graph(6, false);
        // Component 1: Bipartite (0-1)
        mixedGraph.addEdge(0, 1);
        // Component 2: Non-bipartite triangle (2-3-4-2)
        mixedGraph.addEdge(2, 3);
        mixedGraph.addEdge(3, 4);
        mixedGraph.addEdge(4, 2);
        // Component 3: Single vertex (5)
        
        System.out.println("\nMixed Components Tests:");
        System.out.println("Overall Bipartite: " + isBipartiteBFS(mixedGraph));
        
        List<Boolean> componentResults = checkComponentsBipartiteness(mixedGraph);
        System.out.println("Component-wise Results: " + componentResults);
    }
}



