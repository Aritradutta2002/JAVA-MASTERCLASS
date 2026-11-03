package dsa.datastructures.strings.parentheses;

import java.util.*;

/**
 * Longest Valid Parentheses (LeetCode 32) - HARD
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Problem: Find length of longest valid parentheses substring
 */
public class LongestValidParentheses {
    
    // Approach 1: Stack
    public int longestValidParentheses(String s) {
        Stack<Integer> stack = new Stack<>();
        stack.push(-1);
        int maxLen = 0;
        
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') {
                stack.push(i);
            } else {
                stack.pop();
                if (stack.isEmpty()) {
                    stack.push(i);
                } else {
                    maxLen = Math.max(maxLen, i - stack.peek());
                }
            }
        }
        
        return maxLen;
    }
    
    // Approach 2: Dynamic Programming
    public int longestValidParenthesesDP(String s) {
        int n = s.length();
        int[] dp = new int[n];
        int maxLen = 0;
        
        for (int i = 1; i < n; i++) {
            if (s.charAt(i) == ')') {
                if (s.charAt(i - 1) == '(') {
                    dp[i] = (i >= 2 ? dp[i - 2] : 0) + 2;
                } else if (i - dp[i - 1] > 0 && 
                          s.charAt(i - dp[i - 1] - 1) == '(') {
                    dp[i] = dp[i - 1] + 2 + 
                           (i - dp[i - 1] >= 2 ? dp[i - dp[i - 1] - 2] : 0);
                }
                maxLen = Math.max(maxLen, dp[i]);
            }
        }
        
        return maxLen;
    }
    
    // Approach 3: Two Pass (Most Optimal - O(1) space)
    public int longestValidParenthesesTwoPass(String s) {
        int left = 0, right = 0, maxLen = 0;
        
        // Left to right
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '(') {
                left++;
            } else {
                right++;
            }
            
            if (left == right) {
                maxLen = Math.max(maxLen, 2 * right);
            } else if (right > left) {
                left = right = 0;
            }
        }
        
        left = right = 0;
        
        // Right to left
        for (int i = s.length() - 1; i >= 0; i--) {
            if (s.charAt(i) == '(') {
                left++;
            } else {
                right++;
            }
            
            if (left == right) {
                maxLen = Math.max(maxLen, 2 * left);
            } else if (left > right) {
                left = right = 0;
            }
        }
        
        return maxLen;
    }
    
    public static void main(String[] args) {
        LongestValidParentheses solution = new LongestValidParentheses();
        
        System.out.println(solution.longestValidParentheses("(()")); // 2
        System.out.println(solution.longestValidParentheses(")()())")); // 4
        System.out.println(solution.longestValidParentheses("")); // 0
    }
}

