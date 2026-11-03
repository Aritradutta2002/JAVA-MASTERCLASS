package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 98: Validate Binary Search Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/validate-binary-search-tree/
 * 
 * Problem: Determine if a given binary tree is a valid BST.
 * 
 * Example: Input: [2,1,3] → Output: true
 */

public class Validate_Binary_Search_Tree_LeetCode98 {
    
    public boolean isValidBST(TreeNode root) {
        return validate(root, Long.MIN_VALUE, Long.MAX_VALUE);
    }
    
    private boolean validate(TreeNode node, long minVal, long maxVal) {
        if (node == null) return true;
        
        if (node.val <= minVal || node.val >= maxVal) return false;
        
        return validate(node.left, minVal, node.val) && 
               validate(node.right, node.val, maxVal);
    }
    
    // Alternative: In-order traversal approach
    private TreeNode prev = null;
    
    public boolean isValidBSTInorder(TreeNode root) {
        return inorder(root);
    }
    
    private boolean inorder(TreeNode node) {
        if (node == null) return true;
        
        if (!inorder(node.left)) return false;
        
        if (prev != null && prev.val >= node.val) return false;
        prev = node;
        
        return inorder(node.right);
    }
    
    // Test method
    public static void main(String[] args) {
        Validate_Binary_Search_Tree_LeetCode98 solution = new Validate_Binary_Search_Tree_LeetCode98();
        
        // Test case 1: [2,1,3] - Valid BST
        TreeNode root1 = new TreeNode(2);
        root1.left = new TreeNode(1);
        root1.right = new TreeNode(3);
        
        System.out.println("Is valid BST: " + solution.isValidBST(root1)); // Expected: true
        
        // Test case 2: [5,1,4,null,null,3,6] - Invalid BST
        TreeNode root2 = new TreeNode(5);
        root2.left = new TreeNode(1);
        root2.right = new TreeNode(4);
        root2.right.left = new TreeNode(3);
        root2.right.right = new TreeNode(6);
        
        System.out.println("Is valid BST: " + solution.isValidBST(root2)); // Expected: false
    }
}

