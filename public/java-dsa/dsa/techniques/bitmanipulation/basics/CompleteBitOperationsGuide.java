package dsa.techniques.bitmanipulation.basics;
/**
 * Complete Bit Operations Guide - All Fundamental Concepts
 * 
 * This file covers EVERY essential bit manipulation concept that you need to know:
 * 1. Left and Right Shift operators
 * 2. Set/Flip/Clear a bit
 * 3. Check if a bit is set
 * 4. Check if number is divisible by power of 2
 * 5. Clear the right-most set bit
 * 6. Brian Kernighan's algorithm
 * 7. Count set bits up to n
 * 8. All shift operators in detail
 * 9. Bit masking techniques
 * 10. Additional fundamental operations
 */
public class CompleteBitOperationsGuide {
    
    // ======================== SHIFT OPERATORS ========================
    
    /**
     * 1. LEFT SHIFT OPERATOR (<<)
     * Shifts bits to the left, fills with zeros from right
     * Equivalent to multiplying by 2^n
     */
    public static void demonstrateLeftShift() {
        System.out.println("=== LEFT SHIFT OPERATOR (<<) ===");
        int num = 5; // Binary: 101
        
        System.out.println("Original: " + num + " (binary: " + Integer.toBinaryString(num) + ")");
        System.out.println("5 << 1 = " + (num << 1) + " (binary: " + Integer.toBinaryString(num << 1) + ") // Multiply by 2");
        System.out.println("5 << 2 = " + (num << 2) + " (binary: " + Integer.toBinaryString(num << 2) + ") // Multiply by 4");
        System.out.println("5 << 3 = " + (num << 3) + " (binary: " + Integer.toBinaryString(num << 3) + ") // Multiply by 8");
        
        // Fast multiplication by powers of 2
        System.out.println("\nFast multiplication using left shift:");
        System.out.println("7 * 8 = 7 << 3 = " + (7 << 3));
        System.out.println("3 * 16 = 3 << 4 = " + (3 << 4));
    }
    
    /**
     * 2. RIGHT SHIFT OPERATOR (>>)
     * Arithmetic right shift - preserves sign bit
     * Equivalent to dividing by 2^n (rounded down)
     */
    public static void demonstrateRightShift() {
        System.out.println("\n=== RIGHT SHIFT OPERATOR (>>) ===");
        int positive = 20; // Binary: 10100
        int negative = -20;
        
        System.out.println("Positive number:");
        System.out.println("Original: " + positive + " (binary: " + Integer.toBinaryString(positive) + ")");
        System.out.println("20 >> 1 = " + (positive >> 1) + " (binary: " + Integer.toBinaryString(positive >> 1) + ") // Divide by 2");
        System.out.println("20 >> 2 = " + (positive >> 2) + " (binary: " + Integer.toBinaryString(positive >> 2) + ") // Divide by 4");
        
        System.out.println("\nNegative number (sign extension):");
        System.out.println("Original: " + negative + " (binary: " + Integer.toBinaryString(negative) + ")");
        System.out.println("-20 >> 1 = " + (negative >> 1) + " (binary: " + Integer.toBinaryString(negative >> 1) + ")");
        System.out.println("-20 >> 2 = " + (negative >> 2) + " (binary: " + Integer.toBinaryString(negative >> 2) + ")");
    }
    
    /**
     * 3. UNSIGNED RIGHT SHIFT OPERATOR (>>>)
     * Logical right shift - always fills with zeros from left
     */
    public static void demonstrateUnsignedRightShift() {
        System.out.println("\n=== UNSIGNED RIGHT SHIFT OPERATOR (>>>) ===");
        int negative = -20;
        
        System.out.println("Comparing >> vs >>> for negative numbers:");
        System.out.println("Original: " + negative + " (binary: " + Integer.toBinaryString(negative) + ")");
        System.out.println("-20 >> 1 = " + (negative >> 1) + " (arithmetic shift)");
        System.out.println("-20 >>> 1 = " + (negative >>> 1) + " (logical shift)");
        System.out.println("-20 >>> 2 = " + (negative >>> 2) + " (logical shift)");
    }
    
    // ======================== BIT MANIPULATION OPERATIONS ========================
    
