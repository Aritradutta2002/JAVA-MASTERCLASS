package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 104: Maximum Depth of Binary Tree
 * 
 * Problem: Given the root of a binary tree, return its maximum depth. 
 * A binary tree's maximum depth is the number of nodes along the longest path 
 * from the root node down to the farthest leaf node.
 * 
 * Time Complexity: O(n)
 * Space Complexity: O(h) where h is height of tree
 */
public class MaximumDepthBinaryTree {
    
    /**
     * Recursive DFS solution
     */
    public int maxDepth(TreeNode root) {
        if (root == null) {
            return 0;
        }
        
        int leftDepth = maxDepth(root.left);
        int rightDepth = maxDepth(root.right);
        
        return Math.max(leftDepth, rightDepth) + 1;
    }
    
    /**
     * Iterative BFS solution
     */
    public int maxDepthBFS(TreeNode root) {
        if (root == null) {
            return 0;
        }
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        int depth = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            depth++;
            
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                
                if (node.left != null) {
                    queue.offer(node.left);
                }
                if (node.right != null) {
                    queue.offer(node.right);
                }
            }
        }
        
        return depth;
    }
    
    /**
     * Iterative DFS solution using stack
     */
    public int maxDepthDFS(TreeNode root) {
        if (root == null) {
            return 0;
        }
        
        Stack<TreeNode> nodeStack = new Stack<>();
        Stack<Integer> depthStack = new Stack<>();
        
        nodeStack.push(root);
        depthStack.push(1);
        
        int maxDepth = 0;
        
        while (!nodeStack.isEmpty()) {
            TreeNode node = nodeStack.pop();
            int currentDepth = depthStack.pop();
            
            maxDepth = Math.max(maxDepth, currentDepth);
            
            if (node.left != null) {
                nodeStack.push(node.left);
                depthStack.push(currentDepth + 1);
            }
            if (node.right != null) {
                nodeStack.push(node.right);
                depthStack.push(currentDepth + 1);
            }
        }
        
        return maxDepth;
    }
    
    // Test method
    public static void main(String[] args) {
        MaximumDepthBinaryTree solution = new MaximumDepthBinaryTree();
        
        // Test case: [3,9,20,null,null,15,7]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);
        
        System.out.println("Max depth (recursive): " + solution.maxDepth(root)); // 3
        System.out.println("Max depth (BFS): " + solution.maxDepthBFS(root)); // 3
        System.out.println("Max depth (DFS): " + solution.maxDepthDFS(root)); // 3
    }
}

