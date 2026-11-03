package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
 *   Author  : Aritra Dutta
 *   Created : Sunday, 09.02.2025  07:08 pm
 */
public class DiameterOfBinaryTree {
    public static void main(String[] args) {
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);
    }

  /*
    1. It can be called the Longest Path from any two node.
    2. This path can pass the root but not necessary to pass the root.
  */

    static int maxDiameter = 0;

    public static int diameterOfBinaryTree(TreeNode root) {
        calculateDiameter(root);
        return maxDiameter;
    }

    static int calculateDiameter(TreeNode root) {
        if (root == null) return 0;

        int leftHeight = calculateDiameter(root.left);
        int rightHeight = calculateDiameter(root.right);

        maxDiameter = Math.max(maxDiameter, leftHeight + rightHeight);
        return Math.max(leftHeight, rightHeight) + 1;
    }
}


