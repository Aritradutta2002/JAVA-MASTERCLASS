package dsa.datastructures.graphs.problems.hard;
import java.util.*;

/**
 * LeetCode 126: Word Ladder II
 * 
 * Problem: Given two words, beginWord and endWord, and a dictionary wordList,
 * return all the shortest transformation sequences from beginWord to endWord,
 * or an empty list if no such sequence exists. Each sequence should be returned
 * as a list of the words [beginWord, s1, s2, ..., sk].
 * 
 * Time Complexity: O(N * M * 26) for BFS + O(N * M) for DFS reconstruction
 * Space Complexity: O(N * M) where N is number of words, M is word length
 */
class SolutionWordLadder2 {
    public List<List<String>> findSequences(String beginWord, String endWord, List<String> wordList) {
        List<List<String>> result = new ArrayList<>();
        Set<String> wordSet = new HashSet<>(wordList);
        
        if (!wordSet.contains(endWord)) return result;
        
        // BFS to build the graph of shortest paths
        Map<String, List<String>> graph = new HashMap<>();
        Set<String> currentLevel = new HashSet<>();
        Set<String> nextLevel = new HashSet<>();
        
        currentLevel.add(beginWord);
        boolean found = false;
        
        while (!currentLevel.isEmpty() && !found) {
            // Remove current level words from wordSet to avoid revisiting
            wordSet.removeAll(currentLevel);
            
            for (String word : currentLevel) {
                char[] wordArray = word.toCharArray();
                
                for (int i = 0; i < wordArray.length; i++) {
                    char originalChar = wordArray[i];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        wordArray[i] = c;
                        String newWord = new String(wordArray);
                        
                        if (wordSet.contains(newWord)) {
                            nextLevel.add(newWord);
                            graph.computeIfAbsent(word, k -> new ArrayList<>()).add(newWord);
                            
                            if (newWord.equals(endWord)) {
                                found = true;
                            }
                        }
                    }
                    
                    wordArray[i] = originalChar;
                }
            }
            
            currentLevel = nextLevel;
            nextLevel = new HashSet<>();
        }
        
        // DFS to reconstruct all paths
        if (found) {
            List<String> path = new ArrayList<>();
            path.add(beginWord);
            dfs(graph, beginWord, endWord, path, result);
        }
        
