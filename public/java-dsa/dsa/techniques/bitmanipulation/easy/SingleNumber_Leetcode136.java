package dsa.techniques.bitmanipulation.easy;
/**
 * LeetCode 136: Single Number
 * Difficulty: Easy
 * 
 * Given a non-empty array of integers nums, every element appears twice except for one. 
 * Find that single one.
 * You must implement a solution with a linear runtime complexity and use only constant extra space.
 * 
 * Example 1:
 * Input: nums = [2,2,1]
 * Output: 1
 * 
 * Example 2:
 * Input: nums = [4,1,2,1,2]
 * Output: 4
 * 
 * Example 3:
 * Input: nums = [1]
 * Output: 1
 * 
 * Constraints:
 * - 1 <= nums.length <= 3 * 10^4
 * - -3 * 10^4 <= nums[i] <= 3 * 10^4
 * - Each element in the array appears twice except for one element which appears only once.
 */
public class SingleNumber_Leetcode136 {
    
    /**
     * Approach 1: Using XOR (Optimal)
     * XOR properties: a ^ a = 0, a ^ 0 = a, XOR is commutative and associative
     * Time: O(n), Space: O(1)
     */
    public int singleNumber(int[] nums) {
        int result = 0;
        for (int num : nums) {
            result ^= num;
        }
        return result;
    }
    
    /**
     * Approach 2: Using HashSet (Not optimal for this problem)
     * Time: O(n), Space: O(n)
     */
    public int singleNumberHashSet(int[] nums) {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        for (int num : nums) {
            if (set.contains(num)) {
                set.remove(num);
            } else {
                set.add(num);
            }
        }
        return set.iterator().next();
    }
    
    /**
     * Approach 3: Mathematical Approach
     * 2 * (sum of unique elements) - (sum of all elements) = single element
     * Time: O(n), Space: O(n) for set
     */
    public int singleNumberMath(int[] nums) {
        java.util.Set<Integer> set = new java.util.HashSet<>();
        int sumAll = 0, sumUnique = 0;
        
        for (int num : nums) {
            sumAll += num;
            if (!set.contains(num)) {
                set.add(num);
                sumUnique += num;
            }
        }
        
        return 2 * sumUnique - sumAll;
    }
    
    /**
     * Follow-up variations and explanations
     */
    
    /**
     * Variation 1: Every element appears 3 times except one (appears once)
     * Use bit counting approach
     */
    public int singleNumberIII(int[] nums) {
        int ones = 0, twos = 0;
        for (int num : nums) {
            ones = (ones ^ num) & ~twos;
            twos = (twos ^ num) & ~ones;
        }
        return ones;
    }
    
    /**
     * Variation 2: Two elements appear once, rest appear twice
     * LeetCode 260: Single Number III
     */
    public int[] singleNumberTwo(int[] nums) {
        int xor = 0;
        for (int num : nums) {
            xor ^= num;
        }
        
        // Find rightmost set bit in xor
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
     * Test method
     */
    public static void main(String[] args) {
        SingleNumber_Leetcode136 solution = new SingleNumber_Leetcode136();
        
        // Test cases for single number
        int[][] testCases = {
            {2, 2, 1},
            {4, 1, 2, 1, 2},
            {1},
            {7, 3, 5, 3, 5, 7, 9}
        };
        
        System.out.println("Testing Single Number (appears once, others twice):");
        for (int[] nums : testCases) {
            System.out.print("Input: ");
            for (int num : nums) System.out.print(num + " ");
            System.out.println("→ Output: " + solution.singleNumber(nums));
        }
        
        // Test single number III (appears 3 times)
        System.out.println("\nTesting Single Number (appears once, others thrice):");
        int[] nums3 = {2, 2, 3, 2};
        System.out.print("Input: ");
        for (int num : nums3) System.out.print(num + " ");
        System.out.println("→ Output: " + solution.singleNumberIII(nums3));
        
        // Test two single numbers
        System.out.println("\nTesting Two Single Numbers:");
        int[] numsTwo = {1, 2, 1, 3, 2, 5};
        System.out.print("Input: ");
        for (int num : numsTwo) System.out.print(num + " ");
        int[] result = solution.singleNumberTwo(numsTwo);
        System.out.println("→ Output: [" + result[0] + ", " + result[1] + "]");
    }
}

