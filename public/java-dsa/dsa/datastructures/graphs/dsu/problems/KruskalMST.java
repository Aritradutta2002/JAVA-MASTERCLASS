package dsa.datastructures.graphs.dsu.problems;
/**
 * KRUSKAL'S MINIMUM SPANNING TREE (MST) ALGORITHM
 * 
 * ⭐ CLASSIC DSU APPLICATION ⭐
 * 
 * Companies: Google, Amazon, Microsoft, Facebook
 * 
 * Problem: Find minimum cost to connect all nodes in weighted undirected graph
 * 
 * Algorithm:
 * 1. Sort all edges by weight (ascending)
 * 2. Use DSU to detect cycles
 * 3. Add edge if it doesn't create cycle
 * 4. Stop when we have V-1 edges (spanning tree)
 * 
 * Time: O(E log E) for sorting + O(E α(V)) for DSU ≈ O(E log E)
 * Space: O(V) for DSU
 * 
 * Related LeetCode:
 * - #1584: Min Cost to Connect All Points
 * - #1135: Connecting Cities With Minimum Cost
 * - #1168: Optimize Water Distribution
 */

import java.util.*;

public class KruskalMST {
    
    /**
     * Edge class for weighted graph
     */
    static class Edge implements Comparable<Edge> {
        int src, dest, weight;
        
        Edge(int src, int dest, int weight) {
            this.src = src;
            this.dest = dest;
            this.weight = weight;
        }
        
        @Override
        public int compareTo(Edge other) {
            return this.weight - other.weight;
        }
        
        @Override
        public String toString() {
            return "(" + src + "-" + dest + ", w=" + weight + ")";
        }
    }
    
    /**
     * Kruskal's MST Algorithm
     * 
     * Returns: List of edges in MST and total cost
     */
    public static int[] kruskalMST(int vertices, List<Edge> edges) {
        // Sort edges by weight
        Collections.sort(edges);
        
        DSU dsu = new DSU(vertices);
        List<Edge> mst = new ArrayList<>();
        int totalCost = 0;
        
        for (Edge edge : edges) {
            // If adding edge doesn't create cycle
            if (dsu.union(edge.src, edge.dest)) {
                mst.add(edge);
                totalCost += edge.weight;
                
                // MST complete when we have V-1 edges
                if (mst.size() == vertices - 1) {
                    break;
                }
            }
        }
        
        // Print MST edges
        System.out.println("MST Edges:");
        for (Edge e : mst) {
            System.out.println(e);
        }
        
        return new int[]{totalCost, mst.size()};
    }
    
