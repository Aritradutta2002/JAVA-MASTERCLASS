package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 268: Missing Number
 * Difficulty: Easy
 * 
 * Given an array nums containing n distinct numbers in the range [0, n], 
 * return the only number in the range that is missing from the array.
 * 
 * Example 1:
 * Input: nums = [3,0,1]
 * Output: 2
 * Explanation: n = 3 since there are 3 numbers, so all numbers are in the range [0,3]. 
 * 2 is the missing number in the range since it does not appear in nums.
 * 
 * Example 2:
 * Input: nums = [0,1]
 * Output: 2
 * Explanation: n = 2 since there are 2 numbers, so all numbers are in the range [0,2]. 
 * 2 is the missing number in the range since it does not appear in nums.
 * 
 * Example 3:
 * Input: nums = [9,6,4,2,3,5,7,0,1]
 * Output: 8
 * 
 * Constraints:
 * - n == nums.length
 * - 1 <= n <= 10^4
 * - 0 <= nums[i] <= n
 * - All the numbers of nums are unique.
 */
public class MissingNumber_Leetcode268 {
    
    /**
     * Approach 1: XOR (Bit Manipulation) - Optimal
     * XOR all numbers from 0 to n with all numbers in array
     * Missing number will remain as a ^ a = 0
     * Time: O(n), Space: O(1)
     */
    public int missingNumber1(int[] nums) {
        int n = nums.length;
        int xor = 0;
        
        // XOR all numbers from 0 to n
        for (int i = 0; i <= n; i++) {
            xor ^= i;
        }
        
        // XOR with all numbers in array
        for (int num : nums) {
            xor ^= num;
        }
        
        return xor;
    }
    
    /**
     * Approach 2: XOR (Optimized single loop)
     * Combine both XOR operations in single loop
     * Time: O(n), Space: O(1)
     */
    public int missingNumber2(int[] nums) {
        int n = nums.length;
        int xor = n; // Start with n since loop goes from 0 to n-1
        
        for (int i = 0; i < n; i++) {
            xor ^= i ^ nums[i];
        }
        
        return xor;
    }
    
    /**
     * Approach 3: Mathematical Sum
     * Sum of 0 to n = n*(n+1)/2, subtract sum of array
     * Time: O(n), Space: O(1)
     */
    public int missingNumber3(int[] nums) {
        int n = nums.length;
        int expectedSum = n * (n + 1) / 2;
        
        int actualSum = 0;
        for (int num : nums) {
            actualSum += num;
        }
        
        return expectedSum - actualSum;
    }
    
    /**
     * Approach 4: Using HashSet
     * Store all numbers in set and check which is missing
     * Time: O(n), Space: O(n)
     */
    public int missingNumber4(int[] nums) {
        java.util.Set<Integer> numSet = new java.util.HashSet<>();
        
        for (int num : nums) {
            numSet.add(num);
        }
        
        for (int i = 0; i <= nums.length; i++) {
            if (!numSet.contains(i)) {
                return i;
            }
        }
        
        return -1; // Should never reach here
    }
    
    /**
     * Approach 5: Binary Search
     * Sort array and use binary search to find missing number
     * Time: O(n log n), Space: O(1)
     */
    public int missingNumber5(int[] nums) {
        java.util.Arrays.sort(nums);
        
        int left = 0, right = nums.length;
        
        while (left < right) {
            int mid = left + (right - left) / 2;
            
            if (nums[mid] == mid) {
                left = mid + 1;
            } else {
                right = mid;
            }
        }
        
        return left;
    }
    
    /**
     * Follow-up 1: Multiple Missing Numbers
     * Find all missing numbers in range [0, n+k] where k numbers are missing
     */
    public java.util.List<Integer> findMultipleMissingNumbers(int[] nums, int n) {
        boolean[] present = new boolean[n + 1];
        
        for (int num : nums) {
            if (num >= 0 && num <= n) {
                present[num] = true;
            }
        }
        
        java.util.List<Integer> missing = new java.util.ArrayList<>();
        for (int i = 0; i <= n; i++) {
            if (!present[i]) {
                missing.add(i);
            }
        }
        
        return missing;
    }
    
