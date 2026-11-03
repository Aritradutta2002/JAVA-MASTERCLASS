package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 338: Counting Bits
 * Difficulty: Medium
 * 
 * Given an integer n, return an array ans of length n + 1 such that for each i (0 <= i <= n), 
 * ans[i] is the number of 1's in the binary representation of i.
 * 
 * Example 1:
 * Input: n = 2
 * Output: [0,1,1]
 * Explanation:
 * 0 --> 0
 * 1 --> 1
 * 2 --> 10
 * 
 * Example 2:
 * Input: n = 5
 * Output: [0,1,1,2,1,2]
 * Explanation:
 * 0 --> 0
 * 1 --> 1
 * 2 --> 10
 * 3 --> 11
 * 4 --> 100
 * 5 --> 101
 * 
 * Constraints: 0 <= n <= 10^5
 * 
 * Follow up:
 * - It is very easy to come up with a solution with O(n log n) time complexity. Can you do it in linear time O(n)?
 * - Can you do it without using any built-in function (like __builtin_popcount in C++)?
 */
public class CountingBits_Leetcode338 {
    
    /**
     * Approach 1: Brute Force
     * Count bits for each number individually
     * Time: O(n log n), Space: O(1) extra
     */
    public int[] countBits1(int n) {
        int[] result = new int[n + 1];
        
        for (int i = 0; i <= n; i++) {
            result[i] = countSetBits(i);
        }
        
        return result;
    }
    
    private int countSetBits(int num) {
        int count = 0;
        while (num > 0) {
            count++;
            num = num & (num - 1);  // Brian Kernighan's algorithm
        }
        return count;
    }
    
    /**
     * Approach 2: Dynamic Programming - Right Shift Pattern
     * Key insight: dp[i] = dp[i >> 1] + (i & 1)
     * When we shift right, we remove LSB and add 1 if LSB was set
     * Time: O(n), Space: O(1) extra
     */
    public int[] countBits2(int n) {
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            dp[i] = dp[i >> 1] + (i & 1);
        }
        
