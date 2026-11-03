package dsa.techniques.slidingwindow;
/**
 * SLIDING WINDOW PATTERNS - COMPLETE GUIDE
 * 
 * Companies: ALL FAANG companies
 * 
 * Pattern Types:
 * 1. Fixed Size Window
 * 2. Variable Size Window (expand/shrink)
 * 3. Dynamic Window with HashMap
 * 
 * Time: O(n) - Each element visited at most twice
 * Space: O(k) where k = unique elements
 */

import java.util.*;

public class SlidingWindowPatterns {
    
    /**
     * PATTERN 1: FIXED SIZE WINDOW
     * Maximum Sum Subarray of Size K
     */
    public int maxSumSubarray(int[] arr, int k) {
        int maxSum = 0, windowSum = 0;
        
        // Build first window
        for (int i = 0; i < k; i++) {
            windowSum += arr[i];
        }
        maxSum = windowSum;
        
        // Slide window
        for (int i = k; i < arr.length; i++) {
            windowSum += arr[i] - arr[i - k];
            maxSum = Math.max(maxSum, windowSum);
        }
        
        return maxSum;
    }
    
    /**
     * PATTERN 2: LONGEST SUBSTRING WITHOUT REPEATING CHARACTERS
     * LeetCode #3 - Medium
     */
    public int lengthOfLongestSubstring(String s) {
        Map<Character, Integer> map = new HashMap<>();
        int maxLen = 0, left = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            
            // If char seen before, move left pointer
            if (map.containsKey(c)) {
                left = Math.max(left, map.get(c) + 1);
            }
            
            map.put(c, right);
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    /**
     * PATTERN 3: LONGEST SUBSTRING WITH K DISTINCT CHARACTERS
     * LeetCode #340 (Premium) - Medium
     */
    public int lengthOfLongestSubstringKDistinct(String s, int k) {
        if (k == 0) return 0;
        
        Map<Character, Integer> map = new HashMap<>();
        int maxLen = 0, left = 0;
        
        for (int right = 0; right < s.length(); right++) {
            map.put(s.charAt(right), map.getOrDefault(s.charAt(right), 0) + 1);
            
            // Shrink window if more than k distinct
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
    
    /**
     * PATTERN 4: MINIMUM WINDOW SUBSTRING
     * LeetCode #76 - Hard
     */
    public String minWindow(String s, String t) {
        if (s.length() < t.length()) return "";
        
        Map<Character, Integer> target = new HashMap<>();
        for (char c : t.toCharArray()) {
            target.put(c, target.getOrDefault(c, 0) + 1);
        }
        
        Map<Character, Integer> window = new HashMap<>();
        int left = 0, formed = 0, required = target.size();
        int minLen = Integer.MAX_VALUE, minLeft = 0;
        
        for (int right = 0; right < s.length(); right++) {
            char c = s.charAt(right);
            window.put(c, window.getOrDefault(c, 0) + 1);
            
            if (target.containsKey(c) && window.get(c).intValue() == target.get(c).intValue()) {
                formed++;
            }
            
            // Shrink window
            while (left <= right && formed == required) {
                if (right - left + 1 < minLen) {
                    minLen = right - left + 1;
                    minLeft = left;
                }
                
                char leftChar = s.charAt(left);
                window.put(leftChar, window.get(leftChar) - 1);
                if (target.containsKey(leftChar) && window.get(leftChar) < target.get(leftChar)) {
                    formed--;
                }
                left++;
            }
        }
        
        return minLen == Integer.MAX_VALUE ? "" : s.substring(minLeft, minLeft + minLen);
    }
    
    /**
     * PATTERN 5: PERMUTATION IN STRING
     * LeetCode #567 - Medium
     */
    public boolean checkInclusion(String s1, String s2) {
        if (s1.length() > s2.length()) return false;
        
        int[] s1Count = new int[26];
        int[] window = new int[26];
        
        for (char c : s1.toCharArray()) {
            s1Count[c - 'a']++;
        }
        
        int windowSize = s1.length();
        
        for (int i = 0; i < s2.length(); i++) {
            window[s2.charAt(i) - 'a']++;
            
            if (i >= windowSize) {
                window[s2.charAt(i - windowSize) - 'a']--;
            }
            
            if (Arrays.equals(s1Count, window)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * PATTERN 6: MAX CONSECUTIVE ONES III
     * LeetCode #1004 - Medium
     */
    public int longestOnes(int[] nums, int k) {
        int left = 0, zeros = 0, maxLen = 0;
        
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] == 0) zeros++;
            
            // Shrink if more than k zeros
            while (zeros > k) {
                if (nums[left] == 0) zeros--;
                left++;
            }
            
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    /**
     * PATTERN 7: FRUIT INTO BASKETS
     * LeetCode #904 - Medium
     */
    public int totalFruit(int[] fruits) {
        Map<Integer, Integer> basket = new HashMap<>();
        int left = 0, maxFruits = 0;
        
        for (int right = 0; right < fruits.length; right++) {
            basket.put(fruits[right], basket.getOrDefault(fruits[right], 0) + 1);
            
            // More than 2 types
            while (basket.size() > 2) {
                basket.put(fruits[left], basket.get(fruits[left]) - 1);
                if (basket.get(fruits[left]) == 0) {
                    basket.remove(fruits[left]);
                }
                left++;
            }
            
            maxFruits = Math.max(maxFruits, right - left + 1);
        }
        
        return maxFruits;
    }
    
    public static void main(String[] args) {
        SlidingWindowPatterns solution = new SlidingWindowPatterns();
        
        // Test 1: Fixed Window
        System.out.println("=== Max Sum Subarray ===");
        int[] arr1 = {2, 1, 5, 1, 3, 2};
        System.out.println("Max sum (k=3): " + solution.maxSumSubarray(arr1, 3)); // 9
        
        // Test 2: Longest Substring
        System.out.println("\n=== Longest Substring Without Repeating ===");
        System.out.println("abcabcbb: " + solution.lengthOfLongestSubstring("abcabcbb")); // 3
        
        // Test 3: K Distinct
        System.out.println("\n=== K Distinct Characters ===");
        System.out.println("eceba (k=2): " + solution.lengthOfLongestSubstringKDistinct("eceba", 2)); // 3
        
        // Test 4: Min Window
        System.out.println("\n=== Minimum Window Substring ===");
        System.out.println("ADOBECODEBANC, ABC: " + solution.minWindow("ADOBECODEBANC", "ABC")); // BANC
        
        // Test 5: Permutation
        System.out.println("\n=== Permutation in String ===");
        System.out.println("ab in eidbaooo: " + solution.checkInclusion("ab", "eidbaooo")); // true
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Identify pattern: fixed vs variable window");
        System.out.println("2. Use HashMap for character/element counting");
        System.out.println("3. Two pointers: left (start), right (end)");
        System.out.println("4. Expand right always, shrink left conditionally");
        System.out.println("5. Time complexity: O(n), each element visited max twice");
        System.out.println("6. Template: expand -> check condition -> shrink -> update result");
    }
}

