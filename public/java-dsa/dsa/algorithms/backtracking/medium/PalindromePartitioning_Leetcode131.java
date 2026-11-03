package dsa.algorithms.backtracking.medium;

import java.util.*;

/**
 * PALINDROME PARTITIONING - LeetCode 131 (MEDIUM)
 * 
 * Problem: Given a string s, partition s such that every substring of the partition 
 * is a palindrome. Return all possible palindrome partitioning of s.
 * 
 * Example:
 * Input: s = "aab"
 * Output: [["a","a","b"],["aa","b"]]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐
 * - Common in interviews (Amazon, Microsoft, Google)
 * - Tests string manipulation + backtracking
 * - Good for discussing optimization strategies
 * 
 * Time Complexity: O(2^N * N) - 2^N possible partitions, N to check palindrome
 * Space Complexity: O(N) - recursion depth
 */
public class PalindromePartitioning_Leetcode131 {
    
    /**
     * APPROACH 1: BACKTRACKING WITH PALINDROME CHECK
     * Most intuitive approach - check palindrome for each substring
     */
    public List<List<String>> partition(String s) {
        List<List<String>> result = new ArrayList<>();
        backtrack(result, new ArrayList<>(), s, 0);
        return result;
    }
    
    private void backtrack(List<List<String>> result, 
                          List<String> current, 
                          String s, 
                          int start) {
        // Base case: reached end of string
        if (start >= s.length()) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        // Try all possible substrings starting from 'start'
        for (int end = start; end < s.length(); end++) {
            String substring = s.substring(start, end + 1);
            
            if (isPalindrome(substring)) {
                current.add(substring);                    // Choose
                backtrack(result, current, s, end + 1);   // Recurse
                current.remove(current.size() - 1);       // Backtrack
            }
        }
    }
    
