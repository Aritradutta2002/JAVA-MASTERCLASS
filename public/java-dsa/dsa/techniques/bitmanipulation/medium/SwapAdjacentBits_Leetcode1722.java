package dsa.techniques.bitmanipulation.medium;
/**
 * Swap Adjacent Bits Problem
 * Difficulty: Medium
 * 
 * Given an integer, swap every adjacent bits and return the result.
 * 
 * Example 1:
 * Input: n = 43 (101011 in binary)
 * Output: 23 (010111 in binary)
 * 
 * Example 2:
 * Input: n = 21 (10101 in binary)
 * Output: 42 (101010 in binary)
 * 
 * Note: This problem demonstrates advanced bit manipulation techniques
 * commonly asked in technical interviews.
 */
public class SwapAdjacentBits_Leetcode1722 {
    
    /**
     * Approach 1: Mask and Shift (Optimal)
     * Use masks to extract odd and even positioned bits, then combine
     * Time: O(1), Space: O(1)
     */
    public int swapBits(int n) {
        // 0xAAAAAAAA = 10101010...10101010 (odd positions)
        // 0x55555555 = 01010101...01010101 (even positions)
        
        // Extract odd positioned bits and shift right
        int oddBits = (n & 0xAAAAAAAA) >>> 1;
        
        // Extract even positioned bits and shift left  
        int evenBits = (n & 0x55555555) << 1;
        
        // Combine them
        return oddBits | evenBits;
    }
    
    /**
     * Approach 2: Bit-by-bit swapping
     * Manually swap each pair of adjacent bits
     * Time: O(log n), Space: O(1)
     */
    public int swapBitsManual(int n) {
        int result = 0;
        int position = 0;
        
        while (n > 0) {
            // Extract two adjacent bits
            int bit1 = n & 1;
            n >>>= 1;
            int bit2 = (n > 0) ? (n & 1) : 0;
            if (n > 0) n >>>= 1;
            
            // Swap and place them in result
            result |= (bit1 << (position + 1));
            result |= (bit2 << position);
            
            position += 2;
        }
        
        return result;
    }
    
    /**
     * Approach 3: Using bit manipulation with loops
     * Time: O(16) for 32-bit integers, Space: O(1)
     */
    public int swapBitsLoop(int n) {
        int result = 0;
        
        for (int i = 0; i < 32; i += 2) {
            // Get bits at position i and i+1
            int bit1 = (n >>> i) & 1;
            int bit2 = (n >>> (i + 1)) & 1;
            
            // Set swapped bits in result
            result |= (bit1 << (i + 1));
            result |= (bit2 << i);
        }
        
        return result;
    }
    
    /**
     * Approach 4: Divide and conquer approach
     * Swap bits in groups, then merge
     * Time: O(1), Space: O(1)
     */
    public int swapBitsDivideConquer(int n) {
        // This approach swaps adjacent bits using a series of mask operations
        // It's more complex but demonstrates the principle used in parallel algorithms
        
        // Step 1: Swap adjacent bits
        n = ((n & 0xAAAAAAAA) >>> 1) | ((n & 0x55555555) << 1);
        
        return n;
    }
    
    /**
     * Extension: Swap bits in groups of k
     */
    public int swapBitsInGroups(int n, int k) {
        if (k <= 0 || k > 16) {
            throw new IllegalArgumentException("k must be between 1 and 16");
        }
        
        int result = 0;
        int groupSize = 2 * k;
        
        for (int i = 0; i < 32; i += groupSize) {
            // Extract two k-bit groups
            int mask = (1 << k) - 1;
            int group1 = (n >>> i) & mask;
            int group2 = (n >>> (i + k)) & mask;
            
            // Swap and place them
            result |= (group1 << (i + k));
            result |= (group2 << i);
        }
        
        return result;
    }
    
    /**
     * Utility: Reverse pairs of bits
     */
    public int reverseBitPairs(int n) {
        int result = 0;
        
        for (int i = 0; i < 32; i += 2) {
            int bit1 = (n >>> i) & 1;
            int bit2 = (n >>> (i + 1)) & 1;
            
            // Reverse within each pair
            result |= (bit2 << i);
            result |= (bit1 << (i + 1));
        }
        
        return result;
    }
    
