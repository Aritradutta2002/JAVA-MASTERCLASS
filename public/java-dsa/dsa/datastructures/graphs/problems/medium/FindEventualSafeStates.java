package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 802: Find Eventual Safe States
 * 
 * Problem: There is a directed graph of n nodes with each node labeled from 0 to n - 1. 
 * The graph is represented by a 0-indexed 2D integer array graph where graph[i] is an integer array 
 * of nodes adjacent to node i, meaning there is an edge from node i to each node in graph[i].
 * A node is a terminal node if there are no outgoing edges. A node is a safe node if every possible path 
 * starting from that node leads to a terminal node (or another safe node).
 * 
 * Time Complexity: O(V + E)
 * Space Complexity: O(V)
 */
public class FindEventualSafeStates {
    
    /**
     * DFS with memoization solution
     */
    public List<Integer> eventualSafeNodes(int[][] graph) {
        int n = graph.length;
        int[] color = new int[n]; // 0: white, 1: gray, 2: black
        List<Integer> result = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            if (isSafe(graph, i, color)) {
                result.add(i);
            }
        }
        
        return result;
    }
    
    private boolean isSafe(int[][] graph, int node, int[] color) {
        if (color[node] != 0) {
            return color[node] == 2; // Return true if black (safe), false if gray (in cycle)
        }
        
        color[node] = 1; // Mark as gray (visiting)
        
        for (int neighbor : graph[node]) {
            if (!isSafe(graph, neighbor, color)) {
                return false; // If any neighbor is unsafe, current node is unsafe
            }
        }
        
        color[node] = 2; // Mark as black (safe)
        return true;
    }
    
    /**
     * Topological sort solution (reverse graph)
     */
    public List<Integer> eventualSafeNodesTopological(int[][] graph) {
        int n = graph.length;
        
        // Build reverse graph
        List<List<Integer>> reverseGraph = new ArrayList<>();
        int[] indegree = new int[n];
        
        for (int i = 0; i < n; i++) {
            reverseGraph.add(new ArrayList<>());
        }
        
        for (int i = 0; i < n; i++) {
            for (int neighbor : graph[i]) {
                reverseGraph.get(neighbor).add(i);
                indegree[i]++;
            }
        }
        
        // BFS from terminal nodes (indegree 0 in reverse graph)
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (indegree[i] == 0) {
                queue.offer(i);
            }
        }
        
        List<Integer> safeNodes = new ArrayList<>();
        
        while (!queue.isEmpty()) {
            int node = queue.poll();
            safeNodes.add(node);
            
            for (int neighbor : reverseGraph.get(node)) {
                indegree[neighbor]--;
                if (indegree[neighbor] == 0) {
                    queue.offer(neighbor);
                }
            }
        }
        
        Collections.sort(safeNodes);
        return safeNodes;
    }
    
    /**
     * DFS cycle detection solution
     */
    public List<Integer> eventualSafeNodesCycleDetection(int[][] graph) {
        int n = graph.length;
        boolean[] visited = new boolean[n];
        boolean[] inStack = new boolean[n];
        boolean[] unsafe = new boolean[n];
        
        // Check each node for cycles
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                dfsHasCycle(graph, i, visited, inStack, unsafe);
            }
        }
        
        List<Integer> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (!unsafe[i]) {
                result.add(i);
            }
        }
        
        return result;
    }
    
    private boolean dfsHasCycle(int[][] graph, int node, boolean[] visited, boolean[] inStack, boolean[] unsafe) {
        visited[node] = true;
        inStack[node] = true;
        
        for (int neighbor : graph[node]) {
            if (!visited[neighbor]) {
                if (dfsHasCycle(graph, neighbor, visited, inStack, unsafe)) {
                    unsafe[node] = true;
                    return true;
                }
            } else if (inStack[neighbor] || unsafe[neighbor]) {
                unsafe[node] = true;
                return true;
            }
        }
        
        inStack[node] = false;
        return false;
    }
    
    // Test method
    public static void main(String[] args) {
        FindEventualSafeStates solution = new FindEventualSafeStates();
        
        // Test case: [[1,2],[2,3],[5],[0],[5],[],[]]
        int[][] graph = {{1,2},{2,3},{5},{0},{5},{},{}};
        
        List<Integer> result1 = solution.eventualSafeNodes(graph);
        System.out.println("Safe nodes (DFS): " + result1); // [2,4,5,6]
        
        List<Integer> result2 = solution.eventualSafeNodesTopological(graph);
        System.out.println("Safe nodes (Topological): " + result2); // [2,4,5,6]
        
        List<Integer> result3 = solution.eventualSafeNodesCycleDetection(graph);
        System.out.println("Safe nodes (Cycle Detection): " + result3); // [2,4,5,6]
    }
}

