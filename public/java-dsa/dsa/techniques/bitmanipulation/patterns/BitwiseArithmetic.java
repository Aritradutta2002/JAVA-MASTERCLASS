package dsa.techniques.bitmanipulation.patterns;
/**
 * Bitwise Arithmetic Operations - Advanced Interview Patterns
 * 
 * Implementing arithmetic operations using only bitwise operations.
 * Essential for understanding low-level optimizations and certain interview problems.
 */
public class BitwiseArithmetic {
    
    /**
     * Pattern 1: Addition using XOR and AND
     * LeetCode 371: Sum of Two Integers
     */
    public int addWithoutPlus(int a, int b) {
        while (b != 0) {
            int carry = a & b;  // Calculate carry
            a = a ^ b;          // Sum without carry
            b = carry << 1;     // Shift carry left
        }
        return a;
    }
    
    /**
     * Recursive version of addition
     */
    public int addRecursive(int a, int b) {
        if (b == 0) return a;
        return addRecursive(a ^ b, (a & b) << 1);
    }
    
    /**
     * Pattern 2: Subtraction using Addition
     * a - b = a + (-b) = a + (~b + 1)
     */
    public int subtractWithoutMinus(int a, int b) {
        return addWithoutPlus(a, addWithoutPlus(~b, 1));
    }
    
    /**
     * Pattern 3: Multiplication using Shifts and Addition
     * Russian Peasant Algorithm
     */
    public int multiplyWithoutOperator(int a, int b) {
        int result = 0;
        boolean isNegative = (a < 0) ^ (b < 0);
        
        // Work with absolute values
        a = Math.abs(a);
        b = Math.abs(b);
        
        while (b > 0) {
            if ((b & 1) == 1) {  // If b is odd
                result = addWithoutPlus(result, a);
            }
            a <<= 1;  // Double a
            b >>= 1;  // Halve b
        }
        
        return isNegative ? subtractWithoutMinus(0, result) : result;
    }
    
    /**
     * Pattern 4: Division using Binary Search
     * LeetCode 29: Divide Two Integers
     */
    public int divideWithoutOperator(int dividend, int divisor) {
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return Integer.MAX_VALUE;  // Overflow case
        }
        
        boolean isNegative = (dividend < 0) ^ (divisor < 0);
        
        long absDividend = Math.abs((long) dividend);
        long absDivisor = Math.abs((long) divisor);
        
        long result = 0;
        
        while (absDividend >= absDivisor) {
            long tempDivisor = absDivisor;
            long multiple = 1;
            
            // Find the largest multiple
            while (absDividend >= (tempDivisor << 1)) {
                tempDivisor <<= 1;
                multiple <<= 1;
            }
            
            absDividend -= tempDivisor;
            result += multiple;
        }
        
