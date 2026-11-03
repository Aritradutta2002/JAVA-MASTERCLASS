package dsa.datastructures.advanced.segmenttree.basics;

/**
 * Binary Indexed Tree (Fenwick Tree)
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Simpler alternative to Segment Tree for:
 * - Prefix sum queries
 * - Point updates
 * 
 * Advantages over Segment Tree:
 * - Less space: O(n) vs O(4n)
 * - Simpler implementation
 * - Faster in practice
 * 
 * Time: O(log n) for both query and update
 * Space: O(n)
 */
public class BinaryIndexedTree {
    
    private int[] bit;
    private int n;
    
    public BinaryIndexedTree(int size) {
        n = size;
        bit = new int[n + 1]; // 1-indexed
    }
    
    public BinaryIndexedTree(int[] arr) {
        n = arr.length;
        bit = new int[n + 1];
        
        for (int i = 0; i < n; i++) {
            update(i, arr[i]);
        }
    }
    
    // Update value at index by delta
    public void update(int index, int delta) {
        index++; // Convert to 1-indexed
        
        while (index <= n) {
            bit[index] += delta;
            index += index & (-index); // Add last set bit
        }
    }
    
    // Get prefix sum [0, index]
    public int query(int index) {
        index++; // Convert to 1-indexed
        int sum = 0;
        
        while (index > 0) {
            sum += bit[index];
            index -= index & (-index); // Remove last set bit
        }
        
        return sum;
    }
    
    // Get range sum [left, right]
    public int rangeQuery(int left, int right) {
        if (left == 0) return query(right);
        return query(right) - query(left - 1);
    }
    
    // 2D Binary Indexed Tree
    static class BIT2D {
        private int[][] bit;
        private int m, n;
        
        public BIT2D(int rows, int cols) {
            m = rows;
            n = cols;
            bit = new int[m + 1][n + 1];
        }
        
        public void update(int row, int col, int delta) {
            for (int i = row + 1; i <= m; i += i & (-i)) {
                for (int j = col + 1; j <= n; j += j & (-j)) {
                    bit[i][j] += delta;
                }
            }
        }
        
        public int query(int row, int col) {
            int sum = 0;
            for (int i = row + 1; i > 0; i -= i & (-i)) {
                for (int j = col + 1; j > 0; j -= j & (-j)) {
                    sum += bit[i][j];
                }
            }
            return sum;
        }
        
        public int rangeQuery(int row1, int col1, int row2, int col2) {
            return query(row2, col2) 
                 - query(row1 - 1, col2) 
                 - query(row2, col1 - 1) 
                 + query(row1 - 1, col1 - 1);
        }
    }
    
    public static void main(String[] args) {
        // Test 1D BIT
        int[] arr = {1, 3, 5, 7, 9, 11};
        BinaryIndexedTree bit = new BinaryIndexedTree(arr);
        
        System.out.println("Sum [0, 3]: " + bit.rangeQuery(0, 3)); // 16
        
        bit.update(1, 7); // Add 7 to index 1
        System.out.println("Sum [0, 3] after update: " + bit.rangeQuery(0, 3)); // 23
        
        // Test 2D BIT
        System.out.println("\n2D BIT:");
        BIT2D bit2d = new BIT2D(3, 3);
        bit2d.update(0, 0, 1);
        bit2d.update(1, 1, 2);
        bit2d.update(2, 2, 3);
        System.out.println("Sum [0,0] to [2,2]: " + bit2d.rangeQuery(0, 0, 2, 2)); // 6
    }
}

