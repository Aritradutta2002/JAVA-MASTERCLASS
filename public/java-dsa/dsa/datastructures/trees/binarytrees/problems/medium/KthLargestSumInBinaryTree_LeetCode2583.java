package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 2583: Kth Largest Sum in a Binary Tree
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/kth-largest-sum-in-a-binary-tree/
 * 
 * Problem: Compute sum of each level, return k-th largest.
 * 
 * Example: Input: root = [5,8,9,2,1,3,7,4,6], k = 2 → Output: 13
 */

public class KthLargestSumInBinaryTree_LeetCode2583 {
    
    public long kthLargestLevelSum(TreeNode root, int k) {
        Queue<TreeNode> q = new LinkedList<>(); 
        q.offer(root);
        List<Long> levels = new ArrayList<>();
        
        while (!q.isEmpty()) {
            int sz = q.size(); 
            long sum = 0;
            
            for (int i = 0; i < sz; i++) {
                TreeNode n = q.poll(); 
                sum += n.val;
                
                if (n.left != null) q.offer(n.left);
                if (n.right != null) q.offer(n.right);
            }
            levels.add(sum);
        }
        
        if (k > levels.size()) return -1;
        
        levels.sort(Collections.reverseOrder());
        return levels.get(k - 1);
    }
    
    // Test method
    public static void main(String[] args) {
        KthLargestSumInBinaryTree_LeetCode2583 solution = new KthLargestSumInBinaryTree_LeetCode2583();
        
        // Test case: [5,8,9,2,1,3,7,4,6], k = 2
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(8);
        root.right = new TreeNode(9);
        root.left.left = new TreeNode(2);
        root.left.right = new TreeNode(1);
        root.right.left = new TreeNode(3);
        root.right.right = new TreeNode(7);
        root.left.left.left = new TreeNode(4);
        root.left.left.right = new TreeNode(6);
        
        System.out.println("Kth largest level sum: " + solution.kthLargestLevelSum(root, 2)); 
        // Expected: 13 (level sums: [5, 17, 13, 10] -> sorted: [17, 13, 10, 5] -> 2nd largest: 13)
    }
}

