package dsa.datastructures.graphs.topology;
import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Topological Sorting - Multiple Implementations
 * 
 * Topological sorting produces a linear ordering of vertices in a directed acyclic graph (DAG)
 * such that for every directed edge (u,v), vertex u comes before v in the ordering.
 * 
 * Key Properties:
 * - Only works on Directed Acyclic Graphs (DAGs)
 * - Multiple valid topological orders may exist
 * - If graph has cycles, no topological ordering exists
 * - Essential for dependency resolution problems
 * 
 * Time Complexity: O(V + E) for both algorithms
 * Space Complexity: O(V)
 * 
 * Common MAANG Applications:
 * - Course scheduling (prerequisites)
 * - Build systems (dependency resolution)
 * - Task scheduling with dependencies
 * - Project management (task ordering)
 * - Package installation (dependency resolution)
 */
public class TopologicalSort {
    
    /**
     * APPROACH 1: Kahn's Algorithm (BFS-based)
     * 
     * Uses in-degree count and queue. Processes vertices with no dependencies first.
     * Natural for finding cycles since incomplete ordering indicates cycle presence.
     * 
     * Algorithm:
     * 1. Calculate in-degree for each vertex
     * 2. Add all vertices with in-degree 0 to queue
     * 3. Process vertices from queue, decreasing in-degree of neighbors
     * 4. Add neighbors with in-degree 0 to queue
     * 5. If all vertices processed, topological order found; otherwise cycle exists
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return Topologically sorted list, empty if cycle detected
     */
    public static List<Integer> kahnTopologicalSort(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("Topological sort only works on directed graphs");
        }
        
        int vertexCount = graph.vertexCount;
        
