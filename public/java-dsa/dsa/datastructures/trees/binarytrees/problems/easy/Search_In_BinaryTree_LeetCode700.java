package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
 *   Author  : Aritra Dutta
 *   Created : Friday, 31.01.2025  06:42 pm
 */
public class Search_In_BinaryTree_LeetCode700 {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        int val = sc.nextInt();

        TreeNode root = new TreeNode(4);
        root.left = new TreeNode(2);
        root.right = new TreeNode(7);
        root.left.left = new TreeNode(1);
        root.left.right = new TreeNode(3);

        System.out.println(searchBST(root, val));
    }

    public static TreeNode searchBST(TreeNode root, int val) {
        if(root == null) return null;

        if(root.val == val) return root;

        TreeNode leftNode = searchBST(root.left, val);

        if(leftNode != null) return leftNode;

        return searchBST(root.right, val);
    }
}


