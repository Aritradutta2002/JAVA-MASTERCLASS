package dsa.datastructures.graphs.problems.medium;
import java.util.*;

/**
 * Word Ladder I - Find shortest transformation sequence
 * 
 * Problem: Given two distinct words startWord and targetWord, and a list of unique words,
 * find the length of the shortest transformation sequence from startWord to targetWord.
 * 
 * Rules:
 * - Only one letter can be changed in each transformation
 * - Each transformed word must exist in the wordList including targetWord
 * - All words consist of lowercase characters
 * - startWord may or may not be part of wordList
 * 
 * Time Complexity: O(M² × N) where M is word length, N is number of words
 * Space Complexity: O(M² × N) for storing intermediate states and graph
 * 
 * Difficulty: Medium
 * - Requires BFS for shortest path
 * - Graph construction from word transformations
 * - Standard interview problem
 */
class SolutionWordLadder1 {
    public int wordLadderLength(String startWord, String targetWord, List<String> wordList) {
        // Convert to set for O(1) lookup
        Set<String> wordSet = new HashSet<>(wordList);
        
        // If target word is not in the list, transformation is impossible
        if (!wordSet.contains(targetWord)) {
            return 0;
        }
        
        // BFS queue to store current word and transformation count
        Queue<String> queue = new LinkedList<>();
        queue.offer(startWord);
        
        int transformations = 1; // Count includes startWord
        
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            
            // Process all words at current level
            for (int i = 0; i < levelSize; i++) {
                String currentWord = queue.poll();
                
                // Check if we reached target
                if (currentWord.equals(targetWord)) {
                    return transformations;
                }
                
                // Try changing each character position
                char[] wordArray = currentWord.toCharArray();
                for (int pos = 0; pos < wordArray.length; pos++) {
                    char originalChar = wordArray[pos];
                    
                    // Try all 26 possible characters
                    for (char newChar = 'a'; newChar <= 'z'; newChar++) {
                        if (newChar == originalChar) continue;
                        
                        wordArray[pos] = newChar;
                        String transformedWord = new String(wordArray);
                        
                        // If transformed word exists in wordList
                        if (wordSet.contains(transformedWord)) {
                            queue.offer(transformedWord);
                            wordSet.remove(transformedWord); // Mark as visited
                        }
                    }
                    
                    // Restore original character
                    wordArray[pos] = originalChar;
                }
            }
            transformations++;
        }
        
