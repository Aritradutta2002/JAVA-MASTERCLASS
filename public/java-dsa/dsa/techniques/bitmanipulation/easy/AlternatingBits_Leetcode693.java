package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 693: Binary Number with Alternating Bits
 * Difficulty: Easy
 * 
 * Given a positive integer, check whether it has alternating bits: 
 * namely, if two adjacent bits will always have different values.
 * 
 * Example 1:
 * Input: n = 5
 * Output: true
 * Explanation: The binary representation of 5 is: 101
 * 
 * Example 2:
 * Input: n = 7
 * Output: false
 * Explanation: The binary representation of 7 is: 111
 * 
 * Example 3:
 * Input: n = 11
 * Output: false
 * Explanation: The binary representation of 11 is: 1011
 * 
 * Constraints: 1 <= n <= 2^31 - 1
 */
public class AlternatingBits_Leetcode693 {
    
    /**
     * Approach 1: XOR and Power of 2 Check (Optimal)
     * If bits are alternating, XOR with right-shifted version gives all 1s
     * Time: O(1), Space: O(1)
     */
    public boolean hasAlternatingBits(int n) {
        // XOR with right-shifted version
        int xor = n ^ (n >>> 1);
        
        // Check if result is all 1s (i.e., a power of 2 minus 1)
        return (xor & (xor + 1)) == 0;
    }
    
    /**
     * Approach 2: Bit-by-bit checking
     * Time: O(log n), Space: O(1)
     */
    public boolean hasAlternatingBitsBruteForce(int n) {
        int prevBit = n & 1;
        n >>>= 1;
        
        while (n != 0) {
            int currentBit = n & 1;
            if (currentBit == prevBit) {
                return false;
            }
            prevBit = currentBit;
            n >>>= 1;
        }
        
        return true;
    }
    
    /**
     * Approach 3: String-based approach (Less efficient)
     * Time: O(log n), Space: O(log n)
     */
    public boolean hasAlternatingBitsString(int n) {
        String binary = Integer.toBinaryString(n);
        
        for (int i = 0; i < binary.length() - 1; i++) {
            if (binary.charAt(i) == binary.charAt(i + 1)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Approach 4: Mathematical pattern recognition
     * Alternating bit patterns follow specific mathematical sequences
     * Time: O(1), Space: O(1)
     */
    public boolean hasAlternatingBitsMath(int n) {
        // Check if n matches pattern 101010... or 010101...
        // Pattern 1: 1, 5, 21, 85, 341, ... (4^k - 1)/3 for k >= 1
        // Pattern 2: 2, 10, 42, 170, 682, ... 2*(4^k - 1)/3 for k >= 1
        
        return isPattern1(n) || isPattern2(n);
    }
    
    private boolean isPattern1(int n) {
        // Check if n = (4^k - 1) / 3
        long val = 1;
        while (val <= n) {
            if (val == n) return true;
            val = val * 4 + 1; // Next value: (4^(k+1) - 1) / 3
        }
        return false;
    }
    
    private boolean isPattern2(int n) {
        // Check if n = 2 * (4^k - 1) / 3
        return n % 2 == 0 && isPattern1(n / 2);
    }
    
    /**
     * Utility: Generate all valid alternating bit numbers up to limit
     */
    public static void generateAlternatingNumbers(int limit) {
        System.out.println("Valid alternating bit numbers up to " + limit + ":");
        
        for (int i = 1; i <= limit; i++) {
            AlternatingBits_Leetcode693 solution = new AlternatingBits_Leetcode693();
            if (solution.hasAlternatingBits(i)) {
                System.out.printf("%d (%s) ", i, Integer.toBinaryString(i));
            }
        }
        System.out.println();
    }
    
    /**
     * Pattern analysis helper
     */
    public static void analyzePattern() {
        System.out.println("Pattern Analysis:");
        System.out.println("Type 1 (starts with 1): 1, 5, 21, 85, 341, ...");
        System.out.println("Type 2 (starts with 0): 2, 10, 42, 170, 682, ...");
        
        // Generate pattern 1: (4^k - 1) / 3
        long val1 = 1;
        for (int k = 1; k <= 10 && val1 <= Integer.MAX_VALUE; k++) {
            System.out.printf("k=%d: %d (%s)%n", k, val1, Long.toBinaryString(val1));
            val1 = val1 * 4 + 1;
        }
        
        System.out.println();
        
        // Generate pattern 2: 2 * (4^k - 1) / 3
        long val2 = 2;
        for (int k = 1; k <= 10 && val2 <= Integer.MAX_VALUE; k++) {
            System.out.printf("k=%d: %d (%s)%n", k, val2, Long.toBinaryString(val2));
            val2 = val2 * 4 + 2;
        }
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        AlternatingBits_Leetcode693 solution = new AlternatingBits_Leetcode693();
        
        // Test cases
        int[] testCases = {5, 7, 11, 10, 1, 2, 3, 21, 85, 42, 170};
        
        System.out.println("Testing Alternating Bits:");
        for (int n : testCases) {
            boolean result = solution.hasAlternatingBits(n);
            System.out.printf("n=%d (%s) → %b%n", 
                n, Integer.toBinaryString(n), result);
        }
        
        System.out.println();
        generateAlternatingNumbers(100);
        
        System.out.println();
        analyzePattern();
        
        // Performance comparison
        System.out.println("\nPerformance Test:");
        int n = 1431655765; // Large alternating number
        
        long start = System.nanoTime();
        boolean result1 = solution.hasAlternatingBits(n);
        long time1 = System.nanoTime() - start;
        
        start = System.nanoTime();
        boolean result2 = solution.hasAlternatingBitsBruteForce(n);
        long time2 = System.nanoTime() - start;
        
        System.out.printf("XOR method: %b (%.2f ns)%n", result1, time1 / 1.0);
        System.out.printf("Brute force: %b (%.2f ns)%n", result2, time2 / 1.0);
    }
}

