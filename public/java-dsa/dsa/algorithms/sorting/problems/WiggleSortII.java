package dsa.algorithms.sorting.problems;
/**
 * WIGGLE SORT II
 * LeetCode #324 - Medium
 * 
 * Companies: Google, Amazon, Facebook
 * Frequency: MEDIUM-HIGH
 * 
 * Problem: Reorder array such that nums[0] < nums[1] > nums[2] < nums[3]...
 * Example: [1,5,1,1,6,4] → [1,6,1,5,1,4]
 * 
 * Key Techniques:
 * 1. Virtual Indexing with Median - O(n) time, O(1) space OPTIMAL!
 * 2. Sort + Interleave - O(n log n) time, O(n) space
 * 3. Quick Select + 3-Way Partition - O(n) average
 */

import java.util.*;

public class WiggleSortII {
    
    // APPROACH 1: Virtual Indexing (OPTIMAL!)
    // Time: O(n), Space: O(1) - INSANE!
    public void wiggleSort(int[] nums) {
        int n = nums.length;
        
        // Find median using Quick Select
        int median = findKthLargest(nums, (n + 1) / 2);
        
        // 3-way partition with virtual indexing
        // Map: 0->1, 1->3, 2->5, ... (odds)
        //      n/2->0, n/2+1->2, ... (evens)
        int left = 0, i = 0, right = n - 1;
        
        while (i <= right) {
            if (nums[newIndex(i, n)] > median) {
                swap(nums, newIndex(left++, n), newIndex(i++, n));
            } else if (nums[newIndex(i, n)] < median) {
                swap(nums, newIndex(right--, n), newIndex(i, n));
            } else {
                i++;
            }
        }
    }
    
    // Virtual index mapping
    // Odd positions: (1 + 2*index) % (n|1)
    private int newIndex(int index, int n) {
        return (1 + 2 * index) % (n | 1);
    }
    
    private int findKthLargest(int[] nums, int k) {
        int left = 0, right = nums.length - 1;
        Random random = new Random();
        
        while (true) {
            int pivotIndex = left + random.nextInt(right - left + 1);
            pivotIndex = partition(nums, left, right, pivotIndex);
            
            if (pivotIndex == k - 1) {
                return nums[pivotIndex];
            } else if (pivotIndex < k - 1) {
                left = pivotIndex + 1;
            } else {
                right = pivotIndex - 1;
            }
        }
    }
    
    private int partition(int[] nums, int left, int right, int pivotIndex) {
        int pivot = nums[pivotIndex];
        swap(nums, pivotIndex, right);
        int storeIndex = left;
        
        for (int i = left; i < right; i++) {
            if (nums[i] > pivot) { // Note: descending for kth largest
                swap(nums, storeIndex++, i);
            }
        }
        
        swap(nums, right, storeIndex);
        return storeIndex;
    }
    
    // APPROACH 2: Sort + Interleave (Easier to understand)
    // Time: O(n log n), Space: O(n)
    public void wiggleSort_Simple(int[] nums) {
        int[] sorted = nums.clone();
        Arrays.sort(sorted);
        
        int n = nums.length;
        int mid = (n + 1) / 2;
        
        // Fill from middle: small numbers at even positions (from end)
        // Large numbers at odd positions (from end)
        int j = mid - 1;
        for (int i = 0; i < n; i += 2) {
            nums[i] = sorted[j--];
        }
        
        j = n - 1;
        for (int i = 1; i < n; i += 2) {
            nums[i] = sorted[j--];
        }
    }
    
    /**
     * FOLLOW-UP 1: Wiggle Sort (Easier version)
     * LeetCode #280 - nums[0] <= nums[1] >= nums[2] <= nums[3]...
     */
    public void wiggleSort_I(int[] nums) {
        // Time: O(n), Space: O(1)
        for (int i = 0; i < nums.length - 1; i++) {
            if ((i % 2 == 0) == (nums[i] > nums[i + 1])) {
                swap(nums, i, i + 1);
            }
        }
    }
    
