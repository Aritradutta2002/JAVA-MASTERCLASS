package dsa.datastructures.graphs.traversal;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Depth-First Search (DFS) - Iterative Implementations
 * 
 * DFS using explicit stack instead of recursion. This is crucial for:
 * - Very deep graphs where recursion might cause stack overflow
 * - When you need precise control over the traversal order
 * - Memory-conscious environments where stack space is limited
 * 
 * Time Complexity: O(V + E) where V = vertices, E = edges
 * Space Complexity: O(V) for explicit stack and visited array
 * 
 * Key Differences from Recursive DFS:
 * - Uses explicit stack instead of call stack
 * - No risk of stack overflow for deep graphs
 * - Can be paused and resumed
 * - Different traversal order due to stack behavior
 * 
 * Common MAANG Applications:
 * - Large graph traversals
 * - Interactive graph exploration
 * - Memory-constrained environments
 * - When you need to control stack size
 */
public class DFSIterative {
    
    /**
     * APPROACH 1: Basic Iterative DFS
     * 
     * Standard iterative DFS using explicit stack.
     * Note: Order might differ from recursive DFS due to how neighbors are processed.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @return List of vertices in DFS traversal order
     */
    public static List<Integer> dfsIterativeBasic(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        
        boolean[] visited = new boolean[graph.vertexCount];
        List<Integer> traversalOrder = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        
        stack.push(source);
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            
            // Skip if already visited
            if (visited[currentVertex]) {
                continue;
            }
            
            // Mark as visited and add to result
            visited[currentVertex] = true;
            traversalOrder.add(currentVertex);
            
            // Add neighbors to stack in reverse order to maintain left-to-right traversal
            List<Integer> neighbors = graph.adj.get(currentVertex);
            for (int i = neighbors.size() - 1; i >= 0; i--) {
                int neighbor = neighbors.get(i);
                if (!visited[neighbor]) {
                    stack.push(neighbor);
                }
            }
        }
        
