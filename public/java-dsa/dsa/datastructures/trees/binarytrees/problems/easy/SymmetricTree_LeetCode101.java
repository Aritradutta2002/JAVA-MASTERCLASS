package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

import java.util.*;

/*
*   Author  : Aritra Dutta
*   Created : Sunday, 09.02.2025  07:40 pm
*/
public class SymmetricTree_LeetCode101 {

    public static void main(String[] args){
        TreeNode root = new TreeNode(1);
    }

    static public boolean isSymmetric(TreeNode root) {
        if (root == null) return true;
        return isMirror(root.left, root.right);
    }

    static private boolean isMirror(TreeNode left, TreeNode right) {
        if (left == null && right == null) return true;
        if (left == null || right == null) return false;
        if (left.val != right.val) return false;
        return isMirror(left.left, right.right) && isMirror(left.right, right.left);
    }
}


