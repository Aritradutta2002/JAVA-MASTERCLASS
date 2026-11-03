package dsa.algorithms.sorting.problems;
/**
 * LARGEST NUMBER
 * LeetCode #179 - Medium
 * 
 * Companies: Amazon, Microsoft, Bloomberg, Adobe
 * Frequency: HIGH
 * 
 * Problem: Given array of non-negative integers, arrange them such that they form the largest number
 * Example: [3, 30, 34, 5, 9] → "9534330"
 * 
 * Key Insight: Custom comparator - compare concatenated strings!
 * For a, b: if ab > ba, then a should come before b
 */

import java.util.*;

public class LargestNumber {
    
    // OPTIMAL Solution: Custom Comparator
    // Time: O(n log n), Space: O(n)
    public String largestNumber(int[] nums) {
        // Convert to strings
        String[] strs = new String[nums.length];
        for (int i = 0; i < nums.length; i++) {
            strs[i] = String.valueOf(nums[i]);
        }
        
        // Custom comparator: for strings a and b
        // If ab > ba, then a comes first
        Arrays.sort(strs, (a, b) -> (b + a).compareTo(a + b));
        
        // Edge case: all zeros
        if (strs[0].equals("0")) {
            return "0";
        }
        
        // Build result
        StringBuilder sb = new StringBuilder();
        for (String s : strs) {
            sb.append(s);
        }
        
        return sb.toString();
    }
    
