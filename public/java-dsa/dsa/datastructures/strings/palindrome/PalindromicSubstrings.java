package dsa.datastructures.strings.palindrome;

/**
 * Palindromic Substrings (LeetCode 647) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Count all palindromic substrings
 * Time: O(n²), Space: O(1)
 */
public class PalindromicSubstrings {
    
    // Approach 1: Expand Around Center
    public int countSubstrings(String s) {
        int count = 0;
        
        for (int i = 0; i < s.length(); i++) {
            // Odd length palindromes
            count += expandAroundCenter(s, i, i);
            // Even length palindromes
            count += expandAroundCenter(s, i, i + 1);
        }
        
        return count;
    }
    
    private int expandAroundCenter(String s, int left, int right) {
        int count = 0;
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            count++;
            left--;
            right++;
        }
        return count;
    }
    
    // Approach 2: Dynamic Programming
    public int countSubstringsDP(String s) {
        int n = s.length();
        boolean[][] dp = new boolean[n][n];
        int count = 0;
        
        // Single characters
        for (int i = 0; i < n; i++) {
            dp[i][i] = true;
            count++;
        }
        
        // Two characters
        for (int i = 0; i < n - 1; i++) {
            if (s.charAt(i) == s.charAt(i + 1)) {
                dp[i][i + 1] = true;
                count++;
            }
        }
        
        // Longer substrings
        for (int len = 3; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                int j = i + len - 1;
                if (s.charAt(i) == s.charAt(j) && dp[i + 1][j - 1]) {
                    dp[i][j] = true;
                    count++;
                }
            }
        }
        
        return count;
    }
    
    public static void main(String[] args) {
        PalindromicSubstrings solution = new PalindromicSubstrings();
        
        System.out.println(solution.countSubstrings("abc")); // 3
        System.out.println(solution.countSubstrings("aaa")); // 6
    }
}

