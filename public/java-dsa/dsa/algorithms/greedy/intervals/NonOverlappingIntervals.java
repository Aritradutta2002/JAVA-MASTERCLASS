package dsa.algorithms.greedy.intervals;

import java.util.*;

/**
 * Non-overlapping Intervals (LeetCode 435) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Find minimum number of intervals to remove to make rest non-overlapping
 * Time: O(n log n), Space: O(1)
 * 
 * Key: Sort by end time, greedily select intervals that end earliest
 */
public class NonOverlappingIntervals {
    
    public int eraseOverlapIntervals(int[][] intervals) {
        if (intervals.length == 0) return 0;
        
        // Sort by end time (greedy choice)
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
        
        int count = 0;
        int end = intervals[0][1];
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < end) {
                // Overlapping - remove current interval
                count++;
            } else {
                // Non-overlapping - update end
                end = intervals[i][1];
            }
        }
        
        return count;
    }
    
    // Alternative: Sort by start time
    public int eraseOverlapIntervalsAlt(int[][] intervals) {
        if (intervals.length == 0) return 0;
        
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        
        int count = 0;
        int end = intervals[0][1];
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < end) {
                // Overlapping - remove the one with larger end
                count++;
                end = Math.min(end, intervals[i][1]);
            } else {
                end = intervals[i][1];
            }
        }
        
        return count;
    }
    
    public static void main(String[] args) {
        NonOverlappingIntervals solution = new NonOverlappingIntervals();
        
        int[][] intervals1 = {{1, 2}, {2, 3}, {3, 4}, {1, 3}};
        System.out.println(solution.eraseOverlapIntervals(intervals1)); // 1
        
        int[][] intervals2 = {{1, 2}, {1, 2}, {1, 2}};
        System.out.println(solution.eraseOverlapIntervals(intervals2)); // 2
    }
}

