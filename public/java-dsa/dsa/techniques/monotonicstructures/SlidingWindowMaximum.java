package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Sliding Window Maximum (LeetCode 239) - HARD
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft, Apple)
 * 
 * Problem: Find maximum in each sliding window of size k
 * Time: O(n), Space: O(k)
 * 
 * Key Pattern: Monotonic Decreasing Deque
 */
public class SlidingWindowMaximum {
    
    // Approach 1: Monotonic Deque (Optimal)
    public int[] maxSlidingWindow(int[] nums, int k) {
        if (nums == null || nums.length == 0) return new int[0];
        
        int n = nums.length;
        int[] result = new int[n - k + 1];
        Deque<Integer> deque = new ArrayDeque<>(); // Stores indices
        
        for (int i = 0; i < n; i++) {
            // Remove elements outside window
            while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.pollFirst();
            }
            
            // Remove smaller elements (maintain decreasing order)
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
    
    // Approach 2: Using Priority Queue (Less optimal but intuitive)
    public int[] maxSlidingWindowPQ(int[] nums, int k) {
        int n = nums.length;
        int[] result = new int[n - k + 1];
        PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> b[0] - a[0]); // Max heap
        
        for (int i = 0; i < n; i++) {
            pq.offer(new int[]{nums[i], i});
            
            // Remove elements outside window
            while (!pq.isEmpty() && pq.peek()[1] <= i - k) {
                pq.poll();
            }
            
            if (i >= k - 1) {
                result[i - k + 1] = pq.peek()[0];
            }
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        SlidingWindowMaximum solution = new SlidingWindowMaximum();
        
        // Test Case 1
        int[] nums1 = {1, 3, -1, -3, 5, 3, 6, 7};
        int k1 = 3;
        System.out.println("Sliding Window Max: " + Arrays.toString(solution.maxSlidingWindow(nums1, k1)));
        // Output: [3, 3, 5, 5, 6, 7]
        
        // Test Case 2
        int[] nums2 = {1, -1};
        int k2 = 1;
        System.out.println("Sliding Window Max: " + Arrays.toString(solution.maxSlidingWindow(nums2, k2)));
    }
}

