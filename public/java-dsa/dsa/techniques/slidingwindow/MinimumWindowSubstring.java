package dsa.techniques.slidingwindow;

import java.util.*;

/**
 * Minimum Window Substring (LeetCode 76) - HARD
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Find minimum window in s that contains all characters of t
 * Time: O(m + n), Space: O(k) where k is unique chars
 * 
 * Key Pattern: Shrinkable Sliding Window with HashMap
 */
public class MinimumWindowSubstring {
    
    public String minWindow(String s, String t) {
        if (s == null || t == null || s.length() < t.length()) {
            return "";
        }
        
        // Frequency map for target string
        Map<Character, Integer> targetMap = new HashMap<>();
        for (char c : t.toCharArray()) {
            targetMap.put(c, targetMap.getOrDefault(c, 0) + 1);
        }
        
        int required = targetMap.size(); // Unique chars needed
        int formed = 0; // Unique chars matched with desired frequency
        
        Map<Character, Integer> windowMap = new HashMap<>();
        int left = 0, right = 0;
        int minLen = Integer.MAX_VALUE;
        int minLeft = 0;
        
        while (right < s.length()) {
            // Expand window
            char c = s.charAt(right);
            windowMap.put(c, windowMap.getOrDefault(c, 0) + 1);
            
            if (targetMap.containsKey(c) && 
                windowMap.get(c).intValue() == targetMap.get(c).intValue()) {
                formed++;
            }
            
            // Contract window
            while (left <= right && formed == required) {
                // Update result
                if (right - left + 1 < minLen) {
                    minLen = right - left + 1;
                    minLeft = left;
                }
                
                char leftChar = s.charAt(left);
                windowMap.put(leftChar, windowMap.get(leftChar) - 1);
                
                if (targetMap.containsKey(leftChar) && 
                    windowMap.get(leftChar) < targetMap.get(leftChar)) {
                    formed--;
                }
                
                left++;
            }
            
            right++;
        }
        
        return minLen == Integer.MAX_VALUE ? "" : s.substring(minLeft, minLeft + minLen);
    }
    
    // Optimized using array instead of HashMap (for ASCII)
    public String minWindowOptimized(String s, String t) {
        int[] targetCount = new int[128];
        int[] windowCount = new int[128];
        
        for (char c : t.toCharArray()) {
            targetCount[c]++;
        }
        
        int required = 0;
        for (int count : targetCount) {
            if (count > 0) required++;
        }
        
        int formed = 0;
        int left = 0, minLen = Integer.MAX_VALUE, minLeft = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            windowCount[c]++;
            
            if (targetCount[c] > 0 && windowCount[c] == targetCount[c]) {
                formed++;
            }
            
            while (formed == required) {
                if (right - left + 1 < minLen) {
                    minLen = right - left + 1;
                    minLeft = left;
                }
                
                char leftChar = s.charAt(left);
                windowCount[leftChar]--;
                
                if (targetCount[leftChar] > 0 && windowCount[leftChar] < targetCount[leftChar]) {
                    formed--;
                }
                
                left++;
            }
        }
        
        return minLen == Integer.MAX_VALUE ? "" : s.substring(minLeft, minLeft + minLen);
    }
    
    public static void main(String[] args) {
        MinimumWindowSubstring solution = new MinimumWindowSubstring();
        
        // Test Case 1
        System.out.println(solution.minWindow("ADOBECODEBANC", "ABC")); // "BANC"
        
        // Test Case 2
        System.out.println(solution.minWindow("a", "a")); // "a"
        
        // Test Case 3
        System.out.println(solution.minWindow("a", "aa")); // ""
    }
}

