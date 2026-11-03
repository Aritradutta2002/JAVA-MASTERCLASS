package dsa.algorithms.greedy.intervals;

import java.util.*;

/**
 * Merge Intervals (LeetCode 56) - MEDIUM
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Merge overlapping intervals
 * Time: O(n log n), Space: O(n)
 */
public class MergeIntervals {
    
    public int[][] merge(int[][] intervals) {
        if (intervals.length <= 1) return intervals;
        
        // Sort by start time
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        
        List<int[]> result = new ArrayList<>();
        int[] current = intervals[0];
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] <= current[1]) {
                // Overlapping - merge
                current[1] = Math.max(current[1], intervals[i][1]);
            } else {
                // Non-overlapping - add current and move to next
                result.add(current);
                current = intervals[i];
            }
        }
        
        result.add(current);
        return result.toArray(new int[result.size()][]);
    }
    
    public static void main(String[] args) {
        MergeIntervals solution = new MergeIntervals();
        
        int[][] intervals = {{1, 3}, {2, 6}, {8, 10}, {15, 18}};
        System.out.println(Arrays.deepToString(solution.merge(intervals)));
        // [[1, 6], [8, 10], [15, 18]]
    }
}

