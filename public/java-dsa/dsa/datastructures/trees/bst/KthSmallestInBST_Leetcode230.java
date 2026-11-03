package dsa.algorithms.searching.binary.basics_TREES;

import java.util.ArrayList;
import java.util.List;

/*
 *   Author  : Aritra Dutta
 *   Problem : Kth Smallest Element in a BST (Leetcode 230)
 *   Difficulty: Medium
 *   Companies: Amazon, Facebook, Google, Microsoft, Bloomberg
 */
public class KthSmallestInBST_Leetcode230 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(5);
        root.left = new TreeNode(3);
        root.right = new TreeNode(6);
        root.left.left = new TreeNode(2);
        root.left.right = new TreeNode(4);
        root.left.left.left = new TreeNode(1);
        
        int k = 3;
        System.out.println(k + "th smallest element: " + kthSmallest(root, k));
    }
    
    // Approach 1: Inorder Traversal (O(n) space)
    public static int kthSmallest(TreeNode root, int k) {
        List<Integer> nums = new ArrayList<>();
        inorder(root, nums);
        return nums.get(k - 1);
    }
    
    private static void inorder(TreeNode root, List<Integer> nums) {
        if (root == null) return;
        inorder(root.left, nums);
        nums.add(root.val);
        inorder(root.right, nums);
    }
    
    // Approach 2: Optimized with early stopping
    static int count = 0;
    static int result = 0;
    
    public static int kthSmallestOptimized(TreeNode root, int k) {
        count = 0;
        result = 0;
        inorderOptimized(root, k);
        return result;
    }
    
    private static void inorderOptimized(TreeNode root, int k) {
        if (root == null) return;
        
        inorderOptimized(root.left, k);
        count++;
        if (count == k) {
            result = root.val;
            return;
        }
        inorderOptimized(root.right, k);
    }
}

