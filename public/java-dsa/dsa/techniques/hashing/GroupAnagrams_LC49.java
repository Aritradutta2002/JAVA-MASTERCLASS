package dsa.techniques.hashing;

/*
 * Problem: Group Anagrams
 * LeetCode: #49
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Uber, Bloomberg
 * 
 * Problem Statement:
 * Given an array of strings strs, group the anagrams together. 
 * You can return the answer in any order.
 * An Anagram is a word or phrase formed by rearranging the letters of a different 
 * word or phrase, typically using all the original letters exactly once.
 *
 * Example 1:
 * Input: strs = ["eat","tea","tan","ate","nat","bat"]
 * Output: [["bat"],["nat","tan"],["ate","eat","tea"]]
 *
 * Example 2:
 * Input: strs = [""]
 * Output: [[""]]
 *
 * Example 3:
 * Input: strs = ["a"]
 * Output: [["a"]]
 *
 * Constraints:
 * - 1 <= strs.length <= 10^4
 * - 0 <= strs[i].length <= 100
 * - strs[i] consists of lowercase English letters.
 *
 * Time Complexity: O(n * k log k) where n is number of strings, k is max length
 * Space Complexity: O(n * k)
 *
 * Approach:
 * Use HashMap with sorted string as key. All anagrams will have the same sorted form.
 * Alternative: Use character frequency as key for O(n * k) time complexity.
 */

import java.util.*;

public class GroupAnagrams_LC49 {

    // Solution 1: Using sorted string as key
    public List<List<String>> groupAnagrams(String[] strs) {
        Map<String, List<String>> map = new HashMap<>();

        for (String str : strs) {
            char[] chars = str.toCharArray();
            Arrays.sort(chars);
            String key = new String(chars);

            map.putIfAbsent(key, new ArrayList<>());
            map.get(key).add(str);
        }

        return new ArrayList<>(map.values());
    }

    // Solution 2: Using character frequency as key - O(n * k) time
    public List<List<String>> groupAnagramsOptimal(String[] strs) {
        Map<String, List<String>> map = new HashMap<>();

        for (String str : strs) {
            String key = getFrequencyKey(str);

            map.putIfAbsent(key, new ArrayList<>());
            map.get(key).add(str);
        }

        return new ArrayList<>(map.values());
    }

    private String getFrequencyKey(String str) {
        int[] freq = new int[26];

        for (char c : str.toCharArray()) {
            freq[c - 'a']++;
        }

        StringBuilder key = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            if (freq[i] > 0) {
                key.append((char) ('a' + i)).append(freq[i]);
            }
        }

        return key.toString();
    }

    public static void main(String[] args) {
        GroupAnagrams_LC49 solution = new GroupAnagrams_LC49();

        // Test Case 1
        String[] strs1 = { "eat", "tea", "tan", "ate", "nat", "bat" };
        System.out.println("Test 1: " + solution.groupAnagrams(strs1));
        // Output: [["bat"],["nat","tan"],["ate","eat","tea"]]

        // Test Case 2
        String[] strs2 = { "" };
        System.out.println("Test 2: " + solution.groupAnagrams(strs2));
        // Output: [[""]]

        // Test Case 3
        String[] strs3 = { "a" };
        System.out.println("Test 3: " + solution.groupAnagrams(strs3));
        // Output: [["a"]]

        // Test Case 4
        String[] strs4 = { "", "b", "a" };
        System.out.println("Test 4: " + solution.groupAnagrams(strs4));

        // Test optimal solution
        System.out.println("\nOptimal Solution:");
        System.out.println("Test 1: " + solution.groupAnagramsOptimal(strs1));
    }
}

