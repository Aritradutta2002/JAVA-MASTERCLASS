package dsa.algorithms.searching.binary.onanswer;

/*
 * PAINTER'S PARTITION PROBLEM (Classic)
 * Difficulty: Medium-Hard
 * Pattern: Binary Search on Answer (Work Distribution)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * PROBLEM STATEMENT:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * You have to paint N boards of lengths {A1, A2, ... AN}. There are K painters 
 * available and each takes 1 unit time to paint 1 unit of board.
 * 
 * The problem is to find the minimum time to paint all boards under the constraints:
 * 1. Any painter will only paint continuous sections of boards (contiguous constraint)
 * 2. A painter can paint multiple boards but each board can be painted by only one painter
 * 3. All painters work simultaneously
 * 
 * Find the minimum time required to paint all boards.
 * 
 * EXAMPLE 1:
 * Input: boards = [10, 20, 30, 40], painters = 2
 * Output: 60
 * Explanation:
 * - Painter 1: [10, 20, 30] = 60 units
 * - Painter 2: [40] = 40 units
 * Time = max(60, 40) = 60
 * 
 * Other allocations:
 * - [10], [20, 30, 40] → max(10, 90) = 90
 * - [10, 20], [30, 40] → max(30, 70) = 70
 * - [10, 20, 30], [40] → max(60, 40) = 60 ✓ MINIMUM
 * 
 * EXAMPLE 2:
 * Input: boards = [10, 10, 10, 10], painters = 2
 * Output: 20
 * Explanation:
 * - Painter 1: [10, 10] = 20
 * - Painter 2: [10, 10] = 20
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * UNDERSTANDING THE PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * REAL-WORLD SCENARIO:
 * --------------------
 * Imagine K workers painting a fence divided into N sections:
 * - Each section takes different time to paint
 * - Workers work simultaneously
 * - Total time = time taken by slowest worker
 * - Goal: Minimize this maximum time
 * 
 * CONSTRAINT VISUALIZATION:
 * -------------------------
 * Boards: [10, 20, 30, 40]
 * 
 * Valid allocation (contiguous):
 * Painter 1: [10, 20]
 * Painter 2: [30, 40]
 * ✓
 * 
 * Invalid allocation (non-contiguous):
 * Painter 1: [10, 30]  ← Skipped board 20!
 * Painter 2: [20, 40]
 * ✗
 * 
 * WHY BINARY SEARCH?
 * ------------------
 * Problem type: "Minimize the maximum time"
 * 
 * Monotonic property:
 * - If we can finish painting with max time T
 * - Then we can definitely finish with max time T+1
 * - But maybe NOT with max time T-1
 * 
 * Visual:
 * Time:  20  30  40  50  60  70  80  90  100
 * Can do: ✗   ✗   ✗   ✗   ✓   ✓   ✓   ✓   ✓
 *                          ↑
 *                    Minimum time (answer!)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALGORITHM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Same as Book Allocation & Split Array!
 * 
 * 1. Binary search on answer (time)
 * 2. For each candidate time, check if K painters can finish
 * 3. Greedy validation: Give boards to painters until limit reached
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * boards = [10, 20, 30, 40], painters = 2
 * 
 * Search space: [40, 100]
 * 
 * Try mid = 70:
 *   Painter 1: 10 + 20 + 30 = 60 (adding 40 would exceed 70)
 *   Painter 2: 40
 *   Used 2 painters ✓
 *   70 works, try smaller → right = 69
 * 
 * Try mid = 54:
 *   Painter 1: 10 + 20 = 30 (adding 30 would exceed 54)
 *   Painter 2: 30 (adding 40 would exceed 54)
 *   Painter 3: 40
 *   Used 3 painters ✗ Too many!
 *   54 too small → left = 55
 * 
 * Try mid = 60:
 *   Painter 1: 10 + 20 + 30 = 60
 *   Painter 2: 40
 *   Used 2 painters ✓
 *   60 works, try smaller → right = 59
 * 
 * Continue...
 * Answer: 60
 */

import java.util.*;

public class PaintersPartition_Classic {
    
