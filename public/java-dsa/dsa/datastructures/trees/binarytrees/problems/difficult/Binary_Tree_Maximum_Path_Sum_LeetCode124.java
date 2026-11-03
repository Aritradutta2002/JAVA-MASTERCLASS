package dsa.datastructures.trees.binarytrees.problems.difficult;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 124: Binary Tree Maximum Path Sum
 * Difficulty: Hard
 * Link: https://leetcode.com/problems/binary-tree-maximum-path-sum/
 *
 * Problem: Find the maximum path sum of any non-empty path in a binary tree.
 * A path can start and end at any nodes in the tree.
 *
 * Example: Input: [1,2,3] → Output: 6 (path: 2 -> 1 -> 3)
 */


public class Binary_Tree_Maximum_Path_Sum_LeetCode124 {
    
    int ans = Integer.MIN_VALUE;
    
    public int maxPathSum(TreeNode root) {
        gain(root); 
        return ans;
    }
    
    private int gain(TreeNode n) {
        if (n == null) return 0;
        
        int l = Math.max(0, gain(n.left));  // Max gain from left subtree
        int r = Math.max(0, gain(n.right)); // Max gain from right subtree
        
        // Update global maximum with path through current node
        ans = Math.max(ans, n.val + l + r);
        
        // Return max gain when current node is part of a path to parent
        return n.val + Math.max(l, r);
    }
    
    // Test method
    public static void main(String[] args) {
        Binary_Tree_Maximum_Path_Sum_LeetCode124 solution = new Binary_Tree_Maximum_Path_Sum_LeetCode124();
        
        // Test case 1: [1,2,3]
        TreeNode root1 = new TreeNode(1);
        root1.left = new TreeNode(2);
        root1.right = new TreeNode(3);
        
        System.out.println("Max path sum: " + solution.maxPathSum(root1)); // Expected: 6
        
        // Reset for next test
        solution.ans = Integer.MIN_VALUE;
        
        // Test case 2: [-10,9,20,null,null,15,7]
        TreeNode root2 = new TreeNode(-10);
        root2.left = new TreeNode(9);
        root2.right = new TreeNode(20);
        root2.right.left = new TreeNode(15);
        root2.right.right = new TreeNode(7);
        
        System.out.println("Max path sum: " + solution.maxPathSum(root2)); // Expected: 42
    }
}

