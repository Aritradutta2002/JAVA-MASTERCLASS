package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 137: Single Number II
 * Difficulty: Medium
 * 
 * Given an integer array nums where every element appears three times except for one, 
 * which appears exactly once. Find the single element and return it.
 * You must implement a solution with O(n) time complexity and O(1) space complexity.
 * 
 * Example 1:
 * Input: nums = [2,2,3,2]
 * Output: 3
 * 
 * Example 2:
 * Input: nums = [0,1,0,1,0,1,99]
 * Output: 99
 * 
 * Constraints:
 * - 1 <= nums.length <= 3 * 10^4
 * - -2^31 <= nums[i] <= 2^31 - 1
 * - Each element appears exactly three times except for one element which appears once.
 */
public class SingleNumberII_Leetcode137 {
    
    /**
     * Approach 1: Bit Counting
     * Count bits at each position, single number contributes 1, others contribute 3
     * Result bit is 1 if count % 3 == 1
     * Time: O(32n) = O(n), Space: O(1)
     */
    public int singleNumber1(int[] nums) {
        int result = 0;
        
        // Check each bit position
        for (int i = 0; i < 32; i++) {
            int count = 0;
            
            // Count how many numbers have bit i set
            for (int num : nums) {
                if ((num & (1 << i)) != 0) {
                    count++;
                }
            }
            
            // If count is not divisible by 3, the single number has this bit set
            if (count % 3 != 0) {
                result |= (1 << i);
            }
        }
        
        return result;
    }
    
    /**
     * Approach 2: Digital Logic (State Machine)
     * Use two variables to track bit states: 00 -> 01 -> 10 -> 00
     * ones: appears once, twos: appears twice
     * Time: O(n), Space: O(1)
     */
    public int singleNumber2(int[] nums) {
        int ones = 0, twos = 0;
        
        for (int num : nums) {
            // Update ones and twos based on current num
            ones = (ones ^ num) & ~twos;
            twos = (twos ^ num) & ~ones;
        }
        
        return ones;
    }
    
    /**
     * Approach 3: State Machine with Three States
     * Use two bits to represent three states: 00, 01, 10
     * Time: O(n), Space: O(1)
     */
    public int singleNumber3(int[] nums) {
        int first = 0, second = 0;
        
        for (int num : nums) {
            // Calculate new states
            int newFirst = (~first & second & num) | (first & ~second & ~num);
            int newSecond = (~first & ~second & num) | (~first & second & ~num);
            
            first = newFirst;
            second = newSecond;
        }
        
        return second;  // Single number will be in state 01
    }
    
    /**
     * Approach 4: Mathematical Approach
     * 3 * (sum of unique elements) - (sum of all elements) = 2 * (single element)
     * Time: O(n), Space: O(n) for set
     */
    public int singleNumber4(int[] nums) {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        long sumAll = 0, sumUnique = 0;
        
        for (int num : nums) {
            sumAll += num;
            if (!set.contains(num)) {
                set.add(num);
                sumUnique += num;
            }
        }
        
        return (int) ((3 * sumUnique - sumAll) / 2);
    }
    
    /**
     * Generalized Solution: Single Number appearing k times, others appear m times
     * where k < m and gcd(k, m) = 1
     */
    public int singleNumberGeneral(int[] nums, int k, int m) {
        int[] counter = new int[32];
        
        // Count bits at each position
        for (int num : nums) {
            for (int i = 0; i < 32; i++) {
                if ((num & (1 << i)) != 0) {
                    counter[i]++;
                }
            }
        }
        
        int result = 0;
        for (int i = 0; i < 32; i++) {
            if (counter[i] % m == k) {
                result |= (1 << i);
            }
        }
        
        return result;
    }
    
    /**
     * Explanation of Digital Logic Approach:
     * 
     * We need to track how many times each bit appears:
     * - 0 times: represented by ones=0, twos=0
     * - 1 time:  represented by ones=1, twos=0  
     * - 2 times: represented by ones=0, twos=1
     * - 3 times: reset to ones=0, twos=0
     * 
     * Truth table for state transitions:
     * current_ones | current_twos | input | new_ones | new_twos
     * -------------|--------------|-------|----------|----------
     *      0       |      0       |   0   |    0     |    0
     *      0       |      0       |   1   |    1     |    0
     *      1       |      0       |   0   |    1     |    0
     *      1       |      0       |   1   |    0     |    1
     *      0       |      1       |   0   |    0     |    1
     *      0       |      1       |   1   |    0     |    0
     * 
     * From this table:
     * new_ones = (ones XOR input) AND (NOT twos)
     * new_twos = (twos XOR input) AND (NOT new_ones)
     */
    
    /**
     * Test method with detailed explanation
     */
    public static void main(String[] args) {
        SingleNumberII_Leetcode137 solution = new SingleNumberII_Leetcode137();
        
        // Test cases
        int[][] testCases = {
            {2, 2, 3, 2},
            {0, 1, 0, 1, 0, 1, 99},
            {-2, -2, 1, 1, 4, 1, 4, 4, -4, -2},
            {1}
        };
        
        System.out.println("Testing Single Number II (appears once, others thrice):");
        for (int[] nums : testCases) {
            System.out.print("Input: ");
            for (int num : nums) System.out.print(num + " ");
            
            int result1 = solution.singleNumber1(nums);
            int result2 = solution.singleNumber2(nums);
            int result3 = solution.singleNumber3(nums);
            
            System.out.println();
            System.out.println("Bit Counting: " + result1);
            System.out.println("Digital Logic: " + result2);
            System.out.println("State Machine: " + result3);
            System.out.println("All match: " + (result1 == result2 && result2 == result3));
            System.out.println();
        }
        
        // Test generalized solution
        System.out.println("Testing Generalized Solution:");
        int[] generalTest = {1, 1, 1, 1, 2, 2, 2, 2, 3}; // 3 appears 1 time, others 4 times
        int result = solution.singleNumberGeneral(generalTest, 1, 4);
        System.out.println("Single number (k=1, m=4): " + result);
        
        // Demonstrate state transitions
        System.out.println("\nState Transition Demonstration:");
        int[] demo = {5, 5, 5};
        solution.demonstrateStateTransitions(demo);
    }
    
    /**
     * Helper method to demonstrate state transitions
     */
    private void demonstrateStateTransitions(int[] nums) {
        int ones = 0, twos = 0;
        
        System.out.println("Initial: ones=" + ones + ", twos=" + twos);
        
        for (int i = 0; i < nums.length; i++) {
            int num = nums[i];
            System.out.println("Processing " + num + ":");
            
            int newOnes = (ones ^ num) & ~twos;
            int newTwos = (twos ^ num) & ~newOnes;
            
            System.out.println("  Before: ones=" + Integer.toBinaryString(ones) + 
                             ", twos=" + Integer.toBinaryString(twos));
            System.out.println("  After:  ones=" + Integer.toBinaryString(newOnes) + 
                             ", twos=" + Integer.toBinaryString(newTwos));
            
            ones = newOnes;
            twos = newTwos;
        }
        
        System.out.println("Final result: " + ones);
    }
}

