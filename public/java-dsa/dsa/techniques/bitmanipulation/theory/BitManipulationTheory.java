package dsa.techniques.bitmanipulation.theory;

/**
 * Bit Manipulation Theory - Core Concepts from PDF Guide
 * 
 * This file contains all essential theoretical concepts, formulas, and patterns
 * from the Bit-Manipulation-Java-Guide.pdf implemented in Java with examples.
 * 
 * ESSENTIAL FOR INTERVIEWS: Master these concepts before attempting problems!
 */
public class BitManipulationTheory {
    
    /**
     * ==========================================
     * 1. FUNDAMENTAL BITWISE OPERATORS
     * ==========================================
     */
    
    /**
     * AND (&) - Returns 1 only if BOTH bits are 1
     * Truth Table: 0&0=0, 0&1=0, 1&0=0, 1&1=1
     * Usage: Masking, clearing bits, checking if bit is set
     */
    public static void demonstrateAND() {
        System.out.println("=== AND OPERATOR (&) ===");
        int a = 12; // 1100
        int b = 10; // 1010
        System.out.printf("%d & %d = %d (binary: %s)%n", a, b, a & b, Integer.toBinaryString(a & b));
        
        // Common uses:
        System.out.println("Check if even: " + ((a & 1) == 0)); // Check last bit
        System.out.println("Extract lower 4 bits: " + (a & 0xF)); // Mask with 0000...1111
        System.out.println();
    }
    
    /**
     * OR (|) - Returns 1 if AT LEAST ONE bit is 1
     * Truth Table: 0|0=0, 0|1=1, 1|0=1, 1|1=1
     * Usage: Setting bits, combining flags
     */
    public static void demonstrateOR() {
        System.out.println("=== OR OPERATOR (|) ===");
        int a = 12; // 1100
        int b = 10; // 1010
        System.out.printf("%d | %d = %d (binary: %s)%n", a, b, a | b, Integer.toBinaryString(a | b));
        
        // Common uses:
        int num = 0;
        num = num | (1 << 3); // Set 3rd bit
        System.out.println("After setting bit 3: " + Integer.toBinaryString(num));
        System.out.println();
    }
    
    /**
     * XOR (^) - Returns 1 if bits are DIFFERENT
     * Truth Table: 0^0=0, 0^1=1, 1^0=1, 1^1=0
     * MOST IMPORTANT for interviews!
     */
    public static void demonstrateXOR() {
        System.out.println("=== XOR OPERATOR (^) - INTERVIEW CRITICAL ===");
        int a = 12; // 1100
        int b = 10; // 1010
        System.out.printf("%d ^ %d = %d (binary: %s)%n", a, b, a ^ b, Integer.toBinaryString(a ^ b));
        
        // XOR Properties (MEMORIZE THESE!):
        System.out.println("XOR Properties:");
        System.out.println("1. Commutative: a ^ b = b ^ a");
        System.out.println("2. Associative: (a ^ b) ^ c = a ^ (b ^ c)");
        System.out.println("3. Self-inverse: a ^ a = 0");
        System.out.println("4. Identity: a ^ 0 = a");
        System.out.println("5. Twice application: (a ^ b) ^ b = a");
        
        // Applications:
        System.out.println("\nXOR Applications:");
        System.out.println("Find unique number: XOR all elements");
        System.out.println("Swap without temp: a ^= b; b ^= a; a ^= b;");
        System.out.println("Toggle bits: num ^ mask");
        System.out.println();
    }
    
    /**
     * NOT (~) - Inverts all bits (one's complement)
     * IMPORTANT: ~n = -(n+1) in two's complement
     */
    public static void demonstrateNOT() {
        System.out.println("=== NOT OPERATOR (~) ===");
        int a = 5; // 00000101
        System.out.printf("~%d = %d%n", a, ~a);
        System.out.println("Formula: ~n = -(n+1)");
        System.out.println("Verify: ~5 = -(5+1) = -6");
        System.out.println();
    }
    
    /**
     * ==========================================
     * 2. SHIFT OPERATORS
     * ==========================================
     */
    
    /**
     * Left Shift (<<) - Multiplies by 2^n
     * Each left shift doubles the number
     */
    public static void demonstrateLeftShift() {
        System.out.println("=== LEFT SHIFT (<<) ===");
        int a = 5; // 101
        System.out.printf("5 << 1 = %d (5 * 2^1 = %d)%n", a << 1, 5 * 2);
        System.out.printf("5 << 2 = %d (5 * 2^2 = %d)%n", a << 2, 5 * 4);
        System.out.printf("5 << 3 = %d (5 * 2^3 = %d)%n", a << 3, 5 * 8);
        
        System.out.println("Common uses:");
        System.out.println("- Fast multiplication by powers of 2");
        System.out.println("- Create bit masks: (1 << n) - 1");
        System.out.println("- Set bit at position: 1 << position");
        System.out.println();
    }
    
