package dsa.algorithms.backtracking.easy;

import java.util.*;

/**
 * LETTER CASE PERMUTATION - LeetCode 784 (EASY)
 * 
 * Problem: Given a string s, you can transform every letter individually to be 
 * lowercase or uppercase to create another string. Return a list of all possible 
 * strings we could create.
 * 
 * Example:
 * Input: s = "a1b2"
 * Output: ["a1b2","a1B2","A1b2","A1B2"]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐
 * - Great introduction to backtracking
 * - Tests understanding of choose/don't choose pattern
 * - Good warm-up problem for harder backtracking questions
 * 
 * Time Complexity: O(2^L * N) where L = number of letters, N = string length
 * Space Complexity: O(2^L * N) for storing all permutations
 */
public class LetterCasePermutation_Leetcode784 {
    
    /**
     * APPROACH 1: BACKTRACKING - MOST INTUITIVE
     * For each letter, we have 2 choices: lowercase or uppercase
     */
    public List<String> letterCasePermutation(String s) {
        List<String> result = new ArrayList<>();
        backtrack(result, s.toCharArray(), 0);
        return result;
    }
    
    private void backtrack(List<String> result, char[] chars, int index) {
        // Base case: processed all characters
        if (index == chars.length) {
            result.add(new String(chars));
            return;
        }
        
        // If current character is a digit, just move to next
        if (Character.isDigit(chars[index])) {
            backtrack(result, chars, index + 1);
        } else {
            // For letters, try both lowercase and uppercase
            
            // Choice 1: lowercase
            chars[index] = Character.toLowerCase(chars[index]);
            backtrack(result, chars, index + 1);
            
            // Choice 2: uppercase  
            chars[index] = Character.toUpperCase(chars[index]);
            backtrack(result, chars, index + 1);
        }
    }
    
    /**
     * APPROACH 2: BFS/ITERATIVE APPROACH
     * Build permutations level by level
     */
    public List<String> letterCasePermutationIterative(String s) {
        List<String> result = new ArrayList<>();
        result.add(s);
        
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                int size = result.size();
                
                // For each existing string, create a new version with toggled case
                for (int j = 0; j < size; j++) {
                    char[] chars = result.get(j).toCharArray();
                    chars[i] = Character.isLowerCase(chars[i]) ? 
                              Character.toUpperCase(chars[i]) : 
                              Character.toLowerCase(chars[i]);
                    result.add(new String(chars));
                }
            }
        }
        
        return result;
    }
    
    /**
     * APPROACH 3: BIT MANIPULATION
     * Use binary representation to determine case
     */
    public List<String> letterCasePermutationBitMask(String s) {
        List<String> result = new ArrayList<>();
        
        // Find all letter positions
        List<Integer> letterIndices = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                letterIndices.add(i);
            }
        }
        
        int numLetters = letterIndices.size();
        
        // Generate all possible combinations using bit masks
        for (int mask = 0; mask < (1 << numLetters); mask++) {
            char[] chars = s.toCharArray();
            
            for (int i = 0; i < numLetters; i++) {
                int index = letterIndices.get(i);
                if ((mask & (1 << i)) == 0) {
                    chars[index] = Character.toLowerCase(chars[index]);
                } else {
                    chars[index] = Character.toUpperCase(chars[index]);
                }
            }
            
            result.add(new String(chars));
        }
        
        return result;
    }
    
    /**
     * APPROACH 4: RECURSIVE WITH STRING BUILDING
     * Alternative recursive approach building strings
     */
    public List<String> letterCasePermutationRecursive(String s) {
        List<String> result = new ArrayList<>();
        helper(s, 0, "", result);
        return result;
    }
    
    private void helper(String s, int index, String current, List<String> result) {
        if (index == s.length()) {
            result.add(current);
            return;
        }
        
        char c = s.charAt(index);
        
        if (Character.isDigit(c)) {
            helper(s, index + 1, current + c, result);
        } else {
            // Try lowercase
            helper(s, index + 1, current + Character.toLowerCase(c), result);
            // Try uppercase
            helper(s, index + 1, current + Character.toUpperCase(c), result);
        }
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Problem analysis:
     *    - Only letters can be transformed (digits stay the same)
     *    - Each letter has exactly 2 choices
     *    - Total combinations = 2^(number of letters)
     * 
     * 2. Why backtracking works:
     *    - Decision tree: for each letter, choose case
     *    - Can prune early if needed (though not necessary here)
     * 
     * 3. Optimization considerations:
     *    - In-place modification vs string building
     *    - Character array manipulation is more efficient
     * 
     * 4. Follow-up questions:
     *    - What if we only wanted permutations with specific patterns?
     *    - How would you handle Unicode characters?
     *    - Can you solve it without recursion?
     * 
     * 5. Edge cases:
     *    - Empty string
     *    - String with only digits
     *    - String with only letters
     *    - Mixed case input
     */
    
    public static void main(String[] args) {
        LetterCasePermutation_Leetcode784 solution = new LetterCasePermutation_Leetcode784();
        
        // Test case 1
        String s1 = "a1b2";
        System.out.println("Input: " + s1);
        System.out.println("Output: " + solution.letterCasePermutation(s1));
        System.out.println();
        
        // Test case 2
        String s2 = "3z4";
        System.out.println("Input: " + s2);
        System.out.println("Output: " + solution.letterCasePermutation(s2));
        System.out.println();
        
        // Test case 3
        String s3 = "12345";
        System.out.println("Input: " + s3);
        System.out.println("Output: " + solution.letterCasePermutation(s3));
        System.out.println();
        
        // Test case 4 - Edge case
        String s4 = "";
        System.out.println("Input: " + s4);
        System.out.println("Output: " + solution.letterCasePermutation(s4));
        System.out.println();
        
        // Compare different approaches
        String test = "abc";
        System.out.println("Comparing approaches for input: " + test);
        System.out.println("Backtrack: " + solution.letterCasePermutation(test));
        System.out.println("Iterative: " + solution.letterCasePermutationIterative(test));
        System.out.println("BitMask: " + solution.letterCasePermutationBitMask(test));
        System.out.println("Recursive: " + solution.letterCasePermutationRecursive(test));
    }
}
