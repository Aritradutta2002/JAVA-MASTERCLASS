package JAVA_DSA.DYNAMIC_PROGRAMMING.java.string_dp;

import java.util.*;

/**
 * WORD BREAK - Leetcode 139
 * Difficulty: Medium
 * Companies: Google, Amazon, Facebook, Microsoft, Apple, Uber, LinkedIn
 * 
 * PROBLEM:
 * Given a string s and a dictionary of strings wordDict, return true if s can be
 * segmented into a space-separated sequence of one or more dictionary words.
 * Note: The same word in dictionary may be reused multiple times.
 * 
 * EXAMPLES:
 * Input: s = "leetcode", wordDict = ["leet","code"]
 * Output: true
 * Explanation: "leetcode" can be segmented as "leet code".
 * 
 * Input: s = "applepenapple", wordDict = ["apple","pen"]
 * Output: true
 * Explanation: "applepenapple" can be segmented as "apple pen apple".
 * 
 * Input: s = "catsandog", wordDict = ["cats","dog","sand","and","cat"]
 * Output: false
 * 
 * PATTERN: 1D DP on Strings
 * KEY INSIGHT: Check if string up to index i can be broken by checking all possible last words
 */

public class WordBreak_Leetcode139 {
    
    // ========== APPROACH 1: RECURSION WITH SET (TLE) ==========
    // Time: O(2^n) - Try every partition
    // Space: O(n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * Try every possible way to split the string:
     * 
     * For "leetcode" with ["leet", "code"]:
     * - Try "l" + wordBreak("eetcode") → No "l" in dict
     * - Try "le" + wordBreak("etcode") → No "le" in dict
     * - Try "lee" + wordBreak("tcode") → No "lee" in dict
     * - Try "leet" + wordBreak("code") → "leet" in dict! ✓
     *   - Try "c" + wordBreak("ode") → No "c" in dict
     *   - Try "co" + wordBreak("de") → No "co" in dict
     *   - Try "cod" + wordBreak("e") → No "cod" in dict
     *   - Try "code" + wordBreak("") → "code" in dict! ✓ BASE CASE
     * Return TRUE
     */
    public boolean wordBreak_Recursive(String s, List<String> wordDict) {
        Set<String> wordSet = new HashSet<>(wordDict);
        return wordBreakRec(s, wordSet, 0);
    }
    
