package dsa.algorithms.backtracking.hard;

import java.util.*;

/**
 * WORD SEARCH II - LeetCode 212 (HARD)
 * 
 * Problem: Given an m x n board of characters and a list of strings words, 
 * return all words on the board. Each word must be constructed from letters 
 * of sequentially adjacent cells, where adjacent cells are horizontally or 
 * vertically neighboring. The same letter cell may not be used more than once in a word.
 * 
 * Example:
 * board = [["o","a","a","n"],["e","t","a","e"],["i","h","k","r"],["i","f","l","v"]]
 * words = ["oath","pea","eat","rain"]
 * Output: ["eat","oath"]
 * 
 * INTERVIEW IMPORTANCE: ⭐⭐⭐⭐⭐
 * - Advanced combination of Trie + Backtracking
 * - Tests optimization skills and data structure knowledge
 * - Common in Google, Facebook interviews
 * 
 * Time Complexity: O(M × 4^L × N) where M = cells, L = max word length, N = words
 * Space Complexity: O(TOTAL) where TOTAL = total characters in all words
 */
public class WordSearchII_Leetcode212 {
    
    /**
     * APPROACH 1: OPTIMIZED TRIE + BACKTRACKING
     * Build Trie from all words, then DFS once through board
     */
    public List<String> findWords(char[][] board, String[] words) {
        List<String> result = new ArrayList<>();
        
        // Build Trie from all words
        TrieNode root = buildTrie(words);
        
        // DFS from each cell
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                dfs(board, i, j, root, result);
            }
        }
        
        return result;
    }
    
    private void dfs(char[][] board, int row, int col, TrieNode node, List<String> result) {
        char c = board[row][col];
        
        // Check boundaries and visited
        if (c == '#' || node.children[c - 'a'] == null) {
            return;
        }
        
        node = node.children[c - 'a'];
        
        // Found a word
        if (node.word != null) {
            result.add(node.word);
            node.word = null; // Important: avoid duplicates and enable pruning
        }
        
        // Mark as visited
        board[row][col] = '#';
        
        // Explore all 4 directions
        int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            
            if (newRow >= 0 && newRow < board.length && 
                newCol >= 0 && newCol < board[0].length) {
                dfs(board, newRow, newCol, node, result);
            }
        }
        
        // Backtrack
        board[row][col] = c;
        
        // Optimization: remove the leaf node to avoid future DFS
        if (isLeaf(node)) {
            // This optimization can significantly improve performance
            // by pruning branches that won't lead to any more words
        }
    }
    
    private TrieNode buildTrie(String[] words) {
        TrieNode root = new TrieNode();
        
        for (String word : words) {
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                int index = c - 'a';
                if (node.children[index] == null) {
                    node.children[index] = new TrieNode();
                }
                node = node.children[index];
            }
            node.word = word; // Store the word at the end node
        }
        
        return root;
    }
    
    private boolean isLeaf(TrieNode node) {
        for (TrieNode child : node.children) {
            if (child != null) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * APPROACH 2: NAIVE SOLUTION FOR COMPARISON
     * For each word, perform Word Search I algorithm
     * Much less efficient but easier to understand
     */
    public List<String> findWordsNaive(char[][] board, String[] words) {
        List<String> result = new ArrayList<>();
        
        for (String word : words) {
            if (exist(board, word)) {
                result.add(word);
            }
        }
        
        return result;
    }
    
    private boolean exist(char[][] board, String word) {
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                if (dfsWordSearch(board, word, i, j, 0)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean dfsWordSearch(char[][] board, String word, int row, int col, int index) {
        if (index == word.length()) {
            return true;
        }
        
        if (row < 0 || row >= board.length || col < 0 || col >= board[0].length ||
            board[row][col] != word.charAt(index)) {
            return false;
        }
        
        char temp = board[row][col];
        board[row][col] = '#';
        
        boolean found = dfsWordSearch(board, word, row + 1, col, index + 1) ||
                       dfsWordSearch(board, word, row - 1, col, index + 1) ||
                       dfsWordSearch(board, word, row, col + 1, index + 1) ||
                       dfsWordSearch(board, word, row, col - 1, index + 1);
        
        board[row][col] = temp;
        return found;
    }
    
    /**
     * APPROACH 3: ADVANCED OPTIMIZATIONS
     * Multiple optimization techniques for maximum performance
     */
    public List<String> findWordsOptimized(char[][] board, String[] words) {
        List<String> result = new ArrayList<>();
        
        // Filter words that have characters not present in board
        Set<Character> boardChars = new HashSet<>();
        for (char[] row : board) {
            for (char c : row) {
                boardChars.add(c);
            }
        }
        
        List<String> validWords = new ArrayList<>();
        for (String word : words) {
            boolean valid = true;
            for (char c : word.toCharArray()) {
                if (!boardChars.contains(c)) {
                    valid = false;
                    break;
                }
            }
            if (valid) {
                validWords.add(word);
            }
        }
        
        // Build Trie only with valid words
        TrieNode root = buildTrie(validWords.toArray(new String[0]));
        
        // DFS with additional optimizations
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                dfsOptimized(board, i, j, root, result);
            }
        }
        
        return result;
    }
    
    private void dfsOptimized(char[][] board, int row, int col, TrieNode node, List<String> result) {
        char c = board[row][col];
        
        if (c == '#' || node.children[c - 'a'] == null) {
            return;
        }
        
        node = node.children[c - 'a'];
        
        if (node.word != null) {
            result.add(node.word);
            node.word = null; // Avoid duplicates
        }
        
        board[row][col] = '#';
        
        // Only continue DFS if there are still children (potential words)
        if (!isLeaf(node)) {
            int[][] directions = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
            for (int[] dir : directions) {
                int newRow = row + dir[0];
                int newCol = col + dir[1];
                
                if (newRow >= 0 && newRow < board.length && 
                    newCol >= 0 && newCol < board[0].length) {
                    dfsOptimized(board, newRow, newCol, node, result);
                }
            }
        }
        
        board[row][col] = c;
    }
    
    /**
     * Trie Node Definition
     */
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        String word = null;
    }
    
    /**
     * INTERVIEW DISCUSSION POINTS:
     * 
     * 1. Why Trie is essential here:
     *    - Naive approach: O(M × N × W × 4^L) where W = number of words
     *    - Trie approach: O(M × N × 4^L) - sharing common prefixes
     * 
     * 2. Key optimizations:
     *    - Set word to null after finding to avoid duplicates
     *    - Character frequency check before building Trie
     *    - Pruning branches that don't lead to words
     * 
     * 3. Space vs Time trade-offs:
     *    - Trie uses more space but dramatically reduces time
     *    - In-place board modification saves space
     * 
     * 4. Follow-up questions:
     *    - What if board is very large?
     *    - What if words list is very large?
     *    - How to handle real-time word additions?
     *    - Can you return positions of words too?
     * 
     * 5. Edge cases:
     *    - Empty board or words array
     *    - Single character board/words
     *    - No matching words
     *    - Words longer than board allows
     */
    
    public static void main(String[] args) {
        WordSearchII_Leetcode212 solution = new WordSearchII_Leetcode212();
        
        // Test case 1
        char[][] board1 = {
            {'o','a','a','n'},
            {'e','t','a','e'},
            {'i','h','k','r'},
            {'i','f','l','v'}
        };
        String[] words1 = {"oath","pea","eat","rain"};
        
        System.out.println("Test case 1:");
        System.out.println("Board:");
        for (char[] row : board1) {
            System.out.println(Arrays.toString(row));
        }
        System.out.println("Words: " + Arrays.toString(words1));
        System.out.println("Found: " + solution.findWords(board1, words1));
        System.out.println();
        
        // Test case 2
        char[][] board2 = {
            {'a','b'},
            {'c','d'}
        };
        String[] words2 = {"abcb"};
        
        System.out.println("Test case 2:");
        System.out.println("Board:");
        for (char[] row : board2) {
            System.out.println(Arrays.toString(row));
        }
        System.out.println("Words: " + Arrays.toString(words2));
        System.out.println("Found: " + solution.findWords(board2, words2));
        System.out.println();
        
        // Performance comparison
        System.out.println("Performance comparison:");
        char[][] largeboard = new char[5][5];
        for (int i = 0; i < 5; i++) {
            for (int j = 0; j < 5; j++) {
                largeboard[i][j] = (char)('a' + (i * 5 + j) % 26);
            }
        }
        
        String[] manyWords = {"abc", "def", "ghi", "jkl", "mno", "pqr", "stu", "vwx", "yz"};
        
        long start1 = System.nanoTime();
        List<String> result1 = solution.findWords(largeboard, manyWords);
        long end1 = System.nanoTime();
        
        long start2 = System.nanoTime();
        List<String> result2 = solution.findWordsNaive(largeboard, manyWords);
        long end2 = System.nanoTime();
        
        System.out.println("Optimized (Trie): " + (end1 - start1) / 1000000.0 + " ms");
        System.out.println("Naive: " + (end2 - start2) / 1000000.0 + " ms");
        System.out.println("Results match: " + result1.equals(result2));
        System.out.println("Speedup: " + ((double)(end2 - start2) / (end1 - start1)) + "x");
    }
}
