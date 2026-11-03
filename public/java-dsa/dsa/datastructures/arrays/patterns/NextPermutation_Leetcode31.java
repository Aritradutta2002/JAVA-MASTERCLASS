package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 31: NEXT PERMUTATION
 * Difficulty: Medium
 * Pattern: In-place Array Manipulation
 * 
 * PROBLEM STATEMENT:
 * A permutation of an array of integers is an arrangement of its members into a 
 * sequence or linear order.
 * 
 * The next permutation of an array of integers is the next lexicographically 
 * greater permutation of its integer. If such arrangement is not possible, 
 * rearrange it to the lowest possible order (i.e., sorted in ascending order).
 * 
 * The replacement must be in place and use only constant extra memory.
 * 
 * EXAMPLE 1:
 * Input: nums = [1,2,3]
 * Output: [1,3,2]
 * 
 * EXAMPLE 2:
 * Input: nums = [3,2,1]
 * Output: [1,2,3]
 * Explanation: [3,2,1] is the largest permutation, so wrap to smallest [1,2,3]
 * 
 * EXAMPLE 3:
 * Input: nums = [1,1,5]
 * Output: [1,5,1]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE TO UNDERSTANDING THIS PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHAT IS A PERMUTATION?
 * ----------------------
 * A permutation is an arrangement of numbers in a specific order.
 * 
 * Example: For [1,2,3], all permutations are:
 * [1,2,3] → [1,3,2] → [2,1,3] → [2,3,1] → [3,1,2] → [3,2,1]
 * 
 * WHAT IS "LEXICOGRAPHICALLY NEXT"?
 * ----------------------------------
 * It means the "next" arrangement in dictionary order.
 * 
 * Think of it like words in a dictionary:
 * - "abc" comes before "acb"
 * - "acb" comes before "bac"
 * 
 * For numbers: [1,2,3] < [1,3,2] < [2,1,3] < [2,3,1] < [3,1,2] < [3,2,1]
 * 
 * VISUAL EXAMPLE:
 * ---------------
 * All permutations of [1,2,3] in order:
 * 
 * 1. [1, 2, 3] ← Start here
 * 2. [1, 3, 2] ← Next permutation
 * 3. [2, 1, 3]
 * 4. [2, 3, 1]
 * 5. [3, 1, 2]
 * 6. [3, 2, 1] ← Last permutation
 * 
 * After last permutation, wrap around to [1, 2, 3]
 * 
 * WHY IS THIS PROBLEM HARD?
 * --------------------------
 * 1. Can't generate all permutations (too slow!)
 * 2. Must find the NEXT one directly
 * 3. Must do it IN-PLACE (no extra array)
 * 4. Must handle edge cases (largest permutation, duplicates)
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY OBSERVATION:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Look at this pattern:
 * [1, 2, 3] → [1, 3, 2]
 * [1, 3, 2] → [2, 1, 3]
 * [2, 1, 3] → [2, 3, 1]
 * [2, 3, 1] → [3, 1, 2]
 * [3, 1, 2] → [3, 2, 1]
 * [3, 2, 1] → [1, 2, 3] (wrap around)
 * 
 * Notice: When array is in DESCENDING order, it's the LARGEST permutation!
 * Example: [3, 2, 1] is largest for digits 1, 2, 3
 * 
 * To get next permutation:
 * 1. Find the rightmost "ascending" pair (where nums[i] < nums[i+1])
 * 2. Swap it with next larger element
 * 3. Reverse everything after to get smallest arrangement
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALGORITHM EXPLANATION (STEP BY STEP):
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * ALGORITHM:
 * ----------
 * Step 1: Find the PIVOT (rightmost position where nums[i] < nums[i+1])
 *         Scan from right to left
 *         
 * Step 2: If no pivot found → array is largest permutation → reverse entire array
 * 
 * Step 3: Find the element just larger than pivot (from right side)
 * 
 * Step 4: Swap pivot with this element
 * 
 * Step 5: Reverse everything after pivot position
 * 
 * DETAILED EXAMPLE 1:
 * -------------------
 * Input: [1, 3, 5, 4, 2]
 * Goal: Find next permutation
 * 
 * Step 1: Find pivot (scan right to left)
 *   [1, 3, 5, 4, 2]
 *          ↑  ↑
 *          i  i+1
 *   4 < 5? NO
 *   5 < 4? NO
 *   3 < 5? YES! Pivot found at index 1 (value = 3)
 * 
 * Step 2: Find next larger element than pivot (3) from right
 *   [1, 3, 5, 4, 2]
 *       ↑     ↑
 *     pivot   4 (next larger than 3)
 * 
 * Step 3: Swap pivot (3) with 4
 *   [1, 4, 5, 3, 2]
 * 
 * Step 4: Reverse everything after pivot position (index 1)
 *   [1, 4, | 5, 3, 2]
 *   Reverse the part after 4: [5,3,2] → [2,3,5]
 *   [1, 4, 2, 3, 5]
 * 
 * Output: [1, 4, 2, 3, 5]
 * 
 * DETAILED EXAMPLE 2:
 * -------------------
 * Input: [3, 2, 1]
 * 
 * Step 1: Find pivot
 *   [3, 2, 1]
 *   2 < 1? NO
 *   3 < 2? NO
 *   No pivot found! This is the largest permutation
 * 
 * Step 2: Since no pivot, reverse entire array
 *   [3, 2, 1] → [1, 2, 3]
 * 
 * Output: [1, 2, 3]
 * 
 * DETAILED EXAMPLE 3:
 * -------------------
 * Input: [1, 2, 3]
 * 
 * Step 1: Find pivot
 *   [1, 2, 3]
 *       ↑  ↑
 *   2 < 3? YES! Pivot at index 1 (value = 2)
 * 
 * Step 2: Find next larger than 2 from right
 *   [1, 2, 3]
 *       ↑  ↑
 *     pivot 3 (larger than 2)
 * 
 * Step 3: Swap 2 and 3
 *   [1, 3, 2]
 * 
 * Step 4: Reverse after index 1
 *   [1, 3, | 2]
 *   Reverse [2] → [2] (single element, stays same)
 *   [1, 3, 2]
 * 
 * Output: [1, 3, 2]
 * 
 * WHY DOES THIS WORK?
 * -------------------
 * 1. Pivot is rightmost ascending position
 *    → Everything to its right is descending (largest possible)
 * 
 * 2. To get next permutation:
 *    → Make a small increase at pivot
 *    → Make everything after pivot as small as possible
 * 
 * 3. Swapping with next larger gives smallest increase
 * 
 * 4. Reversing makes the suffix smallest (ascending order)
 * 
 * TIME COMPLEXITY: O(n) - At most 3 passes through array
 * SPACE COMPLEXITY: O(1) - In-place modifications only
 */

