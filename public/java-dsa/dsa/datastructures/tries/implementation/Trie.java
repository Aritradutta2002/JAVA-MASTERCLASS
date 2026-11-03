package dsa.datastructures.tries.implementation;
/**
 * TRIE (PREFIX TREE) IMPLEMENTATION
 * 
 * LeetCode #208 - Medium
 * Companies: Google, Amazon, Microsoft, Facebook, Bloomberg
 * 
 * Problem: Implement a trie with insert, search, and startsWith methods.
 * 
 * Use Cases:
 * - Autocomplete
 * - Spell checker
 * - IP routing (Longest prefix matching)
 * - T9 predictive text
 * - Solving word games
 * 
 * Time Complexity:
 * - Insert: O(m) where m = word length
 * - Search: O(m)
 * - StartsWith: O(m)
 * 
 * Space: O(ALPHABET_SIZE * N * M) worst case
 */

public class Trie {
    
    private class TrieNode {
        TrieNode[] children;
        boolean isEndOfWord;
        int wordCount; // For counting word frequency
        
        public TrieNode() {
            children = new TrieNode[26]; // For lowercase a-z
            isEndOfWord = false;
            wordCount = 0;
        }
    }
    
    private TrieNode root;
    
    public Trie() {
        root = new TrieNode();
    }
    
    /**
     * Insert a word into the trie
     */
    public void insert(String word) {
        TrieNode node = root;
        
        for (char c : word.toCharArray()) {
            int index = c - 'a';
            
            if (node.children[index] == null) {
                node.children[index] = new TrieNode();
            }
            
            node = node.children[index];
        }
        
        node.isEndOfWord = true;
        node.wordCount++;
    }
    
    /**
     * Search for exact word match
     */
    public boolean search(String word) {
        TrieNode node = searchNode(word);
        return node != null && node.isEndOfWord;
    }
    
    /**
     * Check if there's any word with given prefix
     */
    public boolean startsWith(String prefix) {
        return searchNode(prefix) != null;
    }
    
    /**
     * Helper: Search for a node at the end of prefix/word
     */
    private TrieNode searchNode(String str) {
        TrieNode node = root;
        
        for (char c : str.toCharArray()) {
            int index = c - 'a';
            
            if (node.children[index] == null) {
                return null;
            }
            
            node = node.children[index];
        }
        
        return node;
    }
    
    /**
     * Delete a word from trie
     * Returns true if word was deleted
     */
    public boolean delete(String word) {
        return deleteHelper(root, word, 0);
    }
    
    private boolean deleteHelper(TrieNode node, String word, int index) {
        if (index == word.length()) {
            // Word not found
            if (!node.isEndOfWord) {
                return false;
            }
            
            node.isEndOfWord = false;
            node.wordCount = 0;
            
            // If node has no children, it can be deleted
            return isEmpty(node);
        }
        
        int charIndex = word.charAt(index) - 'a';
        TrieNode child = node.children[charIndex];
        
        if (child == null) {
            return false;
        }
        
        boolean shouldDeleteChild = deleteHelper(child, word, index + 1);
        
        if (shouldDeleteChild) {
            node.children[charIndex] = null;
            
            // If node has no children and is not end of another word
            return !node.isEndOfWord && isEmpty(node);
        }
        
        return false;
    }
    
    private boolean isEmpty(TrieNode node) {
        for (TrieNode child : node.children) {
            if (child != null) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Count words with given prefix
     */
    public int countWordsWithPrefix(String prefix) {
        TrieNode node = searchNode(prefix);
        if (node == null) return 0;
        
        return countWordsHelper(node);
    }
    
    private int countWordsHelper(TrieNode node) {
        int count = node.isEndOfWord ? node.wordCount : 0;
        
        for (TrieNode child : node.children) {
            if (child != null) {
                count += countWordsHelper(child);
            }
        }
        
        return count;
    }
    
    /**
     * Get all words with given prefix (for autocomplete)
     */
    public java.util.List<String> getWordsWithPrefix(String prefix) {
        java.util.List<String> result = new java.util.ArrayList<>();
        TrieNode node = searchNode(prefix);
        
        if (node != null) {
            collectWords(node, new StringBuilder(prefix), result);
        }
        
        return result;
    }
    
    private void collectWords(TrieNode node, StringBuilder prefix, java.util.List<String> result) {
        if (node.isEndOfWord) {
            result.add(prefix.toString());
        }
        
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) {
                prefix.append((char)('a' + i));
                collectWords(node.children[i], prefix, result);
                prefix.deleteCharAt(prefix.length() - 1);
            }
        }
    }
    
    /**
     * Find longest common prefix of all words
     */
    public String longestCommonPrefix() {
        StringBuilder lcp = new StringBuilder();
        TrieNode node = root;
        
        while (node != null && !node.isEndOfWord && countChildren(node) == 1) {
            int index = getSingleChildIndex(node);
            lcp.append((char)('a' + index));
            node = node.children[index];
        }
        
        return lcp.toString();
    }
    
    private int countChildren(TrieNode node) {
        int count = 0;
        for (TrieNode child : node.children) {
            if (child != null) count++;
        }
        return count;
    }
    
    private int getSingleChildIndex(TrieNode node) {
        for (int i = 0; i < 26; i++) {
            if (node.children[i] != null) return i;
        }
        return -1;
    }
    
    public static void main(String[] args) {
        Trie trie = new Trie();
        
        // Test Case 1: Basic operations
        System.out.println("=== Basic Operations ===");
        trie.insert("apple");
        System.out.println("Search 'apple': " + trie.search("apple"));   // true
        System.out.println("Search 'app': " + trie.search("app"));       // false
        System.out.println("StartsWith 'app': " + trie.startsWith("app")); // true
        
        trie.insert("app");
        System.out.println("After inserting 'app':");
        System.out.println("Search 'app': " + trie.search("app"));       // true
        
        // Test Case 2: Autocomplete
        System.out.println("\n=== Autocomplete ===");
        trie.insert("application");
        trie.insert("apply");
        trie.insert("apple");
        trie.insert("ape");
        
        System.out.println("Words with prefix 'app': " + trie.getWordsWithPrefix("app"));
        System.out.println("Words with prefix 'ap': " + trie.getWordsWithPrefix("ap"));
        
        // Test Case 3: Count words
        System.out.println("\n=== Count Words ===");
        System.out.println("Words with prefix 'app': " + trie.countWordsWithPrefix("app"));
        System.out.println("Words with prefix 'ap': " + trie.countWordsWithPrefix("ap"));
        
        // Test Case 4: Delete
        System.out.println("\n=== Delete ===");
        System.out.println("Delete 'apple': " + trie.delete("apple"));
        System.out.println("Search 'apple' after delete: " + trie.search("apple")); // false
        System.out.println("Search 'app': " + trie.search("app")); // still true
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Trie is optimal for prefix operations");
        System.out.println("2. Space-time tradeoff: uses more space for faster lookups");
        System.out.println("3. Common variations: compressed trie, ternary search trie");
        System.out.println("4. Use for: autocomplete, spell check, IP routing");
        System.out.println("5. Related problems: Word Search II, Replace Words");
    }
}