    /**
     * FOLLOW-UP 2: Wiggle Subsequence
     * LeetCode #376 - Find longest wiggle subsequence
     */
    public int wiggleMaxLength(int[] nums) {
        if (nums.length < 2) return nums.length;
        
        int up = 1;   // Length ending with an upward wiggle
        int down = 1; // Length ending with a downward wiggle
        
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] > nums[i - 1]) {
                up = down + 1;
            } else if (nums[i] < nums[i - 1]) {
                down = up + 1;
            }
        }
        
        return Math.max(up, down);
    }
    
    /**
     * FOLLOW-UP 3: Pancake Sort
     * LeetCode #969 - Sort with flip operations
     */
    public List<Integer> pancakeSort(int[] arr) {
        List<Integer> result = new ArrayList<>();
        int n = arr.length;
        
        for (int size = n; size > 1; size--) {
            // Find max in arr[0..size-1]
            int maxIdx = 0;
            for (int i = 1; i < size; i++) {
                if (arr[i] > arr[maxIdx]) {
                    maxIdx = i;
                }
            }
            
            if (maxIdx != size - 1) {
                // Flip max to front
                if (maxIdx != 0) {
                    flip(arr, maxIdx);
                    result.add(maxIdx + 1);
                }
                // Flip to correct position
                flip(arr, size - 1);
                result.add(size);
            }
        }
        
        return result;
    }
    
    private void flip(int[] arr, int k) {
        int i = 0, j = k;
        while (i < j) {
            swap(arr, i++, j--);
        }
    }
    
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    public static void main(String[] args) {
        WiggleSortII sol = new WiggleSortII();
        
        // Test 1: Basic case
        int[] nums1 = {1, 5, 1, 1, 6, 4};
        System.out.println("=== Test 1: Wiggle Sort II ===");
        System.out.println("Before: " + Arrays.toString(nums1));
        sol.wiggleSort(nums1);
        System.out.println("After:  " + Arrays.toString(nums1));
        System.out.println("Valid: " + isWiggleSorted(nums1) + "\n");
        
        // Test 2: Simple approach
        int[] nums2 = {1, 3, 2, 2, 3, 1};
        System.out.println("=== Test 2: Simple Approach ===");
        System.out.println("Before: " + Arrays.toString(nums2));
        sol.wiggleSort_Simple(nums2);
        System.out.println("After:  " + Arrays.toString(nums2));
        System.out.println("Valid: " + isWiggleSorted(nums2) + "\n");
        
        // Test 3: Wiggle Sort I (easier)
        int[] nums3 = {3, 5, 2, 1, 6, 4};
        System.out.println("=== Test 3: Wiggle Sort I ===");
        System.out.println("Before: " + Arrays.toString(nums3));
        sol.wiggleSort_I(nums3);
        System.out.println("After:  " + Arrays.toString(nums3));
        System.out.println("Valid: " + isWiggleSortedI(nums3) + "\n");
        
        // Test 4: Wiggle Subsequence
        int[] nums4 = {1, 7, 4, 9, 2, 5};
        System.out.println("=== Test 4: Wiggle Subsequence ===");
        System.out.println("Array: " + Arrays.toString(nums4));
        System.out.println("Max Length: " + sol.wiggleMaxLength(nums4));
        System.out.println("Expected: 6 (entire array is wiggle)\n");
        
        // Test 5: Pancake Sort
        int[] nums5 = {3, 2, 4, 1};
        System.out.println("=== Test 5: Pancake Sort ===");
        System.out.println("Before: " + Arrays.toString(nums5));
        List<Integer> flips = sol.pancakeSort(nums5);
        System.out.println("After:  " + Arrays.toString(nums5));
        System.out.println("Flips:  " + flips + "\n");
        
        // Test edge cases
        System.out.println("=== Test 6: Edge Cases ===");
        int[] nums6 = {1, 1, 1, 1, 1};
        int[] copy6 = nums6.clone();
        sol.wiggleSort(copy6);
        System.out.println("All same: " + Arrays.toString(nums6) + " → " + Arrays.toString(copy6));
        
        int[] nums7 = {4, 5, 5, 6};
        int[] copy7 = nums7.clone();
        sol.wiggleSort(copy7);
        System.out.println("With dups: " + Arrays.toString(nums7) + " → " + Arrays.toString(copy7) + "\n");
        
        // Explain virtual indexing
        System.out.println("=== VIRTUAL INDEXING EXPLAINED ===");
        System.out.println("For n=6: Original: [0,1,2,3,4,5]");
        System.out.println("         Virtual:  [1,3,5,0,2,4]");
        System.out.println("Why? Want small nums at even, large at odd");
        System.out.println("Formula: (1 + 2*i) % (n|1)\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. O(n) solution uses virtual indexing - HARD!");
        System.out.println("2. O(n log n) solution is acceptable and easier");
        System.out.println("3. Key: Place smaller half at even indices (reversed)");
        System.out.println("4. Google asks: Can you do it in O(n) time, O(1) space?");
        System.out.println("5. Use Quick Select to find median first");
        System.out.println("6. Then 3-way partition around median");
    }
    
    private static boolean isWiggleSorted(int[] nums) {
        for (int i = 1; i < nums.length; i++) {
            if (i % 2 == 1) {
                if (nums[i] <= nums[i - 1]) return false;
            } else {
                if (nums[i] >= nums[i - 1]) return false;
            }
        }
        return true;
    }
    
    private static boolean isWiggleSortedI(int[] nums) {
        for (int i = 1; i < nums.length; i++) {
            if (i % 2 == 1) {
                if (nums[i] < nums[i - 1]) return false;
            } else {
                if (nums[i] > nums[i - 1]) return false;
            }
        }
        return true;
    }
}

