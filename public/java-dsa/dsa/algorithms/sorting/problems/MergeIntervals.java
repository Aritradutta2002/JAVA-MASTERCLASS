package dsa.algorithms.sorting.problems;
/**
 * MERGE INTERVALS
 * LeetCode #56 - Medium
 * 
 * Companies: Facebook, Amazon, Google, Microsoft, Bloomberg, Apple
 * Frequency: VERY HIGH
 * 
 * Problem: Given array of intervals, merge all overlapping intervals.
 * 
 * Key Pattern: SORT + MERGE
 * Time: O(n log n)
 * Space: O(n)
 */

import java.util.*;

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
        result.add(current); // Don't forget last interval
        
        return result.toArray(new int[result.size()][]);
    }
    
    /**
     * INSERT INTERVAL
     * LeetCode #57 - Medium
     * Given sorted intervals, insert new interval and merge
     */
    public int[][] insert(int[][] intervals, int[] newInterval) {
        List<int[]> result = new ArrayList<>();
        int i = 0;
        
        // Add all intervals before newInterval
        while (i < intervals.length && intervals[i][1] < newInterval[0]) {
            result.add(intervals[i++]);
        }
        
        // Merge overlapping intervals
        while (i < intervals.length && intervals[i][0] <= newInterval[1]) {
            newInterval[0] = Math.min(newInterval[0], intervals[i][0]);
            newInterval[1] = Math.max(newInterval[1], intervals[i][1]);
            i++;
        }
        result.add(newInterval);
        
        // Add remaining intervals
        while (i < intervals.length) {
            result.add(intervals[i++]);
        }
        
        return result.toArray(new int[result.size()][]);
    }
    
    /**
     * NON-OVERLAPPING INTERVALS
     * LeetCode #435 - Medium
     * Min intervals to remove to make non-overlapping
     */
    public int eraseOverlapIntervals(int[][] intervals) {
        if (intervals.length == 0) return 0;
        
        // Sort by END time (greedy: always pick interval that ends earliest)
        Arrays.sort(intervals, (a, b) -> a[1] - b[1]);
        
        int count = 0;
        int end = intervals[0][1];
        
        for (int i = 1; i < intervals.length; i++) {
            if (intervals[i][0] < end) {
                count++; // Remove this interval
            } else {
                end = intervals[i][1];
            }
        }
        
        return count;
    }
    
    /**
     * MEETING ROOMS II
     * LeetCode #253 - Medium (Premium)
     * Min conference rooms needed
     */
    public int minMeetingRooms(int[][] intervals) {
        int[] starts = new int[intervals.length];
        int[] ends = new int[intervals.length];
        
        for (int i = 0; i < intervals.length; i++) {
            starts[i] = intervals[i][0];
            ends[i] = intervals[i][1];
        }
        
        Arrays.sort(starts);
        Arrays.sort(ends);
        
        int rooms = 0, endPtr = 0;
        
        for (int i = 0; i < starts.length; i++) {
            if (starts[i] < ends[endPtr]) {
                rooms++; // Need new room
            } else {
                endPtr++; // Reuse room
            }
        }
        
        return rooms;
    }
    
    public static void main(String[] args) {
        MergeIntervals sol = new MergeIntervals();
        
        // Test 1: Merge Intervals
        System.out.println("=== Merge Intervals ===");
        int[][] intervals1 = {{1,3}, {2,6}, {8,10}, {15,18}};
        System.out.println("Input: " + Arrays.deepToString(intervals1));
        System.out.println("Output: " + Arrays.deepToString(sol.merge(intervals1)));
        // Output: [[1,6], [8,10], [15,18]]
        
        // Test 2: Insert Interval
        System.out.println("\n=== Insert Interval ===");
        int[][] intervals2 = {{1,3}, {6,9}};
        int[] newInterval = {2, 5};
        System.out.println("Output: " + Arrays.deepToString(sol.insert(intervals2, newInterval)));
        // Output: [[1,5], [6,9]]
        
        // Test 3: Meeting Rooms II
        System.out.println("\n=== Meeting Rooms II ===");
        int[][] meetings = {{0,30}, {5,10}, {15,20}};
        System.out.println("Min rooms needed: " + sol.minMeetingRooms(meetings)); // 2
        
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. ALWAYS sort intervals first");
        System.out.println("2. Sort by START for merge problems");
        System.out.println("3. Sort by END for greedy/counting problems");
        System.out.println("4. Watch out for edge cases: empty, single interval");
        System.out.println("5. Pattern: Sort → One pass with tracking");
    }
}