    /**
     * Follow-up 2: Missing Number in Arithmetic Progression
     * Find missing number in arithmetic sequence
     */
    public int missingNumberInAP(int[] nums) {
        int n = nums.length + 1; // Total numbers including missing one
        java.util.Arrays.sort(nums);
        
        // Calculate common difference
        int diff = (nums[nums.length - 1] - nums[0]) / n;
        
        for (int i = 1; i < nums.length; i++) {
            if (nums[i] - nums[i - 1] != diff) {
                return nums[i - 1] + diff;
            }
        }
        
        // Missing number is at the end
        return nums[nums.length - 1] + diff;
    }
    
    /**
     * Follow-up 3: Find Missing Number with Duplicates Allowed
     * Array can have duplicates, find missing number from [1, n+1]
     */
    public int findMissingWithDuplicates(int[] nums) {
        int n = nums.length;
        
        // Mark presence using array indices
        for (int i = 0; i < n; i++) {
            int absValue = Math.abs(nums[i]);
            if (absValue <= n && nums[absValue - 1] > 0) {
                nums[absValue - 1] = -nums[absValue - 1];
            }
        }
        
        // Find first positive number
        for (int i = 0; i < n; i++) {
            if (nums[i] > 0) {
                return i + 1;
            }
        }
        
        return n + 1; // All numbers 1 to n are present
    }
    
    /**
     * Utility method to explain XOR approach
     */
    public void explainXORApproach(int[] nums) {
        System.out.println("Explaining XOR approach:");
        System.out.print("Array: ");
        for (int num : nums) System.out.print(num + " ");
        System.out.println();
        
        int n = nums.length;
        System.out.print("Expected range [0, " + n + "]: ");
        for (int i = 0; i <= n; i++) System.out.print(i + " ");
        System.out.println();
        
        // Step by step XOR
        int xor = 0;
        System.out.println("\nXOR process:");
        
        // XOR expected numbers
        for (int i = 0; i <= n; i++) {
            xor ^= i;
            System.out.println("XOR with " + i + ": " + xor);
        }
        
        System.out.println("After XORing all expected numbers: " + xor);
        
        // XOR with array numbers
        for (int num : nums) {
            xor ^= num;
            System.out.println("XOR with " + num + ": " + xor);
        }
        
        System.out.println("Missing number: " + xor);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        MissingNumber_Leetcode268 solution = new MissingNumber_Leetcode268();
        
        // Test cases
        int[][] testCases = {
            {3, 0, 1},           // Expected: 2
            {0, 1},              // Expected: 2
            {9, 6, 4, 2, 3, 5, 7, 0, 1}, // Expected: 8
            {1},                 // Expected: 0
            {0}                  // Expected: 1
        };
        
        System.out.println("Testing Missing Number:");
        for (int[] nums : testCases) {
            System.out.print("Input: ");
            for (int num : nums) System.out.print(num + " ");
            System.out.println();
            
            int result1 = solution.missingNumber1(nums);
            int result2 = solution.missingNumber2(nums);
            int result3 = solution.missingNumber3(nums);
            int result4 = solution.missingNumber4(nums);
            int result5 = solution.missingNumber5(nums);
            
            System.out.println("XOR (v1): " + result1);
            System.out.println("XOR (v2): " + result2);
            System.out.println("Math Sum: " + result3);
            System.out.println("HashSet: " + result4);
            System.out.println("Binary Search: " + result5);
            
            boolean allMatch = result1 == result2 && result2 == result3 && 
                              result3 == result4 && result4 == result5;
            System.out.println("All approaches match: " + allMatch);
            System.out.println();
        }
        
        // Demonstrate XOR approach
        System.out.println("=== XOR Approach Explanation ===");
        solution.explainXORApproach(new int[]{3, 0, 1});
        
        // Test follow-ups
        System.out.println("\n=== Testing Follow-ups ===");
        
        // Multiple missing numbers
        System.out.println("Multiple missing numbers in [0,7]:");
        int[] incompleteArray = {0, 2, 4, 6};
        System.out.println("Missing: " + solution.findMultipleMissingNumbers(incompleteArray, 7));
        
        // Missing in AP
        System.out.println("\nMissing in arithmetic progression:");
        int[] ap = {2, 4, 8, 10, 12}; // Missing 6
        System.out.println("Missing: " + solution.missingNumberInAP(ap));
    }
}

