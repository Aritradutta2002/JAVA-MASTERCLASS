package dsa.datastructures.strings.advanced;

/**
 * KMP (Knuth-Morris-Pratt) Pattern Matching Algorithm
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Problem: Find pattern in text efficiently
 * Time: O(n + m), Space: O(m)
 * 
 * Used in: LeetCode 28 (Find Index of First Occurrence)
 */
public class KMP {
    
    // Find first occurrence of pattern in text
    public int strStr(String text, String pattern) {
        if (pattern.length() == 0) return 0;
        
        int[] lps = computeLPS(pattern);
        int i = 0; // text pointer
        int j = 0; // pattern pointer
        
        while (i < text.length()) {
            if (text.charAt(i) == pattern.charAt(j)) {
                i++;
                j++;
            }
            
            if (j == pattern.length()) {
                return i - j; // Found pattern
            } else if (i < text.length() && text.charAt(i) != pattern.charAt(j)) {
                if (j != 0) {
                    j = lps[j - 1];
                } else {
                    i++;
                }
            }
        }
        
        return -1; // Pattern not found
    }
    
    // Compute Longest Proper Prefix which is also Suffix
    private int[] computeLPS(String pattern) {
        int m = pattern.length();
        int[] lps = new int[m];
        int len = 0;
        int i = 1;
        
        while (i < m) {
            if (pattern.charAt(i) == pattern.charAt(len)) {
                len++;
                lps[i] = len;
                i++;
            } else {
                if (len != 0) {
                    len = lps[len - 1];
                } else {
                    lps[i] = 0;
                    i++;
                }
            }
        }
        
        return lps;
    }
    
    public static void main(String[] args) {
        KMP kmp = new KMP();
        
        System.out.println(kmp.strStr("hello", "ll")); // 2
        System.out.println(kmp.strStr("aaaaa", "bba")); // -1
        System.out.println(kmp.strStr("mississippi", "issip")); // 4
    }
}

