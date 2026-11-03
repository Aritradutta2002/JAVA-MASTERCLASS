package dsa.techniques.bitmanipulation.basics;
/**
 * Power of Two Check - Multiple Approaches
 * 
 * A number is power of 2 if it has exactly one bit set
 * This is a very common interview question and pattern
 */
public class PowerOfTwo {
    
    /**
     * Approach 1: Using bit counting
     * Count set bits, should be exactly 1
     * Time: O(log n), Space: O(1)
     */
    public static boolean isPowerOfTwoV1(int n) {
        if (n <= 0) return false;
        
        int count = 0;
        while (n > 0) {
            if ((n & 1) == 1) count++;
            n >>= 1;
        }
        return count == 1;
    }
    
    /**
     * Approach 2: Using n & (n-1) trick
     * For power of 2: n & (n-1) == 0
     * Time: O(1), Space: O(1)
     */
    public static boolean isPowerOfTwoV2(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Approach 3: Using rightmost set bit
     * For power of 2: n & (-n) == n
     * Time: O(1), Space: O(1)
     */
    public static boolean isPowerOfTwoV3(int n) {
        return n > 0 && (n & (-n)) == n;
    }
    
    /**
     * Find the next power of 2 greater than or equal to n
     * Time: O(log log n), Space: O(1)
     */
    public static int nextPowerOfTwo(int n) {
        if (n <= 0) return 1;
        if (isPowerOfTwoV2(n)) return n;
        
        // Find position of MSB
        int pos = 0;
        while (n > 0) {
            n >>= 1;
            pos++;
        }
        return 1 << pos;
    }
    
    /**
     * Find the largest power of 2 less than or equal to n
     * Time: O(log n), Space: O(1)
     */
    public static int prevPowerOfTwo(int n) {
        if (n <= 0) return 0;
        
        // Keep removing the rightmost set bit until only one bit remains
        while ((n & (n - 1)) != 0) {
            n = n & (n - 1);
        }
        return n;
    }
    
    public static void main(String[] args) {
        int[] testCases = {1, 2, 3, 4, 8, 16, 15, 32, 0, -1};
        
        System.out.println("Testing Power of Two:");
        for (int num : testCases) {
            System.out.printf("n=%d: V1=%b, V2=%b, V3=%b%n", 
                num, isPowerOfTwoV1(num), isPowerOfTwoV2(num), isPowerOfTwoV3(num));
        }
        
        System.out.println("\nNext/Previous Power of Two:");
        for (int num : new int[]{5, 7, 16, 31}) {
            System.out.printf("n=%d: next=%d, prev=%d%n", 
                num, nextPowerOfTwo(num), prevPowerOfTwo(num));
        }
    }
}

