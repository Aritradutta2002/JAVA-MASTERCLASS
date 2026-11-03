package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 215: KTH LARGEST ELEMENT IN AN ARRAY
 * Difficulty: Medium
 * Pattern: Quick Select / Heap
 * 
 * PROBLEM STATEMENT:
 * Given an integer array nums and an integer k, return the kth largest element in the array.
 * Note that it is the kth largest element in sorted order, not the kth distinct element.
 * 
 * Can you solve it without sorting?
 * 
 * EXAMPLE 1:
 * Input: nums = [3,2,1,5,6,4], k = 2
 * Output: 5
 * Explanation: Sorted: [1,2,3,4,5,6], 2nd largest is 5
 * 
 * EXAMPLE 2:
 * Input: nums = [3,2,3,1,2,4,5,5,6], k = 4
 * Output: 4
 * Explanation: Sorted: [1,2,2,3,3,4,5,5,6], 4th largest is 4
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: SORTING (EASIEST)
 * ═══════════════════════════════════════════════════════════════════════════════
 * TIME: O(n log n), SPACE: O(1)
 */

import java.util.*;

class KthLargest_Sorting {
    public int findKthLargest(int[] nums, int k) {
        Arrays.sort(nums);
        return nums[nums.length - k];
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: MIN HEAP (OPTIMAL FOR SMALL K)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * - Maintain a min heap of size k
 * - The top of heap is always kth largest
 * - If heap size > k, remove smallest
 * 
 * TIME: O(n log k), SPACE: O(k)
 */

class KthLargest_Heap {
    public int findKthLargest(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        return minHeap.peek();
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 3: QUICK SELECT (OPTIMAL - AVERAGE O(n))
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * - Based on QuickSort's partition algorithm
 * - Don't need to fully sort, just partition around kth element
 * - Recursively partition only the side containing kth element
 * 
 * KEY INSIGHT:
 * After partition, all elements left of pivot are smaller,
 * all elements right of pivot are larger.
 * 
 * If pivot lands at position k, we found it!
 * If pivot > k, search left side
 * If pivot < k, search right side
 * 
 * DETAILED EXAMPLE:
 * nums = [3,2,1,5,6,4], k = 2 (find 2nd largest)
 * 
 * Convert: 2nd largest = (n-k)th smallest = 4th smallest (0-indexed: index 4)
 * 
 * Step 1: Partition around 4 (last element)
 *   [3,2,1,4,6,5] pivot=4 lands at index 3
 *   Left: [3,2,1] Right: [6,5]
 * 
 * Step 2: Target index=4, pivot=3, go right
 *   [6,5] partition around 5
 *   [5,6] pivot=5 lands at index 4
 * 
 * Found! nums[4] = 5
 * 
 * TIME: O(n) average, O(n²) worst, SPACE: O(1)
 */

public class KthLargestElement_Leetcode215 {
    
    public int findKthLargest(int[] nums, int k) {
        int n = nums.length;
        // Convert kth largest to index: (n - k)
        return quickSelect(nums, 0, n - 1, n - k);
    }
    
    private int quickSelect(int[] nums, int left, int right, int k) {
        if (left == right) {
            return nums[left];
        }
        
        // Partition and get pivot index
        int pivotIndex = partition(nums, left, right);
        
        if (pivotIndex == k) {
            return nums[pivotIndex];
        } else if (pivotIndex < k) {
            // Search right side
            return quickSelect(nums, pivotIndex + 1, right, k);
        } else {
            // Search left side
            return quickSelect(nums, left, pivotIndex - 1, k);
        }
    }
    
    private int partition(int[] nums, int left, int right) {
        int pivot = nums[right];
        int i = left;
        
        for (int j = left; j < right; j++) {
            if (nums[j] <= pivot) {
                swap(nums, i, j);
                i++;
            }
        }
        
        swap(nums, i, right);
        return i;
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    public static void main(String[] args) {
        KthLargestElement_Leetcode215 solution = new KthLargestElement_Leetcode215();
        
        int[] nums1 = {3,2,1,5,6,4};
        System.out.println("Input: " + Arrays.toString(nums1) + ", k=2");
        System.out.println("Output: " + solution.findKthLargest(nums1, 2));
        
        int[] nums2 = {3,2,3,1,2,4,5,5,6};
        System.out.println("\nInput: " + Arrays.toString(nums2) + ", k=4");
        System.out.println("Output: " + solution.findKthLargest(nums2, 4));
    }
}

/*
 * KEY TAKEAWAYS:
 * 1. Sorting: O(n log n) - Simple but not optimal
 * 2. Min Heap: O(n log k) - Good when k is small
 * 3. Quick Select: O(n) average - Best overall
 * 4. Convert kth largest to (n-k)th smallest for easier indexing
 */


