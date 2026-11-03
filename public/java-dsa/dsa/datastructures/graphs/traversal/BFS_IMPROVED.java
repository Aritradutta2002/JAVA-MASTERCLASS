package dsa.datastructures.graphs.traversal;

import com.dsa.datastructures.graphs.utils.Graph;

import java.util.*;

/**
 * Breadth-First Search (BFS) Algorithm
 * <p>
 * PROBLEM:
 * Find shortest distance from source to all nodes in an unweighted graph.
 * <p>
 * ALGORITHM:
 * 1. Use queue for level-order traversal
 * 2. Mark visited nodes to avoid cycles
 * 3. Track distances from source
 * <p>
 * TIME COMPLEXITY: O(V + E)
 * - V = number of vertices
 * - E = number of edges
 * - Each vertex processed once, each edge explored once
 * <p>
 * SPACE COMPLEXITY: O(V)
 * - Queue can hold up to V vertices
 * - Distance array of size V
 * <p>
 * WHEN TO USE:
 * ✓ Shortest path in UNWEIGHTED graph
 * ✓ Level-order traversal
 * ✓ Finding connected components
 * ✓ Checking if graph is bipartite
 * <p>
 * EDGE CASES:
 * - Empty graph (n=0)
 * - Single node (n=1)
 * - Disconnected graph (some nodes unreachable)
 * - Source node out of bounds
 * <p>
 * LEETCODE PROBLEMS:
 * - #200 Number of Islands
 * - #127 Word Ladder
 * - #286 Walls and Gates
 * - #994 Rotting Oranges
 * - #1091 Shortest Path in Binary Matrix
 * <p>
 * COMMON MISTAKES:
 * ❌ Using DFS instead of BFS for shortest path
 * ❌ Not checking if node already visited
 * ❌ Using wrong data structure (Stack instead of Queue)
 */
public class BFS_IMPROVED {

    /**
     * Performs BFS from source and returns shortest distances.
     *
     * @param g   Graph object (adjacency list representation)
     * @param src Source vertex (0-indexed)
     * @return Array of distances from source (Integer.MAX_VALUE if unreachable)
     * @throws IllegalArgumentException if src is out of bounds
     */
    public static int[] bfs(Graph g, int src) {
        // Input validation
        if (src < 0 || src >= g.n) {
            throw new IllegalArgumentException("Source vertex out of bounds: " + src);
        }

        int n = g.n;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);

        // Edge case: empty graph
        if (n == 0) return dist;

        Queue<Integer> queue = new ArrayDeque<>();
        dist[src] = 0;
        queue.offer(src);

