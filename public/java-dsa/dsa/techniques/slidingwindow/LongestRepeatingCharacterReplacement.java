package dsa.techniques.slidingwindow;

import java.util.*;

/**
 * Longest Repeating Character Replacement (LeetCode 424) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook)
 * 
 * Problem: Find longest substring with same letter after replacing at most k characters
 * Time: O(n), Space: O(26) = O(1)
 */
public class LongestRepeatingCharacterReplacement {
    
    public int characterReplacement(String s, int k) {
        int[] count = new int[26];
        int left = 0, maxCount = 0, maxLen = 0;
        
        for (int right = 0; right < s.length(); right++) {
            // Expand window
            maxCount = Math.max(maxCount, ++count[s.charAt(right) - 'A']);
            
            // Shrink window if invalid
            // Window size - most frequent char count > k means we need more than k replacements
            while (right - left + 1 - maxCount > k) {
                count[s.charAt(left) - 'A']--;
                left++;
            }
            
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    // Alternative: More intuitive but slightly slower
    public int characterReplacementAlternate(String s, int k) {
        int[] count = new int[26];
        int left = 0, maxLen = 0;
        
        for (int right = 0; right < s.length(); right++) {
            count[s.charAt(right) - 'A']++;
            
            // Find max frequency in current window
            int maxFreq = 0;
            for (int freq : count) {
                maxFreq = Math.max(maxFreq, freq);
            }
            
            // If we need more than k replacements, shrink window
            while (right - left + 1 - maxFreq > k) {
                count[s.charAt(left) - 'A']--;
                left++;
            }
            
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    public static void main(String[] args) {
        LongestRepeatingCharacterReplacement solution = new LongestRepeatingCharacterReplacement();
        
        // Test Case 1
        System.out.println(solution.characterReplacement("ABAB", 2)); // 4
        
        // Test Case 2
        System.out.println(solution.characterReplacement("AABABBA", 1)); // 4
        
        // Test Case 3
        System.out.println(solution.characterReplacement("AAAA", 0)); // 4
    }
}

