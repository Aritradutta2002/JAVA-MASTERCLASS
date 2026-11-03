package dsa.datastructures.strings.parentheses;

import java.util.*;

/**
 * Generate Parentheses (LeetCode 22) - MEDIUM
 * FAANG Frequency: Very High (Google, Amazon, Facebook)
 * 
 * Problem: Generate all valid combinations of n pairs of parentheses
 * Time: O(4^n / sqrt(n)), Space: O(n)
 */
public class GenerateParentheses {
    
    // Approach 1: Backtracking
    public List<String> generateParenthesis(int n) {
        List<String> result = new ArrayList<>();
        backtrack(result, new StringBuilder(), 0, 0, n);
        return result;
    }
    
    private void backtrack(List<String> result, StringBuilder current, 
                          int open, int close, int max) {
        if (current.length() == max * 2) {
            result.add(current.toString());
            return;
        }
        
        if (open < max) {
            current.append('(');
            backtrack(result, current, open + 1, close, max);
            current.deleteCharAt(current.length() - 1);
        }
        
        if (close < open) {
            current.append(')');
            backtrack(result, current, open, close + 1, max);
            current.deleteCharAt(current.length() - 1);
        }
    }
    
    // Approach 2: Dynamic Programming
    public List<String> generateParenthesisDP(int n) {
        List<List<String>> dp = new ArrayList<>();
        dp.add(Arrays.asList(""));
        
        for (int i = 1; i <= n; i++) {
            List<String> list = new ArrayList<>();
            for (int j = 0; j < i; j++) {
                for (String left : dp.get(j)) {
                    for (String right : dp.get(i - 1 - j)) {
                        list.add("(" + left + ")" + right);
                    }
                }
            }
            dp.add(list);
        }
        
        return dp.get(n);
    }
    
    public static void main(String[] args) {
        GenerateParentheses solution = new GenerateParentheses();
        
        System.out.println(solution.generateParenthesis(3));
        // ["((()))","(()())","(())()","()(())","()()()"]
    }
}

