package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 231: Power of Two
 * Difficulty: Easy
 * 
 * Given an integer n, return true if it is a power of two. Otherwise, return false.
 * An integer n is a power of two, if there exists an integer x such that n == 2^x.
 * 
 * Example 1:
 * Input: n = 1
 * Output: true
 * Explanation: 2^0 = 1
 * 
 * Example 2:
 * Input: n = 16
 * Output: true
 * Explanation: 2^4 = 16
 * 
 * Example 3:
 * Input: n = 3
 * Output: false
 * 
 * Constraints: -2^31 <= n <= 2^31 - 1
 */
public class PowerOfTwo_Leetcode231 {
    
    /**
     * Approach 1: Iterative Division
     * Keep dividing by 2 until we get 1 or an odd number > 1
     * Time: O(log n), Space: O(1)
     */
    public boolean isPowerOfTwo1(int n) {
        if (n <= 0) return false;
        
        while (n % 2 == 0) {
            n /= 2;
        }
        return n == 1;
    }
    
    /**
     * Approach 2: Count Set Bits
     * Power of 2 has exactly one bit set
     * Time: O(log n), Space: O(1)
     */
    public boolean isPowerOfTwo2(int n) {
        if (n <= 0) return false;
        
        int count = 0;
        while (n > 0) {
            if ((n & 1) == 1) count++;
            if (count > 1) return false;  // Early termination
            n >>= 1;
        }
        return count == 1;
    }
    
    /**
     * Approach 3: Bit Manipulation Trick (Optimal)
     * For power of 2: n & (n-1) == 0
     * Time: O(1), Space: O(1)
     */
    public boolean isPowerOfTwo3(int n) {
        return n > 0 && (n & (n - 1)) == 0;
    }
    
    /**
     * Approach 4: Using Rightmost Set Bit
     * For power of 2: n & (-n) == n (isolates rightmost set bit)
     * Time: O(1), Space: O(1)
     */
    public boolean isPowerOfTwo4(int n) {
        return n > 0 && (n & (-n)) == n;
    }
    
    /**
     * Approach 5: Mathematical Approach
     * Use logarithms to check if log2(n) is an integer
     * Time: O(1), Space: O(1)
     * Note: May have precision issues for very large numbers
     */
    public boolean isPowerOfTwo5(int n) {
        if (n <= 0) return false;
        double log = Math.log(n) / Math.log(2);
        return log == (int) log;
    }
    
    /**
     * Approach 6: Using Built-in Bit Count
     * Time: O(1), Space: O(1)
     */
    public boolean isPowerOfTwo6(int n) {
        return n > 0 && Integer.bitCount(n) == 1;
    }
    
    /**
     * Follow-up: Check if number is power of 4
     * Power of 4 numbers have set bit only at even positions (0, 2, 4, ...)
     * Mask: 0x55555555 = 01010101010101010101010101010101
     */
    public boolean isPowerOfFour(int n) {
        return n > 0 && (n & (n - 1)) == 0 && (n & 0x55555555) == n;
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        PowerOfTwo_Leetcode231 solution = new PowerOfTwo_Leetcode231();
        
        // Test cases
        int[] testCases = {1, 2, 3, 4, 8, 16, 17, 32, 64, 0, -1, -16, 1024};
        
        System.out.println("Testing Power of Two:");
        for (int n : testCases) {
            System.out.printf("n=%d: ", n);
            System.out.printf("Div=%b, Count=%b, Trick=%b, RightBit=%b, Math=%b, BitCount=%b%n",
                solution.isPowerOfTwo1(n), solution.isPowerOfTwo2(n), 
                solution.isPowerOfTwo3(n), solution.isPowerOfTwo4(n),
                solution.isPowerOfTwo5(n), solution.isPowerOfTwo6(n));
        }
        
        System.out.println("\nTesting Power of Four:");
        for (int n : new int[]{1, 4, 8, 16, 64, 256}) {
            System.out.printf("n=%d: isPowerOfFour=%b%n", n, solution.isPowerOfFour(n));
        }
    }
}

