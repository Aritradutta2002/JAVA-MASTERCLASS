package dsa.datastructures.tries.problems;

import java.util.*;

/**
 * Replace Words (LeetCode 648) - MEDIUM
 * FAANG Frequency: Medium (Google, Amazon)
 * 
 * Problem: Replace words with their shortest root from dictionary
 * Time: O(n * m) where n is words, m is avg length
 */
public class ReplaceWords {
    
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        String word = null;
    }
    
    private TrieNode root;
    
    public String replaceWords(List<String> dictionary, String sentence) {
        root = new TrieNode();
        
        // Build Trie with dictionary roots
        for (String root : dictionary) {
            insert(root);
        }
        
        // Process sentence
        String[] words = sentence.split(" ");
        StringBuilder result = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) result.append(" ");
            result.append(findRoot(words[i]));
        }
        
        return result.toString();
    }
    
    private void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.children[c - 'a'] == null) {
                node.children[c - 'a'] = new TrieNode();
            }
            node = node.children[c - 'a'];
        }
        node.word = word;
    }
    
    private String findRoot(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.word != null) {
                return node.word; // Found shortest root
            }
            if (node.children[c - 'a'] == null) {
                return word; // No root found
            }
            node = node.children[c - 'a'];
        }
        return node.word != null ? node.word : word;
    }
    
    public static void main(String[] args) {
        ReplaceWords solution = new ReplaceWords();
        
        List<String> dict = Arrays.asList("cat", "bat", "rat");
        String sentence = "the cattle was rattled by the battery";
        System.out.println(solution.replaceWords(dict, sentence));
        // "the cat was rat by the bat"
    }
}

