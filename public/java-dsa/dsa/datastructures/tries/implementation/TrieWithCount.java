package dsa.datastructures.tries.implementation;

/**
 * Trie with Word Count
 * FAANG Frequency: Medium
 * 
 * Enhanced Trie that tracks word frequency
 */
public class TrieWithCount {
    
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        int count = 0; // Number of words ending here
        int prefixCount = 0; // Number of words with this prefix
    }
    
    private TrieNode root;
    
    public TrieWithCount() {
        root = new TrieNode();
    }
    
    public void insert(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.children[c - 'a'] == null) {
                node.children[c - 'a'] = new TrieNode();
            }
            node = node.children[c - 'a'];
            node.prefixCount++;
        }
        node.count++;
    }
    
    public int countWordsEqualTo(String word) {
        TrieNode node = findNode(word);
        return node == null ? 0 : node.count;
    }
    
    public int countWordsStartingWith(String prefix) {
        TrieNode node = findNode(prefix);
        return node == null ? 0 : node.prefixCount;
    }
    
    public void erase(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.children[c - 'a'] == null) {
                return;
            }
            node = node.children[c - 'a'];
            node.prefixCount--;
        }
        if (node.count > 0) {
            node.count--;
        }
    }
    
    private TrieNode findNode(String word) {
        TrieNode node = root;
        for (char c : word.toCharArray()) {
            if (node.children[c - 'a'] == null) {
                return null;
            }
            node = node.children[c - 'a'];
        }
        return node;
    }
    
    public static void main(String[] args) {
        TrieWithCount trie = new TrieWithCount();
        
        trie.insert("apple");
        trie.insert("apple");
        trie.insert("app");
        
        System.out.println(trie.countWordsEqualTo("apple")); // 2
        System.out.println(trie.countWordsStartingWith("app")); // 3
        
        trie.erase("apple");
        System.out.println(trie.countWordsEqualTo("apple")); // 1
    }
}

