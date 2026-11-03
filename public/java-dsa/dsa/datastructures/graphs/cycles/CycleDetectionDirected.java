package dsa.datastructures.graphs.cycles;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Cycle Detection in Directed Graphs - Multiple Implementations
 * 
 * A cycle in a directed graph is a path of length ≥ 1 that starts and ends
 * at the same vertex, following the direction of edges.
 * 
 * Key Properties:
 * - Back edge in DFS tree indicates cycle
 * - Topological sorting fails if and only if cycles exist
 * - Can detect self-loops (cycles of length 1)
 * - White-Gray-Black coloring helps track DFS states
 * 
 * Time Complexity: O(V + E) for all approaches
 * Space Complexity: O(V)
 * 
 * Common MAANG Applications:
 * - Deadlock detection in systems
 * - Dependency cycle detection (build systems, imports)
 * - Task scheduling validation
 * - Compiler optimization (loop detection)
 * - Process dependency analysis
 * - Course prerequisite validation
 */
public class CycleDetectionDirected {
    
    // DFS state colors for better tracking
    private static final int WHITE = 0;  // Unvisited
    private static final int GRAY = 1;   // Currently being processed (on path)
    private static final int BLACK = 2;  // Completely processed
    
    /**
     * APPROACH 1: DFS-based Cycle Detection with Path Tracking
     * 
     * Uses depth-first search with recursion path tracking. Most intuitive
     * approach that directly implements the cycle definition.
     * 
     * Algorithm:
     * 1. Maintain visited array and current path array
     * 2. For each vertex, perform DFS
     * 3. If we encounter a vertex that's currently on the path, cycle found
     * 4. Remove vertex from path when backtracking
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V) including recursion stack
     * 
     * @param graph Input directed graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("This method is for directed graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        boolean[] onCurrentPath = new boolean[vertexCount];
        
        // Check each unvisited vertex
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (dfsCheckCycle(graph, vertex, visited, onCurrentPath)) {
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
     * @param vertex Current vertex
     * @param visited Global visited array
     * @param onCurrentPath Array tracking vertices on current DFS path
     * @return true if cycle detected from this vertex
     */
    private static boolean dfsCheckCycle(Graph graph, int vertex, boolean[] visited, boolean[] onCurrentPath) {
        visited[vertex] = true;
        onCurrentPath[vertex] = true;
        
        for (int neighbor : graph.adj.get(vertex)) {
            if (!visited[neighbor]) {
                // Recursively check unvisited neighbor
                if (dfsCheckCycle(graph, neighbor, visited, onCurrentPath)) {
                    return true;
                }
            } else if (onCurrentPath[neighbor]) {
                // Back edge found - neighbor is on current path
                return true;
            }
        }
        
        // Remove from current path when backtracking
        onCurrentPath[vertex] = false;
        return false;
    }
    
    /**
     * APPROACH 2: Three-Color DFS
     * 
     * Uses white-gray-black coloring scheme which is more explicit about
     * the DFS states and often preferred in academic settings.
     * 
     * Colors:
     * - White (0): Unvisited
     * - Gray (1): Currently being processed (on DFS path)
     * - Black (2): Completely processed
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleThreeColor(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("This method is for directed graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, WHITE);
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == WHITE) {
                if (dfsThreeColor(graph, vertex, color)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method for three-color DFS
     */
    private static boolean dfsThreeColor(Graph graph, int vertex, int[] color) {
        color[vertex] = GRAY;  // Mark as currently being processed
        
        for (int neighbor : graph.adj.get(vertex)) {
            if (color[neighbor] == GRAY) {
                // Back edge to gray vertex - cycle found
                return true;
            } else if (color[neighbor] == WHITE && dfsThreeColor(graph, neighbor, color)) {
                return true;
            }
        }
        
        color[vertex] = BLACK;  // Mark as completely processed
        return false;
    }
    
