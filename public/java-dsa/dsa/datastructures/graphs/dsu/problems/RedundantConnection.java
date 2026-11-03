package dsa.datastructures.graphs.dsu.problems;
/**
 * REDUNDANT CONNECTION
 * LeetCode #684 - Medium
 * 
 * Companies: Amazon, Google, Microsoft
 * 
 * Problem: In this problem, a tree is an undirected graph that is connected and has no cycles.
 * You are given a graph that started as a tree with n nodes labeled from 1 to n, 
 * with one additional edge added. Return an edge that can be removed so that 
 * the resulting graph is a tree of n nodes.
 * 
 * Key Insight: Use DSU to detect which edge creates a cycle
 */

import java.util.*;

public class RedundantConnection {
    
    public int[] findRedundantConnection(int[][] edges) {
        int n = edges.length;
        DSU dsu = new DSU(n + 1); // Nodes are 1-indexed
        
        for (int[] edge : edges) {
            // If nodes already connected, this edge creates cycle
            if (!dsu.union(edge[0], edge[1])) {
                return edge; // This is the redundant edge
            }
        }
        
        return new int[]{};
    }
    
    // DSU Helper
    class DSU {
        int[] parent, rank;
        
        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) parent[i] = i;
        }
        
        int find(int x) {
            if (parent[x] != x) parent[x] = find(parent[x]);
            return parent[x];
        }
        
        boolean union(int x, int y) {
            int rootX = find(x), rootY = find(y);
            if (rootX == rootY) return false;
            
            if (rank[rootX] < rank[rootY]) parent[rootX] = rootY;
            else if (rank[rootX] > rank[rootY]) parent[rootY] = rootX;
            else { parent[rootY] = rootX; rank[rootX]++; }
            
            return true;
        }
    }
    
    public static void main(String[] args) {
        RedundantConnection sol = new RedundantConnection();
        
        int[][] edges1 = {{1,2}, {1,3}, {2,3}};
        System.out.println("Test 1: " + Arrays.toString(sol.findRedundantConnection(edges1))); // [2,3]
        
        int[][] edges2 = {{1,2}, {2,3}, {3,4}, {1,4}, {1,5}};
        System.out.println("Test 2: " + Arrays.toString(sol.findRedundantConnection(edges2))); // [1,4]
    }
}