        return isNegative ? (int) -result : (int) result;
    }
    
    /**
     * Pattern 5: Fast Exponentiation using Bits
     * Calculate a^b using binary representation of b
     */
    public long fastPower(long base, long exp) {
        long result = 1;
        
        while (exp > 0) {
            if ((exp & 1) == 1) {  // If exp is odd
                result *= base;
            }
            base *= base;  // Square the base
            exp >>= 1;     // Divide exp by 2
        }
        
        return result;
    }
    
    /**
     * Pattern 6: Modular Exponentiation
     * Calculate (base^exp) % mod efficiently
     */
    public long modularPower(long base, long exp, long mod) {
        long result = 1;
        base %= mod;
        
        while (exp > 0) {
            if ((exp & 1) == 1) {
                result = (result * base) % mod;
            }
            base = (base * base) % mod;
            exp >>= 1;
        }
        
        return result;
    }
    
    /**
     * Pattern 7: Square Root using Binary Search
     * LeetCode 69: Sqrt(x)
     */
    public int mySqrt(int x) {
        if (x < 2) return x;
        
        long left = 1, right = x / 2;
        
        while (left <= right) {
            long mid = left + (right - left) / 2;
            long square = mid * mid;
            
            if (square == x) return (int) mid;
            else if (square < x) left = mid + 1;
            else right = mid - 1;
        }
        
        return (int) right;
    }
    
    /**
     * Pattern 8: Check if Perfect Square using Bit Manipulation
     */
    public boolean isPerfectSquare(int num) {
        if (num < 1) return false;
        
        long left = 1, right = num;
        
        while (left <= right) {
            long mid = left + (right - left) / 2;
            long square = mid * mid;
            
            if (square == num) return true;
            else if (square < num) left = mid + 1;
            else right = mid - 1;
        }
        
        return false;
    }
    
    /**
     * Pattern 9: Absolute Value without Conditional
     */
    public int absWithoutConditional(int x) {
        int mask = x >> 31;  // All 1s if negative, all 0s if positive
        return (x + mask) ^ mask;
    }
    
    /**
     * Pattern 10: Min/Max without Conditional
     */
    public int minWithoutConditional(int a, int b) {
        int diff = a - b;
        int sign = (diff >> 31) & 1;  // 1 if a < b, 0 otherwise
        return a - (sign * diff);
    }
    
    public int maxWithoutConditional(int a, int b) {
        int diff = a - b;
        int sign = (diff >> 31) & 1;  // 1 if a < b, 0 otherwise
        return b + (sign * diff);
    }
    
    /**
     * Pattern 11: Sign of Number
     */
    public int getSign(int x) {
        // Returns 1 for positive, -1 for negative, 0 for zero
        return (x >> 31) | ((~x + 1) >>> 31);
    }
    
    /**
     * Pattern 12: Fast Modulo by Power of 2
     */
    public int fastModuloPowerOf2(int x, int powerOf2) {
        // x % (2^n) = x & (2^n - 1)
        return x & (powerOf2 - 1);
    }
    
    /**
     * Pattern 13: Check if Two Numbers Have Opposite Signs
     */
    public boolean haveOppositeSigns(int a, int b) {
        return (a ^ b) < 0;
    }
    
    /**
     * Pattern 14: Average of Two Numbers without Overflow
     */
    public int averageWithoutOverflow(int a, int b) {
        return (a & b) + ((a ^ b) >> 1);
    }
    
    /**
     * Pattern 15: Ceiling Division using Bit Manipulation
     */
    public int ceilingDivision(int a, int b) {
        // Equivalent to Math.ceil(a / b) for positive numbers
        return (a + b - 1) / b;
    }
    
    /**
     * Using only bit operations
     */
    public int ceilingDivisionBitwise(int a, int b) {
        if (b == 0) throw new ArithmeticException("Division by zero");
        
        boolean isNegative = (a < 0) ^ (b < 0);
        a = Math.abs(a);
        b = Math.abs(b);
        
        int result = divideWithoutOperator(addWithoutPlus(a, subtractWithoutMinus(b, 1)), b);
        return isNegative ? subtractWithoutMinus(0, result) : result;
    }
    
    /**
     * Pattern 16: Round to Next Power of 2
     */
    public int nextPowerOf2(int n) {
        if (n <= 1) return 1;
        
        n--;
        n |= n >> 1;
        n |= n >> 2;
        n |= n >> 4;
        n |= n >> 8;
        n |= n >> 16;
        n++;
        
        return n;
    }
    
    /**
     * Pattern 17: Check if Addition Would Overflow
     */
    public boolean wouldAdditionOverflow(int a, int b) {
        if (a > 0 && b > 0) {
            return a > Integer.MAX_VALUE - b;
        }
        if (a < 0 && b < 0) {
            return a < Integer.MIN_VALUE - b;
        }
        return false;  // Different signs, no overflow
    }
    
    /**
     * Pattern 18: Bit Reversal for Fast Walsh-Hadamard Transform
     */
    public int reverseBits(int n, int bitLength) {
        int result = 0;
        for (int i = 0; i < bitLength; i++) {
            result = (result << 1) | (n & 1);
            n >>= 1;
        }
        return result;
    }
    
    /**
     * Utility method to demonstrate operations
     */
    public void demonstrateArithmetic(int a, int b) {
        System.out.println("=== Demonstrating Bitwise Arithmetic ===");
        System.out.println("a = " + a + ", b = " + b);
        System.out.println("Addition: " + a + " + " + b + " = " + addWithoutPlus(a, b));
        System.out.println("Subtraction: " + a + " - " + b + " = " + subtractWithoutMinus(a, b));
        System.out.println("Multiplication: " + a + " * " + b + " = " + multiplyWithoutOperator(a, b));
        if (b != 0) {
            System.out.println("Division: " + a + " / " + b + " = " + divideWithoutOperator(a, b));
        }
        System.out.println("Min: " + minWithoutConditional(a, b));
        System.out.println("Max: " + maxWithoutConditional(a, b));
        System.out.println("Average: " + averageWithoutOverflow(a, b));
        System.out.println("Opposite signs: " + haveOppositeSigns(a, b));
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        BitwiseArithmetic ba = new BitwiseArithmetic();
        
        // Test basic arithmetic operations
        System.out.println("=== Testing Basic Operations ===");
        ba.demonstrateArithmetic(15, 7);
        ba.demonstrateArithmetic(-10, 3);
        ba.demonstrateArithmetic(8, -4);
        
        // Test power operations
        System.out.println("\n=== Testing Power Operations ===");
        System.out.println("2^10 = " + ba.fastPower(2, 10));
        System.out.println("3^5 = " + ba.fastPower(3, 5));
        System.out.println("(2^10) % 1000 = " + ba.modularPower(2, 10, 1000));
        
        // Test square root
        System.out.println("\n=== Testing Square Root ===");
        int[] sqrtTests = {4, 8, 16, 25, 100};
        for (int num : sqrtTests) {
            System.out.println("sqrt(" + num + ") = " + ba.mySqrt(num));
            System.out.println(num + " is perfect square: " + ba.isPerfectSquare(num));
        }
        
        // Test utility functions
        System.out.println("\n=== Testing Utility Functions ===");
        int[] utilTests = {-5, 0, 7, -12};
        for (int num : utilTests) {
            System.out.println("abs(" + num + ") = " + ba.absWithoutConditional(num));
            System.out.println("sign(" + num + ") = " + ba.getSign(num));
        }
        
        // Test next power of 2
        System.out.println("\n=== Testing Next Power of 2 ===");
        int[] powerTests = {1, 3, 5, 8, 15, 16, 17};
        for (int num : powerTests) {
            System.out.println("Next power of 2 after " + num + ": " + ba.nextPowerOf2(num));
        }
        
        // Test overflow detection
        System.out.println("\n=== Testing Overflow Detection ===");
        System.out.println("MAX + 1 would overflow: " + 
            ba.wouldAdditionOverflow(Integer.MAX_VALUE, 1));
        System.out.println("MIN + (-1) would overflow: " + 
            ba.wouldAdditionOverflow(Integer.MIN_VALUE, -1));
        System.out.println("100 + 200 would overflow: " + 
            ba.wouldAdditionOverflow(100, 200));
    }
}

