package dsa.algorithms.backtracking.gametheory;
/*
 Problem: Word Break II (Leetcode 140)
 Category: Backtracking + Memoization (DFS to generate all sentences)

 Approach:
   - DFS(index): returns all sentences that can be formed from s[index..end).
   - For every end in (index+1..n], if s[index:end] is a dictionary word,
     then append it to each sentence from DFS(end).
   - Memoize by index to avoid recomputation (overlapping subproblems).

 Complexity:
   - Let n = s.length, W = number of valid segmentations.
   - Time: Potentially exponential in worst-case (due to enumeration), but memoization
           avoids re-exploring the same index, yielding O(n^2 + W * avgSentenceLen).
   - Space: O(n^2) for memo strings in worst case + recursion O(n).
*/

import java.util.*;

public class WordBreakII_Leetcode140 {

    public List<String> wordBreak(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        Map<Integer, List<String>> memo = new HashMap<>();
        return dfs(0, s, dict, memo);
    }

    private List<String> dfs(int index, String s, Set<String> dict, Map<Integer, List<String>> memo) {
        if (memo.containsKey(index)) return memo.get(index);
        List<String> res = new ArrayList<>();
        int n = s.length();
        if (index == n) {
            res.add(""); // empty sentence to help concatenation
            memo.put(index, res);
            return res;
        }

        for (int end = index + 1; end <= n; end++) {
            String word = s.substring(index, end);
            if (dict.contains(word)) {
                List<String> tails = dfs(end, s, dict, memo);
                for (String tail : tails) {
                    if (tail.isEmpty()) {
                        res.add(word);
                    } else {
                        res.add(word + " " + tail);
                    }
                }
            }
        }
        memo.put(index, res);
        return res;
    }

    // Optional optimization: Early pruning using possible lengths of words
    public List<String> wordBreakWithLengths(String s, List<String> wordDict) {
        Set<String> dict = new HashSet<>(wordDict);
        Set<Integer> lens = new HashSet<>();
        for (String w : wordDict) lens.add(w.length());
        Map<Integer, List<String>> memo = new HashMap<>();
        return dfsLen(0, s, dict, lens, memo);
    }

    private List<String> dfsLen(int index, String s, Set<String> dict, Set<Integer> lens, Map<Integer, List<String>> memo) {
        if (memo.containsKey(index)) return memo.get(index);
        List<String> res = new ArrayList<>();
        int n = s.length();
        if (index == n) {
            res.add("");
            memo.put(index, res);
            return res;
        }
        for (int L : lens) {
            int end = index + L;
            if (end <= n) {
                String word = s.substring(index, end);
                if (dict.contains(word)) {
                    for (String tail : dfsLen(end, s, dict, lens, memo)) {
                        res.add(tail.isEmpty() ? word : word + " " + tail);
                    }
                }
            }
        }
        memo.put(index, res);
        return res;
    }

    // Demo
    public static void main(String[] args) {
        WordBreakII_Leetcode140 solver = new WordBreakII_Leetcode140();

        String s1 = "catsanddog";
        List<String> dict1 = Arrays.asList("cat", "cats", "and", "sand", "dog");
        List<String> ans1 = solver.wordBreak(s1, dict1);
        System.out.println("Input: " + s1 + ", dict=" + dict1);
        System.out.println("Sentences: " + ans1);

        String s2 = "pineapplepenapple";
        List<String> dict2 = Arrays.asList("apple", "pen", "applepen", "pine", "pineapple");
        List<String> ans2 = solver.wordBreak(s2, dict2);
        System.out.println("Input: " + s2 + ", dict=" + dict2);
        System.out.println("Sentences: " + ans2);

        String s3 = "catsandog";
        List<String> dict3 = Arrays.asList("cats", "dog", "sand", "and", "cat");
        List<String> ans3 = solver.wordBreak(s3, dict3);
        System.out.println("Input: " + s3 + ", dict=" + dict3);
        System.out.println("Sentences: " + ans3); // []
    }
}
