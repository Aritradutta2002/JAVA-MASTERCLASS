package dsa.datastructures.advanced.segmenttree.basics;

/**
 * Segment Tree with Lazy Propagation
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Use Case: Range updates and range queries
 * Time: O(log n) per operation
 * Space: O(n)
 */
public class LazyPropagation {
    
    private long[] tree;
    private long[] lazy;
    private int n;
    
    public LazyPropagation(int[] arr) {
        n = arr.length;
        tree = new long[4 * n];
        lazy = new long[4 * n];
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
        tree[node] = tree[2 * node + 1] + tree[2 * node + 2];
    }
    
    // Push lazy value down to children
    private void push(int node, int start, int end) {
        if (lazy[node] != 0) {
            tree[node] += (end - start + 1) * lazy[node];
            
            if (start != end) {
                lazy[2 * node + 1] += lazy[node];
                lazy[2 * node + 2] += lazy[node];
            }
            
            lazy[node] = 0;
        }
    }
    
    // Range update: add value to all elements in [left, right]
    public void updateRange(int left, int right, int value) {
        updateRange(0, 0, n - 1, left, right, value);
    }
    
    private void updateRange(int node, int start, int end, int left, int right, int value) {
        push(node, start, end);
        
        if (right < start || left > end) {
            return;
        }
        
        if (left <= start && end <= right) {
            lazy[node] += value;
            push(node, start, end);
            return;
        }
        
        int mid = start + (end - start) / 2;
        updateRange(2 * node + 1, start, mid, left, right, value);
        updateRange(2 * node + 2, mid + 1, end, left, right, value);
        
        push(2 * node + 1, start, mid);
        push(2 * node + 2, mid + 1, end);
        tree[node] = tree[2 * node + 1] + tree[2 * node + 2];
    }
    
    // Range sum query
    public long query(int left, int right) {
        return query(0, 0, n - 1, left, right);
    }
    
    private long query(int node, int start, int end, int left, int right) {
        if (right < start || left > end) {
            return 0;
        }
        
        push(node, start, end);
        
        if (left <= start && end <= right) {
            return tree[node];
        }
        
        int mid = start + (end - start) / 2;
        return query(2 * node + 1, start, mid, left, right) +
               query(2 * node + 2, mid + 1, end, left, right);
    }
    
    public static void main(String[] args) {
        int[] arr = {1, 3, 5, 7, 9, 11};
        LazyPropagation st = new LazyPropagation(arr);
        
        System.out.println("Sum [1, 3]: " + st.query(1, 3)); // 15
        
        st.updateRange(1, 3, 10);
        System.out.println("Sum [1, 3] after range update: " + st.query(1, 3)); // 45
        
        System.out.println("Sum [0, 5]: " + st.query(0, 5)); // 66
    }
}

