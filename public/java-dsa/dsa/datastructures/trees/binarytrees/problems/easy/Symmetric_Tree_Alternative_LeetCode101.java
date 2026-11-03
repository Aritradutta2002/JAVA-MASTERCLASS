package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 101: Symmetric Tree
 * Difficulty: Easy
 * Link: https://leetcode.com/problems/symmetric-tree/
 * 
 * Problem: Check whether a binary tree is symmetric (mirror of itself).
 * 
 * Example: Input: [1,2,2,3,4,4,3] → Output: true
 */

public class Symmetric_Tree_Alternative_LeetCode101 {
    
    public boolean isSymmetric(TreeNode root) {
        return root == null || mirror(root.left, root.right);
    }
    
    private boolean mirror(TreeNode a, TreeNode b) {
        if (a == null || b == null) return a == b;
        return a.val == b.val && mirror(a.left, b.right) && mirror(a.right, b.left);
    }
    
    // Test method
    public static void main(String[] args) {
        Symmetric_Tree_Alternative_LeetCode101 solution = new Symmetric_Tree_Alternative_LeetCode101();
        
        // Test case: [1,2,2,3,4,4,3]
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(2);
        root.left.left = new TreeNode(3);
        root.left.right = new TreeNode(4);
        root.right.left = new TreeNode(4);
        root.right.right = new TreeNode(3);
        
        System.out.println("Is symmetric: " + solution.isSymmetric(root)); // Expected: true
    }
}