    // Alternative: Using Comparator class
    public String largestNumber_Verbose(int[] nums) {
        String[] strs = new String[nums.length];
        for (int i = 0; i < nums.length; i++) {
            strs[i] = String.valueOf(nums[i]);
        }
        
        Arrays.sort(strs, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                String order1 = a + b;
                String order2 = b + a;
                return order2.compareTo(order1); // DESC order
            }
        });
        
        if (strs[0].equals("0")) return "0";
        
        StringBuilder sb = new StringBuilder();
        for (String s : strs) {
            sb.append(s);
        }
        
        return sb.toString();
    }
    
    /**
     * FOLLOW-UP 1: Largest Number At Least Twice of Others
     * LeetCode #747
     */
    public int dominantIndex(int[] nums) {
        if (nums.length == 1) return 0;
        
        int max = Integer.MIN_VALUE;
        int secondMax = Integer.MIN_VALUE;
        int maxIndex = -1;
        
        for (int i = 0; i < nums.length; i++) {
            if (nums[i] > max) {
                secondMax = max;
                max = nums[i];
                maxIndex = i;
            } else if (nums[i] > secondMax) {
                secondMax = nums[i];
            }
        }
        
        return max >= 2 * secondMax ? maxIndex : -1;
    }
    
    /**
     * FOLLOW-UP 2: Create Maximum Number
     * LeetCode #321 - Hard
     * Pick k digits from two arrays to form largest number
     */
    public int[] maxNumber(int[] nums1, int[] nums2, int k) {
        int n = nums1.length, m = nums2.length;
        int[] ans = new int[k];
        
        // Try all possible splits: i from nums1, k-i from nums2
        for (int i = Math.max(0, k - m); i <= Math.min(k, n); i++) {
            int[] candidate = merge(
                maxArray(nums1, i),
                maxArray(nums2, k - i),
                k
            );
            if (greater(candidate, 0, ans, 0)) {
                ans = candidate;
            }
        }
        
        return ans;
    }
    
    private int[] maxArray(int[] nums, int k) {
        int n = nums.length;
        int[] ans = new int[k];
        int top = 0;
        int remain = n;
        
        for (int i = 0; i < n; i++) {
            while (top > 0 && ans[top - 1] < nums[i] && top - 1 + remain >= k) {
                top--;
            }
            if (top < k) {
                ans[top++] = nums[i];
            }
            remain--;
        }
        
        return ans;
    }
    
    private int[] merge(int[] nums1, int[] nums2, int k) {
        int[] ans = new int[k];
        int i = 0, j = 0, r = 0;
        
        while (r < k) {
            if (greater(nums1, i, nums2, j)) {
                ans[r++] = nums1[i++];
            } else {
                ans[r++] = nums2[j++];
            }
        }
        
        return ans;
    }
    
    private boolean greater(int[] nums1, int i, int[] nums2, int j) {
        while (i < nums1.length && j < nums2.length && nums1[i] == nums2[j]) {
            i++;
            j++;
        }
        return j >= nums2.length || (i < nums1.length && nums1[i] > nums2[j]);
    }
    
    /**
     * FOLLOW-UP 3: Minimum Number
     * Given same array, form SMALLEST number
     */
    public String smallestNumber(int[] nums) {
        String[] strs = new String[nums.length];
        for (int i = 0; i < nums.length; i++) {
            strs[i] = String.valueOf(nums[i]);
        }
        
        // Reverse the comparator for smallest
        Arrays.sort(strs, (a, b) -> (a + b).compareTo(b + a));
        
        if (strs[0].equals("0")) return "0";
        
        StringBuilder sb = new StringBuilder();
        for (String s : strs) {
            sb.append(s);
        }
        
        return sb.toString();
    }
    
    public static void main(String[] args) {
        LargestNumber sol = new LargestNumber();
        
        // Test 1: Basic case
        int[] nums1 = {10, 2};
        System.out.println("=== Test 1: Basic ===");
        System.out.println("Input: " + Arrays.toString(nums1));
        System.out.println("Largest: " + sol.largestNumber(nums1));
        System.out.println("Expected: 210\n");
        
        // Test 2: Complex case
        int[] nums2 = {3, 30, 34, 5, 9};
        System.out.println("=== Test 2: Complex ===");
        System.out.println("Input: " + Arrays.toString(nums2));
        System.out.println("Largest: " + sol.largestNumber(nums2));
        System.out.println("Expected: 9534330\n");
        
        // Test 3: All zeros
        int[] nums3 = {0, 0, 0};
        System.out.println("=== Test 3: All Zeros ===");
        System.out.println("Input: " + Arrays.toString(nums3));
        System.out.println("Largest: " + sol.largestNumber(nums3));
        System.out.println("Expected: 0\n");
        
        // Test 4: Tricky case - prefixes
        int[] nums4 = {121, 12};
        System.out.println("=== Test 4: Tricky Prefixes ===");
        System.out.println("Input: " + Arrays.toString(nums4));
        System.out.println("Largest: " + sol.largestNumber(nums4));
        System.out.println("Explanation: '12121' vs '12112' → '12121' is larger\n");
        
        // Test 5: Smallest number
        System.out.println("=== Test 5: Smallest Number ===");
        int[] nums5 = {3, 30, 34, 5, 9};
        System.out.println("Input: " + Arrays.toString(nums5));
        System.out.println("Smallest: " + sol.smallestNumber(nums5));
        System.out.println("Expected: 303345\n");
        
        // Test 6: Dominant Index
        System.out.println("=== Test 6: Dominant Index ===");
        int[] nums6 = {3, 6, 1, 0};
        System.out.println("Input: " + Arrays.toString(nums6));
        System.out.println("Dominant Index: " + sol.dominantIndex(nums6));
        System.out.println("Explanation: 6 >= 2*3? No → -1\n");
        
        int[] nums7 = {1, 2, 16, 7};
        System.out.println("Input: " + Arrays.toString(nums7));
        System.out.println("Dominant Index: " + sol.dominantIndex(nums7));
        System.out.println("Explanation: 16 >= 2*7? Yes → index 2\n");
        
        // Why this works
        System.out.println("=== WHY THIS WORKS ===");
        System.out.println("For numbers a and b:");
        System.out.println("  If ab > ba, then a should come before b");
        System.out.println("Example: 3 vs 30");
        System.out.println("  '3' + '30' = '330'");
        System.out.println("  '30' + '3' = '303'");
        System.out.println("  '330' > '303', so 3 comes before 30\n");
        
        // Interview Tips
        System.out.println("=== INTERVIEW TIPS ===");
        System.out.println("1. Key insight: Custom comparator with concatenation");
        System.out.println("2. Edge case: All zeros → return '0', not '000'");
        System.out.println("3. Time: O(n log n) - optimal for comparison-based sort");
        System.out.println("4. Amazon asks: Why does this comparator work?");
        System.out.println("5. Prove transitivity: if ab > ba and bc > cb, then ac > ca");
    }
}

