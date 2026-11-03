package dsa.techniques.bitmanipulation.hard;
/**
 * LeetCode 1707: Maximum XOR With an Element From Array
 * Difficulty: Hard
 * 
 * You are given an array nums consisting of non-negative integers. You are also given a queries array, 
 * where queries[i] = [xi, mi].
 * 
 * The answer to the ith query is the maximum bitwise XOR value of xi and any element of nums that does not exceed mi. 
 * In other words, the answer is max(nums[j] XOR xi) for all j such that nums[j] <= mi. 
 * If all elements in nums are larger than mi, then the answer is -1.
 * 
 * Return an integer array answer where answer.length == queries.length and answer[i] is the answer to the ith query.
 * 
 * Example 1:
 * Input: nums = [0,1,2,3,4], queries = [[3,1],[1,3],[5,6]]
 * Output: [3,3,7]
 * Explanation:
 * 1) The elements of nums that are <= 1 are [0,1]. The maximum XOR of 3 with any of these is 3 XOR 1 = 2.
 * 2) The elements of nums that are <= 3 are [0,1,2,3]. The maximum XOR of 1 with any of these is 1 XOR 3 = 2.
 * 3) The elements of nums that are <= 6 are [0,1,2,3,4]. The maximum XOR of 5 with any of these is 5 XOR 3 = 6.
 * 
 * Wait, let me recalculate:
 * 1) 3 XOR 0 = 3, 3 XOR 1 = 2. Maximum is 3.
 * 2) 1 XOR 0 = 1, 1 XOR 1 = 0, 1 XOR 2 = 3, 1 XOR 3 = 2. Maximum is 3.
 * 3) 5 XOR 0 = 5, 5 XOR 1 = 4, 5 XOR 2 = 7, 5 XOR 3 = 6, 5 XOR 4 = 1. Maximum is 7.
 * 
 * Constraints:
 * - 1 <= nums.length, queries.length <= 10^5
 * - queries[i].length == 2
 * - 0 <= nums[j], xi, mi <= 10^9
 */
public class MaximumXORQueries_Leetcode1707 {
    
    /**
     * Trie Node for storing binary representation
     */
    class TrieNode {
        TrieNode[] children = new TrieNode[2];
        int minValue = Integer.MAX_VALUE; // Minimum value in this subtree
    }
    
    private TrieNode root;
    
    /**
     * Approach 1: Offline Processing with Trie
     * Sort queries by mi value and process in order
     * Time: O((n + q) * 30), Space: O(n * 30)
     */
    public int[] maximizeXor(int[] nums, int[][] queries) {
        // Sort nums for easier processing
        java.util.Arrays.sort(nums);
        
        // Create indexed queries and sort by mi
        int q = queries.length;
        int[][] indexedQueries = new int[q][3];
        for (int i = 0; i < q; i++) {
            indexedQueries[i] = new int[]{queries[i][0], queries[i][1], i};
        }
        java.util.Arrays.sort(indexedQueries, (a, b) -> a[1] - b[1]);
        
        int[] result = new int[q];
        root = new TrieNode();
        int numIndex = 0;
        
        for (int[] query : indexedQueries) {
            int xi = query[0];
            int mi = query[1];
            int originalIndex = query[2];
            
            // Insert all numbers <= mi into trie
            while (numIndex < nums.length && nums[numIndex] <= mi) {
                insertToTrie(nums[numIndex]);
                numIndex++;
            }
            
            // Find maximum XOR for current query
            if (numIndex == 0) {
                result[originalIndex] = -1;
            } else {
                result[originalIndex] = findMaxXOR(xi);
            }
        }
        
        return result;
    }
    
    private void insertToTrie(int num) {
        TrieNode current = root;
        current.minValue = Math.min(current.minValue, num);
        
        for (int i = 30; i >= 0; i--) {
            int bit = (num >> i) & 1;
            
            if (current.children[bit] == null) {
                current.children[bit] = new TrieNode();
            }
            
            current = current.children[bit];
            current.minValue = Math.min(current.minValue, num);
        }
    }
    
    private int findMaxXOR(int num) {
        TrieNode current = root;
        if (current.minValue == Integer.MAX_VALUE) return -1;
        
        int maxXor = 0;
        
        for (int i = 30; i >= 0; i--) {
            int bit = (num >> i) & 1;
            int oppositeBit = 1 - bit;
            
            // Try to go to opposite bit for maximum XOR
            if (current.children[oppositeBit] != null) {
                maxXor |= (1 << i);
                current = current.children[oppositeBit];
            } else {
                current = current.children[bit];
            }
        }
        
        return maxXor;
    }
    
    /**
     * Approach 2: Brute Force (for small inputs or verification)
     * Time: O(q * n), Space: O(1)
     */
    public int[] maximizeXorBruteForce(int[] nums, int[][] queries) {
        int[] result = new int[queries.length];
        
        for (int i = 0; i < queries.length; i++) {
            int xi = queries[i][0];
            int mi = queries[i][1];
            
            int maxXor = -1;
            for (int num : nums) {
                if (num <= mi) {
                    maxXor = Math.max(maxXor, xi ^ num);
                }
            }
            
            result[i] = maxXor;
        }
        
        return result;
    }
    
