package dsa.techniques.hashing;

/*
 * Problem: First Unique Character in a String
 * LeetCode: #387
 * Difficulty: Easy
 * Companies: Amazon, Google, Microsoft, Bloomberg
 * 
 * Problem Statement:
 * Given a string s, find the first non-repeating character in it and return its index.
 * If it does not exist, return -1.
 *
 * Example 1:
 * Input: s = "leetcode"
 * Output: 0
 *
 * Example 2:
 * Input: s = "loveleetcode"
 * Output: 2
 *
 * Example 3:
 * Input: s = "aabb"
 * Output: -1
 *
 * Constraints:
 * - 1 <= s.length <= 10^5
 * - s consists of only lowercase English letters.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1) - constant space for 26 letters
 *
 * Approach:
 * 1. First pass: Count frequency of each character using HashMap
 * 2. Second pass: Return index of first character with frequency 1
 */

import java.util.*;

public class FirstUniqueCharacter_LC387 {

    // Solution using HashMap
    public int firstUniqChar(String s) {
        Map<Character, Integer> freqMap = new HashMap<>();

        // Count frequencies
        for (char c : s.toCharArray()) {
            freqMap.put(c, freqMap.getOrDefault(c, 0) + 1);
        }

        // Find first unique character
        for (int i = 0; i < s.length(); i++) {
            if (freqMap.get(s.charAt(i)) == 1) {
                return i;
            }
        }

        return -1;
    }

    // Optimized solution using frequency array
    public int firstUniqCharArray(String s) {
        int[] freq = new int[26];

        // Count frequencies
        for (char c : s.toCharArray()) {
            freq[c - 'a']++;
        }

        // Find first unique character
        for (int i = 0; i < s.length(); i++) {
            if (freq[s.charAt(i) - 'a'] == 1) {
                return i;
            }
        }

        return -1;
    }

    // Alternative: Using LinkedHashMap to maintain insertion order
    public int firstUniqCharLinkedHashMap(String s) {
        Map<Character, Integer> freqMap = new LinkedHashMap<>();

        for (char c : s.toCharArray()) {
            freqMap.put(c, freqMap.getOrDefault(c, 0) + 1);
        }

        for (Map.Entry<Character, Integer> entry : freqMap.entrySet()) {
            if (entry.getValue() == 1) {
                return s.indexOf(entry.getKey());
            }
        }

        return -1;
    }

    public static void main(String[] args) {
        FirstUniqueCharacter_LC387 solution = new FirstUniqueCharacter_LC387();

        // Test Case 1
        System.out.println("Test 1: " + solution.firstUniqChar("leetcode")); // 0

        // Test Case 2
        System.out.println("Test 2: " + solution.firstUniqChar("loveleetcode")); // 2

        // Test Case 3
        System.out.println("Test 3: " + solution.firstUniqChar("aabb")); // -1

        // Test Case 4
        System.out.println("Test 4: " + solution.firstUniqChar("z")); // 0

        // Test Case 5
        System.out.println("Test 5: " + solution.firstUniqChar("dddccdbba")); // 8
    }
}

