package dsa.techniques.slidingwindow;

/**
 * Minimum Size Subarray Sum (LeetCode 209) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Find minimal length subarray with sum >= target
 * Time: O(n), Space: O(1)
 */
public class MinimumSizeSubarraySum {
    
    public int minSubArrayLen(int target, int[] nums) {
        int left = 0, sum = 0;
        int minLen = Integer.MAX_VALUE;
        
        for (int right = 0; right < nums.length; right++) {
            sum += nums[right];
            
            // Shrink window while condition is met
            while (sum >= target) {
                minLen = Math.max(minLen, right - left + 1);
                sum -= nums[left];
                left++;
            }
        }
        
        return minLen == Integer.MAX_VALUE ? 0 : minLen;
    }
    
    // Follow-up: Binary Search approach - O(n log n)
    public int minSubArrayLenBinarySearch(int target, int[] nums) {
        int n = nums.length;
        int[] prefixSum = new int[n + 1];
        
        // Build prefix sum
        for (int i = 0; i < n; i++) {
            prefixSum[i + 1] = prefixSum[i] + nums[i];
        }
        
        int minLen = Integer.MAX_VALUE;
        
        for (int i = 0; i < n; i++) {
            int toFind = target + prefixSum[i];
            int bound = binarySearch(prefixSum, toFind);
            
            if (bound != -1) {
                minLen = Math.min(minLen, bound - i);
            }
        }
        
        return minLen == Integer.MAX_VALUE ? 0 : minLen;
    }
    
    private int binarySearch(int[] arr, int target) {
        int left = 0, right = arr.length - 1;
        int result = -1;
        
        while (left <= right) {
            int mid = left + (right - left) / 2;
            
            if (arr[mid] >= target) {
                result = mid;
                right = mid - 1;
            } else {
                left = mid + 1;
            }
        }
        
        return result;
    }
    
    public static void main(String[] args) {
        MinimumSizeSubarraySum solution = new MinimumSizeSubarraySum();
        
        // Test Case 1
        int[] nums1 = {2, 3, 1, 2, 4, 3};
        System.out.println(solution.minSubArrayLen(7, nums1)); // 2
        
        // Test Case 2
        int[] nums2 = {1, 4, 4};
        System.out.println(solution.minSubArrayLen(4, nums2)); // 1
        
        // Test Case 3
        int[] nums3 = {1, 1, 1, 1, 1, 1, 1, 1};
        System.out.println(solution.minSubArrayLen(11, nums3)); // 0
    }
}

