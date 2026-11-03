package dsa.algorithms.searching.binary.basics_TREES;

/*
 *   Author  : Aritra Dutta
 *   Problem : Delete Node in a BST (Leetcode 450)
 *   Difficulty: Medium
 *   Companies: Amazon, Microsoft, Facebook, Google, Apple
 */
public class DeleteNodeBST_Leetcode450 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(3);
        root.right = new TreeNode(6);
        root.left.left = new TreeNode(2);
        root.left.right = new TreeNode(4);
        root.right.right = new TreeNode(7);
        
        System.out.println("Deleting node 3...");
        root = deleteNode(root, 3);
        inorder(root);
    }
    
    public static TreeNode deleteNode(TreeNode root, int key) {
        if (root == null) return null;
        
        if (key < root.val) {
            root.left = deleteNode(root.left, key);
        } else if (key > root.val) {
            root.right = deleteNode(root.right, key);
        } else {
            // Node with only one child or no child
            if (root.left == null) return root.right;
            if (root.right == null) return root.left;
            
            // Node with two children: Get inorder successor
            TreeNode minNode = findMin(root.right);
            root.val = minNode.val;
            root.right = deleteNode(root.right, root.val);
        }
        
        return root;
    }
    
    private static TreeNode findMin(TreeNode node) {
        while (node.left != null) {
            node = node.left;
        }
        return node;
    }
    
    private static void inorder(TreeNode root) {
        if (root == null) return;
        inorder(root.left);
        System.out.print(root.val + " ");
        inorder(root.right);
    }
}

