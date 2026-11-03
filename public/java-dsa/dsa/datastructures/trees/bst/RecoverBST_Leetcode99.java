package dsa.algorithms.searching.binary.basics_TREES;

/*
 *   Author  : Aritra Dutta
 *   Problem : Recover Binary Search Tree (Leetcode 99)
 *   Difficulty: Medium
 *   Companies: Amazon, Microsoft, Google
 */
public class RecoverBST_Leetcode99 {
    static TreeNode first = null;
    static TreeNode second = null;
    static TreeNode prev = new TreeNode(Integer.MIN_VALUE);
    
    public static void main(String[] args) {
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(1);
        root.right = new TreeNode(4);
        root.right.left = new TreeNode(2);
        
        System.out.println("Before recovery:");
        inorder(root);
        
        recoverTree(root);
        
        System.out.println("\nAfter recovery:");
        inorder(root);
    }
    
    public static void recoverTree(TreeNode root) {
        first = null;
        second = null;
        prev = new TreeNode(Integer.MIN_VALUE);
        
        traverse(root);
        
        // Swap the values
        int temp = first.val;
        first.val = second.val;
        second.val = temp;
    }
    
    private static void traverse(TreeNode root) {
        if (root == null) return;
        
        traverse(root.left);
        
        if (first == null && prev.val > root.val) {
            first = prev;
        }
        if (first != null && prev.val > root.val) {
            second = root;
        }
        prev = root;
        
        traverse(root.right);
    }
    
    private static void inorder(TreeNode root) {
        if (root == null) return;
        inorder(root.left);
        System.out.print(root.val + " ");
        inorder(root.right);
    }
}

