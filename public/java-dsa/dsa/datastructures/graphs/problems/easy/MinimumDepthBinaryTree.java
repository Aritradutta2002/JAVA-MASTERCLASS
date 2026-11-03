package dsa.datastructures.graphs.problems.easy;
import java.util.*;

/**
 * LeetCode 111: Minimum Depth of Binary Tree
 * 
 * Problem: Given a binary tree, find its minimum depth. The minimum depth is the number of nodes 
 * along the shortest path from the root node down to the nearest leaf node.
 * 
 * Time Complexity: O(n) where n is number of nodes
 * Space Complexity: O(h) where h is height of tree
 */

// TreeNode definition
class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;
    TreeNode() {}
    TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}

public class MinimumDepthBinaryTree {
    
    /**
     * DFS recursive solution
     */
    public int minDepth(TreeNode root) {
        if (root == null) {
            return 0;
        }
        
        // Leaf node
        if (root.left == null && root.right == null) {
            return 1;
        }
        
        int minDepth = Integer.MAX_VALUE;
        
        // Only consider paths that lead to leaf nodes
        if (root.left != null) {
            minDepth = Math.min(minDepth, minDepth(root.left));
        }
        if (root.right != null) {
            minDepth = Math.min(minDepth, minDepth(root.right));
        }
        
        return minDepth + 1;
    }
    
    /**
     * BFS solution - more efficient for finding minimum depth
     */
    public int minDepthBFS(TreeNode root) {
        if (root == null) {
            return 0;
        }
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        int depth = 1;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                
                // If we find a leaf node, return current depth
                if (node.left == null && node.right == null) {
                    return depth;
                }
                
                if (node.left != null) {
                    queue.offer(node.left);
                }
                if (node.right != null) {
                    queue.offer(node.right);
                }
            }
            
            depth++;
        }
        
        return depth;
    }
    
    // Test method
    public static void main(String[] args) {
        MinimumDepthBinaryTree solution = new MinimumDepthBinaryTree();
        
        // Test case: [3,9,20,null,null,15,7]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);
        
        System.out.println("Minimum depth (DFS): " + solution.minDepth(root)); // Output: 2
        System.out.println("Minimum depth (BFS): " + solution.minDepthBFS(root)); // Output: 2
    }
}

