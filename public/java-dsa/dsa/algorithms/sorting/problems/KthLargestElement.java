package dsa.algorithms.sorting.problems;
/**
 * KTH LARGEST ELEMENT IN ARRAY
 * LeetCode #215 - Medium (But Critical!)
 * 
 * Companies: Facebook, Amazon, LinkedIn, Microsoft, Apple, Bloomberg
 * Frequency: VERY HIGH
 * 
 * Key Techniques:
 * 1. Quick Select - O(n) average, O(n²) worst
 * 2. Min Heap - O(n log k)
 * 3. Sort - O(n log n)
 */

import java.util.*;

public class KthLargestElement {
    
    // APPROACH 1: Quick Select (OPTIMAL - Must Know!)
    // Time: O(n) average, O(n²) worst
    // Space: O(1)
    public int findKthLargest(int[] nums, int k) {
        return quickSelect(nums, 0, nums.length - 1, nums.length - k);
    }
    
    private int quickSelect(int[] nums, int left, int right, int kSmallest) {
        if (left == right) return nums[left];
        
        // Randomized pivot for better average case
        Random random = new Random();
        int pivotIndex = left + random.nextInt(right - left + 1);
        
        pivotIndex = partition(nums, left, right, pivotIndex);
        
        if (kSmallest == pivotIndex) {
            return nums[kSmallest];
        } else if (kSmallest < pivotIndex) {
            return quickSelect(nums, left, pivotIndex - 1, kSmallest);
        }
        return quickSelect(nums, pivotIndex + 1, right, kSmallest);
    }
    
    private int partition(int[] nums, int left, int right, int pivotIndex) {
        int pivot = nums[pivotIndex];
        // Move pivot to end
        swap(nums, pivotIndex, right);
        int storeIndex = left;
        
        // Move all smaller elements to left
        for (int i = left; i < right; i++) {
            if (nums[i] < pivot) {
                swap(nums, storeIndex++, i);
            }
        }
        
        // Move pivot to final position
        swap(nums, right, storeIndex);
        return storeIndex;
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    // APPROACH 2: Min Heap of size K
    // Time: O(n log k)
    // Space: O(k)
    public int findKthLargest_Heap(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();
        
        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }
        
        return minHeap.peek();
    }
    
    // APPROACH 3: Max Heap (For comparison)
    public int findKthLargest_MaxHeap(int[] nums, int k) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        
        for (int num : nums) {
            maxHeap.offer(num);
        }
        
        for (int i = 0; i < k - 1; i++) {
            maxHeap.poll();
        }
        
        return maxHeap.peek();
    }
    
    /**
     * FOLLOW-UP: Find Kth Smallest
     */
    public int findKthSmallest(int[] nums, int k) {
        // Use Max Heap of size k
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);
        
        for (int num : nums) {
            maxHeap.offer(num);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        
        return maxHeap.peek();
    }
    
    /**
     * KTH LARGEST ELEMENT IN STREAM
     * LeetCode #703 - Easy
     * Design class that maintains kth largest
     */
    static class KthLargest {
        private PriorityQueue<Integer> minHeap;
        private int k;
        
        public KthLargest(int k, int[] nums) {
            this.k = k;
            minHeap = new PriorityQueue<>();
            
            for (int num : nums) {
                add(num);
            }
        }
        
        public int add(int val) {
            minHeap.offer(val);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
            return minHeap.peek();
        }
    }
    
    public static void main(String[] args) {
        KthLargestElement sol = new KthLargestElement();
        
        // Test 1: Basic case
        int[] nums1 = {3, 2, 1, 5, 6, 4};
        int k1 = 2;
        System.out.println("=== Test 1: Kth Largest ===");
        System.out.println("Array: " + Arrays.toString(nums1) + ", k=" + k1);
        System.out.println("Quick Select: " + sol.findKthLargest(nums1.clone(), k1));
        System.out.println("Min Heap: " + sol.findKthLargest_Heap(nums1.clone(), k1));
        System.out.println("Max Heap: " + sol.findKthLargest_MaxHeap(nums1.clone(), k1));
        
        // Test 2: With duplicates
        int[] nums2 = {3, 2, 3, 1, 2, 4, 5, 5, 6};
        int k2 = 4;
        System.out.println("\n=== Test 2: With Duplicates ===");
        System.out.println("Array: " + Arrays.toString(nums2) + ", k=" + k2);
        System.out.println("Result: " + sol.findKthLargest(nums2, k2));
        
        // Test 3: Kth Smallest
        System.out.println("\n=== Test 3: Kth Smallest ===");
        int[] nums3 = {7, 10, 4, 3, 20, 15};
        System.out.println("Array: " + Arrays.toString(nums3) + ", k=3");
        System.out.println("3rd Smallest: " + sol.findKthSmallest(nums3, 3));
        
        // Test 4: Stream
        System.out.println("\n=== Test 4: Kth in Stream ===");
        KthLargest stream = new KthLargest(3, new int[]{4, 5, 8, 2});
        System.out.println("Add 3: " + stream.add(3));
        System.out.println("Add 5: " + stream.add(5));
        System.out.println("Add 10: " + stream.add(10));
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Quick Select is OPTIMAL: O(n) average time");
        System.out.println("2. For Kth Largest: use Min Heap of size k");
        System.out.println("3. For Kth Smallest: use Max Heap of size k");
        System.out.println("4. Randomized pivot prevents O(n²) worst case");
        System.out.println("5. Follow-up: What if numbers keep coming? → Use heap");
        System.out.println("6. Facebook LOVES this problem!");
    }
}

