package dsa.algorithms.searching.binary.basics_TREES;
/*
*   Author  : Aritra Dutta
*   Created : Friday, 28.02.2025  01:22 am
*/
public class TreeNode {
    int val;
    TreeNode left;
    TreeNode right;

    public TreeNode() {}

    public TreeNode(int val) {
        this.val = val;
    }

    public TreeNode(int val, TreeNode left, TreeNode right) {
        this.val = val;
        this.left = left;
        this.right = right;
    }
}

