package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 190: Reverse Bits
 * Difficulty: Easy
 * 
 * Reverse bits of a given 32 bits unsigned integer.
 * 
 * Example 1:
 * Input: n = 00000010100101000001111010011100
 * Output:    00111001011110000010100101000000
 * Explanation: The input binary string represents the unsigned integer 43261596, 
 * so return 964176192 which its binary representation is 00111001011110000010100101000000.
 * 
 * Example 2:
 * Input: n = 11111111111111111111111111111101
 * Output:   10111111111111111111111111111111
 * Explanation: The input binary string represents the unsigned integer 4294967293, 
 * so return 3221225471 which its binary representation is 10111111111111111111111111111111.
 */
public class ReverseBits_Leetcode190 {
    
    /**
     * Approach 1: Bit by bit reversal
     * Extract each bit from right and build result from left
     * Time: O(32) = O(1), Space: O(1)
     */
    public int reverseBits1(int n) {
        int result = 0;
        for (int i = 0; i < 32; i++) {
            // Extract the rightmost bit of n
            int bit = n & 1;
            
            // Shift result left and add the extracted bit
            result = (result << 1) | bit;
            
            // Shift n right to process next bit
            n >>= 1;
        }
        return result;
    }
    
    /**
     * Approach 2: Using bit manipulation with masks
     * More efficient for multiple calls with caching
     * Time: O(1), Space: O(1)
     */
    public int reverseBits2(int n) {
        int result = 0;
        for (int i = 0; i < 32; i++) {
            if ((n & (1 << i)) != 0) {
                result |= (1 << (31 - i));
            }
        }
        return result;
    }
    
    /**
     * Approach 3: Divide and Conquer (Most Efficient)
     * Swap adjacent bits, then adjacent pairs, then adjacent 4-bits, etc.
     * Time: O(1), Space: O(1)
     */
    public int reverseBits3(int n) {
        // Swap adjacent bits
        n = ((n & 0xaaaaaaaa) >>> 1) | ((n & 0x55555555) << 1);
        
        // Swap adjacent pairs
        n = ((n & 0xcccccccc) >>> 2) | ((n & 0x33333333) << 2);
        
        // Swap adjacent 4-bits
        n = ((n & 0xf0f0f0f0) >>> 4) | ((n & 0x0f0f0f0f) << 4);
        
        // Swap adjacent bytes
        n = ((n & 0xff00ff00) >>> 8) | ((n & 0x00ff00ff) << 8);
        
        // Swap adjacent 16-bits
        n = (n >>> 16) | (n << 16);
        
        return n;
    }
    
    /**
     * Approach 4: Using lookup table for optimization
     * Precompute reverse for all 8-bit numbers, then combine
     * Time: O(1), Space: O(256) for lookup table
     */
    private static int[] reverseTable = new int[256];
    
    static {
        // Precompute reverse for all 8-bit numbers
        for (int i = 0; i < 256; i++) {
            int reversed = 0;
            int num = i;
            for (int j = 0; j < 8; j++) {
                reversed = (reversed << 1) | (num & 1);
                num >>= 1;
            }
            reverseTable[i] = reversed;
        }
    }
    
    public int reverseBits4(int n) {
        return (reverseTable[n & 0xFF] << 24) |
               (reverseTable[(n >> 8) & 0xFF] << 16) |
               (reverseTable[(n >> 16) & 0xFF] << 8) |
               (reverseTable[(n >> 24) & 0xFF]);
    }
    
    /**
     * Helper method to print binary representation
     */
    private static String toBinary32(int n) {
        String binary = Integer.toBinaryString(n);
        return String.format("%32s", binary).replace(' ', '0');
    }
    
    /**
     * Utility: Reverse bits in a byte (8 bits)
     */
    public static int reverseByte(int b) {
        return ((b & 0x01) << 7) |
               ((b & 0x02) << 5) |
               ((b & 0x04) << 3) |
               ((b & 0x08) << 1) |
               ((b & 0x10) >> 1) |
               ((b & 0x20) >> 3) |
               ((b & 0x40) >> 5) |
               ((b & 0x80) >> 7);
    }
    
    /**
     * Follow-up: Reverse bits in a range [i, j]
     */
    public int reverseBitsInRange(int n, int i, int j) {
        if (i > j) return n;
        
        while (i < j) {
            // Get bits at positions i and j
            int bitI = (n >> i) & 1;
            int bitJ = (n >> j) & 1;
            
            // If bits are different, swap them
            if (bitI != bitJ) {
                n ^= (1 << i) | (1 << j);
            }
            
            i++;
            j--;
        }
        return n;
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        ReverseBits_Leetcode190 solution = new ReverseBits_Leetcode190();
        
        // Test cases
        int[] testCases = {
            0b00000010100101000001111010011100,  // 43261596
            0b11111111111111111111111111111101,  // 4294967293 (unsigned)
            0b00000000000000000000000000000001,  // 1
            0b11111111111111111111111111111111,  // -1 (all bits set)
            0b10000000000000000000000000000000   // Integer.MIN_VALUE
        };
        
        System.out.println("Testing Reverse Bits:");
        for (int n : testCases) {
            System.out.println("Input:  " + toBinary32(n) + " (" + n + ")");
            
            int result1 = solution.reverseBits1(n);
            int result2 = solution.reverseBits2(n);
            int result3 = solution.reverseBits3(n);
            int result4 = solution.reverseBits4(n);
            
            System.out.println("Output: " + toBinary32(result1) + " (" + result1 + ")");
            System.out.println("All approaches match: " + 
                (result1 == result2 && result2 == result3 && result3 == result4));
            System.out.println();
        }
        
        // Test reverse bits in range
        System.out.println("Testing Reverse Bits in Range:");
        int num = 0b11010010;  // 210
        System.out.println("Original: " + Integer.toBinaryString(num));
        int reversed = solution.reverseBitsInRange(num, 2, 5);
        System.out.println("Reverse bits 2-5: " + Integer.toBinaryString(reversed));
    }
}

