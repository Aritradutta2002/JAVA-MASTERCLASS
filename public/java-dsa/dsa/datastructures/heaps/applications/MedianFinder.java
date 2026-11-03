package dsa.datastructures.heaps.applications;
/**
 * FIND MEDIAN FROM DATA STREAM
 * 
 * LeetCode #295 - Hard
 * Companies: Google, Facebook, Amazon, Microsoft, Apple
 * 
 * Problem: Design a data structure that supports:
 * - addNum(int num) - Add number from stream
 * - findMedian() - Return median of all elements
 * 
 * Solution: Two Heaps
 * - Max Heap: stores smaller half
 * - Min Heap: stores larger half
 * - Median is either top of one heap or average of both tops
 * 
 * Time: O(log n) for add, O(1) for find
 * Space: O(n)
 */

import java.util.PriorityQueue;
import java.util.Collections;

public class MedianFinder {
    
    private PriorityQueue<Integer> maxHeap; // Lower half (max at top)
    private PriorityQueue<Integer> minHeap; // Upper half (min at top)
    
    public MedianFinder() {
        maxHeap = new PriorityQueue<>(Collections.reverseOrder());
        minHeap = new PriorityQueue<>();
    }
    
    /**
     * Add number to the data structure
     * 
     * Strategy:
     * 1. Always keep maxHeap.size() >= minHeap.size()
     * 2. maxHeap.size() - minHeap.size() <= 1
     */
    public void addNum(int num) {
        // Add to max heap first
        maxHeap.offer(num);
        
        // Balance: ensure all in maxHeap <= all in minHeap
        minHeap.offer(maxHeap.poll());
        
        // Balance sizes: maxHeap should have equal or one more element
        if (maxHeap.size() < minHeap.size()) {
            maxHeap.offer(minHeap.poll());
        }
    }
    
    /**
     * Find median of all elements
     */
    public double findMedian() {
        if (maxHeap.size() > minHeap.size()) {
            return maxHeap.peek();
        }
        return (maxHeap.peek() + minHeap.peek()) / 2.0;
    }
    
    // FOLLOW-UP 1: Find median with removal support
    static class MedianFinderWithRemoval {
        private PriorityQueue<Integer> maxHeap;
        private PriorityQueue<Integer> minHeap;
        private int size;
        
        public MedianFinderWithRemoval() {
            maxHeap = new PriorityQueue<>(Collections.reverseOrder());
            minHeap = new PriorityQueue<>();
            size = 0;
        }
        
        public void addNum(int num) {
            maxHeap.offer(num);
            minHeap.offer(maxHeap.poll());
            
            if (maxHeap.size() < minHeap.size()) {
                maxHeap.offer(minHeap.poll());
            }
            size++;
        }
        
        public void removeNum(int num) {
            if (maxHeap.remove(num) || minHeap.remove(num)) {
                size--;
                rebalance();
            }
        }
        
        private void rebalance() {
            if (maxHeap.size() < minHeap.size()) {
                maxHeap.offer(minHeap.poll());
            } else if (maxHeap.size() > minHeap.size() + 1) {
                minHeap.offer(maxHeap.poll());
            }
        }
        
        public double findMedian() {
            if (size == 0) return 0.0;
            if (maxHeap.size() > minHeap.size()) {
                return maxHeap.peek();
            }
            return (maxHeap.peek() + minHeap.peek()) / 2.0;
        }
    }
    
    // FOLLOW-UP 2: Sliding Window Median
    public double[] medianSlidingWindow(int[] nums, int k) {
        MedianFinderWithRemoval mf = new MedianFinderWithRemoval();
        double[] result = new double[nums.length - k + 1];
        
        // Initialize window
        for (int i = 0; i < k; i++) {
            mf.addNum(nums[i]);
        }
        result[0] = mf.findMedian();
        
        // Slide window
        for (int i = k; i < nums.length; i++) {
            mf.removeNum(nums[i - k]);
            mf.addNum(nums[i]);
            result[i - k + 1] = mf.findMedian();
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        // Test Case 1: Basic Median Finder
        System.out.println("=== Basic Median Finder ===");
        MedianFinder mf = new MedianFinder();
        
        mf.addNum(1);
        mf.addNum(2);
        System.out.println("After [1, 2], median: " + mf.findMedian()); // 1.5
        
        mf.addNum(3);
        System.out.println("After [1, 2, 3], median: " + mf.findMedian()); // 2.0
        
        mf.addNum(4);
        System.out.println("After [1, 2, 3, 4], median: " + mf.findMedian()); // 2.5
        
        mf.addNum(5);
        System.out.println("After [1, 2, 3, 4, 5], median: " + mf.findMedian()); // 3.0
        
        // Test Case 2: Unordered stream
        System.out.println("\n=== Unordered Stream ===");
        MedianFinder mf2 = new MedianFinder();
        int[] stream = {5, 15, 1, 3, 8};
        for (int num : stream) {
            mf2.addNum(num);
            System.out.println("Added " + num + ", median: " + mf2.findMedian());
        }
        
        // Test Case 3: Sliding Window Median
        System.out.println("\n=== Sliding Window Median ===");
        MedianFinder mfTest = new MedianFinder();
        int[] nums = {1, 3, -1, -3, 5, 3, 6, 7};
        int k = 3;
        double[] medians = mfTest.medianSlidingWindow(nums, k);
        System.out.println("Array: [1,3,-1,-3,5,3,6,7], k=3");
        System.out.print("Medians: [");
        for (int i = 0; i < medians.length; i++) {
            System.out.print(medians[i]);
            if (i < medians.length - 1) System.out.print(", ");
        }
        System.out.println("]");
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Two heaps is the standard approach");
        System.out.println("2. MaxHeap stores smaller half, MinHeap stores larger half");
        System.out.println("3. Keep size difference <= 1");
        System.out.println("4. Follow-ups: removal, sliding window, k-th percentile");
        System.out.println("5. This pattern: IPO problem, Meeting Rooms III");
    }
}

