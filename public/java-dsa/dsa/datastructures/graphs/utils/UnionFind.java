package dsa.datastructures.graphs.utils;

/**
 * Union-Find (Disjoint Set Union) Data Structure
 * 
 * Efficiently maintains disjoint sets and supports:
 * - Union: Merge two sets
 * - Find: Determine which set an element belongs to
 * - Connected: Check if two elements are in the same set
 * 
 * Optimizations Used:
 * 1. Union by Rank: Always attach smaller tree under root of larger tree
 * 2. Path Compression: Make every node point directly to root during find
 * 3. Size Tracking: Maintain size of each component for additional queries
 * 
 * Time Complexity (with both optimizations):
 * - Constructor: O(n)
 * - Find: O(α(n)) where α is inverse Ackermann function (practically constant)
 * - Union: O(α(n))
 * - Connected: O(α(n))
 * 
 * Space Complexity: O(n)
 * 
 * Common Applications:
 * - Kruskal's MST algorithm
 * - Cycle detection in undirected graphs
 * - Dynamic connectivity queries
 * - Percolation problems
 */
public class UnionFind {
    private final int[] parent;           // parent[i] = parent of element i
    private final int[] rank;             // rank[i] = approximate depth of tree rooted at i
    private final int[] size;             // size[i] = size of component rooted at i
    public int componentCount;            // Number of disjoint components
    
    /**
     * Creates a Union-Find structure with n elements (0 to n-1)
     * Initially each element is in its own component
     * 
     * @param n Number of elements
     * @throws IllegalArgumentException if n <= 0
     */
    public UnionFind(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + n);
        }
        
        parent = new int[n];
        rank = new int[n];
        size = new int[n];
        componentCount = n;
        
        // Initialize: each element is its own parent with rank 0 and size 1
        for (int i = 0; i < n; i++) {
            parent[i] = i;
            rank[i] = 0;
            size[i] = 1;
        }
    }
    
    /**
     * Find the root of the component containing x
     * Uses path compression: makes every node on path point to root
     * 
     * @param x Element to find
     * @return Root of component containing x
     * @throws IllegalArgumentException if x is out of bounds
     */
    public int find(int x) {
        validateElement(x);
        
        // Path compression: recursively find root and update parent
        if (parent[x] != x) {
            parent[x] = find(parent[x]);
        }
        return parent[x];
    }
    
    /**
     * Alternative iterative find with path compression
     * Sometimes preferred for very deep trees to avoid stack overflow
     * 
     * @param x Element to find
     * @return Root of component containing x
     */
    public int findIterative(int x) {
        validateElement(x);
        
        // First pass: find the root
        int root = x;
        while (parent[root] != root) {
            root = parent[root];
        }
        
        // Second pass: path compression
        while (parent[x] != root) {
            int next = parent[x];
            parent[x] = root;
            x = next;
        }
        
        return root;
    }
    
    /**
     * Union two components using union by rank optimization
     * Always attaches tree with smaller rank under tree with larger rank
     * 
     * @param a First element
     * @param b Second element
     * @return true if union was performed (elements were in different components)
     */
    public boolean union(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        
        // Already in same component
        if (rootA == rootB) {
            return false;
        }
        
        // Union by rank: attach smaller tree under larger tree
        if (rank[rootA] < rank[rootB]) {
            parent[rootA] = rootB;
            size[rootB] += size[rootA];
        } else if (rank[rootA] > rank[rootB]) {
            parent[rootB] = rootA;
            size[rootA] += size[rootB];
        } else {
            // Same rank: arbitrarily choose rootA as new root
            parent[rootB] = rootA;
            size[rootA] += size[rootB];
            rank[rootA]++; // Rank increases only when merging equal rank trees
        }
        
        componentCount--;
        return true;
    }
    
    /**
     * Union by size optimization (alternative to union by rank)
     * Always attaches smaller component to larger component
     * Often performs slightly better in practice than union by rank
     * 
     * @param a First element
     * @param b Second element
     * @return true if union was performed
     */
    public boolean unionBySize(int a, int b) {
        int rootA = find(a);
        int rootB = find(b);
        
        if (rootA == rootB) {
            return false;
        }
        
        // Attach smaller component to larger component
        if (size[rootA] < size[rootB]) {
            parent[rootA] = rootB;
            size[rootB] += size[rootA];
        } else {
            parent[rootB] = rootA;
            size[rootA] += size[rootB];
        }
        
        componentCount--;
        return true;
    }
    
    /**
     * Check if two elements are in the same component
     * 
     * @param a First element
     * @param b Second element
     * @return true if elements are connected
     */
    public boolean connected(int a, int b) {
        return find(a) == find(b);
    }
    
    /**
     * Get the size of the component containing element x
     * 
     * @param x Element to check
     * @return Size of component containing x
     */
    public int getComponentSize(int x) {
        return size[find(x)];
    }
    
    /**
     * Get the number of disjoint components
     * 
     * @return Number of components
     */
    public int getComponentCount() {
        return componentCount;
    }
    
    /**
     * Validate that element is within bounds
     * 
     * @param x Element to validate
     * @throws IllegalArgumentException if x is out of bounds
     */
    private void validateElement(int x) {
        if (x < 0 || x >= parent.length) {
            throw new IllegalArgumentException(
                String.format("Element %d is out of bounds [0, %d)", x, parent.length)
            );
        }
    }
    
    /**
     * Get all components as a visualization aid
     * Time Complexity: O(n * α(n))
     * 
     * @return String representation of all components
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("UnionFind: %d elements, %d components\n", 
                  parent.length, componentCount));
        
        java.util.Map<Integer, java.util.List<Integer>> components = new java.util.HashMap<>();
        for (int i = 0; i < parent.length; i++) {
            int root = find(i);
            components.computeIfAbsent(root, k -> new java.util.ArrayList<>()).add(i);
        }
        
        for (java.util.Map.Entry<Integer, java.util.List<Integer>> entry : components.entrySet()) {
            sb.append(String.format("Component %d: %s\n", entry.getKey(), entry.getValue()));
        }
        
        return sb.toString();
    }
}


