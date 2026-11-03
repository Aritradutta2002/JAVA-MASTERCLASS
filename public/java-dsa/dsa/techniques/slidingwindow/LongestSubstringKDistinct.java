package dsa.techniques.slidingwindow;

import java.util.*;

/**
 * Longest Substring with At Most K Distinct Characters (LeetCode 340) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook)
 * 
 * Problem: Find length of longest substring with at most k distinct characters
 * Time: O(n), Space: O(k)
 */
public class LongestSubstringKDistinct {
    
    public int lengthOfLongestSubstringKDistinct(String s, int k) {
        if (s == null || s.length() == 0 || k == 0) {
            return 0;
        }
        
        Map<Character, Integer> map = new HashMap<>();
        int left = 0, maxLen = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            map.put(c, map.getOrDefault(c, 0) + 1);
            
            // Shrink window when we have more than k distinct chars
            while (map.size() > k) {
                char leftChar = s.charAt(left);
                map.put(leftChar, map.get(leftChar) - 1);
                if (map.get(leftChar) == 0) {
                    map.remove(leftChar);
                }
                left++;
            }
            
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    // Variation: Exactly K Distinct Characters
    public int exactlyKDistinct(String s, int k) {
        return atMostKDistinct(s, k) - atMostKDistinct(s, k - 1);
    }
    
    private int atMostKDistinct(String s, int k) {
        if (k == 0) return 0;
        
        Map<Character, Integer> map = new HashMap<>();
        int left = 0, count = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            map.put(c, map.getOrDefault(c, 0) + 1);
            
            while (map.size() > k) {
                char leftChar = s.charAt(left);
                map.put(leftChar, map.get(leftChar) - 1);
                if (map.get(leftChar) == 0) {
                    map.remove(leftChar);
                }
                left++;
            }
            
            count += right - left + 1;
        }
        
        return count;
    }
    
    // Longest Substring with At Most 2 Distinct Characters (LeetCode 159)
    public int lengthOfLongestSubstringTwoDistinct(String s) {
        return lengthOfLongestSubstringKDistinct(s, 2);
    }
    
    public static void main(String[] args) {
        LongestSubstringKDistinct solution = new LongestSubstringKDistinct();
        
        // Test Case 1
        System.out.println(solution.lengthOfLongestSubstringKDistinct("eceba", 2)); // 3 ("ece")
        
        // Test Case 2
        System.out.println(solution.lengthOfLongestSubstringKDistinct("aa", 1)); // 2
        
        // Test Case 3
        System.out.println(solution.exactlyKDistinct("aba", 2)); // 3
    }
}

