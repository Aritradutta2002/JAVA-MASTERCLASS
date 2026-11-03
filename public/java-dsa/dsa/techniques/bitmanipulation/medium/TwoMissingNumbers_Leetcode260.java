package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 260: Single Number III
 * Difficulty: Medium
 * 
 * Given an integer array nums, in which exactly two elements appear only once 
 * and all the other elements appear exactly twice. Find the two elements that 
 * appear only once. You can return the answer in any order.
 * 
 * You must write an algorithm that runs in linear runtime complexity and uses 
 * only constant extra space.
 * 
 * Example 1:
 * Input: nums = [1,2,1,3,2,5]
 * Output: [3,5]
 * Explanation: [5, 3] is also a valid answer.
 * 
 * Example 2:
 * Input: nums = [-1,0]
 * Output: [-1,0]
 * 
 * Example 3:
 * Input: nums = [0,1]
 * Output: [1,0]
 * 
 * Constraints:
 * 2 <= nums.length <= 3 * 10^4
 * -2^31 <= nums[i] <= 2^31 - 1
 * Each integer in nums will appear twice, only two integers will appear once.
 */
public class TwoMissingNumbers_Leetcode260 {
    
    /**
     * Approach: XOR and Bit Partitioning (Optimal)
     * 
     * Algorithm:
     * 1. XOR all numbers to get a ^ b (where a and b are the two unique numbers)
     * 2. Find any set bit in a ^ b (this bit differs between a and b)
     * 3. Partition numbers into two groups based on this bit
     * 4. XOR each group separately to find a and b
     * 
     * Time: O(n), Space: O(1)
     */
    public int[] singleNumber(int[] nums) {
        // Step 1: XOR all numbers to get a ^ b
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        
        // Step 2: Find rightmost set bit in xor
        // This bit position where a and b differ
        int rightmostBit = xor & (-xor);
        
        // Step 3: Partition numbers and XOR each group
        int num1 = 0, num2 = 0;
        for (int num : nums) {
            if ((num & rightmostBit) != 0) {
                num1 ^= num; // Group 1: bit is set
            } else {
                num2 ^= num; // Group 2: bit is not set
            }
        }
        
        return new int[]{num1, num2};
    }
    
    /**
     * Alternative approach: Using any set bit (not necessarily rightmost)
     */
    public int[] singleNumberAnyBit(int[] nums) {
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        
        // Find any set bit (here we find the leftmost set bit)
        int mask = 1;
        while ((xor & mask) == 0) {
            mask <<= 1;
        }
        
        int num1 = 0, num2 = 0;
        for (int num : nums) {
            if ((num & mask) != 0) {
                num1 ^= num;
            } else {
                num2 ^= num;
            }
        }
        
        return new int[]{num1, num2};
    }
    
    /**
     * Helper method: Find all set bit positions in XOR
     */
    public int[] singleNumberWithAllBits(int[] nums) {
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        
        // Demo: Show all possible bit positions for partitioning
        System.out.println("XOR result: " + xor + " (binary: " + Integer.toBinaryString(xor) + ")");
        System.out.println("Possible partitioning bits:");
        
        for (int i = 0; i < 32; i++) {
            if ((xor & (1 << i)) != 0) {
                System.out.println("Bit position " + i + ": " + (1 << i));
            }
        }
        
        // Use rightmost bit for actual calculation
        int rightmostBit = xor & (-xor);
        int num1 = 0, num2 = 0;
        
        for (int num : nums) {
            if ((num & rightmostBit) != 0) {
                num1 ^= num;
            } else {
                num2 ^= num;
            }
        }
        
        return new int[]{num1, num2};
    }
    
