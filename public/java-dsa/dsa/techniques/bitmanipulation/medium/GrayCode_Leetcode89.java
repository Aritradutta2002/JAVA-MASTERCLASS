package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 89: Gray Code
 * Difficulty: Medium
 * 
 * An n-bit gray code sequence is a sequence of 2^n integers where:
 * - Every integer is in the inclusive range [0, 2^n - 1]
 * - The first integer is 0
 * - An integer appears no more than once in the sequence
 * - The binary representation of every pair of adjacent integers differs by exactly one bit
 * - The binary representation of the first and last integers also differs by exactly one bit
 * 
 * Given an integer n, return any valid n-bit gray code sequence.
 * 
 * Example 1:
 * Input: n = 2
 * Output: [0,1,3,2]
 * Explanation:
 * 00 - 0
 * 01 - 1
 * 11 - 3
 * 10 - 2
 * [0,1,3,2] is a valid gray code sequence.
 * 
 * Example 2:
 * Input: n = 1
 * Output: [0,1]
 * 
 * Constraints: 1 <= n <= 16
 */
public class GrayCode_Leetcode89 {
    
    /**
     * Approach 1: Mathematical Formula (Optimal)
     * Gray Code formula: G(i) = i ^ (i >> 1)
     * Time: O(2^n), Space: O(1) excluding output
     */
    public java.util.List<Integer> grayCode(int n) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        int total = 1 << n; // 2^n
        
        for (int i = 0; i < total; i++) {
            result.add(i ^ (i >> 1));
        }
        