    public long paintersPartition(int[] boards, int painters) {
        int n = boards.length;
        
        if (painters > n) {
            painters = n;  // No benefit having more painters than boards
        }
        
        // Search space
        long left = getMax(boards);   // Minimum time (largest board)
        long right = getSum(boards);  // Maximum time (one painter does all)
        long result = right;
        
        // Binary search on time
        while (left <= right) {
            long mid = left + (right - left) / 2;
            
            // Can K painters finish in time 'mid'?
            if (canPaint(boards, painters, mid)) {
                result = mid;
                right = mid - 1;   // Try less time
            } else {
                left = mid + 1;    // Need more time
            }
        }
        
        return result;
    }
    
    // Can 'painters' finish all boards with max time 'maxTime' per painter?
    private boolean canPaint(int[] boards, int painters, long maxTime) {
        int paintersNeeded = 1;
        long currentTime = 0;
        
        for (int board : boards) {
            if (currentTime + board > maxTime) {
                // Current painter can't take this board
                paintersNeeded++;
                currentTime = board;
                
                if (paintersNeeded > painters) {
                    return false;
                }
            } else {
                currentTime += board;
            }
        }
        
        return true;
    }
    
    private long getMax(int[] arr) {
        long max = arr[0];
        for (int num : arr) {
            max = Math.max(max, num);
        }
        return max;
    }
    
    private long getSum(int[] arr) {
        long sum = 0;
        for (int num : arr) {
            sum += num;
        }
        return sum;
    }
    
    public static void main(String[] args) {
        PaintersPartition_Classic solution = new PaintersPartition_Classic();
        
        // Test Case 1
        int[] boards1 = {10, 20, 30, 40};
        int painters1 = 2;
        System.out.println("Test Case 1:");
        System.out.println("Boards: " + Arrays.toString(boards1));
        System.out.println("Painters: " + painters1);
        System.out.println("Minimum time: " + solution.paintersPartition(boards1, painters1));
        System.out.println();
        
        // Test Case 2
        int[] boards2 = {10, 10, 10, 10};
        int painters2 = 2;
        System.out.println("Test Case 2:");
        System.out.println("Boards: " + Arrays.toString(boards2));
        System.out.println("Painters: " + painters2);
        System.out.println("Minimum time: " + solution.paintersPartition(boards2, painters2));
        System.out.println();
        
        // Test Case 3
        int[] boards3 = {5, 5, 5, 5};
        int painters3 = 2;
        System.out.println("Test Case 3:");
        System.out.println("Boards: " + Arrays.toString(boards3));
        System.out.println("Painters: " + painters3);
        System.out.println("Minimum time: " + solution.paintersPartition(boards3, painters3));
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * PATTERN FAMILY - ALL SAME LOGIC:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. Book Allocation Problem
 *    - Allocate books to students
 *    - Minimize maximum pages per student
 * 
 * 2. Split Array Largest Sum (Leetcode 410)
 *    - Split array into k subarrays
 *    - Minimize maximum sum of any subarray
 * 
 * 3. Painter's Partition
 *    - Assign boards to painters
 *    - Minimize maximum time per painter
 * 
 * 4. Capacity To Ship Packages (Leetcode 1010)
 *    - Ship packages in D days
 *    - Minimize ship capacity
 * 
 * ALL USE SAME TEMPLATE:
 * ----------------------
 * 1. Binary search on answer: [max element, sum of elements]
 * 2. Validation: Greedy allocation
 * 3. Search direction: Minimize maximum → right = mid - 1 when valid
 * 
 * MASTER ONE → MASTER ALL!
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */

/*
 * INTERVIEW TIPS:
 * ===============
 * 
 * 1. Recognize the pattern immediately:
 *    "Minimize maximum" + "contiguous allocation" = Binary Search on Answer
 * 
 * 2. Explain the template:
 *    - Define search space
 *    - Binary search on answer
 *    - Greedy validation
 * 
 * 3. Mention similar problems:
 *    Shows breadth of knowledge
 * 
 * 4. Discuss time complexity:
 *    O(n * log(sum - max))
 *    Usually acceptable for n up to 10^6
 * 
 * 5. Handle edge cases:
 *    - painters > boards
 *    - Single board
 *    - All boards same size
 */


