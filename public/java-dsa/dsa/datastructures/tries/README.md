# Trie (Prefix Tree) - FAANG Interview Problems

## Folder Structure
```
TRIE/
├── implementation/     # Basic Trie implementations
├── problems/          # Trie-based problems
└── advanced/          # Advanced Trie applications
```

## Pattern Overview
Trie is a tree data structure for efficient string storage and retrieval:
- Prefix matching
- Autocomplete
- Dictionary operations
- IP routing

## Problem Categories

### 1. Basic Implementation
- **Trie.java** - LeetCode 208 (MEDIUM) ⭐⭐⭐
- **TrieWithWildcard.java** - LeetCode 211 (MEDIUM) ⭐⭐

### 2. Word Problems
- **WordSearchII.java** - LeetCode 212 (HARD) ⭐⭐⭐
- **WordDictionary.java** - LeetCode 211 (MEDIUM) ⭐⭐
- **ReplaceWords.java** - LeetCode 648 (MEDIUM) ⭐⭐

### 3. Advanced Applications
- **AutocompleteSystem.java** - LeetCode 642 (HARD) ⭐⭐⭐
- **MapSumPairs.java** - LeetCode 677 (MEDIUM) ⭐⭐
- **LongestWordInDictionary.java** - LeetCode 720 (MEDIUM) ⭐

## FAANG Interview Frequency
- **Google**: Very High (All Trie problems)
- **Amazon**: High (Word Search II, Autocomplete)
- **Facebook/Meta**: High (Basic Trie, Word problems)
- **Microsoft**: Medium-High (Autocomplete, Dictionary)
- **Apple**: Medium

## Key Concepts

### Trie Node Structure
```java
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEndOfWord;
    // Optional: int count, String word, etc.
}
```

### Time Complexity
- Insert: O(m) where m is word length
- Search: O(m)
- StartsWith: O(m)
- Space: O(ALPHABET_SIZE * N * M)

## Common Patterns

### Basic Operations
```java
// Insert
void insert(String word) {
    TrieNode node = root;
    for (char c : word.toCharArray()) {
        if (node.children[c - 'a'] == null) {
            node.children[c - 'a'] = new TrieNode();
        }
        node = node.children[c - 'a'];
    }
    node.isEndOfWord = true;
}

// Search
boolean search(String word) {
    TrieNode node = root;
    for (char c : word.toCharArray()) {
        if (node.children[c - 'a'] == null) return false;
        node = node.children[c - 'a'];
    }
    return node.isEndOfWord;
}
```

### DFS with Trie
```java
void dfs(TrieNode node, StringBuilder path) {
    if (node.isEndOfWord) {
        // Process word
    }
    for (int i = 0; i < 26; i++) {
        if (node.children[i] != null) {
            path.append((char)('a' + i));
            dfs(node.children[i], path);
            path.deleteCharAt(path.length() - 1);
        }
    }
}
```

## Common Tricks
1. **Store word at leaf**: Avoid rebuilding word during DFS
2. **Count field**: Track word frequency
3. **Wildcard matching**: Use DFS for '.' character
4. **Pruning**: Remove nodes during backtracking
5. **HashMap children**: For large alphabets

## Practice Strategy
1. Master basic Trie implementation
2. Solve Word Search II (combines Trie + DFS)
3. Practice wildcard matching
4. Tackle autocomplete system
5. Learn optimization techniques

## Related Topics
- String algorithms
- DFS/Backtracking
- HashMap
- Recursion
