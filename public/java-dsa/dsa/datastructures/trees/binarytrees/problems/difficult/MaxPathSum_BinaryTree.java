package dsa.datastructures.trees.binarytrees.problems.difficult;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;
/*
*   Author  : Aritra Dutta
*   Created : Sunday, 09.02.2025  07:29 pm
*/
import java.util.*;
public class MaxPathSum_BinaryTree {
    public static void main(String[] args){
        TreeNode root = new TreeNode(-10);
        root.left = new TreeNode(9);
        root.right = new TreeNode(20);
        root.right.left = new TreeNode(15);
        root.right.right = new TreeNode(7);

        System.out.println(maxPathSum(root));
    }

    static int maxi = Integer.MIN_VALUE;

    static public int maxPathSum(TreeNode root) {
        findMaxPathSum(root);
        return maxi;
    }

    static int findMaxPathSum(TreeNode root) {
        if (root == null) return 0;

        int leftMaxPathSum = Math.max(0, findMaxPathSum(root.left));
        int rightMaxPathSum = Math.max(0, findMaxPathSum(root.right));

        maxi = Math.max(maxi, root.val + leftMaxPathSum + rightMaxPathSum);

        return Math.max(leftMaxPathSum, rightMaxPathSum) + root.val;
    }
}


