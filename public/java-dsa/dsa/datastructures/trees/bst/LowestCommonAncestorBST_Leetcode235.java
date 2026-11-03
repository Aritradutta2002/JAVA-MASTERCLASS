package dsa.algorithms.searching.binary.basics_TREES;

/*
 *   Author  : Aritra Dutta
 *   Problem : Lowest Common Ancestor of a Binary Search Tree (Leetcode 235)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Microsoft, LinkedIn, Google
 */
public class LowestCommonAncestorBST_Leetcode235 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(6);
        root.left = new TreeNode(2);
        root.right = new TreeNode(8);
        root.left.left = new TreeNode(0);
        root.left.right = new TreeNode(4);
        root.left.right.left = new TreeNode(3);
        root.left.right.right = new TreeNode(5);
        root.right.left = new TreeNode(7);
        root.right.right = new TreeNode(9);
        
        TreeNode p = root.left;
        TreeNode q = root.left.right;
        
        TreeNode lca = lowestCommonAncestor(root, p, q);
        System.out.println("LCA: " + lca.val);
    }
    
    public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null) return null;
        
        if (p.val < root.val && q.val < root.val) {
            return lowestCommonAncestor(root.left, p, q);
        } else if (p.val > root.val && q.val > root.val) {
            return lowestCommonAncestor(root.right, p, q);
        } else {
            return root;
        }
    }
}