import java.util.*;

public class NextPermutation_Leetcode31 {
    
    public void nextPermutation(int[] nums) {
        int n = nums.length;
        
        // Step 1: Find the pivot (rightmost i where nums[i] < nums[i+1])
        int pivot = -1;  // Initialize to -1 (means not found)
        
        for (int i = n - 2; i >= 0; i--) {
            if (nums[i] < nums[i + 1]) {
                pivot = i;
                break;  // Found the pivot
            }
        }
        
        // Step 2: If no pivot found, array is in descending order
        // This is the largest permutation, so reverse to get smallest
        if (pivot == -1) {
            reverse(nums, 0, n - 1);
            return;
        }
        
        // Step 3: Find the smallest element larger than nums[pivot] from the right
        int nextLarger = -1;
        for (int i = n - 1; i > pivot; i--) {
            if (nums[i] > nums[pivot]) {
                nextLarger = i;
                break;
            }
        }
        
        // Step 4: Swap pivot with nextLarger
        swap(nums, pivot, nextLarger);
        
        // Step 5: Reverse the suffix after pivot to get smallest arrangement
        reverse(nums, pivot + 1, n - 1);
    }
    
    // Helper: Swap two elements
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    // Helper: Reverse array from start to end (inclusive)
    private void reverse(int[] nums, int start, int end) {
        while (start < end) {
            swap(nums, start, end);
            start++;
            end--;
        }
    }
    
