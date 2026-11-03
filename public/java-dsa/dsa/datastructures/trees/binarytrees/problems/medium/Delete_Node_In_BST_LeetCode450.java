package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 450: Delete Node in a BST
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/delete-node-in-a-bst/
 * 
 * Problem: Delete a node with given key from BST and return the root.
 * 
 * Example: Input: root = [5,3,6,2,4,null,7], key = 3 → Output: [5,4,6,2,null,null,7]
 */

public class Delete_Node_In_BST_LeetCode450 {
    
    public TreeNode deleteNode(TreeNode root, int key) {
        if (root == null) return null;
        
        if (key < root.val) {
            root.left = deleteNode(root.left, key);
        } else if (key > root.val) {
            root.right = deleteNode(root.right, key);
        } else {
            // Node to be deleted found
            
            // Case 1: No children (leaf node)
            if (root.left == null && root.right == null) {
                return null;
            }
            
            // Case 2: One child
            if (root.left == null) return root.right;
            if (root.right == null) return root.left;
            
            // Case 3: Two children
            // Find inorder successor (smallest in right subtree)
            TreeNode successor = findMin(root.right);
            root.val = successor.val;
            root.right = deleteNode(root.right, successor.val);
        }
        
        return root;
    }
    
    private TreeNode findMin(TreeNode node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }
    
    // Helper method to print in-order traversal
    public void inorderTraversal(TreeNode root) {
        if (root != null) {
            inorderTraversal(root.left);
            System.out.print(root.val + " ");
            inorderTraversal(root.right);
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Delete_Node_In_BST_LeetCode450 solution = new Delete_Node_In_BST_LeetCode450();
        
        // Test case: [5,3,6,2,4,null,7], delete key = 3
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(3);
        root.right = new TreeNode(6);
        root.left.left = new TreeNode(2);
        root.left.right = new TreeNode(4);
        root.right.right = new TreeNode(7);
        
        System.out.print("Original BST (inorder): ");
        solution.inorderTraversal(root);
        System.out.println();
        
        root = solution.deleteNode(root, 3);
        
        System.out.print("After deleting 3 (inorder): ");
        solution.inorderTraversal(root);
        System.out.println();
        // Expected: 2 4 5 6 7
    }
}