    /**
     * Right Shift (>>) - Divides by 2^n (with sign extension)
     * Arithmetic shift: preserves sign bit
     */
    public static void demonstrateRightShift() {
        System.out.println("=== RIGHT SHIFT (>>) ===");
        int pos = 20;
        int neg = -20;
        
        System.out.printf("20 >> 1 = %d (20 / 2 = %d)%n", pos >> 1, 20 / 2);
        System.out.printf("20 >> 2 = %d (20 / 4 = %d)%n", pos >> 2, 20 / 4);
        
        System.out.printf("-20 >> 1 = %d (-20 / 2 = %d, rounded toward negative infinity)%n", neg >> 1, -20 / 2);
        System.out.printf("-20 >> 2 = %d (-20 / 4 = %d)%n", neg >> 2, -20 / 4);
        System.out.println();
    }
    
    /**
     * Unsigned Right Shift (>>>) - Logical shift with zero fill
     * No sign extension: always fills with 0s
     */
    public static void demonstrateUnsignedRightShift() {
        System.out.println("=== UNSIGNED RIGHT SHIFT (>>>) ===");
        int pos = 20;
        int neg = -20;
        
        System.out.printf("20 >>> 2 = %d (same as >> for positive)%n", pos >>> 2);
        System.out.printf("-20 >> 2 = %d (sign extended)%n", neg >> 2);
        System.out.printf("-20 >>> 2 = %d (zero filled)%n", neg >>> 2);
        
        System.out.println("Use cases:");
        System.out.println("- Safe bit extraction without sign extension");
        System.out.println("- Treating negative numbers as unsigned");
        System.out.println("- Binary search: mid = (low + high) >>> 1");
        System.out.println();
    }
    
    /**
     * ==========================================
     * 3. ESSENTIAL BIT MANIPULATION TRICKS
     * ==========================================
     */
    
