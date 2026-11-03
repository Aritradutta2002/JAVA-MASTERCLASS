package dsa.datastructures.trees.binarytrees.problems.medium;

import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;
import java.util.*;

/*
 * LeetCode 889: Construct Binary Tree from Preorder and Postorder Traversal
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/construct-binary-tree-from-preorder-and-postorder-traversal/
 * 
 * Problem: Construct binary tree from preorder and postorder traversal arrays.
 * Note: The answer is not unique; any valid binary tree is acceptable.
 * 
 * Example: Input: preorder=[1,2,4,5,3,6,7], postorder=[4,5,2,6,7,3,1] → Output: [1,2,3,4,5,6,7]
 */

public class Construct_BT_Preorder_Postorder_LeetCode889 {
    
    int preIdx = 0;
    int postIdx = 0;
    
    public TreeNode constructFromPrePost(int[] preorder, int[] postorder) {
        preIdx = 0;
        postIdx = 0;
        return buildTree(preorder, postorder);
    }
    
    private TreeNode buildTree(int[] preorder, int[] postorder) {
        TreeNode root = new TreeNode(preorder[preIdx++]);
        
        if (root.val != postorder[postIdx]) {
            root.left = buildTree(preorder, postorder);
        }
        if (root.val != postorder[postIdx]) {
            root.right = buildTree(preorder, postorder);
        }
        
        postIdx++;
        return root;
    }
    
    // Alternative approach using hashmap for postorder indices
    public TreeNode constructFromPrePostHashMap(int[] preorder, int[] postorder) {
        Map<Integer, Integer> postMap = new HashMap<>();
        for (int i = 0; i < postorder.length; i++) {
            postMap.put(postorder[i], i);
        }
        return buildTreeWithMap(preorder, 0, preorder.length - 1, 
                               postorder, 0, postorder.length - 1, postMap);
    }
    
    private TreeNode buildTreeWithMap(int[] preorder, int preStart, int preEnd,
                                     int[] postorder, int postStart, int postEnd,
                                     Map<Integer, Integer> postMap) {
        if (preStart > preEnd || postStart > postEnd) return null;
        
        TreeNode root = new TreeNode(preorder[preStart]);
        
        if (preStart == preEnd) return root;
        
        // Find left child in preorder
        int leftChildVal = preorder[preStart + 1];
        int leftChildPostIdx = postMap.get(leftChildVal);
        int leftSubtreeSize = leftChildPostIdx - postStart + 1;
        
        root.left = buildTreeWithMap(preorder, preStart + 1, preStart + leftSubtreeSize,
                                    postorder, postStart, leftChildPostIdx, postMap);
        
        root.right = buildTreeWithMap(preorder, preStart + leftSubtreeSize + 1, preEnd,
                                     postorder, leftChildPostIdx + 1, postEnd - 1, postMap);
        
        return root;
    }
    
    // Stack-based approach
    public TreeNode constructFromPrePostStack(int[] preorder, int[] postorder) {
        Stack<TreeNode> stack = new Stack<>();
        TreeNode root = new TreeNode(preorder[0]);
        stack.push(root);
        
        int preIdx = 1, postIdx = 0;
        
        while (preIdx < preorder.length) {
            TreeNode node = new TreeNode(preorder[preIdx++]);
            TreeNode parent = null;
            
            while (!stack.isEmpty() && stack.peek().val == postorder[postIdx]) {
                parent = stack.pop();
                postIdx++;
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
    
    // Helper method to print preorder traversal
    public void printPreorder(TreeNode root) {
        if (root != null) {
            System.out.print(root.val + " ");
            printPreorder(root.left);
            printPreorder(root.right);
        }
    }
    
    // Helper method to print postorder traversal
    public void printPostorder(TreeNode root) {
        if (root != null) {
            printPostorder(root.left);
            printPostorder(root.right);
            System.out.print(root.val + " ");
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Construct_BT_Preorder_Postorder_LeetCode889 solution = new Construct_BT_Preorder_Postorder_LeetCode889();
        
        // Test case: preorder=[1,2,4,5,3,6,7], postorder=[4,5,2,6,7,3,1]
        int[] preorder = {1, 2, 4, 5, 3, 6, 7};
        int[] postorder = {4, 5, 2, 6, 7, 3, 1};
        
        TreeNode root = solution.constructFromPrePost(preorder, postorder);
        
        System.out.print("Constructed tree (preorder): ");
        solution.printPreorder(root);
        System.out.println();
        // Expected: 1 2 4 5 3 6 7
        
        System.out.print("Constructed tree (postorder): ");
        solution.printPostorder(root);
        System.out.println();
        // Expected: 4 5 2 6 7 3 1
        
        // Test alternative approach
        TreeNode root2 = solution.constructFromPrePostHashMap(preorder, postorder);
        System.out.print("Alternative approach (preorder): ");
        solution.printPreorder(root2);
        System.out.println();
    }
}


