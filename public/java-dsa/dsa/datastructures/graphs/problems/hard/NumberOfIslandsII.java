package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode #305 - Number of Islands II (Premium)
 * 
 * You are given an empty 2D binary grid grid of size m x n.
 * You are given a list of positions where positions[i] = [ri, ci] is the i-th operation to perform.
 * In each operation, grid[ri][ci] becomes 1.
 * 
 * Return an array answer where answer[i] is the number of islands after the i-th operation.
 * An island is surrounded by water and is formed by connecting adjacent lands horizontally or vertically.
 * 
 * Time Complexity: O(k * α(M*N))
 * Space Complexity: O(M * N)
 */
public class NumberOfIslandsII {
    
    class UnionFind {
        private int[] parent;
        private int count;
        
        public UnionFind(int n) {
            parent = new int[n];
            Arrays.fill(parent, -1); // -1 means water
            count = 0;
        }
        
        // Add a new island
        public void addLand(int i) {
            if (parent[i] == -1) {
                parent[i] = i;
                count++;
            }
        }
        
        public boolean isLand(int i) {
            return parent[i] != -1;
        }
        
        public int find(int i) {
            if (parent[i] == i) {
                return i;
            }
            return parent[i] = find(parent[i]);
        }
        
        // Returns true if a merge happened
        public boolean union(int i, int j) {
            int rootI = find(i);
            int rootJ = find(j);
            if (rootI != rootJ) {
                parent[rootI] = rootJ;
                count--;
                return true;
            }
            return false;
        }
        
        public int getCount() {
            return count;
        }
    }
    
    public List<Integer> numIslands2(int m, int n, int[][] positions) {
        List<Integer> results = new ArrayList<>();
        UnionFind uf = new UnionFind(m * n);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        
        for (int[] pos : positions) {
            int r = pos[0];
            int c = pos[1];
            int idx = r * n + c;
            
            // If this is already land, count doesn't change
            if (uf.isLand(idx)) {
                results.add(uf.getCount());
                continue;
            }
            
            // Add the new land
            uf.addLand(idx);
            
            // Check 4 neighbors
            for (int[] dir : directions) {
                int nr = r + dir[0];
                int nc = c + dir[1];
                int n_idx = nr * n + nc;
                
                // Check bounds and if neighbor is land
                if (nr >= 0 && nr < m && nc >= 0 && nc < n && uf.isLand(n_idx)) {
                    uf.union(idx, n_idx);
                }
            }
            
            results.add(uf.getCount());
        }
        
        return results;
    }
}

