package JAVA_DSA.DYNAMIC_PROGRAMMING.java.subsequence_problems;

import java.util.*;

/**
 * LONGEST INCREASING SUBSEQUENCE (LIS) - Leetcode 300
 * Difficulty: Medium
 * Companies: Amazon, Microsoft, Google, Facebook, Apple, Bloomberg, Adobe
 * 
 * PROBLEM:
 * Given an integer array nums, return the length of the longest strictly increasing subsequence.
 * A subsequence is derived from the array by deleting some or no elements without changing 
 * the order of the remaining elements.
 * 
 * EXAMPLES:
 * Input: nums = [10,9,2,5,3,7,101,18]
 * Output: 4
 * Explanation: LIS is [2,3,7,101] or [2,3,7,18], length = 4
 * 
 * Input: nums = [0,1,0,3,2,3]
 * Output: 4
 * Explanation: LIS is [0,1,2,3], length = 4
 * 
 * PATTERN: LIS Pattern
 * This is THE classic LIS problem - foundation for many others!
 */

public class LongestIncreasingSubsequence_Leetcode300 {
    
    // ========== APPROACH 1: RECURSION (TLE) ==========
    // Time: O(2^n) - Each element has 2 choices
    // Space: O(n) - Recursion depth
    /**
     * BEGINNER'S EXPLANATION:
     * For each element, we have 2 choices:
     * 1. INCLUDE it (if it's greater than previous element)
     * 2. EXCLUDE it
     * 
     * Try all possibilities and return maximum length.
     */
    public int lengthOfLIS_Recursive(int[] nums) {
        return lisRec(nums, 0, Integer.MIN_VALUE);
    }
    
    private int lisRec(int[] nums, int index, int prevValue) {
        // Base case
        if (index == nums.length) return 0;
        
        // Choice 1: Exclude current element
        int exclude = lisRec(nums, index + 1, prevValue);
        
        // Choice 2: Include current element (if valid)
        int include = 0;
        if (nums[index] > prevValue) {
            include = 1 + lisRec(nums, index + 1, nums[index]);
        }
        
        return Math.max(include, exclude);
    }
    
    
    // ========== APPROACH 2: MEMOIZATION (Top-Down DP) ==========
    // Time: O(n^2)
    // Space: O(n^2)
    /**
     * BEGINNER'S EXPLANATION:
     * Cache results based on (current index, previous index).
     * Use index instead of value to avoid huge memo array.
     */
    public int lengthOfLIS_Memoization(int[] nums) {
        int n = nums.length;
        int[][] memo = new int[n][n + 1];
        for (int[] row : memo) Arrays.fill(row, -1);
        return lisMemo(nums, 0, -1, memo);
    }
    
