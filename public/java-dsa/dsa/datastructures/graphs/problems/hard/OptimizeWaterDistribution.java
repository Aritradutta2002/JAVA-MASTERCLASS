package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #1168 - Optimize Water Distribution in a Village (Premium)
 * 
 * There are n houses in a village. You are given an array wells of size n where wells[i] is the cost
 * to build a well in house i.
 * You are also given an array pipes where pipes[i] = [house1, house2, cost] represents the cost
 * to connect house1 and house2 with a pipe.
 * 
 * You need to supply water to all houses. You can build wells or connect houses with pipes.
 * Return the minimum total cost.
 * 
 * Time Complexity: O((E + N) log (E + N))
 * Space Complexity: O(E + N)
 */
public class OptimizeWaterDistribution {
    
    class UnionFind {
        private int[] parent;
        
        public UnionFind(int n) {
            parent = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        
        public int find(int i) {
            if (parent[i] == i) return i;
            return parent[i] = find(parent[i]);
        }
        
        public boolean union(int i, int j) {
            int rootI = find(i);
            int rootJ = find(j);
            if (rootI == rootJ) return false;
            parent[rootI] = rootJ;
            return true;
        }
    }
    
    public int minCostToSupplyWater(int n, int[] wells, int[][] pipes) {
        // Create a single list of all edges (pipes + wells)
        // We use n+1 nodes (0 to n). Node 0 is the virtual water source.
        List<int[]> edges = new ArrayList<>();
        
        // Add well "edges"
        for (int i = 0; i < wells.length; i++) {
            // Edge from virtual node 0 to house i+1
            edges.add(new int[]{0, i + 1, wells[i]});
        }
        
        // Add pipe edges
        for (int[] pipe : pipes) {
            edges.add(new int[]{pipe[0], pipe[1], pipe[2]});
        }
        
        // Sort all edges by cost
        Collections.sort(edges, (a, b) -> a[2] - b[2]);
        
        // Run Kruskal's algorithm
        UnionFind uf = new UnionFind(n + 1);
        int totalCost = 0;
        int edgesUsed = 0;
        
        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            int cost = edge[2];
            
            // If union is successful (no cycle)
            if (uf.union(u, v)) {
                totalCost += cost;
                edgesUsed++;
            }
            
            // Optimization: If we've connected all n+1 nodes, we can stop
            if (edgesUsed == n) {
                break;
            }
        }
        
        return totalCost;
    }
}

