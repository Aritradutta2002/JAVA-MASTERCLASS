package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 230: Kth Smallest Element in a BST
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/kth-smallest-element-in-a-bst/
 * 
 * Problem: Return k-th smallest element in a BST.
 * 
 * Example: Input: root=[3,1,4,null,2], k=1 → Output: 1
 */

public class KthSmallestElementInBST_LeetCode230 {
    
    // Iterative inorder traversal
    public int kthSmallest(TreeNode root, int k) {
        Deque<TreeNode> st = new ArrayDeque<>();
        TreeNode cur = root;
        
        while (cur != null || !st.isEmpty()) {
            while (cur != null) {
                st.push(cur);
                cur = cur.left;
            }
            
            cur = st.pop();
            if (--k == 0) return cur.val;
            cur = cur.right;
        }
        
        return -1;
    }
    
    // Recursive approach
    private int count = 0;
    private int result = 0;
    
    public int kthSmallestRecursive(TreeNode root, int k) {
        count = 0;
        inorderTraversal(root, k);
        return result;
    }
    
    private void inorderTraversal(TreeNode node, int k) {
        if (node == null) return;
        
        inorderTraversal(node.left, k);
        
        count++;
        if (count == k) {
            result = node.val;
            return;
        }
        
        inorderTraversal(node.right, k);
    }
    
    // Follow-up: If BST is modified frequently, we can augment each node with count of nodes in its subtree
    static class TreeNodeWithCount {
        int val;
        int count; // Number of nodes in this subtree
        TreeNodeWithCount left, right;
        
        TreeNodeWithCount(int val) {
            this.val = val;
            this.count = 1;
        }
    }
    
    public int kthSmallestWithCount(TreeNodeWithCount root, int k) {
        int leftCount = root.left != null ? root.left.count : 0;
        
        if (k <= leftCount) {
            return kthSmallestWithCount(root.left, k);
        } else if (k > leftCount + 1) {
            return kthSmallestWithCount(root.right, k - 1 - leftCount);
        } else {
            return root.val;
        }
    }
    
    // Test method
    public static void main(String[] args) {
        KthSmallestElementInBST_LeetCode230 solution = new KthSmallestElementInBST_LeetCode230();
        
        // Test case: [3,1,4,null,2], k=1
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(1);
        root.right = new TreeNode(4);
        root.left.right = new TreeNode(2);
        
        System.out.println("1st smallest element: " + solution.kthSmallest(root, 1)); // Expected: 1
        System.out.println("2nd smallest element: " + solution.kthSmallest(root, 2)); // Expected: 2
        System.out.println("3rd smallest element: " + solution.kthSmallest(root, 3)); // Expected: 3
        System.out.println("4th smallest element: " + solution.kthSmallest(root, 4)); // Expected: 4
    }
}