    private int lisMemo(int[] nums, int curr, int prev, int[][] memo) {
        if (curr == nums.length) return 0;
        
        // Check memo (prev+1 because prev can be -1)
        if (memo[curr][prev + 1] != -1) return memo[curr][prev + 1];
        
        // Exclude
        int exclude = lisMemo(nums, curr + 1, prev, memo);
        
        // Include (if valid)
        int include = 0;
        if (prev == -1 || nums[curr] > nums[prev]) {
            include = 1 + lisMemo(nums, curr + 1, curr, memo);
        }
        
        memo[curr][prev + 1] = Math.max(include, exclude);
        return memo[curr][prev + 1];
    }
    
    
    // ========== APPROACH 3: TABULATION (Bottom-Up DP) ⭐ MOST INTUITIVE ==========
    // Time: O(n^2)
    // Space: O(n)
    /**
     * BEGINNER'S EXPLANATION:
     * dp[i] = length of longest increasing subsequence ending at index i
     * 
     * For each position i:
     * - Check all previous positions j < i
     * - If nums[j] < nums[i], we can extend the LIS ending at j
     * - dp[i] = max(dp[i], dp[j] + 1)
     * 
     * Visualization for [10,9,2,5,3,7,101,18]:
     * 
     * Index:    0   1  2  3  4  5   6   7
     * Value:   10   9  2  5  3  7  101  18
     * DP:       1   1  1  2  2  3   4   4
     *                     ↑      ↑   ↑
     *              2<5   2<7  2<101
     *              dp[2]+1=2  ...
     * 
     * Step by step for index 5 (value=7):
     * - Check j=0: 10 > 7, skip
     * - Check j=1: 9 > 7, skip
     * - Check j=2: 2 < 7, dp[5] = max(1, dp[2]+1) = 2
     * - Check j=3: 5 < 7, dp[5] = max(2, dp[3]+1) = 3 ✓
     * - Check j=4: 3 < 7, dp[5] = max(3, dp[4]+1) = 3
     */
    public int lengthOfLIS_Tabulation(int[] nums) {
        int n = nums.length;
        if (n == 0) return 0;
        
        // dp[i] = length of LIS ending at i
        int[] dp = new int[n];
        Arrays.fill(dp, 1);  // Each element is LIS of length 1
        
        int maxLength = 1;
        
        // For each position
        for (int i = 1; i < n; i++) {
            // Check all previous positions
            for (int j = 0; j < i; j++) {
                // If can extend LIS from j
                if (nums[j] < nums[i]) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
            maxLength = Math.max(maxLength, dp[i]);
        }
        
        return maxLength;
    }
    
    
    // ========== APPROACH 4: BINARY SEARCH ⭐ OPTIMAL ==========
    // Time: O(n log n) - Best possible!
    // Space: O(n)
    /**
     * BEGINNER'S EXPLANATION:
     * This is TRICKY but very important to know!
     * 
     * Maintain an array 'tails' where:
     * tails[i] = smallest tail element of all increasing subsequences of length i+1
     * 
     * For each number:
     * - If it's larger than all tails, append it (LIS got longer!)
     * - Otherwise, find the smallest tail >= current number and replace it
     *   (This maintains smaller tails for potential future extensions)
     * 
     * Visualization for [10,9,2,5,3,7,101,18]:
     * 
     * num=10:  tails=[10]                    length=1
     * num=9:   tails=[9]                     length=1  (replace 10 with 9)
     * num=2:   tails=[2]                     length=1  (replace 9 with 2)
     * num=5:   tails=[2,5]                   length=2  (append)
     * num=3:   tails=[2,3]                   length=2  (replace 5 with 3)
     * num=7:   tails=[2,3,7]                 length=3  (append)
     * num=101: tails=[2,3,7,101]             length=4  (append)
     * num=18:  tails=[2,3,7,18]              length=4  (replace 101 with 18)
     *                         ↑
     *                    Answer = 4
     * 
     * Note: tails array doesn't represent actual LIS, just helps find length!
     */
    public int lengthOfLIS(int[] nums) {
        List<Integer> tails = new ArrayList<>();
        
        for (int num : nums) {
            // Binary search for position to insert/replace
            int pos = binarySearch(tails, num);
            
            if (pos == tails.size()) {
                // Larger than all existing tails, append
                tails.add(num);
            } else {
                // Replace to maintain smallest possible tail
                tails.set(pos, num);
            }
        }
        
        return tails.size();
    }
    
    // Find leftmost position where num can be inserted
    private int binarySearch(List<Integer> tails, int num) {
        int left = 0, right = tails.size();
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            if (tails.get(mid) < num) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
    
    
    // ========== BONUS: PRINT ACTUAL LIS ==========
    /**
     * Reconstruct the actual LIS sequence (not just length)
     * Time: O(n^2), Space: O(n)
     */
    public List<Integer> printLIS(int[] nums) {
        int n = nums.length;
        if (n == 0) return new ArrayList<>();
        
        int[] dp = new int[n];
        int[] parent = new int[n];  // Track parent index for reconstruction
        Arrays.fill(dp, 1);
        Arrays.fill(parent, -1);
        
        int maxLength = 1;
        int maxIndex = 0;
        
        for (int i = 1; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i] && dp[j] + 1 > dp[i]) {
                    dp[i] = dp[j] + 1;
                    parent[i] = j;  // Track where we came from
                }
            }
            if (dp[i] > maxLength) {
                maxLength = dp[i];
                maxIndex = i;
            }
        }
        
        // Reconstruct LIS by following parent pointers
        List<Integer> lis = new ArrayList<>();
        int curr = maxIndex;
        while (curr != -1) {
            lis.add(nums[curr]);
            curr = parent[curr];
        }
        
