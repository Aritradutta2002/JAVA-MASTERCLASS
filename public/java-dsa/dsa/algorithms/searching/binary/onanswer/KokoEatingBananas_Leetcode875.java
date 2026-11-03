package dsa.algorithms.searching.binary.onanswer;

/*
 * LEETCODE 875: KOKO EATING BANANAS
 * Difficulty: Medium
 * Pattern: Binary Search on Answer (Speed Optimization)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROBLEM STATEMENT:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Koko loves to eat bananas. There are n piles of bananas, the ith pile has piles[i] bananas.
 * The guards have gone and will come back in h hours.
 * 
 * Koko can decide her bananas-per-hour eating speed of k. Each hour, she chooses some 
 * pile of bananas and eats k bananas from that pile. If the pile has less than k bananas, 
 * she eats all of them instead and will not eat any more bananas during this hour.
 * 
 * Koko likes to eat slowly but still wants to finish eating all the bananas before 
 * the guards return.
 * 
 * Return the minimum integer k such that she can eat all the bananas within h hours.
 * 
 * EXAMPLE 1:
 * Input: piles = [3,6,7,11], h = 8
 * Output: 4
 * Explanation: 
 * - Pile 1 (3 bananas): 1 hour (eats 3 at speed 4)
 * - Pile 2 (6 bananas): 2 hours (eats 4, then 2)
 * - Pile 3 (7 bananas): 2 hours (eats 4, then 3)
 * - Pile 4 (11 bananas): 3 hours (eats 4, 4, then 3)
 * Total: 1+2+2+3 = 8 hours ✓
 * 
 * EXAMPLE 2:
 * Input: piles = [30,11,23,4,20], h = 5
 * Output: 30
 * Explanation: Must eat one pile per hour, so speed = max pile = 30
 * 
 * EXAMPLE 3:
 * Input: piles = [30,11,23,4,20], h = 6
 * Output: 23
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE: WHY BINARY SEARCH?
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * UNDERSTANDING THE PROBLEM:
 * --------------------------
 * Think of it like this:
 * - You have piles of work to complete
 * - You have a deadline (h hours)
 * - You can work at any speed (k units per hour)
 * - Find the MINIMUM speed to finish on time
 * 
 * BRUTE FORCE THINKING:
 * ---------------------
 * We could try every speed from 1 to max(piles):
 * - Speed 1: Can we finish in h hours? Check...
 * - Speed 2: Can we finish in h hours? Check...
 * - Speed 3: Can we finish in h hours? Check...
 * ...
 * 
 * But this is O(max * n) - TOO SLOW!
 * 
 * KEY OBSERVATION (MONOTONICITY):
 * -------------------------------
 * If speed K works, then K+1, K+2, ... all work too!
 * If speed K doesn't work, then K-1, K-2, ... won't work either!
 * 
 * Visual:
 * Speed:  1  2  3  4  5  6  7  8  9  10 ...
 * Works?: ✗  ✗  ✗  ✓  ✓  ✓  ✓  ✓  ✓  ✓
 *                   ↑
 *              First valid speed (our answer!)
 * 
 * This monotonic property means we can use BINARY SEARCH!
 * 
 * ANSWER SPACE:
 * -------------
 * Minimum speed: 1 (eat 1 banana per hour)
 * Maximum speed: max(piles) (eat entire largest pile in 1 hour)
 * 
 * We binary search on this range: [1, max(piles)]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * STEP-BY-STEP SOLUTION:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ALGORITHM:
 * ----------
 * 1. Define search space: [1, max(piles)]
 * 2. Binary search on speed:
 *    - Try mid speed
 *    - Can Koko finish in h hours at this speed?
 *    - If yes: Try slower speed (minimize)
 *    - If no: Need faster speed
 * 3. Return the minimum valid speed
 * 
 * VALIDATION FUNCTION:
 * --------------------
 * For a given speed k, calculate hours needed:
 * For each pile:
 *   hours += ceil(pile / k)
 *   
 * If total hours <= h, speed k is valid!
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * piles = [3, 6, 7, 11], h = 8
 * 
 * Search space: [1, 11]
 * 
 * Iteration 1:
 *   mid = 6
 *   Hours needed: ceil(3/6) + ceil(6/6) + ceil(7/6) + ceil(11/6)
 *              = 1 + 1 + 2 + 2 = 6 hours
 *   6 <= 8? YES! Speed 6 works, try slower
 *   right = 5
 * 
 * Iteration 2:
 *   left = 1, right = 5, mid = 3
 *   Hours: ceil(3/3) + ceil(6/3) + ceil(7/3) + ceil(11/3)
 *       = 1 + 2 + 3 + 4 = 10 hours
 *   10 <= 8? NO! Speed 3 too slow, need faster
 *   left = 4
 * 
 * Iteration 3:
 *   left = 4, right = 5, mid = 4
 *   Hours: ceil(3/4) + ceil(6/4) + ceil(7/4) + ceil(11/4)
 *       = 1 + 2 + 2 + 3 = 8 hours
 *   8 <= 8? YES! Speed 4 works, try slower
 *   right = 3
 * 
 * Iteration 4:
 *   left = 4, right = 3
 *   left > right, STOP
 * 
 * Answer: 4
 * 
 * TIME COMPLEXITY: O(n * log(max)) where max = max(piles)
 * SPACE COMPLEXITY: O(1)
 */

import java.util.*;

public class KokoEatingBananas_Leetcode875 {
    
