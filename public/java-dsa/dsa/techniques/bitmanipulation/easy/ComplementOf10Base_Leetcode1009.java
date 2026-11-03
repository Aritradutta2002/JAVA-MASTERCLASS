package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 1009: Complement of Base 10 Integer
 * Difficulty: Easy
 * 
 * The complement of an integer is the integer you get when you flip all the 0's to 1's 
 * and all the 1's to 0's in its binary representation.
 * 
 * For example, The integer 5 is "101" in binary and its complement is "010" which is 
 * the integer 2.
 * 
 * Given an integer n, return its complement.
 * 
 * Example 1:
 * Input: n = 5
 * Output: 2
 * Explanation: 5 is "101" in binary, with complement "010" in binary, which is 2 in base-10.
 * 
 * Example 2:
 * Input: n = 7
 * Output: 0
 * Explanation: 7 is "111" in binary, with complement "000" in binary, which is 0 in base-10.
 * 
 * Example 3:
 * Input: n = 10
 * Output: 5
 * Explanation: 10 is "1010" in binary, with complement "0101" in binary, which is 5 in base-10.
 * 
 * Constraints: 0 <= n < 10^9
 */
public class ComplementOf10Base_Leetcode1009 {
    
    /**
     * Approach 1: Create mask and XOR (Optimal)
     * Time: O(log n), Space: O(1)
     */
    public int bitwiseComplement(int n) {
        // Special case for 0
        if (n == 0) return 1;
        
        // Find the number of bits in n
        int bitLength = Integer.toBinaryString(n).length();
        
        // Create mask with all 1s for the bit length
        int mask = (1 << bitLength) - 1;
        
        // XOR with mask to flip only significant bits
        return n ^ mask;
    }
    
    /**
     * Approach 2: Using bit manipulation to find mask
     * Time: O(log n), Space: O(1)
     */
    public int bitwiseComplementBitwise(int n) {
        if (n == 0) return 1;
        
        // Find the highest set bit position
        int mask = 0;
        int temp = n;
        
        while (temp > 0) {
            mask = (mask << 1) | 1;
            temp >>= 1;
        }
        
        return n ^ mask;
    }
    
    /**
     * Approach 3: Mathematical approach
     * Time: O(log n), Space: O(1)
     */
    public int bitwiseComplementMath(int n) {
        if (n == 0) return 1;
        
        // Find the next power of 2 greater than n
        int powerOf2 = 1;
        while (powerOf2 <= n) {
            powerOf2 <<= 1;
        }
        
        // Complement is (2^k - 1) - n where 2^k is next power of 2
        return (powerOf2 - 1) ^ n;
    }
    
    /**
     * Approach 4: Using built-in functions
     * Time: O(1), Space: O(1)
     */
    public int bitwiseComplementBuiltIn(int n) {
        if (n == 0) return 1;
        
        // Find number of bits
        int bitLength = 32 - Integer.numberOfLeadingZeros(n);
        
        // Create mask
        int mask = (1 << bitLength) - 1;
        
        return n ^ mask;
    }
    
    /**
     * Helper method: Manual bit counting
     */
    private int countBits(int n) {
        int count = 0;
        while (n > 0) {
            count++;
            n >>= 1;
        }
        return count;
    }
    
    /**
     * Extension: Bit flip operations
     */
    
    /**
     * Flip all bits in a 32-bit integer
     */
    public int flipAllBits(int n) {
        return ~n;
    }
    
    /**
     * Flip specific range of bits
     */
    public int flipBitsInRange(int n, int start, int end) {
        // Create mask for range [start, end]
        int mask = ((1 << (end - start + 1)) - 1) << start;
        return n ^ mask;
    }
    
    /**
     * Flip rightmost n bits
     */
    public int flipRightmostBits(int num, int n) {
        int mask = (1 << n) - 1;
        return num ^ mask;
    }
    
    /**
     * Demo and visualization methods
     */
    public static void demonstrateComplement(int n) {
        ComplementOf10Base_Leetcode1009 solution = new ComplementOf10Base_Leetcode1009();
        
        String binary = Integer.toBinaryString(n);
        int complement = solution.bitwiseComplement(n);
        String complementBinary = Integer.toBinaryString(complement);
        
        System.out.printf("Number: %d%n", n);
        System.out.printf("Binary: %s%n", binary);
        System.out.printf("Complement: %d%n", complement);
        System.out.printf("Complement Binary: %s%n", complementBinary);
        System.out.println("Bit-by-bit comparison:");
        
        // Pad with zeros for better visualization
        int maxLength = Math.max(binary.length(), complementBinary.length());
        binary = String.format("%" + maxLength + "s", binary).replace(' ', '0');
        complementBinary = String.format("%" + maxLength + "s", complementBinary).replace(' ', '0');
        
        System.out.printf("Original:   %s%n", binary);
        System.out.printf("Complement: %s%n", complementBinary);
        System.out.println();
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        ComplementOf10Base_Leetcode1009 solution = new ComplementOf10Base_Leetcode1009();
        
        // Test cases from problem
        int[] testCases = {5, 7, 10, 0, 1, 15, 31, 63};
        
        System.out.println("Testing Bitwise Complement:");
        for (int n : testCases) {
            int result = solution.bitwiseComplement(n);
            System.out.printf("Input: %d → Output: %d%n", n, result);
        }
        
        System.out.println("\nDetailed Analysis:");
        for (int n : new int[]{5, 7, 10}) {
            demonstrateComplement(n);
        }
        
        // Performance comparison
        System.out.println("Performance Comparison:");
        int testNum = 123456;
        
        long start = System.nanoTime();
        int result1 = solution.bitwiseComplement(testNum);
        long time1 = System.nanoTime() - start;
        
        start = System.nanoTime();
        int result2 = solution.bitwiseComplementBitwise(testNum);
        long time2 = System.nanoTime() - start;
        
        start = System.nanoTime();
        int result3 = solution.bitwiseComplementBuiltIn(testNum);
        long time3 = System.nanoTime() - start;
        
        System.out.printf("Method 1 (Mask): %d (%.2f ns)%n", result1, time1 / 1.0);
        System.out.printf("Method 2 (Bitwise): %d (%.2f ns)%n", result2, time2 / 1.0);
        System.out.printf("Method 3 (Built-in): %d (%.2f ns)%n", result3, time3 / 1.0);
        
        // Edge cases
        System.out.println("\nEdge Cases:");
        System.out.println("Complement of 0: " + solution.bitwiseComplement(0));
        System.out.println("Complement of 1: " + solution.bitwiseComplement(1));
        System.out.println("Complement of Integer.MAX_VALUE: " + solution.bitwiseComplement(Integer.MAX_VALUE));
    }
}

