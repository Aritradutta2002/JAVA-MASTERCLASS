package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 100: Same Tree
 * 
 * Problem: Given the roots of two binary trees p and q, write a function to check if they are the same or not.
 * Two binary trees are considered the same if they are structurally identical, and the nodes have the same value.
 * 
 * Time Complexity: O(min(m,n)) where m,n are number of nodes in trees
 * Space Complexity: O(min(m,n)) for recursion stack
 */
public class SameTree {
    
    /**
     * Recursive solution
     */
    public boolean isSameTree(TreeNode p, TreeNode q) {
        // Both null
        if (p == null && q == null) {
            return true;
        }
        
        // One null, other not
        if (p == null || q == null) {
            return false;
        }
        
        // Values must match and subtrees must be same
        return (p.val == q.val) && 
               isSameTree(p.left, q.left) && 
               isSameTree(p.right, q.right);
    }
    
    /**
     * Iterative solution using queue
     */
    public boolean isSameTreeIterative(TreeNode p, TreeNode q) {
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(p);
        queue.offer(q);
        
        while (!queue.isEmpty()) {
            TreeNode first = queue.poll();
            TreeNode second = queue.poll();
            
            if (first == null && second == null) {
                continue;
            }
            
            if (first == null || second == null || first.val != second.val) {
                return false;
            }
            
            queue.offer(first.left);
            queue.offer(second.left);
            queue.offer(first.right);
            queue.offer(second.right);
        }
        
        return true;
    }
    
    // Test method
    public static void main(String[] args) {
        SameTree solution = new SameTree();
        
        // Test case: p = [1,2,3], q = [1,2,3]
        TreeNode p = new TreeNode(1);
        p.left = new TreeNode(2);
        p.right = new TreeNode(3);
        
        TreeNode q = new TreeNode(1);
        q.left = new TreeNode(2);
        q.right = new TreeNode(3);
        
        System.out.println("Same tree (recursive): " + solution.isSameTree(p, q)); // true
        System.out.println("Same tree (iterative): " + solution.isSameTreeIterative(p, q)); // true
    }
}

