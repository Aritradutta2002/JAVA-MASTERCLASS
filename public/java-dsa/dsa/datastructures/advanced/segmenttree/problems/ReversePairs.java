package dsa.datastructures.advanced.segmenttree.problems;

import java.util.*;

/**
 * Reverse Pairs (LeetCode 493) - HARD
 * FAANG Frequency: High (Google, Amazon)
 * 
 * Problem: Count pairs (i, j) where i < j and nums[i] > 2 * nums[j]
 * Time: O(n log n), Space: O(n)
 * 
 * Similar to: Count of Smaller Numbers After Self
 */
public class ReversePairs {
    
    // Approach 1: Merge Sort (Most Efficient)
    public int reversePairs(int[] nums) {
        return mergeSort(nums, 0, nums.length - 1);
    }
    
    private int mergeSort(int[] nums, int left, int right) {
        if (left >= right) return 0;
        
        int mid = left + (right - left) / 2;
        int count = mergeSort(nums, left, mid) + mergeSort(nums, mid + 1, right);
        
        // Count reverse pairs
        int j = mid + 1;
        for (int i = left; i <= mid; i++) {
            while (j <= right && nums[i] > 2L * nums[j]) {
                j++;
            }
            count += j - (mid + 1);
        }
        
        // Merge
        merge(nums, left, mid, right);
        
        return count;
    }
    
    private void merge(int[] nums, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int i = left, j = mid + 1, k = 0;
        
        while (i <= mid && j <= right) {
            if (nums[i] <= nums[j]) {
                temp[k++] = nums[i++];
            } else {
                temp[k++] = nums[j++];
            }
        }
        
        while (i <= mid) temp[k++] = nums[i++];
        while (j <= right) temp[k++] = nums[j++];
        
        System.arraycopy(temp, 0, nums, left, temp.length);
    }
    
    // Approach 2: Binary Indexed Tree with Coordinate Compression
    public int reversePairsBIT(int[] nums) {
        int n = nums.length;
        
        // Get all unique values for coordinate compression
        Set<Long> set = new HashSet<>();
        for (int num : nums) {
            set.add((long) num);
            set.add((long) num * 2);
        }
        
        List<Long> sorted = new ArrayList<>(set);
        Collections.sort(sorted);
        
        Map<Long, Integer> map = new HashMap<>();
        for (int i = 0; i < sorted.size(); i++) {
            map.put(sorted.get(i), i + 1);
        }
        
        BIT bit = new BIT(sorted.size());
        int count = 0;
        
        for (int i = n - 1; i >= 0; i--) {
            count += bit.query(map.get((long) nums[i]) - 1);
            bit.update(map.get((long) nums[i] * 2), 1);
        }
        
        return count;
    }
    
    class BIT {
        private int[] tree;
        private int n;
        
        BIT(int size) {
            n = size;
            tree = new int[n + 1];
        }
        
        void update(int index, int delta) {
            while (index <= n) {
                tree[index] += delta;
                index += index & (-index);
            }
        }
        
        int query(int index) {
            int sum = 0;
            while (index > 0) {
                sum += tree[index];
                index -= index & (-index);
            }
            return sum;
        }
    }
    
    public static void main(String[] args) {
        ReversePairs solution = new ReversePairs();
        
        // Test Case 1
        int[] nums1 = {1, 3, 2, 3, 1};
        System.out.println("Reverse Pairs: " + solution.reversePairs(nums1)); // 2
        
        // Test Case 2
        int[] nums2 = {2, 4, 3, 5, 1};
        System.out.println("Reverse Pairs: " + solution.reversePairs(nums2)); // 3
    }
}

