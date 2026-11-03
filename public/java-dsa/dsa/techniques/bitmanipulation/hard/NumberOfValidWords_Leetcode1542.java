package dsa.techniques.bitmanipulation.hard;
/**
 * LeetCode 1542: Find Longest Awesome Substring
 * Difficulty: Hard
 * 
 * You are given a string s. An awesome substring is a non-empty substring of s such that 
 * we can rearrange the characters of this substring to form a palindrome.
 * Return the length of the maximum length awesome substring of s.
 * 
 * Example 1:
 * Input: s = "3242415"
 * Output: 5
 * Explanation: "24241" is the longest awesome substring, we can form the palindrome "24142" with it.
 * 
 * Example 2:
 * Input: s = "12345678"
 * Output: 1
 * 
 * Example 3:
 * Input: s = "213123"
 * Output: 6
 * Explanation: "213123" is the longest awesome substring, we can form the palindrome "231132" with it.
 * 
 * Constraints:
 * - 1 <= s.length <= 10^5
 * - s consists only of digits.
 */
public class NumberOfValidWords_Leetcode1542 {
    
    /**
     * Approach: Bit Manipulation with Prefix State
     * 
     * Key insight: For a substring to form a palindrome, at most one digit can have odd frequency.
     * We can use a bitmask to represent the parity (odd/even) of digit frequencies.
     * 
     * Time: O(n), Space: O(min(n, 2^10)) = O(n)
     */
    public int longestAwesome(String s) {
        int n = s.length();
        
        // Map to store first occurrence of each bitmask
        java.util.Map<Integer, Integer> firstOccurrence = new java.util.HashMap<>();
        firstOccurrence.put(0, -1); // Empty prefix
        
        int mask = 0; // Current bitmask representing digit parities
        int maxLength = 0;
        
        for (int i = 0; i < n; i++) {
            int digit = s.charAt(i) - '0';
            
            // Toggle bit for current digit
            mask ^= (1 << digit);
            
            // Case 1: Current mask seen before (all digits have even frequency)
            if (firstOccurrence.containsKey(mask)) {
                maxLength = Math.max(maxLength, i - firstOccurrence.get(mask));
            } else {
                firstOccurrence.put(mask, i);
            }
            
            // Case 2: Check if any single digit has odd frequency
            for (int d = 0; d < 10; d++) {
                int targetMask = mask ^ (1 << d);
                if (firstOccurrence.containsKey(targetMask)) {
                    maxLength = Math.max(maxLength, i - firstOccurrence.get(targetMask));
                }
            }
        }
        
        return maxLength;
    }
    
    /**
     * Alternative implementation with array instead of HashMap
     * More efficient for this specific problem since we have at most 2^10 states
     */
    public int longestAwesomeOptimized(String s) {
        int n = s.length();
        
        // Array to store first occurrence of each bitmask (-2 means not seen)
        int[] firstOccurrence = new int[1024]; // 2^10 possible masks
        java.util.Arrays.fill(firstOccurrence, -2);
        firstOccurrence[0] = -1; // Empty prefix
        
        int mask = 0;
        int maxLength = 0;
        
        for (int i = 0; i < n; i++) {
            int digit = s.charAt(i) - '0';
            mask ^= (1 << digit);
            
            // Case 1: All digits have even frequency
            if (firstOccurrence[mask] != -2) {
                maxLength = Math.max(maxLength, i - firstOccurrence[mask]);
            } else {
                firstOccurrence[mask] = i;
            }
            
            // Case 2: Exactly one digit has odd frequency
            for (int d = 0; d < 10; d++) {
                int targetMask = mask ^ (1 << d);
                if (firstOccurrence[targetMask] != -2) {
                    maxLength = Math.max(maxLength, i - firstOccurrence[targetMask]);
                }
            }
        }
        
        return maxLength;
    }
    
    /**
     * Helper method to check if a string can form a palindrome
     */
    public boolean canFormPalindrome(String s) {
        int mask = 0;
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) {
                mask ^= (1 << (c - '0'));
            }
        }
        
        // At most one bit should be set (at most one odd frequency)
        return Integer.bitCount(mask) <= 1;
    }
    
    /**
     * Brute force solution for verification (O(n^3))
     */
    public int longestAwesomeBruteForce(String s) {
        int n = s.length();
        int maxLength = 0;
        
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                String substring = s.substring(i, j + 1);
                if (canFormPalindrome(substring)) {
                    maxLength = Math.max(maxLength, substring.length());
                }
            }
        }
        
        return maxLength;
    }
    
    /**
     * Utility method to explain the bit manipulation approach
     */
    public void explainBitMask(String s) {
        System.out.println("Explaining bit mask approach for: " + s);
        
        int mask = 0;
        System.out.println("Position | Char | Digit | Mask | Binary");
        System.out.println("---------|------|-------|------|--------");
        
        for (int i = 0; i < s.length(); i++) {
            int digit = s.charAt(i) - '0';
            mask ^= (1 << digit);
            
            System.out.printf("%8d | %4c | %5d | %4d | %s%n", 
                i, s.charAt(i), digit, mask, 
                String.format("%10s", Integer.toBinaryString(mask)).replace(' ', '0'));
        }
        
        System.out.println("\nFinal mask: " + mask);
        System.out.println("Set bits (odd frequencies): " + Integer.bitCount(mask));
        System.out.println("Can form palindrome: " + (Integer.bitCount(mask) <= 1));
        System.out.println();
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        NumberOfValidWords_Leetcode1542 solution = new NumberOfValidWords_Leetcode1542();
        
        // Test cases
        String[] testCases = {
            "3242415",  // Expected: 5
            "12345678", // Expected: 1
            "213123",   // Expected: 6
            "00",       // Expected: 2
            "1234",     // Expected: 1
            "1122",     // Expected: 4
            "12321"     // Expected: 5
        };
        
        System.out.println("Testing Longest Awesome Substring:");
        for (String s : testCases) {
            long startTime = System.nanoTime();
            int result1 = solution.longestAwesome(s);
            long time1 = System.nanoTime() - startTime;
            
            startTime = System.nanoTime();
            int result2 = solution.longestAwesomeOptimized(s);
            long time2 = System.nanoTime() - startTime;
            
            // For smaller inputs, verify with brute force
            int bruteForceResult = -1;
            if (s.length() <= 20) {
                startTime = System.nanoTime();
                bruteForceResult = solution.longestAwesomeBruteForce(s);
                long time3 = System.nanoTime() - startTime;
                System.out.printf("Input: %s%n", s);
                System.out.printf("HashMap: %d (%d ns)%n", result1, time1);
                System.out.printf("Array: %d (%d ns)%n", result2, time2);
                System.out.printf("Brute Force: %d (%d ns)%n", bruteForceResult, time3);
            } else {
                System.out.printf("Input: %s%n", s);
                System.out.printf("HashMap: %d (%d ns)%n", result1, time1);
                System.out.printf("Array: %d (%d ns)%n", result2, time2);
            }
            
            System.out.println("Results match: " + 
                ((bruteForceResult == -1 || result1 == bruteForceResult) && result1 == result2));
            System.out.println();
        }
        
        // Demonstrate bit mask approach
        System.out.println("Bit Mask Demonstration:");
        solution.explainBitMask("3242");
        solution.explainBitMask("12321");
        
        // Test palindrome formation
        System.out.println("Testing Palindrome Formation:");
        String[] palindromeTests = {"3242", "12321", "123", "1122"};
        for (String test : palindromeTests) {
            System.out.println(test + " can form palindrome: " + 
                solution.canFormPalindrome(test));
        }
    }
}

