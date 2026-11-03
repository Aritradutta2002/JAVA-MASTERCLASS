package dsa.algorithms.sorting.problems;
/**
 * COUNT OF SMALLER NUMBERS AFTER SELF
 * LeetCode #315 - Hard
 * 
 * Companies: Google, Amazon, Microsoft, Facebook
 * Frequency: HIGH (Advanced sorting problem)
 * 
 * Problem: For each nums[i], count how many numbers after it are smaller
 * Example: [5,2,6,1] → [2,1,1,0]
 * 
 * Key Techniques:
 * 1. Merge Sort with Index Tracking - O(n log n) OPTIMAL!
 * 2. Binary Indexed Tree (BIT) - O(n log n)
 * 3. Segment Tree - O(n log n)
 */

import java.util.*;

public class CountSmallerAfterSelf {
    
    // APPROACH 1: Merge Sort with Index Tracking (OPTIMAL!)
    // Time: O(n log n), Space: O(n)
    private int[] counts;
    private int[] indexes;
    
    public List<Integer> countSmaller(int[] nums) {
        int n = nums.length;
        counts = new int[n];
        indexes = new int[n];
        
        for (int i = 0; i < n; i++) {
            indexes[i] = i;
        }
        
        mergeSort(nums, 0, n - 1);
        
        List<Integer> result = new ArrayList<>();
        for (int count : counts) {
            result.add(count);
        }
        
        return result;
    }
    
    private void mergeSort(int[] nums, int left, int right) {
        if (left >= right) return;
        
        int mid = left + (right - left) / 2;
        mergeSort(nums, left, mid);
        mergeSort(nums, mid + 1, right);
        merge(nums, left, mid, right);
    }
    
    private void merge(int[] nums, int left, int mid, int right) {
        int[] temp = new int[right - left + 1];
        int[] tempIndexes = new int[right - left + 1];
        
        int i = left;      // Left half pointer
        int j = mid + 1;   // Right half pointer
        int k = 0;         // Temp array pointer
        int rightCount = 0; // Count of smaller elements from right
        
        while (i <= mid && j <= right) {
            if (nums[indexes[j]] < nums[indexes[i]]) {
                // Element from right is smaller
                tempIndexes[k] = indexes[j];
                rightCount++;
                j++;
            } else {
                // Element from left is >= element from right
                // Add count of smaller elements seen so far
                tempIndexes[k] = indexes[i];
                counts[indexes[i]] += rightCount;
                i++;
            }
            k++;
        }
        
        // Process remaining left half
        while (i <= mid) {
            tempIndexes[k] = indexes[i];
            counts[indexes[i]] += rightCount;
            i++;
            k++;
        }
        
        // Process remaining right half
        while (j <= right) {
            tempIndexes[k++] = indexes[j++];
        }
        
        // Copy back
        for (i = left; i <= right; i++) {
            indexes[i] = tempIndexes[i - left];
        }
    }
    
    // APPROACH 2: Binary Indexed Tree (Fenwick Tree)
    // Time: O(n log n), Space: O(n)
    public List<Integer> countSmaller_BIT(int[] nums) {
        // Coordinate compression
        int[] sorted = nums.clone();
        Arrays.sort(sorted);
        
        Map<Integer, Integer> ranks = new HashMap<>();
        int rank = 0;
        for (int num : sorted) {
            if (!ranks.containsKey(num)) {
                ranks.put(num, ++rank);
            }
        }
        
        // BIT
        BIT bit = new BIT(ranks.size());
        List<Integer> result = new ArrayList<>();
        
        // Process from right to left
        for (int i = nums.length - 1; i >= 0; i--) {
            int r = ranks.get(nums[i]);
            result.add(bit.query(r - 1));
            bit.update(r, 1);
        }
        
        Collections.reverse(result);
        return result;
    }
    
    static class BIT {
        private int[] tree;
        private int n;
        
        public BIT(int n) {
            this.n = n;
            tree = new int[n + 1];
        }
        
        public void update(int i, int delta) {
            while (i <= n) {
                tree[i] += delta;
                i += i & (-i);
            }
        }
        
        public int query(int i) {
            int sum = 0;
            while (i > 0) {
                sum += tree[i];
                i -= i & (-i);
            }
            return sum;
        }
    }
    
    // APPROACH 3: BST (Alternative)
    // Time: O(n log n) average, O(n²) worst
    public List<Integer> countSmaller_BST(int[] nums) {
        List<Integer> result = new ArrayList<>();
        TreeNode root = null;
        
        for (int i = nums.length - 1; i >= 0; i--) {
            root = insert(root, nums[i], result, 0);
        }
        
        Collections.reverse(result);
        return result;
    }
    
    static class TreeNode {
        int val;
        int count = 1;      // Count of this value
        int leftCount = 0;  // Count of smaller values
        TreeNode left, right;
        
        TreeNode(int val) {
            this.val = val;
        }
    }
    
