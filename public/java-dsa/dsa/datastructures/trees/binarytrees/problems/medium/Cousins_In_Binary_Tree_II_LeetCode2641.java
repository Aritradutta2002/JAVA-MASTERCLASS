package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 2641: Cousins in Binary Tree II
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/cousins-in-binary-tree-ii/
 * 
 * Problem: Replace value of each node with sum of all its cousin nodes.
 * Cousins are nodes at the same level but with different parents.
 * 
 * Example: Input: [5,4,9,1,10,null,7] → Output: [0,0,0,7,7,null,11]
 */

public class Cousins_In_Binary_Tree_II_LeetCode2641 {
    
    public TreeNode replaceValueInTree(TreeNode root) {
        if (root == null) return null;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        List<Integer> levelSums = new ArrayList<>();
        
        // First BFS: Calculate level sums
        while (!queue.isEmpty()) {
            int size = queue.size();
            int levelSum = 0;
            
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                levelSum += node.val;
                
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
            levelSums.add(levelSum);
        }
        
        // Second BFS: Update values
        queue.offer(root);
        root.val = 0; // Root has no cousins
        int level = 0;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                
                int siblingSum = 0;
                if (node.left != null) siblingSum += node.left.val;
                if (node.right != null) siblingSum += node.right.val;
                
                if (node.left != null) {
                    node.left.val = levelSums.get(level + 1) - siblingSum;
                    queue.offer(node.left);
                }
                if (node.right != null) {
                    node.right.val = levelSums.get(level + 1) - siblingSum;
                    queue.offer(node.right);
                }
            }
            level++;
        }
        
        return root;
    }
    
    // Helper method to print tree (for testing)
    public void printLevelOrder(TreeNode root) {
        if (root == null) return;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                System.out.print(node == null ? "null " : node.val + " ");
                
                if (node != null) {
                    queue.offer(node.left);
                    queue.offer(node.right);
                }
            }
            System.out.println();
        }
    }
    
    // Test method
    public static void main(String[] args) {
        Cousins_In_Binary_Tree_II_LeetCode2641 solution = new Cousins_In_Binary_Tree_II_LeetCode2641();
        
        // Test case: [5,4,9,1,10,null,7]
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(4);
        root.right = new TreeNode(9);
        root.left.left = new TreeNode(1);
        root.left.right = new TreeNode(10);
        root.right.right = new TreeNode(7);
        
        System.out.println("Original tree:");
        solution.printLevelOrder(root);
        
        TreeNode result = solution.replaceValueInTree(root);
        System.out.println("After replacing with cousin sums:");
        solution.printLevelOrder(result);
        // Expected: [0,0,0,7,7,null,11]
    }
}

