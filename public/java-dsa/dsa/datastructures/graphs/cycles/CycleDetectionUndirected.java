package dsa.datastructures.graphs.cycles;

import com.dsa.datastructures.graphs.utils.UnionFind;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Cycle Detection in Undirected Graphs - Multiple Implementations
 * 
 * A cycle in an undirected graph is a closed path of length ≥ 3 that starts and ends
 * at the same vertex without repeating any vertex (except the starting/ending vertex).
 * 
 * Key Properties:
 * - Minimum cycle length is 3 (triangle)
 * - Back edge indicates cycle (excluding edge to immediate parent)
 * - Union-Find naturally detects cycles during edge addition
 * - BFS can also detect cycles using parent tracking
 * 
 * Time Complexity: O(V + E) for DFS/BFS, O(E * α(V)) for Union-Find
 * Space Complexity: O(V)
 * 
 * Common MAANG Applications:
 * - Social network analysis (detecting friend circles)
 * - Network topology validation
 * - Graph connectivity problems
 * - Detecting redundant connections
 * - Circuit analysis in electrical networks
 * - Dependency validation in build systems
 */
public class CycleDetectionUndirected {
    
    /**
     * APPROACH 1: DFS-based Cycle Detection
     * 
     * Uses depth-first search with parent tracking. Most intuitive approach
     * that naturally follows the definition of cycles.
     * 
     * Algorithm:
     * 1. Start DFS from each unvisited vertex
     * 2. For each vertex, track its parent to avoid false positive
     * 3. If we encounter a visited vertex that's not the parent, cycle found
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V) including recursion stack
     * 
     * @param graph Input undirected graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        
        // Check each connected component
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (dfsCheckCycle(graph, vertex, -1, visited)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method for DFS cycle detection
     * 
     * @param graph Input graph
     * @param current Current vertex
     * @param parent Parent vertex (-1 for starting vertex)
     * @param visited Visited array
     * @return true if cycle detected in this component
     */
    private static boolean dfsCheckCycle(Graph graph, int current, int parent, boolean[] visited) {
        visited[current] = true;
        
        for (int neighbor : graph.adj.get(current)) {
            if (!visited[neighbor]) {
                // Recursively check unvisited neighbor
                if (dfsCheckCycle(graph, neighbor, current, visited)) {
                    return true;
                }
            } else if (neighbor != parent) {
                // Found a visited vertex that's not the parent = back edge = cycle
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 2: Iterative DFS Cycle Detection
     * 
     * Uses explicit stack to avoid recursion limits for very deep graphs.
     * Same logic as recursive DFS but with manual stack management.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input undirected graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleIterativeDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (iterativeDfsCheckCycle(graph, vertex, visited)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method for iterative DFS cycle detection
     */
    private static boolean iterativeDfsCheckCycle(Graph graph, int startVertex, boolean[] visited) {
        // Stack stores [current_vertex, parent_vertex]
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startVertex, -1});
        
        while (!stack.isEmpty()) {
            int[] current = stack.pop();
            int vertex = current[0];
            int parent = current[1];
            
            if (visited[vertex]) {
                // If we encounter a visited vertex that's not the parent, cycle found
                if (vertex != parent) {
                    return true;
                }
                continue;
            }
            
            visited[vertex] = true;
            
            for (int neighbor : graph.adj.get(vertex)) {
                if (!visited[neighbor]) {
                    stack.push(new int[]{neighbor, vertex});
                } else if (neighbor != parent) {
                    return true; // Back edge found
                }
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 3: BFS-based Cycle Detection
     * 
     * Uses breadth-first search with parent tracking. Alternative to DFS
     * that explores level by level.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input undirected graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleBFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (bfsCheckCycle(graph, vertex, visited)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method for BFS cycle detection
     */
    private static boolean bfsCheckCycle(Graph graph, int startVertex, boolean[] visited) {
        Queue<int[]> queue = new ArrayDeque<>(); // [vertex, parent]
        queue.offer(new int[]{startVertex, -1});
        visited[startVertex] = true;
        
        while (!queue.isEmpty()) {
            int[] current = queue.poll();
            int vertex = current[0];
            int parent = current[1];
            
            for (int neighbor : graph.adj.get(vertex)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    queue.offer(new int[]{neighbor, vertex});
                } else if (neighbor != parent) {
                    return true; // Back edge found
                }
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 4: Union-Find Cycle Detection
     * 
     * Uses Union-Find (Disjoint Set Union) data structure. Most efficient
     * for dynamic graphs where edges are added incrementally.
     * 
     * Algorithm:
     * 1. Initialize each vertex as its own component
     * 2. For each edge (u,v):
     *    - If u and v are in different components, union them
     *    - If u and v are already connected, adding edge creates cycle
     * 
     * Time Complexity: O(E * α(V)) where α is inverse Ackermann function
     * Space Complexity: O(V)
     * 
     * @param graph Input undirected graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleUnionFind(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        UnionFind unionFind = new UnionFind(graph.vertexCount);
        
        // Check each edge
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                // Only process each edge once (avoid duplicates in undirected graph)
                if (vertex < neighbor) {
                    // If vertices are already connected, adding this edge creates cycle
                    if (!unionFind.union(vertex, neighbor)) {
                        return true;
                    }
                }
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 5: Union-Find with Edge List
     * 
     * Alternative Union-Find approach that works with edge list representation.
     * Useful when edges are provided as separate input.
     * 
     * Time Complexity: O(E * α(V))
     * Space Complexity: O(V)
     * 
     * @param vertexCount Number of vertices
     * @param edges Array of edges [u, v]
     * @return true if adding these edges creates a cycle
     */
    public static boolean hasCycleUnionFindEdgeList(int vertexCount, int[][] edges) {
        if (edges == null) {
            throw new IllegalArgumentException("Edges array cannot be null");
        }
        
        UnionFind unionFind = new UnionFind(vertexCount);
        
        for (int[] edge : edges) {
            if (edge.length != 2) {
                throw new IllegalArgumentException("Each edge must have exactly 2 vertices");
            }
            
            int u = edge[0];
            int v = edge[1];
            
            // Validate vertices
            if (u < 0 || u >= vertexCount || v < 0 || v >= vertexCount) {
                throw new IllegalArgumentException("Vertex indices out of bounds");
            }
            
            if (!unionFind.union(u, v)) {
                return true; // Cycle detected
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 6: Cycle Detection with Cycle Reporting
     * 
     * Not only detects cycles but also reports one of the cycles found.
     * Useful for debugging or when the actual cycle is needed.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input undirected graph
     * @return List representing a cycle, empty if no cycle exists
     */
    public static List<Integer> findCycle(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (graph.isDirected) {
            throw new IllegalArgumentException("This method is for undirected graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(parent, -1);
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                List<Integer> cycle = dfsWithCycleReporting(graph, vertex, visited, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            }
        }
        
        return new ArrayList<>(); // No cycle found
    }
    
    /**
     * Helper method for DFS with cycle reporting
     */
    private static List<Integer> dfsWithCycleReporting(Graph graph, int current, boolean[] visited, int[] parent) {
        visited[current] = true;
        
        for (int neighbor : graph.adj.get(current)) {
            if (!visited[neighbor]) {
                parent[neighbor] = current;
                List<Integer> cycle = dfsWithCycleReporting(graph, neighbor, visited, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            } else if (parent[current] != neighbor) {
                // Found back edge - reconstruct cycle
                List<Integer> cycle = new ArrayList<>();
                cycle.add(neighbor);
                
                int vertex = current;
                while (vertex != neighbor) {
                    cycle.add(vertex);
                    vertex = parent[vertex];
                }
                cycle.add(neighbor); // Complete the cycle
                
                return cycle;
            }
        }
        
        return new ArrayList<>();
    }
    
    /**
     * Utility method to count the number of cycles
     * Note: This counts fundamental cycles (minimum set of cycles)
     * 
     * @param graph Input undirected graph
     * @return Number of fundamental cycles
     */
    public static int countFundamentalCycles(Graph graph) {
        if (graph == null) {
            return 0;
        }
        
        // For a connected graph: cycles = edges - vertices + components
        // For disconnected graph: sum over all components
        
        int vertexCount = graph.vertexCount;
        int edgeCount = graph.getEdgeCount();
        
        // Count connected components
        boolean[] visited = new boolean[vertexCount];
        int componentCount = 0;
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                componentCount++;
                // Mark all vertices in this component as visited
                dfsMarkComponent(graph, vertex, visited);
            }
        }
        
        return edgeCount - vertexCount + componentCount;
    }
    
    /**
     * Helper method to mark all vertices in a component as visited
     */
    private static void dfsMarkComponent(Graph graph, int vertex, boolean[] visited) {
        visited[vertex] = true;
        for (int neighbor : graph.adj.get(vertex)) {
            if (!visited[neighbor]) {
                dfsMarkComponent(graph, neighbor, visited);
            }
        }
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        System.out.println("=== Undirected Cycle Detection Demonstrations ===");
        
        // Test Case 1: Graph with cycle
        // 0 -- 1
        // |    |
        // 3 -- 2
        Graph cyclicGraph = new Graph(4, false);
        cyclicGraph.addEdge(0, 1);
        cyclicGraph.addEdge(1, 2);
        cyclicGraph.addEdge(2, 3);
        cyclicGraph.addEdge(3, 0);
        
        System.out.println("Cyclic Graph Tests:");
        System.out.println("DFS: " + hasCycleDFS(cyclicGraph));
        System.out.println("Iterative DFS: " + hasCycleIterativeDFS(cyclicGraph));
        System.out.println("BFS: " + hasCycleBFS(cyclicGraph));
        System.out.println("Union-Find: " + hasCycleUnionFind(cyclicGraph));
        
        List<Integer> cycle = findCycle(cyclicGraph);
        System.out.println("Found cycle: " + cycle);
        System.out.println("Fundamental cycles count: " + countFundamentalCycles(cyclicGraph));
        
        // Test Case 2: Tree (no cycles)
        // 0 -- 1 -- 3
        // |
        // 2
        Graph tree = new Graph(4, false);
        tree.addEdge(0, 1);
        tree.addEdge(0, 2);
        tree.addEdge(1, 3);
        
        System.out.println("\nTree (Acyclic) Tests:");
        System.out.println("DFS: " + hasCycleDFS(tree));
        System.out.println("BFS: " + hasCycleBFS(tree));
        System.out.println("Union-Find: " + hasCycleUnionFind(tree));
        System.out.println("Found cycle: " + findCycle(tree));
        
        // Test Case 3: Union-Find with edge list
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
        System.out.println("\nEdge List Tests:");
        System.out.println("Union-Find Edge List: " + hasCycleUnionFindEdgeList(4, edges));
        
        // Test Case 4: Multiple cycles
        Graph multiCycleGraph = new Graph(6, false);
        multiCycleGraph.addEdge(0, 1);
        multiCycleGraph.addEdge(1, 2);
        multiCycleGraph.addEdge(2, 0); // First cycle
        multiCycleGraph.addEdge(3, 4);
        multiCycleGraph.addEdge(4, 5);
        multiCycleGraph.addEdge(5, 3); // Second cycle
        
        System.out.println("\nMultiple Cycles Tests:");
        System.out.println("Has cycle: " + hasCycleDFS(multiCycleGraph));
        System.out.println("Fundamental cycles: " + countFundamentalCycles(multiCycleGraph));
    }
}



