package dsa.datastructures.advanced.segmenttree.problems;

import java.util.*;

/**
 * Count of Smaller Numbers After Self (LeetCode 315) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: For each element, count how many smaller elements are to its right
 * Time: O(n log n), Space: O(n)
 */
public class CountSmallerAfterSelf {
    
    // Approach 1: Segment Tree with coordinate compression
    public List<Integer> countSmaller(int[] nums) {
        int n = nums.length;
        List<Integer> result = new ArrayList<>();
        
        // Coordinate compression
        int[] sorted = nums.clone();
        Arrays.sort(sorted);
        Map<Integer, Integer> map = new HashMap<>();
        int rank = 0;
        for (int num : sorted) {
            if (!map.containsKey(num)) {
                map.put(num, rank++);
            }
        }
        
        SegmentTree st = new SegmentTree(rank);
        
        // Process from right to left
        for (int i = n - 1; i >= 0; i--) {
            int compressed = map.get(nums[i]);
            result.add(st.query(0, compressed - 1));
            st.update(compressed, 1);
        }
        
        Collections.reverse(result);
        return result;
    }
    
    class SegmentTree {
        private int[] tree;
        private int n;
        
        public SegmentTree(int size) {
            n = size;
            tree = new int[4 * n];
        }
        
        public void update(int index, int value) {
            update(0, 0, n - 1, index, value);
        }
        
        private void update(int node, int start, int end, int index, int value) {
            if (start == end) {
                tree[node] += value;
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
        
        public int query(int left, int right) {
            if (left > right || left < 0) return 0;
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
    
    // Approach 2: Binary Indexed Tree (Fenwick Tree)
    public List<Integer> countSmallerBIT(int[] nums) {
        int n = nums.length;
        List<Integer> result = new ArrayList<>();
        
        // Coordinate compression
        int[] sorted = nums.clone();
        Arrays.sort(sorted);
        Map<Integer, Integer> map = new HashMap<>();
        int rank = 1; // BIT is 1-indexed
        for (int num : sorted) {
            if (!map.containsKey(num)) {
                map.put(num, rank++);
            }
        }
        
        BIT bit = new BIT(rank);
        
        for (int i = n - 1; i >= 0; i--) {
            int compressed = map.get(nums[i]);
            result.add(bit.query(compressed - 1));
            bit.update(compressed, 1);
        }
        
        Collections.reverse(result);
        return result;
    }
    
    class BIT {
        private int[] tree;
        private int n;
        
        public BIT(int size) {
            n = size;
            tree = new int[n + 1];
        }
        
        public void update(int index, int delta) {
            while (index <= n) {
                tree[index] += delta;
                index += index & (-index);
            }
        }
        
        public int query(int index) {
            int sum = 0;
            while (index > 0) {
                sum += tree[index];
                index -= index & (-index);
            }
            return sum;
        }
    }
    
    public static void main(String[] args) {
        CountSmallerAfterSelf solution = new CountSmallerAfterSelf();
        
        // Test Case 1
        int[] nums1 = {5, 2, 6, 1};
        System.out.println(solution.countSmaller(nums1)); // [2, 1, 1, 0]
        
        // Test Case 2
        int[] nums2 = {-1};
        System.out.println(solution.countSmaller(nums2)); // [0]
        
        // Test Case 3
        int[] nums3 = {-1, -1};
        System.out.println(solution.countSmaller(nums3)); // [0, 0]
    }
}

