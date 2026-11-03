package dsa.datastructures.advanced.segmenttree.problems;

/**
 * Range Sum Query - Mutable (LeetCode 307) - MEDIUM
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Problem: Support range sum queries and point updates
 * Time: O(log n) per operation
 * Space: O(n)
 */
public class RangeSumQueryMutable {
    
    // Approach 1: Segment Tree
    static class NumArray {
        private int[] tree;
        private int n;
        
        public NumArray(int[] nums) {
            n = nums.length;
            tree = new int[4 * n];
            if (n > 0) {
                build(nums, 0, 0, n - 1);
            }
        }
        
        private void build(int[] nums, int node, int start, int end) {
            if (start == end) {
                tree[node] = nums[start];
                return;
            }
            
            int mid = start + (end - start) / 2;
            build(nums, 2 * node + 1, start, mid);
            build(nums, 2 * node + 2, mid + 1, end);
            tree[node] = tree[2 * node + 1] + tree[2 * node + 2];
        }
        
        public void update(int index, int val) {
            update(0, 0, n - 1, index, val);
        }
        
        private void update(int node, int start, int end, int index, int val) {
            if (start == end) {
                tree[node] = val;
                return;
            }
            
            int mid = start + (end - start) / 2;
            if (index <= mid) {
                update(2 * node + 1, start, mid, index, val);
            } else {
                update(2 * node + 2, mid + 1, end, index, val);
            }
            tree[node] = tree[2 * node + 1] + tree[2 * node + 2];
        }
        
        public int sumRange(int left, int right) {
            return query(0, 0, n - 1, left, right);
        }
        
        private int query(int node, int start, int end, int left, int right) {
            if (right < start || left > end) {
                return 0;
            }
            
            if (left <= start && end <= right) {
                return tree[node];
            }
            
            int mid = start + (end - start) / 2;
            return query(2 * node + 1, start, mid, left, right) +
                   query(2 * node + 2, mid + 1, end, left, right);
        }
    }
    
    // Approach 2: Binary Indexed Tree (Fenwick Tree) - More space efficient
    static class NumArrayBIT {
        private int[] nums;
        private int[] bit;
        private int n;
        
        public NumArrayBIT(int[] nums) {
            this.nums = nums.clone();
            n = nums.length;
            bit = new int[n + 1];
            
            for (int i = 0; i < n; i++) {
                updateBIT(i, nums[i]);
            }
        }
        
        public void update(int index, int val) {
            int diff = val - nums[index];
            nums[index] = val;
            updateBIT(index, diff);
        }
        
        private void updateBIT(int index, int delta) {
            index++; // BIT is 1-indexed
            while (index <= n) {
                bit[index] += delta;
                index += index & (-index);
            }
        }
        
        public int sumRange(int left, int right) {
            return prefixSum(right) - prefixSum(left - 1);
        }
        
        private int prefixSum(int index) {
            if (index < 0) return 0;
            index++; // BIT is 1-indexed
            int sum = 0;
            while (index > 0) {
                sum += bit[index];
                index -= index & (-index);
            }
            return sum;
        }
    }
    
    public static void main(String[] args) {
        int[] nums = {1, 3, 5};
        
        // Test Segment Tree
        NumArray obj1 = new NumArray(nums);
        System.out.println(obj1.sumRange(0, 2)); // 9
        obj1.update(1, 2);
        System.out.println(obj1.sumRange(0, 2)); // 8
        
        // Test BIT
        NumArrayBIT obj2 = new NumArrayBIT(nums);
        System.out.println(obj2.sumRange(0, 2)); // 9
        obj2.update(1, 2);
        System.out.println(obj2.sumRange(0, 2)); // 8
    }
}

