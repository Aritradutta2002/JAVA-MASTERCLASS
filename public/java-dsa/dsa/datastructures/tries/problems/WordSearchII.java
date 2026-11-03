package dsa.datastructures.tries.problems;
/**
 * WORD SEARCH II
 * 
 * LeetCode #212 - Hard
 * Companies: Google, Amazon, Microsoft, Facebook, Uber
 * 
 * Problem: Given a 2D board and a list of words, find all words in the board.
 * Each word must be constructed from letters of sequentially adjacent cells.
 * 
 * Solution: Trie + Backtracking
 * - Build Trie from word list
 * - DFS from each cell, pruning using Trie
 * 
 * Time: O(M * N * 4^L) where L = max word length
 * Space: O(K * L) where K = number of words
 * 
 * Key Optimizations:
 * 1. Remove word from Trie after found (avoid duplicates)
 * 2. Prune Trie branches that have no words
 * 3. Mark visited cells inline (no extra space)
 */

import java.util.*;

public class WordSearchII {
    
    private class TrieNode {
        Map<Character, TrieNode> children;
        String word; // Store complete word at end node
        
        public TrieNode() {
            children = new HashMap<>();
            word = null;
        }
    }
    
    private TrieNode root;
    private List<String> result;
    private int[][] directions = {{-1,0}, {1,0}, {0,-1}, {0,1}};
    
    public List<String> findWords(char[][] board, String[] words) {
        result = new ArrayList<>();
        
        // Build Trie from words
        root = new TrieNode();
        for (String word : words) {
            insertWord(word);
        }
        
        // DFS from each cell
        int m = board.length;
        int n = board[0].length;
        
        for (int i = 0; i < m; i++) {
            for (int j = 0; j < n; j++) {
                if (root.children.containsKey(board[i][j])) {
                    backtrack(board, i, j, root);
                }
            }
        }
        
        return result;
    }
    
    private void backtrack(char[][] board, int row, int col, TrieNode parent) {
        char letter = board[row][col];
        TrieNode currNode = parent.children.get(letter);
        
        // Check if we found a word
        if (currNode.word != null) {
            result.add(currNode.word);
            currNode.word = null; // Avoid duplicate
        }
        
        // Mark as visited
        board[row][col] = '#';
        
        // Explore neighbors
        for (int[] dir : directions) {
            int newRow = row + dir[0];
            int newCol = col + dir[1];
            
            if (newRow < 0 || newRow >= board.length || 
                newCol < 0 || newCol >= board[0].length) {
                continue;
            }
            
            if (currNode.children.containsKey(board[newRow][newCol])) {
                backtrack(board, newRow, newCol, currNode);
            }
        }
        
        // Restore cell
        board[row][col] = letter;
        
        // OPTIMIZATION: Remove leaf nodes
        if (currNode.children.isEmpty()) {
            parent.children.remove(letter);
        }
    }
    
    private void insertWord(String word) {
        TrieNode node = root;
        
        for (char c : word.toCharArray()) {
            if (!node.children.containsKey(c)) {
                node.children.put(c, new TrieNode());
            }
            node = node.children.get(c);
        }
        
        node.word = word;
    }
    
    // FOLLOW-UP: Word Search with wildcards
    public List<String> findWordsWithWildcard(char[][] board, String[] words) {
        // Handle patterns like "a.c" where '.' matches any character
        result = new ArrayList<>();
        
        for (String word : words) {
            if (existsWithWildcard(board, word)) {
                result.add(word);
            }
        }
        
        return result;
    }
    
    private boolean existsWithWildcard(char[][] board, String word) {
        for (int i = 0; i < board.length; i++) {
            for (int j = 0; j < board[0].length; j++) {
                if (dfsWithWildcard(board, word, 0, i, j)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private boolean dfsWithWildcard(char[][] board, String word, int index, 
                                    int row, int col) {
        if (index == word.length()) return true;
        
        if (row < 0 || row >= board.length || col < 0 || col >= board[0].length) {
            return false;
        }
        
        if (board[row][col] == '#') return false;
        
        char c = word.charAt(index);
        if (c != '.' && c != board[row][col]) {
            return false;
        }
        
        char temp = board[row][col];
        board[row][col] = '#';
        
        boolean found = dfsWithWildcard(board, word, index + 1, row + 1, col) ||
                       dfsWithWildcard(board, word, index + 1, row - 1, col) ||
                       dfsWithWildcard(board, word, index + 1, row, col + 1) ||
                       dfsWithWildcard(board, word, index + 1, row, col - 1);
        
        board[row][col] = temp;
        return found;
    }
    
    public static void main(String[] args) {
        WordSearchII solution = new WordSearchII();
        
        // Test Case 1
        char[][] board1 = {
            {'o','a','a','n'},
            {'e','t','a','e'},
            {'i','h','k','r'},
            {'i','f','l','v'}
        };
        String[] words1 = {"oath","pea","eat","rain"};
        
        System.out.println("=== Test Case 1 ===");
        System.out.println("Board:");
        printBoard(board1);
        System.out.println("Words to find: " + Arrays.toString(words1));
        System.out.println("Found: " + solution.findWords(board1, words1));
        
        // Test Case 2
        char[][] board2 = {
            {'a','b'},
            {'c','d'}
        };
        String[] words2 = {"abcb"};
        
        System.out.println("\n=== Test Case 2 ===");
        System.out.println("Board:");
        printBoard(board2);
        System.out.println("Words to find: " + Arrays.toString(words2));
        System.out.println("Found: " + solution.findWords(board2, words2));
        
        // Test Case 3: Many words
        char[][] board3 = {
            {'a','b','c'},
            {'a','e','d'},
            {'a','f','g'}
        };
        String[] words3 = {"abcdefg","gfedcbaaa","eaabcdgfa","befa","dgc","ade"};
        
        System.out.println("\n=== Test Case 3 ===");
        System.out.println("Found: " + solution.findWords(board3, words3));
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Trie is crucial for efficient prefix pruning");
        System.out.println("2. Without Trie: O(M*N*4^L*K), With Trie: O(M*N*4^L)");
        System.out.println("3. Store word at end node to avoid reconstruction");
        System.out.println("4. Remove found words to avoid duplicates");
        System.out.println("5. Prune empty branches for optimization");
        System.out.println("6. Mark visited inline: use # or temp variable");
    }
    
    private static void printBoard(char[][] board) {
        for (char[] row : board) {
            System.out.println(Arrays.toString(row));
        }
    }
}

