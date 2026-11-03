package dsa.fundamentals.basics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prime Number - Only Divisible by 1 and Itself
 *
 * Problem: Determine if an integer is prime and generate primes within a range.
 * Interview Angles:
 * - Entry point to discuss time complexity improvements (sqrt, skipping evens)
 * - Bridges primality testing and prime generation (sieve)
 * - Encourages discussion on practical constraints (upper bounds, memory)
 */
public class PrimeNumber {

    // Approach 1: Trial division (check all numbers from 2 to n-1)
    // Time: O(n), Space: O(1)
    public static boolean isPrimeBruteForce(int n) {
        if (n <= 1) {
            return false;
        }
        for (int i = 2; i < n; i++) {
            if (n % i == 0) {
                return false;
            }
        }
        return true;
    }

    // Approach 2: Optimized trial division (up to sqrt(n), skip evens after 2)
    // Time: O(sqrt(n)), Space: O(1)
    public static boolean isPrimeOptimized(int n) {
        if (n <= 1) {
            return false;
        }
        if (n == 2 || n == 3) {
            return true;
        }
        if (n % 2 == 0 || n % 3 == 0) {
            return false;
        }
        for (int i = 5; i * i <= n; i += 6) {
            if (n % i == 0 || n % (i + 2) == 0) {
                return false;
            }
        }
        return true;
    }

    // Approach 3: Sieve of Eratosthenes for range queries
    // Time: O(n log log n), Space: O(n)
    public static List<Integer> sieveOfEratosthenes(int limit) {
        if (limit < 2) {
            return new ArrayList<>();
        }
        boolean[] isComposite = new boolean[limit + 1];
        Arrays.fill(isComposite, false);
        for (int i = 2; i * i <= limit; i++) {
            if (!isComposite[i]) {
                for (int multiple = i * i; multiple <= limit; multiple += i) {
                    isComposite[multiple] = true;
                }
            }
        }

        List<Integer> primes = new ArrayList<>();
        for (int i = 2; i <= limit; i++) {
            if (!isComposite[i]) {
                primes.add(i);
            }
        }
        return primes;
    }

    public static void main(String[] args) {
        int candidate = 97;
        int nonPrimeCandidate = 100;
        int rangeLimit = 50;

        System.out.println("=== Prime Check Brute Force ===");
        System.out.println(candidate + " -> " + isPrimeBruteForce(candidate));

        System.out.println("\n=== Prime Check Optimized ===");
        System.out.println(nonPrimeCandidate + " -> " + isPrimeOptimized(nonPrimeCandidate));

        System.out.println("\n=== Sieve of Eratosthenes ===");
        System.out.println("Primes up to " + rangeLimit + ": " + sieveOfEratosthenes(rangeLimit));

        // Discuss trade-offs: brute force simplicity vs optimized checks vs preprocessing with sieve.
    }
}


