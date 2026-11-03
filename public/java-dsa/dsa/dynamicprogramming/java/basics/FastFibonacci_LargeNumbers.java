package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.math.BigInteger;
import java.util.*;

/**
 * FAST FIBONACCI FOR LARGE NUMBERS (1 to 1000)
 * Ultra-optimized implementation for computing Fibonacci numbers up to F(1000)
 * Handles very large numbers efficiently without TLE (Time Limit Exceeded)
 * 
 * PERFORMANCE OPTIMIZATIONS:
 * 1. Matrix Exponentiation: O(log n) time complexity
 * 2. BigInteger for arbitrary precision arithmetic
 * 3. Memoization for repeated calculations
 * 4. Fast doubling algorithm
 * 5. Optimized iterative approach for batch calculations
 * 
 * FEATURES:
 * - Computes F(1000) in milliseconds
 * - No integer overflow issues
 * - Memory efficient
 * - Multiple algorithm implementations
 * - Performance benchmarking
 */
public class FastFibonacci_LargeNumbers {
    
    // Cache for memoization - static to persist across method calls
    private static Map<Integer, BigInteger> memoCache = new HashMap<>();
    
    /**
     * APPROACH 1: Matrix Exponentiation (Fastest for single large values)
     * Time: O(log n), Space: O(log n)
     * 
     * Uses the matrix formula:
     * [F(n+1)]   [1 1]^n   [F(1)]
     * [F(n)  ] = [1 0]   * [F(0)]
     */
    public static BigInteger fibonacciMatrix(int n) {
        if (n <= 0) return BigInteger.ZERO;
        if (n == 1) return BigInteger.ONE;
        
        BigInteger[][] result = matrixPower(new BigInteger[][]{{BigInteger.ONE, BigInteger.ONE}, 
                                                              {BigInteger.ONE, BigInteger.ZERO}}, n - 1);
        return result[0][0];
    }
    
    private static BigInteger[][] matrixPower(BigInteger[][] matrix, int n) {
        if (n == 1) return matrix;
        
        if (n % 2 == 0) {
            BigInteger[][] half = matrixPower(matrix, n / 2);
            return matrixMultiply(half, half);
        } else {
            return matrixMultiply(matrix, matrixPower(matrix, n - 1));
        }
    }
    
    private static BigInteger[][] matrixMultiply(BigInteger[][] a, BigInteger[][] b) {
        return new BigInteger[][]{
            {a[0][0].multiply(b[0][0]).add(a[0][1].multiply(b[1][0])), 
             a[0][0].multiply(b[0][1]).add(a[0][1].multiply(b[1][1]))},
            {a[1][0].multiply(b[0][0]).add(a[1][1].multiply(b[1][0])), 
             a[1][0].multiply(b[0][1]).add(a[1][1].multiply(b[1][1]))}
        };
    }
    
    /**
     * APPROACH 2: Fast Doubling Algorithm (Very fast for large n)
     * Time: O(log n), Space: O(log n)
     * 
     * Uses identities:
     * F(2k) = F(k) * (2*F(k+1) - F(k))
     * F(2k+1) = F(k+1)^2 + F(k)^2
     */
    public static BigInteger fibonacciFastDoubling(int n) {
        return fastDoublingFib(n)[0];
    }
    
    private static BigInteger[] fastDoublingFib(int n) {
        if (n == 0) {
            return new BigInteger[]{BigInteger.ZERO, BigInteger.ONE};
        }
        
        BigInteger[] result = fastDoublingFib(n / 2);
        BigInteger fk = result[0];
        BigInteger fk1 = result[1];
        
        BigInteger f2k = fk.multiply(fk1.multiply(BigInteger.valueOf(2)).subtract(fk));
        BigInteger f2k1 = fk.multiply(fk).add(fk1.multiply(fk1));
        
        if (n % 2 == 0) {
            return new BigInteger[]{f2k, f2k1};
        } else {
            return new BigInteger[]{f2k1, f2k.add(f2k1)};
        }
    }
    
    /**
     * APPROACH 3: Iterative with BigInteger (Best for ranges)
     * Time: O(n), Space: O(1)
     * Most efficient for computing all Fibonacci numbers from 1 to n
     */
    public static BigInteger fibonacciIterative(int n) {
        if (n <= 0) return BigInteger.ZERO;
        if (n == 1) return BigInteger.ONE;
        
        BigInteger prev = BigInteger.ZERO;
        BigInteger curr = BigInteger.ONE;
        
        for (int i = 2; i <= n; i++) {
            BigInteger next = prev.add(curr);
            prev = curr;
            curr = next;
        }
        
        return curr;
    }
    
