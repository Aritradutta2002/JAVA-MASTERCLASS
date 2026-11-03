package dsa.techniques.bitmanipulation.basics;
/**
 * Count Set Bits (Hamming Weight) - Multiple Approaches
 * 
 * Count the number of 1's in binary representation
 * This is one of the most fundamental bit manipulation operations
 */
public class CountSetBits {
    
    /**
     * Approach 1: Simple iteration through each bit
     * Time: O(log n), Space: O(1)
     */
    public static int countSetBitsV1(int n) {
        int count = 0;
        while (n > 0) {
            count += (n & 1);
            n >>= 1;
        }
        return count;
    }
    
    /**
     * Approach 2: Brian Kernighan's Algorithm
     * n & (n-1) removes the rightmost set bit
     * Time: O(number of set bits), Space: O(1)
     */
    public static int countSetBitsV2(int n) {
        int count = 0;
        while (n > 0) {
            n = n & (n - 1);  // Remove rightmost set bit
            count++;
        }
        return count;
    }
    
    /**
     * Approach 3: Using built-in function
     * Time: O(1), Space: O(1)
     */
    public static int countSetBitsV3(int n) {
        return Integer.bitCount(n);
    }
    
    /**
     * Approach 4: Using lookup table (for 32-bit integers)
     * Precompute for all 8-bit numbers, then use 4 lookups
     * Time: O(1), Space: O(256) for lookup table
     */
    private static int[] lookupTable = new int[256];
    
    static {
        // Precompute set bits for all 8-bit numbers
        for (int i = 0; i < 256; i++) {
            lookupTable[i] = countSetBitsV2(i);
        }
    }
    
    public static int countSetBitsV4(int n) {
        return lookupTable[n & 0xFF] +           // Last 8 bits
               lookupTable[(n >> 8) & 0xFF] +    // Next 8 bits
               lookupTable[(n >> 16) & 0xFF] +   // Next 8 bits
               lookupTable[(n >> 24) & 0xFF];    // First 8 bits
    }
    
    /**
     * Count set bits for all numbers from 1 to n
     * Time: O(n log n), Space: O(1)
     */
    public static int countSetBitsRange(int n) {
        int totalCount = 0;
        for (int i = 1; i <= n; i++) {
            totalCount += countSetBitsV2(i);
        }
        return totalCount;
    }
    
    /**
     * Optimized: Count set bits for all numbers from 1 to n
     * Using bit position analysis
     * Time: O(log n), Space: O(1)
     */
    public static int countSetBitsRangeOptimized(int n) {
        int count = 0;
        for (int bit = 0; bit < 32; bit++) {
            int cycleLength = 1 << (bit + 1);
            int completeCycles = (n + 1) / cycleLength;
            int remainder = (n + 1) % cycleLength;
            
            count += completeCycles * (1 << bit);
            if (remainder > (1 << bit)) {
                count += remainder - (1 << bit);
            }
        }
        return count;
    }
    
    public static void main(String[] args) {
        int[] testCases = {0, 1, 7, 15, 31, 255, 1023};
        
        System.out.println("Count Set Bits:");
        for (int num : testCases) {
            System.out.printf("n=%d (binary: %s): V1=%d, V2=%d, V3=%d, V4=%d%n", 
                num, Integer.toBinaryString(num),
                countSetBitsV1(num), countSetBitsV2(num), 
                countSetBitsV3(num), countSetBitsV4(num));
        }
        
        System.out.println("\nCount Set Bits in Range 1 to n:");
        for (int n : new int[]{3, 7, 15}) {
            System.out.printf("n=%d: Simple=%d, Optimized=%d%n", 
                n, countSetBitsRange(n), countSetBitsRangeOptimized(n));
        }
    }
}

