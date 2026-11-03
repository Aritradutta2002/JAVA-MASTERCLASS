package dsa.algorithms.greedy.arrays;

/**
 * Jump Game I & II (LeetCode 55, 45) - MEDIUM/HARD
 * FAANG Frequency: Very High (Amazon, Google, Facebook, Microsoft)
 * 
 * Problem: Determine if you can reach the last index / minimum jumps needed
 */
public class JumpGame {
    
    // Jump Game I - Can reach last index?
    public boolean canJump(int[] nums) {
        int maxReach = 0;
        
        for (int i = 0; i < nums.length; i++) {
            if (i > maxReach) return false;
            maxReach = Math.max(maxReach, i + nums[i]);
            if (maxReach >= nums.length - 1) return true;
        }
        
        return true;
    }
    
    // Jump Game II - Minimum jumps to reach last index
    public int jump(int[] nums) {
        int jumps = 0;
        int currentEnd = 0;
        int farthest = 0;
        
        for (int i = 0; i < nums.length - 1; i++) {
            farthest = Math.max(farthest, i + nums[i]);
            
            if (i == currentEnd) {
                jumps++;
                currentEnd = farthest;
                
                if (currentEnd >= nums.length - 1) {
                    break;
                }
            }
        }
        
        return jumps;
    }
    
    // Jump Game III - Can reach any index with value 0?
    public boolean canReach(int[] arr, int start) {
        if (start < 0 || start >= arr.length || arr[start] < 0) {
            return false;
        }
        
        if (arr[start] == 0) return true;
        
        arr[start] = -arr[start]; // Mark as visited
        
        return canReach(arr, start + arr[start]) || 
               canReach(arr, start - arr[start]);
    }
    
    public static void main(String[] args) {
        JumpGame solution = new JumpGame();
        
        // Test Case 1
        int[] nums1 = {2, 3, 1, 1, 4};
        System.out.println(solution.canJump(nums1)); // true
        System.out.println(solution.jump(nums1)); // 2
        
        // Test Case 2
        int[] nums2 = {3, 2, 1, 0, 4};
        System.out.println(solution.canJump(nums2)); // false
    }
}

