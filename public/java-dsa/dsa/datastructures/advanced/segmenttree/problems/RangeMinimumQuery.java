package dsa.datastructures.advanced.segmenttree.problems;

/**
 * Range Minimum Query (RMQ)
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Problem: Find minimum element in range [L, R]
 * Multiple approaches with different trade-offs
 */
public class RangeMinimumQuery {
    
    // Approach 1: Segment Tree - O(log n) query, O(log n) update
    static class SegmentTreeRMQ {
        private int[] tree;
        private int n;
        
        public SegmentTreeRMQ(int[] arr) {
            n = arr.length;
            tree = new int[4 * n];
            build(arr, 0, 0, n - 1);
        }
        
        private void build(int[] arr, int node, int start, int end) {
            if (start == end) {
                tree[node] = arr[start];
                return;
            }
            
            int mid = start + (end - start) / 2;
            build(arr, 2 * node + 1, start, mid);
            build(arr, 2 * node + 2, mid + 1, end);
            tree[node] = Math.min(tree[2 * node + 1], tree[2 * node + 2]);
        }
        
        public int query(int left, int right) {
            return query(0, 0, n - 1, left, right);
        }
        
        private int query(int node, int start, int end, int left, int right) {
            if (right < start || left > end) {
                return Integer.MAX_VALUE;
            }
            
            if (left <= start && end <= right) {
                return tree[node];
            }
            
            int mid = start + (end - start) / 2;
            return Math.min(
                query(2 * node + 1, start, mid, left, right),
                query(2 * node + 2, mid + 1, end, left, right)
            );
        }
        
        public void update(int index, int value) {
            update(0, 0, n - 1, index, value);
        }
        
        private void update(int node, int start, int end, int index, int value) {
            if (start == end) {
                tree[node] = value;
                return;
            }
            
            int mid = start + (end - start) / 2;
            if (index <= mid) {
                update(2 * node + 1, start, mid, index, value);
            } else {
                update(2 * node + 2, mid + 1, end, index, value);
            }
            tree[node] = Math.min(tree[2 * node + 1], tree[2 * node + 2]);
        }
    }
    
    // Approach 2: Sparse Table - O(1) query, no updates, O(n log n) space
    static class SparseTableRMQ {
        private int[][] table;
        private int n;
        
        public SparseTableRMQ(int[] arr) {
            n = arr.length;
            int maxLog = (int) (Math.log(n) / Math.log(2)) + 1;
            table = new int[n][maxLog];
            
            // Initialize for length 1
            for (int i = 0; i < n; i++) {
                table[i][0] = arr[i];
            }
            
            // Build table
            for (int j = 1; (1 << j) <= n; j++) {
                for (int i = 0; i + (1 << j) <= n; i++) {
                    table[i][j] = Math.min(table[i][j - 1], 
                                          table[i + (1 << (j - 1))][j - 1]);
                }
            }
        }
        
        public int query(int left, int right) {
            int len = right - left + 1;
            int k = (int) (Math.log(len) / Math.log(2));
            return Math.min(table[left][k], table[right - (1 << k) + 1][k]);
        }
    }
    
    public static void main(String[] args) {
        int[] arr = {1, 3, 2, 7, 9, 11};
        
        // Test Segment Tree
        SegmentTreeRMQ st = new SegmentTreeRMQ(arr);
        System.out.println("Min [1, 4]: " + st.query(1, 4)); // 2
        st.update(2, 0);
        System.out.println("Min [1, 4] after update: " + st.query(1, 4)); // 0
        
        // Test Sparse Table
        SparseTableRMQ sparse = new SparseTableRMQ(arr);
        System.out.println("Min [1, 4]: " + sparse.query(1, 4)); // 2
    }
}

