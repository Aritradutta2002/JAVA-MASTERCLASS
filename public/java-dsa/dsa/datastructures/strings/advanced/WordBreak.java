package dsa.datastructures.strings.advanced;
/**
 * WORD BREAK I & II
 * LeetCode #139 (Medium) & #140 (Hard)
 * 
 * Companies: Amazon, Google, Facebook, Microsoft, Bloomberg, Uber
 * Frequency: VERY HIGH
 * 
 * Problem I: Can string be segmented using dictionary words?
 * Problem II: Return all possible segmentations
 * 
 * Key Techniques:
 * I: DP - O(n²)
 * II: Backtracking with Memoization - O(2^n) worst case
 */

import java.util.*;

public class WordBreak {
    
    // WORD BREAK I: Can segment?
    // Time: O(n²*m) where m = avg word length, Space: O(n)
    public boolean wordBreak(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        int n = s.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true; // Empty string
        
        for (int i = 1; i <= n; i++) {
            for (int j = 0; j < i; j++) {
                if (dp[j] && dict.contains(s.substring(j, i))) {
                    dp[i] = true;
                    break;
                }
            }
        }
        
        return dp[n];
    }
    
    // Optimized: Check words from dict instead of all substrings
    public boolean wordBreak_Optimized(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        int n = s.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true;
        
        for (int i = 1; i <= n; i++) {
            for (String word : dict) {
                int len = word.length();
                if (i >= len && dp[i - len] && s.substring(i - len, i).equals(word)) {
                    dp[i] = true;
                    break;
                }
            }
        }
        
        return dp[n];
    }
    
    // WORD BREAK II: Return all segmentations
    // Time: O(2^n) worst case, Space: O(2^n)
    public List<String> wordBreakII(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        Map<String, List<String>> memo = new HashMap<>();
        return backtrack(s, dict, memo);
    }
    
    private List<String> backtrack(String s, Set<String> dict, Map<String, List<String>> memo) {
        if (memo.containsKey(s)) {
            return memo.get(s);
        }
        
        List<String> result = new ArrayList<>();
        
        if (s.length() == 0) {
            result.add("");
            return result;
        }
        
        for (int i = 1; i <= s.length(); i++) {
            String prefix = s.substring(0, i);
            if (dict.contains(prefix)) {
                String suffix = s.substring(i);
                List<String> suffixSegments = backtrack(suffix, dict, memo);
                
                for (String seg : suffixSegments) {
                    result.add(prefix + (seg.isEmpty() ? "" : " " + seg));
                }
            }
        }
        
        memo.put(s, result);
        return result;
    }
    
    /**
     * FOLLOW-UP 1: Concatenated Words
     * LeetCode #472 - Find words made from other words
     */
    public List<String> findAllConcatenatedWords(String[] words) {
        Set<String> dict = new HashSet<>(Arrays.asList(words));
        List<String> result = new ArrayList<>();
        
        for (String word : words) {
            dict.remove(word); // Don't use word itself
            if (canForm(word, dict)) {
                result.add(word);
            }
            dict.add(word);
        }
        
        return result;
    }
    
    private boolean canForm(String word, Set<String> dict) {
        if (word.isEmpty()) return false;
        
        int n = word.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true;
        
        for (int i = 1; i <= n; i++) {
            for (int j = 0; j < i; j++) {
                if (dp[j] && dict.contains(word.substring(j, i))) {
                    dp[i] = true;
                    break;
                }
            }
        }
        
        return dp[n];
    }
    
    /**
     * FOLLOW-UP 2: Extra Characters in String
     * LeetCode #2707 - Min chars to remove
     */
    public int minExtraChar(String s, String[] dictionary) {
        Set<String> dict = new HashSet<>(Arrays.asList(dictionary));
        int n = s.length();
        int[] dp = new int[n + 1];
        
        for (int i = 1; i <= n; i++) {
            dp[i] = dp[i - 1] + 1; // Skip current char
            
            for (int j = 0; j < i; j++) {
                if (dict.contains(s.substring(j, i))) {
                    dp[i] = Math.min(dp[i], dp[j]);
                }
            }
        }
        
        return dp[n];
    }
    
