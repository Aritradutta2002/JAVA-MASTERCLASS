package dsa.datastructures.arrays.basics;

/*
 * Problem: Product of Array Except Self
 * LeetCode: #238
 * Difficulty: Medium
 * Companies: Amazon, Facebook, Google, Microsoft, Apple, LinkedIn
 * 
 * Problem Statement:
 * Given an integer array nums, return an array answer such that answer[i] is equal to 
 * the product of all the elements of nums except nums[i].
 * The product of any prefix or suffix of nums is guaranteed to fit in a 32-bit integer.
 * You must write an algorithm that runs in O(n) time and without using the division operation.
 *
 * Example 1:
 * Input: nums = [1,2,3,4]
 * Output: [24,12,8,6]
 *
 * Example 2:
 * Input: nums = [-1,1,0,-3,3]
 * Output: [0,0,9,0,0]
 *
 * Constraints:
 * - 2 <= nums.length <= 10^5
 * - -30 <= nums[i] <= 30
 * - The product of any prefix or suffix is guaranteed to fit in a 32-bit integer.
 *
 * Follow up: Can you solve it in O(1) extra space? (Output array doesn't count)
 *
 * Time Complexity: O(n)
 * Space Complexity: O(1) excluding output array
 *
 * Approach:
 * Use prefix and suffix products. For each index i:
 * answer[i] = (product of all elements before i) * (product of all elements after i)
 */

public class ProductExceptSelf_LC238 {

    // Optimal solution - O(1) space
    public int[] productExceptSelf(int[] nums) {
        int n = nums.length;
        int[] answer = new int[n];

        // Calculate prefix products
        answer[0] = 1;
        for (int i = 1; i < n; i++) {
            answer[i] = answer[i - 1] * nums[i - 1];
        }

        // Calculate suffix products and combine
        int suffix = 1;
        for (int i = n - 1; i >= 0; i--) {
            answer[i] *= suffix;
            suffix *= nums[i];
        }

        return answer;
    }

    // Solution with explicit prefix and suffix arrays - O(n) space
    public int[] productExceptSelfExplicit(int[] nums) {
        int n = nums.length;
        int[] prefix = new int[n];
        int[] suffix = new int[n];
        int[] answer = new int[n];

        // Build prefix array
        prefix[0] = 1;
        for (int i = 1; i < n; i++) {
            prefix[i] = prefix[i - 1] * nums[i - 1];
        }

        // Build suffix array
        suffix[n - 1] = 1;
        for (int i = n - 2; i >= 0; i--) {
            suffix[i] = suffix[i + 1] * nums[i + 1];
        }

        // Combine
        for (int i = 0; i < n; i++) {
            answer[i] = prefix[i] * suffix[i];
        }

        return answer;
    }

    // Using division (violates constraint but good to know)
    public int[] productExceptSelfDivision(int[] nums) {
        int n = nums.length;
        int[] answer = new int[n];

        int totalProduct = 1;
        int zeroCount = 0;

        for (int num : nums) {
            if (num != 0) {
                totalProduct *= num;
            } else {
                zeroCount++;
            }
        }

        if (zeroCount > 1) {
            return answer; // All zeros
        }

        for (int i = 0; i < n; i++) {
            if (zeroCount == 1) {
                answer[i] = (nums[i] == 0) ? totalProduct : 0;
            } else {
                answer[i] = totalProduct / nums[i];
            }
        }

        return answer;
    }

    public static void main(String[] args) {
        ProductExceptSelf_LC238 solution = new ProductExceptSelf_LC238();

        // Test Case 1
        int[] nums1 = { 1, 2, 3, 4 };
        System.out.println("Test 1: " + java.util.Arrays.toString(solution.productExceptSelf(nums1)));
        // Output: [24, 12, 8, 6]

        // Test Case 2
        int[] nums2 = { -1, 1, 0, -3, 3 };
        System.out.println("Test 2: " + java.util.Arrays.toString(solution.productExceptSelf(nums2)));
        // Output: [0, 0, 9, 0, 0]

        // Test Case 3
        int[] nums3 = { 2, 3, 4, 5 };
        System.out.println("Test 3: " + java.util.Arrays.toString(solution.productExceptSelf(nums3)));
        // Output: [60, 40, 30, 24]

        // Test Case 4
        int[] nums4 = { 1, 1 };
        System.out.println("Test 4: " + java.util.Arrays.toString(solution.productExceptSelf(nums4)));
        // Output: [1, 1]
    }
}

