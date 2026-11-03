package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 337: House Robber III
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/house-robber-iii/
 * 
 * Problem: Max money by robbing nodes (no two adjacent nodes).
 * 
 * Example: Input: [3,2,3,null,3,null,1] → Output: 7
 */

public class HouseRobberIII_LeetCode337 {
    
    public int rob(TreeNode root) {
        int[] res = dfs(root); 
        return Math.max(res[0], res[1]);
    }
    
    // Returns [rob_current, skip_current]
    private int[] dfs(TreeNode n) {
        if (n == null) return new int[2];
        
        int[] L = dfs(n.left);
        int[] R = dfs(n.right);
        
        int rob = n.val + L[1] + R[1]; // Rob current + skip children
        int skip = Math.max(L[0], L[1]) + Math.max(R[0], R[1]); // Skip current + max from children
        
        return new int[]{rob, skip};
    }
    
    // Test method
    public static void main(String[] args) {
        HouseRobberIII_LeetCode337 solution = new HouseRobberIII_LeetCode337();
        
        // Test case: [3,2,3,null,3,null,1]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.right = new TreeNode(3);
        root.right.right = new TreeNode(1);
        
        System.out.println("Max rob amount: " + solution.rob(root)); // Expected: 7
    }
}

