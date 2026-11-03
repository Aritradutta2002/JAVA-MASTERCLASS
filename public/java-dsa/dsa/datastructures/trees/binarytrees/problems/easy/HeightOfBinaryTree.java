package dsa.datastructures.trees.binarytrees.problems.easy;
import com.dsa.datastructures.trees.binarytrees.problems.TreeNode;

/*
*   Author  : Aritra Dutta
*   Created : Monday, 10.02.2025  01:01 am
*/

/*
       # Height of a Binary Tree: --
       The height of a binary tree is the number of edges on the longest path from the root node to a leaf node.
       In other words, it's the distance from the root node to the deepest leaf node.

        # Depth of a Binary Tree: --
        The depth of a binary tree refers to the same concept—the number of edges from the root node to the leaf node.
 */
public class HeightOfBinaryTree{
    public static void main(String[] args){
        TreeNode root = new TreeNode(1);
        root.left = new TreeNode(2);
        root.right = new TreeNode(3);
        root.left.left = new TreeNode(4);
        root.left.right = new TreeNode(5);
        root.right.left = new TreeNode(6);
        root.right.right = new TreeNode(7);
        root.left.left.left = new TreeNode(8);

        System.out.println(height(root));
    }

    public static int height(TreeNode root){
        if(root == null) return -1;
        return Math.max(height(root.left), height(root.right)) + 1;
    }
}


