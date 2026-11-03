package dsa.algorithms.greedy.strings;

import java.util.*;

/**
 * Remove K Digits (LeetCode 402) - MEDIUM
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Remove k digits to get smallest possible number
 * Time: O(n), Space: O(n)
 * 
 * Key: Use monotonic stack to keep digits in increasing order
 */
public class RemoveKDigits {
    
    public String removeKdigits(String num, int k) {
        if (k >= num.length()) return "0";
        
        Stack<Character> stack = new Stack<>();
        
        for (char digit : num.toCharArray()) {
            // Remove larger digits from stack
            while (!stack.isEmpty() && k > 0 && stack.peek() > digit) {
                stack.pop();
                k--;
            }
            stack.push(digit);
        }
        
        // Remove remaining k digits from end
        while (k > 0) {
            stack.pop();
            k--;
        }
        
        // Build result
        StringBuilder sb = new StringBuilder();
        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }
        sb.reverse();
        
        // Remove leading zeros
        while (sb.length() > 1 && sb.charAt(0) == '0') {
            sb.deleteCharAt(0);
        }
        
        return sb.length() == 0 ? "0" : sb.toString();
    }
    
    public static void main(String[] args) {
        RemoveKDigits solution = new RemoveKDigits();
        
        System.out.println(solution.removeKdigits("1432219", 3)); // "1219"
        System.out.println(solution.removeKdigits("10200", 1)); // "200"
        System.out.println(solution.removeKdigits("10", 2)); // "0"
    }
}