    /**
     * MIN COST TO CONNECT ALL POINTS
     * LeetCode #1584 - Medium
     * 
     * Connect all points with minimum Manhattan distance
     */
    public int minCostConnectPoints(int[][] points) {
        int n = points.length;
        List<Edge> edges = new ArrayList<>();
        
        // Generate all possible edges with Manhattan distance
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                int distance = Math.abs(points[i][0] - points[j][0]) + 
                              Math.abs(points[i][1] - points[j][1]);
                edges.add(new Edge(i, j, distance));
            }
        }
        
        // Apply Kruskal's algorithm
        Collections.sort(edges);
        DSU dsu = new DSU(n);
        int totalCost = 0;
        int edgesAdded = 0;
        
        for (Edge edge : edges) {
            if (dsu.union(edge.src, edge.dest)) {
                totalCost += edge.weight;
                edgesAdded++;
                
                if (edgesAdded == n - 1) break;
            }
        }
        
        return totalCost;
    }
    
    /**
     * CONNECTING CITIES WITH MINIMUM COST
     * LeetCode #1135 - Medium (Premium)
     * 
     * Connect N cities with minimum cost, return -1 if impossible
     */
    public int minimumCost(int n, int[][] connections) {
        // connections[i] = [city1, city2, cost]
        List<Edge> edges = new ArrayList<>();
        
        for (int[] conn : connections) {
            edges.add(new Edge(conn[0] - 1, conn[1] - 1, conn[2])); // 0-indexed
        }
        
        Collections.sort(edges);
        DSU dsu = new DSU(n);
        int totalCost = 0;
        int edgesAdded = 0;
        
        for (Edge edge : edges) {
            if (dsu.union(edge.src, edge.dest)) {
                totalCost += edge.weight;
                edgesAdded++;
                
                if (edgesAdded == n - 1) {
                    return totalCost;
                }
            }
        }
        
        return -1; // Cannot connect all cities
    }
    
    /**
     * FIND CRITICAL AND PSEUDO-CRITICAL EDGES IN MST
     * LeetCode #1489 - Hard
     * 
     * Critical edge: removing it increases MST weight
     * Pseudo-critical: can be part of some MST but not all
     */
    public List<List<Integer>> findCriticalAndPseudoCriticalEdges(int n, int[][] edges) {
        // Add index to each edge
        List<int[]> edgeList = new ArrayList<>();
        for (int i = 0; i < edges.length; i++) {
            edgeList.add(new int[]{edges[i][0], edges[i][1], edges[i][2], i});
        }
        
        // Sort by weight
        edgeList.sort((a, b) -> a[2] - b[2]);
        
        // Find original MST weight
        int originalMST = findMSTWeight(n, edgeList, -1, -1);
        
        List<Integer> critical = new ArrayList<>();
        List<Integer> pseudoCritical = new ArrayList<>();
        
        for (int i = 0; i < edgeList.size(); i++) {
            int edgeIdx = edgeList.get(i)[3];
            
            // Check if critical: exclude this edge
            int mstWithoutEdge = findMSTWeight(n, edgeList, i, -1);
            if (mstWithoutEdge > originalMST) {
                critical.add(edgeIdx);
                continue;
            }
            
            // Check if pseudo-critical: force include this edge
            int mstWithEdge = findMSTWeight(n, edgeList, -1, i);
            if (mstWithEdge == originalMST) {
                pseudoCritical.add(edgeIdx);
            }
        }
        
        return Arrays.asList(critical, pseudoCritical);
    }
    
    private int findMSTWeight(int n, List<int[]> edges, int excludeIdx, int includeIdx) {
        DSU dsu = new DSU(n);
        int weight = 0;
        int edgeCount = 0;
        
        // Force include edge if specified
        if (includeIdx != -1) {
            int[] edge = edges.get(includeIdx);
            dsu.union(edge[0], edge[1]);
            weight += edge[2];
            edgeCount++;
        }
        
        // Add other edges
        for (int i = 0; i < edges.size(); i++) {
            if (i == excludeIdx) continue;
            
            int[] edge = edges.get(i);
            if (dsu.union(edge[0], edge[1])) {
                weight += edge[2];
                edgeCount++;
                
                if (edgeCount == n - 1) break;
            }
        }
        
        return edgeCount == n - 1 ? weight : Integer.MAX_VALUE;
    }
    
    // DSU Helper Class
    static class DSU {
        int[] parent, rank;
        
        DSU(int n) {
            parent = new int[n];
            rank = new int[n];
            for (int i = 0; i < n; i++) {
                parent[i] = i;
            }
        }
        
        int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]);
            }
            return parent[x];
        }
        
        boolean union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            
            if (rootX == rootY) return false;
            
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
    }
    
    public static void main(String[] args) {
        System.out.println("=== Kruskal's MST Algorithm ===\n");
        
        // Example 1: Basic MST
        System.out.println("Example 1: Basic Graph");
        List<Edge> edges1 = new ArrayList<>();
        edges1.add(new Edge(0, 1, 10));
        edges1.add(new Edge(0, 2, 6));
        edges1.add(new Edge(0, 3, 5));
        edges1.add(new Edge(1, 3, 15));
        edges1.add(new Edge(2, 3, 4));
        
        int[] result1 = kruskalMST(4, edges1);
        System.out.println("Total MST Cost: " + result1[0]);
        System.out.println("Edges in MST: " + result1[1]);
        
        // Example 2: Min Cost to Connect Points
        System.out.println("\n=== Min Cost to Connect Points ===");
        KruskalMST solution = new KruskalMST();
        int[][] points = {{0,0}, {2,2}, {3,10}, {5,2}, {7,0}};
        System.out.println("Points: " + Arrays.deepToString(points));
        System.out.println("Min cost: " + solution.minCostConnectPoints(points));
        
        // Example 3: Connecting Cities
        System.out.println("\n=== Connecting Cities ===");
        int[][] connections = {{1,2,5}, {1,3,6}, {2,3,1}};
        System.out.println("Min cost to connect 3 cities: " + 
            solution.minimumCost(3, connections));
        
        // Interview Tips
        System.out.println("\n=== KRUSKAL'S ALGORITHM TIPS ===");
        System.out.println("1. Kruskal = Sort edges + DSU for cycle detection");
        System.out.println("2. Greedy approach: always pick minimum weight edge");
        System.out.println("3. DSU prevents cycles (tree property)");
        System.out.println("4. Time: O(E log E) dominated by sorting");
        System.out.println("5. When to use: Sparse graphs (E << V²)");
        System.out.println("6. Alternative: Prim's algorithm (better for dense graphs)");
        System.out.println("7. MST has exactly V-1 edges");
    }
}

