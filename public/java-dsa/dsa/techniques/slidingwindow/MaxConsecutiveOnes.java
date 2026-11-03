package dsa.techniques.slidingwindow;

/**
 * Max Consecutive Ones III (LeetCode 1004) - MEDIUM
 * FAANG Frequency: High (Amazon, Google, Facebook)
 * 
 * Problem: Find max consecutive 1s after flipping at most k zeros
 * Time: O(n), Space: O(1)
 * 
 * Pattern: Shrinkable sliding window
 */
public class MaxConsecutiveOnes {
    
    // Max Consecutive Ones III - Can flip at most k zeros
    public int longestOnes(int[] nums, int k) {
        int left = 0, maxLen = 0, zeros = 0;
        
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] == 0) {
                zeros++;
            }
            
            // Shrink window when we have more than k zeros
            while (zeros > k) {
                if (nums[left] == 0) {
                    zeros--;
                }
                left++;
            }
            
            maxLen = Math.max(maxLen, right - left + 1);
        }
        
        return maxLen;
    }
    
    // Max Consecutive Ones II (LeetCode 487) - Can flip at most 1 zero
    public int findMaxConsecutiveOnesII(int[] nums) {
        return longestOnes(nums, 1);
    }
    
    // Max Consecutive Ones I (LeetCode 485) - Cannot flip any zero
    public int findMaxConsecutiveOnes(int[] nums) {
        int maxLen = 0, currentLen = 0;
        
        for (int num : nums) {
            if (num == 1) {
                currentLen++;
                maxLen = Math.max(maxLen, currentLen);
            } else {
                currentLen = 0;
            }
        }
        
        return maxLen;
    }
    
    // Alternative: Using non-shrinking window
    public int longestOnesNonShrinking(int[] nums, int k) {
        int left = 0, zeros = 0;
        
        for (int right = 0; right < nums.length; right++) {
            if (nums[right] == 0) {
                zeros++;
            }
            
            // Move left pointer only when necessary
            if (zeros > k) {
                if (nums[left] == 0) {
                    zeros--;
                }
                left++;
            }
        }
        
        return nums.length - left;
    }
    
    public static void main(String[] args) {
        MaxConsecutiveOnes solution = new MaxConsecutiveOnes();
        
        // Test Case 1
        int[] nums1 = {1, 1, 1, 0, 0, 0, 1, 1, 1, 1, 0};
        System.out.println(solution.longestOnes(nums1, 2)); // 6
        
        // Test Case 2
        int[] nums2 = {0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 0, 0, 0, 1, 1, 1, 1};
        System.out.println(solution.longestOnes(nums2, 3)); // 10
        
        // Test Case 3
        int[] nums3 = {1, 1, 0, 1};
        System.out.println(solution.findMaxConsecutiveOnesII(nums3)); // 4
    }
}

