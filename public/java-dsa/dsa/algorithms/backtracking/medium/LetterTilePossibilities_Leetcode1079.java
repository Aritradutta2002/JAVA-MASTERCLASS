package dsa.algorithms.backtracking.medium;

import java.util.*;

/**
 * LETTER TILE POSSIBILITIES - LeetCode 1079 (MEDIUM)
 *
 * Problem:
 * Given a string tiles (1 <= tiles.length <= 7) of uppercase English letters, return the number
 * of possible non-empty sequences of letters you can make. Letters may repeat in tiles; sequences
 * that are identical due to repeated letters should be counted once.
 *
 * Interview-ready Approaches:
 * 1) Frequency-based DFS (recommended)
 *    - Count using a 26-length frequency array; at each step, try placing any available letter.
 *    - Each placement contributes +1 (for the sequence ending here) plus the count of longer sequences.
 *    - Avoids building actual strings; counts directly and naturally deduplicates repeated letters.
 *
 * 2) Backtracking with sorting + boolean used[] and same-level de-dup
 *    - Sort characters; at each recursion level, skip choosing the same character value again if
 *      its previous identical peer at this level wasn't chosen.
 *
 * Complexity:
 * - Time: Exponential, bounded well by N! with pruning; N <= 7 so it runs fast.
 * - Space: O(1) extra for freq array (or O(N) for used[]), plus recursion depth up to N.
 */
public class LetterTilePossibilities_Leetcode1079 {

    /**
     * Approach 1: Frequency-based DFS counting.
     * - Normalize to uppercase just in case.
     */
    public int numTilePossibilities(String tiles) {
        if (tiles == null || tiles.isEmpty()) return 0;
        int[] freq = new int[26];
        for (char ch : tiles.toCharArray()) {
            if (ch >= 'a' && ch <= 'z') ch = (char) (ch - 'a' + 'A');
            freq[ch - 'A']++;
        }
        return dfs(freq);
    }

    // Returns count of all non-empty sequences constructible from remaining multiset (freq)
    private int dfs(int[] freq) {
        int count = 0;
        for (int i = 0; i < 26; i++) {
            if (freq[i] == 0) continue;

            // Choose letter i
            count++;            // sequence formed by taking this letter (as the last)
            freq[i]--;         // recurse with one fewer of this letter
            count += dfs(freq);
            freq[i]++;         // backtrack
        }
        return count;
    }

    /**
     * Approach 2: Backtracking with sorting + boolean used[] and level de-dup.
     * Useful as a secondary demonstration; counts sequences rather than listing.
     */
    public int numTilePossibilitiesBacktrack(String tiles) {
        if (tiles == null || tiles.isEmpty()) return 0;
        char[] arr = tiles.toCharArray();
        // Normalize to uppercase and sort
        for (int i = 0; i < arr.length; i++) {
            char ch = arr[i];
            if (ch >= 'a' && ch <= 'z') arr[i] = (char) (ch - 'a' + 'A');
        }
        Arrays.sort(arr);
        boolean[] used = new boolean[arr.length];
        return backtrack(arr, used);
    }

    private int backtrack(char[] arr, boolean[] used) {
        int count = 0;
        for (int i = 0; i < arr.length; i++) {
            if (used[i]) continue;
            // Skip duplicates at the same recursion level
            if (i > 0 && arr[i] == arr[i - 1] && !used[i - 1]) continue;

            used[i] = true;
            count += 1;                   // take current character as the end of a sequence
            count += backtrack(arr, used);
            used[i] = false;
        }
        return count;
    }

    public static void main(String[] args) {
        LetterTilePossibilities_Leetcode1079 solver = new LetterTilePossibilities_Leetcode1079();

        System.out.println("AAB -> " + solver.numTilePossibilities("AAB") + " (expected 8)");
        System.out.println("V -> " + solver.numTilePossibilities("V") + " (expected 1)");
        System.out.println("AAABBC -> " + solver.numTilePossibilities("AAABBC"));

        // Cross-check with alternative approach
        System.out.println("AAB (backtrack) -> " + solver.numTilePossibilitiesBacktrack("AAB") + " (expected 8)");
    }
}

