package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 1: TWO SUM
 * Difficulty: Easy
 * Pattern: Two Pointer / HashMap
 * 
 * PROBLEM STATEMENT:
 * Given an array of integers nums and an integer target, return indices of the 
 * two numbers such that they add up to target.
 * 
 * You may assume that each input would have exactly one solution, and you may 
 * not use the same element twice. You can return the answer in any order.
 * 
 * EXAMPLE 1:
 * Input: nums = [2,7,11,15], target = 9
 * Output: [0,1]
 * Explanation: Because nums[0] + nums[1] == 9, we return [0, 1].
 * 
 * EXAMPLE 2:
 * Input: nums = [3,2,4], target = 6
 * Output: [1,2]
 * 
 * EXAMPLE 3:
 * Input: nums = [3,3], target = 6
 * Output: [0,1]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE TO UNDERSTANDING THIS PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHAT IS THIS PROBLEM ASKING?
 * ----------------------------
 * Imagine you're in a store with $9. You need to find TWO items whose prices 
 * add up to exactly $9. The store gives you a list of prices, and you need to 
 * find which two items you can buy.
 * 
 * For example: Prices = [2, 7, 11, 15], Budget = 9
 * - Can I buy items at $2 and $7? YES! (2 + 7 = 9)
 * - Return their positions: [0, 1]
 * 
 * WHY IS THIS PROBLEM IMPORTANT?
 * -------------------------------
 * - Most frequently asked interview question
 * - Teaches HashMap optimization technique
 * - Foundation for many other "pair" problems
 * - Tests understanding of time-space tradeoff
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: BRUTE FORCE (CHECKING ALL PAIRS)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * The simplest way is to check every possible pair of numbers:
 * - Take first number, check with all others
 * - Take second number, check with all others after it
 * - And so on...
 * 
 * STEP-BY-STEP PROCESS:
 * ---------------------
 * For array [2, 7, 11, 15] and target = 9:
 * 
 * Round 1: Check pairs starting with 2
 *   - 2 + 7 = 9 ✓ Found it! Return [0, 1]
 * 
 * If we had [3, 2, 4] and target = 6:
 * Round 1: Check pairs starting with 3
 *   - 3 + 2 = 5 ✗
 *   - 3 + 4 = 7 ✗
 * Round 2: Check pairs starting with 2
 *   - 2 + 4 = 6 ✓ Found it! Return [1, 2]
 * 
 * VISUALIZATION:
 * --------------
 * Array: [2, 7, 11, 15], Target: 9
 * 
 * i=0: 2 + ? = 9
 *      ↓   ↓
 *      2 + 7 = 9 ✓ FOUND!
 * 
 * TIME COMPLEXITY: O(n²) - We check all pairs (nested loops)
 * SPACE COMPLEXITY: O(1) - No extra space needed
 * 
 * WHEN TO USE:
 * - When array is very small (< 10 elements)
 * - When you want simple, easy-to-understand code
 * - NOT recommended for interviews (too slow)
 */

import java.util.*;

