package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 421: Maximum XOR of Two Numbers in an Array
 * Difficulty: Medium
 * 
 * Given an integer array nums, return the maximum result of nums[i] XOR nums[j], 
 * where 0 <= i <= j < n.
 * 
 * Example 1:
 * Input: nums = [3,10,5,25,2,8]
 * Output: 28
 * Explanation: The maximum result is 5 XOR 25 = 28.
 * 
 * Example 2:
 * Input: nums = [14,70,53,83,49,91,36,80,92,51,66,70]
 * Output: 127
 * 
 * Constraints:
 * - 1 <= nums.length <= 2 * 10^5
 * - 0 <= nums[i] <= 2^31 - 1
 */
public class MaximumXORSubarray_Leetcode421 {
    
    /**
     * Approach 1: Brute Force
     * Try all pairs and find maximum XOR
     * Time: O(n^2), Space: O(1)
     */
    public int findMaximumXOR1(int[] nums) {
        int maxXor = 0;
        
        for (int i = 0; i < nums.length; i++) {
            for (int j = i; j < nums.length; j++) {
                maxXor = Math.max(maxXor, nums[i] ^ nums[j]);
            }
        }
        
        return maxXor;
    }
    
    /**
     * Approach 2: Bit Manipulation with Prefix Trie
     * Build answer bit by bit from most significant bit
     * Time: O(32n) = O(n), Space: O(32n) = O(n)
     */
    public int findMaximumXOR2(int[] nums) {
        int maxXor = 0;
        int mask = 0;
        
        // Build result bit by bit from MSB to LSB
        for (int i = 31; i >= 0; i--) {
            // Update mask to include current bit
            mask |= (1 << i);
            
            // Get prefixes of all numbers with current mask
            java.util.Set<Integer> prefixes = new java.util.HashSet<>();
            for (int num : nums) {
                prefixes.add(num & mask);
            }
            
            // Try to update maxXor with current bit set
            int candidate = maxXor | (1 << i);
            
            // Check if we can achieve this candidate
            for (int prefix : prefixes) {
                if (prefixes.contains(candidate ^ prefix)) {
                    maxXor = candidate;
                    break;
                }
            }
        }
        
        return maxXor;
    }
    
    /**
     * Approach 3: Trie Data Structure (Most Efficient)
     * Build a binary trie and find maximum XOR for each number
     * Time: O(32n) = O(n), Space: O(32n) = O(n)
     */
    class TrieNode {
        TrieNode[] children = new TrieNode[2];
    }
    
    private TrieNode root;
    
    public int findMaximumXOR3(int[] nums) {
        root = new TrieNode();
        
        // Insert all numbers into trie
        for (int num : nums) {
            insertToTrie(num);
        }
        
        int maxXor = 0;
        
        // Find maximum XOR for each number
        for (int num : nums) {
            maxXor = Math.max(maxXor, findMaxXorForNumber(num));
        }
        
        return maxXor;
    }
    
    private void insertToTrie(int num) {
        TrieNode current = root;
        
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            if (current.children[bit] == null) {
                current.children[bit] = new TrieNode();
            }
            current = current.children[bit];
        }
    }
    
    private int findMaxXorForNumber(int num) {
        TrieNode current = root;
        int maxXor = 0;
        
        for (int i = 31; i >= 0; i--) {
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
     * Follow-up: Maximum XOR with queries (LeetCode 1707)
     * Given queries with maximum values, find maximum XOR for each query
     */
    public int[] maximizeXorWithQueries(int[] nums, int[][] queries) {
        // Sort queries by maximum value and process in order
        int[][] indexedQueries = new int[queries.length][3];
        for (int i = 0; i < queries.length; i++) {
            indexedQueries[i] = new int[]{queries[i][0], queries[i][1], i};
        }
        
        java.util.Arrays.sort(indexedQueries, (a, b) -> a[1] - b[1]);
        java.util.Arrays.sort(nums);
        
        int[] result = new int[queries.length];
        root = new TrieNode();
        int numIndex = 0;
        
        for (int[] query : indexedQueries) {
            int x = query[0];
            int m = query[1];
            int originalIndex = query[2];
            
            // Insert all numbers <= m into trie
            while (numIndex < nums.length && nums[numIndex] <= m) {
                insertToTrie(nums[numIndex]);
                numIndex++;
            }
            
            // Find maximum XOR for current query
            if (numIndex == 0) {
                result[originalIndex] = -1;  // No valid numbers
            } else {
                result[originalIndex] = findMaxXorForNumber(x);
            }
        }
        
        return result;
    }
    
    /**
     * Utility method to visualize XOR operations
     */
    public void explainXOR(int a, int b) {
        System.out.println("a = " + a + " (binary: " + Integer.toBinaryString(a) + ")");
        System.out.println("b = " + b + " (binary: " + Integer.toBinaryString(b) + ")");
        System.out.println("a ^ b = " + (a ^ b) + " (binary: " + Integer.toBinaryString(a ^ b) + ")");
        System.out.println();
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        MaximumXORSubarray_Leetcode421 solution = new MaximumXORSubarray_Leetcode421();
        
        // Test cases
        int[][] testCases = {
            {3, 10, 5, 25, 2, 8},
            {14, 70, 53, 83, 49, 91, 36, 80, 92, 51, 66, 70},
            {1, 2, 3, 4},
            {8, 10, 2}
        };
        
        System.out.println("Testing Maximum XOR of Two Numbers:");
        for (int[] nums : testCases) {
            System.out.print("Input: ");
            for (int num : nums) System.out.print(num + " ");
            System.out.println();
            
            long startTime = System.nanoTime();
            int result1 = solution.findMaximumXOR1(nums);
            long time1 = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            int result2 = solution.findMaximumXOR2(nums);
            long time2 = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            int result3 = solution.findMaximumXOR3(nums);
            long time3 = System.nanoTime() - startTime;
            
            System.out.println("Brute Force: " + result1 + " (Time: " + time1 + " ns)");
            System.out.println("Bit Manipulation: " + result2 + " (Time: " + time2 + " ns)");
            System.out.println("Trie: " + result3 + " (Time: " + time3 + " ns)");
            System.out.println("All match: " + (result1 == result2 && result2 == result3));
            System.out.println();
        }
        
        // Demonstrate XOR properties
        System.out.println("XOR Properties Demonstration:");
        solution.explainXOR(5, 25);  // From example 1
        solution.explainXOR(3, 10);
        
        // Test with queries
        System.out.println("Testing Maximum XOR with Queries:");
        int[] numsQuery = {0, 1, 2, 3, 4};
        int[][] queries = {{3, 1}, {1, 3}, {5, 6}};
        
        int[] queryResults = solution.maximizeXorWithQueries(numsQuery, queries);
        for (int i = 0; i < queries.length; i++) {
            System.out.printf("Query [%d, %d]: %d%n", 
                queries[i][0], queries[i][1], queryResults[i]);
        }
    }
}

