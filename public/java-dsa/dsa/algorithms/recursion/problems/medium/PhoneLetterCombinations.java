package dsa.algorithms.recursion.problems.medium;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * PhoneLetterCombinations - Backtracking on digit-to-letter mapping.
 *
 * Interview Angles:
 * - Practice mapping from input digits to branching characters
 * - Demonstrates recursion on strings with variable branching factor
 * - Foundation for problems like T9 predictive text or generating mnemonics
 */
public class PhoneLetterCombinations {

    private static final Map<Character, String> DIGIT_TO_LETTERS = new HashMap<>();

    static {
        DIGIT_TO_LETTERS.put('2', "abc");
        DIGIT_TO_LETTERS.put('3', "def");
        DIGIT_TO_LETTERS.put('4', "ghi");
        DIGIT_TO_LETTERS.put('5', "jkl");
        DIGIT_TO_LETTERS.put('6', "mno");
        DIGIT_TO_LETTERS.put('7', "pqrs");
        DIGIT_TO_LETTERS.put('8', "tuv");
        DIGIT_TO_LETTERS.put('9', "wxyz");
    }

    public static List<String> letterCombinations(String digits) {
        List<String> result = new ArrayList<>();
        if (digits == null || digits.isEmpty()) {
            return result;
        }
        backtrack(digits, 0, new StringBuilder(), result);
        return result;
    }

    private static void backtrack(String digits, int index, StringBuilder slate, List<String> result) {
        if (index == digits.length()) {
            result.add(slate.toString());
            return;
        }
        String letters = DIGIT_TO_LETTERS.getOrDefault(digits.charAt(index), "");
        for (char letter : letters.toCharArray()) {
            slate.append(letter);
            backtrack(digits, index + 1, slate, result);
            slate.deleteCharAt(slate.length() - 1);
        }
    }

    public static void main(String[] args) {
        String digits = "23";
        System.out.println("=== Phone Letter Combinations ===");
        System.out.println(letterCombinations(digits));
    }
}