// APPROACH 1: BRUTE FORCE
class TwoSum_BruteForce {
    public int[] twoSum(int[] nums, int target) {
        int n = nums.length;
        
        // Check every pair of numbers
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                // If we found the pair that sums to target
                if (nums[i] + nums[j] == target) {
                    return new int[] {i, j};
                }
            }
        }
        
        // No solution found (shouldn't happen per problem constraints)
        return new int[] {-1, -1};
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: HASHMAP (OPTIMAL SOLUTION) - SINGLE PASS
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * Instead of checking all pairs, we can be smarter!
 * 
 * Key Insight: For each number, we need to find its "complement"
 * - If current number is 'x' and target is 'target'
 * - Then we need to find: complement = target - x
 * 
 * Example: [2, 7, 11, 15], target = 9
 * - At 2: We need (9 - 2) = 7. Have we seen 7 before? No.
 * - At 7: We need (9 - 7) = 2. Have we seen 2 before? YES! At index 0.
 * - Found the pair: [0, 1]
 * 
 * HOW HASHMAP HELPS:
 * ------------------
 * HashMap stores: {number → index}
 * - Allows us to check "have we seen this number before?" in O(1) time
 * - Stores the index so we can return it later
 * 
 * DETAILED EXAMPLE:
 * -----------------
 * Array: [2, 7, 11, 15], Target: 9
 * HashMap: {}
 * 
 * Step 1: At index 0, num = 2
 *   - Need complement = 9 - 2 = 7
 *   - Is 7 in HashMap? NO
 *   - Add to HashMap: {2 → 0}
 * 
 * Step 2: At index 1, num = 7
 *   - Need complement = 9 - 7 = 2
 *   - Is 2 in HashMap? YES! At index 0
 *   - Found pair! Return [0, 1]
 * 
 * ANOTHER EXAMPLE:
 * ----------------
 * Array: [3, 2, 4], Target: 6
 * HashMap: {}
 * 
 * Step 1: At index 0, num = 3
 *   - Need complement = 6 - 3 = 3
 *   - Is 3 in HashMap? NO
 *   - Add to HashMap: {3 → 0}
 * 
 * Step 2: At index 1, num = 2
 *   - Need complement = 6 - 2 = 4
 *   - Is 4 in HashMap? NO
 *   - Add to HashMap: {3 → 0, 2 → 1}
 * 
 * Step 3: At index 2, num = 4
 *   - Need complement = 6 - 4 = 2
 *   - Is 2 in HashMap? YES! At index 1
 *   - Found pair! Return [1, 2]
 * 
 * EDGE CASE - DUPLICATE NUMBERS:
 * ------------------------------
 * Array: [3, 3], Target: 6
 * 
 * Step 1: At index 0, num = 3
 *   - Need complement = 6 - 3 = 3
 *   - Is 3 in HashMap? NO
 *   - Add to HashMap: {3 → 0}
 * 
 * Step 2: At index 1, num = 3
 *   - Need complement = 6 - 3 = 3
 *   - Is 3 in HashMap? YES! At index 0
 *   - Found pair! Return [0, 1]
 * 
 * This works because we check BEFORE adding, so we don't use same element twice!
 * 
 * TIME COMPLEXITY: O(n) - Single pass through array, O(1) HashMap operations
 * SPACE COMPLEXITY: O(n) - HashMap can store up to n elements
 * 
 * WHEN TO USE:
 * - Almost always! This is the optimal solution
 * - When you need O(n) time complexity
 * - Perfect for interviews (shows knowledge of data structures)
 */

public class TwoSum_Leetcode1 {
    
    // APPROACH 2: HASHMAP (OPTIMAL) - SINGLE PASS
    public int[] twoSum(int[] nums, int target) {
        // HashMap to store: {number → its index}
        HashMap<Integer, Integer> map = new HashMap<>();
        
        // Go through array once
        for (int i = 0; i < nums.length; i++) {
            int currentNum = nums[i];
            
            // Calculate what number we need to find
            int complement = target - currentNum;
            
            // Check if we've seen the complement before
            if (map.containsKey(complement)) {
                // Found it! Return the pair of indices
                return new int[] {map.get(complement), i};
            }
            
            // Haven't found complement yet, store current number and its index
            map.put(currentNum, i);
        }
        
        // No solution found (shouldn't happen per problem constraints)
        return new int[] {-1, -1};
    }
    
