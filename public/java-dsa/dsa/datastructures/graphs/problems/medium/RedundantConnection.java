package dsa.datastructures.graphs.problems.medium;
/**
 * LeetCode 684: Redundant Connection
 * 
 * Problem: In this problem, a tree is an undirected graph that is connected and has no cycles. 
 * You are given a graph that started as a tree with n nodes labeled from 1 to n, with one additional edge added.
 * Return an edge that can be removed so that the resulting graph is a tree of n nodes. 
 * If there are multiple answers, return the answer that occurs last in the input.
 * 
 * Time Complexity: O(n * α(n)) where α is inverse Ackermann function
 * Space Complexity: O(n)
 */
public class RedundantConnection {
    
    /**
     * Union-Find solution
     */
    public int[] findRedundantConnection(int[][] edges) {
        int n = edges.length;
        int[] parent = new int[n + 1];
        
        // Initialize parent array
        for (int i = 1; i <= n; i++) {
            parent[i] = i;
        }
        
        for (int[] edge : edges) {
            int root1 = find(parent, edge[0]);
            int root2 = find(parent, edge[1]);
            
            // If both nodes have same root, adding this edge creates cycle
            if (root1 == root2) {
                return edge;
            }
            
            // Union the two components
            parent[root1] = root2;
        }
        
        return new int[0]; // Should never reach here
    }
    
    private int find(int[] parent, int x) {
        if (parent[x] != x) {
            parent[x] = find(parent, parent[x]); // Path compression
        }
        return parent[x];
    }
    
    /**
     * Union-Find with rank optimization
     */
    public int[] findRedundantConnectionWithRank(int[][] edges) {
        int n = edges.length;
        int[] parent = new int[n + 1];
        int[] rank = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            parent[i] = i;
            rank[i] = 1;
        }
        
        for (int[] edge : edges) {
            if (!union(parent, rank, edge[0], edge[1])) {
                return edge;
            }
        }
        
        return new int[0];
    }
    
    private boolean union(int[] parent, int[] rank, int x, int y) {
        int rootX = find(parent, x);
        int rootY = find(parent, y);
        
        if (rootX == rootY) {
            return false; // Already connected, cycle detected
        }
        
        // Union by rank
        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
        } else {
            parent[rootY] = rootX;
            rank[rootX]++;
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        RedundantConnection solution = new RedundantConnection();
        
        // Test case 1: [[1,2],[1,3],[2,3]]
        int[][] edges1 = {{1,2},{1,3},{2,3}};
        int[] result1 = solution.findRedundantConnection(edges1);
        System.out.println("Redundant connection: [" + result1[0] + "," + result1[1] + "]"); // [2,3]
        
        // Test case 2: [[1,2],[2,3],[3,4],[1,4],[1,5]]
        int[][] edges2 = {{1,2},{2,3},{3,4},{1,4},{1,5}};
        int[] result2 = solution.findRedundantConnectionWithRank(edges2);
        System.out.println("Redundant connection: [" + result2[0] + "," + result2[1] + "]"); // [1,4]
    }
}

