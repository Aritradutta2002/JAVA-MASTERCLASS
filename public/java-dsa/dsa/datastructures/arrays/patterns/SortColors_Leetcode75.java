package dsa.datastructures.arrays.patterns;

/*
 * LEETCODE 75: SORT COLORS
 * Difficulty: Medium
 * 
 * PROBLEM STATEMENT:
 * Given an array nums with n objects colored red, white, or blue, sort them in-place 
 * so that objects of the same color are adjacent, with the colors in the order red, white, and blue.
 * We will use the integers 0, 1, and 2 to represent the color red, white, and blue, respectively.
 * You must solve this problem without using the library's sort function.
 * 
 * EXAMPLE 1:
 * Input: nums = [2,0,2,1,1,0]
 * Output: [0,0,1,1,2,2]
 * 
 * EXAMPLE 2:
 * Input: nums = [2,0,1]
 * Output: [0,1,2]
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * BEGINNER'S GUIDE TO UNDERSTANDING THIS PROBLEM:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * WHAT IS THIS PROBLEM ASKING?
 * ----------------------------
 * Imagine you have a row of colored balls (Red=0, White=1, Blue=2) mixed up randomly.
 * You need to arrange them so all red balls come first, then all white balls, 
 * then all blue balls - and you can only rearrange them in the same row (in-place).
 * 
 * WHY IS IT CALLED "SORT COLORS"?
 * -------------------------------
 * This problem is also known as the "Dutch National Flag Problem" because the 
 * Dutch flag has three horizontal stripes of different colors, just like we're 
 * sorting three different values!
 * 
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 1: COUNTING SORT (EASIEST TO UNDERSTAND)
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * Think of it like counting your money:
 * 1. First, count how many $1 bills, $5 bills, and $10 bills you have
 * 2. Then, arrange them in order: all $1s first, then all $5s, then all $10s
 * 
 * STEP-BY-STEP PROCESS:
 * ---------------------
 * For array [2,0,2,1,1,0]:
 * 
 * Step 1: Count each color
 *   - Count of 0s (red): 2
 *   - Count of 1s (white): 2
 *   - Count of 2s (blue): 2
 * 
 * Step 2: Overwrite the original array
 *   - Put 2 zeros at the start: [0,0,_,_,_,_]
 *   - Put 2 ones next: [0,0,1,1,_,_]
 *   - Put 2 twos at the end: [0,0,1,1,2,2]
 * 
 * TIME COMPLEXITY: O(n) - We go through the array twice
 * SPACE COMPLEXITY: O(1) - We only use 3 variables for counting
 */

