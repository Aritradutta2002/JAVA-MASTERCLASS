package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 169: MAJORITY ELEMENT
 * Difficulty: Easy
 * Pattern: Boyer-Moore Voting Algorithm
 * 
 * PROBLEM STATEMENT:
 * Given an array nums of size n, return the majority element.
 * The majority element is the element that appears more than ⌊n / 2⌋ times.
 * You may assume that the majority element always exists in the array.
 * 
 * EXAMPLE 1:
 * Input: nums = [3,2,3]
 * Output: 3
 * 
 * EXAMPLE 2:
 * Input: nums = [2,2,1,1,1,2,2]
 * Output: 2
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: HASHMAP (COUNT FREQUENCIES)
 * ═══════════════════════════════════════════════════════════════════════════════
 * TIME: O(n), SPACE: O(n)
 */

import java.util.*;

class MajorityElement_HashMap {
    public int majorityElement(int[] nums) {
        HashMap<Integer, Integer> counts = new HashMap<>();
        int majority = nums.length / 2;
        
        for (int num : nums) {
            counts.put(num, counts.getOrDefault(num, 0) + 1);
            if (counts.get(num) > majority) {
                return num;
            }
        }
        
        return -1;
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: SORTING
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * If an element appears > n/2 times, it must occupy the middle position
 * after sorting!
 * 
 * Example: [2,2,1,1,1,2,2]
 * Sorted:  [1,1,1,2,2,2,2]
 *               ↑
 *          Middle is 2!
 * 
 * TIME: O(n log n), SPACE: O(1)
 */

class MajorityElement_Sorting {
    public int majorityElement(int[] nums) {
        Arrays.sort(nums);
        return nums[nums.length / 2];
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 3: BOYER-MOORE VOTING ALGORITHM (OPTIMAL)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * Think of it as a battle between elements!
 * - Each occurrence of candidate: +1 vote
 * - Each occurrence of different element: -1 vote
 * - If votes reach 0, switch candidate
 * 
 * Since majority element appears > n/2 times, it will always win!
 * 
 * EXAMPLE: [2,2,1,1,1,2,2]
 * 
 * i=0: num=2, candidate=2, count=1
 * i=1: num=2, candidate=2, count=2
 * i=2: num=1, candidate=2, count=1 (different, decrease)
 * i=3: num=1, candidate=2, count=0 (reaches 0)
 * i=4: num=1, candidate=1, count=1 (switch to 1)
 * i=5: num=2, candidate=1, count=0 (reaches 0)
 * i=6: num=2, candidate=2, count=1 (switch to 2)
 * 
 * Final: candidate=2 ✓
 * 
 * WHY IT WORKS:
 * If we "cancel out" each majority element with a non-majority element,
 * there will still be majority elements left at the end!
 * 
 * TIME: O(n), SPACE: O(1) - OPTIMAL!
 */

public class MajorityElement_Leetcode169 {
    
    public int majorityElement(int[] nums) {
        int candidate = nums[0];
        int count = 1;
        
        // Phase 1: Find candidate
        for (int i = 1; i < nums.length; i++) {
            if (count == 0) {
                candidate = nums[i];
                count = 1;
            } else if (nums[i] == candidate) {
                count++;
            } else {
                count--;
            }
        }
        
        // Phase 2: Verify (not needed if majority always exists)
        // But good practice for interviews
        count = 0;
        for (int num : nums) {
            if (num == candidate) {
                count++;
            }
        }
        
        return count > nums.length / 2 ? candidate : -1;
    }
    
    public static void main(String[] args) {
        MajorityElement_Leetcode169 solution = new MajorityElement_Leetcode169();
        
        int[] nums1 = {3,2,3};
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Output: " + solution.majorityElement(nums1));
        
        int[] nums2 = {2,2,1,1,1,2,2};
        System.out.println("\nInput: " + Arrays.toString(nums2));
        System.out.println("Output: " + solution.majorityElement(nums2));
    }
}

/*
 * KEY TAKEAWAYS:
 * 1. Boyer-Moore is the optimal O(n) time, O(1) space solution
 * 2. Think of it as "voting" or "cancellation"
 * 3. Works because majority element appears > n/2 times
 * 4. For interview: Mention all approaches, code Boyer-Moore
 */


