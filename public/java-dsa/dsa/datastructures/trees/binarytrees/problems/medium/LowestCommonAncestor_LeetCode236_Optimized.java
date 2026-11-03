package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 236: Lowest Common Ancestor of a Binary Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/lowest-common-ancestor-of-a-binary-tree/
 * 
 * Problem: Given root and nodes p and q, return their lowest common ancestor (LCA).
 * 
 * Example: Input: root=[3,5,1,6,2,0,8,null,null,7,4], p=5, q=1 → Output: 3
 */

public class LowestCommonAncestor_LeetCode236_Optimized {
    
    // Single-pass DFS approach
    public TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {
        if (root == null || root == p || root == q) return root;
        
        TreeNode L = lowestCommonAncestor(root.left, p, q);
        TreeNode R = lowestCommonAncestor(root.right, p, q);
        
        if (L != null && R != null) return root; // Current node is LCA
        return L != null ? L : R; // Return non-null child
    }
    
    // Test method
    public static void main(String[] args) {
        LowestCommonAncestor_LeetCode236_Optimized solution = new LowestCommonAncestor_LeetCode236_Optimized();
        
        // Test case: [3,5,1,6,2,0,8,null,null,7,4], p=5, q=1
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(5);
        root.right = new TreeNode(1);
        root.left.left = new TreeNode(6);
        root.left.right = new TreeNode(2);
        root.right.left = new TreeNode(0);
        root.right.right = new TreeNode(8);
        root.left.right.left = new TreeNode(7);
        root.left.right.right = new TreeNode(4);
        
        TreeNode p = root.left; // Node 5
        TreeNode q = root.right; // Node 1
        
        TreeNode lca = solution.lowestCommonAncestor(root, p, q);
        System.out.println("LCA of " + p.val + " and " + q.val + " is: " + lca.val); // Expected: 3
    }
}

