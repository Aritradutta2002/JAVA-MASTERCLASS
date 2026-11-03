package dsa.algorithms.sorting.theory;
/**
 * COUNTING SORT
 * 
 * Time: O(n + k) where k = range of input
 * Space: O(k)
 * Stability: YES (if implemented correctly)
 * 
 * KEY CONCEPTS:
 * - Not comparison-based (breaks O(n log n) barrier!)
 * - Works ONLY for integers in known range
 * - Building block for Radix Sort
 * - CRITICAL for CP when range is small
 * 
 * When to use:
 * ✓ Range k is O(n) or smaller
 * ✓ Need stable sort
 * ✓ Need O(n) sorting
 */

import java.util.*;

public class CountingSort {
    
    /**
     * Basic Counting Sort (for non-negative integers)
     * Time: O(n + k), Space: O(k)
     */
    public void countingSort(int[] arr) {
        if (arr.length == 0) return;
        
        // Find range
        int max = Arrays.stream(arr).max().getAsInt();
        int min = Arrays.stream(arr).min().getAsInt();
        int range = max - min + 1;
        
        // Count occurrences
        int[] count = new int[range];
        for (int num : arr) {
            count[num - min]++;
        }
        
        // Write back to array
        int index = 0;
        for (int i = 0; i < range; i++) {
            while (count[i]-- > 0) {
                arr[index++] = i + min;
            }
        }
    }
    
    /**
     * STABLE Counting Sort (maintains relative order)
     * This version is used in Radix Sort
     */
    public void countingSortStable(int[] arr) {
        if (arr.length == 0) return;
        
        int max = Arrays.stream(arr).max().getAsInt();
        int min = Arrays.stream(arr).min().getAsInt();
        int range = max - min + 1;
        
        int[] count = new int[range];
        int[] output = new int[arr.length];
        
        // Count occurrences
        for (int num : arr) {
            count[num - min]++;
        }
        
        // Cumulative count (positions)
        for (int i = 1; i < range; i++) {
            count[i] += count[i - 1];
        }
        
        // Build output array (backwards for stability)
        for (int i = arr.length - 1; i >= 0; i--) {
            int val = arr[i];
            output[count[val - min] - 1] = val;
            count[val - min]--;
        }
        
        // Copy back
        System.arraycopy(output, 0, arr, 0, arr.length);
    }
    
    /**
     * Counting Sort for specific digit (used in Radix Sort)
     * exp = 1 for units, 10 for tens, etc.
     */
    public void countingSortByDigit(int[] arr, int exp) {
        int n = arr.length;
        int[] output = new int[n];
        int[] count = new int[10]; // 0-9 digits
        
        // Count occurrences of digits
        for (int num : arr) {
            int digit = (num / exp) % 10;
            count[digit]++;
        }
        
        // Cumulative count
        for (int i = 1; i < 10; i++) {
            count[i] += count[i - 1];
        }
        
        // Build output (backwards for stability)
        for (int i = n - 1; i >= 0; i--) {
            int digit = (arr[i] / exp) % 10;
            output[count[digit] - 1] = arr[i];
            count[digit]--;
        }
        
        System.arraycopy(output, 0, arr, 0, n);
    }
    
    /**
     * CP VARIANT: Count sort with custom range
     * When you know range is [low, high]
     */
    public void countingSortRange(int[] arr, int low, int high) {
        int range = high - low + 1;
        int[] count = new int[range];
        
        for (int num : arr) {
            count[num - low]++;
        }
        
        int index = 0;
        for (int i = 0; i < range; i++) {
            while (count[i]-- > 0) {
                arr[index++] = i + low;
            }
        }
    }
    
    /**
     * CP PROBLEM: Sort colors (0, 1, 2)
     * LeetCode #75 - Dutch National Flag
     */
    public void sortColors(int[] nums) {
        int[] count = new int[3];
        
        for (int num : nums) {
            count[num]++;
        }
        
        int index = 0;
        for (int color = 0; color < 3; color++) {
            while (count[color]-- > 0) {
                nums[index++] = color;
            }
        }
    }
    
    /**
     * CP PROBLEM: Counting sort for characters
     * Useful for string problems
     */
    public String sortString(String s) {
        int[] count = new int[26]; // a-z
        
        for (char c : s.toCharArray()) {
            count[c - 'a']++;
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 26; i++) {
            while (count[i]-- > 0) {
                sb.append((char) ('a' + i));
            }
        }
        
        return sb.toString();
    }
    
    /**
     * CP PROBLEM: Find kth smallest in O(n+k)
     */
    public int kthSmallest(int[] arr, int k) {
        int max = Arrays.stream(arr).max().getAsInt();
        int min = Arrays.stream(arr).min().getAsInt();
        int range = max - min + 1;
        
        int[] count = new int[range];
        for (int num : arr) {
            count[num - min]++;
        }
        
        int cumulative = 0;
        for (int i = 0; i < range; i++) {
            cumulative += count[i];
            if (cumulative >= k) {
                return i + min;
            }
        }
        
        return -1;
    }
    
