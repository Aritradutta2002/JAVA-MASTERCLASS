package dsa.algorithms.recursion.problems.medium;

import java.util.ArrayList;
import java.util.List;

/**
 * GenerateParentheses - Classic backtracking problem.
 *
 * Problem: Given n pairs of parentheses, generate all combinations of well-formed parentheses.
 * Interview Angles:
 * - Validates understanding of pruning (cannot place more ')' than '(')
 * - Highlights state tracking with counts instead of string scanning
 * - Easy pivot to similar problems like binary strings with constraints
 */
public class GenerateParentheses {

    public static List<String> generateParenthesis(int n) {
        List<String> result = new ArrayList<>();
        backtrack(result, new StringBuilder(), n, 0, 0);
        return result;
    }

    private static void backtrack(List<String> result, StringBuilder slate, int n, int open, int close) {
        if (slate.length() == 2 * n) {
            result.add(slate.toString());
            return;
        }

        if (open < n) {
            slate.append('(');
            backtrack(result, slate, n, open + 1, close);
            slate.deleteCharAt(slate.length() - 1);
        }

        if (close < open) {
            slate.append(')');
            backtrack(result, slate, n, open, close + 1);
            slate.deleteCharAt(slate.length() - 1);
        }
    }

    public static void main(String[] args) {
        int pairs = 3;
        System.out.println("=== Generate Parentheses ===");
        System.out.println(generateParenthesis(pairs));
    }
}


