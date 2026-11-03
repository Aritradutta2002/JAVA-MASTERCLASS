package dsa.datastructures.advanced.segmenttree.basics;

/**
 * Segment Tree - Basic Implementation
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Use Cases:
 * - Range sum/min/max queries
 * - Point updates
 * - Range updates with lazy propagation
 * 
 * Time: Build O(n), Query O(log n), Update O(log n)
 * Space: O(4n) = O(n)
 */
public class SegmentTreeBasic {
    
    private int[] tree;
    private int n;
    
    public SegmentTreeBasic(int[] arr) {
        n = arr.length;
        tree = new int[4 * n];
        build(arr, 0, 0, n - 1);
    }
    
    // Build segment tree
    private void build(int[] arr, int node, int start, int end) {
        if (start == end) {
            tree[node] = arr[start];
            return;
        }
        
        int mid = start + (end - start) / 2;
        int leftChild = 2 * node + 1;
        int rightChild = 2 * node + 2;
        
        build(arr, leftChild, start, mid);
        build(arr, rightChild, mid + 1, end);
        
        tree[node] = tree[leftChild] + tree[rightChild]; // Sum
    }
    
    // Range sum query
    public int query(int left, int right) {
        return query(0, 0, n - 1, left, right);
    }
    
    private int query(int node, int start, int end, int left, int right) {
        // No overlap
        if (right < start || left > end) {
            return 0;
        }
        
        // Complete overlap
        if (left <= start && end <= right) {
            return tree[node];
        }
        
        // Partial overlap
        int mid = start + (end - start) / 2;
        int leftSum = query(2 * node + 1, start, mid, left, right);
        int rightSum = query(2 * node + 2, mid + 1, end, left, right);
        
        return leftSum + rightSum;
    }
    
    // Point update
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
        
        tree[node] = tree[2 * node + 1] + tree[2 * node + 2];
    }
    
    public static void main(String[] args) {
        int[] arr = {1, 3, 5, 7, 9, 11};
        SegmentTreeBasic st = new SegmentTreeBasic(arr);
        
        System.out.println("Sum [1, 3]: " + st.query(1, 3)); // 15
        
        st.update(1, 10);
        System.out.println("Sum [1, 3] after update: " + st.query(1, 3)); // 22
    }
}

