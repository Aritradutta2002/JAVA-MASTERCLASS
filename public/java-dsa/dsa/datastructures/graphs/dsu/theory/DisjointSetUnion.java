package dsa.datastructures.graphs.dsu.theory;
/**
 * DISJOINT SET UNION (DSU) / UNION-FIND
 * 
 * ⭐ CORE GRAPH ALGORITHM ⭐
 * 
 * Companies: Google, Facebook, Amazon, Microsoft, Bloomberg
 * 
 * WHY DSU IS A GRAPH ALGORITHM:
 * 1. Manages connected components in GRAPHS
 * 2. Detects cycles in UNDIRECTED GRAPHS
 * 3. Core algorithm for Kruskal's MST (Minimum Spanning Tree)
 * 4. Solves dynamic connectivity in GRAPHS
 * 5. Fundamental for graph theory problems
 * 
 * Use Cases in Graph Problems:
 * - Connected Components (LeetCode #323, #547, #200)
 * - Cycle Detection in Undirected Graphs
 * - Kruskal's MST Algorithm
 * - Network Connectivity
 * - Graph Valid Tree (#261)
 * - Redundant Connection (#684, #685)
 * - Accounts Merge (#721)
 * - Smallest String with Swaps (#1202)
 * 
 * Optimizations:
 * 1. Path Compression: Flatten tree during find - O(α(n))
 * 2. Union by Rank: Attach smaller tree under larger - O(α(n))
 * 
 * Time Complexity (with both optimizations):
 * - Find: O(α(n)) ≈ O(1) amortized (α = inverse Ackermann)
 * - Union: O(α(n)) ≈ O(1) amortized
 * - Connected: O(α(n)) ≈ O(1) amortized
 * 
 * Space: O(n) where n = number of nodes in graph
 */

public class DisjointSetUnion {
    private int[] parent;      // parent[i] = parent of node i
    private int[] rank;        // rank[i] = approximate depth of tree rooted at i
    private int[] size;        // size[i] = size of component containing i
    private int components;    // Number of connected components
    
    /**
     * Initialize DSU for graph with n nodes (0 to n-1)
     */
    public DisjointSetUnion(int n) {
        parent = new int[n];
        rank = new int[n];
        size = new int[n];
        components = n;
        
        // Initially, each node is its own component
        for (int i = 0; i < n; i++) {
            parent[i] = i;  // Self-parent (root of own tree)
            rank[i] = 0;    // Initial rank is 0
            size[i] = 1;    // Single node component
        }
    }
    
    /**
     * FIND with Path Compression
     * 
     * Returns the root (representative) of the component containing x.
     * Path compression: Make all nodes on path point directly to root.
     * 
     * This is the KEY optimization for graph problems!
     */
    public int find(int x) {
        if (parent[x] != x) {
            parent[x] = find(parent[x]); // Path compression (recursive)
        }
        return parent[x];
    }
    
    /**
     * FIND (Iterative with Path Compression)
     * Alternative implementation - slightly more efficient
     */
    public int findIterative(int x) {
        int root = x;
        
        // Find root
        while (parent[root] != root) {
            root = parent[root];
        }
        
        // Path compression: make all nodes point to root
        while (x != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        
        return root;
    }
    
    /**
     * UNION by Rank
     * 
     * Connects two components by making root of one point to root of other.
     * Union by rank: Always attach smaller tree under taller tree.
     * 
     * Returns: true if nodes were in different components (union performed)
     *          false if nodes were already connected (no union needed)
     * 
     * GRAPH APPLICATION: This is how we add EDGES to graph!
     */
    public boolean union(int x, int y) {
        int rootX = find(x);
        int rootY = find(y);
        
        // Already in same component = CYCLE in graph (for tree-building)
        if (rootX == rootY) {
            return false;
        }
        
        // Union by rank: attach shorter tree under taller
        if (rank[rootX] < rank[rootY]) {
            parent[rootX] = rootY;
            size[rootY] += size[rootX];
        } else if (rank[rootX] > rank[rootY]) {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
        } else {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
            rank[rootX]++;  // Only increase rank when trees have equal height
        }
        
        components--;  // Two components merged into one
        return true;
    }
    
    /**
     * UNION by Size (Alternative to Union by Rank)
     * 
     * Sometimes preferred for its simplicity
     */
    public boolean unionBySize(int x, int y) {
        int rootX = find(x);
        int rootY = find(y);
        
        if (rootX == rootY) {
            return false;
        }
        
        // Attach smaller component under larger
        if (size[rootX] < size[rootY]) {
            parent[rootX] = rootY;
            size[rootY] += size[rootX];
        } else {
            parent[rootY] = rootX;
            size[rootX] += size[rootY];
        }
        
        components--;
        return true;
    }
    
    /**
     * CONNECTED - Check if two nodes are in same component
     * 
     * GRAPH APPLICATION: Check if path exists between two nodes
     */
    public boolean connected(int x, int y) {
        return find(x) == find(y);
    }
    
    /**
     * Get number of connected components in graph
     */
    public int getComponents() {
        return components;
    }
    
    /**
     * Get size of component containing node x
     */
    public int getComponentSize(int x) {
        return size[find(x)];
    }
    
    /**
     * CYCLE DETECTION in undirected graph
     * 
     * For each edge (u, v):
     * - If find(u) == find(v): CYCLE EXISTS
     * - Otherwise: union(u, v)
     */
    public boolean hasCycle(int[][] edges) {
        for (int[] edge : edges) {
            int u = edge[0];
            int v = edge[1];
            
            // If both nodes already in same component, adding edge creates cycle
            if (find(u) == find(v)) {
                return true;  // CYCLE DETECTED
            }
            
            union(u, v);
        }
        return false;  // No cycle
    }
    
    /**
     * CHECK if graph is a VALID TREE
     * 
     * A valid tree with n nodes must have:
     * 1. Exactly n-1 edges
     * 2. All nodes connected (1 component)
     * 3. No cycles
     */
    public boolean isValidTree(int n, int[][] edges) {
        // Tree must have exactly n-1 edges
        if (edges.length != n - 1) {
            return false;
        }
        
        // Build graph using DSU
        for (int[] edge : edges) {
            // If nodes already connected, adding edge creates cycle
            if (!union(edge[0], edge[1])) {
                return false;  // Cycle found
            }
        }
        
        // All nodes must be in one component
        return components == 1;
    }
    
    /**
     * Get all nodes in same component as x
     * (Useful for some graph problems)
     */
    public java.util.List<Integer> getComponent(int x) {
        int root = find(x);
        java.util.List<Integer> component = new java.util.ArrayList<>();
        
        for (int i = 0; i < parent.length; i++) {
            if (find(i) == root) {
                component.add(i);
            }
        }
        
        return component;
    }
    
    /**
     * Reset DSU (useful for testing)
     */
    public void reset() {
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
            rank[i] = 0;
            size[i] = 1;
        }
        components = parent.length;
    }
    
