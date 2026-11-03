package dsa.techniques.bitmanipulation.patterns;
/**
 * Bit Counting Patterns - Interview Essentials
 * 
 * Counting set bits (Hamming weight) is fundamental to many bit manipulation problems.
 * This class covers all essential bit counting techniques and their applications.
 */
public class BitCounting {
    
    /**
     * Pattern 1: Basic Bit Counting (Hamming Weight)
     * LeetCode 191: Number of 1 Bits
     */
    public int hammingWeight(int n) {
        int count = 0;
        while (n != 0) {
            n = n & (n - 1);  // Brian Kernighan's algorithm
            count++;
        }
        return count;
    }
    
    /**
     * Pattern 2: Count Bits for All Numbers 0 to n
     * LeetCode 338: Counting Bits
     */
    public int[] countBits(int n) {
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            // dp[i] = dp[i >> 1] + (i & 1)
            // Right shift removes LSB, add 1 if LSB was set
            dp[i] = dp[i >> 1] + (i & 1);
        }
        
        return dp;
    }
    
    /**
     * Alternative DP approach using Brian Kernighan's idea
     */
    public int[] countBitsAlternative(int n) {
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            // dp[i] = dp[i & (i-1)] + 1
            // i & (i-1) removes the rightmost set bit
            dp[i] = dp[i & (i - 1)] + 1;
        }
        
        return dp;
    }
    
    /**
     * Pattern 3: Sum of Set Bits for Range [L, R]
     */
    public long sumOfSetBits(long L, long R) {
        return sumOfSetBitsUpTo(R) - sumOfSetBitsUpTo(L - 1);
    }
    
    private long sumOfSetBitsUpTo(long n) {
        if (n <= 0) return 0;
        
        long sum = 0;
        
        for (int bit = 0; bit < 64; bit++) {
            long cycleLength = 1L << (bit + 1);
            long completeCycles = (n + 1) / cycleLength;
            long remainder = (n + 1) % cycleLength;
            
            // Complete cycles contribute: completeCycles * (cycleLength / 2)
            sum += completeCycles * (1L << bit);
            
            // Remaining part
            if (remainder > (1L << bit)) {
                sum += remainder - (1L << bit);
            }
        }
        
        return sum;
    }
    
    /**
     * Pattern 4: Parity (Even/Odd number of set bits)
     * Check if number has even or odd number of 1s
     */
    public boolean hasEvenParity(int n) {
        // XOR all bits together
        int parity = 0;
        while (n > 0) {
            parity ^= 1;
            n = n & (n - 1);  // Remove rightmost set bit
        }
        return parity == 0;
    }
    
    /**
     * Optimized parity using XOR reduction
     */
    public boolean hasEvenParityOptimized(int n) {
        n ^= n >> 16;
        n ^= n >> 8;
        n ^= n >> 4;
        n ^= n >> 2;
        n ^= n >> 1;
        return (n & 1) == 0;
    }
    
    /**
     * Pattern 5: Find Position of Set Bits
     * Get all positions where bits are set (0-indexed from right)
     */
    public java.util.List<Integer> getSetBitPositions(int n) {
        java.util.List<Integer> positions = new java.util.ArrayList<>();
        
        int position = 0;
        while (n > 0) {
            if ((n & 1) == 1) {
                positions.add(position);
            }
            n >>= 1;
            position++;
        }
        
        return positions;
    }
    
    /**
     * Pattern 6: Count Trailing Zeros
     * Count number of zeros at the end (from right)
     */
    public int countTrailingZeros(int n) {
        if (n == 0) return 32;  // All bits are zero
        
        // Find rightmost set bit using n & (-n)
        int rightmostSetBit = n & (-n);
        
        // Count position of rightmost set bit
        return Integer.numberOfTrailingZeros(rightmostSetBit);
    }
    
    /**
     * Manual implementation without built-in function
     */
    public int countTrailingZerosManual(int n) {
        if (n == 0) return 32;
        
        int count = 0;
        while ((n & 1) == 0) {
            count++;
            n >>= 1;
        }
        return count;
    }
    
    /**
     * Pattern 7: Count Leading Zeros
     * Count number of zeros at the beginning (from left)
     */
    public int countLeadingZeros(int n) {
        if (n == 0) return 32;
        
        int count = 0;
        for (int i = 31; i >= 0; i--) {
            if ((n & (1 << i)) != 0) {
                break;
            }
            count++;
        }
        return count;
    }
    
    /**
     * Pattern 8: Find Most/Least Significant Bit Position
     */
    public int findMSBPosition(int n) {
        if (n == 0) return -1;
        
        int position = 0;
        while (n > 1) {
            n >>= 1;
            position++;
        }
        return position;
    }
    
    public int findLSBPosition(int n) {
        if (n == 0) return -1;
        
        // Find rightmost set bit and count its position
        return countTrailingZeros(n);
    }
    
    /**
     * Pattern 9: Bit Counting with Lookup Table
     * Precompute for optimization in multiple queries
     */
    private static final int[] BIT_COUNT_TABLE = new int[256];
    
    static {
        for (int i = 0; i < 256; i++) {
            BIT_COUNT_TABLE[i] = Integer.bitCount(i);
        }
    }
    
    public int hammingWeightLookup(int n) {
        return BIT_COUNT_TABLE[n & 0xFF] +
               BIT_COUNT_TABLE[(n >> 8) & 0xFF] +
               BIT_COUNT_TABLE[(n >> 16) & 0xFF] +
               BIT_COUNT_TABLE[(n >> 24) & 0xFF];
    }
    
    /**
     * Pattern 10: Bit Manipulation for Subset Counting
     * Count subsets with specific bit properties
     */
    public int countSubsetsWithEvenBits(int[] nums) {
        int n = nums.length;
        int count = 0;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            int xor = 0;
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    xor ^= nums[i];
                }
            }
            
            if (hasEvenParity(xor)) {
                count++;
            }
        }
        
        return count;
    }
    
    /**
     * Pattern 11: Gray Code Related Bit Counting
     * Count bit differences between consecutive Gray codes
     */
    public int[] grayCodeBitDifferences(int n) {
        int[] differences = new int[1 << n];
        
        for (int i = 1; i < (1 << n); i++) {
            int gray1 = i ^ (i >> 1);
            int gray2 = (i - 1) ^ ((i - 1) >> 1);
            
            differences[i] = hammingWeight(gray1 ^ gray2);
        }
        
        return differences;
    }
    
    /**
     * Pattern 12: Bit Counting in Matrix Operations
     * Count set bits in matrix after operations
     */
    public int countBitsInMatrix(int[][] matrix) {
        int totalBits = 0;
        
        for (int[] row : matrix) {
            for (int num : row) {
                totalBits += hammingWeight(num);
            }
        }
        
        return totalBits;
    }
    
    /**
     * Utility method for performance comparison
     */
    public void compareBitCountingMethods(int n) {
        System.out.println("Comparing bit counting methods for n = " + n);
        System.out.println("Binary representation: " + Integer.toBinaryString(n));
        
        long startTime, endTime;
        
        // Method 1: Built-in
        startTime = System.nanoTime();
        int result1 = Integer.bitCount(n);
        endTime = System.nanoTime();
        System.out.println("Built-in: " + result1 + " (Time: " + (endTime - startTime) + " ns)");
        
        // Method 2: Brian Kernighan's
        startTime = System.nanoTime();
        int result2 = hammingWeight(n);
        endTime = System.nanoTime();
        System.out.println("Brian Kernighan's: " + result2 + " (Time: " + (endTime - startTime) + " ns)");
        
        // Method 3: Lookup table
        startTime = System.nanoTime();
        int result3 = hammingWeightLookup(n);
        endTime = System.nanoTime();
        System.out.println("Lookup table: " + result3 + " (Time: " + (endTime - startTime) + " ns)");
        
        System.out.println("All results match: " + (result1 == result2 && result2 == result3));
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        BitCounting bc = new BitCounting();
        
        // Test basic bit counting
        System.out.println("=== Basic Bit Counting ===");
        int[] testNumbers = {0, 1, 7, 15, 31, 255, 1023};
        for (int num : testNumbers) {
            System.out.printf("Number: %d, Binary: %s, Set bits: %d%n",
                num, Integer.toBinaryString(num), bc.hammingWeight(num));
        }
        
        // Test counting bits for range
        System.out.println("\n=== Counting Bits for Range ===");
        int[] countBitsResult = bc.countBits(10);
        System.out.print("Count bits [0-10]: ");
        for (int count : countBitsResult) {
            System.out.print(count + " ");
        }
        System.out.println();
        
        // Test parity
        System.out.println("\n=== Parity Check ===");
        for (int num : new int[]{5, 6, 7, 8}) {
            System.out.printf("Number: %d, Even parity: %b%n",
                num, bc.hasEvenParity(num));
        }
        
        // Test set bit positions
        System.out.println("\n=== Set Bit Positions ===");
        int num = 0b1010110;  // 86 in decimal
        System.out.printf("Number: %d, Binary: %s%n", num, Integer.toBinaryString(num));
        System.out.println("Set bit positions: " + bc.getSetBitPositions(num));
        
        // Test trailing/leading zeros
        System.out.println("\n=== Leading/Trailing Zeros ===");
        int[] zeroTests = {8, 12, 16, 32};
        for (int n : zeroTests) {
            System.out.printf("Number: %d, Binary: %s, Trailing zeros: %d, Leading zeros: %d%n",
                n, String.format("%8s", Integer.toBinaryString(n)).replace(' ', '0'),
                bc.countTrailingZeros(n), bc.countLeadingZeros(n));
        }
        
        // Performance comparison
        System.out.println("\n=== Performance Comparison ===");
        bc.compareBitCountingMethods(0xFFFFFF);
    }
}

