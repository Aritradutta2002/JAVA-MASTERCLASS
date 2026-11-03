package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 226: Invert Binary Tree
 * Difficulty: Easy
 * Link: https://leetcode.com/problems/invert-binary-tree/
 * 
 * Problem: Invert a binary tree (mirror it).
 * 
 * Example: Input: [4,2,7,1,3,6,9] → Output: [4,7,2,9,6,3,1]
 */

public class Invert_Binary_Tree_LeetCode226 {
    
    // Recursive approach
    public TreeNode invertTree(TreeNode root) {
        if (root == null) return null;
        
        // Swap left and right children
        TreeNode temp = root.left;
        root.left = root.right;
        root.right = temp;
        
        // Recursively invert subtrees
        invertTree(root.left);
        invertTree(root.right);
        
        return root;
    }
    
    // Iterative approach using queue (BFS)
    public TreeNode invertTreeIterative(TreeNode root) {
        if (root == null) return null;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            
            // Swap left and right children
            TreeNode temp = node.left;
            node.left = node.right;
            node.right = temp;
            
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        
        return root;
    }
    
    // Helper method to print level order traversal
    public void printLevelOrder(TreeNode root) {
        if (root == null) return;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            TreeNode node = queue.poll();
            System.out.print(node.val + " ");
            
            if (node.left != null) queue.offer(node.left);
            if (node.right != null) queue.offer(node.right);
        }
        System.out.println();
    }
    
    // Test method
    public static void main(String[] args) {
        Invert_Binary_Tree_LeetCode226 solution = new Invert_Binary_Tree_LeetCode226();
        
        // Test case: [4,2,7,1,3,6,9]
        TreeNode root = new TreeNode(4);
        root.left = new TreeNode(2);
        root.right = new TreeNode(7);
        root.left.left = new TreeNode(1);
        root.left.right = new TreeNode(3);
        root.right.left = new TreeNode(6);
        root.right.right = new TreeNode(9);
        
        System.out.print("Original tree (level order): ");
        solution.printLevelOrder(root);
        
        TreeNode inverted = solution.invertTree(root);
        
        System.out.print("Inverted tree (level order): ");
        solution.printLevelOrder(inverted);
        // Expected: 4 7 2 9 6 3 1
    }
}