        return result;
    }
    
    /**
     * Approach 2: Recursive/Iterative Construction
     * Build n-bit gray code from (n-1)-bit gray code
     * Time: O(2^n), Space: O(2^n)
     */
    public java.util.List<Integer> grayCodeRecursive(int n) {
        if (n == 1) {
            return java.util.Arrays.asList(0, 1);
        }
        
        java.util.List<Integer> prevGray = grayCodeRecursive(n - 1);
        java.util.List<Integer> result = new java.util.ArrayList<>(prevGray);
        
        // Add reversed previous sequence with MSB set
        int msb = 1 << (n - 1);
        for (int i = prevGray.size() - 1; i >= 0; i--) {
            result.add(prevGray.get(i) | msb);
        }
        
        return result;
    }
    
    /**
     * Approach 3: Iterative Construction
     * Build step by step from n=1 to n
     * Time: O(2^n), Space: O(2^n)
     */
    public java.util.List<Integer> grayCodeIterative(int n) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        result.add(0);
        
        for (int i = 0; i < n; i++) {
            int size = result.size();
            int msb = 1 << i;
            
            // Add reversed sequence with ith bit set
            for (int j = size - 1; j >= 0; j--) {
                result.add(result.get(j) | msb);
            }
        }
        
        return result;
    }
    
    /**
     * Approach 4: Backtracking (Alternative)
     * Generate valid sequence using backtracking
     * Time: O(2^n), Space: O(2^n)
     */
    public java.util.List<Integer> grayCodeBacktrack(int n) {
        java.util.List<Integer> result = new java.util.ArrayList<>();
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        
        result.add(0);
        visited.add(0);
        backtrack(0, n, result, visited);
        
        return result;
    }
    
    private boolean backtrack(int current, int n, java.util.List<Integer> result, java.util.Set<Integer> visited) {
        if (result.size() == (1 << n)) {
            // Check if last element differs from first by exactly one bit
            return Integer.bitCount(current ^ 0) == 1;
        }
        
        for (int i = 0; i < n; i++) {
            int next = current ^ (1 << i); // Flip ith bit
            
            if (!visited.contains(next)) {
                result.add(next);
                visited.add(next);
                
                if (backtrack(next, n, result, visited)) {
                    return true;
                }
                
                result.remove(result.size() - 1);
                visited.remove(next);
            }
        }
        
        return false;
    }
    
    /**
     * Utility: Convert gray code back to binary
     */
    public int grayToBinary(int gray) {
        int binary = 0;
        while (gray != 0) {
            binary ^= gray;
            gray >>= 1;
        }
        return binary;
    }
    
    /**
     * Utility: Convert binary to gray code
     */
    public int binaryToGray(int binary) {
        return binary ^ (binary >> 1);
    }
    
    /**
     * Utility: Verify if sequence is valid gray code
     */
    public boolean isValidGrayCode(java.util.List<Integer> sequence, int n) {
        if (sequence.size() != (1 << n)) return false;
        if (sequence.get(0) != 0) return false;
        
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        
        for (int i = 0; i < sequence.size(); i++) {
            int current = sequence.get(i);
            
            // Check range
            if (current < 0 || current >= (1 << n)) return false;
            
            // Check uniqueness
            if (seen.contains(current)) return false;
            seen.add(current);
            
            // Check adjacent difference
            int next = sequence.get((i + 1) % sequence.size());
            if (Integer.bitCount(current ^ next) != 1) return false;
        }
        
        return true;
    }
    
    /**
     * Utility: Print gray code sequence with binary representation
     */
    public void printGrayCode(java.util.List<Integer> grayCode, int n) {
        System.out.println("Gray Code Sequence for n = " + n + ":");
        System.out.println("Index | Decimal | Binary   | Gray");
        System.out.println("------|---------|----------|--------");
        
        for (int i = 0; i < grayCode.size(); i++) {
            int value = grayCode.get(i);
            String binary = String.format("%" + n + "s", Integer.toBinaryString(value)).replace(' ', '0');
            System.out.printf("%5d | %7d | %8s | %s%n", i, value, binary, binary);
        }
        
        // Show transitions
        System.out.println("\nTransitions (differing bits marked with *):");
        for (int i = 0; i < grayCode.size(); i++) {
            int current = grayCode.get(i);
            int next = grayCode.get((i + 1) % grayCode.size());
            
            String currentBin = String.format("%" + n + "s", Integer.toBinaryString(current)).replace(' ', '0');
            String nextBin = String.format("%" + n + "s", Integer.toBinaryString(next)).replace(' ', '0');
            
            StringBuilder diff = new StringBuilder();
            for (int j = 0; j < n; j++) {
                diff.append(currentBin.charAt(j) == nextBin.charAt(j) ? ' ' : '*');
            }
            
            System.out.printf("%s → %s%n", currentBin, nextBin);
            System.out.printf("%s%n", diff.toString());
        }
    }
    
    /**
     * Generate all possible gray code sequences for n (there can be multiple valid sequences)
     */
    public java.util.List<java.util.List<Integer>> allGrayCodeSequences(int n) {
        java.util.List<java.util.List<Integer>> allSequences = new java.util.ArrayList<>();
        java.util.List<Integer> current = new java.util.ArrayList<>();
        java.util.Set<Integer> visited = new java.util.HashSet<>();
        
        current.add(0);
        visited.add(0);
        
        generateAllSequences(0, n, current, visited, allSequences);
        return allSequences;
    }
    
    private void generateAllSequences(int current, int n, java.util.List<Integer> sequence, 
                                    java.util.Set<Integer> visited, java.util.List<java.util.List<Integer>> allSequences) {
        if (sequence.size() == (1 << n)) {
            if (Integer.bitCount(current ^ 0) == 1) {
                allSequences.add(new java.util.ArrayList<>(sequence));
            }
            return;
        }
        
        for (int i = 0; i < n; i++) {
            int next = current ^ (1 << i);
            
            if (!visited.contains(next)) {
                sequence.add(next);
                visited.add(next);
                
                generateAllSequences(next, n, sequence, visited, allSequences);
                
                sequence.remove(sequence.size() - 1);
                visited.remove(next);
            }
        }
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        GrayCode_Leetcode89 solution = new GrayCode_Leetcode89();
        
        // Test different values of n
        for (int n = 1; n <= 4; n++) {
            System.out.println("Testing n = " + n);
            
            java.util.List<Integer> result = solution.grayCode(n);
            System.out.println("Result: " + result);
            System.out.println("Valid: " + solution.isValidGrayCode(result, n));
            
            if (n <= 3) {
                solution.printGrayCode(result, n);
            }
            System.out.println();
        }
        
        // Performance comparison
        System.out.println("Performance Comparison for n = 10:");
        int n = 10;
        
        long start = System.nanoTime();
        java.util.List<Integer> result1 = solution.grayCode(n);
        long time1 = System.nanoTime() - start;
        
        start = System.nanoTime();
        java.util.List<Integer> result2 = solution.grayCodeIterative(n);
        long time2 = System.nanoTime() - start;
        
        start = System.nanoTime();
        java.util.List<Integer> result3 = solution.grayCodeRecursive(n);
        long time3 = System.nanoTime() - start;
        
        System.out.printf("Formula method: %.2f ms%n", time1 / 1_000_000.0);
        System.out.printf("Iterative method: %.2f ms%n", time2 / 1_000_000.0);
        System.out.printf("Recursive method: %.2f ms%n", time3 / 1_000_000.0);
        
        // Verify all methods produce valid results
        System.out.println("All methods produce valid results: " + 
            (solution.isValidGrayCode(result1, n) && 
             solution.isValidGrayCode(result2, n) && 
             solution.isValidGrayCode(result3, n)));
        
        // Test conversion functions
        System.out.println("\nTesting Gray ↔ Binary conversion:");
        for (int i = 0; i < 8; i++) {
            int gray = solution.binaryToGray(i);
            int backToBinary = solution.grayToBinary(gray);
            System.out.printf("Binary: %3s → Gray: %3s → Binary: %3s%n",
                Integer.toBinaryString(i),
                Integer.toBinaryString(gray),
                Integer.toBinaryString(backToBinary));
        }
    }
}