        return dp;
    }
    
    /**
     * Approach 3: Dynamic Programming - Clear Rightmost Set Bit
     * Key insight: dp[i] = dp[i & (i-1)] + 1
     * i & (i-1) removes the rightmost set bit
     * Time: O(n), Space: O(1) extra
     */
    public int[] countBits3(int n) {
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            dp[i] = dp[i & (i - 1)] + 1;
        }
        
        return dp;
    }
    
    /**
     * Approach 4: Dynamic Programming - Power of 2 Pattern
     * Use the fact that dp[i] = dp[i - powerOf2] + 1
     * Time: O(n), Space: O(1) extra
     */
    public int[] countBits4(int n) {
        int[] dp = new int[n + 1];
        int powerOf2 = 1;
        int nextPowerOf2 = 2;
        
        for (int i = 1; i <= n; i++) {
            if (i == nextPowerOf2) {
                powerOf2 = i;
                nextPowerOf2 = i << 1;
            }
            dp[i] = dp[i - powerOf2] + 1;
        }
        
        return dp;
    }
    
    /**
     * Approach 5: Using Built-in Function (for comparison)
     * Time: O(n), Space: O(1) extra
     */
    public int[] countBits5(int n) {
        int[] result = new int[n + 1];
        
        for (int i = 0; i <= n; i++) {
            result[i] = Integer.bitCount(i);
        }
        
        return result;
    }
    
    /**
     * Follow-up 1: Count bits in range [left, right]
     */
    public int[] countBitsInRange(int left, int right) {
        int[] allBits = countBits2(right);
        int[] result = new int[right - left + 1];
        
        for (int i = 0; i < result.length; i++) {
            result[i] = allBits[left + i];
        }
        
        return result;
    }
    
    /**
     * Follow-up 2: Find numbers with exactly k bits set
     */
    public java.util.List<Integer> findNumbersWithKBits(int n, int k) {
        int[] bits = countBits2(n);
        java.util.List<Integer> result = new java.util.ArrayList<>();
        
        for (int i = 0; i <= n; i++) {
            if (bits[i] == k) {
                result.add(i);
            }
        }
        
        return result;
    }
    
    /**
     * Follow-up 3: Sum of all bit counts from 0 to n
     */
    public long sumOfAllBitCounts(int n) {
        int[] bits = countBits2(n);
        long sum = 0;
        
        for (int count : bits) {
            sum += count;
        }
        
        return sum;
    }
    
    /**
     * Mathematical approach for sum of bit counts
     * More efficient for large n
     */
    public long sumOfAllBitCountsMath(int n) {
        long sum = 0;
        
        for (int bit = 0; bit < 32; bit++) {
            long cycleLength = 1L << (bit + 1);
            long completeCycles = (n + 1) / cycleLength;
            long remainder = (n + 1) % cycleLength;
            
            sum += completeCycles * (1L << bit);
            
            if (remainder > (1L << bit)) {
                sum += remainder - (1L << bit);
            }
        }
        
        return sum;
    }
    
    /**
     * Utility method to explain the DP patterns
     */
    public void explainDPPatterns(int n) {
        System.out.println("Explaining DP patterns for n = " + n);
        System.out.println("Number | Binary   | Bits | Right Shift | Clear Rightmost");
        System.out.println("-------|----------|------|-------------|----------------");
        
        int[] dp2 = new int[n + 1];
        int[] dp3 = new int[n + 1];
        
        for (int i = 0; i <= n; i++) {
            if (i > 0) {
                dp2[i] = dp2[i >> 1] + (i & 1);
                dp3[i] = dp3[i & (i - 1)] + 1;
            }
            
            System.out.printf("%6d | %8s | %4d | %11s | %15s%n",
                i, 
                Integer.toBinaryString(i),
                Integer.bitCount(i),
                i > 0 ? dp2[i >> 1] + " + " + (i & 1) + " = " + dp2[i] : "0",
                i > 0 ? dp3[i & (i - 1)] + " + 1 = " + dp3[i] : "0"
            );
        }
    }
    
    /**
     * Performance comparison of different approaches
     */
    public void performanceComparison(int n) {
        System.out.println("Performance comparison for n = " + n);
        
        long startTime, endTime;
        
        // Approach 1: Brute Force
        startTime = System.nanoTime();
        int[] result1 = countBits1(n);
        endTime = System.nanoTime();
        System.out.println("Brute Force: " + (endTime - startTime) + " ns");
        
        // Approach 2: Right Shift DP
        startTime = System.nanoTime();
        int[] result2 = countBits2(n);
        endTime = System.nanoTime();
        System.out.println("Right Shift DP: " + (endTime - startTime) + " ns");
        
        // Approach 3: Clear Rightmost DP
        startTime = System.nanoTime();
        int[] result3 = countBits3(n);
        endTime = System.nanoTime();
        System.out.println("Clear Rightmost DP: " + (endTime - startTime) + " ns");
        
        // Approach 4: Power of 2 DP
        startTime = System.nanoTime();
        int[] result4 = countBits4(n);
        endTime = System.nanoTime();
        System.out.println("Power of 2 DP: " + (endTime - startTime) + " ns");
        
        // Verify all results are same
        boolean allSame = java.util.Arrays.equals(result1, result2) &&
                         java.util.Arrays.equals(result2, result3) &&
                         java.util.Arrays.equals(result3, result4);
        System.out.println("All results match: " + allSame);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        CountingBits_Leetcode338 solution = new CountingBits_Leetcode338();
        
        // Test basic cases
        System.out.println("=== Testing Basic Cases ===");
        int[] testCases = {2, 5, 8, 15};
        
        for (int n : testCases) {
            System.out.println("n = " + n);
            
            int[] result2 = solution.countBits2(n);
            int[] result3 = solution.countBits3(n);
            
            System.out.print("Result: ");
            for (int count : result2) {
                System.out.print(count + " ");
            }
            System.out.println();
            
            System.out.println("Approaches 2 and 3 match: " + 
                java.util.Arrays.equals(result2, result3));
            System.out.println();
        }
        
        // Explain DP patterns
        System.out.println("=== DP Pattern Explanation ===");
        solution.explainDPPatterns(8);
        
        // Test follow-ups
        System.out.println("\n=== Testing Follow-ups ===");
        
        // Numbers with exactly k bits
        System.out.println("Numbers with exactly 2 bits in [0,15]:");
        System.out.println(solution.findNumbersWithKBits(15, 2));
        
        // Sum of all bit counts
        System.out.println("Sum of bit counts [0,15]: " + solution.sumOfAllBitCounts(15));
        System.out.println("Sum of bit counts [0,15] (math): " + solution.sumOfAllBitCountsMath(15));
        
        // Performance comparison
        System.out.println("\n=== Performance Comparison ===");
        solution.performanceComparison(1000);
    }
}