    /**
     * Approach 3: Persistent Trie (for multiple query processing)
     * Build trie incrementally and store versions
     */
    class PersistentTrieNode {
        PersistentTrieNode[] children = new PersistentTrieNode[2];
        java.util.List<Integer> values = new java.util.ArrayList<>();
    }
    
    public int[] maximizeXorPersistent(int[] nums, int[][] queries) {
        // Sort nums with their original indices
        Integer[] indices = new Integer[nums.length];
        for (int i = 0; i < nums.length; i++) {
            indices[i] = i;
        }
        java.util.Arrays.sort(indices, (a, b) -> nums[a] - nums[b]);
        
        // Build persistent trie
        PersistentTrieNode persistentRoot = new PersistentTrieNode();
        for (int i = 0; i < nums.length; i++) {
            insertToPersistentTrie(persistentRoot, nums[indices[i]]);
        }
        
        int[] result = new int[queries.length];
        for (int i = 0; i < queries.length; i++) {
            result[i] = queryPersistentTrie(persistentRoot, queries[i][0], queries[i][1]);
        }
        
        return result;
    }
    
    private void insertToPersistentTrie(PersistentTrieNode root, int num) {
        PersistentTrieNode current = root;
        current.values.add(num);
        
        for (int i = 30; i >= 0; i--) {
            int bit = (num >> i) & 1;
            
            if (current.children[bit] == null) {
                current.children[bit] = new PersistentTrieNode();
            }
            
            current = current.children[bit];
            current.values.add(num);
        }
    }
    
    private int queryPersistentTrie(PersistentTrieNode root, int xi, int mi) {
        // Binary search on valid values
        int left = 0, right = root.values.size() - 1;
        int validCount = -1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            if (root.values.get(mid) <= mi) {
                validCount = mid;
                left = mid + 1;
            } else {
                right = mid - 1;
            }
        }
        
        if (validCount == -1) return -1;
        
        // Find maximum XOR among valid values
        int maxXor = -1;
        for (int i = 0; i <= validCount; i++) {
            maxXor = Math.max(maxXor, xi ^ root.values.get(i));
        }
        
        return maxXor;
    }
    
    /**
     * Utility method to explain the algorithm
     */
    public void explainAlgorithm(int[] nums, int[][] queries) {
        System.out.println("Explaining Maximum XOR Queries Algorithm:");
        System.out.println("Numbers: " + java.util.Arrays.toString(nums));
        
        for (int i = 0; i < queries.length; i++) {
            int xi = queries[i][0];
            int mi = queries[i][1];
            
            System.out.println("\nQuery " + (i + 1) + ": xi=" + xi + ", mi=" + mi);
            System.out.println("Valid numbers (≤ " + mi + "):");
            
            int maxXor = -1;
            for (int num : nums) {
                if (num <= mi) {
                    int xor = xi ^ num;
                    System.out.println("  " + xi + " XOR " + num + " = " + xor + 
                        " (binary: " + Integer.toBinaryString(xi) + " XOR " + 
                        Integer.toBinaryString(num) + " = " + Integer.toBinaryString(xor) + ")");
                    maxXor = Math.max(maxXor, xor);
                }
            }
            
            System.out.println("Maximum XOR: " + maxXor);
        }
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        MaximumXORQueries_Leetcode1707 solution = new MaximumXORQueries_Leetcode1707();
        
        // Test case 1
        System.out.println("=== Test Case 1 ===");
        int[] nums1 = {0, 1, 2, 3, 4};
        int[][] queries1 = {{3, 1}, {1, 3}, {5, 6}};
        
        int[] result1 = solution.maximizeXor(nums1, queries1);
        int[] bruteResult1 = solution.maximizeXorBruteForce(nums1, queries1);
        
        System.out.println("Trie result: " + java.util.Arrays.toString(result1));
        System.out.println("Brute force: " + java.util.Arrays.toString(bruteResult1));
        System.out.println("Results match: " + java.util.Arrays.equals(result1, bruteResult1));
        
        // Explain the algorithm
        System.out.println("\n=== Algorithm Explanation ===");
        solution.explainAlgorithm(nums1, queries1);
        
        // Test case 2
        System.out.println("\n=== Test Case 2 ===");
        int[] nums2 = {5, 2, 4, 6, 6, 3};
        int[][] queries2 = {{12, 4}, {8, 1}, {6, 3}};
        
        int[] result2 = solution.maximizeXor(nums2, queries2);
        int[] bruteResult2 = solution.maximizeXorBruteForce(nums2, queries2);
        
        System.out.println("Trie result: " + java.util.Arrays.toString(result2));
        System.out.println("Brute force: " + java.util.Arrays.toString(bruteResult2));
        System.out.println("Results match: " + java.util.Arrays.equals(result2, bruteResult2));
        
        // Performance comparison
        System.out.println("\n=== Performance Comparison ===");
        long startTime, endTime;
        
        startTime = System.nanoTime();
        solution.maximizeXor(nums2, queries2);
        endTime = System.nanoTime();
        System.out.println("Trie approach: " + (endTime - startTime) + " ns");
        
        startTime = System.nanoTime();
        solution.maximizeXorBruteForce(nums2, queries2);
        endTime = System.nanoTime();
        System.out.println("Brute force: " + (endTime - startTime) + " ns");
    }
}