    /**
     * 4. CHECK IF A BIT IS SET
     * Use AND operation with a mask
     */
    public static boolean isBitSet(int num, int position) {
        return (num & (1 << position)) != 0;
    }
    
    /**
     * 5. SET A BIT (make it 1)
     * Use OR operation with a mask
     */
    public static int setBit(int num, int position) {
        return num | (1 << position);
    }
    
    /**
     * 6. FLIP/TOGGLE A BIT (0->1, 1->0)
     * Use XOR operation with a mask
     */
    public static int flipBit(int num, int position) {
        return num ^ (1 << position);
    }
    
    /**
     * 7. CLEAR A BIT (make it 0)
     * Use AND operation with inverted mask
     */
    public static int clearBit(int num, int position) {
        return num & ~(1 << position);
    }
    
    /**
     * Demonstrate all bit operations
     */
    public static void demonstrateBitOperations() {
        System.out.println("\n=== BIT MANIPULATION OPERATIONS ===");
        int num = 12; // Binary: 1100
        System.out.println("Original number: " + num + " (binary: " + Integer.toBinaryString(num) + ")");
        
        // Check if bits are set
        for (int i = 0; i < 4; i++) {
            System.out.println("Bit " + i + " is set: " + isBitSet(num, i));
        }
        
        // Set bit 0
        int afterSet = setBit(num, 0);
        System.out.println("After setting bit 0: " + afterSet + " (binary: " + Integer.toBinaryString(afterSet) + ")");
        
        // Clear bit 2
        int afterClear = clearBit(num, 2);
        System.out.println("After clearing bit 2: " + afterClear + " (binary: " + Integer.toBinaryString(afterClear) + ")");
        
        // Flip bit 1
        int afterFlip = flipBit(num, 1);
        System.out.println("After flipping bit 1: " + afterFlip + " (binary: " + Integer.toBinaryString(afterFlip) + ")");
    }
    
    // ======================== POWER OF 2 OPERATIONS ========================
    
    /**
     * 8. CHECK IF NUMBER IS DIVISIBLE BY POWER OF 2
     * Use modulo with bit masking for powers of 2
     */
    public static boolean isDivisibleByPowerOf2(int num, int power) {
        int divisor = 1 << power; // 2^power
        // For powers of 2, n % (2^k) = n & (2^k - 1)
        return (num & (divisor - 1)) == 0;
    }
    
    /**
     * Fast modulo operation for powers of 2
     */
    public static int fastModuloPowerOf2(int num, int power) {
        return num & ((1 << power) - 1);
    }
    
    /**
     * Check if number is power of 2
     */
    public static boolean isPowerOf2(int num) {
        return num > 0 && (num & (num - 1)) == 0;
    }
    
    public static void demonstratePowerOf2Operations() {
        System.out.println("\n=== POWER OF 2 OPERATIONS ===");
        
        // Test divisibility by powers of 2
        int[] testNumbers = {16, 17, 24, 32};
        for (int num : testNumbers) {
            System.out.println("Number: " + num);
            System.out.println("  Divisible by 2^1 (2): " + isDivisibleByPowerOf2(num, 1));
            System.out.println("  Divisible by 2^2 (4): " + isDivisibleByPowerOf2(num, 2));
            System.out.println("  Divisible by 2^3 (8): " + isDivisibleByPowerOf2(num, 3));
            System.out.println("  Is power of 2: " + isPowerOf2(num));
            System.out.println();
        }
        
        // Fast modulo operations
        System.out.println("Fast modulo operations:");
        int num = 25;
        System.out.println(num + " % 8 = " + fastModuloPowerOf2(num, 3) + " (using bit masking)");
        System.out.println(num + " % 16 = " + fastModuloPowerOf2(num, 4) + " (using bit masking)");
    }
    
    // ======================== BRIAN KERNIGHAN'S ALGORITHM ========================
    
    /**
     * 9. CLEAR THE RIGHT-MOST SET BIT
     * Brian Kernighan's key operation: n & (n-1)
     */
    public static int clearRightmostSetBit(int num) {
        return num & (num - 1);
    }
    
    /**
     * 10. BRIAN KERNIGHAN'S ALGORITHM for counting set bits
     * More efficient than checking each bit position
     */
    public static int countSetBitsBK(int num) {
        int count = 0;
        while (num > 0) {
            num = num & (num - 1); // Clear rightmost set bit
            count++;
        }
        return count;
    }
    
