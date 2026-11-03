package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 101: Symmetric Tree
 * 
 * Problem: Given the root of a binary tree, check whether it is a mirror of itself 
 * (i.e., symmetric around its center).
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(h) where h is height of tree
 */
public class SymmetricTree {
    
    /**
     * Recursive solution
     */
    public boolean isSymmetric(TreeNode root) {
        if (root == null) {
            return true;
        }
        return isMirror(root.left, root.right);
    }
    
    private boolean isMirror(TreeNode left, TreeNode right) {
        // Both null
        if (left == null && right == null) {
            return true;
        }
        
        // One null, other not
        if (left == null || right == null) {
            return false;
        }
        
        // Values must be equal and subtrees must be mirrors
        return (left.val == right.val) && 
               isMirror(left.left, right.right) && 
               isMirror(left.right, right.left);
    }
    
    /**
     * Iterative solution using queue
     */
    public boolean isSymmetricIterative(TreeNode root) {
        if (root == null) {
            return true;
        }
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root.left);
        queue.offer(root.right);
        
        while (!queue.isEmpty()) {
            TreeNode left = queue.poll();
            TreeNode right = queue.poll();
            
            if (left == null && right == null) {
                continue;
            }
            
            if (left == null || right == null || left.val != right.val) {
                return false;
            }
            
            // Add children in mirror order
            queue.offer(left.left);
            queue.offer(right.right);
            queue.offer(left.right);
            queue.offer(right.left);
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        SymmetricTree solution = new SymmetricTree();
        
        // Test case: [1,2,2,3,4,4,3]
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(2);
        root.left.left = new TreeNode(3);
        root.left.right = new TreeNode(4);
        root.right.left = new TreeNode(4);
        root.right.right = new TreeNode(3);
        
        System.out.println("Is symmetric (recursive): " + solution.isSymmetric(root)); // true
        System.out.println("Is symmetric (iterative): " + solution.isSymmetricIterative(root)); // true
    }
}

