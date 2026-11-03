package dsa.fundamentals.basics;

import java.util.HashMap;
import java.util.Map;

/**
 * Fibonacci - Sum of the Previous Two Numbers
 *
 * Problem: Compute the nth Fibonacci number where F(0) = 0, F(1) = 1.
 * Interview Angles:
 * - Demonstrates exponential vs dynamic programming solutions
 * - Highlights memoization and iteration trade-offs
 * - Enables discussion on logarithmic-time solutions via fast doubling
 */
public class Fibonacci {

    // Approach 1: Naive recursion (demonstrates exponential blow-up)
    // Time: O(2^n), Space: O(n) call stack
    public static long fibRecursive(int n) {
        validateInput(n);
        if (n <= 1) {
            return n;
        }
        return fibRecursive(n - 1) + fibRecursive(n - 2);
    }

    // Approach 2: Memoized recursion (top-down DP)
    // Time: O(n), Space: O(n)
    public static long fibMemoized(int n) {
        validateInput(n);
        Map<Integer, Long> cache = new HashMap<>();
        cache.put(0, 0L);
        cache.put(1, 1L);
        return fibMemoizedHelper(n, cache);
    }

    private static long fibMemoizedHelper(int n, Map<Integer, Long> cache) {
        if (cache.containsKey(n)) {
            return cache.get(n);
        }
        long value = fibMemoizedHelper(n - 1, cache) + fibMemoizedHelper(n - 2, cache);
        cache.put(n, value);
        return value;
    }

    // Approach 3: Bottom-up iteration (classic DP)
    // Time: O(n), Space: O(1)
    public static long fibIterative(int n) {
        validateInput(n);
        if (n <= 1) {
            return n;
        }
        long prev = 0L;
        long curr = 1L;
        for (int i = 2; i <= n; i++) {
            long next = prev + curr;
            prev = curr;
            curr = next;
        }
        return curr;
    }

    // Approach 4: Fast doubling (logarithmic time)
    // Time: O(log n), Space: O(log n) due to recursion depth
    public static long fibFastDoubling(int n) {
        validateInput(n);
        return fibFastDoublingHelper(n)[0];
    }

    private static long[] fibFastDoublingHelper(int n) {
        if (n == 0) {
            return new long[]{0L, 1L};
        }
        long[] half = fibFastDoublingHelper(n / 2);
        long a = half[0]; // F(k)
        long b = half[1]; // F(k+1)

        long c = a * (2 * b - a); // F(2k)
        long d = a * a + b * b;   // F(2k + 1)

        if (n % 2 == 0) {
            return new long[]{c, d};
        }
        return new long[]{d, c + d};
    }

    private static void validateInput(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Fibonacci is undefined for negative indices");
        }
    }

    public static void main(String[] args) {
        int sample = 10;
        int biggerSample = 40;

        System.out.println("=== Fibonacci Recursive ===");
        System.out.println("F(" + sample + ") = " + fibRecursive(sample));

        System.out.println("\n=== Fibonacci Memoized ===");
        System.out.println("F(" + biggerSample + ") = " + fibMemoized(biggerSample));

        System.out.println("\n=== Fibonacci Iterative ===");
        System.out.println("F(" + biggerSample + ") = " + fibIterative(biggerSample));

        System.out.println("\n=== Fibonacci Fast Doubling ===");
        System.out.println("F(" + biggerSample + ") = " + fibFastDoubling(biggerSample));

        // Note: Values grow fast; long overflows beyond F(92). Discuss using BigInteger if needed.
    }
}


