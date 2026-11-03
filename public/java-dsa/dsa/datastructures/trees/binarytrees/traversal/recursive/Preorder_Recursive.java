package dsa.datastructures.trees.binarytrees.traversal.recursive;
/*
 *   Author  : Aritra Dutta
 *   Created : Tuesday, 31.12.2024  12:17 pm
 */

import java.util.*;

public class Preorder_Recursive {
    public static void main(String[] args) {
        // Create the binary tree
        BinaryTreeNode root = new BinaryTreeNode(1);
        root.left = new BinaryTreeNode(4);
        root.left.left = new BinaryTreeNode(4);
        root.left.right = new BinaryTreeNode(2);

        PreorderTraversal(root);
        System.out.println();
        System.out.println(preorderTraversal(root));
    }

    /*      PRE ORDER TRAVERSAL STARTS     */
   static public void PreorderTraversal(BinaryTreeNode root) {
        if (root != null) {
            System.out.print(root.data + " ");
            PreorderTraversal(root.left);
            PreorderTraversal(root.right);
        }
    }

    public static List<Integer> preorderTraversal(BinaryTreeNode root) {
       List<Integer> list = new ArrayList<>();
       if (root != null) {
           list.add(root.data);
           list.addAll(preorderTraversal(root.left));
           list.addAll(preorderTraversal(root.right));
       }
       return list;
    }
}