    /**
     * HELPER: Check if segmentation possible with max word length
     */
    public boolean wordBreakWithMaxLen(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        int maxLen = 0;
        for (String word : wordDict) {
            maxLen = Math.max(maxLen, word.length());
        }
        
        int n = s.length();
        boolean[] dp = new boolean[n + 1];
        dp[0] = true;
        
        for (int i = 1; i <= n; i++) {
            for (int j = Math.max(0, i - maxLen); j < i; j++) {
                if (dp[j] && dict.contains(s.substring(j, i))) {
                    dp[i] = true;
                    break;
                }
            }
        }
        
        return dp[n];
    }
    
    public static void main(String[] args) {
        WordBreak sol = new WordBreak();
        
        // Test 1: Word Break I
        String s1 = "leetcode";
        List<String> dict1 = Arrays.asList("leet", "code");
        System.out.println("=== Test 1: Word Break I ===");
        System.out.println("String: '" + s1 + "'");
        System.out.println("Dict: " + dict1);
        System.out.println("Can break: " + sol.wordBreak(s1, dict1));
        System.out.println("Expected: true\n");
        
        // Test 2: Word Break I - False case
        String s2 = "catsandog";
        List<String> dict2 = Arrays.asList("cats", "dog", "sand", "and", "cat");
        System.out.println("=== Test 2: Cannot Break ===");
        System.out.println("String: '" + s2 + "'");
        System.out.println("Dict: " + dict2);
        System.out.println("Can break: " + sol.wordBreak(s2, dict2));
        System.out.println("Expected: false\n");
        
        // Test 3: Word Break II
        String s3 = "catsanddog";
        List<String> dict3 = Arrays.asList("cat", "cats", "and", "sand", "dog");
        System.out.println("=== Test 3: Word Break II ===");
        System.out.println("String: '" + s3 + "'");
        System.out.println("Dict: " + dict3);
        System.out.println("All segmentations:");
        List<String> segments = sol.wordBreakII(s3, dict3);
        for (String seg : segments) {
            System.out.println("  " + seg);
        }
        System.out.println();
        
        // Test 4: Multiple ways
        String s4 = "pineapplepenapple";
        List<String> dict4 = Arrays.asList("apple", "pen", "applepen", "pine", "pineapple");
        System.out.println("=== Test 4: Multiple Segmentations ===");
        System.out.println("String: '" + s4 + "'");
        List<String> segments2 = sol.wordBreakII(s4, dict4);
        System.out.println("Count: " + segments2.size());
        for (String seg : segments2) {
            System.out.println("  " + seg);
        }
        System.out.println();
        
        // Test 5: Concatenated Words
        System.out.println("=== Test 5: Concatenated Words ===");
        String[] words = {"cat", "cats", "catsdogcats", "dog", "dogcatsdog", "hippopotamuses", "rat", "ratcatdogcat"};
        System.out.println("Words: " + Arrays.toString(words));
        System.out.println("Concatenated: " + sol.findAllConcatenatedWords(words));
        System.out.println("Expected: [catsdogcats, dogcatsdog, ratcatdogcat]\n");
        
        // Test 6: Min Extra Characters
        System.out.println("=== Test 6: Min Extra Characters ===");
        String s6 = "leetscode";
        String[] dict6 = {"leet", "code", "leetcode"};
        System.out.println("String: '" + s6 + "'");
        System.out.println("Dict: " + Arrays.toString(dict6));
        System.out.println("Min extra: " + sol.minExtraChar(s6, dict6));
        System.out.println("Explanation: 's' is extra\n");
        
        // DP visualization
        System.out.println("=== DP ARRAY EXPLAINED ===");
        System.out.println("For 'leetcode' with ['leet', 'code']:");
        System.out.println("Index: 0  1  2  3  4  5  6  7  8");
        System.out.println("Char:  '' l  e  e  t  c  o  d  e");
        System.out.println("DP:    T  F  F  F  T  F  F  F  T");
        System.out.println("dp[4]=true because s[0:4]='leet' in dict");
        System.out.println("dp[8]=true because dp[4] && s[4:8]='code' in dict\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. Word Break I: Classic DP - Must know!");
        System.out.println("2. dp[i] = true if s[0..i-1] can be segmented");
        System.out.println("3. Check all j < i: dp[j] && dict.contains(s[j..i])");
        System.out.println("4. Word Break II: Backtracking + Memoization");
        System.out.println("5. Amazon LOVES this problem");
        System.out.println("6. Optimization: Track max word length to limit j");
        System.out.println("7. Follow-ups: Concatenated Words, Min Extra Chars");
    }
}

