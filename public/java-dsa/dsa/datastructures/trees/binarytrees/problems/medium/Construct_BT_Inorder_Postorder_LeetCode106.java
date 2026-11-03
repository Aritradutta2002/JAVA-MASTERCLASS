package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;


import java.util.*;

/*
 * LeetCode 106: Construct Binary Tree from Inorder and Postorder Traversal
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/construct-binary-tree-from-inorder-and-postorder-traversal/
 * 
 * Problem: Build tree from inorder and postorder arrays.
 * 
 * Example: Input: inorder=[9,3,15,20,7], postorder=[9,15,7,20,3] → Output: [3,9,20,null,null,15,7]
 */

public class Construct_BT_Inorder_Postorder_LeetCode106 {
    
    int postIdx;
    int[] post;
    Map<Integer, Integer> idxMap;
    
    public TreeNode buildTree(int[] inorder, int[] postorder) {
        post = postorder;
        postIdx = post.length - 1;
        idxMap = new HashMap<>();
        
        // Build hashmap for quick inorder index lookup
        for (int i = 0; i < inorder.length; i++) {
            idxMap.put(inorder[i], i);
        }
        
        return helper(0, inorder.length - 1);
    }
    
    private TreeNode helper(int left, int right) {
        if (left > right) return null;
        
        int val = post[postIdx--];
        TreeNode root = new TreeNode(val);
        int idx = idxMap.get(val);
        
        // Build right subtree first (as per postorder)
        root.right = helper(idx + 1, right);
        root.left = helper(left, idx - 1);
        
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
        Construct_BT_Inorder_Postorder_LeetCode106 solution = new Construct_BT_Inorder_Postorder_LeetCode106();
        
        // Test case: inorder=[9,3,15,20,7], postorder=[9,15,7,20,3]
        int[] inorder = {9, 3, 15, 20, 7};
        int[] postorder = {9, 15, 7, 20, 3};
        
        TreeNode root = solution.buildTree(inorder, postorder);
        
        System.out.print("Constructed tree (inorder): ");
        solution.printInorder(root);
        System.out.println();
        // Expected: 9 3 15 20 7
    }
}

