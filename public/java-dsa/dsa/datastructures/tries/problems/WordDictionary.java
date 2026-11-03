package dsa.datastructures.tries.problems;

/**
 * Design Add and Search Words Data Structure (LeetCode 211) - MEDIUM
 * FAANG Frequency: High (Google, Amazon, Facebook)
 * 
 * Problem: Support adding words and searching with '.' wildcard
 * Time: O(m) for add, O(m * 26^k) for search with k wildcards
 */
public class WordDictionary {
    
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isWord = false;
    }
    
    private TrieNode root;
    
    public WordDictionary() {
        root = new TrieNode();
    }
    
    public void addWord(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.children[c - 'a'] == null) {
                node.children[c - 'a'] = new TrieNode();
            }
            node = node.children[c - 'a'];
        }
        node.isWord = true;
    }
    
    public boolean search(String word) {
        return searchHelper(word, 0, root);
    }
    
    private boolean searchHelper(String word, int index, TrieNode node) {
        if (index == word.length()) {
            return node.isWord;
        }
        
        char c = word.charAt(index);
        
        if (c == '.') {
            // Try all possible characters
            for (int i = 0; i < 26; i++) {
                if (node.children[i] != null && 
                    searchHelper(word, index + 1, node.children[i])) {
                    return true;
                }
            }
            return false;
        } else {
            if (node.children[c - 'a'] == null) {
                return false;
            }
            return searchHelper(word, index + 1, node.children[c - 'a']);
        }
    }
    
    public static void main(String[] args) {
        WordDictionary wd = new WordDictionary();
        wd.addWord("bad");
        wd.addWord("dad");
        wd.addWord("mad");
        
        System.out.println(wd.search("pad")); // false
        System.out.println(wd.search("bad")); // true
        System.out.println(wd.search(".ad")); // true
        System.out.println(wd.search("b..")); // true
    }
}

