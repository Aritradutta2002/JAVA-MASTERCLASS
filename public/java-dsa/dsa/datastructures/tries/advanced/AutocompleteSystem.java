package dsa.datastructures.tries.advanced;

import java.util.*;

/**
 * Design Search Autocomplete System (LeetCode 642) - HARD
 * FAANG Frequency: Very High (Google, Amazon, Facebook)
 * 
 * Problem: Return top 3 historical hot sentences with same prefix
 * Time: O(p + n log n) where p is prefix length, n is matching sentences
 */
public class AutocompleteSystem {
    
    class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        Map<String, Integer> sentences = new HashMap<>();
    }
    
    private TrieNode root;
    private TrieNode current;
    private StringBuilder prefix;
    
    public AutocompleteSystem(String[] sentences, int[] times) {
        root = new TrieNode();
        current = root;
        prefix = new StringBuilder();
        
        // Build Trie with initial data
        for (int i = 0; i < sentences.length; i++) {
            insert(sentences[i], times[i]);
        }
    }
    
    private void insert(String sentence, int count) {
        TrieNode node = root;
        for (char c : sentence.toCharArray()) {
            node.children.putIfAbsent(c, new TrieNode());
            node = node.children.get(c);
            node.sentences.put(sentence, node.sentences.getOrDefault(sentence, 0) + count);
        }
    }
    
    public List<String> input(char c) {
        if (c == '#') {
            // Save current sentence
            String sentence = prefix.toString();
            insert(sentence, 1);
            
            // Reset
            prefix = new StringBuilder();
            current = root;
            return new ArrayList<>();
        }
        
        prefix.append(c);
        
        if (current != null) {
            current = current.children.get(c);
        }
        
        if (current == null) {
            return new ArrayList<>();
        }
        
        // Get top 3 sentences
        PriorityQueue<Map.Entry<String, Integer>> pq = new PriorityQueue<>((a, b) -> {
            if (a.getValue() != b.getValue()) {
                return a.getValue() - b.getValue(); // Min heap by count
            }
            return b.getKey().compareTo(a.getKey()); // Max heap by lexicographic
        });
        
        for (Map.Entry<String, Integer> entry : current.sentences.entrySet()) {
            pq.offer(entry);
            if (pq.size() > 3) {
                pq.poll();
            }
        }
        
        List<String> result = new ArrayList<>();
        while (!pq.isEmpty()) {
            result.add(0, pq.poll().getKey());
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        String[] sentences = {"i love you", "island", "iroman", "i love leetcode"};
        int[] times = {5, 3, 2, 2};
        
        AutocompleteSystem system = new AutocompleteSystem(sentences, times);
        
        System.out.println(system.input('i')); // ["i love you", "island", "i love leetcode"]
        System.out.println(system.input(' ')); // ["i love you", "i love leetcode"]
        System.out.println(system.input('a')); // []
        System.out.println(system.input('#')); // []
    }
}