    /**
     * Find position of rightmost set bit
     */
    public static int getRightmostSetBitPosition(int num) {
        if (num == 0) return -1;
        
        // Isolate rightmost set bit using n & (-n)
        int rightmostBit = num & (-num);
        
        // Count position (0-indexed from right)
        int position = 0;
        while (rightmostBit > 1) {
            rightmostBit >>= 1;
            position++;
        }
        return position;
    }
    
    public static void demonstrateBrianKernighan() {
        System.out.println("\n=== BRIAN KERNIGHAN'S ALGORITHM ===");
        int num = 44; // Binary: 101100
        System.out.println("Original: " + num + " (binary: " + Integer.toBinaryString(num) + ")");
        
        // Demonstrate clearing rightmost set bit step by step
        int temp = num;
        int step = 1;
        while (temp > 0) {
            int cleared = clearRightmostSetBit(temp);
            System.out.println("Step " + step + ": " + temp + " & " + (temp - 1) + " = " + cleared + 
                             " (binary: " + Integer.toBinaryString(cleared) + ")");
            temp = cleared;
            step++;
        }
        
        System.out.println("Total set bits: " + countSetBitsBK(num));
        System.out.println("Rightmost set bit position: " + getRightmostSetBitPosition(num));
    }
    
    // ======================== COUNT SET BITS UP TO N ========================
    
    /**
     * 11. COUNT SET BITS FOR ALL NUMBERS FROM 1 TO N
     * Optimized approach using bit position analysis
     */
    public static long countSetBitsUpToN(int n) {
        long totalCount = 0;
        
        // For each bit position
        for (int bitPos = 0; bitPos < 32; bitPos++) {
            long cycleLength = 1L << (bitPos + 1); // 2^(bitPos+1)
            long completeCycles = (n + 1) / cycleLength;
            long remainder = (n + 1) % cycleLength;
            
            // Add contribution from complete cycles
            totalCount += completeCycles * (1L << bitPos);
            
            // Add contribution from incomplete cycle
            if (remainder > (1L << bitPos)) {
                totalCount += remainder - (1L << bitPos);
            }
        }
        
        return totalCount;
    }
    
    /**
     * Brute force approach for verification
     */
    public static long countSetBitsUpToNBruteForce(int n) {
        long total = 0;
        for (int i = 1; i <= n; i++) {
            total += countSetBitsBK(i);
        }
        return total;
    }
    
    public static void demonstrateCountSetBitsUpToN() {
        System.out.println("\n=== COUNT SET BITS UP TO N ===");
        
        int[] testValues = {3, 7, 15, 31};
        for (int n : testValues) {
            long optimized = countSetBitsUpToN(n);
            long bruteForce = countSetBitsUpToNBruteForce(n);
            
            System.out.println("n = " + n + ":");
            System.out.println("  Optimized: " + optimized);
            System.out.println("  Brute Force: " + bruteForce);
            System.out.println("  Match: " + (optimized == bruteForce));
            
            // Show individual counts for small n
            if (n <= 15) {
                System.out.print("  Individual: ");
                for (int i = 1; i <= n; i++) {
                    System.out.print(countSetBitsBK(i) + " ");
                }
                System.out.println();
            }
            System.out.println();
        }
    }
    
    // ======================== BIT MASKING TECHNIQUES ========================
    
    /**
     * 12. BIT MASKING FUNDAMENTALS
     */
    public static void demonstrateBitMasking() {
        System.out.println("\n=== BIT MASKING TECHNIQUES ===");
        
        // Creating masks
        System.out.println("Creating masks:");
        int mask1 = (1 << 3) - 1; // 111 (first 3 bits)
        int mask2 = 0xFF;          // 11111111 (first 8 bits)
        int mask3 = ~0;            // All 1s
        
        System.out.println("Mask for first 3 bits: " + Integer.toBinaryString(mask1));
        System.out.println("Mask for first 8 bits: " + Integer.toBinaryString(mask2));
        System.out.println("Mask for all bits: " + Integer.toBinaryString(mask3));
        
        // Extracting bit ranges
        int num = 0b11010110; // 214
        System.out.println("\nExtracting bit ranges from " + num + " (binary: " + Integer.toBinaryString(num) + "):");
        
        // Extract bits 2-4
        int extracted = (num >> 2) & ((1 << 3) - 1);
        System.out.println("Bits 2-4: " + Integer.toBinaryString(extracted));
        
        // Extract even positioned bits
        int evenBits = num & 0x55555555; // 01010101...
        System.out.println("Even positioned bits: " + Integer.toBinaryString(evenBits));
        
        // Extract odd positioned bits  
        int oddBits = num & 0xAAAAAAAA; // 10101010...
        System.out.println("Odd positioned bits: " + Integer.toBinaryString(oddBits));
    }
    