        return result;
    }
    
    private void dfs(Map<String, List<String>> graph, String current, String endWord,
                     List<String> path, List<List<String>> result) {
        if (current.equals(endWord)) {
            result.add(new ArrayList<>(path));
            return;
        }
        
        if (!graph.containsKey(current)) return;
        
        for (String next : graph.get(current)) {
            path.add(next);
            dfs(graph, next, endWord, path, result);
            path.remove(path.size() - 1);
        }
    }
    
    /**
     * Bidirectional BFS approach (more efficient)
     */
    public List<List<String>> findLaddersBidirectional(String beginWord, String endWord, List<String> wordList) {
        List<List<String>> result = new ArrayList<>();
        Set<String> wordSet = new HashSet<>(wordList);
        
        if (!wordSet.contains(endWord)) return result;
        
        Map<String, List<String>> graph = new HashMap<>();
        Set<String> beginSet = new HashSet<>();
        Set<String> endSet = new HashSet<>();
        
        beginSet.add(beginWord);
        endSet.add(endWord);
        
        boolean found = false;
        boolean reverse = false;
        
        while (!beginSet.isEmpty() && !endSet.isEmpty() && !found) {
            // Always expand the smaller set
            if (beginSet.size() > endSet.size()) {
                Set<String> temp = beginSet;
                beginSet = endSet;
                endSet = temp;
                reverse = !reverse;
            }
            
            wordSet.removeAll(beginSet);
            Set<String> nextLevel = new HashSet<>();
            
            for (String word : beginSet) {
                char[] wordArray = word.toCharArray();
                
                for (int i = 0; i < wordArray.length; i++) {
                    char originalChar = wordArray[i];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        wordArray[i] = c;
                        String newWord = new String(wordArray);
                        
                        String key = reverse ? newWord : word;
                        String value = reverse ? word : newWord;
                        
                        if (endSet.contains(newWord)) {
                            found = true;
                            graph.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                        }
                        
                        if (!found && wordSet.contains(newWord)) {
                            nextLevel.add(newWord);
                            graph.computeIfAbsent(key, k -> new ArrayList<>()).add(value);
                        }
                    }
                    
                    wordArray[i] = originalChar;
                }
            }
            
            beginSet = nextLevel;
        }
        
        if (found) {
            List<String> path = new ArrayList<>();
            path.add(beginWord);
            dfs(graph, beginWord, endWord, path, result);
        }
        
        return result;
    }
    
    /**
     * BFS with level tracking and parent mapping
     */
    public List<List<String>> findLaddersWithParents(String beginWord, String endWord, List<String> wordList) {
        List<List<String>> result = new ArrayList<>();
        Set<String> wordSet = new HashSet<>(wordList);
        
        if (!wordSet.contains(endWord)) return result;
        
        Map<String, List<String>> parents = new HashMap<>();
        Queue<String> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.offer(beginWord);
        visited.add(beginWord);
        boolean found = false;
        
        while (!queue.isEmpty() && !found) {
            Set<String> currentLevelVisited = new HashSet<>();
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                String word = queue.poll();
                char[] wordArray = word.toCharArray();
                
                for (int j = 0; j < wordArray.length; j++) {
                    char originalChar = wordArray[j];
                    
                    for (char c = 'a'; c <= 'z'; c++) {
                        if (c == originalChar) continue;
                        
                        wordArray[j] = c;
                        String newWord = new String(wordArray);
                        
                        if (wordSet.contains(newWord)) {
                            if (newWord.equals(endWord)) {
                                found = true;
                            }
                            
                            parents.computeIfAbsent(newWord, k -> new ArrayList<>()).add(word);
                            
                            if (!visited.contains(newWord)) {
                                currentLevelVisited.add(newWord);
                            }
                        }
                    }
                    
                    wordArray[j] = originalChar;
                }
            }
            
            visited.addAll(currentLevelVisited);
            queue.addAll(currentLevelVisited);
        }
        
        if (found) {
            List<String> path = new ArrayList<>();
            buildPaths(endWord, beginWord, parents, path, result);
        }
        
        return result;
    }
    
    private void buildPaths(String word, String beginWord, Map<String, List<String>> parents,
                           List<String> path, List<List<String>> result) {
        if (word.equals(beginWord)) {
            path.add(0, word);
            result.add(new ArrayList<>(path));
            path.remove(0);
            return;
        }
        
        path.add(0, word);
        
        if (parents.containsKey(word)) {
            for (String parent : parents.get(word)) {
                buildPaths(parent, beginWord, parents, path, result);
            }
        }
        
        path.remove(0);
    }
    
    // Test method with provided examples
    public static void main(String[] args) {
        SolutionWordLadder2 solution = new SolutionWordLadder2();
        
        // Test case 1: "der" -> "dfs"
        String startWord1 = "der";
        String targetWord1 = "dfs";
        List<String> wordList1 = Arrays.asList("des", "der", "dfr", "dgt", "dfs");
        
        System.out.println("Test 1:");
        System.out.println("Start: " + startWord1 + ", Target: " + targetWord1);
        System.out.println("WordList: " + wordList1);
        List<List<String>> result1 = solution.findSequences(startWord1, targetWord1, wordList1);
        System.out.println("All shortest paths: " + result1);
        // Expected: [["der", "dfr", "dfs"], ["der", "des", "dfs"]]
        
        // Test case 2: "gedk" -> "geek"
        String startWord2 = "gedk";
        String targetWord2 = "geek";
        List<String> wordList2 = Arrays.asList("geek", "gefk");
        
        System.out.println("\nTest 2:");
        System.out.println("Start: " + startWord2 + ", Target: " + targetWord2);
        System.out.println("WordList: " + wordList2);
        List<List<String>> result2 = solution.findSequences(startWord2, targetWord2, wordList2);
        System.out.println("All shortest paths: " + result2);
        // Expected: [["gedk", "geek"]]
        
        // Test case 3: "abc" -> "xyz"
        String startWord3 = "abc";
        String targetWord3 = "xyz";
        List<String> wordList3 = Arrays.asList("abc", "ayc", "ayz", "xyz");
        
        System.out.println("\nTest 3:");
        System.out.println("Start: " + startWord3 + ", Target: " + targetWord3);
        System.out.println("WordList: " + wordList3);
        List<List<String>> result3 = solution.findSequences(startWord3, targetWord3, wordList3);
        System.out.println("All shortest paths: " + result3);
        // Expected: [["abc", "ayc", "ayz", "xyz"]]
    }
}

