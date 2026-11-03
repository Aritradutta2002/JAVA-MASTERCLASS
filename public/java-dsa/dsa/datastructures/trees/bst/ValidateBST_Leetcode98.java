package dsa.algorithms.searching.binary.basics_TREES;

/*
 *   Author  : Aritra Dutta
 *   Problem : Validate Binary Search Tree (Leetcode 98)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Microsoft, Google, Apple
 */
public class ValidateBST_Leetcode98 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(1);
        root.right = new TreeNode(4);
        root.right.left = new TreeNode(3);
        root.right.right = new TreeNode(6);
        
        System.out.println("Is Valid BST: " + isValidBST(root));
    }
    
    public static boolean isValidBST(TreeNode root) {
        return validate(root, null, null);
    }
    
    private static boolean validate(TreeNode node, Integer min, Integer max) {
        if (node == null) return true;
        
        if ((min != null && node.val <= min) || (max != null && node.val >= max)) {
            return false;
        }
        
        return validate(node.left, min, node.val) && validate(node.right, node.val, max);
    }
}

