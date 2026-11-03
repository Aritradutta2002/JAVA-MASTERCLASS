package dsa.techniques.prefixsum.theory;

/**
 * Prefix Sum Fundamentals - Complete Theory and Examples
 * 
 * What is Prefix Sum?
 * A prefix sum array is a data structure that allows efficient calculation
 * of range sums in an array. For array [a1, a2, a3, ..., an], the prefix
 * sum array contains [a1, a1+a2, a1+a2+a3, ..., a1+a2+...+an]
 * 
 * Time Complexity:
 * - Preprocessing: O(n)
 * - Range Query: O(1)
 * 
 * Space Complexity: O(n) for storing prefix array
 */
 
public class PrefixSumFundamentals {
    
    /**
     * Example 1: Basic Prefix Sum Construction
     * Input: [1, 2, 3, 4, 5]
     * Output: [1, 3, 6, 10, 15]
     */
    public static int[] buildPrefixSum(int[] arr) {
        int n = arr.length;
        int[] prefixSum = new int[n];
        
        prefixSum[0] = arr[0];
        for (int i = 1; i < n; i++) {
            prefixSum[i] = prefixSum[i-1] + arr[i];
        }
        
        return prefixSum;
    }
    
    /**
     * Example 2: Range Sum Query using Prefix Sum
     * Query sum from index left to right (both inclusive)
     * Formula: sum(left, right) = prefixSum[right] - prefixSum[left-1]
     * Special case: if left = 0, then sum = prefixSum[right]
     */
	 
    public static int rangeSum(int[] prefixSum, int left, int right) {
        if (left == 0) {
            return prefixSum[right];
        }
        return prefixSum[right] - prefixSum[left - 1];
    }
    
    /**
     * Example 3: In-place Prefix Sum (Space Optimized)
     * Modify the original array to store prefix sums
     */
	 
    public static void buildPrefixSumInPlace(int[] arr) {
        for (int i = 1; i < arr.length; i++) {
            arr[i] += arr[i-1];
        }
    }
    
    /**
     * Example 4: 2D Prefix Sum
     * For matrix range sum queries
     */
	 
    public static int[][] build2DPrefixSum(int[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        int[][] prefixSum = new int[rows + 1][cols + 1];
        
        for (int i = 1; i <= rows; i++) {
            for (int j = 1; j <= cols; j++) {
                prefixSum[i][j] = matrix[i-1][j-1] 
                                + prefixSum[i-1][j] 
                                + prefixSum[i][j-1] 
                                - prefixSum[i-1][j-1];
            }
        }
        
        return prefixSum;
    }
    
    /**
     * Example 5: 2D Range Sum Query
     * Query sum of rectangle from (row1,col1) to (row2,col2)
     */
	 
    public static int range2DSum(int[][] prefixSum, int row1, int col1, int row2, int col2) {
        // Convert to 1-indexed for prefix sum array
        row1++; col1++; row2++; col2++;
        
        return prefixSum[row2][col2] 
             - prefixSum[row1-1][col2] 
             - prefixSum[row2][col1-1] 
             + prefixSum[row1-1][col1-1];
    }
    
    /**
     * Example 6: Prefix Sum with Modular Arithmetic
     * Useful for problems involving large sums or counting subarrays
     */
	 
    public static long[] buildPrefixSumMod(int[] arr, long mod) {
        int n = arr.length;
        long[] prefixSum = new long[n];
        
        prefixSum[0] = arr[0] % mod;
        for (int i = 1; i < n; i++) {
            prefixSum[i] = (prefixSum[i-1] + arr[i]) % mod;
        }
        
        return prefixSum;
    }
    
    // Demonstration and Testing
    public static void main(String[] args) {
        // Test 1D Prefix Sum
        int[] arr = {1, 2, 3, 4, 5};
        int[] prefixSum = buildPrefixSum(arr);
        
        System.out.println("Original Array: " + java.util.Arrays.toString(arr));
        System.out.println("Prefix Sum: " + java.util.Arrays.toString(prefixSum));
        
        // Test Range Queries
        System.out.println("Sum from index 1 to 3: " + rangeSum(prefixSum, 1, 3)); // 2+3+4 = 9
        System.out.println("Sum from index 0 to 2: " + rangeSum(prefixSum, 0, 2)); // 1+2+3 = 6
        
        // Test 2D Prefix Sum
        int[][] matrix = {
            {1, 2, 3},
            {4, 5, 6},
            {7, 8, 9}
        };
        int[][] prefix2D = build2DPrefixSum(matrix);
        System.out.println("2D Range sum (0,0) to (1,1): " + range2DSum(prefix2D, 0, 0, 1, 1)); // 1+2+4+5 = 12
    }
}
