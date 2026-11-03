package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 572: Subtree of Another Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/subtree-of-another-tree/
 * 
 * Problem: Check if subRoot is a subtree of root.
 * 
 * Example: root = [3,4,5,1,2], subRoot = [4,1,2] → Output: true
 */

public class SubtreeOfAnotherTree_LeetCode572 {
    
    public boolean isSubtree(TreeNode root, TreeNode subRoot) {
        if (root == null) return false;
        
        return isSameTree(root, subRoot) || 
               isSubtree(root.left, subRoot) || 
               isSubtree(root.right, subRoot);
    }
    
    private boolean isSameTree(TreeNode p, TreeNode q) {
        if (p == null && q == null) return true;
        if (p == null || q == null) return false;
        
        return p.val == q.val && 
               isSameTree(p.left, q.left) && 
               isSameTree(p.right, q.right);
    }
    
    // Alternative solution using string matching
    public boolean isSubtreeStringMatching(TreeNode root, TreeNode subRoot) {
        String rootStr = serialize(root);
        String subStr = serialize(subRoot);
        return rootStr.contains(subStr);
    }
    
    private String serialize(TreeNode node) {
        if (node == null) return "null";
        return "#" + node.val + " " + serialize(node.left) + " " + serialize(node.right);
    }
    
    // Test method
    public static void main(String[] args) {
        SubtreeOfAnotherTree_LeetCode572 solution = new SubtreeOfAnotherTree_LeetCode572();
        
        // Test case: root = [3,4,5,1,2], subRoot = [4,1,2]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(4);
        root.right = new TreeNode(5);
        root.left.left = new TreeNode(1);
        root.left.right = new TreeNode(2);
        
        TreeNode subRoot = new TreeNode(4);
        subRoot.left = new TreeNode(1);
        subRoot.right = new TreeNode(2);
        
        System.out.println("Is subtree: " + solution.isSubtree(root, subRoot)); // Expected: true
    }
}

