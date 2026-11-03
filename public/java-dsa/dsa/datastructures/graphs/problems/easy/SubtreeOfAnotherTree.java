package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 572: Subtree of Another Tree
 * 
 * Problem: Given the roots of two binary trees root and subRoot, return true if there is a subtree of root 
 * with the same structure and node values of subRoot and false otherwise.
 * 
 * Time Complexity: O(m * n) where m,n are number of nodes
 * Space Complexity: O(max(m,n)) for recursion stack
 */
public class SubtreeOfAnotherTree {
    
    /**
     * Main solution
     */
    public boolean isSubtree(TreeNode root, TreeNode subRoot) {
        if (root == null) {
            return false;
        }
        
        // Check if trees are same starting from current root
        // Or check if subRoot is subtree of left or right subtree
        return isSameTree(root, subRoot) || 
               isSubtree(root.left, subRoot) || 
               isSubtree(root.right, subRoot);
    }
    
    /**
     * Helper method to check if two trees are identical
     */
    private boolean isSameTree(TreeNode p, TreeNode q) {
        if (p == null && q == null) {
            return true;
        }
        
        if (p == null || q == null) {
            return false;
        }
        
        return (p.val == q.val) && 
               isSameTree(p.left, q.left) && 
               isSameTree(p.right, q.right);
    }
    
    /**
     * Alternative solution using string serialization
     */
    public boolean isSubtreeString(TreeNode root, TreeNode subRoot) {
        String rootStr = serialize(root);
        String subRootStr = serialize(subRoot);
        return rootStr.contains(subRootStr);
    }
    
    private String serialize(TreeNode node) {
        if (node == null) {
            return "null";
        }
        return "#" + node.val + " " + serialize(node.left) + " " + serialize(node.right);
    }
    
    // Test method
    public static void main(String[] args) {
        SubtreeOfAnotherTree solution = new SubtreeOfAnotherTree();
        
        // Test case: root = [3,4,5,1,2], subRoot = [4,1,2]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(4);
        root.right = new TreeNode(5);
        root.left.left = new TreeNode(1);
        root.left.right = new TreeNode(2);
        
        TreeNode subRoot = new TreeNode(4);
        subRoot.left = new TreeNode(1);
        subRoot.right = new TreeNode(2);
        
        System.out.println("Is subtree: " + solution.isSubtree(root, subRoot)); // true
        System.out.println("Is subtree (string): " + solution.isSubtreeString(root, subRoot)); // true
    }
}

