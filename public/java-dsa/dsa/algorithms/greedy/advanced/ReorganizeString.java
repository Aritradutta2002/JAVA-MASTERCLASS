package dsa.algorithms.greedy.advanced;

import java.util.*;

/**
 * Reorganize String (LeetCode 767) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Rearrange string so no two adjacent characters are same
 * Time: O(n log k), Space: O(k) where k is unique characters
 */
public class ReorganizeString {
    
    public String reorganizeString(String s) {
        // Count frequency
        int[] count = new int[26];
        for (char c : s.toCharArray()) {
            count[c - 'a']++;
        }
        
        // Max heap by frequency
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> b[1] - a[1]);
        for (int i = 0; i < 26; i++) {
            if (count[i] > 0) {
                pq.offer(new int[]{i, count[i]});
            }
        }
        
        // Check if reorganization is possible
        if (pq.peek()[1] > (s.length() + 1) / 2) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        int[] prev = null;
        
        while (!pq.isEmpty()) {
            int[] current = pq.poll();
            sb.append((char) ('a' + current[0]));
            current[1]--;
            
            if (prev != null && prev[1] > 0) {
                pq.offer(prev);
            }
            
            prev = current;
        }
        
        return sb.toString();
    }
    
    // Alternative: Fill even positions first, then odd
    public String reorganizeStringAlternate(String s) {
        int[] count = new int[26];
        int maxCount = 0, maxChar = 0;
        
        for (char c : s.toCharArray()) {
            count[c - 'a']++;
            if (count[c - 'a'] > maxCount) {
                maxCount = count[c - 'a'];
                maxChar = c - 'a';
            }
        }
        
        if (maxCount > (s.length() + 1) / 2) return "";
        
        char[] result = new char[s.length()];
        int idx = 0;
        
        // Fill most frequent character first
        while (count[maxChar] > 0) {
            result[idx] = (char) ('a' + maxChar);
            idx += 2;
            count[maxChar]--;
        }
        
        // Fill remaining characters
        for (int i = 0; i < 26; i++) {
            while (count[i] > 0) {
                if (idx >= s.length()) idx = 1;
                result[idx] = (char) ('a' + i);
                idx += 2;
                count[i]--;
            }
        }
        
        return new String(result);
    }
    
    public static void main(String[] args) {
        ReorganizeString solution = new ReorganizeString();
        
        System.out.println(solution.reorganizeString("aab")); // "aba"
        System.out.println(solution.reorganizeString("aaab")); // ""
    }
}