    // ======================== ADDITIONAL FUNDAMENTAL OPERATIONS ========================
    
    /**
     * 13. SWAP BITS AT TWO POSITIONS
     */
    public static int swapBits(int num, int pos1, int pos2) {
        // Get bits at both positions
        int bit1 = (num >> pos1) & 1;
        int bit2 = (num >> pos2) & 1;
        
        // If bits are different, swap them
        if (bit1 != bit2) {
            num ^= (1 << pos1) | (1 << pos2);
        }
        
        return num;
    }
    
    /**
     * 14. REVERSE BITS IN A NUMBER
     */
    public static int reverseBits(int num) {
        int result = 0;
        for (int i = 0; i < 32; i++) {
            result = (result << 1) | (num & 1);
            num >>= 1;
        }
        return result;
    }
    
    /**
     * 15. CHECK IF NUMBER HAS ALTERNATING BITS
     */
    public static boolean hasAlternatingBits(int num) {
        int xor = num ^ (num >> 1);
        return (xor & (xor + 1)) == 0;
    }
    
    public static void demonstrateAdditionalOperations() {
        System.out.println("\n=== ADDITIONAL FUNDAMENTAL OPERATIONS ===");
        
        int num = 0b1010; // 10
        System.out.println("Original: " + num + " (binary: " + Integer.toBinaryString(num) + ")");
        
        // Swap bits
        int swapped = swapBits(num, 1, 3);
        System.out.println("After swapping bits 1 and 3: " + swapped + " (binary: " + Integer.toBinaryString(swapped) + ")");
        
        // Check alternating bits
        System.out.println("Has alternating bits: " + hasAlternatingBits(num));
        System.out.println("1010 has alternating bits: " + hasAlternatingBits(0b1010));
        System.out.println("1011 has alternating bits: " + hasAlternatingBits(0b1011));
    }
    
    // ======================== MAIN TEST METHOD ========================
    
    public static void main(String[] args) {
        System.out.println("COMPLETE BIT OPERATIONS GUIDE");
        System.out.println("============================");
        
        // Run all demonstrations
        demonstrateLeftShift();
        demonstrateRightShift();
        demonstrateUnsignedRightShift();
        demonstrateBitOperations();
        demonstratePowerOf2Operations();
        demonstrateBrianKernighan();
        demonstrateCountSetBitsUpToN();
        demonstrateBitMasking();
        demonstrateAdditionalOperations();
        
        System.out.println("\n=== SUMMARY OF ALL OPERATIONS ===");
        System.out.println("✓ Left Shift (<<) - Multiply by 2^n");
        System.out.println("✓ Right Shift (>>) - Divide by 2^n (arithmetic)");
        System.out.println("✓ Unsigned Right Shift (>>>) - Divide by 2^n (logical)");
        System.out.println("✓ Set Bit - num | (1 << pos)");
        System.out.println("✓ Clear Bit - num & ~(1 << pos)");
        System.out.println("✓ Flip Bit - num ^ (1 << pos)");
        System.out.println("✓ Check Bit - (num & (1 << pos)) != 0");
        System.out.println("✓ Divisible by Power of 2 - (num & (2^k - 1)) == 0");
        System.out.println("✓ Clear Rightmost Set Bit - num & (num - 1)");
        System.out.println("✓ Brian Kernighan's Algorithm - Count set bits efficiently");
        System.out.println("✓ Count Set Bits up to N - Optimized bit position analysis");
        System.out.println("✓ Bit Masking - Extract, combine, and manipulate bit ranges");
        System.out.println("✓ Additional Operations - Swap, reverse, pattern checking");
    }
}