    /**
     * Extension: Generalized approach for k missing numbers
     * Note: This is more complex and requires different techniques
     */
    public int[] findKMissingNumbers(int[] nums, int k) {
        if (k == 1) {
            // Single missing number
            int xor = 0;
            for (int num : nums) {
                xor ^= num;
            }
            return new int[]{xor};
        } else if (k == 2) {
            // Two missing numbers (our current problem)
            return singleNumber(nums);
        } else {
            // For k > 2, need different approach (like bit counting)
            throw new UnsupportedOperationException("k > 2 requires different algorithm");
        }
    }
    
    /**
     * Utility: Demonstrate the partitioning process
     */
    public void demonstratePartitioning(int[] nums) {
        System.out.println("Array: " + java.util.Arrays.toString(nums));
        
        // Step 1: Calculate XOR
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        System.out.println("XOR of all elements: " + xor + " (binary: " + Integer.toBinaryString(xor) + ")");
        
        // Step 2: Find rightmost set bit
        int rightmostBit = xor & (-xor);
        System.out.println("Rightmost set bit: " + rightmostBit + " (binary: " + Integer.toBinaryString(rightmostBit) + ")");
        
        // Step 3: Show partitioning
        System.out.println("\nPartitioning based on bit " + (Integer.numberOfTrailingZeros(rightmostBit)) + ":");
        
        java.util.List<Integer> group1 = new java.util.ArrayList<>();
        java.util.List<Integer> group2 = new java.util.ArrayList<>();
        
        for (int num : nums) {
            if ((num & rightmostBit) != 0) {
                group1.add(num);
            } else {
                group2.add(num);
            }
        }
        
        System.out.println("Group 1 (bit set): " + group1);
        System.out.println("Group 2 (bit not set): " + group2);
        
        // Calculate XOR for each group
        int xor1 = 0, xor2 = 0;
        for (int num : group1) xor1 ^= num;
        for (int num : group2) xor2 ^= num;
        
        System.out.println("XOR of Group 1: " + xor1);
        System.out.println("XOR of Group 2: " + xor2);
        System.out.println("Result: [" + xor1 + ", " + xor2 + "]");
    }
    
    /**
     * Test method with comprehensive examples
     */
    public static void main(String[] args) {
        TwoMissingNumbers_Leetcode260 solution = new TwoMissingNumbers_Leetcode260();
        
        // Test cases
        int[][] testCases = {
            {1, 2, 1, 3, 2, 5},
            {-1, 0},
            {0, 1},
            {1, 0, 1, 2},
            {2, 1, 2, 3, 4, 1},
            {1, 2, 3, 4, 5, 6, 1, 2, 3, 4}
        };
        
        System.out.println("Testing Two Missing Numbers:");
        for (int[] nums : testCases) {
            int[] result = solution.singleNumber(nums);
            System.out.printf("Input: %s → Output: [%d, %d]%n", 
                java.util.Arrays.toString(nums), result[0], result[1]);
        }
        
        System.out.println("\nDetailed Demonstration:");
        solution.demonstratePartitioning(new int[]{1, 2, 1, 3, 2, 5});
        
        // Performance test
        System.out.println("\nPerformance Test:");
        int[] largeArray = new int[10000];
        for (int i = 0; i < 4999; i++) {
            largeArray[2 * i] = i;
            largeArray[2 * i + 1] = i;
        }
        largeArray[9998] = 9999; // First unique
        largeArray[9999] = 10000; // Second unique
        
        long start = System.nanoTime();
        int[] result = solution.singleNumber(largeArray);
        long end = System.nanoTime();
        
        System.out.printf("Large array (10000 elements): [%d, %d] in %.2f ms%n", 
            result[0], result[1], (end - start) / 1_000_000.0);
        
        // Edge cases
        System.out.println("\nEdge Cases:");
        System.out.println("Negative numbers: " + java.util.Arrays.toString(
            solution.singleNumber(new int[]{-1, -2, -1, -3})));
        System.out.println("Mixed signs: " + java.util.Arrays.toString(
            solution.singleNumber(new int[]{1, -1, 2, -2, 3, 4})));
    }
}

