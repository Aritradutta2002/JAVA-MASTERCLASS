package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 191: Number of 1 Bits (Hamming Weight)
 * Difficulty: Easy
 * 
 * Write a function that takes an unsigned integer and returns the number of '1' bits it has.
 * 
 * Example 1:
 * Input: n = 00000000000000000000000000001011
 * Output: 3
 * Explanation: The input binary string has three '1' bits.
 * 
 * Example 2:
 * Input: n = 00000000000000000000000010000000
 * Output: 1
 * 
 * Constraints:
 * - The input must be a binary string of length 32.
 */
public class NumberOf1Bits_Leetcode191 {
    
    /**
     * Approach 1: Check each bit using AND operation
     * Time: O(32) = O(1), Space: O(1)
     */
    public int hammingWeight1(int n) {
        int count = 0;
        for (int i = 0; i < 32; i++) {
            if ((n & (1 << i)) != 0) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Approach 2: Right shift and check LSB
     * Time: O(32) = O(1), Space: O(1)
     */
    public int hammingWeight2(int n) {
        int count = 0;
        while (n != 0) {
            count += (n & 1);
            n >>>= 1;  // Unsigned right shift
        }
        return count;
    }
    
    /**
     * Approach 3: Brian Kernighan's Algorithm (Optimal)
     * n & (n-1) flips the least significant set bit
     * Time: O(number of set bits), Space: O(1)
     */
    public int hammingWeight3(int n) {
        int count = 0;
        while (n != 0) {
            n = n & (n - 1);  // Remove rightmost set bit
            count++;
        }
        return count;
    }
    
    /**
     * Approach 4: Using built-in function
     * Time: O(1), Space: O(1)
     */
    public int hammingWeight4(int n) {
        return Integer.bitCount(n);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        NumberOf1Bits_Leetcode191 solution = new NumberOf1Bits_Leetcode191();
        
        // Test cases
        int[] testCases = {
            0b00000000000000000000000000001011,  // 11 in decimal, 3 set bits
            0b00000000000000000000000010000000,  // 128 in decimal, 1 set bit
            0b11111111111111111111111111111101,  // -3 in decimal, 31 set bits
            0,                                    // 0, 0 set bits
            1,                                    // 1, 1 set bit
            -1                                    // All bits set, 32 set bits
        };
        
        for (int testCase : testCases) {
            System.out.println("Input: " + testCase + " (binary: " + Integer.toBinaryString(testCase) + ")");
            System.out.println("Approach 1: " + solution.hammingWeight1(testCase));
            System.out.println("Approach 2: " + solution.hammingWeight2(testCase));
            System.out.println("Approach 3: " + solution.hammingWeight3(testCase));
            System.out.println("Approach 4: " + solution.hammingWeight4(testCase));
            System.out.println();
        }
    }
}

