package dsa.techniques.bitmanipulation.basics;
/**
 * Basic Bit Operations - Set, Clear, Toggle, Check
 * 
 * These are the fundamental building blocks for all bit manipulation problems
 * Master these operations before moving to complex problems
 */
public class BasicBitOperations {
    
    /**
     * Check if ith bit is set (1) or not
     * Time: O(1), Space: O(1)
     */
    public static boolean isBitSet(int num, int i) {
        return (num & (1 << i)) != 0;
    }
    
    /**
     * Set the ith bit to 1
     * Time: O(1), Space: O(1)
     */
    public static int setBit(int num, int i) {
        return num | (1 << i);
    }
    
    /**
     * Clear the ith bit (set to 0)
     * Time: O(1), Space: O(1)
     */
    public static int clearBit(int num, int i) {
        return num & ~(1 << i);
    }
    
    /**
     * Toggle the ith bit (0->1, 1->0)
     * Time: O(1), Space: O(1)
     */
    public static int toggleBit(int num, int i) {
        return num ^ (1 << i);
    }
    
    /**
     * Clear all bits from MSB to ith bit (inclusive)
     * Time: O(1), Space: O(1)
     */
    public static int clearBitsFromMSB(int num, int i) {
        int mask = (1 << i) - 1;
        return num & mask;
    }
    
    /**
     * Clear all bits from ith bit to LSB (inclusive)
     * Time: O(1), Space: O(1)
     */
    public static int clearBitsFromLSB(int num, int i) {
        int mask = ~((1 << (i + 1)) - 1);
        return num & mask;
    }
    
    /**
     * Update ith bit to given value (0 or 1)
     * Time: O(1), Space: O(1)
     */
    public static int updateBit(int num, int i, int value) {
        // Clear the ith bit first
        int cleared = num & ~(1 << i);
        // Set the ith bit to value
        return cleared | (value << i);
    }
    
    public static void main(String[] args) {
        int num = 12; // 1100 in binary
        System.out.println("Original number: " + num + " (binary: " + Integer.toBinaryString(num) + ")");
        
        // Test all operations
        System.out.println("Bit 2 is set: " + isBitSet(num, 2));
        System.out.println("Set bit 0: " + setBit(num, 0) + " (binary: " + Integer.toBinaryString(setBit(num, 0)) + ")");
        System.out.println("Clear bit 2: " + clearBit(num, 2) + " (binary: " + Integer.toBinaryString(clearBit(num, 2)) + ")");
        System.out.println("Toggle bit 1: " + toggleBit(num, 1) + " (binary: " + Integer.toBinaryString(toggleBit(num, 1)) + ")");
        System.out.println("Update bit 0 to 1: " + updateBit(num, 0, 1) + " (binary: " + Integer.toBinaryString(updateBit(num, 0, 1)) + ")");
    }
}

