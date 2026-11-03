package dsa.techniques.bitmanipulation.medium;

/**
 * LeetCode 201: Bitwise AND of Numbers Range
 * Difficulty: Medium
 * 
 * Given two integers left and right that represent the range [left, right], 
 * return the bitwise AND of all numbers in this range, inclusive.
 * 
 * Example 1:
 * Input: left = 5, right = 7
 * Output: 4
 * Explanation: 5 & 6 & 7 = 4
 * 
 * Example 2:
 * Input: left = 0, right = 0
 * Output: 0
 * 
 * Example 3:
 * Input: left = 1, right = 2147483647
 * Output: 0
 * 
 * Constraints: 0 <= left <= right <= 2^31 - 1
 */
public class BitwiseANDRange_Leetcode201 {
    
    /**
     * Approach 1: Brute Force (TLE for large ranges)
     * Time: O(right - left), Space: O(1)
     */
    public int rangeBitwiseAnd1(int left, int right) {
        int result = left;
        for (int i = left + 1; i <= right; i++) {
            result &= i;
            if (result == 0) break;  // Early termination
        }
        return result;
    }
    
    /**
     * Approach 2: Find Common Prefix (Optimal)
     * Key insight: When we AND consecutive numbers, bits will become 0
     * The result is the common prefix of left and right
     * Time: O(log(max(left, right))), Space: O(1)
     */
    public int rangeBitwiseAnd2(int left, int right) {
        int shift = 0;
        
        // Find common prefix by removing different suffix bits
        while (left != right) {
            left >>= 1;
            right >>= 1;
            shift++;
        }
        
        // Restore the common prefix
        return left << shift;
    }
    
    /**
     * Approach 3: Clear Rightmost Set Bit
     * Keep clearing the rightmost set bit in right until right <= left
     * Time: O(log(right)), Space: O(1)
     */
    public int rangeBitwiseAnd3(int left, int right) {
        while (left < right) {
            // Clear the rightmost set bit in right
            right = right & (right - 1);
        }
        return right;
    }
    
    /**
     * Approach 4: Bit by Bit Analysis
     * For each bit position, check if it remains 1 throughout the range
     * Time: O(32) = O(1), Space: O(1)
     */
    public int rangeBitwiseAnd4(int left, int right) {
        int result = 0;
        
        for (int i = 31; i >= 0; i--) {
            int bitLeft = (left >> i) & 1;
            int bitRight = (right >> i) & 1;
            
            if (bitLeft == 1 && bitRight == 1) {
                // Both have 1 at this position
                // Check if the range is small enough to keep this bit as 1
                long range = right - left;
                long powerOf2 = 1L << i;
                
                if (range < powerOf2) {
                    result |= (1 << i);
                } else {
                    break;  // All lower bits will be 0
                }
            } else if (bitLeft != bitRight) {
                // Different bits at this position, all lower bits will be 0
                break;
            }
            // If both are 0, continue to next bit
        }
        
        return result;
    }
    
    /**
     * Mathematical insight explanation:
     * When we AND a range of consecutive numbers, bits become 0 due to:
     * 1. Bit flipping: In any range of 2^k consecutive numbers, the k-th bit flips
     * 2. Carry propagation: When incrementing, carries can flip higher bits
     * 
     * The result preserves only the common prefix of left and right.
     */
    
    /**
     * Helper method to visualize the process
     */
    public void explainProcess(int left, int right) {
        System.out.println("Range: [" + left + ", " + right + "]");
        System.out.println("Left:  " + Integer.toBinaryString(left));
        System.out.println("Right: " + Integer.toBinaryString(right));
        
        // Show the AND of all numbers (for small ranges)
        if (right - left < 10) {
            int result = left;
            System.out.print("AND process: " + left);
            for (int i = left + 1; i <= right; i++) {
                result &= i;
                System.out.print(" & " + i + " = " + result);
                System.out.println(" (binary: " + Integer.toBinaryString(result) + ")");
            }
        }
        
        System.out.println("Final result: " + rangeBitwiseAnd2(left, right));
        System.out.println("Binary: " + Integer.toBinaryString(rangeBitwiseAnd2(left, right)));
        System.out.println();
    }
    
    /**
     * Follow-up: Bitwise OR of Numbers Range
     */
    public int rangeBitwiseOr(int left, int right) {
        int result = 0;
        
        for (int i = 0; i < 32; i++) {
            // If the range covers a complete cycle of this bit, it will be 1
            long cycleLength = 1L << (i + 1);
            long startCycle = left / cycleLength;
            long endCycle = right / cycleLength;
            
            if (startCycle != endCycle) {
                result |= (1 << i);
            } else {
                // Check if the bit is set in either left or right within the same cycle
                long cycleStart = startCycle * cycleLength;
                long bitStart = cycleStart + (1L << i);
                
                if (left < bitStart && right >= bitStart) {
                    result |= (1 << i);
                } else if ((left & (1 << i)) != 0 || (right & (1 << i)) != 0) {
                    result |= (1 << i);
                }
            }
        }
        
        return result;
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        BitwiseANDRange_Leetcode201 solution = new BitwiseANDRange_Leetcode201();
        
        // Test cases
        int[][] testCases = {
            {5, 7},      // Expected: 4
            {0, 0},      // Expected: 0
            {1, 2147483647}, // Expected: 0
            {1, 3},      // Expected: 0
            {26, 30},    // Expected: 24
            {1, 1},      // Expected: 1
            {2, 6}       // Expected: 0
        };
        
        System.out.println("Testing Bitwise AND of Range:");
        for (int[] testCase : testCases) {
            int left = testCase[0];
            int right = testCase[1];
            
            System.out.printf("Range [%d, %d]:%n", left, right);
            
            // Test all approaches
            int result2 = solution.rangeBitwiseAnd2(left, right);
            int result3 = solution.rangeBitwiseAnd3(left, right);
            
            System.out.printf("Approach 2 (Common Prefix): %d%n", result2);
            System.out.printf("Approach 3 (Clear Bits): %d%n", result3);
            System.out.printf("Results match: %b%n", result2 == result3);
            
            // Detailed explanation for small ranges
            if (right - left <= 5) {
                solution.explainProcess(left, right);
            }
            System.out.println();
        }
        
        // Test OR operation
        System.out.println("Testing Bitwise OR of Range:");
        System.out.println("Range [5, 7] OR: " + solution.rangeBitwiseOr(5, 7));
    }
}
