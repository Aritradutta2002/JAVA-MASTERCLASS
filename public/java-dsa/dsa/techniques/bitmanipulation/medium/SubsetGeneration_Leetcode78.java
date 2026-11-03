package dsa.techniques.bitmanipulation.medium;
/**
 * LeetCode 78: Subsets
 * Difficulty: Medium
 * 
 * Given an integer array nums of unique elements, return all possible subsets (the power set).
 * The solution set must not contain duplicate subsets. Return the solution in any order.
 * 
 * Example 1:
 * Input: nums = [1,2,3]
 * Output: [[],[1],[2],[1,2],[3],[1,3],[2,3],[1,2,3]]
 * 
 * Example 2:
 * Input: nums = [0]
 * Output: [[],[0]]
 * 
 * Constraints:
 * 1 <= nums.length <= 10
 * -10 <= nums[i] <= 10
 * All the numbers of nums are unique.
 */
public class SubsetGeneration_Leetcode78 {
    
    /**
     * Approach 1: Bit Manipulation (Most Intuitive for Bit Manipulation Practice)
     * Each bit represents whether to include the element at that index
     * Time: O(n * 2^n), Space: O(n * 2^n)
     */
    public java.util.List<java.util.List<Integer>> subsets(int[] nums) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        int totalSubsets = 1 << n; // 2^n
        
        for (int mask = 0; mask < totalSubsets; mask++) {
            java.util.List<Integer> subset = new java.util.ArrayList<>();
            
            for (int i = 0; i < n; i++) {
                // Check if ith bit is set in mask
                if ((mask & (1 << i)) != 0) {
                    subset.add(nums[i]);
                }
            }
            
            result.add(subset);
        }
        
