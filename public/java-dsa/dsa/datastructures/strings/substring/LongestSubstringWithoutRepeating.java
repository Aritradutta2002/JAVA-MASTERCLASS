package dsa.datastructures.strings.substring;

import java.util.*;

/**
 * Longest Substring Without Repeating Characters (LeetCode 3) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Find length of longest substring without repeating characters
 * Time: O(n), Space: O(min(n, m)) where m is charset size
 */
public class LongestSubstringWithoutRepeating {
    
    // Approach 1: Sliding Window with HashMap
    public int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> map = new HashMap<>();
        int maxLen = 0;
        int left = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            
            if (map.containsKey(c)) {
                left = Math.max(left, map.get(c) + 1);
            }
            
            map.put(c, right);
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    // Approach 2: Sliding Window with Array (ASCII)
    public int lengthOfLongestSubstringArray(String s) {
        int[] index = new int[128];
        Arrays.fill(index, -1);
        
        int maxLen = 0;
        int left = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            
            if (index[c] >= left) {
                left = index[c] + 1;
            }
            
            index[c] = right;
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    // Approach 3: Sliding Window with Set
    public int lengthOfLongestSubstringSet(String s) {
        Set<Character> set = new HashSet<>();
        int maxLen = 0;
        int left = 0;
        
        for (int right = 0; right < s.length(); right++) {
            while (set.contains(s.charAt(right))) {
                set.remove(s.charAt(left));
                left++;
            }
            
            set.add(s.charAt(right));
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    public static void main(String[] args) {
        LongestSubstringWithoutRepeating solution = new LongestSubstringWithoutRepeating();
        
        System.out.println(solution.lengthOfLongestSubstring("abcabcbb")); // 3
        System.out.println(solution.lengthOfLongestSubstring("bbbbb")); // 1
        System.out.println(solution.lengthOfLongestSubstring("pwwkew")); // 3
    }
}

