package dsa.techniques.bitmanipulation.hard;
/**
 * Binary Arithmetic Without Using Standard Operators
 * Difficulty: Hard
 * 
 * Implement addition, subtraction, multiplication, and division using only bit operations.
 * This demonstrates advanced bit manipulation techniques and is commonly asked in
 * system design and low-level programming interviews.
 * 
 * Key Concepts:
 * - Addition: Use XOR for sum, AND for carry
 * - Subtraction: a - b = a + (~b + 1) = a + (-b)
 * - Multiplication: Repeated addition with bit shifts
 * - Division: Repeated subtraction with bit shifts
 */
public class BinaryArithmetic_NoOperators {
    
    /**
     * Addition using bit manipulation
     * Algorithm: XOR gives sum without carry, AND gives carry
     * Time: O(log max(a,b)), Space: O(1)
     */
    public int add(int a, int b) {
        while (b != 0) {
            int carry = a & b;    // Find carry bits
            a = a ^ b;            // Sum without carry
            b = carry << 1;       // Shift carry to left
        }
        return a;
    }
    
    /**
     * Subtraction using bit manipulation
     * a - b = a + (-b) = a + (~b + 1)
     * Time: O(log max(a,b)), Space: O(1)
     */
    public int subtract(int a, int b) {
        return add(a, add(~b, 1)); // Two's complement negation
    }
    
    /**
     * Alternative subtraction using direct bit manipulation
     */
    public int subtractDirect(int a, int b) {
        while (b != 0) {
            int borrow = (~a) & b;   // Find borrow bits
            a = a ^ b;               // Difference without borrow
            b = borrow << 1;         // Shift borrow to left
        }
        return a;
    }
    
    /**
     * Multiplication using bit manipulation
     * Algorithm: Shift and add based on bits of multiplier
     * Time: O(log b), Space: O(1)
     */
    public int multiply(int a, int b) {
        // Handle negative numbers
        boolean negative = (a < 0) ^ (b < 0);
        a = Math.abs(a);
        b = Math.abs(b);
        
        int result = 0;
        while (b != 0) {
            if ((b & 1) != 0) {        // If current bit is set
                result = add(result, a); // Add current value of a
            }
            a <<= 1;                   // Double a for next bit
            b >>>= 1;                  // Move to next bit
        }
        
        return negative ? add(~result, 1) : result; // Apply sign
    }
    
    /**
     * Optimized multiplication using Booth's algorithm
     */
    public int multiplyBooth(int a, int b) {
        boolean negative = (a < 0) ^ (b < 0);
        a = Math.abs(a);
        b = Math.abs(b);
        
        int result = 0;
        
        while (b > 0) {
            // If b is odd, add a to result
            if ((b & 1) == 1) {
                result = add(result, a);
            }
            
            // Shift a left and b right
            a <<= 1;
            b >>>= 1;
        }
        
        return negative ? add(~result, 1) : result;
    }
    
