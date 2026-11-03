package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 * LeetCode 1448: Count Good Nodes in Binary Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/count-good-nodes-in-binary-tree/
 * 
 * Problem: Count nodes where node.val >= all ancestor values.
 * 
 * Example: Input: [3,1,4,3,null,1,5] → Output: 4
 */

public class Count_Good_Nodes_In_Binary_Tree_LeetCode1448 {
    
    public int goodNodes(TreeNode root) {
        return dfs(root, Integer.MIN_VALUE);
    }
    
    private int dfs(TreeNode node, int maxSoFar) {
        if (node == null) return 0;
        
        int res = node.val >= maxSoFar ? 1 : 0;
        maxSoFar = Math.max(maxSoFar, node.val);
        
        return res + dfs(node.left, maxSoFar) + dfs(node.right, maxSoFar);
    }
    
    // Test method
    public static void main(String[] args) {
        Count_Good_Nodes_In_Binary_Tree_LeetCode1448 solution = new Count_Good_Nodes_In_Binary_Tree_LeetCode1448();
        
        // Test case: [3,1,4,3,null,1,5]
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(1);
        root.right = new TreeNode(4);
        root.left.left = new TreeNode(3);
        root.right.left = new TreeNode(1);
        root.right.right = new TreeNode(5);
        
        System.out.println("Good nodes count: " + solution.goodNodes(root)); // Expected: 4
    }
}

