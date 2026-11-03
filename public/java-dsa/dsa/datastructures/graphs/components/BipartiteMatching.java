package dsa.datastructures.graphs.components;

import java.util.*;

/**
 * Bipartite Matching Algorithms
 * 
 * Finds maximum matching in a bipartite graph - the largest set of edges
 * with no common vertices.
 * 
 * Algorithms:
 * 1. Hungarian Algorithm (Kuhn's) - O(V³) or O(V * E)
 * 2. Hopcroft-Karp - O(E * √V)
 * 3. Using Max Flow - O(V * E²)
 * 
 * Key Concepts:
 * - Matching: Set of edges with no common vertices
 * - Maximum Matching: Largest possible matching
 * - Perfect Matching: All vertices are matched
 * - Augmenting Path: Path that increases matching size
 * - Min Vertex Cover = Max Matching (König's theorem)
 * 
 * Common MAANG Applications:
 * - Job assignment problem
 * - Resource allocation
 * - Task scheduling
 * - Stable matching (marriages)
 * - Network optimization
 * - Ride-sharing assignments
 */
public class BipartiteMatching {
    
    /**
     * APPROACH 1: Hungarian Algorithm (Kuhn's - Simple DFS)
     * 
     * Classic algorithm using DFS to find augmenting paths.
     * Simple to implement, good for interviews.
     * 
     * Time Complexity: O(V * E)
     * Space Complexity: O(V)
     * 
     * @param graph Adjacency list of bipartite graph (left to right)
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return Maximum matching size
     */
    public static int hungarianMatching(List<List<Integer>> graph, int leftSize, int rightSize) {
        int[] match = new int[rightSize]; // match[i] = matched left vertex for right vertex i
        Arrays.fill(match, -1);
        
        int matching = 0;
        
        // Try to find augmenting path from each left vertex
        for (int u = 0; u < leftSize; u++) {
            boolean[] visited = new boolean[rightSize];
            if (dfsAugment(u, graph, match, visited)) {
                matching++;
            }
        }
        
        return matching;
    }
    