    /**
     * Division using bit manipulation
     * Algorithm: Long division using bit shifts
     * Time: O(log a), Space: O(1)
     */
    public int divide(int dividend, int divisor) {
        if (divisor == 0) {
            throw new ArithmeticException("Division by zero");
        }
        
        // Handle overflow case
        if (dividend == Integer.MIN_VALUE && divisor == -1) {
            return Integer.MAX_VALUE;
        }
        
        // Determine sign of result
        boolean negative = (dividend < 0) ^ (divisor < 0);
        
        // Work with positive numbers
        long absDividend = Math.abs((long) dividend);
        long absDivisor = Math.abs((long) divisor);
        
        long result = 0;
        
        while (absDividend >= absDivisor) {
            long temp = absDivisor;
            long multiple = 1;
            
            // Find largest multiple of divisor <= dividend
            while (absDividend >= (temp << 1)) {
                temp <<= 1;
                multiple <<= 1;
            }
            
            absDividend = subtract((int)absDividend, (int)temp);
            result = add((int)result, (int)multiple);
        }
        
        result = negative ? add(~(int)result, 1) : result;
        
        // Handle overflow
        if (result > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (result < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        
        return (int) result;
    }
    
    /**
     * Modulo operation using bit manipulation
     */
    public int modulo(int dividend, int divisor) {
        int quotient = divide(dividend, divisor);
        return subtract(dividend, multiply(quotient, divisor));
    }
    
    /**
     * Power operation using bit manipulation (exponentiation by squaring)
     * Time: O(log exponent), Space: O(1)
     */
    public int power(int base, int exponent) {
        if (exponent == 0) return 1;
        if (exponent < 0) {
            throw new IllegalArgumentException("Negative exponent not supported for integers");
        }
        
        int result = 1;
        while (exponent > 0) {
            if ((exponent & 1) != 0) {
                result = multiply(result, base);
            }
            base = multiply(base, base);
            exponent >>>= 1;
        }
        
        return result;
    }
    
    /**
     * Square root using bit manipulation (Newton's method with bit operations)
     */
    public int sqrt(int x) {
        if (x < 0) {
            throw new IllegalArgumentException("Square root of negative number");
        }
        if (x < 2) return x;
        
        int result = 0;
        
        // Find the highest bit position
        int bit = 1 << 30; // Start from second highest bit
        while (bit > x) {
            bit >>>= 2;
        }
        
        while (bit != 0) {
            int temp = add(result, bit);
            if (x >= multiply(temp, temp)) {
                result = temp;
            }
            bit >>>= 2;
        }
        
        return result;
    }
    
    /**
     * Absolute value using bit manipulation
     */
    public int abs(int x) {
        int mask = x >> 31;  // Arithmetic right shift creates mask
        return (x ^ mask) - mask;  // XOR with mask and subtract mask
    }
    
    /**
     * Maximum of two numbers without conditional statements
     */
    public int max(int a, int b) {
        int diff = subtract(a, b);
        int sign = (diff >> 31) & 1;  // 1 if a < b, 0 if a >= b
        return subtract(a, multiply(sign, diff));
    }
    
    /**
     * Minimum of two numbers without conditional statements
     */
    public int min(int a, int b) {
        int diff = subtract(a, b);
        int sign = (diff >> 31) & 1;  // 1 if a < b, 0 if a >= b
        return subtract(b, multiply(subtract(1, sign), diff));
    }
    
    /**
     * Sign function (-1, 0, or 1)
     */
    public int sign(int x) {
        return (x >> 31) | ((-x) >>> 31);
    }
    
    /**
     * Check if two numbers have opposite signs
     */
    public boolean oppositeSign(int a, int b) {
        return (a ^ b) < 0;
    }
    
    /**
     * Utility: Demonstrate step-by-step addition
     */
    public void demonstrateAddition(int a, int b) {
        System.out.printf("Adding %d + %d step by step:%n", a, b);
        System.out.printf("a = %d (binary: %s)%n", a, Integer.toBinaryString(a));
        System.out.printf("b = %d (binary: %s)%n", b, Integer.toBinaryString(b));
        
        int step = 1;
        while (b != 0) {
            int carry = a & b;
            int sum = a ^ b;
            
            System.out.printf("Step %d:%n", step);
            System.out.printf("  Sum without carry: %s%n", Integer.toBinaryString(sum));
            System.out.printf("  Carry: %s%n", Integer.toBinaryString(carry));
            System.out.printf("  Carry << 1: %s%n", Integer.toBinaryString(carry << 1));
            
            a = sum;
            b = carry << 1;
            step++;
        }
        
        System.out.printf("Final result: %d%n%n", a);
    }
    
    /**
     * Comprehensive test method
     */
    public static void main(String[] args) {
        BinaryArithmetic_NoOperators calc = new BinaryArithmetic_NoOperators();
        
        // Test addition
        System.out.println("=== ADDITION TESTS ===");
        int[][] addTests = {{5, 7}, {-3, 8}, {-5, -7}, {0, 5}, {15, -10}};
        for (int[] test : addTests) {
            int result = calc.add(test[0], test[1]);
            int expected = test[0] + test[1];
            System.out.printf("%d + %d = %d (expected: %d) %s%n", 
                test[0], test[1], result, expected, result == expected ? "✓" : "✗");
        }
        
        // Demonstrate addition steps
        System.out.println();
        calc.demonstrateAddition(5, 7);
        
        // Test subtraction
        System.out.println("=== SUBTRACTION TESTS ===");
        int[][] subTests = {{10, 3}, {5, 8}, {-3, -7}, {0, 5}, {-10, 5}};
        for (int[] test : subTests) {
            int result = calc.subtract(test[0], test[1]);
            int expected = test[0] - test[1];
            System.out.printf("%d - %d = %d (expected: %d) %s%n", 
                test[0], test[1], result, expected, result == expected ? "✓" : "✗");
        }
        
        // Test multiplication
        System.out.println("\n=== MULTIPLICATION TESTS ===");
        int[][] mulTests = {{6, 7}, {-3, 4}, {-5, -6}, {0, 5}, {12, -3}};
        for (int[] test : mulTests) {
            int result = calc.multiply(test[0], test[1]);
            int expected = test[0] * test[1];
            System.out.printf("%d × %d = %d (expected: %d) %s%n", 
                test[0], test[1], result, expected, result == expected ? "✓" : "✗");
        }
        
        // Test division
        System.out.println("\n=== DIVISION TESTS ===");
        int[][] divTests = {{20, 4}, {17, 3}, {-20, 4}, {20, -3}, {-15, -3}};
        for (int[] test : divTests) {
            try {
                int result = calc.divide(test[0], test[1]);
                int expected = test[0] / test[1];
                System.out.printf("%d ÷ %d = %d (expected: %d) %s%n", 
                    test[0], test[1], result, expected, result == expected ? "✓" : "✗");
            } catch (Exception e) {
                System.out.printf("%d ÷ %d = ERROR: %s%n", test[0], test[1], e.getMessage());
            }
        }
        
        // Test power
        System.out.println("\n=== POWER TESTS ===");
        int[][] powTests = {{2, 3}, {5, 2}, {3, 4}, {7, 0}, {-2, 3}};
        for (int[] test : powTests) {
            try {
                int result = calc.power(test[0], test[1]);
                int expected = (int) Math.pow(test[0], test[1]);
                System.out.printf("%d^%d = %d (expected: %d) %s%n", 
                    test[0], test[1], result, expected, result == expected ? "✓" : "✗");
            } catch (Exception e) {
                System.out.printf("%d^%d = ERROR: %s%n", test[0], test[1], e.getMessage());
            }
        }
        
        // Test utility functions
        System.out.println("\n=== UTILITY FUNCTION TESTS ===");
        int[] utilTests = {-5, 0, 7, -12, 15};
        for (int x : utilTests) {
            System.out.printf("abs(%d) = %d, sign(%d) = %d%n", 
                x, calc.abs(x), x, calc.sign(x));
        }
        
        // Test max/min
        System.out.println("\n=== MAX/MIN TESTS ===");
        int[][] maxMinTests = {{5, 7}, {-3, 2}, {0, -5}, {10, 10}};
        for (int[] test : maxMinTests) {
            int maxResult = calc.max(test[0], test[1]);
            int minResult = calc.min(test[0], test[1]);
            System.out.printf("max(%d, %d) = %d, min(%d, %d) = %d%n", 
                test[0], test[1], maxResult, test[0], test[1], minResult);
        }
        
        // Performance test
        System.out.println("\n=== PERFORMANCE TEST ===");
        long start = System.nanoTime();
        for (int i = 0; i < 100000; i++) {
            calc.add(i, i + 1);
            calc.multiply(i % 100, (i + 1) % 100);
        }
        long end = System.nanoTime();
        System.out.printf("100,000 operations completed in %.2f ms%n", (end - start) / 1_000_000.0);
    }
}

