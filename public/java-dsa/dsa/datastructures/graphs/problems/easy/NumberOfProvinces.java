package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 547: Number of Provinces
 * 
 * Problem: There are n cities. Some of them are connected, while some are not.
 * If city a is connected directly with city b, and city b is connected directly 
 * with city c, then city a is connected indirectly with city c.
 * A province is a group of directly or indirectly connected cities.
 * Given an n x n matrix isConnected where isConnected[i][j] = 1 if the ith city 
 * and the jth city are directly connected, and isConnected[i][j] = 0 otherwise,
 * return the total number of provinces.
 * 
 * Time Complexity: O(n²)
 * Space Complexity: O(n) for visited array and recursion stack
 */
public class NumberOfProvinces {
    
    /**
     * DFS Solution
     */
    public int findCircleNum(int[][] isConnected) {
        if (isConnected == null || isConnected.length == 0) {
            return 0;
        }
        
        int n = isConnected.length;
        boolean[] visited = new boolean[n];
        int provinces = 0;
        
        // Check each city
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                // Found a new province, explore all connected cities
                dfs(isConnected, visited, i);
                provinces++;
            }
        }
        
        return provinces;
    }
    
    /**
     * DFS helper method to mark all connected cities as visited
     */
    private void dfs(int[][] isConnected, boolean[] visited, int city) {
        visited[city] = true;
        
        // Check all other cities
        for (int j = 0; j < isConnected.length; j++) {
            // If city j is connected to current city and not visited
            if (isConnected[city][j] == 1 && !visited[j]) {
                dfs(isConnected, visited, j);
            }
        }
    }
    
    /**
     * BFS Solution
     */
    public int findCircleNumBFS(int[][] isConnected) {
        if (isConnected == null || isConnected.length == 0) return 0;
        
        int n = isConnected.length;
        boolean[] visited = new boolean[n];
        int provinces = 0;
        
        java.util.Queue<Integer> queue = new java.util.LinkedList<>();
        
        for (int i = 0; i < n; i++) {
            if (!visited[i]) {
                queue.offer(i);
                visited[i] = true;
                
                while (!queue.isEmpty()) {
                    int city = queue.poll();
                    
                    for (int j = 0; j < n; j++) {
                        if (isConnected[city][j] == 1 && !visited[j]) {
                            visited[j] = true;
                            queue.offer(j);
                        }
                    }
                }
                
                provinces++;
            }
        }
        
        return provinces;
    }
    
    /**
     * Union-Find Solution
     */
    public int findCircleNumUnionFind(int[][] isConnected) {
        if (isConnected == null || isConnected.length == 0) return 0;
        
        int n = isConnected.length;
        UnionFind uf = new UnionFind(n);
        
        // Union connected cities
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (isConnected[i][j] == 1) {
                    uf.union(i, j);
                }
            }
        }
        
        return uf.getComponents();
    }
    
    /**
     * Union-Find data structure
     */
    class UnionFind {
        private int[] parent;
        private int[] rank;
        private int components;
        
        public UnionFind(int n) {
            parent = new int[n];
            rank = new int[n];
            components = n;
            
            for (int i = 0; i < n; i++) {
                parent[i] = i;
                rank[i] = 0;
            }
        }
        
        public int find(int x) {
            if (parent[x] != x) {
                parent[x] = find(parent[x]); // Path compression
            }
            return parent[x];
        }
        
        public void union(int x, int y) {
            int rootX = find(x);
            int rootY = find(y);
            
            if (rootX != rootY) {
                // Union by rank
                if (rank[rootX] < rank[rootY]) {
                    parent[rootX] = rootY;
                } else if (rank[rootX] > rank[rootY]) {
                    parent[rootY] = rootX;
                } else {
                    parent[rootY] = rootX;
                    rank[rootX]++;
                }
                components--;
            }
        }
        
        public int getComponents() {
            return components;
        }
    }
    
    // Test method
    public static void main(String[] args) {
        NumberOfProvinces solution = new NumberOfProvinces();
        
        // Test case 1
        int[][] isConnected1 = {
            {1, 1, 0},
            {1, 1, 0},
            {0, 0, 1}
        };
        
        System.out.println("Test 1 - DFS: " + solution.findCircleNum(isConnected1)); // Output: 2
        System.out.println("Test 1 - BFS: " + solution.findCircleNumBFS(isConnected1)); // Output: 2
        System.out.println("Test 1 - Union-Find: " + solution.findCircleNumUnionFind(isConnected1)); // Output: 2
        
        // Test case 2
        int[][] isConnected2 = {
            {1, 0, 0},
            {0, 1, 0},
            {0, 0, 1}
        };
        
        System.out.println("Test 2 - DFS: " + solution.findCircleNum(isConnected2)); // Output: 3
        System.out.println("Test 2 - BFS: " + solution.findCircleNumBFS(isConnected2)); // Output: 3
        System.out.println("Test 2 - Union-Find: " + solution.findCircleNumUnionFind(isConnected2)); // Output: 3
    }
}

