package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;

import java.util.*;
import java.util.stream.IntStream;

/**
 * LONGEST INCREASING SUBSEQUENCE - Advanced Implementation
 * Multiple approaches with detailed explanations
 * Pattern: LIS (Longest Increasing Subsequence)
 * 
 * PROBLEM:
 * Given an integer array nums, return the length of the longest strictly increasing subsequence.
 * 
 * EXAMPLES:
 * Input: nums = [10,9,2,5,3,7,101,18]
 * Output: 4
 * Explanation: The longest increasing subsequence is [2,3,7,18], therefore the length is 4.
 * 
 * KEY INSIGHTS:
 * 1. O(n²) DP solution is straightforward
 * 2. O(n log n) using binary search + patience sorting
 * 3. Can be extended to print actual LIS, count all LIS, etc.
 */
public class LongestIncreasingSubsequence_Advanced {
    
    /**
     * APPROACH 1: O(n²) DP (Standard)
     * Time: O(n²), Space: O(n)
     */
    public int lengthOfLIS_DP(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        Arrays.fill(dp, 1); // Each element forms LIS of length 1
        
        int maxLength = 1;
        
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i]) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
            maxLength = Math.max(maxLength, dp[i]);
        }
        
        return maxLength;
    }
    
    /**
     * APPROACH 2: O(n log n) Binary Search (Optimal)
     * Time: O(n log n), Space: O(n)
     * Uses "patience sorting" concept
     */
    public int lengthOfLIS_BinarySearch(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        
        for (int num : nums) {
            int pos = binarySearch(tails, num);
            
            if (pos == tails.size()) {
                tails.add(num);
            } else {
                tails.set(pos, num);
            }
        }
        
        return tails.size();
    }
    
    private int binarySearch(List<Integer> tails, int target) {
        int left = 0, right = tails.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (tails.get(mid) < target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
    
    /**
     * APPROACH 3: Print Actual LIS (not just length)
     * Time: O(n²), Space: O(n)
     */
    public List<Integer> findLIS(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        int[] parent = new int[n];
        Arrays.fill(dp, 1);
        Arrays.fill(parent, -1);
        
        int maxLength = 1;
        int maxIndex = 0;
        
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i] && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1;
                    parent[i] = j;
                }
            }
            
            if (dp[i] > maxLength) {
                maxLength = dp[i];
                maxIndex = i;
            }
        }
        
        // Reconstruct LIS
        List<Integer> lis = new ArrayList<>();
        int current = maxIndex;
        
        while (current != -1) {
            lis.add(nums[current]);
            current = parent[current];
        }
        
        Collections.reverse(lis);
        return lis;
    }
    
    /**
     * APPROACH 4: Count Number of LIS
     * Time: O(n²), Space: O(n)
     */
    public int findNumberOfLIS(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];      // Length of LIS ending at i
        int[] count = new int[n];   // Number of LIS ending at i
        
        Arrays.fill(dp, 1);
        Arrays.fill(count, 1);
        
        int maxLength = 1;
        
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i]) {
                    if (dp[j] + 1 > dp[i]) {
                        dp[i] = dp[j] + 1;
                        count[i] = count[j];
                    } else if (dp[j] + 1 == dp[i]) {
                        count[i] += count[j];
                    }
                }
            }
            maxLength = Math.max(maxLength, dp[i]);
        }
        
        int result = 0;
        for (int i = 0; i < n; i++) {
            if (dp[i] == maxLength) {
                result += count[i];
            }
        }
        
        return result;
    }
    
    /**
     * APPROACH 5: LIS with Binary Search that can reconstruct sequence
     * Time: O(n log n), Space: O(n)
     */
    public List<Integer> findLIS_BinarySearch(int[] nums) {
        int n = nums.length;
        int[] tails = new int[n];
        int[] parent = new int[n];
        int[] lisIndex = new int[n]; // Store actual indices in tails array
        
        Arrays.fill(parent, -1);
        int size = 0;
        
        for (int i = 0; i < n; i++) {
            int pos = Arrays.binarySearch(tails, 0, size, nums[i]);
            if (pos < 0) pos = -(pos + 1);
            
            tails[pos] = nums[i];
            lisIndex[pos] = i;
            
            if (pos > 0) {
                parent[i] = lisIndex[pos - 1];
            }
            
            if (pos == size) {
                size++;
            }
        }
        
        // Reconstruct LIS
        List<Integer> result = new ArrayList<>();
        int current = lisIndex[size - 1];
        
        while (current != -1) {
            result.add(nums[current]);
            current = parent[current];
        }
        
        Collections.reverse(result);
        return result;
    }
    
    /**
     * APPROACH 6: LIS Variants
     */
    
    // Longest Non-Decreasing Subsequence (allows duplicates)
    public int lengthOfLNDS(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        
        for (int num : nums) {
            int pos = upperBound(tails, num);
            
            if (pos == tails.size()) {
                tails.add(num);
            } else {
                tails.set(pos, num);
            }
        }
        
        return tails.size();
    }
    
    private int upperBound(List<Integer> tails, int target) {
        int left = 0, right = tails.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (tails.get(mid) <= target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
    
    // Longest Decreasing Subsequence
    public int lengthOfLDS(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        
        for (int num : nums) {
            int pos = binarySearchDesc(tails, num);
            
            if (pos == tails.size()) {
                tails.add(num);
            } else {
                tails.set(pos, num);
            }
        }
        
        return tails.size();
    }
    
    private int binarySearchDesc(List<Integer> tails, int target) {
        int left = 0, right = tails.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (tails.get(mid) > target) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
    
    /**
     * Visualization helper
     */
    public void visualizeLIS_DP(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        Arrays.fill(dp, 1);
        
        System.out.println("LIS DP Visualization:");
        System.out.println("Array: " + Arrays.toString(nums));
        System.out.println("Index: " + Arrays.toString(IntStream.range(0, n).toArray()));
        
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i]) {
                    if (dp[j] + 1 > dp[i]) {
                        dp[i] = dp[j] + 1;
                        System.out.printf("dp[%d] = dp[%d] + 1 = %d (nums[%d]=%d < nums[%d]=%d)\n", 
                            i, j, dp[i], j, nums[j], i, nums[i]);
                    }
                }
            }
        }
        
        System.out.println("Final DP: " + Arrays.toString(dp));
        System.out.println("Max LIS length: " + Arrays.stream(dp).max().orElse(0));
    }
    
    /**
     * Test cases and demonstration
     */
    public static void main(String[] args) {
        LongestIncreasingSubsequence_Advanced solution = new LongestIncreasingSubsequence_Advanced();
        
        System.out.println("=== Longest Increasing Subsequence - Advanced ===");
        
        // Test case 1
        int[] nums1 = {10, 9, 2, 5, 3, 7, 101, 18};
        System.out.println("Array: " + Arrays.toString(nums1));
        System.out.println("LIS Length (DP): " + solution.lengthOfLIS_DP(nums1));
        System.out.println("LIS Length (Binary Search): " + solution.lengthOfLIS_BinarySearch(nums1));
        System.out.println("Actual LIS (DP): " + solution.findLIS(nums1));
        System.out.println("Actual LIS (Binary Search): " + solution.findLIS_BinarySearch(nums1));
        System.out.println("Number of LIS: " + solution.findNumberOfLIS(nums1));
        System.out.println();
        
        solution.visualizeLIS_DP(nums1);
        System.out.println();
        
        // Test case 2 - LIS variants
        int[] nums2 = {1, 3, 2, 4, 3, 5, 7, 6};
        System.out.println("Array: " + Arrays.toString(nums2));
        System.out.println("LIS Length: " + solution.lengthOfLIS_BinarySearch(nums2));
        System.out.println("LNDS Length: " + solution.lengthOfLNDS(nums2));
        System.out.println("LDS Length: " + solution.lengthOfLDS(nums2));
        System.out.println();
        
        // Test case 3 - Edge cases
        System.out.println("=== Edge Cases ===");
        System.out.println("Single element [5]: " + solution.lengthOfLIS_DP(new int[]{5}));
        System.out.println("Decreasing [4,3,2,1]: " + solution.lengthOfLIS_DP(new int[]{4,3,2,1}));
        System.out.println("Increasing [1,2,3,4]: " + solution.lengthOfLIS_DP(new int[]{1,2,3,4}));
        System.out.println("All same [2,2,2,2]: " + solution.lengthOfLIS_DP(new int[]{2,2,2,2}));
        
        // Performance comparison
        System.out.println("\n=== Performance Comparison ===");
        int[] largArray = new int[1000];
        Random rand = new Random(42);
        for (int i = 0; i < largArray.length; i++) {
            largArray[i] = rand.nextInt(1000);
        }
        
        long start = System.nanoTime();
        int result1 = solution.lengthOfLIS_DP(largArray);
        long time1 = System.nanoTime() - start;
        
        start = System.nanoTime();
        int result2 = solution.lengthOfLIS_BinarySearch(largArray);
        long time2 = System.nanoTime() - start;
        
        System.out.println("Array size: " + largArray.length);
        System.out.println("DP O(n²): " + result1 + " (Time: " + time1/1000000.0 + " ms)");
        System.out.println("Binary Search O(n log n): " + result2 + " (Time: " + time2/1000000.0 + " ms)");
    }
}