        return traversalOrder;
    }
    
    /**
     * APPROACH 2: DFS with Pre-Visit Marking
     * 
     * Alternative approach that marks vertices as visited when they're added to stack,
     * not when they're popped. This can be more efficient and avoids duplicate entries.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @return List of vertices in DFS traversal order
     */
    public static List<Integer> dfsIterativePreMark(Graph graph, int source) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        
        boolean[] visited = new boolean[graph.vertexCount];
        List<Integer> traversalOrder = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        
        // Mark source as visited and add to stack
        visited[source] = true;
        stack.push(source);
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            traversalOrder.add(currentVertex);
            
            // Add unvisited neighbors to stack and mark them as visited
            List<Integer> neighbors = graph.adj.get(currentVertex);
            for (int i = neighbors.size() - 1; i >= 0; i--) {
                int neighbor = neighbors.get(i);
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    stack.push(neighbor);
                }
            }
        }
        
        return traversalOrder;
    }
    
    /**
     * APPROACH 3: DFS All Components Iterative
     * 
     * Iterative DFS that handles disconnected graphs by visiting all components.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return List of all vertices in DFS order across all components
     */
    public static List<Integer> dfsAllComponentsIterative(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        boolean[] visited = new boolean[graph.vertexCount];
        List<Integer> completeTraversal = new ArrayList<>();
        
        // Visit all unvisited vertices (handles disconnected components)
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            if (!visited[vertex]) {
                List<Integer> componentTraversal = dfsIterativeFromVertex(graph, vertex, visited);
                completeTraversal.addAll(componentTraversal);
            }
        }
        
        return completeTraversal;
    }
    
    /**
     * APPROACH 4: DFS with Path Reconstruction
     * 
     * Iterative DFS that finds a path between source and target vertices.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @param source Source vertex
     * @param target Target vertex
     * @return Path from source to target, empty list if no path exists
     */
    public static List<Integer> dfsIterativePath(Graph graph, int source, int target) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        validateVertex(target, graph.vertexCount);
        
        if (source == target) {
            return Arrays.asList(source);
        }
        
        boolean[] visited = new boolean[graph.vertexCount];
        int[] parent = new int[graph.vertexCount];
        Arrays.fill(parent, -1);
        
        Deque<Integer> stack = new ArrayDeque<>();
        visited[source] = true;
        stack.push(source);
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            
            if (currentVertex == target) {
                // Reconstruct path
                return reconstructPath(parent, source, target);
            }
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    parent[neighbor] = currentVertex;
                    stack.push(neighbor);
                }
            }
        }
        
        // No path found
        return new ArrayList<>();
    }
    
    /**
     * APPROACH 5: DFS with Custom Processing
     * 
     * Flexible DFS that allows custom processing at each vertex.
     * Useful for complex traversal requirements.
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @param processor Function to process each vertex
     */
    public static void dfsIterativeWithProcessor(Graph graph, int source, 
                                                java.util.function.Consumer<Integer> processor) {
        if (graph == null || processor == null) {
            throw new IllegalArgumentException("Graph and processor cannot be null");
        }
        
        validateVertex(source, graph.vertexCount);
        
        boolean[] visited = new boolean[graph.vertexCount];
        Deque<Integer> stack = new ArrayDeque<>();
        
        visited[source] = true;
        stack.push(source);
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            processor.accept(currentVertex);  // Custom processing
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    stack.push(neighbor);
                }
            }
        }
    }
    
    /**
     * APPROACH 6: Connected Components with Iterative DFS
     * 
     * Finds all connected components using iterative DFS.
     * 
     * Time Complexity: O(V + E)
     * Space Complexity: O(V)
     * 
     * @param graph Input graph
     * @return List of components, each component is a list of vertices
     */
    public static List<List<Integer>> findConnectedComponentsIterative(Graph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Graph cannot be null");
        }
        
        boolean[] visited = new boolean[graph.vertexCount];
        List<List<Integer>> components = new ArrayList<>();
        
        for (int vertex = 0; vertex < graph.vertexCount; vertex++) {
            if (!visited[vertex]) {
                List<Integer> component = dfsIterativeFromVertex(graph, vertex, visited);
                components.add(component);
            }
        }
        
        return components;
    }
    
    /**
     * Helper method for DFS from a specific vertex with existing visited array
     * 
     * @param graph Input graph
     * @param source Starting vertex
     * @param visited Visited array to update
     * @return List of vertices visited in this DFS call
     */
    private static List<Integer> dfsIterativeFromVertex(Graph graph, int source, boolean[] visited) {
        List<Integer> component = new ArrayList<>();
        Deque<Integer> stack = new ArrayDeque<>();
        
        visited[source] = true;
        stack.push(source);
        
        while (!stack.isEmpty()) {
            int currentVertex = stack.pop();
            component.add(currentVertex);
            
            for (int neighbor : graph.adj.get(currentVertex)) {
                if (!visited[neighbor]) {
                    visited[neighbor] = true;
                    stack.push(neighbor);
                }
            }
        }
        
        return component;
    }
    
    /**
     * Helper method to reconstruct path from parent array
     * 
     * @param parent Parent array from DFS
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
     * Utility method to count reachable vertices from source
     * 
     * @param graph Input graph
     * @param source Source vertex
     * @return Number of vertices reachable from source
     */
    public static int countReachableVertices(Graph graph, int source) {
        return dfsIterativeBasic(graph, source).size();
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
        //                      2   4-5
        Graph graph = new Graph(6, false);
        graph.addEdge(0, 1);
        graph.addEdge(0, 2);
        graph.addEdge(1, 3);
        graph.addEdge(4, 5);  // Disconnected component
        
        System.out.println("=== DFS Iterative Demonstrations ===");
        
        // Test 1: Basic iterative DFS
        List<Integer> basicDFS = dfsIterativeBasic(graph, 0);
        System.out.println("Basic iterative DFS from 0: " + basicDFS);
        
        // Test 2: Pre-mark approach
        List<Integer> preMarkDFS = dfsIterativePreMark(graph, 0);
        System.out.println("Pre-mark DFS from 0: " + preMarkDFS);
        
        // Test 3: All components
        List<Integer> allComponents = dfsAllComponentsIterative(graph);
        System.out.println("All components DFS: " + allComponents);
        
        // Test 4: Path finding
        List<Integer> path = dfsIterativePath(graph, 0, 3);
        System.out.println("Path from 0 to 3: " + path);
        
        // Test 5: Connected components
        List<List<Integer>> components = findConnectedComponentsIterative(graph);
        System.out.println("Connected components: " + components);
        
        // Test 6: Custom processing (example: collect even vertices)
        List<Integer> evenVertices = new ArrayList<>();
        dfsIterativeWithProcessor(graph, 0, vertex -> {
            if (vertex % 2 == 0) {
                evenVertices.add(vertex);
            }
        });
        System.out.println("Even vertices in DFS order: " + evenVertices);
        
        // Test 7: Count reachable vertices
        int reachableCount = countReachableVertices(graph, 0);
        System.out.println("Vertices reachable from 0: " + reachableCount);
    }
}



