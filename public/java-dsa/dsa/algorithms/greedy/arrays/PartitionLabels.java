package dsa.algorithms.greedy.arrays;

import java.util.*;

/**
 * Partition Labels (LeetCode 763) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Partition string into as many parts as possible
 * Time: O(n), Space: O(1)
 */
public class PartitionLabels {
    
    public List<Integer> partitionLabels(String s) {
        // Store last occurrence of each character
        int[] last = new int[26];
        for (int i = 0; i < s.length(); i++) {
            last[s.charAt(i) - 'a'] = i;
        }
        
        List<Integer> result = new ArrayList<>();
        int start = 0, end = 0;
        
        for (int i = 0; i < s.length(); i++) {
            end = Math.max(end, last[s.charAt(i) - 'a']);
            
            if (i == end) {
                result.add(end - start + 1);
                start = i + 1;
            }
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        PartitionLabels solution = new PartitionLabels();
        
        System.out.println(solution.partitionLabels("ababcbacadefegdehijhklij"));
        // [9, 7, 8]
    }
}

