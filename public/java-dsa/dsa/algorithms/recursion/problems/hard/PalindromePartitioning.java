package dsa.algorithms.recursion.problems.hard;

import java.util.ArrayList;
import java.util.List;

/**
 * PalindromePartitioning - Partition string into palindrome substrings.
 *
 * Interview Angles:
 * - Demonstrates backtracking with string slicing and palindrome checks
 * - Opportunity to discuss memoization of palindrome states for optimization
 * - Leads into DP variations (minimum cuts for palindrome partitioning)
 */
public class PalindromePartitioning {

    public static List<List<String>> partition(String s) {
        List<List<String>> result = new ArrayList<>();
        backtrack(s, 0, new ArrayList<>(), result);
        return result;
    }

    private static void backtrack(String s, int index, List<String> slate, List<List<String>> result) {
        if (index == s.length()) {
            result.add(new ArrayList<>(slate));
            return;
        }

        for (int end = index; end < s.length(); end++) {
            if (isPalindrome(s, index, end)) {
                slate.add(s.substring(index, end + 1));
                backtrack(s, end + 1, slate, result);
                slate.remove(slate.size() - 1);
            }
        }
    }

    private static boolean isPalindrome(String s, int left, int right) {
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    public static void main(String[] args) {
        String input = "aab";
        System.out.println("=== Palindrome Partitioning ===");
        System.out.println(partition(input));
    }
}


