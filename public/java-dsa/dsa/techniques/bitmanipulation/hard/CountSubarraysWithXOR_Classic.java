package dsa.techniques.bitmanipulation.hard;
/**
 * Count Subarrays with Given XOR
 * Difficulty: Hard
 * 
 * Given an array of integers and a target XOR value, count the number of subarrays
 * whose XOR equals the target.
 * 
 * Example 1:
 * Input: arr = [4, 2, 2, 6, 4], target = 6
 * Output: 4
 * Explanation: Subarrays with XOR 6: [4,2], [2,2,6], [6], [4,2,2,6,4]
 * 
 * Example 2:
 * Input: arr = [5, 6, 7, 8, 9], target = 5
 * Output: 2
 * Explanation: Subarrays with XOR 5: [5], [5,6,7,8,9]
 */
public class CountSubarraysWithXOR_Classic {
    
    /**
     * Approach 1: Brute Force
     * Check all possible subarrays
     * Time: O(n^2), Space: O(1)
     */
    public int countSubarraysWithXOR1(int[] arr, int target) {
        int n = arr.length;
        int count = 0;
        
        for (int i = 0; i < n; i++) {
            int currentXor = 0;
            for (int j = i; j < n; j++) {
                currentXor ^= arr[j];
                if (currentXor == target) {
                    count++;
                }
            }
        }
        
        return count;
    }
    
    /**
     * Approach 2: Prefix XOR with HashMap (Optimal)
     * Use prefix XOR to find subarrays with target XOR
     * If prefixXor[j] ^ prefixXor[i-1] = target, then prefixXor[i-1] = prefixXor[j] ^ target
     * Time: O(n), Space: O(n)
     */
    public int countSubarraysWithXOR2(int[] arr, int target) {
        java.util.Map<Integer, Integer> prefixXorCount = new java.util.HashMap<>();
        
        int count = 0;
        int prefixXor = 0;
        
        // Initialize with 0 (empty prefix)
        prefixXorCount.put(0, 1);
        
        for (int num : arr) {
            prefixXor ^= num;
            
            // Find required prefix XOR
            int requiredXor = prefixXor ^ target;
            
            // Add count of required prefix XORs
            count += prefixXorCount.getOrDefault(requiredXor, 0);
            
            // Update current prefix XOR count
            prefixXorCount.put(prefixXor, prefixXorCount.getOrDefault(prefixXor, 0) + 1);
        }
        
        return count;
    }
    
    /**
     * Follow-up 1: Count subarrays with XOR in range [L, R]
     */
    public int countSubarraysWithXORInRange(int[] arr, int L, int R) {
        int count = 0;
        
        for (int target = L; target <= R; target++) {
            count += countSubarraysWithXOR2(arr, target);
        }
        
        return count;
    }
    
    /**
     * Follow-up 2: Find the k-th smallest XOR of all subarrays
     */
    public int kthSmallestSubarrayXOR(int[] arr, int k) {
        java.util.List<Integer> allXORs = new java.util.ArrayList<>();
        int n = arr.length;
        
        // Generate all subarray XORs
        for (int i = 0; i < n; i++) {
            int currentXor = 0;
            for (int j = i; j < n; j++) {
                currentXor ^= arr[j];
                allXORs.add(currentXor);
            }
        }
        
        // Sort and return k-th element
        java.util.Collections.sort(allXORs);
        return allXORs.get(k - 1);
    }
    
    /**
     * Follow-up 3: Maximum XOR of any subarray
     */
    public int maxSubarrayXOR(int[] arr) {
        int n = arr.length;
        int maxXor = 0;
        
        for (int i = 0; i < n; i++) {
            int currentXor = 0;
            for (int j = i; j < n; j++) {
                currentXor ^= arr[j];
                maxXor = Math.max(maxXor, currentXor);
            }
        }
        
        return maxXor;
    }
    
    /**
     * Optimized Maximum XOR using Trie (for large arrays)
     */
    class TrieNode {
        TrieNode[] children = new TrieNode[2];
    }
    
