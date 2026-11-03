package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;
/*
 *   Author  : Aritra Dutta
 *   Created : Friday, 31.01.2025  07:24 pm
 */
public class Minimum_Depth_BinaryTree_LeetCode111 {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(3);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);

        System.out.println(minDepth(root));;

    }

    public static int minDepth(TreeNode root) {

        if (root == null) {
            return 0;
        }

        if (root.left == null && root.right == null) {
            return 1;
        }

        int minDepth = Integer.MAX_VALUE;

        if (root.left != null) {
            minDepth = Math.min(minDepth(root.left), minDepth);
        }

        if (root.right != null) {
            minDepth = Math.min(minDepth(root.right), minDepth);
        }

        return minDepth + 1;

    }
}