    /**
     * DFS to find augmenting path
     */
    private static boolean dfsAugment(int u, List<List<Integer>> graph, 
                                     int[] match, boolean[] visited) {
        for (int v : graph.get(u)) {
            if (visited[v]) {
                continue;
            }
            visited[v] = true;
            
            // If v is unmatched or we can find augmenting path from match[v]
            if (match[v] == -1 || dfsAugment(match[v], graph, match, visited)) {
                match[v] = u;
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * APPROACH 2: Hopcroft-Karp Algorithm (Fastest)
     * 
     * Uses BFS to find shortest augmenting paths, then DFS to find blocking flow.
     * Best algorithm for maximum bipartite matching.
     * 
     * Time Complexity: O(E * √V)
     * Space Complexity: O(V)
     * 
     * @param graph Adjacency list
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return Maximum matching size
     */
    public static int hopcroftKarp(List<List<Integer>> graph, int leftSize, int rightSize) {
        int[] pairLeft = new int[leftSize];   // pairLeft[u] = matched right vertex
        int[] pairRight = new int[rightSize]; // pairRight[v] = matched left vertex
        int[] dist = new int[leftSize + 1];
        final int NIL = leftSize;             // Special NIL vertex
        
        Arrays.fill(pairLeft, NIL);
        Arrays.fill(pairRight, NIL);
        
        int matching = 0;
        
        // While there exists augmenting path
        while (bfsHopcroftKarp(graph, pairLeft, pairRight, dist, NIL)) {
            // Find all augmenting paths using DFS
            for (int u = 0; u < leftSize; u++) {
                if (pairLeft[u] == NIL && 
                    dfsHopcroftKarp(u, graph, pairLeft, pairRight, dist, NIL)) {
                    matching++;
                }
            }
        }
        
        return matching;
    }
    
    /**
     * BFS to partition graph into layers
     */
    private static boolean bfsHopcroftKarp(List<List<Integer>> graph, int[] pairLeft, 
                                          int[] pairRight, int[] dist, int NIL) {
        Queue<Integer> queue = new LinkedList<>();
        
        // Initialize distances
        for (int u = 0; u < pairLeft.length; u++) {
            if (pairLeft[u] == NIL) {
                dist[u] = 0;
                queue.offer(u);
            } else {
                dist[u] = Integer.MAX_VALUE;
            }
        }
        dist[NIL] = Integer.MAX_VALUE;
        
        // BFS
        while (!queue.isEmpty()) {
            int u = queue.poll();
            
            if (dist[u] < dist[NIL]) {
                for (int v : graph.get(u)) {
                    if (dist[pairRight[v]] == Integer.MAX_VALUE) {
                        dist[pairRight[v]] = dist[u] + 1;
                        queue.offer(pairRight[v]);
                    }
                }
            }
        }
        
        return dist[NIL] != Integer.MAX_VALUE;
    }
    
    /**
     * DFS to find augmenting paths
     */
    private static boolean dfsHopcroftKarp(int u, List<List<Integer>> graph, 
                                          int[] pairLeft, int[] pairRight, 
                                          int[] dist, int NIL) {
        if (u != NIL) {
            for (int v : graph.get(u)) {
                if (dist[pairRight[v]] == dist[u] + 1) {
                    if (dfsHopcroftKarp(pairRight[v], graph, pairLeft, pairRight, dist, NIL)) {
                        pairRight[v] = u;
                        pairLeft[u] = v;
                        return true;
                    }
                }
            }
            dist[u] = Integer.MAX_VALUE;
            return false;
        }
        return true;
    }
    
    /**
     * APPROACH 3: Get Actual Matching Pairs
     * 
     * Returns the actual matching pairs, not just the count.
     * 
     * @param graph Adjacency list
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return List of matching pairs [left, right]
     */
    public static List<int[]> getMatchingPairs(List<List<Integer>> graph, 
                                              int leftSize, int rightSize) {
        int[] match = new int[rightSize];
        Arrays.fill(match, -1);
        
        for (int u = 0; u < leftSize; u++) {
            boolean[] visited = new boolean[rightSize];
            dfsAugment(u, graph, match, visited);
        }
        
        List<int[]> pairs = new ArrayList<>();
        for (int v = 0; v < rightSize; v++) {
            if (match[v] != -1) {
                pairs.add(new int[]{match[v], v});
            }
        }
        
        return pairs;
    }
    
    /**
     * APPROACH 4: Minimum Vertex Cover (König's Theorem)
     * 
     * In bipartite graphs: |Min Vertex Cover| = |Max Matching|
     * Finds minimum set of vertices that cover all edges.
     * 
     * Time Complexity: O(V * E)
     * Space Complexity: O(V)
     * 
     * @param graph Adjacency list
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return Set of vertices in minimum vertex cover
     */
    public static Set<Integer> minimumVertexCover(List<List<Integer>> graph, 
                                                  int leftSize, int rightSize) {
        int[] match = new int[rightSize];
        Arrays.fill(match, -1);
        
        // Find maximum matching
        for (int u = 0; u < leftSize; u++) {
            boolean[] visited = new boolean[rightSize];
            dfsAugment(u, graph, match, visited);
        }
        
        // Find vertices reachable from unmatched left vertices
        boolean[] visitedLeft = new boolean[leftSize];
        boolean[] visitedRight = new boolean[rightSize];
        
        for (int u = 0; u < leftSize; u++) {
            boolean isMatched = false;
            for (int v : graph.get(u)) {
                if (match[v] == u) {
                    isMatched = true;
                    break;
                }
            }
            if (!isMatched) {
                dfsReachable(u, graph, match, visitedLeft, visitedRight);
            }
        }
        
        // Vertex cover = (unvisited left) + (visited right)
        Set<Integer> cover = new HashSet<>();
        for (int u = 0; u < leftSize; u++) {
            if (!visitedLeft[u]) {
                cover.add(u);
            }
        }
        for (int v = 0; v < rightSize; v++) {
            if (visitedRight[v]) {
                cover.add(leftSize + v);
            }
        }
        
        return cover;
    }
    
    /**
     * DFS to find reachable vertices
     */
    private static void dfsReachable(int u, List<List<Integer>> graph, int[] match,
                                    boolean[] visitedLeft, boolean[] visitedRight) {
        visitedLeft[u] = true;
        
        for (int v : graph.get(u)) {
            if (!visitedRight[v]) {
                visitedRight[v] = true;
                if (match[v] != -1) {
                    dfsReachable(match[v], graph, match, visitedLeft, visitedRight);
                }
            }
        }
    }
    
    /**
     * APPROACH 5: Maximum Independent Set
     * 
     * In bipartite graphs: |Max Independent Set| = V - |Max Matching|
     * Finds largest set of vertices with no edges between them.
     * 
     * @param graph Adjacency list
     * @param leftSize Size of left set
     * @param rightSize Size of right set
     * @return Size of maximum independent set
     */
    public static int maximumIndependentSet(List<List<Integer>> graph, 
                                           int leftSize, int rightSize) {
        int maxMatching = hungarianMatching(graph, leftSize, rightSize);
        return (leftSize + rightSize) - maxMatching;
    }
    
    /**
     * Utility: Check if matching is perfect
     */
    public static boolean isPerfectMatching(List<List<Integer>> graph, 
                                           int leftSize, int rightSize) {
        int maxMatching = hungarianMatching(graph, leftSize, rightSize);
        return maxMatching == Math.min(leftSize, rightSize);
    }
    
    /**
     * Utility: Get unmatched vertices
     */
    public static List<Integer> getUnmatchedVertices(List<List<Integer>> graph, 
                                                     int leftSize, int rightSize) {
        int[] match = new int[rightSize];
        Arrays.fill(match, -1);
        
        for (int u = 0; u < leftSize; u++) {
            boolean[] visited = new boolean[rightSize];
            dfsAugment(u, graph, match, visited);
        }
        
        List<Integer> unmatched = new ArrayList<>();
        
        // Check unmatched left vertices
        boolean[] matchedLeft = new boolean[leftSize];
        for (int v = 0; v < rightSize; v++) {
            if (match[v] != -1) {
                matchedLeft[match[v]] = true;
            }
        }
        
        for (int u = 0; u < leftSize; u++) {
            if (!matchedLeft[u]) {
                unmatched.add(u);
            }
        }
        
        // Check unmatched right vertices
        for (int v = 0; v < rightSize; v++) {
            if (match[v] == -1) {
                unmatched.add(leftSize + v);
            }
        }
        
        return unmatched;
    }
    
    /**
     * Demonstration
     */
    public static void main(String[] args) {
        System.out.println("=== Bipartite Matching Algorithms ===\n");
        
        // Example: Job assignment
        // Jobs (left): 0, 1, 2
        // Tasks (right): 0, 1, 2, 3
        // Edges represent which job can do which task
        
        List<List<Integer>> graph = new ArrayList<>();
        graph.add(Arrays.asList(0, 1));       // Job 0 can do tasks 0, 1
        graph.add(Arrays.asList(1, 2));       // Job 1 can do tasks 1, 2
        graph.add(Arrays.asList(0, 3));       // Job 2 can do tasks 0, 3
        
        int leftSize = 3;  // 3 jobs
        int rightSize = 4; // 4 tasks
        
        System.out.println("Job-Task Assignment:");
        System.out.println("Job 0 → Tasks [0, 1]");
        System.out.println("Job 1 → Tasks [1, 2]");
        System.out.println("Job 2 → Tasks [0, 3]");
        
        // Test Hungarian
        int matching1 = hungarianMatching(graph, leftSize, rightSize);
        System.out.println("\nHungarian Algorithm: " + matching1 + " jobs matched");
        
        // Test Hopcroft-Karp
        int matching2 = hopcroftKarp(graph, leftSize, rightSize);
        System.out.println("Hopcroft-Karp: " + matching2 + " jobs matched");
        
        // Get actual pairs
        List<int[]> pairs = getMatchingPairs(graph, leftSize, rightSize);
        System.out.println("\nMatching pairs:");
        for (int[] pair : pairs) {
            System.out.println("Job " + pair[0] + " → Task " + pair[1]);
        }
        
        // Check if perfect
        boolean isPerfect = isPerfectMatching(graph, leftSize, rightSize);
        System.out.println("\nIs perfect matching? " + isPerfect);
        
        // Minimum vertex cover
        Set<Integer> cover = minimumVertexCover(graph, leftSize, rightSize);
        System.out.println("Minimum vertex cover size: " + cover.size());
        System.out.println("Vertex cover: " + cover);
        
        // Maximum independent set
        int indepSet = maximumIndependentSet(graph, leftSize, rightSize);
        System.out.println("Maximum independent set size: " + indepSet);
        
        // Unmatched vertices
        List<Integer> unmatched = getUnmatchedVertices(graph, leftSize, rightSize);
        System.out.println("Unmatched vertices: " + unmatched);
    }
}


