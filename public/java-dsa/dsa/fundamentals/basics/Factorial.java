package dsa.fundamentals.basics;

import java.math.BigInteger;

/**
 * Factorial - Product of All Positive Integers Up To n
 *
 * Problem: Given a non-negative integer n, compute n!
 * Interview Angles:
 * - Tests recursion fundamentals and stack awareness
 * - Explores iterative optimization and tail recursion
 * - Opens the door to BigInteger discussions and overflow handling
 */
public class Factorial {

    // Approach 1: Iterative multiplication
    // Time: O(n), Space: O(1)
    public static long factorialIterative(int n) {
        validateInput(n);
        long result = 1L;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }

    // Approach 2: Classic recursion
    // Time: O(n), Space: O(n) due to call stack
    public static long factorialRecursive(int n) {
        validateInput(n);
        if (n <= 1) {
            return 1L;
        }
        return n * factorialRecursive(n - 1);
    }

    // Approach 3: Tail recursion (optimized in languages with tail-call optimization)
    // Time: O(n), Space: O(n) in Java (no tail-call optimization)
    public static long factorialTailRecursive(int n) {
        validateInput(n);
        return factorialTailHelper(n, 1L);
    }

    private static long factorialTailHelper(int n, long accumulator) {
        if (n <= 1) {
            return accumulator;
        }
        return factorialTailHelper(n - 1, accumulator * n);
    }

    // Approach 4: BigInteger for large results (up to hundreds/thousands safely)
    // Time: O(n * log(result)) due to BigInteger multiplications, Space: O(1) auxiliary
    public static BigInteger factorialBigInteger(int n) {
        validateInput(n);
        BigInteger result = BigInteger.ONE;
        for (int i = 2; i <= n; i++) {
            result = result.multiply(BigInteger.valueOf(i));
        }
        return result;
    }

    private static void validateInput(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Factorial is undefined for negative numbers");
        }
    }

    public static void main(String[] args) {
        int sample = 5;
        int largerSample = 20;

        System.out.println("=== Factorial Iterative ===");
        System.out.println(sample + "! = " + factorialIterative(sample));

        System.out.println("\n=== Factorial Recursive ===");
        System.out.println(sample + "! = " + factorialRecursive(sample));

        System.out.println("\n=== Factorial Tail-Recursive ===");
        System.out.println(sample + "! = " + factorialTailRecursive(sample));

        System.out.println("\n=== Factorial BigInteger ===");
        System.out.println(largerSample + "! = " + factorialBigInteger(largerSample));

        // Overflow awareness: factorialIterative/Recursive overflow beyond 20!
        // Encourage discussing limits during interviews.
    }
}


