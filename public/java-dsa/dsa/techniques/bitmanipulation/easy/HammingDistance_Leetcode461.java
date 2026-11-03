package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 461: Hamming Distance
 * Difficulty: Easy
 * 
 * The Hamming distance between two integers is the number of positions at which 
 * the corresponding bits are different.
 * Given two integers x and y, return the Hamming distance between them.
 * 
 * Example 1:
 * Input: x = 1, y = 4
 * Output: 2
 * Explanation:
 * 1   (0 0 0 1)
 * 4   (0 1 0 0)
 *        ↑   ↑
 * The above arrows point to positions where the corresponding bits are different.
 * 
 * Example 2:
 * Input: x = 3, y = 1
 * Output: 1
 * 
 * Constraints:
 * 0 <= x, y <= 2^31 - 1
 */
public class HammingDistance_Leetcode461 {
    
    /**
     * Approach 1: XOR + Built-in popcount (Optimal)
     * XOR gives 1 where bits differ, then count the 1s
     * Time: O(1), Space: O(1)
     */
    public int hammingDistance(int x, int y) {
        return Integer.bitCount(x ^ y);
    }
    
    /**
     * Approach 2: XOR + Manual bit counting
     * Time: O(log(max(x,y))), Space: O(1)
     */
    public int hammingDistanceManual(int x, int y) {
        int xor = x ^ y;
        int count = 0;
        
        while (xor != 0) {
            count += xor & 1;
            xor >>= 1;
        }
        
        return count;
    }
    
    /**
     * Approach 3: Brian Kernighan's Algorithm
     * Clears rightmost set bit in each iteration
     * Time: O(number of set bits), Space: O(1)
     */
    public int hammingDistanceBK(int x, int y) {
        int xor = x ^ y;
        int count = 0;
        
        while (xor != 0) {
            xor &= (xor - 1); // Clear rightmost set bit
            count++;
        }
        
        return count;
    }
    
    /**
     * Approach 4: Using lookup table (for optimization)
     * Precompute bit counts for all 8-bit numbers
     */
    private static final int[] BIT_COUNT = new int[256];
    static {
        for (int i = 0; i < 256; i++) {
            BIT_COUNT[i] = Integer.bitCount(i);
        }
    }
    
    public int hammingDistanceLookup(int x, int y) {
        int xor = x ^ y;
        return BIT_COUNT[xor & 0xFF] + 
               BIT_COUNT[(xor >>> 8) & 0xFF] +
               BIT_COUNT[(xor >>> 16) & 0xFF] +
               BIT_COUNT[(xor >>> 24) & 0xFF];
    }
    
    /**
     * Extension: Calculate Hamming distance for arrays
     * Find total Hamming distance between all pairs
     */
    public int totalHammingDistance(int[] nums) {
        int total = 0;
        int n = nums.length;
        
        // Check each bit position
        for (int i = 0; i < 32; i++) {
            int countOnes = 0;
            
            // Count numbers with ith bit set
            for (int num : nums) {
                countOnes += (num >>> i) & 1;
            }
            
            // Pairs with different bits at position i
            int countZeros = n - countOnes;
            total += countOnes * countZeros;
        }
        
        return total;
    }
    
    /**
     * Test method with comprehensive examples
     */
    public static void main(String[] args) {
        HammingDistance_Leetcode461 solution = new HammingDistance_Leetcode461();
        
        // Test cases
        int[][] testCases = {
            {1, 4},    // Expected: 2
            {3, 1},    // Expected: 1
            {0, 0},    // Expected: 0
            {15, 0},   // Expected: 4
            {5, 3}     // Expected: 2
        };
        
        System.out.println("Testing Hamming Distance:");
        for (int[] testCase : testCases) {
            int x = testCase[0], y = testCase[1];
            int result = solution.hammingDistance(x, y);
            
            System.out.printf("x=%d (%s), y=%d (%s) → Distance=%d%n",
                x, Integer.toBinaryString(x),
                y, Integer.toBinaryString(y),
                result);
        }
        
        // Test total Hamming distance
        System.out.println("\nTesting Total Hamming Distance:");
        int[] nums = {4, 14, 2};
        int totalDistance = solution.totalHammingDistance(nums);
        System.out.println("Array: [4, 14, 2] → Total Hamming Distance: " + totalDistance);
        
        // Binary representations
        System.out.println("Binary representations:");
        for (int num : nums) {
            System.out.printf("%d: %s%n", num, String.format("%4s", Integer.toBinaryString(num)).replace(' ', '0'));
        }
    }
}

