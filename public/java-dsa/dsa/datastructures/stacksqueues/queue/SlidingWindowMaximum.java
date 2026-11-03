package dsa.datastructures.stacksqueues.queue;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;

/*
 *   Author : Aritra
 *   LeetCode #239 - Sliding Window Maximum (Hard)
 *   Problem: Find maximum element in each sliding window of size k
 *   Time Complexity: O(n), Space Complexity: O(k)
 */

public class SlidingWindowMaximum {
    public static void main(String[] args) {
        SlidingWindowMaximum solution = new SlidingWindowMaximum();
        
        int[] nums1 = {1, 3, -1, -3, 5, 3, 6, 7};
        int k1 = 3;
        System.out.println("Input: " + Arrays.toString(nums1) + ", k = " + k1);
        System.out.println("Output: " + Arrays.toString(solution.maxSlidingWindow(nums1, k1)));
        // Expected: [3, 3, 5, 5, 6, 7]
        
        int[] nums2 = {1};
        int k2 = 1;
        System.out.println("\nInput: " + Arrays.toString(nums2) + ", k = " + k2);
        System.out.println("Output: " + Arrays.toString(solution.maxSlidingWindow(nums2, k2)));
        // Expected: [1]
    }
    
    public int[] maxSlidingWindow(int[] nums, int k) {
        if (nums == null || nums.length == 0) {
            return new int[0];
        }
        
        int n = nums.length;
        int[] result = new int[n - k + 1];
        Deque<Integer> deque = new ArrayDeque<>(); // stores indices
        
        for (int i = 0; i < n; i++) {
            // Remove elements outside current window
            while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.pollFirst();
            }
            
            // Remove smaller elements from rear (they will never be maximum)
            while (!deque.isEmpty() && nums[deque.peekLast()] < nums[i]) {
                deque.pollLast();
            }
            
            deque.offerLast(i);
            
            // Add to result when window is complete
            if (i >= k - 1) {
                result[i - k + 1] = nums[deque.peekFirst()];
            }
        }
        
        return result;
    }
}

