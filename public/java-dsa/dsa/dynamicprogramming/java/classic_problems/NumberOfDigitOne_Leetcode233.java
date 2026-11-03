package JAVA_DSA.DYNAMIC_PROGRAMMING.java.classic_problems;

import java.util.*;

/**
 * NUMBER OF DIGIT ONE - Leetcode 233
 * Difficulty: Hard
 * Pattern: Digit DP (Digital Dynamic Programming)
 * 
 * PROBLEM:
 * Given an integer n, count the total number of digit 1 appearing in all non-negative 
 * integers less than or equal to n.
 * 
 * EXAMPLES:
 * Input: n = 13
 * Output: 6
 * Explanation: Digit 1 occurred in the following numbers: 1, 10, 11, 12, 13.
 * 
 * Input: n = 0
 * Output: 0
 * 
 * KEY INSIGHT:
 * Digit DP - consider each digit position and count how many 1's appear.
 * For each position, consider cases: digit < 1, digit = 1, digit > 1.
 */
public class NumberOfDigitOne_Leetcode233 {
    
    /**
     * APPROACH 1: Digit DP with Memoization
     * Time: O(log n * 2 * 2), Space: O(log n * 2 * 2)
     */
    public int countDigitOne_DigitDP(int n) {
        String s = String.valueOf(n);
        int len = s.length();
        
        // memo[pos][tight][started] = count of 1's
        Integer[][][] memo = new Integer[len][2][2];
        return digitDP(s, 0, true, false, memo);
    }
    
    private int digitDP(String s, int pos, boolean tight, boolean started, Integer[][][] memo) {
        if (pos == s.length()) {
            return 0;
        }
        
        int tightInt = tight ? 1 : 0;
        int startedInt = started ? 1 : 0;
        
        if (memo[pos][tightInt][startedInt] != null) {
            return memo[pos][tightInt][startedInt];
        }
        
        int limit = tight ? (s.charAt(pos) - '0') : 9;
        int result = 0;
        
        for (int digit = 0; digit <= limit; digit++) {
            boolean newTight = tight && (digit == limit);
            boolean newStarted = started || (digit > 0);
            
            int currentOnes = 0;
            if (digit == 1 && newStarted) {
                currentOnes = 1;
            }
            
            result += currentOnes + digitDP(s, pos + 1, newTight, newStarted, memo);
        }
        
        memo[pos][tightInt][startedInt] = result;
        return result;
    }
    
    /**
     * APPROACH 2: Mathematical Pattern (Optimal)
     * Time: O(log n), Space: O(1)
     * 
     * For each digit position, count 1's by analyzing patterns.
     */
    public int countDigitOne_Mathematical(int n) {
        int count = 0;
        long factor = 1;
        
        while (factor <= n) {
            long higher = n / (factor * 10);
            long current = (n / factor) % 10;
            long lower = n % factor;
            
            if (current == 0) {
                // Current digit is 0: count = higher * factor
                count += higher * factor;
            } else if (current == 1) {
                // Current digit is 1: count = higher * factor + lower + 1
                count += higher * factor + lower + 1;
            } else {
                // Current digit > 1: count = (higher + 1) * factor
                count += (higher + 1) * factor;
            }
            
            factor *= 10;
        }
        
        return count;
    }
    
    /**
     * APPROACH 3: Brute Force (for small numbers and verification)
     * Time: O(n * log n), Space: O(1)
     */
    public int countDigitOne_BruteForce(int n) {
        int count = 0;
        for (int i = 1; i <= n; i++) {
            count += countOnesInNumber(i);
        }
        return count;
    }
    
    private int countOnesInNumber(int num) {
        int count = 0;
        while (num > 0) {
            if (num % 10 == 1) {
                count++;
            }
            num /= 10;
        }
        return count;
    }
    
