package dsa.datastructures.heaps.basics;

import java.util.*;

/**
 * Sliding Window Median (LeetCode 480) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Find median in each sliding window
 * Time: O(n log k), Space: O(k)
 * 
 * Key: Two heaps + lazy deletion
 */
public class SlidingWindowMedian {
    
    PriorityQueue<Integer> maxHeap; // Lower half
    PriorityQueue<Integer> minHeap; // Upper half
    Map<Integer, Integer> delayed; // Lazy deletion map
    int maxHeapSize, minHeapSize;
    
    public double[] medianSlidingWindow(int[] nums, int k) {
        maxHeap = new PriorityQueue<>((a, b) -> Integer.compare(b, a));
        minHeap = new PriorityQueue<>();
        delayed = new HashMap<>();
        
        double[] result = new double[nums.length - k + 1];
        
        // Initialize first window
        for (int i = 0; i < k; i++) {
            maxHeap.offer(nums[i]);
        }
        
        for (int i = 0; i < k / 2; i++) {
            minHeap.offer(maxHeap.poll());
        }
        
        maxHeapSize = maxHeap.size();
        minHeapSize = minHeap.size();
        
        result[0] = getMedian();
        
        // Slide window
        for (int i = k; i < nums.length; i++) {
            int outNum = nums[i - k];
            int inNum = nums[i];
            int balance = 0;
            
            // Remove outgoing element (lazy deletion)
            if (outNum <= maxHeap.peek()) {
                balance--;
                if (outNum == maxHeap.peek()) {
                    maxHeap.poll();
                    maxHeapSize--;
                } else {
                    delayed.put(outNum, delayed.getOrDefault(outNum, 0) + 1);
                    maxHeapSize--;
                }
            } else {
                balance++;
                if (outNum == minHeap.peek()) {
                    minHeap.poll();
                    minHeapSize--;
                } else {
                    delayed.put(outNum, delayed.getOrDefault(outNum, 0) + 1);
                    minHeapSize--;
                }
            }
            
            // Add incoming element
            if (maxHeapSize == 0 || inNum <= maxHeap.peek()) {
                maxHeap.offer(inNum);
                maxHeapSize++;
                balance++;
            } else {
                minHeap.offer(inNum);
                minHeapSize++;
                balance--;
            }
            
            // Rebalance heaps
            if (balance < 0) {
                maxHeap.offer(minHeap.poll());
                maxHeapSize++;
                minHeapSize--;
            } else if (balance > 0) {
                minHeap.offer(maxHeap.poll());
                minHeapSize--;
                maxHeapSize++;
            }
            
            // Clean up tops
            while (!maxHeap.isEmpty() && delayed.containsKey(maxHeap.peek())) {
                int num = maxHeap.poll();
                delayed.put(num, delayed.get(num) - 1);
                if (delayed.get(num) == 0) {
                    delayed.remove(num);
                }
            }
            
            while (!minHeap.isEmpty() && delayed.containsKey(minHeap.peek())) {
                int num = minHeap.poll();
                delayed.put(num, delayed.get(num) - 1);
                if (delayed.get(num) == 0) {
                    delayed.remove(num);
                }
            }
            
            result[i - k + 1] = getMedian();
        }
        
        return result;
    }
    
    private double getMedian() {
        if (maxHeapSize == minHeapSize) {
            return ((long) maxHeap.peek() + (long) minHeap.peek()) / 2.0;
        } else {
            return maxHeap.peek();
        }
    }
    
    public static void main(String[] args) {
        SlidingWindowMedian solution = new SlidingWindowMedian();
        
        // Test Case 1
        int[] nums1 = {1, 3, -1, -3, 5, 3, 6, 7};
        System.out.println(Arrays.toString(solution.medianSlidingWindow(nums1, 3)));
        // [1.0, -1.0, -1.0, 3.0, 5.0, 6.0]
    }
}