    /**
     * APPROACH 3: Kahn's Algorithm (Topological Sort Based)
     * 
     * Uses the fact that a DAG has a topological ordering.
     * If we can't topologically sort all vertices, cycles exist.
     * 
     * Algorithm:
     * 1. Calculate in-degrees for all vertices
     * 2. Process vertices with in-degree 0
     * 3. If we process all vertices, no cycle exists
     * 4. If some vertices remain unprocessed, cycles exist
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleKahn(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("This method is for directed graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        
        // Calculate in-degrees
        int[] inDegree = new int[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                inDegree[neighbor]++;
            }
        }
        
        // Initialize queue with vertices having in-degree 0
        Queue<Integer> queue = new ArrayDeque<>();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (inDegree[vertex] == 0) {
                queue.offer(vertex);
            }
        }
        
        int processedCount = 0;
        
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            processedCount++;
            
            // Remove current vertex and update in-degrees
            for (int neighbor : graph.adj.get(currentVertex)) {
                inDegree[neighbor]--;
                if (inDegree[neighbor] == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // If we couldn't process all vertices, cycles exist
        return processedCount != vertexCount;
    }
    
    /**
     * APPROACH 4: Iterative DFS Cycle Detection
     * 
     * Uses explicit stack to avoid recursion limits. More complex than
     * recursive version but necessary for very deep graphs.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycleIterativeDFS(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("This method is for directed graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        int[] color = new int[vertexCount];
        Arrays.fill(color, WHITE);
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (color[vertex] == WHITE) {
                if (iterativeDfsCheckCycle(graph, vertex, color)) {
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * Helper method for iterative DFS cycle detection
     */
    private static boolean iterativeDfsCheckCycle(Graph graph, int startVertex, int[] color) {
        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startVertex, 0}); // [vertex, phase] where phase: 0=pre, 1=post
        
        while (!stack.isEmpty()) {
            int[] current = stack.pop();
            int vertex = current[0];
            int phase = current[1];
            
            if (phase == 0) {
                // Pre-processing phase
                if (color[vertex] == GRAY) {
                    return true; // Back edge found
                }
                
                if (color[vertex] == WHITE) {
                    color[vertex] = GRAY;
                    stack.push(new int[]{vertex, 1}); // Schedule post-processing
                    
                    // Add neighbors for pre-processing
                    for (int neighbor : graph.adj.get(vertex)) {
                        if (color[neighbor] != BLACK) {
                            stack.push(new int[]{neighbor, 0});
                        }
                    }
                }
            } else {
                // Post-processing phase
                color[vertex] = BLACK;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 5: Cycle Detection with Cycle Reporting
     * 
     * Not only detects cycles but also reports one of the cycles found.
     * Useful for debugging and understanding the cycle structure.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return List representing a cycle, empty if no cycle exists
     */
    public static List<Integer> findCycle(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("This method is for directed graphs only");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        boolean[] onCurrentPath = new boolean[vertexCount];
        int[] parent = new int[vertexCount];
        Arrays.fill(parent, -1);
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                List<Integer> cycle = dfsWithCycleReporting(graph, vertex, visited, onCurrentPath, parent);
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
    private static List<Integer> dfsWithCycleReporting(Graph graph, int vertex, boolean[] visited, 
                                                      boolean[] onCurrentPath, int[] parent) {
        visited[vertex] = true;
        onCurrentPath[vertex] = true;
        
        for (int neighbor : graph.adj.get(vertex)) {
            if (!visited[neighbor]) {
                parent[neighbor] = vertex;
                List<Integer> cycle = dfsWithCycleReporting(graph, neighbor, visited, onCurrentPath, parent);
                if (!cycle.isEmpty()) {
                    return cycle;
                }
            } else if (onCurrentPath[neighbor]) {
                // Found back edge - reconstruct cycle
                List<Integer> cycle = new ArrayList<>();
                
                // Add the cycle starting from neighbor
                int current = vertex;
                while (current != neighbor) {
                    cycle.add(current);
                    current = parent[current];
                }
                cycle.add(neighbor); // Complete the cycle
                
                Collections.reverse(cycle);
                return cycle;
            }
        }
        
        onCurrentPath[vertex] = false;
        return new ArrayList<>();
    }
    
    /**
     * APPROACH 6: Self-Loop Detection
     * 
     * Specifically checks for self-loops (cycles of length 1).
     * Often needed as a preprocessing step or special case.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(1)
     * 
     * @param graph Input directed graph
     * @return true if graph contains self-loops
     */
    public static boolean hasSelfLoop(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                if (neighbor == vertex) {
                    return true; // Self-loop found
                }
            }
        }
        
        return false;
    }
    
    /**
     * Utility method to find all strongly connected components
     * Related to cycle detection - each SCC with >1 vertex contains cycles
     * 
     * @param graph Input directed graph
     * @return Number of strongly connected components
     */
    public static int countStronglyConnectedComponents(Graph graph) {
        if (graph == null) {
            return 0;
        }
        
        // This is a simplified version - real SCC algorithms like Kosaraju's or Tarjan's
        // would be implemented in dedicated files
        // For now, we'll use a basic approach
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        int componentCount = 0;
        
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                componentCount++;
                dfsMarkComponent(graph, vertex, visited);
            }
        }
        
        return componentCount;
    }
    
    /**
     * Helper method to mark component vertices
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
     * Utility method to check if graph is a DAG (Directed Acyclic Graph)
     * 
     * @param graph Input directed graph
     * @return true if graph is acyclic
     */
    public static boolean isDAG(Graph graph) {
        return !hasCycleDFS(graph);
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        System.out.println("=== Directed Cycle Detection Demonstrations ===");
        
        // Test Case 1: Simple cycle
        // 0 -> 1 -> 2 -> 0
        Graph cyclicGraph = new Graph(3, true);
        cyclicGraph.addEdge(0, 1);
        cyclicGraph.addEdge(1, 2);
        cyclicGraph.addEdge(2, 0);
        
        System.out.println("Cyclic Graph Tests:");
        System.out.println("DFS: " + hasCycleDFS(cyclicGraph));
        System.out.println("Three-Color DFS: " + hasCycleThreeColor(cyclicGraph));
        System.out.println("Kahn's Algorithm: " + hasCycleKahn(cyclicGraph));
        System.out.println("Iterative DFS: " + hasCycleIterativeDFS(cyclicGraph));
        
        List<Integer> cycle = findCycle(cyclicGraph);
        System.out.println("Found cycle: " + cycle);
        System.out.println("Is DAG: " + isDAG(cyclicGraph));
        
        // Test Case 2: DAG (no cycles)
        // 0 -> 1 -> 3
        // |    |
        // v    v
        // 2 -> 4
        Graph dag = new Graph(5, true);
        dag.addEdge(0, 1);
        dag.addEdge(0, 2);
        dag.addEdge(1, 3);
        dag.addEdge(1, 4);
        dag.addEdge(2, 4);
        
        System.out.println("\nDAG (Acyclic) Tests:");
        System.out.println("DFS: " + hasCycleDFS(dag));
        System.out.println("Kahn's Algorithm: " + hasCycleKahn(dag));
        System.out.println("Found cycle: " + findCycle(dag));
        System.out.println("Is DAG: " + isDAG(dag));
        
        // Test Case 3: Self-loop
        Graph selfLoopGraph = new Graph(3, true);
        selfLoopGraph.addEdge(0, 1);
        selfLoopGraph.addEdge(1, 1); // Self-loop
        selfLoopGraph.addEdge(1, 2);
        
        System.out.println("\nSelf-Loop Tests:");
        System.out.println("Has self-loop: " + hasSelfLoop(selfLoopGraph));
        System.out.println("DFS detects cycle: " + hasCycleDFS(selfLoopGraph));
        System.out.println("Found cycle: " + findCycle(selfLoopGraph));
        
        // Test Case 4: Complex graph with multiple cycles
        Graph complexGraph = new Graph(6, true);
        complexGraph.addEdge(0, 1);
        complexGraph.addEdge(1, 2);
        complexGraph.addEdge(2, 0); // First cycle: 0->1->2->0
        complexGraph.addEdge(3, 4);
        complexGraph.addEdge(4, 5);
        complexGraph.addEdge(5, 3); // Second cycle: 3->4->5->3
        complexGraph.addEdge(2, 3); // Connect the cycles
        
        System.out.println("\nComplex Graph Tests:");
        System.out.println("Has cycle: " + hasCycleDFS(complexGraph));
        System.out.println("SCC count: " + countStronglyConnectedComponents(complexGraph));
    }
}



