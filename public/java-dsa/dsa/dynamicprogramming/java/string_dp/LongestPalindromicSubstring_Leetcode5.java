package JAVA_DSA.DYNAMIC_PROGRAMMING.java.string_dp;

/**
 * LONGEST PALINDROMIC SUBSTRING - Leetcode 5
 * Difficulty: Medium
 * Companies: Amazon, Microsoft, Google, Facebook, Apple, Bloomberg, Adobe
 * 
 * PROBLEM:
 * Given a string s, return the longest palindromic substring in s.
 * A palindrome reads the same forward and backward.
 * 
 * EXAMPLES:
 * Input: s = "babad"
 * Output: "bab"  (or "aba", both are valid)
 * 
 * Input: s = "cbbd"
 * Output: "bb"
 * 
 * PATTERN: 2D String DP or Expand Around Center
 * Multiple approaches possible - from brute force to optimal!
 */

public class LongestPalindromicSubstring_Leetcode5 {
    
    // ========== APPROACH 1: BRUTE FORCE (TLE) ==========
    // Time: O(n^3) - Check all substrings
    // Space: O(1)
    /**
     * BEGINNER'S EXPLANATION:
     * Check every possible substring if it's a palindrome.
     * Keep track of the longest one found.
     */
    public String longestPalindrome_BruteForce(String s) {
        int maxLen = 0;
        String result = "";
        
        // Try all substrings
        for (int i = 0; i < s.length(); i++) {
            for (int j = i; j < s.length(); j++) {
                String substring = s.substring(i, j + 1);
                if (isPalindrome(substring) && substring.length() > maxLen) {
                    maxLen = substring.length();
                    result = substring;
                }
            }
        }
        
        return result;
    }
    
    private boolean isPalindrome(String s) {
        int left = 0, right = s.length() - 1;
        while (left < right) {
            if (s.charAt(left) != s.charAt(right)) return false;
            left++;
            right--;
        }
        return true;
    }
    
    
    // ========== APPROACH 2: EXPAND AROUND CENTER ⭐ MOST INTUITIVE ==========
    // Time: O(n^2) - Expand from each center
    // Space: O(1)
    /**
     * BEGINNER'S EXPLANATION:
     * Key insight: Every palindrome has a CENTER!
     * 
     * For "babad":
     * - Center at 'b': expand → "b"
     * - Center at 'a': expand → "bab" ✓
     * - Center at 'b': expand → "aba" ✓
     * - Center at 'a': expand → "a"
     * - Center at 'd': expand → "d"
     * 
     * IMPORTANT: Handle both odd and even length palindromes:
     * - Odd length: Single character center (like "aba")
     * - Even length: Two character center (like "abba")
     * 
     * Visualization:
     * Odd:  a b a → expand from 'b'
     *       ← →
     * Even: a b b a → expand from 'bb'
     *         ← →
     */
    public String longestPalindrome_ExpandCenter(String s) {
        if (s == null || s.length() < 1) return "";
        
        int start = 0, end = 0;
        
        for (int i = 0; i < s.length(); i++) {
            // Odd length palindrome (center is one character)
            int len1 = expandAroundCenter(s, i, i);
            
            // Even length palindrome (center is between two characters)
            int len2 = expandAroundCenter(s, i, i + 1);
            
            // Take maximum of both
            int len = Math.max(len1, len2);
            
            // Update if found longer palindrome
            if (len > end - start) {
                start = i - (len - 1) / 2;
                end = i + len / 2;
            }
        }
        
        return s.substring(start, end + 1);
    }
    