        Collections.reverse(lis);
        return lis;
    }
    
    
    // ========== HELPER: VISUALIZE DP TABLE ==========
    public void visualizeDP(int[] nums) {
        int n = nums.length;
        int[] dp = new int[n];
        Arrays.fill(dp, 1);
        
        System.out.println("Index\tValue\tDP[i]\tExplanation");
        System.out.println("-----\t-----\t-----\t-----------");
        
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < i; j++) {
                if (nums[j] < nums[i]) {
                    dp[i] = Math.max(dp[i], dp[j] + 1);
                }
            }
            System.out.printf("%d\t%d\t%d\tLIS ending at %d\n", 
                            i, nums[i], dp[i], nums[i]);
        }
        
        int maxLen = Arrays.stream(dp).max().getAsInt();
        System.out.println("\nLongest Increasing Subsequence Length: " + maxLen);
        System.out.println("One possible LIS: " + printLIS(nums));
    }
    
    
    // ========== TEST CASES ==========
    public static void main(String[] args) {
        LongestIncreasingSubsequence_Leetcode300 solution = 
            new LongestIncreasingSubsequence_Leetcode300();
        
        // Test case 1
        int[] nums1 = {10, 9, 2, 5, 3, 7, 101, 18};
        System.out.println("Array: [10,9,2,5,3,7,101,18]");
        System.out.println("LIS Length: " + solution.lengthOfLIS(nums1)); // 4
        solution.visualizeDP(nums1);
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 2
        int[] nums2 = {0, 1, 0, 3, 2, 3};
        System.out.println("Array: [0,1,0,3,2,3]");
        System.out.println("LIS Length: " + solution.lengthOfLIS(nums2)); // 4
        System.out.println("LIS: " + solution.printLIS(nums2));
        System.out.println("\n" + "=".repeat(60) + "\n");
        
        // Test case 3
        int[] nums3 = {7, 7, 7, 7};
        System.out.println("Array: [7,7,7,7]");
        System.out.println("LIS Length: " + solution.lengthOfLIS(nums3)); // 1
    }
}

/**
 * ========== KEY TAKEAWAYS FOR INTERVIEWS ==========
 * 
 * 1. THIS IS A MUST-KNOW PROBLEM:
 *    - One of the most classic DP problems
 *    - Foundation for many variations
 *    - Tests both DP and binary search understanding
 * 
 * 2. TWO MAIN APPROACHES:
 *    - O(n^2) DP: More intuitive, easier to explain
 *    - O(n log n) Binary Search: Optimal, impressive!
 * 
 * 3. DP APPROACH (O(n^2)):
 *    - dp[i] = length of LIS ending at index i
 *    - For each i, check all j < i
 *    - If nums[j] < nums[i], can extend: dp[i] = max(dp[i], dp[j]+1)
 * 
 * 4. BINARY SEARCH APPROACH (O(n log n)):
 *    - Maintain smallest tail for each length
 *    - Use binary search to find position
 *    - Replace or append based on position
 *    - TRICKY but very powerful!
 * 
 * 5. WHY BINARY SEARCH WORKS:
 *    - We want smallest possible tail for each length
 *    - Smaller tails give more chances for future extensions
 *    - Binary search finds where to place each number
 *    - Final array length = LIS length (but array itself ≠ LIS!)
 * 
 * 6. FOLLOW-UP QUESTIONS:
 *    - Print actual LIS? → Track parent pointers
 *    - Count number of LIS? → Leetcode 673
 *    - Longest divisible subset? → Leetcode 368
 *    - Russian doll envelopes? → Leetcode 354
 *    - Non-decreasing (allow equal)? → Change < to <=
 * 
 * 7. RELATED PROBLEMS:
 *    - Leetcode 300: Longest Increasing Subsequence (this)
 *    - Leetcode 673: Number of Longest Increasing Subsequence
 *    - Leetcode 354: Russian Doll Envelopes
 *    - Leetcode 368: Largest Divisible Subset
 *    - Leetcode 646: Maximum Length of Pair Chain
 *    - Leetcode 1143: Longest Common Subsequence (different!)
 * 
 * 8. INTERVIEW STRATEGY:
 *    - Start with O(n^2) DP (easier to explain)
 *    - Draw small example and fill DP table
 *    - Mention O(n log n) exists
 *    - If time permits, explain binary search approach
 *    - Can implement O(n^2) first, then optimize
 * 
 * 9. COMMON MISTAKES:
 *    - Confusing subsequence with subarray (contiguous)
 *    - Not initializing all dp[i] to 1
 *    - In binary search: not maintaining "smallest tail"
 *    - Thinking tails array IS the LIS (it's not!)
 *    - Forgetting strictly increasing (not equal)
 * 
 * 10. VARIATIONS:
 *     - Longest Decreasing Subsequence: Reverse array or change comparison
 *     - Longest Bitonic Subsequence: LIS from left + LDS from right
 *     - Longest Alternating Subsequence: Track last direction
 *     - 2D LIS (Russian Dolls): Sort + LIS on second dimension
 * 
 * 11. COMPLEXITY COMPARISON:
 *     Recursion:     O(2^n) time, O(n) space
 *     Memoization:   O(n^2) time, O(n^2) space
 *     DP:            O(n^2) time, O(n) space
 *     Binary Search: O(n log n) time, O(n) space ⭐ OPTIMAL
 */

