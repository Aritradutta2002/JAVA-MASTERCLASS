package dsa.datastructures.strings.advanced;
/**
 * MANACHER'S ALGORITHM
 * Longest Palindromic Substring in O(n) time
 * 
 * CP Essential: Handles all centers in linear time using transformed string with separators.
 */

import java.util.*;

public class ManacherAlgorithm {
    // Transform s into T with separators to handle even/odd palindromes uniformly
    private String transform(String s) {
        StringBuilder sb = new StringBuilder();
        sb.append('^');
        for (int i = 0; i < s.length(); i++) {
            sb.append('#').append(s.charAt(i));
        }
        sb.append('#').append('$');
        return sb.toString();
    }

    // Return longest palindromic substring
    public String longestPalindrome(String s) {
        if (s == null || s.length() == 0) return "";
        String T = transform(s);
        int n = T.length();
        int[] P = new int[n];
        int center = 0, right = 0;

        for (int i = 1; i < n - 1; i++) {
            int mirror = 2 * center - i;
            if (i < right) P[i] = Math.min(right - i, P[mirror]);

            // Expand around i
            while (T.charAt(i + 1 + P[i]) == T.charAt(i - 1 - P[i])) P[i]++;

            // Update center and right
            if (i + P[i] > right) {
                center = i;
                right = i + P[i];
            }
        }

        // Find max
        int maxLen = 0; int centerIndex = 0;
        for (int i = 1; i < n - 1; i++) {
            if (P[i] > maxLen) { maxLen = P[i]; centerIndex = i; }
        }
        int start = (centerIndex - maxLen) / 2; // map back to s index
        return s.substring(start, start + maxLen);
    }

    // Count palindromic substrings in O(n)
    public long countPalindromes(String s) {
        String T = transform(s);
        int n = T.length();
        int[] P = new int[n];
        int center = 0, right = 0;
        long total = 0;
        for (int i = 1; i < n - 1; i++) {
            int mirror = 2 * center - i;
            if (i < right) P[i] = Math.min(right - i, P[mirror]);
            while (T.charAt(i + 1 + P[i]) == T.charAt(i - 1 - P[i])) P[i]++;
            if (i + P[i] > right) { center = i; right = i + P[i]; }
            total += P[i] / 2; // each two steps in T equals 1 in s
        }
        return total;
    }

    public static void main(String[] args) {
        ManacherAlgorithm m = new ManacherAlgorithm();
        System.out.println("=== Manacher's Algorithm ===");
        System.out.println(m.longestPalindrome("babad") + " (expected: bab or aba)");
        System.out.println(m.longestPalindrome("cbbd") + " (expected: bb)");
        System.out.println("Count pal substrings in 'aaa': " + m.countPalindromes("aaa") + " (expected: 6)");
    }
}

