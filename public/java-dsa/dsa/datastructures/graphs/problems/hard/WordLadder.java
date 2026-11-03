package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 127: Word Ladder
 * 
 * Problem: A transformation sequence from word beginWord to word endWord using a dictionary wordList is a 
 * sequence of words beginWord -> s1 -> s2 -> ... -> sk such that:
 * - Every adjacent pair of words differs by a single letter.
 * - Every si for 1 <= i <= k is in wordList. Note that beginWord does not need to be in wordList.
 * - sk == endWord
 * 
 * Given two words, beginWord and endWord, and a dictionary wordList, return the number of words in the 
 * shortest transformation sequence from beginWord to endWord, or 0 if no such sequence exists.
 * 
 * Time Complexity: O(M^2 * N) where M is length of words, N is number of words
 * Space Complexity: O(M^2 * N)
 */
public class WordLadder {
    
    /**
     * BFS solution using word patterns
     */
    public int ladderLength(String beginWord, String endWord, List<String> wordList) {
        if (!wordList.contains(endWord)) {
            return 0;
        }
        
        // Create pattern map: "*ot" -> ["hot", "dot", "lot"]
        Map<String, List<String>> patternMap = new HashMap<>();
        int wordLength = beginWord.length();
        
        for (String word : wordList) {
            for (int i = 0; i < wordLength; i++) {
                String pattern = word.substring(0, i) + "*" + word.substring(i + 1);
                patternMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(word);
            }
        }
        
        // BFS
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(beginWord);
        visited.add(beginWord);
        
        int level = 1;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                String currentWord = queue.poll();
                
                // Generate all possible patterns for current word
                for (int j = 0; j < wordLength; j++) {
                    String pattern = currentWord.substring(0, j) + "*" + currentWord.substring(j + 1);
                    
                    // Get all words matching this pattern
                    List<String> neighbors = patternMap.getOrDefault(pattern, new ArrayList<>());
                    
                    for (String neighbor : neighbors) {
                        if (neighbor.equals(endWord)) {
                            return level + 1;
                        }
                        
                        if (!visited.contains(neighbor)) {
                            visited.add(neighbor);
                            queue.offer(neighbor);
                        }
                    }
                }
            }
            
            level++;
        }
        
        return 0;
    }
    
    /**
     * Bidirectional BFS solution
     */
    public int ladderLengthBidirectional(String beginWord, String endWord, List<String> wordList) {
        if (!wordList.contains(endWord)) {
            return 0;
        }
        
        Set<String> wordSet = new HashSet<>(wordList);
        Set<String> beginSet = new HashSet<>();
        Set<String> endSet = new HashSet<>();
        
        beginSet.add(beginWord);
        endSet.add(endWord);
        
        int level = 1;
        
        while (!beginSet.isEmpty() && !endSet.isEmpty()) {
            // Always expand the smaller set
            if (beginSet.size() > endSet.size()) {
                Set<String> temp = beginSet;
                beginSet = endSet;
                endSet = temp;
            }
            
            Set<String> nextLevel = new HashSet<>();
            
            for (String word : beginSet) {
                char[] chars = word.toCharArray();
                
                for (int i = 0; i < chars.length; i++) {
                    char originalChar = chars[i];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        chars[i] = c;
                        String newWord = new String(chars);
                        
                        if (endSet.contains(newWord)) {
                            return level + 1;
                        }
                        
                        if (wordSet.contains(newWord)) {
                            nextLevel.add(newWord);
                            wordSet.remove(newWord); // Avoid revisiting
                        }
                    }
                    
                    chars[i] = originalChar; // Restore
                }
            }
            
            beginSet = nextLevel;
            level++;
        }
        
        return 0;
    }
    
    /**
     * Standard BFS solution
     */
    public int ladderLengthStandard(String beginWord, String endWord, List<String> wordList) {
        Set<String> wordSet = new HashSet<>(wordList);
        if (!wordSet.contains(endWord)) {
            return 0;
        }
        
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(beginWord);
        visited.add(beginWord);
        
        int level = 1;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                String currentWord = queue.poll();
                
                if (currentWord.equals(endWord)) {
                    return level;
                }
                
                // Try changing each character
                char[] chars = currentWord.toCharArray();
                for (int j = 0; j < chars.length; j++) {
                    char originalChar = chars[j];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        chars[j] = c;
                        String newWord = new String(chars);
                        
                        if (wordSet.contains(newWord) && !visited.contains(newWord)) {
                            visited.add(newWord);
                            queue.offer(newWord);
                        }
                    }
                    
                    chars[j] = originalChar; // Restore
                }
            }
            
            level++;
        }
        
        return 0;
    }
    
    // Test method
    public static void main(String[] args) {
        WordLadder solution = new WordLadder();
        
        // Test case: beginWord = "hit", endWord = "cog", wordList = ["hot","dot","dog","lot","log","cog"]
        String beginWord = "hit";
        String endWord = "cog";
        List<String> wordList = Arrays.asList("hot","dot","dog","lot","log","cog");
        
        System.out.println("Ladder length (Pattern): " + solution.ladderLength(beginWord, endWord, wordList)); // 5
        System.out.println("Ladder length (Bidirectional): " + solution.ladderLengthBidirectional(beginWord, endWord, wordList)); // 5
        System.out.println("Ladder length (Standard): " + solution.ladderLengthStandard(beginWord, endWord, wordList)); // 5
    }
}