        return result;
    }
    
    /**
     * Approach 2: Iterative Build-up
     * Start with empty set, for each new element, duplicate existing subsets
     * Time: O(n * 2^n), Space: O(n * 2^n)
     */
    public java.util.List<java.util.List<Integer>> subsetsIterative(int[] nums) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        result.add(new java.util.ArrayList<>()); // Start with empty subset
        
        for (int num : nums) {
            int size = result.size();
            for (int i = 0; i < size; i++) {
                java.util.List<Integer> newSubset = new java.util.ArrayList<>(result.get(i));
                newSubset.add(num);
                result.add(newSubset);
            }
        }
        
        return result;
    }
    
    /**
     * Approach 3: Backtracking/DFS
     * Classic recursive approach
     * Time: O(n * 2^n), Space: O(n) for recursion stack
     */
    public java.util.List<java.util.List<Integer>> subsetsBacktrack(int[] nums) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        backtrack(nums, 0, new java.util.ArrayList<>(), result);
        return result;
    }
    
    private void backtrack(int[] nums, int start, java.util.List<Integer> current, 
                          java.util.List<java.util.List<Integer>> result) {
        result.add(new java.util.ArrayList<>(current));
        
        for (int i = start; i < nums.length; i++) {
            current.add(nums[i]);
            backtrack(nums, i + 1, current, result);
            current.remove(current.size() - 1);
        }
    }
    
    /**
     * Advanced: Generate subsets in lexicographic order using bit manipulation
     */
    public java.util.List<java.util.List<Integer>> subsetsLexicographic(int[] nums) {
        java.util.Arrays.sort(nums); // Sort for lexicographic order
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            java.util.List<Integer> subset = new java.util.ArrayList<>();
            
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(nums[i]);
                }
            }
            
            result.add(subset);
        }
        
        return result;
    }
    
    /**
     * Extension: Generate k-sized subsets (combinations)
     */
    public java.util.List<java.util.List<Integer>> kSizedSubsets(int[] nums, int k) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            if (Integer.bitCount(mask) == k) {
                java.util.List<Integer> subset = new java.util.ArrayList<>();
                
                for (int i = 0; i < n; i++) {
                    if ((mask & (1 << i)) != 0) {
                        subset.add(nums[i]);
                    }
                }
                
                result.add(subset);
            }
        }
        
        return result;
    }
    
    /**
     * Extension: Generate subsets with specific sum
     */
    public java.util.List<java.util.List<Integer>> subsetsWithSum(int[] nums, int targetSum) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        
        for (int mask = 0; mask < (1 << n); mask++) {
            java.util.List<Integer> subset = new java.util.ArrayList<>();
            int sum = 0;
            
            for (int i = 0; i < n; i++) {
                if ((mask & (1 << i)) != 0) {
                    subset.add(nums[i]);
                    sum += nums[i];
                }
            }
            
            if (sum == targetSum) {
                result.add(subset);
            }
        }
        
        return result;
    }
    
    /**
     * Advanced: Generate subsets using Gray Code sequence
     * Each step changes exactly one element (more efficient for some applications)
     */
    public java.util.List<java.util.List<Integer>> subsetsGrayCode(int[] nums) {
        java.util.List<java.util.List<Integer>> result = new java.util.ArrayList<>();
        int n = nums.length;
        java.util.List<Integer> current = new java.util.ArrayList<>();
        
        // Generate subsets in Gray Code order
        for (int i = 0; i < (1 << n); i++) {
            int grayCode = i ^ (i >> 1); // Convert to Gray Code
            
            // Build subset based on Gray Code
            java.util.List<Integer> subset = new java.util.ArrayList<>();
            for (int j = 0; j < n; j++) {
                if ((grayCode & (1 << j)) != 0) {
                    subset.add(nums[j]);
                }
            }
            
            result.add(subset);
        }
        
        return result;
    }
    
    /**
     * Utility: Count total number of subsets
     */
    public int countSubsets(int[] nums) {
        return 1 << nums.length; // 2^n
    }
    
    /**
     * Utility: Get subset by index (0-based)
     */
    public java.util.List<Integer> getSubsetByIndex(int[] nums, int index) {
        java.util.List<Integer> subset = new java.util.ArrayList<>();
        
        for (int i = 0; i < nums.length; i++) {
            if ((index & (1 << i)) != 0) {
                subset.add(nums[i]);
            }
        }
        
        return subset;
    }
    
    /**
     * Utility: Convert subset to bitmask
     */
    public int subsetToBitmask(int[] nums, java.util.List<Integer> subset) {
        int mask = 0;
        java.util.Map<Integer, Integer> indexMap = new java.util.HashMap<>();
        
        for (int i = 0; i < nums.length; i++) {
            indexMap.put(nums[i], i);
        }
        
        for (int num : subset) {
            if (indexMap.containsKey(num)) {
                mask |= (1 << indexMap.get(num));
            }
        }
        
        return mask;
    }
    
    /**
     * Demonstration: Show binary representation for each subset
     */
    public void demonstrateSubsetGeneration(int[] nums) {
        System.out.println("Subset Generation with Binary Representation:");
        System.out.println("Array: " + java.util.Arrays.toString(nums));
        System.out.println();
        
        int n = nums.length;
        System.out.printf("%-8s %-15s %-20s %s%n", "Index", "Binary", "Subset", "Elements");
        System.out.println("---------------------------------------------------");
        
        for (int mask = 0; mask < (1 << n); mask++) {
            java.util.List<Integer> subset = getSubsetByIndex(nums, mask);
            String binary = String.format("%" + n + "s", Integer.toBinaryString(mask)).replace(' ', '0');
            
            System.out.printf("%-8d %-15s %-20s %s%n", 
                mask, binary, subset.toString(), subset.isEmpty() ? "∅" : subset.toString());
        }
        
        System.out.println();
        System.out.println("Total subsets: " + (1 << n));
    }
    
    /**
     * Performance comparison between different approaches
     */
    public void performanceComparison(int[] nums) {
        System.out.println("Performance Comparison:");
        
        // Bit Manipulation
        long start = System.nanoTime();
        java.util.List<java.util.List<Integer>> result1 = subsets(nums);
        long time1 = System.nanoTime() - start;
        
        // Iterative
        start = System.nanoTime();
        java.util.List<java.util.List<Integer>> result2 = subsetsIterative(nums);
        long time2 = System.nanoTime() - start;
        
        // Backtracking
        start = System.nanoTime();
        java.util.List<java.util.List<Integer>> result3 = subsetsBacktrack(nums);
        long time3 = System.nanoTime() - start;
        
        System.out.printf("Bit Manipulation: %.3f ms (%d subsets)%n", time1 / 1_000_000.0, result1.size());
        System.out.printf("Iterative:        %.3f ms (%d subsets)%n", time2 / 1_000_000.0, result2.size());
        System.out.printf("Backtracking:     %.3f ms (%d subsets)%n", time3 / 1_000_000.0, result3.size());
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        SubsetGeneration_Leetcode78 solution = new SubsetGeneration_Leetcode78();
        
        // Test case 1
        System.out.println("=== TEST CASE 1 ===");
        int[] nums1 = {1, 2, 3};
        java.util.List<java.util.List<Integer>> result1 = solution.subsets(nums1);
        System.out.println("Input: " + java.util.Arrays.toString(nums1));
        System.out.println("Output: " + result1);
        System.out.println();
        
        // Demonstrate with binary representation
        solution.demonstrateSubsetGeneration(nums1);
        
        // Test case 2
        System.out.println("=== TEST CASE 2 ===");
        int[] nums2 = {0};
        java.util.List<java.util.List<Integer>> result2 = solution.subsets(nums2);
        System.out.println("Input: " + java.util.Arrays.toString(nums2));
        System.out.println("Output: " + result2);
        System.out.println();
        
        // Test k-sized subsets
        System.out.println("=== K-SIZED SUBSETS ===");
        int[] nums3 = {1, 2, 3, 4};
        for (int k = 0; k <= nums3.length; k++) {
            java.util.List<java.util.List<Integer>> kSubsets = solution.kSizedSubsets(nums3, k);
            System.out.println("k=" + k + ": " + kSubsets);
        }
        System.out.println();
        
        // Test subsets with specific sum
        System.out.println("=== SUBSETS WITH SUM ===");
        int[] nums4 = {1, 2, 3, 4};
        int targetSum = 5;
        java.util.List<java.util.List<Integer>> sumSubsets = solution.subsetsWithSum(nums4, targetSum);
        System.out.println("Array: " + java.util.Arrays.toString(nums4));
        System.out.println("Target sum: " + targetSum);
        System.out.println("Subsets with sum " + targetSum + ": " + sumSubsets);
        System.out.println();
        
        // Test Gray Code generation
        System.out.println("=== GRAY CODE ORDER ===");
        int[] nums5 = {1, 2, 3};
        java.util.List<java.util.List<Integer>> graySubsets = solution.subsetsGrayCode(nums5);
        System.out.println("Gray Code order subsets:");
        for (int i = 0; i < graySubsets.size(); i++) {
            int grayCode = i ^ (i >> 1);
            String binary = String.format("%3s", Integer.toBinaryString(grayCode)).replace(' ', '0');
            System.out.printf("Step %d (Gray: %s): %s%n", i, binary, graySubsets.get(i));
        }
        System.out.println();
        
        // Performance comparison
        System.out.println("=== PERFORMANCE COMPARISON ===");
        int[] largePerfArray = {1, 2, 3, 4, 5, 6, 7, 8}; // 2^8 = 256 subsets
        solution.performanceComparison(largePerfArray);
        System.out.println();
        
        // Edge cases
        System.out.println("=== EDGE CASES ===");
        
        // Empty array (edge case, though not in constraints)
        int[] empty = {};
        System.out.println("Empty array subsets: " + solution.subsets(empty));
        
        // Single element
        int[] single = {5};
        System.out.println("Single element subsets: " + solution.subsets(single));
        
        // Negative numbers
        int[] negative = {-1, -2};
        System.out.println("Negative numbers subsets: " + solution.subsets(negative));
        
        // Test subset to bitmask conversion
        System.out.println("\n=== SUBSET TO BITMASK CONVERSION ===");
        int[] testArray = {1, 2, 3};
        java.util.List<Integer> testSubset = java.util.Arrays.asList(1, 3);
        int bitmask = solution.subsetToBitmask(testArray, testSubset);
        System.out.printf("Array: %s, Subset: %s → Bitmask: %d (binary: %s)%n",
            java.util.Arrays.toString(testArray), testSubset, bitmask, Integer.toBinaryString(bitmask));
        
        // Verify by converting back
        java.util.List<Integer> backToSubset = solution.getSubsetByIndex(testArray, bitmask);
        System.out.println("Converted back to subset: " + backToSubset);
    }
}