    /**
     * Utility: Check if swapping is correct
     */
    public boolean verifySwap(int original, int swapped) {
        String origBin = String.format("%32s", Integer.toBinaryString(original)).replace(' ', '0');
        String swapBin = String.format("%32s", Integer.toBinaryString(swapped)).replace(' ', '0');
        
        // Check each pair of adjacent bits
        for (int i = 0; i < 32; i += 2) {
            if (origBin.charAt(31 - i) != swapBin.charAt(31 - (i + 1)) ||
                origBin.charAt(31 - (i + 1)) != swapBin.charAt(31 - i)) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Demonstration method
     */
    public void demonstrateSwap(int n) {
        int swapped = swapBits(n);
        
        String originalBin = Integer.toBinaryString(n);
        String swappedBin = Integer.toBinaryString(swapped);
        
        System.out.printf("Original: %d (%s)%n", n, originalBin);
        System.out.printf("Swapped:  %d (%s)%n", swapped, swappedBin);
        
        // Show bit-by-bit comparison
        System.out.println("Bit-by-bit analysis:");
        String fullOriginal = String.format("%32s", originalBin).replace(' ', '0');
        String fullSwapped = String.format("%32s", swappedBin).replace(' ', '0');
        
        System.out.println("Position: ...76543210");
        System.out.println("Original: ..." + fullOriginal.substring(24));
        System.out.println("Swapped:  ..." + fullSwapped.substring(24));
        
        // Show pairs
        System.out.print("Pairs:    ...");
        for (int i = 7; i >= 0; i--) {
            if (i % 2 == 1) {
                System.out.print("[");
            }
            System.out.print(fullOriginal.charAt(24 + i));
            if (i % 2 == 0) {
                System.out.print("]");
            }
        }
        System.out.println();
        
        System.out.println("Verified: " + verifySwap(n, swapped));
        System.out.println();
    }
    
    /**
     * Performance benchmark
     */
    public void benchmark() {
        int[] testNumbers = new int[1000];
        for (int i = 0; i < testNumbers.length; i++) {
            testNumbers[i] = (int)(Math.random() * Integer.MAX_VALUE);
        }
        
        // Test approach 1 (optimal)
        long start = System.nanoTime();
        for (int num : testNumbers) {
            swapBits(num);
        }
        long time1 = System.nanoTime() - start;
        
        // Test approach 2 (manual)
        start = System.nanoTime();
        for (int num : testNumbers) {
            swapBitsManual(num);
        }
        long time2 = System.nanoTime() - start;
        
        // Test approach 3 (loop)
        start = System.nanoTime();
        for (int num : testNumbers) {
            swapBitsLoop(num);
        }
        long time3 = System.nanoTime() - start;
        
        System.out.println("Performance Benchmark (1000 operations):");
        System.out.printf("Mask approach: %.2f μs%n", time1 / 1000.0);
        System.out.printf("Manual approach: %.2f μs%n", time2 / 1000.0);
        System.out.printf("Loop approach: %.2f μs%n", time3 / 1000.0);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        SwapAdjacentBits_Leetcode1722 solution = new SwapAdjacentBits_Leetcode1722();
        
        // Test cases
        int[] testCases = {43, 21, 85, 0, 1, 2, 3, 15, 255, 1023};
        
        System.out.println("Testing Adjacent Bit Swapping:");
        for (int n : testCases) {
            int result = solution.swapBits(n);
            System.out.printf("Input: %d → Output: %d%n", n, result);
        }
        
        System.out.println("\nDetailed Analysis:");
        solution.demonstrateSwap(43);
        solution.demonstrateSwap(21);
        solution.demonstrateSwap(85);
        
        // Test consistency across methods
        System.out.println("Consistency Test:");
        for (int n : testCases) {
            int result1 = solution.swapBits(n);
            int result2 = solution.swapBitsManual(n);
            int result3 = solution.swapBitsLoop(n);
            
            boolean consistent = (result1 == result2) && (result2 == result3);
            System.out.printf("n=%d: %s%n", n, consistent ? "✓" : "✗");
        }
        
        // Test group swapping
        System.out.println("\nGroup Swapping (k=2):");
        for (int n : new int[]{15, 85, 255}) {
            int swapped = solution.swapBitsInGroups(n, 2);
            System.out.printf("Input: %d (%s) → Output: %d (%s)%n", 
                n, Integer.toBinaryString(n), 
                swapped, Integer.toBinaryString(swapped));
        }
        
        // Performance test
        System.out.println();
        solution.benchmark();
        
        // Edge cases
        System.out.println("\nEdge Cases:");
        System.out.println("0: " + solution.swapBits(0));
        System.out.println("1: " + solution.swapBits(1));
        System.out.println("Integer.MAX_VALUE: " + solution.swapBits(Integer.MAX_VALUE));
        System.out.println("-1: " + solution.swapBits(-1));
    }
}

