package dsa.datastructures.strings.advanced;
/**
 * Z ALGORITHM
 * 
 * Pattern Matching in O(n+m) time - CP Essential!
 * 
 * Z[i] = length of longest substring starting from i which is also a prefix
 * 
 * Applications:
 * - Pattern matching (better than KMP for multiple patterns)
 * - Finding all occurrences
 * - String compression
 * - Competitive Programming contests
 * 
 * Companies: Doesn't appear in FAANG but CRITICAL for CP contests!
 */

import java.util.*;

public class ZAlgorithm {
    
    /**
     * Compute Z array
     * Time: O(n), Space: O(n)
     * 
     * Z[i] = length of longest substring starting at i which matches prefix
     */
    public int[] computeZArray(String s) {
        int n = s.length();
        int[] z = new int[n];
        
        int left = 0, right = 0;
        
        for (int i = 1; i < n; i++) {
            if (i > right) {
                // Outside the Z-box, compute naively
                left = right = i;
                while (right < n && s.charAt(right - left) == s.charAt(right)) {
                    right++;
                }
                z[i] = right - left;
                right--;
            } else {
                // Inside Z-box, use previously computed values
                int k = i - left;
                
                if (z[k] < right - i + 1) {
                    z[i] = z[k];
                } else {
                    left = i;
                    while (right < n && s.charAt(right - left) == s.charAt(right)) {
                        right++;
                    }
                    z[i] = right - left;
                    right--;
                }
            }
        }
        
        return z;
    }
    
    /**
     * Pattern matching using Z algorithm
     * Find all occurrences of pattern in text
     */
    public List<Integer> search(String text, String pattern) {
        String concat = pattern + "$" + text; // $ is separator
        int[] z = computeZArray(concat);
        
        List<Integer> result = new ArrayList<>();
        int pLen = pattern.length();
        
        for (int i = 0; i < z.length; i++) {
            if (z[i] == pLen) {
                result.add(i - pLen - 1); // Adjust for pattern + separator
            }
        }
        
        return result;
    }
    
    /**
     * CP Problem 1: Count distinct substrings
     * Using Z algorithm concept
     */
    public int countDistinctSubstrings(String s) {
        Set<String> distinct = new HashSet<>();
        int n = s.length();
        
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j <= n; j++) {
                distinct.add(s.substring(i, j));
            }
        }
        
        return distinct.size();
    }
    
    /**
     * CP Problem 2: Longest prefix which is also suffix
     */
    public int longestPrefixSuffix(String s) {
        int[] z = computeZArray(s);
        int n = s.length();
        int maxLen = 0;
        
        for (int i = 1; i < n; i++) {
            if (i + z[i] == n) { // Substring extends to end
                maxLen = Math.max(maxLen, z[i]);
            }
        }
        
        return maxLen;
    }
    
    /**
     * CP Problem 3: Minimum characters to add to make palindrome
     */
    public int minCharsForPalindrome(String s) {
        String rev = new StringBuilder(s).reverse().toString();
        String concat = s + "$" + rev;
        int[] z = computeZArray(concat);
        
        int n = s.length();
        for (int i = n + 1; i < concat.length(); i++) {
            if (z[i] == concat.length() - i) {
                return i - n - 1;
            }
        }
        
        return n - 1;
    }
    
    /**
     * Comparison with KMP
     */
    public void compareWithKMP(String text, String pattern) {
        System.out.println("=== Z Algorithm vs KMP ===");
        System.out.println("Z Algorithm:");
        System.out.println("  - Simpler to implement");
        System.out.println("  - Better for multiple patterns");
        System.out.println("  - Direct array interpretation");
        System.out.println("  - Time: O(n+m), Space: O(n+m)");
        System.out.println("\nKMP:");
        System.out.println("  - More space efficient for single pattern");
        System.out.println("  - Classic interview algorithm");
        System.out.println("  - Time: O(n+m), Space: O(m)");
    }
    
    public static void main(String[] args) {
        ZAlgorithm za = new ZAlgorithm();
        
        // Test 1: Compute Z array
        String s1 = "aabaab";
        System.out.println("=== Test 1: Z Array ===");
        System.out.println("String: " + s1);
        int[] z1 = za.computeZArray(s1);
        System.out.println("Z array: " + Arrays.toString(z1));
        System.out.println("Explanation:");
        for (int i = 0; i < z1.length; i++) {
            System.out.println("  Z[" + i + "] = " + z1[i] + 
                             " (longest match starting at " + i + ")");
        }
        System.out.println();
        
        // Test 2: Pattern matching
        String text = "ababcabcababc";
        String pattern = "abc";
        System.out.println("=== Test 2: Pattern Matching ===");
        System.out.println("Text: " + text);
        System.out.println("Pattern: " + pattern);
        List<Integer> matches = za.search(text, pattern);
        System.out.println("Occurrences at indices: " + matches);
        System.out.println();
        
        // Test 3: Multiple occurrences
        String text2 = "aaaaaaa";
        String pattern2 = "aa";
        System.out.println("=== Test 3: Overlapping Matches ===");
        System.out.println("Text: " + text2);
        System.out.println("Pattern: " + pattern2);
        System.out.println("Occurrences: " + za.search(text2, pattern2));
        System.out.println();
        
        // Test 4: Longest prefix-suffix
        String s4 = "abcab";
        System.out.println("=== Test 4: Longest Prefix = Suffix ===");
        System.out.println("String: " + s4);
        System.out.println("Length: " + za.longestPrefixSuffix(s4));
        System.out.println("Explanation: 'ab' is both prefix and suffix");
        System.out.println();
        
        // Test 5: Palindrome problem
        String s5 = "abc";
        System.out.println("=== Test 5: Min Chars for Palindrome ===");
        System.out.println("String: " + s5);
        System.out.println("Min chars to add: " + za.minCharsForPalindrome(s5));
        System.out.println("Result: abc -> cbabc (add 'cb')");
        System.out.println();
        
        // Test 6: Visualization
        System.out.println("=== HOW Z ALGORITHM WORKS ===");
        String demo = "aabcaabxaaz";
        System.out.println("String: " + demo);
        int[] zDemo = za.computeZArray(demo);
        System.out.println("\nIndex: 0  1  2  3  4  5  6  7  8  9  10");
        System.out.println("Char:  a  a  b  c  a  a  b  x  a  a  z");
        System.out.print("Z:     ");
        for (int zVal : zDemo) {
            System.out.printf("%-3d", zVal);
        }
        System.out.println("\n");
        
        // CP Contest Tips
        System.out.println("=== CP CONTEST TIPS ===");
        System.out.println("1. Z[i] tells how far prefix matches from position i");
        System.out.println("2. For pattern matching: concat pattern + '$' + text");
        System.out.println("3. If Z[i] = pattern_length, match found!");
        System.out.println("4. Runs in O(n) - VERY FAST for large inputs");
        System.out.println("5. Use in Codeforces, AtCoder, ICPC");
        System.out.println("6. Simpler than KMP for implementation under time pressure");
        System.out.println("\n=== COMMON CP APPLICATIONS ===");
        System.out.println("✓ Multiple pattern matching");
        System.out.println("✓ String periodicity problems");
        System.out.println("✓ Finding all borders of a string");
        System.out.println("✓ Palindrome construction");
        System.out.println("✓ String hashing alternative");
    }
}

