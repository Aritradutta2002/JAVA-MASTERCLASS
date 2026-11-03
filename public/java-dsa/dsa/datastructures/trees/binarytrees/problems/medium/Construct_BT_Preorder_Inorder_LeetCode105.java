package dsa.datastructures.trees.binarytrees.problems.medium;

import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 105: Construct Binary Tree from Preorder and Inorder Traversal
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/construct-binary-tree-from-preorder-and-inorder-traversal/
 * 
 * Problem: Given preorder and inorder traversal arrays, build the binary tree.
 * 
 * Example: Input: preorder=[3,9,20,15,7], inorder=[9,3,15,20,7] → Output: [3,9,20,null,null,15,7]
 */

public class Construct_BT_Preorder_Inorder_LeetCode105 {
    
    int preIdx = 0;
    int[] preorder;
    Map<Integer, Integer> idxMap;
    
    public TreeNode buildTree(int[] preorder, int[] inorder) {
        this.preorder = preorder;
        this.preIdx = 0;
        idxMap = new HashMap<>();
        
        // Build hashmap for quick inorder index lookup
        for (int i = 0; i < inorder.length; i++) {
            idxMap.put(inorder[i], i);
        }
        
        return helper(0, inorder.length - 1);
    }
    
    private TreeNode helper(int left, int right) {
        if (left > right) return null;
        
        int rootVal = preorder[preIdx++];
        TreeNode root = new TreeNode(rootVal);
        
        int index = idxMap.get(rootVal);
        
        // Build left subtree first (as per preorder)
        root.left = helper(left, index - 1);
        root.right = helper(index + 1, right);
        
        return root;
    }
    
    // Helper method to print inorder traversal for verification
    public void printInorder(TreeNode root) {
        if (root != null) {
            printInorder(root.left);
            System.out.print(root.val + " ");
            printInorder(root.right);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Construct_BT_Preorder_Inorder_LeetCode105 solution = new Construct_BT_Preorder_Inorder_LeetCode105();
        
        // Test case: preorder=[3,9,20,15,7], inorder=[9,3,15,20,7]
        int[] preorder = {3, 9, 20, 15, 7};
        int[] inorder = {9, 3, 15, 20, 7};
        
        TreeNode root = solution.buildTree(preorder, inorder);
        
        System.out.print("Constructed tree (inorder): ");
        solution.printInorder(root);
        System.out.println();
        // Expected: 9 3 15 20 7
    }
}

