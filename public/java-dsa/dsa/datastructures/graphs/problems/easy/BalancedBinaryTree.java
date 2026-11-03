package dsa.datastructures.graphs.problems.easy;
/**
 * LeetCode 110: Balanced Binary Tree
 * 
 * Problem: Given a binary tree, determine if it is height-balanced. 
 * A height-balanced binary tree is a binary tree in which the depth of the two subtrees 
 * of every node never differs by more than one.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(h) where h is height of tree
 */
public class BalancedBinaryTree {
    
    /**
     * Optimized solution - single pass
     */
    public boolean isBalanced(TreeNode root) {
        return checkBalance(root) != -1;
    }
    
    /**
     * Returns height if balanced, -1 if not balanced
     */
    private int checkBalance(TreeNode node) {
        if (node == null) {
            return 0;
        }
        
        int leftHeight = checkBalance(node.left);
        if (leftHeight == -1) return -1; // Left subtree is not balanced
        
        int rightHeight = checkBalance(node.right);
        if (rightHeight == -1) return -1; // Right subtree is not balanced
        
        // Check if current node is balanced
        if (Math.abs(leftHeight - rightHeight) > 1) {
            return -1; // Current node is not balanced
        }
        
        return Math.max(leftHeight, rightHeight) + 1;
    }
    
    /**
     * Alternative solution - separate height calculation (less efficient)
     */
    public boolean isBalancedAlternative(TreeNode root) {
        if (root == null) {
            return true;
        }
        
        int leftHeight = getHeight(root.left);
        int rightHeight = getHeight(root.right);
        
        return Math.abs(leftHeight - rightHeight) <= 1 && 
               isBalancedAlternative(root.left) && 
               isBalancedAlternative(root.right);
    }
    
    private int getHeight(TreeNode node) {
        if (node == null) {
            return 0;
        }
        return Math.max(getHeight(node.left), getHeight(node.right)) + 1;
    }
    
    // Test method
    public static void main(String[] args) {
        BalancedBinaryTree solution = new BalancedBinaryTree();
        
        // Test case 1: [3,9,20,null,null,15,7] - balanced
        TreeNode root1 = new TreeNode(3);
        root1.left = new TreeNode(9);
        root1.right = new TreeNode(20);
        root1.right.left = new TreeNode(15);
        root1.right.right = new TreeNode(7);
        
        System.out.println("Is balanced (optimized): " + solution.isBalanced(root1)); // true
        
        // Test case 2: [1,2,2,3,3,null,null,4,4] - not balanced
        TreeNode root2 = new TreeNode(1);
        root2.left = new TreeNode(2);
        root2.right = new TreeNode(2);
        root2.left.left = new TreeNode(3);
        root2.left.right = new TreeNode(3);
        root2.left.left.left = new TreeNode(4);
        root2.left.left.right = new TreeNode(4);
        
        System.out.println("Is balanced (alternative): " + solution.isBalancedAlternative(root2)); // false
    }
}

