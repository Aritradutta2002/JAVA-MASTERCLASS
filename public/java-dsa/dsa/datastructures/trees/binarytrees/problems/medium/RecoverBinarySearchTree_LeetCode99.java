package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 99: Recover Binary Search Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/recover-binary-search-tree/
 * 
 * Problem: Two nodes of a BST are swapped by mistake; recover the tree without changing its structure.
 * 
 * Example: Input: [1,3,null,null,2] → Output: [3,1,null,null,2]
 */

public class RecoverBinarySearchTree_LeetCode99 {
    
    TreeNode first, second, prev;
    
    public void recoverTree(TreeNode root) {
        first = second = prev = null;
        prev = new TreeNode(Integer.MIN_VALUE);
        
        inorder(root);
        
        // Swap the values of the two nodes
        int tmp = first.val;
        first.val = second.val;
        second.val = tmp;
    }
    
    private void inorder(TreeNode node) {
        if (node == null) return;
        
        inorder(node.left);
        
        // Process current node
        if (first == null && prev.val > node.val) {
            first = prev; // First violation
        }
        if (first != null && prev.val > node.val) {
            second = node; // Second violation
        }
        prev = node;
        
        inorder(node.right);
    }
    
    // Morris traversal approach (O(1) space)
    public void recoverTreeMorris(TreeNode root) {
        TreeNode first = null, second = null, prev = null;
        TreeNode predecessor = null;
        
        while (root != null) {
            if (root.left != null) {
                // Find predecessor
                predecessor = root.left;
                while (predecessor.right != null && predecessor.right != root) {
                    predecessor = predecessor.right;
                }
                
                if (predecessor.right == null) {
                    // Make threading
                    predecessor.right = root;
                    root = root.left;
                } else {
                    // Process node and remove threading
                    if (prev != null && prev.val > root.val) {
                        if (first == null) first = prev;
                        second = root;
                    }
                    prev = root;
                    predecessor.right = null;
                    root = root.right;
                }
            } else {
                // Process node
                if (prev != null && prev.val > root.val) {
                    if (first == null) first = prev;
                    second = root;
                }
                prev = root;
                root = root.right;
            }
        }
        
        // Swap values
        int tmp = first.val;
        first.val = second.val;
        second.val = tmp;
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
        RecoverBinarySearchTree_LeetCode99 solution = new RecoverBinarySearchTree_LeetCode99();
        
        // Test case: [1,3,null,null,2] (nodes 1 and 3 are swapped)
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(3);
        root.left.right = new TreeNode(2);
        
        System.out.print("Before recovery (inorder): ");
        solution.printInorder(root);
        System.out.println();
        
        solution.recoverTree(root);
        
        System.out.print("After recovery (inorder): ");
        solution.printInorder(root);
        System.out.println();
        // Expected: 1 2 3 (correct BST order)
    }
}

