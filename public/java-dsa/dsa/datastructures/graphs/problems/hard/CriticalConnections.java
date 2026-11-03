package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 1192: Critical Connections in a Network
 * 
 * Problem: There are n servers numbered from 0 to n - 1 connected by undirected server-to-server connections 
 * forming a network where connections[i] = [ai, bi] represents a connection between servers ai and bi. 
 * Any server can reach other servers directly or indirectly through the network.
 * A critical connection is a connection that, if removed, will make some servers unable to reach some other server.
 * Return all critical connections in the network in any order.
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V + E)
 */
public class CriticalConnections {
    
    private int time;
    
    /**
     * Tarjan's algorithm for finding bridges
     */
    public List<List<Integer>> criticalConnections(int n, List<List<Integer>> connections) {
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (List<Integer> connection : connections) {
            graph.get(connection.get(0)).add(connection.get(1));
            graph.get(connection.get(1)).add(connection.get(0));
        }
        
        // Initialize arrays for Tarjan's algorithm
        int[] disc = new int[n];      // Discovery time
        int[] low = new int[n];       // Lowest reachable vertex
        boolean[] visited = new boolean[n];
        int[] parent = new int[n];
        
        Arrays.fill(parent, -1);
        time = 0;
        
        List<List<Integer>> bridges = new ArrayList<>();
        
        // Run DFS from each unvisited vertex
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                bridgeUtil(graph, i, visited, disc, low, parent, bridges);
            }
        }
        
        return bridges;
    }
    
    private void bridgeUtil(List<List<Integer>> graph, int u, boolean[] visited, 
                           int[] disc, int[] low, int[] parent, List<List<Integer>> bridges) {
        visited[u] = true;
        disc[u] = low[u] = ++time;
        
        for (int v : graph.get(u)) {
            if (!visited[v]) {
                parent[v] = u;
                bridgeUtil(graph, v, visited, disc, low, parent, bridges);
                
                // Update low value of u for parent function calls
                low[u] = Math.min(low[u], low[v]);
                
                // If the lowest vertex reachable from subtree under v is below u in DFS tree,
                // then u-v is a bridge
                if (low[v] > disc[u]) {
                    bridges.add(Arrays.asList(u, v));
                }
            } else if (v != parent[u]) {
                // Update low value of u for back edge
                low[u] = Math.min(low[u], disc[v]);
            }
        }
    }
    
    /**
     * Alternative implementation with cleaner structure
     */
    public List<List<Integer>> criticalConnectionsAlternative(int n, List<List<Integer>> connections) {
        List<Set<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new HashSet<>());
        }
        
        for (List<Integer> connection : connections) {
            graph.get(connection.get(0)).add(connection.get(1));
            graph.get(connection.get(1)).add(connection.get(0));
        }
        
        int[] rank = new int[n];
        Arrays.fill(rank, -2); // -2: unvisited, -1: visiting, >=0: visited with rank
        
        List<List<Integer>> result = new ArrayList<>();
        dfs(graph, 0, 0, rank, result);
        return result;
    }
    
    private int dfs(List<Set<Integer>> graph, int node, int depth, int[] rank, List<List<Integer>> result) {
        if (rank[node] >= 0) {
            return rank[node]; // Already visited
        }
        
        rank[node] = depth;
        int minRank = depth;
        
        for (int neighbor : graph.get(node)) {
            if (rank[neighbor] == depth - 1) {
                continue; // Skip parent
            }
            
            int neighborRank = dfs(graph, neighbor, depth + 1, rank, result);
            
            if (neighborRank <= depth) {
                // There's a back edge, so this edge is not a bridge
                graph.get(node).remove(neighbor);
                graph.get(neighbor).remove(node);
            }
            
            minRank = Math.min(minRank, neighborRank);
        }
        
        return minRank;
    }
    
    /**
     * Convert remaining edges to result format for alternative method
     */
    public List<List<Integer>> getRemainingEdges(List<Set<Integer>> graph) {
        List<List<Integer>> result = new ArrayList<>();
        Set<String> added = new HashSet<>();
        
        for (int i = 0; i < graph.size(); i++) {
            for (int neighbor : graph.get(i)) {
                String edge = Math.min(i, neighbor) + "," + Math.max(i, neighbor);
                if (!added.contains(edge)) {
                    result.add(Arrays.asList(i, neighbor));
                    added.add(edge);
                }
            }
        }
        
        return result;
    }
    
    // Test method
    public static void main(String[] args) {
        CriticalConnections solution = new CriticalConnections();
        
        // Test case: n = 4, connections = [[0,1],[1,2],[2,0],[1,3]]
        List<List<Integer>> connections = Arrays.asList(
            Arrays.asList(0, 1),
            Arrays.asList(1, 2),
            Arrays.asList(2, 0),
            Arrays.asList(1, 3)
        );
        
        List<List<Integer>> result = solution.criticalConnections(4, connections);
        System.out.println("Critical connections: " + result); // [[1,3]]
        
        // Reset time for second test
        solution.time = 0;
        List<List<Integer>> result2 = solution.criticalConnectionsAlternative(4, connections);
        System.out.println("Critical connections (Alternative): " + result2);
    }
}

