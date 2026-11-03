package dsa.techniques.hashing;

/*
 * Problem: Isomorphic Strings
 * LeetCode: #205
 * Difficulty: Easy
 * Companies: Amazon, Google, Microsoft, Bloomberg
 * 
 * Problem Statement:
 * Given two strings s and t, determine if they are isomorphic.
 * Two strings s and t are isomorphic if the characters in s can be replaced to get t.
 * All occurrences of a character must be replaced with another character while 
 * preserving the order of characters. No two characters may map to the same 
 * character, but a character may map to itself.
 *
 * Example 1:
 * Input: s = "egg", t = "add"
 * Output: true
 *
 * Example 2:
 * Input: s = "foo", t = "bar"
 * Output: false
 *
 * Example 3:
 * Input: s = "paper", t = "title"
 * Output: true
 *
 * Constraints:
 * - 1 <= s.length <= 5 * 10^4
 * - t.length == s.length
 * - s and t consist of any valid ASCII character.
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1) - at most 256 ASCII characters
 *
 * Approach:
 * Use two HashMaps to maintain bidirectional mapping between characters.
 * Ensure one-to-one mapping in both directions.
 */

import java.util.*;

public class IsomorphicStrings_LC205 {

    // Solution using two HashMaps
    public boolean isIsomorphic(String s, String t) {
        if (s.length() != t.length())
            return false;

        Map<Character, Character> mapST = new HashMap<>();
        Map<Character, Character> mapTS = new HashMap<>();

        for (int i = 0; i < s.length(); i++) {
            char c1 = s.charAt(i);
            char c2 = t.charAt(i);

            // Check mapping from s to t
            if (mapST.containsKey(c1)) {
                if (mapST.get(c1) != c2) {
                    return false;
                }
            } else {
                mapST.put(c1, c2);
            }

            // Check mapping from t to s
            if (mapTS.containsKey(c2)) {
                if (mapTS.get(c2) != c1) {
                    return false;
                }
            } else {
                mapTS.put(c2, c1);
            }
        }

        return true;
    }

    // Optimized solution using arrays (for ASCII characters)
    public boolean isIsomorphicArrays(String s, String t) {
        int[] mapS = new int[256];
        int[] mapT = new int[256];

        Arrays.fill(mapS, -1);
        Arrays.fill(mapT, -1);

        for (int i = 0; i < s.length(); i++) {
            char c1 = s.charAt(i);
            char c2 = t.charAt(i);

            if (mapS[c1] == -1 && mapT[c2] == -1) {
                mapS[c1] = c2;
                mapT[c2] = c1;
            } else if (mapS[c1] != c2 || mapT[c2] != c1) {
                return false;
            }
        }

        return true;
    }

    public static void main(String[] args) {
        IsomorphicStrings_LC205 solution = new IsomorphicStrings_LC205();

        // Test Case 1
        System.out.println("Test 1: " + solution.isIsomorphic("egg", "add")); // true

        // Test Case 2
        System.out.println("Test 2: " + solution.isIsomorphic("foo", "bar")); // false

        // Test Case 3
        System.out.println("Test 3: " + solution.isIsomorphic("paper", "title")); // true

        // Test Case 4
        System.out.println("Test 4: " + solution.isIsomorphic("ab", "aa")); // false

        // Test Case 5
        System.out.println("Test 5: " + solution.isIsomorphic("badc", "baba")); // false
    }
}