    /**
     * Check if number is power of 2
     * FORMULA: n > 0 && (n & (n-1)) == 0
     */
    public static boolean isPowerOfTwo(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Count set bits (Population count)
     * Brian Kernighan's Algorithm: O(number of set bits)
     */
    public static int countSetBits(int n) {
        int count = 0;
        while (n != 0) {
            n &= (n - 1); // Clear rightmost set bit
            count++;
        }
        return count;
    }
    
    /**
     * Isolate rightmost set bit
     * FORMULA: n & -n (using two's complement)
     */
    public static int isolateRightmostSetBit(int n) {
        return n & (-n);
    }
    
    /**
     * Clear rightmost set bit
     * FORMULA: n & (n-1)
     */
    public static int clearRightmostSetBit(int n) {
        return n & (n - 1);
    }
    
    /**
     * Check if bit at position i is set
     * METHOD 1: (n >> i) & 1
     * METHOD 2: (n & (1 << i)) != 0
     */
    public static boolean isBitSet(int n, int i) {
        return ((n >> i) & 1) == 1;
        // Alternative: return (n & (1 << i)) != 0;
    }
    
    /**
     * Set bit at position i
     */
    public static int setBit(int n, int i) {
        return n | (1 << i);
    }
    
    /**
     * Clear bit at position i
     */
    public static int clearBit(int n, int i) {
        return n & ~(1 << i);
    }
    
    /**
     * Toggle bit at position i
     */
    public static int toggleBit(int n, int i) {
        return n ^ (1 << i);
    }
    
    /**
     * ==========================================
     * 4. ADVANCED PATTERNS FROM PDF
     * ==========================================
     */
    
    /**
     * Swap two numbers without temporary variable
     * INTERVIEW CLASSIC
     */
    public static void swapWithoutTemp(int a, int b) {
        System.out.println("=== SWAP WITHOUT TEMP VARIABLE ===");
        System.out.printf("Before: a=%d, b=%d%n", a, b);
        
        a = a ^ b;
        b = a ^ b; // b = (a^b)^b = a
        a = a ^ b; // a = (a^b)^a = b
        
        System.out.printf("After: a=%d, b=%d%n", a, b);
        System.out.println();
    }
    
    /**
     * Find unique number in array where all others appear twice
     * MOST COMMON INTERVIEW QUESTION
     */
    public static int findUniqueNumber(int[] arr) {
        int result = 0;
        for (int num : arr) {
            result ^= num; // XOR all elements
        }
        return result; // All pairs cancel out, unique remains
    }
    
    /**
     * Generate all subsets using bit manipulation
     * Each bit represents inclusion/exclusion of element
     */
    public static void generateSubsets(int[] arr) {
        System.out.println("=== GENERATE ALL SUBSETS ===");
        int n = arr.length;
        int totalSubsets = 1 << n; // 2^n subsets
        
        for (int mask = 0; mask < totalSubsets; mask++) {
            System.out.print("Subset: {");
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    System.out.print(arr[i] + " ");
                }
            }
            System.out.printf("} (mask: %s)%n", Integer.toBinaryString(mask));
        }
        System.out.println();
    }
    
    /**
     * Case conversion tricks using bit manipulation
     * 'A' = 65 = 01000001, 'a' = 97 = 01100001
     * Difference is bit 5 (32 = space character)
     */
    public static void demonstrateCaseConversion() {
        System.out.println("=== CASE CONVERSION TRICKS ===");
        
        char upper = 'A';
        char lower = 'a';
        
        // Upper to lower: ch | ' ' (set bit 5)
        char toLower = (char)(upper | ' ');
        System.out.printf("'%c' to lower: '%c'%n", upper, toLower);
        
        // Lower to upper: ch & '_' (clear bit 5)
        char toUpper = (char)(lower & '_');
        System.out.printf("'%c' to upper: '%c'%n", lower, toUpper);
        
        // Toggle case: ch ^ ' ' (flip bit 5)
        char toggled = (char)(upper ^ ' ');
        System.out.printf("Toggle '%c': '%c'%n", upper, toggled);
        System.out.println();
    }
    
    /**
     * ==========================================
     * 5. JAVA-SPECIFIC IMPORTANT FACTS
     * ==========================================
     */
    public static void demonstrateJavaSpecificFacts() {
        System.out.println("=== JAVA-SPECIFIC BIT MANIPULATION FACTS ===");
        
        // 1. Shift overflow
        System.out.println("1. Shift overflow:");
        System.out.printf("1 << 31 = %d (Integer.MIN_VALUE!)%n", 1 << 31);
        System.out.printf("1L << 31 = %d (use long to avoid overflow)%n", 1L << 31);
        
        // 2. Shift wrapping
        System.out.println("\n2. Shift distance wrapping:");
        System.out.printf("1 << 32 = %d (same as 1 << 0 due to wrapping)%n", 1 << 32);
        System.out.printf("1L << 64 = %d (long also wraps)%n", 1L << 64);
        
        // 3. Sign extension
        System.out.println("\n3. Sign extension with >>:");
        int neg = -8;
        System.out.printf("-8 >> 2 = %d (sign extended)%n", neg >> 2);
        System.out.printf("-8 >>> 2 = %d (zero filled)%n", neg >>> 2);
        
        // 4. Two's complement representation
        System.out.println("\n4. Two's complement:");
        System.out.println("-1 in binary: " + Integer.toBinaryString(-1));
        System.out.println("Integer.MIN_VALUE: " + Integer.MIN_VALUE);
        System.out.println("Integer.MAX_VALUE: " + Integer.MAX_VALUE);
        
        System.out.println();
    }
    
    /**
     * ==========================================
     * 6. DEMONSTRATION AND TESTING
     * ==========================================
     */
    public static void demonstrateAllConcepts() {
        System.out.println("BIT MANIPULATION THEORY - COMPLETE DEMONSTRATION");
        System.out.println("============================================================");
        
        demonstrateAND();
        demonstrateOR();
        demonstrateXOR();
        demonstrateNOT();
        
        demonstrateLeftShift();
        demonstrateRightShift();
        demonstrateUnsignedRightShift();
        
        // Essential tricks
        System.out.println("=== ESSENTIAL TRICKS ===");
        System.out.println("isPowerOfTwo(8): " + isPowerOfTwo(8));
        System.out.println("isPowerOfTwo(10): " + isPowerOfTwo(10));
        System.out.println("countSetBits(15): " + countSetBits(15)); // 1111 = 4 bits
        System.out.println("isolateRightmostSetBit(12): " + isolateRightmostSetBit(12)); // 1100 -> 0100 = 4
        System.out.println("clearRightmostSetBit(12): " + clearRightmostSetBit(12)); // 1100 -> 1000 = 8
        System.out.println();
        
        swapWithoutTemp(5, 7);
        
        // Unique number
        int[] arr = {4, 1, 2, 1, 2, 5, 4};
        System.out.println("Find unique in [4,1,2,1,2,5,4]: " + findUniqueNumber(arr));
        System.out.println();
        
        generateSubsets(new int[]{1, 2, 3});
        demonstrateCaseConversion();
        demonstrateJavaSpecificFacts();
    }
    
    /**
     * Main method for comprehensive demonstration
     */
    public static void main(String[] args) {
        demonstrateAllConcepts();
        
        System.out.println("KEY TAKEAWAYS FOR INTERVIEWS:");
        System.out.println("1. Master XOR properties: a^a=0, a^0=a");
        System.out.println("2. Power of 2 check: n>0 && (n&(n-1))==0");
        System.out.println("3. Count bits: Brian Kernighan's n&(n-1)");
        System.out.println("4. Isolate rightmost bit: n&(-n)");
        System.out.println("5. Generate subsets: use bitmasks 0 to 2^n-1");
        System.out.println("6. Remember Java quirks: shift overflow, sign extension");
    }
}
