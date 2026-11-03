package dsa.techniques.monotonicstructures;

import java.util.*;

/**
 * Next Greater Element I (LeetCode 496) - EASY
 * FAANG Frequency: High (Amazon, Google)
 * 
 * Problem: Find next greater element for each element in nums1 from nums2
 * Time: O(n), Space: O(n)
 */
public class NextGreaterElement {
    
    // Approach 1: Using Monotonic Stack
    public int[] nextGreaterElement(int[] nums1, int[] nums2) {
        Map<Integer, Integer> map = new HashMap<>();
        Stack<Integer> stack = new Stack<>();
        
        // Build next greater map for nums2
        for (int num : nums2) {
            while (!stack.isEmpty() && stack.peek() < num) {
                map.put(stack.pop(), num);
            }
            stack.push(num);
        }
        
        // Build result for nums1
        int[] result = new int[nums1.length];
        for (int i = 0; i < nums1.length; i++) {
            result[i] = map.getOrDefault(nums1[i], -1);
        }
        
        return result;
    }
    
    // Next Greater Element II (Circular Array) - LeetCode 503
    public int[] nextGreaterElements(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Stack<Integer> stack = new Stack<>();
        
        // Traverse twice to handle circular nature
        for (int i = 0; i < 2 * n; i++) {
            int num = nums[i % n];
            while (!stack.isEmpty() && nums[stack.peek()] < num) {
                result[stack.pop()] = num;
            }
            if (i < n) {
                stack.push(i);
            }
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        NextGreaterElement nge = new NextGreaterElement();
        
        // Test Case 1
        int[] nums1 = {4, 1, 2};
        int[] nums2 = {1, 3, 4, 2};
        System.out.println("Next Greater Elements: " + Arrays.toString(nge.nextGreaterElement(nums1, nums2)));
        
        // Test Case 2: Circular
        int[] nums = {1, 2, 1};
        System.out.println("Next Greater (Circular): " + Arrays.toString(nge.nextGreaterElements(nums)));
    }
}