    /**
     * FREQUENCY ARRAY TECHNIQUE (Very common in CP!)
     * Count frequencies for analysis
     */
    public int[] getFrequencyArray(int[] arr, int maxVal) {
        int[] freq = new int[maxVal + 1];
        for (int num : arr) {
            freq[num]++;
        }
        return freq;
    }
    
    public static void main(String[] args) {
        CountingSort cs = new CountingSort();
        
        // Test 1: Basic counting sort
        int[] arr1 = {4, 2, 2, 8, 3, 3, 1};
        System.out.println("=== Test 1: Basic Counting Sort ===");
        System.out.println("Original: " + Arrays.toString(arr1));
        cs.countingSort(arr1);
        System.out.println("Sorted: " + Arrays.toString(arr1));
        System.out.println();
        
        // Test 2: Stable sort
        int[] arr2 = {4, 2, 2, 8, 3, 3, 1};
        System.out.println("=== Test 2: Stable Sort ===");
        System.out.println("Original: " + Arrays.toString(arr2));
        cs.countingSortStable(arr2);
        System.out.println("Sorted: " + Arrays.toString(arr2));
        System.out.println("(Maintains relative order of equal elements)");
        System.out.println();
        
        // Test 3: Negative numbers
        int[] arr3 = {-5, -10, 0, -3, 8, 5, -1, 10};
        System.out.println("=== Test 3: With Negatives ===");
        System.out.println("Original: " + Arrays.toString(arr3));
        cs.countingSort(arr3);
        System.out.println("Sorted: " + Arrays.toString(arr3));
        System.out.println();
        
        // Test 4: Sort colors
        int[] colors = {2, 0, 2, 1, 1, 0};
        System.out.println("=== Test 4: Sort Colors ===");
        System.out.println("Original: " + Arrays.toString(colors));
        cs.sortColors(colors);
        System.out.println("Sorted: " + Arrays.toString(colors));
        System.out.println();
        
        // Test 5: String sorting
        String s = "dcba";
        System.out.println("=== Test 5: Sort String ===");
        System.out.println("Original: " + s);
        System.out.println("Sorted: " + cs.sortString(s));
        System.out.println();
        
        // Test 6: Kth smallest
        int[] arr6 = {7, 10, 4, 3, 20, 15};
        int k = 3;
        System.out.println("=== Test 6: Kth Smallest ===");
        System.out.println("Array: " + Arrays.toString(arr6));
        System.out.println(k + "rd smallest: " + cs.kthSmallest(arr6, k));
        System.out.println();
        
        // Explain algorithm
        System.out.println("=== HOW COUNTING SORT WORKS ===");
        System.out.println("For array [4, 2, 2, 8, 3, 3, 1]:");
        System.out.println();
        System.out.println("Step 1: Count occurrences");
        System.out.println("Index: 1  2  3  4  5  6  7  8");
        System.out.println("Count: 1  2  2  1  0  0  0  1");
        System.out.println();
        System.out.println("Step 2: Cumulative count (for stable sort)");
        System.out.println("Index: 1  2  3  4  5  6  7  8");
        System.out.println("Cum:   1  3  5  6  6  6  6  7");
        System.out.println();
        System.out.println("Step 3: Place elements at correct positions");
        System.out.println("Result: [1, 2, 2, 3, 3, 4, 8]");
        System.out.println();
        
        // Time complexity
        System.out.println("=== TIME COMPLEXITY ===");
        System.out.println("Step 1 (Count): O(n)");
        System.out.println("Step 2 (Cumulative): O(k)");
        System.out.println("Step 3 (Output): O(n)");
        System.out.println("Total: O(n + k)");
        System.out.println();
        System.out.println("When k = O(n): O(n) total!");
        System.out.println("Breaks O(n log n) comparison barrier");
        System.out.println();
        
        // When to use
        System.out.println("=== WHEN TO USE COUNTING SORT ===");
        System.out.println("✓ Small range: k = O(n)");
        System.out.println("✓ Integers only");
        System.out.println("✓ Need stability");
        System.out.println("✓ Part of Radix Sort");
        System.out.println();
        System.out.println("✗ Large range: k >> n (wastes space)");
        System.out.println("✗ Floating point numbers");
        System.out.println("✗ Unknown range");
        System.out.println();
        
        // CP applications
        System.out.println("=== CP APPLICATIONS ===");
        System.out.println("1. Small range sorting (ages, grades, etc.)");
        System.out.println("2. Character frequency in strings");
        System.out.println("3. Digit-by-digit in Radix Sort");
        System.out.println("4. Frequency array technique");
        System.out.println("5. Finding kth element in O(n+k)");
        System.out.println("6. Histogram-based problems");
        System.out.println();
        
        System.out.println("=== CP CONTEST TIPS ===");
        System.out.println("1. Always check constraints for range!");
        System.out.println("2. Use int[26] for lowercase letters");
        System.out.println("3. Use int[128] for ASCII");
        System.out.println("4. Frequency arrays solve many CP problems");
        System.out.println("5. Combine with prefix sums for queries");
    }
}

