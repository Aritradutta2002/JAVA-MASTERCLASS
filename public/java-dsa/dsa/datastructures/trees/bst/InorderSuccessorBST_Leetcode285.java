package dsa.algorithms.searching.binary.basics_TREES;

/*
 *   Author  : Aritra Dutta
 *   Problem : Inorder Successor in BST (Leetcode 285)
 *   Difficulty: Medium
 *   Companies: Facebook, Microsoft, Amazon, Google
 */
public class InorderSuccessorBST_Leetcode285 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(3);
        root.right = new TreeNode(6);
        root.left.left = new TreeNode(2);
        root.left.right = new TreeNode(4);
        root.left.left.left = new TreeNode(1);
        
        TreeNode p = root.left;
        TreeNode successor = inorderSuccessor(root, p);
        System.out.println("Inorder Successor of " + p.val + " is: " + 
                          (successor != null ? successor.val : "null"));
    }
    
    public static TreeNode inorderSuccessor(TreeNode root, TreeNode p) {
        TreeNode successor = null;
        
        while (root != null) {
            if (p.val >= root.val) {
                root = root.right;
            } else {
                successor = root;
                root = root.left;
            }
        }
        
        return successor;
    }
}