// APPROACH 1: COUNTING SORT
class SortColors_CountingSort {
    public void sortColors(int[] nums) {
        // STEP 1: Count how many 0s, 1s, and 2s we have
        int count0 = 0;  // Count of red balls
        int count1 = 0;  // Count of white balls
        int count2 = 0;  // Count of blue balls
        
        // Go through each number and count them
        for (int num : nums) {
            if (num == 0) {
                count0++;
            } else if (num == 1) {
                count1++;
            } else {
                count2++;
            }
        }
        
        // STEP 2: Overwrite the array with sorted values
        int index = 0;
        
        // Fill all 0s first
        while (count0 > 0) {
            nums[index] = 0;
            index++;
            count0--;
        }
        
        // Then fill all 1s
        while (count1 > 0) {
            nums[index] = 1;
            index++;
            count1--;
        }
        
        // Finally fill all 2s
        while (count2 > 0) {
            nums[index] = 2;
            index++;
            count2--;
        }
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * APPROACH 2: DUTCH NATIONAL FLAG ALGORITHM (3-POINTER) - OPTIMAL SOLUTION
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * INTUITION:
 * ----------
 * Imagine you're organizing a line of people:
 * - People wearing RED shirts should go to the LEFT
 * - People wearing BLUE shirts should go to the RIGHT
 * - People wearing WHITE shirts stay in the MIDDLE
 * 
 * You use THREE pointers:
 * - LOW pointer: marks where the next 0 should go (left boundary)
 * - MID pointer: current element we're examining
 * - HIGH pointer: marks where the next 2 should go (right boundary)
 * 
 * VISUALIZATION:
 * --------------
 * Initial: [2, 0, 2, 1, 1, 0]
 *          ↑        ↑        ↑
 *         low      mid      high
 * 
 * The idea is to create three regions:
 * [0s region] [1s region] [unexplored] [2s region]
 *  ^           ^           ^           ^
 *  0          low         mid        high
 * 
 * ALGORITHM RULES:
 * ----------------
 * 1. If nums[mid] == 0 (RED):
 *    - Swap it with nums[low]
 *    - Move both low and mid forward
 *    - Why? Because we want all 0s at the beginning
 * 
 * 2. If nums[mid] == 1 (WHITE):
 *    - Just move mid forward
 *    - Why? 1s are in the correct middle position
 * 
 * 3. If nums[mid] == 2 (BLUE):
 *    - Swap it with nums[high]
 *    - Move high backward
 *    - DON'T move mid! We need to check what we swapped
 *    - Why? Because we want all 2s at the end
 * 
 * STEP-BY-STEP EXAMPLE:
 * ---------------------
 * Array: [2, 0, 2, 1, 1, 0]
 * 
 * Initial State:
 * [2, 0, 2, 1, 1, 0]
 *  ↑           ↑
 * low,mid     high
 * 
 * Step 1: nums[mid]=2, swap with nums[high]
 * [0, 0, 2, 1, 1, 2]
 *  ↑        ↑
 * low,mid  high
 * 
 * Step 2: nums[mid]=0, swap with nums[low], move both
 * [0, 0, 2, 1, 1, 2]
 *     ↑     ↑
 *    low   mid,high
 * 
 * And so on... until mid > high
 * 
 * TIME COMPLEXITY: O(n) - Single pass through the array
 * SPACE COMPLEXITY: O(1) - Only using 3 pointers
 */

public class SortColors_Leetcode75 {
    
    // APPROACH 2: DUTCH NATIONAL FLAG (3-POINTER) - OPTIMAL
    public void sortColors(int[] nums) {
        // Initialize three pointers
        int low = 0;          // Boundary for 0s (everything before low is 0)
        int mid = 0;          // Current element being examined
        int high = nums.length - 1;  // Boundary for 2s (everything after high is 2)
        
        // Keep going until mid crosses high
        while (mid <= high) {
            
            // CASE 1: Current element is 0 (RED)
            if (nums[mid] == 0) {
                // Swap current element with element at low
                swap(nums, low, mid);
                // Move both pointers forward
                low++;
                mid++;
            }
            
            // CASE 2: Current element is 1 (WHITE)
            else if (nums[mid] == 1) {
                // 1 is already in correct position (middle)
                // Just move to next element
                mid++;
            }
            
            // CASE 3: Current element is 2 (BLUE)
            else {
                // Swap current element with element at high
                swap(nums, mid, high);
                // Move high pointer backward
                high--;
                // DON'T move mid! We need to check what we just swapped
            }
        }
    }
    
    // Helper method to swap two elements in an array
    private void swap(int[] nums, int i, int j) {
        int temp = nums[i];
        nums[i] = nums[j];
        nums[j] = temp;
    }
    
    // Test the solution
    public static void main(String[] args) {
        SortColors_Leetcode75 solution = new SortColors_Leetcode75();
        
        // Test Case 1
        int[] nums1 = {2, 0, 2, 1, 1, 0};
        System.out.println("Before sorting: ");
        printArray(nums1);
        solution.sortColors(nums1);
        System.out.println("After sorting: ");
        printArray(nums1);
        
        System.out.println();
        
        // Test Case 2
        int[] nums2 = {2, 0, 1};
        System.out.println("Before sorting: ");
        printArray(nums2);
        solution.sortColors(nums2);
        System.out.println("After sorting: ");
        printArray(nums2);
    }
    
    // Helper method to print array
    private static void printArray(int[] nums) {
        System.out.print("[");
        for (int i = 0; i < nums.length; i++) {
            System.out.print(nums[i]);
            if (i < nums.length - 1) {
                System.out.print(", ");
            }
        }
        System.out.println("]");
    }
}

/*
 * ═══════════════════════════════════════════════════════════════════════════════
 * KEY TAKEAWAYS FOR BEGINNERS:
 * ═══════════════════════════════════════════════════════════════════════════════
 * 
 * 1. WHEN TO USE COUNTING SORT:
 *    - When you have a small range of values (like 0, 1, 2)
 *    - When you want simple, easy-to-understand code
 *    - Trade-off: Requires two passes through the array
 * 
 * 2. WHEN TO USE DUTCH NATIONAL FLAG:
 *    - When you need optimal O(n) time with single pass
 *    - In interviews, this shows advanced problem-solving
 *    - Trade-off: Slightly more complex logic
 * 
 * 3. COMMON MISTAKES TO AVOID:
 *    - In Dutch Flag: Don't increment mid when you swap with high
 *      (because you don't know what value you swapped)
 *    - Make sure to handle edge cases (empty array, single element)
 *    - Remember the loop condition is mid <= high (not mid < high)
 * 
 * 4. PRACTICE TIP:
 *    - Draw out the array on paper
 *    - Track low, mid, and high pointers at each step
 *    - Understand WHY each pointer moves (or doesn't move)
 * 
 * 5. INTERVIEW TIPS:
 *    - Start with counting sort if you're nervous
 *    - Then mention you know the optimal single-pass solution
 *    - Explain the three regions concept clearly
 */