    // Test the solution
    public static void main(String[] args) {
        TwoSum_Leetcode1 solution = new TwoSum_Leetcode1();
        
        // Test Case 1
        int[] nums1 = {2, 7, 11, 15};
        int target1 = 9;
        int[] result1 = solution.twoSum(nums1, target1);
        System.out.println("Test Case 1:");
        System.out.println("Input: nums = " + Arrays.toString(nums1) + ", target = " + target1);
        System.out.println("Output: " + Arrays.toString(result1));
        System.out.println("Explanation: nums[" + result1[0] + "] + nums[" + result1[1] + "] = " 
                          + nums1[result1[0]] + " + " + nums1[result1[1]] + " = " + target1);
        System.out.println();
        
        // Test Case 2
        int[] nums2 = {3, 2, 4};
        int target2 = 6;
        int[] result2 = solution.twoSum(nums2, target2);
        System.out.println("Test Case 2:");
        System.out.println("Input: nums = " + Arrays.toString(nums2) + ", target = " + target2);
        System.out.println("Output: " + Arrays.toString(result2));
        System.out.println();
        
        // Test Case 3
        int[] nums3 = {3, 3};
        int target3 = 6;
        int[] result3 = solution.twoSum(nums3, target3);
        System.out.println("Test Case 3:");
        System.out.println("Input: nums = " + Arrays.toString(nums3) + ", target = " + target3);
        System.out.println("Output: " + Arrays.toString(result3));
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 3: TWO POINTER (REQUIRES SORTING)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * NOTE: This approach DOESN'T work for this problem because:
 * - We need to return the ORIGINAL indices
 * - Sorting the array changes the indices
 * - We'd need extra space to track original indices (defeating the purpose)
 * 
 * However, this technique is useful for variants like:
 * - "Does a pair exist?" (yes/no question)
 * - "Find all unique pairs that sum to target"
 * - "Find pair in a SORTED array"
 * 
 * INTUITION (for sorted array):
 * -----------------------------
 * Use two pointers: one at start, one at end
 * - If sum is too small → move left pointer right (increase sum)
 * - If sum is too large → move right pointer left (decrease sum)
 * - If sum equals target → found the pair!
 * 
 * Example: [2, 7, 11, 15] (already sorted), target = 9
 * 
 * left = 0 (2), right = 3 (15)
 *   2 + 15 = 17 > 9, move right pointer left
 * 
 * left = 0 (2), right = 2 (11)
 *   2 + 11 = 13 > 9, move right pointer left
 * 
 * left = 0 (2), right = 1 (7)
 *   2 + 7 = 9 = target ✓ Found!
 * 
 * TIME COMPLEXITY: O(n) for two pointers, but O(n log n) if we need to sort first
 * SPACE COMPLEXITY: O(1) - Only using two pointers
 */

class TwoSum_TwoPointer {
    // This returns boolean (pair exists or not), not indices
    public boolean twoSumExists(int[] nums, int target) {
        // First sort the array (changes indices!)
        Arrays.sort(nums);
        
        int left = 0;
        int right = nums.length - 1;
        
        while (left < right) {
            int sum = nums[left] + nums[right];
            
            if (sum == target) {
                return true;  // Found the pair!
            } else if (sum < target) {
                left++;  // Need larger sum, move left pointer right
            } else {
                right--;  // Need smaller sum, move right pointer left
            }
        }
        
        return false;  // No pair found
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR BEGINNERS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. PATTERN RECOGNITION:
 *    - "Find a pair" → Think HashMap or Two Pointer
 *    - "Return indices" → HashMap is better (preserves index info)
 *    - "Find all pairs" → Two Pointer on sorted array
 * 
 * 2. TIME-SPACE TRADEOFF:
 *    - Brute Force: O(n²) time, O(1) space
 *    - HashMap: O(n) time, O(n) space
 *    - Choosing the right approach depends on constraints
 * 
 * 3. HASHMAP STRATEGY:
 *    - Store what you've seen so far
 *    - Check for complement before adding current element
 *    - This prevents using same element twice
 * 
 * 4. COMMON MISTAKES:
 *    - Using same element twice (check index i != j)
 *    - Sorting when you need to preserve indices
 *    - Not checking if complement exists before accessing HashMap
 * 
 * 5. FOLLOW-UP QUESTIONS (Be Prepared):
 *    - "What if array is sorted?" → Use two pointer
 *    - "What if we need all pairs?" → Return list of pairs
 *    - "What if no solution exists?" → Return empty array or null
 *    - "What if multiple solutions exist?" → Return any one
 * 
 * 6. INTERVIEW TIPS:
 *    - Always start by explaining the brute force approach
 *    - Then explain why it's slow (O(n²))
 *    - Introduce HashMap as optimization
 *    - Explain the complement logic clearly
 *    - Walk through an example step by step
 * 
 * 7. VARIATIONS TO PRACTICE:
 *    - 3Sum (Leetcode 15)
 *    - 4Sum (Leetcode 18)
 *    - Two Sum II - Input Array Is Sorted (Leetcode 167)
 *    - Two Sum Less Than K (Leetcode 1099)
 * 
 * 8. RELATED PATTERNS:
 *    - Subarray Sum Equals K (uses prefix sum + HashMap)
 *    - Valid Anagram (uses HashMap for character counting)
 *    - Group Anagrams (uses HashMap with sorted string as key)
 */

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * COMPLEXITY ANALYSIS SUMMARY:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * | Approach      | Time Complexity | Space Complexity | Use Case                |
 * |---------------|-----------------|------------------|-------------------------|
 * | Brute Force   | O(n²)           | O(1)            | Very small arrays       |
 * | HashMap       | O(n)            | O(n)            | **Best for interviews** |
 * | Two Pointer   | O(n log n)      | O(1)            | Sorted array variants   |
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 */


