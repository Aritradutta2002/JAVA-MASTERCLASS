package JAVA_DSA.BINARY_TREES.TREES_PROBLEM_SET;
/*
 *   Author  : Aritra Dutta
 *   Created : Friday, 31.01.2025  06:43 pm
 */

public class TreeNode {
    public int val;
    public TreeNode left;
    public TreeNode right;
    TreeNode() {

    }
    public TreeNode(int val) { this.val = val; }
    TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}