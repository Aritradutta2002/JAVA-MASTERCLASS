package dsa.techniques.hashing;

/*
 * Problem: Valid Anagram
 * LeetCode: #242
 * Difficulty: Easy
 * Companies: Amazon, Facebook, Google, Microsoft, Bloomberg
 * 
 * Problem Statement:
 * Given two strings s and t, return true if t is an anagram of s, and false otherwise.
 * An Anagram is a word or phrase formed by rearranging the letters of a different word 
 * or phrase, typically using all the original letters exactly once.
 *
 * Example 1:
 * Input: s = "anagram", t = "nagaram"
 * Output: true
 *
 * Example 2:
 * Input: s = "rat", t = "car"
 * Output: false
 *
 * Constraints:
 * - 1 <= s.length, t.length <= 5 * 10^4
 * - s and t consist of lowercase English letters.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1) for frequency array, O(n) for HashMap
 *
 * Approach:
 * 1. Check if lengths are different - if so, not an anagram
 * 2. Count frequency of each character in both strings
 * 3. Compare frequency counts
 */

import java.util.*;

public class ValidAnagram_LC242 {

    // Optimal Solution using frequency array (only for lowercase letters)
    public boolean isAnagram(String s, String t) {
        if (s.length() != t.length()) {
            return false;
        }

        int[] freq = new int[26];

        for (int i = 0; i < s.length(); i++) {
            freq[s.charAt(i) - 'a']++;
            freq[t.charAt(i) - 'a']--;
        }

        for (int count : freq) {
            if (count != 0) {
                return false;
            }
        }

        return true;
    }

    // Alternative: Using HashMap (works for any characters, including Unicode)
    public boolean isAnagramHashMap(String s, String t) {
        if (s.length() != t.length()) {
            return false;
        }

        Map<Character, Integer> map = new HashMap<>();

        for (char c : s.toCharArray()) {
            map.put(c, map.getOrDefault(c, 0) + 1);
        }

        for (char c : t.toCharArray()) {
            if (!map.containsKey(c)) {
                return false;
            }
            map.put(c, map.get(c) - 1);
            if (map.get(c) == 0) {
                map.remove(c);
            }
        }

        return map.isEmpty();
    }

    // Alternative: Sorting approach - O(n log n) time
    public boolean isAnagramSorting(String s, String t) {
        if (s.length() != t.length()) {
            return false;
        }

        char[] sChars = s.toCharArray();
        char[] tChars = t.toCharArray();

        Arrays.sort(sChars);
        Arrays.sort(tChars);

        return Arrays.equals(sChars, tChars);
    }

    public static void main(String[] args) {
        ValidAnagram_LC242 solution = new ValidAnagram_LC242();

        // Test Case 1
        System.out.println("Test 1: " + solution.isAnagram("anagram", "nagaram")); // true

        // Test Case 2
        System.out.println("Test 2: " + solution.isAnagram("rat", "car")); // false

        // Test Case 3
        System.out.println("Test 3: " + solution.isAnagram("listen", "silent")); // true

        // Test Case 4
        System.out.println("Test 4: " + solution.isAnagram("hello", "world")); // false

        // Test Case 5 - Different lengths
        System.out.println("Test 5: " + solution.isAnagram("a", "ab")); // false
    }
}