    /**
     * APPROACH 4: Memoized Recursion (Good for repeated queries)
     * Time: O(n) first call, O(1) subsequent calls
     * Space: O(n)
     */
    public static BigInteger fibonacciMemoized(int n) {
        if (n <= 0) return BigInteger.ZERO;
        if (n == 1) return BigInteger.ONE;
        
        if (memoCache.containsKey(n)) {
            return memoCache.get(n);
        }
        
        BigInteger result = fibonacciMemoized(n - 1).add(fibonacciMemoized(n - 2));
        memoCache.put(n, result);
        return result;
    }
    
    /**
     * BATCH COMPUTATION: Generate all Fibonacci numbers from 1 to limit
     * Most efficient approach for generating ranges
     */
    public static List<BigInteger> generateFibonacciRange(int limit) {
        List<BigInteger> fibNumbers = new ArrayList<>();
        
        if (limit >= 1) fibNumbers.add(BigInteger.ZERO);   // F(0)
        if (limit >= 2) fibNumbers.add(BigInteger.ONE);    // F(1)
        
        BigInteger prev = BigInteger.ZERO;
        BigInteger curr = BigInteger.ONE;
        
        for (int i = 2; i <= limit; i++) {
            BigInteger next = prev.add(curr);
            fibNumbers.add(next);
            prev = curr;
            curr = next;
        }
        
        return fibNumbers;
    }
    
    /**
     * PERFORMANCE BENCHMARKING
     */
    public static void benchmarkAlgorithms(int n) {
        System.out.println("🏁 PERFORMANCE BENCHMARK for F(" + n + ")");
        System.out.println("=" .repeat(60));
        
        // Matrix Exponentiation
        long start = System.nanoTime();
        BigInteger result1 = fibonacciMatrix(n);
        long time1 = System.nanoTime() - start;
        System.out.printf("Matrix Exponentiation: %,.3f ms\n", time1 / 1_000_000.0);
        
        // Fast Doubling
        start = System.nanoTime();
        BigInteger result2 = fibonacciFastDoubling(n);
        long time2 = System.nanoTime() - start;
        System.out.printf("Fast Doubling:         %,.3f ms\n", time2 / 1_000_000.0);
        
        // Iterative (for comparison)
        if (n <= 5000) { // Only test for reasonable n
            start = System.nanoTime();
            BigInteger result3 = fibonacciIterative(n);
            long time3 = System.nanoTime() - start;
            System.out.printf("Iterative:             %,.3f ms\n", time3 / 1_000_000.0);
        } else {
            System.out.println("Iterative:             Skipped (too slow for large n)");
        }
        
        // Verify results match
        System.out.println("Results match: " + result1.equals(result2));
        System.out.println("Result length: " + result1.toString().length() + " digits");
    }
    
    /**
     * DISPLAY UTILITIES
     */
    public static void displayFibonacci(int n, BigInteger fib, boolean showFullNumber) {
        String fibStr = fib.toString();
        
        if (showFullNumber || fibStr.length() <= 50) {
            System.out.printf("F(%3d) = %s\n", n, fibStr);
        } else {
            // Show first and last 20 digits for very large numbers
            String start = fibStr.substring(0, 20);
            String end = fibStr.substring(fibStr.length() - 20);
            System.out.printf("F(%3d) = %s...%s (%d digits)\n", n, start, end, fibStr.length());
        }
    }
    
    public static void displayFibonacciRange(int start, int end, boolean showFullNumbers) {
        System.out.println("🔢 FIBONACCI NUMBERS FROM " + start + " TO " + end);
        System.out.println("=" .repeat(80));
        
        // Use iterative for range generation (most efficient)
        if (start == 1) {
            // Generate from beginning
            List<BigInteger> fibRange = generateFibonacciRange(end);
            for (int i = start - 1; i < Math.min(end, fibRange.size()); i++) {
                displayFibonacci(i + 1, fibRange.get(i), showFullNumbers);
            }
        } else {
            // Generate individual numbers (less efficient but works for any range)
            for (int i = start; i <= end; i++) {
                BigInteger fib = fibonacciMatrix(i); // Use fastest algorithm
                displayFibonacci(i, fib, showFullNumbers);
            }
        }
    }
    
    /**
     * STATISTICAL ANALYSIS
     */
    public static void analyzeFibonacciGrowth(int limit) {
        System.out.println("\n📊 FIBONACCI GROWTH ANALYSIS");
        System.out.println("=" .repeat(50));
        
        List<BigInteger> fibs = generateFibonacciRange(Math.min(limit, 100));
        
        System.out.println("Powers of 10 milestones:");
        int digitTarget = 1;
        
        for (int i = 0; i < fibs.size(); i++) {
            int digits = fibs.get(i).toString().length();
            if (digits >= digitTarget) {
                System.out.printf("F(%d) is first with %d+ digits\n", i + 1, digitTarget);
                digitTarget *= 10;
                if (digitTarget > 1000) break;
            }
        }
        
        // Golden ratio approximation
        if (limit >= 10) {
            BigInteger f10 = fibs.size() > 9 ? fibs.get(9) : fibonacciMatrix(10);
            BigInteger f9 = fibs.size() > 8 ? fibs.get(8) : fibonacciMatrix(9);
            
            double ratio = f10.doubleValue() / f9.doubleValue();
            double goldenRatio = (1 + Math.sqrt(5)) / 2;
            
            System.out.printf("\nGolden Ratio Analysis:\n");
            System.out.printf("F(10)/F(9) = %.10f\n", ratio);
            System.out.printf("Golden Ratio = %.10f\n", goldenRatio);
            System.out.printf("Difference = %.10f\n", Math.abs(ratio - goldenRatio));
        }
    }
    
