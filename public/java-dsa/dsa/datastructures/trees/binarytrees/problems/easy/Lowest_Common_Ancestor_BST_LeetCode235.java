package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 235: Lowest Common Ancestor of a Binary Search Tree
 * Difficulty: Easy
 * Link: https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-search-tree/
 * 
 * Problem: LCA in BST uses value ordering.
 * 
 * Example: Input: root=[6,2,8,0,4,7,9,null,null,3,5], p=2,q=8 → Output: 6
 */

public class Lowest_Common_Ancestor_BST_LeetCode235 {
    
    // Iterative solution using BST property
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        TreeNode cur = root;
        while (cur != null) {
            if (p.val < cur.val && q.val < cur.val) {
                cur = cur.left;
            } else if (p.val > cur.val && q.val > cur.val) {
                cur = cur.right;
            } else {
                return cur; // Found LCA
            }
        }
        return null;
    }
    
    // Recursive solution
    public TreeNode lowestCommonAncestorRecursive(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null) return null;
        
        if (p.val < root.val && q.val < root.val) {
            return lowestCommonAncestorRecursive(root.left, p, q);
        } else if (p.val > root.val && q.val > root.val) {
            return lowestCommonAncestorRecursive(root.right, p, q);
        } else {
            return root;
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Lowest_Common_Ancestor_BST_LeetCode235 solution = new Lowest_Common_Ancestor_BST_LeetCode235();
        
        // Test case: [6,2,8,0,4,7,9,null,null,3,5], p=2, q=8
        TreeNode root = new TreeNode(6);
        root.left = new TreeNode(2);
        root.right = new TreeNode(8);
        root.left.left = new TreeNode(0);
        root.left.right = new TreeNode(4);
        root.right.left = new TreeNode(7);
        root.right.right = new TreeNode(9);
        root.left.right.left = new TreeNode(3);
        root.left.right.right = new TreeNode(5);
        
        TreeNode p = root.left; // Node 2
        TreeNode q = root.right; // Node 8
        
        TreeNode lca = solution.lowestCommonAncestor(root, p, q);
        System.out.println("LCA of " + p.val + " and " + q.val + " is: " + lca.val); // Expected: 6
    }
}

