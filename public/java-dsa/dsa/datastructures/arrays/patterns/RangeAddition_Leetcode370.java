package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 370: RANGE ADDITION (Premium)
 * Difficulty: Medium
 * Pattern: Difference Array
 * 
 * PROBLEM STATEMENT:
 * You are given an integer length and an array updates where 
 * updates[i] = [startIdx_i, endIdx_i, inc_i].
 * 
 * You have an array arr of length with all zeros, and you have some 
 * operation to apply on arr. In the ith operation, you should increment 
 * all elements arr[startIdx_i], arr[startIdx_i + 1], ..., arr[endIdx_i] by inc_i.
 * 
 * Return arr after applying all the updates.
 * 
 * EXAMPLE:
 * Input: length = 5, updates = [[1,3,2],[2,4,3],[0,2,-2]]
 * Output: [-2,0,3,5,3]
 * 
 * Explanation:
 * Initial: [0,0,0,0,0]
 * After [1,3,2]: [0,2,2,2,0]
 * After [2,4,3]: [0,2,5,5,3]
 * After [0,2,-2]: [-2,0,3,5,3]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: BRUTE FORCE
 * ═══════════════════════════════════════════════════════════════════════════════
 * TIME: O(n*k) where k is number of updates
 */

import java.util.*;

class RangeAddition_BruteForce {
    public int[] getModifiedArray(int length, int[][] updates) {
        int[] result = new int[length];
        
        for (int[] update : updates) {
            int start = update[0];
            int end = update[1];
            int inc = update[2];
            
            for (int i = start; i <= end; i++) {
                result[i] += inc;
            }
        }
        
        return result;
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: DIFFERENCE ARRAY (OPTIMAL)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * Instead of updating entire range, mark boundaries:
 * - Add inc at start position
 * - Subtract inc at (end+1) position
 * - Then compute prefix sum to get final array
 * 
 * WHY IT WORKS:
 * Difference array stores: diff[i] = arr[i] - arr[i-1]
 * When we do prefix sum on diff, we get original array!
 * 
 * To increment range [start, end] by inc:
 * - diff[start] += inc (increases from start onwards)
 * - diff[end+1] -= inc (cancels increase after end)
 * 
 * EXAMPLE: length=5, updates=[[1,3,2]]
 * 
 * diff = [0,0,0,0,0,0] (size n+1 for safety)
 * 
 * Update [1,3,2]:
 * diff[1] += 2 → [0,2,0,0,0,0]
 * diff[4] -= 2 → [0,2,0,0,-2,0]
 * 
 * Prefix sum: [0,2,2,2,0,0]
 * Result: [0,2,2,2,0]
 * 
 * TIME: O(n+k) - Much better!
 * SPACE: O(n)
 */

public class RangeAddition_Leetcode370 {
    
    public int[] getModifiedArray(int length, int[][] updates) {
        int[] diff = new int[length + 1];  // Extra space to avoid overflow
        
        // Apply all updates to difference array
        for (int[] update : updates) {
            int start = update[0];
            int end = update[1];
            int inc = update[2];
            
            diff[start] += inc;        // Start incrementing from 'start'
            diff[end + 1] -= inc;      // Stop incrementing after 'end'
        }
        
        // Compute prefix sum to get result
        int[] result = new int[length];
        result[0] = diff[0];
        
        for (int i = 1; i < length; i++) {
            result[i] = result[i - 1] + diff[i];
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        RangeAddition_Leetcode370 solution = new RangeAddition_Leetcode370();
        
        int[][] updates = {{1,3,2}, {2,4,3}, {0,2,-2}};
        System.out.println("Updates: " + Arrays.deepToString(updates));
        System.out.println("Output: " + Arrays.toString(solution.getModifiedArray(5, updates)));
    }
}

/*
 * KEY TAKEAWAYS:
 * 1. Difference array: O(1) range updates
 * 2. Mark boundaries instead of updating entire range
 * 3. Final step: prefix sum to reconstruct array
 * 4. Great for multiple range update queries
 */


