package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 112: Path Sum
 * Difficulty: Easy
 * Link: https://leetcode.com/problems/path-sum/
 * 
 * Problem: Determine if there's a root-to-leaf path with sum equal to targetSum.
 * 
 * Example: Input: [5,4,8,11,null,13,4,7,2,null,null,null,1], targetSum=22 → Output: true
 */

public class PathSum_LeetCode112 {
    
    // Recursive DFS approach
    public boolean hasPathSum(TreeNode root, int targetSum) {
        if (root == null) return false;
        
        // If it's a leaf node, check if current value equals remaining sum
        if (root.left == null && root.right == null) {
            return root.val == targetSum;
        }
        
        // Recursively check left and right subtrees with updated target
        return hasPathSum(root.left, targetSum - root.val) || 
               hasPathSum(root.right, targetSum - root.val);
    }
    
    // Iterative approach using stack
    public boolean hasPathSumIterative(TreeNode root, int targetSum) {
        if (root == null) return false;
        
        Stack<TreeNode> nodeStack = new Stack<>();
        Stack<Integer> sumStack = new Stack<>();
        
        nodeStack.push(root);
        sumStack.push(targetSum - root.val);
        
        while (!nodeStack.isEmpty()) {
            TreeNode node = nodeStack.pop();
            int currentSum = sumStack.pop();
            
            // Check if it's a leaf node with target sum
            if (node.left == null && node.right == null && currentSum == 0) {
                return true;
            }
            
            if (node.right != null) {
                nodeStack.push(node.right);
                sumStack.push(currentSum - node.right.val);
            }
            
            if (node.left != null) {
                nodeStack.push(node.left);
                sumStack.push(currentSum - node.left.val);
            }
        }
        
        return false;
    }
    
    // Path Sum II: Return all root-to-leaf paths with target sum
    public List<List<Integer>> pathSum(TreeNode root, int targetSum) {
        List<List<Integer>> result = new ArrayList<>();
        List<Integer> currentPath = new ArrayList<>();
        pathSumHelper(root, targetSum, currentPath, result);
        return result;
    }
    
    private void pathSumHelper(TreeNode node, int remainingSum, 
                              List<Integer> currentPath, List<List<Integer>> result) {
        if (node == null) return;
        
        currentPath.add(node.val);
        
        // If it's a leaf and sum matches
        if (node.left == null && node.right == null && remainingSum == node.val) {
            result.add(new ArrayList<>(currentPath));
        } else {
            pathSumHelper(node.left, remainingSum - node.val, currentPath, result);
            pathSumHelper(node.right, remainingSum - node.val, currentPath, result);
        }
        
        currentPath.remove(currentPath.size() - 1); // Backtrack
    }
    
    // Test method
    public static void main(String[] args) {
        PathSum_LeetCode112 solution = new PathSum_LeetCode112();
        
        // Test case: [5,4,8,11,null,13,4,7,2,null,null,null,1], targetSum=22
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(4);
        root.right = new TreeNode(8);
        root.left.left = new TreeNode(11);
        root.right.left = new TreeNode(13);
        root.right.right = new TreeNode(4);
        root.left.left.left = new TreeNode(7);
        root.left.left.right = new TreeNode(2);
        root.right.right.right = new TreeNode(1);
        
        int targetSum = 22;
        System.out.println("Has path sum " + targetSum + ": " + solution.hasPathSum(root, targetSum));
        // Expected: true (path: 5->4->11->2 = 22)
        
        System.out.println("Has path sum " + targetSum + " (iterative): " + solution.hasPathSumIterative(root, targetSum));
        // Expected: true
        
        System.out.println("All paths with sum " + targetSum + ": " + solution.pathSum(root, targetSum));
        // Expected: [[5,4,11,2],[5,8,4,5]] (if such paths exist)
    }
}

