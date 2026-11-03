package dsa.datastructures.strings.palindrome;

/**
 * Valid Palindrome I & II (LeetCode 125, 680) - EASY/MEDIUM
 * FAANG Frequency: Very High (Amazon, Facebook, Microsoft)
 * 
 * Problem: Check if string is palindrome (with variations)
 */
public class ValidPalindrome {
    
    // Valid Palindrome I - Ignore non-alphanumeric, case-insensitive
    public boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        
        while (left < right) {
            while (left < right && !Character.isLetterOrDigit(s.charAt(left))) {
                left++;
            }
            while (left < right && !Character.isLetterOrDigit(s.charAt(right))) {
                right--;
            }
            
            if (Character.toLowerCase(s.charAt(left)) != Character.toLowerCase(s.charAt(right))) {
                return false;
            }
            
            left++;
            right--;
        }
        
        return true;
    }
    
    // Valid Palindrome II - Can delete at most one character
    public boolean validPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                // Try deleting left or right character
                return isPalindromeRange(s, left + 1, right) || 
                       isPalindromeRange(s, left, right - 1);
            }
            left++;
            right--;
        }
        
        return true;
    }
    
    private boolean isPalindromeRange(String s, int left, int right) {
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }
    
    // Valid Palindrome III - Can delete at most k characters (DP)
    public boolean isValidPalindrome(String s, int k) {
        int n = s.length();
        int[][] dp = new int[n][n];
        
        // dp[i][j] = minimum deletions to make s[i..j] palindrome
        for (int len = 2; len <= n; len++) {
            for (int i = 0; i <= n - len; i++) {
                int j = i + len - 1;
                
                if (s.charAt(i) == s.charAt(j)) {
                    dp[i][j] = dp[i + 1][j - 1];
                } else {
                    dp[i][j] = 1 + Math.min(dp[i + 1][j], dp[i][j - 1]);
                }
            }
        }
        
        return dp[0][n - 1] <= k;
    }
    
    public static void main(String[] args) {
        ValidPalindrome solution = new ValidPalindrome();
        
        // Test Case 1
        System.out.println(solution.isPalindrome("A man, a plan, a canal: Panama")); // true
        
        // Test Case 2
        System.out.println(solution.validPalindrome("abca")); // true
        
        // Test Case 3
        System.out.println(solution.isValidPalindrome("abcdeca", 2)); // true
    }
}