    /**
     * Print current state (for debugging)
     */
    public void printState() {
        System.out.println("Parent: " + java.util.Arrays.toString(parent));
        System.out.println("Rank:   " + java.util.Arrays.toString(rank));
        System.out.println("Size:   " + java.util.Arrays.toString(size));
        System.out.println("Components: " + components);
    }
    
    public static void main(String[] args) {
        System.out.println("=== DSU for Graph Problems ===\n");
        
        // Example 1: Connected Components
        System.out.println("Example 1: Connected Components");
        System.out.println("Graph edges: (0,1), (1,2), (3,4)");
        DisjointSetUnion dsu = new DisjointSetUnion(5);
        
        dsu.union(0, 1);
        dsu.union(1, 2);
        dsu.union(3, 4);
        
        System.out.println("Components: " + dsu.getComponents()); // 2
        System.out.println("Connected(0, 2): " + dsu.connected(0, 2)); // true
        System.out.println("Connected(0, 3): " + dsu.connected(0, 3)); // false
        
        // Example 2: Cycle Detection
        System.out.println("\n Example 2: Cycle Detection");
        DisjointSetUnion dsu2 = new DisjointSetUnion(4);
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}};
        System.out.println("Edges: (0,1), (1,2), (2,3), (3,0)");
        System.out.println("Has cycle: " + dsu2.hasCycle(edges)); // true
        
        // Example 3: Valid Tree
        System.out.println("\nExample 3: Valid Tree Check");
        DisjointSetUnion dsu3 = new DisjointSetUnion(5);
        int[][] treeEdges = {{0, 1}, {0, 2}, {0, 3}, {1, 4}};
        System.out.println("Is valid tree: " + dsu3.isValidTree(5, treeEdges)); // true
        
        // Example 4: Component Size
        System.out.println("\nExample 4: Component Sizes");
        DisjointSetUnion dsu4 = new DisjointSetUnion(10);
        dsu4.union(0, 1); dsu4.union(1, 2); dsu4.union(2, 3);  // Component of size 4
        dsu4.union(5, 6); dsu4.union(6, 7);                     // Component of size 3
        
        System.out.println("Size of component containing 0: " + dsu4.getComponentSize(0)); // 4
        System.out.println("Size of component containing 5: " + dsu4.getComponentSize(5)); // 3
        System.out.println("Total components: " + dsu4.getComponents()); // 6
        
        // Interview Tips
        System.out.println("\n=== GRAPH ALGORITHM TIPS ===");
        System.out.println("1. DSU is PERFECT for dynamic graph connectivity");
        System.out.println("2. Use for: connected components, cycle detection, MST");
        System.out.println("3. ALWAYS use path compression + union by rank");
        System.out.println("4. In graphs: union() = add edge, find() = check connectivity");
        System.out.println("5. If find(u) == find(v) before union: CYCLE EXISTS");
        System.out.println("6. Time: O(α(n)) ≈ O(1) per operation with optimizations");
        System.out.println("7. For directed graphs: use DFS/BFS, not DSU");
    }
}