    private boolean wordBreakRec(String s, Set<String> wordSet, int start) {
        // Base case: reached end of string
        if (start == s.length()) return true;
        
        // Try all possible end positions
        for (int end = start + 1; end <= s.length(); end++) {
            String word = s.substring(start, end);
            // If current word is in dictionary AND rest can be broken
            if (wordSet.contains(word) && wordBreakRec(s, wordSet, end)) {
                return true;
            }
        }
        
        return false;
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(n^2 * m) where m is average word length
    // Space: O(n) - Memo array + recursion
    /**
     * BEGINNER'S EXPLANATION:
     * Same as recursion but cache results for each starting position.
     * memo[i] = can we break string starting from index i?
     */
    public boolean wordBreak_Memoization(String s, List<String> wordDict) {
        Set<String> wordSet = new HashSet<>(wordDict);
        Boolean[] memo = new Boolean[s.length()];
        return wordBreakMemo(s, wordSet, 0, memo);
    }
    
    private boolean wordBreakMemo(String s, Set<String> wordSet, int start, Boolean[] memo) {
        if (start == s.length()) return true;
        
        // Return cached result
        if (memo[start] != null) return memo[start];
        
        // Try all possible splits
        for (int end = start + 1; end <= s.length(); end++) {
            String word = s.substring(start, end);
            if (wordSet.contains(word) && wordBreakMemo(s, wordSet, end, memo)) {
                memo[start] = true;
                return true;
            }
        }
        
        memo[start] = false;
        return false;
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ RECOMMENDED ==========
    // Time: O(n^2 * m) - n^2 for loops, m for substring comparison
    // Space: O(n) - DP array
    /**
     * BEGINNER'S EXPLANATION:
     * dp[i] = true if string up to index i can be segmented
     * 
     * Build from left to right:
     * - dp[0] = true (empty string can always be segmented)
     * - For each position i, check all previous positions j
     * - If dp[j] is true AND s[j:i] is in dict, then dp[i] = true
     * 
     * Visualization for "leetcode" with ["leet", "code"]:
     * 
     * Index:  0  1  2  3  4  5  6  7  8
     * String: "" l  e  e  t  c  o  d  e
     * DP:     T  F  F  F  T  F  F  F  T
     *         ↑           ↑           ↑
     *       empty      "leet"    "leet"+"code"
     * 
     * How dp[4] becomes TRUE:
     * - Check j=0: s[0:4]="leet" is in dict, dp[0]=true → dp[4]=true!
     * 
     * How dp[8] becomes TRUE:
     * - Check j=0: s[0:8]="leetcode" not in dict
     * - Check j=1: s[1:8]="eetcode" not in dict
     * - ...
     * - Check j=4: s[4:8]="code" is in dict, dp[4]=true → dp[8]=true!
     */
    public boolean wordBreak(String s, List<String> wordDict) {
        Set<String> wordSet = new HashSet<>(wordDict);
        int n = s.length();
        
        // dp[i] = true if s[0:i] can be segmented
        boolean[] dp = new boolean[n + 1];
        dp[0] = true; // Empty string
        
        // For each ending position
        for (int i = 1; i <= n; i++) {
            // Check all possible starting positions for last word
            for (int j = 0; j < i; j++) {
                // If prefix s[0:j] can be segmented
                // AND s[j:i] is a valid word
                if (dp[j] && wordSet.contains(s.substring(j, i))) {
                    dp[i] = true;
                    break; // Found one way, no need to check more
                }
            }
        }
        
        return dp[n];
    }
    
    
    // ========== APPROACH 4: BFS APPROACH (Alternative) ==========
    // Time: O(n^2 * m)
    // Space: O(n)
    /**
     * BEGINNER'S EXPLANATION:
     * Think of it as a graph problem!
     * - Each index is a node
     * - Edge from i to j if s[i:j] is in dictionary
     * - Question: Can we reach index n starting from 0?
     * 
     * Use BFS to explore all reachable positions.
     */
    public boolean wordBreak_BFS(String s, List<String> wordDict) {
        Set<String> wordSet = new HashSet<>(wordDict);
        Queue<Integer> queue = new LinkedList<>();
        boolean[] visited = new boolean[s.length()];
        
        queue.offer(0);
        
        while (!queue.isEmpty()) {
            int start = queue.poll();
            
            if (start == s.length()) return true;
            
            // Try all possible next positions
            for (int end = start + 1; end <= s.length(); end++) {
                // Skip if already visited this position
                if (visited[end - 1]) continue;
                
                if (wordSet.contains(s.substring(start, end))) {
                    queue.offer(end);
                    visited[end - 1] = true;
                }
            }
        }
        
        return false;
    }
    
    
    // ========== OPTIMIZATION: Using Trie (Advanced) ==========
    /**
     * If wordDict is very large, use Trie for O(1) word lookup
     * This is an advanced optimization - mention only if interviewer asks
     */
    class TrieNode {
        TrieNode[] children = new TrieNode[26];
        boolean isWord = false;
    }
    
    public boolean wordBreak_Trie(String s, List<String> wordDict) {
        // Build Trie
        TrieNode root = new TrieNode();
        for (String word : wordDict) {
            TrieNode node = root;
            for (char c : word.toCharArray()) {
                if (node.children[c - 'a'] == null) {
                    node.children[c - 'a'] = new TrieNode();
                }
                node = node.children[c - 'a'];
            }
            node.isWord = true;
        }
        
        // DP with Trie
        int n = s.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true;
        
        for (int i = 1; i <= n; i++) {
            for (int j = i - 1; j >= 0; j--) {
                if (dp[j]) {
                    // Check if s[j:i] exists in Trie
                    TrieNode node = root;
                    boolean found = true;
                    for (int k = j; k < i; k++) {
                        char c = s.charAt(k);
                        if (node.children[c - 'a'] == null) {
                            found = false;
                            break;
                        }
                        node = node.children[c - 'a'];
                    }
                    if (found && node.isWord) {
                        dp[i] = true;
                        break;
                    }
                }
            }
        }
        
        return dp[n];
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        WordBreak_Leetcode139 solution = new WordBreak_Leetcode139();
        
        // Test case 1
        String s1 = "leetcode";
        List<String> dict1 = Arrays.asList("leet", "code");
        System.out.println("Input: s = \"" + s1 + "\", wordDict = " + dict1);
        System.out.println("Output: " + solution.wordBreak(s1, dict1)); // true
        System.out.println();
        
        // Test case 2
        String s2 = "applepenapple";
        List<String> dict2 = Arrays.asList("apple", "pen");
        System.out.println("Input: s = \"" + s2 + "\", wordDict = " + dict2);
        System.out.println("Output: " + solution.wordBreak(s2, dict2)); // true
        System.out.println();
        
        // Test case 3
        String s3 = "catsandog";
        List<String> dict3 = Arrays.asList("cats", "dog", "sand", "and", "cat");
        System.out.println("Input: s = \"" + s3 + "\", wordDict = " + dict3);
        System.out.println("Output: " + solution.wordBreak(s3, dict3)); // false
        System.out.println();
        
        // Test case 4: Reuse words
        String s4 = "aaaaaaa";
        List<String> dict4 = Arrays.asList("aa", "aaa");
        System.out.println("Input: s = \"" + s4 + "\", wordDict = " + dict4);
        System.out.println("Output: " + solution.wordBreak(s4, dict4)); // true
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. PATTERN RECOGNITION:
 *    - String partitioning problem
 *    - Can be solved with DP, BFS, or DFS
 *    - Most intuitive: Bottom-up DP
 * 
 * 2. DP STATE DEFINITION:
 *    dp[i] = true if substring s[0:i] can be segmented
 *    
 * 3. RECURRENCE RELATION:
 *    dp[i] = true if there exists j < i such that:
 *            dp[j] == true AND s[j:i] is in wordDict
 * 
 * 4. WHY THIS WORKS:
 *    - If we can segment s[0:j] into words
 *    - AND s[j:i] is a valid word
 *    - Then we can segment s[0:i] into words!
 * 
 * 5. FOLLOW-UP QUESTIONS:
 *    - Print all possible segmentations? → Word Break II (Leetcode 140)
 *    - What if words have different costs? → Add cost tracking
 *    - Find minimum number of words? → Track count in DP
 *    - Case insensitive? → Convert to lowercase first
 * 
 * 6. OPTIMIZATION TRICKS:
 *    - Use Set instead of List for O(1) lookup
 *    - Break inner loop early once dp[i] is true
 *    - Use Trie for very large dictionary
 *    - Prune: Start inner loop from i-maxWordLength
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 139: Word Break (this)
 *    - Leetcode 140: Word Break II (return all segmentations)
 *    - Leetcode 472: Concatenated Words
 *    - Leetcode 1745: Palindrome Partitioning IV
 * 
 * 8. INTERVIEW STRATEGY:
 *    - Start with recursive explanation
 *    - Draw DP array for small example
 *    - Explain recurrence clearly
 *    - Code bottom-up DP
 *    - Mention BFS alternative if time permits
 *    - Discuss optimization with Trie
 * 
 * 9. COMMON MISTAKES:
 *    - Using List instead of Set (slower lookup)
 *    - Off-by-one errors in substring indices
 *    - Not handling empty string base case
 *    - Forgetting that words can be reused
 *    - Not breaking inner loop after finding solution
 * 
 * 10. TIME COMPLEXITY BREAKDOWN:
 *     - Outer loop: O(n)
 *     - Inner loop: O(n)
 *     - Substring creation: O(m) where m is word length
 *     - Set lookup: O(m) for string comparison
 *     - Total: O(n^2 * m)
 */

