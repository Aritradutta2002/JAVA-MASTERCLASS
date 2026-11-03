package dsa.techniques.prefixsum.problems.medium;

/**
 * LeetCode 238: Product of Array Except Self
 * Difficulty: Medium
 * 
 * Problem: Given an integer array nums, return an array answer such that 
 * answer[i] is equal to the product of all the elements of nums except nums[i].
 * 
 * Constraints:
 * - You must write an algorithm that runs in O(n) time
 * - Do not use the division operator
 * 
 * Example:
 * Input: nums = [1,2,3,4]
 * Output: [24,12,8,6]
 * Explanation: [2*3*4, 1*3*4, 1*2*4, 1*2*3]
 * 
 * Approach: Use prefix and suffix products
 * Time: O(n)
 * Space: O(1) excluding output array
 */
public class ProductExceptSelf_Leetcode238 {
    
    /**
     * Optimal solution: Two-pass with O(1) extra space
     * First pass: Calculate prefix products in result array
     * Second pass: Calculate suffix products on the fly
     */
    public static int[] productExceptSelf(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        
        // First pass: Calculate prefix products
        result[0] = 1;
        for (int i = 1; i < n; i++) {
            result[i] = result[i - 1] * nums[i - 1];
        }
        
        // Second pass: Multiply with suffix products
        int suffixProduct = 1;
        for (int i = n - 1; i >= 0; i--) {
            result[i] *= suffixProduct;
            suffixProduct *= nums[i];
        }
        
        return result;
    }
    
    /**
     * Alternative approach: Using separate prefix and suffix arrays
     * Easier to understand but uses O(n) extra space
     */
    public static int[] productExceptSelfVerbose(int[] nums) {
        int n = nums.length;
        int[] prefix = new int[n];
        int[] suffix = new int[n];
        int[] result = new int[n];
        
        // Build prefix products
        prefix[0] = 1;
        for (int i = 1; i < n; i++) {
            prefix[i] = prefix[i - 1] * nums[i - 1];
        }
        
        // Build suffix products
        suffix[n - 1] = 1;
        for (int i = n - 2; i >= 0; i--) {
            suffix[i] = suffix[i + 1] * nums[i + 1];
        }
        
        // Combine prefix and suffix
        for (int i = 0; i < n; i++) {
            result[i] = prefix[i] * suffix[i];
        }
        
        return result;
    }
    
    /**
     * Solution handling zeros (edge case consideration)
     */
    public static int[] productExceptSelfWithZeros(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        
        // Count zeros and calculate product of non-zero elements
        int zeroCount = 0;
        int productWithoutZeros = 1;
        
        for (int num : nums) {
            if (num == 0) {
                zeroCount++;
            } else {
                productWithoutZeros *= num;
            }
        }
        
        for (int i = 0; i < n; i++) {
            if (zeroCount > 1) {
                // More than one zero means all products are zero
                result[i] = 0;
            } else if (zeroCount == 1) {
                // Exactly one zero
                result[i] = (nums[i] == 0) ? productWithoutZeros : 0;
            } else {
                // No zeros
                result[i] = productWithoutZeros / nums[i];
            }
        }
        
        return result;
    }
    
    /**
     * Variation: Product of array except self with modular arithmetic
     * Useful when dealing with large numbers
     */
    public static int[] productExceptSelfMod(int[] nums, int mod) {
        int n = nums.length;
        int[] result = new int[n];
        
        // Calculate prefix products with mod
        result[0] = 1;
        for (int i = 1; i < n; i++) {
            result[i] = (int)((long)result[i - 1] * nums[i - 1] % mod);
        }
        
        // Calculate suffix products with mod
        long suffixProduct = 1;
        for (int i = n - 1; i >= 0; i--) {
            result[i] = (int)((long)result[i] * suffixProduct % mod);
            suffixProduct = suffixProduct * nums[i] % mod;
        }
        
        return result;
    }
    
    /**
     * Helper method to verify solution correctness
     */
    public static boolean verifySolution(int[] nums, int[] result) {
        for (int i = 0; i < nums.length; i++) {
            int expectedProduct = 1;
            for (int j = 0; j < nums.length; j++) {
                if (i != j) {
                    expectedProduct *= nums[j];
                }
            }
            if (result[i] != expectedProduct) {
                return false;
            }
        }
        return true;
    }
    
    public static void main(String[] args) {
        // Test case 1: Normal case
        int[] nums1 = {1, 2, 3, 4};
        int[] result1 = productExceptSelf(nums1);
        System.out.println("Input: " + java.util.Arrays.toString(nums1));
        System.out.println("Output: " + java.util.Arrays.toString(result1)); // Expected: [24, 12, 8, 6]
        System.out.println("Verification: " + verifySolution(nums1, result1));
        System.out.println();
        
        // Test case 2: With zeros
        int[] nums2 = {-1, 1, 0, -3, 3};
        int[] result2 = productExceptSelf(nums2);
        System.out.println("Input: " + java.util.Arrays.toString(nums2));
        System.out.println("Output: " + java.util.Arrays.toString(result2)); // Expected: [0, 0, 9, 0, 0]
        System.out.println("Verification: " + verifySolution(nums2, result2));
        System.out.println();
        
        // Test case 3: All positive
        int[] nums3 = {2, 3, 4, 5};
        int[] result3 = productExceptSelf(nums3);
        System.out.println("Input: " + java.util.Arrays.toString(nums3));
        System.out.println("Output: " + java.util.Arrays.toString(result3)); // Expected: [60, 40, 30, 24]
        System.out.println();
        
        // Test case 4: With negative numbers
        int[] nums4 = {-1, 2, -3, 4};
        int[] result4 = productExceptSelf(nums4);
        System.out.println("Input: " + java.util.Arrays.toString(nums4));
        System.out.println("Output: " + java.util.Arrays.toString(result4)); // Expected: [-24, 12, -8, 6]
        System.out.println();
        
        // Compare approaches
        System.out.println("Comparing approaches:");
        int[] verboseResult = productExceptSelfVerbose(nums1);
        System.out.println("Verbose approach: " + java.util.Arrays.toString(verboseResult));
        
        int[] zeroHandlingResult = productExceptSelfWithZeros(nums2);
        System.out.println("Zero handling approach: " + java.util.Arrays.toString(zeroHandlingResult));
        
        // Test with modular arithmetic
        int[] modResult = productExceptSelfMod(nums1, 1000000007);
        System.out.println("With modular arithmetic: " + java.util.Arrays.toString(modResult));
    }
}