    /**
     * APPROACH 4: Detailed Mathematical Explanation
     * Shows step-by-step calculation for understanding
     */
    public int countDigitOne_Explained(int n) {
        System.out.println("=== Counting Digit 1 in range [1, " + n + "] ===");
        
        int count = 0;
        long factor = 1;
        
        while (factor <= n) {
            long higher = n / (factor * 10);
            long current = (n / factor) % 10;
            long lower = n % factor;
            
            System.out.printf("Position %d (factor=%d):\n", 
                Math.round(Math.log10(factor)), factor);
            System.out.printf("  Number: %d = %d|%d|%d\n", n, higher, current, lower);
            
            int positionCount = 0;
            if (current == 0) {
                positionCount = (int)(higher * factor);
                System.out.printf("  Current digit = 0: count = %d * %d = %d\n", 
                    higher, factor, positionCount);
            } else if (current == 1) {
                positionCount = (int)(higher * factor + lower + 1);
                System.out.printf("  Current digit = 1: count = %d * %d + %d + 1 = %d\n", 
                    higher, factor, lower, positionCount);
            } else {
                positionCount = (int)((higher + 1) * factor);
                System.out.printf("  Current digit > 1: count = (%d + 1) * %d = %d\n", 
                    higher, factor, positionCount);
            }
            
            count += positionCount;
            factor *= 10;
        }
        
        System.out.println("Total count: " + count);
        return count;
    }
    
    /**
     * Method to list all numbers containing digit 1 (for verification)
     */
    public List<Integer> getAllNumbersWithOne(int n) {
        List<Integer> result = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            if (String.valueOf(i).contains("1")) {
                result.add(i);
            }
        }
        return result;
    }
    
    /**
     * Advanced Digit DP: Count digit d in range [0, n]
     */
    public int countDigitD(int n, int d) {
        if (d < 0 || d > 9) return 0;
        
        String s = String.valueOf(n);
        int len = s.length();
        
        // memo[pos][tight][started]
        Integer[][][] memo = new Integer[len][2][2];
        return countDigitD_DP(s, 0, true, false, d, memo);
    }
    
    private int countDigitD_DP(String s, int pos, boolean tight, boolean started, 
                               int targetDigit, Integer[][][] memo) {
        if (pos == s.length()) {
            return 0;
        }
        
        int tightInt = tight ? 1 : 0;
        int startedInt = started ? 1 : 0;
        
        if (memo[pos][tightInt][startedInt] != null) {
            return memo[pos][tightInt][startedInt];
        }
        
        int limit = tight ? (s.charAt(pos) - '0') : 9;
        int result = 0;
        
        for (int digit = 0; digit <= limit; digit++) {
            boolean newTight = tight && (digit == limit);
            boolean newStarted = started || (digit > 0);
            
            int currentCount = 0;
            if (digit == targetDigit && (newStarted || targetDigit == 0)) {
                currentCount = 1;
            }
            
            result += currentCount + countDigitD_DP(s, pos + 1, newTight, newStarted, targetDigit, memo);
        }
        
        memo[pos][tightInt][startedInt] = result;
        return result;
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        NumberOfDigitOne_Leetcode233 solution = new NumberOfDigitOne_Leetcode233();
        
        // Test case 1
        int n1 = 13;
        System.out.println("=== Number of Digit One ===");
        System.out.println("n = " + n1);
        
        List<Integer> numbersWithOne = solution.getAllNumbersWithOne(n1);
        System.out.println("Numbers containing 1: " + numbersWithOne);
        System.out.println("Count (Brute Force): " + solution.countDigitOne_BruteForce(n1));
        System.out.println("Count (Mathematical): " + solution.countDigitOne_Mathematical(n1));
        System.out.println("Count (Digit DP): " + solution.countDigitOne_DigitDP(n1));
        System.out.println();
        
        // Test case 2 with explanation
        int n2 = 1234;
        System.out.println("=== Detailed Analysis ===");
        solution.countDigitOne_Explained(n2);
        System.out.println("Verification with Digit DP: " + solution.countDigitOne_DigitDP(n2));
        System.out.println();
        
        // Test case 3 - Edge cases
        System.out.println("=== Edge Cases ===");
        System.out.println("n = 0: " + solution.countDigitOne_Mathematical(0));
        System.out.println("n = 1: " + solution.countDigitOne_Mathematical(1));
        System.out.println("n = 10: " + solution.countDigitOne_Mathematical(10));
        System.out.println("n = 100: " + solution.countDigitOne_Mathematical(100));
        System.out.println();
        
        // Test advanced digit counting
        System.out.println("=== Advanced: Count other digits ===");
        int n3 = 100;
        for (int d = 0; d <= 9; d++) {
            System.out.println("Count of digit " + d + " in [0, " + n3 + "]: " + 
                             solution.countDigitD(n3, d));
        }
    }
}