package dsa.algorithms.backtracking.easy;

import java.util.*;

/**
 * GENERATE PARENTHESES - LeetCode 22 (EASY/MEDIUM)
 * 
 * Problem: Given n pairs of parentheses, write a function to generate all combinations 
 * of well-formed parentheses.
 * 
 * Example:
 * Input: n = 3
 * Output: ["((()))","(()())","(())()","()(())","()()()"]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐⭐
 * - Very common in interviews (Google, Facebook, Amazon)
 * - Tests constraint-based backtracking
 * - Good for discussing optimization and pruning
 * 
 * Time Complexity: O(4^n / √n) - Catalan number
 * Space Complexity: O(4^n / √n) - to store all valid combinations
 */
public class GenerateParentheses_Leetcode22 {
    
    /**
     * APPROACH 1: BACKTRACKING WITH CONSTRAINTS
     * Key insight: Only add '(' if we haven't used all n, only add ')' if it won't exceed '('
     */
    public List<String> generateParenthesis(int n) {
        List<String> result = new ArrayList<>();
        backtrack(result, new StringBuilder(), 0, 0, n);
        return result;
    }
    
    private void backtrack(List<String> result, 
                          StringBuilder current, 
                          int open, 
                          int close, 
                          int max) {
        // Base case: we've used all n pairs
        if (current.length() == max * 2) {
            result.add(current.toString());
            return;
        }
        
        // Add opening bracket if we haven't used all n
        if (open < max) {
            current.append('(');
            backtrack(result, current, open + 1, close, max);
            current.deleteCharAt(current.length() - 1); // Backtrack
        }
        
        // Add closing bracket if it won't exceed opening brackets
        if (close < open) {
            current.append(')');
            backtrack(result, current, open, close + 1, max);
            current.deleteCharAt(current.length() - 1); // Backtrack
        }
    }
    
    /**
     * APPROACH 2: USING STRING INSTEAD OF STRINGBUILDER
     * Less efficient but easier to understand for beginners
     */
    public List<String> generateParenthesisString(int n) {
        List<String> result = new ArrayList<>();
        backtrackString(result, "", 0, 0, n);
        return result;
    }
    
    private void backtrackString(List<String> result, 
                               String current, 
                               int open, 
                               int close, 
                               int max) {
        if (current.length() == max * 2) {
            result.add(current);
            return;
        }
        
        if (open < max) {
            backtrackString(result, current + "(", open + 1, close, max);
        }
        
        if (close < open) {
            backtrackString(result, current + ")", open, close + 1, max);
        }
    }
    
    /**
     * APPROACH 3: CLOSURE NUMBER (DYNAMIC PROGRAMMING APPROACH)
     * For understanding different algorithms, not typical in interviews
     */
    public List<String> generateParenthesisDP(int n) {
        List<String> result = new ArrayList<>();
        if (n == 0) {
            result.add("");
            return result;
        }
        
        for (int c = 0; c < n; ++c) {
            for (String left : generateParenthesisDP(c)) {
                for (String right : generateParenthesisDP(n - 1 - c)) {
                    result.add("(" + left + ")" + right);
                }
            }
        }
        
        return result;
    }
    
    /**
     * APPROACH 4: BRUTE FORCE (FOR COMPARISON)
     * Generate all possible combinations and filter valid ones
     * NOT RECOMMENDED but good for discussion
     */
    public List<String> generateParenthesisBruteForce(int n) {
        List<String> combinations = new ArrayList<>();
        generateAllCombinations(new char[2 * n], 0, combinations);
        
        List<String> result = new ArrayList<>();
        for (String combination : combinations) {
            if (isValid(combination)) {
                result.add(combination);
            }
        }
        return result;
    }
    
    private void generateAllCombinations(char[] current, int pos, List<String> combinations) {
        if (pos == current.length) {
            combinations.add(new String(current));
            return;
        }
        
        current[pos] = '(';
        generateAllCombinations(current, pos + 1, combinations);
        
        current[pos] = ')';
        generateAllCombinations(current, pos + 1, combinations);
    }
    
    private boolean isValid(String s) {
        int balance = 0;
        for (char c : s.toCharArray()) {
            if (c == '(') {
                balance++;
            } else {
                balance--;
            }
            if (balance < 0) {
                return false;
            }
        }
        return balance == 0;
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Why backtracking is better than brute force?
     *    - Brute force: Generate 2^(2n) combinations, then filter
     *    - Backtracking: Only generate valid combinations (much fewer)
     * 
     * 2. Constraints for valid parentheses:
     *    - Never have more closing than opening at any point
     *    - Total opening brackets = Total closing brackets = n
     * 
     * 3. Optimization opportunities:
     *    - Use StringBuilder instead of String for efficiency
     *    - Memoization if needed (though not necessary here)
     * 
     * 4. Follow-up questions:
     *    - What if we had multiple types of brackets? [{()}]
     *    - What if we needed to remove invalid parentheses?
     *    - Can you solve it iteratively?
     * 
     * 5. Edge cases to consider:
     *    - n = 0 (should return [""])
     *    - n = 1 (should return ["()"])
     */
    
    public static void main(String[] args) {
        GenerateParentheses_Leetcode22 solution = new GenerateParentheses_Leetcode22();
        
        // Test cases
        for (int n = 0; n <= 4; n++) {
            System.out.println("n = " + n);
            System.out.println("Result: " + solution.generateParenthesis(n));
            System.out.println("Count: " + solution.generateParenthesis(n).size());
            System.out.println();
        }
        
        // Performance comparison
        System.out.println("Performance test for n = 3:");
        
        long start1 = System.nanoTime();
        List<String> result1 = solution.generateParenthesis(3);
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        List<String> result2 = solution.generateParenthesisBruteForce(3);
        long end2 = System.nanoTime();
        
        System.out.println("Backtracking time: " + (end1 - start1) + " ns");
        System.out.println("Brute force time: " + (end2 - start2) + " ns");
        System.out.println("Results match: " + result1.equals(result2));
    }
}
