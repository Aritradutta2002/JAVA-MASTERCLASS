package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 112: Path Sum
 * 
 * Problem: Given the root of a binary tree and an integer targetSum, return true if the tree has 
 * a root-to-leaf path such that adding up all the values along the path equals targetSum.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(h) where h is height of tree
 */
public class PathSum {
    
    /**
     * Recursive DFS solution
     */
    public boolean hasPathSum(TreeNode root, int targetSum) {
        if (root == null) {
            return false;
        }
        
        // If it's a leaf node, check if remaining sum equals node value
        if (root.left == null && root.right == null) {
            return targetSum == root.val;
        }
        
        // Recursively check left and right subtrees with reduced target
        int remainingSum = targetSum - root.val;
        return hasPathSum(root.left, remainingSum) || hasPathSum(root.right, remainingSum);
    }
    
    /**
     * Iterative solution using stack
     */
    public boolean hasPathSumIterative(TreeNode root, int targetSum) {
        if (root == null) {
            return false;
        }
        
        Stack<TreeNode> nodeStack = new Stack<>();
        Stack<Integer> sumStack = new Stack<>();
        
        nodeStack.push(root);
        sumStack.push(targetSum - root.val);
        
        while (!nodeStack.isEmpty()) {
            TreeNode node = nodeStack.pop();
            int currentSum = sumStack.pop();
            
            // If it's a leaf node, check if sum is 0
            if (node.left == null && node.right == null && currentSum == 0) {
                return true;
            }
            
            if (node.left != null) {
                nodeStack.push(node.left);
                sumStack.push(currentSum - node.left.val);
            }
            
            if (node.right != null) {
                nodeStack.push(node.right);
                sumStack.push(currentSum - node.right.val);
            }
        }
        
        return false;
    }
    
    // Test method
    public static void main(String[] args) {
        PathSum solution = new PathSum();
        
        // Test case: [5,4,8,11,null,13,4,7,2,null,null,null,1], targetSum = 22
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(4);
        root.right = new TreeNode(8);
        root.left.left = new TreeNode(11);
        root.left.left.left = new TreeNode(7);
        root.left.left.right = new TreeNode(2);
        root.right.left = new TreeNode(13);
        root.right.right = new TreeNode(4);
        root.right.right.right = new TreeNode(1);
        
        System.out.println("Has path sum (recursive): " + solution.hasPathSum(root, 22)); // true
        System.out.println("Has path sum (iterative): " + solution.hasPathSumIterative(root, 22)); // true
    }
}

