package dsa.techniques.monotonicstructures;
/**
 * MONOTONIC STACK/QUEUE PATTERNS
 * 
 * Companies: Amazon, Google, Microsoft, Facebook
 * 
 * Monotonic Stack: Stack where elements are always in increasing/decreasing order
 * 
 * Use Cases:
 * - Next/Previous Greater Element
 * - Next/Previous Smaller Element  
 * - Largest Rectangle in Histogram
 * - Trapping Rain Water
 * - Stock Span Problem
 * 
 * Time: O(n) - Each element pushed and popped once
 * Space: O(n)
 */

import java.util.*;

public class MonotonicStackPatterns {
    
    /**
     * PATTERN 1: NEXT GREATER ELEMENT
     * For each element, find next element that is greater
     */
    public int[] nextGreaterElement(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Stack<Integer> stack = new Stack<>(); // Store indices
        
        for (int i = 0; i < n; i++) {
            // Pop smaller elements
            while (!stack.isEmpty() && nums[i] > nums[stack.peek()]) {
                result[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        
        return result;
    }
    
    /**
     * PATTERN 2: NEXT SMALLER ELEMENT
     */
    public int[] nextSmallerElement(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Stack<Integer> stack = new Stack<>();
        
        for (int i = 0; i < n; i++) {
            // Pop larger elements
            while (!stack.isEmpty() && nums[i] < nums[stack.peek()]) {
                result[stack.pop()] = nums[i];
            }
            stack.push(i);
        }
        
        return result;
    }
    
    /**
     * PATTERN 3: PREVIOUS GREATER ELEMENT
     */
    public int[] previousGreaterElement(int[] nums) {
        int n = nums.length;
        int[] result = new int[n];
        Arrays.fill(result, -1);
        Stack<Integer> stack = new Stack<>();
        
        for (int i = 0; i < n; i++) {
            // Maintain decreasing stack
            while (!stack.isEmpty() && nums[stack.peek()] <= nums[i]) {
                stack.pop();
            }
            
            if (!stack.isEmpty()) {
                result[i] = nums[stack.peek()];
            }
            
            stack.push(i);
        }
        
        return result;
    }
    
    /**
     * DAILY TEMPERATURES
     * LeetCode #739 - Medium
     * Return days until warmer temperature
     */
    public int[] dailyTemperatures(int[] temperatures) {
        int n = temperatures.length;
        int[] result = new int[n];
        Stack<Integer> stack = new Stack<>();
        
        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && temperatures[i] > temperatures[stack.peek()]) {
                int idx = stack.pop();
                result[idx] = i - idx;
            }
            stack.push(i);
        }
        
        return result;
    }
    
    /**
     * LARGEST RECTANGLE IN HISTOGRAM
     * LeetCode #84 - Hard
     */
    public int largestRectangleArea(int[] heights) {
        Stack<Integer> stack = new Stack<>();
        int maxArea = 0;
        int n = heights.length;
        
        for (int i = 0; i <= n; i++) {
            int h = (i == n) ? 0 : heights[i];
            
            while (!stack.isEmpty() && h < heights[stack.peek()]) {
                int height = heights[stack.pop()];
                int width = stack.isEmpty() ? i : i - stack.peek() - 1;
                maxArea = Math.max(maxArea, height * width);
            }
            
            stack.push(i);
        }
        
        return maxArea;
    }
    
    /**
     * SLIDING WINDOW MAXIMUM
     * LeetCode #239 - Hard
     * Uses Monotonic Deque
     */
    public int[] maxSlidingWindow(int[] nums, int k) {
        if (nums.length == 0) return new int[0];
        
        int[] result = new int[nums.length - k + 1];
        Deque<Integer> deque = new ArrayDeque<>(); // Store indices
        
        for (int i = 0; i < nums.length; i++) {
            // Remove elements outside window
            while (!deque.isEmpty() && deque.peekFirst() < i - k + 1) {
                deque.pollFirst();
            }
            
            // Remove smaller elements (maintain decreasing order)
            while (!deque.isEmpty() && nums[i] > nums[deque.peekLast()]) {
                deque.pollLast();
            }
            
            deque.offerLast(i);
            
            // Add to result once window is full
            if (i >= k - 1) {
                result[i - k + 1] = nums[deque.peekFirst()];
            }
        }
        
        return result;
    }
    
    /**
     * TRAPPING RAIN WATER (Using Monotonic Stack)
     * LeetCode #42 - Hard
     */
    public int trap(int[] height) {
        Stack<Integer> stack = new Stack<>();
        int water = 0;
        
        for (int i = 0; i < height.length; i++) {
            while (!stack.isEmpty() && height[i] > height[stack.peek()]) {
                int bottom = stack.pop();
                
                if (stack.isEmpty()) break;
                
                int left = stack.peek();
                int width = i - left - 1;
                int boundedHeight = Math.min(height[i], height[left]) - height[bottom];
                water += width * boundedHeight;
            }
            
            stack.push(i);
        }
        
        return water;
    }
    
    /**
     * STOCK SPAN PROBLEM
     * For each day, find span (consecutive days with price <= current)
     */
    public int[] stockSpan(int[] prices) {
        int n = prices.length;
        int[] span = new int[n];
        Stack<Integer> stack = new Stack<>();
        
        for (int i = 0; i < n; i++) {
            // Pop days with smaller or equal prices
            while (!stack.isEmpty() && prices[stack.peek()] <= prices[i]) {
                stack.pop();
            }
            
            span[i] = stack.isEmpty() ? (i + 1) : (i - stack.peek());
            stack.push(i);
        }
        
        return span;
    }
    
    /**
     * SUM OF SUBARRAY MINIMUMS
     * LeetCode #907 - Medium
     */
    public int sumSubarrayMins(int[] arr) {
        int MOD = 1000000007;
        int n = arr.length;
        long result = 0;
        
        // For each element, find range where it's minimum
        int[] left = new int[n];   // Distance to previous smaller
        int[] right = new int[n];  // Distance to next smaller
        
        Stack<Integer> stack = new Stack<>();
        
        // Previous smaller elements
        for (int i = 0; i < n; i++) {
            while (!stack.isEmpty() && arr[stack.peek()] > arr[i]) {
                stack.pop();
            }
            left[i] = stack.isEmpty() ? i + 1 : i - stack.peek();
            stack.push(i);
        }
        
        stack.clear();
        
        // Next smaller elements
        for (int i = n - 1; i >= 0; i--) {
            while (!stack.isEmpty() && arr[stack.peek()] >= arr[i]) {
                stack.pop();
            }
            right[i] = stack.isEmpty() ? n - i : stack.peek() - i;
            stack.push(i);
        }
        
        // Calculate sum
        for (int i = 0; i < n; i++) {
            result = (result + (long) arr[i] * left[i] * right[i]) % MOD;
        }
        
        return (int) result;
    }
    
    public static void main(String[] args) {
        MonotonicStackPatterns solution = new MonotonicStackPatterns();
        
        // Test 1: Next Greater Element
        System.out.println("=== Next Greater Element ===");
        int[] arr1 = {4, 5, 2, 10, 8};
        System.out.println("Input: " + Arrays.toString(arr1));
        System.out.println("Output: " + Arrays.toString(solution.nextGreaterElement(arr1)));
        
        // Test 2: Daily Temperatures
        System.out.println("\n=== Daily Temperatures ===");
        int[] temps = {73, 74, 75, 71, 69, 72, 76, 73};
        System.out.println("Input: " + Arrays.toString(temps));
        System.out.println("Output: " + Arrays.toString(solution.dailyTemperatures(temps)));
        
        // Test 3: Largest Rectangle
        System.out.println("\n=== Largest Rectangle ===");
        int[] heights = {2, 1, 5, 6, 2, 3};
        System.out.println("Heights: " + Arrays.toString(heights));
        System.out.println("Max Area: " + solution.largestRectangleArea(heights));
        
        // Test 4: Sliding Window Maximum
        System.out.println("\n=== Sliding Window Maximum ===");
        int[] nums = {1, 3, -1, -3, 5, 3, 6, 7};
        System.out.println("Input: " + Arrays.toString(nums) + ", k=3");
        System.out.println("Output: " + Arrays.toString(solution.maxSlidingWindow(nums, 3)));
        
        // Test 5: Trapping Rain Water
        System.out.println("\n=== Trapping Rain Water ===");
        int[] waterHeights = {0, 1, 0, 2, 1, 0, 1, 3, 2, 1, 2, 1};
        System.out.println("Heights: " + Arrays.toString(waterHeights));
        System.out.println("Water trapped: " + solution.trap(waterHeights));
        
        // Interview Tips
        System.out.println("\n=== INTERVIEW TIPS ===");
        System.out.println("1. Monotonic stack maintains increasing/decreasing order");
        System.out.println("2. For next greater: pop smaller, for next smaller: pop larger");
        System.out.println("3. Store indices, not values (need position info)");
        System.out.println("4. Each element pushed/popped once = O(n) time");
        System.out.println("5. Pattern: while(condition) pop, then push current");
        System.out.println("6. Deque for sliding window (need remove from both ends)");
    }
}

