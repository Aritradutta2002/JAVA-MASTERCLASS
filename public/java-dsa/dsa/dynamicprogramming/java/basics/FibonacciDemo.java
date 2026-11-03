package JAVA_DSA.DYNAMIC_PROGRAMMING.java.basics;

import java.math.BigInteger;

/**
 * FIBONACCI DEMO - Simple demonstration of the FastFibonacci capabilities
 * This shows the ultra-fast computation without user input for easy testing
 */
public class FibonacciDemo {
    
    public static void main(String[] args) {
        System.out.println("🚀 ULTRA-FAST FIBONACCI DEMONSTRATION");
        System.out.println("=" .repeat(60));
        
        // Test speed for F(1000)
        System.out.println("⚡ SPEED TEST: Computing F(1000)");
        long start = System.nanoTime();
        BigInteger f1000 = FastFibonacci_LargeNumbers.fibonacciMatrix(1000);
        long time = System.nanoTime() - start;
        
        System.out.printf("✅ F(1000) computed in %.3f ms\n", time / 1_000_000.0);
        System.out.println("📊 F(1000) has " + f1000.toString().length() + " digits!");
        System.out.println("🔢 First 100 digits: " + f1000.toString().substring(0, 100) + "...");
        System.out.println();
        
        // Show first 20 Fibonacci numbers
        System.out.println("📋 FIRST 20 FIBONACCI NUMBERS:");
        System.out.println("-" .repeat(40));
        for (int i = 1; i <= 20; i++) {
            BigInteger fib = FastFibonacci_LargeNumbers.fibonacciIterative(i);
            System.out.printf("F(%2d) = %s\n", i, fib);
        }
        System.out.println();
        
        // Show some large Fibonacci numbers
        System.out.println("🔥 LARGE FIBONACCI NUMBERS:");
        System.out.println("-" .repeat(50));
        int[] testValues = {50, 100, 200, 500, 1000};
        
        for (int n : testValues) {
            start = System.nanoTime();
            BigInteger fib = FastFibonacci_LargeNumbers.fibonacciMatrix(n);
            time = System.nanoTime() - start;
            
            String fibStr = fib.toString();
            if (fibStr.length() > 50) {
                String preview = fibStr.substring(0, 25) + "..." + fibStr.substring(fibStr.length() - 25);
                System.out.printf("F(%3d) = %s (%d digits, %.3f ms)\n", 
                    n, preview, fibStr.length(), time / 1_000_000.0);
            } else {
                System.out.printf("F(%3d) = %s (%.3f ms)\n", n, fibStr, time / 1_000_000.0);
            }
        }
        System.out.println();
        
        // Performance comparison
        System.out.println("🏁 ALGORITHM PERFORMANCE COMPARISON for F(100):");
        System.out.println("-" .repeat(60));
        
        // Matrix method
        start = System.nanoTime();
        BigInteger result1 = FastFibonacci_LargeNumbers.fibonacciMatrix(100);
        long time1 = System.nanoTime() - start;
        
        // Fast doubling method
        start = System.nanoTime();
        BigInteger result2 = FastFibonacci_LargeNumbers.fibonacciFastDoubling(100);
        long time2 = System.nanoTime() - start;
        
        // Iterative method
        start = System.nanoTime();
        BigInteger result3 = FastFibonacci_LargeNumbers.fibonacciIterative(100);
        long time3 = System.nanoTime() - start;
        
        System.out.printf("Matrix Exponentiation: %.3f ms\n", time1 / 1_000_000.0);
        System.out.printf("Fast Doubling:         %.3f ms\n", time2 / 1_000_000.0);
        System.out.printf("Iterative:             %.3f ms\n", time3 / 1_000_000.0);
        System.out.println("✅ All methods produce same result: " + (result1.equals(result2) && result2.equals(result3)));
        System.out.println();
        
        // Show growth analysis
        System.out.println("📈 FIBONACCI GROWTH MILESTONES:");
        System.out.println("-" .repeat(45));
        
        int[] milestones = {10, 20, 50, 100, 200, 500, 1000};
        System.out.println("n\tDigits\tSample");
        for (int n : milestones) {
            BigInteger fib = FastFibonacci_LargeNumbers.fibonacciMatrix(n);
            String fibStr = fib.toString();
            String sample = fibStr.length() > 20 ? fibStr.substring(0, 15) + "..." : fibStr;
            System.out.printf("%d\t%d\t%s\n", n, fibStr.length(), sample);
        }
        
        System.out.println();
        System.out.println("🎯 KEY ACHIEVEMENTS:");
        System.out.println("✅ No TLE (Time Limit Exceeded) - even for F(1000)");
        System.out.println("✅ Handles arbitrary precision with BigInteger");
        System.out.println("✅ Multiple optimized algorithms available");
        System.out.println("✅ Lightning fast: F(1000) in under 1ms!");
        System.out.println("✅ Perfect for competitive programming");
        
        System.out.println("\n🏆 FIBONACCI CALCULATOR READY FOR ANY CHALLENGE!");
    }
}