    public int minEatingSpeed(int[] piles, int h) {
        // Step 1: Define search space
        int left = 1;              // Minimum speed: 1 banana per hour
        int right = getMax(piles); // Maximum speed: largest pile in 1 hour
        int result = right;        // Initialize with max (worst case)
        
        // Step 2: Binary search on speed
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            // Step 3: Check if this speed works
            if (canFinish(piles, mid, h)) {
                result = mid;      // This speed works, save it
                right = mid - 1;   // Try to find slower speed (minimize)
            } else {
                left = mid + 1;    // Speed too slow, need faster
            }
        }
        
        return result;
    }
    
    // Validation function: Can Koko finish all piles in h hours at speed k?
    private boolean canFinish(int[] piles, int speed, int h) {
        long hoursNeeded = 0;  // Use long to avoid overflow
        
        for (int pile : piles) {
            // Calculate hours needed for this pile
            // ceil(pile / speed) = (pile + speed - 1) / speed
            hoursNeeded += (pile + speed - 1) / speed;
            
            // Early termination if already exceeded h hours
            if (hoursNeeded > h) {
                return false;
            }
        }
        
        return hoursNeeded <= h;
    }
    
    // Helper: Find maximum element in array
    private int getMax(int[] piles) {
        int max = piles[0];
        for (int pile : piles) {
            max = Math.max(max, pile);
        }
        return max;
    }
    
    // Alternative ceil division (more intuitive)
    private int ceilDivision(int dividend, int divisor) {
        return (dividend + divisor - 1) / divisor;
    }
    
    public static void main(String[] args) {
        KokoEatingBananas_Leetcode875 solution = new KokoEatingBananas_Leetcode875();
        
        // Test Case 1
        int[] piles1 = {3, 6, 7, 11};
        int h1 = 8;
        System.out.println("Test Case 1:");
        System.out.println("Piles: " + Arrays.toString(piles1) + ", Hours: " + h1);
        System.out.println("Minimum eating speed: " + solution.minEatingSpeed(piles1, h1));
        System.out.println();
        
        // Test Case 2
        int[] piles2 = {30, 11, 23, 4, 20};
        int h2 = 5;
        System.out.println("Test Case 2:");
        System.out.println("Piles: " + Arrays.toString(piles2) + ", Hours: " + h2);
        System.out.println("Minimum eating speed: " + solution.minEatingSpeed(piles2, h2));
        System.out.println();
        
        // Test Case 3
        int[] piles3 = {30, 11, 23, 4, 20};
        int h3 = 6;
        System.out.println("Test Case 3:");
        System.out.println("Piles: " + Arrays.toString(piles3) + ", Hours: " + h3);
        System.out.println("Minimum eating speed: " + solution.minEatingSpeed(piles3, h3));
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR INTERVIEWS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. PATTERN RECOGNITION:
 *    Keywords: "minimum speed", "within h hours"
 *    → Think: Binary search on answer (speed)
 * 
 * 2. MONOTONICITY PROOF:
 *    If speed K works → all speeds > K also work
 *    This is the KEY insight that enables binary search!
 * 
 * 3. ANSWER SPACE BOUNDS:
 *    Minimum: 1 (slowest possible)
 *    Maximum: max(piles) (fastest needed)
 *    Don't use sum(piles) - that's unnecessarily large!
 * 
 * 4. VALIDATION FUNCTION:
 *    Simple greedy simulation:
 *    - For each pile, calculate hours needed
 *    - Sum up total hours
 *    - Compare with h
 * 
 * 5. CEILING DIVISION TRICK:
 *    Instead of: (int)Math.ceil((double)pile / speed)
 *    Use: (pile + speed - 1) / speed
 *    Avoids floating point operations!
 * 
 * 6. COMMON MISTAKES:
 *    ❌ Using right = sum(piles) (too large)
 *    ❌ Not using long for hoursNeeded (overflow)
 *    ❌ Wrong search direction (left vs right update)
 *    ❌ Forgetting to save result before narrowing
 * 
 * 7. EDGE CASES:
 *    - h == piles.length (must eat one pile per hour)
 *    - All piles same size
 *    - Single pile
 *    - Very large pile values (overflow prevention)
 * 
 * 8. INTERVIEW STRATEGY:
 *    a. Explain brute force (linear search)
 *    b. Identify monotonic property
 *    c. Propose binary search on speed
 *    d. Define bounds clearly
 *    e. Write validation function first
 *    f. Implement binary search
 *    g. Test with example
 *    h. Analyze complexity
 * 
 * 9. SIMILAR PROBLEMS:
 *    - Capacity To Ship Packages (Leetcode 1010)
 *    - Minimum Time to Repair Cars (Leetcode 2594)
 *    - Minimum Speed to Arrive on Time (Leetcode 1870)
 *    
 * 10. COMPLEXITY ANALYSIS:
 *     Time: O(n * log(max)) 
 *       - Binary search: O(log(max))
 *       - Validation: O(n)
 *     Space: O(1) - only using variables
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */

/*
 * VISUALIZATION:
 * ==============
 * 
 * Piles: [3, 6, 7, 11], h = 8
 * 
 * Speed 1: ████████████████████████████ 27 hours ✗
 * Speed 2: ██████████████ 14 hours ✗
 * Speed 3: ██████████ 10 hours ✗
 * Speed 4: ████████ 8 hours ✓ ← ANSWER
 * Speed 5: ███████ 7 hours ✓
 * Speed 6: ██████ 6 hours ✓
 * ...
 * Speed 11: ████ 4 hours ✓
 * 
 * We want the MINIMUM speed that works!
 */