    public int maxSubarrayXOROptimized(int[] arr) {
        TrieNode root = new TrieNode();
        int maxXor = 0;
        int prefixXor = 0;
        
        // Insert 0 for empty prefix
        insertToTrie(root, 0);
        
        for (int num : arr) {
            prefixXor ^= num;
            
            // Find maximum XOR with any previous prefix
            maxXor = Math.max(maxXor, findMaxXOR(root, prefixXor));
            
            // Insert current prefix XOR
            insertToTrie(root, prefixXor);
        }
        
        return maxXor;
    }
    
    private void insertToTrie(TrieNode root, int num) {
        TrieNode current = root;
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            if (current.children[bit] == null) {
                current.children[bit] = new TrieNode();
            }
            current = current.children[bit];
        }
    }
    
    private int findMaxXOR(TrieNode root, int num) {
        TrieNode current = root;
        int maxXor = 0;
        
        for (int i = 31; i >= 0; i--) {
            int bit = (num >> i) & 1;
            int oppositeBit = 1 - bit;
            
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
     * Utility method to print all subarrays with target XOR
     */
    public void printSubarraysWithXOR(int[] arr, int target) {
        int n = arr.length;
        System.out.println("Subarrays with XOR " + target + ":");
        
        for (int i = 0; i < n; i++) {
            int currentXor = 0;
            for (int j = i; j < n; j++) {
                currentXor ^= arr[j];
                if (currentXor == target) {
                    System.out.print("[");
                    for (int k = i; k <= j; k++) {
                        System.out.print(arr[k]);
                        if (k < j) System.out.print(", ");
                    }
                    System.out.println("]");
                }
            }
        }
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        CountSubarraysWithXOR_Classic solution = new CountSubarraysWithXOR_Classic();
        
        // Test cases
        int[][] testArrays = {
            {4, 2, 2, 6, 4},
            {5, 6, 7, 8, 9},
            {1, 3, 3, 3, 5},
            {8, 2, 4, 1, 3}
        };
        
        int[] targets = {6, 5, 3, 4};
        
        System.out.println("Testing Count Subarrays with XOR:");
        for (int i = 0; i < testArrays.length; i++) {
            int[] arr = testArrays[i];
            int target = targets[i];
            
            System.out.print("Array: ");
            for (int num : arr) System.out.print(num + " ");
            System.out.println("Target: " + target);
            
            long startTime = System.nanoTime();
            int result1 = solution.countSubarraysWithXOR1(arr, target);
            long time1 = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            int result2 = solution.countSubarraysWithXOR2(arr, target);
            long time2 = System.nanoTime() - startTime;
            
            System.out.println("Brute Force: " + result1 + " (Time: " + time1 + " ns)");
            System.out.println("Prefix XOR: " + result2 + " (Time: " + time2 + " ns)");
            System.out.println("Results match: " + (result1 == result2));
            
            // Print actual subarrays for small arrays
            if (arr.length <= 10) {
                solution.printSubarraysWithXOR(arr, target);
            }
            System.out.println();
        }
        
        // Test maximum subarray XOR
        System.out.println("Testing Maximum Subarray XOR:");
        for (int[] arr : testArrays) {
            System.out.print("Array: ");
            for (int num : arr) System.out.print(num + " ");
            
            int maxXor1 = solution.maxSubarrayXOR(arr);
            int maxXor2 = solution.maxSubarrayXOROptimized(arr);
            
            System.out.println("Max XOR: " + maxXor1 + " (Optimized: " + maxXor2 + ")");
            System.out.println("Results match: " + (maxXor1 == maxXor2));
            System.out.println();
        }
        
        // Test k-th smallest XOR
        System.out.println("Testing K-th Smallest Subarray XOR:");
        int[] smallArray = {1, 2, 3};
        for (int k = 1; k <= 6; k++) {  // 6 subarrays total
            System.out.println(k + "-th smallest XOR: " + 
                solution.kthSmallestSubarrayXOR(smallArray, k));
        }
    }
}

