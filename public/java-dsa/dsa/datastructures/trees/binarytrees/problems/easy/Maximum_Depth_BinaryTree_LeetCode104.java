package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;
/*
 *   Author  : Aritra Dutta
 *   Created : Friday, 31.01.2025  06:59 pm
 */
public class Maximum_Depth_BinaryTree_LeetCode104 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);

        System.out.println(maxDepth(root));;
    }

    public static int maxDepth(TreeNode root) {
        int ans = 0;
        if(root == null) return 0;

        return 1 + Math.max(maxDepth(root.left), maxDepth(root.right));
    }
}


