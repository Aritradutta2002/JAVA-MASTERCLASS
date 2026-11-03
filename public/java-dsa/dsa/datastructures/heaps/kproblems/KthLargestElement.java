package dsa.datastructures.heaps.kproblems;

import java.util.PriorityQueue;
import java.util.Random;

/**
 * KTH LARGEST ELEMENT IN AN ARRAY
 *
 * LeetCode #215 - Medium
 * Companies: Facebook, Amazon, Microsoft, Google, Apple
 *
 * Problem: Find the kth largest element in an unsorted array.
 *
 * Solutions:
 * 1. Min Heap of size K - O(n log k) time, O(k) space
 * 2. Quick Select - O(n) average, O(n²) worst
 * 3. Max Heap - O(n + k log n) time, O(n) space
 */
public class KthLargestElement {

    // APPROACH 1: Min Heap (Most interview-friendly)
    // Keep heap size = k, top is kth largest
    public int findKthLargest_MinHeap(int[] nums, int k) {
        PriorityQueue<Integer> minHeap = new PriorityQueue<>();

        for (int num : nums) {
            minHeap.offer(num);
            if (minHeap.size() > k) {
                minHeap.poll();
            }
        }

        if (minHeap.isEmpty()) {
            throw new IllegalStateException("Cannot find kth largest element in an empty array.");
        }
        return minHeap.peek();
    }

    // APPROACH 2: Quick Select (Optimal average case)
    public int findKthLargest_QuickSelect(int[] nums, int k) {
        if (nums == null || nums.length == 0 || k < 1 || k > nums.length) {
            throw new IllegalArgumentException("Invalid input");
        }
        return quickSelect(nums, 0, nums.length - 1, nums.length - k);
    }

    private int quickSelect(int[] nums, int left, int right, int kSmallest) {
        if (left == right) return nums[left];

        Random random = new Random();
        int pivotIndex = left + random.nextInt(right - left + 1);

        pivotIndex = partition(nums, left, right, pivotIndex);

        if (kSmallest == pivotIndex) {
            return nums[kSmallest];
        } else if (kSmallest < pivotIndex) {
            return quickSelect(nums, left, pivotIndex - 1, kSmallest);
        } else {
            return quickSelect(nums, pivotIndex + 1, right, kSmallest);
        }
    }

    private int partition(int[] nums, int left, int right, int pivotIndex) {
        int pivotValue = nums[pivotIndex];
        // Move pivot to end
        swap(nums, pivotIndex, right);
        int storeIndex = left;

        // Move all smaller elements to the left
        for (int i = left; i < right; i++) {
            if (nums[i] < pivotValue) {
                swap(nums, storeIndex, i);
                storeIndex++;
            }
        }

        // Move pivot to its final position
        swap(nums, right, storeIndex);
        return storeIndex;
    }

    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }

    // APPROACH 3: Max Heap
    public int findKthLargest_MaxHeap(int[] nums, int k) {
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);

        for (int num : nums) {
            maxHeap.offer(num);
        }

        if (k > nums.length) {
            throw new IllegalArgumentException("k is larger than the number of elements");
        }

        for (int i = 0; i < k - 1; i++) {
            maxHeap.poll();
        }

        if (maxHeap.isEmpty()) {
            throw new IllegalStateException("Cannot find kth largest element.");
        }
        return maxHeap.peek();
    }

    // KTH SMALLEST ELEMENT
    public int findKthSmallest(int[] nums, int k) {
        // Use max heap of size k
        PriorityQueue<Integer> maxHeap = new PriorityQueue<>((a, b) -> b - a);

        for (int num : nums) {
            maxHeap.offer(num);
            if (maxHeap.size() > k) {
                maxHeap.poll();
            }
        }
        if (maxHeap.isEmpty()) {
            throw new IllegalStateException("Cannot find kth smallest element.");
        }
        return maxHeap.peek();
    }

    public static void main(String[] args) {
        KthLargestElement solution = new KthLargestElement();

        // Test Case 1
        int[] nums1 = {3, 2, 1, 5, 6, 4};
        int k1 = 2;
        System.out.println("Array: [3,2,1,5,6,4], k=2");
        System.out.println("Kth Largest (MinHeap): " + solution.findKthLargest_MinHeap(nums1.clone(), k1));
        System.out.println("Kth Largest (QuickSelect): " + solution.findKthLargest_QuickSelect(nums1.clone(), k1));
        System.out.println("Kth Largest (MaxHeap): " + solution.findKthLargest_MaxHeap(nums1.clone(), k1));

        // Test Case 2
        int[] nums2 = {3, 2, 3, 1, 2, 4, 5, 5, 6};
        int k2 = 4;
        System.out.println("\nArray: [3,2,3,1,2,4,5,5,6], k=4");
        System.out.println("Kth Largest: " + solution.findKthLargest_MinHeap(nums2.clone(), k2));
        System.out.println("Kth Smallest: " + solution.findKthSmallest(nums2.clone(), k2));

        // Interview Tip
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Min Heap approach is most interview-friendly");
        System.out.println("2. Always discuss trade-offs: Time vs Space");
        System.out.println("3. Quick Select has better average case but Min Heap is safer");
        System.out.println("4. For Kth Largest: use Min Heap of size K");
        System.out.println("5. For Kth Smallest: use Max Heap of size K");
    }
}

