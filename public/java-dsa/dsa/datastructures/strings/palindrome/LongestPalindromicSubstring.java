package dsa.datastructures.strings.palindrome;

/**
 * Longest Palindromic Substring (LeetCode 5) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Find the longest palindromic substring
 * Multiple approaches with different trade-offs
 */
public class LongestPalindromicSubstring {
    
    // Approach 1: Expand Around Center - O(n²) time, O(1) space (Most Popular)
    public String longestPalindrome(String s) {
        if (s == null || s.length() < 1) return "";
        
        int start = 0, end = 0;
        
        for (int i = 0; i < s.length(); i++) {
            // Odd length palindrome
            int len1 = expandAroundCenter(s, i, i);
            // Even length palindrome
            int len2 = expandAroundCenter(s, i, i + 1);
            int len = Math.max(len1, len2);
            
            if (len > end - start) {
                start = i - (len - 1) / 2;
                end = i + len / 2;
            }
        }
        
        return s.substring(start, end + 1);
    }
    
    private int expandAroundCenter(String s, int left, int right) {
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        return right - left - 1;
    }
    
    // Approach 2: Dynamic Programming - O(n²) time, O(n²) space
    public String longestPalindromeDP(String s) {
        int n = s.length();
        boolean[][] dp = new boolean[n][n];
        int start = 0, maxLen = 1;
        
        // All single characters are palindromes
        for (int i = 0; i < n; i++) {
            dp[i][i] = true;
        }
        
        // Check for length 2
        for (int i = 0; i < n - 1; i++) {
            if (s.charAt(i) == s.charAt(i + 1)) {
                dp[i][i + 1] = true;
                start = i;
                maxLen = 2;
            }
        }
        
        // Check for lengths greater than 2
        for (int len = 3; len <= n; len++) {
            for (int i = 0; i < n - len + 1; i++) {
                int j = i + len - 1;
                
                if (s.charAt(i) == s.charAt(j) && dp[i + 1][j - 1]) {
                    dp[i][j] = true;
                    start = i;
                    maxLen = len;
                }
            }
        }
        
        return s.substring(start, start + maxLen);
    }
    
    // Approach 3: Manacher's Algorithm - O(n) time, O(n) space (Most Optimal)
    public String longestPalindromeManacher(String s) {
        if (s == null || s.length() == 0) return "";
        
        // Transform string: "abc" -> "^#a#b#c#$"
        StringBuilder sb = new StringBuilder("^");
        for (char c : s.toCharArray()) {
            sb.append("#").append(c);
        }
        sb.append("#$");
        String t = sb.toString();
        
        int n = t.length();
        int[] p = new int[n]; // p[i] = radius of palindrome centered at i
        int center = 0, right = 0;
        int maxLen = 0, centerIndex = 0;
        
        for (int i = 1; i < n - 1; i++) {
            int mirror = 2 * center - i;
            
            if (i < right) {
                p[i] = Math.min(right - i, p[mirror]);
            }
            
            // Expand around center i
            while (t.charAt(i + p[i] + 1) == t.charAt(i - p[i] - 1)) {
                p[i]++;
            }
            
            // Update center and right boundary
            if (i + p[i] > right) {
                center = i;
                right = i + p[i];
            }
            
            // Track maximum
            if (p[i] > maxLen) {
                maxLen = p[i];
                centerIndex = i;
            }
        }
        
        int start = (centerIndex - maxLen) / 2;
        return s.substring(start, start + maxLen);
    }
    
    public static void main(String[] args) {
        LongestPalindromicSubstring solution = new LongestPalindromicSubstring();
        
        // Test Case 1
        System.out.println(solution.longestPalindrome("babad")); // "bab" or "aba"
        
        // Test Case 2
        System.out.println(solution.longestPalindrome("cbbd")); // "bb"
        
        // Test Case 3
        System.out.println(solution.longestPalindromeManacher("racecar")); // "racecar"
    }
}