        return 0; // No transformation sequence found
    }
    
    /**
     * Bidirectional BFS - More efficient for larger search spaces
     * Searches from both start and end simultaneously
     */
    public int wordLadderLengthBidirectional(String startWord, String targetWord, List<String> wordList) {
        Set<String> wordSet = new HashSet<>(wordList);
        if (!wordSet.contains(targetWord)) return 0;
        
        Set<String> beginSet = new HashSet<>();
        Set<String> endSet = new HashSet<>();
        
        beginSet.add(startWord);
        endSet.add(targetWord);
        
        int transformations = 1;
        
        while (!beginSet.isEmpty() && !endSet.isEmpty()) {
            // Always expand the smaller set for efficiency
            if (beginSet.size() > endSet.size()) {
                Set<String> temp = beginSet;
                beginSet = endSet;
                endSet = temp;
            }
            
            Set<String> nextLevel = new HashSet<>();
            
            for (String word : beginSet) {
                char[] wordArray = word.toCharArray();
                
                for (int i = 0; i < wordArray.length; i++) {
                    char originalChar = wordArray[i];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        wordArray[i] = c;
                        String transformedWord = new String(wordArray);
                        
                        // If the other set contains this word, we found a path
                        if (endSet.contains(transformedWord)) {
                            return transformations + 1;
                        }
                        
                        // If word exists in wordList, add to next level
                        if (wordSet.contains(transformedWord)) {
                            nextLevel.add(transformedWord);
                            wordSet.remove(transformedWord);
                        }
                    }
                    
                    wordArray[i] = originalChar;
                }
            }
            
            beginSet = nextLevel;
            transformations++;
        }
        
        return 0;
    }
    
    /**
     * Preprocessing approach with pattern mapping
     * Creates intermediate patterns like "h*t", "*it", "hi*" for "hit"
     */
    public int wordLadderLengthWithPreprocessing(String startWord, String targetWord, List<String> wordList) {
        if (!wordList.contains(targetWord)) return 0;
        
        int wordLength = startWord.length();
        
        // Create pattern -> words mapping
        Map<String, List<String>> patternMap = new HashMap<>();
        
        // Add all words from wordList
        for (String word : wordList) {
            for (int i = 0; i < wordLength; i++) {
                String pattern = word.substring(0, i) + "*" + word.substring(i + 1);
                patternMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(word);
            }
        }
        
        // Add startWord patterns (in case it's not in wordList)
        for (int i = 0; i < wordLength; i++) {
            String pattern = startWord.substring(0, i) + "*" + startWord.substring(i + 1);
            patternMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(startWord);
        }
        
        // BFS with pattern-based neighbor finding
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(startWord);
        visited.add(startWord);
        
        int transformations = 1;
        
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            
            for (int i = 0; i < levelSize; i++) {
                String currentWord = queue.poll();
                
                if (currentWord.equals(targetWord)) {
                    return transformations;
                }
                
                // Check all patterns for current word
                for (int j = 0; j < wordLength; j++) {
                    String pattern = currentWord.substring(0, j) + "*" + currentWord.substring(j + 1);
                    
                    if (patternMap.containsKey(pattern)) {
                        for (String neighbor : patternMap.get(pattern)) {
                            if (!visited.contains(neighbor)) {
                                visited.add(neighbor);
                                queue.offer(neighbor);
                            }
                        }
                    }
                }
            }
            transformations++;
        }
        
        return 0;
    }
    
    /**
     * Helper method to check if two words differ by exactly one character
     */
    private boolean isOneCharDifferent(String word1, String word2) {
        if (word1.length() != word2.length()) return false;
        
        int differences = 0;
        for (int i = 0; i < word1.length(); i++) {
            if (word1.charAt(i) != word2.charAt(i)) {
                differences++;
                if (differences > 1) return false;
            }
        }
        return differences == 1;
    }
    
    /**
     * Graph-based approach (less efficient but educational)
     * Build explicit graph first, then BFS
     */
    public int wordLadderLengthGraphBased(String startWord, String targetWord, List<String> wordList) {
        if (!wordList.contains(targetWord)) return 0;
        
        // Add startWord to wordList if not present
        List<String> allWords = new ArrayList<>(wordList);
        if (!allWords.contains(startWord)) {
            allWords.add(startWord);
        }
        
        // Build adjacency list
        Map<String, List<String>> graph = new HashMap<>();
        for (String word : allWords) {
            graph.put(word, new ArrayList<>());
        }
        
        // Connect words that differ by one character
        for (int i = 0; i < allWords.size(); i++) {
            for (int j = i + 1; j < allWords.size(); j++) {
                String word1 = allWords.get(i);
                String word2 = allWords.get(j);
                
                if (isOneCharDifferent(word1, word2)) {
                    graph.get(word1).add(word2);
                    graph.get(word2).add(word1);
                }
            }
        }
        
        // BFS on the graph
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(startWord);
        visited.add(startWord);
        
        int transformations = 1;
        
        while (!queue.isEmpty()) {
            int levelSize = queue.size();
            
            for (int i = 0; i < levelSize; i++) {
                String currentWord = queue.poll();
                
                if (currentWord.equals(targetWord)) {
                    return transformations;
                }
                
                for (String neighbor : graph.get(currentWord)) {
                    if (!visited.contains(neighbor)) {
                        visited.add(neighbor);
                        queue.offer(neighbor);
                    }
                }
            }
            transformations++;
        }
        
        return 0;
    }
    
    // Test method with provided examples
    public static void main(String[] args) {
        SolutionWordLadder1 solution = new SolutionWordLadder1();
        
        // Test case 1
        List<String> wordList1 = Arrays.asList("des","der","dfr","dgt","dfs");
        String startWord1 = "der";
        String targetWord1 = "dfs";
        
        System.out.println("Test 1:");
        System.out.println("WordList: " + wordList1);
        System.out.println("Start: " + startWord1 + ", Target: " + targetWord1);
        System.out.println("BFS Result: " + solution.wordLadderLength(startWord1, targetWord1, wordList1)); // Expected: 3
        System.out.println("Bidirectional Result: " + solution.wordLadderLengthBidirectional(startWord1, targetWord1, wordList1)); // Expected: 3
        System.out.println();
        
        // Test case 2
        List<String> wordList2 = Arrays.asList("geek", "gefk");
        String startWord2 = "gedk";
        String targetWord2 = "geek";
        
        System.out.println("Test 2:");
        System.out.println("WordList: " + wordList2);
        System.out.println("Start: " + startWord2 + ", Target: " + targetWord2);
        System.out.println("BFS Result: " + solution.wordLadderLength(startWord2, targetWord2, wordList2)); // Expected: 2
        System.out.println("Preprocessing Result: " + solution.wordLadderLengthWithPreprocessing(startWord2, targetWord2, wordList2)); // Expected: 2
        System.out.println();
        
        // Test case 3
        List<String> wordList3 = Arrays.asList("hot", "dot", "dog", "lot", "log");
        String startWord3 = "hit";
        String targetWord3 = "cog";
        
        System.out.println("Test 3:");
        System.out.println("WordList: " + wordList3);
        System.out.println("Start: " + startWord3 + ", Target: " + targetWord3);
        System.out.println("BFS Result: " + solution.wordLadderLength(startWord3, targetWord3, wordList3)); // Expected: 0
        System.out.println("Graph-based Result: " + solution.wordLadderLengthGraphBased(startWord3, targetWord3, wordList3)); // Expected: 0
        System.out.println();
        
        // Test case 4 - Successful transformation
        List<String> wordList4 = Arrays.asList("hot", "dot", "dog", "lot", "log", "cog");
        
        System.out.println("Test 4 (with 'cog' in wordList):");
        System.out.println("WordList: " + wordList4);
        System.out.println("Start: " + startWord3 + ", Target: " + targetWord3);
        System.out.println("BFS Result: " + solution.wordLadderLength(startWord3, targetWord3, wordList4)); // Expected: 5
    }
}