    private boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right) {
            if (s.charAt(left++) != s.charAt(right--)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * APPROACH 2: OPTIMIZED WITH PRECOMPUTED PALINDROME TABLE
     * Use DP to precompute all palindrome substrings
     */
    public List<List<String>> partitionOptimized(String s) {
        List<List<String>> result = new ArrayList<>();
        boolean[][] isPalin = new boolean[s.length()][s.length()];
        
        // Precompute palindrome table
        computePalindromeTable(s, isPalin);
        
        backtrackOptimized(result, new ArrayList<>(), s, 0, isPalin);
        return result;
    }
    
    private void computePalindromeTable(String s, boolean[][] isPalin) {
        int n = s.length();
        
        // Single characters are palindromes
        for (int i = 0; i < n; i++) {
            isPalin[i][i] = true;
        }
        
        // Check for palindromes of length 2
        for (int i = 0; i < n - 1; i++) {
            isPalin[i][i + 1] = (s.charAt(i) == s.charAt(i + 1));
        }
        
        // Check for palindromes of length 3 and more
        for (int len = 3; len <= n; len++) {
            for (int i = 0; i < n - len + 1; i++) {
                int j = i + len - 1;
                isPalin[i][j] = (s.charAt(i) == s.charAt(j)) && isPalin[i + 1][j - 1];
            }
        }
    }
    
    private void backtrackOptimized(List<List<String>> result,
                                   List<String> current,
                                   String s,
                                   int start,
                                   boolean[][] isPalin) {
        if (start >= s.length()) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int end = start; end < s.length(); end++) {
            if (isPalin[start][end]) {
                String substring = s.substring(start, end + 1);
                current.add(substring);
                backtrackOptimized(result, current, s, end + 1, isPalin);
                current.remove(current.size() - 1);
            }
        }
    }
    
    /**
     * APPROACH 3: EXPAND AROUND CENTER OPTIMIZATION
     * Check palindromes by expanding around center
     */
    public List<List<String>> partitionExpandCenter(String s) {
        List<List<String>> result = new ArrayList<>();
        Set<String> palindromes = new HashSet<>();
        
        // Find all palindromes using expand around center
        for (int i = 0; i < s.length(); i++) {
            // Odd length palindromes
            expandAroundCenter(s, i, i, palindromes);
            // Even length palindromes
            expandAroundCenter(s, i, i + 1, palindromes);
        }
        
        backtrackWithSet(result, new ArrayList<>(), s, 0, palindromes);
        return result;
    }
    
    private void expandAroundCenter(String s, int left, int right, Set<String> palindromes) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            palindromes.add(s.substring(left, right + 1));
            left--;
            right++;
        }
    }
    
    private void backtrackWithSet(List<List<String>> result,
                                 List<String> current,
                                 String s,
                                 int start,
                                 Set<String> palindromes) {
        if (start >= s.length()) {
            result.add(new ArrayList<>(current));
            return;
        }
        
        for (int end = start; end < s.length(); end++) {
            String substring = s.substring(start, end + 1);
            if (palindromes.contains(substring)) {
                current.add(substring);
                backtrackWithSet(result, current, s, end + 1, palindromes);
                current.remove(current.size() - 1);
            }
        }
    }
    
    /**
     * APPROACH 4: MEMOIZATION (IF NEEDED FOR REPEATED CALLS)
     * Cache results for subproblems
     */
    public List<List<String>> partitionMemo(String s) {
        Map<String, List<List<String>>> memo = new HashMap<>();
        return backtrackMemo(s, memo);
    }
    
    private List<List<String>> backtrackMemo(String s, Map<String, List<List<String>>> memo) {
        if (memo.containsKey(s)) {
            return memo.get(s);
        }
        
        List<List<String>> result = new ArrayList<>();
        
        if (s.length() == 0) {
            result.add(new ArrayList<>());
            return result;
        }
        
        for (int i = 1; i <= s.length(); i++) {
            String prefix = s.substring(0, i);
            if (isPalindrome(prefix)) {
                String suffix = s.substring(i);
                List<List<String>> suffixPartitions = backtrackMemo(suffix, memo);
                
                for (List<String> partition : suffixPartitions) {
                    List<String> newPartition = new ArrayList<>();
                    newPartition.add(prefix);
                    newPartition.addAll(partition);
                    result.add(newPartition);
                }
            }
        }
        
        memo.put(s, result);
        return result;
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Problem breakdown:
     *    - Try all possible ways to split the string
     *    - Each split must result in palindromes
     *    - Return all valid partitionings
     * 
     * 2. Optimization strategies:
     *    - Precompute palindrome table: O(N²) preprocessing, O(1) lookup
     *    - Expand around center: Find palindromes efficiently
     *    - Memoization: Cache results for repeated subproblems
     * 
     * 3. Time complexity analysis:
     *    - Basic: O(2^N * N) - 2^N ways to partition, N to check palindrome
     *    - Optimized: O(2^N + N²) - N² for preprocessing, 2^N for backtracking
     * 
     * 4. Follow-up questions:
     *    - What's the minimum number of cuts needed? (Palindrome Partitioning II)
     *    - Can you return just one valid partitioning?
     *    - What if we want the lexicographically smallest partitioning?
     * 
     * 5. Edge cases:
     *    - Empty string
     *    - Single character (always palindrome)
     *    - All same characters
     *    - No palindromes except single characters
     */
    
    public static void main(String[] args) {
        PalindromePartitioning_Leetcode131 solution = new PalindromePartitioning_Leetcode131();
        
        // Test cases
        String[] testCases = {"aab", "raceacar", "abcde", "a", "aa", "abccba"};
        
        for (String s : testCases) {
            System.out.println("Input: \"" + s + "\"");
            
            // Test basic approach
            long start1 = System.nanoTime();
            List<List<String>> result1 = solution.partition(s);
            long end1 = System.nanoTime();
            
            // Test optimized approach
            long start2 = System.nanoTime();
            List<List<String>> result2 = solution.partitionOptimized(s);
            long end2 = System.nanoTime();
            
            System.out.println("Result: " + result1);
            System.out.println("Basic time: " + (end1 - start1) / 1000.0 + " μs");
            System.out.println("Optimized time: " + (end2 - start2) / 1000.0 + " μs");
            System.out.println("Results match: " + result1.equals(result2));
            System.out.println();
        }
        
        // Performance test with longer string
        String longString = "abcdefghijklm";
        System.out.println("Performance test with string: \"" + longString + "\"");
        
        long start = System.nanoTime();
        List<List<String>> result = solution.partitionOptimized(longString);
        long end = System.nanoTime();
        
        System.out.println("Number of partitions: " + result.size());
        System.out.println("Time taken: " + (end - start) / 1000000.0 + " ms");
    }
}
