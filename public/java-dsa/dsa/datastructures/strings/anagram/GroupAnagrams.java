package dsa.datastructures.strings.anagram;

import java.util.*;

/**
 * Group Anagrams (LeetCode 49) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft)
 * 
 * Problem: Group strings that are anagrams of each other
 * Time: O(n * k log k) or O(n * k), Space: O(n * k)
 */
public class GroupAnagrams {
    
    // Approach 1: Sort each string as key
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
    
    // Approach 2: Character count as key (More optimal)
    public List<List<String>> groupAnagramsCount(String[] strs) {
        Map<String, List<String>> map = new HashMap<>();
        
        for (String str : strs) {
            String key = getKey(str);
            map.putIfAbsent(key, new ArrayList<>());
            map.get(key).add(str);
        }
        
        return new ArrayList<>(map.values());
    }
    
    private String getKey(String str) {
        int[] count = new int[26];
        for (char c : str.toCharArray()) {
            count[c - 'a']++;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            if (count[i] > 0) {
                sb.append((char) ('a' + i));
                sb.append(count[i]);
            }
        }
        
        return sb.toString();
    }
    
    // Approach 3: Prime number multiplication (Unique but overflow risk)
    public List<List<String>> groupAnagramsPrime(String[] strs) {
        int[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53, 59, 61, 67, 71, 73, 79, 83, 89, 97, 101};
        Map<Long, List<String>> map = new HashMap<>();
        
        for (String str : strs) {
            long key = 1;
            for (char c : str.toCharArray()) {
                key *= primes[c - 'a'];
            }
            
            map.putIfAbsent(key, new ArrayList<>());
            map.get(key).add(str);
        }
        
        return new ArrayList<>(map.values());
    }
    
    public static void main(String[] args) {
        GroupAnagrams solution = new GroupAnagrams();
        
        String[] strs = {"eat", "tea", "tan", "ate", "nat", "bat"};
        System.out.println(solution.groupAnagrams(strs));
        // [["bat"], ["nat", "tan"], ["ate", "eat", "tea"]]
    }
}

