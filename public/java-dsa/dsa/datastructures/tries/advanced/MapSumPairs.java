package dsa.datastructures.tries.advanced;

import java.util.*;

/**
 * Map Sum Pairs (LeetCode 677) - MEDIUM
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Problem: Sum all values with given prefix
 * Time: O(m) for insert, O(m) for sum
 */
public class MapSumPairs {
    
    class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        int value = 0;
    }
    
    private TrieNode root;
    private Map<String, Integer> map; // Track previous values
    
    public MapSumPairs() {
        root = new TrieNode();
        map = new HashMap<>();
    }
    
    public void insert(String key, int val) {
        int delta = val - map.getOrDefault(key, 0);
        map.put(key, val);
        
        TrieNode node = root;
        for (char c : key.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
            node.value += delta;
        }
    }
    
    public int sum(String prefix) {
        TrieNode node = root;
        for (char c : prefix.toCharArray()) {
            if (!node.children.containsKey(c)) {
                return 0;
            }
            node = node.children.get(c);
        }
        return node.value;
    }
    
    public static void main(String[] args) {
        MapSumPairs mapSum = new MapSumPairs();
        mapSum.insert("apple", 3);
        System.out.println(mapSum.sum("ap")); // 3
        mapSum.insert("app", 2);
        System.out.println(mapSum.sum("ap")); // 5
    }
}