    private TreeNode insert(TreeNode root, int val, List<Integer> result, int preCount) {
        if (root == null) {
            result.add(preCount);
            return new TreeNode(val);
        }
        
        if (val == root.val) {
            root.count++;
            result.add(preCount + root.leftCount);
        } else if (val < root.val) {
            root.leftCount++;
            root.left = insert(root.left, val, result, preCount);
        } else {
            root.right = insert(root.right, val, result, preCount + root.leftCount + root.count);
        }
        
        return root;
    }
    
    /**
     * FOLLOW-UP: Count of Range Sum
     * LeetCode #327 - Hard
     */
    public int countRangeSum(int[] nums, int lower, int upper) {
        int n = nums.length;
        long[] sums = new long[n + 1];
        
        for (int i = 0; i < n; i++) {
            sums[i + 1] = sums[i] + nums[i];
        }
        
        return countWhileMergeSort(sums, 0, n + 1, lower, upper);
    }
    
    private int countWhileMergeSort(long[] sums, int start, int end, int lower, int upper) {
        if (end - start <= 1) return 0;
        
        int mid = start + (end - start) / 2;
        int count = countWhileMergeSort(sums, start, mid, lower, upper) 
                  + countWhileMergeSort(sums, mid, end, lower, upper);
        
        int j = mid, k = mid, t = mid;
        long[] cache = new long[end - start];
        int r = 0;
        
        for (int i = start; i < mid; i++) {
            while (k < end && sums[k] - sums[i] < lower) k++;
            while (j < end && sums[j] - sums[i] <= upper) j++;
            count += j - k;
            
            while (t < end && sums[t] < sums[i]) cache[r++] = sums[t++];
            cache[r++] = sums[i];
        }
        
        System.arraycopy(cache, 0, sums, start, r);
        return count;
    }
    
    public static void main(String[] args) {
        CountSmallerAfterSelf sol = new CountSmallerAfterSelf();
        
        // Test 1: Basic case
        int[] nums1 = {5, 2, 6, 1};
        System.out.println("=== Test 1: Basic ===");
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Merge Sort: " + sol.countSmaller(nums1));
        System.out.println("BIT: " + sol.countSmaller_BIT(nums1));
        System.out.println("BST: " + sol.countSmaller_BST(nums1));
        System.out.println("Expected: [2, 1, 1, 0]\n");
        
        // Test 2: Descending
        int[] nums2 = {5, 4, 3, 2, 1};
        System.out.println("=== Test 2: Descending ===");
        System.out.println("Input: " + Arrays.toString(nums2));
        System.out.println("Result: " + sol.countSmaller(nums2));
        System.out.println("Expected: [4, 3, 2, 1, 0]\n");
        
        // Test 3: Ascending
        int[] nums3 = {1, 2, 3, 4, 5};
        System.out.println("=== Test 3: Ascending ===");
        System.out.println("Input: " + Arrays.toString(nums3));
        System.out.println("Result: " + sol.countSmaller(nums3));
        System.out.println("Expected: [0, 0, 0, 0, 0]\n");
        
        // Test 4: With duplicates
        int[] nums4 = {2, 0, 1, 2, 0, 1};
        System.out.println("=== Test 4: With Duplicates ===");
        System.out.println("Input: " + Arrays.toString(nums4));
        System.out.println("Result: " + sol.countSmaller(nums4));
        System.out.println("Expected: [4, 0, 1, 2, 0, 0]\n");
        
        // Test 5: Count Range Sum
        System.out.println("=== Test 5: Count Range Sum ===");
        int[] nums5 = {-2, 5, -1};
        int lower = -2, upper = 2;
        System.out.println("Input: " + Arrays.toString(nums5));
        System.out.println("Range: [" + lower + ", " + upper + "]");
        System.out.println("Result: " + sol.countRangeSum(nums5, lower, upper));
        System.out.println("Expected: 3 (ranges: [0,0], [2,2], [0,2])\n");
        
        // Performance comparison
        System.out.println("=== PERFORMANCE TEST ===");
        int[] large = new int[10000];
        Random rand = new Random();
        for (int i = 0; i < large.length; i++) {
            large[i] = rand.nextInt(10000);
        }
        
        long start = System.nanoTime();
        sol.countSmaller(large.clone());
        long mergeTime = System.nanoTime() - start;
        
        start = System.nanoTime();
        sol.countSmaller_BIT(large.clone());
        long bitTime = System.nanoTime() - start;
        
        System.out.println("Array size: 10000");
        System.out.println("Merge Sort: " + mergeTime / 1_000_000.0 + " ms");
        System.out.println("BIT: " + bitTime / 1_000_000.0 + " ms\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. Merge Sort approach is MOST IMPORTANT");
        System.out.println("2. Key insight: Count inversions during merge");
        System.out.println("3. Track original indexes throughout sorting");
        System.out.println("4. BIT/Fenwick Tree needs coordinate compression");
        System.out.println("5. Google LOVES this problem - tests sorting + data structures");
        System.out.println("6. Similar: Reverse Pairs (#493), Count Range Sum (#327)");
    }
}