        // Step 1: Calculate in-degrees for all vertices
        int[] inDegree = new int[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                inDegree[neighbor]++;
            }
        }
        
        // Step 2: Initialize queue with vertices having in-degree 0
        Queue<Integer> queue = new ArrayDeque<>();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (inDegree[vertex] == 0) {
                queue.offer(vertex);
            }
        }
        
        // Step 3: Process vertices in topological order
        List<Integer> topologicalOrder = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            int currentVertex = queue.poll();
            topologicalOrder.add(currentVertex);
            
            // Remove current vertex and update in-degrees of neighbors
            for (int neighbor : graph.adj.get(currentVertex)) {
                inDegree[neighbor]--;
                
                // If neighbor's in-degree becomes 0, add to queue
                if (inDegree[neighbor] == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        // Step 4: Check for cycles
        if (topologicalOrder.size() != vertexCount) {
            return Collections.emptyList(); // Cycle detected
        }
        
        return topologicalOrder;
    }
    
    /**
     * APPROACH 2: DFS-based Topological Sort
     * 
     * Uses depth-first search with post-order traversal. Naturally handles
     * cycle detection and provides a different valid ordering than Kahn's.
     * 
     * Algorithm:
     * 1. Perform DFS from each unvisited vertex
     * 2. During DFS, track current path to detect cycles
     * 3. Add vertex to result after processing all descendants (post-order)
     * 4. Reverse final result to get topological order
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V) including recursion stack
     * 
     * @param graph Input directed graph
     * @return Topologically sorted list, empty if cycle detected
     */
    public static List<Integer> dfsTopologicalSort(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("Topological sort only works on directed graphs");
        }
        
        int vertexCount = graph.vertexCount;
        boolean[] visited = new boolean[vertexCount];
        boolean[] onCurrentPath = new boolean[vertexCount];
        List<Integer> postOrder = new ArrayList<>();
        
        // Perform DFS from each unvisited vertex
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (!visited[vertex]) {
                if (!dfsVisit(graph, vertex, visited, onCurrentPath, postOrder)) {
                    return Collections.emptyList(); // Cycle detected
                }
            }
        }
        
        // Reverse post-order to get topological order
        Collections.reverse(postOrder);
        return postOrder;
    }
    
    /**
     * Helper method for DFS-based topological sort
     * 
     * @param graph Input graph
     * @param vertex Current vertex
     * @param visited Global visited array
     * @param onCurrentPath Track vertices on current DFS path (for cycle detection)
     * @param postOrder List to store post-order traversal
     * @return false if cycle detected, true otherwise
     */
    private static boolean dfsVisit(Graph graph, int vertex, boolean[] visited, 
                                   boolean[] onCurrentPath, List<Integer> postOrder) {
        visited[vertex] = true;
        onCurrentPath[vertex] = true;
        
        // Visit all neighbors
        for (int neighbor : graph.adj.get(vertex)) {
            if (!visited[neighbor]) {
                // Recursively visit unvisited neighbor
                if (!dfsVisit(graph, neighbor, visited, onCurrentPath, postOrder)) {
                    return false; // Cycle detected in recursive call
                }
            } else if (onCurrentPath[neighbor]) {
                // Back edge found - cycle detected
                return false;
            }
        }
        
        // Remove from current path and add to post-order
        onCurrentPath[vertex] = false;
        postOrder.add(vertex);
        return true;
    }
    
    /**
     * APPROACH 3: Lexicographic Topological Sort
     * 
     * Produces the lexicographically smallest topological ordering.
     * Uses priority queue instead of regular queue in Kahn's algorithm.
     * 
     * Time Complexity: O((V + E) log V)
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return Lexicographically smallest topological order, empty if cycle detected
     */
    public static List<Integer> lexicographicTopologicalSort(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("Topological sort only works on directed graphs");
        }
        
        int vertexCount = graph.vertexCount;
        
        // Calculate in-degrees
        int[] inDegree = new int[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                inDegree[neighbor]++;
            }
        }
        
        // Use priority queue to ensure lexicographic order
        PriorityQueue<Integer> priorityQueue = new PriorityQueue<>();
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            if (inDegree[vertex] == 0) {
                priorityQueue.offer(vertex);
            }
        }
        
        List<Integer> lexicographicOrder = new ArrayList<>();
        
        while (!priorityQueue.isEmpty()) {
            int currentVertex = priorityQueue.poll();
            lexicographicOrder.add(currentVertex);
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                inDegree[neighbor]--;
                if (inDegree[neighbor] == 0) {
                    priorityQueue.offer(neighbor);
                }
            }
        }
        
        if (lexicographicOrder.size() != vertexCount) {
            return Collections.emptyList(); // Cycle detected
        }
        
        return lexicographicOrder;
    }
    
    /**
     * APPROACH 4: All Possible Topological Orders
     * 
     * Finds all valid topological orderings using backtracking.
     * Useful for problems that need to consider all possibilities.
     * 
     * Time Complexity: O(V! * (V + E)) in worst case
     * Space Complexity: O(V)
     * 
     * @param graph Input directed graph
     * @return List of all possible topological orders
     */
    public static List<List<Integer>> allTopologicalSorts(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        if (!graph.isDirected) {
            throw new IllegalArgumentException("Topological sort only works on directed graphs");
        }
        
        int vertexCount = graph.vertexCount;
        
        // Calculate initial in-degrees
        int[] inDegree = new int[vertexCount];
        for (int vertex = 0; vertex < vertexCount; vertex++) {
            for (int neighbor : graph.adj.get(vertex)) {
                inDegree[neighbor]++;
            }
        }
        
        List<List<Integer>> allOrders = new ArrayList<>();
        List<Integer> currentOrder = new ArrayList<>();
        boolean[] visited = new boolean[vertexCount];
        
        findAllTopologicalOrders(graph, inDegree, visited, currentOrder, allOrders);
        
        return allOrders;
    }
    
    /**
     * Helper method for finding all topological orders using backtracking
     */
    private static void findAllTopologicalOrders(Graph graph, int[] inDegree, boolean[] visited,
                                                List<Integer> currentOrder, List<List<Integer>> allOrders) {
        // Base case: if current order contains all vertices
        if (currentOrder.size() == graph.vertexCount) {
            allOrders.add(new ArrayList<>(currentOrder));
            return;
        }
        
        // Try all vertices with in-degree 0
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            if (!visited[vertex] && inDegree[vertex] == 0) {
                // Choose this vertex
                visited[vertex] = true;
                currentOrder.add(vertex);
                
                // Update in-degrees of neighbors
                for (int neighbor : graph.adj.get(vertex)) {
                    inDegree[neighbor]--;
                }
                
                // Recurse
                findAllTopologicalOrders(graph, inDegree, visited, currentOrder, allOrders);
                
                // Backtrack
                visited[vertex] = false;
                currentOrder.remove(currentOrder.size() - 1);
                
                // Restore in-degrees of neighbors
                for (int neighbor : graph.adj.get(vertex)) {
                    inDegree[neighbor]++;
                }
            }
        }
    }
    
    /**
     * Utility method to check if graph is a DAG (has valid topological ordering)
     * 
     * @param graph Input directed graph
     * @return true if graph is acyclic (DAG)
     */
    public static boolean isDAG(Graph graph) {
        return !kahnTopologicalSort(graph).isEmpty();
    }
    
    /**
     * Utility method to detect cycle using topological sort
     * 
     * @param graph Input directed graph
     * @return true if graph contains a cycle
     */
    public static boolean hasCycle(Graph graph) {
        return !isDAG(graph);
    }
    
    /**
     * Utility method to find vertices that cause cycles
     * 
     * @param graph Input directed graph
     * @return Set of vertices involved in cycles
     */
    public static Set<Integer> findCyclicVertices(Graph graph) {
        List<Integer> topologicalOrder = kahnTopologicalSort(graph);
        Set<Integer> validVertices = new HashSet<>(topologicalOrder);
        Set<Integer> cyclicVertices = new HashSet<>();
        
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            if (!validVertices.contains(vertex)) {
                cyclicVertices.add(vertex);
            }
        }
        
        return cyclicVertices;
    }
    
    /**
     * Demonstration and testing
     */
    public static void main(String[] args) {
        System.out.println("=== Topological Sort Demonstrations ===");
        
        // Test Case 1: Simple DAG
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
        
        System.out.println("DAG Test:");
        System.out.println("Kahn's Algorithm: " + kahnTopologicalSort(dag));
        System.out.println("DFS Algorithm: " + dfsTopologicalSort(dag));
        System.out.println("Lexicographic: " + lexicographicTopologicalSort(dag));
        System.out.println("Is DAG: " + isDAG(dag));
        
        // Test Case 2: Graph with cycle
        Graph cyclicGraph = new Graph(3, true);
        cyclicGraph.addEdge(0, 1);
        cyclicGraph.addEdge(1, 2);
        cyclicGraph.addEdge(2, 0); // Creates cycle
        
        System.out.println("\nCyclic Graph Test:");
        System.out.println("Kahn's Algorithm: " + kahnTopologicalSort(cyclicGraph));
        System.out.println("DFS Algorithm: " + dfsTopologicalSort(cyclicGraph));
        System.out.println("Has Cycle: " + hasCycle(cyclicGraph));
        System.out.println("Cyclic Vertices: " + findCyclicVertices(cyclicGraph));
        
        // Test Case 3: All possible topological orders (small graph)
        Graph smallDAG = new Graph(3, true);
        smallDAG.addEdge(0, 2);
        
        System.out.println("\nAll Topological Orders:");
        List<List<Integer>> allOrders = allTopologicalSorts(smallDAG);
        for (int i = 0; i < allOrders.size(); i++) {
            System.out.println("Order " + (i + 1) + ": " + allOrders.get(i));
        }
    }
}

