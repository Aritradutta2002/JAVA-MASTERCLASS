package dsa.datastructures.trees.binarytrees.traversal.recursive;

import java.util.ArrayList;
import java.util.List;

/*
 *   Author  : Aritra Dutta
 *   Created : Wednesday, 29.01.2025  08:56 pm
 */
public class InOrder_Recursive {

    public static void main(String[] args) {
        BinaryTreeNode root = new BinaryTreeNode(1);
        root.left = new BinaryTreeNode(4);
        root.left.left = new BinaryTreeNode(4);
        root.left.right = new BinaryTreeNode(2);

        InorderTraversal(root);
        System.out.println();
        System.out.println(inorderTraversal(root));
    }

    /*    IN ORDER TRAVERSAL STARTS    */
    static public void InorderTraversal(BinaryTreeNode root) {
        if(root != null) {
            InorderTraversal(root.left);
            System.out.print(root.data + " ");
            InorderTraversal(root.right);
        }
    }

    public static List<Integer> inorderTraversal(BinaryTreeNode root) {
        List<Integer> ans = new ArrayList<>();

        if( root != null){
            ans.addAll(inorderTraversal(root.left));
            ans.add(root.data);
            ans.addAll(inorderTraversal(root.right));
        }
        return ans;
    }
}

