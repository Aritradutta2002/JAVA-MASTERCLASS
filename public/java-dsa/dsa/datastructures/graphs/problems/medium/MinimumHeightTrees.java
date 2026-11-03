package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * LeetCode 310: Minimum Height Trees
 * 
 * Problem: A tree is an undirected graph in which any two vertices are connected by exactly one path. 
 * Given a tree of n nodes labelled from 0 to n - 1, and an array of n - 1 edges where edges[i] = [ai, bi] 
 * indicates that there is an undirected edge between the two nodes ai and bi in the tree, you can choose 
 * any node of the tree as the root. When you select a node x as the root, the result tree has height h. 
 * Among all possible rooted trees, those with minimum height are called minimum height trees (MHTs).
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(n)
 */
public class MinimumHeightTrees {
    
    /**
     * Topological sort solution - remove leaves layer by layer
     */
    public List<Integer> findMinHeightTrees(int n, int[][] edges) {
        // Edge case: single node
        if (n == 1) {
            return Arrays.asList(0);
        }
        
        // Build adjacency list and degree array
        List<Set<Integer>> graph = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            graph.add(new HashSet<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
        }
        
        // Find initial leaves (nodes with degree 1)
        Queue<Integer> leaves = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (graph.get(i).size() == 1) {
                leaves.offer(i);
            }
        }
        
        int remaining = n;
        
        // Remove leaves layer by layer until we have 1 or 2 nodes left
        while (remaining > 2) {
            int leafCount = leaves.size();
            remaining -= leafCount;
            
            for (int i = 0; i < leafCount; i++) {
                int leaf = leaves.poll();
                
                // Remove leaf from its neighbor
                int neighbor = graph.get(leaf).iterator().next();
                graph.get(neighbor).remove(leaf);
                
                // If neighbor becomes a leaf, add it to queue
                if (graph.get(neighbor).size() == 1) {
                    leaves.offer(neighbor);
                }
            }
        }
        
        // The remaining nodes are the roots of MHTs
        return new ArrayList<>(leaves);
    }
    
    /**
     * Alternative implementation with degree array
     */
    public List<Integer> findMinHeightTreesAlternative(int n, int[][] edges) {
        if (n == 1) return Arrays.asList(0);
        
        // Build adjacency list
        List<List<Integer>> graph = new ArrayList<>();
        int[] degree = new int[n];
        
        for (int i = 0; i < n; i++) {
            graph.add(new ArrayList<>());
        }
        
        for (int[] edge : edges) {
            graph.get(edge[0]).add(edge[1]);
            graph.get(edge[1]).add(edge[0]);
            degree[edge[0]]++;
            degree[edge[1]]++;
        }
        
        // Find leaves
        Queue<Integer> queue = new LinkedList<>();
        for (int i = 0; i < n; i++) {
            if (degree[i] == 1) {
                queue.offer(i);
            }
        }
        
        int remaining = n;
        
        while (remaining > 2) {
            int size = queue.size();
            remaining -= size;
            
            for (int i = 0; i < size; i++) {
                int node = queue.poll();
                
                for (int neighbor : graph.get(node)) {
                    degree[neighbor]--;
                    if (degree[neighbor] == 1) {
                        queue.offer(neighbor);
                    }
                }
            }
        }
        
        List<Integer> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            result.add(queue.poll());
        }
        
        return result;
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumHeightTrees solution = new MinimumHeightTrees();
        
        // Test case 1: n = 4, edges = [[1,0],[1,2],[1,3]]
        int[][] edges1 = {{1,0},{1,2},{1,3}};
        List<Integer> result1 = solution.findMinHeightTrees(4, edges1);
        System.out.println("MHT roots: " + result1); // [1]
        
        // Test case 2: n = 6, edges = [[3,0],[3,1],[3,2],[3,4],[5,4]]
        int[][] edges2 = {{3,0},{3,1},{3,2},{3,4},{5,4}};
        List<Integer> result2 = solution.findMinHeightTreesAlternative(6, edges2);
        System.out.println("MHT roots: " + result2); // [3,4]
    }
}

