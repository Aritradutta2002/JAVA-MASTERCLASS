package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 102: Binary Tree Level Order Traversal
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/binary-tree-level-order-traversal/
 * 
 * Problem: Return list of levels of node values (BFS level order).
 * 
 * Example: Input: [3,9,20,null,null,15,7] → Output: [[3],[9,20],[15,7]]
 */

public class Binary_Tree_Level_Order_Traversal_LeetCode102 {
    
    // BFS approach
    public List<List<Integer>> levelOrder(TreeNode root) {
        List<List<Integer>> ans = new ArrayList<>();
        if (root == null) return ans;
        
        Queue<TreeNode> q = new LinkedList<>();
        q.offer(root);
        
        while (!q.isEmpty()) {
            int sz = q.size();
            List<Integer> level = new ArrayList<>();
            
            for (int i = 0; i < sz; i++) {
                TreeNode n = q.poll();
                level.add(n.val);
                
                if (n.left != null) q.offer(n.left);
                if (n.right != null) q.offer(n.right);
            }
            ans.add(level);
        }
        return ans;
    }
    
    // DFS approach
    public List<List<Integer>> levelOrderDFS(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        dfs(root, 0, result);
        return result;
    }
    
    private void dfs(TreeNode node, int level, List<List<Integer>> result) {
        if (node == null) return;
        
        // If this is the first node of this level, create a new list
        if (level == result.size()) {
            result.add(new ArrayList<>());
        }
        
        result.get(level).add(node.val);
        
        dfs(node.left, level + 1, result);
        dfs(node.right, level + 1, result);
    }
    
    // Bottom-up level order traversal (Level Order Traversal II - LeetCode 107)
    public List<List<Integer>> levelOrderBottom(TreeNode root) {
        List<List<Integer>> result = levelOrder(root);
        Collections.reverse(result);
        return result;
    }
    
    // Test method
    public static void main(String[] args) {
        Binary_Tree_Level_Order_Traversal_LeetCode102 solution = new Binary_Tree_Level_Order_Traversal_LeetCode102();
        
        // Test case: [3,9,20,null,null,15,7]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);
        
        System.out.println("Level order traversal: " + solution.levelOrder(root));
        // Expected: [[3],[9,20],[15,7]]
        
        System.out.println("Level order traversal (DFS): " + solution.levelOrderDFS(root));
        // Expected: [[3],[9,20],[15,7]]
        
        System.out.println("Level order bottom-up: " + solution.levelOrderBottom(root));
        // Expected: [[15,7],[9,20],[3]]
    }
}