    private int expandAroundCenter(String s, int left, int right) {
        // Expand while characters match and within bounds
        while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
            left--;
            right++;
        }
        // Return length of palindrome
        return right - left - 1;
    }
    
    
    // ========== APPROACH 3: 2D DP (CLASSIC DP SOLUTION) ==========
    // Time: O(n^2)
    // Space: O(n^2)
    /**
     * BEGINNER'S EXPLANATION:
     * Build 2D table where dp[i][j] = true if s[i:j+1] is palindrome
     * 
     * Recurrence:
     * - If s[i] == s[j] AND inner substring is palindrome
     *   → dp[i][j] = true
     * 
     * DP Table for s = "babad":
     *       b  a  b  a  d
     *    b  T  F  T  F  F
     *    a     T  F  T  F
     *    b        T  F  F
     *    a           T  F
     *    d              T
     * 
     * Fill diagonally from bottom-left to top-right!
     * 
     * Base cases:
     * - Single character: always palindrome
     * - Two characters: palindrome if same
     * 
     * Build from smaller lengths to larger lengths.
     */
    public String longestPalindrome_DP(String s) {
        int n = s.length();
        if (n < 2) return s;
        
        boolean[][] dp = new boolean[n][n];
        int maxLen = 1;
        int start = 0;
        
        // Every single character is a palindrome
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
                int j = i + len - 1;  // Ending index
                
                // Check if s[i:j+1] is palindrome
                if (s.charAt(i) == s.charAt(j) && dp[i + 1][j - 1]) {
                    dp[i][j] = true;
                    start = i;
                    maxLen = len;
                }
            }
        }
        
        return s.substring(start, start + maxLen);
    }
    
    
    // ========== APPROACH 4: MANACHER'S ALGORITHM (OPTIMAL) ==========
    // Time: O(n) - Linear time!
    // Space: O(n)
    /**
     * ADVANCED EXPLANATION:
     * This is the OPTIMAL solution but complex to understand.
     * Mention this exists but implement expand-around-center in interviews!
     * 
     * Key idea: Use previously computed palindrome information to avoid redundant checks.
     * Transform string to handle both odd and even length palindromes uniformly.
     * 
     * Transform: "babad" → "^#b#a#b#a#d#$"
     * (^ and $ are sentinels, # between characters)
     * 
     * Not recommended for interviews unless specifically asked for O(n) solution!
     */
    public String longestPalindrome_Manacher(String s) {
        if (s == null || s.length() == 0) return "";
        
        // Transform string
        String t = preprocess(s);
        int n = t.length();
        int[] p = new int[n];  // p[i] = radius of palindrome centered at i
        int center = 0, right = 0;
        
        for (int i = 1; i < n - 1; i++) {
            int mirror = 2 * center - i;
            
            if (right > i) {
                p[i] = Math.min(right - i, p[mirror]);
            }
            
            // Expand around center i
            while (t.charAt(i + 1 + p[i]) == t.charAt(i - 1 - p[i])) {
                p[i]++;
            }
            
            // Update center and right boundary
            if (i + p[i] > right) {
                center = i;
                right = i + p[i];
            }
        }
        
        // Find longest palindrome
        int maxLen = 0;
        int centerIndex = 0;
        for (int i = 1; i < n - 1; i++) {
            if (p[i] > maxLen) {
                maxLen = p[i];
                centerIndex = i;
            }
        }
        
        int start = (centerIndex - maxLen) / 2;
        return s.substring(start, start + maxLen);
    }
    
    private String preprocess(String s) {
        StringBuilder sb = new StringBuilder("^");
        for (char c : s.toCharArray()) {
            sb.append("#").append(c);
        }
        sb.append("#$");
        return sb.toString();
    }
    
    
    // ========== RECOMMENDED SOLUTION ⭐ ==========
    // Use Expand Around Center - Best balance of simplicity and efficiency
    public String longestPalindrome(String s) {
        return longestPalindrome_ExpandCenter(s);
    }
    
    
    // ========== HELPER: VISUALIZE ALL PALINDROMES ==========
    public void visualizePalindromes(String s) {
        System.out.println("String: " + s);
        System.out.println("\nAll palindromic substrings:");
        
        int maxLen = 0;
        String longest = "";
        
        for (int i = 0; i < s.length(); i++) {
            // Odd length
            int len1 = expandAroundCenter(s, i, i);
            if (len1 > 1) {
                String pal = s.substring(i - (len1 - 1) / 2, i + len1 / 2 + 1);
                System.out.println("  Center at " + i + ": " + pal + " (length " + len1 + ")");
                if (len1 > maxLen) {
                    maxLen = len1;
                    longest = pal;
                }
            }
            
            // Even length
            if (i < s.length() - 1 && s.charAt(i) == s.charAt(i + 1)) {
                int len2 = expandAroundCenter(s, i, i + 1);
                if (len2 > 1) {
                    String pal = s.substring(i - (len2 - 2) / 2, i + len2 / 2 + 1);
                    System.out.println("  Center between " + i + " and " + (i + 1) + ": " + pal + " (length " + len2 + ")");
                    if (len2 > maxLen) {
                        maxLen = len2;
                        longest = pal;
                    }
                }
            }
        }
        
        System.out.println("\nLongest Palindromic Substring: " + longest);
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        LongestPalindromicSubstring_Leetcode5 solution = new LongestPalindromicSubstring_Leetcode5();
        
        // Test case 1
        String s1 = "babad";
        System.out.println("Input: " + s1);
        System.out.println("Output: " + solution.longestPalindrome(s1));
        solution.visualizePalindromes(s1);
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 2
        String s2 = "cbbd";
        System.out.println("Input: " + s2);
        System.out.println("Output: " + solution.longestPalindrome(s2));
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 3
        String s3 = "racecar";
        System.out.println("Input: " + s3);
        System.out.println("Output: " + solution.longestPalindrome(s3));
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. RECOMMENDED APPROACH:
 *    - Use "Expand Around Center" - O(n^2) time, O(1) space
 *    - Easy to explain and code
 *    - Efficient enough for interviews
 * 
 * 2. EXPAND AROUND CENTER LOGIC:
 *    - Every palindrome has a center
 *    - Odd length: center is one character
 *    - Even length: center is between two characters
 *    - Try both cases for each position!
 * 
 * 3. WHY EXPAND AROUND CENTER WORKS:
 *    - Palindromes are symmetric around center
 *    - Start from center and expand outward
 *    - Stop when characters don't match
 *    - Linear expansion for each center
 * 
 * 4. DP APPROACH:
 *    - Good for understanding the problem
 *    - dp[i][j] = is s[i:j+1] a palindrome?
 *    - Build from smaller substrings to larger
 *    - More space (O(n^2)) but easier to understand
 * 
 * 5. MANACHER'S ALGORITHM:
 *    - Optimal O(n) solution
 *    - Very complex to explain and code
 *    - Only mention if interviewer asks for O(n)
 *    - Focus on expand-around-center in interviews
 * 
 * 6. FOLLOW-UP QUESTIONS:
 *    - Count all palindromic substrings? → Leetcode 647
 *    - Longest palindromic subsequence? → Leetcode 516
 *    - Check if can form palindrome? → Leetcode 266
 *    - Minimum insertions to make palindrome? → Leetcode 1312
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 5: Longest Palindromic Substring (this)
 *    - Leetcode 516: Longest Palindromic Subsequence
 *    - Leetcode 647: Palindromic Substrings (count)
 *    - Leetcode 131: Palindrome Partitioning
 *    - Leetcode 125: Valid Palindrome (easy version)
 * 
 * 8. INTERVIEW STRATEGY:
 *    - Start with brute force explanation
 *    - Mention expand around center approach
 *    - Implement expand around center
 *    - Handle both odd and even cases
 *    - Mention DP and Manacher's exist
 * 
 * 9. COMMON MISTAKES:
 *    - Forgetting to check even-length palindromes
 *    - Off-by-one errors in substring indices
 *    - Not handling empty string
 *    - Confusing substring vs subsequence
 *    - Incorrect calculation of start/end indices
 * 
 * 10. EDGE CASES:
 *     - Empty string: return ""
 *     - Single character: return that character
 *     - No palindrome > 1: return any single character
 *     - All same characters: return entire string
 *     - Two equal characters: return both
 * 
 * 11. COMPLEXITY COMPARISON:
 *     Brute Force:        O(n^3) time, O(1) space
 *     Expand Center:      O(n^2) time, O(1) space ⭐ BEST
 *     2D DP:              O(n^2) time, O(n^2) space
 *     Manacher's:         O(n) time, O(n) space (complex!)
 */

