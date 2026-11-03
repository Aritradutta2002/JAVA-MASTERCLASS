package dsa.datastructures.trees.binarytrees.problems.difficult;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 987: Binary Tree Vertical Order Traversal
 * Difficulty: Hard
 * Link: https://leetcode.com/problems/binary-tree-vertical-order-traversal/
 * 
 * Problem: Return nodes column-wise from left to right.
 * 
 * Example: Input: [3,9,20,null,null,15,7] → Output: [[9],[3,15],[20],[7]]
 */

public class Binary_Tree_Vertical_Order_Traversal_LeetCode987 {
    
    public List<List<Integer>> verticalTraversal(TreeNode root) {
        TreeMap<Integer, TreeMap<Integer, PriorityQueue<Integer>>> map = new TreeMap<>();
        dfs(root, 0, 0, map);
        
        List<List<Integer>> res = new ArrayList<>();
        for (TreeMap<Integer, PriorityQueue<Integer>> ys : map.values()) {
            List<Integer> col = new ArrayList<>();
            for (PriorityQueue<Integer> pq : ys.values()) {
                while (!pq.isEmpty()) {
                    col.add(pq.poll());
                }
            }
            res.add(col);
        }
        return res;
    }
    
    private void dfs(TreeNode node, int x, int y, 
                     TreeMap<Integer, TreeMap<Integer, PriorityQueue<Integer>>> map) {
        if (node == null) return;
        
        map.computeIfAbsent(x, k -> new TreeMap<>())
           .computeIfAbsent(y, k -> new PriorityQueue<>())
           .offer(node.val);
           
        dfs(node.left, x - 1, y + 1, map);
        dfs(node.right, x + 1, y + 1, map);
    }
    
    // Test method
    public static void main(String[] args) {
        Binary_Tree_Vertical_Order_Traversal_LeetCode987 solution = new Binary_Tree_Vertical_Order_Traversal_LeetCode987();
        
        // Test case: [3,9,20,null,null,15,7]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);
        
        System.out.println("Vertical traversal: " + solution.verticalTraversal(root));
        // Expected: [[9],[3,15],[20],[7]]
    }
}

