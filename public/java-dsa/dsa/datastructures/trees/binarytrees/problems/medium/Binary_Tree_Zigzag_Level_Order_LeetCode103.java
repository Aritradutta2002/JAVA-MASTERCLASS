package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 103: Binary Tree Zigzag Level Order Traversal (Alternative Implementation)
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/binary-tree-zigzag-level-order-traversal/
 * 
 * Problem: Return zigzag level order (alternate left→right, right→left).
 * 
 * Example: Input: [3,9,20,null,null,15,7] → Output: [[3],[20,9],[15,7]]
 */

public class Binary_Tree_Zigzag_Level_Order_LeetCode103 {
    
    // Using Deque for zigzag traversal
    public List<List<Integer>> zigzagLevelOrder(TreeNode root) {
        List<List<Integer>> ans = new ArrayList<>();
        if (root == null) return ans;
        
        Deque<TreeNode> dq = new ArrayDeque<>();
        dq.offer(root);
        boolean leftToRight = true;
        
        while (!dq.isEmpty()) {
            int sz = dq.size();
            List<Integer> level = new ArrayList<>();
            
            for (int i = 0; i < sz; i++) {
                if (leftToRight) {
                    TreeNode n = dq.pollFirst();
                    level.add(n.val);
                    if (n.left != null) dq.offerLast(n.left);
                    if (n.right != null) dq.offerLast(n.right);
                } else {
                    TreeNode n = dq.pollLast();
                    level.add(n.val);
                    if (n.right != null) dq.offerFirst(n.right);
                    if (n.left != null) dq.offerFirst(n.left);
                }
            }
            
            leftToRight = !leftToRight;
            ans.add(level);
        }
        return ans;
    }
    
    // Alternative: BFS with direction flag and reverse
    public List<List<Integer>> zigzagLevelOrderReverse(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        if (root == null) return result;
        
        Queue<TreeNode> queue = new LinkedList<>();
        queue.offer(root);
        boolean leftToRight = true;
        
        while (!queue.isEmpty()) {
            int size = queue.size();
            List<Integer> level = new ArrayList<>();
            
            for (int i = 0; i < size; i++) {
                TreeNode node = queue.poll();
                level.add(node.val);
                
                if (node.left != null) queue.offer(node.left);
                if (node.right != null) queue.offer(node.right);
            }
            
            if (!leftToRight) {
                Collections.reverse(level);
            }
            
            result.add(level);
            leftToRight = !leftToRight;
        }
        
        return result;
    }
    
    // DFS approach
    public List<List<Integer>> zigzagLevelOrderDFS(TreeNode root) {
        List<List<Integer>> result = new ArrayList<>();
        dfs(root, 0, result);
        return result;
    }
    
    private void dfs(TreeNode node, int level, List<List<Integer>> result) {
        if (node == null) return;
        
        if (level == result.size()) {
            result.add(new LinkedList<>());
        }
        
        if (level % 2 == 0) {
            // Left to right
            result.get(level).add(node.val);
        } else {
            // Right to left
            result.get(level).add(0, node.val);
        }
        
        dfs(node.left, level + 1, result);
        dfs(node.right, level + 1, result);
    }
    
    // Test method
    public static void main(String[] args) {
        Binary_Tree_Zigzag_Level_Order_LeetCode103 solution = new Binary_Tree_Zigzag_Level_Order_LeetCode103();
        
        // Test case: [3,9,20,null,null,15,7]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);
        
        System.out.println("Zigzag traversal (Deque): " + solution.zigzagLevelOrder(root));
        // Expected: [[3],[20,9],[15,7]]
        
        System.out.println("Zigzag traversal (Reverse): " + solution.zigzagLevelOrderReverse(root));
        // Expected: [[3],[20,9],[15,7]]
        
        System.out.println("Zigzag traversal (DFS): " + solution.zigzagLevelOrderDFS(root));
        // Expected: [[3],[20,9],[15,7]]
    }
}

