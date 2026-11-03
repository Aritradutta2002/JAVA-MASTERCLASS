package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 1008: Construct Binary Search Tree from Preorder Traversal
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/construct-binary-search-tree-from-preorder-traversal/
 * 
 * Problem: Construct BST from its preorder traversal array.
 * 
 * Example: Input: preorder=[8,5,1,7,10,12] → Output: BST with root 8
 */

public class Construct_BST_From_Preorder_LeetCode1008 {
    
    // Approach 1: Using bounds (O(n) time, O(h) space)
    int idx = 0;
    
    public TreeNode bstFromPreorder(int[] preorder) {
        idx = 0;
        return build(preorder, Integer.MIN_VALUE, Integer.MAX_VALUE);
    }
    
    private TreeNode build(int[] preorder, int min, int max) {
        if (idx >= preorder.length) return null;
        
        int val = preorder[idx];
        if (val < min || val > max) return null;
        
        idx++;
        TreeNode root = new TreeNode(val);
        root.left = build(preorder, min, val);
        root.right = build(preorder, val, max);
        
        return root;
    }
    
    // Approach 2: Using stack (O(n) time, O(n) space)
    public TreeNode bstFromPreorderStack(int[] preorder) {
        if (preorder.length == 0) return null;
        
        TreeNode root = new TreeNode(preorder[0]);
        Stack<TreeNode> stack = new Stack<>();
        stack.push(root);
        
        for (int i = 1; i < preorder.length; i++) {
            TreeNode node = new TreeNode(preorder[i]);
            TreeNode parent = null;
            
            // Find the correct parent
            while (!stack.isEmpty() && stack.peek().val < preorder[i]) {
                parent = stack.pop();
            }
            
            if (parent != null) {
                parent.right = node;
            } else {
                stack.peek().left = node;
            }
            
            stack.push(node);
        }
        
        return root;
    }
    
    // Approach 3: Sort and use inorder + preorder construction
    public TreeNode bstFromPreorderSortInorder(int[] preorder) {
        int[] inorder = preorder.clone();
        Arrays.sort(inorder);
        return buildFromPreorderInorder(preorder, inorder);
    }
    
    private TreeNode buildFromPreorderInorder(int[] preorder, int[] inorder) {
        Map<Integer, Integer> inorderMap = new HashMap<>();
        for (int i = 0; i < inorder.length; i++) {
            inorderMap.put(inorder[i], i);
        }
        return buildHelper(preorder, 0, inorder.length - 1, inorderMap, new int[]{0});
    }
    
    private TreeNode buildHelper(int[] preorder, int left, int right, 
                                Map<Integer, Integer> inorderMap, int[] preIdx) {
        if (left > right) return null;
        
        int rootVal = preorder[preIdx[0]++];
        TreeNode root = new TreeNode(rootVal);
        int index = inorderMap.get(rootVal);
        
        root.left = buildHelper(preorder, left, index - 1, inorderMap, preIdx);
        root.right = buildHelper(preorder, index + 1, right, inorderMap, preIdx);
        
        return root;
    }
    
    // Helper method to print inorder traversal
    public void printInorder(TreeNode root) {
        if (root != null) {
            printInorder(root.left);
            System.out.print(root.val + " ");
            printInorder(root.right);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Construct_BST_From_Preorder_LeetCode1008 solution = new Construct_BST_From_Preorder_LeetCode1008();
        
        // Test case: [8,5,1,7,10,12]
        int[] preorder = {8, 5, 1, 7, 10, 12};
        
        TreeNode root1 = solution.bstFromPreorder(preorder);
        System.out.print("BST from preorder (bounds approach): ");
        solution.printInorder(root1);
        System.out.println();
        // Expected: 1 5 7 8 10 12 (sorted order)
        
        TreeNode root2 = solution.bstFromPreorderStack(preorder);
        System.out.print("BST from preorder (stack approach): ");
        solution.printInorder(root2);
        System.out.println();
        // Expected: 1 5 7 8 10 12 (sorted order)
    }
}


