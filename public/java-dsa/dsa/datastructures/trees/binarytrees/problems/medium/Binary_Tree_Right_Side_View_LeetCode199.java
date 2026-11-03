package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;
import java.util.*;

/*
 * LeetCode 199: Binary Tree Right Side View
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/binary-tree-right-side-view/
 * 
 * Problem: Return values visible from right side.
 * 
 * Example: Input: [1,2,3,null,5,null,4] → Output: [1,3,4]
 */

public class Binary_Tree_Right_Side_View_LeetCode199 {
    
    public List<Integer> rightSideView(TreeNode root) {
        List<Integer> res = new ArrayList<>();
        if (root == null) return res;
        
        Queue<TreeNode> q = new LinkedList<>(); 
        q.add(root);
        
        while (!q.isEmpty()) {
            int size = q.size();
            for (int i = 0; i < size; i++) {
                TreeNode node = q.poll();
                if (i == size - 1) res.add(node.val); // Last node in level
                if (node.left != null) q.add(node.left);
                if (node.right != null) q.add(node.right);
            }
        }
        return res;
    }
    
    // Alternative DFS solution
    public List<Integer> rightSideViewDFS(TreeNode root) {
        List<Integer> res = new ArrayList<>();
        dfs(root, 0, res); 
        return res;
    }
    
    private void dfs(TreeNode node, int depth, List<Integer> res){
        if(node == null) return;
        if(depth == res.size()) res.add(node.val);
        dfs(node.right, depth + 1, res);
        dfs(node.left, depth + 1, res);
    }
    
    // Test method
    public static void main(String[] args) {
        Binary_Tree_Right_Side_View_LeetCode199 solution = new Binary_Tree_Right_Side_View_LeetCode199();
        
        // Test case: [1,2,3,null,5,null,4]
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.right = new TreeNode(5);
        root.right.right = new TreeNode(4);
        
        System.out.println("Right side view: " + solution.rightSideView(root)); // Expected: [1,3,4]
    }
}

