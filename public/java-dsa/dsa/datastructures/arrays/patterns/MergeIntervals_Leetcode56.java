package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 56: MERGE INTERVALS
 * Difficulty: Medium
 * Pattern: Intervals / Sorting
 * 
 * PROBLEM STATEMENT:
 * Given an array of intervals where intervals[i] = [start_i, end_i], merge all 
 * overlapping intervals, and return an array of the non-overlapping intervals 
 * that cover all the intervals in the input.
 * 
 * EXAMPLE 1:
 * Input: intervals = [[1,3],[2,6],[8,10],[15,18]]
 * Output: [[1,6],[8,10],[15,18]]
 * Explanation: Since intervals [1,3] and [2,6] overlap, merge them into [1,6].
 * 
 * EXAMPLE 2:
 * Input: intervals = [[1,4],[4,5]]
 * Output: [[1,5]]
 * Explanation: Intervals [1,4] and [4,5] are considered overlapping.
 * 
 * EXAMPLE 3:
 * Input: intervals = [[1,4],[2,3]]
 * Output: [[1,4]]
 * Explanation: Interval [2,3] is completely inside [1,4].
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE TO UNDERSTANDING THIS PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHAT ARE INTERVALS?
 * -------------------
 * An interval is a range: [start, end]
 * Example: [1, 3] means from time 1 to time 3
 * 
 * Think of it like meeting schedules:
 * - Meeting 1: 1pm to 3pm
 * - Meeting 2: 2pm to 6pm
 * - These overlap! Combined: 1pm to 6pm
 * 
 * WHEN DO INTERVALS OVERLAP?
 * ---------------------------
 * Two intervals [a, b] and [c, d] overlap if:
 * 
 * Case 1: Partial overlap
 *   [a-------b]
 *       [c-------d]
 *   Overlaps! Merge to [a, d]
 * 
 * Case 2: One contains another
 *   [a-----------d]
 *      [b---c]
 *   Overlaps! Merge to [a, d]
 * 
 * Case 3: Adjacent (touching)
 *   [a---b]
 *        [c---d]
 *   Overlaps! (b = c) Merge to [a, d]
 * 
 * Case 4: No overlap
 *   [a---b]  [c---d]
 *   No overlap! Keep both separate
 * 
 * MATHEMATICAL CONDITION:
 * -----------------------
 * Intervals [a,b] and [c,d] overlap if: c <= b
 * (assuming we've sorted by start time: a <= c)
 * 
 * WHY IS THIS PROBLEM IMPORTANT?
 * -------------------------------
 * - Very common in real applications (scheduling, time management)
 * - Tests understanding of sorting and greedy approach
 * - Foundation for many interval-based problems
 * - Frequently asked in FAANG interviews
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: BRUTE FORCE (CHECKING ALL PAIRS)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * - Compare each interval with all others
 * - Merge if they overlap
 * - Repeat until no more merges possible
 * 
 * PROBLEM: Very inefficient, complex to implement
 * TIME COMPLEXITY: O(n³) or worse
 * 
 * NOT RECOMMENDED - Just for understanding
 */

import java.util.*;

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: SORTING + MERGING (OPTIMAL SOLUTION)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * Key Insight: If we SORT intervals by start time, we only need to check 
 *              if current interval overlaps with the LAST merged interval!
 * 
 * Why? Because if intervals are sorted by start:
 * - We process them left to right
 * - Current interval can only overlap with previous one
 * - No need to check intervals that came before!
 * 
 * ALGORITHM STEPS:
 * ----------------
 * 1. Sort intervals by start time
 * 2. Initialize result with first interval
 * 3. For each subsequent interval:
 *    a. If it overlaps with last interval in result → merge
 *    b. If it doesn't overlap → add as new interval
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * Input: [[1,3], [2,6], [8,10], [15,18]]
 * 
 * Step 1: Sort by start time (already sorted)
 *   [[1,3], [2,6], [8,10], [15,18]]
 * 
 * Step 2: Initialize result with first interval
 *   result = [[1,3]]
 * 
 * Step 3: Process [2,6]
 *   Last in result: [1,3]
 *   Does [2,6] overlap with [1,3]?
 *   Check: 2 <= 3? YES! They overlap
 *   Merge: [1, max(3,6)] = [1,6]
 *   result = [[1,6]]
 * 
 * Step 4: Process [8,10]
 *   Last in result: [1,6]
 *   Does [8,10] overlap with [1,6]?
 *   Check: 8 <= 6? NO! They don't overlap
 *   Add as new interval
 *   result = [[1,6], [8,10]]
 * 
 * Step 5: Process [15,18]
 *   Last in result: [8,10]
 *   Does [15,18] overlap with [8,10]?
 *   Check: 15 <= 10? NO! They don't overlap
 *   Add as new interval
 *   result = [[1,6], [8,10], [15,18]]
 * 
 * Output: [[1,6], [8,10], [15,18]]
 * 
 * ANOTHER EXAMPLE (MORE COMPLEX):
 * -------------------------------
 * Input: [[1,4], [2,3]]
 * 
 * Step 1: Sort → [[1,4], [2,3]]
 * Step 2: result = [[1,4]]
 * Step 3: Process [2,3]
 *   Does [2,3] overlap with [1,4]? (2 <= 4)
 *   YES! Merge: [1, max(4,3)] = [1,4]
 *   result = [[1,4]]
 * 
 * Output: [[1,4]]
 * 
 * EDGE CASE - ADJACENT INTERVALS:
 * -------------------------------
 * Input: [[1,4], [4,5]]
 * 
 * Step 1: Sort → [[1,4], [4,5]]
 * Step 2: result = [[1,4]]
 * Step 3: Process [4,5]
 *   Does [4,5] overlap with [1,4]? (4 <= 4)
 *   YES! They touch at 4 (considered overlapping)
 *   Merge: [1, max(4,5)] = [1,5]
 *   result = [[1,5]]
 * 
 * Output: [[1,5]]
 * 
 * VISUAL REPRESENTATION:
 * ----------------------
 * Before merging:
 * 
 * [1,3]  |-------|
 * [2,6]      |-----------|
 * [8,10]                    |---|
 * [15,18]                          |---|
 * 
 * After merging:
 * 
 * [1,6]  |---------------|
 * [8,10]                    |---|
 * [15,18]                          |---|
 * 
 * TIME COMPLEXITY: O(n log n) - Dominated by sorting
 * SPACE COMPLEXITY: O(n) - For storing result (or O(log n) if sorting in-place)
 */

public class MergeIntervals_Leetcode56 {
    
    public int[][] merge(int[][] intervals) {
        // Edge case: empty or single interval
        if (intervals == null || intervals.length <= 1) {
            return intervals;
        }
        
        // Step 1: Sort intervals by start time
        // Arrays.sort with custom comparator
        Arrays.sort(intervals, (a, b) -> Integer.compare(a[0], b[0]));
        
        // Step 2: Use a list to store merged intervals
        List<int[]> merged = new ArrayList<>();
        
        // Step 3: Add first interval to start
        merged.add(intervals[0]);
        
        // Step 4: Process each interval
        for (int i = 1; i < intervals.length; i++) {
            int[] currentInterval = intervals[i];
            int[] lastMerged = merged.get(merged.size() - 1);
            
            // Check if current interval overlaps with last merged
            if (currentInterval[0] <= lastMerged[1]) {
                // Overlapping! Merge by extending the end time
                lastMerged[1] = Math.max(lastMerged[1], currentInterval[1]);
            } else {
                // Not overlapping, add as new interval
                merged.add(currentInterval);
            }
        }
        
        // Step 5: Convert list to array and return
        return merged.toArray(new int[merged.size()][]);
    }
    
    // Alternative cleaner version using enhanced for loop
    public int[][] mergeAlternative(int[][] intervals) {
        if (intervals.length <= 1) {
            return intervals;
        }
        
        // Sort by start time
        Arrays.sort(intervals, (a, b) -> a[0] - b[0]);
        
        List<int[]> result = new ArrayList<>();
        int[] currentInterval = intervals[0];
        result.add(currentInterval);
        
        for (int[] interval : intervals) {
            int currentEnd = currentInterval[1];
            int nextStart = interval[0];
            int nextEnd = interval[1];
            
            if (currentEnd >= nextStart) {
                // Merge by updating end time
                currentInterval[1] = Math.max(currentEnd, nextEnd);
            } else {
                // Start new interval
                currentInterval = interval;
                result.add(currentInterval);
            }
        }
        
        return result.toArray(new int[result.size()][]);
    }
    
    // Test the solution
    public static void main(String[] args) {
        MergeIntervals_Leetcode56 solution = new MergeIntervals_Leetcode56();
        
        // Test Case 1
        int[][] intervals1 = {{1,3}, {2,6}, {8,10}, {15,18}};
        System.out.println("Test Case 1:");
        System.out.println("Input: " + Arrays.deepToString(intervals1));
        System.out.println("Output: " + Arrays.deepToString(solution.merge(intervals1)));
        System.out.println("Explanation: [1,3] and [2,6] overlap → merge to [1,6]");
        System.out.println();
        
        // Test Case 2
        int[][] intervals2 = {{1,4}, {4,5}};
        System.out.println("Test Case 2:");
        System.out.println("Input: " + Arrays.deepToString(intervals2));
        System.out.println("Output: " + Arrays.deepToString(solution.merge(intervals2)));
        System.out.println("Explanation: [1,4] and [4,5] touch at 4 → merge to [1,5]");
        System.out.println();
        
        // Test Case 3
        int[][] intervals3 = {{1,4}, {2,3}};
        System.out.println("Test Case 3:");
        System.out.println("Input: " + Arrays.deepToString(intervals3));
        System.out.println("Output: " + Arrays.deepToString(solution.merge(intervals3)));
        System.out.println("Explanation: [2,3] is inside [1,4] → result is [1,4]");
        System.out.println();
        
        // Test Case 4: Unsorted input
        int[][] intervals4 = {{8,10}, {1,3}, {15,18}, {2,6}};
        System.out.println("Test Case 4 (Unsorted):");
        System.out.println("Input: " + Arrays.deepToString(intervals4));
        System.out.println("Output: " + Arrays.deepToString(solution.merge(intervals4)));
        System.out.println("Explanation: After sorting and merging");
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR BEGINNERS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. SORTING IS THE KEY:
 *    - Always sort intervals by start time first
 *    - This simplifies the problem dramatically
 *    - You only need to check adjacent intervals
 * 
 * 2. OVERLAP CONDITION:
 *    For sorted intervals [a,b] and [c,d] where a <= c:
 *    - They overlap if: c <= b
 *    - They don't overlap if: c > b
 * 
 * 3. MERGING LOGIC:
 *    When merging [a,b] and [c,d]:
 *    - New start: min(a,c) = a (since sorted)
 *    - New end: max(b,d) (take the furthest end point)
 * 
 * 4. DATA STRUCTURES:
 *    - Use ArrayList for result (don't know final size)
 *    - Convert to array at the end
 *    - Can modify last element in list easily
 * 
 * 5. COMMON MISTAKES:
 *    - Forgetting to sort first
 *    - Wrong overlap condition (using < instead of <=)
 *    - Not handling equal start times
 *    - Modifying input array directly
 * 
 * 6. EDGE CASES TO HANDLE:
 *    - Empty input: return empty
 *    - Single interval: return as is
 *    - All intervals overlap: return one merged interval
 *    - No intervals overlap: return all as is
 *    - Intervals with same start time
 *    - Adjacent intervals (touching but not overlapping)
 * 
 * 7. INTERVIEW STRATEGY:
 *    Step 1: Ask about edge cases (empty, sorted/unsorted)
 *    Step 2: Explain sorting approach
 *    Step 3: Draw visual example
 *    Step 4: Explain overlap condition
 *    Step 5: Code the solution
 *    Step 6: Walk through test case
 *    Step 7: Analyze time/space complexity
 * 
 * 8. TIME COMPLEXITY BREAKDOWN:
 *    - Sorting: O(n log n)
 *    - Merging: O(n) - single pass
 *    - Total: O(n log n) - dominated by sorting
 * 
 * 9. SPACE COMPLEXITY BREAKDOWN:
 *    - Result list: O(n) in worst case (no merges)
 *    - Sorting: O(log n) for recursion stack
 *    - Total: O(n)
 * 
 * 10. RELATED PROBLEMS TO PRACTICE:
 *     - Insert Interval (Leetcode 57)
 *     - Meeting Rooms (Leetcode 252)
 *     - Meeting Rooms II (Leetcode 253)
 *     - Non-overlapping Intervals (Leetcode 435)
 *     - Minimum Number of Arrows to Burst Balloons (Leetcode 452)
 *     - Employee Free Time (Leetcode 759)
 *     - Interval List Intersections (Leetcode 986)
 * 
 * 11. VARIATIONS:
 *     - Merge with new interval insertion
 *     - Count overlapping intervals
 *     - Find maximum overlapping intervals
 *     - Remove minimum intervals to make non-overlapping
 * 
 * 12. PATTERN RECOGNITION:
 *     When you see "intervals" or "ranges" in a problem:
 *     - Think: Should I sort first?
 *     - Usually yes! Sorting by start (or end) time helps
 *     - Then use greedy approach or sweep line algorithm
 */

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPLEXITY COMPARISON:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * | Approach           | Time Complexity | Space Complexity | Recommended? |
 * |--------------------|-----------------|------------------|--------------|
 * | Brute Force        | O(n³)           | O(n)            | No           |
 * | Sorting + Merging  | O(n log n)      | O(n)            | **Yes!**     |
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */


