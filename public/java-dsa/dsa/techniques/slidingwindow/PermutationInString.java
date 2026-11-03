package dsa.techniques.slidingwindow;

import java.util.*;

/**
 * Permutation in String (LeetCode 567) - MEDIUM
 * FAANG Frequency: High (Amazon, Microsoft, Facebook)
 * 
 * Problem: Check if s2 contains permutation of s1
 * Time: O(n), Space: O(1)
 * 
 * Key: Fixed-size sliding window with character frequency matching
 */
public class PermutationInString {
    
    // Approach 1: Using frequency array
    public boolean checkInclusion(String s1, String s2) {
        if (s1.length() > s2.length()) return false;
        
        int[] s1Count = new int[26];
        int[] s2Count = new int[26];
        
        // Initialize frequency for s1 and first window of s2
        for (int i = 0; i < s1.length(); i++) {
            s1Count[s1.charAt(i) - 'a']++;
            s2Count[s2.charAt(i) - 'a']++;
        }
        
        if (matches(s1Count, s2Count)) return true;
        
        // Slide the window
        for (int i = s1.length(); i < s2.length(); i++) {
            s2Count[s2.charAt(i) - 'a']++;
            s2Count[s2.charAt(i - s1.length()) - 'a']--;
            
            if (matches(s1Count, s2Count)) return true;
        }
        
        return false;
    }
    
    private boolean matches(int[] s1Count, int[] s2Count) {
        for (int i = 0; i < 26; i++) {
            if (s1Count[i] != s2Count[i]) return false;
        }
        return true;
    }
    
    // Approach 2: Optimized with single counter
    public boolean checkInclusionOptimized(String s1, String s2) {
        if (s1.length() > s2.length()) return false;
        
        int[] count = new int[26];
        
        // Count characters in s1
        for (char c : s1.toCharArray()) {
            count[c - 'a']++;
        }
        
        int required = s1.length();
        
        // Process first window
        for (int i = 0; i < s1.length(); i++) {
            if (count[s2.charAt(i) - 'a']-- > 0) {
                required--;
            }
        }
        
        if (required == 0) return true;
        
        // Slide window
        for (int i = s1.length(); i < s2.length(); i++) {
            // Add new character
            if (count[s2.charAt(i) - 'a']-- > 0) {
                required--;
            }
            
            // Remove old character
            if (count[s2.charAt(i - s1.length()) - 'a']++ >= 0) {
                required++;
            }
            
            if (required == 0) return true;
        }
        
        return false;
    }
    
    // Find All Anagrams in String (LeetCode 438) - Similar problem
    public List<Integer> findAnagrams(String s, String p) {
        List<Integer> result = new ArrayList<>();
        if (p.length() > s.length()) return result;
        
        int[] pCount = new int[26];
        int[] sCount = new int[26];
        
        for (int i = 0; i < p.length(); i++) {
            pCount[p.charAt(i) - 'a']++;
            sCount[s.charAt(i) - 'a']++;
        }
        
        if (Arrays.equals(pCount, sCount)) {
            result.add(0);
        }
        
        for (int i = p.length(); i < s.length(); i++) {
            sCount[s.charAt(i) - 'a']++;
            sCount[s.charAt(i - p.length()) - 'a']--;
            
            if (Arrays.equals(pCount, sCount)) {
                result.add(i - p.length() + 1);
            }
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        PermutationInString solution = new PermutationInString();
        
        // Test Case 1
        System.out.println(solution.checkInclusion("ab", "eidbaooo")); // true
        
        // Test Case 2
        System.out.println(solution.checkInclusion("ab", "eidboaoo")); // false
        
        // Test Case 3: Find all anagrams
        System.out.println(solution.findAnagrams("cbaebabacd", "abc")); // [0, 6]
    }
}

