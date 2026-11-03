package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 797: All Paths From Source to Target
 * 
 * Problem: Given a directed acyclic graph (DAG) of n nodes labeled from 0 to n - 1, 
 * find all possible paths from node 0 to node n - 1 and return them in any order.
 * The graph is given as follows: graph[i] is a list of all nodes you can visit from node i.
 * 
 * Time Complexity: O(2^n * n) in worst case
 * Space Complexity: O(2^n * n) for storing all paths
 */
public class AllPathsSourceTarget {
    
    /**
     * DFS backtracking solution
     */
    public List<List<Integer>> allPathsSourceTarget(int[][] graph) {
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> path = new ArrayList<>();
        
        path.add(0); // Start from node 0
        dfs(graph, 0, graph.length - 1, path, result);
        
        return result;
    }
    
    private void dfs(int[][] graph, int node, int target, List<Integer> path, List<List<Integer>> result) {
        // If we reached the target, add current path to result
        if (node == target) {
            result.add(new ArrayList<>(path));
            return;
        }
        
        // Explore all neighbors
        for (int neighbor : graph[node]) {
            path.add(neighbor);
            dfs(graph, neighbor, target, path, result);
            path.remove(path.size() - 1); // Backtrack
        }
    }
    
    /**
     * BFS solution
     */
    public List<List<Integer>> allPathsSourceTargetBFS(int[][] graph) {
        List<List<Integer>> result = new ArrayList<>();
        Queue<List<Integer>> queue = new LinkedList<>();
        
        // Start with path containing only node 0
        queue.offer(Arrays.asList(0));
        
        while (!queue.isEmpty()) {
            List<Integer> currentPath = queue.poll();
            int lastNode = currentPath.get(currentPath.size() - 1);
            
            // If we reached target, add to result
            if (lastNode == graph.length - 1) {
                result.add(currentPath);
                continue;
            }
            
            // Explore all neighbors
            for (int neighbor : graph[lastNode]) {
                List<Integer> newPath = new ArrayList<>(currentPath);
                newPath.add(neighbor);
                queue.offer(newPath);
            }
        }
        
        return result;
    }
    
    /**
     * Memoization solution (for optimization if needed)
     */
    public List<List<Integer>> allPathsSourceTargetMemo(int[][] graph) {
        Map<Integer, List<List<Integer>>> memo = new HashMap<>();
        return dfsWithMemo(graph, 0, memo);
    }
    
    private List<List<Integer>> dfsWithMemo(int[][] graph, int node, Map<Integer, List<List<Integer>>> memo) {
        if (memo.containsKey(node)) {
            return memo.get(node);
        }
        
        List<List<Integer>> paths = new ArrayList<>();
        
        // Base case: if we're at target node
        if (node == graph.length - 1) {
            paths.add(Arrays.asList(node));
            memo.put(node, paths);
            return paths;
        }
        
        // Explore all neighbors
        for (int neighbor : graph[node]) {
            List<List<Integer>> neighborPaths = dfsWithMemo(graph, neighbor, memo);
            for (List<Integer> path : neighborPaths) {
                List<Integer> newPath = new ArrayList<>();
                newPath.add(node);
                newPath.addAll(path);
                paths.add(newPath);
            }
        }
        
        memo.put(node, paths);
        return paths;
    }
    
    // Test method
    public static void main(String[] args) {
        AllPathsSourceTarget solution = new AllPathsSourceTarget();
        
        // Test case: [[1,2],[3],[3],[]]
        int[][] graph = {{1,2},{3},{3},{}};
        
        List<List<Integer>> result1 = solution.allPathsSourceTarget(graph);
        System.out.println("All paths (DFS): " + result1); // [[0,1,3],[0,2,3]]
        
        List<List<Integer>> result2 = solution.allPathsSourceTargetBFS(graph);
        System.out.println("All paths (BFS): " + result2); // [[0,1,3],[0,2,3]]
        
        List<List<Integer>> result3 = solution.allPathsSourceTargetMemo(graph);
        System.out.println("All paths (Memo): " + result3); // [[0,1,3],[0,2,3]]
    }
}