        // BFS traversal
        while (!queue.isEmpty()) {
            int u = queue.poll();

            // Explore all neighbors
            for (int v : g.adj.get(u)) {
                // If not visited yet
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = dist[u] + 1;
                    queue.offer(v);
                }
            }
        }

        return dist;
    }

    public static BFSResult bfsWithPath(Graph g, int src) {
        if (src < 0 || src >= g.n) {
            throw new IllegalArgumentException("Source vertex out of bounds: " + src);
        }

        int n = g.n;
        int[] dist = new int[n];
        int[] parent = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);

        if (n == 0) return new BFSResult(dist, parent);

        Queue<Integer> queue = new ArrayDeque<>();
        dist[src] = 0;
        queue.offer(src);

        while (!queue.isEmpty()) {
            int u = queue.poll();

            for (int v : g.adj.get(u)) {
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = dist[u] + 1;
                    parent[v] = u;
                    queue.offer(v);
                }
            }
        }

        return new BFSResult(dist, parent);
    }

    /**
     * Multi-source BFS (useful for problems like Rotting Oranges).
     *
     * @param g       Graph object
     * @param sources List of source vertices
     * @return Array of distances from nearest source
     */
    public static int[] multiSourceBFS(Graph g, List<Integer> sources) {
        int n = g.n;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);

        Queue<Integer> queue = new ArrayDeque<>();

        // Add all sources to queue
        for (int src : sources) {
            if (src >= 0 && src < n) {
                dist[src] = 0;
                queue.offer(src);
            }
        }

        while (!queue.isEmpty()) {
            int u = queue.poll();

            for (int v : g.adj.get(u)) {
                if (dist[v] == Integer.MAX_VALUE) {
                    dist[v] = dist[u] + 1;
                    queue.offer(v);
                }
            }
        }

        return dist;
    }

    public static void main(String[] args) {
        testBasicBFS();
        testDisconnectedGraph();
        testSingleNode();
        testWithPath();
        testMultiSource();
    }

    // ========== TEST CASES ==========

    private static void testBasicBFS() {
        System.out.println("=== Test 1: Basic BFS ===");
        // Graph: 0---1---3---4---5
        //        |   |
        //        2---+
        Graph g = new Graph(6, false);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(1, 3);
        g.addEdge(2, 3);
        g.addEdge(3, 4);
        g.addEdge(4, 5);

        int[] dist = bfs(g, 0);
        System.out.println("Distances from 0: " + Arrays.toString(dist));
        System.out.println("Expected: [0, 1, 1, 2, 3, 4]");
        System.out.println();
    }

    private static void testDisconnectedGraph() {
        System.out.println("=== Test 2: Disconnected Graph ===");
        // Graph: 0---1    2---3
        Graph g = new Graph(4, false);
        g.addEdge(0, 1);
        g.addEdge(2, 3);

        int[] dist = bfs(g, 0);
        System.out.println("Distances from 0: " + Arrays.toString(dist));
        System.out.println("Expected: [0, 1, MAX, MAX] (2 and 3 unreachable)");
        System.out.println();
    }

    private static void testSingleNode() {
        System.out.println("=== Test 3: Single Node ===");
        Graph g = new Graph(1, false);
        int[] dist = bfs(g, 0);
        System.out.println("Distances from 0: " + Arrays.toString(dist));
        System.out.println("Expected: [0]");
        System.out.println();
    }

    private static void testWithPath() {
        System.out.println("=== Test 4: BFS with Path ===");
        Graph g = new Graph(5, false);
        g.addEdge(0, 1);
        g.addEdge(0, 2);
        g.addEdge(1, 3);
        g.addEdge(2, 3);
        g.addEdge(3, 4);

        BFSResult result = bfsWithPath(g, 0);
        System.out.println("Path from 0 to 4: " + result.getPath(4));
        System.out.println("Expected: [0, 1, 3, 4] or [0, 2, 3, 4]");
        System.out.println();
    }

    private static void testMultiSource() {
        System.out.println("=== Test 5: Multi-Source BFS ===");
        Graph g = new Graph(5, false);
        g.addEdge(0, 1);
        g.addEdge(1, 2);
        g.addEdge(2, 3);
        g.addEdge(3, 4);

        List<Integer> sources = Arrays.asList(0, 4);
        int[] dist = multiSourceBFS(g, sources);
        System.out.println("Distances from sources {0, 4}: " + Arrays.toString(dist));
        System.out.println("Expected: [0, 1, 1, 1, 0] (node 2 closest to both)");
        System.out.println();
    }

    /**
     * Alternative: BFS with path reconstruction.
     * Returns both distances and parent array for path retrieval.
     */
    public static class BFSResult {
        public int[] dist;
        public int[] parent;

        public BFSResult(int[] dist, int[] parent) {
            this.dist = dist;
            this.parent = parent;
        }

        /**
         * Reconstructs path from source to target.
         *
         * @return List of nodes in path, or empty if no path exists
         */
        public List<Integer> getPath(int target) {
            if (dist[target] == Integer.MAX_VALUE) {
                return Collections.emptyList();
            }

            List<Integer> path = new ArrayList<>();
            for (int at = target; at != -1; at = parent[at]) {
                path.add(at);
            }
            Collections.reverse(path);
            return path;
        }
    }
}



