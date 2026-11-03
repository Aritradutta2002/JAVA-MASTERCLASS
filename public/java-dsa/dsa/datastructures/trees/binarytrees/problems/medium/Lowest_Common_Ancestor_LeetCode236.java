package dsa.datastructures.trees.binarytrees.problems.medium;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;
import java.lang.*;
import java.math.*;
/*
*   Author : Aritra
*   Created On: Wednesday,14.05.2025 10:46 pm 22 46
*/

class LowestCommonAncestor_Leetcode236 {
    public static void main(String[] args){
        // Test Case 1
        System.out.println("Test Case 1:");
        TreeNode root1 = buildTreeExample1();
        TreeNode p1 = root1.left;
        TreeNode q1 = root1.right;
        TreeNode result1 = lowestCommonAncestor(root1, p1, q1);
        System.out.println("LCA of nodes 5 and 1 is: " + result1.val);
        
        // Test Case 2
        System.out.println("\nTest Case 2:");
        TreeNode root2 = buildTreeExample1();
        TreeNode p2 = root2.left;
        TreeNode q2 = root2.left.right.right;
        TreeNode result2 = lowestCommonAncestor(root2, p2, q2);
        System.out.println("LCA of nodes 5 and 4 is: " + result2.val);
        
        // Test Case 3
        System.out.println("\nTest Case 3:");
        TreeNode root3 = new TreeNode(1);
        root3.left = new TreeNode(2);
        TreeNode p3 = root3;
        TreeNode q3 = root3.left;
        TreeNode result3 = lowestCommonAncestor(root3, p3, q3);
        System.out.println("LCA of nodes 1 and 2 is: " + result3.val);
    }

    private static TreeNode buildTreeExample1() {
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(5);
        root.right = new TreeNode(1);
        root.left.left = new TreeNode(6);
        root.left.right = new TreeNode(2);
        root.right.left = new TreeNode(0);
        root.right.right = new TreeNode(8);
        root.left.right.left = new TreeNode(7);
        root.left.right.right = new TreeNode(4);
        return root;
    }

    public static TreeNode lowestCommonAncestor(TreeNode root, TreeNode p, TreeNode q) {

        if (root == null || root == p || root == q) {
            return root;
        }

        TreeNode leftResult = lowestCommonAncestor(root.left, p, q);
        TreeNode rightResult = lowestCommonAncestor(root.right, p, q);

        if (leftResult != null && rightResult != null) {
            return root;
        }

        return leftResult != null ? leftResult : rightResult;
    }
}


