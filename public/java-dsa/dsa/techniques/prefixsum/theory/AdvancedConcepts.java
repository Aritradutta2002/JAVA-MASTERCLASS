package dsa.techniques.prefixsum.theory;
import java.util.*;

/**
 * Advanced Prefix Sum Concepts
 * 
 * This file covers advanced techniques and optimizations
 * that are commonly asked in technical interviews.
 */
public class AdvancedConcepts {
    
    /**
     * 1. Difference Array Technique
     * Used for range update queries in O(1) time
     * 
     * Application: When you need to add a value to all elements in a range [l, r]
     * Instead of O(n) updates, use difference array for O(1) updates
     */
	 
    public static class DifferenceArray {
        private int[] diff;
        private int n;
        
        public DifferenceArray(int[] arr) {
            n = arr.length;
            diff = new int[n + 1];
            
            // Build difference array
            diff[0] = arr[0];
            for (int i = 1; i < n; i++) {
                diff[i] = arr[i] - arr[i-1];
            }
        }
        
        // Add value to range [l, r] in O(1)
        public void rangeUpdate(int l, int r, int value) {
            diff[l] += value;
            if (r + 1 < n) {
                diff[r + 1] -= value;
            }
        }
        
        // Get final array after all updates
        public int[] getFinalArray() {
            int[] result = new int[n];
            result[0] = diff[0];
            for (int i = 1; i < n; i++) {
                result[i] = result[i-1] + diff[i];
            }
            return result;
        }
    }
    
    /**
     * 2. Prefix Sum with HashMap
     * Pattern: Count subarrays with specific sum/property
     * 
     * Key Insight: If prefixSum[j] - prefixSum[i] = target,
     * then subarray from i+1 to j has sum = target
     */
	 
    public static int countSubarraysWithSum(int[] arr, int target) {
        Map<Integer, Integer> prefixSumCount = new HashMap<>();
        prefixSumCount.put(0, 1); // Empty prefix
        
        int prefixSum = 0;
        int count = 0;
        
        for (int num : arr) {
            prefixSum += num;
            
            // Check if (prefixSum - target) exists
            if (prefixSumCount.containsKey(prefixSum - target)) {
                count += prefixSumCount.get(prefixSum - target);
            }
            
            // Add current prefix sum to map
            prefixSumCount.put(prefixSum, prefixSumCount.getOrDefault(prefixSum, 0) + 1);
        }
        
        return count;
    }
    
    /**
     * 3. Prefix XOR
     * Similar to prefix sum but with XOR operation
     * Property: XOR of range [l, r] = prefixXOR[r] ^ prefixXOR[l-1]
     */
	 
    public static int[] buildPrefixXOR(int[] arr) {
        int n = arr.length;
        int[] prefixXOR = new int[n];
        
        prefixXOR[0] = arr[0];
        for (int i = 1; i < n; i++) {
            prefixXOR[i] = prefixXOR[i-1] ^ arr[i];
        }
        
        return prefixXOR;
    }
    
    /**
     * 4. Prefix Maximum/Minimum
     * Track running maximum or minimum up to each position
     */
	 
    public static int[] buildPrefixMax(int[] arr) {
        int n = arr.length;
        int[] prefixMax = new int[n];
        
        prefixMax[0] = arr[0];
        for (int i = 1; i < n; i++) {
            prefixMax[i] = Math.max(prefixMax[i-1], arr[i]);
        }
        
        return prefixMax;
    }
    
    /**
     * 5. Binary Indexed Tree (Fenwick Tree)
     * Advanced data structure for prefix sum with updates
     * Supports both range query and point update in O(log n)
     */
	 
    public static class BinaryIndexedTree {
        private int[] tree;
        private int n;
        
        public BinaryIndexedTree(int size) {
            n = size;
            tree = new int[n + 1];
        }
        
        // Update value at index i
        public void update(int i, int delta) {
            for (++i; i <= n; i += i & (-i)) {
                tree[i] += delta;
            }
        }
        
        // Get prefix sum from 0 to i
        public int query(int i) {
            int sum = 0;
            for (++i; i > 0; i -= i & (-i)) {
                sum += tree[i];
            }
            return sum;
        }
        
        // Get range sum from l to r
        public int rangeQuery(int l, int r) {
            return query(r) - (l > 0 ? query(l - 1) : 0);
        }
    }
    
    /**
     * 6. Sparse Table for Range Minimum Query
     * Preprocessing: O(n log n), Query: O(1)
     */
	 
    public static class SparseTable {
        private int[][] st;
        private int[] log;
        
        public SparseTable(int[] arr) {
            int n = arr.length;
            int k = Integer.numberOfTrailingZeros(Integer.highestOneBit(n)) + 1;
            
            st = new int[n][k];
            log = new int[n + 1];
            
            // Precompute logarithms
            for (int i = 2; i <= n; i++) {
                log[i] = log[i / 2] + 1;
            }
            
            // Initialize for length 1
            for (int i = 0; i < n; i++) {
                st[i][0] = arr[i];
            }
            
            // Build sparse table
            for (int j = 1; j < k; j++) {
                for (int i = 0; i + (1 << j) <= n; i++) {
                    st[i][j] = Math.min(st[i][j-1], st[i + (1 << (j-1))][j-1]);
                }
            }
        }
        
        public int query(int l, int r) {
            int j = log[r - l + 1];
            return Math.min(st[l][j], st[r - (1 << j) + 1][j]);
        }
    }
    
    /**
     * 7. Coordinate Compression
     * When dealing with large coordinate values, compress them
     */
	 
    public static Map<Integer, Integer> compressCoordinates(int[] coords) {
        Set<Integer> uniqueCoords = new TreeSet<>();
        for (int coord : coords) {
            uniqueCoords.add(coord);
        }
        
        Map<Integer, Integer> compressed = new HashMap<>();
        int index = 0;
        for (int coord : uniqueCoords) {
            compressed.put(coord, index++);
        }
        
        return compressed;
    }
    
    // Demo and testing
    public static void main(String[] args) {
        // Test Difference Array
        int[] arr = {1, 2, 3, 4, 5};
        DifferenceArray diffArr = new DifferenceArray(arr);
        diffArr.rangeUpdate(1, 3, 10); // Add 10 to range [1,3]
        System.out.println("After range update: " + Arrays.toString(diffArr.getFinalArray()));
        
        // Test Subarray Count
        int[] nums = {1, 1, 1};
        System.out.println("Subarrays with sum 2: " + countSubarraysWithSum(nums, 2));
        
        // Test Binary Indexed Tree
        BinaryIndexedTree bit = new BinaryIndexedTree(5);
        bit.update(0, 1);
        bit.update(1, 2);
        bit.update(2, 3);
        System.out.println("BIT range query [0,2]: " + bit.rangeQuery(0, 2));
    }
}