    // Test the solution
    public static void main(String[] args) {
        NextPermutation_Leetcode31 solution = new NextPermutation_Leetcode31();
        
        // Test Case 1
        int[] nums1 = {1, 2, 3};
        System.out.println("Test Case 1:");
        System.out.println("Input:  " + Arrays.toString(nums1));
        solution.nextPermutation(nums1);
        System.out.println("Output: " + Arrays.toString(nums1));
        System.out.println();
        
        // Test Case 2
        int[] nums2 = {3, 2, 1};
        System.out.println("Test Case 2 (Largest permutation):");
        System.out.println("Input:  " + Arrays.toString(nums2));
        solution.nextPermutation(nums2);
        System.out.println("Output: " + Arrays.toString(nums2));
        System.out.println("(Wraps to smallest)");
        System.out.println();
        
        // Test Case 3
        int[] nums3 = {1, 1, 5};
        System.out.println("Test Case 3 (With duplicates):");
        System.out.println("Input:  " + Arrays.toString(nums3));
        solution.nextPermutation(nums3);
        System.out.println("Output: " + Arrays.toString(nums3));
        System.out.println();
        
        // Test Case 4
        int[] nums4 = {1, 3, 5, 4, 2};
        System.out.println("Test Case 4 (Complex):");
        System.out.println("Input:  " + Arrays.toString(nums4));
        solution.nextPermutation(nums4);
        System.out.println("Output: " + Arrays.toString(nums4));
        System.out.println();
        
        // Show full sequence
        System.out.println("Full sequence for [1,2,3]:");
        int[] sequence = {1, 2, 3};
        for (int i = 0; i < 6; i++) {  // 3! = 6 permutations
            System.out.println((i + 1) + ": " + Arrays.toString(sequence));
            solution.nextPermutation(sequence);
        }
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR BEGINNERS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. PATTERN RECOGNITION:
 *    - Descending sequence = largest possible arrangement
 *    - To get next: find rightmost ascending pair
 *    - This is the KEY insight!
 * 
 * 2. WHY SCAN FROM RIGHT TO LEFT?
 *    - We want the smallest increase
 *    - Rightmost change gives smallest increase
 *    - Example: 1234 → 1243 (smaller than 1324)
 * 
 * 3. WHY REVERSE THE SUFFIX?
 *    - After swapping, suffix is still descending
 *    - Descending = largest arrangement
 *    - Reversing makes it ascending = smallest arrangement
 *    - This ensures we get the NEXT permutation, not some larger one
 * 
 * 4. HANDLING DUPLICATES:
 *    - Algorithm works fine with duplicates
 *    - Example: [1,1,5] → [1,5,1] → [5,1,1]
 *    - Comparison < handles equal elements correctly
 * 
 * 5. EDGE CASES:
 *    - Single element: [1] → stays [1]
 *    - Two elements: [1,2] → [2,1] → [1,2]
 *    - All same: [1,1,1] → [1,1,1]
 *    - Largest: [3,2,1] → [1,2,3]
 * 
 * 6. COMMON MISTAKES:
 *    - Scanning left to right instead of right to left
 *    - Not reversing the suffix after swap
 *    - Using >= instead of > when finding next larger
 *    - Forgetting to handle the "largest permutation" case
 * 
 * 7. STEP-BY-STEP DEBUGGING:
 *    If stuck, trace through [1,3,5,4,2]:
 *    - Find pivot: 3 (index 1)
 *    - Find next larger: 4 (index 3)
 *    - Swap: [1,4,5,3,2]
 *    - Reverse suffix: [1,4,2,3,5]
 * 
 * 8. INTERVIEW STRATEGY:
 *    a. Explain what permutation means
 *    b. Show small example: [1,2,3] all permutations
 *    c. Identify the pattern (descending = largest)
 *    d. Explain the algorithm steps
 *    e. Code with helper functions
 *    f. Walk through test case
 *    g. Mention time/space complexity
 * 
 * 9. COMPLEXITY ANALYSIS:
 *    - Finding pivot: O(n) worst case
 *    - Finding next larger: O(n) worst case
 *    - Swapping: O(1)
 *    - Reversing: O(n) worst case
 *    - Total: O(n)
 *    - Space: O(1) (in-place)
 * 
 * 10. RELATED PROBLEMS:
 *     - Previous Permutation (reverse logic)
 *     - Permutations (Leetcode 46)
 *     - Permutations II (Leetcode 47)
 *     - Permutation Sequence (Leetcode 60)
 *     - Next Greater Element III (Leetcode 556)
 * 
 * 11. REAL-WORLD APPLICATIONS:
 *     - Generating test cases in order
 *     - Combinatorial optimization
 *     - Cryptography (key generation)
 *     - Scheduling algorithms
 * 
 * 12. OPTIMIZATION NOTES:
 *     - Can't do better than O(n) since we need to examine elements
 *     - In-place is optimal for space
 *     - This is the BEST possible solution
 * 
 * 13. VISUALIZATION TRICK:
 *     Draw the array vertically:
 *     
 *     [1, 3, 5, 4, 2]
 *     
 *     1 ──┐
 *     3 ──┤ Find this "valley"
 *     5 ──┘
 *     4
 *     2
 *     
 *     The valley (3) is the pivot!
 */

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * ALTERNATIVE APPROACH: Using Built-in Functions
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * Some languages have built-in next_permutation (like C++ STL).
 * But in interviews, you should implement it yourself!
 * 
 * Understanding this algorithm is MORE IMPORTANT than just using a library.
 */

class NextPermutation_WithComments {
    public void nextPermutation(int[] nums) {
        // The key insight: 
        // A descending sequence is the largest permutation
        // Example: [3,2,1] is larger than [1,2,3] or [2,1,3]
        
        int n = nums.length;
        int pivot = n - 2;
        
        // Step 1: Find the rightmost position where nums[i] < nums[i+1]
        // This is the "pivot" that needs to change
        while (pivot >= 0 && nums[pivot] >= nums[pivot + 1]) {
            pivot--;
        }
        
        // Step 2: If pivot is -1, entire array is descending
        // This means it's the largest permutation, wrap to smallest
        if (pivot == -1) {
            reverse(nums, 0, n - 1);
            return;
        }
        
        // Step 3: Find the smallest element larger than nums[pivot]
        // from the right side (everything right of pivot is descending)
        int successor = n - 1;
        while (nums[successor] <= nums[pivot]) {
            successor--;
        }
        
        // Step 4: Swap the pivot with its successor
        swap(nums, pivot, successor);
        
        // Step 5: Reverse the suffix to get the smallest arrangement
        // The suffix is still in descending order after swap
        // Reversing it makes it ascending (smallest possible)
        reverse(nums, pivot + 1, n - 1);
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    private void reverse(int[] nums, int start, int end) {
        while (start < end) {
            swap(nums, start++, end--);
        }
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * SUMMARY:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * This problem teaches:
 * ✓ Pattern recognition (descending = largest)
 * ✓ In-place array manipulation
 * ✓ Greedy algorithm (make smallest increase)
 * ✓ Understanding lexicographic ordering
 * ✓ Efficient O(n) solution with O(1) space
 * 
 * Master this problem and you'll handle many permutation-related questions!
 */