    /**
     * MAIN PROGRAM: Generate all Fibonacci numbers from 1 to 1000
     */
    public static void main(String[] args) {
        System.out.println("🚀 ULTRA-FAST FIBONACCI CALCULATOR");
        System.out.println("Generating Fibonacci numbers from 1 to 1000");
        System.out.println("Using optimized algorithms for large number computation");
        System.out.println("=" .repeat(80));
        
        // Quick demonstration of large number capability
        System.out.println("\n⚡ SPEED TEST - Computing F(1000):");
        long totalStart = System.nanoTime();
        BigInteger f1000 = fibonacciMatrix(1000);
        long totalTime = System.nanoTime() - totalStart;
        
        System.out.printf("F(1000) computed in %.3f ms\n", totalTime / 1_000_000.0);
        System.out.println("F(1000) has " + f1000.toString().length() + " digits!");
        System.out.println("First 50 digits: " + f1000.toString().substring(0, 50) + "...");
        System.out.println();
        
        // User options
        Scanner scanner = new Scanner(System.in);
        
        while (true) {
            System.out.println("\n📋 CHOOSE AN OPTION:");
            System.out.println("1. Show all Fibonacci numbers 1-100 (manageable output)");
            System.out.println("2. Show specific range (you choose start and end)");
            System.out.println("3. Show large Fibonacci numbers (500-510)");
            System.out.println("4. Performance benchmark different algorithms");
            System.out.println("5. Statistical analysis of Fibonacci growth");
            System.out.println("6. Show F(1000) in full");
            System.out.println("7. Exit");
            System.out.print("\nEnter your choice (1-7): ");
            
            int choice = scanner.nextInt();
            
            switch (choice) {
                case 1:
                    displayFibonacciRange(1, 100, false);
                    break;
                    
                case 2:
                    System.out.print("Enter start number (1-1000): ");
                    int start = scanner.nextInt();
                    System.out.print("Enter end number (1-1000): ");
                    int end = scanner.nextInt();
                    
                    if (start >= 1 && end <= 1000 && start <= end) {
                        boolean showFull = (end - start) <= 20; // Show full numbers for small ranges
                        displayFibonacciRange(start, end, showFull);
                    } else {
                        System.out.println("Invalid range! Please use numbers between 1-1000.");
                    }
                    break;
                    
                case 3:
                    System.out.println("🔥 LARGE FIBONACCI NUMBERS (these are HUGE!):");
                    displayFibonacciRange(500, 510, false);
                    break;
                    
                case 4:
                    System.out.print("Enter number for benchmark (1-1000): ");
                    int benchN = scanner.nextInt();
                    if (benchN >= 1 && benchN <= 1000) {
                        benchmarkAlgorithms(benchN);
                    }
                    break;
                    
                case 5:
                    analyzeFibonacciGrowth(100);
                    break;
                    
                case 6:
                    System.out.println("🔢 F(1000) IN FULL:");
                    System.out.println("This number has " + f1000.toString().length() + " digits:");
                    System.out.println(f1000);
                    break;
                    
                case 7:
                    System.out.println("👋 Thanks for using Ultra-Fast Fibonacci Calculator!");
                    scanner.close();
                    return;
                    
                default:
                    System.out.println("Invalid choice! Please enter 1-7.");
            }
        }
    }
    
    /**
     * BONUS: Generate and save to file (for very large datasets)
     */
    public static void generateAndSaveToFile(int limit, String filename) {
        try (java.io.PrintWriter writer = new java.io.PrintWriter(filename)) {
            writer.println("Fibonacci Numbers from 1 to " + limit);
            writer.println("Generated using optimized algorithms");
            writer.println("=" .repeat(50));
            
            List<BigInteger> fibs = generateFibonacciRange(limit);
            for (int i = 0; i < fibs.size(); i++) {
                writer.printf("F(%d) = %s\n", i + 1, fibs.get(i).toString());
            }
            
            System.out.println("Fibonacci numbers saved to " + filename);
        } catch (Exception e) {
            System.out.println("Error saving to file: " + e.getMessage());
        }
    }
}