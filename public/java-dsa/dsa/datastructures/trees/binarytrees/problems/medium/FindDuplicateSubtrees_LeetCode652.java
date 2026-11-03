package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 * LeetCode 652: Find Duplicate Subtrees
 * Difficulty: Medium
 * Link: https://leetcode.com/problems/find-duplicate-subtrees/
 * 
 * Problem: Find all duplicate subtrees and return their root nodes.
 * 
 * Example: Input: [1,2,3,4,null,2,4,null,null,4] → Output: [[2,4],[4]]
 */

public class FindDuplicateSubtrees_LeetCode652 {
    
    private Map<String, Integer> count = new HashMap<>();
    private List<TreeNode> result = new ArrayList<>();
    
    public List<TreeNode> findDuplicateSubtrees(TreeNode root) {
        count.clear();
        result.clear();
        serialize(root);
        return result;
    }
    
    private String serialize(TreeNode node) {
        if (node == null) return "null";
        
        String serial = node.val + "," + serialize(node.left) + "," + serialize(node.right);
        
        count.put(serial, count.getOrDefault(serial, 0) + 1);
        
        if (count.get(serial) == 2) {
            result.add(node);
        }
        
        return serial;
    }
    
    // Alternative approach using postorder traversal with ID mapping
    private Map<String, Integer> trees = new HashMap<>();
    private Map<Integer, Integer> countById = new HashMap<>();
    private List<TreeNode> ans = new ArrayList<>();
    private int t = 1;
    
    public List<TreeNode> findDuplicateSubtreesOptimized(TreeNode root) {
        trees.clear();
        countById.clear();
        ans.clear();
        t = 1;
        lookup(root);
        return ans;
    }
    
    private int lookup(TreeNode node) {
        if (node == null) return 0;
        
        String serial = node.val + "," + lookup(node.left) + "," + lookup(node.right);
        int uid = trees.computeIfAbsent(serial, x -> t++);
        countById.put(uid, countById.getOrDefault(uid, 0) + 1);
        
        if (countById.get(uid) == 2) {
            ans.add(node);
        }
        
        return uid;
    }
    
    // Helper method to print tree values
    private void printTree(TreeNode root) {
        if (root == null) {
            System.out.print("null ");
            return;
        }
        System.out.print(root.val + " ");
        printTree(root.left);
        printTree(root.right);
    }
    
    // Test method
    public static void main(String[] args) {
        FindDuplicateSubtrees_LeetCode652 solution = new FindDuplicateSubtrees_LeetCode652();
        
        // Test case: [1,2,3,4,null,2,4,null,null,4]
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.right.left = new TreeNode(2);
        root.right.right = new TreeNode(4);
        root.right.left.left = new TreeNode(4);
        
        List<TreeNode> duplicates = solution.findDuplicateSubtrees(root);
        
        System.out.println("Duplicate subtrees found:");
        for (int i = 0; i < duplicates.size(); i++) {
            System.out.print("Subtree " + (i + 1) + ": ");
            solution.printTree(duplicates.get(i));
            System.out.println();
        }
        // Expected: Two duplicates - subtree [2,4] and subtree [4]
    }
}

